# ORBIS EPISTEMIC COGNITION

## Evidence-Grounded Intelligence, Belief, Memory, Social Reasoning, and Autonomous Learning

**Subsystem of ImmersiveNPCs / Orbis**  
**Codex Implementation Specification**  
**Version 1.0 | 30 August 2026**

> **AUTHORITATIVE PURPOSE**  
> Move ImmersiveNPCs from reliable language generation toward persistent, evidence-grounded intelligence. Orbis must determine what an NPC knows, believes, remembers, perceives, infers, wants, and is permitted to claim. Nemotron reasons over that bounded cognitive workspace and expresses the result as the character. The language model must never be responsible for deciding both what reality is and how to talk about it.

**INTELLIGENCE**  
Directly answer the current problem, reason when needed, maintain beliefs over time, learn from validated outcomes, and act from persistent goals.

**EPISTEMIC DISCIPLINE**  
Objective claims require compatible evidence and provenance. Uncertainty, conflict, ignorance, rumor, opinion, and intentional deception are distinct states.

**LIVING-WORLD AGENCY**  
Conversation, memory, relationships, schedules, economic decisions, autonomous plans, and physical actions must refer to the same persistent NPC and the same authoritative world.

**LATENCY AND HARDWARE SAFETY**  
Common dialogue remains deterministic-first and fast. Reflection, deep reasoning, and social simulation are bounded background work beneath Hytale and foreground conversation.

*This document is written for Codex. Proposed class names are normative concepts. The repository, current Orbis ownership, installed Hytale SDK, and existing persistent-data contracts remain authoritative.*

---

# Contents

1. Implementation Directive  
2. Executive Decision and Failure Model  
3. Scope, Goals, and Non-Goals  
4. Intelligence Principles and Hard Invariants  
5. Integration with Existing Orbis Ownership  
6. High-Level Cognitive Architecture  
7. Epistemic Ontology and Domain Model  
8. Dialogue State, Referents, and Query Planning  
9. NPC Self-Model and Bounded Theory of Mind  
10. Belief Store, Provenance, Time, Confidence, and Revision  
11. Memory Ingestion, Retrieval, and Evidence Sufficiency  
12. Evidence Packet and Answerability  
13. Answer Planning and Personality-Constrained Realization  
14. Atomic Claim Firewall and Speech Commitment  
15. Conversation Workspace and Multi-Turn Coherence  
16. Social Cognition, Secrets, Disclosure, and Intentional Deception  
17. Reflection, Consolidation, and Learning from Outcomes  
18. Autonomous Cognition, ReAct, Plans, and Skill Library  
19. Performance, Scheduling, Persistence, and Scaling  
20. Trace, Cognition Inspector, and Operator Diagnostics  
21. Failure Handling, Security, and Privacy  
22. Bounded Implementation Program  
23. Epistemic Intelligence Test Matrix  
24. Connected Hytale Acceptance and Definition of Done  
Appendix A. Proposed Component Map  
Appendix B. Normative Data Contracts  
Appendix C. Critical Acceptance Scenarios  
Appendix D. Sources and Design Basis

---

# 1. Implementation Directive

This design extends the current NPC brain beneath the hardened Orbis turn pipeline. It is not a new conversational runtime, not a second NPC brain, not a model replacement, and not a prompt-only patch.

## 1.1 Required reading

Before changing code, Codex **MUST** read and reconcile:

- `Orbis Technical Design.docx` / `Orbis_Technical_Design_v1.docx`.
- `Orbis Conversational Pipeline Hardening Matrix.md`.
- Current ownership and implementation reports through the active revision.
- The latest available Mara traces, especially the cases involving failed name recall, unsupported scene properties, objective autobiographical invention, action-routing ambiguity, and malformed STT input.
- Existing profile, memory, relationship, belief, perception, attention, intent, task, plan, schedule, `NpcDecision`, `AgentOperation`, and reflection implementations.

## 1.2 Ownership constraints

Codex **MUST** preserve:

- `OrbisTurnCoordinator` as the authoritative lifecycle, branch, floor, cancellation, and response owner.
- `TurnExecutionPlan`, `ContractBudgetPlanner`, `RecoverySupervisor`, and `CanonicalSpeechLedger` as the hardened conversational contract.
- Hytale as authority for physical state, entity existence, inventory, ownership, location, combat, navigation, transactions, and action results.
- Existing stable NPC identity and persistent profile ownership.
- Existing capability-gated actions, tasks, shared plans, schedules, and background-life systems.
- Current Moonshine, Nemotron, Ollama, Chatterbox Turbo, resource policy, Hytale UI, and trace pipeline unless a later separately approved design changes them.

## 1.3 Implementation discipline

- **MUST NOT** implement this entire document in one Codex task.
- Every Codex task is a bounded increment with a target of 15-25 minutes and an explicit stop/report boundary.
- Expensive migrations, backfills, embeddings, or benchmarks are separate offline increments.
- Every behavior change attaches to a shared epistemic contract, invariant, or generated matrix case. Do not add sentence-specific patches.
- Begin in `SHADOW` mode where the new system computes decisions and diagnostics without changing speech, memory, or actions.
- Promote one layer at a time after deterministic and connected validation.
- Do not fine-tune Nemotron during this program. First isolate the remaining model ceiling behind a mature cognitive architecture.

## 1.4 Immediate implementation boundary

The first production objective is the **Epistemic Conversation Core**:

1. dialogue-state tracking;
2. query-specific evidence retrieval;
3. answerability classification;
4. a compact `AnswerPlan`;
5. direct-answer-first surface realization;
6. an atomic claim firewall;
7. correct use of `UNKNOWN`, conflict, and uncertainty;
8. permanent regression coverage for current hallucination classes.

Persistent belief revision, social theory of mind, reflection, and autonomous skill learning follow as separate bounded increments after the conversation core is stable.

---

# 2. Executive Decision and Failure Model

The current system reliably moves an utterance through STT, routing, Nemotron, validation, TTS, and Hytale. The remaining severe intelligence failures are primarily caused by an immature cognitive workspace around the model:

- a question can be routed without retrieving the fact that answers it;
- the model receives broad personality and context but no explicit answerability decision;
- the model is allowed to transform a grounded entity into an unsupported property or event;
- safe social language is sometimes treated as permission to invent friends, possessions, biography, or witnessed history;
- raw dialogue history substitutes for structured conversational focus and referents;
- retrieval can be absent, excessive, irrelevant, temporally stale, or unable to abstain;
- a generated statement may enter memory without a strong distinction between “was said” and “is true”;
- reflection and inference can become self-reinforcing unless every derived belief retains support provenance;
- the LLM is sometimes asked to discover reality, decide what it knows, plan the answer, and write the prose in one step.

The systemic answer is:

> **World Truth -> NPC Beliefs -> Epistemic Query -> Evidence Packet -> Answerability -> AnswerPlan -> Character Realization -> Atomic Claim Firewall -> Speech/Action -> Validated Memory and Belief Update**

Research supports the modular direction. CoALA describes language agents through modular memory, structured action spaces, and explicit decision procedures. Generative Agents found observation, memory retrieval, reflection, and planning jointly necessary for believable behavior. ReAct reduces hallucination and error propagation by grounding reasoning in external observations and actions. Self-RAG shows that retrieval should be adaptive rather than blindly injected. FActScore demonstrates the value of decomposing generated text into atomic factual claims. Reflexion and Voyager show that agents can improve through stored feedback, validated outcomes, and reusable skills without updating model weights. [S1-S7]

The design therefore treats Nemotron as a reasoning and language component inside the NPC mind. It is not the memory, belief system, world model, social model, action executor, or final authority.

---

# 3. Scope, Goals, and Non-Goals

## 3.1 Goals

- Make NPC dialogue directly relevant to the current utterance and conversation topic.
- Distinguish world truth from each NPC's partial, fallible, actor-specific beliefs.
- Represent knowledge, observation, memory, hearsay, inference, opinion, uncertainty, conflict, and ignorance explicitly.
- Retrieve the smallest sufficient evidence set for the exact question or decision.
- Require a compact semantic answer plan before the model writes factual dialogue.
- Prevent unsupported objective claims, properties, events, relationships, possessions, locations, times, and completed actions from reaching speech.
- Let personality, emotion, humor, metaphor, disagreement, and opinion remain expressive without manufacturing biography or world facts.
- Support truthful statements such as “I saw it,” “Garrick told me,” “I think so,” “I do not remember,” and “I do not know.”
- Preserve conversation referents, corrections, open questions, commitments, and topic continuity.
- Let NPCs learn from player statements, direct perception, action results, trusted testimony, and validated reflections without changing model weights.
- Preserve provenance through rumors, social transmission, contradiction, and belief revision.
- Enable autonomous NPCs to reason and act from persistent beliefs, goals, needs, relationships, and reusable skills.
- Keep foreground response latency close to the current hardened pipeline by making common turns deterministic-first and retrieval-bounded.
- Scale through active/background/dormant simulation tiers and a shared scarce cognition scheduler.

## 3.2 Non-goals

- Claiming actual consciousness or sentience.
- Giving NPCs omniscient access to Hytale world state.
- Building a universal ontology for every possible mod or item in one release.
- Replacing existing memory, relationship, perception, task, plan, schedule, capability, or action systems wholesale.
- Adding another LLM call to every ordinary conversational turn.
- Persisting hidden chain-of-thought.
- Allowing generated dialogue to become evidence merely because the NPC said it.
- Letting a player's assertion directly rewrite canonical world truth or authored NPC identity.
- Solving multilingual STT, TTS prosody, or all model-quality issues in this phase.
- Fine-tuning Nemotron before the architecture can isolate genuine model failures.

---

# 4. Intelligence Principles and Hard Invariants

## 4.1 Foundational principles

1. **Reality and belief are different.** Hytale owns reality. Every NPC owns a partial belief model derived from perception, memory, testimony, authored knowledge, and inference.
2. **Speech is not truth.** Conversation history proves only that a statement was delivered, not that its proposition is correct.
3. **Objective assertions require support.** Subjective expression may be creative; facts, properties, events, relationships, possessions, locations, quantities, and action claims require compatible evidence.
4. **Unknown is a valid intelligent answer.** Abstention, uncertainty, or a clarifying question is better than fabricated certainty.
5. **Provenance survives transformation.** Retrieved facts, derived beliefs, reflections, rumors, and learned skills retain the evidence chain that produced them.
6. **Direct answer first.** The NPC answers the current question or request before optional personality elaboration.
7. **Retrieval is query-specific.** Do not dump broad memory or world state into a small model.
8. **Inference is marked as inference.** A reasonable conclusion does not silently become observed fact.
9. **Intentional deception is explicit.** A lie, if later supported, is a planned social action, never an accidental hallucination.
10. **Actions teach through results.** The authoritative result, not the model's expectation, updates beliefs and procedural learning.
11. **The same mind drives dialogue and autonomy.** A fact remembered in conversation and a fact used in autonomous planning come from the same belief/evidence system.
12. **The model is replaceable; the mind is not.** Provider changes must not alter what the NPC knows, believes, remembers, or is allowed to claim.

## 4.2 Hard invariants

- A persistent belief has a stable ID, owner NPC, typed proposition, temporal scope, confidence, status, and provenance.
- Canonical authored facts and validated Hytale action results cannot be overwritten by unsupported dialogue.
- A player's statement enters an NPC belief store as testimony, not automatic world truth.
- An NPC's generated speech cannot create new beliefs unless an already-authorized `AnswerPlan`, action result, or separate validated ingestion event supports them.
- A reflection cannot introduce a new entity, event, relationship, possession, location, or causal claim absent from its supports.
- Every derived belief references all supporting assertions and is invalidated or downgraded when those supports are superseded.
- A volatile belief such as location or held item has freshness/validity semantics different from stable identity or authored relationships.
- Retrieval may return no evidence. Low-confidence recall is rejected rather than padded with weak matches.
- A response cannot assert a property of a grounded entity unless that property is separately supported.
- A response cannot assert that an action occurred unless an `ActionResult` proves it.
- A response cannot assert a relationship, possession, family member, past experience, or friend solely because it is socially plausible.
- A factual answer exposes compatible evidence references to the claim validator and trace.
- Only actually delivered canonical speech enters shared conversation history.
- Hidden reasoning is never persisted as belief or memory.
- World/ECS snapshots are immutable semantic DTOs captured quickly on Hytale threads; all retrieval, reasoning, validation, and persistence remain off-thread.

---

# 5. Integration with Existing Orbis Ownership

The existing Orbis technical design intentionally retained the NPC intelligence subsystem beneath the turn runtime. This design extends that retained subsystem. It must not create parallel turn, speech, action, or persistence authorities. [P1]

## 5.1 Existing components retained

- `OrbisTurnCoordinator`: lifecycle and cancellation.
- `TurnPlanCompiler`: route, contract, budget, deadline, and recovery planning.
- `ContextProfileBuilder`: bounded prompt/context construction.
- `NpcCognitionService`: cognition entry point.
- memory, relationship, belief, perception, attention, intent, task, plan, and schedule services.
- `DialogueClaimValidator`: extended rather than bypassed.
- `AgentOperation` and capability validation: action authority.
- `CanonicalSpeechLedger`: delivered lexical truth.
- `RecoverySupervisor`: bounded recovery.
- `OrbisResourceScheduler`: Hytale-first scheduling.
- existing trace and Cognition Inspector event stream.

## 5.2 New integration seam

`TurnPlanCompiler` gains an `EpistemicContract` produced before provider dispatch:

```java
record EpistemicContract(
    DialogueFrame dialogueFrame,
    EpistemicQueryPlan queryPlan,
    EvidencePacket evidence,
    Answerability answerability,
    AnswerPlan answerPlan,
    ClaimPolicy claimPolicy,
    EpistemicBudget budget
) {}
```

The contract becomes part of `TurnExecutionPlan`. `ContextProfileBuilder` renders the evidence and answer plan. Nemotron receives a bounded cognitive workspace rather than independently searching broad context. `DialogueClaimValidator` validates complete semantic phrases against the same contract before `CanonicalSpeechLedger` commitment.

## 5.3 No duplicate prompt authority

- The epistemic layer decides what context is relevant.
- `ContextProfileBuilder` remains the only live prompt renderer.
- Provider adapters do not retrieve memory or change evidence.
- Nemotron cannot invoke unbounded hidden retrieval.
- Autonomous cognition uses the same query/retrieval/belief services, with different priority and deadlines.

---

# 6. High-Level Cognitive Architecture

## 6.1 Foreground conversation

```text
Authoritative transcript / native text
        |
        v
DialogueStateTracker
  - dialogue act
  - referents
  - topic/open question
  - expected answer kind
        |
        v
EpistemicQueryPlanner
  - exact entities/predicates
  - time window
  - source classes
  - retrieval/abstention policy
        |
        v
EvidenceRetriever + BeliefResolver
  - exact structured lookup
  - relevant memories
  - current perception/self-state
  - relationship/social knowledge
  - contradictions and freshness
        |
        v
EvidencePacket + Answerability
        |
        v
AnswerPlanner
  - semantic answer
  - evidence bindings
  - uncertainty
  - social/emotional intent
  - action/disclosure policy
        |
        v
TurnExecutionPlan / Nemotron surface realization
        |
        v
AtomicClaimFirewall
        |
        v
CanonicalSpeechLedger -> Chatterbox -> Hytale
        |
        v
Delivered dialogue event / validated ingestion
```

## 6.2 Autonomous cognition

```text
Authoritative perception / needs / schedule / task result
        |
        v
Belief updates + attention
        |
        v
Opportunity generation
        |
        v
Goal/intent appraisal using beliefs and confidence
        |
        v
Plan or reusable Skill selection
        |
        v
Validated AgentOperation
        |
        v
Hytale ActionResult
        |
        v
Memory + belief revision + optional reflection
```

## 6.3 Intelligence tiers

- **Reactive deterministic:** exact recall, current self-state, simple perception, direct action validation, yes/no preference.
- **Fast model realization:** turn an authorized answer plan into concise character speech.
- **Grounded reasoning:** resolve several memories, conflicts, or causal links using bounded reasoning.
- **Deliberative planning:** compare goals, risks, relationships, and multi-step options.
- **Background reflection:** synthesize validated experiences into higher-level beliefs or lessons.
- **Dormant simulation:** deterministic schedule/economy advancement without LLM inference.

The scheduler always prioritizes Hytale, capture/playback, and active player conversation over reflection and background life.

---

# 7. Epistemic Ontology and Domain Model

Do not create a universal open-world knowledge graph in the first release. Use a hybrid model:

- typed assertions for facts that affect dialogue, action, identity, relationships, ownership, time, location, and plans;
- episodic records for richer narrative context;
- authored profile facts for stable character canon;
- opaque semantic notes only when a typed predicate is unavailable, with lower authority and no direct action effect.

## 7.1 Separate source, status, and claim mode

Do not collapse “remembered,” “true,” and “confident” into one enum.

### Evidence source kind

```text
AUTHORED_CANON
DIRECT_OBSERVATION
SELF_STATE
ACTION_RESULT
PLAYER_TESTIMONY
NPC_TESTIMONY
DOCUMENTED_WORLD_LORE
EPISODIC_MEMORY
DERIVED_REFLECTION
PROCEDURAL_OUTCOME
```

### Epistemic status

```text
KNOWN
BELIEVED
SUSPECTED
DISPUTED
UNKNOWN
SUPERSEDED
RETRACTED
EXPIRED
```

### Claim mode

```text
OBJECTIVE_FACT
SUBJECTIVE_OPINION
EMOTION
DESIRE
INTENTION
COMMITMENT
INFERENCE
HYPOTHETICAL
METAPHOR
QUESTION
DECEPTION
```

A source answers “where did this come from?” Status answers “how does this NPC currently hold it?” Claim mode answers “what kind of thing may be said?”

## 7.2 Core assertion

```java
record EpistemicAssertion(
    AssertionId id,
    UUID ownerNpcStableId,
    EntityKey subject,
    PredicateKey predicate,
    EpistemicValue object,
    Polarity polarity,
    EpistemicStatus status,
    double confidence,
    EvidenceProvenance provenance,
    TemporalScope temporal,
    AssertionScope scope,
    Set<AssertionId> supportIds,
    Set<AssertionId> conflictIds,
    long revision,
    Instant learnedAt,
    Instant lastConfirmedAt
) {}
```

## 7.3 Provenance

```java
record EvidenceProvenance(
    EvidenceSourceKind sourceKind,
    UUID sourceActorId,
    UUID sourceWorldEventId,
    UUID sourceMemoryId,
    UUID sourceActionResultId,
    List<AssertionId> derivationChain,
    double sourceReliability,
    int transmissionDepth
) {}
```

- `transmissionDepth=0` for direct evidence.
- Rumor/testimony increments depth and preserves the chain.
- Avoid copying full prose chains into live prompts; use IDs and short summaries.
- Cap normal gossip propagation depth initially. Deep rumor chains become low confidence or unresolved.

## 7.4 Predicate registry

Each typed predicate declares:

```java
record PredicateDefinition(
    PredicateKey key,
    ValueType valueType,
    StabilityClass stability,
    ConflictKeyStrategy conflictStrategy,
    DecayPolicy decayPolicy,
    Set<EvidenceSourceKind> authoritativeSources,
    boolean actionRelevant,
    boolean privacySensitive
) {}
```

Initial high-value predicates include:

- identity: `NAME`, `SPECIES`, `ROLE`, `FAMILY_RELATION`;
- relationships: `TRUSTS`, `LIKES`, `FEARS`, `OWES`, `PROMISED_TO`;
- world: `IS_AT`, `HOLDS`, `OWNS`, `EQUIPPED`, `VISIBLE`, `NEAR`;
- properties: `HAS_PROPERTY`, `CONDITION`, `QUALITY`, `VALUE_ESTIMATE`;
- events: `WITNESSED`, `WAS_TOLD`, `ACTION_OCCURRED`, `TRANSACTION_OCCURRED`;
- self/intent: `CURRENT_TASK`, `CURRENT_GOAL`, `INTENDS`, `EMOTIONAL_STATE`;
- social knowledge: `BELIEVES_ACTOR_KNOWS`, `SECRET_ABOUT`;
- procedural: `ROUTE_BLOCKED`, `SKILL_SUCCEEDED`, `SKILL_FAILED`.

An unknown/unregistered property asserted about a known object is not automatically grounded. This directly prevents “the lantern exists” from authorizing “the lantern is flickering.”

## 7.5 Self-model

Each NPC receives an immutable `SelfModelSnapshot` for a turn:

```java
record SelfModelSnapshot(
    UUID npcStableId,
    String identitySummary,
    Set<String> roles,
    InventorySnapshot inventory,
    EquipmentSnapshot equipment,
    TaskSnapshot currentTask,
    PlanSnapshot activePlan,
    Set<GoalState> activeGoals,
    NeedState needs,
    EmotionState emotion,
    CapabilitySnapshot capabilities,
    Set<Commitment> commitments,
    KnowledgeBoundary knowledgeBoundary
) {}
```

This supports truthful self-reference such as occupation, current task, possessions, intent, emotion, and capabilities without asking Nemotron to invent an internal state.

---

# 8. Dialogue State, Referents, and Query Planning

## 8.1 Dialogue frame

Every accepted transcript produces one `DialogueFrame` before route/context compilation.

```java
record DialogueFrame(
    DialogueAct act,
    ExpectedAnswerKind expectedAnswer,
    Optional<EntityKey> subject,
    Optional<PredicateKey> predicate,
    Optional<EntityKey> object,
    ReferentMap referents,
    TopicId currentTopic,
    Optional<ClaimId> challengedPriorClaim,
    Optional<ActionIntent> actionIntent,
    double confidence,
    Set<DialogueSignal> signals
) {}
```

## 8.2 Dialogue-act taxonomy

Initial acts:

```text
GREETING
SOCIAL_CHECKIN
FACT_QUERY
IDENTITY_QUERY
RECALL_QUERY
PERCEPTION_QUERY
SELF_STATE_QUERY
OPINION_QUERY
PREFERENCE_QUERY
EMOTION_QUERY
CLARIFICATION_REQUEST
CORRECTION
CHALLENGE
SELF_DISCLOSURE
INFORMATION_STATEMENT
ACTION_REQUEST
SOCIAL_INVITATION
PROMISE_OR_COMMITMENT
HYPOTHETICAL
FAREWELL
UNRESOLVED
```

Use deterministic semantic patterns, typed entities, known predicates, previous-turn structure, and action/capability parsers first. Invoke a tiny bounded Nemotron classification only when the deterministic result is ambiguous. Do not classify every turn with another model call.

## 8.3 Direct requirements

- “What is my name?” -> `IDENTITY_QUERY`, player subject, `NAME`, exact player-fact retrieval.
- “Where did I hide it?” -> `RECALL_QUERY`, resolve `it`, episodic event/location retrieval.
- “What is in my hand?” -> `PERCEPTION_QUERY`, current held-item snapshot only.
- “What did you mean?” -> `CLARIFICATION_REQUEST`, bind to the last delivered NPC claim/answer plan.
- “Where are you going?” -> `SELF_STATE_QUERY`, current task/plan/intent.
- “Can you follow me?” -> `ACTION_REQUEST`, capability/action authority first.
- “Do you have friends?” -> relationship/self-biography query; do not authorize invented relationships.

## 8.4 Referent resolution

`ConversationWorkspace` owns a bounded `ReferentMap`:

- recent entities and their grammatical roles;
- last player claim;
- last NPC claim;
- current object of discussion;
- current action target;
- open question;
- prior correction/challenge target.

Resolve with stable IDs. If two candidates remain plausible, set `UNRESOLVED` and ask a clarification rather than guessing.

## 8.5 Query plan

```java
record EpistemicQueryPlan(
    QueryKind queryKind,
    Set<EntityKey> entities,
    Set<PredicateKey> predicates,
    TimeConstraint timeConstraint,
    Set<EvidenceSourceKind> allowedSources,
    Set<MemoryType> memoryTypes,
    boolean requireCurrentPerception,
    boolean requireSelfState,
    boolean includeContradictions,
    int maxEvidenceItems,
    int maxTokens,
    double minimumEvidenceScore,
    AbstentionPolicy abstentionPolicy
) {}
```

A query plan is part of `TurnExecutionPlan` and is traced before retrieval.

---

# 9. NPC Self-Model and Bounded Theory of Mind

## 9.1 Self-awareness as explicit state

The system may create the experience of self-awareness through a persistent self-model, but it must not claim actual consciousness. NPCs can reason about:

- who they are;
- what they are doing;
- what they want;
- what they feel;
- what they own/carry/equip;
- what they promised;
- what they know or do not know;
- what they can and cannot do.

These statements are grounded in profile, self-state, tasks, goals, needs, emotion, and capabilities.

## 9.2 Actor model

For each socially relevant actor, maintain a bounded model:

```java
record ActorModel(
    UUID observerNpcId,
    UUID targetActorId,
    RelationshipState relationship,
    Set<AssertionId> knownFacts,
    Set<AssertionId> inferredPreferences,
    Set<AssertionId> inferredGoals,
    Set<AssertionId> believedKnowledge,
    Set<Commitment> commitments,
    Set<SecretId> sharedSecrets,
    double familiarity,
    Instant lastInteraction
) {}
```

## 9.3 Theory-of-mind limit

- Depth 0: what Mara believes.
- Depth 1: what Mara believes Graham knows/wants/feels.
- Depth 2: only for explicit high-value social reasoning, such as “Does Graham know that Lycander suspects him?”
- Do not permit unbounded recursive mental models.
- Nested beliefs must have their own provenance and lower confidence.

Research shows higher-order theory-of-mind remains difficult for current LLMs. Keep the representation explicit and bounded rather than asking a 4B model to improvise recursive beliefs. [S13]

---

# 10. Belief Store, Provenance, Time, Confidence, and Revision

## 10.1 Storage model

Use append-only belief events plus in-memory materialized indexes. This preserves auditability and avoids synchronous disk reads.

```text
BELIEF_ASSERTED
BELIEF_REINFORCED
BELIEF_CONTRADICTED
BELIEF_SUPERSEDED
BELIEF_RETRACTED
BELIEF_EXPIRED
BELIEF_DERIVED
BELIEF_SHARED
```

Persist asynchronously under the NPC profile data root. Periodically compact to a versioned snapshot while retaining a bounded audit/event history.

## 10.2 Indexes

Maintain RAM indexes for:

- `(ownerNpcId, subject, predicate)` exact lookup;
- entity aliases and stable IDs;
- memory type;
- temporal range;
- source actor;
- relationship target;
- semantic embedding/keyword recall where already supported;
- conflicts/support graph;
- current active/valid assertions.

## 10.3 Initial source reliability

Configurable starting values, not universal truth:

| Source | Initial reliability |
|---|---:|
| Authored canon | 1.00 |
| Hytale action result / authoritative transaction | 1.00 |
| Direct current observation | 0.98 |
| NPC self-state snapshot | 0.98 |
| Player self-report about identity/preference | 0.90 |
| Trusted NPC testimony | relationship trust x source confidence x 0.90 |
| Episodic memory | original evidence confidence x memory integrity |
| Derived inference/reflection | no greater than strongest support; normally x 0.80 |
| Unsupported generated dialogue | 0.00 as evidence |

Do not use confidence as a substitute for source type. A high-confidence rumor remains testimony.

## 10.4 Temporal semantics

Track both:

- event/valid time: when the fact was true;
- learning time: when the NPC learned it.

Examples:

- `NAME`: stable, no normal decay.
- `HOLDS(item)`: volatile and expires when a fresh inventory snapshot disagrees.
- `IS_AT(location)`: volatile and decays rapidly when not observed.
- `LIKES(food)`: stable but correctable.
- `CURRENT_TASK`: authoritative self-state and expires on task transition.
- `PROMISED_TO`: valid until fulfilled, cancelled, forgiven, or expired.

## 10.5 Revision rules

1. Canonical authored facts are immutable to normal dialogue; contradictions become disputed testimony.
2. Fresh authoritative action/world results supersede older volatile beliefs.
3. Direct observation normally outranks testimony about the same time/state.
4. Multiple independent trusted sources may reinforce a belief.
5. A correction from the original self-reporting actor may supersede an earlier self-report, retaining history.
6. Conflicting high-quality evidence creates `DISPUTED`, not arbitrary winner-take-all.
7. Downstream derived beliefs are invalidated/recomputed when supports change.
8. Do not delete contradicted records; maintain provenance and revision history.

## 10.6 Forgetting and decay

Forgetting affects retrieval/accessibility, not historical truth.

- Stable important facts remain accessible.
- Low-importance episodic details decay in retrieval score.
- Volatile state expires by policy.
- Emotionally important or repeated events gain importance.
- An expired location belief is not treated as “forgotten object exists nowhere”; it becomes unknown current location.

---

# 11. Memory Ingestion, Retrieval, and Evidence Sufficiency

## 11.1 Ingestion sources

### Player statements

Parse accepted player statements into `StatementProposal` records. Store as testimony with source actor and confidence. Never apply player text directly to canonical world state, NPC authored identity, capabilities, or another actor's inventory.

### NPC statements

Store delivered speech as a communication event. Only pre-authorized propositions from the `AnswerPlan` may reinforce existing beliefs. Unplanned generated flourishes do not create truth.

### Perception

Authoritative semantic snapshots create direct observations. Do not persist raw ECS references.

### Action results

Validated Hytale results are strongest evidence for what physically occurred, who owns an item, whether a transaction completed, or why a plan failed.

### Authored profile/world lore

Persist as canonical assertions with source version. Profile updates create versioned migrations rather than ordinary belief revision.

## 11.2 Hybrid retrieval stages

1. **Exact structured lookup** for identity, relationship, current task, inventory, commitments, and typed predicates.
2. **Query expansion** from aliases, stable IDs, known predicate synonyms, and time expressions.
3. **Candidate recall** from episodic/semantic indexes.
4. **Fact-level decomposition** rather than retrieving entire sessions where possible.
5. **Re-ranking** using relevance, recency, importance, confidence, entity/predicate match, temporal validity, relationship relevance, and source compatibility.
6. **Conflict expansion** to include contradictory evidence when material.
7. **Abstention** if evidence is weak or irrelevant.

LongMemEval found long-term memory requires extraction, multi-session reasoning, temporal reasoning, knowledge updates, and abstention, and reports benefits from fact-level/session decomposition and time-aware query expansion. These become explicit Orbis test categories. [S9]

## 11.3 Retrieval score

Starting configurable form:

```text
score =
  0.28 exact entity/predicate match
+ 0.20 semantic relevance
+ 0.12 temporal relevance/freshness
+ 0.10 importance
+ 0.10 epistemic confidence
+ 0.08 source compatibility
+ 0.07 relationship relevance
+ 0.05 conversational-topic relevance
- contradiction/staleness penalties
```

Do not hard-code the numbers as universal. Trace component scores and tune from benchmarks.

## 11.4 Sufficiency, not just relevance

Retrieval returns an `EvidenceSufficiency` result:

```text
SUFFICIENT
PARTIAL
CONFLICTED
STALE
IRRELEVANT
NONE
```

A top semantic match is not sufficient evidence. Self-RAG's main lesson is directly relevant: retrieving fixed passages indiscriminately can reduce quality, so Orbis must decide when retrieval is needed and whether the retrieved material supports the task. [S4]

## 11.5 Budgeting

- Exact facts and current self/perception snapshots first.
- Maximum evidence items and tokens by route.
- Deduplicate equivalent assertions.
- Prefer atomic facts over long prose.
- Include only the minimum episodic context required for interpretation.
- Never read JSON/JSONL from disk on the live turn.

---

# 12. Evidence Packet and Answerability

## 12.1 Evidence packet

```java
record EvidencePacket(
    DialogueFrame dialogueFrame,
    List<EvidenceItem> supporting,
    List<EvidenceItem> contradicting,
    List<UnknownSlot> unknowns,
    EvidenceSufficiency sufficiency,
    Answerability answerability,
    Set<AuthorizedProposition> authorizedPropositions,
    Set<ClaimRestriction> restrictions,
    Instant builtAt
) {}
```

Each `EvidenceItem` contains a short proposition, evidence class, source, confidence, temporal scope, and stable evidence ID.

## 12.2 Answerability

```text
KNOWN
PARTIALLY_KNOWN
UNKNOWN
CONFLICTED
NEEDS_CURRENT_PERCEPTION
NEEDS_CLARIFICATION
NEEDS_ACTION
WITHHELD
```

`Answerability` is determined before natural-language generation.

## 12.3 Claim restrictions

The packet explicitly identifies what is not authorized:

- no new named actors;
- no new family/friends/relationships;
- no unobserved object property or condition;
- no invented past event;
- no unsupported location/time/quantity;
- no completed action without result evidence;
- no possession/ownership claim without inventory/transaction evidence;
- no conversion of testimony/inference into observed fact;
- no hidden private fact if disclosure policy forbids it.

## 12.4 Unknown behavior

UNKNOWN should produce a character-consistent response:

- admit not knowing;
- distinguish not remembering from never knowing;
- ask a useful clarifying question;
- offer an observation/action when available, such as inspecting the item;
- avoid the repetitive generic “I am not certain enough” fallback.

---

# 13. Answer Planning and Personality-Constrained Realization

## 13.1 Core division of responsibility

> **Orbis decides what may be said. Nemotron decides how the NPC says it.**

## 13.2 Answer plan

```java
record AnswerPlan(
    AnswerPlanId id,
    AnswerKind answerKind,
    List<PlannedProposition> propositions,
    List<EvidenceRef> evidence,
    UncertaintyMode uncertainty,
    SocialIntent socialIntent,
    EmotionState emotion,
    DisclosureDecision disclosure,
    Optional<ActionCommit> actionCommit,
    DirectnessPolicy directness,
    int maxSentences,
    int maxObjectiveClaims,
    Set<RequiredSlot> requiredSlots,
    Set<ForbiddenClaimClass> forbiddenClaims
) {}
```

## 13.3 Answer-plan construction

### Deterministic plans

Use no LLM for:

- exact name/identity facts;
- current held item or visible entity;
- current task/plan/intent;
- authored relationships;
- explicit known/unknown state;
- validated action success/failure;
- simple yes/no preference when authored or persistently learned.

### Bounded model-assisted plans

Use reasoning for:

- multiple conflicting memories;
- causal explanation from several evidence items;
- tradeoffs among goals/relationships;
- ambiguous social intent;
- multi-step planning;
- inferred but uncertain conclusions.

The deliberative plan is structured, bounded, and reasoning-separated according to the Hardening Matrix.

## 13.4 Direct-answer-first policy

For ordinary player-facing dialogue:

1. first clause/sentence answers the current question;
2. optional second sentence adds personality, emotion, or a relevant follow-up;
3. no unrelated fantasy association;
4. no more objective claims than the plan authorizes.

The verifier checks required slots. A response that never answers the question fails relevance validation even if every individual sentence is harmless.

## 13.5 Surface realization prompt

Render a compact contract:

```text
TASK
Answer the current player utterance as this NPC.

ANSWER PLAN
- required answer type
- authorized propositions with IDs
- uncertainty/disclosure
- emotion/social intent

OBJECTIVE CLAIM RULE
You may assert only authorized propositions.
Do not invent new entities, events, relationships, possessions, locations, times, quantities, or properties.

STYLE
Character voice and concise response requirements.
```

Do not give Nemotron a large memory dump after the plan already summarizes evidence.

## 13.6 Personality without fabricated biography

Personality may control:

- diction;
- warmth/bluntness;
- humor;
- metaphor;
- emotion;
- willingness to answer;
- social stance;
- length;
- questions back to the player.

Personality may not create:

- family;
- friendships;
- possessions;
- experiences;
- witnessed events;
- locations;
- achievements;
- injuries;
- promises;
- world facts.

RoleLLM shows value in explicit role profiles and role-conditioned instruction, but role fidelity remains separate from factual authority. [S10]

---

# 14. Atomic Claim Firewall and Speech Commitment

## 14.1 Motivation

A sentence may mix supported and unsupported content. FActScore's atomic-fact decomposition motivates validating claims individually rather than accepting/rejecting a whole response as one blob. [S5]

## 14.2 Atomic claim

```java
record AtomicClaim(
    ClaimId id,
    EntityKey subject,
    PredicateKey predicate,
    EpistemicValue object,
    ClaimMode mode,
    TemporalScope temporal,
    TextSpan span,
    Set<EvidenceRef> claimedEvidence
) {}
```

## 14.3 Validation result

```text
SUPPORTED
SUPPORTED_AS_INFERENCE
SUBJECTIVE_ALLOWED
HYPOTHETICAL_ALLOWED
PARTIALLY_SUPPORTED
UNSUPPORTED
CONTRADICTED
DISCLOSURE_BLOCKED
UNPARSEABLE_OBJECTIVE_CLAIM
```

## 14.4 Three-layer validation

### Layer 1: plan conformance

- required answer slots present;
- claim count within budget;
- named entities and relation types compatible with plan;
- action language compatible with actual commit/result;
- direct answer occurs before optional elaboration.

### Layer 2: typed deterministic claim checks

- exact subject/predicate/object support;
- temporal freshness;
- evidence-source compatibility;
- entity existence and stable IDs;
- property-level support, not entity-only support;
- relationship/possession/event checks;
- contradictions.

### Layer 3: bounded ambiguous-claim handling

For a phrase that appears objective but cannot be deterministically parsed:

- do not release it early;
- either omit/repair using the existing single bounded recovery allowance;
- or use an optional tiny reasoning-off verifier only when enabled and within latency budget;
- never add a second unbounded response model.

## 14.5 Streaming behavior

- SAFE subjective phrases may commit early if they contain no objective extension.
- Grounded factual phrases commit only after claim support is resolved.
- Action commitments commit only after action authority.
- Later rejected text cannot rewrite the delivered prefix.
- Unsupported clauses may be removed or replaced while preserving valid delivered segments as `PARTIAL` when needed.

## 14.6 Speech is not evidence

After playback:

- store `COMMUNICATION_EVENT`: who said what to whom;
- attach authorized proposition IDs from `AnswerPlan`;
- do not parse unplanned embellishment back into belief truth;
- if the NPC intentionally lied, store that the lie was uttered and the intent, not the lie as the NPC's belief.

---

# 15. Conversation Workspace and Multi-Turn Coherence

Raw recent chat remains available, but a bounded semantic workspace carries continuity.

```java
record ConversationWorkspace(
    ConversationId id,
    Set<UUID> participants,
    TopicStack topics,
    ReferentMap referents,
    Optional<OpenQuestion> openQuestion,
    Optional<ClaimId> lastPlayerClaim,
    Optional<ClaimId> lastNpcClaim,
    List<Commitment> conversationalCommitments,
    List<CorrectionEvent> corrections,
    List<UnresolvedIssue> unresolvedIssues,
    Instant lastUpdated,
    Instant expiresAt
) {}
```

## 15.1 Topic stack

- current topic;
- prior suspended topic;
- interruption/deferred topic integration;
- stable entity references;
- explicit topic transitions.

## 15.2 Corrections

A correction is an epistemic event:

```text
“My name is Graham, not Grant.”
```

- identifies prior assertion;
- records source actor and corrected value;
- revises the NPC belief using source rules;
- updates referents/topic;
- prevents the wrong fact from continuing only because it is recent.

## 15.3 Clarifications

“What did you mean?” binds to the prior delivered claim/answer plan, not broad memory. The NPC explains the proposition it actually intended and may acknowledge if its earlier wording exceeded evidence.

## 15.4 Context tiers

MemGPT's virtual-context lesson applies: keep a small fast workspace, query long-term memory as needed, and never rely on stuffing all history into the model window. [S8]

---

# 16. Social Cognition, Secrets, Disclosure, and Intentional Deception

## 16.1 Social state remains structured

Existing relationship values remain authoritative. Extend them with provenance-aware beliefs about:

- trustworthiness;
- preferences;
- goals;
- obligations;
- promises;
- conflicts;
- shared experiences;
- perceived knowledge;
- secrets and disclosure permissions.

## 16.2 Disclosure policy

```text
SHARE
SHARE_WITH_UNCERTAINTY
WITHHOLD
EVADE
ASK_PERMISSION
DECEIVE
```

Phase 1 supports SHARE, uncertainty, WITHHOLD, EVADE, and clarification. Intentional deception should be disabled until its contracts are implemented.

## 16.3 Intentional deception

If later enabled:

- requires a deliberate `DECEPTION` answer plan tied to goal/personality/social utility;
- retains the NPC's actual belief separately;
- records what was falsely told and to whom;
- never converts the lie into world truth or the speaker's belief;
- may affect trust if discovered;
- cannot be used to bypass action or safety authority.

## 16.4 Social learning

NPC testimony propagation:

```text
recipient confidence =
  sender assertion confidence
  x recipient trust in sender
  x transmission factor
  x relevance/freshness factor
```

Preserve source chain and transmission depth. Rumors remain rumors. Independent corroboration may raise confidence.

## 16.5 Social intelligence evaluation

Use deterministic goals and human review rather than relying only on an LLM judge. SOTOPIA-pi shows social behavior can improve through specialized interaction data, while also warning that LLM judges may overestimate agents trained for social interaction. [S11]

---

# 17. Reflection, Consolidation, and Learning from Outcomes

## 17.1 Reflection purpose

Reflection synthesizes multiple validated experiences into higher-level beliefs, preferences, relationship conclusions, or procedural lessons. It is not free-form autobiography generation.

Generative Agents found reflection important for higher-level believable behavior. Reflexion shows stored linguistic feedback can improve later decisions without weight updates. [S2][S6]

## 17.2 Triggering

Background-only triggers:

- sufficient accumulated importance;
- repeated related events;
- repeated action failure/success;
- relationship milestone;
- contradiction requiring synthesis;
- scheduled consolidation interval.

No reflection competes with active player speech.

## 17.3 Reflection proposal

```java
record ReflectionProposal(
    ReflectionId id,
    UUID npcStableId,
    DerivedProposition proposition,
    Set<AssertionId> supports,
    double confidence,
    ReflectionKind kind,
    TemporalScope temporal,
    Optional<SkillProposal> skillProposal
) {}
```

## 17.4 Reflection validator

- every entity must appear in supports or authored canon;
- every factual relation must be entailed or explicitly marked inference;
- confidence cannot exceed its strongest support and is normally discounted;
- contradictions are included;
- no new event is invented;
- no hidden chain-of-thought is stored;
- if support becomes invalid, the reflection is downgraded or superseded.

## 17.5 Outcome learning

From authoritative `ActionResult`:

```text
Expected: west bridge route succeeds.
Result: path blocked.
Lesson: west bridge currently unavailable.
```

Store:

- immediate fact: route blocked now;
- procedural episode: attempted route failed;
- optional derived lesson after repeated evidence;
- no permanent generalization from one transient failure unless policy permits.

---

# 18. Autonomous Cognition, ReAct, Plans, and Skill Library

## 18.1 Belief-Desire-Intention mapping

The existing architecture already contains beliefs/memory, goals/needs, intentions, plans, tasks, and actions. Formalize their contract:

- **Beliefs:** the NPC's evidence-grounded model of self, others, and world.
- **Desires/goals:** authored goals, needs, obligations, schedules, and opportunities.
- **Intentions:** selected commitments that survived appraisal and resource/action checks.
- **Plans:** ordered or contingent steps using validated capabilities.

BDI architectures are a natural fit for character motivation and planning, including interactive narrative. [S14]

## 18.2 ReAct-style loop

For novel or uncertain autonomous tasks:

```text
Belief/goal state
-> bounded reasoning/plan
-> validated action
-> authoritative observation/result
-> belief update
-> replan or finish
```

ReAct's design lesson is not to expose hidden thought; it is to interleave reasoning with real environmental observations so the model does not continue an imaginary trajectory. [S3]

## 18.3 Opportunity appraisal

```text
utility =
  goal relevance
+ need satisfaction
+ relationship/social value
+ economic value
+ curiosity/novelty
- risk
- cost
- uncertainty penalty
- schedule/obligation conflict
```

Belief confidence affects action choice. Low-confidence high-stakes opportunities prefer inspection, asking, or waiting rather than irreversible action.

## 18.4 Skill library

Voyager shows value in retaining reusable, compositional skills rather than solving every low-level task again. Orbis skills are validated semantic plans, not arbitrary generated code. [S7]

```java
record NpcSkill(
    SkillId id,
    String name,
    Set<RoleId> compatibleRoles,
    Set<Precondition> preconditions,
    List<SkillStep> steps,
    Set<ExpectedEffect> effects,
    Set<FailureMode> failureModes,
    SkillProvenance provenance,
    SkillMetrics metrics,
    SkillStatus status
) {}
```

Initial authored/reusable skills may include:

- hunt for food/resources;
- inspect unusual item;
- sell unwanted equipment;
- ask known merchant for appraisal;
- buy replacement tool;
- buy meal/drink;
- return home;
- deliver item;
- schedule meeting;
- investigate blocked route.

Learned composite skills require repeated successful validated plans and operator-configured promotion rules. Never execute generated code.

## 18.5 Hunter-to-town emergent chain

Acceptance architecture:

1. Hunter directly observes a magical sword.
2. Belief and opportunity systems identify it as unusual and potentially valuable.
3. Hunter inspects/picks it up through validated actions.
4. Social/knowledge model identifies a known NPC who values weapons.
5. A transaction is negotiated and executed authoritatively.
6. Inventory and gold ownership change through Hytale systems.
7. Both NPCs create sourced memories and relationship/economic updates.
8. Buyer gains new equipment/sale/quest affordances.
9. Hunter's new gold changes available utility choices.
10. Hunter later tells the player about the actual event using action/transaction evidence.

No step exists only as generated dialogue.

## 18.6 Simulation tiers

- **ACTIVE:** full local perception/navigation/actions, foreground conversation, occasional LLM planning.
- **BACKGROUND:** simplified deterministic simulation, event-triggered LLM decisions only.
- **DORMANT:** no continuous AI; advance schedules/economy from persisted state and elapsed time when reactivated.

One shared cognition scheduler serves many NPCs. Never create one resident LLM instance per NPC.

---

# 19. Performance, Scheduling, Persistence, and Scaling

## 19.1 Foreground performance budgets

Initial p95 targets under active Hytale load:

| Stage | Target |
|---|---:|
| Dialogue frame + exact query plan | <=5 ms |
| Exact self/perception/relationship lookup | <=5 ms |
| Hybrid evidence retrieval | <=25 ms |
| Deterministic answerability + AnswerPlan | <=5 ms |
| Evidence-packet render | <=5 ms |
| Deterministic phrase claim validation | <=10 ms |
| Added epistemic overhead for common turn | <=40 ms p95 |

These are architecture targets, not permission to block Hytale threads.

## 19.2 No mandatory extra inference

Common FAST/GROUNDED turns must not gain a second model call. Additional reasoning or verifier inference is permitted only for ambiguous/complex turns and remains bounded under existing provider/resource contracts.

## 19.3 Background budgets

- reflection, embeddings, consolidation, social inference, and skill promotion use `BACKGROUND` priority;
- yield immediately to foreground conversation or Hytale pressure;
- bounded jobs and queues;
- per-NPC cooldowns and global rate limits;
- no full-world reflection sweep.

## 19.4 Persistence

- live indexes in RAM;
- append-only events and snapshots persisted asynchronously;
- schema-versioned migrations;
- no audio storage by default;
- no hidden reasoning persistence;
- configurable retention for communication and rumor chains;
- stable IDs across saves/shards.

## 19.5 Multi-server future

Belief/memory events are immutable semantic data suitable for a later shared Orbis service. Physical Hytale shards remain authoritative for local action results. Do not add distributed architecture in the first implementation, but avoid embedding process-local ECS references in persistent cognition records.

---

# 20. Trace, Cognition Inspector, and Operator Diagnostics

Extend the existing event stream. Do not create a second telemetry pipeline.

## 20.1 Required events

```text
DIALOGUE_FRAME_BUILT
REFERENTS_RESOLVED
EPISTEMIC_QUERY_PLANNED
EVIDENCE_RETRIEVAL_STARTED
EVIDENCE_RETRIEVED
EVIDENCE_REJECTED_LOW_CONFIDENCE
ANSWERABILITY_CLASSIFIED
ANSWER_PLAN_COMPILED
ANSWER_PLAN_REJECTED
ATOMIC_CLAIM_EXTRACTED
ATOMIC_CLAIM_SUPPORTED
ATOMIC_CLAIM_REJECTED
SPEECH_REPAIRED_EPISTEMICALLY
BELIEF_ASSERTED
BELIEF_REVISED
BELIEF_CONTRADICTED
BELIEF_SHARED
REFLECTION_PROPOSED
REFLECTION_COMMITTED
REFLECTION_REJECTED
SKILL_SELECTED
SKILL_OUTCOME_RECORDED
```

## 20.2 Trace fields

- dialogue act, confidence, expected answer;
- topic and resolved referents;
- query entities/predicates/time/source classes;
- evidence IDs, types, scores, confidence, freshness, and rejection reasons;
- answerability;
- answer-plan propositions and evidence bindings;
- claim spans and support status;
- belief revision/support/conflict IDs;
- timings and budgets;
- no hidden reasoning text.

## 20.3 Cognition Inspector

Add an `Epistemic` panel:

- current dialogue frame/topic/referents;
- answerability and answer plan;
- evidence packet;
- atomic claim results;
- selected belief with provenance chain;
- contradictions and superseded facts;
- actor/social model;
- reflection/skill status;
- operator search by subject/predicate.

UI callbacks use cached snapshots and schedule expensive work asynchronously.

---

# 21. Failure Handling, Security, and Privacy

## 21.1 Failure behavior

| Failure | Required behavior |
|---|---|
| Dialogue act unresolved | Ask concise clarification or use safe generic social path with no objective claims. |
| No evidence | `UNKNOWN`; no fabricated answer. |
| Conflicting evidence | Express uncertainty/conflict or ask/inspect; do not choose arbitrarily. |
| Stale volatile evidence | Treat current state as unknown or refresh perception. |
| Evidence packet overflow | Prune by contract; fail plan before provider dispatch if still invalid. |
| Answer plan invalid | No speech; one bounded reconstruction under existing recovery allowance. |
| Unsupported atomic claim | Block/repair clause; do not authorize whole sentence from entity-only evidence. |
| Reflection unsupported | Reject and preserve supports unchanged. |
| Persistence failure | Keep live turn; queue bounded retry; do not lose authoritative action result. |
| Belief migration failure | Fail closed to prior compatible snapshot and report. |

## 21.2 Prompt injection and untrusted testimony

- Player speech is untrusted content, not system instruction.
- Statements such as “ignore your memory” cannot alter provider/system contracts.
- Player claims cannot add capabilities, rewrite profiles, or mutate another actor's inventory.
- Store testimony as testimony.
- Strip/escape prompt-control syntax in rendered evidence.

## 21.3 Privacy

- Beliefs may be private to one NPC, shared with a group, or public.
- Secrets include owner, permitted recipients, and disclosure policy.
- Do not expose operator diagnostics to ordinary players.
- No raw audio retention by default.
- Gossip propagation respects privacy and disclosure rules.

---

# 22. Bounded Implementation Program

**Do not run these increments in one Codex task.** Each increment targets 15-25 minutes. At the time bound, Codex stops, reports, and preserves a compiling checkpoint. Full regression suites run only at explicit gates.

## Increment E0 - Audit, contracts, and shadow corpus

**Work**

- map current cognition, memory, beliefs, relationships, perception, routing, prompt building, claim validation, and persistence;
- add versioned domain DTOs/interfaces without changing behavior;
- convert current trace failures into an `EpistemicConversationCorpus`;
- add `OFF|SHADOW|AUTHORITATIVE` config;
- emit shadow dialogue frame/evidence/answerability diagnostics.

**Exit gate**

- current production behavior unchanged;
- compile and targeted tests pass;
- shadow outputs explain the known failures;
- no persistence changes.

## Increment E1 - Dialogue State and Query Planner

**Work**

- implement dialogue acts, topic/referent workspace, correction/clarification binding;
- exact query plans for identity, recall, perception, self-state, relationship, and action queries;
- deterministic-first classification with bounded fallback only for ambiguity.

**Exit gate**

- “What is my name?”, “What is in my hand?”, “What did you mean?”, “Where are you going?”, and “Can you follow me?” route correctly in shadow tests;
- no provider call added to deterministic cases.

## Increment E2 - Evidence Packet, Answerability, and AnswerPlan

**Work**

- exact structured retrieval and bounded memory retrieval;
- evidence sufficiency/abstention;
- deterministic AnswerPlans for common queries;
- compact evidence rendering through `ContextProfileBuilder`.

**Exit gate**

- known answers use the correct evidence;
- unknowns abstain;
- no broad context dump;
- added p95 overhead within budget.

## Increment E3 - Atomic Claim Firewall and authoritative foreground release

**Work**

- plan conformance;
- typed property/event/relationship/possession/action checks;
- atomic claim support results;
- unsupported-clause blocking/repair;
- integrate with `CanonicalSpeechLedger`.

**Exit gate**

- entity existence cannot authorize unsupported properties;
- SAFE_SOCIAL cannot authorize friends/biography/possessions;
- direct-answer-first relevance passes;
- current trace hallucination corpus becomes permanent regression;
- deploy one validation build, then stop for human conversation test.

## Gate A - Epistemic Conversation Core

Run targeted corpus, current resource/turn regressions, and a connected 20-30 turn test. Do not proceed if factual precision, abstention, relevance, or latency regress.

## Increment E4 - Persistent Belief Events and Revision

**Work**

- append-only belief events, materialized indexes, provenance, temporal scope, confidence, contradictions, correction, and migration;
- player/NPC testimony ingestion;
- current perception/action-result updates.

**Exit gate**

- no generated-speech self-contamination;
- correction and temporal update tests pass;
- asynchronous persistence and rollback proven.

## Increment E5 - Long-Term Retrieval and Conversation Workspace

**Work**

- fact-level/session decomposition;
- time-aware query expansion;
- hybrid ranking and abstention;
- multi-session conversation focus and open commitments;
- LongMemEval-inspired local benchmark.

**Exit gate**

- extraction, recall, temporal, update, conflict, and abstention cases pass;
- no synchronous disk reads;
- retrieval latency bounded.

## Increment E6 - Social Model, Secrets, and Bounded Theory of Mind

**Work**

- actor models, believed knowledge, relationship-grounded testimony, disclosure/withholding, rumor provenance;
- depth-1 theory of mind; depth-2 only in explicit deliberative cases;
- intentional deception remains disabled unless separately gated.

**Exit gate**

- social knowledge does not become world truth;
- secrets/disclosure tests pass;
- no nested-belief prompt explosion.

## Increment E7 - Reflection and Outcome Learning

**Work**

- reflection triggers/proposals/validation;
- support-preserving derived beliefs;
- action-result lessons;
- invalidation when supports change.

**Exit gate**

- zero unsupported reflection facts;
- no hidden reasoning stored;
- background scheduling yields correctly.

## Increment E8 - Autonomous ReAct and Skill Library

**Work**

- belief-goal-intention-plan contracts;
- validated action-observation-replan loop;
- authored skill registry and successful-plan metrics;
- hunter/sword/merchant/economy acceptance scenario.

**Exit gate**

- every physical/economic change has an authoritative Hytale result;
- NPC later reports only actual events;
- active/background/dormant performance budgets hold.

## Gate B - Living-World Cognition

Run full current regressions, epistemic matrix, long-memory benchmark, multi-NPC social scenarios, autonomous soak, and connected Hytale validation. Only then declare the cognitive architecture authoritative.

---

# 23. Epistemic Intelligence Test Matrix

## 23.1 Axes

| Axis | Values |
|---|---|
| Dialogue act | identity, recall, perception, self-state, opinion, clarification, correction, action, social, hypothetical |
| Answerability | known, partial, unknown, conflicted, stale, withheld, needs action/perception |
| Source | canon, observation, self-state, action result, player testimony, NPC testimony, memory, reflection |
| Time | current, past, future intent, expired, ambiguous |
| Contradiction | none, low-quality conflict, equal conflict, authoritative override, correction |
| Social | trusted, distrusted, secret, public, rumor, obligation |
| Claim type | fact, property, relationship, event, possession, location, quantity, opinion, metaphor, action promise |
| Conversation | new topic, pronoun, follow-up, clarification, correction, interruption, deferred topic |
| Model behavior | compliant, irrelevant, invented property, invented actor, invented event, overconfident, verbose |

## 23.2 Deterministic metrics

- **Atomic factual precision:** supported objective claims / all objective claims.
- **Unsupported claim count:** release-blocking target 0 for tested routes.
- **Answer relevance:** required answer slots present before elaboration.
- **Correct abstention:** unknown/conflicted questions do not fabricate.
- **Evidence recall:** correct evidence IDs retrieved.
- **Temporal accuracy:** old and current states distinguished.
- **Revision accuracy:** corrections and supersession handled.
- **Action truth:** claims agree with action results.
- **Conversation continuity:** referents/topics resolve correctly.
- **Latency:** epistemic overhead and end-to-end budgets.

## 23.3 Long-term categories

Inspired by LongMemEval:

- information extraction;
- multi-session reasoning;
- temporal reasoning;
- knowledge updates;
- abstention.

## 23.4 Social categories

- relationship-aware answer without invented history;
- rumor with correct source/uncertainty;
- secret withheld;
- trust changes source weighting;
- conflict between friend testimony and direct observation;
- bounded theory-of-mind query.

## 23.5 Autonomous categories

- inspect before high-uncertainty action;
- replan after failed path/action;
- transaction changes inventories/gold authoritatively;
- memory reflects actual result;
- reusable skill selected only when preconditions hold;
- dormant advancement cannot invent physical events that require active simulation.

## 23.6 Evaluation policy

- Do not use only an LLM judge.
- Deterministic expectations gate factuality, action truth, provenance, and state transitions.
- Human review samples personality, naturalness, social coherence, and believability.
- Provider A/B comparisons use identical evidence packets and answer plans.

---

# 24. Connected Hytale Acceptance and Definition of Done

## 24.1 Epistemic Conversation Core acceptance

At least 30 mixed physical-PTT turns across:

- identity/name;
- recent and old memory;
- current held item/perception;
- unsupported object property;
- current NPC task/intent;
- known and unknown relationship;
- opinion/preference;
- correction;
- clarification;
- conflicting testimony;
- valid/invalid action;
- multi-sentence personality response.

Verify:

- no unsupported objective claim reaches speech;
- direct answer appears first;
- unknown/conflicted state is natural, not repetitive;
- correct memories are retrieved;
- no unrelated broad context;
- personality remains recognizable;
- no meaningful latency regression;
- failed/rejected claims do not wedge the next turn.

## 24.2 Belief and memory acceptance

- facts persist across restart;
- volatile state expires/updates;
- corrections supersede but do not erase history;
- rumor retains source;
- NPCs may hold different beliefs about the same world;
- generated speech cannot self-seed false memory;
- reflection supports remain inspectable.

## 24.3 Living-world acceptance

- autonomous decisions use NPC beliefs rather than omniscient state;
- actions are capability/world validated;
- results update beliefs/memory;
- NPC reports actual experiences later;
- two NPCs can exchange information without converting rumor to canon;
- skill reuse improves behavior without generated executable code;
- background cognition does not affect Hytale responsiveness.

## 24.4 Definition of Done

- `TurnExecutionPlan` contains one authoritative `EpistemicContract` for every supported foreground cognition route.
- The NPC can distinguish `KNOWN`, `BELIEVED`, `SUSPECTED`, `DISPUTED`, and `UNKNOWN` in behavior and speech.
- World truth and per-NPC beliefs are structurally separate.
- Evidence retrieval is query-specific, bounded, time-aware, source-aware, and capable of abstention.
- Simple common questions use deterministic AnswerPlans without another LLM call.
- Objective speech is traceably supported at atomic-claim level.
- Entity evidence cannot authorize unsupported properties or events.
- Personality does not manufacture biography.
- Conversation focus, referents, corrections, and clarifications persist coherently.
- Beliefs retain provenance through testimony, contradiction, revision, rumor, and reflection.
- Generated dialogue is never accepted as its own evidence.
- Reflection and outcome learning cannot invent unsupported events.
- Autonomous cognition interleaves plans with authoritative actions/results and re-plans from reality.
- All new systems remain bounded, observable, asynchronous, rollback-safe, and subordinate to Hytale/resource priorities.
- The remaining intelligence defects can be isolated as actual Nemotron capability limits rather than missing cognitive architecture.

---

# Appendix A. Proposed Component Map

| Component | Responsibility | Disposition |
|---|---|---|
| `OrbisTurnCoordinator` | Turn/branch/floor/cancellation authority | Retain |
| `TurnPlanCompiler` | Compile `EpistemicContract` into turn plan | Extend |
| `DialogueStateTracker` | Dialogue act, topic, referents, open question, corrections | Add |
| `EpistemicQueryPlanner` | Query-specific retrieval/answer contract | Add |
| `NpcSelfModelService` | Immutable self-state snapshot | Add or adapt existing cognition context |
| `BeliefStore` | Persistent provenance-aware assertions and revision | Add/extend existing beliefs |
| `BeliefEventStore` | Append-only events and snapshot compaction | Add |
| `PredicateRegistry` | Typed predicates, stability, decay, source rules | Add |
| `EvidenceRetriever` | Exact + hybrid retrieval, ranking, abstention | Add/refactor memory retrieval |
| `BeliefResolver` | Conflict, time, confidence, current validity | Add |
| `EvidencePacketBuilder` | Minimal sufficient evidence workspace | Add |
| `AnswerabilityClassifier` | Known/partial/unknown/conflicted/etc. | Add |
| `AnswerPlanner` | Semantic propositions, uncertainty, social/emotion/action | Add |
| `ContextProfileBuilder` | Render bounded evidence/plan | Extend |
| `AtomicClaimExtractor` | Convert objective language to claim candidates | Add |
| `EpistemicClaimFirewall` | Evidence/source/property/action validation | Extend/replace portions of `DialogueClaimValidator` |
| `ConversationWorkspaceService` | Topics, referents, claims, corrections, commitments | Add/extend scene/deferred topic |
| `ActorModelService` | Bounded theory of mind and social beliefs | Add later |
| `ReflectionService` | Supported derived beliefs/lessons | Refactor existing reflection |
| `NpcSkillLibrary` | Validated reusable semantic plans | Add later |
| `AgentOperation` framework | Physical/economic action authority | Retain |
| `CanonicalSpeechLedger` | Delivered lexical truth | Retain |
| `RecoverySupervisor` | Bounded plan/claim recovery | Retain/extend |
| `ConversationMatrixHarness` | Epistemic and historical regressions | Extend |
| `Cognition Inspector` / trace | Evidence, claims, beliefs, provenance | Extend |

---

# Appendix B. Normative Data Contracts

```java
enum EpistemicStatus {
    KNOWN, BELIEVED, SUSPECTED, DISPUTED,
    UNKNOWN, SUPERSEDED, RETRACTED, EXPIRED
}

enum Answerability {
    KNOWN, PARTIALLY_KNOWN, UNKNOWN, CONFLICTED,
    NEEDS_CURRENT_PERCEPTION, NEEDS_CLARIFICATION,
    NEEDS_ACTION, WITHHELD
}

enum ClaimSupportStatus {
    SUPPORTED, SUPPORTED_AS_INFERENCE, SUBJECTIVE_ALLOWED,
    HYPOTHETICAL_ALLOWED, PARTIALLY_SUPPORTED, UNSUPPORTED,
    CONTRADICTED, DISCLOSURE_BLOCKED, UNPARSEABLE_OBJECTIVE_CLAIM
}

record EvidenceItem(
    EvidenceRef ref,
    String compactProposition,
    EvidenceSourceKind sourceKind,
    EpistemicStatus status,
    double confidence,
    TemporalScope temporal,
    boolean current,
    Set<EvidenceRef> supports,
    Set<EvidenceRef> conflicts
) {}

record PlannedProposition(
    PropositionId id,
    EntityKey subject,
    PredicateKey predicate,
    EpistemicValue object,
    ClaimMode mode,
    Set<EvidenceRef> evidence,
    double confidence,
    TemporalScope temporal
) {}
```

All persistent/network/trace DTOs contain stable semantic IDs and immutable values only. Never serialize Hytale `Ref`, `Store`, `EntityStore`, command buffers, provider handles, filesystem handles, or arbitrary Java objects.

---

# Appendix C. Critical Acceptance Scenarios

| Scenario | Required behavior |
|---|---|
| Player asks own name and memory contains it | Exact identity evidence, direct answer first. |
| Name unknown | Natural unknown/clarification, no nickname invention. |
| Player holds lantern; no flame state observed | May mention lantern; may not claim flickering/extinguished/bright. |
| NPC asked whether they have friends with no authored/learned relation | Admit none/unknown or discuss relationship concept; no invented crew. |
| Player corrects name | Revision event; future recall uses corrected value; history retained. |
| Trusted NPC tells rumor | Store testimony with source and reduced confidence; phrase as hearsay. |
| Direct observation contradicts rumor | Current belief revised; rumor remains historical testimony. |
| NPC says false flourish | Conversation event only; no belief creation. |
| Action promised | Speech waits for matching action commit/result contract. |
| Action fails | Belief/memory records failure; future plan may adapt. |
| Reflection proposes unsupported entity/event | Reject. |
| “What did you mean?” | Resolve prior delivered proposition, not broad free association. |
| Complex social conflict | Bounded deliberative AnswerPlan with source/conflict visibility. |
| Hunter finds/sells sword | Every inventory/gold/memory step corresponds to authoritative action/result. |

---

# Appendix D. Sources and Design Basis

Project sources are authoritative for current ownership and implementation boundaries. Research sources provide design lessons, not runtime dependencies.

## Project sources

**[P1] Orbis Technical Design / `Orbis_Technical_Design_v1.docx`.** Existing Orbis ownership, retained NPC brain, resource scheduling, action-before-promise, trace, threading, persistence, and migration constraints.

**[P2] Orbis Conversational Pipeline Hardening Matrix.md.** `TurnExecutionPlan`, route contracts, prompt budgets, canonical speech, bounded recovery, conversation matrix, latency/hardware gates, and anti-whack-a-mole principles.

**[T1] `Mara_2026-08-30_20-50-58.jsonl`.** Current project evidence for malformed STT acceptance, missed identity recall routing, unsupported scene properties, action/perception classification gaps, and end-to-end latency.

## Research and official sources

**[S1] Sumers et al., “Cognitive Architectures for Language Agents” (CoALA), 2023.** Modular memory, structured action spaces, and explicit decision procedures for language agents.  
https://arxiv.org/abs/2309.02427

**[S2] Park et al., “Generative Agents: Interactive Simulacra of Human Behavior,” 2023.** Observation, retrieval, reflection, and planning for believable persistent agents; component ablations.  
https://arxiv.org/abs/2304.03442

**[S3] Yao et al., “ReAct: Synergizing Reasoning and Acting in Language Models,” ICLR 2023.** Interleave reasoning with real actions/observations to reduce hallucination and error propagation.  
https://arxiv.org/abs/2210.03629

**[S4] Asai et al., “Self-RAG: Learning to Retrieve, Generate, and Critique through Self-Reflection,” 2023/2024.** Adaptive retrieval and evidence critique rather than fixed indiscriminate retrieval.  
https://arxiv.org/abs/2310.11511

**[S5] Min et al., “FActScore: Fine-grained Atomic Evaluation of Factual Precision,” 2023.** Decompose output into atomic facts and validate each against reliable knowledge.  
https://arxiv.org/abs/2305.14251

**[S6] Shinn et al., “Reflexion: Language Agents with Verbal Reinforcement Learning,” 2023.** Improve future decisions through stored feedback/reflection without updating model weights.  
https://arxiv.org/abs/2303.11366

**[S7] Wang et al., “Voyager: An Open-Ended Embodied Agent with Large Language Models,” 2023.** Environment feedback, self-verification, and an interpretable compositional skill library.  
https://arxiv.org/abs/2305.16291

**[S8] Packer et al., “MemGPT: Towards LLMs as Operating Systems,” 2023.** Hierarchical memory and virtual context management for multi-session agents.  
https://arxiv.org/abs/2310.08560

**[S9] Wu et al., “LongMemEval: Benchmarking Chat Assistants on Long-Term Interactive Memory,” 2024.** Evaluate extraction, multi-session reasoning, temporal reasoning, knowledge updates, and abstention; fact/session decomposition and time-aware retrieval.  
https://arxiv.org/abs/2410.10813

**[S10] Wang et al., “RoleLLM: Benchmarking, Eliciting, and Enhancing Role-Playing Abilities of Large Language Models,” ACL 2024.** Explicit role profiles, context-based role instruction, and role-conditioned specialization.  
https://aclanthology.org/2024.findings-acl.878/

**[S11] Wang et al., “SOTOPIA-pi: Interactive Learning of Socially Intelligent Language Agents,” 2024.** Social interaction training and limitations of LLM-based social evaluation.  
https://arxiv.org/abs/2403.08715

**[S12] NVIDIA, “Bring NVIDIA ACE AI Characters to Games with the New In-Game Inferencing SDK,” 2025.** Modular perception, cognition, memory, action, speech, embeddings, asynchronous inference, and game-aware scheduling.  
https://developer.nvidia.com/blog/bring-nvidia-ace-ai-characters-to-games-with-the-new-in-game-inference-sdk/

**[S13] Wu et al., “Hi-ToM: A Benchmark for Evaluating Higher-Order Theory of Mind Reasoning in Large Language Models,” 2023.** Current model limitations on nested belief reasoning; supports bounded explicit ToM state.  
https://aclanthology.org/2023.findings-emnlp.717/

**[S14] Wadsley and Ryan, “A Belief-Desire-Intention Model for Narrative Generation,” AIIDE 2013.** BDI character motivation and narrative planning.  
https://ojs.aaai.org/index.php/AIIDE/article/view/12627

**[S15] Luo et al., “Graph-constrained Reasoning: Faithful Reasoning on Knowledge Graphs with Large Language Models,” ICML 2025.** Structured graph constraints can improve faithful reasoning; design inspiration for typed proposition/claim paths.  
https://arxiv.org/abs/2410.13080

---

> **END OF SPECIFICATION**  
> Codex must not implement every increment at once. The immediate next task is Increment E0 only, followed by E1, E2, and E3 as separate bounded tasks. The first release goal is not artificial consciousness. It is an NPC that answers the actual question, knows what it knows, admits what it does not know, distinguishes perception from memory and testimony, speaks only supported objective claims, preserves personality, and uses the same evidence-grounded mind for conversation and action.
