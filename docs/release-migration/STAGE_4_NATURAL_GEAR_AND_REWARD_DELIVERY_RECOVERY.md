# Chapter I Stage 4: natural equipment and reward delivery

## Scope, source and completion identity

Engineering candidate: **R262C1S4-CANDIDATE**, version
`0.2.0-R262C1S4-CANDIDATE`, exact server requirement `=0.7.0`.
Branch: `codex/chapter1-stage4-gear-loot-recovery`, worktree `C:\hystage4`.
Parent: published Stage 3 `d28d92f49e7639a6c75da71a810088918456b79b`.
This retains Stage 1 `63bf6a616e9031e33fb722a1e30f515abe6bb7c5`, Stage 2
`b5bd3585fb69bcd5f615555cebfea2a2d9d7c88f`, and the earlier startup, four-slot,
player-readiness and INV1 inventory recovery work. At discovery GitHub `main`
was `6d3ef15ff0145532ef193c42e3b648307ee6e915`, 26 commits behind Stage 3.

The owner's direct instruction accompanying Stage 4 explicitly requests safe
integration into GitHub `main` after completion. This authorizes integrating
the reviewed cumulative engineering candidate; it does not accept connected
gameplay or authorize deployment. It supersedes the earlier stage handoffs'
instruction to leave these dependencies unmerged. **Connected QA remains
QA_PENDING**, including the earlier dependent gameplay changes. Historical
baseline entries below the current entry describe their state at that time.

Final committed source, full test counts, JAR SHA-256 and the post-commit
check log are bound by `build/guardian/verification.json`. The deployment
pair and exact source are recorded in `build/stage4/deployment-manifest.json`;
the publication/main integration receipt is `build/stage4/integration.json`.
These generated receipts avoid changing the source commit to record its own
HEAD-dependent artifact hash. ENGINEERING_READY requires all completion gates
below; publication alone is not sufficient.

The separate `C:\hyhro`, `codex/hro-stage1` diagnostic task was observed on
`3e359b8e036ad023c9c8bd93064bcb0f78068f83` with unfinished changes. Its gear
instrumentation concerns equipment transfers, while Stage 4 changes automatic
world-item pickup. None of its work is imported. It must reconcile the new
integrated baseline before its own integration. The dirty primary checkout,
including artwork and its different AGENTS.md, is preserved and is not
silently synchronized or switched.

## Evidence and confirmed failure boundaries

The authoritative release save is
`C:\Users\Zemio\AppData\Roaming\Hytale\UserData\Saves\Hywind`.
Runtime mod data is under `mods\InigmasGames_HytaleRPGPhase00Audit`, including
`encounters`, the difficulty registry, and `evidence\gear-trace`.
Historical `data\pre-release\Saves\RPG` is comparison evidence only. The
legacy AGENTS.md deployment-target discrepancy remains documented and the
protected policy file is unchanged.

The October 1 gear captures copied into Hywind are identical to the historical
RPG captures, including `gear-trace-20261001T225351689Z-30c15b69.jsonl`.
They contain enemy-death gear decisions and `WORLD_DROP_SPAWN: SPAWNED`, for
example item `b14bc165-e2e7-390a-9dad-bdc648d45b91`. They prove historical
projection, not fresh release natural-origin acceptance. The release logs and
traces reviewed in Stage 0 show `WORLD_NOT_READY` and the persistence-admission
failure, before gear delivery. Stage 1 repairs those upstream boundaries;
Stage 2 restores the native hook contract. None of those builds has been
accepted through connected QA. Stage 4 does not claim to have reproduced a
new connected equipment drop failure from these old captures.

Two downstream defects were reproduced offline before correction:

1. **Frozen death reward mismatch.** `EncounterContributions.death` computed
   the common XP pot from legacy `spawn.rarity()`, while `DeathPlan` validates
   each share against `spawn.rewardXp()`, which includes the immutable Master
   Enemies reward context. The composed natural Champion and Unique fixtures
   failed with `DEATH_SHARE_PROFILE_MISMATCH` before freezing a death or
   delivering gear. The existing helper now computes both sides. Multipliers,
   contribution eligibility, party splitting and validation are retained.
2. **Pickup without a native projection.** `HytaleGearLoot.project` retains a
   WORLD receipt during unloaded/non-ticking or failed entity projection, but
   still calls `autoPickup`. That method formerly fell back to receipt
   coordinates for an absent/invalid/positionless native entity. Two native
   regression cases reached the pickup scan before the fix. Automatic pickup
   now requires a valid projected entity and its Transform. Deferred receipts
   retry the existing projection path; no invisible item is collected.

The before-fix failure evidence is preserved outside source control as
`build/stage4-death-before.xml` and `build/stage4-regression-before.xml`, with
corresponding logs. Fixture setup failures encountered while building the
native test are not presented as production defects.

## Existing owners retained

| Boundary | Production owner and audit result |
|---|---|
| Natural origin and rarity | Existing classification, birth planner, durable descriptors/packs and staging. Stage 3 remains intact. |
| Weapon/skill contribution and death | `HytaleEncounterRewards` joins accepted damage and native zero-Health death callbacks; `PersistentEncounterRuntime` and `EncounterContributions` freeze eligible shares. QA/non-economic contexts remain sterile. |
| Death delivery | `FileEncounterStore.drainPrepared` persists each XP cursor, invokes configured `deliverPicks`, then completes the parent death. A gear failure cannot replay completed XP. |
| Pick opportunities | `GearLootProfiles.CURRENT` and `GearLootService.DeathPicks` freeze the parent decision before independent child receipts. Legacy receipts retain their compatibility path. |
| Gear generation | `GearDropGenerator`, catalog, bindings and native metadata remain the sole owners of legal same-era bases, item level, requirements, rarity and affixes. Content errors remain distinct from no-drop. |
| MF and allocation | Contributions freeze claims; death freezes effective MF. Existing allocation, exclusive interval, sponsor/cursor, revision CAS and expiry remain authoritative. |
| Native world items | `HytaleGearLoot.project`, native `ItemComponent.generateItemDrop`, `PreventPickup` and NonSerialized prevent competing pickup and saved-entity duplication. |
| Spatial pickup | `SpatialInventoryTransferCoordinator` plans bag fit, reserves source, checks session/revision, publishes bag, saves player, then acknowledges custody. |
| Native inventory fallback | Existing space check, exact identity, reservation, native save and acknowledgement; interrupted saves remain pending for explicit recovery. |
| Native corpse drops | `GearNativeDeathDrops` intercepts only registered difficulty enemies and shares the native Role once-only flag. Random weapon/armor drops are filtered; ingredients and pickup-storage restitution retain native delivery. QA corpses remain sterile; unmanaged NPCs retain their normal path. |

The plugin's existing configured gear-delivery callback calls `deliverPicks`
and publishes every returned WORLD receipt to the same gear runtime. No new
loot service, inventory, party owner, schema, spawning system, or reward ledger
was introduced. Combat, skills, progression, difficulty, HUD, CanvasUI,
SpatialBag and NPC boundaries remain intact.

## Profile, generation and ownership verification

The installed `gear-loot-picks-v2` policy is unchanged:

| Rank | Guaranteed | Optional chance(s) | Equipment cap |
|---|---:|---|---:|
| Common | 0 | 0.30 | 1 |
| Specialist | 0 | 0.50 | 1 |
| Elite | 0 | 0.75 | 1 |
| Miniboss | 1 | 0.25 | 2 |
| Boss | 1 | 0.75 | 2 |

There are no authored role overrides. The Gear Master v1.2 document's older
one-item description differs from this current multi-pick runtime. Preserve
the runtime and its independent receipt identities. Master Enemies quantity
adds frozen children only to successful base opportunities; it does not
convert NO_DROP into a drop. No rates, rarity weights, affix counts, caps or
MF rules were rebalanced. The requested RPG master v1.7 was not locally
available; the known v1.5.2 and Gear v1.2 references are historical context,
not authority to undo later engineering.

The new `ChapterINaturalRewardDeliveryTest` composes the existing birth
planner, published durable special packs, accepted damage, death freezing,
configured parent delivery, real on-disk gear receipts, pickup reservations
and acknowledgement across restarts. It exercises Normal, Champion and Unique,
including faults after `loot-picks`, `loot` and `cursors` publication, no XP
replay, frozen MF/identity, legal mapped items, wrong-owner/full-inventory
rejection, interrupted reservation release, exact-item acknowledgement and
no resurrection after inventory custody. A legitimate optional no-drop
completes XP and retains an empty decision across restart. Deterministic
fixture selection does not alter production probabilities.

Existing suites retain coverage for no contributor/stale credit, exclusion,
QA origins, duplicate death/removal, party snapshots and cursors, simultaneous
pickup, legacy single receipts, content-definition changes, multi-pick
interruptions, economic quantity, spatial receipt recovery, no-fit bags,
metadata, player-issued drops, salvage and consumed items. The current party
provider's existing availability restrictions remain in force; no unavailable
provider is synthesized.

## Native projection and inventory limits of proof

`NativeGearWorldProjectionTest` invokes the real `HytaleGearLoot.project` and
`autoPickup` with the installed release API, real native ECS stores, decoded
packaged gear and `ItemComponent.generateItemDrop`. It checks unloaded and
non-ticking deferral, live item creation, `PreventPickup`, NonSerialized,
frozen metadata, no duplicate projection, native-ref removal/reprojection,
world-custody rejection by `GearNativeItems.read`, and projection cleanup on
pickup reservation. Missing/invalid/positionless refs never reach pickup.

The fixture uses inert world/chunk objects and supplies chunk availability;
it never starts a World, server, chunk IO, client or authentication. Chunk
streaming/persistence systems are omitted only in the isolated test JVM. The
existing asset fixture supplies referenced keys; full package/asset checks
remain separate. It does not prove a visible client entity, native streaming,
collision/physics, network pickup, or an actual native player save callback.
The durable-custody test invokes acknowledgement after a simulated adapter
save boundary and labels that limitation explicitly. Connected inventory and
death-corpse suppression remain owner QA requirements, not inferred successes.

Fifteen relevant native class entries are byte-identical between the installed
release and historical pre-release server: item generation, pickup markers
and system, all ten InventoryComponent variants audited, and NPC
`DropDeathItems`. Exact hashes are in `build/stage4-native-loot-audit.json`.
This supports reusing the native adapters; it does not replace connected QA.

## Source changes and completion gates

Production changes are limited to `EncounterContributions.java` (use the
existing frozen XP helper) and `HytaleGearLoot.java` (require a projected
position for automatic pickup). Two regression classes are added;
`build.gradle` assigns the native fixture to the existing isolated native
test lane. `gradle.properties`, `README.md`, this report and `DEVELOPMENT_BASELINE.md`
record the completed candidate. No player command changed; existing syntax,
permission and save/build gates remain in `docs/COMMANDS.md`.

Completion requires focused tests, full `gradlew check` at committed HEAD
(root, isolated native, CanvasUI and tavern lanes), offline package/UI/asset
validation, strict-baseline SpotBugs, native patch regression, Guardian
preflight/change/staged/completion checks and hash inspection of both artifacts.
Actual final counts and command results are in the verification receipt and
`build/stage4/verification-summary.json`; a failure blocks completion. The
before-fix tests failed for the causal defects above; the corrected focused
tests and final complete suite must pass with zero failures or skips.

Known nonfatal compiler/asset warnings include deprecated native APIs,
legacy asset-key casing and 32 campaign assets without authored regions.
They were not rewritten as part of this bounded recovery. SpotBugs uses the
existing reviewed baseline; no new exclusions are introduced.

## Paired artifacts, deployment and rollback

Offline deliverables:

- `build/stage4/Hywind.jar`
- `build/stage4/HytaleServer-packbound.jar`
- `build/stage4/deployment-manifest.json`

Compile against installed release **0.7.0**, server revision
`648ebf375a034beabf119d7d790cd11d4f665ff4`, stock SHA-256
`c3b5985b9e72727bf3bcf220923d7e61e78fe0f37cd1cd9c2a73f51e4f408ff8`.
The matching Stage 2 patch must remain SHA-256
`e0bcd36c7a981949d2c15b1b48f1cdd7f9623373269ef03b58f2d9b9da7f6edf`.
The patch regression checks six transformed classes, eleven added hook
classes, unknown-server rejection, duplicate-patch rejection and JVM bytecode
verification. The release runtime verifies the actual native hook origin and
contract; a plugin copy of the classes is not a substitute for patched native
call sites.

**No deployment is performed in Stage 4.** The installed Hywind mod remains
INV1, SHA-256 `3b80550cca85e59061c40b393a574fa42f7009180e5b711f91930be1af719b47`;
the installed server remains stock. Neither save is modified. Stage 1's
guarded world-UUID registry reconciliation remains a future first-load change,
with old identities retained in history; journals and inventories are not
reset or regenerated. No source-level Stage 4 save/schema migration is added.

After separate deployment authorization: verify the game/server is stopped,
compare exact candidate ancestry and both hashes, back up the installed
plugin, server, Hywind world configs, difficulty registry and backup, player
files, encounter/birth/death journals and mod data outside `Saves`. Verify
backup hashes. Install the matching release server patch and Hywind JAR as a
pair and recheck installed hashes. Never install into historical RPG or mix
this plugin with the stock/pre-release server. Rollback restores the backed-up
pair and the corresponding save snapshot; do not cherry-pick mutable receipt
files or delete journals. No server-launching smoke script is authorized.

## Owner connected QA

After paired deployment, launch Hywind in the main game. Use the existing
`/rpg-trace gear start` and `stop` around legitimate natural enemy deaths with
both native weapons and RPG skills. Include Normal, Champion and Unique when
encountered. Verify no exception at death, normal XP, genuine item appearance,
protected allocation, pickup into the current inventory owner, unchanged item
identity/affixes, full-bag behavior and one retained item after reconnect.
Correlate natural-spawn/death IDs with `ENEMY_GEAR_PROFILE`,
`ENEMY_GEAR_DECISION`, `GEAR_ROLL`, `GEAR_GENERATED`, `WORLD_DROP_SPAWN` and
`REWARD_DELIVERED`/pickup receipts. NO_DROP is valid when its optional pick
fails. Include save/restart recovery and retained native ingredient drops.

`/rpg gear odds <era> <1..99> <common|specialist|elite|miniboss|boss> [seed]`
is an existing read-only diagnostic with gear-QA and author-permission gates;
it cannot prove delivery. Command-generated items and `/rpg spawn` QA enemies
cannot prove natural rewards. No additional diagnostic command or debugging
duty is added. The owner supplies the existing trace/log and gameplay findings;
Codex investigates those. Engineering publication/integration and connected
acceptance remain distinct.
