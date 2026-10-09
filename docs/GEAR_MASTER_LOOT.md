# Gear Master equipment quantity — R191 U7P5

The maintained equipment quantity contract is `src/main/resources/rpg/gear/loot-profiles-v1.json`. An eligible death resolves its canonical RPG role and rank, then chooses a role override if one exists or the rank default. **No role-specific overrides are authored in this revision.** The loader retains canonical role and `role@RANK` override support.

| Rank | Guaranteed picks | Optional picks | Maximum items | Expected zero / one / two / three+ | Expected items/kill |
| --- | ---: | ---: | ---: | --- | ---: |
| Common | 0 | one at 30% | 1 | 70% / 30% / 0% / 0% | 0.30 |
| Specialist | 0 | one at 50% | 1 | 50% / 50% / 0% / 0% | 0.50 |
| Elite | 0 | one at 75% | 1 | 25% / 75% / 0% / 0% | 0.75 |
| Miniboss | 1 | one at 25% | 2 | 0% / 75% / 25% / 0% | 1.25 |
| Boss | 1 | one at 75% | 2 | 0% / 25% / 75% / 0% | 1.75 |

Guaranteed picks make no NoDrop roll. Each optional pick uses a deterministic independent roll and succeeds when the roll is below its configured chance. The cap is enforced. The entire decision and frozen death-time Magic Find map are saved once in `loot-picks` **before** item generation. Each successful pick has a stable `sourceEventId/gear-pick/<index>` child receipt and its own generated item identity. A failed optional pick has no item receipt. Legacy pre-profile one-item receipts replay unchanged. Repeated death delivery, reconnect, and restart reuse the saved decision and receipts.

Magic Find is absent from the quantity decision. Its unchanged production formula affects only the Normal/Magic/Rare quality weights of each generated item. Source level and era, base selection, intrinsic roll, requirements, affixes, ownership, pickup, inventory, salvage, XP, Luck, Fortune, and native Hytale drops retain their existing owners. Native ingredients and currency are separate from Hywind equipment.

The reproducible 100,000-kill-per-rank-per-MF simulation and connected QA steps are in [the R191 loot QA report](GEAR_LOOT_R191_QA.md). `/rpg gear odds` prints the rank-default guaranteed/optional profile and deterministic diagnostic results. `/rpg geartrace on` records the canonical role, resolved rank/profile, every pick and child ID, frozen MF, quality results, and world-spawn events.
