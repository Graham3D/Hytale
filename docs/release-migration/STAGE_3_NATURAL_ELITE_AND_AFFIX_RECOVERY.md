# Chapter I Stage 3 — natural Elite and Monster Affix recovery

Date: 2026-10-12. State: **ENGINEERING_READY / QA_PENDING**, subject to the
exact-HEAD completion receipt and owner-operated connected QA. Branch:
`codex/chapter1-stage3-elite-restoration`, parent Stage 2 commit
`b5bd3585fb69bcd5f615555cebfea2a2d9d7c88f`. Stage 1, Stage 2, and their
earlier cumulative ancestors remain unaccepted and unmerged. The protected
release save is `UserData/Saves/Hywind`; historical `data/pre-release/Saves/RPG`
was read only. The older `AGENTS.md` routine-save wording conflicts with the
owner's explicit Hywind selection and was not changed.

## Evidence and earliest release failure

The R249-U7P5A pre-release natural spawn trace
`monster-spawn-1791602605952.jsonl` recorded 1,142 `NATIVE_CLASSIFY_READY`,
18 `BIRTH_ROOT_DURABLE`, and 18 `ELITE_PUBLISHED` events, including Rat,
Snapdragon, Raptor, Piranha, Goblin, Wolf, Toad, and Rex roles. This is real
native-job evidence rather than `/rpg spawn` QA evidence. The Chapter I INV1
trace `monster-spawn-1791847491604.jsonl` recorded 1,256
`NATIVE_CLASSIFY_REJECT`, all `WORLD_NOT_READY`, and no natural birth. Stage 0
identified the copied difficulty registry's old UUIDs and absent release
native patch as the earliest demonstrated blockers. Stage 1 reconciles the
two verified release UUIDs and protects encounter admission. Stage 2 supplies
the exact 0.7.0 mutation, damage, projectile, and population call sites and
requires their shared patched-server code source before production promotion.
Neither stage has yet been connected-QA accepted or deployed.

## Release native spawn and group audit

The installed stock release server is Hytale 0.7.0, revision
`648ebf375a034beabf119d7d790cd11d4f665ff4`, SHA-256
`c3b5985b9e72727bf3f220923d7e61e78fe0f37cd1cd9c2a73f51e4f408ff8`.
The exact `WorldSpawnJobSystems.Ticking`, `WorldSpawnJobSystems`,
`FlockPlugin`, `NPCPlugin`, `SpawnJobData`, `NPCEntity`, and `WorldSupport`
class bytes were compared with the installed, historically functioning
pre-release server. **All seven classes are byte-for-byte identical.** Their
release SHA-256 values are pinned in `ChapterINativeSpawnBinaryTest`. Bytecode
inspection confirms `Ticking.tick` synchronously calls the native `run`, which
calls `NPCPlugin.spawnEntity` and `FlockPlugin.trySpawnFlock`; the native
leader's existing membership is reused by flock extension. This permits the
existing `NativeEnemySpawnGroups` holder callback and `NativeEnemyFlockExtension`
to remain the authoritative release path. No second spawner, after-the-fact
nearby scan, replacement AI, or new member-count algorithm was introduced.

The existing production handoff is:

1. A certified native hostile job enters `NativeEnemySpawnGroups.tick`, which
   wraps the original native tick and captures only same-job holder additions.
   `NativeEnemyBirthOwner.eligible` requires the complete Stage 2 hook set,
   admitted world, certified role, and bounded group size.
2. `NativeEnemyBirthDecision` classifies the concrete roster through the
   Stage 1 difficulty binding and existing authored profile. It calls
   `EnemyBirthPlanner` for the unchanged 1,000-weight Normal/Champion/Unique
   draw and `EnemyAffixSelection` for the frozen legal affix set. Super Unique
   remains an authored/explicit route.
3. The existing `NativeEnemyFlockExtension` creates only the calculated
   additional native members, with population and pack-capacity checks.
   Rejected pre-root plans release the staged original group to ordinary
   spawning; uncertain durable writes stay quarantined under recovery.
4. `NativeEnemyBirthReservation` writes the immutable root before
   `NativeEnemyBirthAttachment` binds Health, native actions, and state.
   `NativeEnemyBirthPublication` advances the pack durably before releasing
   native staging flags and presenting the exact saved actors.
5. `NativeEnemyWholeBirthRecovery` and `NativeEnemyWorldRebind` reload the
   frozen birth, actors, affixes, and pack state without rolling again.

This audit found **no additional release-specific defect in those production
owners after the Stage 1 and Stage 2 upstream corrections**. An apparent
affix-policy cache issue was explicitly disproved: a policy-only
`monsterAffixes` change alters the `EnemyBalance` revision, which is already
the planner cache key. No speculative source change was retained.

## Stage 3 regression coverage

Two new tests cross boundaries that earlier tests covered separately:

- `ChapterINativeSpawnBinaryTest` pins the seven installed release native
  classes used for job ownership, synchronous capture, flock formation, and
  concrete hostile identity. A changed binary requires fresh review.
- `ChapterINaturalElitePipelineTest` copies the historical checked registry
  into a temporary directory, reconciles the two actual release world UUIDs,
  and classifies an authored `Wolf_Black` in the active release `default`
  identity. It finds deterministic Normal, Champion, and Unique job seeds
  without altering production probabilities, runs the existing pack demand
  and additional-member preparation, verifies actor/role/affix identity,
  writes the immutable birth root and pack state, restarts the store, and
  confirms the saved plan and affixes are unchanged. A Normal draw retains
  the ordinary no-pack outcome. These fixtures do not launch a server or
  claim a connected native NPC was spawned.

Existing passing suites retain coverage for native capture/staging and
duplicate callbacks (`EnemyNativeStagingTest`), roles and assets
(`ProductionEliteInstalledRouteTest`, `ProductionEliteArchetypeAssetTest`,
`EnemyNativeBindingsTest`), rarity and affix restrictions
(`EnemyBirthPlannerTest`, `EnemyAffixSelectionTest`,
`EnemyAffixSnapshotTest`), flock headroom/rollback
(`NativeEnemyFlockExtensionTest`, `R245FlockRollbackTest`), durable fault
injection/recovery (`R244NaturalBirthIntegrationTest`,
`R245BirthTransactionTest`, `EnemyBirthPersistenceTest`,
`NativeEnemyStagingRecoveryTest`), combat/protection and damage leaves
(`EnemyNativeDamageLeafTest`, `EnemyNativeDefenseTest`,
`NativePatchGateTest`), and presentation (`EnemyNativeHealthbarQaTest`,
`HytaleEliteTintTest`, `R244NamesTest`). Stage 1 world identity and context
tests and Stage 2 native patch regressions remain required. The existing
Monster Affix selection engine retains its authored exclusions, role
capabilities, immunity rules, caps, inheritance, and frozen birth descriptors;
no coefficients, weights, spawn density, reward ownership, or QA economic
exclusion changed.

## Verification and artifact boundary

Source metadata: revision `R262C1S3-CANDIDATE`, version
`0.2.0-R262C1S3-CANDIDATE`, exact `ServerVersion =0.7.0`. The final source
commit, full JUnit count, package SHA-256, and Guardian state are recorded in
the generated `build/guardian/verification.json` after committed verification;
a HEAD-bound artifact hash cannot be placed in this tracked report without
changing HEAD. The candidate plugin is `build/stage3/Hywind.jar`; its matching
native binary is `build/stage3/HytaleServer-packbound.jar`. The latter must
retain the Stage 2 reviewed hash
`e0bcd36c7a981949d2c15b1b48f1cdd7f9623373269ef03b58f2d9b9da7f6edf`.
The exact release compile, full test lanes, patch regression, package/asset
validation, static analysis, Guardian checks, and final SHA-256 are performed
as the Stage 3 completion gate. Any final failure prevents ENGINEERING_READY.

Known nonfatal release warnings include deprecated API use and legacy item
asset-key casing. They are unrelated to the demonstrated natural birth gate
and were not rewritten. This work is offline: it cannot prove a displayed
Healthbar, actual native weapon/skill damage, natural Champion or Unique
frequency, or post-restart connected appearance. The game's **Show Entity
Health Bars** setting can also affect the visible native bar. Those are owner
QA observations, not assumptions from passing unit tests.

## Safe paired deployment and rollback

Stage 3 does **not** authorize installation. For separately authorized
deployment, first stop the game and local server, verify the installed
release stock server and matching patch identity, and back up the installed
`Saves/Hywind/mods/Hywind.jar`, native server, release world configs,
`difficulty-worlds.json` and `.bak`, encounter journals/contexts/deaths/births,
player progression/inventory, and relevant mod data to a recovery directory
**outside `Saves`**. Verify backup hashes before replacing the native server
and Hywind JAR as a pair. After installation, verify both installed SHA-256s.
Rollback restores that same backed-up pair and save state; never mix the
Stage 3 plugin with the stock or pre-release patched server. Do not deploy to
or alter historical `Saves/RPG`.

## Owner connected QA and Stage 4 handoff

After authorization and paired deployment, use `/rpg-trace spawn start` and
`/rpg-trace spawn stop` around native hostile activity in the **Hywind** world.
Look for `NATIVE_CLASSIFY_READY`, `RARITY_DECISION`, `ELITE_PLAN_RESULT`,
`BIRTH_ROOT_DURABLE`, `BIRTH_PACK_PUBLISHED_DURABLE`,
`BIRTH_NATIVE_FINISH`, and `ELITE_PUBLISHED`; a Normal draw is valid ordinary
fallback. Inspect a naturally promoted Champion and Unique for pack membership,
affix labels, rarity color/name/tint, native attacks, weapon and skill damage,
Packbound guard behavior where selected, Healthbar after Health loss, death,
cleanup, and unload/reload identity. `/rpg spawn Skeleton_Fighter unique hell`
is a separate deterministic, economically sterile QA route and cannot prove
natural promotion. Supply the spawn trace, relevant combat trace, server log,
and observations/screenshots. If the first connected failure differs from
the reviewed offline path, repair that exact boundary on this feature branch;
connected acceptance is the owner's decision.

Stage 4 begins only after Stage 3 handoff. It owns complete natural
equipment-drop generation/delivery acceptance. Stage 3 preserves encounter
and reward identity but does not alter Magic Find, item rarity, XP,
equipment-drop probabilities, inventory, or loot economy.
