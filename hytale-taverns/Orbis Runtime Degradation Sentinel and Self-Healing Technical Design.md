# ORBIS RUNTIME DEGRADATION SENTINEL

## Invariant Detection, Bounded Self-Healing, Incident Capture, and Regression Automation

**Subsystem of ImmersiveNPCs / Orbis**  
**Codex Implementation Specification**  
**Version 1.0 | 31 August 2026**

> **AUTHORITATIVE PURPOSE**  
> Extend the hardened Orbis runtime with a permanent control plane that detects contract degradation while it is happening, contains unsafe behavior before it reaches speech, action, or persistence, executes only pre-authorized bounded recovery, and captures a deterministic reproduction for unresolved source defects. Orbis may self-heal runtime state. It may never rewrite source code, rebuild its JAR, invent world truth, or silently mutate durable NPC state in order to conceal a defect.

**LIVE INVARIANT ENFORCEMENT**  
Critical ownership, contract, authority, delivery, resource, and persistence rules are verified at the boundary where violating them would become externally visible or durable.

**BOUNDED SELF-HEALING**  
Recovery uses a versioned allowlist of deterministic actions, shared recovery budgets, circuit breakers, stable degraded modes, and explicit post-recovery proof.

**DURABLE INCIDENT EVIDENCE**  
Every material degradation produces a compact, privacy-safe incident bundle with the exact plan, state, event window, failure signature, recovery attempt, and next-turn outcome needed for engineering diagnosis.

**AUTOMATED REGRESSION CAPTURE**  
Replayable incidents become sanitized regression candidates for the existing ConversationMatrixHarness without generating or modifying source code at runtime.

*This document is written for Codex implementation use. Repository ownership, current Orbis contracts, the installed Hytale SDK, and existing persistent-data formats remain authoritative.*

---

# Contents

1. Implementation Directive  
2. Executive Decision and Failure Model  
3. Scope, Goals, and Non-Goals  
4. Existing Orbis Ownership and Integration Boundary  
5. Architectural Decisions  
6. High-Level Architecture  
7. Degradation Taxonomy and Severity Model  
8. Runtime Invariant Registry  
9. Degradation Sentinel Evaluation Model  
10. Scoped Health and State Machines  
11. Failure Signatures, Deduplication, and Escalation  
12. Bounded Recovery Policy Registry  
13. Circuit Breakers, Quarantine, and Stable Degraded Modes  
14. Durable-State Safety and Persistence Firewall  
15. Incident Capture and Diagnostic Bundles  
16. Regression Candidate Generation  
17. Deterministic Replay and Self-Heal Verification  
18. Performance, Scheduling, and Retention  
19. Native Hytale UI, Trace, and Operator Controls  
20. Failure Handling, Security, and Privacy  
21. Three-Phase Bounded Implementation Program  
22. Sentinel Test Matrix  
23. Connected Hytale Acceptance and Definition of Done  
Appendix A. Proposed Component Map  
Appendix B. Normative Data Contracts  
Appendix C. Initial Invariant Catalog  
Appendix D. Initial Recovery Matrix  
Appendix E. Project Sources and Design Basis

---

# 1. Implementation Directive

This design extends the existing Hardening Matrix and Epistemic Cognition architecture. It is not a replacement for either system, not another conversational runtime, and not permission for the mod to edit or rebuild itself.

## 1.1 Required reading

Before changing code, Codex **MUST** read and reconcile:

- `Orbis Technical Design.docx` and current ownership reports.
- `Orbis Conversational Pipeline Hardening Matrix.docx` or its current Markdown equivalent.
- `Orbis Epistemic Cognition Technical Design.docx`.
- Current implementation reports and active revision through the latest deployed build.
- Recent connected traces and incident reports involving route bypass, prompt-budget drift, claim-firewall bypass, provider degradation, STT corruption, resource starvation, canonical speech faults, and persistence risk.
- Existing `RecoverySupervisor`, `TurnPlanCompiler`, `ContractBudgetPlanner`, `CanonicalSpeechLedger`, `DialogueClaimValidator` or `EpistemicClaimFirewall`, `OrbisResourceScheduler`, provider adapters, persistence queues, trace pipeline, readiness HUD, and Cognition Inspector.

## 1.2 Ownership constraints

Codex **MUST** preserve:

- `OrbisTurnCoordinator` as authoritative lifecycle, branch, floor, epoch, cancellation, and response owner.
- `TurnExecutionPlan` as the immutable execution contract.
- `RecoverySupervisor` as the bounded recovery executor and terminal-cleanup owner.
- `ContractBudgetPlanner` as prompt/schema/output preflight authority.
- `CanonicalSpeechLedger` as delivered lexical truth.
- `DialogueClaimValidator` / `EpistemicClaimFirewall` as the final objective-claim authority.
- `AgentOperation` and Hytale action validation as physical-world authority.
- `OrbisResourceScheduler` as Hytale-first admission, residency, and pressure authority.
- Existing memory, belief, relationship, profile, task, plan, schedule, and persistence owners.
- Existing trace and native Hytale UI as the only operator-observability surface.

The Sentinel observes and coordinates policy. It does not become a second writer for turn state, speech, actions, resources, or persistence.

## 1.3 Implementation discipline

- **MUST NOT** implement this entire document in one Codex task.
- Implement exactly three bounded engineering phases: S1, S2, and S3.
- Each phase targets 15-25 minutes. At the time bound, Codex stops, reports, and preserves a compiling checkpoint.
- Expensive full matrices, long soaks, and connected validation run only at the named gate, not inside every phase.
- **MUST NOT** add sentence-specific patches. Every detection and recovery attaches to a versioned invariant, failure signature, or policy.
- **MUST NOT** add another LLM call to ordinary turns for detection or repair.
- **MUST NOT** let the Sentinel alter Java source, native binaries, scripts, model weights, JAR files, or build configuration at runtime.
- **MUST NOT** let the Sentinel manufacture missing evidence, world state, action results, or persistent beliefs.
- Begin with detection in `OBSERVE`, then promote only proven high-confidence invariants to `ENFORCE`.
- Any recovery that can change externally visible behavior must be deterministic, pre-registered, bounded, and rollback-safe.

## 1.4 Self-healing boundary

Permitted runtime self-healing:

- reject an invalid dispatch before provider work;
- recompile or prune a plan once under an approved policy;
- re-transcribe preserved audio once;
- cancel, drain, restart, or circuit-break a provider;
- remove or replace an unsupported speech clause using an already-authorized `AnswerPlan`;
- preserve a valid delivered prefix and fail the remaining response as `PARTIAL`;
- enter a measured lower-resource profile;
- fail fast instead of repeating a known-broken path;
- quarantine a route, provider, or persistence writer for future turns;
- reject an invalid durable write;
- rebuild a materialized index from a verified snapshot and event tail;
- roll back to a verified last-known-good snapshot when the persistence contract explicitly permits it;
- capture an incident and regression candidate.

Forbidden runtime behavior:

- modify or generate source code;
- compile or replace the active JAR;
- invent a new recovery algorithm with Nemotron;
- silently increase budgets or reduce safety reserves to make a failure disappear;
- convert unsupported model output into truth;
- overwrite Hytale state or authored canon;
- silently switch providers inside an active branch;
- delete evidence or history merely because it conflicts;
- suppress incidents to preserve a misleading `READY` state.

---

# 2. Executive Decision and Failure Model

The Hardening Matrix made the conversational pipeline contract-driven and recoverable for known route, provider, speech, resource, and cancellation failure classes. Epistemic Cognition added semantic and factual authority. Those systems still depend on the implementation continuing to honor their contracts as new features are integrated.

Recent development has shown a remaining failure class:

> A subsystem can compile and pass isolated tests while the live integrated path silently bypasses, duplicates, misorders, or misbudgets that subsystem.

Examples of this class include:

- a supported epistemic route is classified correctly in shadow analysis but the live legacy router still wins;
- a prompt is preflighted before new epistemic sections are appended, so the measured prompt is not the dispatched prompt;
- objective speech reaches a commit path that did not execute the claim firewall;
- startup reaches `READY` from a transient resource snapshot that is not a sustainable operating state;
- provider cancellation completes logically while real provider work continues;
- a valid entity authorizes an unsupported property;
- generated dialogue re-enters memory as factual evidence;
- persistence accepts a structurally valid event whose provenance or revision invariant is broken.

These are not best solved by another large collection of route-specific fixes. The missing layer is a runtime control plane that continuously asks:

1. What invariants are required at this boundary?
2. Can the current immutable state prove they hold?
3. If not, can an approved bounded recovery restore them safely?
4. If recovery cannot be proven, what scoped degraded mode prevents repetition or contamination?
5. What minimal evidence must be saved so the defect becomes reproducible without another manual trace investigation?

The systemic loop is:

```text
Immutable Orbis events and boundary snapshots
        |
        v
InvariantRegistry + SentinelEvaluator
        |
        +--> PASS
        |
        +--> DegradationSignal
                 |
                 v
          FailureSignatureEngine
                 |
                 v
          ScopedHealthProjection
                 |
                 v
          RecoveryPolicyRegistry
                 |
                 v
          RecoverySupervisor / existing owners
                 |
                 v
          RecoveryVerification
             /          \
            /            \
       VERIFIED       UNRESOLVED
          |                |
          v                v
   close incident     circuit/quarantine
          |                |
          +------> IncidentRecorder
                         |
                         v
               RegressionCandidateExtractor
```

The Sentinel is a meta-control layer around existing authority. It does not replace the authorities it monitors.

---

# 3. Scope, Goals, and Non-Goals

## 3.1 Goals

- Detect proven invariant violations before they reach provider dispatch, canonical speech, Hytale action, or durable persistence whenever a pre-side-effect boundary exists.
- Detect trend degradation such as increasing latency, repeated starvation, provider drain failure, or queue growth before the player must diagnose it manually.
- Convert known runtime protections into one versioned invariant and recovery registry rather than scattered checks.
- Execute only pre-authorized deterministic recovery plans.
- Keep the next valid turn immediately usable after a contained failure.
- Prevent repeated identical failures from consuming five-second waits, provider retries, or user attention.
- Introduce scoped circuit breakers and stable degraded modes instead of global shutdown or per-turn flapping.
- Protect persistent beliefs, memories, relationships, actions, and authored state from contamination.
- Produce compact incident bundles automatically without requiring `/npc trace` to have been enabled beforehand.
- Convert replayable incidents into deterministic regression candidates compatible with the existing ConversationMatrixHarness.
- Allow Codex to inspect a concise incident/candidate set rather than reverse-engineer large traces.
- Feed the existing readiness HUD and Cognition Inspector from one authoritative health projection.
- Remain lightweight enough that ordinary turns do not experience meaningful latency regression.
- Apply equally to the current conversational stack and later E4-E8 belief, social, reflection, and autonomous systems.

## 3.2 Non-goals

- Self-modifying or self-compiling software.
- Automatic source-code repair.
- Replacing Codex or human engineering review for novel defects.
- Inferring arbitrary recovery with an LLM.
- Guaranteeing that every model-quality problem is detectable as a deterministic bug.
- Treating an odd but valid in-character response as an invariant failure without a contract basis.
- Hiding failures by loosening claim, budget, action, delivery, or resource authority.
- Creating a second tracing system, web dashboard, database, or cloud service.
- Persisting raw audio, hidden reasoning, secrets, or full prompts by default.
- Automatically promoting a regression candidate into repository source code.
- Running heavy replay, matrix, or model tests during active gameplay.
- Replacing the Hardening Matrix, Epistemic Cognition, or their test suites.

## 3.3 What can and cannot be detected

High-confidence deterministic detection is appropriate for:

- missing or invalid execution contracts;
- prompt-budget mismatch;
- supported route bypass;
- stale epoch or ownership crossover;
- missing claim validation before objective speech;
- action promise without action authority;
- canonical speech range/order mismatch;
- duplicate terminal transition or resource release;
- persistent event missing provenance or revision validity;
- provider request still active after declared drain;
- sustained resource envelope violation;
- repeated identical failure signature;
- known STT partial/final collapse and incomplete-final heuristics.

The Sentinel cannot prove from runtime structure alone that:

- a subjective opinion is boring;
- a joke is not funny;
- a small model could have reasoned better;
- a nuanced social response is emotionally ideal;
- a novel user utterance was semantically misunderstood when no deterministic expectation exists.

Those remain trace, benchmark, corpus, and human-review concerns. The Sentinel should capture suspicious context when an existing contract or metric can justify it, not invent a universal quality judge.

---

# 4. Existing Orbis Ownership and Integration Boundary

## 4.1 Existing owners retained

| Concern | Authoritative owner | Sentinel role |
|---|---|---|
| Turn, branch, epoch, floor, cancellation | `OrbisTurnCoordinator` | Observe events; request scoped containment through existing APIs |
| Route, context, output, budget, deadline | `TurnPlanCompiler` / planners | Verify contract and rendered-budget invariants before dispatch |
| Provider work | Provider adapter and provider lifecycle owner | Detect stalls, ownership mismatch, false drain, health degradation |
| Recovery and terminal cleanup | `RecoverySupervisor` | Select registered policy; never execute competing cleanup |
| Objective speech authority | `DialogueClaimValidator` / `EpistemicClaimFirewall` | Require proof before ledger commitment |
| Canonical speech and delivery | `CanonicalSpeechLedger` / playback coordinator | Verify ordered spans, delivery truth, and stale rejection |
| Physical actions | `AgentOperation` and Hytale validators | Require commit/result evidence; never mutate directly |
| Resource policy | `OrbisResourceScheduler` | Detect sustainable-envelope and repeated-admission degradation |
| Memory, beliefs, relationships | Existing stores | Guard durable writes and capture contamination attempts |
| Persistence | Existing async writers/snapshots | Reject invalid writes; coordinate verified replay/rollback only |
| Diagnostics/UI | Existing Orbis event stream and Inspector | Publish Sentinel health/incidents through existing observers |

## 4.2 Single-writer rule

The Sentinel never mutates authoritative domain objects directly.

It emits immutable commands or requests such as:

- `REQUEST_PLAN_RECOMPILE`
- `REQUEST_PROVIDER_CANCEL`
- `REQUEST_PROVIDER_RESTART`
- `REQUEST_ROUTE_QUARANTINE`
- `REQUEST_PERSISTENCE_WRITE_REJECT`
- `REQUEST_SNAPSHOT_REBUILD`

The current authoritative owner serializes and applies the state transition. Sentinel callbacks cannot race the owner or make side effects from arbitrary provider threads.

## 4.3 Boundary guards versus trend monitors

Two evaluation classes are required.

### Boundary guard

A cheap deterministic check immediately before an irreversible or externally visible side effect.

Examples:

- before provider dispatch;
- before objective phrase commitment;
- before action commitment;
- before durable event append;
- before terminal completion is published.

Boundary guards may block the side effect. They execute on the serialized Orbis control path and must be constant-time or bounded by small immutable data.

### Trend monitor

An asynchronous projection over immutable events and cached telemetry.

Examples:

- rising p95 latency;
- repeated resource starvation;
- provider cancel-to-drain regression;
- queue growth;
- circuit-breaker occurrence rate;
- readiness flapping.

Trend monitors never block Hytale callbacks. They may request a future-turn degraded mode or circuit breaker.

---

# 5. Architectural Decisions

| ID | Decision | Requirement |
|---|---|---|
| SENT-01 | Explicit invariant registry | Every enforced condition has a stable ID, version, scope, boundary, severity, evaluator, and approved recovery policy. |
| SENT-02 | Event-driven state projection | Sentinel state is derived from immutable Orbis events and bounded snapshots, not ad hoc reads from many mutable services. |
| SENT-03 | One authority per side effect | Sentinel requests containment or recovery through the existing owner. It never becomes a second state writer. |
| SENT-04 | Fail closed for authority and durability | Missing proof before speech, action, or persistence blocks that side effect. Diagnostics-only failures do not fail gameplay. |
| SENT-05 | Pre-authorized recovery only | Recovery comes from a versioned allowlist. No model invents a fix. |
| SENT-06 | Shared recovery budget | A turn or response cannot hide a defect through repeated retries across subsystems. Existing one-recovery semantics remain authoritative. |
| SENT-07 | Scoped health | Health and circuit breakers are keyed to the smallest safe scope: request, route, provider, NPC, persistence stream, world, or global runtime. |
| SENT-08 | Recovery must be proven | Success requires the violated invariant to pass, cleanup to complete, and the relevant next-use probe to succeed. |
| SENT-09 | Repetition opens a circuit | Repeated identical signatures stop re-entering the known-broken path. |
| SENT-10 | Stable degradation over flapping | Once a route/profile/provider is degraded for a pressure epoch, it remains stable until hysteresis and a half-open probe prove recovery. |
| SENT-11 | Incident capture is automatic and bounded | Material violations produce a compact sanitized bundle even when operator trace was not manually enabled. |
| SENT-12 | Runtime never edits source | Incidents and candidates are data only. They do not change repository files or active binaries. |
| SENT-13 | Regression candidates are reviewable | Automatic capture may create replay data, not trusted source tests or expectations. |
| SENT-14 | No mandatory inference | Detection, policy selection, incident generation, and replay extraction use deterministic code. |
| SENT-15 | Hytale remains highest priority | Sentinel evaluation, serialization, and replay yield to Hytale and active player conversation. |
| SENT-16 | Future cognition inherits protection | E4-E8 durable beliefs, social cognition, reflection, and autonomous actions must register invariants before becoming authoritative. |

---

# 6. High-Level Architecture

```text
Orbis immutable event stream
        |
        v
SentinelStateProjection
  - current turn/branch contracts
  - provider/request ownership
  - resource/readiness state
  - speech/claim coverage
  - persistence revision state
        |
        +--------------------------+
        |                          |
        v                          v
BoundaryGuardEvaluator       TrendMonitorEvaluator
        |                          |
        +-------------+------------+
                      |
                      v
             InvariantRegistry
                      |
                      v
              InvariantVerdict
                 /          \
              PASS       VIOLATION
                             |
                             v
                   DegradationSignal
                             |
                             v
                  FailureSignatureEngine
                             |
                             v
                 ScopedHealthProjection
                             |
                             v
                  RecoveryPolicyRegistry
                             |
                             v
       Existing owners / RecoverySupervisor / ResourceScheduler
                             |
                             v
                  RecoveryVerification
                    /             \
                 PASS             FAIL
                  |                |
                  v                v
            HEALTHY/DEGRADED   QUARANTINED/
                              OPERATOR_REQUIRED
                  \                /
                   \              /
                    v            v
                     IncidentRecorder
                             |
                             v
                RegressionCandidateExtractor
                             |
                             v
                 IncidentReplayHarness
```

## 6.1 Sentinel modes

```text
OFF
OBSERVE
ENFORCE
```

- `OFF`: no Sentinel evaluation beyond compatibility scaffolding.
- `OBSERVE`: detect, classify, trace, incident-capture, and compare expected recovery without changing production behavior. Existing hardening guards remain active.
- `ENFORCE`: proven invariants may block side effects and invoke approved recovery.

Mode changes affect future turns and background jobs. An active branch remains pinned to its captured Sentinel policy version.

## 6.2 Policy versions

Every turn records:

- invariant registry version;
- recovery policy registry version;
- Sentinel mode;
- circuit-breaker snapshot ID;
- incident-sanitizer version.

This permits deterministic replay and prevents a later policy update from being mistaken for the policy that governed an earlier turn.

---

# 7. Degradation Taxonomy and Severity Model

## 7.1 Degradation categories

### CONTRACT_DRIFT

The executed path differs from the compiled contract.

Examples:

- actual prompt differs from budgeted prompt;
- supported epistemic route becomes non-authoritative;
- provider adapter changes output contract or reasoning mode;
- final speech path bypasses required validation.

### OWNERSHIP_VIOLATION

Two systems believe they own the same request, response, session, or persistent revision.

Examples:

- stale provider completion releases a newer session;
- two terminal transitions;
- duplicated response ID ownership;
- one action result bound to the wrong branch.

### INPUT_INTEGRITY

The authoritative player input is incomplete, inconsistent, duplicated, or implausibly malformed.

Examples:

- partial/final STT collapse;
- materially incomplete final transcript;
- duplicate accepted transcript;
- malformed input confidently semanticized.

### PROVIDER_LIFECYCLE

Provider work is not in the state Orbis claims.

Examples:

- cancel reported but stream/body/inference remains active;
- provider declared ready while draining;
- duplicate or out-of-order deltas;
- request completion without terminal provider state.

### RESOURCE_ENVELOPE

The runtime cannot sustainably service its declared state.

Examples:

- `READY` while ordinary foreground work is not admissible;
- repeated starvation loop;
- provider residency ping-pong;
- monotonic queue or permit growth;
- Hytale reserve violation.

### SPEECH_DELIVERY_INTEGRITY

Generated, authorized, displayed, synthesized, played, and recorded text diverge.

Examples:

- objective phrase committed without claim verdict;
- non-contiguous ledger spans;
- text marked delivered without playback;
- later final rewrites an immutable prefix.

### EPISTEMIC_AUTHORITY

Speech or belief exceeds available evidence or the compiled `AnswerPlan`.

Examples:

- entity evidence authorizes an unsupported property;
- `UNKNOWN` becomes confident factual speech;
- SAFE_SOCIAL invents biography;
- generated flourish becomes evidence.

### ACTION_TRUTH

Speech, memory, or plan claims physical execution without authoritative result.

Examples:

- “I sold it” without transaction result;
- promise committed before action authority;
- failed action stored as success.

### DURABLE_STATE_INTEGRITY

A write or replay would contaminate persistent cognition.

Examples:

- belief event missing provenance;
- revision regresses or duplicates;
- snapshot and event tail disagree;
- partially written event becomes visible;
- unsupported generated text enters belief truth.

### PERFORMANCE_DEGRADATION

A previously healthy path crosses a sustained, configured latency or frame-pressure envelope.

Examples:

- warm common-turn stage p95 materially regresses;
- provider cancellation latency increases beyond its profile;
- inference-correlated Hytale hitches recur.

## 7.2 Severity

```text
NOTICE
WARNING
DEGRADED
CRITICAL
FATAL_OPERATOR_REQUIRED
```

- `NOTICE`: diagnostic anomaly with no contract impact.
- `WARNING`: one suspicious event or approaching threshold.
- `DEGRADED`: supported functionality remains usable through a reduced profile or safe fallback.
- `CRITICAL`: side effect must be blocked or active work cancelled to protect truth, delivery, action, or persistence.
- `FATAL_OPERATOR_REQUIRED`: no safe automatic continuation exists for the affected scope.

## 7.3 Detection confidence

```text
PROVEN
HIGH_CONFIDENCE
SUSPECT
INSUFFICIENT_DATA
```

Only `PROVEN` and explicitly approved `HIGH_CONFIDENCE` verdicts may trigger enforcement. `SUSPECT` creates telemetry and may move health to `SUSPECT`, but does not block an authoritative side effect unless an existing hardening guard independently requires it.

## 7.4 Scope

```text
REQUEST
TURN
BRANCH
CONVERSATION_SCENE
NPC
ROUTE
PROVIDER
RESOURCE_PROFILE
PERSISTENCE_STREAM
WORLD
GLOBAL_RUNTIME
```

The Sentinel chooses the smallest scope that contains the defect without disabling unrelated NPCs or conversations.

---

# 8. Runtime Invariant Registry

## 8.1 Invariant definition

```java
record InvariantDefinition(
    InvariantId id,
    int version,
    String description,
    InvariantCategory category,
    EvaluationBoundary boundary,
    InvariantScope scope,
    SentinelSeverity severity,
    DetectionConfidence minimumEnforcementConfidence,
    InvariantEvaluatorId evaluator,
    RecoveryPolicyId recoveryPolicy,
    IncidentPolicy incidentPolicy,
    RegressionExtractorId regressionExtractor,
    Duration evaluationDeadline,
    boolean enabledInObserve,
    boolean enabledInEnforce
) {}
```

Definitions are project-owned, versioned, and immutable for an active turn. They are not loaded from arbitrary player data.

## 8.2 Evaluation boundaries

Initial boundaries:

```text
TRANSCRIPT_ACCEPT
DIALOGUE_FRAME_COMPLETE
TURN_PLAN_COMPILE
CONTEXT_RENDER_COMPLETE
PROVIDER_DISPATCH
PROVIDER_STREAM_EVENT
PROVIDER_TERMINAL
CLAIM_VALIDATION
SPEECH_LEDGER_APPEND
TTS_QUEUE
PLAYBACK_COMMIT
ACTION_COMMIT
ACTION_RESULT
BELIEF_WRITE_PROPOSED
PERSISTENCE_APPEND
TERMINAL_CLEANUP
READINESS_SAMPLE
LATENCY_WINDOW_UPDATE
```

## 8.3 Verdict

```java
record InvariantVerdict(
    InvariantId invariantId,
    VerdictStatus status,
    DetectionConfidence confidence,
    String boundedReasonCode,
    List<CorrelationId> evidenceIds,
    Optional<FailureSignatureSeed> signatureSeed,
    Instant evaluatedAt,
    long evaluationMicros
) {}
```

`VerdictStatus`:

```text
PASS
FAIL
NOT_APPLICABLE
INSUFFICIENT_DATA
EVALUATOR_ERROR
```

An evaluator error is not silently treated as PASS.

- For an authority or durable-state guard, evaluator error fails the side effect closed and creates a Sentinel incident.
- For a trend-only monitor, evaluator error disables that monitor temporarily and reports diagnostics without failing the turn.

## 8.4 Initial critical invariant set

The first release prioritizes the failures already proven expensive or dangerous.

### Turn and ownership

- One physical utterance produces one authoritative transcript and one turn.
- Every branch/turn reaches exactly one terminal state.
- Stale epochs cannot mutate current branches.
- Every resource handle is released exactly once.

### Plan and prompt

- No provider dispatch without a valid `TurnExecutionPlan` and budget.
- The content hash and token estimate budgeted are the content hash and estimate dispatched.
- A supported authoritative epistemic route carries one authoritative `EpistemicContract`.
- Required prompt sections are present; forbidden redundant sections are absent when the compact epistemic plan replaces them.
- Structured output worst case fits the final-answer budget.

### Provider lifecycle

- Provider request identity matches turn, response, branch epoch, and session owner.
- Cancellation is not terminally complete until the provider is actually drained or restarted.
- A provider cannot be `READY` and `DRAINING` simultaneously.
- Duplicate/out-of-order events cannot advance state.

### Speech and epistemic authority

- Every objective phrase has a claim-support verdict before ledger commitment.
- Every action commitment has matching action authority/result.
- Entity support cannot authorize an unobserved property.
- `UNKNOWN`, `CONFLICTED`, or `NEEDS_CLARIFICATION` cannot become unqualified objective certainty.
- Canonical speech spans are ordered, contiguous, non-overlapping, and immutable.
- Only delivered segments enter conversation history.

### Resources

- `READY` implies the current preferred or degraded profile can sustainably admit ordinary foreground work.
- The Hytale reserve remains intact.
- A repeated starvation signature cannot incur the full wait on every subsequent turn.
- A pressure epoch permits at most the configured remediation transitions.

### Persistence

- Every objective persistent assertion has provenance and a valid source class.
- Generated speech is not factual evidence merely because it was delivered.
- Event IDs and revisions are monotonic/idempotent.
- Snapshot plus event tail rebuilds the same materialized state.
- A failed or partial write cannot become visible as committed state.

Appendix C defines the initial registry in more detail.

---

# 9. Degradation Sentinel Evaluation Model

## 9.1 State projection

`SentinelStateProjection` consumes sequenced Orbis events and builds immutable, bounded views needed for evaluation.

It may project:

- current turn/branch lifecycle;
- compiled plan and actual-dispatch hashes;
- provider request/session ownership;
- prompt/schema/output budgets;
- dialogue frame, query plan, evidence packet, answer plan, and claim verdict coverage;
- canonical speech generated/authorized/queued/played/delivered spans;
- resource/readiness samples;
- persistence snapshot/event revision state;
- recovery allowance and cleanup counters;
- stage timings.

It does not become an alternate gameplay state store. Projections are disposable and reconstructible from events plus bounded snapshots.

## 9.2 Boundary evaluation ordering

For a guarded side effect:

```text
owner prepares immutable candidate state
        |
        v
Sentinel guard evaluates registered invariants
        |
     PASS / FAIL
        |
        +--> PASS: owner commits side effect
        |
        +--> FAIL: owner does not commit; recovery request is queued
```

The guard never commits the side effect itself.

## 9.3 No recursive Sentinel failure

Sentinel-generated events are marked with `sentinelInternal=true` and cannot recursively create identical incidents without a state change.

Example:

- `INVARIANT_VIOLATED` may trigger `INCIDENT_CAPTURED`.
- A failed incident write may create one `INCIDENT_PERSISTENCE_FAILED` server warning.
- It may not recursively create unlimited incidents about the incident writer.

## 9.4 Evaluation budget

- Critical boundary evaluator: target <=1 ms p95 each.
- Total common-turn synchronous Sentinel overhead: target <=3 ms p95.
- Asynchronous monitor update: target <=5 ms p95 per event batch.
- No disk, network, provider call, GPU query, or model inference in a boundary evaluator.
- Heavy incident serialization and replay extraction run off-thread after containment.

## 9.5 Safe data dependencies

An evaluator may read only:

- the immutable candidate and turn plan supplied at the boundary;
- cached immutable snapshots already captured by authoritative owners;
- `SentinelStateProjection` on the serialized control executor;
- versioned registry/policy data.

An evaluator may not synchronously query ECS, disk, provider health, `nvidia-smi`, or mutable stores from a Hytale callback.

---

# 10. Scoped Health and State Machines

## 10.1 Health states

```text
HEALTHY
SUSPECT
DEGRADED
RECOVERING
QUARANTINED
FAILED_OPERATOR_REQUIRED
```

## 10.2 State transitions

```text
HEALTHY
  -> SUSPECT              one unproven anomaly or approaching trend threshold
  -> DEGRADED             proven invariant violation with safe reduced operation
  -> RECOVERING            approved recovery begins

SUSPECT
  -> HEALTHY              subsequent samples clear the condition
  -> DEGRADED             threshold or proof reached
  -> RECOVERING           recovery is justified

RECOVERING
  -> HEALTHY              recovery verification passes at normal profile
  -> DEGRADED             recovery passes only at reduced profile
  -> QUARANTINED          recovery fails or same signature immediately repeats

DEGRADED
  -> RECOVERING           cooldown and recovery preconditions satisfied
  -> QUARANTINED          repeated signature exceeds threshold
  -> HEALTHY              multi-sample hysteresis and half-open probe pass

QUARANTINED
  -> HALF_OPEN_PROBE      cooldown/operator reset
  -> HEALTHY              bounded probe passes
  -> QUARANTINED          probe fails
  -> FAILED_OPERATOR_REQUIRED if no safe scope remains
```

`HALF_OPEN_PROBE` is represented as a recovery substate rather than a public readiness state.

## 10.3 Scoped health keys

Examples:

```text
PROVIDER:nemotron
PROVIDER:chatterbox
ROUTE:GROUNDED_DIALOGUE
ROUTE:EPISTEMIC_OBJECTIVE_PROPERTY
RESOURCE_PROFILE:NEMOTRON_4_LAYER
PERSISTENCE_STREAM:NPC_<stableId>_BELIEFS
NPC:<stableId>
WORLD:<worldId>
GLOBAL:ORBIS
```

One NPC's corrupt belief stream should not disable all speech. One provider-wide CUDA stall may require provider scope. A canonical ledger integrity failure may require response or route scope depending on signature repetition.

## 10.4 Derived readiness

The existing readiness HUD consumes the same health projection.

Examples:

- `Orbis: READY` only when all required foreground scopes are serviceable.
- `Orbis: DEGRADED` when safe conversation continues with route/provider/persistence limitations.
- `Nemotron: READY` only if provider and current resource profile are genuinely admissible.
- `Epistemic Persistence: DEGRADED_READ_ONLY` if belief writes are quarantined but validated reads remain safe.

No UI-only readiness authority is permitted.

## 10.5 Hysteresis

No state recovers from a single good sample after repeated failure.

Use configurable:

- minimum consecutive healthy samples;
- minimum degraded duration;
- cooldown before half-open probe;
- occurrence window;
- maximum remediation transitions per pressure epoch.

Resource and latency trends require multiple fresh samples. Hard authority invariants such as “objective speech has no claim verdict” fail immediately.

---

# 11. Failure Signatures, Deduplication, and Escalation

## 11.1 Failure signature

```java
record FailureSignature(
    String fingerprint,
    InvariantId invariantId,
    InvariantCategory category,
    InvariantScope scope,
    String normalizedStage,
    String routeOrContract,
    String providerOrComponent,
    String boundedReasonCode,
    String policyVersion,
    String relevantConfigHash
) {}
```

The fingerprint excludes volatile IDs, timestamps, player text, and raw exception stacks unless a normalized exception category is part of the root cause.

## 11.2 Normalization examples

These should share one signature:

- prompt sizes `1528>1200`, `1674>1200`, and `1505>1200` when caused by the same post-render budget mismatch;
- multiple NPCs whose supported epistemic route resolves to legacy non-authoritative execution;
- repeated `RESOURCE_STARVED` at slightly different free-VRAM values under one unsustainable readiness profile.

These should not share one signature:

- Nemotron provider drain failure versus Chatterbox synthesis timeout;
- objective property firewall bypass versus action-promise authority failure;
- one NPC-specific corrupt belief event versus global event-log schema mismatch.

## 11.3 Deduplication

For one signature in a rolling window:

- first occurrence: full incident bundle;
- repeated occurrences without material state change: compact occurrence record;
- first occurrence after recovery/profile/revision change: new full incident linked to prior signature;
- circuit-open occurrences: count and sample only, avoiding disk spam.

## 11.4 Escalation

Starting policy, configurable per invariant:

```text
1 proven occurrence
    -> contain/recover + incident

2 occurrences within 5 minutes
    -> DEGRADED for affected scope

3 occurrences within 10 minutes
    -> open circuit / quarantine affected path

critical durable-state or authority violation
    -> immediate quarantine of that write/commit path
```

Do not use one global threshold for every invariant. A duplicate trace event is less severe than an unsupported belief write.

## 11.5 Cross-revision tracking

Incident fingerprints persist across revisions. A later build can report:

- `NEW_SIGNATURE`
- `KNOWN_UNRESOLVED`
- `RECURRENCE_AFTER_FIX`
- `RESOLVED_BY_CURRENT_BUILD`

This prevents a superficially different trace from restarting the same whack-a-mole cycle.

---

# 12. Bounded Recovery Policy Registry

## 12.1 Recovery policy

```java
record RecoveryPolicy(
    RecoveryPolicyId id,
    int version,
    Set<InvariantId> allowedInvariants,
    InvariantScope maximumScope,
    int maxAttemptsPerTurn,
    int maxAttemptsPerPressureEpoch,
    Duration softDeadline,
    Duration hardDeadline,
    List<RecoveryAction> actions,
    List<RecoveryPrecondition> preconditions,
    List<RecoveryPostcondition> postconditions,
    FailureDisposition onFailure
) {}
```

## 12.2 Approved recovery actions

Initial action vocabulary:

```text
REJECT_SIDE_EFFECT
FAIL_FAST_WITH_SAFE_REASON
RECOMPILE_TURN_PLAN
PRUNE_CONTEXT_AND_RECOMPILE
RETRANSCRIBE_PRESERVED_AUDIO
DROP_UNSUPPORTED_CLAUSE
REALIZE_SAFE_ANSWERPLAN_FALLBACK
CANCEL_PROVIDER_REQUEST
WAIT_FOR_PROVIDER_DRAIN
RESTART_PROVIDER_PROCESS
RESET_CONVERSATION_SESSION
CLOSE_PLAYBACK_PRESERVE_PARTIAL
RELEASE_AND_REACQUIRE_RESOURCE_PROFILE
DOWNGRADE_TO_APPROVED_RESOURCE_PROFILE
MARK_ROUTE_SHADOW_ONLY
QUARANTINE_ROUTE
QUARANTINE_PROVIDER
REJECT_DURABLE_WRITE
REBUILD_MATERIALIZED_INDEX
ROLLBACK_TO_VERIFIED_SNAPSHOT
DISABLE_PERSISTENCE_WRITES_READ_ONLY
REQUEST_OPERATOR_ATTENTION
```

No arbitrary reflection, prompt rewriting, source editing, shell command, or model-generated recovery is permitted.

## 12.3 Shared recovery allowance

The Hardening Matrix's bounded recovery semantics remain authoritative.

- A response normally receives one shared recovery allowance across provider, structure, plan, and claim repair.
- Sentinel recovery cannot silently reset the allowance by changing subsystem.
- Pre-dispatch recompilation caused by a proven contract mismatch may be classified as plan correction rather than provider retry, but remains bounded to one corrected plan.
- Repeated signature escalation opens a circuit instead of repeatedly consuming full turn latency.

## 12.4 Recovery must change a relevant condition

Do not retry the identical request under identical state after a proven deterministic failure.

A recovery attempt must change at least one approved factor, such as:

- pruned context and new prompt hash;
- corrected route authority;
- preserved-audio batch transcription;
- lower approved resource profile;
- restarted provider process generation;
- unsupported clause removed;
- verified snapshot selected;
- route set to safe degraded mode.

If no meaningful condition can change, fail fast and quarantine rather than retry.

## 12.5 Recovery verification

A recovery is successful only when all applicable postconditions pass:

- original invariant now passes;
- no stronger invariant fails;
- authoritative owner reached a valid state;
- all stale callbacks are rejected;
- resource/queue/provider handles are released or transferred exactly once;
- canonical speech and delivered history remain consistent;
- durable state hash/revision is valid;
- affected route/provider/profile can pass one bounded half-open probe;
- next-turn readiness is restored where the failure affected foreground conversation.

`no exception thrown` is not proof of recovery.

## 12.6 Safe fallback response

A deterministic recovery utterance may be used only when:

- TTS is healthy;
- it makes no unsupported world, memory, relationship, action, or persistence claim;
- it reflects the real failure mode without exposing internal implementation text;
- it passes the same claim and canonical-speech authority path.

Examples:

- concise clarification for unresolved input;
- natural inability/uncertainty for unavailable evidence;
- brief “I can't answer that right now” for quarantined cognition.

Do not use generic recovery speech to conceal systematic failure indefinitely. Repetition opens a circuit and marks the route degraded.

---

# 13. Circuit Breakers, Quarantine, and Stable Degraded Modes

## 13.1 Circuit state

```text
CLOSED
OPEN
HALF_OPEN
DISABLED_BY_OPERATOR
```

## 13.2 Circuit key

A circuit key combines:

- invariant or signature family;
- affected scope;
- route/provider/profile/schema version;
- relevant configuration hash.

## 13.3 Route quarantine

When a supported route repeatedly violates its authoritative contract:

- do not repeatedly dispatch the known-broken path;
- future matching turns use a pre-approved safe degraded policy;
- examples: SHADOW-only semantic analysis plus safe generic dialogue with no objective claims, or deterministic `UNKNOWN/clarification` when the missing authority is factual;
- trace `ROUTE_QUARANTINED` and the exact lost capability;
- other routes continue normally.

Do not silently claim full intelligence readiness while an authoritative route is quarantined.

## 13.4 Provider quarantine

When a provider repeatedly stalls, violates ordering, or fails drain/restart:

- stop selecting it for future branches according to explicit provider policy;
- active failed branch does not silently restart through another provider;
- preserve capture and future-turn availability;
- mark provider unhealthy in readiness HUD;
- operator or cooldown half-open probe is required.

## 13.5 Resource degraded mode

Reuse the existing sustainable-envelope and pressure-epoch semantics.

- preferred profile becomes unsafe;
- one approved remediation/profile transition occurs;
- degraded profile remains stable for the session or pressure epoch;
- no automatic upshift until sustained recovery and half-open probe;
- no Nemotron/Chatterbox ping-pong;
- if no profile is safe, fail fast instead of repeating full admission waits.

## 13.6 Persistence read-only mode

If durable belief or memory writes cannot satisfy provenance, revision, replay, or storage integrity:

- reject the unsafe write;
- retain the last verified in-memory/read snapshot where safe;
- set persistence scope to `DEGRADED_READ_ONLY`;
- continue conversation only if it can do so without pretending new learning will persist;
- queue no unbounded retries;
- capture incident and require verified recovery before writes resume.

## 13.7 Epistemic safe mode

If the claim firewall or authoritative epistemic route cannot be proven:

- block objective factual speech for the affected route;
- permit only claim-free subjective/social language when the `ClaimPolicy` proves it contains no objective extension;
- otherwise use a safe `UNKNOWN/clarification` answer;
- never bypass the firewall to preserve conversational fluency.

## 13.8 Half-open probes

A half-open probe:

- is one bounded synthetic or real future request;
- uses current versioned plan and policy;
- cannot mutate durable state unless its write gate independently passes;
- does not run during Hytale pressure;
- closes the circuit only after all postconditions pass.

---

# 14. Durable-State Safety and Persistence Firewall

This section is mandatory before E4 persistent beliefs, E6 testimony, E7 reflection, or E8 autonomous outcome learning become authoritative.

## 14.1 Durable mutation gate

Every proposed persistent cognition mutation passes:

```text
schema validation
-> stable identity validation
-> provenance validation
-> support/authority validation
-> revision/conflict validation
-> temporal validation
-> privacy/scope validation
-> idempotency check
-> append permission
```

Only then may the existing persistence owner enqueue an append.

## 14.2 Required invariants

- Objective belief has non-empty provenance.
- Generated speech alone is never an evidence source with positive factual authority.
- Action occurrence requires `ActionResult` evidence.
- Reflection requires existing support IDs and cannot introduce unsupported entities/events.
- Revision increments monotonically for the assertion/conflict key.
- Duplicate event ID is idempotent.
- Canonical authored facts cannot be overwritten by ordinary testimony.
- Current volatile state cannot be replaced by stale incompatible memory.
- Event schema and predicate policy version are supported.
- A write is not visible as committed before durable append acknowledgement according to current persistence semantics.

## 14.3 Write rejection

On a proven invalid write:

- do not partially apply to the materialized store;
- emit `DURABLE_WRITE_REJECTED` with bounded reason;
- preserve source memory/action result unchanged;
- mark only the affected writer/scope degraded;
- generate a persistence incident and candidate;
- do not attempt to “repair” missing provenance with generated text.

## 14.4 Index rebuild

A materialized index may be rebuilt automatically only from:

- a verified schema-compatible snapshot;
- a strictly ordered, checksum-valid event tail;
- idempotent event application.

The Sentinel requests rebuild through the persistence owner and verifies the resulting state hash/revision summary.

## 14.5 Snapshot rollback

Rollback is allowed only when:

- a last-known-good snapshot is verified;
- event tail boundaries are known;
- the current corrupt or incomplete tail is quarantined, not deleted silently;
- rollback cannot reverse authoritative Hytale state;
- the operator/trace is informed of potential lost cognition events;
- current schema defines rollback safety.

If these conditions cannot be proven, enter read-only degraded mode and require operator attention.

## 14.6 Incident before contamination

For durable boundaries, incident capture begins from the rejected candidate and current verified state. It must not wait for a failed write to corrupt the store.

---

# 15. Incident Capture and Diagnostic Bundles

## 15.1 Incident lifecycle

```text
DETECTED
CONTAINED
RECOVERY_STARTED
RECOVERED_VERIFIED
DEGRADED_STABLE
QUARANTINED
UNRESOLVED
OPERATOR_REQUIRED
```

## 15.2 Incident schema

```java
record OrbisIncident(
    IncidentId id,
    FailureSignature signature,
    InvariantId invariantId,
    SentinelSeverity severity,
    DetectionConfidence confidence,
    InvariantScope scope,
    IncidentState state,
    RuntimeIdentity runtime,
    CorrelationBundle correlation,
    Optional<TurnPlanSnapshot> turnPlan,
    Optional<EpistemicSnapshot> epistemic,
    Optional<ProviderSnapshot> provider,
    Optional<ResourceSnapshot> resources,
    Optional<SpeechSnapshot> speech,
    Optional<PersistenceSnapshotSummary> persistence,
    List<IncidentEvent> eventWindow,
    Optional<RecoveryPlanSummary> recovery,
    Optional<RecoveryVerificationSummary> verification,
    ReproductionTier reproductionTier,
    Instant detectedAt,
    Instant updatedAt,
    String sanitizerVersion,
    String payloadSha256
) {}
```

## 15.3 Runtime identity

Capture:

- ImmersiveNPCs revision and JAR hash;
- Orbis schema/policy versions;
- provider/model identifiers;
- world/save identifier according to privacy policy;
- platform and resource profile;
- feature modes such as epistemic `SHADOW/AUTHORITATIVE` and Sentinel `OBSERVE/ENFORCE`.

## 15.4 Correlation

Capture where applicable:

- TurnId;
- ResponseId;
- provider request/session ID;
- branch epoch;
- NPC stable ID;
- player/session pseudonymous ID;
- conversation scene ID;
- action/operation/result IDs;
- belief event/assertion IDs.

## 15.5 Turn-plan snapshot

Capture compactly:

- cognition mode;
- semantic/query route;
- context profile;
- decision/speech contract;
- prompt-section names and sizes;
- budget composition;
- budgeted content hash;
- dispatched content hash;
- deadlines and recovery allowance;
- authoritative/shadow flags.

Do not store a full raw prompt by default. Store sanitized compact content only under existing operator trace policy.

## 15.6 Epistemic snapshot

Capture compactly:

- DialogueFrame;
- EpistemicQueryPlan;
- evidence IDs/source classes;
- Answerability;
- authorized proposition IDs;
- ClaimPolicy;
- atomic claim spans and verdicts;
- unsupported/contradicted reason.

Do not store hidden reasoning.

## 15.7 Event window

Capture a bounded sequence around detection:

- default up to 32 events before and 32 after containment;
- preserve sequence numbers and correlation IDs;
- exclude raw audio and unbounded token deltas;
- coalesce repetitive resource/latency samples.

## 15.8 Storage

Suggested additive path:

```text
mods/ImmersiveNPCs/diagnostics/incidents/
    index.json
    2026-08-31/
        <incident-id>.json
        occurrences.jsonl
```

Requirements:

- asynchronous write;
- bounded queue;
- configurable total size/count retention;
- atomic temp-write and move;
- incident writer failure cannot fail gameplay;
- payload checksum;
- no source repository modification.

## 15.9 Deduplicated occurrence record

Repeated signatures may store:

- occurrence time;
- affected NPC/route scope;
- current health/circuit state;
- whether recovery was attempted or skipped due open circuit;
- next-turn outcome.

The first full bundle remains the diagnostic anchor.

## 15.10 Incident quality

An incident should let Codex answer without the original full trace:

- what invariant failed;
- where it failed;
- which contract/version governed the path;
- what exact state proved the failure;
- whether unsafe output/state was blocked;
- what recovery ran;
- whether recovery was verified;
- whether the next turn remained usable;
- whether the same signature has occurred before.

---

# 16. Regression Candidate Generation

## 16.1 Purpose

A regression candidate is sanitized data sufficient to recreate an invariant failure through deterministic test infrastructure. It is not generated Java source and is not automatically trusted as the correct expected behavior.

## 16.2 Candidate schema

```java
record RegressionCandidate(
    CandidateId id,
    IncidentId sourceIncidentId,
    FailureSignature signature,
    RegressionFixtureKind kind,
    String fixtureSchemaVersion,
    DeterministicSeed seed,
    MinimalSemanticInputs inputs,
    SyntheticBehaviorFixture syntheticBehavior,
    ExpectedInvariantOutcome expected,
    List<RequiredHarnessCapability> requirements,
    CandidateStatus status,
    String sanitizerVersion,
    String payloadSha256,
    Instant createdAt
) {}
```

## 16.3 Fixture kinds

```text
TURN_PLAN
PROMPT_BUDGET
ROUTE_AUTHORITY
STT_PARTIAL_FINAL
PROVIDER_STREAM
PROVIDER_CANCEL_DRAIN
RESOURCE_SEQUENCE
ATOMIC_CLAIM
CANONICAL_SPEECH_LEDGER
ACTION_AUTHORITY
PERSISTENCE_EVENT
SNAPSHOT_REPLAY
QUEUE_CLEANUP
LATENCY_WINDOW
```

## 16.4 Minimal semantic inputs

Prefer stable semantic DTOs:

- route/query class;
- compact dialogue frame;
- evidence/answer plan IDs or synthetic facts;
- prompt-section sizes and hashes;
- synthetic provider events/output text;
- resource telemetry sequence;
- speech segment spans;
- persistence event/snapshot summaries;
- expected violation and containment.

Never embed live ECS handles, provider handles, arbitrary Java objects, secrets, raw audio, or hidden reasoning.

## 16.5 Captured provider behavior

When a model/provider output caused the incident, the candidate stores the bounded candidate text or event sequence needed for replay.

Examples:

- the exact bad sentence containing one supported and one unsupported clause;
- a reasoning-only stream pattern;
- duplicate/out-of-order delta sequence;
- provider cancel followed by late completion;
- truncated structured JSON.

This permits deterministic replay without calling the real provider.

## 16.6 Candidate status

```text
NEW
REPLAYABLE
NON_DETERMINISTIC
REPLAY_PASSED_CURRENT_BUILD
REPLAY_FAILED_CURRENT_BUILD
PROMOTED_TO_SOURCE_FIXTURE
REJECTED_AS_FALSE_POSITIVE
SUPERSEDED
```

Only Codex or an operator workflow promotes a candidate into repository test assets.

## 16.7 Automatic minimization

S3 may remove irrelevant event fields using deterministic schema-aware reduction.

It may not use an LLM to rewrite or interpret the expected behavior.

A reduced candidate remains linked to the full incident and must preserve the same failure signature under replay.

## 16.8 Storage

Suggested path:

```text
mods/ImmersiveNPCs/diagnostics/regression-candidates/
    <candidate-id>.json
```

Candidates are bounded, additive, and ignored by older production JARs.

---

# 17. Deterministic Replay and Self-Heal Verification

## 17.1 Replay harness

Extend the existing `ConversationMatrixHarness` rather than building a second test runtime.

Add an `IncidentReplayHarness` adapter that maps a candidate to existing synthetic components such as:

- SyntheticMoonshineProvider;
- SyntheticNemotronProvider;
- SyntheticChatterboxProvider;
- SyntheticResourceScheduler;
- TurnStateModel;
- GoldenTraceAssertions;
- persistence fixtures.

## 17.2 Live-runtime separation

Replay never:

- invokes a real provider unless explicitly run as an operator benchmark;
- mutates the live world;
- writes live NPC beliefs/memories/actions;
- plays Hytale audio;
- competes with active player conversation.

It runs in a sandboxed in-memory fixture context.

## 17.3 Automatic replay policy

After containment and incident serialization:

- if the candidate is fully deterministic and the server is idle with no frame/resource pressure, S3 may queue one low-priority replay;
- otherwise mark the candidate `REPLAYABLE` for operator/Codex execution;
- replay yields immediately to foreground work;
- no repeated replay loop for a failing candidate in one session.

## 17.4 Replay outcomes

```text
REPRODUCED_UNRESOLVED
CONTAINMENT_VERIFIED
RECOVERY_VERIFIED
FALSE_POSITIVE_OR_STALE_CANDIDATE
HARNESS_CAPABILITY_MISSING
```

## 17.5 Self-heal proof

A runtime self-heal is considered verified when replay or a bounded half-open probe proves:

- the original unsafe side effect remains blocked;
- the recovery path reaches the expected valid/degraded state;
- terminal cleanup is exact;
- stale events cannot return;
- next-use/next-turn succeeds where applicable;
- no durable-state mutation violates the candidate's guard expectations.

## 17.6 After a new build

At startup or an operator command, unresolved candidates from prior revisions may run as a bounded smoke set after the runtime is ready and idle.

Report:

- signatures resolved by current build;
- signatures still reproduced;
- candidates whose schema is no longer compatible;
- no automatic deletion of historical incident evidence.

## 17.7 Operator commands

Adapt exact command names to current repository conventions.

Conceptual controls:

```text
/npc sentinel status
/npc sentinel incidents [count]
/npc sentinel incident <id>
/npc sentinel circuits
/npc sentinel reset-circuit <scope>
/npc sentinel candidates [status]
/npc sentinel replay <candidate-id>
/npc sentinel replay-smoke
/npc sentinel mode <OFF|OBSERVE|ENFORCE>
```

Operator-only. Controls operate on cached state and schedule work asynchronously.

---

# 18. Performance, Scheduling, and Retention

## 18.1 Foreground budgets

Initial p95 targets:

| Work | Target |
|---|---:|
| One boundary invariant evaluator | <=1 ms |
| Total common-turn synchronous Sentinel guards | <=3 ms |
| Async state-projection/event batch | <=5 ms |
| Failure signature generation | <=1 ms |
| Incident enqueue after containment | <=2 ms |
| Incident serialization | background only |
| Regression candidate extraction | background only |
| Deterministic replay | idle/background; no player latency budget |

## 18.2 Scheduling priorities

```text
Hytale callbacks and frame-critical work
> capture/playback
> foreground conversation and safety guards
> provider recovery needed for next turn
> incident enqueue
> background persistence
> incident serialization
> candidate extraction
> deterministic replay
```

## 18.3 Queue bounds

Every Sentinel queue is bounded.

- signal queue;
- recovery requests;
- incident writes;
- candidate extraction;
- replay jobs.

On overflow:

- never block Hytale;
- keep the first/highest-severity record;
- increment a compact dropped-count metric;
- emit one bounded warning;
- avoid recursive incident storms.

## 18.4 Retention

Configurable defaults:

- full incidents: latest 100 or 256 MiB, whichever first;
- compact occurrences: bounded rolling JSONL;
- regression candidates: retain until promoted/rejected or operator cleanup;
- no raw audio;
- no hidden reasoning;
- safe transcript text follows existing trace privacy policy.

## 18.5 Memory

Sentinel projections and event windows are bounded by active turns/scopes. No unbounded in-memory history. Persistent incident storage is not queried synchronously during a turn.

## 18.6 No GPU work

The Sentinel performs no model inference or GPU probes. It consumes cached resource telemetry from `OrbisResourceScheduler`.

---

# 19. Native Hytale UI, Trace, and Operator Controls

## 19.1 Readiness HUD integration

Extend the existing top-right AI readiness panel without adding a second status model.

Possible compact states:

```text
Orbis: Ready
Orbis: Degraded
Orbis: Recovering
Orbis: Quarantined
```

Optional later subrow:

```text
Sentinel: Healthy
Sentinel: 1 Circuit Open
Sentinel: Persistence Read-Only
```

Keep the HUD player-appropriate. Detailed incidents remain operator-only.

## 19.2 Cognition Inspector Sentinel panel

Display cached information:

- Sentinel mode and policy versions;
- global and scoped health;
- active recovery;
- open/half-open circuits;
- last incident and signature;
- repeated occurrence counts;
- quarantined routes/providers/persistence streams;
- unresolved regression candidates;
- latest replay outcome;
- dropped incident/candidate counts;
- evaluation latency and queue depth.

## 19.3 Trace events

Extend the current event stream with:

```text
SENTINEL_INVARIANT_EVALUATED
SENTINEL_INVARIANT_VIOLATED
SENTINEL_DEGRADATION_SIGNAL
SENTINEL_HEALTH_CHANGED
SENTINEL_RECOVERY_PLANNED
SENTINEL_RECOVERY_STARTED
SENTINEL_RECOVERY_SUCCEEDED
SENTINEL_RECOVERY_FAILED
SENTINEL_RECOVERY_VERIFIED
SENTINEL_CIRCUIT_OPENED
SENTINEL_CIRCUIT_HALF_OPEN
SENTINEL_CIRCUIT_CLOSED
SENTINEL_ROUTE_QUARANTINED
SENTINEL_PROVIDER_QUARANTINED
SENTINEL_PERSISTENCE_READ_ONLY
SENTINEL_INCIDENT_CAPTURED
SENTINEL_INCIDENT_WRITE_FAILED
SENTINEL_REGRESSION_CANDIDATE_CREATED
SENTINEL_REPLAY_STARTED
SENTINEL_REPLAY_PASSED
SENTINEL_REPLAY_FAILED
```

Do not log every PASS verdict in ordinary trace by default. Aggregate pass counters and log full details only for violations, recovery, state transitions, or explicit diagnostic mode.

## 19.4 Operator controls

Controls:

- future-turn Sentinel mode;
- inspect/export incident;
- inspect/reset a circuit;
- schedule a candidate replay;
- run bounded replay smoke;
- clear resolved incidents/candidates under retention policy;
- never execute source edits, builds, or arbitrary scripts.

## 19.5 Incident export

Provide one operator-safe export containing selected incident/candidate JSON and a manifest. Do not include raw audio, secrets, or unrelated NPC data.

---

# 20. Failure Handling, Security, and Privacy

## 20.1 Sentinel subsystem failure

Failure | Required behavior
---|---
Boundary evaluator throws | Authority/durable side effect fails closed; evaluator incident; unrelated routes continue
Trend monitor throws | Disable that monitor temporarily; existing runtime continues; warning/incident
Signal queue saturated | Preserve highest severity; drop/coalesce low severity; do not block gameplay
Recovery planner unavailable | Do not improvise; fail safe or enter predefined degraded mode
Recovery execution fails | Verify cleanup, open circuit/quarantine, incident `UNRESOLVED`
Incident writer fails | Gameplay continues; bounded server warning; in-memory last incident retained if possible
Candidate extraction fails | Incident remains valid; candidate status unavailable
Replay harness fails | No live effect; report harness failure
Sentinel state projection mismatch | Rebuild projection from bounded authoritative snapshot/events; authority guard fails closed if proof unavailable

## 20.2 Sentinel tamper boundary

Player speech, NPC dialogue, profiles, memories, and world text cannot define:

- invariant IDs;
- recovery policies;
- circuit thresholds;
- operator commands;
- incident paths;
- source or executable paths.

Treat all model/player strings as untrusted content. Reason codes are project enums, not provider-controlled free-form commands.

## 20.3 Path safety

Incident/candidate paths are under the authoritative mod data root. Reject path traversal, absolute paths from runtime content, symbolic-link escape where practical, and oversized payloads.

## 20.4 Privacy

- No raw player or NPC audio.
- No hidden chain-of-thought.
- No secret values or credentials.
- Safe transcript text only under current operator trace policy.
- Player IDs may be pseudonymized in export.
- Private belief details appear only when directly relevant to the incident and follow existing operator access rules.

## 20.5 Incident integrity

Each incident/candidate includes schema version and SHA-256 of canonical serialized payload. This detects accidental corruption; it is not a remote-authentication scheme.

## 20.6 No silent failure suppression

A degraded or quarantined scope must appear in health/trace/UI. The Sentinel may reduce player-visible failure, but it cannot claim `READY` while a required route, provider, or persistence writer is unavailable.

---

# 21. Three-Phase Bounded Implementation Program

Do not implement S1-S3 in one Codex task. Each phase targets 15-25 minutes and stops at its exit gate.

## Phase S1 - Invariant Registry and Degradation Sentinel

### Purpose

Build the detection/control-plane foundation in `OBSERVE` mode and prove that it identifies known historical and current contract degradations without changing production behavior.

### Work

- Audit existing hardening checks and map each to an invariant or existing owner.
- Add versioned Sentinel modes: `OFF`, `OBSERVE`, `ENFORCE`; default S1 to `OBSERVE`.
- Add `InvariantRegistry`, definitions, evaluators, verdicts, and evaluation boundaries.
- Add `SentinelStateProjection` over the existing immutable event stream.
- Add `DegradationSignal`, severity/confidence/scope, and scoped health projection.
- Add `FailureSignatureEngine` and occurrence counters.
- Implement the initial critical detectors in observation mode:
  - dispatch without valid plan;
  - budgeted prompt hash differs from dispatched prompt;
  - supported epistemic route not authoritative;
  - objective speech without claim verdict;
  - action promise without action authority;
  - canonical span/order mismatch;
  - stale event acceptance;
  - duplicate terminal/release;
  - READY outside sustainable resource envelope;
  - belief write missing provenance or generated-speech contamination attempt, if E4 scaffolding exists.
- Extend existing trace and cached readiness/Inspector state.
- Convert historical synthetic failures into Sentinel detection tests.

### Non-goals

- No automatic recovery beyond existing hardening behavior.
- No circuit breakers.
- No incident disk bundles beyond a minimal in-memory prototype if needed.
- No regression-candidate generation.
- No production behavior changes.

### Exit gate

- Every initial invariant has a stable ID/version and named existing authority.
- Known route-bypass, prompt-budget, missing-firewall, stale-event, resource-envelope, and cleanup fixtures emit the expected `DegradationSignal`.
- False-positive corpus remains PASS/NOT_APPLICABLE.
- `OBSERVE` cannot change speech, action, provider selection, resources, or persistence.
- Common-turn synchronous overhead <=3 ms p95.
- No second trace pipeline.
- Compile and targeted tests pass.

### Deliverable

- S1 design/ownership report;
- invariant catalog;
- detection test results;
- measured overhead;
- exact S2 boundary.

Then stop.

## Phase S2 - Bounded Recovery, Circuit Breakers, and Incident Capture

### Purpose

Promote proven critical invariants to `ENFORCE`, reuse existing recovery primitives through a policy registry, prevent repeated failure loops, and automatically capture compact incidents.

### Work

- Add `RecoveryPolicyRegistry` and `RecoveryPlan` mapped to existing owners/`RecoverySupervisor`.
- Implement the approved action vocabulary and shared recovery allowance.
- Add recovery verification and postconditions.
- Add scoped health state machines, hysteresis, pressure epochs, and `ScopedCircuitBreakerRegistry`.
- Promote only proven critical invariants to enforcement:
  - invalid dispatch/budget mismatch;
  - supported route bypass;
  - missing objective-claim validation;
  - action promise without authority;
  - canonical speech corruption;
  - provider drain/stale ownership;
  - sustainable-resource failure;
  - invalid persistence write.
- Implement stable degraded modes:
  - route quarantine/safe epistemic mode;
  - provider quarantine;
  - lower resource profile;
  - persistence read-only;
  - fail-fast known-broken signature.
- Add automatic `OrbisIncident` capture, deduplication, bounded async storage, retention, and payload checksum.
- Extend readiness HUD and Cognition Inspector with health/circuit/incident state.
- Add fault-injection tests proving containment and next-turn readiness.

### Non-goals

- No source-code generation.
- No candidate-to-test promotion.
- No heavy automatic replay.
- No broad new recovery algorithms beyond the registry.
- No E4-E8 feature implementation.

### Exit gate

- Injected proven violations are blocked before unsafe speech/action/persistence where applicable.
- Approved recovery executes at most once per configured allowance/epoch.
- Recovery is not marked successful without postcondition proof.
- Repeated identical signatures open a scoped circuit and stop repeated full waits/retries.
- Unrelated routes/NPCs/providers continue.
- Next valid turn succeeds after recoverable faults.
- Durable invalid writes are rejected without contaminating current verified state.
- Full incident bundle is produced automatically without manual trace enablement.
- Incident serialization never blocks Hytale/foreground turn.
- Targeted tests and deployment validation pass.

### Deliverable

- S2 recovery/circuit/incident report;
- deployed Sentinel validation JAR if gates pass;
- connected test instructions;
- exact S3 boundary.

Then stop.

## Phase S3 - Regression Candidate Generation and Deterministic Replay

### Purpose

Turn unresolved or recovered runtime incidents into reproducible data, automatically verify known self-healing behavior in the existing harness, and establish a permanent anti-whack-a-mole gate.

### Work

- Add `RegressionCandidateExtractor` and versioned fixture kinds.
- Capture minimal semantic inputs and synthetic provider/resource/persistence behavior from incidents.
- Add schema-aware deterministic minimization.
- Store bounded candidate files outside the source repository.
- Add `IncidentReplayHarness` adapter to `ConversationMatrixHarness` and existing synthetic providers.
- Add idle/background one-shot replay for fully deterministic candidates.
- Add operator commands/UI for incidents, candidates, circuits, replay, and export.
- Load unresolved prior-revision candidates into a bounded smoke list.
- Report resolved, reproduced, stale-schema, and harness-missing outcomes.
- Add Sentinel-specific combinatorial matrix and a deterministic soak.

### Non-goals

- No runtime Java/JUnit source generation.
- No automatic code changes.
- No LLM judge or model-generated expected results.
- No live-world mutation during replay.
- No unbounded startup replay.

### Exit gate

- A live/synthetic invariant violation produces one sanitized candidate automatically.
- Candidate replay reproduces the signature or verifies containment without real providers/world mutation.
- Repeated incidents deduplicate to one candidate family.
- Current build can run unresolved candidate smoke and report status.
- Replay yields to Hytale and foreground conversation.
- No candidate can mutate live memory, belief, action, speech, or persistence.
- Sentinel matrix and soak show no incident storm, queue leak, circuit flapping, or monotonic memory growth.
- Operator export is sufficient for Codex diagnosis.

### Deliverable

- S3 candidate/replay report;
- Gate S results;
- final deployed Sentinel build if connected acceptance passes;
- return to Epistemic E4 only after Gate S.

Then stop.

## Gate S - Runtime Self-Healing Acceptance

Gate S is a validation gate, not a fourth engineering phase.

Run:

- targeted historical incident fixtures;
- Sentinel matrix;
- deterministic mixed soak;
- connected Hytale fault-injection/real-use test;
- incident/candidate/replay verification;
- readiness/health UI review.

Do not proceed to persistent E4 writes if the Sentinel cannot reject an invalid durable mutation, preserve the prior verified state, and create an incident.

---

# 22. Sentinel Test Matrix

## 22.1 Axes

| Axis | Values |
|---|---|
| Invariant category | contract, ownership, input, provider, resource, speech, epistemic, action, persistence, performance |
| Boundary | transcript, plan, render, dispatch, stream, claim, ledger, playback, action, persistence, cleanup, readiness |
| Confidence | proven, high, suspect, insufficient data |
| Scope | request, turn, route, provider, NPC, persistence stream, world, global |
| Repetition | first, second, threshold, circuit open, half-open probe |
| Recovery | none, success, fail, timeout, stale completion, secondary invariant failure |
| Provider state | warm, cold, draining, stalled, crashed, duplicate/out-of-order |
| Resource state | normal, drift, pressure, starvation, recovered |
| Persistence state | clean, duplicate, partial tail, corrupt snapshot, missing provenance, generated-speech contamination |
| Cancellation point | before dispatch, provider prefill, generation, claim, TTS, playback, persistence queue |
| Conversation topology | single NPC, multi-listener, owner mismatch, repeated turns, two scenes |

## 22.2 Critical cases

| Case | Required detection and self-heal |
|---|---|
| Supported epistemic route becomes legacy/non-authoritative | Detect before dispatch; recompile once or quarantine route; incident/candidate |
| Prompt measured before appended context | Hash mismatch before dispatch; rebuild/prune and re-budget; no provider call on invalid plan |
| Objective phrase has no claim verdict | Block ledger append; safe repair/partial; critical incident |
| Entity-only evidence plus invented property | Reject property clause; preserve supported entity claim; no belief ingestion |
| Action promise without commit/result | Block promise; action unchanged; safe truthful response |
| Provider cancel wrapper completes while work continues | remain DRAINING; restart/circuit if deadline exceeded; next turn preserved |
| Duplicate/out-of-order provider delta | reject event; no duplicate text/audio; incident after threshold |
| STT final loses stable suffix | one preserved-audio recovery; one authoritative transcript |
| READY becomes unsustainable after resource drift | move DEGRADED/approved profile; no repeated five-second starvation |
| TTS later phrase fails | valid delivered prefix becomes PARTIAL; later text not delivered/history |
| Canonical spans overlap/gap | block invalid segment; preserve prefix; no rewrite |
| Generated speech proposed as belief evidence | reject durable write; persistence remains clean/read-only if repeated |
| Belief event missing provenance | reject append; incident; no materialized mutation |
| Snapshot/event tail mismatch | quarantine tail; verified rebuild/rollback or read-only |
| Same signature repeats | circuit opens; full expensive path not retried each turn |
| Sentinel evaluator itself fails | authority side effect fails closed; trend monitor disables only itself; no recursive storm |
| Incident writer disk failure | gameplay continues; bounded warning; no unbounded memory growth |
| Regression replay runs during active turn | job yields/cancels; no frame/latency impact |

## 22.3 Metrics

- Detection precision for deterministic fixtures: target 100%.
- False positive count on passing current corpus: 0 release-blocking false positives.
- Unsupported objective speech released after proven violation: 0.
- Invalid durable writes committed after proven violation: 0.
- Duplicate recovery attempts beyond policy: 0.
- Repeated full-wait loops after circuit threshold: 0.
- Next-turn success after recoverable fault: 100% deterministic fixtures.
- Incident completeness: all required correlation/contract/recovery fields present.
- Candidate replayability for eligible deterministic incidents: target >=90% initial set.
- Common-turn Sentinel overhead: <=3 ms p95 synchronous.
- Sentinel queues/memory: no monotonic growth in soak.

## 22.4 Evaluation policy

- Deterministic invariants gate release.
- Synthetic providers and state models test edge cases.
- Human review verifies that degraded/fallback behavior remains understandable and not excessively repetitive.
- Do not use an LLM judge as the sole detector or oracle.

---

# 23. Connected Hytale Acceptance and Definition of Done

## 23.1 Connected scenarios

At minimum validate:

1. Normal 20-30 turn conversation with no false Sentinel intervention.
2. Supported epistemic factual, subjective, unknown, clarification, and action routes.
3. Test-build injection: supported route marked non-authoritative before dispatch.
4. Test-build injection: post-render prompt budget/hash mismatch.
5. Test-build injection: objective phrase missing claim validation.
6. One supported plus one unsupported clause; supported clause remains, unsupported clause is blocked.
7. Provider stall/cancel/drain and immediate next utterance.
8. Resource drift from preferred to degraded-ready profile.
9. TTS later-chunk failure with correct `PARTIAL` delivery truth.
10. Canonical speech duplicate/out-of-order callback injection.
11. Invalid belief/persistence write proposal; write rejected and prior state intact.
12. Repeated same signature until scoped circuit opens.
13. Half-open probe and recovery without flapping.
14. Automatic incident creation with trace disabled.
15. Automatic deterministic regression candidate and replay.
16. Restart and verify unresolved incident/candidate index remains readable and bounded.

## 23.2 User-visible behavior

- Unsafe output/state is blocked before becoming audible, actionable, or durable.
- Recoverable faults usually result in one bounded delay or a truthful degraded response, not repeated silence.
- Known-broken routes fail fast after circuit opening.
- Readiness HUD truthfully reports degraded/recovering/quarantined state.
- Other NPCs/routes remain usable when failure scope is narrow.
- No raw internal exception text is spoken to players.

## 23.3 Performance acceptance

- Common healthy turns add <=3 ms p95 synchronous Sentinel overhead.
- Incident capture does not materially change first-audible latency.
- No recurring >50 ms Sentinel-correlated Hytale hitch.
- Replay never runs during active pressure/foreground work.
- No monotonic incident queue, candidate queue, circuit registry, event-window, or memory growth.

## 23.4 Durable-state acceptance

Before E4 production belief writes:

- missing-provenance event is rejected;
- generated-speech contamination is rejected;
- duplicate event is idempotent;
- corrupt/partial tail cannot become current materialized truth;
- last verified state remains usable;
- persistence may enter read-only degraded mode;
- incident and replay candidate are created;
- older rollback build can ignore additive Sentinel files.

## 23.5 Definition of Done

- Orbis owns a versioned runtime invariant registry spanning turn, plan, provider, resource, speech, epistemic, action, and persistence boundaries.
- Critical irreversible side effects cannot occur without the required proof.
- All recoveries are pre-authorized, bounded, scoped, and executed through existing owners.
- Recovery is verified, not assumed.
- Repeated signatures open scoped circuits and stop whack-a-mole retry loops.
- Stable degraded modes preserve safe functionality without false `READY` state or rapid flapping.
- Material runtime failures automatically produce a compact incident even when manual tracing was not enabled.
- Eligible incidents automatically produce sanitized deterministic regression candidates.
- Candidates replay through the existing harness without real providers or live-world mutation.
- Sentinel does not modify source, binaries, models, or authoritative world state.
- Sentinel overhead and queues remain bounded beneath Hytale and foreground conversation.
- Persistent cognition is protected from invalid/generated-speech contamination before E4-E8 are promoted.
- The remaining source defects arrive to Codex as concise reproducible incidents rather than requiring the player to discover, trace, explain, and retest every failure manually.

---

# Appendix A. Proposed Component Map

| Component | Responsibility | Disposition |
|---|---|---|
| `OrbisTurnCoordinator` | Authoritative turn/branch/epoch/cancellation state | Retain |
| `TurnPlanCompiler` | Route/context/output/budget/recovery contract | Retain; expose final rendered/hash checkpoint |
| `RecoverySupervisor` | Execute bounded recovery and cleanup | Retain; consume registered recovery plans |
| `OrbisResourceScheduler` | Sustainable envelope, profiles, admission | Retain; publish cached health events |
| `DialogueClaimValidator` / `EpistemicClaimFirewall` | Objective speech authority | Retain; publish claim coverage/verdicts |
| `CanonicalSpeechLedger` | Canonical lexical/delivery truth | Retain; add guard hook before append |
| `OrbisDegradationSentinel` | Coordinate evaluation, health, policy requests | Add |
| `InvariantRegistry` | Versioned invariant definitions | Add |
| `InvariantEvaluatorRegistry` | Boundary/trend evaluators | Add |
| `SentinelStateProjection` | Bounded event-derived evaluation state | Add |
| `FailureSignatureEngine` | Normalize and fingerprint root failure classes | Add |
| `ScopedHealthRegistry` | Health state and hysteresis per scope | Add |
| `RecoveryPolicyRegistry` | Approved recovery mappings and postconditions | Add |
| `ScopedCircuitBreakerRegistry` | Repetition, open/half-open/close state | Add |
| `DurableMutationGate` | Pre-append belief/memory/persistence invariants | Add before E4 authority |
| `OrbisIncidentRecorder` | Bounded sanitized incident persistence | Add |
| `RegressionCandidateExtractor` | Convert incidents to replay data | Add in S3 |
| `IncidentReplayHarness` | Adapt candidates to ConversationMatrixHarness | Add in S3 |
| `ConversationMatrixHarness` | Synthetic deterministic regression system | Retain/extend |
| Cognition Inspector/readiness HUD | Health, circuits, incidents, controls | Retain/extend |

---

# Appendix B. Normative Data Contracts

```java
enum SentinelMode {
    OFF, OBSERVE, ENFORCE
}

enum SentinelSeverity {
    NOTICE, WARNING, DEGRADED, CRITICAL, FATAL_OPERATOR_REQUIRED
}

enum DetectionConfidence {
    PROVEN, HIGH_CONFIDENCE, SUSPECT, INSUFFICIENT_DATA
}

enum InvariantScope {
    REQUEST, TURN, BRANCH, CONVERSATION_SCENE, NPC, ROUTE,
    PROVIDER, RESOURCE_PROFILE, PERSISTENCE_STREAM, WORLD,
    GLOBAL_RUNTIME
}

enum ScopedHealth {
    HEALTHY, SUSPECT, DEGRADED, RECOVERING,
    QUARANTINED, FAILED_OPERATOR_REQUIRED
}

enum CircuitState {
    CLOSED, OPEN, HALF_OPEN, DISABLED_BY_OPERATOR
}

enum VerdictStatus {
    PASS, FAIL, NOT_APPLICABLE, INSUFFICIENT_DATA, EVALUATOR_ERROR
}

enum IncidentState {
    DETECTED, CONTAINED, RECOVERY_STARTED, RECOVERED_VERIFIED,
    DEGRADED_STABLE, QUARANTINED, UNRESOLVED, OPERATOR_REQUIRED
}

enum CandidateStatus {
    NEW, REPLAYABLE, NON_DETERMINISTIC,
    REPLAY_PASSED_CURRENT_BUILD, REPLAY_FAILED_CURRENT_BUILD,
    PROMOTED_TO_SOURCE_FIXTURE, REJECTED_AS_FALSE_POSITIVE,
    SUPERSEDED
}
```

```java
record DegradationSignal(
    SignalId id,
    InvariantId invariantId,
    SentinelSeverity severity,
    DetectionConfidence confidence,
    InvariantScope scope,
    ScopeKey scopeKey,
    String boundedReasonCode,
    List<CorrelationId> evidence,
    FailureSignatureSeed signatureSeed,
    Instant detectedAt
) {}
```

```java
record RecoveryPlan(
    RecoveryPlanId id,
    RecoveryPolicyId policyId,
    FailureSignature signature,
    InvariantScope scope,
    ScopeKey scopeKey,
    List<RecoveryAction> actions,
    int allowedAttempts,
    Duration softDeadline,
    Duration hardDeadline,
    List<PostconditionId> postconditions,
    long branchEpoch,
    String policyVersion
) {}
```

```java
record RecoveryOutcome(
    RecoveryPlanId planId,
    RecoveryOutcomeStatus status,
    List<RecoveryActionResult> actions,
    List<PostconditionResult> postconditions,
    boolean cleanupVerified,
    boolean nextUseVerified,
    ScopedHealth resultingHealth,
    Instant completedAt
) {}
```

All persistent/network/trace DTOs contain stable semantic IDs and immutable values only. Never serialize Hytale `Ref`, `Store`, `EntityStore`, command buffers, provider handles, filesystem handles, or arbitrary Java objects.

---

# Appendix C. Initial Invariant Catalog

| ID | Boundary | Requirement | Severity | Initial recovery |
|---|---|---|---|---|
| TURN-001 | transcript accept | one accepted transcript and one turn per utterance | CRITICAL | reject duplicate/stale |
| TURN-002 | terminal cleanup | exactly one terminal transition | CRITICAL | idempotent cleanup, circuit on repeat |
| TURN-003 | terminal cleanup | resource handles released exactly once | CRITICAL | cleanup verification, quarantine leaking component |
| PLAN-001 | provider dispatch | valid TurnExecutionPlan exists | CRITICAL | reject, recompile once |
| PLAN-002 | provider dispatch | budgeted prompt hash equals dispatched prompt hash | CRITICAL | re-render/re-budget once |
| PLAN-003 | provider dispatch | supported epistemic route is authoritative in AUTHORITATIVE mode | CRITICAL | corrected compile or route quarantine |
| PLAN-004 | provider dispatch | final prompt/schema/output fit budgets | CRITICAL | prune/recompile or fail fast |
| STT-001 | transcript accept | one authoritative transcript, no material partial/final collapse | DEGRADED | one preserved-audio retranscription |
| STT-002 | semantic planning | malformed input cannot receive high-confidence query plan | DEGRADED | clarification/unresolved |
| PROV-001 | provider stream | event belongs to current request/epoch | CRITICAL | stale discard, incident on repeat |
| PROV-002 | provider terminal | cancel/drain is real before READY | CRITICAL | wait bounded, restart/circuit |
| PROV-003 | provider stream | delta sequence monotonic/non-duplicate | CRITICAL | discard, restart if stream integrity lost |
| RES-001 | readiness sample | READY implies sustainable foreground envelope | DEGRADED | approved profile transition |
| RES-002 | admission | repeated same starvation signature fails fast after threshold | DEGRADED | circuit/profile quarantine |
| RES-003 | pressure epoch | no provider residency ping-pong | DEGRADED | stable profile for epoch |
| EPI-001 | claim validation | objective claim has compatible evidence | CRITICAL | drop/repair clause |
| EPI-002 | claim validation | property needs property-level support | CRITICAL | drop unsupported property |
| EPI-003 | claim validation | UNKNOWN/CONFLICTED cannot become certainty | CRITICAL | safe answer-plan realization |
| EPI-004 | persistence proposed | generated speech is not factual evidence | CRITICAL | reject write, persistence incident |
| ACT-001 | speech/action commit | action promise/result claim requires authority | CRITICAL | block promise; preserve action truth |
| SPEECH-001 | ledger append | objective segment has claim verdict | CRITICAL | reject append/repair |
| SPEECH-002 | ledger append | spans ordered, contiguous, non-overlapping | CRITICAL | preserve prefix, partial/fail |
| SPEECH-003 | playback/history | only delivered text enters history | CRITICAL | correct delivery state, incident |
| PERSIST-001 | durable append | event schema, provenance, revision valid | CRITICAL | reject write/read-only on repeat |
| PERSIST-002 | replay | snapshot + tail deterministic and checksum-valid | CRITICAL | rebuild/rollback/read-only |
| PERSIST-003 | durable append | duplicate event ID is idempotent | DEGRADED | ignore duplicate, incident on divergent payload |
| PERF-001 | latency window | sustained stage p95 stays within configured profile | WARNING/DEGRADED | health degrade, profile/circuit review |
| NEXT-001 | recovery verification | next valid turn/use remains available | CRITICAL | keep scope recovering/quarantined |

---

# Appendix D. Initial Recovery Matrix

| Failure class | Detect before side effect? | Bounded recovery | Stable fallback |
|---|---:|---|---|
| Missing/invalid plan | Yes | recompile once | fail fast / route quarantine |
| Prompt hash/budget mismatch | Yes | prune, render, re-budget once | fail fast; incident |
| Supported route bypass | Yes | compile from authoritative semantic route | SHADOW/safe route quarantine |
| STT collapse/suspect final | At transcript boundary | one preserved-audio retranscription | unresolved/clarification |
| Provider cancel not drained | During cancel | bounded wait, restart | provider circuit open |
| Provider event order corrupt | During stream | discard; restart if integrity lost | provider quarantine |
| Resource envelope unsafe | Continuous | approved lower profile once | degraded-ready or fail fast |
| Objective claim unsupported | Before ledger | drop/repair clause once | safe AnswerPlan fallback/partial |
| Action promise unsupported | Before ledger/action | block commitment | truthful inability/rejection |
| Canonical span corruption | Before append | reject invalid span | preserve prefix as PARTIAL |
| TTS later chunk fails | During synthesis/playback | calibrated smaller chunk if policy allows | PARTIAL; later text not delivered |
| Belief write lacks provenance | Before append | none by invention; reject | persistence read-only on repeat |
| Snapshot/tail mismatch | Startup/rebuild | verified rebuild/rollback | read-only/operator required |
| Repeated same signature | Occurrence threshold | no repeated full recovery | scoped circuit/quarantine |
| Incident writer failure | Background | bounded retry | keep gameplay; operator warning |

---

# Appendix E. Project Sources and Design Basis

Project documents are authoritative for current ownership, contracts, and implementation boundaries.

**[P1] Orbis Technical Design.** Existing authoritative turn, branch, provider, cancellation, resource, action, playback, trace, and persistence ownership.

**[P2] Orbis Conversational Pipeline Hardening Matrix.** Contract-driven turn plans, pre-dispatch budgets, canonical speech, bounded recovery, resource safety, generated conversation matrix, next-turn readiness, and the requirement that historical failures become permanent regressions.

**[P3] Orbis Epistemic Cognition Technical Design.** Authoritative evidence/AnswerPlan/claim-firewall path, speech-is-not-truth, persistence provenance, rollback-safe bounded implementation, and the requirement that generated dialogue never self-seed beliefs.

**[T1] Current connected Mara traces and R0xx implementation reports.** Local evidence that novel integration drift can bypass otherwise-correct isolated components and still require manual trace diagnosis.

The design also uses standard deterministic resilience concepts such as invariant guards, watchdogs, scoped circuit breakers, stable degraded modes, event-sourced incidents, and replayable failure fixtures. These are design patterns, not new runtime dependencies.

---

> **END OF SPECIFICATION**  
> Codex must implement S1, S2, and S3 as separate bounded tasks. The success criterion is not that Orbis can edit its own code. The success criterion is that Orbis notices contract degradation before the player does where possible, blocks unsafe speech/action/persistence, restores a pre-authorized safe state, stops repeating a known-broken path, proves the next use is healthy, and hands Codex a compact deterministic reproduction when source engineering is still required.
