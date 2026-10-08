# R229-U7P5 consolidated production Elite release

Date: 2026-10-08. Deployed once to the active `Saves/RPG/mods/Hywind.jar`; no intermediate owner QA builds were deployed for this coverage pass.

## Coverage

- Catalog: 275 IDs; 82 appear in native world-spawn entries.
- Naturally spawning catalogued hostile roles: **68** (including native roles whose omitted `DefaultPlayerAttitude` resolves to Hostile).
- Production Elite-certified hostile roles: **66**. All naturally spawning hostile roles with a certifiable existing native combat route are covered.
- Genuine technical exclusions: **Goblin_Turret** and **Scarak_Seeker**. Both use native `ProjectileInteraction` plus projectile-config impact effects. The existing version-pinned exact-root receipt hook covers `LaunchProjectileInteraction`, not this route; Master Enemies cannot safely correlate these hits and status effects without a separately authorized native receipt seam. Their original native attacks remain unchanged, and production admission remains fail-closed for these two roles.
- Fourteen other naturally spawning catalogued roles are not hostile Elite candidates because of native Neutral attitude/protected ownership. The exact per-role reasons and newly certified archetypes are in [R229 production Elite coverage](R229_PRODUCTION_ELITE_COVERAGE.md).

## Implementation boundary

This release extends the existing `EnemyNativeBindings`/`EnemyAffixSelection` certification path with pinned installed combat archetypes and reuses existing action-root receipts. It preserves all 27 affix implementations, rarity rules, rewards, progression, encounter ownership, and natural spawn architecture. Native role adapters change existing damage-leaf type only; native attack selection, animations, movement, damage values, projectile behavior, and effects remain owned by Hytale. Unsupported affixes remain excluded by action capability rather than silently doing nothing.

The installed asset and R228 package baselines are pinned by `tools/u7p5-r228-semantic-baseline.json` and the offline compatibility validator. Runtime attachment revalidates native action graphs and fails closed on a mismatch.

## Automated validation

- `gradlew check --offline --quiet`: **PASS** on 2026-10-08.
- Main test task: **3,407 tests across 340 suites, zero failures/errors**. Additional offline native-control, package, resource, and asset validation gates in `check` passed.
- Packaged JAR: `InigmasGames:HyARPG@0.2.0-R229-U7P5`, 19,183,662 bytes, 19,270 entries, no ImmersiveNPCs-owned payload.
- No local Hytale server or connected game test was run. Connected acceptance belongs to the user's one final QA pass.

## Deployment

- Active save: `C:\Users\Zemio\AppData\Roaming\Hytale\data\pre-release\Saves\RPG`.
- Deployed JAR: `C:\Users\Zemio\AppData\Roaming\Hytale\data\pre-release\Saves\RPG\mods\Hywind.jar`.
- Deployed SHA-256: `06472874DD16894D351C306ADEE6ADC22AB8C971A84E1CFA80FF8E721E95B29F` (matches the validated build artifact).
- Previous R228-U7P5 JAR backed up outside Saves: `C:\Users\Zemio\.codex\backups\Hytale\Hywind-R228-U7P5-5d24abff-20261008.jar`.
- Backup SHA-256: `5D24ABFFE40018ADC62179F720D708B58C58C7FD047166F1403C39A428C08387`.

## Connected owner QA

Restart the RPG world to load R229-U7P5, then encounter naturally spawning hostile mobs across ordinary biomes and eras. The final build is ready for this connected pass. `Goblin_Turret` and `Scarak_Seeker` are documented technical exclusions; no Elite promotion is expected for them.
