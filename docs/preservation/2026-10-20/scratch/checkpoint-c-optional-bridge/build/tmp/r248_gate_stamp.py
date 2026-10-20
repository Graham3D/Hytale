import json
from pathlib import Path

path = Path('docs/enemies/MONSTER_AFFIX_RELEASE_GATE.json')
gate = json.loads(path.read_text(encoding='utf-8'))
source = '97621fb0368f0ab755b095f436614a049dbd8fd0'
build = 'B68C61C4CA1A40B75D6238B604B1E71E69CE5028FCF062E5FD4D49A490251803'
gate['candidateSourceCommit'] = source
gate['candidateJarSha256'] = build
for row in gate['requirements']:
    row['sourceCommit'] = source
    row['buildSha256'] = build
    for evidence in ('docs/enemies/R248_VERIFICATION.json', 'docs/enemies/R248_DEPLOYMENT.md'):
        if evidence not in row['evidence']:
            row['evidence'].append(evidence)
path.write_text(json.dumps(gate, indent=2) + '\n', encoding='utf-8')
