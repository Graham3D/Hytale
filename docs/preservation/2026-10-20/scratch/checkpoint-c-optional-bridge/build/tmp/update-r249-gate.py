import json
from pathlib import Path
path = Path('docs/enemies/MONSTER_AFFIX_RELEASE_GATE.json')
gate = json.loads(path.read_text(encoding='utf-8'))
source = '2f2f74098f70c38ed3a77da5b010e83ece58817c'
jar = '8666C66C3127C4E0593A2263CC5729C850E43A5F135071A21E144FCBEFD46E7E'
gate['candidateSourceCommit'] = source
gate['candidateJarSha256'] = jar
gate['status'] = 'OPEN'
gate['ownerAccepted'] = False
notes = {
 'SKELETON_BINDINGS': 'The exact Frost Ranger bow root and Frost Archer Wander movement alias now resolve pinned native bindings; the prior seven certifications remain. Natural combat, promotion and LOAD require connected proof.',
 'PRESENTATION': 'Offline color atlas, single-name owner, fallback and hitbox layout checks pass. Native client glyph visibility, clustered names, affix alignment, target-card completeness, Healthbar and unload/reload require connected screenshots; Polar Bear washout cause remains unproven.',
 'CHAMPION_IDENTITY': 'Offline Champion prefix and blue glyph semantics pass. Connected localized rendering, long names, clustered packs and LOAD still need proof.'
}
extra_tests = {
 'SKELETON_BINDINGS': ['com.inigmasgames.hytalerpg.enemies.ProductionEliteArchetypeAssetTest', 'com.inigmasgames.hytalerpg.enemies.ProductionEliteInstalledRouteTest'],
 'PRESENTATION': ['com.inigmasgames.hytalerpg.execution.hytale.MonsterPresentationLayoutTest', 'com.inigmasgames.hytalerpg.execution.hytale.PromotedNameGlyphsTest', 'com.inigmasgames.hytalerpg.enemies.EnemyNativePaletteTest'],
 'CHAMPION_IDENTITY': ['com.inigmasgames.hytalerpg.execution.hytale.PromotedNameGlyphsTest']
}
for row in gate['requirements']:
 row['sourceCommit'] = source
 row['buildSha256'] = jar
 row['ownerAccepted'] = False
 if row['id'] in notes:
  row['missingOrFailure'] = notes[row['id']]
  for test in extra_tests[row['id']]:
   if test not in row['tests']: row['tests'].append(test)
  for evidence in ['docs/enemies/R249_RELEASE_CANDIDATE.md', 'docs/enemies/R249_VERIFICATION.json', 'docs/enemies/R249_DEPLOYMENT.md']:
   if evidence not in row['evidence']: row['evidence'].append(evidence)
 if row['id'] == 'PRESENTATION': row['automatedPassed'] = False
path.write_text(json.dumps(gate, indent=2, ensure_ascii=False) + '\n', encoding='utf-8')
