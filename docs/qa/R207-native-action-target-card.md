# R207-U7P5 — native action acceptance and target card

## Connected R206 evidence

The owner ran `/rpg spawn Trork_Warrior unique extrastrong frenzied armorbreaker`. The server accepted the QA birth with `[ME-002, ME-019, ME-025]`. At the Trork's first native melee, the certified damage leaf threw `ENEMY_ACTION_ACCEPTANCE_SNAPSHOT_MISSING` via `NativeEnemyActions.scope`. The target card occupied nearly the full viewport. The selected `Minion · Trork Warrior · Lv 5` displayed only `Extra Strong (Inherited)`.

## Cause and repair

The installed 0.7.0-pre.5.1 native `InteractionChain` constructor initializes `finalState` to `Finished`. `InteractionManager.executeChain0` raises `InteractionChainStartEvent` before its first tick. R206's `NativeEnemyActions.start` incorrectly required `NotFinished`, so it skipped every new accepted root and left no immutable offense snapshot for the later leaf. R207 admits a noncancelled, nonforked start event regardless of its pre-tick final state. All actor/binding validation, capture, and the missing-snapshot fail-closed guard remain in place. No native damage calculation or SDK patch changed.

`RpgEnemyTarget.ui` now anchors its transparent outer group at the upper left with a fixed width. Only its content-sized inner panel paints a background. No target-card data or tag projection changed.

## Leader and minion verification

The focused QA planner/display test models the exact Trork Unique request. It confirms the leader owns and displays `ME-002`, `ME-019`, and `ME-025`; a minion owns none and displays only `Extra Strong (Inherited)`. This matches the registry: Extra Strong grants an inherited Physical increase, while Frenzied and Armor Breaker have no minion inheritance. The R206 screenshot identified a minion, not the Unique leader. Connected gameplay effects for Frenzied and Armor Breaker still require owner observation after native melee is repaired.

## Offline verification and deployment

- Focused `EnemyQaBirthPlannerTest` passed, including leader/minion projection. `NativeEnemyActionsStartTest` passed in the repository's isolated native-test JVM and checks that a fresh `Finished` chain is eligible for acceptance before its first tick while a cancelled start is rejected. `EnemyNativeOutgoingTest` also passed in that native-test JVM, covering the existing snapshot/certified-action path.
- Java compilation, native patch hash verification, native proc asset audit, and `validateCustomUi` (88 documents) passed. No standalone Hytale server was launched.
- Packaged manifest: `Version=0.2.0-R207-U7P5`, `RpgRevision=R207-U7P5`.
- R206 backup outside Saves: `evidence/hyarpg/jar-deploy-20261007T132510680Z/HyARPG-before.jar`, SHA-256 `74B9AD14942182E9F080800EA59D6E1D6BF2A8688F7D1F60452EA687CCEDEBB9`.
- Sole active RPG save mod: `Hytale/data/pre-release/Saves/RPG/mods/HyARPG.jar`, SHA-256 `2ADF7BBE5D9A0C29E9E9A14E04D519BCCB4E574D7CE609278507F34036A4C77C`. Built and installed checksums match.

After restarting the world, spawn the same Trork Unique pack. Target the leader and a minion separately: the leader should show three own affixes and the minion only inherited Extra Strong. Let the Trork perform native melee and check for absence of `ENEMY_ACTION_ACCEPTANCE_SNAPSHOT_MISSING`; then observe Frenzied and Armor Breaker. Check that the target panel stays compact. Connected acceptance remains with the owner. Request a detailed trace only if Frenzied or Armor Breaker still cannot be verified.
