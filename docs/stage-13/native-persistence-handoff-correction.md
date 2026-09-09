# Native persistence handoff correction — cohort J

2026-09-09. Local correction complete; **ELIGIBLE_FOR_ISOLATED_CONNECTED_QA**, not release ready. Stage 13 remains **BLOCKED**; its earliest outstanding qualification boundary is **NATIVE_RPG_TICK_WORK_NOT_MEASURED**. No connected playtest or live installation was performed.

Candidate: R032 / 0.0.25, distinguished from older same-version development builds by cohort and SHA256:
`529F7601D69F9DBDA54ABAD87FBF2C674A31C3944E641602AE1BC79225A696AC`.
Archive: [exact three mods](../../evidence/stage-13/cohort-j/artifacts/).
Publication identity is the RPG commit containing this report and its checksum manifest; no self-referential commit hash is manufactured inside the report.

Starting checkpoint: `d28a9f2006f4bdd56878b3c81637ba1eb9d691dd` on `RPG`.
The previous stop audit is historical. The owner's subsequent instruction explicitly replaces the per-hit Managuard/Shared Aegis save requirement with pre-durable absorption authority. No native damage continuation is proposed or required.

## Before editing: facts and intended changes

| Existing source fact | Correction to implement |
|---|---|
| Both shield hit paths save a deficit before reducing native damage | Persist escrow debit before publishing capacity; hits spend memory-only authority. Restart has no escrow and retains the debit. |
| Support recharge/checkpoint can write a lower deficit | Separate actual connected deficit from durable reserved deficit; checkpoint includes all outstanding authorization. Never reclaim spent authorization or grant from an unfinished save. |
| Encounter lifecycle waits under its ledger monitor | Capture immutable finite work and release ledger ownership before any storage operation. |
| Native callbacks await the ordered effects tail | Reserve bounded jobs before mutation; capture predecessor before enqueue; poll completed receipts only. |
| Player reads acquire an IO-owned holder lock | Publish a defensive committed read view after successful persistence, with explicit readiness/uncertainty. |
| Support/cooldown mutations may block native execution | Keep the particular durable-dependent action pending; worker jobs contain values only; native completion remains owner-thread work. |
| Synthetic 64-update acknowledgement latency is mapped to the tick budget | Preserve workload and historical nominal comparison; actual native tick 4/8 ms gate remains NOT_MEASURED. |

WAL v2, `force(true)`, existing deadlines/capacities, exact-once rewards, formulas, native damage execution, HUD and ability integration remain unchanged. Unused shield authorization may be forfeited after abnormal termination; it is never restored as uncharged capacity. No live deployment or connected QA is authorized. Targeted validation precedes one coherent-candidate full validation.

## Implemented boundaries and reasoning

The initial ledger above is retained as the before-edit record. Stage I's published checkpoint is `efe9e159b13de005a67a69bb90b84066d0e4efd6`, implementation `f5f9cb3f9859b55ca263d85f9d63a099933512eb`. Work continued from the newer audit HEAD, not a reset. The installed adapter is 0.7.0-pre.1, build `e8b4d191fc98a977bf5546a951a7b25473d323e3`.

| Native caller / former wait owner | Implemented handoff |
|---|---|
| Encounter damage/mastery; ordered effects tail | Reserve WAL/effect capacity, capture value inputs and finite predecessors, return provisional receipt. Mastery runs after covering force on the ordered worker. |
| Death callback; effects tail, runtime monitor, freeze IO | Capture position/presence/party/native time now; mark finalizing with an owned ticket. Resolve progression after prior mastery; freeze and deliver only on the worker. |
| Delivery tick; `runtime.drain(8)` and player reward IO | Schedule one outstanding worker delivery cycle globally, at most eight attempts per second across worlds. Tick performs bounded coordination only. |
| Tracking removal / attach; durable tail or store load | Removal drops native ownership, not immutable owed work. Async context readiness retains bounded event descriptors until load completes. |
| Conversion; exclusion tombstone save | Immediate non-admission, finite predecessor capture, durable exclusion receipt; native conversion stays pending until owner revalidation. |
| Player-ready / HUD / combat stat getters; IO-held `Holder` monitor | Preload IDs asynchronously; explicitly gate readiness. Hot getters read defensively copied committed state without that monitor or lazy recovery IO. |
| Support / cooldown; synchronous player save | Value-only submissions; pending cast/teardown tickets. Complete on a fresh owner-thread port after receipts; cancel/refund work retains ownership if admission is full. |
| Shield hit callback; deficit save | Spend only previously durable escrow. No persistence submission, future wait or damage continuation in a hit. |
| Child mastery binding; root budget monitor held across earned reward save | Atomic immutable primary binding; native reads/binding do not acquire the award writer's monitor. Award ordinal/dedup serialization remains unchanged. |

Existing synchronous repository/runtime APIs remain for explicit off-native operations and retained fixtures; production combat/lifecycle routing no longer calls their blocking variants. Native support/cooldown bindings deliberately reject accidental synchronous saves. Writer serialization remains intact: no unlock/write/relock stale overwrite protocol was introduced.

### Pending versus durable lifecycle

```text
owner: validate facts + reserve bounded work
   -> ordered provisional contribution / pending death / pending cast
   -> RETURN (not a durable-success announcement)
worker: finite predecessor -> existing WAL append + covering force(true)
   -> ordered mastery -> immutable death plan force
   -> existing earned intent -> player checkpoint -> delivery cursor / receipt
owner: inspect completed result -> revalidate current world/session/plan
   -> bounded native application or committed-state publication
```

No worker receives a `Store`, `Ref`, component, command buffer or world-capturing native port. Death-time facts remain death-time facts; level/WIS/learning that must include prior mastery are resolved after the ordered dependency, not prematurely frozen. New unrelated hits cannot extend a captured frontier forever. Entity removal does not cancel a frozen reward. Duplicate exclusion retains the same receipt and cannot reopen an identity while its tombstone is pending.

An unacknowledged death observation is still RAM, not a recovery log. A crash after contribution force but before death-plan force retains those contributions but does not invent a durable death plan. After plan force, recovery must deliver the owed reward exactly once. This preserves the documented unacknowledged-event boundary; native callback return is never mislabeled durable plan success.

### Shield escrow contract

`ShieldEscrow` uses the existing saved `ManaguardLedger` deficits as conservative crash debits. Before authority publication, the saved owner deficit covers the full validated owner capacity, and the saved shared deficit covers its existing half-capacity pool; older larger debit floors are not erased by a smaller maximum. This changes authorization timing, not authored shielding/resource formulas.

The running session separately retains the actual deficit and a finite owner/shared credit balance. A successful matching receipt replaces credit; it never adds another copy. Hits spent while a replacement debit is pending are subtracted before the new allowance is published. A pending or failed write grants nothing. A hit can consume only the lesser of durable credit and actual remaining authored shielding.

Recharge, allocation and toggle saves preserve the outstanding debit. Clean teardown revokes credit first, then queues the actual deficit settlement behind already admitted writes. A late receipt from a revoked generation cannot reactivate shielding. While settlement is pending, reconnect cannot install another session using the same capacity. After abnormal termination, no memory credit survives: persisted debits may forfeit unused capacity, never restore free shielding. The same rule applies to Shared Aegis; no suspend/resume native API is assumed.

### Capacity, uncertainty and shutdown

The retained WAL limits remain: 256 pending operations, 64 per context, 64 records / 256 KiB per group, 16 KiB per record. Effects and each reused player/context work queue admit at most 256 jobs. Their conservative captured-value reservation is 256 KiB per job (64 MiB at full admission), not a measurement of actual heap usage. Captured participant count is bounded to 256, support contexts to 64, identities to their validated lengths. Context/tombstone ownership is bounded to 4,096; pending death plans/tickets to 256. The pure transition executor has a derived finite bound of 25,088, not an unconstrained work queue. Cast, conversion and player-readiness tickets are bounded to 256. Support session plus retained settlement ownership is bounded to 256; completion/adoption polling is bounded. Diagnostic result capacity is separate from reward authority.

Admission occurs before provisional mutation. If an already applied native event cannot be captured safely, relevant progression fails closed; it is not retrospectively called an unobserved hit and cannot mint rewards from an incomplete ledger. Genuine persistence uncertainty preserves `ENCOUNTER_PERSISTENCE_UNCERTAIN_RESTART_REQUIRED`. A five-second deadline marks failure/uncertainty, not cancellation of a physical write or an acceptable normal-latency SLO. Late physical completion remains recoverable and is not blindly retried. Shutdown stops admission and drains only in shutdown ownership, with bounded deadlines and cleanup in `finally` blocks.

## Requirement mapping and measurements

Pinned master SHA256: `750483855846FF6DB2564B4D6D626C2A12F1AC6EBBBBF12232B2C3ECCF667010`.
Section 12.3's **4 ms p95 / 8 ms p99** requirement is RPG simulation added wall time per **actual server tick**, in its declared battle. The authoritative correction explicitly supersedes the earlier synthetic acknowledgement interpretation. No independent replacement acknowledgement SLO is invented. G/H/I reports and results are unchanged historical evidence, including their unfavorable samples.

| Measurement | Cohort J evidence / status |
|---|---|
| Actual native RPG tick work, 4/8 ms | **NOT_MEASURED**; no connected battle was run. |
| Original 60 samples, each 64 submissions plus all durable acknowledgements | p50 **4.6011**, p95 **8.1914**, p99 **19.2184 ms**. Historical nominal 4/8 comparison remains **false**. |
| Individual durable receipts, 3,840 samples | p50 **4.1729**, p95 **7.7464**, p99 **13.8242 ms**; distinct distribution, not the 60-sample test. |
| Journal append, 119 operations | p50 0.1069 / p95 0.1769 / p99 0.2554 ms. |
| Journal force, 119 operations | p50 1.8798 / p95 2.0920 / p99 2.1279 ms. |
| Checkpoint serialization, 3 samples | p50 1.7112 / p95 2.5424 ms; too few samples for a meaningful tail qualification. |
| Checkpoint write, 6 samples; force, 9 samples | Write p95 0.4587 ms; force p95 3.7103 ms. All retained in the diagnostic. |
| End-to-end native event → durable reward/mastery → owner publication | **NATIVE_NOT_MEASURED**. Instrumented separately, including the one-second delivery cadence. |

Stage I's corresponding 64-update result was 4.5529 / 7.8891 / 16.1037 ms. J did not improve that diagnostic; this correction removes native waiting rather than optimizing the disk protocol. Both runs used 119 foreground forces for 3,840 records and nine checkpoint forces. J recorded peak 64 pending records/operations, checkpoint backlog one, zero admission rejections and all 3,840 successful receipts. This short workload does not establish sustained connected throughput or absence of growing reward lag. Percentiles from different phase distributions are not added together.

`NativeRpgTickMetrics` uses the installed `World.getTick()` and owner-thread check, not a synthetic loop counter. Exclusive nested spans cover execution, damage, status/fields, support, summons, progression, HUD and lifecycle callbacks. The prior whole-world duration is paired only with an adjacent native tick, based on inspected `World.tick` / `TickingThread.run` order. The reported non-RPG remainder is **not** a clean vanilla baseline: it contains uninstrumented native/framework work and background contention. The QA procedure requires a separately recorded baseline and whole-server health. Missing ticks, trace loss, rejected diagnostic worlds or missing publication samples invalidate claimed coverage rather than counting as zero cost.

Metrics expose pending/reserved bytes, receipt age, death and transition tickets, rejection/failure counts, checkpoint/WAL backlog and bounded owner-publication lag. Publication means the owner observed committed state, not that a client rendered it. Recovered events without a process-local origin timestamp explicitly report unknown origin.

## Local validation

**2,087 tests**, zero failures/errors/skips: all **2,053 Stage I identities and retained assertions** plus **34 new cases**. The full `gradlew build` ran once, including the original durable workload, native-control and CanvasUI tests. Final review then corrected two narrow cross-world bookkeeping races (atomic readiness capacity admission and concurrent conversion receipt removal). The affected 156-test subset passed; no persistence/benchmark source changed. [Run chain](../../evidence/stage-13/cohort-j/validation/run-chain.json) records the pre-review full-build hash, final hash, changed classes and replacement test results. Unaffected full results and affected rerun results are explicitly composed, not represented as a second full run.

- Held **actual WAL force**: production-routed damage, death, delivery, detach and unrelated reads return under the watchdog while force remains held; no early durable mastery/reward; release proves ordered one-time delivery.
- Held **actual player repository / Holder**: hot loadout, presentation, attributes, level and mastery reads continue. Failed writes withdraw progression authority. A real earned award holding the mastery budget cannot block native primary binding/read capture.
- Held **context/checkpoint authority IO**: already admitted context events survive readiness delay and entity removal; no synchronous native store load.
- Pending and failed support/cooldown receipts, duplicate publication, stale owner, failed second admission, cancellation/refund saturation and reconnect settlement are covered. Two worlds share the one-second/eight-attempt global delivery budget; exclusion remains idempotent; overflow rejects before a new watermark; shutdown drains off-owner.
- **48 retained + 7 new real process-halt cases**. New boundaries: before debit, debit before publication, owner hit, shared hit, clean settlement, contribution durable before death plan, and death plan durable before delivery. Each new case exits with `Runtime.halt(73)` and is reopened three times. This is process-crash evidence, not simulated physical power loss.
- Exact final JAR: isolated loopback/offline **three-mod smoke PASS**, clean boot and shutdown, all retained asset registrations; **32 source UI documents / 10 packaged RPG UI documents** statically validated. Native triggers remain 87 zero-cost, zero-cooldown bridges. Protected content/UI bytes remain unchanged; no new native damage/HUD/input mechanism.
- Archive, source hashes, catalog/matrix checks and copied rollback readers pass. Matrices retain 5,742 skill/passive cells, 2,145 passive pairs and 1,000 valid six-link graph cases. Counts remain 87 skills / 66 passives.

The release-readiness script intentionally exits nonzero after writing `BLOCKED` with isolated QA eligibility. That is the retained release gate working, not a failed local correctness suite.

## Rollback, handoff and remaining gates

Stage I's exact JAR is preserved under `cohort-j/rollback/stage13-i/`, SHA256 `0082FA775EB2C7C42445194E3E0736515ED21CBCEF77BEC42E04ECA1CFBF3D36`. `Stage13EscrowArchiveRollbackTest` loads the actual archived I classes: the full saved debit yields no restored owner/shared capacity; existing earned receipts prevent duplicate awards; the coordinated player/reward/encounter fixture remains readable. Its copied world payload is an opaque fixture, **not a native world reopen test**. That connected copied-world drill remains required before deployment.

Rollback must stop all writers and restore the matching **player + earned-reward + encounter + world** checkpoint with the chosen binary. Never rewind one authority independently or rely solely on unchanged WAL v2. Player schema remains 9; encounter envelope/earned intent remain 1; WAL/checkpoint remain v2. Clean-settlement and conservative crash-debit semantics are tested in the old reader, not assumed from schema numbers.

[Machine-readable qualification](../../evidence/stage-13/cohort-j/release-readiness.json), [verification](../../evidence/stage-13/cohort-j/verification.json), [checksum manifest](../../evidence/stage-13/cohort-j/checkpoint-manifest.json), and [single isolated QA procedure](native-persistence-isolated-qa.md) accompany this report. All artifacts are in the GitHub checkout, not Google Drive. Owner `art/` is untouched. The live three JARs and the previously inventoried RPG-owned data files remain byte-identical; this is not a full world-content comparison.

Remaining gates include the actual four-player native tick qualification, sustained backlog/reward lag, intended-player-count scaling, connected cast/effect/animation/HUD/rejoin evidence, copied native-world rollback, remaining master fault matrix and final Stage 13 RC. Existing bow range, selective Bone Cage collision, Guard release and per-actor basic cadence adapter gates remain explicit; this correction neither solves them nor declares them impossible. No release PASS, casting fix, live deployment or connected success is claimed. Stop here after publishing this bounded handoff.
