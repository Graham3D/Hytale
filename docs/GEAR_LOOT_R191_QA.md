# R191 equipment loot cadence QA

The approved rank profiles and guaranteed/optional semantics are maintained in [Gear Master loot](GEAR_MASTER_LOOT.md). The deterministic simulation below evaluates **100,000 kills for every rank at each Magic Find (MF) value**. Each MF run uses the identical death/profile/seed family. Quantity is exactly equal across all three MF runs; only the unchanged production Normal/Magic/Rare quality weighting varies. Quality percentages use generated items as the denominator, at Normal era and source level 25. Exact counts and fractions are written by `GearLootProfilesTest` to `build/reports/gear-loot-simulation.csv`.

| Rank | MF | Zero | One | Two | Three+ | Mean items | Normal | Magic | Rare |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| Common | 0 | 70.13% | 29.87% | 0% | 0% | 0.299 | 69.75% | 27.16% | 3.10% |
| Common | 1.60 | 70.13% | 29.87% | 0% | 0% | 0.299 | 46.20% | 44.98% | 8.82% |
| Common | 1.792 | 70.13% | 29.87% | 0% | 0% | 0.299 | 45.77% | 45.31% | 8.92% |
| Specialist | 0 | 49.84% | 50.16% | 0% | 0% | 0.502 | 69.02% | 27.88% | 3.09% |
| Specialist | 1.60 | 49.84% | 50.16% | 0% | 0% | 0.502 | 45.04% | 45.82% | 9.14% |
| Specialist | 1.792 | 49.84% | 50.16% | 0% | 0% | 0.502 | 44.65% | 46.13% | 9.22% |
| Elite | 0 | 25.22% | 74.79% | 0% | 0% | 0.748 | 67.78% | 28.68% | 3.54% |
| Elite | 1.60 | 25.22% | 74.79% | 0% | 0% | 0.748 | 43.45% | 46.26% | 10.29% |
| Elite | 1.792 | 25.22% | 74.79% | 0% | 0% | 0.748 | 43.10% | 46.51% | 10.40% |
| Miniboss | 0 | 0% | 75.15% | 24.85% | 0% | 1.248 | 66.51% | 29.49% | 4.00% |
| Miniboss | 1.60 | 0% | 75.15% | 24.85% | 0% | 1.248 | 42.09% | 46.79% | 11.11% |
| Miniboss | 1.792 | 0% | 75.15% | 24.85% | 0% | 1.248 | 41.70% | 47.06% | 11.24% |
| Boss | 0 | 0% | 25.02% | 74.98% | 0% | 1.750 | 65.44% | 30.33% | 4.22% |
| Boss | 1.60 | 0% | 25.02% | 74.98% | 0% | 1.750 | 40.70% | 47.48% | 11.82% |
| Boss | 1.792 | 0% | 25.02% | 74.98% | 0% | 1.750 | 40.30% | 47.76% | 11.95% |

Expected theoretical distributions are Common 70/30/0, Specialist 50/50/0, Elite 25/75/0, Miniboss 0/75/25, and Boss 0/25/75 for zero/one/two items. Means are 0.30, 0.50, 0.75, 1.25, and 1.75. Three or more items are impossible under these caps. The simulation asserts every rank remains within one percentage point of its target, identical quantity counts at all MF values, and increasing Rare share at high MF. Durable integration tests cover failed optional picks with no item receipt, guaranteed first picks, independent child and item IDs, repeat delivery, and restart recovery.

Connected QA: run `/rpg geartrace on`, kill representative Common, Specialist, Elite, Miniboss, and Boss enemies, then `/rpg geartrace off`. In the printed JSONL path, compare `ENEMY_GEAR_PROFILE` pick rolls and `ENEMY_GEAR_DECISION` generated items with `WORLD_DROP_SPAWN` events. Miniboss and Boss should always get the first guaranteed equipment opportunity, and Boss should produce two distinct items about 75% of the time. `/rpg gear odds normal 25 boss <seed>` shows the rank-default quantity and quality diagnostics without granting gear. World spawning and pickup remain connected-only acceptance; no native game/server launch was used for offline validation.
