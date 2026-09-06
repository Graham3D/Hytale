# Stage 02 — shared combat kernel report

Status date: 2026-09-06  
Branch: `RPG`  
Starting commit: `8539d8fc3e84386fc70d0326b4f9a47d0c4ea0f2`  
R010 implementation commit: `9f1fe9a91f710b36465cf1729eb7b3dbb3153f33`
R011 implementation commit: `fceaed0d48678194c2fa43634bd58869d6bfa155`
RPG revision: `R011`
Hytale target: `0.7.0-pre.1`  
RPG version: `0.0.4`
Built JAR: `HytaleRPG-0.0.4.jar`
JAR SHA-256: `9C6A2C39B3B4B94A6AC200181683A5AD9E78BE66CFB92AC7C5443A68350B26E6`

## Result and scope boundary

R010 introduced the shared Stage 02 combat kernel. R011 is a Stage 02-only
closure pass. Neither revision implements a
Strike, Projectile, Beam, Aura, Summon, or other skill-family executor and it
does not begin Stage 03 UI work. The existing Stage 01B loadout, graph,
persistence, rollback, and trace paths remain intact.

R010 connected testing passed STR scaling/reset, compilation, Mana spending,
crit math, status thresholding, and command stability. It exposed two evidence gaps:
self-target damage stopped after `DAMAGE_GATHERED`, and native regeneration
made the recovery amounts zero before they could be measured. R011 corrects
the diagnostic design and the two content inconsistencies. Its automated suite
and isolated three-mod bare-server startup pass, and the exact R011 JAR is
deployed. R011 connected testing now closes both remaining evidence gaps and
the restart/rejoin persistence gate. Therefore:

**Stage 02 result: PASS.**

```ini
Stage02 = PASS
```

**Safe to begin Stage 03: YES; no Stage 03 work was begun in R011.**

## Reconciliation decisions

The Master Specification v1.1 and canonical catalogs are authoritative over
older or contradictory implementation instructions. R011 records:

- Level-1 resource maxima subtract the effective value of the starting raw 10
  points. The correct formula is `100 + coefficient × (E(A) - E(10))`, not
  `100 + coefficient × E(A)`.
- Cooldown recovery is a recovery-rate divisor, as closed by the Master:
  `max(0.25, BaseCooldown × DurationFactors / (1 + clamp(totalRecovery,0,0.75)))`.
- Potency is `+15% Increased` scalable magnitude. R011 removes R010's incorrect
  `+10%` override and reads `0.15` from the versioned balance profile.
- Efficiency follows the Master integer spend rule: zero remains zero;
  otherwise `max(1, ceil(baseCost × 0.85))`.
- The kernel retains a generic typed cooldown-recovery modifier. No compiler
  branch or content assumption exists for a named noncanonical passive; the
  catalog remains exactly 66 passives.
- The RPG `LIGHT`, `HEAVY`, and `MAGIC` classifications are separate from
  Hytale's attack-mode `DamageClass`.

## Versioned balance and registry data

Balance profile `rpg.combat-kernel.r011`, schema 1, is packaged at
`rpg/balance/combat-kernel-v1.json`. It owns every Stage 02 breakpoint,
coefficient, cap, recovery fraction, timer, and status constant.

Item power registry `rpg.item-power.audit.r011`, schema 1, is packaged at
`rpg/balance/item-power-registry-v1.json`. It contains three explicit 20-power
development fixtures (Light, Heavy, Magic). Unknown production items fail
closed. Resolver decisions use explicit tags or exact registry ItemIds; display
names and resource type are never classification inputs.

The shared effective curve is:

```text
E(A) = min(A,150)
     + 0.75 × clamp(A-150,0,100)
     + 0.50 × clamp(A-250,0,100)
     + 0.35 × clamp(A-350,0,100)
     + 0.20 × max(A-450,0)
```

It is used identically for STR, DEX, INT, WIS, and LUCK. The verified values
are E(150)=150, E(250)=225, E(350)=275, E(450)=310, and E(500)=320, with
continuity checked on both sides of all four breakpoints.

## Derived-stat contract

`EffectiveAttributeService` and `DerivedStatService` are pure and deterministic.
Their authoritative formulas are:

```text
MaxHealth  = 100 + 2.00 × (E(STR) - E(10)) + flatHealth
MaxStamina = 100 + 0.50 × (E(DEX) - E(10)) + flatStamina
MaxMana    = 100 + 0.75 × (E(INT) - E(10)) + flatMana

Heavy multiplier = 1 + 0.003 × E(STR)
Light multiplier = 1 + 0.003 × E(DEX)
Magic multiplier = 1 + 0.003 × E(INT)
Healing multiplier = 1 + 0.003 × E(WIS)

Wisdom cooldown recovery = 0.30 × E(WIS) / (E(WIS) + 160)
Wisdom learn rate = min(0.40, 0.0015 × E(WIS))
Luck crit chance = min(0.75, 0.05 + 0.25 × E(LUCK)/(E(LUCK)+250))
Luck upgrade success = 0.10 × E(LUCK)/(E(LUCK)+300)
Luck magic find = 0.005 × E(LUCK)
```

The undefined Dexterity Accuracy and Intelligence debuff-resistance formulas
remain unimplemented.

## Native resource authority

Hytale `EntityStatMap` remains the only owner of current Health, Mana, and
Stamina. No current resource value is persisted in RPG player state.

`DerivedStatEntityAdapter` projects derived maxima as named MAX modifiers and
preserves the entity's current percentage when an attribute changes.
`EntityStatResourcePort` performs transactional reads/writes directly against
Hytale stat indices from `DefaultEntityStatTypes`.

The RPG asset pack replaces the player Mana/Stamina definitions with the
specified 100-point baseline and `1.5% maximum/second` native regeneration.
Vanilla sprint/glide stamina drains and non-player regeneration are retained.
This avoids a second code-driven regeneration tick and therefore avoids double
regeneration.

`RpgResourceService` implements `canAfford`, `reserveCost`, `commitCost`, and
`refundIfUncommitted`. Holds prevent parallel overcommit; pre-commit failure
consumes nothing; a committed cost is never silently refunded. Mana, Stamina,
and NONE are legal, while a declaration containing both Mana and Stamina is
rejected.

Normal and charged hostile-hit recovery are `4%` and `12%` of each maximum.
The service deduplicates by player plus root weapon attack ID, so one swing
hitting several targets cannot restore several times. Bed restoration fills
Stamina and unreserved Mana. Home restoration fills both after two seconds in
an actual `WildernessTracker` Home region and three seconds out of hostile
combat. A native entity tick system runs this rule; the damage Inspect hook
maintains conservative runtime hostile-combat timestamps.

## Reservations and cooldowns

`ReservationService` accepts fixed and percentage Mana reservations, sums
them against total maximum Mana, rejects oversubscription, clamps current Mana
to the spendable maximum, and never mints Mana when a reservation is removed.
Regeneration, bed, and Home restoration all respect the unreserved cap.

`RpgCooldownService` owns runtime-only per-player/per-skill cooldowns with
`canActivate`, `startCooldown`, `remaining`, and `clear`. It uses monotonic
time. Wisdom recovery and compiled passive recovery are summed, capped at 75%,
and applied as the Master recovery-rate divisor. Cooldown state is not added to
ordinary persisted player state.

## Base power, scaling, crit, and damage

`BasePowerResolver` supports `WEAPON`, `MAGIC_WEAPON`, `INNATE`, and `NONE`:

- WEAPON requires audited power plus exactly one Light/Heavy class;
- MAGIC_WEAPON requires authored MagicPower and Magic classification;
- damaging no-weapon content requires explicit InnateBasePower;
- NONE yields zero for utility/non-damaging content.

`ModifierBuckets` implements one additive Increased/Reduced bucket followed by
independent More and Less products. `DamageCalculationService` computes:

```text
AttributeMultiplier = 1 + 0.003 × EffectiveAttribute
ScaledBasePower = BasePower × AttributeMultiplier
SkillRawDamage = ScaledBasePower × SkillCoefficient
ModifierFactor = max(0, 1 + sum(Increased) - sum(Reduced))
               × product(More) × product(1 - Less)
PreCritDamage = SkillRawDamage × ModifierFactor
PreMitigationDamage = PreCritDamage × (critical ? CriticalMultiplier : 1)
```

Direct hits can crit by default; periodic/DoT requests cannot. The baseline
chance is 5%, multiplier 1.50x, and derived total chance is capped at 75%.
Seeded RNG fixtures prove deterministic critical decisions. Kernel math stays
in `double`; `DamageCalculationService.Result.toHytaleDamageFloat()` is the one
documented narrowing boundary required by Hytale's `Damage` constructor.

## Hytale damage integration

`HytaleDamageAdapter` constructs a native `Damage`, attaches serialized RPG
metadata, and calls `DamageSystems.executeDamage`. It never mutates Health.
Four registered `DamageEventSystem` hooks observe the native lifecycle:

```text
RPG Gather observer
-> Hytale gather systems
-> Hytale filters / armor / resistance / immunity / PvP rules
-> RPG post-filter boundary observer
-> Hytale DamageSystems.ApplyDamage (the only Health mutation)
-> RPG post-Apply observer
-> RPG Inspect observer using actual Health delta
```

The metadata carries actor, rootCastId, skillInstanceId, correlationId,
pre-mitigation amount, and Health-before. Inspect records the filtered amount,
Health-after, and actual authoritative loss. R011 adds no RPG armor or
resistance layer.

R010's self-target command was legitimately cancelled by Hytale's
`PlayerDamageFilterSystem`: with PvP disabled, any player-sourced damage to a
player is cancelled, and Hytale's cancellable event dispatcher does not invoke
later systems. R011 rejects self/non-NPC targets before submission and resolves
the entity under the player's crosshair through Hytale `TargetUtil`. It requires
a living `NPCEntity` with positive native Health, then submits that real target
through `HytaleDamageAdapter` and `DamageSystems.executeDamage`.

## Status framework

`StatusService` is monotonic-time and server-authoritative:

- Chill applies -5% movement per stack, maximum five, with a six-second
  refreshed stack timer. Stack five consumes Chill and requests Frozen.
- Frozen is non-stacking two-second hard control followed by three seconds of
  Frozen immunity.
- boss/protected/control-resistant targets receive a two-second 30% Slow
  substitute for Frozen; other protected hard-control requests fail closed.
- Burn is a non-stacking four-second refreshed status.
- Poison is an RPG-owned non-stacking six-second refreshed status; it does not
  assume vanilla Poison semantics.
- Root, Fear, Taunt, and Stagger are non-stacking refreshed states for normal
  targets and consult the control profile before application.

Stage 02 implements the status authority and diagnostic lifecycle, not the
later skill-specific damage coefficients or family executors that will request
these statuses.

## Immutable snapshot and compiler consumption

`CombatSnapshotFactory` is the single future SkillInstance commit boundary.
The resulting immutable `CombatSnapshot` defensively copies raw/effective
attributes, derived stats, item/class/source/base power, plan hash, coefficient,
crit contract, all modifier buckets, evaluated cost/cooldown, and relevant
status modifiers. Derived effects can retain the snapshot rather than
resampling equipment mid-cast.

`CompiledSkillPlan` moves from schema 1 to schema 2 and adds typed
`KernelModifiers`. The descriptive Stage 01B operations remain compatible.
The compiler emits Potency and Efficiency values consumed by snapshot,
resource, and damage services. `KernelModifiers.cooldownRecoveryBonus` remains
a generic typed capability consumed by the cooldown service, independent of
any catalog name.
The persisted `RpgPlayerState` stays schema 2; no migration or current resource
pool was added.

## Commands and trace

The temporary command frontend adds:

```text
/rpg stats
/rpg dev attribute <str|dex|int|wis|luck> <raw>
/rpg dev reset
/rpg dev resource <mana|stamina> <spend|regen> <amount-or-seconds>
/rpg dev recovery <normal|charged> <root-id>
/rpg dev recovery-proof
/rpg dev potency-proof
/rpg dev damage <never|force|seeded> <base-power>
/rpg dev status <chill|burn|poison|root|fear|taunt|stagger>
```

`/rpg stats` applies derived maxima to native stats and prints raw/effective
attributes, native current/max resource values, primary multipliers, crit,
cooldown recovery, learn rate, upgrade success, and Magic Find. Attribute
overrides are persisted through the existing transactional loadout authority;
`/rpg dev reset` restores all five to raw 10.

`recovery-proof` sets live native Mana and Stamina to zero, then synchronously
uses the real recovery service for normal, duplicate normal, charged, and
duplicate charged root IDs. One correlation ID joins the setup and four result
records. Production regeneration is unchanged.

`potency-proof` compiles an isolated Fire Bolt + canonical Potency graph through
the real `LinkCompiler`, records the typed modifier, and does not mutate the
player loadout. The damage command requires an aimed living NPC and uses the
real shared resolver/calculator and native Hytale pipeline.

The always-on trace vocabulary now includes attribute, derived-stat, resource,
reservation, cooldown, numeric damage stages, all four Hytale lifecycle stages,
and status request/apply/refresh/reject/threshold/removal events. `CombatTrace`
adds rootCastId, skillInstanceId, and correlationId to every combat record and
isolates all diagnostic failures from gameplay results. No per-frame trace is
emitted.

## Automated and server verification

`tools/Verify-Stage02.ps1` performs a clean multi-project build, validates all
CustomUI files retained from earlier stages, inspects required JAR resources
and classes, checks both data schemas, confirms the canonical passive count is
still 66, confirms the noncanonical name is absent, verifies Potency `0.15`, and aggregates the
RPG plus CanvasUI regression tests.

Final automated result:

- `65` tests, `0` failures, `0` errors, `0` skipped;
- `44` RPG tests, including `27` Stage 02 kernel tests;
- `21` retained CanvasUI regression tests;
- all required JAR entries present;
- 87 skills and 66 passives retained;
- CustomUI validation passed.

The Stage 02 tests cover every requested curve breakpoint and continuity,
E(500)=320, all five attributes, level-1 maxima, primary/secondary equations,
caps, modifier buckets, seeded/direct/periodic crit, all four power-source
contracts, Mana/Stamina/NONE and dual-resource rejection, transactional holds,
insufficient-resource no-consume, Efficiency, Potency, generic cooldown recovery, passive
regen, normal/charged recovery deduplication, bed/Home restoration,
reservations, cooldown state and cap/floor, Chill-to-Frozen, protected
substitution, Burn/Poison/control refresh, Frozen immunity, snapshot defensive
copying, native adapter signatures, trace failure isolation, and all Stage 01B
regressions.

`tools/Run-Stage02Smoke.ps1` starts the installed Hytale server API against an
isolated directory containing exactly HytaleDevLib, CanvasUI, and the R011 RPG
JAR. Hytale discovered all three, loaded the RPG asset pack, registered the
combat systems, logged `RPG_STAGE02_READY`, enabled the plugin, and produced no
RPG-scoped exception. Exit code 9 is the established immediate
`--boot-command=stop` behavior; the unrelated bare-server Permissions shutdown
warning remains present as in prior smoke runs.

Evidence:

- `evidence/stage-02/R010/connected-client-summary.json`
- `evidence/stage-02/R010/verification.json`
- `evidence/stage-02/R010/server-smoke-summary.json`
- `evidence/stage-02/R010/server-smoke.txt`
- `evidence/stage-02/R010/installation.json`
- `evidence/stage-02/R011/verification.json`
- `evidence/stage-02/R011/server-smoke-summary.json`
- `evidence/stage-02/R011/server-smoke.txt`
- `evidence/stage-02/R011/installation.json`
- `evidence/stage-02/R011/connected-client-summary.json`

## Connected-client checklist

The R011 closure checks passed:

1. `/rpg dev damage never 5` against a living NPC produced the same
   rootCastId, skillInstanceId, and correlationId across calculation,
   `DAMAGE_GATHERED`, `DAMAGE_FILTERED`, `DAMAGE_APPLIED`, and
   `DAMAGE_INSPECTED`. Filter accepted `5.15`, native Apply rounded to `5.0`,
   and Inspect recorded Health `124 -> 119`, actual loss `5.0`.
2. `/rpg dev recovery-proof` depleted both pools to zero. Normal recovery
   restored Mana/Stamina `4/4`; its duplicate restored `0/0` with
   `deduplicated=true`. Charged recovery restored `12/12`; its duplicate also
   restored `0/0` with `deduplicated=true`.
3. `/rpg dev potency-proof` compiled Fire Bolt + Potency through the real
   compiler and recorded `scalablePayloadIncreased=0.15`, expected `0.15`,
   compile PASS, and 66 canonical passives.
4. The first server logged a clean R011 shutdown. The second server loaded
   player schema 2, RPG revision 6 with validation PASS. `/rpg loadout` then
   compiled successfully and displayed Quick Slash plus Fire Bolt with the
   Fork route intact.

## Known limitations and rollback

- The three item records are development fixtures. Production vanilla ItemIds
  remain unsupported until their authored RPG base powers are audited; the
  resolver deliberately does not mine post-mitigation native damage.
- Generic typed cooldown-recovery support exists without a named content assumption.
- Bed restoration is exposed in the shared service, but no public,
  ownership-verified bed-rest event was found in the audited API. It is not
  attached to an inferred block interaction.
- Status lifecycle is implemented and traceable; native movement/effect visuals
  will be attached by later legal skill/status executors, not guessed by Stage
  02 diagnostic commands.

The installer retains the previous RPG JAR beneath
`evidence/stage-02/R011/rollback`. To roll back, stop the RPG world, remove
`HytaleRPG-0.0.4.jar`, restore the retained R010 `HytaleRPG-0.0.3.jar` to the
save's mods directory, and leave player state files in place. Both revisions
use player schema 2.

Stage 02 is closed by R011. No Stage 03 or skill-family work was begun during
this closure pass.
