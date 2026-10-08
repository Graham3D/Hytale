# Production Elites — Task 1 checkpoint

Revision: **R226-U7P5 source checkpoint (Task 1)**. This is an unreleased working-tree change. No JAR was deployed and no owner gameplay QA was requested. Task 2 will assign the next deployed revision after its presentation and activation work passes.

## Implemented

- Kept the existing native world-spawn capture, deterministic 92/2/6 rarity roll, birth planner, persistence, native pack extension, and Normal fallback. `NativeEnemyBirthOwner` now consults the same role eligibility decision used by the generated matrix. The existing production flags are unchanged: only Larva is currently enabled.
- Added a static production eligibility decision to `EnemyNativeBindings`. It rejects unbound, authored campaign, boss, abstract/component, and QA-only profiles with explicit reasons. The staged Master Enemies classifier additionally requires native `WorldSupport` hostility and rejects QA-marked actors. The ordinary reward classifier retains its prior behavior.
- Extended the existing `EnemyAffixSelection` predicates with deterministic rejection reasons. ME-015 now needs a certified native knockback action; ME-021 needs at least two initial minions; ME-024 needs a Unique/Super Unique leader and a finite guard roster. Existing affix group constraints and all 27 implementations remain unchanged. Super Unique fixed-affix validation reports the same selector's reason.
- Certified Larva and Trork native knockback from their installed melee assets. Removed ME-015 from the Scout arrow's advertised capability set because its native projectile has no knockback. Trork's already-tested native flock roster is represented by its leader/minion capabilities.
- Generated [ELITE_ROLE_MATRIX.md](ELITE_ROLE_MATRIX.md) from the actual role catalog, native bindings, balance, template catalog, and affix selector. `generateEliteRoleMatrix` regenerates it; the focused test checks it byte-for-byte.

## Catalog result

| Outcome | Concrete IDs |
| --- | ---: |
| Inspected in the authored role/alias catalog | 275 |
| Certified role bindings with complete Champion and Unique affix sets in all three eras | 4 |
| Authored campaign encounters excluded | 5 |
| No production-certified native binding yet | 266 |
| Currently enabled for natural promotion | 1 (Larva_Void) |

The four technically certified roles are `Larva_Void`, `Trork_Warrior`, `Skeleton_Scout`, and `Golem_Firesteel`. The latter three stay behind the Task 2 production flag. Certification is conditional on each spawned instance passing the hostile native world-spawn classifier; the catalog includes wildlife and aliases and does not itself grant promotion. Unbound roles do not borrow the permissive QA fallback. The only currently authored Super Unique template whose fixed affixes pass this resolver is `grimgor_the_ashen` on Trork.

The 266 unbound IDs are an explicit coverage limit. Adding them to production requires native action/role certification, not a role-name guess or a second affix database. Task 2 should only activate bindings whose existing native birth and presentation paths remain certified. Campaign Crystal Golems remain under their authored encounter owner.

## Verification and release gate

- `compileJava` passed.
- `generateEliteRoleMatrix` passed.
- Focused offline tests cover catalog completeness and document parity, legal rarity counts, selector predicates, deterministic random rarity, Super Unique fixed-affix rejection, and installed native knockback assets.
- No standalone server or connected smoke test was run. No package or active-save deployment was made for Task 1.

Task 2 remains: generic rarity tint/scale, production activation for certified roles, package validation, and the single owner QA build.
