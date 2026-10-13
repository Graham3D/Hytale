# HRO Stage 2 — mechanical evidence

Candidate `R262C1HRO2-CANDIDATE`, version `0.2.0-R262C1HRO2-CANDIDATE`, Hytale
`=0.7.0`. The implementation extends Stage 1's recorder, queue budget, events,
contracts, bundle and offline tool. It does not start legacy skill/gear/combat
captures, change gameplay decisions, add a persistence engine, launch a server,
deploy an artifact, or grant connected acceptance.

## Source and integration

Worktree `C:/hyhro2`, branch `codex/hro-stage2`. Parent HRO Stage 1 is
`92ac44fb23c515acd59f2470403dd9feca292b30`. Reconciliation merge
`255a94c1138169b1ab492d813023643eed232616` includes remote main
`ce3865dd5f81bee95003cb5dde8dc2f959bdac2b`, preserving Stage 4's actual native
item-position pickup gate and frozen reward-XP correction. Only README,
baseline documentation and candidate version conflicted; both histories were
retained. The owner explicitly authorized the earlier migration task's main
integration; that does not establish connected QA acceptance.

Main contains Chapter I Stage 4 but does not yet contain HRO Stage 1 or Stage 2.
Stage 2 depends on Stage 1. The dirty primary checkout at `1dfa2cea` remains
preserved; publishing this branch does not synchronize that checkout. The
installed INV1 JAR still embeds `afdf9bfbce9d5fb395bd68848833480a849f0e0b`.
No cumulative official R-number was claimed: this is a scoped provisional HRO2
candidate. The final source, tests and publication state are in
[DEVELOPMENT_BASELINE.md](DEVELOPMENT_BASELINE.md).

## Contracts and proof boundaries

The existing nine predicates remain in place. All contracts retain PASS, FAIL,
BLOCKED, NOT_EXERCISED, INCONCLUSIVE and NOT_APPLICABLE, plus `NO_BASELINE`.
Registration, world/connection/operation identity and loss checks apply before
conclusions. The first supported failure remains sticky. Capture interruption,
loss or operation-budget exhaustion invalidates certainty, including passing
findings. Missing successors are never converted into timeout failures.

| Contract | Production evidence | Conclusion / explicit gap |
| --- | --- | --- |
| GEAR.EQUIPPED | Existing requirement-validated equipment snapshot, candidate item identity, category and revision | Membership accepted/rejected; category does not claim an exact native slot readback. Rejected candidate changes are observed too. |
| AFFIX.MODIFIER | Actual `GearEffectSnapshot.percent` return inside managed attack/skill calculation scope | Single-source percentage points → fraction boundary with item, affix, revision, root and operator. Rolled values alone are insufficient. Multi-source aggregate attribution and uninstrumented consumers remain capability gaps; no complete damage-effect claim. |
| AFFIX.PROC | Existing WA-135/137/138/140/143 signature rolls, sample, probability, selection, target and frozen equipment revision | Miss is legal; selection awaits native outcome. The current signature owner does not expose a correlated native effect acknowledgement. Selected live signature procs therefore remain INCONCLUSIVE, not PASS. Native outcome/immune/settled-missing semantics have matched fixtures, including cross-target rejection. No extra RNG calls. |
| COMBAT.RECONCILE | Existing Gather, stage, accepted, immediately-before-Apply and applied callbacks | Uses the same MATCH/MISMATCH/UNOBSERVABLE implementation as Combat Damage Trace, including overkill and 0.02 tolerance. Existing retained offense results supply scaling/coefficient/modifier/crit stages. Hidden filters, absent pre-snapshot inputs and critical RNG samples are not reconstructed. |
| CYCLONE.ACCOUNTING | R261 forced payment/state receipts, generation/debit, barrier before/after, overflow, actual reflection request/factor, confirmed Mana Before Life readback | Detects duplicate payment/debit and inconsistent barrier/overflow/reflection/confirmed mana accounting. Shared player debit is captured but child-generation attribution is unavailable and INCONCLUSIVE. NPC shared recipients, full capacity-construction provenance and recovery policy transformations are capability-blocked. Reflection-request correctness does not claim target visual or effect acceptance. |
| SPAWN.ADMISSION | Actual natural job rarity decision, fallback, closed world admission gate | Normal rarity is legal. Eligible decision opportunities are counted by encounter. Expected rejection/closed gate is BLOCKED. No polling or new rolls. |
| SPAWN.PROMOTION | Frozen plan submission and existing birth-phase/native publication receipts | Requires the prior matching plan before publication can pass. Compensation is legal. Accepted without successor remains INCONCLUSIVE; failure requires an explicit settled-unpublished observation. Production currently lacks a separate generic negative settlement/readback API, so missing publication alone cannot fail. |
| PERSISTENCE.RECOVERY | Existing player-file checksum after successful atomic save and verified load; committed/replayed Cyclone state scalars | Same subject, schema and revision required. Different revision/schema or missing earlier evidence is INCONCLUSIVE. Player checksum covers the real saved payload; Cyclone fingerprint covers captured state fields, not receipt-set contents. No extra writes, loads, flushes or owner-thread hashing. |

Affix coverage enumerates all `GearCatalog.rollableAffixes()` (including the
extension, not just the historical 160-row list). Each row records DEFINED,
INSTRUMENTED, EXERCISED, OUTCOME_OBSERVED, CAPABILITY_BLOCKED and/or
INSUFFICIENT_EVIDENCE as applicable. A unit conversion observation is not a
native outcome or acceptance of the entire affix family. Identity aliases are
capture-local; durable subject hashes are stable opaque provider-specific keys
for explicit recovery joins.

Natural opportunity counts exclude QA and other provenance categories. A
natural decision counts once per encounter. Publication keeps the frozen
descriptor's provenance (including QA); authored/event/dungeon/summoned/loaded
origins are never relabeled as natural. Their absent dedicated observers are
gaps. The runtime does not establish an independent-trial model or comparable
eligibility denominators, so frequency significance, expected means/variance
and confidence claims are explicitly declined. Twenty normals or zero Elites
is not a regression. No random sampling test is invented from actor counts.

## Capture and offline use

Use the existing operator capture workflow documented in [COMMANDS.md](COMMANDS.md).
Stage 1 actually exposed start/status/stop; this pass adds the brief's `mark`
checkpoint with the same administrative permission, no arguments or save gate.
There are no subsystem-specific commands to run.

Bundles retain all Stage 1 files and add `mechanical.json`: authored affix
matrix, provenance-separated natural decision counts, combat stages/native
results, defensive receipts, publication and recovery details. Detail sections
retain 32 rows each with an omitted count; remaining events are in the bounded
gzip stream and operation index. `findings.json` contains verdicts, first
supported divergence, source owner and evidence locator. This is server-side
evidence, not proof of client rendering.

The existing `hroAnalyze` Gradle task accepts `-PhroBundle=<absolute folder>`,
`-PhroAction=analyze|extract|recover|recovery-join`, optional
`-PhroOperation=<operationId>` for extract, and required
`-PhroLaterBundle=<absolute later folder>` for recovery-join.
Tool entrypoints are `analyze <bundle>`, `extract <bundle> <operationId>`,
`recover <interrupted-bundle>` and the new explicit
`recovery-join <earlier-bundle> <later-bundle>`. Recovery join verifies both
checksum inventories and completeness, requires compatible capture-build
identity and ordered distinct runs, and joins the last earlier save by stable
durable subject. Output is `recovery-analysis.json` in the later bundle.
This compares observed durable receipts only; it is not Stage 3 historical
baseline comparison or an implicit import of legacy captures.

## Bounds and qualification

One existing worker owns JSON, compression, catalog/report expansion and
analysis. Owner hooks copy allowlisted scalar facts only: at most 20 facts,
192 characters per string, 16 KiB shared reservation per event. Truncation is
counted as evidence loss. The existing 16 MiB process-wide shared queue budget,
512-entry HRO queue, 64 MiB heap target, ten-minute duration and disk/retention
limits remain. Thirty-two queue slots are reserved for outcomes/control under
routine pressure. Sampling is explicitly deterministic 1-of-1; no lifecycle
fragments are silently sampled. Pressure drops routine records with loss
evidence, or stops capture at the hard bound. Producers never wait for a lock
or disk. Packet-local combat observers stop admitting at 2,048 per capture;
no global native-entity map is introduced. Analyzer operation state is capped
at 2,048, findings at 128, report detail at 128 rows and opportunity IDs at 2,048.

`HroMechanicalContractsTest` covers all requested failure/control families;
Stage 1's duplicate execution, partial gzip, dropped queue and disk-fault
fixtures remain. `HroProductionBridgeTest` uses installed Hytale types and
real native asset fixtures, existing proc owners and real offline journal
save/replay. It checks identical proc children/draw counts, unchanged damage
packets, inactive legacy captures and unchanged durable bytes on replay.
Example bundles generated under `build/reports/hro/stage2-example-*` are
explicitly synthetic; `stage2-example-path.txt` points to the latest one.

Qualification artifacts are `build/reports/hro/qualification.json`,
`overload.json` and `heap.json`. Timings are offline synthetic producer/owner
batches, with matched disabled/enabled workloads and a blocked-writer overload.
The test-only Instrumentation agent measures a full queue with two-byte strings
and 2,048 saturated mechanical analyzer operations, then adds a conservative
16 MiB allowance for other/transient objects. The agent is not packaged in Hywind.
Actual whole-world tick p95/p99 and client-frame degradation thresholds remain
UNVERIFIED because no matched connected measurements were taken. Stage 3 owns
connected acceptance and final release qualification; no gameplay acceptance
is inferred from these tests.

Final verification results and exact commit/artifact identity are recorded in
the development baseline and ignored exact-HEAD Guardian receipt. No installed
server, Assets.zip, save, artwork or AGENTS.md is modified by this assignment.

## Engineering result

Production implementation commit: `24968cab388ceefee463edc21566c27417ca6c17`,
including foundation `c5e5ae9b35cd181184c3ab19233b7d42d619d1d4`. The final
handoff is documentation-only. The full checkpoint before the final proc
identity refinement passed 4,265 JUnit tests (3,722 root / 489 native /
49 CanvasUI / 5 Taverns), zero failures/errors/skips; strict SpotBugs and
package/asset/CustomUI checks passed. The refinement adds one passing target
mismatch fixture and passed focused native bridge/strict static analysis.
Focused JaCoCo covered 735/832 HRO lines. The final exact-HEAD receipt records
the complete repeated suite for the corrected source.
Coverage is evidence of execution, not native outcome acceptance.

| Offline measurement | Result | Target |
| --- | --- | --- |
| Warmed disabled allocation | 0 bytes | No allocation |
| Active producer p99 | 0.0102 ms | <0.05 ms |
| Synthetic 16-observation owner batch mean / p99 | 0.0234 / 0.1016 ms | <=0.10 / <=0.50 ms |
| Blocked-writer overload producer p99 | 0.0025 ms | <0.05 ms; critical lane retained |
| Saturated queue + analyzer + allowance | 41,300,248 bytes / 39.4 MiB | <=64 MiB |
| Whole-world tick / connected client frame | UNVERIFIED | Requires matched native measurements |

The implementation is engineering-ready for Stage 3. Guardian's final
exact-commit repetition is a mandatory publication gate; its ignored receipt
contains the final JAR hash/source commit. No branch merge, deployment or
connected acceptance is part of this result.
