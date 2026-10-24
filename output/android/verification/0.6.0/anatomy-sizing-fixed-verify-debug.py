import hashlib
import json
from pathlib import Path
import re
import struct
import subprocess
import zipfile
from datetime import datetime, timezone

root = Path(__file__).resolve().parents[4]
folder = Path(__file__).resolve().parent
build=json.loads((folder/'anatomy-sizing-fixed-build-exit.json').read_text(encoding='utf-8'))
assert build['status']=='completed' and build['exitCode']==0
start=datetime.fromisoformat(build.get('artifactFreshnessLowerBoundUtc',build['startedAtUtc']).replace('Z','+00:00')).timestamp()
from shutil import copy2
apk=root/'output/android/Sahara-0.6.0-24Oct-anatomy-sizing-fixed-debug.apk'
source=root/'android/app/build/outputs/apk/debug/app-debug.apk'
assert source.stat().st_mtime>=start, 'Stale debug output'
copy2(source,apk)
testSource=root/'android/app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk'
testDestination=root/'output/android/Sahara-0.6.0-24Oct-anatomy-sizing-fixed-androidTest.apk'
testInputs=json.loads((folder/'anatomy-diagnostics-test-inputs.json').read_text(encoding='utf-8'))
for item in testInputs['files']:
    assert hashlib.sha256((root/item['path']).read_bytes()).hexdigest().upper()==item['sha256'], item['path']
copy2(testSource,testDestination)
sdk = Path('C:/Users/basit/AppData/Local/Android/Sdk/build-tools/36.1.0')
jbr = Path('C:/Program Files/Android/Android Studio/jbr/bin')
badging = subprocess.check_output([str(sdk / 'aapt.exe'), 'dump', 'badging', str(apk)], text=True, encoding='utf-8')
attrs = dict(re.findall(r"(\w+)='([^']*)'", next(line for line in badging.splitlines() if line.startswith('package:'))))
identity = {key: attrs[key] for key in ('name', 'versionCode', 'versionName')}
assert identity == dict(name='in.wedevit.sahara.development', versionCode='7', versionName='0.6.0'), identity
assert 'application-debuggable' in badging
subprocess.run([str(sdk / 'zipalign.exe'), '-c', '-P', '16', '4', str(apk)], check=True)
signature = subprocess.check_output([str(jbr / 'java.exe'), '-Xmx128m', '-XX:+UseSerialGC', '-XX:ActiveProcessorCount=1', '-jar', str(sdk / 'lib/apksigner.jar'), 'verify', '--verbose', '--print-certs', str(apk)], text=True, encoding='utf-8')
cert = re.search(r'Signer #1 certificate SHA-256 digest: (\w+)', signature).group(1).upper()
assets = []
libraries = []
with zipfile.ZipFile(apk) as archive:
    assert archive.testzip() is None
    for source in sorted((root / 'android/app/src/main/assets/pain-body-viewer').iterdir()):
        if not source.is_file(): continue
        data = source.read_bytes()
        assert archive.read('assets/pain-body-viewer/' + source.name) == data, source
        assets.append(dict(name=source.name, sha256=hashlib.sha256(data).hexdigest().upper(), matchesFrozenSource=True))
    assert len(assets) == 9
    for name in sorted(archive.namelist()):
        if not name.endswith('.so') or not name.startswith(('lib/arm64-v8a/', 'lib/x86_64/')): continue
        data = archive.read(name)
        assert data[:6] == b'\x7fELF\x02\x01', name
        phoff = struct.unpack_from('<Q', data, 32)[0]
        phsize, phnum = struct.unpack_from('<HH', data, 54)
        loads = []
        for i in range(phnum):
            header = struct.unpack_from('<IIQQQQQQ', data, phoff + i * phsize)
            if header[0] == 1:
                assert header[7] >= 16384 and (header[2] - header[3]) % 16384 == 0, name
                loads.append(header[7])
        assert loads
        libraries.append(dict(path=name, sha256=hashlib.sha256(data).hexdigest().upper(), elf16KbAligned=True))
with apk.open('rb') as stream: apk_hash = hashlib.file_digest(stream, 'sha256').hexdigest().upper()
report = dict(checkedAtUtc=datetime.now(timezone.utc).isoformat(), apk=apk.relative_to(root).as_posix(),
              bytes=apk.stat().st_size, sha256=apk_hash, manifest=identity, debuggable=True,
              signatureVerified=True, signerCertificateSha256=cert, zip16KbAlignment=True, zipCrcVerified=True,
              sourceAssets=assets, nativeLibraries=libraries, runtime16KbVerified=False, physicalDeviceVerified=False,
              testApk=testDestination.relative_to(root).as_posix(), testApkSha256=hashlib.file_digest(testDestination.open('rb'),'sha256').hexdigest().upper(), testFixtureInputs='anatomy-diagnostics-test-inputs.json', nativeCompilationInputSha256=build['nativeCompilationInputSha256'], permissions=re.findall(r"^uses-permission: name='([^']+)'", badging, re.M))
(folder / 'anatomy-sizing-fixed-debug-artifacts.json').write_text(json.dumps(report, indent=2) + '\n', encoding='utf-8')
print(json.dumps({key: report[key] for key in ['apk', 'bytes', 'sha256', 'manifest', 'signatureVerified', 'signerCertificateSha256', 'zip16KbAlignment']} | dict(nativeLibraryCount=len(libraries), assetsVerified=len(assets)), indent=2))
