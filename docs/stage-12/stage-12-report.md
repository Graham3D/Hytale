# Stage 12 — progression, encounter rewards, acquisition and mastery

Revision R031, version 0.0.24. Work stays on RPG in the local GitHub checkout.
Stage 11's preceding local closure is e9944e1. No live deployment has occurred.
Stage status: IMPLEMENTATION_IN_PROGRESS. Connected gate: UNVERIFIED.

## Evidence contract

The updated master v1.2 Markdown is authoritative (SHA-256
750483855846FF6DB2564B4D6D626C2A12F1AC6EBBBBF12232B2C3ECCF667010).
Local formula tests, compiled-asset audits, and an isolated network server boot
are separate engineering evidence. They cannot establish client input, native
combat, progression rendering, source acquisition, or connected persistence.
Production acquisition requires VERIFIED_CONNECTED exact source evidence;
unassigned or merely inspected source candidates remain disabled.

## Cohort A — authoritative progression math and gated profiles

### Why this boundary comes first

The existing CharacterXpProjectionService already owns the master character-XP
curve. ProgressionMath calls it instead of introducing a second XP formula or
editing the HUD. A fixture transcribes all 99 rows of Appendix A.1, checking each
level's next XP, cumulative threshold, and ordinary equal-level kill reward.
This separates reward arithmetic from the native eligibility and persistence
boundaries that still need implementation.

### Implemented

- Exact half-up XP/reward math, the piecewise K(L) table, rank and rarity factors,
  Boss-without-Unique-double-stacking, and every level-difference interval.
- Equal-pot division with the specified positive minimum, cumulative XP
  advancement, multi-level +5 attribute/+5 pending points, level-99 cap, retained
  overflow, and checked integer overflow. No production award hook yet.
- Mastery thresholds and +2% per level beyond one, capped at 20/+38%; additive
  effective-Wisdom learning chance, exact-source pity thresholds 20/60/150;
  Foundation/Advanced/Specialist Insight prices 20/40/80.
- Five contiguous RPG biome bands and Normal/Nightmare/Hell difficulty profiles.
  These are RPG level bands, NOT claims about vanilla NPC levels. The native
  biome-binding registry is deliberately empty until exact spawn APIs/assets
  are audited. Unknown mappings cannot award rewards. Nightmare and Hell remain
  disabled for missing authored spawn/encounter/source data.
- Startup validation logs five bands, three difficulties, zero native biome
  bindings and awardHook=false. Corrected stale manifest Metadata.Stage from 10
  to 12; the R031 main build identity also states Stage 12.

### Local verification and retained ownership

28 new tests; full clean build: **1405 tests, zero failed/error/skipped**.
Normal isolated three-mod server reached Hytale Server Booted and shut down
cleanly with all retained asset/bridge checks and progression-profile audit.
Nine packaged CustomUI documents pass the existing structural cleanup gate.
Protected paths match e9944e1: native input/HUD, XP composition, CanvasUI, old
Stage 04/05 profile data, ProjectileConfigs and balance. 87 canonical skills,
66 passives, and 60 zero-cost/zero-cooldown native trigger items are retained.
Player schema 7 and compiled-plan schema 35 are unchanged in this cohort.

Build SHA-256: 0B5E80C552ECBF6AA85E067390FBFF23204536D222DB5D946B0E9C3979C24999.
Rollback: Stage 11 cohort Z, SHA-256
57A8688729BB33ABE1C76711B3777C98AF35351F98FEDA74365E6912811D9378.
Machine evidence, exact archived artifact, preceding artifact and normal smoke:
`evidence/stage-12/cohort-a/`. Test-case inventory is in `test-results.json`.
The live R023 three-mod installation, owner world, native Rune recovery journal,
and art/lost and found remain untouched. No Google Drive files were written.

### Remaining gates

Durable exact-once awards, spawn-context identity/attribution, public learning,
mastery evidence, Insight ownership, respec safeguards and native integration
are not established by this mathematical foundation. Continue at cohort B.
Connected combat/XP/learning/mastery/HUD/restart proof remains required.
