"""Promote only fresh signed outputs and record identity, crypto, source and alignment evidence."""
from datetime import datetime, timezone
import hashlib
import json
from pathlib import Path
import re
import shutil
import subprocess
import sys
import zipfile

root = Path(__file__).resolve().parents[4]
folder = Path(__file__).resolve().parent
build = json.loads((folder / 'anatomy-sizing-fixed-build-exit.json').read_text(encoding='utf-8'))
assert build['status'] == 'completed' and build['exitCode'] == 0, build
start = datetime.fromisoformat(build.get('artifactFreshnessLowerBoundUtc',build['startedAtUtc']).replace('Z', '+00:00')).timestamp()
inputs = json.loads((folder / 'anatomy-sizing-fixed-inputs-before.json').read_text(encoding='utf-8'))
units = json.loads((folder / 'anatomy-history-unit-checks.json').read_text(encoding='utf-8'))
assert units['nativeCompilationInputSha256'] == inputs['unitCoveredProductionFingerprint']
previous=json.loads((folder/'anatomy-history-inputs-before.json').read_text(encoding='utf-8'))
old={item['path']:item['sha256'] for item in previous['nativeCompilationInputs']}
delta=[item for item in inputs['nativeCompilationInputs'] if old[item['path']]!=item['sha256']]
assert [item['path'] for item in delta]==['android/app/src/main/assets/pain-body-viewer/viewer.css'], delta
assert inputs['kotlinJvmBuildSchemaInputsUnchanged'] and inputs['cssCopiesByteIdentical']
assert units['allXmlFresh'] and units['totals']['tests'] == 308 and units['totals']['failures'] == units['totals']['errors'] == 0
frozen_paths = {item['path'] for item in inputs['nativeCompilationInputs']}
for prefix in ('android/app/src/main/', 'android/app/schemas/'):
    actual_paths = {path.relative_to(root).as_posix() for path in (root / prefix).rglob('*') if path.is_file()}
    assert actual_paths == {path for path in frozen_paths if path.startswith(prefix)}, 'Frozen file inventory changed: ' + prefix
for item in inputs['nativeCompilationInputs']:
    assert hashlib.sha256((root / item['path']).read_bytes()).hexdigest().upper() == item['sha256'], item['path']
outputs = [root / 'android/app/build/outputs/apk/release/app-release.apk', root / 'android/app/build/outputs/bundle/release/app-release.aab']
destinations = [root / 'output/android/Sahara-0.6.0-24Oct-anatomy-sizing-fixed-release.apk', root / 'output/android/Sahara-0.6.0-24Oct-anatomy-sizing-fixed-release.aab']
for source, destination in zip(outputs, destinations):
    assert source.stat().st_mtime >= start, 'Stale release output: ' + str(source)
    shutil.copy2(source, destination)
apk, aab = destinations
sdk = Path('C:/Users/basit/AppData/Local/Android/Sdk/build-tools/36.1.0')
jbr = Path('C:/Program Files/Android/Android Studio/jbr/bin')
identity_report = folder / 'anatomy-sizing-fixed-release-identity-native.json'
subprocess.run([sys.executable, str(root / 'android/scripts/verify-release.py'), '--apk', str(apk), '--bundle', str(aab), '--aapt', str(sdk / 'aapt.exe'), '--version', '0.6.0', '--code', '7', '--out', str(identity_report)], check=True, stdout=subprocess.DEVNULL)
subprocess.run([str(sdk / 'zipalign.exe'), '-c', '-P', '16', '4', str(apk)], check=True)
signature = subprocess.check_output([str(jbr / 'java.exe'), '-Xmx128m', '-XX:+UseSerialGC', '-XX:ActiveProcessorCount=1', '-jar', str(sdk / 'lib/apksigner.jar'), 'verify', '--verbose', '--print-certs', str(apk)], text=True, encoding='utf-8')
apk_cert = re.search(r'Signer #1 certificate SHA-256 digest: (\w+)', signature).group(1).upper()
jar = subprocess.check_output([str(jbr / 'jarsigner.exe'), '-J-Xmx128m', '-J-XX:+UseSerialGC', '-verify', str(aab)], text=True, encoding='utf-8', stderr=subprocess.STDOUT)
assert 'jar verified.' in jar.lower(), 'Bundle JAR verification did not confirm success'
cert = subprocess.check_output([str(jbr / 'keytool.exe'), '-J-Xmx128m', '-J-XX:+UseSerialGC', '-printcert', '-jarfile', str(aab)], text=True, encoding='utf-8', stderr=subprocess.STDOUT)
aab_cert = re.search(r'SHA256:\s*([0-9A-Fa-f:]+)', cert).group(1).replace(':', '').upper()
expected_signer = '607D227A8B25BF7126BBCD742110BBBA26F8BA9D75A2F47917013E9589341AD2'
assert apk_cert == aab_cert == expected_signer, dict(apk=apk_cert, aab=aab_cert)
assets = []
with zipfile.ZipFile(apk) as az, zipfile.ZipFile(aab) as bz:
    for source in sorted((root / 'android/app/src/main/assets/pain-body-viewer').iterdir()):
        if not source.is_file(): continue
        data = source.read_bytes()
        assert az.read('assets/pain-body-viewer/' + source.name) == bz.read('base/assets/pain-body-viewer/' + source.name) == data, source.name
        assets.append(dict(name=source.name, sha256=hashlib.sha256(data).hexdigest().upper(), apkAndBundleMatchSource=True))
    assert len(assets) == 9
identity = json.loads(identity_report.read_text(encoding='utf-8'))
badging = subprocess.check_output([str(sdk / 'aapt.exe'), 'dump', 'badging', str(apk)], text=True, encoding='utf-8')
report = dict(checkedAtUtc=datetime.now(timezone.utc).isoformat(), sourceCommit=subprocess.check_output(['git', 'rev-parse', 'HEAD'], cwd=root, text=True, encoding='utf-8').strip(),
              nativeCompilationInputSha256=inputs['nativeCompilationInputSha256'], allFrozenProductionInputsUnchanged=True,
              apk=apk.relative_to(root).as_posix(), bundle=aab.relative_to(root).as_posix(),
              apkBytes=apk.stat().st_size, bundleBytes=aab.stat().st_size,
              apkSha256=identity['apkSha256'], bundleSha256=identity['bundleSha256'],
              apkManifest=identity['apkManifest'], bundleManifest=identity['bundleManifest'],
              debuggable=False, apkSignatureVerified=True, bundleJarSignatureVerified=True,
              signerCertificateSha256=apk_cert, signerMatchesPreviousUploadKey=True,
              zip16KbAlignment=True, native64BitLibraryCount=len(identity['nativeLibraries']), all64BitElf16KbAligned=True,
              sourceAssets=assets, permissions=re.findall(r"^uses-permission: name='([^']+)'", badging, re.M),
              unitCheckReport='anatomy-history-unit-checks.json', unitTestsPassed=units['totals']['tests'], unitCoveredProductionFingerprint=inputs['unitCoveredProductionFingerprint'], unitApplicability='308 tests cover unchanged Kotlin/JVM/build/schema inputs; CSS-only production delta is separately repackaged. Corrected native interaction is not established by these unit or artifact checks.', onlyProductionChangesSince308UnitPass=inputs['onlyProductionChangesSince308UnitPass'],
              instrumentation='Current test fixtures compiled; corrected-asset emulator runtime verification recorded separately',
              runtime16KbVerified=False, physicalDeviceVerified=False, uploaded=False, submitted=False)
(folder / 'anatomy-sizing-fixed-release-verification.json').write_text(json.dumps(report, indent=2) + '\n', encoding='utf-8')
build['signedArtifactVerified'] = True
build['verificationReport'] = 'output/android/verification/0.6.0/anatomy-sizing-fixed-release-verification.json'
(folder / 'anatomy-sizing-fixed-build-exit.json').write_text(json.dumps(build, indent=2) + '\n', encoding='utf-8')
print(json.dumps({key: report[key] for key in ['apk', 'bundle', 'apkBytes', 'bundleBytes', 'apkSha256', 'bundleSha256', 'apkManifest', 'signerCertificateSha256', 'zip16KbAlignment', 'native64BitLibraryCount', 'uploaded', 'submitted']}, indent=2))
