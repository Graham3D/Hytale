# Hytale RPG — implementation status for owner / ChatGPT review

## Read this first

**2026-09-12 latest:** [R032-AG particle/attachment report](stage-13-presentation-ag-report.md). **DEPLOYED** `HyARPG.jar` selects the requested `Beam_Heal_Green2`, adds target-parented `Effect_Health_Pack` including full-health primary targets, and `Staff_Bronze` on 26 audited native staff models. Native create/update/remove, shared-recipient cleanup, and resolved staff attachment checks pass. The directed particle stream still uses authoritative spatial anchors, not the animated staff-tip pose. Connected rendering and all previously open release gates remain unverified. Retained result: 2,243 passing tests after rerunning the unchanged icon tests without the overlapping smoke process; initial seven safety refusals are preserved.

**2026-09-12 latest:** [R032-AF Healing Beam appearance report](stage-13-presentation-af-report.md) supersedes AE's Beam appearance choice. AE connected logs prove four native create/update/remove cycles, but the screenshot rejects the broad `Basic` ribbons. AF selects the existing green `RPG_Healing` trail with native width multiplier 0.025; gameplay and AE's lifecycle/HUD are unchanged. **Deployed**, 2,238 retained tests pass; corrected connected appearance remains unverified.

**2026-09-12 current correction:** [R032-AE report and connected checklist](stage-13-presentation-ae-report.md). `HyARPG.jar` is deployed to the actual pre-release RPG world, with save/rollback and owner icons preserved. Healing Beam's processing-phase ECS mutation is corrected without changing `Basic`; cooldown HUD adds a full-face radial, countdown and separate low-Mana feedback without changing Blizzard timing. 2,237 retained tests pass, including the current trace fixtures, plus installed-byte isolated native smoke. **Connected rendering is unverified.** Earlier no-deployment statements and filenames below are historical; unrelated stage/performance gates remain at their existing evidence status.

**2026-09-11 deployment follow-up:** The owner subsequently authorized deploying R032-V for testing. The exact candidate is installed; full pre-V save/mod backup verified, save data unchanged. See the [deployment receipt and QA report](stage-13-support-tether-report.md). Earlier no-deployment statements are historical. Connected acceptance and formal performance remain outstanding.

**2026-09-11 candidate update:** [R032-V support/Tether report](stage-13-support-tether-report.md) adds the explicitly authorized SK-088/089 and LP-067, expanding the catalog to 89 skills / 67 passives. Package-only; no deployment or connected proof. Its report/receipts supersede the older implementation inventory below, not historical connected results or unresolved release gates.

**2026-09-09 supersession:** this page's H/F checkpoint narrative below is historical. The current implementation is Stage 13 L (`cd7cdbd`), deployed with 2,093 local tests passing; the owner subsequently reports that the mod loads. Current all-mechanics/production connected acceptance remains unverified. Use the [complete current review packet](../review/README.md), [stage history](../review/implementation-history-00-13.md) and [QA/QC checklist](../review/qa-qc-checklist.md). No older result below is retroactively upgraded.

Latest isolated correction: [Stage13 H encounter group-commit report](encounter-group-commit-report.md).
H adds bounded submission/durable-completion separation, real WAL grouping and
asynchronous checkpoint publication. It passes 1,963 tests, isolated three-mod smoke,
archive and rollback checks. The unchanged 64-update gate remains **BLOCKED**:
p50 9.0060 ms, p95 16.0318 ms, p99 19.8859 ms versus 4/8 ms p95/p99 limits.
Forces fell from 3,840 to 119, but force-only p95 is still 5.9862 ms per sample.
The separate sparse test retained a 653.0265 ms force stall. No live deployment or
connected QA was performed. H's report/manifest identify the exact binary and
remaining boundary; [G](encounter-wal-correction-report.md) and F below are historical.

This is a detailed **checkpoint report, not a completion or release certificate**.
The implementation checkpoint is
[`14a0f42954d1ac45215664591921372997f90596`](https://github.com/Graham3D/Hytale/commit/14a0f42954d1ac45215664591921372997f90596)
on the long-lived `RPG` branch, pushed on2026-09-09. This report's later commit is
documentation-only; the exact tested binary is identified below.

The task is **not fully finished**. Stage13's functional regression/build/smoke
checks pass, but integrated release hardening is **BLOCKED**. A new real-storage
load test exposed a shared synchronous persistence bottleneck. Three unresolved
native skill integrations also cannot honestly be classified as completed or as
proven engine impossibilities. There is no final release candidate and no live
deployment. Connected Hytale behavior remains separately unverified.

Work and published evidence are in the C: GitHub repository. No Google Drive
files were written. The owner's untracked `art/`, including `art/lost and found`,
was preserved. Recovered loose class files are not treated as installable mods.

## Build identity and reproducibility

| Item | Exact identity |
|---|---|
| Code revision/version | R032 /0.0.25 |
| Player schema |9 |
| Ephemeral compiled-plan schema |41 |
| Canonical inventory |87 skills /66 passives; no67th Swift Recovery passive |
| Runtime records |87;83 without a whole-skill activation gate,4 gated |
| Hytale |0.7.0-pre.1, `e8b4d191fc98a977bf5546a951a7b25473d323e3` |
| Java | Eclipse Adoptium25.0.4 |
| RPG JAR SHA256 |`F7F55FCF05AFEA2A985AC2801CB4F78D346389C2E135E2DCEA22E1F193BCFA83` |
| Server JAR SHA256 |`EC57E9BD6E2CA3CB16CC5883D42B04A0C64D382DEE532C5BC1CFCF68421E1EE3` |
| Installed Assets SHA256 |`46F6AA12DECF4F900FCFDF28ECC67568403C37A3AD0A03A4E4CF7653A324AE39` |
| Master v1.2 Markdown SHA256 |`750483855846FF6DB2564B4D6D626C2A12F1AC6EBBBBF12232B2C3ECCF667010` |

The [checkpoint manifest](../../evidence/stage-13/cohort-f/checkpoint-manifest.json)
separates code, content, adapter, schema and art identities and verifies25 evidence
file checksums. It is checksum-verified, **not cryptographically signed**.
[Source hashes](../../evidence/stage-13/cohort-f/source-sha256.json) identify the
exact main/test inputs. Full test-case names and counts are in
[test-results.json](../../evidence/stage-13/cohort-f/test-results.json).

Three archived mods, and only these three, are intended for a later approved
deployment: HytaleDevLib0.5.0, CanvasUI0.1.0, HytaleRPG0.0.25. CanvasUI includes
its demo; no fourth demo JAR is needed. Their exact hashes are in the manifest.

The **live** RPG world was inspected read-only and still has R023's RPG0.0.16 JAR,
SHA256 `D3CEEA9CEEA5995F515451317452AB9A9CBB62F3E6CF56953B5F707A1BF7FA42`.
Its CanvasUI and HytaleDevLib hashes still match the archived unchanged dependencies.
The existence of newer repository JARs does not mean the owner has run them.

## Stage history and evidence boundaries

Counts below are historical per-checkpoint retained suites, not additive totals.
Stages06–12 passed their recorded local engineering scopes, not connected-client
acceptance. Later integration findings can reopen release readiness.

| Stage | Identifiable checkpoint(s) | Delivered scope / present evidence |
|---|---|---|
|00|`6e3996b`, `eaeb93d`|Feasibility probes and installed0.7 re-audit. Canvas presentation/event research does not prove a draggable spline editor; interactive Canvas development remains paused.|
|01/01B|`8a933e7`, `8539d8f`|Versioned catalog, graph/compiler, loadout persistence and rollback. Owner connected acceptance recorded.|
|02|`fceaed0`, `83fce1b`|Shared attributes/resources/costs/cooldowns/crit/status/damage authority. R011 connected closure recorded, including valid non-self damage and deterministic4%/12% recovery proof.|
|03|`0676b59`, `df7a199`, `fe0f1ee`|Character/skill-tree/HUD foundation. Later R020 native ownership supersedes earlier resource graphics. Connected current HUD/input not inferred.|
|04|`5e3257b`, `fa6e047`|Shared activation/family execution; representative pilots. Remaining catalog mechanics subsequently addressed in Stage13; native input dependent.|
|05|`ac4d9c3`, `78b314a`, `119c791`|Projectile family pilots and native carrier authority; later shared continuation/lifetime corrections retained.|
|06|`689d451`|15 spatial skills, four general Links;217 retained tests. Server geometry, finite ownership, LOS/protection, status and cleanup.|
|07|`61fcc86`|12 projectile modifier primitives;274 tests. Root identity, deterministic continuations, generation/secondary limits, no child cost.|
|08|`31d4a74`|Eight line/beam/tether/orbit skills;320 tests. Bounded sampling, real upkeep/tick ledgers and cleanup.|
|09|`837ed80`|16 support/barrier/Aura records and seven passives;516 tests. Durable shield deficit, native stat decorators, work-preserving cooldown recovery; native-contact/native-enemy-CD limitations remain.|
|10|`2e5d928`|Eight summon/corpse/conversion implementations and the authorized disabled Bone Cage record;662 tests. Exclusive corpse claims, restoration/exclusion, bounded ownership; source roles restricted.|
|11|`e9944e1`|Remaining40 passive primitives;1377 tests; full66-passive compatibility/combination matrix. Saved inactive nodes preserve ownership/topology.|
|12|`de60a02`|Progression/acquisition closure;1653 tests, smoke/archive and actual archived-schema rollback. Source-verified acquisition and party/native integration constrained; new Stage13 storage-load failure blocks integrated release readiness.|
|13|`7c5c4f6`, `255463f`, `057c436`, `55a551b`, `845e371`, `14a0f42`|Cohorts A–F;1886 full retained tests at F. Complete record inventory, shared runtime corrections and explicit disabled decisions; release BLOCKED, not closed.|

The full stage reports retain cohort reasoning and earlier failures:
[program index](../implementation-program.md), [Stage12](../stage-12/stage-12-report.md),
[Stage13](stage-13-report.md). No earlier owner-connected PASS is expanded to
cover newly implemented mechanics, new binaries or untested native adapters.

## What Stage13 changed

- A: shared irreversible dispatch boundary now retains paid cost/cooldown when a
  native effect may already have happened; pre-dispatch rollback remains. Six
  strike profiles reuse the common executor, bounded full-height geometry and
  finite native action-lock/feedback assets.
- B: six projectile profiles and exact native item-power audit. Weapon power
  comes from audited uncharged interaction paths, not an invented average of
  minimum/maximum damage. Explicit RPG-authored magic-item bases are labeled.
- C: six authored projectile profiles, finite spread/homing/fuse/impact patterns,
  scheduled launch ownership, and root-lifetime48-effect/16-secondary accounting
  shared across families. Native carriers contain no duplicate damage/cost logic.
- D: bounded native-observed ground/path movement, swept loaded-world clearance,
  movement/readback witnesses and explicit Guard held-item/release gate.
- E: Execution Strike's live25%-Health condition, Backstab's live rear120° check,
  three-root Finishing Strike token, audited native **melee** basic-hit receipts,
  actual Health-loss recovery and separate noninteractive Finisher pips. No
  packet watcher is treated as the native combat authority.
- F: eliminated unchanged-plan compilation from presentation/combat reads,
  cached immutable profiles and canonical XP thresholds, bounded trace work with
  explicit loss evidence, recorded the disabled Frenzy contract, audited actual
  native time-shift semantics, generated the complete inventory, ran the actual
  Stage12H copied-store rollback, and exposed the storage performance blocker.

The preserved catalog content remains authoritative: Potency15%, no Swift
Recovery content,87 skills/66 passives. The Stage13 changes are not a rebalance
of resource formulas, regeneration, attribute scaling or native HUD ownership.

## Every skill and passive

The complete readable inventory is
[coverage.md](../../evidence/stage-13/cohort-f/hardening/coverage.md), with the
machine-readable equivalent
[coverage.json](../../evidence/stage-13/cohort-f/hardening/coverage.json).
It lists each of87 skills and66 passives individually, including exact IDs,
families/operations, evidence disposition and limitations. Do not interpret
83 enabled profile records as83 connected-proven or fully unrestricted skills.

Whole-skill exceptions:

| Skill | Earliest unresolved boundary | Honest disposition |
|---|---|---|
|Frenzy|Per-actor native basic-attack cadence and held stance lifecycle|Disabled before payment. Canonical parameters recorded; no implemented upkeep/stacks/native rate lease. Unresolved integration, not universal engine impossibility.|
|Guard|Native held main-hand reaction and release route|Disabled before payment; shipped Wielding control audited, not yet safely integrated.|
|Snipe|Connected U hold/effect/arrow/straight-flight verification|Correction U preserves T's held-release path, changes gravity to zero per owner instruction, references the exact shipped arrow model and reuses native yellow charged-bow emitters. 85 m/s and authored 48 m cap remain. Connected presentation/trajectory remains unverified; see stage-13-snipe-native-visuals-report.md.|
|Bone Cage|Enemy-only native collision/protection contract|Explicit master-authorized safety disable; no decorative cage masquerades as confinement.|

Other limitations must remain visible: native ranged/projectile-parent basic
recovery is not part of the melee-only observer; Flame Weapon's native basic-hit
contact and Pedanticism's native enemy cooldown-progress adapters are limited;
Revive/ Dominate native source coverage is restricted; party membership defaults
to SOLO_ONLY without a trusted adapter. Global C/K binding and native Inventory
extension are not fabricated. No current Ability4 support is claimed.

The final requested release classification is not complete for the three
unverified native integrations. The inventory deliberately uses an explicit
implementation-blocked disposition instead of falsely assigning them to a
proven-capability or completed category.

## Progression and persistence

Implemented/backend-tested: canonical64-bit cumulative XP and half-up formulas,
multi-level awards of5 unspent and5 pending points per earned level, mastery,
Insight spending, source-specific pity and acquisition transactions, quiet-window
respec, fixed-layout build transfer, contribution/anti-farm ledgers, permanent
encounter exclusions, reward event identity and crash-recovered exactly-once
transactions. Earned rewards do not fabricate combat trace phases.

The installed audited registry contains1053 NPC roles; only three have approved
pilot RPG XP mappings (Wolf_Black, Trork_Warrior, Skeleton_Archer), and only four
native biome bindings are currently used. The other1050 roles remain XP-disabled.
These are not invented vanilla enemy levels. Proposed source names are not
verified skill-learning sources: there are **zero connected-verified acquisition
bindings**. Unknown enemies cannot silently award XP/skills. Actual healing and
absorption credit use observed outcomes, not requested amounts.

The Stage12H local closure proved formulas and crash/idempotence mechanics. It
did **not** measure the newly discovered high-frequency storage workload. This
distinction is now explicitly appended to the Stage12 report.

## Tests and performance — keep the gates separate

Latest retained suite: **1886 tests**,1837 RPG +28 native-control +21 CanvasUI,
zero failures/errors/skips. All Stage12H baseline classes/counts were retained.
Compilation and CustomUI source/archive checks passed. The complete matrix
contains5742 skill/passive cells,2145 passive pairs,1000 valid six-Link property
graphs, with no unresolved numeric-profile exceptions. Whole-skill runtime gates
are still explicitly included, not counted as successful native executions.

The isolated exact-three-mod smoke booted the network and shut down cleanly on
`2026-09-09T11:51:19.8090271Z`. No client joined it. All earlier packaged UI bytes
match the Stage12H archive; native resources, Signature and XP artwork remain
untouched. XP remains702×28 frame,696×28 background,1×22 fill, left anchored,
Background→Fill→Frame, with no extra XP text.

The **separate release-readiness command fails**. Real synchronous durable
contribution writes for four actors ×16 victims (64 accepted synthetic updates,
60 samples) measured:

| Run | p50 ms | p95 ms | p99 ms |
|---|---:|---:|---:|
|Targeted baseline|189.6511|270.7072|3349.7013|
|Full retained run|187.8359|731.5307|4053.5935|

Machine: Ryzen9 7900X,12 cores/24 threads,63.15GiB RAM, Windows11, JDK25.0.4;
JUnit's temporary C: directory. Samples are retained, including cold-path and
system-contention costs. This excludes native physics/AI/network/rendering and
is **not** a connected or complete base-game-versus-RPG tick benchmark. It is a
real component cost that already exceeds the nominal4ms p95/8ms p99 budget.

Cause: native Inspect/control/healing calls enter `PersistentEncounterRuntime`,
which writes a fully validated, checksummed, forced, atomically replaced
encounter snapshot for every accepted contribution. Mastery/reward saves are
also synchronous. It is a shared architecture problem, not an optional refinement.
The exact recorded boundary is
`SYNCHRONOUS_DURABLE_ENCOUNTER_CONTRIBUTION_EXCEEDS_RPG_TICK_BUDGET`.

No volatile-only queue, skipped fsync, removed test or relaxed expected value was
introduced to make this pass. Moving writes to a worker without a durable ordered
handoff would not preserve existing crash guarantees. This correction still
needs implementation. Required properties are ordered capture/admission through
death and mastery, bounded overload, durable acknowledgements, crash/cancellation
recovery, no background ECS access, and no duplicate/lost accepted rewards.

The combined four-player workload (16 hostiles,24 projectiles/8 fields/8 summons
per caster), scaling profile, full remaining fault matrix and final RC reruns are
not complete. Historical bounded family tests remain useful but do not substitute
for those gates. Inspect
[release-readiness.json](../../evidence/stage-13/cohort-f/release-readiness.json)
for explicit failure reasons and machine details.

## Native input and connected checklist

The owner's R023 control confirmed that native Fireball visibly cast while the
packet watcher saw no input. Installed Quiche execution bypassed that watcher.
R024 (`5c5e55e`) moved the bridge to native server interaction execution; later
code retains that architecture and zero native cost/cooldown. **R024/R032 RPG
casting is not yet proven by a connected owner session.** R022's static remote
synchronization audit never proved packet delivery.

Only after the blocking local work and an approved copied-world deployment:

1. Preserve/reconcile the retained R023 control journal, including its interrupted
   second-control recovery state; do not delete temporary-item ownership records.
2. Join, open `/rpg skilltree`, equip Quick Slash and Fire Bolt; inspect
   `/rpg dev ability-status`. Confirm native Ability2/3 projection and unchanged
   native Signature/resource presentation. Do not fabricate an Ability4 binding.
3. Press E/R once each. Require real input→request→validation→commit→dispatch,
   projectile spawn for Fire Bolt, authoritative target Health loss and exactly
   one resource cost/cooldown. Record both positive and invalid/cancel cases.
4. Run per-skill/per-passive connected cases from the master: geometry/LOS,
   status/crit, owned cleanup, meaningful mastery, actual4%/12% recovery, no
   duplicate native/RPG damage, parties/protection, multiplayer, death/logout/
   world-change/unload, source acquisition, XP level-up and persistence/rejoin.
5. Review all HUD/pip/animation/template rendering. Native Stamina's temporary
   visibility must be preserved. Canvas dragging/panning is not an implied feature.
6. Use full session JSONL, not sampled console counts. From F onward, console
   output is rate-limited and traces have explicit drop/failure metrics. A
   `TRACE_GAP` or missing rotated segment invalidates absence-based conclusions.

No client observation, current input receipt, visual quality, animation timing,
authoritative connected damage or live restart/rejoin was fabricated in this run.

## Rollback and operator runbook

The complete Stage13 A–F and Stage12 A–H archives remain under `evidence/`.
The new [F artifacts](../../evidence/stage-13/cohort-f/artifacts/) contain exactly
three mods. The retained Stage12H RPG rollback hash is
`C55DD5C1A939E5727AD01945FDC6DC0B7D7ECF1C185D94AC95B0BDAE885EB87B`.
Older schema8 Stage12G rollback hash is
`3DBB0CFC764CC68C1D29ABA20915C48286D6E8B640738466C6263FA333AAEF78`.

Two actual archived-class drills pass: the retained G schema8→9 upgrade with
rejected in-place downgrade; and the new H coordinated copied checkpoint with
current-code reward advancement followed by byte-exact restoration, old-reader
XP10, duplicate reward rejection and permanent encounter exclusion. These are
copied-store tests, **not a connected world rollback/rejoin test**.

Before any eventual deployment: obtain operator approval, stop the target world,
copy the full world/mod/plugin-data checkpoint (not just a player JSON), preserve
native inventories and control journals, verify all manifest hashes and exactly
three JARs, and test the candidate in an isolated copied world first. Keep player,
earned-reward, encounter and corpse/control stores coordinated. Never downgrade
schema in place or rewind only one reward ledger. Roll back the full checkpoint
with the matching old mods; preserve the failed copy for diagnosis. Do not replay
journal rewards with different event IDs. No destructive live command is supplied
or required for reviewing this checkpoint.

## Required next work, in order

1. Correct the measured shared durable-contribution/native-callback boundary;
   retain identical reward/persistence safety and rerun its load and fault tests.
2. Resolve or establish precise supported/unsupported native contracts for the
   remaining skill integrations; do not label implementation gaps engine limits.
3. Complete the master combined/scaling load and fault-injection matrix, including
   native basic ranged attribution and teardown. Keep connected gates unverified.
4. Run the complete retained suite, isolated three-mod smoke, archive/package and
   actual rollback at Stage13 closure **and again** for the final RC. Update all
   evidence and this report. Only then may local status become
   RELEASE_CANDIDATE_AWAITING_CONNECTED_QA; never RELEASED/PASS without connected QA.

This report makes the remaining wall explicit. It does not waive it in response
to the request to finish quickly.
