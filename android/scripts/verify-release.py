"""Verify actual Android release identities and native alignment, never filenames alone."""
import argparse
import hashlib
import json
import re
import struct
import subprocess
import zipfile
from datetime import datetime, timezone
from pathlib import Path


def proto_fields(data):
    offset = 0
    def varint():
        nonlocal offset
        value = 0
        for shift in range(0, 70, 7):
            byte = data[offset]
            offset += 1
            value |= (byte & 127) << shift
            if byte < 128:
                return value
        raise ValueError('Invalid protobuf varint')
    result = {}
    while offset < len(data):
        tag = varint()
        number, wire = tag >> 3, tag & 7
        if wire == 0:
            value = varint()
        elif wire in (1, 5):
            length = 8 if wire == 1 else 4
            value = data[offset:offset + length]
            offset += length
        elif wire == 2:
            length = varint()
            value = data[offset:offset + length]
            offset += length
        else:
            raise ValueError('Unexpected protobuf wire type')
        result.setdefault(number, []).append(value)
    assert offset == len(data)
    return result


def bundle_identity(bundle):
    element = proto_fields(proto_fields(bundle.read('base/manifest/AndroidManifest.xml'))[1][0])
    assert element[3][0] == b'manifest'
    attributes = {}
    for value in element.get(4, []):
        attribute = proto_fields(value)
        name = attribute[2][0].decode()
        literal = attribute.get(3, [b''])[0].decode()
        if not literal and name == 'versionCode':
            literal = str(proto_fields(proto_fields(attribute[6][0])[7][0])[6][0])
        attributes[name] = literal
    return {key: attributes[key] for key in ('package', 'versionCode', 'versionName')}


def sha(data):
    return hashlib.sha256(data).hexdigest().upper()


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    for flag in ('apk', 'bundle', 'aapt', 'version', 'code', 'out'):
        parser.add_argument('--' + flag, required=True)
    parser.add_argument('--package', default='in.wedevit.sahara')
    parser.add_argument('--previous')
    args = parser.parse_args()
    expected = {'package': args.package, 'versionCode': args.code, 'versionName': args.version}
    badging = subprocess.check_output([args.aapt, 'dump', 'badging', args.apk], text=True, encoding='utf-8')
    package_line = next(line for line in badging.splitlines() if line.startswith('package:'))
    attrs = dict(re.findall(r"(\w+)='([^']*)'", package_line))
    apk_identity = {'package': attrs['name'], 'versionCode': attrs['versionCode'], 'versionName': attrs['versionName']}
    assert apk_identity == expected, apk_identity
    assert 'application-debuggable' not in badging, 'A release must not be debuggable'
    libraries = []
    previous = zipfile.ZipFile(args.previous) if args.previous else None
    try:
        with zipfile.ZipFile(args.bundle) as bundle, zipfile.ZipFile(args.apk) as apk:
            actual = bundle_identity(bundle)
            assert actual == expected, actual
            assert bundle.testzip() is None and apk.testzip() is None
            for name in sorted(apk.namelist()):
                if not name.endswith('.so') or not name.startswith(('lib/arm64-v8a/', 'lib/x86_64/')):
                    continue
                data = apk.read(name)
                assert data[:6] == b'\x7fELF\x02\x01', name
                assert data == bundle.read('base/' + name), name
                phoff = struct.unpack_from('<Q', data, 32)[0]
                phsize, phnum = struct.unpack_from('<HH', data, 54)
                loads = []
                for i in range(phnum):
                    header = struct.unpack_from('<IIQQQQQQ', data, phoff + i * phsize)
                    if header[0] == 1:
                        assert header[7] >= 16384 and (header[2] - header[3]) % 16384 == 0, name
                        loads.append(header[7])
                assert loads, name
                libraries.append({'path': name, 'sha256': sha(data), 'elf16KbAligned': True,
                                  'bundleMatchesApk': True,
                                  'sameAsPrevious': previous is not None and name in previous.namelist() and data == previous.read(name)})
            assert libraries, 'No 64-bit native libraries were inspected'
    finally:
        if previous:
            previous.close()
    report = {'checkedAtUtc': datetime.now(timezone.utc).isoformat(), 'apkManifest': apk_identity,
              'bundleManifest': actual, 'apkSha256': sha(Path(args.apk).read_bytes()),
              'bundleSha256': sha(Path(args.bundle).read_bytes()), 'nativeLibraries': libraries,
              'runtime16KbVerified': False, 'signatureVerification': 'Run apksigner/jarsigner separately'}
    output = Path(args.out)
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text(json.dumps(report, indent=2) + '\n', encoding='utf-8')
    print(json.dumps(report, indent=2))


if __name__ == '__main__':
    main()
