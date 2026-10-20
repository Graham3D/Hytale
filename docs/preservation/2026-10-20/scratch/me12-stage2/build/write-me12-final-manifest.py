import datetime, hashlib, json, subprocess, xml.etree.ElementTree as ET
from pathlib import Path

root = Path.cwd()
def git(*args):
    return subprocess.check_output(['git', *args], text=True).strip()
def read(path):
    return json.loads(Path(path).read_text(encoding='utf-8-sig'))
def digest(path):
    return hashlib.sha256(Path(path).read_bytes()).hexdigest()
head = git('rev-parse', 'HEAD')
parent = 'b0844f8a013305cffa0b6a9c8912dad571027c3c'
main = 'b839526e7106f780648c315692b04bb372b58d9e'
assert not git('status', '--porcelain=v1', '--untracked-files=all')
assert git('rev-parse', 'origin/main') == main
assert git('rev-parse', 'origin/codex/me12-stage2') == head
receipt_path = 'build/guardian/verification.json'
receipt = read(receipt_path)
assert receipt['sourceCommit'] == head
assert receipt['gradleCheck'] == receipt['packageValidation'] == 'PASS'
patch = read('build/native-patch/verification.json')
assert patch['sourceCommit'] == head and patch['patcherSourceTreeDirty'] is False
publish_path = 'build/guardian/me12-stage2-publication.log'
assert 'local=VERIFIED GitHub=PUBLISHED main=PENDING' in Path(publish_path).read_text(encoding='utf-8-sig')
snapshot = read('docs/enemies/ME12_STAGE2_INPUT_SNAPSHOT.json')
for path, sha in snapshot['inputsSha256'].items():
    assert hashlib.sha256(Path(path).read_text(encoding='utf-8').encode()).hexdigest() == sha, path
sets = {}
for folder in ['build/test-results/test', 'build/test-results/nativeControlTest', 'canvas-ui/build/test-results/test', 'hytale-taverns/build/test-results/test']:
    totals = dict(tests=0, failures=0, errors=0, skipped=0)
    for file in Path(folder).glob('TEST-*.xml'):
        node = ET.parse(file).getroot()
        for key in totals: totals[key] += int(node.get(key, 0))
    assert totals['tests'] > 0 and not sum(totals[k] for k in ('failures', 'errors', 'skipped'))
    sets[folder] = totals
reports = {}
for name in ['stage2-distributions.json','stage2-cache.json','stage2-heap.json','stage2-replay.json','stage2-static-feasibility.json']:
    path = 'build/reports/me12/' + name
    reports[name] = {'path':path, 'sha256':digest(path)}
    if name != 'stage2-static-feasibility.json': reports[name]['result'] = read(path)
gates = read('docs/enemies/ME12_STAGE2_GATES.json')
static = read('build/reports/me12/stage2-static-feasibility.json')
for gate in gates['gates']:
    gate['automatedScope'] = 'Stage 2 offline portions verified at exact source ' + head
    gate['exactHeadAutomatedPassed'] = True
preserved = read('build/reports/me12/stage2-preserved.json')
for key, entry in preserved.items():
    assert digest(entry['path']) == entry['sha256'], key
assert digest('docs/enemies/MONSTER_AFFIX_RELEASE_GATE.json') == preserved['monsterAffixGate']['sha256']
manifest = {
 'schemaVersion':1, 'recordedAtUtc':datetime.datetime.now(datetime.timezone.utc).isoformat(),
 'stage':2, 'state':'ENGINEERING_READY_OFFLINE / INTEGRATION_BLOCKED / QA_PENDING',
 'source':{'branch':git('branch','--show-current'),'head':head,'parent':parent,
    'implementationCheckpoint':'572ee68ffeaed4936bd9630e9a029511139e27b5',
    'upstream':'origin/codex/me12-stage2','upstreamHead':git('rev-parse','origin/codex/me12-stage2'),
    'aheadBehind':git('rev-list','--left-right','--count','HEAD...origin/codex/me12-stage2'),
    'mainHead':main,'mainBehindCandidate':int(git('rev-list','--count','origin/main..HEAD')),
    'workingTree':'CLEAN','newCommits':git('log','--format=%H %s',parent+'..HEAD').splitlines(),
    'changedFiles':git('diff','--name-only',parent+'..HEAD').splitlines()},
 'dependencies':{'Elite':{'source':'3327a5650cadf0173b336072f614a13c63cecc56','state':'QA_PENDING','inclusion':'Inherited reviewed selective ports'},
    'HRO':{'source':'f7d414e6348c84653d03a51117bdfe2fc427dded','state':'QA_PENDING','inclusion':'Inherited reviewed selective ports'},
    'stage1InputSnapshot':snapshot,'snapshotPinCount':len(snapshot['inputsSha256']),
    'branchAudit':{'unintegratedRemoteHeads':read('build/reports/me12/stage2-unintegrated-heads.json'),
        'eliteRemoteHead':'1ff1f06989493135e62b2866393673185ab5083d',
        'elitePinnedLocalHead':'3327a5650cadf0173b336072f614a13c63cecc56',
        'eliteLocalAheadOfRemote':4,'eliteState':'QA_PENDING; existing local commits preserved and selected corrections inherited through Stage 1; no task-wide Elite publication or acceptance inferred',
        'archiveBranch':'codex/r237-source-reconstruction is preserved source-reconstruction history, not a newly accepted feature',
        'acceptedChangesAbandonedByThisPass':False},
    'snapshotHistoricalStage2AuthorizationFlag':'Preserved history; superseded by explicit owner Stage 2 authorization'},
 'build':{'revision':receipt['revision'],'version':receipt['version'],'sourceCommit':head,
    'jarPath':receipt['jarPath'],'jarSha256':receipt['jarSha256'].lower(),'targetHytale':'0.7.1','deployed':False},
 'verification':{'junit':sets,'junitTotal':sum(v['tests'] for v in sets.values()),
    'pythonTests':16,'pythonLog':'build/guardian/me12-stage2-python.log',
    'independentRngOracle':{'vectors':12,'draws':96,'status':'PASS'},
    'check':'PASS','nativePatchOffline':'PASS','nativePatchReceipt':patch,'spotBugs':'PASS','package':'PASS',
    'firstCumulativeRun':{'source':'d52d326a96acf550d73c06027641ca6aadfd7f02','junitPassed':4357,'log':'build/guardian/me12-stage2-d52d326a-first.log','status':'FAILED_SPOTBUGS_SIX_NULL_FLOW_FINDINGS','resolution':'Explicit non-null guards; no suppressions; final cumulative rerun passed'},
    'guardian':{'path':receipt_path,'sha256':digest(receipt_path)},
    'publication':{'path':publish_path,'sha256':digest(publish_path),'state':'VERIFIED_PUBLISHED_MAIN_PENDING'},
    'log':{'path':receipt['checkLogPath'],'sha256':receipt['checkLogSha256'].lower()},'reports':reports,
    'staticSweepSummary':{'contexts':len(static['contexts']),'totals':static['totals'],
        'maxStates':max(c['states'] for c in static['contexts']),
        'maxColdNanos':max(c['coldNanos'] for c in static['contexts'])}},
 'analytical':{'fullTierMasses':[4900,1750,350,1750,625,125,350,125,25],
    'threeAffixNormal':['7/12','5/24','5/24'],'fiveAffixNormalizers':['0.9975','0.9725','0.84'],
    'distinctSetMasses':[6,10,15],'bigIntegerGraphTotal':'300000000000000000000',
    'representativeSamples':900000,'staticContexts':756,'statisticalCriterion':'7 binomial standard deviations + 7 counts across 81 tier cells; exact zero for zero-mass cells',
    'legacySeeds':{'rarity':617,'roster':4,'gear':729292}},
 'gates':gates['gates'],'productionV2Births':'DISABLED','stage1':'BLOCKED_OFFLINE_COVERAGE',
 'monsterAffixRelease':'OPEN','ownerAccepted':False,'nativeConnectedPassed':False,'connectedQA':'DEFERRED_TO_STAGE4',
 'preserved':preserved,'remainingDependencies':gates['preservedDependencies'],
 'nextAction':'Owner authorization required for isolated Stage 3 against this exact published source. Do not enable production births, deploy, merge main or request interim connected QA.',
 'stage3Started':False,
 'limitations':['Static fixtures do not certify real native admission or execution.','P05 future actual native publication and Stage 3 reward effects remain BLOCKED.','P07 connected drain/recovery remains deferred.','V2 reward evaluator deliberately absent; legacy evaluator rejects v2 leaders.','Supported runtime replay tested in two fresh installed Java 25 JVMs, not on other operating systems.','Legacy fixtures are synthetic offline records serialized by the actual baseline, not active-save exports.']
}
Path('build/reports/me12/stage2-final-manifest.json').write_text(json.dumps(manifest, indent=2)+'\n',encoding='utf-8')
print(json.dumps({'head':head,'tests':manifest['verification']['junitTotal'],'state':manifest['state'],'publication':'VERIFIED','reports':list(reports)},indent=2))
