# R242 spawn throughput correction

The owner's connected R241 trace is `monster-spawn-1791513038052.jsonl` (8 October 2026, 120 seconds). It confirms R241 loaded but did not materially improve the observed throughput: 2,069 native jobs, 242,731 `NO_POSITION` rejections for Frostgill/Snapjaw/Trilobite, and 122 `NATIVE_CLASSIFICATION` Elite fallbacks. R241's cave water tag alone was insufficient because Hytale still created and repeatedly probed water-only jobs in dry columns. The trace also showed three correctly captured, hostile Skeleton members in one native flock before a classification fallback.

R242 checks the existing native job's fluid tag and the three installed roles that inherit `Template_Swimming_Passive`. For those jobs only, a complete loaded chunk-column with no fluid in any section is demonstrably ineligible. The adapter calls native `SpawnJobData.terminate()` and then native `WorldSpawnJobSystems.Ticking.tick`, retaining Hytale's failed-job accounting and removal. A missing section is unknown and leaves the job with native probing; any fluid leaves it with native probing. This does not change spawn weights, budgets, density, placement, or wildlife admission.

Captured world-job members now classify through the existing authored difficulty/encounter owners without the ordinary uncaptured-NPC `isReserved()` exclusion. Hytale may reserve flock members during its own spawn; the exact synchronous job capture, staging identity, role binding, hostile attitude, world provenance, authored profile, and display name remain mandatory. Uncaptured natural NPCs still use the prior classifier. Classification failures now carry bounded subreasons in the spawn trace.

The next connected trace should show `NATIVE_FLUID_JOB_DRY_COLUMN` in its 10-second buckets, lower fish `NO_POSITION` counts and budget use, and fewer `NATIVE_CLASSIFICATION` fallbacks. If any remain, `NATIVE_CLASSIFY_REJECT` priority rows identify their exact gate. These outcomes require owner connected QA; offline tests and packaging cannot establish their measured rates.

No save records or operator world configuration were changed. R240 active-pack leasing, native extension/rollback, and reward ownership are unchanged.

Offline verification: 3,440 tests passed with no failures or skips. The final focused native staging/rollback and fluid-admission tests passed after the last source edit. Package validation passed with 19,309 entries and no ImmersiveNPCs payload. No standalone Hytale server was launched.

Release: `R242-U7P5`, packaged SHA-256 `2493FCC59E90FE47888193F71BCF350C752C0AEC4FB612F3A4423CDCB00B819D`. The corresponding source commit is reported with the deployment result.
