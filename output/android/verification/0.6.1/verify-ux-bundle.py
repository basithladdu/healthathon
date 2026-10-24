"""Verify a completed local 0.6.1 bundle; never build, copy, install or upload it.

First run verify-ux-artifacts.py with its --release-apk/--release-receipt options.
Then supply the existing standalone bundletool JAR with --bundletool <local.jar>.
"""
import argparse
from datetime import datetime, timezone
import hashlib
import importlib.util
import json
from pathlib import Path
import re
import sys
import xml.etree.ElementTree as ET
import zipfile


FOLDER = Path(__file__).resolve().parent
ROOT = Path(__file__).resolve().parents[4]
spec = importlib.util.spec_from_file_location("ux_artifact_checks", FOLDER / "verify-ux-artifacts.py")
ux = importlib.util.module_from_spec(spec)
spec.loader.exec_module(ux)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--bundletool", type=Path, required=True, help="Existing local standalone bundletool JAR")
    parser.add_argument("--bundle", type=Path, default=ROOT / "android/app/build/outputs/bundle/release/app-release.aab")
    parser.add_argument("--jbr-bin", type=Path, default=Path("C:/Program Files/Android/Android Studio/jbr/bin"))
    parser.add_argument("--out", type=Path, default=FOLDER / "ux-bundle-verification.json")
    args = parser.parse_args()
    report = {"checkedAtUtc": datetime.now(timezone.utc).isoformat(), "verified": False,
              "localCandidateOnly": True, "uploaded": False, "submitted": False,
              "deviceInteractionVerified": False, "runtime16KbVerified": False}
    try:
        receipt_path = FOLDER / "ux-release-build-exit.json"
        receipt, start, finish = ux.successful_build(receipt_path)
        report["completedBuild"] = {"receipt": ux.relative(receipt_path), "sha256": ux.sha(receipt_path), **receipt}
        frozen, _ = ux.frozen_inputs(FOLDER / "ux-inputs-before.json", start)
        report["frozenInputs"] = frozen
        proof_path = FOLDER / "ux-artifacts-verification.json"
        proof = ux.read_json(proof_path)
        ux.require(proof.get("verified") is True, "Current APK/unit verification has not passed")
        ux.require(proof["frozenInputs"]["sha256"] == frozen["sha256"], "APK proof covers another frozen input snapshot")
        release = proof["releaseApk"]
        expected = {"package": "in.wedevit.sahara", "versionCode": "8", "versionName": "0.6.1"}
        ux.require(release["manifest"] == expected and release.get("signatureVerified") is True
                   and release.get("debuggable") is False and release.get("signerCertificateSha256") == ux.UPLOAD_SIGNER,
                   "Current release APK identity/signature proof is incomplete")
        apk = (ROOT / release["path"]).resolve()
        ux.require(ux.sha(apk) == release["sha256"], "The verified current release APK changed")
        ux.fresh(apk, start, finish)
        bundle = args.bundle.resolve()
        ux.fresh(bundle, start, finish)
        ux.require(args.bundletool.is_file(), "Existing local bundletool JAR is missing")
        java = [args.jbr_bin / "java.exe", "-Xmx256m", "-XX:+UseSerialGC", "-XX:ActiveProcessorCount=1",
                "-jar", args.bundletool.resolve()]
        ux.command(java + ["validate", "--bundle=" + str(bundle)])
        manifest_xml = ux.command(java + ["dump", "manifest", "--bundle=" + str(bundle), "--module=base"])
        manifest = ET.fromstring(manifest_xml.strip())
        android = "{http://schemas.android.com/apk/res/android}"
        identity = {"package": manifest.get("package"), "versionCode": manifest.get(android + "versionCode"),
                    "versionName": manifest.get(android + "versionName")}
        ux.require(identity == expected, "Unexpected actual bundle manifest: " + str(identity))
        jar_result = ux.command([args.jbr_bin / "jarsigner.exe", "-J-Xmx128m", "-J-XX:+UseSerialGC", "-verify", bundle])
        ux.require("jar verified." in jar_result.lower(), "JAR verifier did not confirm the bundle signature")
        ux.require(not re.search(r"unsigned entries|jar is unsigned|not signed", jar_result, re.I),
                   "JAR verifier found unsigned bundle content")
        certificates = ux.command([args.jbr_bin / "keytool.exe", "-J-Xmx128m", "-J-XX:+UseSerialGC", "-printcert", "-jarfile", bundle])
        digests = {value.replace(":", "").upper() for value in re.findall(r"SHA256:\s*([0-9a-fA-F:]+)", certificates)}
        ux.require(digests == {ux.UPLOAD_SIGNER}, "Bundle signer differs from the verified upload certificate")
        assets, libraries = [], []
        asset_dir = ROOT / "android/app/src/main/assets/pain-body-viewer"
        with zipfile.ZipFile(bundle) as bz, zipfile.ZipFile(apk) as az:
            ux.require(bz.testzip() is None, "Bundle archive CRC check failed")
            # Cryptographic JAR verification plus coverage of every non-signature archive entry.
            jar_manifest = bz.read("META-INF/MANIFEST.MF").decode("utf-8")
            unfolded = re.sub(r"\r?\n ", "", jar_manifest)
            signed_names = set(re.findall(r"^Name: (.+?)\r?$", unfolded, re.M))
            payloads = {name for name in bz.namelist() if not name.endswith("/")
                        and not re.fullmatch(r"META-INF/(?:MANIFEST\.MF|[^/]+\.(?:SF|RSA|DSA|EC))", name, re.I)}
            ux.require(payloads <= signed_names, "Bundle contains entries not covered by the verified JAR manifest")
            current_assets = sorted(path for path in asset_dir.rglob("*") if path.is_file())
            asset_names = {path.relative_to(asset_dir).as_posix() for path in current_assets}
            packaged_assets = {name.removeprefix("base/assets/pain-body-viewer/") for name in bz.namelist()
                               if name.startswith("base/assets/pain-body-viewer/") and not name.endswith("/")}
            ux.require(asset_names and packaged_assets == asset_names, "Bundle viewer asset inventory differs")
            for source in current_assets:
                name, data = source.relative_to(asset_dir).as_posix(), source.read_bytes()
                ux.require(bz.read("base/assets/pain-body-viewer/" + name)
                           == az.read("assets/pain-body-viewer/" + name) == data,
                           "Bundle/APK/Android pain asset bytes differ: " + name)
                assets.append({"name": name, "bytes": len(data), "sha256": hashlib.sha256(data).hexdigest().upper(),
                               "bundleAndVerifiedApkMatchAndroidSource": True})
            apk_native = {name for name in az.namelist() if name.endswith(".so")
                          and name.startswith(("lib/arm64-v8a/", "lib/x86_64/"))}
            bundle_native = {name.removeprefix("base/") for name in bz.namelist() if name.endswith(".so")
                             and name.startswith(("base/lib/arm64-v8a/", "base/lib/x86_64/"))}
            ux.require(apk_native and apk_native == bundle_native, "Bundle/APK 64-bit native inventories differ")
            for name in sorted(apk_native):
                digest, size = hashlib.sha256(), 0
                with az.open(name) as left, bz.open("base/" + name) as right:
                    while True:
                        block = left.read(1024 * 1024)
                        ux.require(block == right.read(len(block) or 1), "Bundle/APK native bytes differ: " + name)
                        if not block:
                            break
                        digest.update(block)
                        size += len(block)
                libraries.append({"path": name, "bytes": size, "sha256": digest.hexdigest().upper(),
                                  "bundleBytesMatchVerifiedCurrentApk": True})
        ux.require(ux.frozen_inputs(FOLDER / "ux-inputs-before.json", start)[0] == frozen,
                   "Frozen inputs changed during bundle verification")
        ux.require(ux.viewer_assets(apk) == release["painViewerAssets"], "Viewer inputs changed since the APK proof")
        report.update(verified=True, bundle=ux.relative(bundle), bundleBytes=bundle.stat().st_size,
                      bundleSha256=ux.sha(bundle), bundleManifest=identity, bundletoolValidated=True,
                      bundletoolJarSha256=ux.sha(args.bundletool), zipCrcVerified=True,
                      jarSignatureVerified=True, allArchivePayloadsSigned=True,
                      signerCertificateSha256=ux.UPLOAD_SIGNER, signerMatchesCurrentReleaseApk=True,
                      verifiedApk=release["path"], verifiedApkSha256=release["sha256"],
                      apkProof=ux.relative(proof_path), apkProofSha256=ux.sha(proof_path),
                      sourceAssets=assets, native64BitLibraries=libraries,
                      priorStaticAlignmentBasis=release.get("nativeLibraryContinuity"),
                      verificationBoundary="Manifest, bundle validation, signatures, archive CRC and byte parity only; no device/runtime or Play approval claim.")
    except Exception as failure:
        report["failure"] = str(failure)
        args.out.write_text(json.dumps(report, indent=2) + "\n", encoding="utf-8")
        print(json.dumps({"verified": False, "failure": str(failure), "report": str(args.out)}, indent=2))
        return 1
    args.out.write_text(json.dumps(report, indent=2) + "\n", encoding="utf-8")
    print(json.dumps({"verified": True, "report": str(args.out), "bundleSha256": report["bundleSha256"],
                      "manifest": report["bundleManifest"], "nativeLibraryCount": len(libraries)}, indent=2))
    return 0


if __name__ == "__main__":
    sys.exit(main())
