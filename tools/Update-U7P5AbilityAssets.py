"""Migrate owned bridge carriers to U7P5 CoreItemAbility without changing RPG skills."""
import json
from pathlib import Path

root = Path(__file__).resolve().parents[1] / 'src/main/resources/Server/Item/Items/RPG/Abilities'
changed = 0
for path in sorted(root.glob('*.json')):
    text = path.read_text(encoding='utf-8')
    item = json.loads(text)
    ability = item['Ability']
    if ability['Slot'] not in ('Primary', 'Core'):
        raise ValueError(f'Unreviewed ability discriminator: {path}')
    if ability.get('Cost') != 0 or ability.get('Cooldown') != 0 or ability.get('CostType') != 'None':
        raise ValueError(f'Carrier must remain payment-free: {path}')
    if 'Tags' in ability and ability['Tags'] != ['rpg.native.bridge']:
        raise ValueError(f'Unreviewed legacy ability tags: {path}')
    # U7P5 replaces the old monolithic ItemAbility with Core/Support subtypes.
    # The former bridge tag is diagnostic only, not a native support keyword.
    ability['Slot'] = 'Core'
    ability.pop('Tags', None)
    rendered = json.dumps(item, indent=2) + '\n'
    if json.loads(text) != json.loads(rendered):
        path.write_text(rendered, encoding='utf-8')
        changed += 1
print(f'U7P5: validated {len(list(root.glob("*.json")))} bridge carriers; migrated {changed}.')
