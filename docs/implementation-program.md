# RPG implementation program — Stages 06–13

## Authority and evidence rules

Owner authorization: attachment `4be7b0e7-6165-4222-9c28-5ef5ca1faddf`, followed by
the supplied master v1.2 Markdown and explicit connected-evidence requirements.
Master v1.2 SHA-256: `750483855846FF6DB2564B4D6D626C2A12F1AC6EBBBBF12232B2C3ECCF667010`.
Baseline RPG HEAD: `8aa4ae3af8925847f78361a5799108c724ed3c39`.

The later authorization supersedes old per-stage owner-approval pauses. Cohort
limits, testing, reports, rollback and separate stage commits remain mandatory.
Only connected Hytale observations establish rendering, input, animation, packet
behavior and native execution. Unit tests and isolated smoke have separate gates.
Unverified connected input does not block independent backend implementation.

## Reconciliation

- MD-19/00.4 and R020/R021 supersede older custom resource-bar and fake ability HUD
  instructions. Native Health/Mana/Stamina and Signature Move remain untouched.
- Retain R016's explicit three logical slots. Stage 09 adds schema 4 support
  then schema 5 shared-deficit/cooldown-work migration, with the schema-3 backup required for rollback. Do not restore obsolete
  four-slot topology or remove skill03 to work around Ability4 unavailability.
- The live deployed build is R023. R024 is a separately committed correction:
  `5c5e55e`, server-side interaction entry, awaiting connected verification. The
  owner-confirmed vanilla Rune cast plus silent watcher and Quiche bytecode audit
  prove the previous observer boundary was inappropriate for this transport.
- R023's interrupted second control has a recovery journal. Preserve/recover it;
  never silently delete temporary-item ownership evidence.
- Stage 01B and Stage 02 retain their recorded connected PASS scope. Stage 03–05
  reports describe limited cohorts and pending connected gates, not all-catalog
  completion. There are only twelve current executable pilot profiles: six each
  from Stages 04 and 05. Final coverage must account for their other 27 skills,
  not label them implemented merely because all 87 catalog entries exist.
- Potency remains 15%. There is no canonical Swift Recovery passive. Preserve
  generic cooldown recovery independently.
- The existing XP assets/geometry and all three-mod ownership corrections remain.
- A normal loopback smoke must reach `Hytale Server Booted`; plugin readiness or
  inherited `--bare` startup alone is not a valid server-start gate.

## Existing ownership map

| Contract | Existing implementation to extend |
|---|---|
| Loadout/persistence/migration | RpgLoadoutService, FileRpgPlayerStateRepository, RpgStateMigrator |
| Catalog/graph/compiler | RpgCatalog, RpgLinkGraphService, CompatibilityService, LinkCompiler |
| Resource/cooldown/snapshot/damage | RpgCombatKernel and HytaleDamageAdapter/lifecycle systems |
| Activation/family orchestration | SkillExecutionService, SkillExecutionPort, SkillExecutorRegistry |
| Native world ownership | HytaleSkillExecutionSystem and bounded owned-effect registries |
| Projectile carrier authority | RpgProjectileService, ProjectileLifecycleRegistry, StandardPhysicsProvider |
| Native input projection | NativeAbilityProjectionService; R024 native interaction callback |
| XP UI | CharacterXpProjectionService and existing XP-only HUD |

No second damage engine, current-resource store, fake native input or UI-only XP
authority will be introduced. Exact stage contracts and installed API/asset
definitions are read before each corresponding implementation.

## Stage boundaries

| Stage | Scope | Initial state |
|---|---|---|
| 06 | 15 spatial skills; Potency, Expanded Radius, Echo, Skill Delay | R025 local gate complete; connected verification outstanding |
| 07 | 12 projectile continuation/multiplicity passives | R026 local gate complete; connected verification outstanding |
| 08 | 8 line/beam/tether/orbit skills | R027 local gate complete; connected verification outstanding |
| 09 | 16 support/barrier/Aura skills and 7 passives | R028 local gate complete: 516 retained tests, schema 5, all seven passive primitives; Flame Weapon root contact and Pedanticism native-enemy cooldown progress gated; connected verification outstanding |
| 10 | 9 summon/corpse/conversion skills and 3 passives | R029 cohorts A–G local scope complete: eight native skill implementations plus Bone Cage intentionally disabled by master collision safety gate; 662 retained tests, plan schema 9; corpse/decoy/conversion role coverage restricted; connected verification outstanding |
| 11 | remaining 40 passives; component-scoped matrix/combinations | R030 cohorts A–Z local scope complete: all forty primitives, 1377 tests, plan schema35, player schema7; 5742 cells,2145 pairs,1000 valid six-Link property fixtures and saved inactive-node recovery; connected verification outstanding |
| 12 | progression, attribution, exact-once rewards and acquisition | R031 cohorts A–H local engineering closure: 1653 retained tests, normal three-mod smoke, packaged/archive checks and actual schema8 archived-JAR rollback drill; schema9 durable learning/pity/Insight/respec, fixed-layout build transfer; zero connected-verified acquisition sources; native party/movement witness unavailable; connected verification outstanding |
| 13 | full 87/66 coverage, hardening, release-candidate assessment | R032 cohorts A–E local gates: latest complete retained run1866 tests and normal three-mod smoke;86 runtime profiles, Frenzy remaining; Snipe native-range and Guard held-item/release routes explicitly gated; shared budgets, projectiles, bounded movement and native basic-hit/combo/victim conditions implemented; plan schema40; final hardening/rollback gates not complete; connected UNVERIFIED |

Advance only after each local engineering gate passes. Each stage has a report,
machine-readable evidence, archived build/rollback and an independent commit.
No stage receives connected PASS from the local gate. Development-only grants and
internal fixtures must never be relabeled production acquisition evidence.

Owner continuation on 2026-09-08 authorizes the fewest safe remaining cohorts,
targeted tests while implementing, and complete retained regressions, isolated
three-mod smoke, packaging/archive and rollback validation at each stage closure
and again for the final Stage13 candidate. This supersedes redundant full-suite
runs after every intermediate change, not any correctness or master-spec gate.

## Live deployment

Build/archive without mutating the live RPG save during this implementation
program. Operator approval and a stopped world are required for deployment. The
authorization to continue implementation does not authorize interrupting a live
test or silently replacing the owner's active mod set. Exactly three established
mods remain the deployment target when approved.
