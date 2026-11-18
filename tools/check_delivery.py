"""Fail CI when the current release header, submission docs or JVM counts drift."""
import argparse
import json
import pathlib
import re
import xml.etree.ElementTree as ET

root = pathlib.Path(__file__).resolve().parents[1]
parser = argparse.ArgumentParser()
parser.add_argument('--require-unit-reports', action='store_true')
args = parser.parse_args()
build = (root / 'app/build.gradle.kts').read_text(encoding='utf-8')
version = re.search(r'versionName = "([^"]+)"', build).group(1)
code = int(re.search(r'versionCode = (\d+)', build).group(1))
manifest = json.loads((root / 'evidence' / ('v' + version.replace('.', '')) / 'validation.json').read_text(encoding='utf-8'))
assert manifest['version'] == version and manifest['versionCode'] == code, 'Release manifest version drift'
for number in range(3, 9):
    path = next((root / 'docs').glob(f'{number:02d}-*.md'))
    header = next(line for line in path.read_text(encoding='utf-8').splitlines() if line.startswith('> 当前交付'))
    for expected in (
        f'v{version} / versionCode {code}',
        f"代码基线 `{manifest['codeCommit'][:7]}`",
        f"JVM {manifest['jvmTests']} 例",
        f"网页 {manifest['webTests']} 例",
        f"安卓设备测试 {manifest['instrumentationTests']} 例",
        *(f"{artifact['bytes']:,} 字节" for artifact in manifest['artifacts']),
    ):
        assert expected in header, f'{path.name}: expected {expected}'
assert f'## v{version}' in (root / 'README.md').read_text(encoding='utf-8'), 'README release section missing'
if args.require_unit_reports:
    reports = list((root / 'app/build/test-results/testDebugUnitTest').glob('TEST-*.xml'))
    assert reports, 'No JVM XML reports'
    results = [ET.parse(path).getroot() for path in reports]
    assert sum(int(r.get('tests')) for r in results) == manifest['jvmTests'], 'JVM test count drift'
    assert all(int(r.get('failures')) == 0 and int(r.get('errors')) == 0 for r in results), 'JVM tests failed'
print(f'v{version}: build, release manifest and six submission docs agree')
