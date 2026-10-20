import json
from pathlib import Path

out = Path('docs/enemies/MONSTER_AFFIX_RELEASE_GATE.json')
core = {
    'ENEMY_ACTION': 'Accepted native melee/projectile leaves use the frozen exact action snapshot.',
    'ORDINARY_DIFFICULTY': 'Every eligible ordinary native spawn receives the current era profile after a declined Elite birth.',
    'SKELETON_BINDINGS': 'All seven reported concrete Skeleton roles have certified native actions and capabilities.',
    'CHAMPION_IDENTITY': 'Every Champion presents Champion plus its native creature name across native and inspect views.',
    'CHAMPION_COMPOSITION': 'Every Champion group has peer Champion members and no Unique minion or leader semantics.',
    'PLAYER_CAST': 'The reported charged_bolt cast commits or rejects for a proven valid reason without combat corruption.',
    'ADMISSION': 'Natural and QA admissions preserve source, capacity, rollback and native spawning safeguards.',
    'PRESENTATION': 'Native name, healthbar, affix and rarity presentation is correct and persistent.',
    'REWARDS': 'XP, equipment and learning receipts are exactly once and exclude disallowed sources.',
    'LIFECYCLE': 'Spawn, profile, fight, wound, unload, reload, republish, defeat, reward and restart all pass.'
}
rows = []
for ident, expected in core.items():
    rows.append(dict(id=ident, expected=expected, status='BLOCKED', implemented=False,
                     automatedPassed=False, nativeConnectedPassed=False, ownerAccepted=False,
                     tests=[], evidence=[], missingOrFailure='R248 candidate verification and owner connected QA pending.'))
for number in range(1, 28):
    ident = f'ME-{number:03d}'
    rows.append(dict(id=ident, expected=f'Accepted {ident} positive and negative vectors, era behavior, native delivery, persistence and cleanup.',
                     status='NOT_RUN', implemented=True, automatedPassed=False, nativeConnectedPassed=False,
                     ownerAccepted=False, tests=[], evidence=[],
                     missingOrFailure='Map existing focused tests and collect final-build connected evidence.'))
data = dict(schema=1, feature='Monster Affix', status='OPEN', ownerAccepted=False,
            baselineSourceCommit='be5c57d1dcdabe019fda7b612e6a4d39551245c0',
            baselineReviewedCommit='f96e67ab096c47ba8d6e665667079d7e185e4cfd',
            installedJarSha256='819CC21B80E49EF10FF079361C6B24F413ABA3A6DBB8DFC0D5EEDFC4E8667599',
            installedServerSha256='a032a64e03390ca0aeb6c03c3b1600eaa892c8bc833d1bf3ac39307ccdf3cb20',
            installedAssetsSha256='eddf425546559bfbc3f2656babf30daa4008543c38ee5aa4499c605ed1732236',
            worldConfigSha256='3D7E86AB1B97F36E41F124FD3E03C682923F77F59B898FCE8CA0221542DF7986',
            candidateSourceCommit='', candidateJarSha256='', requirements=rows)
out.write_text(json.dumps(data, indent=2) + '\n', encoding='utf-8')
