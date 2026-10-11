"""Offline package/reference guard for R250, retaining the historical R248 baseline."""
import argparse
import hashlib
import json
import runpy
import sys
import zipfile
from collections import Counter
from pathlib import Path

HERE = Path(__file__).resolve().parent.parent
BASELINE = HERE / 'tools/u7p5-r248-package-baseline.json'
R250_REVIEW = HERE / 'tools/u7p5-r250-reviewed-json-baseline.json'
CYCLONE_REVIEW = HERE / 'tools/u7p5-r256-cyclone-reviewed-assets.json'
R248_BASELINE_NORMALIZED_SHA256 = '69464e8c153519ca2f97cf76f9a6e17055401a3f2f1a18084c1672ca57e8c168'
GENERATED_STOCK_JSON = 'Server/Prefabs/Testing/VolumeShowcase/Trigger_Volume_Showcase.prefab.json'
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


def normalized_json_sha256(data):
    # Checkout platforms may change CRLF to LF. Preserve every other byte.
    return hashlib.sha256(data.replace(b'\r\n', b'\n')).hexdigest()


def validate(package, installed, report):
    baseline_bytes = BASELINE.read_bytes()
    failures = []
    if normalized_json_sha256(baseline_bytes) != R248_BASELINE_NORMALIZED_SHA256:
        failures.append('Historical R248 baseline JSON file SHA-256 changed')
    baseline = json.loads(baseline_bytes)
    review = json.loads(R250_REVIEW.read_text())
    cyclone = json.loads(CYCLONE_REVIEW.read_text())
    checked_references = 0
    if baseline.get('baselineJarSha256') != 'b68c61c4ca1a40b75d6238b604b1e71e69ce5028fcf062e5fd4d49a490251803':
        failures.append('Deployed R248 baseline identity changed')
    if (review.get('revision') != 'R250-U7P5B' or
            review.get('referenceJarSha256') != 'a0ea6eeb80577aa30aa787475e7b7750417f5de7530a57ec7df72de7d8e13101' or
            review.get('reviewedSourceCommit') != '44184d936d750e0a6729a8f797e7f6a4fe30356f'):
        failures.append('Reviewed R250 asset reference identity changed')
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
        json_entries = [name for name in candidate.namelist()
                        if name.endswith('.json') and name != 'manifest.json']
        actual = set(json_entries)
        if len(json_entries) != len(actual):
            failures.append('Duplicate JSON asset entry in package')
        allowed_added = set(baseline['allowedAdded'])
        allowed_changed = set(baseline['allowedChanged'])
        r249_added = review['r249AddedNormalizedSha256']
        r249_changed = review['r249ChangedNormalizedSha256']
        r250_changed = review['r250ChangedNormalizedSha256']
        teleport_paths = {'rpg/catalog/skills.json', 'rpg/runtime/lightning-v2.json'}
        if (set(r249_added) != allowed_added or set(r249_changed) != allowed_changed or
                set(r250_changed) != teleport_paths or
                len(allowed_added) != 94 or len(allowed_changed) != 1 or
                review['jsonAssetCount'] != baseline['jsonAssetCount'] + len(allowed_added) or
                review['guardedJsonCount'] + len(allowed_added) + len(allowed_changed) + len(teleport_paths)
                != review['jsonAssetCount']):
            failures.append('Reviewed R250 JSON partition differs from R248/R249 history')
        cyclone_changes = cyclone['jsonChangesNormalizedSha256']
        prior_guarded = cyclone['replacedGuardedNormalizedSha256']
        particle_assets = cyclone['newParticleAssetsSha256']
        if (cyclone['revision'] != 'R256-U7P5B' or set(cyclone_changes) != {
                'rpg/catalog/skills.json', 'rpg/runtime/stage-09-support-cohort-c.json',
                'rpg/presentation/icon-index.json', 'Server/Entity/Effects/RPG/RPG_Cyclone_Armor.json'} or
                set(prior_guarded) != {'rpg/runtime/stage-09-support-cohort-c.json',
                                       'rpg/presentation/icon-index.json'} or
                set(particle_assets) != {'Server/Particles/RPG/Wind_Swoosh_CycloneArmor.particlespawner',
                                        'Server/Particles/RPG/RPG_Cyclone_Armor.particlesystem'}):
            failures.append('Cyclone reviewed asset inventory changed')
        if len(actual) != review['jsonAssetCount'] + 1:
            failures.append('R256 JSON asset count differs from reviewed package')
        resource_roots = (HERE / 'src/main/resources', HERE / 'hytale-taverns/src/main/resources')
        source_paths = {}
        for root in resource_roots:
            for source in root.rglob('*.json'):
                name = source.relative_to(root).as_posix()
                if name != 'manifest.json':
                    if name in source_paths:
                        failures.append(f'{name}: duplicate JSON source path')
                    source_paths[name] = source
        if set(source_paths) != actual - {GENERATED_STOCK_JSON}:
            failures.append('Packaged JSON asset paths differ from checked source paths')
        reviewed = {**r249_added, **r249_changed, **r250_changed}
        if not set(reviewed).issubset(actual):
            failures.append('Reviewed R249/R250 JSON asset is missing')
        guarded_names = actual - set(reviewed) - set(cyclone_changes)
        if len(guarded_names) + len(prior_guarded) != review['guardedJsonCount']:
            failures.append('Guarded R250 JSON asset count differs from reviewed package')
        guarded = {name: normalized_json_sha256(candidate.read(name)) for name in guarded_names}
        guarded.update(prior_guarded)
        if asset_root(guarded) != review['guardedNormalizedJsonSha256']:
            failures.append('Guarded R250 JSON asset set or normalized contents changed')
        for name, expected in reviewed.items():
            if name in actual and name not in cyclone_changes and normalized_json_sha256(candidate.read(name)) != expected:
                failures.append(f'{name}: reviewed normalized JSON content changed')
        for name, expected in cyclone_changes.items():
            if name not in actual or normalized_json_sha256(candidate.read(name)) != expected:
                failures.append(f'{name}: reviewed Cyclone JSON content changed')
        for name, expected in particle_assets.items():
            source = HERE / 'src/main/resources' / name
            if name not in own_names or not source.is_file() or candidate.read(name) != source.read_bytes() or hashlib.sha256(candidate.read(name)).hexdigest() != expected:
                failures.append(f'{name}: reviewed Cyclone particle asset changed')
        for rarity in ('Champion', 'Unique', 'SuperUnique', 'Boss'):
            name = f'Common/Items/RPG/NameGlyphs/{rarity}.png'
            source = HERE / 'src/main/resources' / name
            if name not in own_names or not source.is_file() or candidate.read(name) != source.read_bytes():
                failures.append(f'{name}: glyph atlas missing or differs from checked source')
        counts = Counter()
        for name in sorted(actual):
            package_bytes = candidate.read(name)
            document = json.loads(package_bytes)
            source = source_paths.get(name)
            if source is not None:
                source_bytes = source.read_bytes()
                if name in reviewed or name in cyclone_changes:
                    if source_bytes != package_bytes:
                        failures.append(f'{name}: packaged reviewed asset differs from checked source')
                elif normalized_json_sha256(source_bytes) != normalized_json_sha256(package_bytes):
                    failures.append(f'{name}: packaged guarded asset differs from checked source')
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
              'reviewedRevision': review['revision'], 'jsonAssetsChecked': len(actual),
              'guardedJsonChecked': len(guarded_names), 'reviewedJsonChecked': len(reviewed),
              'jsonNormalization': 'CRLF_TO_LF_ONLY', 'serverAssetCounts': dict(counts),
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
