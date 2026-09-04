# Orbis NPC Authoring Studio — Technical Design and Implementation Report

Updated: 2026-09-03 (America/New_York)

Program: A0–A7, stage-gated

Authoritative design: `hytale-taverns/Orbis NPC Authoring Studio Technical Design.docx`

Design SHA-256: `7F1FD4FE5E7C37B90A2CF79B2DBD09CBC7C565002FD5F6052E5C388809F0FDC9`

Repository baseline: `5ea4c424bb9e754c1a081e31dc9176b2d8674eaf`

A1 source checkpoint: `4f32daff921eb6d5cec8f5c34981b454ae0a0584`

Branch/remote: `main` / `https://github.com/Graham3D/Hytale.git`

## Program status

| Stage | Implementation | Automated gate | Connected gate | Promotion state |
|---|---|---|---|---|
| A0 — audit/freeze | Complete | PASS | Prior R124 connected evidence retained | PASS |
| A1 — session/workspace shell | Complete test candidate | PASS | PENDING operator validation | STOPPED AT GATE |
| A2 — complete coupled inventory | Not activated | Not run | Not run | BLOCKED by A1 connected gate |
| A3 — gear/loadout/live stats | Not activated | Not run | Not run | BLOCKED |
| A4 — profile editor/generate | Not activated | Not run | Not run | BLOCKED |
| A5 — appearance editor | Not activated | Not run | Not run | BLOCKED |
| A6 — voice recorder | Not activated | Not run | Not run | BLOCKED |
| A7 — integration/polish | Not activated | Not run | Not run | BLOCKED |

The model-distillation subsystem remains paused and isolated. No D6/D7, training,
adapter, model-download, environment, promotion, or runtime-model work was performed.

## A0 — Repository audit and production freeze

### Current installed Hytale build

- Server JAR: `C:\Users\Zemio\AppData\Roaming\Hytale\install\pre-release\package\game\latest\Server\HytaleServer.jar`
- Server size: `110,437,983` bytes
- Server SHA-256: `337E47E2A4AD931DFCAE227F75F9E84D2CF3D1DAC35ADCB916316F2E0819FC7E`
- Implementation version: `0.6.0-pre.13.1`
- Installed revision: `f0a85f20ac60b34232fa6b42d3585850bd959dde`
- Client executable size: `58,362,728` bytes
- Client executable SHA-256: `D04FD3453098D698717B96504AA617A3EE79B5B3AB9F3707E81766BDC0AD659F`

The installed server API was inspected directly for current-build page/window,
inventory, equipment, stats, cosmetics, skin, and voice contracts. Relevant confirmed
types include `InteractiveCustomUIPage`, `PageManager`, `WindowManager`, `Window`,
`ContainerWindow`, `InventoryUtils`, `InventoryComponent`, `EntityStatMap`,
`CosmeticRegistry`, `CosmeticsModule`, `PlayerVoiceInterceptor`, `PlayerVoiceFrame`,
`VoiceModule`, and `VoiceSpeaker`. `CharacterPreviewComponent` remains a declarative
Custom UI component rather than a server Java class.

### Frozen deployed baseline and rollback

- Active baseline at freeze: `ImmersiveNPCs-0.6.0-pre.13.1-R124-NPC-PROFILE-POLISH.jar`
- Active baseline size: `2,417,989` bytes
- Active baseline SHA-256: `BBAE9340409853EB8F5BD1661B7A7F43AEFA64E29714E241F9DF7EE417E3C830`
- Exact independent rollback: `C:\HytaleRollback\NpcAuthoringStudio-A0-2026-09-03\ImmersiveNPCs-0.6.0-pre.13.1-R124-NPC-PROFILE-POLISH.jar`
- Rollback size/SHA-256: `2,417,989` bytes / `BBAE9340409853EB8F5BD1661B7A7F43AEFA64E29714E241F9DF7EE417E3C830`

The private migration archive's R124 artifact and the repository's regenerated R124
artifact were not treated as substitutes because neither matched the exact active
binary. The exact active binary above is the rollback authority.

### Authority and ownership map

| Concern | Existing authoritative owner | A1 treatment |
|---|---|---|
| Stable NPC identity/profile canon | `ProfileRepository`, `NpcProfileRegistry`, `NpcProfile` | Reused; lease keys use stable UUID |
| Profile file selection/commit | `NpcProfileEditorService` and existing repositories | Reused; no new persistence owner |
| NPC storage/equipment persistence | `NpcInventoryRepository` and its runtime hydration pipeline | Unchanged |
| Inventory mutations | `CustomInventoryTransactionBridge` validating intent, then `InventoryUtils.moveItem` | Unchanged; envelope added around intent |
| Player storage | Player ECS `InventoryComponent.Storage` | Unchanged |
| NPC armor/loadout | Existing native `ItemContainer`/`ContainerWindow` instances and filters | Unchanged |
| Preview/model/skin/equipment | `NpcMeshPreviewSession` client-local packet overlay | Unchanged; registered in ordered cleanup |
| Appearance resolution/application | `AppearanceRepository` and current skin/model adapter | Unchanged |
| Voice sample naming/discovery | `VoicePresetRepository` | Unchanged |
| Conversation voice capture | `OrbisRuntime` / existing `PlayerVoiceInterceptor` ownership | Unchanged; A6 not activated |
| Generate resources | `OrbisResourceScheduler` and existing provider/runtime architecture | Unchanged; A4 not activated |
| Live stats | Hytale `EntityStatMap` | Audited only; A3 not activated |
| UI/session admission | New `NpcAuthoringSession` and registry | Presentation/intents only; owns no domain state |

### Frozen format/persistence observations

- Profile, appearance, voice, inventory, and equipment continue using their existing
  paths and schemas.
- No migration or schema change was made in A0/A1.
- No active save/profile/model artifact was mutated during A0.
- Baseline presentation class hash before the A1 edit was recorded in the audit;
  unchanged core inventory repository SHA-256 remains
  `296A7A49019AE7CCAF12BA3F90A54CDB2AB9BDB35B4CFE01A0C71D87E27BEAC5`.
- `NpcMeshPreviewSession` SHA-256 remains
  `644AD5D8240CFD8FA1C5FA9316611A881DDF286EB07029846FE70919591489B4`.

### A0 evidence and decision

- Full deterministic suite passed before A1 work.
- R124's existing connected reports establish the baseline for spawned/unspawned
  storage, internal NPC moves, persistence normalization, page reopen, and preview
  restoration. No current live client was running during the freeze audit.
- The build/install scripts disagreed: build produced R124 while installer requested
  R123. A1 corrects both to one R129 artifact name.
- Ownership was sufficiently resolved. A0 status: **PASS**.

## A1 — Unified authoring session and workspace shell

### Session and security architecture

Added:

- `NpcAuthoringSession`
- `NpcAuthoringSessionRegistry`
- `NpcAuthoringEventEnvelope` schema version 1
- `NpcAuthoringPermissions`

Implemented properties:

- one active writer lease per stable NPC;
- one active studio per viewer;
- session, viewer, stable NPC, optional live entity, page generation, active editor,
  editor generation, and open-time domain revision hashes;
- `CLOSED`, `OPENING`, `READY`, `PROFILE_EDIT`, `APPEARANCE_EDIT`, `VOICE_EDIT`,
  `COMMITTING`, `DEGRADED`, and `CLOSING` states;
- one contextual editor at a time;
- dirty-domain Save/Discard/Stay guard infrastructure;
- allowlisted, permission-checked, generation-bound UI event admission;
- unknown/malformed/stale/foreign events fail closed;
- idempotent cleanup ordered as inventory-event bridge, viewer preview restoration,
  and inventory persistence flush, followed by lease release;
- viewer drain and world removal close active sessions.

`NpcProfilePage` continues to submit inventory intent to the existing bridge. It does
not call `InventoryUtils` itself, does not own item state, and does not introduce a
manual `ItemStack` mutation path.

### Workspace presentation

The base Custom UI was reorganized into:

- separate top-left `NPC GEAR + LIVE STATS` panel;
- separate top-right NPC Profile Summary panel;
- one bottom `COUPLED INVENTORIES` panel with NPC left, narrow transfer rail, and
  Player Storage right;
- square 58-pixel gear cells and square 62-pixel inventory cells;
- gold NPC and cyan player visual semantics;
- compact secondary profile/skin file controls;
- restrained contextual-editor overlay shell;
- dirty-editor confirmation and existing delete/browser overlays.

Profile Editor, NPC Appearance, and Voice Recorder buttons currently prove the shared
contextual-editor/session shell only. Their domain editors are deliberately inert until
their gated stages. Generate, cosmetics editing, recording, and live stats have not been
activated.

Armor semantic icons remain packaged project assets. The three loadout placeholders use
the packaged generic special-slot icon for this connected layout gate because no verified
current-build sword/shield/ammunition artwork was found in the project-owned asset set.
Specific loadout artwork remains a later presentation refinement and is not being sourced
from a developer-local installation path.

### A1 files changed

- `persistent-npcs/src/main/java/com/inigmasgames/persistentnpcs/authoring/*`
- `PersistentNpcsPlugin.java`
- `AbstractImmersiveNpcProfileCommand.java`
- `NpcProfileEditorService.java`
- `CustomInventoryBridgeUi.java`
- `NpcProfilePage.java`
- `ImmersiveNpcProfile.ui`
- `manifest.json`, `build.ps1`, `install.ps1`, `test.ps1`
- `R129NpcAuthoringStudioA1Test.java`
- historical structural tests updated only where their exact legacy layout assertions
  conflicted with the authorized final A1 layout; their authority/integrity assertions
  remain intact.

### Automated gate

Command: `persistent-npcs/test.ps1 -SkipLive`

Result: **PASS**. The full deterministic suite completed, including R092/R101/R102,
R113–R124, the new R129 gate, voice, persistence, Orbis, Sentinel, evaluation, and the
8,100-scenario conversation matrix. Final deterministic counters included 100 soak turns,
zero stale commits, zero malformed action executions, zero unspoken delivered text, and
zero leaked resources. Live local-model tests were intentionally skipped; no training or
model behavior was changed.

Current A1 presentation hashes before deployment:

- `NpcProfilePage.java`: `6002DD745243178CBB66E5DAE286B4206077BAB774830935FCB4D885082C82F8`
- `ImmersiveNpcProfile.ui`: `16E8EDCCB5C3F18D633916CFF36A2E098621D2DEDB96880B98D9B5D1D6BEA805`

### Connected gate

Status: **PENDING**. Static tests cannot prove the current client's Custom UI parser,
ItemGrid hit testing/cursor reconciliation, preview restoration, or resolution layout.
The R129 JAR is a connected-test candidate only.

Deployed test candidate:

- Path: `C:\Users\Zemio\AppData\Roaming\Hytale\UserData\Saves\NPC\mods\ImmersiveNPCs-0.6.0-pre.13.1-R129-NPC-AUTHORING-STUDIO-A1.jar`
- Size: `2,666,118` bytes
- SHA-256: `01F30F324F73D6FCD9F5B1564BCA8F1765905426CCCF0E09E6F55DC733C004AE`
- Installed NPC mod JAR count: exactly one
- Build/deployed hash equality: verified
- Rollback remains the exact R124 binary at the A0 path with SHA-256
  `BBAE9340409853EB8F5BD1661B7A7F43AEFA64E29714E241F9DF7EE417E3C830`.
- The formerly active R124 copy was moved out of `mods` into the rollback directory's
  `retired-from-mods` subdirectory; it was not deleted.

Required connected checks:

1. At 1920×1080, open `/npc update Jonalith` for a spawned NPC.
2. Confirm separate Gear/Stats and Profile panels, one coupled inventory panel, square
   cells, no clipping/overlap, and correct NPC preview.
3. Move one item Player→NPC, NPC→Player, and NPC slot→NPC slot; close/reopen and confirm
   authoritative state.
4. Open and close each placeholder contextual editor button; only one overlay may appear
   and Return to Studio must restore the base workspace.
5. Close/reopen at least three times and confirm the viewer's world model, skin, held item,
   and equipment never change.
6. Repeat `/npc update Mara` while Mara is not spawned; verify persisted-authoring storage
   still opens, transfers, closes, and reopens correctly.
7. Repeat visual checks at 2560×1440.

### Gate decision

A1 implementation/automated status: **PASS**.

A1 connected status: **PENDING**.

Next allowed stage after an operator-reported connected PASS: **A2**.

No A2+ functionality may be activated before that report.
