from pathlib import Path
import json,hashlib,subprocess,datetime,xml.etree.ElementTree as ET,zipfile
root=Path.cwd()
def git(*args): return subprocess.check_output(['git',*args],text=True,encoding='utf-8').strip()
def sha(p):
 h=hashlib.sha256()
 with Path(p).open('rb') as f:
  for b in iter(lambda:f.read(1024*1024),b''):h.update(b)
 return h.hexdigest()
def read(p):return json.loads(Path(p).read_text(encoding='utf-8-sig'))
head=git('rev-parse','HEAD'); parent='a030722af4f9e75db012f18eadfb332f50cbf191'
assert not git('status','--porcelain=v1')
receipt=read('build/guardian/verification.json');assert receipt['sourceCommit']==head
assert receipt['gradleCheck']==receipt['packageValidation']=='PASS'
assert sha(receipt['jarPath'])==receipt['jarSha256'].lower()
assert sha(receipt['checkLogPath'])==receipt['checkLogSha256'].lower()
with zipfile.ZipFile(receipt['jarPath']) as z:assert ('rpg.sourceCommit='+head) in z.read('rpg-build.properties').decode()
counts={}
for folder in ('build/test-results/test','build/test-results/nativeControlTest','canvas-ui/build/test-results/test','hytale-taverns/build/test-results/test'):
 suites=[ET.parse(p).getroot() for p in Path(folder).glob('TEST-*.xml')]
 assert suites
 assert all(int(s.attrib.get(k,0))==0 for s in suites for k in ('failures','errors','skipped'))
 counts[folder]=sum(int(s.attrib['tests']) for s in suites)
assert sum(counts.values())>=4357
patch=read('build/native-patch/verification.json'); assert patch['sourceCommit']==head and not patch['patcherSourceTreeDirty']
assert not ET.parse('build/reports/spotbugs/main.xml').getroot().findall('BugInstance')
assert 'Ran 16 tests' in Path('build/guardian/me12-stage3-python.log').read_text() and '\nOK' in Path('build/guardian/me12-stage3-python.log').read_text()
assert '12 vectors / 96 draws verified' in Path('build/guardian/me12-stage3-rng.log').read_text()
starting=read('C:/Users/Zemio/.codex/worktrees/me12-stage2/Hytale-main-clean/build/reports/me12/stage2-final-manifest.json')
snapshot=read('docs/enemies/ME12_STAGE2_INPUT_SNAPSHOT.json')
for p,digest in snapshot['inputsSha256'].items():assert hashlib.sha256(Path(p).read_text(encoding='utf-8').replace('\r\n','\n').encode()).hexdigest()==digest,p
preserved=starting['preserved']
for key,v in preserved.items():assert sha(v['path'])==v['sha256'],key
assert sha('docs/enemies/MONSTER_AFFIX_RELEASE_GATE.json')==preserved['monsterAffixGate']['sha256']
assert git('diff',parent,'HEAD','--','AGENTS.md')==''
reports=Path('build/reports/me12')
artifacts={p.as_posix():sha(p) for p in reports.rglob('*') if p.is_file() and p.name!='stage3-final-manifest.json'}
for p in ('build/guardian/verification.json','build/guardian/me12-stage3-final.log','build/guardian/me12-stage3-python.log','build/guardian/me12-stage3-rng.log','build/guardian/me12-stage3-publication.log'):
 artifacts[p]=sha(p)
for p in ('build/native-patch/verification.json','build/reports/spotbugs/main.xml'):
 artifacts[p]=sha(p)
branch=git('branch','--show-current');remote=git('rev-parse','origin/'+branch);assert remote==head
main=git('rev-parse','origin/main')
measurements={p.stem:read(p) for p in reports.glob('*.json') if p.name in ('stage3-lifecycle.json','stage2-cache.json','stage2-heap.json','stage2-static-feasibility.json','stage2-distributions.json')}
manifest=dict(schemaVersion=1,stage=3,recordedAtUtc=datetime.datetime.now(datetime.timezone.utc).isoformat(),state='OFFLINE_VERIFIED_PUBLISHED_WITH_NORMATIVE_BLOCKERS',
 source=dict(branch=branch,head=head,parent=parent,implementationCheckpoint='1bfbd3484daebb783bd67386259f7cb33683b187',upstream='origin/'+branch,upstreamHead=remote,aheadBehind=git('rev-list','--left-right','--count','HEAD...origin/'+branch),workingTree='CLEAN',mainHead=main,mainBehindCandidate=int(git('rev-list','--count',main+'..HEAD')),newCommits=git('log','--format=%H %s',parent+'..HEAD').splitlines(),changedFiles=git('diff','--name-only',parent,'HEAD').splitlines()),
 dependencies=dict(stage2=starting['source'],stage1=starting['dependencies'],pinnedInputs=snapshot['inputsSha256'],unmergedRemoteHeads=git('for-each-ref','--format=%(refname:short) %(objectname)','refs/remotes/origin','--no-merged','origin/main').splitlines()),
 build=dict(revision=receipt['revision'],version=receipt['version'],sourceCommit=head,jarPath=receipt['jarPath'],jarSha256=receipt['jarSha256'].lower(),targetHytale='0.7.1',deployed=False),
 verification=dict(junitCounts=counts,junitTotal=sum(counts.values()),failures=0,errors=0,skips=0,pythonTests=16,rngVectors=12,rngDraws=96,guardian='PUBLISHED',check='PASS',nativePatch=patch,spotbugs='PASS',artifactsSha256=artifacts,measurements=measurements),
 gates=read('docs/enemies/ME12_STAGE3_GATES.json')['gates'],preserved=preserved,primaryStatus=git('-C','C:/Users/Zemio/OneDrive/Documents/GitHub/Hytale-main-clean','status','--short','--branch'),
 sourceReadiness=dict(skillAcceptedBindings=0,passiveAcceptedBindings=0,typedAdapter='OFFLINE_VERIFIED',realSourceGate='BLOCKED'),
 productionV2Births='DISABLED',stage1='BLOCKED_OFFLINE_COVERAGE',monsterAffixRelease='OPEN',ownerAccepted=False,nativeConnectedPassed=False,connectedQA='DEFERRED_TO_STAGE4',stage4Started=False,
 nextAction='Resolve native capture certificates and real source assignments; separately authorize cumulative Stage 4. Do not deploy or enable births from this manifest.',
 limitations=['Native capture seam has no certified provider; static fixtures are explicitly rejected','Offline Java2D images are not Hytale client render evidence; second immunity is a synthetic presentation stress input','Only Mana Burn/Vampiric have dedicated resource readbacks in the ME12 view; other affixes retain raw HRO evidence without a semantic PASS','Historical generator restoration requires exact evaluator/definitions; default resolver only accepts matching installed revision','Guardian publication is not gameplay acceptance or main integration'])
(reports/'stage3-final-manifest.json').write_text(json.dumps(manifest,indent=2)+'\n',encoding='utf-8')
print(json.dumps({'head':head,'tests':manifest['verification']['junitTotal'],'counts':counts,'jar':manifest['build']['jarSha256'],'artifacts':len(artifacts),'manifest':str(reports/'stage3-final-manifest.json')}))
