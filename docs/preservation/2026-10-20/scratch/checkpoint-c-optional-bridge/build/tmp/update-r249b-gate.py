import json
from pathlib import Path
path=Path('docs/enemies/MONSTER_AFFIX_RELEASE_GATE.json')
gate=json.loads(path.read_text(encoding='utf-8'))
source='6c0ab9fd6a8ddaf647ba075d831bab1128f495c1'
jar='A0EA6EEB80577AA30AA787475E7B7750417F5DE7530A57EC7DF72DE7D8E13101'
gate['candidateSourceCommit']=source
gate['candidateJarSha256']=jar
gate['status']='OPEN'
gate['ownerAccepted']=False
for row in gate['requirements']:
 row['sourceCommit']=source
 row['buildSha256']=jar
 row['ownerAccepted']=False
 if row['id']=='PRESENTATION':
  row['status']='BLOCKED'
  row['automatedPassed']=False
  row['nativeConnectedPassed']=False
  row['missingOrFailure']='R249B offline tests prove full overhead affix spelling and a 0.20 visible name-to-affix gap while the name height stays fixed. Native client spacing, crowded packs, movement, rebind, rich target card, Healthbar and Polar Bear washout still need connected evidence.'
  for test in ['com.inigmasgames.hytalerpg.execution.hytale.MonsterAffixLabelTest','com.inigmasgames.hytalerpg.execution.hytale.MonsterPresentationLayoutTest']:
   if test not in row['tests']: row['tests'].append(test)
  for evidence in ['docs/enemies/R249B_PRESENTATION_CORRECTION.md','docs/enemies/R249B_VERIFICATION.json','docs/enemies/R249B_DEPLOYMENT.md']:
   if evidence not in row['evidence']: row['evidence'].append(evidence)
path.write_text(json.dumps(gate,indent=2,ensure_ascii=False)+'\n',encoding='utf-8')
