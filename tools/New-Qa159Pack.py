"""Deterministic authoring aid. Java Qa159Pack validates the result against runtime legality."""
import json
import random
from collections import defaultdict, deque
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
DATA = ROOT / 'src/main/resources/rpg/gear'
BASE_IDS = [
    'gm.plate_iron.head.h', 'gm.robes_oracle.chest.h',
    'gm.cloth_cinder.hands.h', 'gm.leather_raven.legs.h',
    'gm.sword_copper.h', 'gm.battleaxe_iron.h', 'gm.mace_thorium.h',
    'gm.daggers_adamantite.h', 'gm.daggers_cobalt.h',
    'gm.shortbow_cobalt.h', 'gm.crossbow_heavy.h', 'gm.longsword_adamantite.h',
    'gm.shield_mithril.h', 'gm.staff_flame.h', 'gm.staff_healing.h',
    'gm.book_binder.h', 'gm.wand_crystal.h',
]

def eligible(a, b):
    if b['sourceWindow'][-1] < max(a['firstItemLevel'], {'ALL':78, 'FAMILY':55, 'NAMED':35}.get(a['tierModel'], 1)): return False
    if b['category'] == 'ARMOR':
        if a['id'].startswith('GA-'): return True
        ext = a['armorExtension']
        if ext.startswith('none.'): return False
        if not (ext.startswith('All armor') or b['slot'].lower() in [s.strip().lower() for s in ext.split(';')[0].split(',')]): return False
        if 'INT/WIS armor' in ext: return b['primaryAttribute'] in ('INT', 'WIS')
        if 'INT armor' in ext: return b['primaryAttribute'] == 'INT'
        if 'WIS armor' in ext: return b['primaryAttribute'] == 'WIS'
        return True
    if a['id'].startswith('GA-'): return False
    action = b['id'][3:].split('_')[0]
    code = 'H' if action == 'shield' else 'C' if action in ('staff', 'wand', 'book') else 'R' if action in ('shortbow','longbow','crossbow','rifle','blunderbuss') else 'B' if action == 'bomb' else 'M'
    tokens = a['eligibility'].split(', ')
    if a['eligibility'] in ('ALL', 'MATCH'): return True  # Exact MATCH selectors are rechecked by Java.
    return code in tokens or code == 'C' and 'C*' in tokens and a['id'] in ['WA-008'] + [f'WA-{n:03}' for n in range(17,23)] or action == 'daggers' and a['id'] == 'WA-141'

def solve(affixes, bases, seed):
    rng = random.Random(seed)
    graph = defaultdict(dict)
    def edge(a,b,n):
        graph[a][b] = n
        graph[b].setdefault(a,0)
    ordered = list(affixes); rng.shuffle(ordered)
    for a in ordered:
        edge('source',a['id'],1)
        indexes = list(range(17)); rng.shuffle(indexes)
        for i in indexes:
            b = bases[i]
            if eligible(a,b): edge(a['id'],f"group/{i}/{a['exclusionGroup']}",1)
    for i,b in enumerate(bases):
        for group in {a['exclusionGroup'] for a in affixes if eligible(a,b)}:
            edge(f'group/{i}/{group}',f'base/{i}',1)
        edge(f'base/{i}','sink',10 if i < 6 else 9)
    count = 0
    while True:
        parents = {'source':None}; queue=deque(['source'])
        while queue and 'sink' not in parents:
            node=queue.popleft()
            for dest,capacity in graph[node].items():
                if capacity and dest not in parents: parents[dest]=node;queue.append(dest)
        if 'sink' not in parents: break
        node='sink'
        while parents[node] is not None:
            prev=parents[node];graph[prev][node]-=1;graph[node][prev]+=1;node=prev
        count+=1
    if count != 159: raise ValueError(f'Only {count}/159 assignments fit')
    result=[[] for _ in bases]
    for a in affixes:
        for dest,capacity in graph[a['id']].items():
            if dest.startswith('group/') and capacity == 0:
                result[int(dest.split('/')[1])].append(a['id'])
    # Lowest legal tier retains normal gates. Q floors at the first tier are 10;
    # fixed/rank gates and base gates still count toward the three-stat limit.
    by_id={a['id']:a for a in affixes}
    for b,ids in zip(bases,result):
        attrs={a for a,v in b['requiredAttributes'].items() if v>10}
        for id in ids:
            a=by_id[id]
            if a['tierModel'] != 'Q':
                attrs.add(b['primaryAttribute'] if a['requirementPolicy'].startswith('BASE-') else a['requirementPolicy'].split('-')[0])
        if len(attrs)>3: return None
    return result

def main():
    affixes=[a for a in json.loads((DATA/'affixes-v1.json').read_text(encoding='utf-8'))['affixes'] if a['id']!='WA-155']
    bases_by_id={b['id']:b for b in json.loads((DATA/'bases-v1.json').read_text(encoding='utf-8'))['bases']}
    bases=[bases_by_id[id] for id in BASE_IDS]
    bindings={b['baseId']:b for b in json.loads((DATA/'native-bindings-v1.json').read_text(encoding='utf-8'))['bindings']}
    for b in bases:
        if bindings[b['id']]['disposition']!='MAPPED': raise ValueError(f"Unmapped {b['id']}")
    for seed in range(10000):
        assignment=solve(affixes,bases,seed)
        if assignment is not None: break
    else: raise ValueError('No assignment satisfies requirement gates')
    fixtures=[dict(fixtureId=f'qa159-{i+1:02}',itemBaseId=b['id'],itemLevel=b['sourceWindow'][-1],rarity='LEGENDARY',group='qa159',affixIds=sorted(ids)) for i,(b,ids) in enumerate(zip(bases,assignment))]
    output=DATA/'qa159-v1.json'
    output.write_text(json.dumps(dict(schemaVersion=1,note='Approved 17-item pack: 13 skiller exclusions require 13 held carriers plus four armor pieces. Count budget only is bypassed.',fixtures=fixtures),indent=2)+'\n',encoding='utf-8')
    print(f'Wrote {len(fixtures)} fixtures, 159 unique affixes; assignment seed {seed}')
    for f in fixtures: print(f['fixtureId'],f['itemBaseId'],len(f['affixIds']),','.join(f['affixIds']))

if __name__ == '__main__': main()
