# R261-C offline verification and final handoff

R261-C is a development candidate. The active RPG save and R260 JAR remain
unchanged; connected-game acceptance belongs to a later R261 Final pass.

## Offline evidence map

| Boundary | Focused evidence |
| --- | --- |
| Natural Health and effective STR/INT | `R261CProgressionTest`, `Stage02CombatKernelTest` |
| Native current value preservation and dead state | `EnemyNativeHealthTest.playerCapacityRecalculationPreservesNativeCurrentAndClampsOnReduction` |
| New armor affix, stacking, v1 identity freeze, saved roll | `ManaBeforeLifeAffixTest`, `GearAffixQaSuiteTest`, `Qa159PackTest` |
| Mana readback, Managuard reservation, failures, concurrency | `ManaBeforeLifeStageTest` |
| One conversion per native damage event | `R261CManaDamageReceiptTest` |
| Barrier meter and native resource values | `R261CResourceHudTest` |
| Damage bridge latency | `R261CCombatLatencyTest`; exploratory measurements in `R261C_ENGINEERING.md` |

The complete offline `check`, strict SpotBugs, focused JaCoCo, asset audit,
package validator, and Guardian receipt qualify the exact implementation
commit for publication. They do not establish screen layout, native event
timing, or live tick latency in a connected game.

## R261 Final acceptance boundaries

The final pass must audit the combined behavior on its integrated commit,
address Cyclone reconnect forfeiture and Shared Aegis, review synchronous
forced writes and global resource-owner lock contention, and confirm native
damage ordering and HUD rendering in the intended runtime. The Cyclone gate
remains disabled by default in R261-C. Do not deploy this candidate or merge
it into `main` as part of this milestone.
