# Stage 13 native persistence handoff — pre-implementation stop audit

Date: 2026-09-09. Requested task: `Stage_13_Nonblocking_Persistence_Codex_Task.md`.

## Result

**BLOCKED_PENDING_NATIVE_DAMAGE_CONTINUATION_CONTRACT. No nonblocking candidate was implemented or built.** This invokes the task's explicit source-audit stop condition, not its candidate-success handoff. Stage I's implementation and all historical evidence remain unchanged. Isolated connected-QA eligibility is **NOT_ELIGIBLE**; actual native tick performance is **NOT_MEASURED**; release readiness remains **BLOCKED**.

Starting and current implementation HEAD: `efe9e159b13de005a67a69bb90b84066d0e4efd6`, containing validated implementation `f5f9cb3f9859b55ca263d85f9d63a099933512eb`. The branch was already `RPG`; it was not reset. The only pre-existing worktree change was untracked owner `art/`, which is preserved. This audit adds documentation and read-only diagnostic tooling/evidence only. Its publication commit is available in this file's Git history and the final handoff; it does not identify a new runtime build.

Unchanged Stage I RPG archive SHA-256: `0082FA775EB2C7C42445194E3E0736515ED21CBCEF77BEC42E04ECA1CFBF3D36`. No live JAR installation, save conversion, rollback, connected session, or hardware/security/cache change occurred. Work stayed in the C: GitHub repository, not Google Drive.

## Requirement/call-path ledger, recorded before implementation

The source audit read the pinned master §12.3, G/H/I reports, current release-readiness script, the durable workload, encounter runtime/store/group/effects sources, native encounter callbacks, player authority/read paths, and directly reached support/cooldown/damage/reward collaborators. These are **current source facts**, not claims that a proposed handoff exists.

| Boundary | Current implementation fact | Required correction / status |
|---|---|---|
| Master tick budget | §12.3 specifies RPG simulation added wall time per real server tick: <=4 ms p95 / <=8 ms p99 | Preserve exactly; native gate NOT_MEASURED |
| Synthetic durability sample | 64 submissions and all 64 durable receipts are timed; scope explicitly excludes native ticks | Retain unchanged diagnostic and historical nominal 4/8 comparison; not a native tick gate |
| Native damage/control/heal/absorb credit | Asynchronous contribution submissions plus the existing bounded single effects worker | Keep WAL v2 and force-before-durable-completion; remove indirect IO-owned locks |
| Native death | `died` calls `durableEffects.await`, then contributor/death methods that wait on storage | Capture finite dependencies and event-time native facts; prepare/freeze asynchronously; not implemented |
| Native delivery | One global cycle per second calls `await` and `runtime.drain(8)` | Preserve cadence/budget; enqueue slow delivery and poll only ready results; not implemented |
| Encounter attach/detach/conversion | Attach loads/creates persisted context; detach/exclusion wait; synchronized runtime protects both slow and fast work | Explicit readiness/generation and short ledger ownership; not implemented |
| Earned mastery/rewards | Existing effects worker calls `awardEarned`/`awardGenerated`; player holder stays locked across durable repository work | Publish immutable committed read view with fail-closed status; not implemented |
| Player hot reads | `characterLevel`, `rawAttribute`, `masteryXp`, presentation/loadout getters reach the same holder, including lazy load/recovery | No IO, future wait, or IO-owned monitor in hot reads; not implemented |
| Cooldowns | `spendCharge` saves before executor permission; periodic checkpoint/detach also save under the cooldown monitor | Requires pending cast/save receipt ownership, not merely a background `save` call; not implemented |
| **Managuard and Shared Aegis** | **Support deficit must be saved before returning reduced incoming native damage** | **The native hit needs a verified suspend/resume continuation; this is the stop boundary** |

No separately applicable owner-authored acknowledgement-latency SLO was identified beyond the earlier 64-damage directives that this task expressly supersedes. The actual master text matches the supplied four-player/16-target/24-projectile-per-caster/8-field-per-caster/8-summon-per-caster description. Intended-deployment scaling remains separate. The five-second operational failure deadline is not a normal latency target.

The correct eventual measurement mapping is:

- `NATIVE_RPG_TICK_WORK`: actual owner-thread work including lock waits and ready-result processing; **4/8 gate, NOT_MEASURED**.
- `DURABLE_ACK_LATENCY`: unchanged submission-through-force/receipt measurement; diagnostic, not native tick evidence.
- `END_TO_END_PROGRESSION_LATENCY`: event through persisted mastery/reward and owner-thread publication, including delivery cadence; must be measured separately, with backlog/deadlines reported.

The old release-readiness script still conflates the synthetic result with the tick gate. That source fact is explicitly recorded here; this audit has **not** silently edited old results, marked that script passing, or claimed the requested implementation mapping change is complete. The eventual coherent correction must change current mapping explicitly and leave historical G/H/I JSON intact.

## Exact stop boundary: native shield absorption

Production path for the player's Managuard:

`HytaleSupportSystem.Absorb.handle` -> `SupportRuntime.absorbDetailed` -> `SupportProgressStore.save` -> `RpgLoadoutService.mutateSupport` -> `FileRpgPlayerStateRepository.save` -> `FileChannel.force(true)` -> return reduced remainder -> `damage.setAmount(remainder)` -> native `DamageSystems.ApplyDamage`.

`SupportRuntime.absorbShared` enforces the same deficit-before-reduced-damage rule for Shared Aegis. This is not an optional trace or eventual reward. It decides the amount of **the currently executing native hit**, including whether that hit kills the player.

The native binding is real: `HytaleSupportSystem` binds `SupportProgressStore.save` directly to `loadouts.mutateSupport`. `Absorb` is ordered after the filter group and before native ApplyDamage. The source explicitly says that failed persistence must leave the original native amount unchanged. The retained tests enforce no unrecorded shield on a failed save:

- `Stage09SupportRuntimeTest.failedDurableAbsorptionCannotGiveFreeShield`
- `Stage09FinalSupportPassivesTest.failedDurableSharedAbsorptionGrantsNoShield`
- `Stage09CooldownPersistenceTest.failedStartSaveDoesNotPublishAnUnrecordedCooldown` is the related cast-before-durable-debt contract, not a substitute for the absorption issue.

### Installed API evidence

The exact installed pre-release server JAR was rehashed to `EC57E9BD6E2CA3CB16CC5883D42B04A0C64D382DEE532C5BC1CFCF68421E1EE3`, matching Stage I. Public signatures and bytecode were inspected with the installed Java 25 `javap`:

- `EntityEventSystem.handle` returns `void`, not a completion/continuation.
- `CancellableEcsEvent` exposes cancellation, not suspension and resumption.
- The exposed `ComponentAccessor`, `Store`, and `CommandBuffer` invocation methods start event invocation; the inspected API has no resume-after-this-filter operation or continuation token.
- Every `DamageSystems.executeDamage` overload delegates to normal event `invoke`. It is not a mid-pipeline continuation.
- Native `DamageSystems.ApplyDamage.handle` rounds the event amount, subtracts authoritative Health, and may add `DeathComponent` within that call.

This audit establishes **no verified supported continuation at the required point in this installed API**. It does not claim that every possible future native integration is impossible or that no custom design could ever be reviewed.

### Why an effects-worker change alone does not close this

If absorption saves on a worker and the callback waits, the native path is still blocking. If it returns the reduced amount before the save, it violates deficit-before-shield authority. If it returns the original amount and later heals/refunds Health, an intervening death or downstream native side effect cannot be safely undone. If it cancels the hit and later calls `executeDamage`, it starts normal invocation again; it is not proof that earlier filters/side effects, attack metadata, event ordering, and post-hit consumers run exactly once. A readiness check does not resolve any of these transitions.

The task correctly permits leaving an irreversible operation pending. The unresolved part is **how this native damage operation resumes safely after filtering**, rather than replaying a second hit or writing Health through a competing subsystem. Silently inventing that contract would make the claimed closure depend on unverified native behavior—the exact problem the task forbids. Leaving absorption synchronously blocking while changing encounter rewards would also fail the requested combat-path closure.

The conflict is therefore between the retained immediate native damage/deficit durability contract and the new blanket no-wait native hot-path requirement **without an established native continuation mechanism**. This is a named native integration contract, not another WAL optimization problem.

## Minimum decision needed before implementation resumes

Establish a separately reviewed native pending-damage continuation contract for durable shield decisions. It must preserve the original event's filtering/metadata, exactly-once native side effects, Apply/Inspect order, subsequent-hit ordering, death behavior, lifecycle generation checks, bounded capacity, failure behavior, and removal/unload semantics. A new native callback/hook may be necessary; an ordinary `executeDamage` replay has not been shown equivalent.

Alternatively, the owner would have to explicitly change the shield durability/gameplay contract or narrow the correction's scope. Neither is assumed here. No early shielding, conservative extra deficit, cancelled accepted hit, disabled Managuard, relaxed test, or second damage engine was introduced.

Once this boundary is resolved, the proposed encounter flow remains the requested one: native immutable death observation and finite contribution/mastery frontier; worker persistence of the existing death plan; existing exact-once reward/cursor protocol; bounded ready-result publication on the owning thread. RAM pending observations must never be called persisted death plans. No part of that proposed flow is represented as implemented by this audit.

## Validation and candidate status

Targeted validation: **59 retained tests passed**, zero failures/errors/skips: 22 `Stage09SupportRuntimeTest`, 27 `Stage09FinalSupportPassivesTest`, and 10 `Stage09CooldownPersistenceTest`. The target is the retained support/shared-shield/cooldown safety tests, not the full retained suite or a new latency qualification. Static native API and source-route evidence is captured by `tools/Test-Stage13NativePersistenceStopBoundary.ps1`; it is explicitly not connected evidence or a successful held-force nonblocking test. [Machine-readable audit](../../evidence/stage-13/native-handoff-audit/audit.json), [installed signatures](../../evidence/stage-13/native-handoff-audit/native-signatures.txt), [installed damage bytecode](../../evidence/stage-13/native-handoff-audit/native-damage-bytecode.txt).

The initial unqualified Gradle `test --tests ...` command successfully ran these root tests, then failed because it also applied the same filters to CanvasUI, which has no matching tests. The corrected `:test --tests ...` command succeeded and reused the already successful root result (`UP-TO-DATE`); it did not rerun those 59 tests. This was task-selection correction, not a skipped CanvasUI regression or a suppressed test failure. The unrelated full CanvasUI suite was not requested during this targeted audit.

No coherent changed runtime candidate exists, so the expensive full build, 48 crash cases, three-mod smoke, archive recapture, rollback replay, durable-load rerun, and 40,000-force qualification were **not repeated**. Stage I's 2,053-test/48-crash validation remains historical evidence of its exact unchanged binary, not a passing validation of this requested nonblocking correction. No tests were removed, skipped, weakened, or added to lock in the defective blocking behavior.

The requested new held-force/held-player-save nonblocking integration tests, lifecycle handoff, immutable hot read view, real native tick instrumentation, lag/backlog metrics, and isolated QA profile are **NOT_IMPLEMENTED/NOT_RUN**. They are not inferred from existing source audits or tests. This audit stops before a partial architecture migration, as directed by the task's incompatible-contract stop clause.

Historical I diagnostic: 3,840 updates / 119 contribution forces; 4.5529 / 7.8891 / 16.1037 ms p50/p95/p99; nine checkpoint-related forces. These remain diagnostic history, not a new run and not evidence that the actual native 4/8 tick gate passes or fails. Reward/publication lag has not been newly measured.

Stage I's exact archive and rollback artifacts remain untouched. Since no runtime or authority format/semantics changed, no new compatibility claim or live conversion is needed. Native integration, combined battle/scaling, master fault matrix, and final release-candidate validation remain unresolved. The audit stops here pending the native damage continuation decision; it does not call this the last possible defect.
