# R171 Pre-release 4 Compatibility Port

Revision: `R171-PRE4-COMPAT`

R171 is the standalone ImmersiveNPCs R170 feature baseline compiled for the
installed Hytale pre-release server, `0.7.0-pre.4`. The earlier task reference to
`0.7.0-pre.1` was superseded by the installed server and was not used as a guessed
binary target.

## Compatibility audit

| Surface | R170 result on pre.4 | R171 change |
| --- | --- | --- |
| Plugin metadata | Rejected `0.7.x` | Exact `=0.7.0-pre.4` compatibility |
| Plugin entry point | Merger-era source was abstract and only bootable through a host subclass | Restored the concrete public `PersistentNpcsPlugin(JavaPluginInit)` entry point present in the working R170 JAR |
| Chunk block lookup | Seven compile failures because `WorldChunk.getBlockType` was removed | Resolve `BlockType` through `BlockType.getAssetMap().getAsset(chunk.getBlock(...))` |
| Entity/components and `CommandBuffer` | Compiles unchanged | None |
| Player/NPC lifecycle and events | Compiles unchanged | None |
| Voice capture, networking, and `VoiceSpeaker` | Compiles unchanged | None |
| Custom UI and resources | Retained validation passes against installed pre.4 assets | None |
| Persistence/profile schemas | Retained tests pass | Existing reversible legacy identity migration retained |
| Nemotron/OpenAI-compatible provider | Direct non-streaming and SSE checks pass | None |
| Moonshine/Faster-Whisper/Chatterbox | Existing `.venv-turbo` imports and modern worker checks pass | None; no reinstall |

## Data boundary

The active data identity is `mods/ImmersiveNPCs`. On the first R171 installation,
missing files are copied from `mods/InigmasGames_PersistentNPCs`; existing modern
files win. The legacy tree is then moved intact to the save-local
`ImmersiveNPCs-Legacy-Backups` directory. Authored files under `exports`, including
Mara's skin and voice WAVs, are not modified. Prior project JARs are moved to
`C:\HytaleRollback` before the single R171 JAR is activated.

Orbis training/distillation state is not changed by this port.

## Validation record

- The full retained deterministic suite passes against the installed pre.4 server.
- An isolated server loaded R171, restored the copied R023 profile/persistence data,
  warmed Moonshine, completed a real Nemotron SSE request, warmed CUDA Chatterbox
  with Mara's cached conditioning, and shut down cleanly.
- The deployed pre-release save repeated the same lifecycle successfully.
- Explicit Faster-Whisper startup reached `READY` in the existing Turbo Python
  environment; no package, model, CUDA, or Python installation was performed.
- A direct Chatterbox synthesis produced 79 ordered Opus frames with the configured
  `+4 dB` gain and no limiter reduction.
- Mara retained stable UUID `3f84ec9e-37c5-4f11-9a74-106cd3bc04da`.
- The R170 memory loader added current durability/appraisal fields and consolidated
  one duplicate `PLAYER_FACT` stating `Graham`; the equivalent durable fact remains.
  The untouched 146-record source is preserved in both rollback locations.
- All authored files under `exports/voices/Mara` and `exports/skins/Mara` retained
  their pre-deployment hashes.

The server-side PTT/STT/LLM/TTS/spatial pipeline is loaded and warm, but an audible
connected-client conversation still requires the player client. Checkpoint C must
not begin until that physical acceptance test passes.
