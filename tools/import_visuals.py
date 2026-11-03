"""Package generated artwork: retain alpha, downscale, and export lossless WebP resources.

Usage: python tools/import_visuals.py --source-dir <generation-output-directory>
No drawing, recoloring, background removal, or generative edits are performed here.
"""
from pathlib import Path
import argparse
import hashlib
import json
from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parents[1]
SPEC = ROOT / 'assets/visuals-v024/asset-spec.json'


def digest(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--source-dir', type=Path, required=True)
    args = parser.parse_args()
    spec = json.loads(SPEC.read_text(encoding='utf-8'))
    android = ROOT / 'app/src/main/res/drawable-nodpi'
    web = ROOT / 'web-demo/assets/art-v024'
    android.mkdir(exist_ok=True)
    web.mkdir(parents=True, exist_ok=True)
    records = []
    for asset in spec['assets']:
        source = args.source_dir / asset['source']
        image = Image.open(source).convert('RGBA')
        assert image.getchannel('A').getextrema() == (0, 255), asset['name']
        image.thumbnail((asset['maxEdge'], asset['maxEdge']), Image.Resampling.LANCZOS)
        target = android / (asset['resource'] + '.webp')
        image.save(target, 'WEBP', lossless=True, method=6, exact=True)
        (web / target.name).write_bytes(target.read_bytes())
        records.append({'name':asset['name'], 'file':target.name, 'width':image.width,
                        'height':image.height, 'bytes':target.stat().st_size,
                        'sourceSha256':digest(source), 'sha256':digest(target)})
    # Launcher packaging uses the same generated foreground, scaled inside a safe area.
    mark = Image.open(android/'art_v024_launcher.webp').convert('RGBA')
    def launcher(size, rounded=False):
        canvas = Image.new('RGBA', (size,size), (61,90,74,255))
        foreground = mark.copy()
        foreground.thumbnail((round(size*0.60), round(size*0.60)), Image.Resampling.LANCZOS)
        canvas.alpha_composite(foreground, ((size-foreground.width)//2,(size-foreground.height)//2))
        if rounded:
            mask = Image.new('L',(size,size),0)
            ImageDraw.Draw(mask).ellipse((0,0,size-1,size-1),fill=255)
            canvas.putalpha(mask)
        return canvas
    for density,px in {'mdpi':48,'hdpi':72,'xhdpi':96,'xxhdpi':144,'xxxhdpi':192}.items():
        folder=ROOT/f'app/src/main/res/mipmap-{density}'
        launcher(px).save(folder/'ic_launcher_v024.webp','WEBP',lossless=True)
        launcher(px,True).save(folder/'ic_launcher_round_v024.webp','WEBP',lossless=True)
    launcher(512).save(ROOT/'assets/visuals-v024/app-icon-512.png')
    launcher(256).save(web/'app-icon.webp','WEBP',lossless=True)
    (ROOT/'assets/visuals-v024/manifest.json').write_text(json.dumps({
        'version':'0.24','generator':'built-in image_gen','assets':records,
        'runtimeBytes':sum(r['bytes'] for r in records),
        'processing':'alpha-preserving downscale and lossless encoding; no content edits'
    },ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
    print(json.dumps({'assets':len(records),'runtimeBytes':sum(r['bytes'] for r in records)},ensure_ascii=False))


if __name__ == '__main__':
    main()
