# Immersive NPC Framework Architecture (R036)

## Authority boundary

The selected language-model provider receives detached facts and eligible OpenAI-compatible function
schemas. It never receives Hytale objects, entity refs, callbacks, packets, or
arbitrary command access. NpcActionRegistry resolves the requested ID,
rechecks profile capabilities and roles, runs a validator, then delegates to a
bounded deterministic executor. Unknown actions fail closed.

## Replaceable inference boundary

`AiServiceRouter` is the single inference entry point and independently routes
`SpeechToTextProvider`, `LanguageModelProvider`, and `TextToSpeechProvider` requests.
Providers expose asynchronous health, capabilities, cancellation, concurrency, execution
mode, hardware/backend descriptions, and inference/network latency. Default local adapters
wrap the existing dedicated Moonshine/Faster-Whisper and Chatterbox Turbo processes; the
existing OpenAI-compatible Nemotron client remains the language-model adapter. Explicit
`IMMERSIVE_HTTP` providers can run on localhost, a LAN worker, or a configured remote host.
Only immutable semantic/audio request data crosses the boundary—never ECS objects. Lost
workers complete futures exceptionally off the simulation thread, and fallback is disabled
unless explicitly configured. STT and TTS retain separate providers and queues.

## State boundaries

Stable NPC profile UUIDs remain the persistence key. Relationships, memories,
action results, and conversation sessions include the NPC UUID and player UUID;
every session also has its own UUID and in-flight guard. Concurrent players do
not share history or request ordering. NpcRuntimeRegistry separately maps the
stable profile UUID to a currently loaded Hytale entity UUID.

## Interaction and reasoning flow

Before context collection, `CognitiveContextRouter` assigns `DIRECT_FACT`,
`SIMPLE_SOCIAL`, `CONTEXTUAL_CONVERSATION`, or `COMPLEX_INTENT`. The resulting plan is
the allow-list for perception, relationships, memories, beliefs, tasks, plans, goals,
actions, and weather. Deterministic profile/relationship constraints cross a final
`AuthoritativeDialogueValidator` boundary before one canonical response is committed to
both display and TTS. Importance ranks memories only after semantic relevance passes its
gate; prior NPC-generated dialogue is retained as history but cannot become factual evidence.

Normal proximity flow:

1. NpcSocialAttentionService checks real distance and NPC line of sight.
2. At 4m Mara marks one SocialFocus target and enters LISTENING.
3. Normal chat selects one candidate by name, look direction, then distance.
4. NpcPerceptionService captures bounded entity/item facts and a cached semantic
   environment snapshot on the world thread.
5. The async local provider returns dialogue or a registered function call.
6. The registry validates fresh server state and deterministic game code acts.
7. The action result is stored as typed memory and sent into a follow-up prompt.
8. Dialogue streams privately through the owning World executor.
9. At 5m or lost visibility, focus and head tracking are released.

Approach alone never invokes the LLM. Navigation, waiting, meeting scheduling,
inventory mutation, and crafting stay deterministic.

## Grounded cognition convergence

R029 captures one immutable, response-scoped `CognitionContext` before generation.
It contains authoritative perception and game time, sourced player beliefs, relevant
typed memories and relationships, obligations, shared plans, active operations, and
only the action IDs eligible for that turn. Deterministic intent candidates prioritize
danger, obligations, relationships, active plans, missing information, direct requests,
information, and ambient response in that order. Unknown locations and unsupported
world facts select a question or refusal instead of becoming inferred state.

The chosen `GroundedNpcDecision` carries the response, conversation, speaker, source,
intent, emotion, optional paralinguistic event, registered action, spoken text, and
evidence IDs. Orbis branch epochs and `CancellationScope` reject superseded responses before action
execution. Immutable committed speech chunks are appended verbatim to chat and sent
to TTS in the same order; the final trace records that exact committed text. Sourced
beliefs persist in `persistence/sourced-beliefs.json`; Hytale's blackboard and sensor
caches remain transient runtime state.

## Semantic perception and latency

R030 makes the conversational boundary explicit: Hytale ECS/sensor capture produces an
immutable `RawPerceptionSnapshot`; `SemanticPerceptionNormalizer` deterministically
clusters it into a compact `SemanticWorldModel`; only that semantic model enters
`CognitionContext` prompt construction. Raw UUIDs, coordinates, asset IDs, block samples,
LOS metadata, and scan diagnostics remain available to the inspector and deterministic
action validators but never become ordinary dialogue context. Canonical chunks pass a
fail-closed debug-leakage validator before either display or TTS receives them.

`ResponseLatencyTraceStore` joins perception, cognition, prompt construction, Nemotron,
canonical chunking, Chatterbox conditioning/synthesis, Opus availability, and Hytale
voice submission by response ID. Budget warnings are configured independently in
`latency-budgets.json` and displayed beside semantic and raw inspector panels.

## Relationship-based location and guidance

R031 resolves named NPCs through `NpcProfileRegistry`, requires an explicit
NPC-to-NPC `RelationshipRecord`, then uses `NpcRuntimeRegistry` to address only the
target's authoritative loaded entity. A world-thread transform check enforces the
500-block bound without scanning blocks, chunks, or the whole entity store. Only a
`KnownNpcLocatorResult` containing semantic distance, direction, availability, and
navigation feasibility is attached to `SemanticWorldModel`.

The cognition ranking can report the location, offer guidance, accept guidance, or
report that the lookup failed. An accepted guide action claims the existing
`AgentOperationStore`, persists an active `SharedPlan`, and schedules a
`GUIDE_PLAYER_TO_NPC` task. The task refreshes the target transform every tick and lets
native NPC pathing traverse toward it. It never teleports or persists continuously
sampled coordinates. Target loss, range exit, player separation, expiry, and lack of
path progress terminate the operation and restore movement work suspended by the guide.

## Voice, hearing, and remote hailing

R032 treats player speech as one world event. The Hytale voice interceptor copies each
Opus frame once, a persistent Moonshine/Whisper worker produces one canonical transcript,
and `PlayerUtteranceAudienceService` resolves loaded Immersive NPC entity transforms in
the captured world. It does not scan blocks, chunks, or the full Hytale entity store.
The immutable `PlayerUtteranceEvent` contains every eligible listener; one deterministic
attention owner responds, while other listeners may receive bounded overheard memory.

`conversationListenRadius`, `remoteHailRadius`, and `npcSpeechMaxRadius` are separate
configuration values (5m, 15m, and 15m by default). Only a direct name/alias match can
extend reception into the hail range. The response prompt receives semantic distance,
direction, and authoritative surroundings, never raw coordinates or invented building
names. `SpeechProjection` changes only bounded TTS gain/performance metadata and leaves
the immutable lexical chunk unchanged.

NPC output remains on Update 6 `VoiceModule.openEntityVoice`, so it stays spatialized at
the authoritative entity. The installed SDK exposes a global spatial maximum but no
per-speaker distance setter; R032 therefore leaves the global value unchanged rather
than mutating it per utterance. Losing social focus never closes a speaker or cancels a
clip. Only response-ID cancellation/supersession, entity/world invalidation, playback
failure, or native completion controls audio lifetime.

Latency tracing begins at the first voice frame and covers endpointing, STT, audience
resolution, cognition, Nemotron TTFT, first immutable speech commit, cached conditioning,
Chatterbox synthesis, Opus, and Hytale submission. Streaming commits go to display and
TTS immediately while generation continues. First chunks are bounded to 120 characters;
later chunks remain sentence-oriented and capped at 220 characters.

## Response-correlated diagnostics

R032.2 gates historical diagnostics behind an explicit OP trace session. The in-memory
`NpcTraceManager` keys sessions by operator plus stable NPC identity, and each
`NpcTraceSession` exclusively owns one compact JSONL writer under
`profiles/<name>/traces/<name>_<yyyy-MM-dd_HH-mm-ss>.jsonl`. The existing turn adapter and
voice pipeline publish only meaningful response events; with no matching session the
manager discards them without touching the filesystem. Toggle-off, disconnect, or plugin
shutdown closes and removes the in-memory session. No restart/reconnect recovery exists.

Trace records correlate input, classification, semantic perception, selected and
competing intents, sourced belief updates, retrieved memories, relationship inputs,
active operations, action results, raw model output, canonical speech, voice chunk order,
emotion, cancellation, and latency. Navigation ticks, unchanged state, raw scans, and
hidden model reasoning are not persisted.

## Semantic environment perception

Environment-sensitive requests refresh a bounded scan of already loaded chunks
within 14m of the NPC. Asset IDs/groups, block models, interaction metadata,
block-entity container components, station/seat/bed/door flags, emitted light,
and fluid assets are detached into samples. EnvironmentSemanticAnalyzer ranks
rare POIs ahead of terrain and aggregates repeated stone, wood, vegetation,
grass/soil, and fluid samples into capped semantic features with approximate
cardinal direction and distance. The raw samples are discarded before prompt
construction.

Snapshots expire after 2.5 seconds and are also invalidated by a world change,
3m NPC movement, or 6m focused-player movement. No block-volume scan runs on a
server tick. `ENVIRONMENT_QUERY` turns buffer output until obvious present-world
claims pass structured environment validation; `FICTIONAL_STORY` remains exempt.

## Provider and streaming

OpenAiCompatibleProvider handles choices[0].delta.content, empty/role-only
deltas, reasoning deltas, fragmented delta.tool_calls, finish reasons, and
[DONE]. It preserves JSON fallback. Response-start and resettable stream-idle
deadlines are separate. TTFT is the first useful dialogue or tool-call token.

## Hytale integration

NpcIntelligenceTickSystem advances attention and tasks for loaded Mara entities.
The role asset uses Target/HeadMotion Watch for SocialFocus and Seek with
UsePathfinder for leash destinations. All network work remains off the server
tick. All entity and inventory mutations are rescheduled to the owning world
thread and re-resolve UUIDs immediately before execution.

NpcTaskStore persists meetings, navigation and composite fetch/craft delivery.
NpcRuntimeStateStore separately advances unloaded logical arrival and schedule
state when the NPC is next reconciled. It never teleports a loaded entity.

## Extensibility and bounded autonomy

PersistentNpcsApi accepts registered action, trigger, context and knowledge
providers. Configurable game events route through cooldown-protected trigger
definitions. Only MEMORY responses currently mutate core state automatically;
reasoning/task/action response kinds require an explicitly registered consumer
and still pass the normal action boundary. NPC scenes have participant,
distance, turn-count and pair-cooldown bounds. Autonomous/director proposals
must cite a real event and a registered action before execution can be added.
