"""Verify packaged visual assets and optionally the built APK, without altering images."""
from pathlib import Path
import argparse
import hashlib
import json
import zipfile
from PIL import Image

ROOT=Path(__file__).resolve().parents[1]


def main():
    parser=argparse.ArgumentParser()
    parser.add_argument('--apk',type=Path)
    args=parser.parse_args()
    manifest=json.loads((ROOT/'assets/visuals-v024/manifest.json').read_text(encoding='utf-8'))
    assert len(manifest['assets'])==10
    seen=set()
    for item in manifest['assets']:
        android=ROOT/'app/src/main/res/drawable-nodpi'/item['file']
        web=ROOT/'web-demo/assets/art-v024'/item['file']
        data=android.read_bytes()
        assert data==web.read_bytes(),item['name']
        assert hashlib.sha256(data).hexdigest()==item['sha256'],item['name']
        with Image.open(android) as image:
            assert image.size==(item['width'],item['height'])
            assert image.convert('RGBA').getchannel('A').getextrema()==(0,255)
        seen.add(item['sha256'])
    assert len(seen)==10,'Unexpected duplicate art or expression'
    if args.apk:
        with zipfile.ZipFile(args.apk) as apk:
            names=apk.namelist()
            for item in manifest['assets']:
                assert any(n.endswith('/'+item['file']) for n in names),item['file']
    print(json.dumps({'assets':10,'alpha':True,'webMatchesAndroid':True,
                      'apkContainsAll':bool(args.apk),'runtimeBytes':manifest['runtimeBytes']}))


if __name__=='__main__':
    main()
