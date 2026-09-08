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

## Cohort B — durable exact-once earned-reward authority

### Implementation and reasoning

Added a player-schema-8 RewardLedger (monotonic sequence, last receipt hash and
Insight balance). Existing cumulative currentXp, level, skillMastery, unspent
and pending points remain the only progression values read by the existing UI.
No second XP store or client grant command was introduced.

The RpgLoadoutService's existing per-player lock owns the complete transaction.
An EarnedReward carries a stable event ID and immutable XP/Insight/mastery deltas,
plus reason/rootCastId/skillInstanceId/correlationId. Its native eligibility is a
separate responsibility, not asserted by the storage layer. Startup configures
the disk-backed writer before any player can load. No in-memory award fallback
exists. Native enemy awardHook remains false in this cohort.

FileEarnedRewardStore uses this order:

1. Reconcile player checkpoint with the committed disk head and any pending intent.
2. Reject an existing event as duplicate; reject a changed payload under that ID.
3. Validate a full before/after progression checkpoint, including checked overflow.
4. Persist the immutable pending intent with forced file contents and atomic rename.
5. Atomically save the complete authoritative player, preserving unrelated state.
6. Persist its immutable event receipt, advance the committed head, then remove
   only the completed pending-intent file.

Crash recovery can apply a not-yet-applied matching intent or finish the receipt
for an already-applied matching checkpoint. It never blindly adds a delta again.
Receipts are sharded by SHA-256 of the event ID, so filenames cannot escape the
plugin directory. Lookups and in-memory work do not scan reward history. Old
receipts are never evicted; disk use grows with earned events. Each read is
size-bounded, schema/shape checked and checksum verified. A per-player OS file
lock excludes a second writer; stale player authority cannot overwrite a newer
committed head.

Player-file writes now force their content and require atomic replacement too;
unsupported atomic rename fails the save instead of falling back to a partial
replacement. An uncertain player-write exception freezes that in-memory holder
until restart/recovery. Other interrupted reward writes must recover before
subsequent mutations can alter the pending snapshot.

XP advances reuse cohort A's canonical curve, adding exactly five unspent and
five pending points per crossed level. Mastery and Insight are checked additions
in the same player save. Awards do not recompile the graph, invoke loadout
listeners, clear Attunement/trigger state, change native resource values, clear
cooldowns/charge debt, refill shield deficit, or change ownership/equipment.
The existing bounded HUD tick reads committed progress. New progression trace
events describe actual persisted awards, duplicate rejection and recovery;
they do not fabricate any native damage, cast or contribution event.

### Persistence safety and rollback

Schema 7 -> 8 initializes only the previously nonexistent reward checkpoint;
it preserves XP, levels, points, ownership, mastery, inactive topology, support
deficit and cooldown/charge debt. The existing immutable `.schema-v7.bak` captures
pre-migration state. Migration does not infer historical earnings or reconcile
an inconsistent legacy level by giving free points. If cached level and XP's
derived level disagree when an earned award is requested, the exact boundary is
REWARD_LEVEL_XP_MISMATCH; retain the state and review it explicitly.

Back up/restore **players plus the entire earned-rewards directory together**.
Restoring just an older player or removing its committed head is refused. For
cohort A code rollback, use the pre-schema-8 player checkpoint and its matching
pre-award ledger backup; never downgrade the schema number or delete dedup keys
to force a load. Completed pending files are removed only after both durable
authorities agree; receipts remain. Orphan unique temporary files are ignored,
not replayed. Corrupt or torn authoritative files are retained for diagnosis.

The guarantee tested here is process interruption/restart on the local filesystem
with forced file contents and atomic replacement. Whole-volume power loss,
external deletion/tampering of individual receipts and arbitrary inconsistent
backup restoration are not claimed recoverable. Receipt disk growth and sustained
multi-player I/O latency require the later performance/hardening pass.

### Local evidence

42 new tests cover XP thresholds/multi-level/cap, mastery/Insight-only awards,
dedup after later events/restart, changed payload rejection, independent eligible
players, trace identity, untouched gameplay state, all five injected interruption
boundaries (after intent/player/receipt/head/cleanup), before/after-player-save
exceptions, stale second authority, corrupted/missing/oversized/foreign files,
orphan temps, 24 concurrent duplicate attempts, overflow, legacy inconsistency,
migration/checkpoint survival, unforgeable generic mutation and path safety.
An initial test compile rejected a compound `var` declaration; it was corrected
before running the successful suite. No runtime failure was concealed.

Full clean retained build: **1447 tests, zero failures/errors/skips**, 50 seconds.
Normal isolated three-mod startup/shutdown and all retained asset gates passed.
The startup log confirms schema 8, write-ahead storage and immutable receipts;
it explicitly states awardHook=false and connectedProof=false.
Compiled plan schema remains 35. Protected HUD/input/balance/old Stage04/05 paths
remain unchanged; 87 skills, 66 passives and 60 neutral native triggers remain.

Build SHA-256: 7AAF6BB922BB3B615919963D81F55C28E6174B88931EABC2F27F879A60ED3932.
Rollback cohort A: 0B5E80C552ECBF6AA85E067390FBFF23204536D222DB5D946B0E9C3979C24999.
Evidence/archive/rollback: `evidence/stage-12/cohort-b/`.
No live deployment, owner-world mutation, Google Drive write or art change.

This proves local transactional structure, not that a connected native enemy
death earned anything. Continue with audited encounter/spawn/contribution paths.

## Cohort C — audited enemy identities and bounded contribution eligibility

### Installed evidence and deliberate scope

Audited the exact pinned server/Assets.zip, retaining bytecode and the normalized
asset inventory under `evidence/stage-12/cohort-c/api`. The inventory contains
1053 named NPC role assets (73 Abstract, 159 Component, 322 Generic, 499 Variant),
270 legacy Tile/Custom biome assets and 28 zones. These are asset counts, **not**
verified combat-enemy population counts. All 87 catalog source rows remain in
the audit: 66 proposed assignments and 21 UNASSIGNED; no source was promoted to
VERIFIED_CONNECTED and no public learning opportunity was enabled.

The first explicit RPG reward pilot classifies Wolf_Black, Trork_Warrior and
Skeleton_Archer as COMMON/ORDINARY. Their installed role JSON hashes are recorded
in `enemy-registry.json`. The choice is a small set of concrete predator, melee
and ranged roles with auditable hostile kits. Patrol aliases are not assumed
equivalent. All other roles, owned/summoned/revived/converted/training actors and
unknown origins remain reward-disabled. Rank is an RPG authored profile, not a
claim that native Hytale exposes that rank.

Four qualified legacy biome identities are bound explicitly:

| Native identity (Default generator) | RPG band | Pilot combat level |
|---|---|---|
| Zone1_Tier1/Forest_Birch | Emerald Wilds | 5 |
| Zone2_Tier1/Savannah_Plains | Howling Sands | 25 |
| Zone3_Tier1/Forest_Fir | Whisperfrost | 45 |
| Zone4_Tier4/Wastes_Grasslands | Devastated Lands | 65 |

These deliberately limited, low-band pilot levels are authored RPG values,
not vanilla enemy levels or measured pacing. Registry identity and biome are
captured in an immutable Spawn value; moving an enemy cannot change its reward.
Other biomes, newer world generators and authored endgame remain unclassified.
No difficulty multiplier is enabled by this assignment alone.

The actual installed FileContextLoader resolves all four names from Assets.zip
in automated native-control tests. Its zone requirement must be explicit (an
empty set loads no zones), and its FileIO provider must point at the zip root.
The first two test attempts exposed these setup requirements; both were corrected
before the passing run. A second test matches all seven role/biome asset hashes.
This proves installed asset identity and loader compatibility, not connected
spawn provenance, rendering, combat behavior or skill-source eligibility.

### Contribution/reward-plan implementation

EncounterContributions accepts actual Health loss, actual hostile absorption,
effective control/taunt, or non-overheal healing of a recent contributor from a
trusted server caller. No last hit is required. Death eligibility enforces the
inclusive 20-second contribution and 64-meter distance limits, same loaded world,
unique players and immutable spawn profile. The death plan freezes recipients,
amounts and event ID once; retries cannot recalculate using changed membership.

Party members share one pot equally, without a multiplier. The master does not
specify which player level resolves a mixed-level party's common pot. This cohort
makes the conservative policy explicit: use the **highest eligible member level**
for the level-difference factor, then split equally (minimum one for positive XP).
An ineligible distant/high-level member does not affect the pot. Solo players use
their own level. Each eligible participant receives the authored rank Insight.
The party ID is a trusted-provider input; no native party API or live party
integration is claimed from these fixtures.

Mastery eligibility rejects 11+-level-under targets and stale/farm-like encounters.
The 60-second progress watermark advances on a new record-low enemy Health
fraction; healing and repeatedly damaging back to the same low point do not
reset it. Effective support can qualify initially, but unchanged support alone
cannot keep a captive encounter productive indefinitely. A conversion invalidates
the encounter for its lifetime, including after allegiance restoration.

Budgets are explicit: 4096 encounters, 256 contributors/encounter, 16384 total
contributions and 64 indexed encounters/player for healing queries. No unbounded
world scan is introduced. Exhausted admission rejects new credit. Removal/world
unload clears the indices. Native hooks, durable spawn/farm state and durable
partial-party death recovery are the **next cohort**, not implicitly completed
by this in-memory primitive.

### Local evidence and limitations

37 contribution/registry tests plus two real loader/asset tests were added.
Coverage includes disallowed origins, immutable biome context, real HP deltas,
support credit and overheal rejection, expiry/distance/world boundaries, mixed
parties, duplicate death callbacks, conversion exclusion, farm limits and bounded
cleanup. A backend death-plan fixture passes through the real cohort-B durable
award service into authoritative XP/level/5+5 points/Insight and the existing XP
view; replay awards nothing. It is **not** a native death or connected HUD test.

Full clean retained build: **1486 tests, zero failures/errors/skips**, 53 seconds.
Normal isolated three-mod network boot/clean stop and retained asset gates pass.
Startup validates the three actual spawnable roles and four configured biome
bindings, explicitly logging awardHook=false and connectedProof=false.
Player schema8 and compiled plan35 are unchanged. Native HUD, input, resource
balance and old Stage04/05 profiles remain untouched; 87/66 and 60 neutral native
ability triggers are retained.

Artifact SHA-256: 0399B67A7F8279E578575D38985D5BA2A2B88F948A3C9A55533BF7C01365D652.
Rollback cohort B: 7AAF6BB922BB3B615919963D81F55C28E6174B88931EABC2F27F879A60ED3932.
Evidence/archive/rollback: `evidence/stage-12/cohort-c/`.
No live deployment, owner-world mutation, art change or Google Drive write.
Local gate PASS; connected gate UNVERIFIED. Continue with durable encounters and
native event hooks, retaining exact failure boundaries instead of fabricating XP.
