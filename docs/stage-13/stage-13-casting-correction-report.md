# Stage 13 L connected casting defects — correction M

Date: 2026-09-09. Branch: `RPG`. Baseline: `24efcdfd203e209cae5b34ec4d0d34f19d02e7f6` (documentation checkpoint over deployed L implementation `cd7cdbd`). Build identity remains **R032 / 0.0.25**; distinguish this correction by **cohort M and SHA256**, not filename.

Status: **IMPLEMENTED / RETAINED_VALIDATION_PASS / DEPLOYED_FOR_CONNECTED_RETEST**. **Connected casting after this correction: NOT_RUN. Stage 13 production acceptance is not PASS.** The owner previously confirmed L loads; that statement does not prove M's input, animation, native projectiles, or damage.

## 1. Connected evidence and earliest failures

Reviewed the current server log `2026-09-09_16-32-27_server.log`, plus the existing `skill-trace.jsonl` and `ui-trace.jsonl` under `Saves/RPG/mods/InigmasGames_HytaleRPGPhase00Audit/logs/rpg`. Source hashes, timestamps, limited inventory evidence, and installed asset metadata are retained in [connected-source-audit.json](../../evidence/stage-13/cohort-m/connected-source-audit.json). The relevant correlated events are in [connected-casting-excerpt.jsonl](../../evidence/stage-13/cohort-m/connected-casting-excerpt.jsonl). No full character save was copied into this report packet.

Across the selected September 9 records there are 12 native ability input observations, 8 preparation-failure records, and 6 empty-target-failure records. Rejections emit both `SKILL_VALIDATION_REJECTED` and `SKILL_ACTIVATION_REJECTED`: those counts represent **four preparation-failed casts and three empty-target-rejected casts**, not fourteen separate inputs. UI trace has nine records (open/close/layout/XP/teardown), not a preparation exception stack. The server's console trace is rate-limited; JSONL is the detailed source.

Latest Fire Bolt example, 20:32:57 UTC:

```text
correlationId = 7887755b-4cc8-4524-a357-35b0f37ed003
rootCastId = input-9-7887755b
skillInstanceId = activation-804c6bb1-5def-48fe-b738-ccc8a846affa

NATIVE_ABILITY_INPUT_OBSERVED  Ability3 / skill02 / NATIVE_EXECUTION_MAPPED
SKILL_ACTIVATION_REQUEST     MANUAL
SKILL_VALIDATION_PASS        fire_bolt / PROJECTILE / windup 0
SKILL_VALIDATION_REJECTED    COMMIT_PREPARATION_FAILED_IllegalArgumentException
SKILL_ACTIVATION_REJECTED    COMMIT_PREPARATION_FAILED_IllegalArgumentException
```

There is no commit/dispatch/projectile event for this root. This is evidence **against blaming the ability bridge or projectile executor for this failure**. Quick Slash's latest attempts similarly reach RPG activation but reject `NO_VALID_TARGET`.

Installed reference remains Hytale `0.7.0-pre.1`:

- Server JAR SHA256: `EC57E9BD6E2CA3CB16CC5883D42B04A0C64D382DEE532C5BC1CFCF68421E1EE3`.
- Assets ZIP SHA256: `46F6AA12DECF4F900FCFDF28ECC67568403C37A3AD0A03A4E4CF7653A324AE39`.

## 2. Manual targeting contract

### Root cause

`HytaleSkillExecutionSystem.Port.familyPrerequisites()` performed a legitimate bounded strike query for admission, then performed another query and rejected an empty accepted set unless the strike was a finisher. A matching `validateRelease()` check rejected `COMMITTED_STRIKE_EMPTY` after payment for delayed strikes. Both confused **resolving entities hit by a spatial attack** with **authorizing the cast**.

`executeStrikeHit()` already iterates only accepted geometric intersections, and `executeStrike()` already presents its VFX and returns `STRIKE_COMPLETE` with zero applied targets when the query is empty. No fake target, fake hit, free retry or damage compensation was needed.

### Correction

- Added `StrikeCastPrerequisites.check()` as the shared initial/release admission rule. Empty geometry passes. The existing `STRIKE_QUERY_OVERFLOW` and `STRIKE_TARGET_CAP_OVERFLOW` limits still reject, and unrelated world errors are not swallowed.
- Removed the generic initial empty-strike rejection and post-commit empty-strike cancellation. Geometry is resolved again at execution using the existing strike selector. A zero-hit cast pays the normal cost and cooldown.
- Preserved equipment, actor state, native assets, capability gates, terrain/aim/path requirements, boundedness, status rules and authored geometry.
- Retained explicit target checks for target-directed Pounce, entity-targeted connections/support, conversion, corpse skills and owned-minion consumption. Pounce's authored description is a leap toward a target, unlike ordinary empty-space swings or aim-launched projectiles.

### World/aim context omission found at the same boundary

Direct code inspection exposed the next deterministic obstacle: `validateDurableCompletion()` requires a non-null committed world context, but the coordinator previously captured one only for scheduled/repeat/specific families. Plain Quick Slash and Fire Bolt therefore had `context.target() == null` even though native completion demanded a world ID.

`SkillExecutionPort.requiresSpatialCommitContext()` now lets the native adapter require the already-existing `CommittedTarget` capture for immediate casts too. Native capture stores world UUID, origin, aim/point and an **optional** entity UUID; ordinary swings and bolts need no entity. Existing world-change rejection remains intact. Self-armed reactions pass their family release branch only after the same world/equipment/state checks. This is a bounded context-contract correction, not a persistence redesign: no queue, durable ordering, escrow, resource or cooldown protocol changed.

### Audit of all 87 profiles

[target-contract-87-profiles.csv](../../evidence/stage-13/cohort-m/target-contract-87-profiles.csv) lists every unique runtime skill, source profile file, family, targeting disposition, authored/native prerequisites, explicit activation gate and connected retest status. The audit followed the shared native adapter's dispatch order and delegated support/summon/conversion preflights.

- Strike geometry, aim-launched projectiles, cones, lines, radial/ground/overhead areas and ordinary movement do not acquire a mandatory enemy at initial admission.
- Spatial profiles still require appropriate legal terrain, muzzle/path clearance, loaded world state, native assets and capacity where authored. “Targetless” is not permission to cast through invalid world state.
- Tether/Chain/Drain retain `ConnectionProfile.requiresTarget()` and real `ConnectionTargeting` selection.
- Hostile support, eligible conversion, corpse authority and owned-summon consumer checks are unchanged. Heal/shield preserve the existing valid-recipient/self-fallback policy; no global null-target bypass was added.
- Bone Cage, Frenzy, Guard and Snipe remain explicitly capability-gated. A targeting audit does not declare them playable.

This is a source/profile audit plus local regression evidence, **not 87 connected skill passes**.

## 3. Fire Bolt preparation exception

### Exact reproduced exception

The saved native hotbar from the end of the latest connected session has active zero-based slot 5 containing `Weapon_Staff_Mithril`. Its installed item asset has raw `Family=[Staff]`, `Type=[Weapon]`. It has **no entry** in the 15-item `native-item-power-r032.json` registry and no approved RPG MagicPower.

The production path was:

```text
HytaleEquipmentAdapter: exact registry lookup misses
  -> raw Family Staff classifies as STAFF
  -> ItemPowerDescriptor(RPG_WEAPON_MAGIC, weaponPower=null, magicPower=null)
SkillExecutionService.validateEquipment: STAFF is allowed for Fire Bolt
commitAndDispatch -> resolvePower -> BasePowerResolver.resolve(MAGIC_WEAPON)
  -> IllegalArgumentException("Item has no authored MagicPower: Weapon_Staff_Mithril")
```

The new regression invokes the **same production equipment description and BasePowerResolver with the actual installed asset tags**, asserts that exact exception text, then verifies the corrected coordinator rejects before payment or cooldown submission.

Evidence limitation: L's old trace retained only the exception class, not its message or item-at-cast. The session-end inventory plus deterministic production reproduction strongly identifies this failure mechanism; it is **not** a recovered historical stack trace and cannot prove every earlier failed cast used that item.

### Supported magic weapon records verified, not rebalanced

| Item ID | Installed raw Family / Type | Registry kind | Approved MagicPower | Source policy |
|---|---|---|---:|---|
| `Weapon_Staff_Crystal_Flame` | Magic / Weapon | STAFF | 10 | Exact uncharged `Fireball_Impact_0/.../EntityDamage` value |
| `Weapon_Staff_Crystal_Ice` | Staff / Weapon | STAFF | 20 | Existing `RPG_AUTHORED_BASE` |
| `Weapon_Wand_Wood` | Wand / Weapon | WAND | 20 | Existing `RPG_AUTHORED_BASE` |
| `Weapon_Staff_Mithril` | Staff / Weapon | STAFF classification only | **Unavailable** | No approved record; remains unsupported |

The Flame staff's native `Magic` Family exception is already bound to its exact item ID; the actual installed tags resolve successfully. Ice staff and Wooden Wand resolve their existing explicit authored bases. No registry values, item-name heuristics, damage averages or new arbitrary default powers were introduced. Mithril's native summon interaction and referenced melee damage are not authorization to invent RPG magic power.

### Correction and diagnostics

- `HytaleEquipmentAdapter.describe(itemId, rawTags)` extracts the existing native-inventory resolution into one directly testable production entry point; resolution semantics are unchanged.
- Weapon-backed skills now resolve their audited power as part of equipment validation, before `SKILL_VALIDATION_PASS`, resource reservation, cooldown or execution. Missing authored power rejects `EQUIPMENT_POWER_UNAVAILABLE`. Commit still resolves its actual snapshot power as before.
- Added `SKILL_PREPARATION_FAILED`, retaining correlation/root/instance IDs, with a bounded stage, failure code/type, allowlisted safe message, skill/source/item/kind, and power-presence flags.
- Commit preparation identifies attribute snapshot, power resolution, cooldown preparation, modifier construction, mastery, summon modifiers, snapshot/context construction, leech budget and persistence preparation.
- Arbitrary exception text, stack traces, paths and inventory metadata are not serialized. Invalid or oversized token fields are replaced with `OMITTED`; messages are fixed allowlisted descriptions. Existing rollback/rejection behavior is preserved; exceptions are not treated as success.

For Mithril the diagnostic is `EQUIPMENT_POWER_VALIDATION / MISSING_AUTHORED_MAGIC_POWER`, item `Weapon_Staff_Mithril`. **Mithril is not newly supported by this correction. Use one of the audited magic weapons for the connected positive test.** The same fail-closed rule applies to other unregistered weapon tiers, including an unaudited sword whose family happens to be allowed.

## 4. Changed files and protected boundaries

Production files, under `src/main/java/com/inigmasgames/hytalerpg/`:

| File | Bounded purpose |
|---|---|
| `execution/hytale/HytaleSkillExecutionSystem.java` | Shared empty-strike admission/release; native spatial context requirement; self-reaction release branch |
| `execution/strike/StrikeCastPrerequisites.java` | Empty-query-legal admission, retaining overload guards |
| `execution/SkillExecutionPort.java` | Native world/aim context capability |
| `execution/hytale/HytaleEquipmentAdapter.java` | Extract existing exact-ID/raw-tag description for native reads and tests |
| `execution/SkillExecutionService.java` | Early audited power validation, required native capture, staged preparation diagnostics |
| `execution/PreparationFailureDiagnostics.java` | Bounded allowlisted failure details |
| `diagnostics/RpgTraceEventType.java` | New diagnostic event |

Added `Stage13ConnectedCastingCorrectionTest.java`. No retained test file was changed or deleted. Existing smoke/publisher scripts were extended for cohort M; added source-audit and package-verification scripts. No Stage 04/05 executor implementation, projectile simulation/payload mechanics, native ability projection, native HUD/resource ownership, XP art, gameplay balance, persistent data format, durable ordering or shield escrow implementation changed.

[jar-differential.json](../../evidence/stage-13/cohort-m/jar-differential.json) verifies only classes belonging to the listed casting/diagnostic owners changed. All other entries, including all resources, balance/weapon registry, HUD, ability bridge and persistence classes, are byte-identical to L. Nested-class byte changes include recompiled line-number/debug information and enclosing-class references; they do not indicate changes to each nested system's mechanics.

## 5. Validation

Focused runs used the new correction tests, then the retained strike, projectile, native equipment, connection, finite support, conversion, corpse and consumption tests. Both focused runs passed. The coherent candidate then received **one full retained build/validation run**:

```powershell
.\gradlew.bat :test :nativeControlTest :canvas-ui:test build --rerun-tasks --console=plain
```

Result: **2,106 tests, zero failures, zero errors, zero skipped**: 2,054 root, 31 isolated native, 21 CanvasUI. The new fixture contributes 13 cases (including three supported-weapon parameter cases); all 2,093 L case identities are retained. No assertions or expected behavior were weakened. CustomUI validation passed for all 32 documents. Existing deprecation/native-access build warnings remain nonfatal.

New coverage includes:

- Quick Slash empty geometry reaches commit/dispatch with one paid zero-hit swing; wrong weapon and insufficient Stamina reject normally.
- Initial and delayed strike admission accept an empty query; overload and unrelated-error behavior remains enforced.
- Each supported installed staff/wand resolves actual raw tags, commits Fire Bolt, builds a real production projectile plan and registers a projectile lifecycle without an entity target.
- Wrong equipment/resource/cooldown rejection; one resource write and one cooldown save; expiry/duplicate termination does not refund, add damage or award mastery.
- Pending durability does not dispatch early; the saved spatial world anchor survives completion; changing worlds rejects without dispatch.
- Every authored strike geometry accepts zero candidates; all three intrinsically targeted connection profiles reject missing targets through the real coordinator/selector.
- Exact Mithril missing-power reproduction, early no-payment rejection, correlated diagnostics, and injected preparation exception redaction/stage reporting.
- Structural native wiring checks protect the production admission/capture hooks and retained intrinsic target guards.

Local world ports model bounded world observations; they **do not spawn an actual Hytale client-visible carrier or run an in-game swing**. Those observations remain connected gates. The existing archived-reader rollback, journal crash/fault, exact-once, escrow, native handoff and resource regressions passed unchanged in the complete run.

Retained real-storage diagnostic: 60 samples of 64 updates (3,840 total), restored contributor counts true. [Measured output](../../evidence/stage-13/cohort-m/durability-load.json): p50 **4.5438 ms**, p95 **8.1949 ms**, p99 **17.5146 ms**. Its existing `withinNominalRpgTickBudget` remains **false**, not converted to PASS. Journal-force p95 was 2.1603 ms; this does not prove native tick latency. Connected native 4 ms p95 / 8 ms p99 qualification remains outstanding. No threshold or timing test was altered, and this casting correction did not pursue storage optimization.

Isolated three-mod smoke: exact candidate, offline loopback ephemeral port, successful asset resolution/startup/network boot/clean shutdown, exit 0, every retained cohort audit enabled. No live world was used for smoke. Package verification hashed each of exactly three ZIP entries, compared L/M JAR entries, and exercised an isolated atomic L rollback and M roll-forward.

Evidence: [full validation](../../evidence/stage-13/cohort-m/full-validation.txt), [test case inventory](../../evidence/stage-13/cohort-m/test-results.json), [new test XML](../../evidence/stage-13/cohort-m/TEST-com.inigmasgames.hytalerpg.Stage13ConnectedCastingCorrectionTest.xml), [smoke](../../evidence/stage-13/cohort-m/server-smoke-summary.json), [deployment](../../evidence/stage-13/cohort-m/casting-correction.json).

## 6. Package, deployment and rollback

| Artifact | SHA256 |
|---|---|
| [HytaleRPG-0.0.25.jar](../../evidence/stage-13/cohort-m/artifacts/HytaleRPG-0.0.25.jar) | `AABF71F03C31014A092C471C45965013DBD3FE491D50E17AB817148ACBA1D410` |
| [Three-mod test ZIP](../../evidence/stage-13/cohort-m/Hytale-RPG-Stage13-M-casting-correction.zip) | `280E60C38E1CFEFBB64C721708F589AA10A7DF11FB1160A08C1EE29090BA179B` |
| CanvasUI-0.1.0.jar, unchanged | `218DFFD40ABBCD57629EC57FC20436169C4AFCCC18B9B5A9F94D67835CBA07B6` |
| HYTALEDEVLIB-0.5.0.jar, unchanged | `DE01E4BAAF1DAA679CB00E4182AD999DA67ECC49A8533942DE3EA87DA4129230` |
| [Rollback L RPG JAR](../../evidence/stage-13/cohort-m/rollback/HytaleRPG-0.0.25.jar) | `1E4A5CAA1344CE71C8701EBCFBFCB3883BD288DC90BE9732066338A5F6A5B0F6` |

Deployed at 21:09:13 UTC, with no Hytale server process running:

```text
C:\Users\Zemio\AppData\Roaming\Hytale\data\pre-release\Saves\RPG\mods\HytaleRPG-0.0.25.jar
```

Only the RPG JAR was replaced, using a hashed staged file and atomic replacement. The two support JARs remained untouched. The folder contains exactly three JARs with the package hashes. All 17 non-JAR mod-data files matched before/after; no save/character/world data was edited, migrated or reset. All new files are in the GitHub checkout, not Google Drive. Owner `art/` is untouched.

To roll back, fully stop the world/server and replace only the deployed RPG JAR with the linked L backup. L contains the known casting defects; rollback is recovery, not a claim that those defects disappear. No save rollback is required because no format changed. Do not copy a stale save over current progress.

## 7. Minimal connected QA checklist

Use the existing RPG test world. Keep each test separate and note the time. **Restart Hytale/rejoin first** so the new JAR loads. Do not use a generic `/rpg dev cast` substitute for native ability input.

1. Run `/rpg loadout`, `/rpg dev ability-status`, `/rpg stats`. In `/rpg skilltree`, equip Quick Slash in `skill01` and Fire Bolt in `skill02`. Confirm native Ability2/Ability3 projection. For a clean baseline, use no linked passives affecting these skills; record/restore your previous links afterward. Do not wipe character data.
2. **Quick Slash positive:** hold audited `Weapon_Sword_Iron` or `Weapon_Longsword_Iron` in the active hotbar slot, face empty space, and press the actual Ability2 binding once (E if still bound that way). Require a visible swing/feedback without an enemy, one `SKILL_COMMITTED`, one `EXECUTOR_DISPATCH`, and `STRIKE_QUERY.acceptedCount=0`. No damage or mastery event should be fabricated. Base profile is 5 Stamina / 0.8 s cooldown; compiled modifiers/attributes can change the displayed result.
3. **Fire Bolt positives:** obtain the exact supported items through the game's inventory interface, one at a time: `Weapon_Staff_Crystal_Flame`, `Weapon_Staff_Crystal_Ice`, `Weapon_Wand_Wood`. With each in hand, face open air and press Ability3 once (R if still bound). Require a visible projectile plus `PROJECTILE_SPAWN_REQUEST` and `PROJECTILE_SPAWNED`. Let one expire without contact and fire another at nearby terrain. Require termination without damage/mastery/refund. Base profile is 8 Mana / 1.4 s cooldown, before compiled modifiers/attributes.
4. For each positive cast, correlate `NATIVE_ABILITY_INPUT_OBSERVED -> SKILL_ACTIVATION_REQUEST -> SKILL_VALIDATION_PASS -> SKILL_COMMITTED -> EXECUTOR_DISPATCH` by root/instance/correlation. Native input observation precedes creation of the RPG root; subsequent RPG records carry its IDs. Check exactly one commit and normal cooldown/resource behavior. `SKILL_COMMITTED` contains the committed cost/type/cooldown/charges. **Do not demand a separate generic `RESOURCE_COMMIT` or `COOLDOWN_STARTED` event from this path: those events are not emitted for every ordinary cast.** Native regeneration can affect before/after `/rpg stats` readings.
5. **Negative guards:** hold a sword and try Fire Bolt; hold a staff and try Quick Slash. Require `INVALID_MAIN_HAND` and no commit. Press a valid ability twice rapidly; any second input that reaches RPG during its cooldown must reject `COOLDOWN_ACTIVE`, with no second paid execution. Native input can suppress cooldown presses locally; absence of a second RPG event alone does not prove this guard ran.
6. **Unsupported item check:** hold `Weapon_Staff_Mithril`, try Fire Bolt once. Expected: `EQUIPMENT_POWER_UNAVAILABLE`, diagnostic `MISSING_AUTHORED_MAGIC_POWER`, no payment/commit/spawn. Mithril is not an audited positive-test weapon.
7. **Insufficient resource:** use gameplay spending to get the appropriate pool below the compiled cost, then press the skill immediately. Require `INSUFFICIENT_RESOURCE` and no commit. Development-only exact command syntax, if needed, is `/rpg dev resource mana spend <amount>` or `/rpg dev resource stamina spend <amount>`; choose an affordable amount from `/rpg stats`. Native regeneration makes manual timing nondeterministic, so repeat only if the trace shows the pool was already replenished. Do not disable regeneration or edit saves for this test.
8. **Real hits:** once empty casts pass, test a legal hostile entity inside Quick Slash's arc and in Fire Bolt's trajectory. Require actual native Health loss with normal damage phases and no duplicate RPG/native damage. Do not label a visible projectile alone as damage proof.
9. Stop/rejoin normally, then rerun `/rpg loadout` and `/rpg dev ability-status`. Confirm skills/links/progress persist and native Health/Mana/Stamina/HUD ownership remains unchanged. Restore any deliberately changed test loadout/links.

If anything fails, send the new server log, skill/UI JSONL, approximate time, exact held item ID and observed behavior. For preparation failures, preserve `SKILL_PREPARATION_FAILED` and its stage/code rather than inferring a projectile problem. No connected success is claimed until those observations exist. Remaining native tick, ability-specific capability, animation, collision, hit and restart integration gates stay unverified; this correction does not begin another stage or architecture redesign.
