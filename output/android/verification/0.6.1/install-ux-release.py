"""Install the verified local candidate without clearing data; private device details stay outside Git."""
from datetime import datetime, timezone
import hashlib
import json
from pathlib import Path
import re
import subprocess
import sys

ROOT = Path(__file__).resolve().parents[4]
FOLDER = Path(__file__).resolve().parent
PRIVATE = Path('F:/BuildKeys/sahara')
ADB = Path('C:/Users/basit/AppData/Local/Android/Sdk/platform-tools/adb.exe')
JAVA = Path('C:/Program Files/Android/Android Studio/jbr/bin/java.exe')
SIGNER = Path('C:/Users/basit/AppData/Local/Android/Sdk/build-tools/36.1.0/lib/apksigner.jar')
PACKAGE = 'in.wedevit.sahara'
EXPECTED_CERT = '607D227A8B25BF7126BBCD742110BBBA26F8BA9D75A2F47917013E9589341AD2'

def command(args, timeout=180):
    return subprocess.run([str(x) for x in args], stdout=subprocess.PIPE, stderr=subprocess.PIPE, timeout=timeout)

def sha(path):
    with path.open('rb') as stream:
        return hashlib.file_digest(stream, 'sha256').hexdigest().upper()

def certificate(path):
    result = command([JAVA, '-Xmx128m', '-XX:+UseSerialGC', '-XX:ActiveProcessorCount=1', '-jar', SIGNER, 'verify', '--print-certs', path])
    assert result.returncode == 0, 'APK signature verification failed'
    matches = re.findall(r'Signer #\d+ certificate SHA-256 digest: (\w+)', result.stdout.decode())
    assert len(matches) == 1, 'Ambiguous signer identity'
    return matches[0].upper()

def package_version(output):
    code = re.search(r'\bversionCode=(\d+)', output)
    name = re.search(r'\bversionName=(\S+)', output)
    return dict(versionCode=int(code[1]) if code else None, versionName=name[1] if name else None)

receipt = dict(startedAt=datetime.now(timezone.utc).isoformat(), versionName='0.6.1', versionCode=8,
               device='Samsung A34', dataCleared=False, uninstalled=False, playUploaded=False,
               privateDeviceDetails='Stored outside the repository', status='checking')
private = {}

def save():
    (FOLDER / 'ux-device-release-install.json').write_text(json.dumps(receipt, indent=2)+'\n', encoding='utf-8')
    (PRIVATE / 'ux-061-device-install-private.json').write_text(json.dumps(private, indent=2)+'\n', encoding='utf-8')

try:
    verified = json.loads((FOLDER / 'ux-artifacts-verification.json').read_text(encoding='utf-8-sig'))
    assert verified['verified'], 'Artifact verification is incomplete'
    release = verified['releaseApk']
    apk = ROOT / release['path']
    assert sha(apk) == release['sha256'], 'The verified release APK changed'
    assert certificate(apk) == EXPECTED_CERT, 'Candidate signer differs from the existing upload key'
    receipt.update(apk=release['path'], apkSha256=release['sha256'], signerCertificateSha256=EXPECTED_CERT)
    devices = command([ADB, 'devices']).stdout.decode().splitlines()
    serials = [line.split()[0] for line in devices if len(line.split()) == 2 and line.split()[1] == 'device']
    assert len(serials) == 1, 'Expected one authorized connected Android device'
    private['serial'] = serials[0]
    def adb(*args, timeout=180): return command([ADB, '-s', serials[0], *args], timeout)
    private['model'] = adb('shell', 'getprop', 'ro.product.model').stdout.decode().strip()
    assert private['model'] == 'SM-A346E', 'Connected device is not the previously selected Samsung A34'
    # adb devices -l normalizes the model to SM_A346E; getprop retains SM-A346E.
    authorized = (PRIVATE / 'user-device-current-signed-inventory.txt').read_text(encoding='utf-8-sig')
    selected = [line.split() for line in authorized.splitlines()
                if 'model:SM_A346E' in line and len(line.split()) > 1 and line.split()[1] == 'device']
    assert len(selected) == 1 and selected[0][0] == serials[0], 'Connected device differs from the earlier authorized inventory'
    receipt['samePreviouslyAuthorizedDevice'] = True
    private['packageBefore'] = adb('shell', 'dumpsys', 'package', PACKAGE).stdout.decode(errors='replace')
    receipt['installedBefore'] = package_version(private['packageBefore'])
    paths = adb('shell', 'pm', 'path', PACKAGE).stdout.decode().splitlines()
    bases = [line.removeprefix('package:').strip() for line in paths if line.endswith('/base.apk')]
    assert len(bases) == 1, 'Existing production APK could not be inspected'
    assert receipt['installedBefore']['versionCode'] is not None and receipt['installedBefore']['versionCode'] <= 8, 'Refusing an unknown version or downgrade'
    before = PRIVATE / 'ux-before-update-base.apk'
    pulled = adb('pull', bases[0], before, timeout=240)
    assert pulled.returncode == 0 and before.exists(), 'Could not inspect the installed APK'
    assert certificate(before) == EXPECTED_CERT, 'Installed signer differs; no uninstall or data clear was performed'
    if sha(before) == release['sha256']:
        receipt['installSkippedAlreadyExact'] = True
        receipt['installSucceeded'] = True
    else:
        receipt['status'] = 'installing'; save()
        result = adb('install', '-r', apk, timeout=240)
        private['installOutput'] = (result.stdout+result.stderr).decode(errors='replace')
        receipt['installSucceeded'] = result.returncode == 0 and 'Success' in private['installOutput']
        assert receipt['installSucceeded'], 'adb install -r did not confirm success'
    private['packageAfter'] = adb('shell', 'dumpsys', 'package', PACKAGE).stdout.decode(errors='replace')
    receipt['installedAfter'] = package_version(private['packageAfter'])
    assert receipt['installedAfter'] == dict(versionCode=8, versionName='0.6.1'), 'Installed version does not match the verified candidate'
    launch = adb('shell', 'am', 'start', '-W', '-n', PACKAGE+'/in.wedevit.care.MainActivity', timeout=45)
    private['launchOutput'] = (launch.stdout+launch.stderr).decode(errors='replace')
    receipt['launchConfirmed'] = launch.returncode == 0 and bool(re.search(r'^Status:\s*ok\s*$', private['launchOutput'], re.M))
    assert receipt['launchConfirmed'], 'Normal activity launch was not confirmed'
    receipt['status'] = 'installed_and_launched'
except Exception as error:
    receipt['status'] = 'failed'
    receipt['error'] = str(error)
finally:
    receipt['finishedAt'] = datetime.now(timezone.utc).isoformat(); save()
print(json.dumps(receipt, indent=2))
sys.exit(0 if receipt['status'] == 'installed_and_launched' else 1)
