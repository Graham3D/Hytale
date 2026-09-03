**ORBIS CONVERSATIONAL PIPELINE**

**HARDENING MATRIX**

Systemic Reliability, Latency and Voice-Delivery Technical Design

Subsystem of ImmersiveNPCs / Orbis  
Codex Implementation Specification  
Version 1.0 | 30 August 2026

| AUTHORITATIVE PURPOSE. Replace route-by-route patching with a contract-driven, matrix-tested conversational pipeline. Codex must harden the complete Moonshine \-\> Orbis/Nemotron \-\> Chatterbox \-\> Hytale path before further NPC feature work. |
| :---- |

**VOICE ACTING IMMERSION**  
one coherent, expressive response with complete spoken delivery

**LATENCY EFFICIENCY**  
fast ordinary dialogue and bounded deep reasoning

**CONSUMER HARDWARE SAFETY**  
no unacceptable Hytale frame pressure, VRAM emergency or provider thrash

*This document is written for Codex implementation use. Proposed class names are normative concepts; repository ownership and existing abstractions remain authoritative.*

# **Contents**

1\. Implementation Directive

2\. Executive Decision and Trace Evidence

3\. Scope, Goals and Non-Goals

4\. Pipeline Invariants

5\. Hardened Architecture and Turn Plan

6\. Route and Output-Contract Matrix

7\. Contract Compiler and Token Budget Planner

8\. Context Profiles and Prompt Containment

9\. Nemotron Reliability and Reasoning Finalization

10\. Canonical Speech Ledger and Chatterbox Delivery

11\. Moonshine Transcript Integrity

12\. Failure Containment and Recovery Supervisor

13\. Resource and Lifecycle Hardening

14\. Conversation Matrix Test Harness

15\. Native Hytale UI, Trace and Diagnostics

16\. One-Phase Implementation Plan

17\. Verification, Performance Budgets and Definition of Done

Appendix A. Matrix Axes and Critical Cases

Appendix B. Proposed Component Map

Appendix C. Sources

# **1\. Implementation Directive**

This design is a systemic hardening phase for the current Orbis cascade. It is not another narrow bugfix, not a model replacement, and not a new conversational runtime.

* **MUST** read Orbis Technical Design.docx, current ownership reports, R058-current implementation reports, and the three trace files listed in Section 2 before changing code.  
* **MUST** preserve Orbis authority over TurnId, NpcTurnBranch, floor ownership, cancellation, resource admission, world truth, action validation, delivery truth, trace, and Hytale spatial playback.  
* **MUST** keep Moonshine, nemotron-3-nano:4b, Chatterbox Turbo, current profiles, memories, relationships, tasks, schedules, autonomy, and persistent data formats unless a migration-safe correction is strictly required.  
* **MUST** implement this as one bounded Codex engineering effort with internal gates. Do not deploy a partially hardened route matrix.  
* **MUST NOT** continue adding route-specific exceptions as the primary solution. Every fix must attach to a shared contract, invariant, or generated matrix test.  
* **MUST NOT** change NPC world logic, add new gameplay capabilities, replace providers, or re-open Direct Voice work during this phase.  
* **MUST** reuse current Java/Python provider adapters and test infrastructure where equivalent ownership already exists. Remove duplicate/legacy response assembly paths if they compete with the hardened path.

| DEPLOYMENT GATE. No new JAR is production-ready until the complete route/contract/failure matrix passes deterministic tests and a connected Hytale mixed-route soak. A single repaired trace is not sufficient. |
| :---- |

# **2\. Executive Decision and Trace Evidence**

The recurring failures are not independent model defects. They occur where routing, context selection, output contracts, token budgets, streaming finalization, and recovery policies change between conversation types. The solution is to compile every turn into a validated execution contract before provider dispatch and test the cross-product systematically.

| Trace evidence | Observed failure | Systemic implication |
| :---- | :---- | :---- |
| Mara\_2026-08-30\_11-04-53.jsonl | Repeated \`canonical chunks must be ordered\`; final response diverged from an immutable early phrase; a perception question was routed to long deliberation. | Streaming speech had no single append-only source of truth; router and finalizer could disagree after audio was committed. |
| Mara\_2026-08-30\_11-42-33.jsonl | A clarification phrase containing “make” triggered action/crafting semantics, a 19,864-character / \~4,966-token prompt, and 512 reasoning-only events over \~29 seconds with no dialogue. | Keyword heuristics, broad context expansion and reasoning lacked shared semantic and wall-clock contracts. |
| Mara\_2026-08-30\_12-00-25.jsonl | Two FAST turns completed, but a DIRECT\_ACTION turn sent 16,599 characters / \~4,150 prompt tokens with 13 context sections and only 96 output tokens. Nemotron stopped mid-JSON; schema validation failed with EOF. | The route selected an output contract whose minimum valid serialization and prompt/schema cost were incompatible with its budget. Structured failure killed the whole turn. |

| CENTRAL DESIGN ANSWER. Introduce a TurnPlanCompiler, route-specific output contracts, a pre-dispatch ContractBudgetPlanner, an append-only CanonicalSpeechLedger, a bounded RecoverySupervisor, and a generated ConversationMatrixHarness. No provider request may launch without a valid plan. |
| :---- |

# **3\. Scope, Goals and Non-Goals**

## **3.1 Goals**

* Make every supported conversation route complete reliably from ingress through delivered Hytale audio.  
* Preserve expressive cloned voice, ordered multi-sentence delivery, phrase-level low-latency TTS, and emotion/prosody metadata without adding another inference model.  
* Keep ordinary FAST/GROUNDED dialogue responsive while retaining bounded deep reasoning and autonomous cognition for genuinely complex decisions.  
* Prevent prompt/schema/output-budget mismatch before provider dispatch.  
* Guarantee that malformed, truncated, reasoning-only, silent, out-of-order, cancelled, or resource-starved provider behavior cannot wedge later turns.  
* Cover the route/failure/resource/cancellation matrix efficiently through exhaustive critical cases plus pairwise generated coverage.  
* Keep Hytale as the highest-priority workload on the target RTX 4070 Ti 12 GB consumer system.

## **3.2 Non-Goals**

* Replacing Nemotron, Chatterbox Turbo, Moonshine, Ollama, Hytale VoiceSpeaker, or Orbis.  
* Adding new NPC knowledge such as server time, new actions, new schedules, or new world perception semantics.  
* Fine-tuning/training providers.  
* Building an external dashboard or a second trace pipeline.  
* Guaranteeing datacenter-scale concurrency on one 12 GB GPU.  
* Using repeated LLM repair loops to conceal an invalid contract.

# **4\. Pipeline Invariants**

1. One physical utterance creates exactly one canonical PlayerUtterance and one OrbisTurn.  
2. Every branch and turn reaches exactly one terminal state: COMPLETED, PARTIAL, CANCELLED, or FAILED.  
3. Every provider request is preceded by a valid TurnExecutionPlan and ContractBudgetPlan.  
4. A structured contract is never dispatched when its bounded serialized output cannot fit inside the allocated final-answer budget.  
5. No route receives a giant union schema or all context sections by default.  
6. Plain spoken dialogue uses plain text streaming; structured control output is isolated from the early-speech path.  
7. One NPC turn owns one canonical spoken response. All TTS chunks cover contiguous, ordered, non-overlapping spans of that response exactly once.  
8. Already committed speech is never rewritten by final parsing or post-hoc normalization.  
9. Displayed dialogue, delivered speech, and canonical conversation history preserve identical content and order; only actually delivered segments enter history.  
10. Action promises require authoritative action/decision evidence; generated speech never mutates Hytale directly.  
11. Late/stale callbacks cannot cross response epochs, branches, players, NPCs, or scenes.  
12. Every resource reservation, queue entry, playback handle and provider request is released exactly once at terminal cleanup.  
13. Failure in one turn cannot prevent the next valid microphone utterance from being captured, transcribed, routed and answered.  
14. No network, model, resampling, JSON parsing, TTS generation, disk I/O or resource polling blocks Hytale voice/world/UI callbacks.

# **5\. Hardened Architecture and Turn Plan**

Player voice/text/internal ingress  
        |  
        v  
OrbisTurnCoordinator  
        |  
        v  
TurnPlanCompiler \---------------------\> RecoverySupervisor  
  |             |                         |  
  |             \+-\> ContextProfile        \+-\> bounded retry/fallback  
  |             \+-\> CognitionMode         \+-\> terminal cleanup  
  |             \+-\> DecisionContract      \+-\> next-turn readiness  
  |             \+-\> SpeechContract  
  |             \+-\> Budget/Deadline Plan  
  v  
Provider Adapter (Moonshine / Nemotron / Chatterbox)  
        |  
        \+-\> ContractValidator / Grounding / Action authority  
        |  
        v  
CanonicalSpeechLedger \-\> TTS Phrase Queue \-\> Hytale VoiceSpeaker  
        |  
        \+-\> delivered text/history only

The compiler separates cognition depth from output shape. FAST, GROUNDED, DIRECT\_ACTION, DELIBERATIVE and AUTONOMOUS are not allowed to imply one universal prompt/schema. The same cognitive depth may use different contracts depending on whether Orbis already owns the action result or requires a compact discretionary decision.

## **5.1 TurnExecutionPlan**

record TurnExecutionPlan(  
    TurnId turnId,  
    NpcTurnBranch branch,  
    CognitionMode cognitionMode,  
    ContextProfile contextProfile,  
    DecisionContract decisionContract,  
    SpeechContract speechContract,  
    ContractBudgetPlan budgets,  
    DeadlinePlan deadlines,  
    RecoveryPolicy recovery,  
    long branchEpoch  
) {}

* The plan is immutable and traced before any provider request.  
* If compilation fails, the turn fails before inference with an explicit PLAN\_REJECTED reason and no resource leak.  
* Provider adapters consume the plan; they may not infer their own route, schema, token limit or recovery behavior.

# **6\. Route and Output-Contract Matrix**

| Route | Authoritative work before LLM | Provider output contract | Reasoning | Early speech |
| :---- | :---- | :---- | :---- | :---- |
| FAST\_DIALOGUE | Audience, recent dialogue, profile/personality, relationship summary | DialogueTextContract: one plain canonical reply | Off | Yes |
| GROUNDED\_DIALOGUE | Retrieve compatible memory/world/profile evidence | DialogueTextContract \+ deterministic claim guard | Off unless evidence conflicts | Yes |
| DIRECT\_ACTION: deterministic | Parse target/action; capability/world validation; commit/reject through Orbis | ActionResultDialogueContract: plain speech conditioned on authoritative result | Off | Yes after result |
| DIRECT\_ACTION: discretionary choice | Resolve available choices/capabilities; no action commit yet | CompactChoiceContract: strict bounded JSON, including bounded spokenText or reasonCode | Off or one bounded deliberation stage | No until contract validates |
| DELIBERATIVE | Bounded relevant evidence/goals/plans; explicit decision question | Two-stage: bounded reasoning memo, then CompactDeliberativeFinalContract with reasoning off | On only in stage 1 | No until final contract validates |
| AUTONOMOUS | Perception/attention/opportunity and current plans | AutonomousDecisionContract: strict bounded JSON; speech absent unless separately scheduled | Adaptive/bounded | Not player-blocking |

| NO GIANT ACTION SCHEMA. For an unambiguous player request, Orbis already owns parsing, capability checks and execution. Nemotron should describe the authoritative result, not re-emit broad action arrays. Only discretionary choices or autonomous decisions use structured control contracts. |
| :---- |

# **7\. Contract Compiler and Token Budget Planner**

Structured output must be treated as a compiled protocol, not a prompt suggestion. Ollama supports JSON-schema-constrained output through \`format\` / OpenAI-compatible \`response\_format\`; official guidance recommends reusable schemas and low temperature. \[S1\] Known upstream issues show that schema cost may be missing from reported prompt counts and that max-token truncation can cut JSON/tool arguments. \[S2\]\[S3\]

## **7.1 Contract rules**

* Use versioned route-specific schemas with bounded strings, bounded arrays, enums, and no open-ended \`details\` objects.  
* Structured calls use reasoning off, temperature 0, and non-streaming unless the exact installed backend proves schema-constrained streaming reliable.  
* Do not combine reasoning \+ streaming \+ strict structured output in one request. Community reports show this combination can bypass or break schema enforcement. \[S4\]  
* Do not use large tool-call arguments or giant schemas as a transport for dialogue.  
* The schema itself is included in the prompt-budget calculation. Do not trust provider \`prompt\_eval\_count\` alone because Ollama has reported schema omission from that metric. \[S2\]  
* Every field has a maximum serialized length. The compiler calculates a conservative worst-case output size and refuses to dispatch if it does not fit.

## **7.2 ContractBudgetPlan**

promptTokens  
\+ schemaTokens  
\+ reasoningReserve  
\+ finalAnswerReserve  
\+ safetyMargin  
\<= configuredContextWindow

maxOutputTokens \>= boundedWorstCaseSerializedTokens \+ 25% safety margin

| Profile | Initial prompt ceiling | Final output ceiling | Notes |
| :---- | :---- | :---- | :---- |
| FAST dialogue | \~600 tokens | 56-80 tokens | Plain text; one concise reply. |
| GROUNDED dialogue | \~1,200 tokens | 80-112 tokens | Only directly relevant evidence. |
| Deterministic direct action | \~1,400 tokens | 80-112 tokens | No action schema; authoritative result supplied. |
| Discretionary choice | \~2,000 tokens including schema | 160-224 tokens | Compact enum-based JSON; bounded spoken text. |
| Deliberative stage 1 | \~5,000 tokens | Bounded reasoning/time | No structured speech. |
| Deliberative finalization | \~1,500 tokens including schema | 192-256 tokens | Reasoning off; compact final contract. |
| Autonomous decision | \~5,000 tokens including schema | 192-320 tokens | No player-facing speech by default. |

These are initial ceilings for Codex calibration, not immutable balance values. Any route requiring more must explain the additional context in trace and still pass the contract preflight.

## **7.3 Truncation handling**

* Map \`done\_reason=length\`, output count equal to budget, EOF/unterminated JSON, and missing required fields to TRUNCATED\_OUTPUT rather than generic PROVIDER\_FAILURE.  
* Never parse or execute partial structured output.  
* Allow at most one bounded retry when the planner can prove a safe corrected budget/context. The retry must use the same branch and reject stale output from the first request.  
* If the contract still fails, emit a deterministic recovery response when TTS is healthy, terminate the branch cleanly, and preserve next-turn readiness.

# **8\. Context Profiles and Prompt Containment**

Context selection is part of the contract. The latest failed DIRECT\_ACTION turn included 13 sections despite zero relevant memories and only one offered action. This must become impossible by construction.

| Context section | FAST | GROUNDED | Deterministic action | Choice | Deliberative | Autonomous |
| :---- | :---- | :---- | :---- | :---- | :---- | :---- |
| Profile/personality | Required | Required | Required | Required | Required | Required |
| Recent delivered dialogue | Required | Required | Required | Required | Required | Optional |
| Relationship summary | Compact | Compact | Compact | Compact | Relevant only | Relevant only |
| Relevant memories/beliefs | No | Evidence only | Target-specific | Choice-specific | Ranked | Ranked |
| Semantic world/perception | Minimal current | Evidence only | Target/action only | Choice only | Relevant subset | Relevant subset |
| Capabilities/actions | No | No | Selected action only | Available choices only | Relevant choices | Relevant subset |
| Tasks/plans/goals/obligations | Active item only if referenced | Only if answer depends on it | Only if action depends on it | Only if choice depends on it | Ranked and bounded | Ranked and bounded |

* Context builders read current in-memory indexes; no JSON/JSONL disk reads on the live path.  
* Use explicit section allowlists and per-section token ceilings. Prune lowest-relevance data first.  
* Trace included/omitted sections, token estimate, evidence IDs and prune reason.  
* The user transcript and route question are never pruned.  
* Action definitions are compact semantic contracts, not raw Java classes, ECS handles or every supported capability.

# **9\. Nemotron Reliability and Reasoning Finalization**

NVIDIA documents Nemotron 3 Nano 4B as a unified reasoning/non-reasoning model and notes a quality tradeoff when reasoning is disabled for harder tasks. \[S5\] The hardened design keeps adaptive reasoning but prevents reasoning, structured output and speech streaming from competing inside one fragile response format.

## **9.1 Plain dialogue path**

* FAST/GROUNDED/ActionResult dialogue requests return plain text only.  
* Stream tokens into the CanonicalSpeechLedger; do not wrap spoken dialogue in JSON.  
* First speech can commit only at a complete semantic phrase and after deterministic grounding/action checks.  
* The final provider text is not reparsed into a second rewritten canonical version.

## **9.2 Deliberative structured path**

* Stage 1: bounded reasoning on, unexposed memo/result, explicit wall-clock and event/token ceiling.  
* Stage 2: same model, reasoning off, compact strict JSON finalization with only the decision fields needed by Orbis.  
* If stage 1 yields no usable result before its budget, use the existing single bounded same-model recovery with compact context; never allow unbounded reasoning-only streams.  
* Player focus/range loss, barge-in, disconnect, stale epoch or higher-priority Hytale pressure cancels both stages immediately.

## **9.3 Provider response classifier**

| Provider outcome | Required Orbis classification |
| :---- | :---- |
| Valid plain text | DIALOGUE\_COMPLETE or DIALOGUE\_PARTIAL |
| Valid strict JSON | CONTRACT\_VALID |
| Length stop / output budget exhausted | TRUNCATED\_OUTPUT |
| Reasoning events with no final content | REASONING\_NO\_FINAL |
| Empty content | EMPTY\_PROVIDER\_OUTPUT |
| Malformed JSON / schema mismatch | CONTRACT\_INVALID |
| Transport timeout/disconnect | PROVIDER\_TRANSPORT\_FAILURE |
| Cancelled/stale callback | STALE\_DISCARDED |

# **10\. Canonical Speech Ledger and Chatterbox Delivery**

One NPC turn produces one canonical spoken response. Multiple sentences, newlines and emotional beats are normalized into that response; Chatterbox receives ordered phrases, not independent dialogue packets.

## **10.1 Append-only speech contract**

record CanonicalSpeechSegment(  
    ResponseId responseId,  
    int segmentIndex,  
    int startChar,  
    int endChar,  
    String text,  
    ProsodyCue prosody,  
    EvidenceClass evidenceClass,  
    DeliveryState deliveryState  
) {}

* The provider stream is the only lexical source of truth for plain dialogue.  
* Whitespace/newlines are normalized incrementally; committed prefix characters never change.  
* Segment indices and character spans are strictly monotonic, contiguous and non-overlapping.  
* The final canonical response must equal the concatenation of accepted segments plus any validated final tail. A mismatch is a provider/assembler defect, not a reason to rewrite delivered speech.  
* If later output fails, preserve already delivered segments as PARTIAL and discard the rest.

## **10.2 Chatterbox chunking and voice acting**

Chatterbox Turbo is the production TTS baseline. Resemble positions Turbo as its low-compute/low-VRAM voice-agent model with native paralinguistic tags. \[S9\] Community reports indicate instability on long inputs, including degradation above roughly 350 characters or 25-30 seconds, so Orbis must keep synthesis phrases bounded. \[S10\]\[S11\]

* Prefer complete sentences. If a sentence exceeds the calibrated maximum, split at a clause boundary, then a word boundary as a last resort.  
* Initial maximum: 220-280 normalized characters per TTS request; never exceed the empirically validated safe ceiling.  
* Do not synthesize empty, punctuation-only, or extremely short fragments unless they are approved paralinguistic cues.  
* Reuse one cached voice conditioning object per NPC voice/profile revision.  
* Prosody/emotion metadata comes from existing Orbis emotional state or the validated phrase; do not add another LLM call.  
* Render supported Chatterbox tags from an allowlist in the TTS adapter. Tags are metadata and do not appear in player-visible canonical text.  
* Queue/synthesize later phrases while prior audio plays, but never reorder them or display text ahead of deliverable speech.

## **10.3 Delivery truth**

* A segment becomes DELIVERED only after Hytale playback completion confirms it.  
* Visible dialogue must be one canonical response. If the current chat surface cannot update one line incrementally, finalize the text once while audio may start earlier; do not emit disconnected text lines that only partially speak.  
* TTS failure after one or more delivered segments produces PARTIAL, not full conversation history.

# **11\. Moonshine Transcript Integrity**

Moonshine official APIs support true streaming transcription that reuses prior audio work, and its benchmarks target sub-200 ms end-of-speech finalization. \[S6\]\[S7\] Community reports show a failure mode where streaming partials are correct but the final committed transcript contains only the first portion. \[S8\] The hardened path must detect this class of collapse.

* Feed the streaming model continuously from the authoritative capture session; keep callbacks off Hytale threads.  
* Preserve the bounded complete utterance PCM until final transcript acceptance.  
* Track latest stable partial and final transcript. If the final loses a material suffix, becomes implausibly shorter, ends in an incomplete token/hyphen, or violates stream sequence, mark FINAL\_TRANSCRIPT\_SUSPECT.  
* On suspicion, perform at most one bounded re-transcription from preserved audio using batch Moonshine or Faster-Whisper fallback according to current health/policy.  
* Exactly one transcript crosses AUTHORITATIVE\_TRANSCRIPT\_ACCEPTED. Partials never create memory, actions or turns.  
* Warm the real streaming session before player-ready and trace requested/actual engine and fallback reason.

# **12\. Failure Containment and Recovery Supervisor**

| Stage/fault | Bounded behavior | Terminal guarantee |
| :---- | :---- | :---- |
| Capture/STT timeout or suspect final | One preserved-audio retry/fallback; no fabricated text. | Turn fails or continues with one authoritative transcript. |
| Router/plan invalid | Reject before provider dispatch; record PLAN\_REJECTED. | No model/resource work begins. |
| Prompt/context overflow | Prune by profile; if still invalid, reject. | No silent truncation. |
| Reasoning no final | Cancel at budget; one same-model reasoning-off recovery. | Original output remains stale. |
| Structured truncation/invalid JSON | Do not execute; one planner-corrected retry at most. | Safe recovery dialogue or clean failure. |
| Grounding/action rejection | Block unsupported segment/commitment. | No false speech/action. |
| TTS failure | Retry only if policy allows smaller safe chunk; otherwise PARTIAL/FAILED. | No text marked delivered without audio. |
| Resource starvation | Bounded admission and reclaim; explicit timeout. | No indefinite queue or blocked next turn. |
| Barge-in/focus loss/disconnect | Cancel providers, queued TTS and playback; increment epoch. | Late callbacks discarded. |

## **12.1 Recovery dialogue**

After the allowed retry is exhausted, Orbis may emit one short deterministic recovery utterance through Chatterbox when TTS is healthy. It is not a model fallback, does not claim world/action facts, is explicitly traced, and prevents silent failure from masquerading as an unresponsive NPC.

## **12.2 Cleanup assertions**

* Terminal cleanup is idempotent.  
* Active capture, STT, LLM, TTS, playback, resource permits and queue entries return to zero/expected baseline.  
* No terminal failure may cancel a newer epoch.  
* The next turn must pass a synthetic immediate-follow-up probe in tests.

# **13\. Resource and Lifecycle Hardening**

* Keep the R057+ asynchronous world-load warmup and provider residency telemetry.  
* A TurnExecutionPlan records whether required providers are warm; cold-load cost is never hidden inside ordinary stage timing.  
* Foreground player conversation outranks autonomous/background cognition; Hytale outranks all AI work.  
* No provider may evict another required provider without a measured lifecycle plan. Avoid Nemotron/Chatterbox warmup ping-pong.  
* Resource gates remain bounded. Overlap LLM/TTS only if connected Hytale measurements prove it safe; otherwise serialize without deadlock and expose the wait.  
* Keep the 512 MiB Hytale GPU safety reserve or a newer measured reserve. Never solve reliability by admitting work into VRAM emergency.  
* Bound all executor and telemetry queues; no synchronous \`nvidia-smi\` or worker RPC on recheck/UI paths.  
* Record cold/warm state, incremental memory need, queue time, inference time and frame-pressure state separately.

# **14\. Conversation Matrix Test Harness**

The matrix is the primary anti-whack-a-mole mechanism. NIST reports that most faults arise from interactions among a small number of parameters and that t-way combinatorial testing can provide strong fault detection with far fewer cases than exhaustive cross-products. \[S12\] Use exhaustive critical cases plus generated pairwise coverage for the remaining dimensions. JUnit parameterized/dynamic tests are sufficient if already present; do not add a new dependency unless needed. \[S13\]

## **14.1 Matrix axes**

| Axis | Values |
| :---- | :---- |
| Ingress | VOICE\_CAPTURE, NATIVE\_TEXT\_CHAT, MANUAL\_SUBMISSION, NPC\_INTERNAL |
| Cognition | FAST, GROUNDED, DIRECT\_ACTION, DELIBERATIVE, AUTONOMOUS |
| Output contract | Plain dialogue, action-result dialogue, compact choice, deliberative final, autonomous decision |
| Provider state | Warm, cold/loading, degraded, unavailable, timeout |
| Resource state | Normal, VRAM pressure, GPU gate contention, CPU pressure, queue saturation |
| Output behavior | Valid, length-truncated, invalid JSON, empty, reasoning-only, duplicate, out-of-order, divergent final, hallucinated claim |
| Cancellation point | Before dispatch, STT, context, reasoning, phrase stream, TTS, playback, focus/range loss, disconnect |
| Conversation topology | Single NPC, multi-listener, owner mismatch, two scenes, repeated turns |

## **14.2 Harness components**

* **ConversationMatrixScenario** immutable test input combining the axes and expected terminal outcome.  
* **SyntheticMoonshineProvider** partial/final sequencing, collapse, timeout, error and fallback fixtures.  
* **SyntheticNemotronProvider** plain text, strict JSON, truncation, reasoning-only, malformed, duplicate/out-of-order and delayed fixture streams.  
* **SyntheticChatterboxProvider** success, slow synthesis, chunk failure, stale completion and playback fixtures.  
* **SyntheticResourceScheduler** admission, pressure, reclaim, timeout and permit-leak injection.  
* **TurnStateModel** allowed state transitions and terminal invariants; implementation events must conform.  
* **GoldenTraceAssertions** verify event order, identifiers, budgets, cleanup, delivery truth and next-turn recovery.

## **14.3 Coverage policy**

* Exhaustively test every cognition/output-contract pair through success, truncation, cancellation and provider failure.  
* Exhaustively test every stage boundary for stale callback and resource-release correctness.  
* Generate pairwise coverage across non-critical axes; persist the generated seed/case list for reproducibility.  
* Replay all previously failing trace patterns as permanent regression fixtures.  
* Run 1,000+ synthetic mixed cases and at least one 100-turn deterministic soak without monotonic queue/resource growth.

# **15\. Native Hytale UI, Trace and Diagnostics**

Extend the existing Cognition Inspector and compact trace. Do not create an external dashboard or duplicate telemetry pipeline.

## **15.1 Required trace events/fields**

* TURN\_PLAN\_COMPILED: cognition mode, context profile, decision/speech contract, schema version, deadlines and recovery policy.  
* CONTRACT\_BUDGET\_PLANNED: prompt/schema/reasoning/final/safety budgets, worst-case serialized size and context fit result.  
* CONTRACT\_VALID / CONTRACT\_INVALID / TRUNCATED\_OUTPUT with exact non-sensitive reason.  
* CANONICAL\_SPEECH\_SEGMENT\_APPENDED / COMMITTED / DELIVERED with segment index and character range.  
* RECOVERY\_ATTEMPTED / RECOVERY\_SUCCEEDED / RECOVERY\_EXHAUSTED.  
* MATRIX\_INVARIANT\_FAILED for test/dev builds only.

## **15.2 Inspector panel**

* Current TurnExecutionPlan and route/contract.  
* Prompt/schema/output budget utilization and context sections.  
* Provider finish reason and reasoning/final token counts.  
* Canonical speech coverage: generated, validated, queued, audible and delivered characters.  
* Recovery state, resource state and terminal cleanup counts.  
* Operator control to run the deterministic conversation-matrix smoke suite outside an active player turn.

| TRACE DISCIPLINE. Do not log hidden reasoning text or raw audio. Keep full snapshots at meaningful boundaries and lightweight deltas elsewhere. Diagnostic failure must never fail a gameplay turn. |
| :---- |

# **16\. One-Phase Implementation Plan**

This is one Codex engineering phase with internal milestones and two hard gates. Codex should continue through deployment in the same task only when both gates pass.

| Milestone | Implementation work | Exit condition |
| :---- | :---- | :---- |
| A. Audit and contract freeze | Map current R058+ routes, schemas, streaming/finalization paths and provider ownership. Convert all prior trace failures into fixtures. | Baseline compiles; ownership remains single; failure corpus is reproducible. |
| B. Turn plan and budgets | Add TurnPlanCompiler, route contracts, schema registry, context profiles and ContractBudgetPlanner. | Every dispatch has a valid plan; impossible budget/schema combinations are rejected pre-inference. |
| C. Output and speech hardening | Implement plain-text dialogue paths, compact structured paths, append-only CanonicalSpeechLedger and complete TTS delivery mapping. | All canonical ordering/divergence/multi-line regressions pass. |
| D. Recovery and transcript integrity | Add RecoverySupervisor, truncation/reasoning recovery, Moonshine final-integrity check and idempotent cleanup. | Every injected failure terminates cleanly and next-turn probe succeeds. |
| Gate 1: deterministic matrix | Run exhaustive critical \+ pairwise generated matrix and 100-turn deterministic soak. | Zero invariant violations, stale commits, leaked permits, malformed action execution or unspoken delivered text. |
| E. Provider/resource calibration | Calibrate real Nemotron budgets/schemas, Chatterbox chunk ceiling, Moonshine anomaly thresholds and resource deadlines. | Provider fixtures and real provider probes agree with contract behavior. |
| F. Connected Hytale matrix | Cold/warm startup, mixed-route voice conversation, interruptions, focus loss, resource pressure, multi-listener and long soak. | Performance/quality budgets pass; no route-specific silent failure. |
| Gate 2: deployment | Build, hash, install exactly one JAR, preserve profiles/data, produce report. | Definition of Done satisfied. |

# **17\. Verification, Performance Budgets and Definition of Done**

## **17.1 Automated acceptance**

* Every route contract validates with minimum, typical and maximum bounded content.  
* No schema can be dispatched with an output budget below its computed worst case.  
* No structured output is executed after length stop, EOF or schema failure.  
* One physical utterance creates one canonical turn/transcript.  
* One canonical reply maps to ordered, gap-free TTS spans and delivered text exactly once.  
* Reasoning-only, empty, duplicate, out-of-order and divergent-final streams recover or fail cleanly.  
* Barge-in/focus loss/disconnect/stale epochs cannot return old text/audio.  
* Every terminal path releases resource/capture/provider/playback state exactly once.  
* All current R001-current tests remain passing.

## **17.2 Connected Hytale acceptance**

* At least 30 mixed real PTT turns covering FAST, GROUNDED, deterministic action, discretionary choice and DELIBERATIVE routes.  
* At least one valid/invalid action, one memory query, one perception query, one clarification, one social invitation, one complex decision and one autonomous decision.  
* Interrupt during Nemotron streaming, Chatterbox synthesis and playback; walk out of range during deliberation; immediately speak again after each failure/cancel.  
* Cold startup and warm repeated turns; provider eviction/reload only when policy requires it.  
* Multi-sentence replies display and speak the same complete canonical response in order.  
* No unsupported world/memory/action claim reaches speech.

## **17.3 Performance and hardware budgets**

| Metric | Target | Blocking failure |
| :---- | :---- | :---- |
| Moonshine final transcript | Preserve current sub-300 ms warm class; anomaly retry explicit | Missing/duplicated accepted transcript or repeated first-use truncation |
| FAST/GROUNDED speech end \-\> first audible | \<=3 s p50; \<=5 s p95 when warm | Sustained regression without documented hardware pressure |
| Deterministic action speech end \-\> first audible | \<=5 s p50; \<=8 s p95 | Prompt/schema expansion or silent structured failure |
| DELIBERATIVE foreground | Bounded route-specific wall clock; \<=12 s initial target | Unbounded reasoning/no final answer or gameplay wedge |
| Chatterbox request size | Within calibrated safe character/duration ceiling | Long-input corruption, missing sentence or unspoken text |
| Hytale p95 frame-time impact | \<=10% sustained degradation from AI-active baseline | \>15% sustained or recurring inference-correlated \>50 ms hitches |
| GPU safety | No OOM/thrashing; preserve measured safety reserve | VRAM emergency accepted as normal operation |
| Soak stability | No monotonic queue/VRAM/permit growth | Any reproducible leak or route degradation |

## **17.4 Definition of Done**

* The current cascade is contract-driven from ingress through delivered audio; no supported route bypasses TurnPlanCompiler.  
* FAST/GROUNDED dialogue uses plain text streaming; structured control is compact, versioned, pre-budgeted and separately validated.  
* Unambiguous player actions are resolved by Orbis authority rather than broad LLM action schemas.  
* Deliberative reasoning and strict structured finalization are separated and bounded.  
* One turn produces one canonical reply; every displayed sentence is spoken in the same order or explicitly marked not delivered.  
* Moonshine final transcripts are protected against partial-collapse behavior with one bounded preserved-audio recovery.  
* All historical failure traces are permanent regressions and the generated matrix passes.  
* A failure in any stage leaves the next turn immediately usable.  
* Voice acting, latency and consumer-hardware budgets pass in connected Hytale testing.  
* Exactly one deployed JAR exists; build/deployed hashes match; persistent profiles/data are preserved.

# **Appendix A. Matrix Axes and Critical Cases**

| Critical case | Required outcome |
| :---- | :---- |
| FAST plain reply with two sentences | One canonical response; both sentences voiced in order; no JSON. |
| GROUNDED memory/world answer | Only compatible evidence authorizes factual speech. |
| Unambiguous FOLLOW/GO\_TO request | Orbis validates action first; plain speech reflects committed/rejected result. |
| Social invitation requiring NPC choice | Compact choice schema fits budget; no giant action context. |
| Complex conflicting-goal decision | Bounded reasoning stage then strict compact finalization. |
| Output hits max tokens mid-JSON | TRUNCATED\_OUTPUT; no execution; one bounded correction or recovery. |
| Reasoning produces no final answer | Budget cancellation; one reasoning-off recovery; no 30-second wedge. |
| Streaming final diverges from early phrase | Impossible under append-only ledger; fixture must fail test before delivery. |
| Out-of-order/duplicate phrase callback | Discard/diagnose; no audio/text duplication. |
| Moonshine final shorter than stable partial | Preserved-audio re-transcription; one authoritative transcript. |
| TTS later chunk fails | Earlier audio remains PARTIAL; later text not recorded as delivered. |
| Barge-in/focus loss at every stage | Immediate cancellation and stale event rejection; next turn succeeds. |
| VRAM pressure / provider loading | Bounded admission/reclaim; no Hytale frame-pressure violation. |

# **Appendix B. Proposed Component Map**

| Component | Responsibility | Disposition |
| :---- | :---- | :---- |
| OrbisTurnCoordinator | Authoritative lifecycle, branch, floor and cancellation owner | Retain |
| TurnPlanCompiler | Compile cognition/context/output/deadline/recovery contract | Add |
| OutputContractRegistry | Versioned dialogue/choice/deliberative/autonomous contracts | Add |
| ContractBudgetPlanner | Prompt/schema/output/context preflight | Add |
| ContextProfileBuilder | Route-specific bounded context selection | Add or refactor current builder |
| NemotronProviderAdapter | Plain dialogue streaming and strict non-streaming structured calls | Refactor |
| CanonicalSpeechLedger | Append-only response and exact TTS/display/history mapping | Add/replace competing assemblers |
| DialogueClaimValidator | Deterministic evidence compatibility and safe-social validation | Retain/extend |
| AgentOperation/action framework | Final action validation/execution | Retain; move unambiguous action authority before LLM |
| RecoverySupervisor | One bounded route-aware recovery and terminal cleanup | Add |
| MoonshineProvider | Streaming STT plus final-integrity guard | Extend |
| ChatterboxWorker/adapter | Cached voice, bounded phrase synthesis, prosody tags | Retain/extend |
| OrbisResourceScheduler | Hytale-first admission, residency, reclaim and deadlines | Retain |
| ConversationMatrixHarness | Generated scenarios, synthetic providers, state model, golden traces | Add |
| Cognition Inspector / NpcTrace | Plan/contract/budget/delivery/recovery diagnostics | Extend compactly |

# **Appendix C. Sources**

Official documentation is authoritative for supported APIs. GitHub issues are risk evidence and regression inspiration, not normative guarantees. Sources reviewed 30 August 2026\.

**\[T1\] Mara trace: canonical phrase/streaming failures.** [file:Mara\_2026-08-30\_11-04-53.jsonl](file:Mara_2026-08-30_11-04-53.jsonl) Local project evidence: ordering exceptions, immutable-prefix divergence and routing latency.

**\[T2\] Mara trace: reasoning-only failure.** [file:Mara\_2026-08-30\_11-42-33.jsonl](file:Mara_2026-08-30_11-42-33.jsonl) Local project evidence: 19,864-character prompt, 512 reasoning-only events, no dialogue and \~29-second failure.

**\[T3\] Mara trace: structured action truncation.** [file:Mara\_2026-08-30\_12-00-25.jsonl](file:Mara_2026-08-30_12-00-25.jsonl) Local project evidence: 16,599-character / \~4,150-token prompt, 96-token output budget and EOF-invalid JSON.

**\[P1\] Orbis Technical Design.docx.** [file:Orbis Technical Design.docx](file:Orbis%20Technical%20Design.docx) Local authoritative ownership, lifecycle, resource, cancellation, trace, cognition and playback design.

**\[S1\] Ollama Structured Outputs.** [https://docs.ollama.com/capabilities/structured-outputs](https://docs.ollama.com/capabilities/structured-outputs) Official JSON/schema support, reusable validation and low-temperature guidance.

**\[S2\] Ollama issue \#11022: schema omitted from prompt\_eval\_count.** [https://github.com/ollama/ollama/issues/11022](https://github.com/ollama/ollama/issues/11022) Upstream evidence that client budget accounting must explicitly include schema cost.

**\[S3\] Ollama issue \#15465: truncated JSON/tool call at max tokens.** [https://github.com/ollama/ollama/issues/15465](https://github.com/ollama/ollama/issues/15465) Upstream evidence for explicit length detection and no execution of partial structured output.

**\[S4\] Ollama issue \#14440: structured output \+ streaming \+ thinking.** [https://github.com/ollama/ollama/issues/14440](https://github.com/ollama/ollama/issues/14440) Upstream risk evidence supporting separation of reasoning, strict structure and early speech.

**\[S5\] NVIDIA Nemotron 3 Nano 4B GGUF model card.** [https://huggingface.co/nvidia/NVIDIA-Nemotron-3-Nano-4B-GGUF](https://huggingface.co/nvidia/NVIDIA-Nemotron-3-Nano-4B-GGUF) Official reasoning/non-reasoning behavior, local runtimes and model purpose.

**\[S6\] Moonshine Voice C API: streaming STT.** [https://moonshine-voice.readthedocs.io/en/latest/api/c-api/](https://moonshine-voice.readthedocs.io/en/latest/api/c-api/) Official stream lifecycle and incremental transcription API.

**\[S7\] Moonshine Voice benchmarks.** [https://moonshine-voice.readthedocs.io/en/latest/using/benchmarks/](https://moonshine-voice.readthedocs.io/en/latest/using/benchmarks/) Official responsiveness target and streaming finalization measurement.

**\[S8\] Moonshine streaming partial-final collapse report.** [https://github.com/cjpais/Handy/issues/1835](https://github.com/cjpais/Handy/issues/1835) Community reproduction of correct partials followed by incomplete final transcript.

**\[S9\] Resemble AI Chatterbox repository.** [https://github.com/resemble-ai/chatterbox](https://github.com/resemble-ai/chatterbox) Official Turbo positioning, compute/VRAM intent and paralinguistic tags.

**\[S10\] Chatterbox Turbo issue \#424: long text instability.** [https://github.com/resemble-ai/chatterbox/issues/424](https://github.com/resemble-ai/chatterbox/issues/424) Community report motivating bounded phrase length.

**\[S11\] Chatterbox Turbo issue \#543: degradation near 25-30 seconds.** [https://github.com/resemble-ai/chatterbox/issues/543](https://github.com/resemble-ai/chatterbox/issues/543) Community report motivating short semantic TTS chunks.

**\[S12\] NIST Combinatorial Testing.** [https://www.nist.gov/programs-projects/combinatorial-testing](https://www.nist.gov/programs-projects/combinatorial-testing) Basis for pairwise/t-way matrix coverage with lower test-count cost.

**\[S13\] JUnit 5 User Guide.** [https://docs.junit.org/5.11.0/user-guide/index.html](https://docs.junit.org/5.11.0/user-guide/index.html) Parameterized and dynamic test support for the generated matrix.

| END OF SPECIFICATION. Codex should implement this as one systemic hardening revision. The success criterion is not that the latest trace passes; it is that all route, contract, failure, cancellation and resource combinations conform to shared invariants and the connected Hytale matrix remains stable. |
| :---- |

