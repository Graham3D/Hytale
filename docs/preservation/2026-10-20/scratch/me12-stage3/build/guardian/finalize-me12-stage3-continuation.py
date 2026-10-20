import datetime, hashlib, json, subprocess, xml.etree.ElementTree as ET, zipfile
from pathlib import Path

def git(*args):
    return subprocess.check_output(['git', *args], text=True).strip()
def read(path):
    return json.loads(Path(path).read_text(encoding='utf-8-sig'))
def sha(path):
    with open(path, 'rb') as stream:
        return hashlib.file_digest(stream, 'sha256').hexdigest()

parent='63fcb510e1a65ba6491b5564fdb20e901cb2360a'
head=git('rev-parse','HEAD')
assert not git('status','--porcelain=v1','--untracked-files=all')
receipt=read('build/guardian/verification.json')
assert receipt['sourceCommit']==head, receipt.keys()
assert receipt['packageValidation']=='PASS'
assert sha(receipt['jarPath'])==receipt['jarSha256'].lower()
assert sha(receipt['checkLogPath'])==receipt['checkLogSha256'].lower()
with zipfile.ZipFile(receipt['jarPath']) as jar:
    assert 'rpg.sourceCommit='+head in jar.read('rpg-build.properties').decode()
counts={}
for folder in ('build/test-results/test','build/test-results/nativeControlTest','canvas-ui/build/test-results/test','hytale-taverns/build/test-results/test'):
    suites=[ET.parse(p).getroot() for p in Path(folder).glob('TEST-*.xml')]
    assert suites
    assert all(int(s.attrib.get(k,0))==0 for s in suites for k in ('failures','errors','skipped'))
    counts[folder]=sum(int(s.attrib['tests']) for s in suites)
assert sum(counts.values())>=4375
patch=read('build/native-patch/verification.json')
assert patch['sourceCommit']==head and not patch['patcherSourceTreeDirty']
assert not ET.parse('build/reports/spotbugs/main.xml').getroot().findall('BugInstance')
prefix='build/guardian/me12-stage3-continuation-'
assert 'Ran 16 tests' in Path(prefix+'python.log').read_text() and '\nOK' in Path(prefix+'python.log').read_text()
assert '12 vectors / 96 draws verified' in Path(prefix+'rng.log').read_text()
original=read('build/reports/me12/stage3-final-manifest.json')
assert original['source']['head']==parent
snapshot=read('docs/enemies/ME12_STAGE2_INPUT_SNAPSHOT.json')
for path,digest in snapshot['inputsSha256'].items():
    assert hashlib.sha256(Path(path).read_text(encoding='utf-8').replace('\r\n','\n').encode()).hexdigest()==digest,path
preserved=original['preserved']
for key,value in preserved.items():
    assert sha(value['path'])==value['sha256'],key
assert git('diff',parent,'HEAD','--','AGENTS.md')==''
branch=git('branch','--show-current')
remote=git('rev-parse','origin/'+branch)
assert remote==head
main=git('rev-parse','origin/main')
reports=Path('build/reports/me12')
artifact_paths=[p for p in reports.rglob('*') if p.is_file() and p.name!='stage3-continuation-manifest.json']
artifact_paths += [Path(prefix+name+'.log') for name in ('final','python','rng','publication')]
artifact_paths += [Path('build/guardian/verification.json'),Path('build/native-patch/verification.json'),Path('build/reports/spotbugs/main.xml')]
artifact_paths += [p for p in Path('build/guardian/stage3-63fcb510-evidence').rglob('*') if p.is_file()]
manifest=dict(schemaVersion=1,stage=3,recordedAtUtc=datetime.datetime.now(datetime.timezone.utc).isoformat(),
    state='IN_PROGRESS_INTEGRATION_BLOCKED_OFFLINE_CHECKPOINT_PUBLISHED',
    source=dict(branch=branch,head=head,parent=parent,upstream='origin/'+branch,upstreamHead=remote,
        aheadBehind=git('rev-list','--left-right','--count','HEAD...origin/'+branch),workingTree='CLEAN',
        mainHead=main,mainBehindCandidate=int(git('rev-list','--count',main+'..HEAD')),
        newCommits=git('log','--format=%H %s',parent+'..HEAD').splitlines(),changedFiles=git('diff','--name-only',parent,'HEAD').splitlines()),
    dependencies=original['dependencies'],
    build=dict(revision=receipt['revision'],version=receipt['version'],sourceCommit=head,jarPath=receipt['jarPath'],jarSha256=receipt['jarSha256'].lower(),targetHytale='0.7.1',deployed=False),
    verification=dict(junitCounts=counts,junitTotal=sum(counts.values()),failures=0,errors=0,skips=0,pythonTests=16,rngVectors=12,rngDraws=96,
        guardian='PUBLISHED',check='PASS',nativePatch=patch,spotbugs='PASS',artifactsSha256={p.as_posix():sha(p) for p in artifact_paths}),
    gates=read('docs/enemies/ME12_STAGE3_GATES.json')['gates'],preserved=preserved,
    primaryStatus=git('-C','C:/Users/Zemio/OneDrive/Documents/GitHub/Hytale-main-clean','status','--short','--branch'),
    sourceReadiness=dict(skillAcceptedBindings=0,passiveAcceptedBindings=0,editableConfiguration='IMPLEMENTED_SYNTHETIC_TESTS_ONLY',realSourceGate='BLOCKED'),
    productionV2Births='DISABLED',stage4Ready=False,stage4Started=False,monsterAffixRelease='OPEN',ownerAccepted=False,nativeConnectedPassed=False,
    axes=dict(implemented=False,automatedPassed=False,automatedRegressionPassed=True,nativeConnectedPassed=False,ownerAccepted=False),
    offlineBlockers=['Modern Turret/Seeker projectile creation/impact/removal and persistence ownership','Mixed-flock concrete future-role certification',
        'Complete production-provider native capture/classifier/archive/publication/rollback integration fixture','Full Firesteel production attachment/recovery fixture',
        'Complete semantic execution predicates for all 27 operators','Separate guarded candidate-only QA activation authority'],
    connectedBlockers=['Native natural spawn and combat execution','Native publication/unload/reload and recovery','Client rendering/performance/cleanup','Owner-selected Skill and first-copy Passive sources'],
    acquisitionDeferral='PROPOSED_RELEASE_SCOPE_AMENDMENT_NOT_AD1_COMPLETION',
    nextAction='Continue the shared modern projectile receipt lifecycle and full native-owner fixtures. No deployment, Stage 4, or owner QA request.',
    report='docs/enemies/ME12_STAGE3_CONTINUATION.md')
destination=reports/'stage3-continuation-manifest.json'
destination.write_text(json.dumps(manifest,indent=2)+'\n',encoding='utf-8')
print(json.dumps({'head':head,'tests':sum(counts.values()),'counts':counts,'jar':manifest['build']['jarSha256'],'stage4Ready':False,'manifest':str(destination)}))
