# R034 — Skeleton Archer authoritative arrow damage correction

Date: 2026-09-14  
Branch: `RPG`  
Stage: 13 connected-QA correction  
Connected status: **REQUIRES OWNER QA**

## Scope and evidence

This revision implements both owner-supplied tasks: the R033 connected arrow-damage regression correction and the canonical Skeleton Archer base-damage contract. It does not redesign summon navigation, native input, player skills, persistence, rewards, HUD or unrelated Stage 13 systems.

The connected R033 trace established the earliest failing boundary:

- the native Skeleton Archer arrow contacted a hostile target;
- summon ownership was recovered and native damage was suppressed intentionally;
- the queued RPG parent payload reached Gather, Filter, Apply and Inspect;
- the old fixture calculated `1.0 * 1.03 * 0.385 = 0.39655` Physical;
- ordinary contacts resolved to zero native Health loss while critical contacts at `0.594825` resolved to one;
- 27 parent contacts produced 24 zero-loss results and three one-Health critical results;
- 54 Fork descendants were created, but none contacted an entity and at least 52 immediately contacted terrain.

This classified the primary regression as `ZERO_MAGNITUDE`, not missing contact, missing submission or missing combat text. It also exposed a separate ground-level descendant-origin defect.

## Implemented damage authority

`Summon Skeleton Archers` now uses:

```text
snapshottedMagicPower = committed magic weapon base power * committed Magic multiplier
baseArrowPhysicalDamage = snapshottedMagicPower * 0.35
```

The skill profile now resolves `MAGIC_WEAPON` power and retains `MAGIC` scaling. The result is submitted as `DamageCause.PHYSICAL` through the existing RPG `HytaleDamageAdapter` and native `DamageSystems.executeDamage` lifecycle. The hostile vanilla Skeleton Archer's own raw damage remains presentation/contact input only and never becomes RPG magnitude authority.

Each summon lease stores immutable:

- snapshotted Magic Power;
- canonical base coefficient `0.35`;
- summon-passive magnitude factor;
- resulting per-summon coefficient.

Invalid or missing Magic Power, a non-magic source, a nonpositive coefficient, a coefficient other than `0.35`, or a non-Physical arrow cause now fails explicitly. There is no zero- or one-damage fallback.

Skill level continues to change summon count only:

```text
count = 1 + floor((EffectiveSkillLevel - 1) / 2)
```

The per-arrow `0.35` coefficient does not increase with level. Level-one recast/replacement behavior remains unchanged.

## Parent and continuation ordering

The retained ownership order remains:

```text
native arrow contact
-> suppress native hostile-NPC damage
-> claim one summon attack ordinal
-> queue one world-thread RPG conversion
-> submit parent Physical damage exactly once
-> emit parent damage receipt
-> evaluate Arc/Fork/Chain
-> create legal descendants
```

Parent damage does not depend on a linked projectile passive. Continuation failure cannot consume or roll back a parent hit that has already completed.

Chain's authored 30% less-Hit factor is applied once to each supported projectile payload. Fork leaves the parent impact intact and gives each descendant 65% of its eligible parent payload. Descendants inherit the same committed combat snapshot, owner, source skill, summon proxy, ancestry, visited-target ledger and bounded work budgets.

The R033 descendant origin used `primary.position()`, which is the target's feet/ground position. Fork retained the inbound downward trajectory and collided with terrain almost immediately. R034 starts continuation construction from the target bounding-box centre plus 0.08 m clearance and derives the continuation trajectory from that point. This remains subject to connected visual/contact confirmation.

## Trace correction

R034 adds `SUMMON_ARROW_DAMAGE` for parent, Arc, Chain and Fork damage receipts. Parent records include the intercepted native amount/cause index where available. Records expose:

- summon, owner, source skill and skill level;
- target and attack ordinal;
- snapshotted Magic Power;
- base coefficient and base Physical damage;
- passive and continuation magnitude factors;
- pre-mitigation damage and resolved `PHYSICAL` cause;
- native submission/cancellation/filter result;
- Health before/after and final Health removed;
- combat-text eligibility;
- parent/child projectile lineage for descendants.

Successful continuation creation now emits the child projectile IDs and corrected spawn coordinates. Existing damage lifecycle and projectile spawn/contact/termination events remain available under the same root cast identity.

## Files changed for R034

- `src/main/java/com/inigmasgames/hytalerpg/execution/summon/SummonArrowDamage.java`
- `src/main/java/com/inigmasgames/hytalerpg/execution/summon/SummonRegistry.java`
- `src/main/java/com/inigmasgames/hytalerpg/execution/summon/SummonProfile.java`
- `src/main/java/com/inigmasgames/hytalerpg/execution/hytale/HytaleSummonSystem.java`
- `src/main/java/com/inigmasgames/hytalerpg/execution/hytale/HytaleSkillExecutionSystem.java`
- `src/main/java/com/inigmasgames/hytalerpg/diagnostics/RpgTraceEventType.java`
- `src/main/resources/rpg/runtime/stage-10-summons-cohort-h.json`
- `src/main/resources/rpg/catalog/skills.json`
- `src/test/java/com/inigmasgames/hytalerpg/SummonSkeletonArchersTest.java`
- `src/test/java/com/inigmasgames/hytalerpg/execution/hytale/SummonNativeDamageCauseTest.java`
- `tools/Run-Stage13CohortSmoke.ps1`
- `gradle.properties`

## Verification

Focused production-contract tests passed, including:

- Magic Power 40 produces 14 Physical before mitigation;
- a live lease captures the committed resolved Magic Power;
- level changes count without changing coefficient or captured power;
- level one produces one archer and recast replaces it;
- invalid power/coefficient cannot become a zero/one fallback;
- parent submission appears exactly once and precedes continuation evaluation;
- native suppression precedes the deferred RPG conversion;
- Chain and Fork do not alter/suppress the parent coefficient;
- Fork children inherit the same base/derived snapshot with a 0.65 child factor;
- Chain children inherit the same exact snapshot;
- null/wrong Physical cause is explicitly guarded;
- native role and visible tracked-carrier assets remain intact.

Full retained run:

```text
2336 tests executed
2329 passed
7 failed: Stage13IconUpdaterTest only
```

All seven failures were the icon updater's expected safety refusal because `HytaleClient` and its Hytale Java server were running during the suite. Each failure reported: `Close Hytale and its server completely, then run Update RPG Icons again.` No RPG compilation, summon, continuation, native-control, persistence or gameplay assertion failed. After Hytale closed, all nine `Stage13IconUpdaterTest` cases were rerun unchanged and passed. Assertions were not weakened or removed. Taken together, every retained test passed; the initial full invocation retains its truthful environmental-failure record.

Isolated exact-JAR three-mod smoke: **PASS**

- process exit `0`;
- exactly three mods;
- R034 setup/ready identity found;
- RPG plugin enabled and manager/network started;
- native bridge and shipped Rune controls resolved;
- summon, batch-role, projectile, support, strike, progression, acquisition and encounter assets resolved;
- no native ability asset rejection;
- clean shutdown.

Evidence: `evidence/stage-13/cohort-r034-final/`.

## Package

```text
evidence/stage-13/revision-r034/package/HyARPG.jar
SHA-256 437A998DA0B054652EA04EB7F051D95B541D63D6DCD7FCE0973C34E15B97E68E

evidence/stage-13/revision-r034/package/HyARPG-R034-three-mods.zip
SHA-256 625391F5B66CF32AB90589481457ACB4392322464DAD999853C330CF097A30D1
```

The archive contains exactly:

- `HyARPG.jar`
- `CanvasUI-0.1.0.jar`
- `HYTALEDEVLIB-0.5.0.jar`

The JAR embeds `rpg.revision=R034`, `rpg.version=0.0.25`, Stage 13 and Hytale `0.7.0-pre.2`.

## Live deployment

Deployment status: **DEPLOYED; CONNECTED VERIFICATION PENDING**

Before replacement, the installed R033 JAR, RPG mod-data and save-level player state were copied to:

```text
evidence/stage-13/revision-r034/live-backup-20260914-201602/
31 backed-up files
previous JAR SHA-256 C00B091B452CCC0A500C51FCB85C3EA28CF760F0B1E32ABBF91D12393466030A
```

Hytale and its Hytale Java server were confirmed closed before backup and replacement. No live save-state file was edited. The new JAR was installed at:

```text
C:\Users\Zemio\AppData\Roaming\Hytale\data\pre-release\Saves\RPG\mods\HyARPG.jar
SHA-256 437A998DA0B054652EA04EB7F051D95B541D63D6DCD7FCE0973C34E15B97E68E
```

The packaged and installed hashes match exactly. Post-install archive inspection confirms the live JAR embeds R034 and the production summon profile resolves `MAGIC_WEAPON`, coefficient `0.35`, `PHYSICAL`, effective-level count policy and no development-fixture flag. Only one RPG implementation JAR (`HyARPG.jar`) is installed; CanvasUI, HYTALEDEVLIB and the owner's ImmersiveNPCs mod remain untouched.

## Connected acceptance still required

R034 must not be described as connected-verified until the owner demonstrates authoritative hostile Health removal.

1. Confirm the top-right revision is `R034`.
2. With no linked projectile passive, cast Summon Skeleton Archers and let one native arrow hit a hostile Spider. Confirm Health decreases and a normal damage number appears.
3. Recast at effective level one and confirm the prior archer is replaced, leaving exactly one.
4. Link Chain, arrange at least three hostiles and confirm parent A takes damage before a visible child travels A to B. Confirm child Health loss and no repeated target.
5. Link Fork and confirm the parent takes damage, exactly two visible children leave the impact above terrain, and any child contact applies the 0.65 descendant payload.
6. Inspect `skill-trace.jsonl` for `SUMMON_ARROW_DAMAGE`, `SUMMON_ARROW_CONTINUATION`, `PROJECTILE_SPAWNED`, `PROJECTILE_ENTITY_HIT` and the complete native damage lifecycle.

Expected no-passive trace:

```text
native contact -> SUMMON_ARROW_DAMAGE continuation=PARENT finalHealthRemoved>0
```

Expected Chain/Fork trace:

```text
parent SUMMON_ARROW_DAMAGE
-> SUMMON_ARROW_CONTINUATION with child IDs
-> PROJECTILE_SPAWNED
-> PROJECTILE_ENTITY_HIT
-> child SUMMON_ARROW_DAMAGE with parent/child lineage
```

## Remaining known issues and rollback

- Connected child trajectory/contact and combat text remain owner-QA requirements.
- The separately observed encounter-inspection and shutdown persistence-uncertainty messages were not broadened into this correction and remain open if reproducible.
- Existing Hytale API deprecation warnings remain non-blocking.
- No branch commit or push was performed because the cumulative RPG worktree contains substantial accepted uncommitted work from prior revisions.

Rollback requires Hytale to be closed. Restore the backed-up R033 `HyARPG.jar` from the R034 live-backup directory. Restore copied mod-data/player state only if an actual state rollback is required; normal JAR rollback does not require save mutation.
