# R032-AS — Quick Slash authoritative Light Attack reference profiles

Date: 2026-09-13. Branch: `RPG`. Starting HEAD: `25b85cb2819351a0d727e33132b2f57013cda024`.

Status: **IMPLEMENTED — PACKAGED — DEPLOYED**. CONNECTED-VERIFIED: **NO**.

Installed in the normal single-player RPG save as `HyARPG.jar`. Built, archived, isolated-smoked and live-installed SHA-256 all match: `114AFD95E29A4DC49DBAE688EA8D0038680FF56AE1855A1F3AC649185A033E25`.

## Authority and scope

The owner's task amendment supersedes the literal-native-attack proposal and the previous native-speed boundary stop. Quick Slash remains an RPG Strike. Hytale supplies an authoritative **reference composition and timing**, not a second executed native attack. No native interaction chain, charge/combo controller, basic-attack recovery, input packet handler, durability/ammo controller or global attack-speed state is cloned or changed.

This candidate is cumulative over the installed R032-AR build. Existing dirty AQ/AR changes and owner art were preserved. Nothing is pushed. Reports and artifacts remain in the local GitHub/Hytale folder, not Google Drive. No unrelated skills, Healing Beam, Blizzard, resource formulas, progression/save formats, native HUD ownership or Mantle's ordinary-native-weapon producer are redesigned.

The new authored contract is **two 37.5% weapon-composition strikes**, not the old two full-damage strikes. Base Stamina remains 5 once and base cooldown remains 0.8 seconds once; existing stat/passive modifiers still apply to both costs/cooldown and outgoing damage. The final cooldown is therefore not necessarily exactly 0.8 seconds on a character with cooldown recovery.

## Reference reader and actual native evidence

`WeaponLightAttackProfileResolver` is a bounded read/compile adapter. `NativeWeaponLightProfiles` feeds it the actual installed, inherited native `Item`, `RootInteraction` and polymorphic `Interaction` asset maps through their codecs, together with the exact item's resolved `InteractionVars`. It follows the uncharged zero branch and first ordinary combo entry; it never calculates a charged/combo average or guesses damage from an item name.

Supported structure: roots, Simple timing, Charging's uncharged branch, Chaining's first entry, exact Replace/default roots, sequential Serial operations, single-damage Parallel paths, Selector contact windows and one absolute DamageEntity calculator. The existing managed Fire leaf is read as its authored calculator, not invoked as a native producer.

The reader rejects missing graphs/variables, unknown operations, more than one damage leaf, relative/sequential calculators, actual angle/target-specific calculator overrides, invalid values and graph-budget overflow. Limits are 48 recursion levels, 256 visited nodes, 32 unique components, finite timing/ranges and a five-second maximum reference duration. There is no generic `0.85` fallback in the production Quick Slash path.

Two details required loaded-native verification rather than source-JSON assumptions:

1. Empty native TargetedDamage serializes as `{}`, not necessarily `[]`.
2. Shipped `DamageEntityParent.Next` contains a Serial feedback tail: target `Red_Flash` and removal of named native potion-regeneration effects. The reader explicitly validates that zero-time tail, but does not reproduce those native recipient-side effects. Unknown effects/additional damage still fail closed.

The installed `DamageEntityInteraction` bytecode also proves that an AngledDamage/TargetedDamage entry with a **null calculator retains the base calculator**. Feedback-only dagger angles are therefore representable; they do not justify rejecting ordinary Bronze/Bone/Onyxium dagger source damage. Actual non-null conditional calculators remain unsupported. The reader neither averages them nor imports native conditional execution.

Pinned native inputs:

- Hytale `0.7.0-pre.2` server SHA-256: `9CDC1E77DADFDAD0B1D849977CDAD5C2752207F538272FB3D76D4E915546B25E`.
- Assets.zip SHA-256: `0E35DF2D4E4A803F7752CB300FA7C9FE7ACDF62B4311978DA95C7AFF7ABBC126`.
- Exact final loaded inventory and reasons are archived as `evidence/stage-13/cohort-as/native-light-profiles.json`.

Final native audit: **51 supported profiles** (16 SWORD, 19 LONGSWORD, 16 DAGGER) out of 53 currently audited eligible records. The two explicit unsupported records are:

- `Weapon_Claws_Tribal`: native `Condition` operation; no unambiguous bounded direct reference accepted.
- `Weapon_Longsword_Ruined_Giant_Dual_NPC`: multiple damage leaves, not a single ordinary reference hit.

Class follows authoritative registry tags, not the spelling of the item name. For example, the existing registry classifies `Weapon_Longsword_Void` as SWORD; this correction does not change that classification.

The isolated native audit checks all currently audited eligible item records, requires all three existing classes, and asserts concrete inherited values for Iron Sword, Iron Daggers and Flame Longsword. Native asset loading/compilation is **not connected rendering, input, timing or damage proof**.

## Snapshot, damage and critical ownership

An immutable profile carries weapon ID/class, source revision/root, normal duration/contact offset, component IDs, channels, minimum/maximum ranges, random grouping, provenance and critical eligibility. The production adapter validates it before commit, and `SkillExecutionService` attaches the captured profile to the shared root effect owner during commit preparation. Both authored hits and allowed repeat children use that same snapshot. Native release validation rejects changed equipment/world/state rather than substituting a new weapon for hit two.

Each authored hit has a separate `.../light-0` or `.../light-1` execution/effect identity and a separate hit ledger index. It samples one native uniform variance value across the base calculator's channels, once per hit, shared across that hit's victims. The second authored hit gets a new sample. A future affix provider can use an independent random group. No native attack is executed just to obtain a random roll.

The existing RPG critical owner supplies one independent critical opportunity per authored hit. All eligible components/victims consume that resolved decision; component damage calculations do not roll another critical. Non-critical-eligible components remain noncritical. The normal RPG scaling/modifier/native Gather–Filter–Apply–Inspect path remains responsible for final damage. Native Health remains the sole Health writer.

For an unmodified deterministic source of 100 Physical + 20 Fire + 10 Cold, each hit supplies 37.5 + 7.5 + 3.75; both hits supply 75 + 15 + 7.5. This is the composition coefficient before character modifiers, criticals, mitigation and native defenses. Individual connected random/critical/armored Health-loss readings should not be compared as if they were a deterministic 75% scalar.

Multi-channel delivery aggregates one authored-hit/victim receipt for generic proc and conditional-repeat admission. This avoids multiplying Fear rolls by component count. Physical bleed uses the Physical portion, not the sum of Fire/Cold. Existing root budgets, target ICDs, Shockwave-once-per-root, actual-Health-loss gates and NoProc/derived restrictions are retained. Source traces now identify the actual profile component and sampled base rather than incorrectly labeling it with the old scalar weapon power.

## Timing and sequence ownership

The reference cycle is the windup + selector/contact window + recovery padding exposed by the installed graph. Quick Slash schedules two child cycles of `T/2`, retaining the contact fraction. Animation starts and gameplay contacts have separate deadlines; hit zero is no longer applied at commit before the authored contact offset.

| Reference | Native T | Child cycle | Contact 1 | Contact 2 | Base sequence end |
| --- | ---: | ---: | ---: | ---: | ---: |
| Iron Sword | 0.334 s | 0.167 s | 0.0585 s | 0.2255 s | 0.334 s |
| Iron Daggers | 0.207 s | 0.1035 s | 0.0345 s | 0.1380 s | 0.207 s |
| Flame Longsword | 0.520 s | 0.260 s | 0.1145 s | 0.3745 s | 0.520 s |

These are authored deadlines; native world ticks quantize actual delivery. No sleeps, native speed overrides or shared interaction mutations are used. RPG-specific animation profiles reuse the existing left/right first- and third-person swing files at exactly twice their original animation speeds. Animation-file length and interaction-cycle padding are different native quantities: this is not a claim that the native animation's last frame coincides exactly with a contact/recovery deadline. Visual blending/contact alignment remains connected QA.

Multistrike still repeats the complete pair using the existing controller: six contacts, two derived 65%-magnitude pair copies, no recursive roots, no additional activation payment/cooldown and no derived Mantle pulses.

The existing world-tick repeat owner is now keyed by **execution identity**, not only player UUID. This is necessary because the new native-reference durations can overlap already-admitted Echo/conditional releases. One derived pair must not overwrite the paid pair or leave its lifecycle stranded. `StrikeSequenceRegistry` bounds storage to 256 globally and seven per actor (one primary plus the existing six-release owner budget), rejects duplicate/overflow insertion without replacing accepted entries, and cancels all owned sequences on teardown. It introduces no new scheduler thread or native combat controller. The action lock remains until the last required owned sequence ends. Normal roots require a live matching lifecycle; separately admitted derived releases retain their existing release-owner authority.

Death/unusable actor, logout/world teardown, incompatible state and changed committed equipment cancel remaining contacts. A canceled schedule cannot later yield a contact or animation. Paid misses and interrupted committed casts do not receive a fabricated refund.

## Mantle and extension seam

Each base Quick Slash hit constructs one complete `WeaponDamageExecution` envelope with `RPG_WEAPON`, direct weapon provenance and its own identity **before iterating victims**. Mantle consumes that envelope through its existing resource/target/conversion owner. Each hit can claim at most one pulse; a five-victim strike does not supply the source five times. Physical/other channels stay direct when eligible Fire is converted. Derived copies retain NoProc and cannot recursively generate Mantle pulses.

The installed Flame Longsword source is Fire 31 ±15%. Before RPG attribute/modifier/critical changes, each Quick Slash hit has mean Fire **11.625**, range approximately **9.88125–13.36875**. Two unmodified means total 23.25. Each hit has its own Mantle source; the renderer/runtime must not merge them into one 23.25-source pulse. Ordinary native Mantle coverage remains limited to its prior verified native paths; no Flame-Longsword-specific branch exists in Quick Slash's resolver/executor. The literal item ID occurs only in the native audit fixture.

`WeaponLightAttackProfile.Provider` is a narrow commit-time component-extension seam. A synthetic test adds authoritative +20 Fire to a 100 Physical weapon, proving the same 37.5/7.5 consumer. No affix mod is implemented and unsupported Flame Weapon is not activated. Its future provenance can be represented without changing Quick Slash.

RPG damage metadata distinguishes these skill-generated weapon hits from ordinary native basics. They do not claim the normal 4%/12% basic-hit recovery. Existing authored Leeching/Lifeblood/Attunement and other explicit passive rules remain authoritative.

## Validation history and boundaries

Focused profile, timing, damage, resource, Mantle, cadence, secondary/proc and passive tests were run during implementation. Named coverage includes Potency, Multistrike, Ruthless, Shockwave, Lifeblood, Attunement, Executioner and Opportunist. New deterministic tests cover component sums/random/crit, provider composition, conditional-profile rejection, commit snapshot, one payment/cooldown, Mantle source deduplication, multi-channel proc coalescing, no stale hit after cancellation, overlapping pair identity and bounded owner admission.

The first full run found a retained Stage04 assertion expecting the **old** 0.85 coefficient. It was updated to the owner's new 0.375 contract without removing payment, cooldown, identity or other assertions. Another intermediate run failed the unchanged `Stage13V2RecoveryEdgesTest.checkpointWorkerBacklogBackpressuresBeforeThirdEpochAndEventuallyProgresses` assertion at line 54. That persistence test/code was not relaxed or changed; its result is retained in the intermediate validation log and must not be concealed by the final run. Subsequent receipts distinguish the final result from that observed intermittent failure.

Final totals, native gates, matrix count, archive/rollback validation and exact hashes are recorded below. No tests were deleted or skipped to obtain a pass. Earlier development native failures are retained in `cohort-as-dev1` through `cohort-as-dev3`; dev4 is an earlier successful candidate audit, not the final JAR. Full reruns were necessary after the retained old-contract assertion and subsequent sequence/feedback-only-angle fixes; only the final run validates the deployed artifact's code.

## Connected QA procedure

Use your **normal RPG single-player world**, not a disposable Direct Connect server. Confirm the top-right badge says **R032-AS** after reopening the game.

Run:

```text
/rpg-trace normal
/rpg equip skill01 quick_slash
/rpg dev ability-status
/give Weapon_Sword_Iron
```

Equip the given sword in hand. Use native Ability2 (**E by default**, unless rebound). Cast into empty space and then into a nearby valid hostile target. Expect two contacts/swing directions and one Stamina charge/cooldown. Compare multiple noncritical trials with ordinary uncharged native Primary attacks on equivalent targets. Source variance and mitigation mean a single HP number is not proof of the 75% composition coefficient.

Repeat with:

```text
/give Weapon_Daggers_Iron
/give Weapon_Longsword_Flame
```

Check the approximate sequence duration against the timing table. Try changing away from the committed weapon during the pair: there must be no stale hit from a replacement weapon. Logout/rejoin and normal death/world transitions must leave no delayed contact or action lock. Existing equipment may make that narrow sub-second test difficult; the automated cancellation tests do not replace observing connected cleanup.

For Mantle:

```text
/rpg equip skill02 mantle_of_flame
/rpg dev ability-status
```

Hold the Flame Longsword, toggle Mantle with native Ability3 (**R by default**) and use Quick Slash with E while appropriate nearby hostiles and Mana are available. Expect separate eligible source/pulse opportunities for the two base hits, never one source multiplied by the number of victims and never more than two base pulses. Check separate Mantle Mana charges through its existing feedback/traces. Toggle Mantle off afterward. If a skill is not learned in this save, use the existing skilltree/acquisition controls rather than editing save files.

For passive QA, use `/rpg skilltree` to link the named passive to Quick Slash. Check Multistrike produces three pairs with one activation cost and that Shockwave remains root-bounded. Repeat a normal native Primary attack afterward: its source, timing, native recovery and prior Mantle path must remain unchanged.

Trace evidence remains under the save's existing `mods/InigmasGames_HytaleRPGPhase00Audit/logs/rpg/skill-trace.jsonl` (plus rotated segments). Look for `STRIKE_HIT` phases `LIGHT_PROFILE_CAPTURED` and `WEAPON_LIGHT_EXECUTION`, separate `.../light-0` and `.../light-1`, component/source Fire amounts, `skillGenerated=true`, `basicRecoveryEligible=false`, native damage phases, and existing Mantle decision/pulse receipts. NORMAL aggregation/rotation is unchanged. `/rpg-trace status` reports active trace mode; do not delete historical traces to make a run look clean.

## Remaining limitations

- All connected casting, damage, rendering, native animation alignment, interruption, Mantle visuals and native-basic regression checks above remain **UNVERIFIED** for AS until the owner tests them.
- Native profiles requiring real conditional calculators, multiple damage executions or unknown conditions are explicitly rejected, never approximated. See final native inventory.
- The existing Stage13 physical-storage/native-tick release-performance qualification and prior shutdown persistence-uncertainty warning are not fixed by this bounded combat correction. Passing local regressions is not a full release qualification.
- Future affix/temporary-imbue providers are a contract seam and tests only, not newly enabled gameplay.

## Final receipts

Final command:

```powershell
.\gradlew.bat -PhealingProbeLiveTest=true :check :canvas-ui:check :jar --rerun-tasks --console=plain
```

Final result: **2,403 passing tests**, zero failures/errors/skips: 2,316 RPG tests + 66 native-control tests + 21 CanvasUI tests. The final profile-specific class contains 40 test invocations. The compatibility generator produced **6,030 single cells (90 skills × 67 passives)** and retained pair/property evidence. The suite also retained crash/recovery, exact-once, escrow, nonblocking persistence, resource and cooldown tests without production changes to those owners.

The unchanged real-storage benchmark in this final run reports p50 **4.5758 ms**, p95 **8.1816 ms**, p99 **29.0211 ms**, versus unchanged 4/8 ms targets: `withinNominalRpgTickBudget=false`. This is not native-tick/connected proof and is not a passed release-performance gate. The intermittent intermediate checkpoint-backlog test failure remains recorded; its final unchanged rerun passed. Neither finding was hidden or resolved through combat changes.

`Run-Stage13CohortSmoke.ps1 -Cohort as -NativeProjectileSpawnAudit` booted the **exact final JAR** with exactly three mods in an isolated world, loaded the pinned native assets, passed the profile audit and retained native projectile/presentation/Mantle gates, and shut down with exit code 0. It logged:

```text
HYTALE_RPG_SETUP revision=R032-AS version=0.0.25 hytale=0.7.0-pre.2 stage=13 combatEnabled=true
RPG_LIGHT_ATTACK_PROFILES result=PASS supported=51 unsupported=2 connectedProof=false
```

This proves the archived binary executes AS code in the isolated native server; it does **not** claim that the owner has rejoined the live world or observed AS combat yet.

`Package-QuickSlashAS.ps1 -Deploy` verified the final smoke hash, full test receipts, every ZIP entry hash, exactly three archived mods, zero removed cumulative JAR entries and a binary **AR → AS → AR** rollback rehearsal outside the live save. There are 52 changed/new packaged entries, including nested-class recompilation metadata. Protected packaged owners passed byte-identity checks against AR: native input; Healing Beam classes/assets; projectile-family classes; native managed weapon-Fire producer and weapon overrides; resource/cooldown classes; support runtime/profile; selected persistence/escrow owners; native-resource HUD document and existing language assets. The shared execution adapter necessarily changes to deliver the new strikes; that does not imply changes to unrelated methods in that class.

Artifacts:

- `evidence/stage-13/cohort-as/artifacts/HyARPG.jar` — SHA-256 `114AFD95E29A4DC49DBAE688EA8D0038680FF56AE1855A1F3AC649185A033E25`.
- `evidence/stage-13/cohort-as/HyARPG-R032-AS-three-mods.zip` — SHA-256 `F98FBF0D7D56482C2E332CD0D9F121E61110B70F7CE9D4DFB0C1714E9A45CFF8`.
- Included CanvasUI SHA-256 `218DFFD40ABBCD57629EC57FC20436169C4AFCCC18B9B5A9F94D67835CBA07B6`; HytaleDevLib SHA-256 `DE01E4BAAF1DAA679CB00E4182AD999DA67ECC49A8533942DE3EA87DA4129230`.

Live deployment:

`C:\Users\Zemio\AppData\Roaming\Hytale\data\pre-release\Saves\RPG\mods\HyARPG.jar`

Before replacement, the script verified Hytale/server processes were stopped and copied the **entire matching live RPG save, including mod data and all installed mods**, to:

`C:\Users\Zemio\OneDrive\Documents\GitHub\Hytale\evidence\stage-13\cohort-as\before\save\20260913T203044Z\RPG`

All **538 files** were SHA-256 verified against the backup. A verified pending copy was atomically replaced into the live JAR location. `Confirm-QuickSlashAS.ps1` independently checked every active mod manifest: **exactly one RPG identity**, matching the new hash. CanvasUI, HytaleDevLib and ImmersiveNPCs are unchanged. Comparison against the full backup found exactly one changed live file (`mods/HyARPG.jar`) and **zero new or deleted save files**. Active traces, NPC data and progression were not cleared or edited.

AR rollback JAR: `evidence/stage-13/cohort-ar/artifacts/HyARPG.jar`, SHA-256 `D424F051F55F9B61F89A84B2C8858192C42A55992A72419E513D79E4C263659D`. A matching pre-replacement copy also exists in the new full save backup. For rollback, close Hytale first and replace only the live JAR with that verified AR artifact; do not restore an older full save over subsequent test progress unless intentionally rolling back state as well.

Receipts: `package-validation.json`, `post-deployment-validation.json`, `server-smoke-summary.json`, `server-smoke.txt`, `full-validation-final.txt`, archived JUnit XML, `native-light-profiles.json`, `stage11-matrix/`, `stage13-hardening/`, and pinned native damage/Serial bytecode under `evidence/stage-13/cohort-as/`.

## Affected source files

- New profile owners: `combat/power/WeaponLightAttackProfile.java`, `WeaponLightAttackProfileResolver.java`, `execution/hytale/NativeWeaponLightProfiles.java`.
- New sampled-hit/sequence owners: `execution/strike/WeaponLightHit.java`, `StrikeSequenceRegistry.java`.
- Integration: `SkillExecutionPort`, `SkillExecutionService`, `RootEffectBudget`, `HytaleSkillExecutionSystem`, `StrikeRepeatSchedule`, `HitProcRuntime`.
- Native asset audit/presentation: `NativeProjectileSpawnAuditCommand`, `NativeStrikeFeedback`, the three `RPG_QuickSlash_*` animation JSONs.
- Content/identity: Quick Slash entries in `rpg/runtime/stage-04-skills.json` and `rpg/catalog/skills.json`; `gradle.properties` revision.
- Tests: new `QuickSlashLightProfileTest`; legitimate old-contract expectations updated in `Stage04ExecutionTest`, `Stage13PlayerFeedbackCorrectionTest`, `Stage13QuickSlashSpeedTest`. Other retained tests were not weakened.
- Tooling/report: AS gates and distinct development labels in the retained smoke runner; new `Package-QuickSlashAS.ps1`, `Confirm-QuickSlashAS.ps1`, and this report. No commits or pushes were performed.
