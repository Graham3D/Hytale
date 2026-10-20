import json
from pathlib import Path

path = Path('docs/enemies/MONSTER_AFFIX_RELEASE_GATE.json')
gate = json.loads(path.read_text(encoding='utf-8'))
document = 'docs/enemies/R248_RELEASE_CANDIDATE.md'
cases = {
    'ENEMY_ACTION': (True, True, ['com.inigmasgames.hytalerpg.enemies.EnemyNativeOutgoingTest#capturesNativeFlatPercentAndInheritedSourceEffectsWithoutMutatingSourceOrSubmittingDamage', 'com.inigmasgames.hytalerpg.enemies.EnemyNativeBindingsTest'], 'Wrapped Selector fork fails against the baseline raw operation comparison and passes after unwrapping. Connected Goblin/Bear contacts, stale/duplicate execution and long combat remain unverified.'),
    'ORDINARY_DIFFICULTY': (True, False, ['com.inigmasgames.hytalerpg.execution.hytale.NativeEnemyPreRootRollbackTest#preRootReleaseDefersOrdinaryProfileWithoutReenteringBirth', 'com.inigmasgames.hytalerpg.enemies.R244ProductionProfileTest'], 'Deferred handoff path is source-checked and profile resolver tested; native Hell fallback Health/damage and alternate ingress routes need connected proof.'),
    'SKELETON_BINDINGS': (True, True, ['com.inigmasgames.hytalerpg.enemies.ProductionEliteArchetypeAssetTest', 'com.inigmasgames.hytalerpg.enemies.EnemyNativeBindingsTest'], 'Seven trace-reported concrete role IDs resolve pinned installed action assets; actual natural combat and promotion need connected proof.'),
    'CHAMPION_IDENTITY': (True, True, ['com.inigmasgames.hytalerpg.enemies.EnemyDisplayDtoTest'], 'Primary native/inspect projection has Champion prefix; connected localized rendering, long names and LOAD need proof.'),
    'CHAMPION_COMPOSITION': (True, True, ['com.inigmasgames.hytalerpg.enemies.EnemyBirthPlannerTest'], 'Planner produces peer CHAMPION/MEMBER actors; native publication and LOAD need connected proof.'),
    'PLAYER_CAST': (False, False, ['com.inigmasgames.hytalerpg.Stage11RetaliationTest'], 'Charged Bolt trace proves validation PASS followed by COMMIT_FAILED_IllegalStateException, but the prior trace lost the inner cause. New bounded stage/cause diagnostic awaits a connected recurrence.'),
    'ADMISSION': (True, False, ['com.inigmasgames.hytalerpg.execution.hytale.NativeEnemyPreRootRollbackTest', 'com.inigmasgames.hytalerpg.enemies.R247WorldRebindLifecycleTest'], 'R247 connected publications exist; full R248 natural source/rollback/profile and capacity paths are not yet connected verified.'),
    'PRESENTATION': (True, False, ['com.inigmasgames.hytalerpg.enemies.EnemyDisplayDtoTest'], 'Native client name, affix, Healthbar, long-name and rarity presentation need screenshots and connected observation.'),
    'REWARDS': (True, False, ['com.inigmasgames.hytalerpg.progress.MasterAffixEconomyCompletionTest', 'com.inigmasgames.hytalerpg.enemies.EnemyBirthPersistenceTest'], 'Exactly-once death, economic receipt and restart behavior on this candidate have no connected proof.'),
    'LIFECYCLE': (True, False, ['com.inigmasgames.hytalerpg.enemies.R247WorldRebindLifecycleTest', 'com.inigmasgames.hytalerpg.enemies.EnemyBirthPersistenceTest'], 'Full spawn, profile, fight, wound, unload, reload, republish, defeat, rewards and restart sequence is NOT_RUN on the candidate.'),
}
for row in gate['requirements']:
    rid = row['id']
    row['sourceCommit'] = gate.get('candidateSourceCommit', '')
    row['buildSha256'] = gate.get('candidateJarSha256', '')
    if rid in cases:
        implemented, automated, tests, reason = cases[rid]
        row.update(status='NOT_RUN' if rid == 'LIFECYCLE' else 'BLOCKED', implemented=implemented,
                   automatedPassed=automated, nativeConnectedPassed=False, ownerAccepted=False,
                   tests=tests, evidence=[document], missingOrFailure=reason)
    else:
        row.update(status='BLOCKED', implemented=True, automatedPassed=False,
                   nativeConnectedPassed=False, ownerAccepted=False,
                   tests=['com.inigmasgames.hytalerpg.enemies.EnemyAffixSnapshotTest',
                          'com.inigmasgames.hytalerpg.enemies.EnemyAffixSelectionTest'],
                   evidence=['docs/enemies/COMPLETION_MATRIX.md', document],
                   missingOrFailure='Existing implementation and partial offline fixtures are mapped; individual native positive/negative, era, persistence, cleanup and connected evidence are incomplete for this card.')
path.write_text(json.dumps(gate, indent=2) + '\n', encoding='utf-8')
