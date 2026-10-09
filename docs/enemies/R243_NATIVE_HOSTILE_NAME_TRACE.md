# R243 natural-spawn trace and native hostile names

Connected R242 trace: `monster-spawn-1791546773000.jsonl` (120 seconds). Compared with R241 `monster-spawn-1791513038052.jsonl` (120 seconds):

| Counter | R241 | R242 |
| --- | ---: | ---: |
| Native jobs | 2,069 | 2,173 |
| Frostgill, Trilobite, Snapjaw jobs | 1,505 | 1,348 |
| Their `NO_POSITION` rejections | 242,731 | 222,714 |
| All `NO_POSITION` rejections | 293,465 | 320,569 |
| Elite fallbacks | 201 | 13 |
| Elite packs published | 1 | 0 |

The routes and sampled environments differ, so these counts do not establish a controlled throughput improvement. R242's dry-column guard rejected only 10 fluid jobs. Aquatic placement pressure remains unresolved, and this release makes no further ecological or native scheduler change. One Elite plan was accepted in the R242 trace; no pack was published during that capture. Connected Elite birth acceptance remains to be verified.

`Bear_Grizzly` and `Bear_Polar` are native hostile predators and are classified `HOSTILE` by the population catalog. R242 recorded three Elite fallbacks for each bear role, with two staged profile rejections for each. A declined pre-root birth deliberately releases the original native actors without ordinary encounter enrollment, preserving R234 rollback safety. That path therefore skipped the encounter-owned nameplate projection; ordinary actors lacking an accepted profile could remain unnamed. The screenshot confirms a bear without a visible name while a profiled Skeleton Scout displays its level.

R243 writes a plain native `Nameplate` for known hostile catalog roles on ordinary native add/load and after pre-root native group release. It keeps an existing nonblank plate, skips QA/summon/conversion/nonserialized actors, and never enrolls a declined birth or changes encounter, reward, or population state. An accepted authored profile can still replace the plain name with its name-and-level presentation. The fallback does not fabricate a level when profile admission or native Health baseline validation fails.

Connected QA: restart the active RPG world, find a naturally spawned bear and another hostile whose Elite birth was declined, and confirm each shows a native name. A profiled Scout should still show its authored level. Capture a new spawn trace to compare aquatic work and Elite publication across a comparable route. No standalone server was launched for this release.
