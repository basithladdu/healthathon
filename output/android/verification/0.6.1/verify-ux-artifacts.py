"""Verify the completed 0.6.1 UX checks without building, copying, installing or uploading.

Run with the bundled Python after run-ux-checks.ps1 exits. Optional signed release:
  --release-apk <path> --release-receipt <successful-build-receipt.json>
The AGP test APK normally has no app version: its target package, runner, freshness,
signature and frozen fixture inputs are checked instead of inventing a version.
"""
import argparse
from datetime import datetime, timezone
import hashlib
import json
from pathlib import Path
import re
import subprocess
import sys
import xml.etree.ElementTree as ET
import zipfile


ROOT = Path(__file__).resolve().parents[4]
FOLDER = Path(__file__).resolve().parent
VERSION = "0.6.1"
CODE = "8"
DEBUG_PACKAGE = "in.wedevit.sahara.development"
UPLOAD_SIGNER = "607D227A8B25BF7126BBCD742110BBBA26F8BA9D75A2F47917013E9589341AD2"


def require(condition, message):
    if not condition:
        raise ValueError(message)


def read_json(path):
    return json.loads(path.read_text(encoding="utf-8-sig"))


def timestamp(value):
    result = datetime.fromisoformat(value.replace("Z", "+00:00"))
    require(result.tzinfo is not None, "Receipt timestamps must include a timezone")
    return result.timestamp()


def sha(path):
    with path.open("rb") as stream:
        return hashlib.file_digest(stream, "sha256").hexdigest().upper()


def relative(path):
    return path.resolve().relative_to(ROOT).as_posix()


def successful_build(path):
    data = read_json(path)
    require(data.get("exitCode") == 0, "Build did not finish with exitCode 0: " + path.name)
    require(data.get("status", "completed") == "completed", "Build is not completed: " + path.name)
    start = data.get("startedAt", data.get("startedAtUtc"))
    finish = data.get("finishedAt", data.get("finishedAtUtc"))
    require(start and finish, "Build receipt lacks start/finish timestamps: " + path.name)
    start_time, finish_time = timestamp(start), timestamp(finish)
    require(finish_time >= start_time, "Build receipt has reversed timestamps")
    return data, start_time, finish_time


def fresh(path, start, finish):
    require(path.is_file(), "Missing output: " + str(path))
    require(start <= path.stat().st_mtime <= finish + 2, "Output is not fresh for this completed run: " + relative(path))


def command(argv):
    result = subprocess.run([str(item) for item in argv], stdout=subprocess.PIPE,
                            stderr=subprocess.STDOUT, encoding="utf-8", errors="replace", timeout=300)
    require(result.returncode == 0, Path(argv[0]).name + " verification failed: " + result.stdout[-2000:])
    return result.stdout


def inspect_apk(path, expected_package, sdk, jbr, start, finish, version_required=True):
    fresh(path, start, finish)
    with zipfile.ZipFile(path) as archive:
        require(archive.testzip() is None, "APK archive CRC failure: " + relative(path))
        require("AndroidManifest.xml" in archive.namelist(), "APK lacks manifest")
    badging = command([sdk / "aapt.exe", "dump", "badging", path])
    package_line = next((line for line in badging.splitlines() if line.startswith("package:")), "")
    attributes = dict(re.findall(r"(\w+)='([^']*)'", package_line))
    require(attributes.get("name") == expected_package, "Unexpected APK package: " + str(attributes))
    identity = {"package": attributes.get("name"), "versionCode": attributes.get("versionCode", ""),
                "versionName": attributes.get("versionName", "")}
    if version_required:
        require(identity["versionCode"] == CODE and identity["versionName"] == VERSION,
                "Unexpected APK manifest version: " + str(identity))
    signature = command([jbr / "java.exe", "-Xmx128m", "-XX:+UseSerialGC", "-XX:ActiveProcessorCount=1",
                         "-jar", sdk / "lib/apksigner.jar", "verify", "--verbose", "--print-certs", path])
    certificates = re.findall(r"Signer #\d+ certificate SHA-256 digest:\s*([0-9a-fA-F]+)", signature)
    require(len(certificates) == 1, "Expected exactly one verified APK signer")
    report = {"path": relative(path), "bytes": path.stat().st_size, "sha256": sha(path), "manifest": identity,
              "freshForCompletedBuild": True, "zipCrcVerified": True, "signatureVerified": True,
              "signerCertificateSha256": certificates[0].upper(),
              "debuggable": "application-debuggable" in badging}
    return report, badging


def viewer_assets(apk):
    directory = ROOT / "android/app/src/main/assets/pain-body-viewer"
    sources = sorted(path for path in directory.rglob("*") if path.is_file())
    require(sources, "Android pain viewer asset directory is empty")
    prefix = "assets/pain-body-viewer/"
    assets = []
    with zipfile.ZipFile(apk) as archive:
        packaged = {name[len(prefix):] for name in archive.namelist()
                    if name.startswith(prefix) and not name.endswith("/")}
        expected = {path.relative_to(directory).as_posix() for path in sources}
        require(packaged == expected, "Packaged pain viewer asset inventory differs from Android source")
        for source in sources:
            name = source.relative_to(directory).as_posix()
            data = source.read_bytes()
            require(archive.read(prefix + name) == data, "Packaged pain viewer asset differs: " + name)
            assets.append({"name": name, "bytes": len(data), "sha256": hashlib.sha256(data).hexdigest().upper(),
                           "packagedBytesMatchAndroidSource": True})
    shared = ROOT / "prototype/public/pain-body-viewer/viewer.js"
    parity = {"publicViewerExists": shared.is_file()}
    if shared.is_file():
        android = directory / "viewer.js"
        require(android.read_bytes() == shared.read_bytes(), "Public and Android viewer.js differ")
        parity.update(publicPath=relative(shared), androidPath=relative(android),
                      sha256=sha(shared), bytes=shared.stat().st_size, byteIdentical=True)
    return {"assetCount": len(assets), "allPackagedAssetsMatchSource": True, "assets": assets,
            "sharedViewerJavascript": parity}


def unchanged_release_natives(apk):
    proof_path = ROOT / "output/android/verification/0.6.0/anatomy-sizing-fixed-release-verification.json"
    proof = read_json(proof_path)
    previous = (ROOT / proof["apk"]).resolve()
    require(previous.is_relative_to(ROOT) and previous.is_file(), "Missing preserved 0.6.0 signed APK")
    previous_sha = sha(previous)
    require(previous_sha == proof["apkSha256"], "Preserved 0.6.0 APK no longer matches its verification receipt")
    require(proof.get("apkSignatureVerified") is True and proof.get("all64BitElf16KbAligned") is True,
            "Prior receipt does not establish signed APK/static 16 KB ELF verification")
    require(proof.get("signerCertificateSha256") == UPLOAD_SIGNER, "Prior static basis has another signer")
    def native_names(archive):
        return {name for name in archive.namelist()
                if name.endswith(".so") and name.startswith(("lib/arm64-v8a/", "lib/x86_64/"))}
    libraries = []
    with zipfile.ZipFile(previous) as old, zipfile.ZipFile(apk) as current:
        names = native_names(current)
        require(names and names == native_names(old), "64-bit native library inventory changed since 0.6.0")
        require(len(names) == proof["native64BitLibraryCount"], "Prior native library count differs")
        for name in sorted(names):
            digest, size = hashlib.sha256(), 0
            with current.open(name) as new_stream, old.open(name) as old_stream:
                while True:
                    block = new_stream.read(1024 * 1024)
                    require(block == old_stream.read(len(block) or 1), "Native library bytes changed: " + name)
                    if not block:
                        break
                    size += len(block)
                    digest.update(block)
            libraries.append({"path": name, "bytes": size, "sha256": digest.hexdigest().upper(),
                              "bytesIdenticalToPreservedSigned060Apk": True})
    return {"baselineApk": relative(previous), "baselineApkSha256": previous_sha,
            "priorStaticAlignmentReceipt": relative(proof_path), "priorStaticAlignmentReceiptSha256": sha(proof_path),
            "prior64BitElf16KbAlignmentVerified": True, "nativeLibraryCount": len(libraries),
            "all64BitNativeBytesUnchanged": True, "libraries": libraries,
            "staticAlignmentBasis": "Exact byte comparison with the preserved 0.6.0 signed APK whose referenced receipt verified 64-bit ELF 16 KB alignment; this is not a new runtime test.",
            "runtime16KbVerified": False}


def frozen_inputs(path, started):
    data = read_json(path)
    require(str(data.get("versionCode")) == CODE and data.get("versionName") == VERSION,
            "Frozen snapshot has the wrong version")
    require(timestamp(data["capturedAt"]) <= started, "Input snapshot was captured after the build started")
    files = data.get("files", [])
    require(files, "Frozen input snapshot is empty")
    names = [item["path"] for item in files]
    require(len(names) == len(set(names)), "Duplicate paths in frozen snapshot")
    mismatches = []
    for item in files:
        source = (ROOT / item["path"]).resolve()
        require(source.is_relative_to(ROOT), "Frozen path escapes the repository")
        if not source.is_file() or sha(source) != item["sha256"].upper():
            mismatches.append(item["path"])
    require(not mismatches, "Frozen inputs changed: " + ", ".join(mismatches))
    return {"path": relative(path), "sha256": sha(path), "fileCount": len(files),
            "allFrozenChangedInputsUnchanged": True}, files


def unit_results(start, finish, inputs):
    directory = ROOT / "android/app/build/test-results/testDebugUnitTest"
    paths = sorted(directory.glob("TEST-*.xml"))
    require(paths, "No unit-test XML reports found")
    suites, totals = [], dict(tests=0, failures=0, errors=0, skipped=0)
    for path in paths:
        fresh(path, start, finish)
        suite = ET.parse(path).getroot()
        require(suite.tag == "testsuite", "Unexpected JUnit XML root: " + path.name)
        result = {key: int(suite.get(key, "0")) for key in totals}
        require(result["tests"] == len(suite.findall("testcase")), "JUnit case count mismatch: " + path.name)
        require(not suite.findall(".//failure") and not suite.findall(".//error"), "JUnit failure: " + path.name)
        suites.append({"name": suite.get("name"), "path": relative(path), "sha256": sha(path), **result})
        for key in totals:
            totals[key] += result[key]
    require(totals["tests"] > 0 and totals["failures"] == totals["errors"] == 0, "Unit tests did not pass")
    for item in inputs:
        if item["path"].startswith("android/app/src/test/") and item["path"].endswith("Test.kt"):
            name = Path(item["path"]).stem
            require(any(suite["name"].endswith("." + name) and suite["tests"] > 0 for suite in suites),
                    "Changed test class has no fresh results: " + name)
    return {"allXmlFresh": True, "suiteCount": len(suites), "totals": totals, "suites": suites}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--sdk-tools", type=Path, default=Path("C:/Users/basit/AppData/Local/Android/Sdk/build-tools/36.1.0"))
    parser.add_argument("--jbr-bin", type=Path, default=Path("C:/Program Files/Android/Android Studio/jbr/bin"))
    parser.add_argument("--release-apk", type=Path)
    parser.add_argument("--release-receipt", type=Path)
    parser.add_argument("--out", type=Path, default=FOLDER / "ux-artifacts-verification.json")
    args = parser.parse_args()
    report = {"checkedAtUtc": datetime.now(timezone.utc).isoformat(), "verified": False,
              "instrumentationExecuted": False, "deviceInteractionVerified": False,
              "uploaded": False, "submitted": False}
    try:
        receipt_path = FOLDER / "ux-combined-checks-exit.json"
        receipt, start, finish = successful_build(receipt_path)
        report["completedBuild"] = {"receipt": relative(receipt_path), "sha256": sha(receipt_path), **receipt}
        report["frozenInputs"], inputs = frozen_inputs(FOLDER / "ux-inputs-before.json", start)
        report["unitTests"] = unit_results(start, finish, inputs)
        debug, _ = inspect_apk(ROOT / "android/app/build/outputs/apk/debug/app-debug.apk",
                               DEBUG_PACKAGE, args.sdk_tools, args.jbr_bin, start, finish)
        require(debug["debuggable"], "Debug APK is not debuggable")
        debug["painViewerAssets"] = viewer_assets(ROOT / debug["path"])
        test, badging = inspect_apk(ROOT / "android/app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk",
                                    DEBUG_PACKAGE + ".test", args.sdk_tools, args.jbr_bin, start, finish, version_required=False)
        instrumentation = next((line for line in badging.splitlines() if line.startswith("instrumentation:")), "")
        fields = dict(re.findall(r"(\w+)='([^']*)'", instrumentation))
        if not fields:
            manifest = command([args.sdk_tools / "aapt.exe", "dump", "xmltree",
                                ROOT / test["path"], "AndroidManifest.xml"])
            depth = None
            for line in manifest.splitlines():
                indent = len(line) - len(line.lstrip())
                if "E: instrumentation " in line:
                    depth = indent
                elif depth is not None:
                    if line.strip() and indent <= depth:
                        break
                    match = re.search(r'android:(name|targetPackage)(?:\([^)]*\))?="([^"]*)"', line)
                    if match:
                        fields[match.group(1)] = match.group(2)
        require(fields.get("targetPackage") == DEBUG_PACKAGE and fields.get("name") == "androidx.test.runner.AndroidJUnitRunner",
                "Test APK does not target the expected app/runner")
        require(test["signerCertificateSha256"] == debug["signerCertificateSha256"], "Debug/test APK signer mismatch")
        test["instrumentation"] = fields
        test["versionPolicy"] = "AGP test harness is identified by target package, runner, frozen fixtures, signature and freshness; its manifest version is reported as actually declared."
        report.update(debugApk=debug, androidTestApk=test)
        if args.release_apk:
            require(args.release_receipt is not None, "--release-apk requires its completed --release-receipt")
            _, release_start, release_finish = successful_build(args.release_receipt)
            require(release_start >= start, "Release receipt predates the verified UX check run")
            release, _ = inspect_apk(args.release_apk.resolve(), "in.wedevit.sahara", args.sdk_tools,
                                      args.jbr_bin, release_start, release_finish)
            require(not release["debuggable"], "Signed release APK is debuggable")
            require(release["signerCertificateSha256"] == UPLOAD_SIGNER, "Release signer differs from the 0.6.0 upload signer")
            command([args.sdk_tools / "zipalign.exe", "-c", "-P", "16", "4", args.release_apk.resolve()])
            release.update(signerMatchesPreviousUploadKey=True, zip16KbAlignment=True, runtime16KbVerified=False)
            release["painViewerAssets"] = viewer_assets(args.release_apk.resolve())
            release["nativeLibraryContinuity"] = unchanged_release_natives(args.release_apk.resolve())
            report["releaseApk"] = release
        # Recheck source identity after slow archive/signature reads to detect concurrent edits.
        frozen_inputs(FOLDER / "ux-inputs-before.json", start)
        require(viewer_assets(ROOT / debug["path"]) == debug["painViewerAssets"],
                "Pain viewer assets changed during verification")
        report["verified"] = True
    except Exception as failure:
        report["failure"] = str(failure)
        args.out.write_text(json.dumps(report, indent=2) + "\n", encoding="utf-8")
        print(json.dumps({"verified": False, "failure": str(failure), "report": str(args.out)}, indent=2))
        return 1
    args.out.write_text(json.dumps(report, indent=2) + "\n", encoding="utf-8")
    print(json.dumps({"verified": True, "report": str(args.out), "unitTotals": report["unitTests"]["totals"],
                      "debugSha256": report["debugApk"]["sha256"], "testSha256": report["androidTestApk"]["sha256"]}, indent=2))
    return 0


if __name__ == "__main__":
    sys.exit(main())
