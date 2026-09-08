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

## Cohort D — persistent encounter context and interrupted-party delivery

### What changed and why

Added FileEncounterStore (encounter file schema1, independently versioned from
player8/compiled35). The store preserves immutable spawn identity/biome/level,
actual contribution timestamps, first-combat time and the record-low Health
anti-farm watermark. Reload restores the bounded contribution indices; it does
not begin a new encounter or refresh the 20-second window. A stale snapshot cannot
roll back its time/progress watermark. A durable exclusion tombstone also covers
ownership/conversion before a spawn context was captured. Restoring allegiance
cannot clear this exclusion. The next native cohort will call these boundaries.

Death processing has its own persistent fan-out transaction because per-player
dedup alone would not freeze a partially paid party's membership or common pot.
Before any award, freeze the entire validated death plan atomically in the bounded
pending directory. Delivery uses the existing cohort-B award authority. Persist
a delivery cursor only after that player's award succeeds. A crash between those
two writes retries the same event/payload, which the player ledger deduplicates.
After all recipients are committed, write a permanent completion receipt and
remove only the finished pending file. A crash during that cleanup finds the
completion receipt and removes the pending file without awarding again.

Already frozen/completed deaths cannot be recreated, recalculated, reclassified
by moving the corpse or changed into a different reward payload. The store also
requires a persisted recent contribution for each share; it does not accept an
in-memory hit as a substitute for durable attribution. Empty/ineligible deaths
can be permanently completed without a player award. Deaths already frozen
retain the eligibility decision made at death; later administrative changes do
not retroactively reroll earned rewards.

### Bounds and failure policy

There are at most 256 pending death plans and at most 256 recipients/plan. Each
drain has an explicit 1..256 award-attempt budget and persists partial progress.
Recovery enumerates only that bounded pending directory, never permanent history.
Context, exclusion and completed-death lookups use stable hashed world/entity IDs
and sharded paths. Each file is bounded to 256 KiB, checksum/shape/schema checked,
forced to storage and atomically renamed. In-process callers serialize through
the store; a second file writer is refused by an OS lock. Corrupt/torn/foreign
files are retained and fail closed, not reset or treated as new enemies.

The store deliberately prioritizes authority over availability: a bad pending
record or unavailable player writer can pause subsequent delivery until repaired.
Admission exhaustion cannot silently discard an accepted payout. Permanent
contexts/exclusions/death receipts grow on disk; sustained I/O cost and retention
operations still require Stage13 measurement. These are process-crash guarantees
on the local atomic filesystem, not whole-volume power-loss or arbitrary external
tampering guarantees. Back up/restore **players + earned-rewards + encounters**
together. Never remove dedup/exclusion files to make a rollback load.

### Evidence

43 new tests cover original-context reload, no unknown-LOAD classification,
expired support, persistent captive-farm limits, immutable exclusions, all five
death crash boundaries (freeze/award/cursor/completion/cleanup), interrupted
context/exclusion writes, partial-party replay through the actual player award
service, invalid shares, no persisted contribution, queue/restore admission,
foreign/corrupt/oversized/shape-invalid files, orphan temps and writer exclusion.
An initial filtered Gradle invocation also selected CanvasUI, which has no Stage12
test names, and failed its empty filter. The root-qualified `:test` invocation
passed; the subsequent **full** build ran all CanvasUI tests without filtering.

Full clean retained build: **1529 tests, zero failures/errors/skips**, approximately
one minute. Normal isolated three-mod network boot/clean stop and retained asset
gates passed. Startup configures the encounter store and logs zero pending jobs,
frozen plans, permanent exclusions, awardHook=false and connectedProof=false.
The native event hookup remains explicitly incomplete in D; there is no claim
that a live NPC death has yet caused an award.

Artifact SHA-256: 45EA5583A7CCF25651B2BEB3BE9D41434110CB0888D3AB97E2AC89FE7720D320.
Rollback cohort C: 0399B67A7F8279E578575D38985D5BA2A2B88F948A3C9A55533BF7C01365D652.
Evidence/archive/rollback: `evidence/stage-12/cohort-d/`.
No live deployment, owner-world mutation, native HUD/input/balance edit, art
change or Google Drive write. Local gate PASS; connected gate UNVERIFIED.
Continue with the audited native spawn, actual-damage and death adapters.

## Cohort E — native spawn/Health-loss/death hookup

### Earliest native boundaries audited

The installed WorldSpawnJobSystems passes a pre-add callback to NPCPlugin. That
callback sets spawn role, environment and spawn configuration before
Store.addEntity(SPAWN). Ordinary manual NPCPlugin spawns leave environment/spawn
configuration at Integer.MIN_VALUE. The adapter requires a fresh SPAWN with both
provenance fields initialized before classifying a new context. LOAD only restores
an existing RPG context; an old NPC without that context earns nothing. This is
deliberately narrower than all possible native spawning mechanisms: beacon,
marker, newer-generator and unaudited mechanisms are not inferred equivalent.

Native ApplyDamage subtracts Health, then queues DeathComponent creation through
CommandBuffer.run. The RPG observer runs in Inspect after Apply, before RPG
reflection, and reads the existing SupportDamageSystems pre-Apply Health capture
and current authoritative Health. It requires positive actual loss, a loaded
player source/credited owner, native hostile attitude and an eligible natural
target. Cancelled, self, friendly, reflected and redirected damage grant no
contribution. RPG metadata retains rootCastId/skillInstanceId/correlationId;
ordinary native attacks do not receive fabricated RPG cast IDs. Native projectile
sources inherit EntitySource with the owner reference, as verified in bytecode.

The actual HytaleWorldGenProvider codec identifies Default. A first draft used
the generator data-directory basename, but bytecode showed that the provider can
load Default from a different root path. That assumption was removed before
archiving: use public codec Name/Path fields, not reflection or toString parsing.
Custom Path overrides and non-legacy generators require a separate asset audit.
The actual legacy biome lookup uses the installed zone/biome result at spawn.

### Authority and lifecycle

PersistentEncounterRuntime serializes spawn, contribution, death and delivery
callbacks. Each accepted contribution persists its timestamp and farm watermark.
Any uncertain storage operation freezes progression awards until restart; it
does not cancel native damage, refund casts, rewrite Health or stop native death.
Ref removal/unload clears only the in-memory indices, retaining persistent context.
Current-role changes or ownership/conversion disqualify previously tracked actors.

Conversion now persists its exclusion before installing the native relationship
overlay. If that write fails, the overlay is not installed; an already-paid cast
is not silently refunded. Its existing lease cleanup remains in place. An
interrupted or later-restored conversion cannot become an ordinary XP source.
RPG summoned/revived actors remain excluded as targets, while their RPG-attributed
damage credits the loaded owner once. Native converted attacks resolve to the
lease owner while the overlay exists. No second summon/owner payout is added.

The native DeathComponent hook requires real death information and Health at its
minimum, then queries only the previously credited player IDs (maximum256) for
current world presence/position/level. A new read-only characterLevel accessor
avoids recompiling a loadout for this check. It freezes the cohort-D plan before
queueing delivery. A global (not per-player/per-world multiplied) one-second
delivery schedule permits eight award attempts per tick window. The existing
earned-reward service writes XP, levels, 5+5 points and Insight and emits its
commit traces. No death callback directly increments XP or replays the last hit.

### Deliberate limitations

This cohort's native participants are **solo**. No native party membership API
was found by the installed class/API audit; do not mistake the injected party
tests in cohortC for a live party adapter. The next cohort must expose a trusted
provider contract and retain an explicit unavailable status without a provider.
Healing/absorption/effective-control native contribution hooks and mastery are
still next work. Frozen plans aggregate enemy contributions, so their death event
uses the stable enemy correlation rather than pretending one root owned all XP.

Startup now truthfully advertises awardHook=true but connectedProof=false. New
trace events distinguish context restore, actual post-Apply contribution, frozen
native death and rejected reward processing. One bounded incident is logged on
adapter/storage failure. There is no new command, HUD, balance formula, native
cost, cooldown or damage writer in this cohort. Synchronous persistence latency
and the eight-awards/second delivery policy remain explicit Stage13 load-test
risks, not performance claims.

### Verification and rollback

18 runtime tests cover persisted native-input-shaped events, reload/unload,
unknown spawns, immutable role/biome context, cancelled/non-loss eligibility,
conversion, actual exclusion/farm persistence, death replay, uncertain storage
and player-write failure. Five additional native-control tests exercise the
actual installed worldgen codec and spawn-provenance policy. A test initially
referenced a recording field through the tracer interface and did not compile;
the fixture now retains its concrete RecordingTracer and passes.

Full clean retained build: **1552 tests, zero failures/errors/skips**, 62 seconds.
Normal isolated three-mod network boot/clean stop and all retained asset gates
pass, including construction/registration of the native tracking, Inspect,
DeathComponent and delivery systems. That smoke has **no connected player** and
does not prove natural spawn observation, real damage credit, native death reward
execution, input delivery, XP rendering or notification behavior.

Artifact SHA-256: A353FEE1E0F480569B2BBC540392596948823ED2290181DE1F56DC81E2DEA11E.
Rollback cohort D: 45EA5583A7CCF25651B2BEB3BE9D41434110CB0888D3AB97E2AC89FE7720D320.
Evidence/archive/rollback: `evidence/stage-12/cohort-e/`; exact bytecode audit in
its `api/` subdirectory. Player8/plan35 unchanged. Restore players, earned-rewards
and encounters as a coordinated checkpoint when rolling back earned progress.
Live R023 and its control-recovery journal remain untouched; no Google Drive or
art writes. Local gate PASS; connected gate UNVERIFIED. Continue Stage12.

## Cohort F — actual support credit and trusted party-provider boundary

Continued from pushed 208fb0d, retaining the unfinished local changes. Healing
observes the existing post-native-write callback and records positive actual
Health restored to an eligible contributor, not requested healing or overheal.
Actual finite shield and Managuard consumption feed the existing central
absorption callback before the optional Reflective Ward branch. Unused shields
and non-hostile/recursive damage do not become absorption contributions. These
observers do not alter damage, healing, costs, regeneration or native HUD/input.

The persistent runtime saves the affected recent encounters (bounded to 64 per
beneficiary). Unknown, expired, excluded or completed encounters cannot gain late
credit. Uncertain persistence still freezes awards until recovery. Neither hook
awards mastery yet: hostile-healing provenance and meaningful-root validation
must be established before positive healing alone can qualify for mastery.

No installed native party membership adapter has been established. A server-only
PartyMembershipProvider accepts a bounded immutable candidate set and returns a
validated snapshot. Default status is NATIVE_PARTY_PROVIDER_UNAVAILABLE_SOLO_ONLY.
Foreign members, invalid IDs, null results and provider failures reject rather
than silently reprice a party as solo. The frozen death plan retains its original
membership/pricing across replay. This seam is not proof of connected parties.

24 new tests cover actual/non-overheal support, expiration/world/exclusion/death
boundaries, persistence, party snapshot integrity, one-pot sharing and replay.
Full clean retained build: **1576 tests, zero failures/errors/skips**, 62 seconds.
Normal isolated three-mod boot and clean stop passed; support callbacks register.
Packaged UI/asset validation, unchanged native authority checks, artifact hashing
and prior-build archive checks passed. These prove local structure only; native
support execution, party integration and all client behavior remain UNVERIFIED.

Artifact SHA-256: 2964B5D9FA94A903D7BC7B77F9543B2FA5B9932FF8EA6023B560B1F4289289A5.
Rollback E: A353FEE1E0F480569B2BBC540392596948823ED2290181DE1F56DC81E2DEA11E.
Evidence/archive: `evidence/stage-12/cohort-f/`. Player8/plan35 unchanged. Restore
players, earned-rewards and encounters together; never clear reward tombstones.
Synchronous support persistence cost remains a required Stage13 measurement.
No live deployment, owner-art mutation or Google Drive writes occurred.

Following the owner's latest efficiency instruction, remaining related work uses
targeted tests during implementation, with complete retained regression, normal
three-mod smoke, archive and rollback checks at Stage12 closure and Stage13 final
candidate. No assertions, behavior or connected gate is relaxed by that batching.
