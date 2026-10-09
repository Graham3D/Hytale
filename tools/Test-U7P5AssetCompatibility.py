"""Offline package/reference guard against the connected R228 build and reviewed R229-R231 deltas."""
import argparse
import copy
import hashlib
import json
import runpy
import sys
import zipfile
from collections import Counter
from pathlib import Path

HERE = Path(__file__).resolve().parent.parent
BASELINE = HERE / 'tools/u7p5-r228-semantic-baseline.json'
CATEGORIES = {
    'Quality': 'Item/Qualities', 'PlayerAnimationsId': 'Item/Animations',
    'ItemPlayerAnimationsId': 'Item/Animations', 'ItemSoundSetId': 'Audio/ItemSounds',
    'SystemId': 'Particles', 'ParticleSystemId': 'Particles',
    'EffectId': 'Entity/Effects', 'EntityEffectId': 'Entity/Effects',
    'TrailId': 'Entity/Trails', 'EntityStatId': 'Entity/Stats',
    'Cast': 'Item/RootInteractions', 'BlockSets': 'Item/Block/Sets',
}


def digest(document):
    return hashlib.sha256(json.dumps(document, sort_keys=True, separators=(',', ':'),
                                    ensure_ascii=False).encode()).hexdigest()


def validate(package, installed, report):
    baseline = json.loads(BASELINE.read_text())
    failures = []
    checked_references = 0
    if baseline.get('baselineJarSha256') != '5d24abffe40018adc62179f720d708b58c58c7fd047166f1403c39a428c08387':
        failures.append('Connected R228 baseline identity changed')
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
        expected = baseline['assets']
        allowed_added = set(baseline['reviewedR229AddedAssets']) | set(baseline['reviewedR230AddedAssets'])
        allowed_changed = set(baseline['reviewedR229ChangedAssets']) | set(baseline['reviewedR231ChangedAssets'])
        added = actual - set(expected)
        removed = set(expected) - actual
        if added != allowed_added or removed:
            failures.append(f'JSON asset set changed outside reviewed R229/R230 additions: added={sorted(added)}, removed={sorted(removed)}')
        counts = Counter()
        for name in sorted(actual):
            document = json.loads(candidate.read(name))
            comparison = document
            if name == 'rpg/progression/enemy-registry.json':
                comparison = copy.deepcopy(document)
                spectres = [row for row in comparison['roles'] if row['roleId'] == 'Void_Spectre']
                if len(spectres) != 1 or spectres[0]['assetSha256'] != (
                        'E005E84AE112430819C630C7E39679C07CB96CF2B59C4C999D789BFB217844EC'):
                    failures.append('Void_Spectre catalog source hash differs from reviewed native correction')
                else:
                    spectres[0]['assetSha256'] = 'E527A7370FCE03709FEEFE164E3DBE546BED19C2B71711EA8EED2FDAB18C8944'
            if name == 'rpg/enemies/master-enemies-v1.json':
                comparison = copy.deepcopy(document)
                promotion = comparison['promotion']
                expected_weights = {
                    'NORMAL': {'NORMAL': 920, 'CHAMPION': 16, 'UNIQUE': 64},
                    'NIGHTMARE': {'NORMAL': 860, 'CHAMPION': 28, 'UNIQUE': 112},
                    'HELL': {'NORMAL': 800, 'CHAMPION': 40, 'UNIQUE': 160},
                }
                if promotion.get('weightsByDifficulty') != expected_weights:
                    failures.append('Master Enemies rarity thresholds differ from reviewed R231 density')
                promotion.pop('weightsByDifficulty', None)
                promotion['weights'] = {'NORMAL': 92, 'CHAMPION': 2, 'UNIQUE': 6}
            if name in expected and digest(comparison) != expected[name] and name not in allowed_changed:
                failures.append(f'{name}: semantics differ from the connected R228 baseline')
            if name == 'rpg/progression/enemy-registry.json' and digest(comparison) != expected[name]:
                failures.append('Enemy registry changed beyond the reviewed Void_Spectre hash correction')
            if name == 'rpg/enemies/master-enemies-v1.json' and digest(comparison) != expected[name]:
                failures.append('Master Enemies balance changed beyond the reviewed rarity density')
            if name in allowed_added or name in allowed_changed:
                source = HERE / 'src/main/resources' / name
                if not source.is_file() or json.loads(source.read_text(encoding='utf-8')) != document:
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
