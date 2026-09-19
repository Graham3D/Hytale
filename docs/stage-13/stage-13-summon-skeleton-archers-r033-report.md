# R033 — Summon Skeleton Archers and projectile-continuation correction

Date: 2026-09-14  
Branch: `RPG`  
Baseline commit: `25b85cb2819351a0d727e33132b2f57013cda024` plus the preserved cumulative working tree  
Hytale target: `0.7.0-pre.2`  
Release status: **IMPLEMENTED / PACKAGED / DEPLOYED / CONNECTED-UNVERIFIED**

R033 adopts numeric-only revision labels. Future revisions increment the number and do not append letter suffixes.

## Scope and outcome

This revision addresses the R032-AW follow-up defects without redesigning the summon skill or the retained Stage 13 systems:

- level-one Skeleton Archer summons retain the existing one-active-summon replacement policy;
- the summoned archer now has explicit owner-follow states and uses native pathfinding toward the owner's current leash point;
- the RPG-owned archer uses a summon-only native role whose inherited close-range flee distance is zero, while vanilla `Skeleton_Archer` remains untouched;
- summon-arrow processing is deferred through a bounded world-thread queue so a native damage callback cannot re-enter ECS mutation or invoke RPG arrow conversion with a null `CommandBuffer`;
- Chain and Fork use the shared RPG projectile-continuation runtime and create real tracked projectile descendants;
- summon descendants preserve caster, summon, source-skill, root, attack and parent-projectile lineage;
- the revision identifier is now `R033`, with no letter suffix.

No gameplay formula, persistence contract, HUD ownership, resource rule or unrelated skill behavior was intentionally changed in this correction.

## Connected evidence reviewed

The pre-correction connected server log `2026-09-14_15-28-26_server.log` contained the concrete world-thread failure:

```text
NullPointerException: Cannot invoke DamageCause.getId() because damageCause is null
HytaleSkillExecutionSystem.lambda$configureSummons$0(...:162)
HytaleSummonSystem$DamageGuard.lambda$handle$0(...:389)
CommandBuffer.consume(...:536)
```

This occurred when the summoned archer attacked. The failure ran on `WorldThread - flat_world`, removed that world exceptionally, and matches the user's black-screen/embedded-player symptom. The damaged boundary was not native bow presentation; it was the synchronous summon-arrow conversion callback. It crossed from a native damage guard into RPG continuation work while the store was processing and supplied no valid command buffer.

Two later R032-AW sessions also logged:

```text
RPG_ENCOUNTER_FAILURE boundary=NATIVE_CONTRIBUTION_INSPECT error=NoSuchElementException
ENCOUNTER_PERSISTENCE_UNCERTAIN_RESTART_REQUIRED
```

Those encounter/persistence errors are recorded as a separate known issue. They were not used to broaden this summon/projectile correction and are not claimed fixed by R033.

## Native asset/API audit

The implementation was checked against the installed Hytale `0.7.0-pre.2` assets.

- The shipped `Skeleton_Archer` derives from `Template_Intelligent`, uses appearance `Skeleton_Archer`, the rusty iron shortbow and `Skeleton_Archer_Bow_Shoot`.
- The shipped hostile role authors a close-range flee distance of 4 m and does not back off after an attack.
- `Template_Intelligent` supplies the audited zero/default close-range flee behavior needed by the summon-only variant.
- Native `ReturnHome` behavior paths toward the saved leash point after the role's leash threshold is crossed.
- `Arrow_Iron` is a shipped renderable projectile model and is reused for RPG-owned summon continuation children.
- The Skeleton model retains its native `Spawn` animation set; spawn lifecycle remains owned by Hytale's NPC spawn path.

The RPG role derives directly from `Template_Intelligent` rather than from the shipped `Skeleton_Archer` role because the attempted child-role overrides exposed private parameters. This keeps the override narrow and avoids modifying the vanilla role.

## Implementation details

### Owner following

`HytaleSummonSystem` now maintains explicit bounded state for each projected summon:

- `IDLE_NEAR_OWNER` while inside the follow threshold;
- `FOLLOW_OWNER` beyond 8 m, updating the native leash point to the owner's authoritative position and entering native `ReturnHome` pathing;
- return to `IDLE_NEAR_OWNER` at approximately 6 m;
- `COMBAT` while a valid hostile is engaged.

Ordinary follow never writes the summon transform to the owner transform. At an extreme 64 m separation, the existing safe summon recovery/termination policy is used instead of leaving an orphan or performing an ordinary teleport.

### Aggression without changing vanilla NPCs

`RPG_Summon_Skeleton_Archer` is an RPG-only role. It reuses the native skeleton appearance, bow and attack presentation while inheriting the intelligent template's zero close-range flee distance. Hostile acquisition enters native `Combat`; friendly/owner candidates remain rejected by the existing attitude policy. `/npc spawn Skeleton_Archer` continues to resolve the unmodified shipped role.

### World-thread crash correction

Native archer hits are now converted through a bounded immutable queue:

- maximum queued hit records: 256;
- maximum records drained per summon tick: 64;
- overload is rejected before RPG state mutation;
- the native hit is claimed once, then UUID/lineage data is queued;
- the world tick drains the record with its valid `CommandBuffer`;
- owner, summon lease, target and hostility are revalidated before continuation work;
- a conversion failure quarantines/removes the summon through the valid buffer instead of unwinding the world thread.

No Hytale ECS/world object is retained in the queue.

### Generic visible Chain and Fork descendants

The previous summon-specific instantaneous Chain/Fork payload path is no longer used. The obsolete `SummonArrowContinuations` helper was removed so player and summon projectiles share one authoritative continuation engine.

Chain behavior:

- resolves the original contact normally;
- deterministically selects an unvisited legal target;
- registers a new child plan at the impact location;
- spawns a visible `Arrow_Iron` carrier for summon-arrow descendants;
- performs child travel and collision before damage;
- carries the visited-target ledger and remaining budgets forward;
- applies the 30% less-Hit factor once to the supported projectile sequence rather than compounding it per hop.

Fork behavior:

- preserves the original contact damage;
- terminates/replaces the parent after the contact;
- creates exactly two tracked child plans;
- gives each child an independent carrier/travel/contact lifecycle;
- applies a 0.65 magnitude factor to each child;
- clears the child's Fork budget to prevent recursive Fork.

Fork-to-Chain remains legal where the compiled plan permits it. The implementation is shared by ordinary RPG projectiles and summon-arrow proxy contexts.

### Provenance and traceability

Projectile lifecycle records now include summon lineage where applicable:

- root/cast ID;
- summon ID;
- attack ID;
- parent and child projectile IDs;
- continuation type;
- source victim and selected destination/trajectory;
- remaining continuation budgets;
- effect factor;
- contact/termination/cancellation reason.

`SUMMON_ARROW_CONTINUATION` mirrors actual spawn, contact and termination lifecycle rather than logging an immediate synthetic damage request. Proxy projectile completion does not terminate the root summon skill context.

## Assets and principal files

Principal production changes include:

- `src/main/java/com/inigmasgames/hytalerpg/execution/hytale/HytaleSummonSystem.java`
- `src/main/java/com/inigmasgames/hytalerpg/execution/hytale/HytaleSkillExecutionSystem.java`
- `src/main/java/com/inigmasgames/hytalerpg/execution/hytale/SummonProjection.java`
- `src/main/java/com/inigmasgames/hytalerpg/execution/projectile/ProjectileContinuation.java`
- `src/main/java/com/inigmasgames/hytalerpg/execution/projectile/ProjectileContinuationBalance.java`
- `src/main/java/com/inigmasgames/hytalerpg/execution/summon/SummonProfile.java`
- `src/main/resources/Server/NPC/Roles/RPG/RPG_Summon_Skeleton_Archer.json`
- `src/main/resources/Server/ProjectileConfigs/RPG/Projectile_Config_RPG_Summon_Arrow.json`
- `src/main/resources/rpg/runtime/stage-10-summons-cohort-h.json`
- `src/main/java/com/inigmasgames/hytalerpg/phase00/Phase00Plugin.java`
- `tools/Run-Stage13CohortSmoke.ps1`
- `gradle.properties`

Tests were extended in `SummonSkeletonArchersTest`, `Stage07ContinuationTest` and `Stage07SecondaryTest`.

## Automated validation

Focused continuation/summon tests passed. The exact final source state then passed:

```powershell
.\gradlew.bat clean test nativeControlTest
```

Result: `BUILD SUCCESSFUL`.

| Suite | Tests | Failures/errors |
|---|---:|---:|
| RPG retained suite | 2,331 | 0 |
| Native-control suite | 66 | 0 |
| CanvasUI retained suite | 21 | 0 |
| Total | 2,418 | 0 |

CustomUI validation also passed. Compilation retains existing deprecation warnings against Hytale APIs; no new compilation failure was accepted.

## Isolated three-mod smoke

The exact release candidate passed the established isolated smoke with exactly:

- `HyARPG.jar`
- `CanvasUI-0.1.0.jar`
- `HYTALEDEVLIB-0.5.0.jar`

Accepted evidence: `evidence/stage-13/cohort-r033-final3/`.

All reported smoke gates were true, including plugin setup/enable, RPG manager startup, network boot, native bridge/rune assets, summon assets, projectile assets and clean shutdown. Startup logged:

```text
RPG_STAGE10_SKELETON_SUMMON role=RPG_Summon_Skeleton_Archer fleeDistance=0 followLeash=20 projectile=Projectile_Config_RPG_Summon_Arrow result=PASS connectedProof=false
```

Two earlier candidates were rejected and retained as evidence:

1. direct inheritance from the native role failed because `CombatBackOffAfterAttack` is private;
2. explicitly authoring a zero range produced native role validation errors requiring `Range > 0`.

The smoke checker was strengthened to treat `[NPC|P] FAIL:` as a failure boundary so those native role errors cannot be hidden by a successful process exit.

## Packaging and deployment

Final RPG JAR:

```text
C:\Users\Zemio\OneDrive\Documents\GitHub\Hytale\evidence\stage-13\revision-r033\package\HyARPG.jar
SHA-256 C00B091B452CCC0A500C51FCB85C3EA28CF760F0B1E32ABBF91D12393466030A
```

Three-mod archive:

```text
C:\Users\Zemio\OneDrive\Documents\GitHub\Hytale\evidence\stage-13\revision-r033\package\HyARPG-R033-three-mods.zip
SHA-256 9DD7DF0AD2F6FF64DF9A5F9ADD370A2C310FCA7BF69CB6A192758853D366A948
```

The archive contains exactly three root entries. Supporting hashes:

```text
CanvasUI-0.1.0.jar     218DFFD40ABBCD57629EC57FC20436169C4AFCCC18B9B5A9F94D67835CBA07B6
HYTALEDEVLIB-0.5.0.jar DE01E4BAAF1DAA679CB00E4182AD999DA67ECC49A8533942DE3EA87DA4129230
```

Live deployment:

```text
C:\Users\Zemio\AppData\Roaming\Hytale\data\pre-release\Saves\RPG\mods\HyARPG.jar
SHA-256 C00B091B452CCC0A500C51FCB85C3EA28CF760F0B1E32ABBF91D12393466030A
```

The live JAR's embedded properties resolve `rpg.revision=R033`, `rpg.version=0.0.25`, Stage 13 and Hytale `0.7.0-pre.2`. Only one RPG JAR is installed. The new role and projectile config are both present in that installed JAR.

Before replacement, the previous live JAR and RPG mod-data/player state were copied to:

```text
C:\Users\Zemio\OneDrive\Documents\GitHub\Hytale\evidence\stage-13\revision-r033\live-backup-20260914-185802
```

Previous live JAR SHA-256:

```text
48370E917124398AFEF3713708DF72F15908C3C61FB00E961851DAB062FDD54C
```

Hytale was not running during replacement. Deployment used a separately hashed pending copy followed by an atomic file replacement. No live save data was edited.

## Connected QA checklist

Connected verification remains required. After starting the RPG save, first confirm the top-right revision reads `R033`.

1. Equip `Summon Skeleton Archers` at effective level 1 and cast once. Confirm exactly one archer emerges with the native spawn presentation.
2. Recast. Confirm the old archer is removed and exactly one replacement remains.
3. With no hostiles nearby, walk more than approximately 8 m from the archer. Confirm it paths toward the player and settles near 6 m without teleporting.
4. Repeat around obstacles and at longer distance. Confirm no player displacement, black screen or world crash.
5. Spawn a hostile melee NPC near the summon. Confirm the archer enters combat, uses its native bow and does not flee indefinitely. Repeat with multiple hostiles.
6. Spawn an ordinary vanilla `/npc spawn Skeleton_Archer` and confirm its behavior remains unchanged.
7. Link Chain to `Summon Skeleton Archers`; arrange three hostiles. Confirm the native arrow hits A, then a visible arrow travels A→B and another B→C. Confirm no target repeats.
8. Link Fork; confirm the original hit resolves and exactly two visible child arrows emerge and travel independently.
9. Link Fork then Chain together and confirm descendants remain bounded, visible and nonrecursive.
10. Repeat Chain and Fork on one compatible player projectile skill to prove the continuation correction is generic.
11. Review `skill-trace.jsonl` for `SUMMON_FOLLOW_STATE`, `SUMMON_ARROW_CONTINUATION`, projectile spawn/contact/termination and matching root/attack/summon lineage.

Expected follow trace transition:

```text
IDLE_NEAR_OWNER -> FOLLOW_OWNER -> IDLE_NEAR_OWNER
```

## Remaining limitations and rollback

- Native navigation, aggression, visible child projectile motion and crash elimination are not claimed connected-verified until the checklist above is run.
- The separately observed encounter-inspection/persistence-uncertainty errors remain open evidence and may require a bounded follow-up if they recur on R033.
- Existing Hytale deprecation warnings remain; they did not block compilation or smoke startup.
- No branch push or commit was performed.

Rollback requires Hytale to be closed. Restore the backed-up `HyARPG.jar` from the backup directory above to the live mods directory. The backup also retains the pre-deployment RPG mod-data and player-state copies for forensic recovery; they should only be restored if an actual state rollback is required.
