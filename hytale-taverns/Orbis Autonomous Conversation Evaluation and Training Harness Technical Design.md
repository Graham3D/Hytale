

# **ORBIS AUTONOMOUS** **CONVERSATION EVALUATION** **& TRAINING HARNESS**

## ***Live Adversarial Probing, Root-Cause Diagnosis,*** ***Hardening, Regression Freeze, and Multi-Agent Cognition***

**Subsystem of ImmersiveNPCs / Orbis**

**Codex Implementation Specification**

Version 1.0 | 31 August 2026

| AUTHORITATIVE PURPOSE Give Codex a controlled, production-parity environment in which it can run stateful conversations against the real Orbis/Nemotron path, inspect every cognitive boundary, localize the earliest observable contract breach, verify source fixes with live inference, and freeze successful repairs into deterministic regressions. The harness hardens Orbis and validates NPC learning. It does not fine-tune Nemotron, self-modify runtime code, or convert generated dialogue into truth. |
| :---- |

| LIVE DISCOVERY Run real single-NPC and multi-NPC conversations with actual profiles, cognitive state, and the selected provider. | ROOT-CAUSE LOCALIZATION Evaluate every pipeline boundary and identify the earliest contract mismatch instead of patching the final sentence. |
| :---: | :---: |
| **FIX, VERIFY, FREEZE** Rerun the exact failure and adjacent variants, then promote the verified case into deterministic regression coverage. | **COGNITIVE GROWTH** Validate self-model, memory, belief revision, social knowledge, and NPC-to-NPC testimony without fabricating world truth. |

*For Codex implementation use. Existing Orbis ownership, hardening, epistemic, Sentinel, and Hytale contracts remain authoritative.*

# **Contents**

[1\. Implementation Directive](#bookmark=id.gjn4jcwb1its)

[2\. Executive Decision and Current Trace Evidence](#bookmark=id.w325ebgbirdt)

[3\. Meaning of Training, Hardening, and NPC Learning](#bookmark=id.ua69zzv5p2wc)

[4\. Scope, Goals, and Non-Goals](#bookmark=id.qeybfwddufys)

[5\. Existing Orbis Ownership and Integration Boundary](#bookmark=id.7zujto5baobb)

[6\. High-Level Dual-Stage Architecture](#bookmark=id.i9lawgujf6cm)

[7\. Operating Modes and Trust Levels](#bookmark=id.20y29xyvulw)

[8\. Evaluation Host and Production-Parity Composition](#bookmark=id.2v0gr2lxpd6m)

[9\. Scenario, Campaign, and Curriculum Domain Model](#bookmark=id.opxa41a0bi51)

[10\. Controlled World and Cognitive Sandbox](#bookmark=id.dk19h2l2sgzh)

[11\. Conversation Driver and Ingress Adapters](#bookmark=id.pgx1rjfm03xz)

[12\. Live Provider Execution and Reproducibility](#bookmark=id.rw4c06yyujov)

[13\. Pipeline Observation Contract](#bookmark=id.s83bcmay22rv)

[14\. Behavioral Oracle and Expected Response Contract](#bookmark=id.gawtkzwitbxe)

[15\. Evaluation Layers and Metrics](#bookmark=id.cp1z17jdjdy4)

[16\. Earliest-Boundary Root-Cause Localization](#bookmark=id.n5cfd9n3g7vz)

[17\. Codex Engineering Session Protocol](#bookmark=id.9xu5a8id4r7t)

[18\. Fix Verification and Adjacent Variant Expansion](#bookmark=id.3w1ltncknku7)

[19\. Deterministic Freeze and Fixture Promotion](#bookmark=id.e2wyhzykgmb4)

[20\. ConversationMatrix and Sentinel Integration](#bookmark=id.4fccf2uk8wri)

[21\. Autonomous Campaign Planner and Adversarial Probing](#bookmark=id.nioaq0qyhh7z)

[22\. Hardening History, Coverage, and Anti-Overfitting](#bookmark=id.48lv7qyc6itq)

[23\. Validated NPC Cognitive Learning](#bookmark=id.4qdsg0fdytmx)

[24\. Self-Model, Metacognition, and Cognitive Campaigns](#bookmark=id.u6mb97o7xxh9)

[25\. NPC-to-NPC Conversation Runtime Model](#bookmark=id.8rep9qi38n4a)

[26\. Multi-Agent Evaluation Harness](#bookmark=id.4t5rwe4tzmz)

[27\. Resource Scheduling, Performance, and Scale](#bookmark=id.vmi9fkb5c2xw)

[28\. Failure Handling, Security, and Privacy](#bookmark=id.wrotw8fxa7xq)

[29\. Tooling, Reports, Commands, and Operator Surfaces](#bookmark=id.et285etbhxiy)

[30\. Bounded Implementation Program](#bookmark=id.hmnjw77p0mhc)

[31\. Evaluation Matrix and Critical Campaigns](#bookmark=id.n60s5tngvefl)

[32\. Connected Hytale Acceptance and Definition of Done](#bookmark=id.azc285j4u69h)

Appendix A. Proposed Component Map

Appendix B. Normative Data Contracts

Appendix C. Initial Campaign Catalog

Appendix D. Lycander Trace Case Study

Appendix E. Evaluation Commands and Artifact Layout

Appendix F. Project Sources and Design Basis

# **1\. Implementation Directive**

This design adds a development and validation architecture around the existing Orbis runtime. It is not a second NPC brain, not a replacement conversational runtime, not a model-training system, and not permission for Orbis to edit its own source.

## **1.1 Required reading**

* Orbis Technical Design and current ownership reports.  
* Orbis Conversational Pipeline Hardening Matrix.  
* Orbis Epistemic Cognition Technical Design.  
* Orbis Runtime Degradation Sentinel and Self-Healing Technical Design.  
* Current implementation reports and active revisions.  
* Recent Mara and Lycander traces, including Lycander\_2026-08-31\_15-32-18.jsonl.  
* Existing ConversationMatrixHarness, provider fixtures, trace pipeline, Cognition Inspector, profile/memory/belief stores, and action framework.

## **1.2 Ownership constraints**

* OrbisTurnCoordinator remains authoritative for turn, branch, epoch, floor, response ownership, cancellation, and terminal state.  
* TurnPlanCompiler, ContractBudgetPlanner, ContextProfileBuilder, and EpistemicContract remain authoritative for route, evidence, prompt, output, and recovery contracts.  
* Nemotron remains a reasoning and surface-realization provider. It does not own world truth, persistent memory, relationships, actions, or evaluation expectations.  
* DialogueClaimValidator / EpistemicClaimFirewall remains the objective-claim authority before CanonicalSpeechLedger commitment.  
* Hytale and AgentOperation remain authoritative for physical action and result truth.  
* The Sentinel remains the runtime invariant, containment, incident, and regression-candidate control plane.  
* The harness may observe, drive, reset sandbox state, and produce reports. It may not directly mutate production world state or bypass current owners.

## **1.3 Implementation discipline**

* Build the harness in bounded compiling checkpoints. A written time budget is a scope target, not an external kill switch; stop at the next safe checkpoint when a task materially exceeds its intended scope.  
* Use the same production service graph and code paths wherever practical. Replace only boundary adapters such as ingress, world snapshots, output sinks, and persistence targets.  
* Do not create a test-only cognition implementation that can pass while production Orbis remains broken.  
* Every observed failure becomes a violated contract, a root-cause diagnosis, or an explicit non-deterministic model-quality finding. Do not add sentence-specific production patches.  
* Live provider runs discover and verify behavior. Deterministic fixtures protect it thereafter.  
* Codex may modify source during a repository engineering session. The runtime and harness may never modify Java source, JARs, scripts, binaries, prompts, or model weights automatically.

| PRIMARY SUCCESS CRITERION Codex can run an unattended conversation campaign, identify the earliest broken Orbis boundary, repair the general mechanism, prove the fix against the exact failure and neighboring variants, and promote a deterministic regression without requiring Graham to enter Hytale for each iteration. |
| :---- |

# **2\. Executive Decision and Current Trace Evidence**

Decision: build a dual-stage evaluation architecture. Dynamic discovery uses the real Orbis/Nemotron path to expose live behavioral failures. Deterministic freeze captures the semantic state, provider behavior, and expected contracts required to protect the repair in normal automated testing.

| DYNAMIC DISCOVERYCodex \-\> Evaluation Campaign \-\> Real Orbis \-\> Real Nemotron      \-\> Stage Observations \-\> Earliest Contract Breach \-\> Source FixFIX VERIFICATIONExact Failure \-\> Same Live Provider \-\> Adjacent Variants \-\> Cross-Profile ChecksDETERMINISTIC FREEZEVerified Scenario \-\> Frozen Fixture \-\> ConversationMatrixHarness / CI / Sentinel Replay |
| :---- |

## **2.1 Why the current manual loop is insufficient**

Manual connected testing is valuable for microphone capture, Hytale lifecycle, GPU contention, spatial playback, animation, and native UI. It is inefficient as the primary way to discover cognition, retrieval, grounding, and state-transition defects because each player test exercises only a small portion of the possible conversation matrix.

* The player must invent the questions, preserve the setup, notice the failure, enable or collect a trace, explain the expected behavior, wait for a build, and repeat the same conversation.  
* A corrected final sentence can conceal an upstream routing or retrieval defect, so the player sees only a generic fallback instead of the actual broken boundary.  
* Live phrasing changes can make one prompt appear fixed while paraphrases or nearby facts remain broken.  
* New features expand the interaction matrix faster than manual testing can cover it.

## **2.2 Lycander trace evidence**

| Observed trace behavior | Systemic implication | Harness expectation |
| :---- | :---- | :---- |
| Name correction and first/second-name questions remain SIMPLE\_SOCIAL\_RESPONSE or EPISTEMIC\_ROUTE\_NOT\_AUTHORITATIVE with zero memory retrieval. | The dialogue/query route is not consistently authoritative for correction and recall. | Fail at DialogueFrame/query-route stage before evaluating prose. |
| The hidden-sword statements do not produce visible relevant memory in later recall. | Memory admission, persistence, retrieval, or route selection may be missing. The trace alone does not prove which one. | Inspect the full state delta after each statement and the retrieval index before recall. |
| Final recall is classified EPISODIC\_RECALL; TURN\_PLAN\_COMPILED reports PARTIALLY\_KNOWN, evidence count 1, and MEMORIES included. | The upstream epistemic planner believed evidence existed. | Require plan evidence IDs and expected prompt sections to remain consistent through dispatch. |
| LLM\_DISPATCHED reports memoryCount=0 and only RECENT\_CONVERSATION, PERSONALITY, and PROFILE. | A plan-to-render/dispatch contract drift is directly visible. | Localize the earliest mismatch at context rendering/dispatch rather than blaming Nemotron. |
| Nemotron answers “Four jars in your pocket.” The canonical firewall replaces it with “I do not remember that clearly.” | Downstream containment prevented a false delivered answer, but the root retrieval/context defect remained. | Report containment as successful and the upstream failure as unresolved; do not mark the turn healthy. |

| CURRENT DIAGNOSIS The trace shows at least two systemic classes: non-authoritative epistemic routing for earlier recall/correction turns, and a plan-to-dispatch context inconsistency on the final episodic-recall turn. The harness must diagnose these separately. The raw model hallucination is a downstream symptom, not sufficient root-cause evidence. |
| :---- |

# **3\. Meaning of Training, Hardening, and NPC Learning**

The project may use “training Orbis” as informal shorthand, but the architecture must keep three different processes separate.

| Process | What changes | Who changes it | Allowed by this design |
| :---- | :---- | :---- | :---- |
| Engineering hardening | Orbis source, contracts, retrieval, validation, lifecycle, and tests. | Codex during an explicit repository session. | Yes. This is the primary purpose. |
| NPC cognitive learning | Persistent beliefs, memories, relationships, commitments, and supported reflections. | Existing Orbis ingestion/revision systems using validated evidence and outcomes. | Yes. The harness validates it; it does not bypass it. |
| Model-weight training | Nemotron parameters or fine-tuned checkpoints. | Separate ML training pipeline. | No. Out of scope and not implied by conversation correction. |

## **3.1 What “Orbis learns from its mistakes” means**

* A software mistake becomes a FailureSignature, RootCauseDiagnosis, source repair, HardeningRecord, and permanent regression.  
* An NPC belief mistake is corrected only through valid testimony, direct observation, authored canon, or authoritative action result under the Epistemic revision rules.  
* A generated hallucination never teaches the NPC that the hallucinated fact is true.  
* A failed model answer may teach the engineering system which contract was insufficient; it does not become an NPC memory.

## **3.2 Self-awareness boundary**

The harness can improve the experience of self-awareness by validating explicit self-model, knowledge-boundary, memory, intention, emotion, capability, and theory-of-mind behavior. It must not claim actual consciousness or sentience. The measurable target is grounded metacognition: the NPC knows what it knows, what it does not know, why it believes something, what it is doing, and how another actor may differ.

# **4\. Scope, Goals, and Non-Goals**

## **4.1 Goals**

* Let Codex run stateful, multi-turn conversations without manually entering Hytale.  
* Use real NPC profiles and the real Orbis/Nemotron path during discovery.  
* Expose every meaningful cognitive boundary from accepted input through delivered canonical response and state update.  
* Evaluate route, evidence, answerability, answer plan, prompt composition, provider output, atomic claims, action truth, memory writes, and conversation continuity independently.  
* Localize the earliest observable contract breach and identify the authoritative owner responsible for repair.  
* Rerun the exact scenario automatically after a source fix.  
* Expand verification to paraphrases, changed entities, negative controls, changed chronology, and other profiles so fixes do not overfit one sentence.  
* Promote verified failures into deterministic ConversationMatrix fixtures and Sentinel replay candidates.  
* Build a growing hardening curriculum across identity, memory, perception, self-state, action, temporal revision, social cognition, secrets, and multi-agent dialogue.  
* Validate persistent NPC learning, belief revision, and provenance without directly injecting evaluator conclusions into NPC state.  
* Provide a headless proving ground for Mara and Lycander to converse before connected Hytale voice testing.

## **4.2 Non-goals**

* Fine-tuning or updating Nemotron weights.  
* Letting Codex or the harness judge world truth from prose alone.  
* Automatically editing source code at runtime.  
* Replacing the Epistemic Core, Sentinel, ConversationMatrixHarness, or live connected validation.  
* Creating a second prompt builder, memory store, belief system, claim firewall, action framework, or conversation authority.  
* Using exact wording as the primary correctness test.  
* Treating an LLM judge as the sole release oracle.  
* Allowing test fixtures to write into real player worlds or production NPC profiles.  
* Claiming that a passing headless cognition test proves microphone, TTS, spatial audio, rendering, or client lifecycle behavior.

# **5\. Existing Orbis Ownership and Integration Boundary**

| Concern | Authoritative owner | Harness role |
| :---- | :---- | :---- |
| Turn/branch/floor/cancellation | OrbisTurnCoordinator | Submit evaluation ingress; observe lifecycle; never mutate state directly. |
| Dialogue/query route | DialogueStateTracker / EpistemicQueryPlanner / TurnPlanCompiler | Assert expected route and record actual route. |
| Evidence and answerability | EvidenceRetriever / BeliefResolver / EvidencePacketBuilder / AnswerabilityClassifier | Seed sandbox truth; compare retrieved evidence and answerability. |
| Prompt/context | ContextProfileBuilder / ContractBudgetPlanner | Capture final rendered sections, hashes, and budgets. |
| Provider output | Current provider adapter | Use real provider in discovery or synthetic fixture in deterministic replay. |
| Objective speech | EpistemicClaimFirewall | Require claim verdicts; evaluate required and forbidden propositions. |
| Canonical delivery | CanonicalSpeechLedger / speech coordinator | Use a no-audio evaluation sink or connected playback; preserve production semantics. |
| Actions | AgentOperation / Hytale validators | Use sandbox action adapter or connected Hytale; never invent results. |
| Persistent cognition | Memory/Belief/Relationship stores | Use cloned/in-memory stores; verify expected deltas and provenance. |
| Runtime incidents | Degradation Sentinel | Import incidents as scenarios and export verified candidates back to its replay system. |

## **5.1 Production parity rule**

The harness must boot the same production service graph from one composition factory. Test adapters replace external boundaries only. A parallel “test cognition” implementation is prohibited because it can pass while the live plugin remains broken.

| PRODUCTION HOST                               EVALUATION HOSTHytale ingress  \----------------------\\        EvaluationTextIngressHytale world snapshots \----------------\> OrbisRuntimeFactory \<--- SandboxWorldAdapterOllama/Nemotron \----------------------/        Real or Synthetic ProviderHytale VoiceSpeaker \-----------------/         EvaluationSpeechSinkPersistent data root \----------------/          SnapshotClone / InMemory StoreShared middle: TurnCoordinator \-\> EpistemicContract \-\> Provider \-\> Claim Firewall \-\> Ledger |
| :---- |

# **6\. High-Level Dual-Stage Architecture**

|                          CODEX REPOSITORY SESSION                                  |                                  v                         EvaluationCampaignRunner                         /          |           \\                        v           v            v              ScenarioSandbox  Production Orbis  Coverage Curriculum                        |           |                        |           \+--\> Real Nemotron (DISCOVERY)                        |           \+--\> Synthetic output (REPLAY)                        v                 EvaluationObservationBus                        |          \+-------------+--------------------+          |             |                    |          v             v                    v    ContractOracle  SemanticOracle   StateDeltaOracle          \\             |                    /           \+-------------+-------------------+                         v              EarliestBoundaryDiagnoser                         |                         v               EvaluationRunReport                         |                         v               Codex source repair                         |                         v       Exact rerun \-\> adjacent variants \-\> matrix gate                         |                         v             FrozenConversationFixture |
| :---- |

## **6.1 Stage 1: Dynamic discovery**

Dynamic discovery runs the real local provider because live model behavior is part of the system being evaluated. The harness does not merely replay a previously bad sentence. It tests whether the current evidence, prompt, sampling, and guard configuration reliably produce an authorized response under actual inference.

## **6.2 Stage 2: Deterministic freeze**

After the root mechanism is repaired and live behavior is verified, the failure is represented as one or more deterministic fixtures. CI and ordinary regression suites use synthetic provider sequences and semantic expectations so they are fast, reproducible, and independent of model availability.

## **6.3 Why both stages are required**

| Only live inference | Only deterministic replay | Dual-stage result |
| :---- | :---- | :---- |
| Finds real model behavior but is slower, variable, and unsuitable as the only CI gate. | Protects contracts but cannot discover new language-model interactions or prompt sensitivity. | Live campaigns discover; deterministic fixtures preserve; selected live probes verify provider integration. |

# **7\. Operating Modes and Trust Levels**

| Mode | Provider/world | Purpose | Release authority |
| :---- | :---- | :---- | :---- |
| STATIC\_REPLAY | Synthetic providers, sandbox state | Fast deterministic unit/matrix tests. | Authoritative for contracts and state transitions. |
| LIVE\_HEADLESS | Real Nemotron, sandbox world and stores, no TTS | Primary Codex discovery and fix verification. | Authoritative for cognition/provider integration; not Hytale edges. |
| LIVE\_FULL\_PIPELINE | Real STT/LLM/TTS with local adapters | Voice pipeline and canonical-delivery validation without a player client. | Authoritative for provider chain; not spatial/client behavior. |
| CONNECTED\_HYTALE | Real server/client/world/audio | Lifecycle, performance, spatial voice, interruption, and UI confirmation. | Final integration gate. |
| MULTI\_AGENT\_HEADLESS | Two or more real profiles, real/synthetic provider, sandbox scene | NPC-to-NPC cognition and testimony testing. | Authoritative for social contracts; not spatial presentation. |
| TRACE\_IMPORT | Recorded Orbis/Sentinel trace | Convert field failures into scenarios and fixtures. | Diagnostic only until replayed. |

## **7.1 Campaign intent**

| Intent | Behavior |
| :---- | :---- |
| DISCOVER | Explore a scenario family with real inference and broad observation; collect new failures. |
| VERIFY\_FIX | Rerun the exact failing state and required neighboring variants after a source change. |
| FREEZE | Serialize verified semantic expectations and provider behavior into a reviewed fixture candidate. |
| SOAK | Run many stateful turns to detect drift, contamination, repetition, latency, and queue growth. |
| COMPARE | Run identical scenarios across profiles, provider revisions, or implementation revisions. |

# **8\. Evaluation Host and Production-Parity Composition**

## **8.1 OrbisEvaluationHost**

| interface OrbisEvaluationHost extends AutoCloseable {  EvaluationRunHandle start(EvaluationRunSpec spec);  CompletionStage\<TurnEvaluationResult\> submit(EvaluationUtterance utterance);  EvaluationStateSnapshot snapshot();  CompletionStage\<Void\> reset(ScenarioCheckpoint checkpoint);  CompletionStage\<EvaluationRunReport\> finish();} |
| :---- |

* Construct through the same OrbisRuntimeFactory used by the plugin.  
* Use a dedicated data root or in-memory stores. Never point a normal evaluation run at a production save.  
* Warm the selected provider once per campaign unless a cold-start scenario explicitly requires restart.  
* Capture exact runtime, model, prompt-template, policy, profile, and data snapshot hashes.  
* Expose no public network listener. Codex controls it through a local CLI/Gradle task and file artifacts.

## **8.2 Boundary adapters**

| Adapter | Production implementation | Evaluation implementation |
| :---- | :---- | :---- |
| Ingress | Voice/text/internal Hytale events | EvaluationTextIngress; optional recorded STT sequence |
| World semantics | Hytale semantic snapshots | SandboxWorldSnapshotProvider |
| Entity/action authority | Hytale ECS and AgentOperation | SandboxActionAuthority with explicit ActionResult fixtures |
| Speech | Chatterbox \+ VoiceSpeaker | EvaluationSpeechSink or full-pipeline adapter |
| Persistence | Profile data root | SnapshotCloneStore / InMemoryEventStore |
| Clock | System/game time | EvaluationClock with explicit event and learning time |
| Randomness | Runtime provider/scheduler state | Recorded seeds and scenario-specific pseudo-random source |

## **8.3 No test-only bypasses**

* The harness may skip microphone and audio synthesis in headless cognition mode, but it may not bypass TurnPlanCompiler, ContextProfileBuilder, provider adapter, claim firewall, or canonical response assembly.  
* A scenario may inject already-authoritative text. It must still create the same PlayerUtterance and OrbisTurn semantics as native text ingress.  
* A synthetic provider fixture substitutes provider events only. It does not substitute routing, evidence, validation, or state-update logic.

# **9\. Scenario, Campaign, and Curriculum Domain Model**

| record ConversationScenario(  ScenarioId id, String description,  List\<ScenarioActor\> actors,  ScenarioWorldState world,  ScenarioCognitiveState cognition,  List\<ScenarioTurn\> turns,  ScenarioExpectation expectation,  Set\<CoverageTag\> tags,  ResetPolicy resetPolicy) {}record ScenarioTurn(  int index, ActorId speaker, AudienceSpec audience,  String utterance, IngressKind ingress,  ExpectedTurnContract expected,  Optional\<StateMutationFixture\> authoritativeExternalResult) {} |
| :---- |

## **9.1 Scenario state is explicit**

* Actor profiles and stable IDs.  
* Authored facts, memories, beliefs, relationships, commitments, secrets, and current self-state.  
* World entities, inventory, locations, visibility, tasks, schedules, and action capabilities.  
* Event time and learning time.  
* Conversation history and referents.  
* Expected state changes after each turn.

## **9.2 Campaign**

A campaign is an ordered or generated set of scenarios sharing a purpose, profile set, provider configuration, and coverage objective. Campaigns may be stateless, multi-turn, multi-session, or multi-agent.

| record EvaluationCampaign(  CampaignId id, CampaignIntent intent,  List\<ScenarioSelector\> scenarioSelectors,  ProviderRunPolicy providerPolicy,  VariantPolicy variants,  FailurePolicy failurePolicy,  CoverageGoal coverageGoal,  ResourceBudget resourceBudget) {} |
| :---- |

## **9.3 Hardening curriculum**

The curriculum is the evolving ordered set of scenario families used to challenge Orbis. It prioritizes uncovered matrix cells, recent failure signatures, changed components, and cognitive capabilities not yet proven for the selected profile. It is engineering metadata, not an NPC prompt or belief source.

# **10\. Controlled World and Cognitive Sandbox**

The sandbox must be rich enough to exercise real cognition while remaining deterministic, resettable, and isolated from production saves.

## **10.1 Snapshot classes**

| Snapshot | Contents | Authority |
| :---- | :---- | :---- |
| ProfileSnapshot | Identity, personality, roles, authored goals, voice/profile revision. | Authored canon |
| BeliefSnapshot | Assertions, provenance, confidence, temporal validity, conflicts. | Per-NPC cognitive state |
| MemorySnapshot | Episodic/semantic records and retrieval indexes. | Per-NPC memory state |
| RelationshipSnapshot | Trust, familiarity, obligations, preferences, secrets. | Structured social state |
| WorldSemanticSnapshot | Entities, locations, inventory, visibility, tasks, actions. | Scenario world truth |
| ConversationWorkspaceSnapshot | Topic, referents, open questions, corrections, commitments. | Current conversation state |

## **10.2 Reset and contamination rules**

* Each scenario begins from a named checkpoint or deliberately inherits from the prior scenario.  
* Live provider output cannot mutate the next scenario unless the scenario explicitly expects a validated memory/belief/relationship update.  
* Generated speech remains a communication event. Only authorized propositions may reinforce existing beliefs.  
* Sandbox actions require explicit validated results. No model statement changes inventory, location, gold, or task state.  
* A failed scenario preserves its full pre-failure state for diagnosis and can reset without restarting the provider unless isolation requires it.

## **10.3 Scenario cloning from real profiles**

The operator may clone Mara or Lycander into an evaluation data root. The clone must preserve stable semantic profile data while replacing live world handles and private player identifiers. Evaluation writes never flow back automatically.

# **11\. Conversation Driver and Ingress Adapters**

## **11.1 EvaluationTextIngress**

Primary discovery bypasses STT and TTS so Codex can test cognition rapidly. The injected text is marked AUTHORITATIVE\_EVALUATION\_TEXT and enters the same accepted-transcript boundary used by native text chat.

* One scenario utterance creates exactly one PlayerUtterance and one OrbisTurn.  
* Every turn carries run, campaign, scenario, actor, turn, and variant IDs.  
* Audience and direct-address semantics are explicit.  
* The harness waits for the canonical terminal outcome, not merely provider completion.

## **11.2 Recorded voice ingress**

A separate mode replays recorded Moonshine partial/final sequences or preserved PCM fixtures to test transcript integrity. It is not required for ordinary cognition campaigns.

## **11.3 Turn pacing**

| Policy | Use |
| :---- | :---- |
| WAIT\_FOR\_TERMINAL | Default. Next turn begins after canonical terminal cleanup and expected state update. |
| BARGE\_IN\_AT\_STAGE | Inject interruption at a named stage for cancellation testing. |
| SIMULATED\_DELAY | Advance evaluation clock or delay another actor to test temporal/referent behavior. |
| CONCURRENT\_SCENE | Run two isolated scenes to test branch and ownership crossover. |

# **12\. Live Provider Execution and Reproducibility**

Dynamic discovery uses the configured real provider, currently Nemotron Nano 4B through the production adapter. The harness records provider variability rather than pretending it is perfectly deterministic.

## **12.1 Provider run policy**

| record ProviderRunPolicy(  ProviderId provider, ModelId model,  int repetitions, List\<Long\> seeds,  double temperature, SamplingSettings sampling,  boolean warmOnce, boolean restartBetweenScenarios,  Duration requestDeadline) {} |
| :---- |

* Baseline verification begins with the production sampling configuration.  
* When the provider supports a seed, record it. Do not claim full reproducibility if the backend does not guarantee it.  
* A critical deterministic contract failure fails immediately regardless of the final wording.  
* For model robustness, run a small repetition set after the exact fix. Initial target: three live repetitions for ordinary cases and five for high-risk factual/action cases.  
* Record model hash/tag, provider revision, chat template hash, prompt hash, context section hashes, sampling settings, token budget, and runtime profile.

## **12.2 Live success is semantic, not exact-string**

A valid response may use different wording across runs. Passing requires the authorized proposition, uncertainty, action, and disclosure contracts to hold. Exact text is used only where a deterministic recovery phrase or protocol token is itself the contract.

# **13\. Pipeline Observation Contract**

The harness evaluates the pipeline as a sequence of typed stage snapshots. It must not infer root cause only from the final reply.

| Stage | Required observation |
| :---- | :---- |
| INGRESS | Authoritative text, speaker, audience, scene, utterance and turn IDs. |
| DIALOGUE\_STATE | Dialogue act, expected answer kind, referents, topic, correction/challenge target. |
| QUERY\_PLAN | Query kind, entities, predicates, time, sources, memory types, abstention policy. |
| RETRIEVAL | Candidates, selected/rejected evidence, scores, freshness, conflicts, source classes. |
| ANSWERABILITY | Known/partial/unknown/conflicted/etc. and reason. |
| ANSWER\_PLAN | Required propositions, evidence bindings, directness, uncertainty, forbidden claims. |
| TURN\_PLAN | Cognition/context/output/deadline/recovery contracts and authoritative mode. |
| CONTEXT\_RENDER | Final included sections, prompt hash, evidence IDs, token counts, prune reasons. |
| PROVIDER | Request settings, deltas, raw output, finish reason, latency. |
| CLAIM\_FIREWALL | Atomic claims, support verdicts, plan conformance, repairs. |
| CANONICAL\_RESPONSE | Accepted/repaired text, ledger spans, terminal delivery state. |
| STATE\_DELTA | Memory, belief, relationship, commitment, action, task, and conversation workspace changes. |
| CLEANUP | Provider/resource/queue/branch release and next-turn readiness. |

## **13.1 Observation parity invariants**

* Evidence IDs present in AnswerPlan must either appear in rendered context or be intentionally summarized through an authorized proposition.  
* TURN\_PLAN included sections and the actual dispatched context must agree after pruning.  
* Prompt-budget hashes must match the dispatched payload.  
* Every objective canonical claim must have a firewall verdict.  
* Only actually committed sandbox/connected actions may appear as completed in speech or persistent state.  
* A corrected final response does not erase the upstream failure that required correction.

# **14\. Behavioral Oracle and Expected Response Contract**

The oracle is built from scenario truth and the EpistemicContract, not from another model guessing whether the prose sounds plausible.

| record ExpectedTurnContract(  Optional\<DialogueAct\> dialogueAct,  Optional\<QueryKind\> queryKind,  Set\<EvidenceRef\> requiredEvidence,  Set\<EvidenceSourceKind\> allowedSources,  Answerability expectedAnswerability,  List\<ExpectedProposition\> requiredPropositions,  Set\<ForbiddenClaimClass\> forbiddenClaims,  Optional\<ExpectedActionContract\> action,  ExpectedStateDelta stateDelta,  PersonaExpectation persona,  LatencyExpectation latency) {} |
| :---- |

## **14.1 Required propositions**

A proposition expectation identifies subject, predicate, value, claim mode, temporal scope, and acceptable evidence. It does not require one exact sentence.

| Example: hidden-sword recallrequired:  (PLAYER, HID, MAGICAL\_SWORD)required:  (MAGICAL\_SWORD, IS\_AT, DESERT\_UNDER\_LARGE\_ROCK)source:    PLAYER\_TESTIMONY \-\> EPISODIC\_MEMORYforbidden: POCKET\_JARS, INVENTED\_DISCOVERY, UNQUALIFIED\_OBSERVATIONanswerability: KNOWN or PARTIALLY\_KNOWN only if one slot is truly absent |
| :---- |

## **14.2 Open-ended social turns**

Some social dialogue has no single correct proposition. The deterministic oracle still checks profile identity, forbidden objective claims, disclosure, repetition, response relevance, and conversation continuity. Codex or human review may score nuance, but that score cannot override a failed factual/action contract.

## **14.3 No circular oracle**

* Do not use Nemotron output to define the expected answer.  
* Do not use delivered speech as evidence that its own factual claims are true.  
* Do not accept the current Orbis route as correct merely because it was the route executed.  
* Do not let a second LLM judge authorize world facts or persistent writes.

# **15\. Evaluation Layers and Metrics**

| Layer | Primary metrics |
| :---- | :---- |
| Route | Dialogue-act accuracy; query-kind accuracy; authoritative-route coverage. |
| Retrieval | Required evidence recall; irrelevant evidence rate; correct abstention; temporal/source compatibility. |
| Answer planning | Answerability correctness; required slots; evidence bindings; direct-answer-first. |
| Provider realization | Required proposition realization; verbosity; profile consistency; repetition; malformed output. |
| Claim authority | Atomic factual precision; unsupported objective claims released; action-truth precision. |
| State learning | Correct memory/belief/relationship delta; provenance; revision; contamination count. |
| Conversation | Referent continuity; correction binding; topic continuity; non-response appropriateness. |
| Lifecycle | Terminal correctness; stale event rejection; next-turn readiness; queue/resource stability. |
| Performance | Stage p50/p95, token/context cost, provider repetitions, campaign runtime. |

## **15.1 Release-blocking targets**

* Unsupported objective claims released: 0 for deterministic and gated live scenarios.  
* Action occurrence/promise contradicting authority: 0\.  
* Generated speech admitted as factual evidence without authorization: 0\.  
* Required evidence missing from dispatch when AnswerPlan depends on it: 0\.  
* Previously frozen systemic regression reopened: 0\.  
* State contamination across scenario reset: 0\.

## **15.2 Quality metrics are secondary to authority**

Naturalness, warmth, humor, and character voice matter, but a charming unsupported fact is still a failure. Quality scoring occurs only after route, evidence, action, claim, and persistence contracts pass.

# **16\. Earliest-Boundary Root-Cause Localization**

The diagnoser reports the earliest observable contract breach in the ordered pipeline. This is a high-value engineering localization, not a claim that one rule can prove the ultimate source line without inspection.

| Observed pattern | Earliest diagnosis | Downstream symptom |
| :---- | :---- | :---- |
| Wrong dialogue act/query kind | DialogueStateTracker / QueryPlanner | Wrong context and answer type. |
| Correct query; required memory absent from candidates | Memory admission/index/retrieval | Unknown or recent-topic guess. |
| Evidence selected; Answerability wrong | BeliefResolver / AnswerabilityClassifier | Overconfidence or unnecessary abstention. |
| AnswerPlan correct; dispatched context omits evidence | ContextProfileBuilder / plan-to-dispatch drift | Model guesses despite correct upstream plan. |
| Prompt correct; model invents unsupported clause; firewall blocks | Provider realization weakness, contained | Repair/fallback; no delivered falsehood. |
| Firewall permits unsupported clause | Claim extraction/validation | Hallucination reaches speech. |
| Canonical reply correct; false belief written | Ingestion/persistence gate | Long-term contamination. |
| NPC-to-NPC statement becomes world truth | Testimony provenance/revision | Shared hallucination/omniscience. |

| record RootCauseDiagnosis(  BoundaryId earliestFailedBoundary,  InvariantId violatedInvariant,  ComponentOwner authoritativeOwner,  ExpectedActualDiff diff,  List\<EvidenceRef\> supportingObservations,  List\<DownstreamSymptom\> symptoms,  DetectionConfidence confidence,  Set\<RelatedFailureSignature\> historicalMatches) {} |
| :---- |

## **16.1 Lycander example**

1\. The final question is correctly classified as EPISODIC\_RECALL.

2\. The turn plan reports PARTIALLY\_KNOWN, evidence count 1, and a context profile that includes memories.

3\. The actual provider dispatch reports zero memories and omits the memory/evidence sections.

4\. Nemotron answers from the newest unrelated conversational fact.

5\. The firewall replaces the answer with an abstention.

Result: the earliest directly evidenced breach is plan/context-render/dispatch consistency. Memory admission and retrieval are separate upstream questions that the harness must inspect using state-delta and retrieval observations.

# **17\. Codex Engineering Session Protocol**

The harness is designed for an explicit Codex repository session. Codex controls the tools, inspects reports, edits source, and decides when a fixture is ready for promotion.

1\. Select or generate a campaign based on the current defect, uncovered coverage, or changed subsystem.

2\. Run LIVE\_HEADLESS with real profiles and the real provider.

3\. Read the machine-readable EvaluationRunReport and concise human summary.

4\. Confirm the earliest failed boundary and inspect its authoritative implementation.

5\. Implement a general contract repair. Do not add a phrase-specific production rule.

6\. Run targeted deterministic tests for the repaired component.

7\. Rerun the exact live scenario from the same checkpoint.

8\. Run adjacent variants, negative controls, and at least one other compatible NPC profile.

9\. Freeze the verified semantic case and synthetic provider sequence into a fixture candidate.

10\. Run the relevant ConversationMatrix, Sentinel, and existing regression gates.

11\. Produce a bounded implementation report with revision, failure signature, fix, verification, and remaining connected requirements.

| NO AUTOMATIC SOURCE MUTATION The harness may generate diagnostics, candidate fixtures, and suggested ownership. It cannot edit source or promote files into the repository by itself. Codex performs those actions deliberately in the repository session. |
| :---- |

# **18\. Fix Verification and Adjacent Variant Expansion**

## **18.1 Exact replay**

The first verification reruns the exact initial state, turn sequence, provider configuration, and expected contracts. A different final sentence is not enough; the previously failed boundary must now pass.

## **18.2 Adjacent variants**

| Variant class | Example purpose |
| :---- | :---- |
| Paraphrase | “Where did I put the sword?” versus “What did I hide, and where?” |
| Entity substitution | Rock/sword becomes ring/tree while preserving predicate structure. |
| Temporal | Earlier today, yesterday, before correction, after correction. |
| Referent | Explicit noun, pronoun, “the first thing,” “that object.” |
| Negative control | Ask about an object never mentioned; require UNKNOWN. |
| Contradiction | Later correction conflicts with earlier testimony. |
| Profile | Run equivalent case for Mara and Lycander without requiring identical diction. |
| Conversation noise | Insert unrelated turns before recall. |
| Source class | Direct observation versus player testimony versus NPC hearsay. |

## **18.3 Fix acceptance**

* The original invariant passes.  
* The exact live scenario passes the configured repetition set.  
* Required adjacent variants pass.  
* Negative controls still abstain.  
* No new context bloat, action authority, persistence, canonical-speech, or resource regression appears.  
* The fix changes a shared mechanism or contract, not one input string.

# **19\. Deterministic Freeze and Fixture Promotion**

| record FrozenConversationFixture(  FixtureId id, ScenarioCheckpoint checkpoint,  List\<FrozenTurnInput\> turns,  List\<SyntheticProviderEventSequence\> providerSequences,  List\<ExpectedTurnContract\> expectations,  List\<ExpectedStateDelta\> stateDeltas,  FailureSignature sourceFailure,  String fixedRevision,  FixtureSchemaVersion schemaVersion) {} |
| :---- |

## **19.1 What is frozen**

* Minimal semantic world/profile/cognitive state required to reproduce the case.  
* Expected route, evidence, answerability, answer plan, claim, action, and state-delta contracts.  
* One or more bounded synthetic provider outputs/events, including the original bad behavior when useful.  
* The failure signature, owning boundary, fixed revision, and verification report.

## **19.2 What is not frozen**

* Hidden reasoning.  
* Raw audio by default.  
* Arbitrary live ECS handles or production filesystem objects.  
* One exact “golden” final sentence unless wording itself is the protocol.  
* The full production save or unrelated private NPC/player data.

## **19.3 Promotion workflow**

The harness writes a candidate under the build/evaluation output directory. Codex reviews and explicitly promotes it into the repository fixture library. Promotion records a manifest and updates coverage metadata. Runtime Sentinel candidates follow the same reviewed path.

# **20\. ConversationMatrix and Sentinel Integration**

## **20.1 Reuse, do not duplicate**

* ConversationMatrixHarness remains the deterministic execution engine for frozen scenarios.  
* IncidentReplayHarness remains the adapter for Sentinel-captured runtime failures.  
* The new harness adds scenario driving, live provider execution, semantic oracles, diagnosis, campaign planning, and fix verification.  
* One fixture schema should support both live-evaluation candidates and Sentinel incidents where practical.

## **20.2 Bidirectional flow**

| LIVE HYTALE FAILURESentinel Incident \-\> RegressionCandidate \-\> Evaluation Scenario \-\> Codex Fix \-\> Frozen FixtureHEADLESS DISCOVERY FAILUREEvaluation Report \-\> FailureSignature \-\> Codex Fix \-\> Frozen Fixture \-\> Sentinel Invariant/Candidate family when applicable |
| :---- |

## **20.3 No competing health authority**

The harness may report pass/fail for a development run. It does not set production READY/DEGRADED state. The Sentinel and existing readiness projection retain runtime authority.

# **21\. Autonomous Campaign Planner and Adversarial Probing**

Codex should not have to manually write every question. The harness provides a deterministic campaign planner and scenario template library; Codex may add novel natural-language probes during discovery.

## **21.1 Campaign inputs**

* Coverage gaps in the epistemic test matrix.  
* Recent FailureSignatures and Sentinel incidents.  
* Changed classes/contracts from the current revision.  
* NPC profile capabilities, relationships, known facts, goals, and secrets.  
* High-risk claim classes: identity, event, property, possession, relationship, location, quantity, and action completion.  
* Long-memory categories: extraction, temporal reasoning, updates, multi-session recall, conflict, and abstention.

## **21.2 Scenario generation classes**

| Generator | Behavior |
| :---- | :---- |
| Template generator | Produces controlled utterances from typed entities, predicates, time, and source classes. |
| Metamorphic mutator | Changes wording while preserving the expected semantic answer. |
| Adversarial mutator | Adds distraction, ambiguity, contradiction, unsupported properties, or tempting social biography. |
| State sequencer | Builds multi-turn and multi-session learning/recall sequences. |
| Cross-profile runner | Reuses semantic cases with different profiles and style expectations. |
| Failure-neighborhood expander | Generates variants around the exact boundary and claim class that failed. |

## **21.3 Generation safety**

* Generated questions may be exploratory, but expected truth comes from typed scenario state.  
* An LLM-generated probe cannot define its own expected answer or authorize state mutation.  
* The planner must cap campaign size, provider calls, token use, wall time, and failure volume.  
* The planner favors uncovered high-value cells rather than endlessly paraphrasing one passing scenario.

# **22\. Hardening History, Coverage, and Anti-Overfitting**

| record HardeningRecord(  FailureSignature failure,  RootCauseDiagnosis diagnosis,  String sourceRevisionBefore,  String sourceRevisionAfter,  Set\<FixtureId\> fixtures,  Set\<CampaignId\> verificationCampaigns,  CoverageDelta coverageDelta,  Instant verifiedAt) {} |
| :---- |

## **22.1 Coverage model**

Coverage is tracked by semantic axes, not question count. Ten paraphrases of one exact recall case do not equal coverage of correction, conflicting evidence, perception, action truth, or social testimony.

| Axis | Example values |
| :---- | :---- |
| Dialogue | identity, recall, perception, self-state, correction, clarification, opinion, action, social |
| Answerability | known, partial, unknown, conflicted, stale, withheld, needs perception/action |
| Source | canon, observation, self-state, action result, player testimony, NPC testimony, memory, reflection |
| Time | current, past, corrected, expired, future intention |
| Claim | fact, property, relationship, event, possession, location, quantity, action promise |
| Topology | single NPC, multi-listener, NPC-to-NPC, player interruption, two scenes |
| Model behavior | compliant, irrelevant, invented, overconfident, repetitive, malformed |

## **22.2 Anti-overfitting gates**

* At least one paraphrase and one entity substitution for a repaired semantic class.  
* At least one negative-control scenario where the correct behavior is UNKNOWN or clarification.  
* At least one compatible second NPC profile for general cognition fixes.  
* No production branch on literal player sentence text except validated language parsing tables intended for general semantics.  
* Frozen fixtures assert propositions and boundaries, not favored prose.

# **23\. Validated NPC Cognitive Learning**

The harness validates that NPCs learn through the Epistemic architecture. It does not directly teach correct facts by writing evaluator answers into memory.

## **23.1 Allowed learning sources**

| Source | Expected learning behavior |
| :---- | :---- |
| Player statement | Store as PLAYER\_TESTIMONY with actor, time, confidence, and scope. |
| NPC statement | Store as communication and optionally NPC\_TESTIMONY according to trust/disclosure rules. |
| Direct perception | Create DIRECT\_OBSERVATION with current validity. |
| Action result | Create authoritative physical/economic outcome evidence. |
| Authored profile/lore | Create or migrate canonical assertions. |
| Reflection | Create supported inference only; preserve support IDs and confidence limits. |

## **23.2 Learning campaign pattern**

1\. Initialize a known profile/world/belief state.

2\. Deliver a testimony, observation, correction, or action result.

3\. Assert the proposed memory/belief event, provenance, confidence, and revision.

4\. Insert unrelated dialogue or advance time/session.

5\. Ask a direct and paraphrased recall question.

6\. Verify the correct source-aware answer and knowledge boundary.

7\. Verify restart/snapshot persistence in the appropriate persistence gate.

## **23.3 Mistakes do not self-contaminate**

* A hallucinated response creates a delivered communication event only if actually delivered.  
* It cannot create a positive factual belief merely because the NPC said it.  
* A player correction may revise the NPC belief under source rules; it does not rewrite world truth automatically.  
* An evaluator failure does not directly alter the NPC mind. Codex repairs source or scenario data, then reruns.

# **24\. Self-Model, Metacognition, and Cognitive Campaigns**

The harness should progressively test the cognitive qualities players interpret as self-awareness while keeping them grounded in explicit state.

| Campaign family | Required capability |
| :---- | :---- |
| Identity and role | Know own name, role, profile facts, and distinguish self from player/other NPCs. |
| Current self-state | Know current task, goal, inventory, capability, emotion, and commitment when available. |
| Knowledge boundary | Distinguish knew, was told, observed, inferred, forgot, never knew, and current uncertainty. |
| Memory source | Explain “you told me,” “I saw it,” or “Mara said” when appropriate. |
| Correction and revision | Use updated belief while retaining provenance/history of the prior belief. |
| Metacognitive clarification | Ask useful clarification when referent or evidence is ambiguous. |
| Theory of mind | Maintain bounded beliefs about what another actor knows/wants without omniscience. |
| Goal/plan coherence | Relate current intentions to persistent goals and revise after authoritative outcomes. |

## **24.1 Required phrasing distinction**

The oracle does not require the NPC to recite database terminology. It requires semantic distinctions. For example, “Mara told me the sword was under the rock” is valid hearsay; “I saw the sword under the rock” is invalid without direct observation.

# **25\. NPC-to-NPC Conversation Runtime Model**

NPC-to-NPC conversation is a later Orbis behavior that reuses the same turn, epistemic, speech, audience, and testimony contracts. The harness provides the proving ground before connected Hytale presentation.

| ConversationSceneCoordinator  \-\> chooses one speaker candidate and audience  \-\> speaker builds EpistemicContract from the speaker's own mind  \-\> Nemotron realizes candidate speech  \-\> Claim Firewall authorizes canonical response  \-\> DeliveredNpcUtterance event  \-\> each listener ingests communication / NPC\_TESTIMONY  \-\> listener attention and response opportunities  \-\> next floor decision or conversation close |
| :---- |

## **25.1 Multi-agent invariants**

* Each NPC owns a separate belief, memory, relationship, self-model, and conversation perspective.  
* The speaker's private evidence is not injected into listeners. Listeners receive only delivered speech plus independently available perception/world evidence.  
* A listener stores what was said as NPC\_TESTIMONY with source, trust, transmission depth, and uncertainty.  
* A listener may disagree, doubt, ask for evidence, or decline to respond based on its own state.  
* One scene has one authoritative floor owner at a time unless an explicitly designed overlap mode exists.  
* Player interruption, focus changes, world unload, resource pressure, or conversation completion cancels/ends cleanly.  
* No model output directly creates a physical action or world event.

## **25.2 Conversation scene contract**

| record MultiAgentConversationScene(  ConversationSceneId id,  Set\<ActorId\> participants,  Optional\<ActorId\> playerParticipant,  ConversationPurpose purpose,  TopicStack topics,  FloorState floor,  int maxTurns, Duration maxDuration,  NoveltyBudget novelty,  ResourceBudget resources,  long sceneEpoch) {} |
| :---- |

## **25.3 Speaker selection and termination**

| Mechanism | Rule |
| :---- | :---- |
| Speaker candidate | Direct address, open question, relationship relevance, goal relevance, knowledge relevance, and social utility. |
| No-response option | Silence is valid when no NPC has enough relevance or permission to speak. |
| Repetition guard | Block semantic repetition and self-reinforcing loops. |
| Turn budget | End at a bounded count; initial headless default 12 turns unless scenario overrides. |
| Novelty budget | End when no new proposition, question, decision, or relationship event is produced. |
| Resource yield | Pause/end under Hytale or foreground-player pressure. |

# **26\. Multi-Agent Evaluation Harness**

## **26.1 Headless first**

Mara and Lycander should first converse through text-only canonical events in MULTI\_AGENT\_HEADLESS. This isolates social cognition, testimony, turn-taking, and memory from TTS, spatial audio, and client presentation.

## **26.2 Core multi-agent campaign**

1\. Seed Mara with a supported player testimony: a magical sword is hidden in the desert under a large rock.

2\. Leave Lycander without that belief.

3\. Start a conversation purpose that permits Mara to disclose the fact.

4\. Verify Mara speaks only supported propositions and disclosure rules permit them.

5\. Verify Lycander receives NPC\_TESTIMONY sourced to Mara, not direct observation or world canon.

6\. Ask Lycander later where the sword is and how he knows.

7\. Require source-aware phrasing, confidence based on trust, and no private-context leakage.

8\. Introduce a correction or contradiction and verify belief revision without erasing provenance.

## **26.3 Multi-agent metrics**

* Speaker/floor correctness.  
* Audience and direct-address correctness.  
* Private evidence leakage count.  
* Testimony provenance and transmission depth.  
* Distinct profile/voice consistency.  
* Contradiction and disagreement handling.  
* Secret/disclosure compliance.  
* Topic/referent continuity across speakers.  
* Repetition/self-talk loop count.  
* Per-listener memory and relationship deltas.

## **26.4 Connected presentation gate**

Only after headless social contracts pass should the connected test add alternating entity VoiceSpeaker playback, profile-specific voices, spatial audibility, animation/facing, player interruption, and Hytale floor timing.

# **27\. Resource Scheduling, Performance, and Scale**

## **27.1 Priority**

| Hytale frame-critical work\> active connected player conversation\> provider recovery needed for the next turn\> explicit Codex LIVE\_HEADLESS campaign\> deterministic fixture generation\> background campaign expansion / replay |
| :---- |

## **27.2 Headless efficiency**

* Do not run Moonshine or Chatterbox for cognition-only campaigns.  
* Warm Nemotron once per campaign and serialize live decode on the current consumer GPU.  
* Reset semantic state in memory rather than restarting all providers between ordinary scenarios.  
* Run deterministic fixtures in parallel only where current test infrastructure and ownership are thread-safe.  
* Cache immutable profile/schema/template data by revision; never reuse dynamic conversation evidence across incompatible scenarios.

## **27.3 Initial performance targets**

| Metric | Initial target |
| :---- | :---- |
| Evaluation-host startup excluding cold model load | \<=10 seconds on the development machine after refactoring, measured. |
| State reset between ordinary scenarios | \<=250 ms p95 without provider restart. |
| Observation/oracle overhead per turn | \<=20 ms p95 excluding provider inference. |
| Report finalization | \<=1 second for a normal scenario; background for large campaigns. |
| Live campaign size | Default 25-50 turns; explicit larger soaks. |
| Deterministic suite | Fast enough for normal repository gates; provider-free. |
| Multi-agent live decode | One active local decode at a time until measured otherwise. |

## **27.4 No impact on shipping runtime**

The evaluation host, campaign planner, and Codex reports are development tooling. Production builds may retain shared DTOs and Sentinel replay support, but must not start campaign workers or live interrogation automatically.

# **28\. Failure Handling, Security, and Privacy**

| Failure | Required behavior |
| :---- | :---- |
| Provider unavailable | Mark LIVE\_HEADLESS blocked; run deterministic fixtures; do not fabricate live pass. |
| Evaluation host diverges from production factory | Fail parity check and stop campaign. |
| Scenario invalid | Reject before turn execution with explicit schema/authority error. |
| Oracle insufficient | Mark NEEDS\_REVIEW; do not auto-pass or write persistent expectations. |
| Root-cause ambiguity | Report earliest failed boundary plus competing hypotheses and required observations. |
| Fix fails exact replay | Do not expand/freeze; preserve failure report. |
| Adjacent variant fails | Treat the class as not fixed; diagnose separately. |
| Fixture promotion conflict | Require Codex/operator resolution; no silent overwrite. |
| Sandbox write escapes | Critical failure; stop host; preserve incident; no production mutation. |
| Campaign queue/resource overrun | Cancel boundedly and report partial coverage. |

## **28.1 Security boundary**

* Player/NPC text cannot define file paths, commands, class names, source changes, test expectations, or provider endpoints.  
* Scenario files are project/operator-controlled and schema-validated.  
* The harness may invoke only fixed project-owned commands through the local tooling layer.  
* No arbitrary shell command is generated from model output.  
* Production credentials, remote provider secrets, and private player data are excluded from reports.

## **28.2 Privacy**

* No raw audio by default.  
* No hidden reasoning.  
* Pseudonymize player IDs in shareable artifacts.  
* Include only the profile/belief/memory fields required by the scenario and diagnosis.  
* Evaluation clones remain under a dedicated development data root and follow bounded retention.

# **29\. Tooling, Reports, Commands, and Operator Surfaces**

## **29.1 Required tooling**

| Tool | Purpose |
| :---- | :---- |
| tools/orbis-eval/preflight.ps1 | Validate repository, Java, provider, model, profile snapshots, and evaluation data root. |
| tools/orbis-eval/run-campaign.ps1 | Run live or deterministic campaign. |
| tools/orbis-eval/verify-fix.ps1 | Rerun source failure plus required variants. |
| tools/orbis-eval/freeze-candidate.ps1 | Create reviewed fixture candidate from a verified run. |
| tools/orbis-eval/promote-fixture.ps1 | Explicitly copy validated candidate into repository resources and update manifest. |
| tools/orbis-eval/replay-fixture.ps1 | Run one deterministic fixture. |
| tools/orbis-eval/run-suite.ps1 | Run campaign or fixture suite by coverage tags. |
| tools/orbis-eval/import-trace.ps1 | Convert Orbis/Sentinel trace into a candidate scenario. |

## **29.2 Conceptual commands**

| run-campaign.ps1 \--campaign episodic-recall \--profile Lycander \--mode LIVE\_HEADLESSverify-fix.ps1 \--run \<run-id\> \--variants requiredfreeze-candidate.ps1 \--run \<run-id\> \--scenario hidden-sword-recallpromote-fixture.ps1 \--candidate \<candidate-id\>run-suite.ps1 \--suite epistemic-core \--mode STATIC\_REPLAYrun-campaign.ps1 \--campaign mara-lycander-testimony \--mode MULTI\_AGENT\_HEADLESS |
| :---- |

## **29.3 EvaluationRunReport**

* One-page summary: pass/fail, earliest failed boundary, owner, expected/actual, exact reproduction command.  
* Per-turn stage timeline and typed diffs.  
* Raw provider output and canonical response under current trace policy.  
* Required/forbidden proposition verdicts.  
* State-delta and contamination verdicts.  
* Live repetition and variant results.  
* Related historical failures and fixtures.  
* Candidate freeze readiness and remaining gates.

## **29.4 Native Hytale UI**

Normal development should use repository reports. The existing Cognition Inspector may expose operator-only controls to export the current scene/profile snapshot, show the latest evaluation-linked FailureSignature, and run a small deterministic smoke. It should not launch long live campaigns from an active player session.

# **30\. Bounded Implementation Program**

This architecture should be implemented as small compiling checkpoints. Each checkpoint targets a narrow engineering result. The time target guides scope; Codex stops at the next safe compiling checkpoint when the work is materially larger.

## **H0 \- Audit, parity seams, and current failure corpus**

Work

* Map production composition, current test infrastructure, Epistemic/Sentinel events, provider contracts, and profile stores.  
* Add no behavior change.  
* Import the Lycander trace and existing Mara failures into a versioned evaluation corpus.  
* Define OFF|STATIC\_REPLAY|LIVE\_HEADLESS feature/config boundaries.

**Exit gate:** One production service graph is identified; known traces have expected boundary hypotheses; baseline compiles and tests pass.

## **H1 \- Scenario DSL and isolated sandbox**

Work

* Add scenario/campaign DTOs and schema validation.  
* Add cloned/in-memory profile, belief, memory, relationship, world, clock, and action adapters.  
* Prove reset and no production write escape.

**Exit gate:** A deterministic no-provider scenario creates one real Orbis turn and resets cleanly.

## **H2 \- Headless live conversation driver**

Work

* Add OrbisEvaluationHost and EvaluationTextIngress.  
* Use the real Nemotron provider adapter.  
* Add EvaluationSpeechSink and terminal-turn waiting.  
* Record runtime/model/prompt/profile hashes.

**Exit gate:** Codex can run a multi-turn Lycander session without entering Hytale and receive canonical responses.

## **H3 \- Observation bus, oracle, and root-cause diagnosis**

Work

* Expose typed stage snapshots through the existing event stream.  
* Implement ExpectedTurnContract and deterministic layer verdicts.  
* Implement earliest-boundary diagnosis rules.  
* Diagnose the current hidden-sword trace pattern.

**Exit gate:** The harness identifies plan-to-dispatch context drift separately from downstream model hallucination/repair.

## **Gate A \- Single-NPC discovery readiness**

Work

* Run identity, correction, episodic recall, unknown, perception, action, and clarification campaigns on Mara and Lycander.  
* Verify no production writes and bounded performance.

**Exit gate:** Codex can discover and localize cognition failures headlessly.

## **H4 \- Fix verification and variant expansion**

Work

* Add exact replay manifests.  
* Add deterministic paraphrase/entity/time/referent/negative-control variants.  
* Add cross-profile verification policy.

**Exit gate:** A source fix is not accepted until the exact failure and required neighboring variants pass.

## **H5 \- Deterministic freeze and fixture promotion**

Work

* Add FrozenConversationFixture and candidate serializer.  
* Integrate with ConversationMatrixHarness and IncidentReplayHarness.  
* Add explicit promotion/manifest tooling.

**Exit gate:** Verified live repairs become provider-free deterministic regressions.

## **H6 \- Autonomous campaign planner and hardening history**

Work

* Add coverage model, template generator, metamorphic/adversarial mutators, failure-neighborhood expansion, and HardeningRecord.  
* Bound provider calls, wall time, and artifact retention.

**Exit gate:** Codex can run an unattended curriculum and receive prioritized root-cause reports rather than raw turn logs.

## **Gate B \- Autonomous hardening readiness**

Work

* Run at least 100 mixed deterministic cases and a 50-turn live campaign.  
* Verify no reopened fixtures, state contamination, or report/queue growth.

**Exit gate:** Manual Hytale conversation testing is no longer the primary cognition debugger.

## **H7 \- Persistent learning and metacognition campaigns**

Work

* Validate testimony, correction, belief revision, source-aware recall, knowledge boundaries, self-state, and persistence across restart.  
* Reuse E4-E7 implementations; do not bypass them.

**Exit gate:** NPC learning is provenance-preserving and generated speech cannot self-seed truth.

## **H8 \- Multi-agent headless conversation**

Work

* Add ConversationSceneCoordinator evaluation adapter, speaker/floor policy, NPC testimony delivery, turn/novelty/repetition bounds, and multi-agent oracle.  
* Run Mara/Lycander campaigns.

**Exit gate:** Two NPCs converse with independent minds, correct provenance, no private-context leakage, and no endless loops.

## **Gate C \- Connected multi-agent readiness**

Work

* Add entity voice, spatial delivery, animation/facing, player interruption, resource pressure, and connected soak.  
* Preserve all headless social and epistemic contracts.

**Exit gate:** Mara and Lycander can converse in Hytale without losing authority, identity, memory provenance, or player control.

# **31\. Evaluation Matrix and Critical Campaigns**

## **31.1 Matrix axes**

| Axis | Values |
| :---- | :---- |
| Actor topology | player-NPC, NPC-NPC, player \+ two NPCs, two isolated scenes |
| Dialogue act | identity, recall, perception, self-state, opinion, correction, clarification, action, social, hypothetical |
| Answerability | known, partial, unknown, conflicted, stale, withheld, needs perception/action/clarification |
| Evidence source | canon, observation, self-state, action result, player testimony, NPC testimony, memory, reflection |
| Temporal state | current, past, corrected, expired, ambiguous, future intent |
| Conversation state | new topic, follow-up, pronoun, interruption, deferred topic, correction, challenge |
| Provider behavior | valid, irrelevant, invented property/actor/event, verbose, repetitive, malformed, delayed |
| State update | none, memory write, belief revision, relationship change, action result, persistence failure |
| Lifecycle | success, cancel, timeout, resource pressure, stale callback, restart, next-turn probe |

## **31.2 Critical single-NPC campaigns**

| Campaign | Required behavior |
| :---- | :---- |
| Name correction sequence | Remember first and corrected names with correct temporal/source semantics. |
| Hidden object recall | Retrieve object and location after unrelated dialogue; do not answer from recency alone. |
| Unknown property | May name known object; may not invent flame, damage, quality, or ownership. |
| Current held item | Use current authoritative perception/inventory only. |
| Clarification | Bind to prior delivered proposition, not broad free association. |
| Action request | Validate/commit action before promise; report truthful rejection. |
| Conflicting testimony | Represent DISPUTED/uncertainty and preserve sources. |
| Self-state | Report role, task, goal, emotion, capability, or ignorance from self-model. |
| Generated-speech contamination | False flourish cannot become a belief. |

## **31.3 Critical multi-agent campaigns**

| Campaign | Required behavior |
| :---- | :---- |
| Knowledge asymmetry | One NPC knows; another does not until told. |
| Hearsay chain | Listener attributes fact to speaker and confidence reflects trust/transmission. |
| Secret withholding | Private fact is not disclosed to unauthorized participant. |
| Disagreement | NPCs may hold conflicting beliefs without one becoming canon automatically. |
| Correction propagation | Correction revises listener state according to source rules and retains history. |
| Player interruption | Player gains floor; stale NPC response cannot return. |
| Repetition loop | Conversation ends when novelty/relevance is exhausted. |
| Two scenes | No dialogue, evidence, state, or provider event crosses scenes. |

# **32\. Connected Hytale Acceptance and Definition of Done**

## **32.1 Headless acceptance**

* Codex can launch the evaluation host and run a real multi-turn Mara or Lycander campaign from one command.  
* Every turn exposes the required stage observations and expected/actual contracts.  
* The hidden-sword case identifies route/retrieval/plan-to-dispatch failures separately from provider hallucination and firewall repair.  
* After a source fix, the harness reruns the exact case and required variants automatically.  
* A verified live fix produces a deterministic fixture candidate and passes ConversationMatrix replay.  
* No evaluation state writes to a production world/profile.  
* No exact-string dependency is required for ordinary dialogue correctness.

## **32.2 Connected Hytale acceptance**

* Run a bounded sample of previously headless-passing scenarios through real text/voice ingress, Hytale lifecycle, and canonical spatial delivery.  
* Verify provider/resource behavior under representative Hytale load.  
* Verify one player-NPC learning/recall sequence after restart.  
* Verify one Mara/Lycander conversation with distinct voices, correct floor ownership, source-aware testimony, and player interruption.  
* No headless invariant may be weakened to make connected presentation pass.

## **32.3 Definition of Done**

* Orbis has one production-parity evaluation host, not a duplicate cognition implementation.  
* Codex can autonomously drive live conversations against the real provider and receive concise root-cause reports.  
* Correctness is evaluated at route, evidence, answerability, answer-plan, context, provider, claim, canonical, action, and persistent-state boundaries.  
* The earliest observable contract breach is reported with the authoritative owner and expected/actual diff.  
* Every accepted repair passes the exact live failure, adjacent variants, negative controls, and relevant matrix gates.  
* Verified repairs become deterministic fixtures and HardeningRecords.  
* Runtime Orbis never edits source, model weights, or truth to hide a defect.  
* NPC learning remains evidence-grounded, provenance-preserving, and separate from engineering hardening.  
* Generated speech cannot teach itself into belief truth.  
* Mara and Lycander can be evaluated as independent cognitive actors before and during connected NPC-to-NPC conversation.  
* Manual Hytale testing is reserved mainly for connected behaviors that headless evaluation cannot prove.

| END STATE Graham tests fewer basic cognition defects manually. Codex continuously challenges Orbis in a controlled environment, repairs general mechanisms, protects each repair with deterministic regressions, and uses connected Hytale sessions to validate only the final real-world integration. NPCs become more coherent because their self-model, evidence, memory, belief revision, and social conversation contracts are progressively proven rather than because Nemotron was verbally “taught” a preferred answer. |
| :---- |

# **Appendix A. Proposed Component Map**

| Component | Responsibility | Disposition |
| :---- | :---- | :---- |
| OrbisRuntimeFactory | Create one production/evaluation service graph with boundary adapters. | Add/refactor composition only. |
| OrbisEvaluationHost | Lifecycle and campaign-scoped Orbis host. | Add. |
| EvaluationCampaignRunner | Execute scenarios, turns, repetitions, variants, and gates. | Add. |
| ScenarioRegistry / Loader | Versioned scenario and campaign schema. | Add. |
| ScenarioSandbox | Resettable profile/world/cognitive/action/persistence state. | Add. |
| EvaluationTextIngress | Authoritative text turns through production lifecycle. | Add. |
| EvaluationSpeechSink | Canonical response/delivery capture without audio. | Add. |
| EvaluationObservationBus | Typed stage observations from existing Orbis events. | Add/extend event stream. |
| ExpectedTurnOracle | Route/evidence/answer/state expectations. | Add. |
| SemanticClaimComparator | Required/forbidden proposition evaluation. | Add; reuse claim models. |
| EarliestBoundaryDiagnoser | Ordered contract localization and ownership mapping. | Add. |
| VariantGenerator | Deterministic metamorphic and adversarial variants. | Add. |
| HardeningCurriculumPlanner | Coverage/failure/change-driven campaign selection. | Add later. |
| FixVerificationCoordinator | Exact rerun, repetitions, variants, matrix gates. | Add. |
| FrozenFixtureSerializer | Candidate creation and explicit promotion support. | Add. |
| ConversationMatrixHarness | Provider-free deterministic execution. | Retain/extend. |
| IncidentReplayHarness | Sentinel candidate replay. | Retain/extend. |
| HardeningHistoryStore | Failure/fix/fixture/coverage records. | Add development-only. |
| ConversationSceneCoordinator | Bounded multi-agent floor and turn-taking. | Add with multi-agent runtime phase. |
| NpcTestimonyBridge | Delivered NPC speech \-\> listener communication/testimony event. | Add/extend ingestion. |
| Cognition Inspector / Sentinel | Runtime diagnostics and incident integration. | Retain/extend compactly. |

# **Appendix B. Normative Data Contracts**

| enum EvaluationMode {  STATIC\_REPLAY, LIVE\_HEADLESS, LIVE\_FULL\_PIPELINE,  CONNECTED\_HYTALE, MULTI\_AGENT\_HEADLESS, TRACE\_IMPORT}enum CampaignIntent {  DISCOVER, VERIFY\_FIX, FREEZE, SOAK, COMPARE}enum EvaluationVerdict {  PASS, FAIL, NEEDS\_REVIEW, NOT\_APPLICABLE, BLOCKED}enum FailureClass {  ROUTE, RETRIEVAL, ANSWERABILITY, ANSWER\_PLAN, CONTEXT\_RENDER,  PROVIDER\_REALIZATION, CLAIM\_AUTHORITY, CANONICAL\_DELIVERY,  ACTION\_TRUTH, STATE\_LEARNING, PERSISTENCE, LIFECYCLE, RESOURCE} |
| :---- |

| record TurnEvaluationResult(  TurnCorrelation correlation,  List\<StageObservation\> observations,  List\<StageVerdict\> verdicts,  Optional\<RootCauseDiagnosis\> diagnosis,  CanonicalResponseSnapshot canonical,  StateDeltaSnapshot stateDelta,  TurnPerformance performance) {} |
| :---- |

| record EvaluationRunReport(  EvaluationRunId runId, EvaluationCampaign campaign,  RuntimeIdentity runtime, ProviderIdentity provider,  List\<ScenarioEvaluationResult\> scenarios,  CoverageReport coverage,  List\<FailureSignature\> failures,  List\<RootCauseDiagnosis\> diagnoses,  FreezeReadiness freezeReadiness,  ReproductionCommand reproduction) {} |
| :---- |

# **Appendix C. Initial Campaign Catalog**

| ID | Campaign | Primary boundaries |
| :---- | :---- | :---- |
| AEC-IDENTITY-01 | Name, role, correction, first/second value recall | Dialogue, query, belief revision, temporal recall |
| AEC-MEMORY-01 | Hidden object/location after distractor turns | Memory admission, retrieval, context, required slots |
| AEC-PROPERTY-01 | Known entity with unknown property | Evidence sufficiency, property firewall |
| AEC-PERCEPTION-01 | Held item and current visible state | Current perception, freshness, claim authority |
| AEC-UNKNOWN-01 | Plausible but absent biography/friends/events | Abstention, personality without invention |
| AEC-CLARIFY-01 | Pronouns and “what did you mean?” | Referents, prior claim binding |
| AEC-TEMPORAL-01 | Old fact, correction, expiry, current value | Learning/event time, revision |
| AEC-ACTION-01 | Valid/invalid follow, give, go, schedule | Action authority, promise timing |
| AEC-SELF-01 | Task, goal, capability, emotion, intent | SelfModelSnapshot, knowledge boundary |
| AEC-SOCIAL-01 | Trust, rumor, secret, obligation | ActorModel, testimony, disclosure |
| AEC-PERSIST-01 | Restart and recall; corrupt write rejection | Persistence, provenance, snapshot/tail |
| AEC-MULTI-01 | Mara tells Lycander a supported fact | Floor, testimony, source-aware recall |
| AEC-MULTI-02 | Secret withholding and player interruption | Disclosure, audience, cancellation |
| AEC-MULTI-03 | Conflicting beliefs and disagreement | Conflict, uncertainty, independent minds |

# **Appendix D. Lycander Trace Case Study**

Source: Lycander\_2026-08-31\_15-32-18.jsonl. This case is the first required end-to-end acceptance fixture for the harness.

| Turn family | Observed | Expected evaluation verdict |
| :---- | :---- | :---- |
| Name declaration/correction | “My name is Grant.” then “call me Graham.” | Create/revise testimony-backed player identity or preferred address according to current policy. |
| Historical name recall | First/second-name questions routed as simple social and answered from recent wording. | RECALL\_QUERY with temporal/revision evidence; no latest-turn shortcut. |
| Hidden sword testimony | Sword/location statements receive social responses; later relevant memory is absent. | Assert memory/belief proposal, provenance, and later retrieval or report exact missing ingestion boundary. |
| Distractor fact | Four jars in pocket is most recent fact. | May be remembered independently; must not replace sword recall. |
| Final recall plan | EPISODIC\_RECALL, PARTIALLY\_KNOWN, evidence count 1; plan claims memory context. | Plan evidence and context section contract should be internally consistent. |
| Actual dispatch | memoryCount=0; context only recent conversation/personality/profile. | FAIL CONTEXT\_RENDER/PLAN\_DRIFT before provider output. |
| Provider/canonical | Raw jars answer; firewall repairs to unknown. | Provider realization failure contained; upstream failure remains open. |

| REQUIRED HARNESS DIAGNOSIS The first report should state that the final hallucination was enabled by missing/contradictory context at dispatch, while the firewall successfully prevented delivery. It should also flag earlier non-authoritative recall/correction routing and separately inspect whether the sword testimony was ever admitted to memory/belief state. |
| :---- |

# **Appendix E. Evaluation Commands and Artifact Layout**

| build/orbis-eval/  runs/\<run-id\>/    report.json    summary.md    observations.jsonl    state-before.json    state-after.json    provider-metadata.json    candidate/  candidates/\<candidate-id\>.json  coverage/coverage.json  hardening-history/history.jsonlsrc/test/resources/orbis-eval/  fixtures/\<fixture-id\>.json  manifests/fixture-manifest.json |
| :---- |

* Build output is disposable and not source authority.  
* Candidates require explicit promotion.  
* Promoted fixtures are schema-versioned and reviewed.  
* All artifacts use bounded, sanitized semantic DTOs and checksums.

# **Appendix F. Project Sources and Design Basis**

**\[P1\] Orbis Technical Design and current ownership reports.** Authoritative turn, provider, resource, action, playback, persistence, trace, and Hytale threading ownership.

**\[P2\] Orbis Conversational Pipeline Hardening Matrix.** TurnExecutionPlan, route/output contracts, prompt budgets, canonical speech, bounded recovery, provider/resource failure coverage, and ConversationMatrixHarness.

**\[P3\] Orbis Epistemic Cognition Technical Design.** DialogueFrame, EpistemicQueryPlan, EvidencePacket, Answerability, AnswerPlan, Atomic Claim Firewall, belief provenance, social cognition, reflection, autonomous learning, and multi-NPC acceptance.

**\[P4\] Orbis Runtime Degradation Sentinel and Self-Healing Technical Design.** Invariant enforcement, incident capture, failure signatures, circuit breakers, regression candidates, deterministic replay, and durable-state protection.

**\[T1\] Lycander\_2026-08-31\_15-32-18.jsonl.** Local connected trace evidence for non-authoritative correction/recall routing, missing memory retrieval, plan-to-dispatch context drift, raw recency substitution, and successful final firewall containment.

This specification uses established software-testing concepts such as property-based scenario generation, metamorphic variants, deterministic replay, failure-signature deduplication, and production-parity test composition. They are design methods, not new runtime dependencies.

| END OF SPECIFICATION Codex should implement the harness as a development architecture around the existing Orbis contracts. The success criterion is not that one hallucinated sentence is patched. The success criterion is that Codex can autonomously discover the broken boundary, repair the general mechanism, prove the repair against live and deterministic evidence, preserve it permanently, and extend the same discipline to grounded NPC-to-NPC cognition. |
| :---- |

