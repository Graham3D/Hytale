"""Offline package/reference guard against deployed R247 and the reviewed R248 asset delta."""
import argparse
import hashlib
import json
import runpy
import sys
import zipfile
from collections import Counter
from pathlib import Path

HERE = Path(__file__).resolve().parent.parent
BASELINE = HERE / 'tools/u7p5-r247-package-baseline.json'
CATEGORIES = {
    'Quality': 'Item/Qualities', 'PlayerAnimationsId': 'Item/Animations',
    'ItemPlayerAnimationsId': 'Item/Animations', 'ItemSoundSetId': 'Audio/ItemSounds',
    'SystemId': 'Particles', 'ParticleSystemId': 'Particles',
    'EffectId': 'Entity/Effects', 'EntityEffectId': 'Entity/Effects',
    'TrailId': 'Entity/Trails', 'EntityStatId': 'Entity/Stats',
    'Cast': 'Item/RootInteractions', 'BlockSets': 'Item/Block/Sets',
}


def asset_root(entries):
    lines = '\n'.join(f'{name} {entries[name]}' for name in sorted(entries)) + '\n'
    return hashlib.sha256(lines.encode()).hexdigest()


def validate(package, installed, report):
    baseline = json.loads(BASELINE.read_text())
    failures = []
    checked_references = 0
    if baseline.get('baselineJarSha256') != '819cc21b80e49ef10ff079361c6b24f413aba3a6dbb8dfc0d5eedfc4e8667599':
        failures.append('Deployed R247 baseline identity changed')
    try:
        runpy.run_path(str(HERE / 'tools/Build-ProductionEliteRemaining.py'),
                       run_name='r229_asset_builder')['main'](check=True)
    except Exception as error:
        failures.append(f'R229 pinned native archetype proof failed: {error}')
    with zipfile.ZipFile(package) as candidate, zipfile.ZipFile(installed) as native:
        manifest = json.loads(candidate.read('manifest.json'))
        if manifest['ServerVersion'] != '=0.7.0-pre.5.1':
            failures.append('Manifest does not target the installed U7P5.1 schema')
        if (manifest['Group'], manifest['Name'], manifest['Main']) != (
                'InigmasGames', 'HyARPG', 'com.inigmasgames.hywind.HyArpgPlugin'):
            failures.append('Plugin identity changed')
        own_names = set(candidate.namelist())
        all_names = own_names | set(native.namelist())
        sources = {}
        for archive in (native, candidate):
            for name in archive.namelist():
                if name.startswith('Server/') and name.endswith(('.json', '.particlesystem', '.particlespawner', '.trail')):
                    sources.setdefault(name.rsplit('/', 1)[0], {})[Path(name).stem] = name
        def has(category, ident):
            prefix = 'Server/' + category
            return any(ident in entries for folder, entries in sources.items()
                       if folder == prefix or folder.startswith(prefix + '/'))
        def require(category, ident, path, field):
            nonlocal checked_references
            if not isinstance(ident, str) or '[' in ident or ident.startswith('*'):
                return
            checked_references += 1
            if not has(category, ident):
                failures.append(f'{path}: {field} references missing {category}/{ident}')
        def common(ident, path):
            nonlocal checked_references
            checked_references += 1
            variants = ['Common/' + ident]
            if ident.endswith('.png'):
                variants.append('Common/' + ident[:-4] + '@2x.png')
            if not any(name in all_names for name in variants):
                failures.append(f'{path}: missing common asset {ident}')
        def walk(value, path, field=''):
            if isinstance(value, list):
                for child in value:
                    if field == 'BlockSets':
                        require('Item/Block/Sets', child, path, field)
                    else:
                        walk(child, path, field)
            elif isinstance(value, dict):
                for key, child in value.items():
                    if key in CATEGORIES and isinstance(child, str):
                        require(CATEGORIES[key], child, path, key)
                    elif key.endswith('SoundEventId') and isinstance(child, str):
                        require('Audio/SoundEvents', child, path, key)
                    elif key == 'Parent' and isinstance(child, str):
                        category = path[len('Server/'):].split('/')
                        if category[:2] == ['Item', 'Interactions']:
                            require('Item/Interactions', child, path, key)
                        elif category[:2] == ['Item', 'Items']:
                            require('Item/Items' if field == '' else 'Item/Interactions', child, path, key)
                        elif category[0] == 'ProjectileConfigs':
                            require('ProjectileConfigs' if field == '' else 'Item/Interactions', child, path, key)
                    elif key == 'Config' and value.get('Type') in (
                            'RPG_GearProjectile', 'RPG_CarrierProjectile', 'LaunchProjectile'):
                        require('ProjectileConfigs', child, path, key)
                    walk(child, path, key)
            elif isinstance(value, str):
                if value.endswith(('.png', '.blockymodel', '.blockyanim')):
                    common(value, path)
                elif field in ('Interactions', 'Next', 'Failed', 'HitEntity', 'HitBlock',
                               'ProjectileHit', 'ProjectileMiss', 'ProjectileSpawn'):
                    if path.startswith(('Server/Item/Interactions/', 'Server/Item/RootInteractions/',
                                        'Server/ProjectileConfigs/')) and not '[' in value:
                        if not has('Item/Interactions', value) and not has('Item/RootInteractions', value):
                            failures.append(f'{path}: unresolved action {field}/{value}')
        actual = {name for name in own_names if name.endswith('.json') and name != 'manifest.json'}
        allowed_added = set(baseline['allowedAdded'])
        allowed_changed = set(baseline['allowedChanged'])
        if len(actual) != baseline['jsonAssetCount'] + len(allowed_added):
            failures.append('R248 JSON asset count differs from pinned R247 plus reviewed additions')
        if not allowed_added.issubset(actual) or not allowed_changed.issubset(actual):
            failures.append('Reviewed R248 JSON asset is missing')
        unchanged = {name: hashlib.sha256(candidate.read(name)).hexdigest()
                     for name in actual - allowed_added - allowed_changed}
        if asset_root(unchanged) != baseline['unchangedJsonSha256']:
            failures.append('Unreviewed JSON asset set or contents differ from deployed R247')
        counts = Counter()
        for name in sorted(actual):
            document = json.loads(candidate.read(name))
            if name in allowed_added or name in allowed_changed:
                source = HERE / 'src/main/resources' / name
                if not source.is_file() or source.read_bytes() != candidate.read(name):
                    failures.append(f'{name}: packaged reviewed asset differs from checked source')
            if name.startswith('Server/'):
                counts['/'.join(name.split('/')[1:3])] += 1
                walk(document, name)
        language = {}
        for archive in (native, candidate):
            for name in archive.namelist():
                if name.startswith('Server/Languages/en-US/') and name.endswith('.lang'):
                    prefix = Path(name).stem + '.'
                    for line in archive.read(name).decode('utf-8-sig').splitlines():
                        if '=' in line and not line.lstrip().startswith('#'):
                            key, value = line.split('=', 1)
                            language[prefix + key.strip()] = value.strip()
        for name in sorted(actual):
            if not name.startswith('Server/Item/'): continue
            document = json.loads(candidate.read(name))
            keys = list(document.get('TranslationProperties', {}).values())
            if 'LocalizationKey' in document: keys.append(document['LocalizationKey'])
            for key in keys:
                if key not in language: failures.append(f'{name}: missing translation {key}')
    result = {'result': 'PASS' if not failures else 'FAIL', 'patch': '0.7.0-pre.5.1',
              'baselineRevision': baseline['baselineRevision'], 'baselineJarSha256': baseline['baselineJarSha256'],
              'jsonAssetsChecked': len(actual), 'serverAssetCounts': dict(counts),
              'referencesChecked': checked_references, 'failures': failures,
              'nativeServerStarted': False, 'saveMigration': 'R200 spatial catalog revision 3 preserved; R230 has no inventory migration',
              'connectedAcceptance': 'USER_TEST_PENDING'}
    if report:
        report.parent.mkdir(parents=True, exist_ok=True)
        report.write_text(json.dumps(result, indent=2) + '\n')
    print(json.dumps(result, indent=2))
    return not failures


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--package', type=Path, default=HERE / 'build/libs/HyARPG.jar')
    parser.add_argument('--assets-zip', type=Path, default=Path.home() / 'AppData/Roaming/Hytale/install/pre-release/package/game/latest/Assets.zip')
    parser.add_argument('--report', type=Path)
    args = parser.parse_args()
    sys.exit(0 if validate(args.package, args.assets_zip, args.report) else 1)
