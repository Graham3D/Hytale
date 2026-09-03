# Immersive AI for Hytale

An independent Hytale server plugin for persistent, server-authoritative NPC
conversations backed by replaceable local or remote inference providers. It is
separate from Tavern and has no Tavern dependency.

R036 introduces a central `AiServiceRouter` with independent STT, LLM, and TTS
provider contracts. Existing Moonshine/Faster-Whisper, OpenAI-compatible Nemotron,
and Chatterbox Turbo implementations remain the default local providers. Each service
can instead use an explicitly configured remote Immersive HTTP worker, and fallback
occurs only when `fallbackEnabled` and a fallback definition are both present.
`ai-providers.json` is migrated from the existing `config.json` and `voice.json` on
first start. Provider health, mode, backend, queue/concurrency, inference/network
latency, and fallback state appear in the native Cognition Inspector. Hytale remains
authoritative for cognition, memory, actions, ECS state, and spatial playback.

For a GPU-less Hytale host, copy the shipped `immersive_voice_worker.py` plus its
existing Python environment to the inference machine and run independent services, for
example `python -u immersive_voice_worker.py --transport http --worker-role stt
--listen-host 0.0.0.0 --listen-port 8766` and a second process with `--worker-role tts
--listen-port 8767`. Set the STT/TTS definitions in `ai-providers.json` to type
`IMMERSIVE_HTTP`, mode `REMOTE`, and the corresponding base endpoints. The worker exposes
`/health`, `/v1/stt/*`, `/v1/tts/synthesize`, and `/v1/cancel`. Bind it only on a trusted
LAN or place it behind an authenticated proxy; the lightweight worker transport itself
does not manage public-internet credentials. Keep the two processes separate so STT can
never queue behind TTS.

R035 routes each utterance through an inspectable cognitive depth before collecting context.
Identity and authored relationship facts are mandatory generation constraints; simple social
turns omit perception, weather, memory, goals, and action state; contextual and complex turns
retain the existing grounded cognition pipeline. Generated NPC wording is conversation history,
not factual evidence, and semantic relevance now gates memory ranking before importance. The
canonical boundary preserves valid no-action dialogue, naturalizes environment fallbacks, and
records raw/canonical decisions. Chatterbox uses one cancellable Java-side queue ahead of the
single Python worker and traces queue, cache, synthesis, model-load, and CUDA-memory timings.

R034.1 keeps player transcription responsive while Chatterbox is synthesizing by running
Whisper in a dedicated worker, and records capture/transcription stages in active operator
traces. R034 evolves the existing memory records in place with deterministic importance,
durability, emotional/relationship/goal/danger/novelty appraisal, reinforcement-aware
recall, LANDMARK retention, score-breakdown diagnostics, and RAM-first asynchronous
atomic persistence. Legacy memory JSON is migrated on load without losing provenance.

R033 preserves the R032.3 native `/npc spawn <name>` recovery and adds structured
player-report extraction, provenance-preserving episodic recall, true per-listener
multi-NPC hearing, NPC-bound response tracing, and compact Hearing/Memory sections in
the native Cognition Inspector. Speech arbitration now selects who may answer without
preventing other eligible NPCs from hearing, classifying, or remembering the utterance.

R032.3 repairs native `/npc spawn <name>` after `/npc clean` or another native
entity removal. The native command remains authoritative. When its new entity reaches the
Immersive intelligence tick, a registry entry is replaced only if its former entity is no
longer loaded; a genuinely active persistent instance still wins and the duplicate is
removed. Stable profile identity and all persistent cognition data remain unchanged.

R032.2 retains the R032.1 cognition-metadata dialogue fix and replaces its always-on audit
sink with explicit OP-owned trace sessions. `/npc trace <name>` toggles an in-memory trace
for one stable NPC identity. While active, meaningful response-correlated cognition,
model, action, canonical dialogue, voice chunk, cancellation, and latency events are
written to `profiles/<name>/traces/<name>_<yyyy-MM-dd_HH-mm-ss>.jsonl`. Toggle-off,
operator disconnect, and plugin shutdown close the writer; reconnect and restart never
resume it. Normal NPC use creates no trace file, and navigation ticks/raw scans are not
trace events. Structured conclusions are recorded, never hidden chain-of-thought.

Questions, commands, and bare confirmations are not persisted or retrieved as player
facts; legacy noisy records remain preserved on disk but are excluded from cognition.
The streaming commit guard rejects timestamps, provenance labels, prompt headings, and
internal status narration before either chat or TTS receives them.

R032 separates ordinary listening, remote named hailing, and spatial NPC speech into
independent configurable ranges. One player utterance is captured and transcribed once,
then becomes an immutable event shared by every eligible loaded NPC. Deterministic
attention arbitration selects one response owner while non-owners can retain bounded
overheard memory. Directly named NPCs can answer from their actual entity location at up
to the hail radius with CALL/SHOUT performance metadata; canonical displayed and spoken
words remain identical. Playback now follows response/entity lifetime rather than social
focus, and the native cognition inspector exposes audience, routing, cancellation, and
end-to-end first-audio timing. The first streaming TTS chunk is capped at 120 characters
to reduce the Chatterbox long tail without rewriting dialogue.

R031 adds relationship-gated social navigation. When a player names another authored
NPC, the speaker resolves the stable identity, verifies an explicit relationship, and
performs one direct loaded-entity lookup bounded to 500 blocks. Cognition receives only
semantic distance/direction and availability. Accepted offers become existing
`AgentOperation`, `SharedPlan`, and `NpcTask` records; native NPC navigation refreshes the
target every tick, keeps the player nearby, fails safely, and resumes suspended movement
work without persisting a trail of coordinates.

R030 separates engine-facing raw observations from NPC-facing understanding. A
deterministic semantic normalizer now compresses current ECS/sensor state into
question-relevant concepts and authoritative self-state before cognition or Nemotron.
Raw entity IDs, coordinates, block samples, sensor metadata, and diagnostic syntax stay
in the native developer inspector. Response-scoped latency tracing covers perception,
cognition, Nemotron TTFT, canonical speech commitment, Turbo conditioning/synthesis,
Opus availability, and Hytale voice submission with configurable budgets in
`latency-budgets.json`.

R029 grounds each conversation in one response-scoped cognition decision. Player
reports become provenance-preserving sourced beliefs; authoritative Hytale state,
relationships, memories, obligations, shared plans, and registered actions feed a
deterministic intent ranking before Nemotron speaks or acts. The committed lexical
dialogue remains identical for display and TTS. Administrators can inspect the latest
belief, evidence, competing intents, selected intent, action, and result with
`/immersivecognition <name>` when granted
`inigmasgames.immersivenpcs.debug.cognition`.

R018 explicitly enables Hytale's supported `VoiceModule` in a private
single-player save before the client connects, allowing Push-to-Talk input to
reach the local Whisper pipeline. It also applies Mara's authored cosmetics via
the Update 6 ECS `CommandBuffer`, fixing the rejected in-system model mutation
that left her rendered with the bare fallback model.

R017 gives Mara a persistent home anchor and exclusive locomotion states:
`IDLE_HOME`, `INVESTIGATING`, `RETURNING_HOME`, and `FOLLOWING_PLAYER`. She now
wanders only intermittently within a configurable radius, returns to her anchor,
and follows only after a validated player action. Legacy unprovenanced follow
tasks are cancelled once during migration. Her authored appearance is also
reapplied to saved entities when they load.

R016 adds a fail-closed runtime gate: if the launcher starts release 0.5.9, no
Update 6 NPC tick/event systems are registered and AI commands explain how to
select Pre-release instead of allowing a `NoSuchMethodError` to crash the world.

R015 fixes NPC spawning across the `Rotation3f.lookAt` ABI change between release
0.5.9 and Update 6. It keeps the Update 6 target and official spatial voice API.
Installing the pre-release does not select it automatically: verify the Hytale
launcher channel says **Pre-release** before starting the NPC save.

R014 targets Hytale Update 6 pre-release and uses its official spatial voice API.
Player Push-to-Talk Opus is observed by a non-blocking interceptor, decoded and
transcribed by local Whisper, then routed only to that player's active Mara session.
Streaming Nemotron phrases are synthesized by one persistent GPU-capable
`ChatterboxTurboTTS` worker, resampled in memory, encoded as 48 kHz mono Opus, and
played sequentially through Mara's entity-following `VoiceSpeaker`. No runtime WAV
or SoundEvent registration is used.

R013 assigns Mara a persistent local Chatterbox voice preset, keeps voice identity
independent from LLM/model routing, and deterministically maps the R012 `VocalState`
into bounded, turn-smoothed Chatterbox performance controls. R012 adds a compact NPC self-model, persistent decaying emotion, per-meaningful-turn
structured appraisal, relationship/personality-based action authorization, bounded
look-around/POI attention with a real uncertainty emote, and a deterministic fallback
that converts an explicit model agreement into the validated `FOLLOW_PLAYER` action.
Spawn and follow targets now resolve loaded walkable ground independently of a
Creative/flying player's Y position. Mara's `appearancePreset = "Mara"` resolves the
Skin Swap-compatible `SS_SKIN_Mara.json` export through Hytale's own cosmetics model
builder; no Skin Swap runtime or source dependency is required.

R011 adds request-time semantic environment grounding from authoritative loaded
Hytale chunks. A bounded 14m scan prioritizes portals, doors, stations, containers,
furniture, and lights, then aggregates masonry, terrain, fluids, and vegetation.
Snapshots have a short TTL with movement invalidation; raw block dumps never enter
the LLM context. The server log records the semantic grounding diagnostics and scan
duration for the current NPC/player scene.

R010 separates fictional stories, hypothetical plans, authoritative world state,
active tasks, and validated quests during ordinary dialogue. Unsupported present-action
claims are rewritten before reaching Hytale chat, and fictional story events remain
session-local rather than becoming persistent memories or world state.

R009 adds a persistent, server-validated Dynamic Quest Director, deterministic
item reward budgets, event-scored autonomous intent, an ephemeral/persistent
monster reasoning overlay, Profile Schema v1, sticky local-model routing, bounded
NPC-to-NPC text scenes, and semantic voice-ready response state. It preserves the
R008 grounding/dialogue stabilization and all earlier interaction behavior.

The R006 framework adds fresh held-item grounding, request-filtered registered
actions, transactional item/equipment transfers, resumable fetch/craft delivery,
persistent schedules and off-screen logical state, data-driven occupations,
typed memory/relationship/gossip state, configurable triggers, bounded scene and
autonomy foundations, SSE streaming, and latency/budget instrumentation. At R006,
Mara remained text-only and entirely local; R014 adds the optional local voice path.

## Requirements

- Hytale Update 6 pre-release `0.6.0-pre.13.1` or newer compatible `0.6.x` build
- Java 25 JDK for building
- Ollama, LM Studio, or another local OpenAI-compatible server
- No cloud service, API subscription, or paid API key

## Build and isolated deployment

From PowerShell in this project:

```powershell
.\test.ps1
.\install.ps1
```

The installer now defaults only to:

```text
%APPDATA%\Hytale\UserData\Saves\NPC\mods
```

The current connected-client validation artifact is
`dist/ImmersiveNPCs-0.6.0-pre.13.1-R064-PHASE2-VALIDATION.jar`. Automated real-provider
and steady-state resource calibration has passed; production Gate 2 remains open until the physical PTT/connected Hytale
matrix is completed. R061 remains the rollback artifact outside the active mod folder. Do not
deploy the validation build to the Tavern save unless that is explicitly intended. A custom isolated target can
still be passed with `-ModsDirectory "G:\path\to\server\mods"`.

Start the NPC save once after installation, then stop it. The plugin creates:

```text
%APPDATA%\Hytale\UserData\Saves\NPC\mods\ImmersiveNPCs\config.json
```

Existing configurations are not overwritten during upgrades.

## Recommended baseline: Nemotron 3 Nano 4B with Ollama

The default is `nemotron-3-nano:4b` at Ollama's local OpenAI-compatible
endpoint. Its Q4_K_M package is about 2.8 GB and is a practical low-latency
starting point for local NPC dialogue. NVIDIA describes the 4B model as an
edge-ready model for local conversational agents and gaming NPCs. See the
[official NVIDIA model card](https://huggingface.co/nvidia/NVIDIA-Nemotron-3-Nano-4B-GGUF),
[NVIDIA release post](https://huggingface.co/blog/nvidia/nemotron-3-nano-4b),
and [Ollama tags](https://ollama.com/library/nemotron-3-nano/tags).

First-time Ollama setup:

1. [Install Ollama](https://ollama.com/download) and launch it. On systems where
   it is not already a background service, keep `ollama serve` open in another
   PowerShell window.
2. Download and warm the recommended model:

   ```powershell
   ollama pull nemotron-3-nano:4b
   ollama run nemotron-3-nano:4b "Reply with only: ready"
   ollama ls
   ```

3. Verify that the OpenAI-compatible route lists the exact ID:

   ```powershell
   (Invoke-RestMethod http://127.0.0.1:11434/v1/models).data.id
   ```

   It must include `nemotron-3-nano:4b`.
4. The generated default configuration is already correct:

   ```json
   {
     "endpoint": "http://127.0.0.1:11434/v1/chat/completions",
     "model": "nemotron-3-nano:4b",
     "apiKey": "",
     "connectTimeoutMillis": 1500,
     "requestTimeoutMillis": 12000,
     "temperature": 0.7,
     "maxTokens": 180,
     "maxPlayerMessageCharacters": 600,
     "recentMemoryCount": 6,
     "maxMemoryRecords": 2000,
     "sessionIdleSeconds": 300,
     "streamResponses": true,
     "responseStartTimeoutMillis": 60000,
     "streamIdleTimeoutMillis": 15000,
     "reasoningEffort": "none",
     "maxConcurrentLlmRequests": 2,
     "autonomousRequestsPerMinute": 6,
     "npcToNpcRequestsPerMinute": 4,
     "perPlayerRequestsPerMinute": 20,
     "perNpcAutonomyCooldownSeconds": 60,
     "deepConversationTurnThreshold": 6,
     "modelTiers": {
       "generic": {
         "endpoint": "http://127.0.0.1:11434/v1/chat/completions",
         "model": "nemotron-3-nano:4b",
         "apiKey": "",
         "reasoningEffort": "none"
       }
     }
   }
   ```

5. Restart the NPC save, then run `/npc spawn Mara`. Walk within four meters and
   speak in normal chat. Provider readiness and dialogue timing are recorded in the
   server log.

Optional stronger local models can be added under `modelTiers` with keys
`important` and `deepConversation`. Each tier uses the same OpenAI-compatible
fields as `generic`. Omit an unavailable tier; routing then uses the strongest
configured local tier at or below the requested tier, and falls back to generic
when a stronger endpoint fails before streaming begins.

Ollama documents its local `/v1/chat/completions`, streaming, and `/v1/models`
routes in the [official OpenAI compatibility guide](https://docs.ollama.com/api/openai-compatibility).
`reasoningEffort: "none"` prevents Nemotron from spending the dialogue token
budget on a private reasoning trace. The response-start budget allows a cold
local model to load, while the shorter resettable stream-idle timeout still
detects a stalled response after SSE begins.

NVIDIA's exact GGUF can alternatively be loaded directly with:

```powershell
ollama run hf.co/nvidia/NVIDIA-Nemotron-3-Nano-4B-GGUF:Q4_K_M
```

If using that form, copy the exact ID printed by `/v1/models` into `model`.

## LM Studio setup

LM Studio uses the same provider abstraction; only endpoint and model ID
change.

1. [Install LM Studio](https://lmstudio.ai/download) and launch it once.
2. Download NVIDIA's GGUF, selecting the small `Q4_K_M` quantization, then
   inspect the installed key:

   ```powershell
   lms get nvidia/NVIDIA-Nemotron-3-Nano-4B-GGUF
   lms ls
   ```

3. Replace `<MODEL_KEY_FROM_LMS_LS>` with that exact key, assign a stable API
   name, and start the local server:

   ```powershell
   lms load <MODEL_KEY_FROM_LMS_LS> --identifier "mara-nemotron"
   lms server start --port 1234
   lms ps
   lms server status
   (Invoke-RestMethod http://127.0.0.1:1234/v1/models).data.id
   ```

4. Confirm the route includes `mara-nemotron`, then set these fields in the
   generated config and leave the other defaults unchanged:

   ```json
   {
     "endpoint": "http://127.0.0.1:1234/v1/chat/completions",
     "model": "mara-nemotron",
     "apiKey": "",
     "streamResponses": true,
     "reasoningEffort": "none"
   }
   ```

5. Restart the NPC save, verify provider readiness in the server log, and talk to Mara.

LM Studio documents the [`lms get`](https://lmstudio.ai/docs/cli/local-models/get),
[`lms load`](https://lmstudio.ai/docs/cli/local-models/load), and
[OpenAI-compatible server](https://lmstudio.ai/docs/developer/openai-compat)
commands. Any other local OpenAI-compatible chat model remains supported by
putting its exact `/v1/models` ID and endpoint in `config.json`.

## Streaming and latency

When `streamResponses` is true, the provider requests OpenAI-compatible SSE and
records:

- request start (`Instant`)
- time to first non-empty dialogue token (TTFT)
- response-stream completion time
- total conversation time after parsing, validation, and persistence

Readable sentence-sized chunks are sent to the focused player as they arrive.
If the endpoint rejects streaming with a normal unsupported-request response,
the same request is retried as non-streaming JSON. Set `streamResponses` to
false to force that fallback directly.

Streaming uses separate failure phases: `responseStartTimeoutMillis` waits for
HTTP response start (including a cold model load), while
`streamIdleTimeoutMillis` resets on every SSE line, including role-only and
reasoning-only deltas. `[DONE]` completes the response immediately. The legacy
`requestTimeoutMillis` remains the health-check timeout. Set `reasoningEffort`
to an empty string to omit the optional OpenAI field for a generic backend that
does not support it.

The server log reports endpoint, model, configured state, reachability, streaming
preference, connection/error reason, session state, request start, TTFT,
completion, delivery mode, and total latency for each exchange.

`test.ps1` runs deterministic SSE and fallback integration tests. It then
probes the default real Nemotron endpoint and performs a real generation only
when the exact model is available. To benchmark another local configuration:

```powershell
$env:PERSISTENT_NPC_LLM_ENDPOINT = 'http://127.0.0.1:1234/v1/chat/completions'
$env:PERSISTENT_NPC_LLM_MODEL = 'mara-nemotron'
.\test.ps1
```

If no matching local model is running, the benchmark says why it was skipped;
mock timings are never presented as real model performance.

## Mara, attention, actions, and persistence

Spawn the completed native role with Hytale's `/npc spawn` workflow in a clear area.
At four meters or less with line of sight,
Mara targets the player through Hytale's marked-target/head-steering API and
begins listening without invoking the LLM. At five meters or more, or when
line of sight is lost, focus and head tracking are released. This 4m/5m
hysteresis prevents boundary flicker.

Normal chat routes to one focused NPC. Candidate scoring is explicit NPC name,
then look direction, then distance; a chat message is never broadcast to all
nearby NPCs. Use/secondary-interact still focuses Mara, and normal nearby chat is
the authoritative conversation path.

The local model receives compact facts captured from real Hytale components:
game time, positions, visible players/NPCs/hostiles/dropped items, loaded
interactable and crafting-station blocks, the focused player's exact held
ItemStack, and Mara's inventory. It may select only eligible registered
OpenAI-compatible function tools. The server revalidates stale entity,
distance, ownership, capacity, capability, recipe, station, and ingredient
state before deterministic execution.

Registered actions cover FOLLOW_PLAYER/STOP_FOLLOWING, GO_TO, PATROL, WANDER,
FLEE, WAIT and cancellation; PICK_UP/TAKE/GIVE/DROP/BRING/INSPECT and filtered
EQUIP/UNEQUIP; SCHEDULE_MEETING/SCHEDULE_TASK; CRAFT/COOK/PROCESS; bounded
relationship changes; and currency-neutral obligations. Tool schemas are
filtered against the current request before Nemotron sees them. Crafting and
occupation actions remain role/capability gated. Unknown actions, fabricated
UUIDs and arbitrary commands fail closed.

Framework data remains under the plugin data directory:

```text
config.json
profiles/Mara/profile.json
persistence/relationships.json
persistence/memories.json
persistence/tasks.json
persistence/runtime-state.json
persistence/gossip.json
persistence/obligations.json
occupations.json
triggers.json
```

Mara's stable profile UUID is the relationship/memory key. Keep it unchanged
after players meet her. Profiles are managed with `/npc create <name>` and
`/npc update <name>`; configuration changes require a restart.

## Local Chatterbox Turbo voices

Mara's authored profile always selects `voicePreset: "mara"` and
`voiceEffectPreset: "none"`. The separately persisted preset is created at:

```text
%APPDATA%\Hytale\UserData\Saves\NPC\exports\voices\Mara\preset.json
```

Its user-owned canonical identity recording belongs at:

```text
%APPDATA%\Hytale\UserData\Saves\NPC\mods\ImmersiveNPCs\profiles\Mara\reference.wav
```

Emotion references use these exact filenames in the same directory:

```text
sample-calm.wav
sample-curious.wav
sample-excited.wav
sample-uneasy.wav
sample-angry.wav
sample-sad.wav
sample-tender.wav
sample-amused.wav
```

Do not use a copyrighted game-character recording or a recording without the
speaker's consent. Chatterbox Turbo requires each selected prompt to be longer than
five seconds. A missing, unreadable, or shorter emotion recording falls back to
`reference.wav`; the files are never concatenated.

Optional nonverbal performance metadata is limited to Chatterbox Turbo's official
`[clear throat]`, `[sigh]`, `[shush]`, `[cough]`, `[groan]`, `[sniff]`, `[gasp]`,
`[chuckle]`, and `[laugh]` tags. A cooldown policy selects them sparingly and injects
them only into the first TTS chunk. They never enter displayed dialogue or memory.

First-time local setup on Windows (PowerShell):

```powershell
$voices = Join-Path $env:APPDATA 'Hytale\UserData\Saves\NPC\exports\voices'
py -3.12 -m venv (Join-Path $voices '.venv-turbo')
$python = Join-Path $voices '.venv-turbo\Scripts\python.exe'
& $python -m pip install --upgrade pip 'setuptools<81'
& $python -m pip install torch==2.6.0+cu124 torchaudio==2.6.0+cu124 `
  --index-url https://download.pytorch.org/whl/cu124
& $python -m pip install chatterbox-tts==0.1.7 av faster-whisper
```

The separate `.venv-turbo` leaves the earlier CPU environment untouched. A CUDA
toolkit installation is not required for these wheels, but a compatible NVIDIA
driver is. Confirm GPU discovery before starting Hytale:

```powershell
& $python -c 'import torch; print(torch.__version__, torch.cuda.is_available()); print(torch.cuda.get_device_name(0) if torch.cuda.is_available() else "CPU")'
```

Start the NPC save once to create `preset.json`, `voice.json`, and
`immersive_voice_worker.py`. The worker loads Turbo and Whisper once and remains
alive until plugin shutdown. The first start downloads local model weights and can
take substantially longer than subsequent starts.

In Hytale Audio settings select `Voice Input Mode: Push to Talk` and bind
`Push to Talk: E`. Update 6 exposes transmitted voice frames to the server but no
API for reading or changing a client's keybind, so the plugin displays this setup
instruction once when a player becomes ready.

Voice runtime settings are generated at:

```text
%APPDATA%\Hytale\UserData\Saves\NPC\mods\ImmersiveNPCs\voice.json
```

The default keeps Turbo on automatic CUDA/CPU selection and Whisper `base.en` on
CPU `int8`, avoiding GPU memory competition. Set `ttsDevice` to `cpu` to disable
GPU TTS. `exportDebugWav` is false; normal playback never writes a WAV.

## Current limitations

- A connected Update 6 client must still verify end-to-end microphone capture,
  moving-entity spatial playback, attenuation, and Push-to-Talk release behavior.
  Automated tests cover official API linkage, phrase ordering, reference selection,
  Opus size validation, local Turbo synthesis, Opus round-trip, and Whisper.
- The supplied emotion recordings are currently 2.9–3.9 seconds and therefore fail
  Turbo's strict greater-than-five-second prompt requirement. They are detected as
  invalid and safely fall back to the 24.5-second `reference.wav` until replaced.
- CRAFT_ITEM now persists navigation, recipe execution, return and physical
  delivery. A connected client must still verify real station reachability and
  every armor/recipe asset filter. The installed inventory API has no documented
  cross-step atomic craft transaction, so the implementation preflights both
  sides and reports an explicit recovery error if output insertion unexpectedly
  fails after ingredient removal.
- A scheduled meeting/task persists and resumes when Mara is loaded. Unloaded
  state advances logical location/arrival on reload, but never teleports a
  loaded or visible NPC.
- Natural descriptions such as “behind that house” have no authoritative
  semantic-location resolver yet. Meetings accept resolved coordinates or use
  the current NPC location if the model supplies none.
- Streaming appears as private sentence-sized chat messages, not a speech
  bubble or progressively edited UI widget.
- Local vector embeddings remain optional behind an abstraction because the
  configured chat-completions endpoint does not guarantee an embeddings route;
  lexical/entity/importance/recency retrieval remains active.
- The current loaded-world block accessor exposes asset IDs, groups, models,
  materials, interactions, light, fluid IDs, and block components, but no stable
  biome/zone or weather query at an NPC coordinate. R011 reports those fields as
  unavailable instead of guessing them.
- Mara's profile, relationships, memories, gossip, obligations and tasks persist, but her Hytale
  entity is not automatically respawned after a server restart.
- `PlayerInteractEvent` remains deprecated in Update 6; normal nearby chat remains
  available without relying on it.
- The R018 top-right counter is deliberately development-facing.

## R009 framework references

- [Emergent-world implementation status](docs/EMERGENT_WORLD_STATUS_R009.md)
- [Profile Schema v1](docs/PROFILE_SCHEMA_V1.md)

See [ARCHITECTURE.md](ARCHITECTURE.md), [the complete action matrix](docs/ACTION_SUPPORT_MATRIX.md),
the [R058 adaptive reasoning/streaming report](docs/R058_ADAPTIVE_REASONING_STREAMING.md),
the [R059 streaming/range/overlap repair](docs/R059_STREAMING_RANGE_OVERLAP_REPAIR.md),
the [R060 canonical response assembly contract](docs/R060_CANONICAL_RESPONSE_ASSEMBLY.md),
the [R061 trace-driven lag repair](docs/R061_TRACE_LAG_REPAIR.md),
and [the R006 API/blocker report](docs/FRAMEWORK_STATUS_R006.md).
