# Orbis NPC Authoring Studio — Technical Design and Implementation Report

Updated: 2026-09-04 (America/New_York)

Program: A0–A7, stage-gated

Authoritative design: `hytale-taverns/Orbis NPC Authoring Studio Technical Design.docx`

Design SHA-256: `7F1FD4FE5E7C37B90A2CF79B2DBD09CBC7C565002FD5F6052E5C388809F0FDC9`

Repository baseline entering A6: `e4f8d67ca5b9fd829e17e430049ffb2975a1d8c4`

A1 source checkpoint: `4f32daff921eb6d5cec8f5c34981b454ae0a0584`

Branch/remote: `main` / `https://github.com/Graham3D/Hytale.git`

## Program status

| Stage | Implementation | Automated gate | Connected gate | Promotion state |
|---|---|---|---|---|
| A0 — audit/freeze | Complete | PASS | Prior R124 connected evidence retained | PASS |
| A1 — session/workspace shell | Complete | PASS | PASS: stable Jonalith/Mara open-close at available 1080p/720p modes | PASS; inventory limitation carried into A2 |
| A2 — complete coupled inventory | Complete in R131 | PASS | PASS: full operator transaction matrix | PASS |
| A3 — gear/loadout/live stats | Complete in R132 | PASS | PASS | PASS |
| A4 — profile editor/generate | Complete in R133.1 | PASS | PASS: operator confirmed editor and workspace behavior | PASS |
| A5 — appearance editor | Complete in R134.2 | PASS | PASS | PASS |
| A6 / P1 — voice recorder polish | R145 recorder retained unchanged in R146 | PASS | No additional connected approval inferred | Recorder not modified by main-menu milestone |
| Main Profile polish | R146 compact main-menu candidate, explicitly authorized by latest request | PASS | PENDING | HOLD FOR CONNECTED APPROVAL; Appearance Editor polish not begun |

The model-distillation subsystem remains paused and isolated. No D6/D7, training,
adapter, model-download, environment, promotion, or runtime-model work was performed.

## A0 — Repository audit and production freeze

### Initially audited Hytale build (A0–A4)

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

A1 connected status: **PASS for the shared shell/session gate**. The operator confirmed
reliable open/close and both Jonalith and Mara profiles at the client-supported display
modes. Custom-grid item pickup succeeded but placement did not; that transaction defect
is the first bounded A2 repair rather than an A1 shell regression.

Next active stage: **A2**.

No A3+ functionality may be activated before the complete A2 connected matrix is green.

## A2 — Complete coupled inventory

### R130 connected finding

The client emitted valid `Dropped` events for all three essential routes: NPC internal,
NPC to Player Storage, and Player Storage to NPC. However, the bridge session closed with
zero observed operations. The payload showed why: `AuthoringEditor` and
`AuthoringEditorGeneration` were transmitted literally as UI selector strings. The
current client does not resolve arbitrary `.Text` selectors inside an ItemGrid `Dropped`
binding, so the authoring envelope rejected the event before the inventory bridge ran.

### R131 repair and transaction expansion

- Event bindings now embed the server-owned editor and editor-generation values that
  were current when the binding was created. Old bindings remain safely rejectable by
  the existing generation check.
- Client item ID and quantity remain diagnostic only. The server re-reads the source
  slot and derives a full-stack operation for primary-button drops or a one-item
  operation for secondary-button drops.
- Empty-slot moves and compatible merges use Hytale's native
  `ItemContainer.moveItemStackFromSlotToSlot` transaction.
- Occupied incompatible destinations use the native one-slot `ItemContainer.swapItems`
  transaction only for the already-authorized Player Storage/NPC Storage identities.
- Every mutation is followed by an authoritative source/destination reread. A commit is
  reported only when the native transaction succeeds and its post-state proves a
  quantity-conserving move/merge or an exact swap.
- Full destinations, stale sources, unsupported partial swaps, malformed sections,
  duplicate releases, late events, and authority mismatches fail closed and trigger a
  complete two-grid authoritative refresh.
- Replay fingerprints now include authoritative source and destination pre-state.
- Session-close diagnostics report observed, committed, rejected, stale, duplicate, and
  post-state invariant-violation counters.
- No hand-authored remove/add/set stack mutation was introduced.

Automated gate: `persistent-npcs/test.ps1 -SkipLive` — **PASS**, including the new R131
structural/safety gate and the complete existing deterministic suite. Live model tests
remain intentionally skipped because A2 does not change inference behavior.

Connected gate: **PASS** on 2026-09-04. The operator completed the full R131 acceptance
matrix, including Player→NPC, NPC→Player, NPC-internal, Player-internal, compatible merge,
occupied swap, secondary-button one-item placement, shift-click/quick-move,
full-destination rejection, close/reopen persistence, repeated Jonalith/Mara cycles, and
the mixed-operation soak with no loss, duplication, stale cursor, wrong-slot, or wrong-NPC
mutation observed.

The exact deployed A2 authority retained for A3 rollback is:

- JAR: `ImmersiveNPCs-0.6.0-pre.13.1-R131-NPC-AUTHORING-STUDIO-A2-INVENTORY-BRIDGE.jar`
- SHA-256: `C6FB298934E5B1FA96DFB54FF4DF7D0373F1741645D9F44DAF929AA989840C18`
- Size: `2,667,348` bytes
- Rollback copy: `C:\HytaleRollback\NpcAuthoringStudio-A3-2026-09-04\ImmersiveNPCs-0.6.0-pre.13.1-R131-NPC-AUTHORING-STUDIO-A2-INVENTORY-BRIDGE.jar`

The complete deterministic suite was rerun from the R131 source immediately before the
A3 checkpoint and passed. Stage A3 may now activate gear, loadout, and live stats; A4+
remains unauthorized.

## A3 — Gear, loadout, and live stats

### Installed API evidence

A3 was implemented against the installed Update 6 pre-release server binary, not an
assumed SDK. The audited binary was:

- `C:\Users\Zemio\AppData\Roaming\Hytale\install\pre-release\package\game\latest\Server\HytaleServer.jar`
- SHA-256: `337E47E2A4AD931DFCAE227F75F9E84D2CF3D1DAC35ADCB916316F2E0819FC7E`
- size: `110,437,983` bytes

The inspected contracts include `ItemArmor.getArmorSlot()`,
`ItemArmor.getBaseDamageResistance()`, `Item.getWeapon()`, `Item.getArmor()`,
`Item.getUtility()`, `AssetExtraInfo.Data.getRawTags()`,
`ActiveSlotInventoryComponent.getActiveSlot()/setActiveSlot()`,
`EntityStatMap.get(String)`, and `InventoryUtils.createEquipmentUpdate(...)`.
Shipped item assets establish the authoritative `Family=Arrow` and `Family=Shield`
tags and bow/crossbow item families used by the resolver.

### Authoritative equipment ownership

Spawned NPC authoring now opens the exact live ECS `Armor`, `Hotbar`, `Utility`, and
`Storage` `ItemContainer` objects. Every one is wrapped in its own registered
`ContainerWindow`; section and object identity are revalidated for every transaction.
Unspawned NPCs retain repository-owned bounded containers and the same persisted state
shape.

The semantic endpoints are:

- armor: live `InventoryComponent.Armor`, slots 0–3;
- primary weapon: live `InventoryComponent.Hotbar`, physical slot 0;
- shield/offhand: live `InventoryComponent.Utility`, physical slot 0;
- preferred ammunition: live `InventoryComponent.Hotbar`, physical slot 1;
- NPC storage: live `InventoryComponent.Storage`, 40 slots;
- Player storage: the viewer's built-in negative Storage section.

Allowed movement is explicitly limited to Player/NPC Storage ↔ Armor and
Player/NPC Storage ↔ Loadout, plus existing internal storage movement. Direct
Armor↔Loadout moves fail closed. Occupied endpoints use the already-proven atomic native
swap; a displaced item must validate for the source equipment endpoint or the whole
operation is rejected. Partial occupied swaps, full-destination failures, stale intent,
unknown sections, inactive windows, identity drift, and unknown compatibility all reject
without manual item copying, deletion, dropping, or rollback reconstruction.

### Compatibility and dependent-state policy

`NpcEquipmentCompatibilityResolver` returns a typed `COMPATIBLE`, `INCOMPATIBLE`,
`UNKNOWN`, or `REQUIRES_REVIEW` verdict with evidence. Armor uses only the exact
`ItemArmor.armorSlot` contract. Primary weapon, shield, and ammunition use installed item
metadata and raw asset tags; filename-prefix heuristics are not accepted as authority.
Unknown and review-required verdicts fail closed.

Preferred ammunition uses **model A: a physical stack** in Hotbar slot 1. A primary
weapon change re-evaluates that stack. An incompatible dependent item remains in its
authoritative slot, is rendered incompatible, and disables the infinite-ammunition
effect; it is never silently moved or deleted.

Infinite ammunition is persisted as policy only and never manufactures stacks. Its
effective state additionally requires compatible physical weapon/ammunition state, the
Gear permission envelope, and the server configuration boundary
`immersive.npcs.infiniteAmmunition.enabled` (default `true`).

### Visibility, world state, preview, and commit ordering

Armor visibility controls are only exposed for occupied slots. The four persisted flags
are applied to the NPC's native `PlayerSettings`, never the viewer. After an equipment
commit the implementation authoritatively rereads, synchronously flushes persistence,
selects the authored Hotbar/Utility active cells, marks native equipment outdated,
creates the current SDK `EquipmentUpdate`, refreshes the NPC preview, captures stats, and
sends one coalesced Custom UI update. Preview failure is explicitly degraded and does not
roll back an item transaction.

The viewer remains a transport/render target for the already-proven preview session only;
the A3 mutation and visibility paths address the NPC ECS reference and exact NPC
containers exclusively. Existing preview restoration safeguards remain unchanged.

### Live-stat snapshot semantics

Health, Stamina, and Mana are read from the live NPC `EntityStatMap` and display current
and maximum values. Defense is deliberately labeled `base` and is the authoritative sum
of equipped `ItemArmor.baseDamageResistance`; no unsupported derived aggregate is
invented. Missing components or values render `Unavailable`, never fake zero.

Every snapshot carries NPC stable ID, live entity UUID, capture time, equipment revision,
authoring session UUID, and page generation. Identity/generation drift rejects the
snapshot. Snapshots are captured on open, after gear or visibility changes, and on a
bounded two-second refresh; unchanged values do not emit UI traffic. Stats failure
degrades only the stats strip and leaves inventory/profile authoring usable.

### Automated gate and connected stop

`persistent-npcs/test.ps1 -SkipLive` passes the complete deterministic suite, including
the new R132 A3 gate, all R092–R131 inventory/profile/persistence/preview regressions,
the 8,100-scenario conversation matrix, and the existing Orbis/Sentinel/evaluation gates.
The only compiler warning remains the pre-existing deprecated `WorldChunk.getFluidId`
use. Live model tests remain intentionally skipped because A3 does not alter inference.

A3 automated status: **PASS**. Connected-client status: **PENDING**. The R132 JAR is a
validation candidate only. Work must stop after deployment for the operator's connected
acceptance; A4 and later stages remain unauthorized.

Deployed connected-test candidate:

- path: `C:\Users\Zemio\AppData\Roaming\Hytale\UserData\Saves\NPC\mods\ImmersiveNPCs-0.6.0-pre.13.1-R132-NPC-AUTHORING-STUDIO-A3-GEAR-STATS.jar`;
- size: `2,698,580` bytes;
- SHA-256: `9BE08F1F34211C7161DFB04955FD5C1197F06FE4E2C372EEDEFD9A597D72A949`;
- installed Immersive NPC JAR count: exactly one;
- build/deployed hash equality: verified.

The independently preserved R131 rollback remains unchanged at
`C:\HytaleRollback\NpcAuthoringStudio-A3-2026-09-04\ImmersiveNPCs-0.6.0-pre.13.1-R131-NPC-AUTHORING-STUDIO-A2-INVENTORY-BRIDGE.jar`
(2,667,348 bytes; SHA-256
`C6FB298934E5B1FA96DFB54FF4DF7D0373F1741645D9F44DAF929AA989840C18`).

## A4 — Profile Editor and Generate

A3 connected validation passed in full before A4 activation. R133 implemented the
profile draft/editor transaction and proposal-only Generate workflow; R133.1 corrected
the current-client label-alignment enum without changing those authorities. The
operator subsequently confirmed the editor and main workspace opened and behaved as
expected, which is the connected A4 PASS authorizing A5.

Profile edits remain server-owned drafts with stable NPC identity, base revision/hash,
field-level dirty state, unknown-root-field preservation, validation, atomic replacement,
rollback, audit, and stale-writer conflict rejection. Generate remains low-priority,
proposal-only, and incapable of committing profile canon. The accepted A4 checkpoint is
commit `3bafb58cead0d314366bc8e756c87a5fa86e39dc`.

The exact accepted A4 JAR is preserved independently for immediate A5 rollback:

- path: `C:\HytaleRollback\NpcAuthoringStudio-A5-2026-09-04\ImmersiveNPCs-0.6.0-pre.13.1-R133.1-NPC-AUTHORING-STUDIO-A4-UI-PARSER-HOTFIX.jar`;
- size: `2,735,834` bytes;
- SHA-256: `666ED5EB1A585127D4DA2141B1F05243B4B107E7EDCF3825A1B7814AE71881FF`.

## A5 — NPC Appearance Editor

### Current-build preflight and feasibility gate

A5 was audited and compiled against the actual installed release server used by the
current client:

- server version/revision: `0.6.3` / `ff802bf5a538f7e4b1df43a575c72f9d2bebb504`;
- server JAR: `C:\Users\Zemio\AppData\Roaming\Hytale\install\release\package\game\latest\Server\HytaleServer.jar`;
- size: `110,438,908` bytes;
- SHA-256: `BB4ECBE2D1BC189C22E63491EB4078C95B42EF6CBE6650CCFA3E6160AAED7102`.

Direct API inspection confirmed the current build exposes `CosmeticsModule`, its live
`CosmeticRegistry`, every `PlayerSkin` category map used by the editor, part IDs/names,
textures, gradient sets, variants, tags, compatibility descriptors, default-asset
classification, parsing, validation, random generation, and model construction. The
hard feasibility gate therefore passed without an invented appearance schema or asset
path. The protocol `PlayerSkin` is fully qualified at the codec boundary to avoid the
server/protocol type-name collision.

### Catalog and codec authority

`NpcAppearanceCatalogService` creates one immutable, lazily captured snapshot from the
live current-build registry. Its identity records the Hytale build, deterministic
registry hash, enabled-registry source-set hash, adapter version, and capture time.
Options carry category, exact cosmetic ID, display name, tags, available colors,
gradients/variants, compatibility metadata, and whether the part is a Hytale default or
an enabled registry extension. Search and pagination are local and bounded to twelve
tiles; they perform no inference request and no model warmup.

`NpcSkinCodecAdapter` is the explicit serialization boundary. The existing profile-local
`SS_Skin_Character.json` and Hytale's parser/validator remain authoritative. A draft
deep-copies the raw JSON tree and patches only the twenty known protocol fields, so
unknown future or asset-pack properties survive round trip. Missing registry values are
retained and visibly marked; there is no silent substitution. Client event IDs are
re-resolved against the pinned server catalog before validation.

### Draft, preview, save, and live application

Each `NpcAppearanceDraft` carries its own UUID, authoring session UUID, stable NPC UUID,
editor generation, base revision/hash, extension-preserving raw document, current
protocol skin, dirty categories, and preview generation. Randomize delegates to the
current Hytale cosmetics runtime. Reset restores the persisted open-time skin.

Preview reuses `NpcMeshPreviewSession`: the server builds a validated Hytale model and
sends model/skin/equipment packets only to the viewing client. It never writes the
logged-in player's ECS model, skin, or equipment. Equipment is reasserted after every
draft preview. Preview generations are newest-only, and Cancel, dirty-discard, page
dismissal, disconnect, world unload, plugin shutdown, and replacement session all
converge through ordered restoration. A successful save advances the preview's
persisted NPC target; final page close still restores the viewer's immutable baseline.

Save performs optimistic revision/hash validation, Hytale validation, temporary-file
write, reread/round-trip verification, rollback creation, atomic replacement, revision
sidecar update, and JSONL audit append. Only after persistence succeeds does it apply the
new appearance to a spawned NPC. A live-apply failure leaves saved authority intact and
marks the session degraded for reload recovery. Unspawned NPCs, including Mara, use the
same profile-local persistence path without requiring a live entity.

### A5 UI and assets

The inert appearance shell is now an interactive full-workspace editor with primary and
secondary category rails, bounded search/pagination, twelve reusable option tiles,
color/gradient and variant controls, selection/source/compatibility details, a large
gold-backed 3D preview, validation status, and the required action order:
`Randomize | Reset | Cancel | Save Appearance`.

No Hytale-owned thumbnail asset is copied or repackaged. Until a supported icon path is
exposed, the editor uses project-authored bounded text tiles and the authoritative 3D
preview, with a project-authored text fallback when preview is unavailable.

### A5 deterministic gate and connected stop

The full deterministic suite passes against the release server JAR, including the new
R134 A5 gate and every A0–A4 inventory, persistence, equipment, preview, profile,
generation, cognition, Sentinel, evaluation, and 8,100-scenario conversation regression.
The A5 gate directly verifies catalog allowlisting, bounded search/pagination, lossless
unknown-field preservation, atomic save/rollback/audit, stale-draft conflict rejection,
invalid-ID rejection, removed/missing-cosmetic retention with validation at the commit
boundary, action order, asset-ownership hygiene, and packet-only preview restoration.
Live model tests remain intentionally skipped.

The deployed connected-test candidate is:

- path: `C:\Users\Zemio\AppData\Roaming\Hytale\UserData\Saves\NPC\mods\ImmersiveNPCs-0.6.3-R134-NPC-AUTHORING-STUDIO-A5-APPEARANCE.jar`;
- size: `2,788,451` bytes;
- SHA-256: `DDDBDF6F575469476C934DE72AFFFF97DB7810A83C273089CEE96949E073DF5A`;
- installed Immersive NPC JAR count: exactly one;
- build/deployed hash equality: verified.

The accepted A4 rollback listed above remains outside the active save and is unchanged.

A5 automated status: **PASS**. Connected-client status: **PENDING**. R134.1 must remain a
validation candidate until the operator confirms registry browsing, preview changes,
save/cancel/reset/randomize, spawned and unspawned persistence, close/reopen and restart
behavior, stale/failure handling, equipment continuity, and viewer restoration. A6 is
not authorized.

### R134.1 current-client UI parser hotfix

The first R134 connection attempt exposed a current-client parser rejection at
`ImmersiveNpcProfile.ui (368:158)`: the fallback label contained a `\n` escape, while
this UI grammar permits a backslash only before another backslash or a quote. R134.1
replaces that text with parser-safe single-line content and adds a deterministic gate
that rejects unsupported backslash escapes in the complete UI document.

- deployed path: `C:\Users\Zemio\AppData\Roaming\Hytale\UserData\Saves\NPC\mods\ImmersiveNPCs-0.6.3-R134.1-NPC-AUTHORING-STUDIO-A5-UI-PARSER-HOTFIX.jar`;
- size: `2,788,471` bytes;
- SHA-256: `6496AD7A18C63A161F9C4A732AC06892C5AB219545597EE2FA55E309CC80EB08`;
- installed Immersive NPC JAR count: exactly one;
- build/deployed hash equality: verified;
- full deterministic suite: PASS against the installed `0.6.3` release server API.

The original R134 candidate is independently preserved at
`C:\HytaleRollback\NpcAuthoringStudio-A5-R134-2026-09-04\ImmersiveNPCs-0.6.3-R134-NPC-AUTHORING-STUDIO-A5-APPEARANCE.jar`
(2,788,451 bytes; SHA-256
`DDDBDF6F575469476C934DE72AFFFF97DB7810A83C273089CEE96949E073DF5A`).

### R134.2 connected acceptance and A6 rollback authority

The operator confirmed the complete A5 connected matrix as PASS on R134.2. The exact
accepted binary was reconciled before A6 work and preserved outside the active save:

- accepted/deployed filename: `ImmersiveNPCs-0.6.3-R134.2-NPC-AUTHORING-STUDIO-A5-POLISH.jar`;
- accepted binary size: `2,924,533` bytes;
- accepted binary SHA-256:
  `CABEB3D78BF88B9755498AEDD5F8EE29ED1711039F84EEBCC3721B9851631222`;
- independent rollback:
  `C:\HytaleRollback\NpcAuthoringStudio-A5-R134.2-CONNECTED-PASS-2026-09-04\ImmersiveNPCs-0.6.3-R134.2-NPC-AUTHORING-STUDIO-A5-POLISH.jar`;
- source checkpoint: `e4f8d67ca5b9fd829e17e430049ffb2975a1d8c4`.

## A6 — Voice Recorder

### Current-build voice preflight

The A6 implementation was reconciled against the installed Hytale 0.6.3 voice API and
the current Orbis/Moonshine/Chatterbox paths. `VoiceModule` exposes priority-ordered
`PlayerVoiceInterceptor` registration, callback-owned `PlayerVoiceFrame` Opus bytes,
sequence/timestamp metadata, final delivery suppression through `drop()`, and creator-
scoped playback through `openDirectVoice(Collection<UUID>)` plus `VoiceSpeaker.play`.
The installed `VoiceSpeaker` enforces a maximum of 512 bytes per Opus frame; it does not
impose a 512-frame clip limit. The existing local worker already owns PyAV/Opus and is
therefore reused for model-free decode/encode instead of introducing a second codec
stack.

### Exclusive capture and privacy boundary

`VoiceCaptureLeaseManager` is the single per-player authority for `NONE`,
`ORBIS_CONVERSATION`, and `VOICE_SAMPLE_RECORDING`. Orbis now checks that lease before
copying an Opus frame or admitting any STT work. The recorder is registered at
`EventPriority.LAST`, after the Orbis interceptor, and drops accepted microphone frames
before Hytale performs final routing. Its callback is limited to session/generation
lookup, frame validation, a bounded opaque-byte copy, sequence/timestamp copy, drop,
non-blocking queue offer, and return. Decode, analysis, disk access, UI work, Moonshine,
and Chatterbox never run in the callback.

Capture is bounded to one recorder per player, four concurrent sessions, 1,600 queued
frames, 1 MiB, a five-second armed timeout, and thirty seconds. Queue overflow, no input,
timeout, invalid frame sizes, stale generations, excessive gaps/out-of-order frames,
short audio, silence, and clipping fail the recorder attempt visibly without modifying
an authoritative sample. Stop releases the microphone lease before finalization.
Page/editor close, disconnect, world/session cleanup, playback stop, and plugin shutdown
invalidate late work and idempotently release frames, drafts, speakers, registrations,
and leases.

### Finalization, playback, persistence, and readiness

After Stop, ordered Opus frames are decoded off-thread by the existing STT-role worker
without calling transcription or warming a speech model. The worker produces 48 kHz
mono PCM16 WAV plus duration, peak dBFS, RMS dBFS, clipping, silence, decode time, and a
bounded waveform envelope. Raw Opus, PCM, and WAV data never crosses the Custom UI
payload boundary and is never logged.

Draft playback reuses the ordered captured Opus frames. Saved-sample playback uses the
same worker only for WAV-to-Opus encoding and does not load Chatterbox. Both use a direct
voice speaker whose audience is exactly the creator. Playback holds the recording lease,
so its audio cannot enter Orbis capture, STT, NPC turns, memory, beliefs, relationships,
tasks, actions, or the canonical speech ledger. Playback generation checks prevent a
late saved-WAV encode from opening a speaker after Stop, editor close, disconnect, or
session replacement.

`NpcVoiceSamplePersistenceService` writes profile-local temporary drafts and retains
`VoicePresetRepository` as canonical ownership. Save rechecks the per-emotion SHA-256,
validates a temporary sibling WAV, preserves a rollback copy, atomically replaces the
canonical sample, rereads and hashes it, appends a metadata-only audit event, rescans
voice readiness, and invalidates resident Chatterbox conditioning aliases. Failure
retains the prior canonical sample and, where safe, the draft. Delete moves a canonical
sample to recoverable profile-local trash, rescans, and invalidates conditioning.
Reference deletion has a stronger confirmation warning; missing optional emotions keep
the existing Reference fallback semantics.

### A6 UI and deterministic gate

The Voice Recorder workspace now exposes the seven canonical emotions and their saved
statuses, a conspicuous recording indicator, authoritative elapsed/max duration,
bounded waveform, quality metrics, readiness feedback, Record, Stop, private draft and
saved playback, Stop Playback, Record Again, Delete Draft, Save Sample, recoverable
Delete Saved confirmation, and Return to Studio. `Save Sample` remains the final commit
action. Every event is allowlisted and revalidates authoring session, viewer, stable NPC,
page/editor/recording generations, active editor, and
`immersivenpcs.authoring.voice` permission.

The full deterministic suite passes against the installed API, including the new R135
gate and every prior A0–A5 inventory, gear, profile, appearance, cognition, Sentinel,
evaluation, and 8,100-scenario conversation regression. The R135 gate exercises capture
exclusivity/no dual admission, frame bounds, sequence wrap/gaps/duplicates/order,
short/silent/clipped/valid quality policy, all seven canonical emotion paths, atomic
save/replacement, rollback, stale conflict rejection, injected draft-read failure,
recoverable optional/Reference deletion, readiness/fallback, rescan, cache invalidation,
path containment, temp cleanup, privacy/model-free source contracts, state machine,
lifecycle cleanup markers, UI selectors, manifest, and installer identity. Live model
tests remain intentionally skipped.

The R135 connected-test candidate is:

- source artifact:
  `C:\HytaleMigration\persistent-npcs\dist\ImmersiveNPCs-0.6.3-R135-NPC-AUTHORING-STUDIO-A6-VOICE-RECORDER.jar`;
- deployed artifact:
  `C:\Users\Zemio\AppData\Roaming\Hytale\UserData\Saves\NPC\mods\ImmersiveNPCs-0.6.3-R135-NPC-AUTHORING-STUDIO-A6-VOICE-RECORDER.jar`;
- size: `2,975,631` bytes;
- SHA-256: `67D50B949F1CC260EAFAF4DDD9E83ED208FDF2E758B68444E10BD80BFDA237B2`;
- source/deployed hash equality: verified;
- installed Immersive NPC JAR count: exactly one.

A6 automated status: **PASS**. Connected-client status: **PENDING**. A7 remains
unauthorized and was not started.

### R136 bounded A6 connected-validation repair

R136 preserves the R135 privacy, capture-lease, persistence, rollback, inventory,
gear, profile, and appearance authorities while repairing the connected-validation
findings. The recorder presentation is now the approved compact arrangement:
`Record | Play/Stop | Delete`, followed by a full-width `Save Sample`. The Play control
becomes Stop while capture is armed/recording or private playback is active. Delete
removes a draft immediately and retains the existing recoverable confirmation before
deleting a canonical saved sample. The five project-owned recorder icons are packaged
with the UI, and the corrected alpha versions of the sword and shield slot assets replace
the prior copies without changing slot behavior.

The Unicode waveform approximation is removed. The existing model-free Opus decode now
feeds its real bounded 32-bucket PCM peak envelope into 32 centered UI bars. Silence is a
two-pixel center line and larger decoded peaks yield proportionally taller, clamped bars.
Selecting a canonical saved sample starts a model-free WAV analysis in the STT-role
worker; it does not invoke Moonshine transcription or Chatterbox. The callback is guarded
by recording generation, emotion identity, draft absence, and canonical sample revision,
so late analysis is rejected and can never repaint a newer draft. Raw Opus, PCM, and WAV
remain outside Custom UI.

The installed Hytale 0.6.3 contract was decompiled before this repair. Client-to-server
microphone media is `VoiceData`; server-to-client `VoiceConfig` exposes server enablement,
codec/rate/channel and distance limits but no capture/start or input-mode command.
`PlayerVoiceSettings` is a client-reported ECS snapshot whose modes are `VoiceActivity`,
`PushToTalk`, and `PushToTalkToggle`. `VoiceModule` can intercept frames already sent and
can suppress/reroute them, but it cannot activate the client microphone. Therefore the
requested independent PTT bypass is not implementable in 0.6.3 without changing client
settings or requiring the client PTT control. R136 does neither: it reads the mode only,
states the exact limitation, preserves final-interceptor dropping/no-broadcast behavior,
and restores all prior voice behavior because no client setting is ever mutated.

`/npc create <name>` now creates a project-owned neutral, mostly-undressed, registry-valid
skin scaffold with the stable profile and inventory. Missing legacy skins receive the
same canonical scaffold. Existing malformed authored skin bytes are never overwritten at
open: Studio continues in a degraded state with a temporary neutral preview, and a repair
draft fingerprints the original bytes so an explicit Save remains optimistic and creates
a rollback before replacement. Preview creation failure is logged but is no longer a
prerequisite for opening Studio. No creator appearance is read or copied.

The R136 deterministic gate covers recorder control transitions, bounded waveform height
propagation, stale waveform rejection, read-only PTT/Open-Mic contract interpretation,
recorder-to-conversation isolation, missing-skin creation/reopen, malformed-byte
preservation, packaged assets, and preview-as-consumer behavior. The full deterministic
suite, including the 8,100-scenario conversation matrix, passes with live model tests
intentionally skipped. A7 was not started.

The R136 connected-test candidate is:

- source artifact:
  `C:\HytaleMigration\persistent-npcs\dist\ImmersiveNPCs-0.6.3-R136-NPC-AUTHORING-STUDIO-A6-VOICE-RECORDER-HOTFIX.jar`;
- deployed artifact:
  `C:\Users\Zemio\AppData\Roaming\Hytale\UserData\Saves\NPC\mods\ImmersiveNPCs-0.6.3-R136-NPC-AUTHORING-STUDIO-A6-VOICE-RECORDER-HOTFIX.jar`;
- size: `3,004,042` bytes;
- SHA-256: `1841F365F108345984FC0C4BDF2E4D5C1B84140546179FEF030610C17E0538D0`;
- source/deployed hash equality: verified;
- installed Immersive NPC JAR count: exactly one;
- accepted R134.2 rollback and pre-hotfix R135 snapshot: preserved outside the game
  directory under `C:\HytaleRollback`.

A6 automated hotfix status: **PASS**. Connected-client validation status: **PENDING**.
A7 remains unauthorized and was not started.

### R137 A6 compact-recorder event-binding hotfix

The first R136 connected open failed before interaction because
`bindVoiceRecorderEvents` still registered the removed legacy selector
`#VoiceDeleteSavedButton`. The R136 compact presentation had consolidated that action
into `#VoiceDeleteButton`; Hytale therefore rejected the complete event-binding packet
with `Target element in CustomUI event binding was not found`. R137 removes only that
stale registration. The saved-sample confirmation overlay and its Confirm/Cancel event
bindings remain intact, so the consolidated Delete control still preserves recoverable
saved-sample deletion.

The R137 regression enumerates every selector declaration in the recorder-binding
method, expands the seven dynamic emotion selectors, and proves each target exists in
the packaged UI document. It also rejects any return of the removed legacy selector.
The full deterministic suite passes, including R135, R136, R137, and the 8,100-scenario
conversation matrix.

The R137 connected-test candidate is:

- source artifact:
  `C:\HytaleMigration\persistent-npcs\dist\ImmersiveNPCs-0.6.3-R137-NPC-AUTHORING-STUDIO-A6-VOICE-RECORDER-BINDING-HOTFIX.jar`;
- deployed artifact:
  `C:\Users\Zemio\AppData\Roaming\Hytale\UserData\Saves\NPC\mods\ImmersiveNPCs-0.6.3-R137-NPC-AUTHORING-STUDIO-A6-VOICE-RECORDER-BINDING-HOTFIX.jar`;
- size: `3,004,030` bytes;
- SHA-256: `9FE965B65F27250FEDDD10DC46C26A0292AE8F9D5250F395FDDFCF9E74CF4198`;
- source/deployed hash equality: verified;
- installed Immersive NPC JAR count: exactly one;
- failed R136 artifact: preserved independently at
  `C:\HytaleRollback\NpcAuthoringStudio-A6-R136-FAILED-BINDING-2026-09-04`.

A6 R137 automated status: **PASS**. Connected-client validation status: **PENDING**.
A7 remains unauthorized and was not started.

### R138 A6 waveform-property hotfix

R137 allowed the Studio to open, but entering Voice Recorder disconnected the client
because `#VoiceWaveformBar0.Anchor.Height` is not a mutable Hytale Custom UI markup
property. The installed 0.6.3 server API exposes `Anchor.CODEC`, registers `Anchor` in
`UICommandBuilder.setObject`, and accepts the complete `Anchor` property as a command
target. R138 therefore sends each real waveform height by replacing
`#VoiceWaveformBarN.Anchor` atomically with a codec-backed Anchor that preserves the
five-pixel width and carries the bounded dynamic height. No synthetic waveform or raw
audio is introduced.

The R138 installed-API gate constructs and serializes the exact Anchor update, verifies
the emitted selector and width/height payload, and rejects any return of nested
`.Anchor.Height`. The full deterministic suite passes, including R135–R138 and the
8,100-scenario conversation matrix.

The R138 connected-test candidate is:

- source artifact:
  `C:\HytaleMigration\persistent-npcs\dist\ImmersiveNPCs-0.6.3-R138-NPC-AUTHORING-STUDIO-A6-VOICE-RECORDER-WAVEFORM-HOTFIX.jar`;
- deployed artifact:
  `C:\Users\Zemio\AppData\Roaming\Hytale\UserData\Saves\NPC\mods\ImmersiveNPCs-0.6.3-R138-NPC-AUTHORING-STUDIO-A6-VOICE-RECORDER-WAVEFORM-HOTFIX.jar`;
- size: `3,004,183` bytes;
- SHA-256: `72000DE6FEB642DFB524E162EE00DBC3A89DCA64D1DA342A29F7C23B954A999A`;
- source/deployed hash equality: verified;
- installed Immersive NPC JAR count: exactly one;
- R137 artifact: preserved independently at
  `C:\HytaleRollback\NpcAuthoringStudio-A6-R137-BINDING-HOTFIX-2026-09-04`.

A6 R138 automated status: **PASS**. Connected-client validation status: **PENDING**.
A7 remains unauthorized and was not started.

### R139 P1 Voice Recorder polish

R139 is a bounded presentation-only polish pass over the accepted R138 recorder
behavior. It does not change voice capture, privacy, transaction, persistence,
waveform-analysis, playback, deletion, rollback, inventory, gear, profile, or
appearance authority. The recorder is now a centered, bounded 520-by-780 panel with a
framed header, stronger world-background separation, a compact framed emotion list,
aligned selected-sample and elapsed-time metadata, and a double-framed waveform well.
The real 32-bucket waveform and its codec-backed Anchor updates are unchanged.

The visible controls remain exactly the approved compact set: Record, state-aware
Play/Stop, Delete, full-width Save Sample, and Return to Studio. The packaged
record/play/stop/delete icon assets remain in use. Saved emotion states now read
`SAVED`, an unsaved required Reference reads `REQUIRED`, optional missing samples use a
quiet em dash, and invalid samples remain conspicuously red. Typography, spacing,
padding, row heights, and button hierarchy were tightened without changing event IDs or
control behavior.

The R139 gate verifies the recorder's bounded geometry against both 1920-by-1080 and
2560-by-1440 canvases, the complete compact-control and icon contract, waveform framing,
privacy copy, status vocabulary, absence of legacy redundant controls, and preservation
of all four recorder event intents. The full deterministic suite passes with live model
tests intentionally skipped. An incidental unrequested live-model benchmark run reached
the local model but failed on a nondeterministic dialogue response; it is unrelated to
this UI-only change and is not part of the deterministic promotion gate.

The R139 P1 connected-test candidate is:

- source artifact:
  `C:\HytaleMigration\persistent-npcs\dist\ImmersiveNPCs-0.6.3-R139-NPC-AUTHORING-STUDIO-A6-VOICE-RECORDER-P1-POLISH.jar`;
- deployed artifact:
  `C:\Users\Zemio\AppData\Roaming\Hytale\UserData\Saves\NPC\mods\ImmersiveNPCs-0.6.3-R139-NPC-AUTHORING-STUDIO-A6-VOICE-RECORDER-P1-POLISH.jar`;
- size: `3,004,466` bytes;
- SHA-256: `ECC6C5CB73A2B27039D9C7AB9E3F7667905125302634386B1B843C5F1E035120`;
- source/deployed hash equality: verified;
- installed Immersive NPC JAR count: exactly one;
- R138 artifact: preserved independently at
  `C:\HytaleRollback\NpcAuthoringStudio-A6-R138-WAVEFORM-HOTFIX-2026-09-04`.

P1 automated status: **PASS**. P1 connected-client validation status: **PENDING**.
P2 Appearance Editor polish and P3 Main Studio polish were not started and remain
blocked pending explicit P1 connected approval.

### R140 P1 decorated-frame and waveform-containment polish

R139's functional layout was retained but was not accepted visually. R140 is a second
P1-only presentation pass using the approved compact mockup as the direct composition
target. The raw panel shell is replaced by Hytale's proven `DecoratedContainer` with its
native integrated title/header, ornamental outer frame, and content hierarchy. A fully
opaque project-owned inner surface separates the recorder from the world while retaining
the Hytale frame around it. Emotion rows now use quieter unselected treatment, consistent
bullets, a right-side selected-sample marker, tighter padding, and concise saved states.

The waveform defect was caused by placing the center line and bar layer as two siblings
inside a centering layout, which arranged them beside one another. R140 places both in a
single fixed 448-by-116 canvas inside a framed 468-by-124 viewport. The two-pixel center
line is fixed at vertical offset 57 and uses light blue over a dark blue background. All
32 five-pixel waveform bars remain centered within that same canvas, and the existing
bounded 2–116 pixel Anchor heights fit entirely inside it. Audio-derived amplitude data,
waveform analysis, codec-backed updates, and recorder behavior are unchanged.

The generation/capture-contract line, quality diagnostics, generic action message, and
profile-readiness sentence are no longer exposed. Their selectors remain hidden so the
existing server update packet stays valid. The only informational footer copy is
`Format: WAV • 16bit • 48kHz`. Record, state-aware Play/Stop, Delete, Save Sample, and
Return to Studio retain their existing selectors, events, icons, and authority.

The new R140 gate verifies the decorated hierarchy, opaque surfaces, fixed waveform
viewport and centered line, all 32 contained buckets, hidden legacy status targets,
approved format footer, compact controls, and unchanged recorder event intents. The full
deterministic suite passes with live local-model tests skipped. P2 and P3 remain untouched.

The R140 P1 connected-test candidate is:

- source artifact:
  `C:\HytaleMigration\persistent-npcs\dist\ImmersiveNPCs-0.6.3-R140-NPC-AUTHORING-STUDIO-A6-VOICE-RECORDER-P1-FRAME-POLISH.jar`;
- deployed artifact:
  `C:\Users\Zemio\AppData\Roaming\Hytale\UserData\Saves\NPC\mods\ImmersiveNPCs-0.6.3-R140-NPC-AUTHORING-STUDIO-A6-VOICE-RECORDER-P1-FRAME-POLISH.jar`;
- size: `3,004,534` bytes;
- SHA-256: `73973E7B09AE6FBBCA5523C9617E5186CFFF62062BFD5200A47A9DEC1E2FD9CB`;
- source/deployed hash equality: verified;
- installed Immersive NPC JAR count: exactly one;
- R139 rollback: preserved independently at
  `C:\HytaleRollback\NpcAuthoringStudio-P1-R139-2026-09-04`.

P1 R140 automated status: **PASS**. Connected-client validation status: **PENDING**.
P2 Appearance Editor polish and P3 Main Studio polish remain blocked.

### R141 P1 action chrome, Back navigation, and selected-state polish

R141 is the third bounded P1 candidate and changes only Voice Recorder presentation and
child-editor navigation. Capture, playback, waveform derivation, persistence, validation,
privacy isolation, and every existing recorder intent remain unchanged. Record, Play/Stop,
Delete, and Save Sample now use Hytale's shipped destructive, secondary, and primary
`ButtonStyle` definitions. Those definitions supply the same framed normal, hover, pressed,
and disabled states used by first-party UI while retaining the project-owned recorder icons
and the equal three-button control row.

The large `RETURN TO STUDIO` control was removed. The page now exposes one native
`BackButton`. While Voice Recorder is active, the page lifetime is `CantClose` and that
button routes to the existing `CLOSE_EDITOR` event; the handler stops active recording or
playback before applying the existing unsaved-draft decision and returning to Studio. When
no child Voice editor is active, lifetime returns to `CanDismiss`, so Back from the main
Studio closes the page normally. This preserves the existing session and recorder cleanup
owners rather than adding a second navigation path.

The recorder frame is reduced from 520-by-780 to 520-by-720 after removing the obsolete
return row. The fixed waveform geometry remains 448-by-116 with 32 genuine amplitude
buckets, but bars and baseline now use restrained blue-gray colors (`#6d8798` and
`#496274`). Emotion selection now moves a gold framed marker and arrow with the selected
emotion, while the selected name receives a gold accent and SAVED/INVALID/required state
copy remains smaller and right-aligned. The sole visible informational footer remains
`Format: WAV • 16bit • 48kHz`, with slightly improved contrast and no added status noise.

The R141 deterministic gate checks the tightened 1080p/1440p-safe frame, native stateful
button styles, preserved icon/control contract, moving selected-emotion treatment, subdued
waveform palette, removal of the in-panel return button, child-editor Back routing, and
capture/playback quiescence before navigation. The complete deterministic suite passed,
including R135–R141 and the 8,100-scenario conversation matrix; live local-model tests were
intentionally skipped. P2 and P3 were not started.

The R141 P1 connected-test candidate is:

- source artifact:
  `C:\HytaleMigration\persistent-npcs\dist\ImmersiveNPCs-0.6.3-R141-NPC-AUTHORING-STUDIO-A6-VOICE-RECORDER-P1-UX-POLISH.jar`;
- deployed artifact:
  `C:\Users\Zemio\AppData\Roaming\Hytale\UserData\Saves\NPC\mods\ImmersiveNPCs-0.6.3-R141-NPC-AUTHORING-STUDIO-A6-VOICE-RECORDER-P1-UX-POLISH.jar`;
- size: `3,004,880` bytes;
- SHA-256: `6BA00DBC16CEBA031EEAF4FA3274FE601E8E3EC28D1CE591B1B280F4337A835D`;
- source/deployed hash equality: verified;
- installed Immersive NPC JAR count: exactly one;
- R140 rollback: preserved independently at
  `C:\HytaleRollback\NpcAuthoringStudio-P1-R140-2026-09-04` with SHA-256
  `73973E7B09AE6FBBCA5523C9617E5186CFFF62062BFD5200A47A9DEC1E2FD9CB`.

P1 R141 automated status: **PASS**. Connected-client validation status: **PENDING**.
P2 Appearance Editor polish and P3 Main Studio polish remain blocked pending explicit P1
approval.

### R142 P1 UI-load and neutral default-appearance hotfix

R142 repairs the client-entry blocker introduced by R141 without changing recorder
authority or beginning P2. The installed Hytale 0.6.3 Custom UI document exports the
stateful button styles as `CancelButtonStyle`, `SecondaryButtonStyle`, and
`DefaultButtonStyle`; it does not export the main-menu names `DestructiveButtonStyle` or
`PrimaryButtonStyle`. The recorder now uses only the three Custom UI exports, preserving
the red destructive, neutral, and primary hierarchy while allowing the UI documents to
load.

The missing-appearance lifecycle was re-verified for the connected Hoit report. New NPC
creation materializes the packaged neutral appearance, and update/reopen materializes the
same appearance only when a new or legacy NPC has no appearance file. The canonical
template is a valid bald base character wearing only `Boxer.Red`, matching Hytale's reset
avatar starting point. Existing authored appearances are not replaced; malformed authored
files remain preserved and use only a temporary valid preview. Hoit's currently missing
`SS_Skin_Character.json` is intentionally left untouched on disk before connected testing
so `/npc update Hoit` proves the repair path itself.

No voice sample was modified. Draft deletion remains limited to the unsaved draft. Saved
sample deletion remains confirmation-gated and moves the prior WAV into recoverable
`.voice-trash` storage rather than permanently deleting it. The R142 regression gate locks
the valid Custom UI symbols, both missing-appearance materialization paths, the neutral
template contract, and the saved-audio confirmation/recovery contract.

The complete deterministic suite passed, including R135-R142, the 8,100-scenario
conversation matrix, and all earlier inventory, gear, profile, appearance, persistence,
and voice-isolation gates; live local-model tests were intentionally skipped.

The R142 connected-test candidate is:

- source artifact:
  `C:\HytaleMigration\persistent-npcs\dist\ImmersiveNPCs-0.6.3-R142-NPC-AUTHORING-STUDIO-A6-VOICE-RECORDER-P1-UI-LOAD-DEFAULT-APPEARANCE-HOTFIX.jar`;
- deployed artifact:
  `C:\Users\Zemio\AppData\Roaming\Hytale\UserData\Saves\NPC\mods\ImmersiveNPCs-0.6.3-R142-NPC-AUTHORING-STUDIO-A6-VOICE-RECORDER-P1-UI-LOAD-DEFAULT-APPEARANCE-HOTFIX.jar`;
- size: `3,004,872` bytes;
- SHA-256: `589CA0232818FBAA766CB6E4FE6CE6C4B2268BC8240537582F283F82F5E47DC9`;
- source/deployed hash equality: verified;
- installed Immersive NPC JAR count: exactly one;
- R141 rollback: preserved independently at
  `C:\HytaleRollback\NpcAuthoringStudio-P1-R141-2026-09-04` with SHA-256
  `6BA00DBC16CEBA031EEAF4FA3274FE601E8E3EC28D1CE591B1B280F4337A835D`.

P1 R142 automated status: **PASS**. Connected-client validation: server entry restored,
but Hoit update failed with `Unknown face: Face`; superseded by R143 below.
P2 Appearance Editor polish and P3 Main Studio polish remain blocked pending explicit P1
approval.

### R143 neutral appearance registry repair and accurate revision display

The September 4 15:15 client log confirms that the plugin and asset pack loaded the R142
JAR. The HUD nevertheless displayed R135 because `PersistentNpcsPlugin.REVISION` was
stale; the manifest still reported R140. R143 aligns the HUD, log revision, manifest,
builder, and installer. A new build gate rejects disagreement between these identifiers.

Hoit's update failure was a real defect in the packaged default: `Face` is not a face ID
in the installed cosmetics registry. The previous mock validator checked only field
presence, so the earlier claim that the template had been validated against Hytale was
too strong. The corrected face is `Face_Neutral`, as listed in
`Cosmetics/CharacterCreator/Faces.json`. Underwear is now `Suit.Red`, the red base garment
shown in the user's reset-avatar screenshot. The template remains bald and barefoot.

`validate-release-resources.ps1` runs on every build and checks all six neutral appearance
selections, their explicit gradient values, and their model/texture files against the
Assets.zip adjacent to the selected server installation. The corrected template passed
against both installed release and pre-release registries. A deliberate stale artifact
name was rejected by the version gate. Existing create, reopen, restart, and malformed
appearance preservation coverage also passed. Hoit's missing skin will be materialized
through the normal update path; no runtime profile, appearance, or voice file was edited
during deployment.

The first full-suite run hit an existing `ConcurrentModificationException` in
`R046OrbisInterruptionTest` while iterating its asynchronous event list. A full rerun
passed, including the 8,100-scenario conversation matrix. Live model tests were skipped;
connected Hoit creation/reopening and R143 HUD confirmation remain pending.

- Deployed JAR: `C:\Users\Zemio\AppData\Roaming\Hytale\UserData\Saves\NPC\mods\ImmersiveNPCs-0.6.3-R143-NPC-AUTHORING-STUDIO-A6-VOICE-RECORDER-DEFAULT-SKIN-HOTFIX.jar`
- Size: `3,004,927` bytes; SHA-256: `4A30743AC398B2CE8032DE75E965483A19B4FC613AA1B1AF067A8226B381E69F`.
- Source/deployed hashes match; exactly one active ImmersiveNPCs JAR.
- R142 moved into `C:\HytaleRollback\NpcAuthoringStudio-P1-R142-2026-09-04`; hash `589CA0232818FBAA766CB6E4FE6CE6C4B2268BC8240537582F283F82F5E47DC9`.

P1 remains pending connected approval. P2 and P3 have not begun.

### R144 P1 compact action row and explicit header Back

R144 places Record, Play/Stop, Delete, and Save in one equal-height, equal-width row.
Save uses the user's `NpcIconSave.png`, copied unchanged from the synced Hytale Taverns
Drive folder into `Common/UI/Custom/Pages/ImmersiveNpcInventory`. Its source and packaged
file SHA-256 is `70FCAE051ABE9CF56636AF79118E543B3DF88E7CB5CA52CE8DEA765C85756D82`;
the PNG is 64-by-64 with alpha. All four controls retain the shipped Custom UI button
styles and their default, hover, pressed, and disabled backgrounds. Existing intent IDs,
Play/Stop policy, and READY-only Save eligibility are unchanged.

The frame is reduced from 520-by-720 to 520-by-600. A secondary 64-by-24 `BACK` button
sits at the upper-left of the title bar and invokes the existing `CLOSE_EDITOR` handler.
The native `BackButton` event remains bound to that same handler when Voice Recorder is
active. Controller delivery of that event still requires connected confirmation; no
global Escape binding or input interception was added. Capture/playback quiescence,
unsaved-draft confirmation, and final handle closure still use the existing cleanup path.
If an unsaved draft exists, returning to Studio retains the normal save/discard decision.

The real waveform generation, geometry, and restrained blue-gray palette are unchanged.
Emotion selection retains its gold marker/arrow; saved/invalid/missing labels now use End
alignment in their existing right-hand columns. The format footer remains unchanged.

Validation: the full deterministic suite passed, including voice state/lease/cleanup,
waveform, persistence, binding, and P1 gates plus the 8,100-scenario matrix. The existing
binding gate was updated for the new ninth declaration. All referenced Custom Common.ui
exports were checked against the installed assets. Static layout calculations place the
frame at (700,240) on 1920-by-1080 and (1020,420) on 2560-by-1440, with four 113.5-by-44
buttons and 6-pixel gaps. These are layout/asset checks, not connected render captures;
visual clipping, icon appearance, mouse states, and controller delivery remain pending
the user's in-game review. Live local-model tests were skipped.

- Deployed JAR: `C:\Users\Zemio\AppData\Roaming\Hytale\UserData\Saves\NPC\mods\ImmersiveNPCs-0.6.3-R144-NPC-AUTHORING-STUDIO-A6-VOICE-RECORDER-P1-COMPACT-CONTROLS.jar`
- Size: `3,007,760` bytes; SHA-256: `9B8036A737D0430B3D051660A31FF8189BE05E625A91BCDFF4E3B99F36D58B53`.
- Source/deployed hashes match; exactly one active JAR. HUD and manifest identify R144.
- R143 rollback: `C:\HytaleRollback\NpcAuthoringStudio-P1-R143-2026-09-04`, hash `4A30743AC398B2CE8032DE75E965483A19B4FC613AA1B1AF067A8226B381E69F`.

Connected checklist (repeat visual checks at 1080p and 1440p): verify all four action
buttons and icons, hover/pressed/disabled states, READY-only Save, Play changing to Stop,
emotion selection/status alignment, waveform containment, and tight footer spacing.
Use header Back while idle, recording, and playing; complete the unsaved-draft decision
when shown, confirm Studio returns, and reopen the recorder to verify capture/playback
can start again. Test controller Back if available; confirm Escape still opens Hytale's
own menu and normal Studio exit still works. Existing saved NPC audio was not modified.

P1 R144 awaits connected approval. P2 and P3 remain unstarted.

### R145 P1 header parser hotfix

R144 connected testing failed at server entry. Client log
`2026-09-04_15-57-47_client.log` reports `Pages/ImmersiveNpcProfile.ui (409:75) –
Expected {, found =`. The title template instance placed `@Text` after its `Anchor`
property. R145 moves the positioning into an enclosing Group and leaves the title
instance with its template parameter only. This uses the existing title template
syntax while preserving the compact header Back and four-action layout.

The full deterministic suite passed, including the recorder and 8,100-scenario matrix.
Those tests do not run the Hytale client document parser; connected loading and P1
visual approval remain pending. No recorder/audio/appearance/persistence behavior changed.
All release counters identify R145. P2 and P3 remain unstarted.

- Sole deployed JAR: `C:\Users\Zemio\AppData\Roaming\Hytale\UserData\Saves\NPC\mods\ImmersiveNPCs-0.6.3-R145-NPC-AUTHORING-STUDIO-A6-VOICE-RECORDER-P1-HEADER-HOTFIX.jar`
- Size: `3,007,761` bytes; source/deployed SHA-256: `0818ADE5E2BCEA48DAAD8542147FDBFA441FEB0A4D5606C91EAFBD67705E4AD4`.
- R144 retained in `C:\HytaleRollback\NpcAuthoringStudio-P1-R144-2026-09-04`; earlier R143 rollback remains available.
- Retest: restart the world, confirm server entry and R145 HUD, open Voice Recorder,
  confirm header Back and all four actions, then continue the R144 connected checklist.

### R146 compact NPC Profile main-menu polish

Scope is the user's new main-menu milestone, not a continuation into Appearance
Editor polish or an implicit approval of earlier recorder candidates. Baseline:
`601e9bb1b2d7a594533a086799deb43290134f6f` on clean `main`.

The overview is now a centered 1180-by-890 composition with two native decorated
containers: Profile above, Inventories below. The integrated title is
`<NPC NAME>'S NPC PROFILE`. The left rail contains Overview, Inventory, Appearance,
Profile Editor, and Voice Recorder. The two local selection actions pass the existing
session/envelope/permission gate; they update only selected-state visibility and never
rebuild inventories or execute transfers. Child-editor buttons keep their existing
actions and cleanup/draft lifecycle.

The central NPCBackground preview retains authoritative rendering. Four compact framed
stats sit below it; unresolved values are a quiet dash. Gear sits entirely to its right
in two labeled columns, retaining the original 58-pixel slots, row ordering, armor
visibility toggles, section IDs, filters, and drop bindings. Infinite Ammo remains nearby.
Overview metadata, file controls, profile summary, and voice table are hidden under
`ProfileAssetsPanel`, not deleted. Delete/Enter remain behind a hidden ancestor.
The sole visible footer action is secondary `CLOSE PROFILE`, using the existing
Cancel/close cleanup. Main Profile shows no unverified Escape/controller hint.

Both inventory grids use Profile-specific documents with 48-pixel square slots
(504-by-204 grid bounds). They retain the exact prior construction-time section
bindings, 10-column ordering, native drag flags, ContainerWindow, and server-authoritative
bridge. All IDs 1–1024 are packaged; player storage remains section -2. Probe documents
are unchanged. The two framed gold carets are hit-test-disabled Groups with no event
bindings or transfer logic.

Native artwork was copied unchanged from the installed release client Interface tree:
Common/DefaultDropdownCaret and DefaultDropdownCaretLeft; MyAvatar category
BodyCharacteristic and Head; AvatarPreset/IconEdit; Hud/InputBindingIconInventory;
Hud/Voice/VoiceMicOn. These are packaged under the existing ImmersiveNpcInventory
asset directory, with ProfileNav names for the rail. Existing NPCBackground and
corrected equipment artwork are reused.

Validation:

- Full `test.ps1 -SkipLive` passed, including inventory/gear/persistence, session
  authority, appearance, profile, recorder/privacy/cleanup, and the 8,100-scenario matrix.
- R146 gate checks hidden metadata/actions, unique navigation controls, selection-only
  navigation, decorative arrows, resource signatures, section documents 1–1024, and
  identical compact/probe grid behavior after excluding geometry.
- Older UI tests were adjusted only for the new document paths, frame/title layout,
  quiet unavailable-stat presentation, and revision naming. The recorder packaging
  check now matches the current revision and verifies the voice worker remains packaged.
- Release build passed. Neutral cosmetics were checked against the exact installed
  registry; all 16 referenced Custom Common.ui exports were found. Child editor
  document content is unchanged from R145. R145 title parameter ordering has regression
  coverage. No actual Hytale client parser or connected renderer was run.
- Static bounds: main frame starts at (370,95) at 1920x1080 and (690,275) at
  2560x1440. Top preview/stat stack uses 414 of 422 available pixels; lower
  inventory label/grid stack uses 232 of 238. Each 504-pixel grid fits a 542-pixel
  column. These calculations do not prove rendering under client UI scaling.

Deployment:

- Sole active JAR: `C:\Users\Zemio\AppData\Roaming\Hytale\UserData\Saves\NPC\mods\ImmersiveNPCs-0.6.3-R146-NPC-PROFILE-MAIN-MENU-POLISH.jar`
- Size: 3,312,226 bytes; matching source/deployed SHA-256:
  `38D97B3FFD0143A2282A496DF01C1855C18B9242865F93F41A516BC931A253A3`.
- R145 moved intact to `C:\HytaleRollback\NpcAuthoringStudio-Profile-R145-2026-09-04`;
  SHA-256 `0818ADE5E2BCEA48DAAD8542147FDBFA441FEB0A4D5606C91EAFBD67705E4AD4`.
- HUD, manifest, build, and installer all identify R146. No NPC runtime files,
  established voice samples, archival backups, or distillation files were changed.

Connected acceptance (repeat visual checks at 1080p and 1440p):

1. Restart the NPC world, verify R146 in the HUD, and open `/npc update Mara`
   and a spawned NPC. Check centered frames, integrated title, opacity, slot labels,
   preview, stats, all inventory rows, and no overlap/clipping.
2. Select Inventory, then Overview. Check gold selection/focus treatment and that
   the same inventories remain. Open all three child editors, return, and close/reopen.
3. Move items both directions and within each inventory; test merging, occupied-slot
   swaps, quick movement, invalid/full destinations, valid/invalid gear, visibility,
   and ammo dependencies. Confirm preview/live-NPC parity and authoritative stats.
4. Click both decorative arrows: nothing should happen. Verify item counts before
   and after transactions. Close/reopen and restart to check persistence.
5. Confirm player appearance/equipment is restored, including after child editors,
   and existing profile, appearance, and voice data is intact.

STOP: R146 is deployed for connected approval. No further polish milestone or QA
promotion is started by this pass.

## R147 — bounded NPC Profile repair candidate (2026-09-04)

Baseline: clean `main` at `44d21b1e47303dadab35988f8e5f83d81dd7522c`, verified
against remote `main` before editing. The user's subsequent repair instructions
authorize this candidate without further preview investigation or Appearance Editor polish.

### Implemented scope

- Both Profile storage grids use **74px cells, 2px spacing, 64px icons**, seven
  columns, and a 534×458 full-content grid inside native `TopScrolling` hosts.
  Each viewport has approximately four visible rows; scrolling reaches the last
  two rows. All 40 NPC slots remain in the same section/index order. Player Storage
  remains the original authoritative container/section. There is no pagination
  index translation, capacity change, migration, synthetic container, or sliced
  replacement authority. The native transaction bridge and literal section documents
  remain unchanged. Isolated inventory-probe geometry is unchanged.
- Removed Inventory navigation and its obsolete selection/binding state. Overview,
  Appearance, Profile Editor and Voice Recorder remain; inventories stay on Overview.
- The existing 820×792 NPC background now occupies a **205×198 bottom-centered**
  decorative region rather than stretching across the whole preview. The character
  viewport increases from 310×308 to 340×330 through layout only. Final visual foot/glow
  placement remains a connected-review item; this is not a new preview renderer.
- Restored `NPC GEAR & STATS`, retained aligned compact gear labels, and resolved
  display-name casing from the canonical profile. Infinite Ammo sits beneath the
  preview; its state-dependent explanation is a tooltip instead of permanent text.
  Successful appearance lifecycle diagnostics go to logs, while degraded/error
  messages remain available to the creator.
- Unspawned Defense now sums the four authoritative session armor slots using
  `ItemArmor.getBaseDamageResistance()`, independently of a live `EntityStatMap`.
  Equip/remove uses the existing coalesced post-commit refresh. Reopen derives Defense
  again from hydrated persisted armor, not a separately persisted derived number.
  Armor-hide flags do not affect Defense. Unspawned Health/Stamina/Mana remain `—`:
  there are no authoritative persisted vitals in the current NPC inventory schema.
  Spawned vitals still use live `EntityStatMap`; missing live stat maps no longer
  discard otherwise available armor Defense.
- Both native directional caret assets remain decorative and non-hit-testable.
  The compact rail has no button/event/transfer action.
- Outer document-space bounds are 1180×1030, with 534px top workspace and 410px
  inventory panel. Static geometry checks cover 1920×1080 and 2560×1440, including
  scrollbar allowance and every NPC slot. These are not rendered-client QA claims.

### Preserved preview contract — known limitation, not solved

`CharacterPreviewComponent` shares the local player's client-side model/equipment
representation; it is **not an independently targetable NPC equipment preview**.
Native inventory/selection can reassert held-item presentation, and the viewer's
four local armor-hide settings affect NPC armor visibility. Exact visual restoration
of skin, armor, hands and selected-hotbar presentation is not proven.

R147 leaves `NpcMeshPreviewSession` and its `ModelUpdate → PlayerSkinUpdate →
EquipmentUpdate` architecture unchanged. The current NPC skin/model preview remains
visible. No armor-hide settings, player inventory/gear/hotbar/ECS, fake inventory
containers, or packet rewrite loops were introduced. Existing restoration behavior
is preserved, not upgraded to a guarantee.

The historical investigation report, two evidence extracts and read-only inspection
script are now checked into `persistent-npcs/docs/R146-PreviewContract/` for GitHub
review. Its original hold/next-experiment language is historical; this R147 section
records the later bounded authorization. No client binaries or analysis dependencies
were added, and no further investigation was performed.

### Verification and deployment

- Full deterministic suite: `persistent-npcs/test.ps1 -SkipLive` **PASS**, exit 0.
  This includes the A0–A6 regressions, new R147 service/layout coverage and the
  8,100-scenario conversation matrix (zero stale commits or leaked resources).
  Live model tests were intentionally skipped; no distillation work was resumed.
- One earlier full-suite attempt hit an intermittent `ConcurrentModificationException`
  in unchanged `R053CompactResourceTraceTest.materialOwnershipChangeEmitsNewFullSnapshot`
  at line 156. The complete rerun passed, including R053. No unrelated production
  or test synchronization changes were made to suppress that failure.
- Existing deprecated SDK/Unsafe warnings remain. Legacy layout assertions were
  updated for the explicitly requested seven-column geometry and revised hierarchy;
  transaction/persistence assertions were not removed.
- Release-client `build.ps1`: **PASS**, compiled against the installed release
  `HytaleServer.jar`; R147 stats and Profile layout/packaging tests also pass using
  that release JAR. Release resource checks agree on HUD revision, manifest, build
  and installer version.
- Connected validation is **PENDING**; do not mark this candidate accepted based on
  deterministic tests. Hytale UI parsing, native scroll hit testing, visual placement
  and exact restoration still require the connected checklist.

Deployment verified with Hytale/Java stopped:

- Exactly one active **ImmersiveNPCs** JAR:
  `C:\Users\Zemio\AppData\Roaming\Hytale\UserData\Saves\NPC\mods\ImmersiveNPCs-0.6.3-R147-NPC-PROFILE-REPAIR.jar`.
  Size **3,312,293 bytes**; source/staged/deployed SHA-256:
  `23011E529135BB82A3D5737209E80C5217CF10209CA8B58562DCBF785A375175`.
- R146 moved intact to
  `C:\HytaleRollback\NpcAuthoringStudio-Profile-R146-2026-09-04\ImmersiveNPCs-0.6.3-R146-NPC-PROFILE-MAIN-MENU-POLISH.jar`.
  SHA-256 remains `38D97B3FFD0143A2282A496DF01C1855C18B9242865F93F41A516BC931A253A3`.
  Existing R145 rollback and the unrelated SkinSwap JAR remain intact.
- Deployment copied/moved only the project JARs; the broad installer was not run.
  No runtime NPC profile, appearance, inventory, voice file, player setting,
  migration archive or paused distillation state was changed.
- This report and the historical R146 investigation evidence are included alongside
  the repair source/tests in the R147 GitHub commit. The commit containing this
  section is the candidate source checkpoint; its hash is reported in the handoff.

Focused coverage added: real stats-service calls over SDK armor fixtures, empty and
non-armor slots, hidden equipped armor, equip/remove revision changes, JSON armor
round-trip with a fresh service, absent unspawned vitals, canonical name binding,
tooltip-only help, visible unchanged preview, native grid geometry and complete
40-slot scroll reachability. Fixture item lookup substitutes for a running asset
registry; in-game restart/asset hydration remains in the connected checklist.

### Connected approval checklist — repeat visual checks at both resolutions

1. Restart the NPC world and verify **R147-NPC-PROFILE-REPAIR** in the HUD. Open
   `/npc update mara` and a spawned NPC. Verify canonical name casing, frame bounds,
   gear hierarchy, larger preview, grounded artwork and no clipping/overlap.
2. Compare cells with native Inventory. Scroll both inventory panes; reach NPC slots
   28–39 (zero-based), including the last occupied slot, with all 40 slots reachable.
   Move an item into the last slot, scroll away/back, retrieve it and verify counts.
3. Test Player↔NPC and internal moves, occupied swaps, stack merge/split, quick-move,
   full/invalid rejection and valid/invalid gear moves. Close/reopen and restart;
   verify item locations and counts. Scrolling must not alter transaction indices.
4. For an unspawned NPC, equip/remove known armor and check immediate summed base
   Defense, then close/reopen/restart. Health/Stamina/Mana should remain quiet `—`.
   For a spawned NPC, verify authoritative live vitals still refresh.
5. Confirm only four navigation entries. Open/return from all child editors without
   touching established voice samples. Check Infinite Ammo tooltip/dependencies and
   visibility toggles. Click the decorative carets: no action should occur.
6. Compare authoritative player inventory/equipment before/after, allowing only the
   transfers intentionally made. Check player visual restoration separately and
   report mismatches against the documented preview limitation; do not infer exact
   visual restoration from server-side inventory integrity.

STOP after candidate deployment and GitHub push. Await connected approval; no
Appearance Editor polish, preview expansion, or next milestone is authorized here.

## R148 — native-size paging, typed armor resistance, configured unspawned vitals

**2026-09-04 — IMPLEMENTED / DETERMINISTIC PASS / DEPLOYED CANDIDATE. Connected approval pending.**

Source started clean on `main` at R147 commit `233a48da24149744c10ec50d98eec44f62d3d819`.
The accepted rollback baseline remains **R146**, not R147. This section supersedes
R147's scrolling and base-only Defense behavior; it does not declare R147 accepted.

### Inventory presentation and authority

- Both grids use native 74px cells, 2px spacing and 64px item icons. Each viewport
  is 534×306: seven columns and four rows. NPC pages show authoritative slots
  **0–27** and **28–39**, respectively. Capacity remains **40**. Player storage is
  independently paged using its actual capacity (36 here: 28 + 8).
- Each pane has bounded `< PAGE 1 / 2 >` controls; first/last navigation disables
  at the boundary. The last page contains only valid slots, not fake empty targets.
- The same existing native window IDs, container objects, session identity and
  transaction bridge remain in use. Paging remounts presentation/bindings only.
  No migration, container slicing/replacement, reduced capacity or item copy occurs.
- `InventorySlotIndex` and drag `SourceSlotId` stay absolute. The target grid's
  visual `SlotIndex` is mapped through the current page offset, bounds-checked,
  then submitted to the unchanged authoritative bridge. Source IDs are **not**
  offset twice. An embedded inventory-view revision rejects stale page/drop events
  before submission; out-of-page targets fail closed.
- Existing transaction/swap/merge/quick-move policies remain unchanged. Native
  hit-testing and client page-two event delivery are explicitly connected gates,
  not proven by the headless mapping tests alone.

### Armor resistance and vitals

- Replaced the base-only Defense model with **typed equipped-armor protection**.
  Production calls the installed SDK's
  `DamageSystems.ArmorDamageReduction.getResistanceModifiers(null, armor, false, null)`.
  This is its equipment-only path: no world lookup/broken-item penalty calculation
  and no live effect controller. Thus it works without a spawned entity.
- Native aggregation adds Flat amounts and Percent fractions within each damage
  type, including each applicable item's BaseDamageResistance in that type's flat
  term. Types are never added into one invented scalar. Native damage application
  uses `max(0, damage - flat) × max(0, 1 - percent)`, then inherited type entries.
  Inheritance and bypass markers are retained in the tooltip. Effects and broken-item
  penalties are explicitly excluded from this equipment-only summary, not claimed
  as a complete effective combat calculation.
- The compact DEFENSE card prefers Physical (otherwise the first available type);
  hover its value for every typed flat/percent contribution and the formula.
  An empty resistance map says **No armor**, not misleading `0 base`.
- Release Trork assets were checked directly: Head .05, Chest .09, Hands .04,
  Legs .07 Percent for both Physical and Projectile, with base zero. The full-set
  card now reads **25% Physical**; removing Head produces **20% Physical**.
  Projectile remains a separate tooltip entry. Nonzero base still participates.
- Existing gear-commit refresh recomputes resistance immediately and on reopen;
  hidden cosmetic armor still contributes. No changes to item persistence.
- Unspawned NPC cards read explicit `MaxHealth`, `MaxStamina`, `MaxMana` fields
  from that NPC's existing `native-role/<canonical name>.json`. Hoit's explicit
  MaxHealth 100 becomes **MAX 100**, never fabricated `100 / 100`. Missing Stamina
  or Mana maxima stay **—**. These are configured values, not inferred inherited
  role defaults, armor-adjusted live maxima, or current health.
- Configured Invulnerable is disclosed in the unspawned stat tooltip, not Defense.
  Missing/unreadable configuration does not write or repair a runtime file.
  Spawned NPCs continue reading current/min/max from their live EntityStatMap;
  missing live stats do not fall back to fabricated current values.

### Safe visual cleanup and unchanged preview limitation

- Kept canonical NPC casing, `NPC GEAR & STATS`, compact gear labels, tooltip-only
  Infinite Ammo help, and no permanent success diagnostics. Navigation remains
  Overview / Appearance / Profile Editor / Voice Recorder; no Inventory entry.
- Rebalanced the top/inventory panels to 506/438px while retaining the 1180×1030
  outer footprint. Grid title, four native rows and pagination fit the calculated
  content bounds at 1920×1080 and 2560×1440 document-space viewports.
- Character viewport remains moderately larger than R146 (340×326). Ground art
  stays at an exact 205×198 quarter-scale of the 820×792 image, horizontally centered.
  Its bottom offset now compensates for the texture's transparent lower margin,
  moving the luminous ground 50px downward. Final feet alignment needs connected review.
- Existing compact vertical native-carets rail remains decorative and non-hit-testable;
  pagination controls are distinct, with no transfer actions attached to the rail.
- **Preview authority is unchanged.** CharacterPreviewComponent consumes the local
  client's player representation, not an independently targetable NPC entity.
  Native hotbar/equipment updates can overwrite temporary visual equipment, and
  viewer armor-hide settings can suppress armor. Exact independent NPC equipment
  rendering/restoration is **not proven**. R148 does not claim to fix the reported
  held-weapon bleed/missing armor. No further preview research, hide-setting edits,
  authoritative player mutations, fake containers or packet rewrite loops occurred.

### Verification and deployment

- Complete `test.ps1 -SkipLive`: **PASS**. Live model tests skipped as requested by
  that deterministic mode; existing SDK deprecation/Unsafe warnings remain.
  Initial new-test fixture mistakes were corrected before the passing full run.
- Added executable R148 tests: all 40 wire indices across 28/12 pages, last-slot
  reachability, stale/out-of-bounds rejection, native Player↔NPC and internal
  page-two moves with exact quantities, slot-39 JSON reopen/restart reconstruction,
  real SDK typed armor aggregation, installed Trork JSON verification, removal,
  nonzero typed base participation, read-only configured MAX 100/missing vitals.
  Existing R132/R146/R147 assertions were updated for the superseding typed/paged
  contracts without removing their identity/persistence/navigation coverage.
- Release `build.ps1`: **PASS**. R148, R147, R146 layout and R132 gear/stats tests
  also passed against the installed **release** SDK after the release build.
- HUD revision, manifest, build and installer all identify **R148-NPC-PROFILE-REPAIR**.
- With Hytale/Java stopped, deployed exactly one active project JAR:
  `C:\Users\Zemio\AppData\Roaming\Hytale\UserData\Saves\NPC\mods\ImmersiveNPCs-0.6.3-R148-NPC-PROFILE-REPAIR.jar`.
  Size **3,320,861 bytes**. Source/staged/deployed SHA-256:
  `C4D464BA7735C3EE7F312CF407BAC83F63EEB03D7B405F643E2078C3026450C3`.
- R146 rollback preserved unchanged at
  `C:\HytaleRollback\NpcAuthoringStudio-Profile-R146-2026-09-04\ImmersiveNPCs-0.6.3-R146-NPC-PROFILE-MAIN-MENU-POLISH.jar`;
  SHA-256 `38D97B3FFD0143A2282A496DF01C1855C18B9242865F93F41A516BC931A253A3`.
- R147 candidate moved intact out of active mods into
  `C:\HytaleRollback\NpcAuthoringStudio-Profile-R147-2026-09-04\ImmersiveNPCs-0.6.3-R147-NPC-PROFILE-REPAIR.jar`;
  SHA-256 `23011E529135BB82A3D5737209E80C5217CF10209CA8B58562DCBF785A375175`.
  The unrelated SkinSwap JAR is unchanged. No runtime NPC, voice, player settings,
  migration archive or distillation source/state was modified by deployment.

### Connected approval checklist — R148 only

1. Start the NPC world; confirm **R148** in the HUD. Open `/npc update hoit`.
   Confirm canonical Hoit, Health **MAX 100**, Stamina/Mana **—**, and the tooltip's
   configured invulnerability. Check a spawned NPC still displays live current/max.
2. At **1920×1080 and 2560×1440**, verify four full native-size rows, no overflow,
   all page controls and child navigation, gear layout and ground-art placement.
3. Reach NPC page two; put an item in its last valid cell (absolute slot39), move
   it internally and to/from player storage (also test player page two). Exercise
   swap/merge/split/quick-move and invalid/full rejection. Check counts, switch pages,
   close/reopen and restart; confirm every position/count persists without duplication.
4. Equip all four Trork pieces; expect **25% Physical** and separate Physical /
   Projectile tooltip entries. Remove Head: **20% Physical** immediately. Repeat on
   an unspawned NPC and after reopening/restarting. Do not use shared-preview armor
   visibility as proof that authoritative armor is absent.
5. Open/return from each child editor without modifying established samples. Check
   Infinite Ammo behavior and decorative carets. Compare actual player inventory
   before/after intentional moves; known client preview bleed remains a limitation.

Source, tests and this report belong to the R148 candidate commit pushed to `main`;
the exact commit hash is supplied in the handoff. **STOP for connected approval.**
No Appearance Editor polish or new milestone was started.

## R149 — S1 Persistent Vanilla NPC Stats Foundation

Authoritative specification: **Orbis Persistent NPC Vanilla Stats Technical Design.docx**,
provided 2026-09-04, SHA-256
`9343E0B579C8734A65F3D033B923D30A47B3897F1230112C68508634DE82F034`.
Implementation baseline: clean `main` at R148 commit
`84a5580e2be0c4f240f1afaef8e92210886e554d` (also verified on remote `main`).
This is S1 only: Health, Stamina, Mana and existing independent typed armor
resistance. No attributes, levels, classes, perks, RPG/Mercenary formulas, stat
editing UI, offline regeneration, appearance polish, or distillation changes.

### Authority and architecture

- `VanillaNpcStats` resolves Health/Stamina/Mana through the installed
  `DefaultEntityStatTypes` integer indexes each time; no serialized numeric indexes
  and no production deprecated `EntityStatMap.get(String)` calls. Native value IDs
  are checked against the requested string ID before reads or hydration.
- `VanillaNpcStatBaselineResolver` reads installed `EntityStatType` definitions
  and the actual native role's MaxHealth/Invulnerable policy. The spawn adapter now
  selects the same registered per-profile native role used for unspawned baseline
  resolution. Existing native-role JSON is preserved, not replaced with a template.
  Unchanged Profile commits no longer reload/reinitialize an unchanged native role.
- Installed release definitions are **Health 100, 0..100; Stamina 10, -4..10;
  Mana 0, 0..0**. These are verified asset facts, not production constants. In
  particular, the document's illustrative Stamina minimum of zero is not copied
  over the installed native minimum of **-4**. Role MaxHealth affects the resolved
  Health range; initial is clamped into that range, not unconditionally set to max.
- While spawned, **the native EntityStatMap owns current values and effective
  bounds**. Hytale continues to own regeneration, damage, death, effects, modifiers,
  interactions and stat networking. S1 does not create a replacement map, tick a
  parallel simulation, apply custom stat modifiers, or reset zero Health.
- While unspawned, **the profile-local stat record owns current values**. Those
  values remain frozen offline. Last observed effective bounds are historical
  observations; saved cards use current/base maximum and disclose that distinction.
- The dormant `NpcStatModifierContributor` interface exposes source-keyed native
  modifier contributions for a later authorized stage. There are no active
  contributors and no serialized modifier stacks in S1.

### Persistence and migration

Runtime path: `mods/ImmersiveNPCs/profiles/<canonical NPC name>/npc-stats.json`.
For this test world the root is
`C:\Users\Zemio\AppData\Roaming\Hytale\UserData\Saves\NPC\mods\ImmersiveNPCs\profiles`.
No existing runtime NPC files were manually rewritten during this implementation.
Migration occurs through the deployed lifecycle when the world is started.

Schema 1 contains `stableNpcId`, monotonic successful-save `revision`, `savedAt`,
`captureReason`, and a string-keyed `stats` map. Each known entry has `current`,
`baseInitial`, `baseMin`, `baseMax`, `lastKnownEffectiveMin`,
`lastKnownEffectiveMax`, and `source`. Armor resistance/Defense is **not** stored
in this file. Unknown future stat-ID records and top-level fields are retained
without applying them to the vanilla map. Known records require finite
native-range numbers, ordered bounds, complete fields and valid provenance.

- `/npc create <name>` initializes persistent stats alongside the existing stable
  profile/appearance/inventory lifecycle. A retry after partial creation preserves
  the existing identity and authored files; an already-complete profile still
  rejects duplicate creation. The command asynchronously awaits stat preparation
  before opening Studio, without blocking the world thread on stat disk I/O.
- First attachment with a missing/corrupt file migrates **live currents**, including
  injured Health and negative Stamina. Native changes occurring while the first
  save is being written are not overwritten by that migration snapshot.
- Existing unspawned profiles are initialized from installed baselines after the
  initial world scan, or lazily when opened. Loaded entities are scanned first;
  failure to scan a world aborts background unspawned migration.
- Corrupt/empty/identity-mismatched originals are copied byte-for-byte to
  `npc-stats.conflict-<timestamp>-<uuid>.json` before reconstruction. Foreign
  identity values are rejected, never reassigned; reconstruction uses the correct
  profile's live map or its baseline. Unsupported future schema versions and
  unreadable authority fail closed instead of being downgraded or fabricated.
- On world-restored attachment with an existing record, that record wins as the
  design specifies. Unexpected native currents are first preserved as
  `npc-stats.runtime-conflict-<timestamp>-<uuid>.json`. A proven fresh SPAWN does
  not require that conflict file. Evidence failure prevents hydration.
- Each successful write uses a unique same-directory temporary file, forced file
  contents, and atomic replacement. There is no non-atomic truncate/overwrite
  fallback. Cache/revision update only after success. One serialized writer lane
  coalesces backlogs to one pending snapshot/completion per NPC, rejects stale
  attachment tokens, retains failed dirty captures for retry, and drains on close.

### Lifecycle integration

1. `NpcStatRemovalCaptureSystem` records native SPAWN/LOAD add reasons and reads
   immutable snapshots during REMOVE/UNLOAD callbacks while the ref is still
   valid. Identity resolution requires a managed role plus an exact role/name
   profile binding; it never falls back to the default NPC. Player components are
   explicitly excluded. A duplicate entity cannot steal an active stat attachment.
2. `NpcStatHydrationSystem` runs after native `EntityStatsSystems.Recalculate`;
   native Holder Setup/Balancing has already completed. It implements
   `EntityStatsSystems.StatModifyingSystem`, so native `Changes` orders after it.
   `Predictable.NONE` writes clamp persistent current into actual live bounds.
   Hydration is once per stable NPC/live entity/attachment, with the source
   repository revision recorded. Checkpoint revisions and Profile refreshes never
   trigger repeat hydration. Readiness retry is bounded at 600 ticks.
3. Read-only `NpcStatCheckpointSystem` runs after hydration/native Changes and
   samples at most once per second per attached NPC. Unchanged values do not
   rewrite disk or increment revision. World refs/maps never enter writer tasks.
4. Controlled adapter removal queues the final capture and waits asynchronously
   for durability, rechecking current values before removal. A persistently
   changing map, unavailable authority, or failing disk safely refuses removal.
   User-confirmed profile deletion waits for this barrier, then retires pending
   writers before invoking the existing whole-profile deletion path.
5. SDK REMOVE/UNLOAD hooks cover native removals outside the adapter. Reattachment
   waits for an outstanding prior removal capture. Plugin shutdown schedules
   final reads on the owning world threads and drains removal/writer work with
   bounded waits. No stat disk writes run on the world thread.

### NPC Profile cards and preserved systems

Spawned cards read **LIVE current/effective maximum**. Unspawned cards read
**SAVED current/base maximum** from the immutable repository cache, with source,
revision and last-observed bounds in the tooltip. Unavailable values remain `—`.
Invulnerability is reported separately as Yes/No/Unknown native role policy.
R148 native typed armor aggregation remains independent of vitals and works for
unspawned equipment. No preview packets, player inventory/equipment, gear rules,
voice recordings, capture leases, cognition, or UI layout assets were changed.
The pre-existing client preview weapon/armor bleed is **not repaired by S1**.

### Verification and candidate deployment

- **PASS:** complete pre-edit deterministic suite at clean R148, then complete
  `test.ps1 -SkipLive` on the pre-release SDK and the final complete suite using
  `test.ps1 -SkipLive -ServerJar <installed release HytaleServer.jar>`.
  The final deployable JAR is built against the **release** SDK. Existing SDK
  deprecation/Unsafe warnings remain; live model tests were not run.
- Added `R149PersistentVanillaStatsTest`: installed asset/role baseline verification,
  actual create/close/update/restart preparation, stable IDs, injured live migration,
  zero Health, negative Stamina, native indexed `EntityStatMap` set/subtract behavior,
  effective-bound clamps, one-time hydration, independent NPC/player maps, saved
  display and unavailable authority, exact conflict backups, future-record retention,
  identity mismatch/future-schema safety, disk failure and dirty retry, shutdown
  flush, stale reattachment rejection, and retirement. A blocked-writer test sends
  1,000 captures and verifies one coalesced completion/save with the latest values.
  Lifecycle registration/order/cleanup also has deterministic wiring guards;
  actual connected entity/world callbacks remain on the checklist below.
- Full suite retained the 8,100-scenario conversation matrix and existing A0-A6,
  recorder, appearance, gear, native container/paging, typed resistance and UI
  packaging tests. R147's source assertions now recognize the saved-vitals argument
  while retaining the independent armor/session checks. Initial test-fixture setup
  and superseded source-assertion failures were corrected before the passing runs.
- HUD revision, manifest, build and installer identify
  **R149-PERSISTENT-VANILLA-NPC-STATS**. With Hytale/Java stopped, deployed exactly
  one active project JAR:
  `C:\Users\Zemio\AppData\Roaming\Hytale\UserData\Saves\NPC\mods\ImmersiveNPCs-0.6.3-R149-PERSISTENT-VANILLA-NPC-STATS.jar`.
  Size **3,369,904 bytes**; source/staged/deployed SHA-256:
  `5E4BEB960C98826C76095B3209508B7D81E917AA6EE65E8D21E168C40C5BEA24`.
- Previous active R148 preserved intact at
  `C:\HytaleRollback\NpcAuthoringStudio-Stats-R148-2026-09-04\ImmersiveNPCs-0.6.3-R148-NPC-PROFILE-REPAIR.jar`;
  SHA-256 `C4D464BA7735C3EE7F312CF407BAC83F63EEB03D7B405F643E2078C3026450C3`.
- Accepted R146 rollback remains unchanged at
  `C:\HytaleRollback\NpcAuthoringStudio-Profile-R146-2026-09-04\ImmersiveNPCs-0.6.3-R146-NPC-PROFILE-MAIN-MENU-POLISH.jar`;
  SHA-256 `38D97B3FFD0143A2282A496DF01C1855C18B9242865F93F41A516BC931A253A3`.
  SkinSwap remains unchanged (SHA-256
  `0444550F8B84E21AF6D1A991512E748BA7946AAAC47314B902BAE61018C76E77`).
- All **61 runtime profile-tree files / 50,323,360 bytes** matched their pre-deploy
  path+SHA-256 snapshot after deployment. Aggregate digest:
  `9EC0E0D7832DE162E3BE514AF8BAA6EA7DE8F78EC26FDE7E4917B2F555CCE73A`.
  No existing NPC, inventory, appearance or voice data was altered by deployment.
  No migration archive or distillation state was touched. The game was not launched
  on the user's behalf; first connected S1 migration is pending user testing.

Source, tests and this report are included together in the R149 candidate commit
on `main`; its exact commit hash is provided in the final handoff.

### Connected S1 approval checklist — use a disposable test NPC

1. Start the NPC world and verify **R149-PERSISTENT-VANILLA-NPC-STATS** in the HUD/log.
   Confirm no stat-system registration/order errors or `NPC_STATS_*_FAILED` messages.
2. `/npc create S1Test`: verify a stable folder, valid default appearance, and
   `npc-stats.json` with that profile's stable UUID. Expect the installed native
   vanilla values (normally 100/100, 10/10, 0/0), not invented RPG stats. Close and
   `/npc update S1Test`; identity and values must be unchanged. Restart and repeat.
3. Check an existing **spawned** NPC with legitimate injured/current values on its
   first S1 migration. Verify `MIGRATION_FROM_LIVE`, no heal/reset, LIVE cards, and
   a matching saved checkpoint after at least one second. Do not assume an
   invulnerable NPC can be injured by attacks; inspect the separate policy tooltip.
4. On a disposable damageable NPC, exercise native Health changes and available
   native Stamina/Mana changes. Native regeneration remains native. Test zero
   Health without a reset-to-max, and negative Stamina if supported by native play.
5. Despawn/remove without deleting the profile, then reopen while unspawned.
   SAVED currents must reflect the final live capture and stay frozen offline.
   Respawn/restart: hydrate once, clamp to actual bounds, then resume native updates.
6. Close/reopen Profile and save unrelated Profile/appearance changes while live;
   they must not reset vitals. Test world unload/reload and graceful server shutdown.
   Inspect runtime-conflict evidence if the world-restored map differs from the file.
7. Equip/remove armor while spawned and unspawned. Typed Physical/Projectile
   resistance should refresh independently; stat JSON must never gain Defense or
   armor-resistance fields. Check invulnerability stays separate from armor.
8. Verify Hoit, Mara, Jonalith and the player have independent vitals. Repeat an
   inventory transfer, gear move, child editor navigation and non-destructive voice
   playback check. No established voice samples need to be deleted for S1 testing.
9. Confirm stat cards/tooltips at 1920×1080 and 2560×1440. Layout is unchanged;
   connected rendering and native hit testing remain part of this approval gate.

Native entity/world lifecycle ordering and gameplay behavior require this connected
checklist: deterministic native-map tests are not a claim of connected PASS.
Uncontrolled native removals cannot wait for disk in the engine callback; they
queue a final immutable capture. Abrupt process/OS failure can lose the bounded
checkpoint/write interval. Shutdown capture timeouts and persistent disk failures
are explicit diagnostics, not silent success. Keep rollback until connected PASS.

**STOP at this S1 candidate. Do not begin Appearance Editor polish, P2, RPG work,
or another stage without explicit approval.**

## R150 — NPC Appearance Editor visual-polish candidate

Date: 2026-09-04. The operator explicitly authorized this Appearance-only polish pass,
superseding the preceding stop for this scope only. Baseline was clean `main` at
`33e535a79c7b90c7e27784db428e3bafed5f191e` (R149). No Profile Editor polish or other
stage is included. R149 connected acceptance is not inferred from this authorization.

### Presentation implementation

- Replaced the oversized 1520×960 editor surface with one 1380×910 native
  `DecoratedContainer`, integrated title, opaque navy panels and restrained gold
  selection treatment. The main Profile is not duplicated behind the editor.
- Added normal/selected native MyAvatar category artwork to the six existing primary
  categories and every secondary category. Existing category semantics are unchanged.
  Icon aspect ratios are preserved. Category labels remain visible alongside icons.
- Reflowed the same twelve reusable options into three columns/four rows. Cards have
  native stateful button framing, gold selected borders, bounded readable labels and
  full-name tooltips. Removed repeated pack/debug metadata from the visible surface;
  compatibility details remain available on the selection tooltip.
- Search uses a packaged native search icon; all existing search, option, color and
  variant paging events remain bound. Explicit no-results messaging replaces blank
  search output. Six variant choices use two rows, avoiding the former cramped strip.
- Added native circular color masks/frames/selection rings. Swatches read actual
  `PlayerSkinPartTexture.BaseColor` values from the installed registry, selecting
  variant textures first and the part's gradient set as fallback. Up to two actual
  colors are displayed. Missing/invalid palette data displays a question marker and
  the original color-name tooltip rather than inventing a palette. The current color
  name remains above the swatches. Color IDs and validation are unchanged.
- Enlarged the preview allocation, retained the actual 440×520 native live preview,
  framed it in an opaque navy panel and used the existing project NPC ground artwork
  at its intended aspect ratio. Missing-preview and retained-invalid-selection states
  remain readable and repairable.
- Kept Randomize, Reset, Discard/Back and Save Appearance, now as consistently framed
  actions with Save as the primary action. Discard/Back uses the existing Cancel event
  and dirty-discard confirmation, not a new navigation or persistence path. Normal
  status text is reduced to saved/unsaved state; actual validation errors remain visible.

The only catalog addition is a read-only swatch lookup; `AppearanceEditorPresentation`
contains pure icon/label mappings. Appearance event handlers, draft authority, catalog
admission, randomization, save/discard, codec, repository, model materialization and
preview/restoration implementation were not rewritten. No persisted data format changed.
R149 stats classes/lifecycle hooks, inventory/gear, Profile Editor, Voice Recorder and
player authoritative state are unchanged. No established voice samples were deleted.

The native MyAvatar card thumbnails are client-generated previews, not a static
thumbnail set imported by this pass. Option cards remain honest text choices plus
the real large live preview; no fabricated cosmetic imagery is shown.

### Native assets and packaging

Inspected installed Hytale 0.6.3 `MyAvatarPage.ui`, `ColorOption.ui`, CategoryIcons and
Custom UI `Common.ui`. Used the Custom UI-exported native button styles (including
hovered/pressed/disabled states), not incompatible main-menu-only style names.
Native `UIGalleryPage`/`PluginListPage` bytecode confirmed the `Value.ref` document/name
contract used for selected-icon patches. Swatch updates use the whole `PatchStyle`
codec rather than unsupported nested property assignments.

46 native PNGs (124,867 bytes) are packaged under
`Common/UI/Custom/Pages/ImmersiveNpcAppearance/`; every copy was SHA-256 compared with
the installed original. Asset-by-asset provenance/hashes are in
`persistent-npcs/docs/R150_APPEARANCE_ASSET_PROVENANCE.md`.
There are no runtime absolute installation-path dependencies. The deployed JAR was
inspected and contains all 46 assets. HUD revision, manifest, builder and installer
artifact name all agree on R150.

### Deterministic verification

Full installed-release suite: **PASS**, including the 8,100-scenario conversation
matrix, prior appearance migration/save/conflict/unknown-field tests, inventory,
gear, recorder/isolation, R146–R148 repair gates and R149 persistent vanilla stats.
The final source/resource build was rerun through the complete suite before deployment:

```powershell
.\test.ps1 -SkipLive -ServerJar 'C:/Users/Zemio/AppData/Roaming/Hytale/install/release/package/game/latest/Server/HytaleServer.jar'
```

New `R150NpcAppearancePolishTest` checks every category-to-asset mapping, PNG decoding,
normal/selected patch references, bounded labels/swatches, unknown-palette handling,
SDK serialization of selection/icon/color commands, retained event names, and worst-case
layout budgets at 1920×1080 and 2560×1440. All twelve options plus both paged color and
variant sections fit simultaneously. Native title height was verified as 38px.
`git diff --check` passed. Existing SDK deprecated/Unsafe warnings remain unchanged.

**Connected rendering, hit testing and gameplay validation remain PENDING.** Layout
budgets and deterministic tests are not screenshots or a native-client render test.
Live local-model tests were intentionally skipped; no inference behavior changed.
Existing native preview architecture limitations are not claimed fixed by this polish.

### Deployment and rollback verification

- Active: `C:\Users\Zemio\AppData\Roaming\Hytale\UserData\Saves\NPC\mods\ImmersiveNPCs-0.6.3-R150-NPC-APPEARANCE-POLISH.jar`
- Size: **3,501,307 bytes**.
- SHA-256: `A33CDB8F82D311B52029BDE184FE11F84DF2581605E7D3A7646758A35BA63DB7`.
- Build, staging and deployed hashes matched; exactly **one active project JAR**.
- Prior R149 preserved at
  `C:\HytaleRollback\NpcAuthoringStudio-Appearance-R149-2026-09-04\ImmersiveNPCs-0.6.3-R149-PERSISTENT-VANILLA-NPC-STATS.jar`,
  SHA-256 `5E4BEB960C98826C76095B3209508B7D81E917AA6EE65E8D21E168C40C5BEA24`.
- Accepted R146 rollback remains untouched, SHA-256
  `38D97B3FFD0143A2282A496DF01C1855C18B9242865F93F41A516BC931A253A3`.
- SkinSwap remains untouched, SHA-256
  `0444550F8B84E21AF6D1A991512E748BA7946AAAC47314B902BAE61018C76E77`.
- Hytale/Java were stopped for deployment. Only the project JAR was replaced;
  the broad installer was not run. The live profile tree stayed **70 files /
  50,331,816 bytes**, with identical before/after sorted relative-path + file-SHA-256
  aggregate `FE55EE0CA6E779A58D2D368E4FEB89416D23491397E8734105A9BB2106C0E554`.
  NPC profiles, stats, appearance and voice files were not modified by deployment.

### Connected approval checklist

Perform the following at both **1920×1080** and **2560×1440**. Confirm HUD shows
`R150-NPC-APPEARANCE-POLISH`, then open `/npc update Hoit` (also Mara/Jonalith as appropriate)
and choose Appearance.

1. Inspect frame/header, preview centering, category labels/icons, option cards,
   search, swatches, variants, status and footer. Nothing should clip or overlap;
   check normal/hovered/pressed/disabled paging buttons and gold selection changes.
2. Visit all six primary categories and every subcategory. Navigate multiple pages,
   select options, search for a known choice, search for no matches and clear search.
   Verify selection follows the correct item and paging does not lose functionality.
3. Choose hair/clothing colors, including a later color page and a multi-color option
   if available. Confirm swatch/name/preview agreement. Exercise variants and their
   paging where the active registry supplies enough variants.
4. Randomize, inspect, then Reset: the open-time persisted appearance must return.
   Make another change, choose Discard/Back and test both staying and confirming discard.
   Reopen: unsaved changes must not have persisted.
5. Make a deliberate appearance change, Save Appearance, close and reopen the Studio,
   then restart the save/server and reopen. Confirm the same NPC identity/appearance
   and the unchanged stats/inventory/gear persist.
6. Check a new `/npc create` NPC reopens with its valid default. Use only an existing
   designated invalid/degraded fixture (or a disposable backed-up test NPC) to verify
   retained invalid data and safe recovery. Do not corrupt established NPC files.
7. Compare the player's skin, armor, held item/offhand and selected hotbar before/after
   preview, Randomize, Reset, Save, discard, close and reopen. No authoritative player
   changes are permitted; restoration must behave as before this pass.
8. Briefly smoke-test coupled inventory moves, gear/stat cards, Profile Editor and
   non-destructive Voice Recorder playback. Do not delete established voice samples.

**STOP for connected approval. Do not begin Profile Editor visual polish or another stage.**

## R151 — Appearance Editor native cards, rails and full palette (2026-09-05)

**Status: implemented and deployed; deterministic suite PASS; connected visual/function approval PENDING. STOP.**
Baseline: clean main `b122dbca1e321a215c7bdf9202e3fecfc7085518` (R150).
Scope is Appearance Editor refinement only. Profile Editor polish, other stages,
distillation, migration and archival backup work were not started.

### Presentation architecture

- Native decorated 1380×910 window retained; two 62px icon-only primary/secondary rails
  replace wide text columns. Tooltips retain names. Selected icons use native gold
  artwork, with a restrained rail accent. Packaged native ContainerVerticalSeparator
  textures frame both rails; ContainerTitleArrow forms the CATEGORY > SUBCATEGORY
  breadcrumb.
- Five 92×149 graphical cards per row in a native TopScrolling viewport, with native
  masks/frames, hover/pressed/disabled states, restrained selected underline, and
  KeepScrollPosition. Cosmetic paging controls and page state are removed. All matching
  options—including None for optional categories—are returned by the same pinned
  catalog filter through `queryAll`; the old paged query API still delegates to that
  filter and retains its compatibility tests.
- Every valid color ID is bound/rendered simultaneously, thirteen 38px swatches per
  row; palette height grows by whole rows. All color Previous/Next controls, labels,
  allowlist actions and page state are removed. Native circular swatches and gold
  selected rings remain; palette colors still come from the existing live registry
  lookup. No guessed color IDs or authority changes.
- Variants retain their six-choice controls and paging. A stale presentation-page
  offset is clamped **before** event binding when Randomize/category changes reduce
  the variant count, matching the existing displayed clamp.
- Search uses the installed MyAvatar field decoration and the short placeholder
  “Search”. Its native icon property is `Texture`, not `TexturePath`; this exact
  markup distinction is regression-tested.
- Preview panel gives the existing NPC preview a 590×690 region and the existing
  authored ground artwork. Permanent “Appearance Preview”, preview-only footer,
  cosmetic-name caption and dirty-state prose are removed. Missing saved cosmetics
  retain an inline compact unavailable indicator/tooltip; errors remain contextual.
- Footer: separate BACK; compact native Randomize/Reset utility icons; DISCARD CHANGES;
  SAVE. Back routes through the existing guarded cancel flow (dirty confirmation,
  clean return). Discard also uses that existing confirmation/cleanup flow and is
  disabled when clean. Reset/Save reflect dirty state. No Escape interception.
- Dynamic cards/swatches are appended with numeric-only container selectors; exact
  cosmetic/color IDs remain event values behind the unchanged authoring-session,
  identity, generation and draft guards. Rebuilds regenerate bindings with nodes.

### Cosmetic thumbnail contract and limits

Native MyAvatar's `PartPreviewComponent` is populated by client code. Inspection
found no verified server Custom UI part-preview contract or static native thumbnail
library. No undocumented preview component is used and no player representation is
borrowed to generate cards.

The explicitly authorized fallback is a deterministic offline software renderer:
**590 packaged reference cards covering all pinned installed cosmetic entries**, using
actual blockymodel geometry, UV textures and gradients on a canonical neutral mannequin.
Cards show representative reference colors/first variants, not the current NPC draft
or exact native animated/shader presentation; tooltips identify this distinction.
The existing live preview remains responsible for the selected appearance.

A future catalog entry absent from the packaged index is still reachable/selectable
with a clear unavailable-thumbnail placeholder. No generic category icon is passed
off as its cosmetic image. Native resources remain owned by Hytale/Hypixel.
No absolute install paths, Python, renderer service, model requests or player data
are required at runtime.

See [asset provenance and reproduction instructions](persistent-npcs/docs/R151_APPEARANCE_ASSET_PROVENANCE.md),
[renderer contact sheet](persistent-npcs/docs/R151_APPEARANCE_THUMBNAIL_CONTACT_SHEET.png),
and the packaged [per-card hash index](persistent-npcs/src/main/resources/Common/UI/Custom/Pages/ImmersiveNpcAppearance/Thumbnails/index.tsv).
The contact sheet is an offline renderer check, **not a connected Appearance UI screenshot**.

### Safety and deterministic verification

Full final suite passed using the installed release Server SDK:

```powershell
.\test.ps1 -SkipLive -ServerJar 'C:/Users/Zemio/AppData/Roaming/Hytale/install/release/package/game/latest/Server/HytaleServer.jar'
```

Includes the 8,100-scenario conversation matrix (zero stale commits, malformed action
executions or leaked resources), A5 draft/save/conflict/unknown-field tests, voice
privacy/isolation, inventory/gear, R146–R148 repair tests and **R149 persistent vanilla
stats S1**. New R151 tests cover 113 filtered cosmetics plus None, 73 exact color IDs,
dynamic SDK command rows, every packaged PNG/hash/reference, malformed/unknown
thumbnail lookup, separated Back/Discard controls, contextual errors, and logical
1080p/1440p layout budgets. Only obsolete R150 fixed-grid/paging expectations were
updated; its icon/swatch/authority checks remain.

Two independent full thumbnail bakes matched **all 590 PNG hashes**:
index SHA-256 `516237E45943A7F6A82A7F8054E68B1D2E9E436B1FE9952825403A6C2D7ED6AB`.
Release validation verifies consumed installed source hashes and packaged PNG hashes,
failing closed if rebaking is needed. Existing SDK deprecation/Unsafe warnings remain.
`git diff --check` passed. No system dependencies were installed.

Connected client parsing/rendering/hit testing, scroll retention and gameplay checks
remain **pending**, including both requested resolutions. Arithmetic and SDK
serialization tests do not claim native-client render validation. No existing preview
architecture limitation is claimed repaired by this presentation pass.

### Deployment verification

- Active: `C:\Users\Zemio\AppData\Roaming\Hytale\UserData\Saves\NPC\mods\ImmersiveNPCs-0.6.3-R151-NPC-APPEARANCE-NATIVE-CARDS.jar`.
- Size: **11,347,935 bytes**; packaged thumbnails: **590**.
- SHA-256: `B7B328F2DFF926BB4BADF72435A59EE76F5764CE85B1E7F9E0A0EBE7C5F23A98`.
- Manifest, build/installer artifact names and HUD counter all identify R151.
- Build/staging/deployed hashes matched; exactly **one active project JAR**.
- R150 preserved at `C:\HytaleRollback\NpcAuthoringStudio-Appearance-R150-2026-09-05\ImmersiveNPCs-0.6.3-R150-NPC-APPEARANCE-POLISH.jar`,
  SHA-256 `A33CDB8F82D311B52029BDE184FE11F84DF2581605E7D3A7646758A35BA63DB7`.
- Accepted R146 rollback remains untouched:
  `38D97B3FFD0143A2282A496DF01C1855C18B9242865F93F41A516BC931A253A3`.
- R149 rollback remains untouched:
  `5E4BEB960C98826C76095B3209508B7D81E917AA6EE65E8D21E168C40C5BEA24`.
- SkinSwap remains untouched:
  `0444550F8B84E21AF6D1A991512E748BA7946AAAC47314B902BAE61018C76E77`.
- Hytale and Java were stopped. The broad installer was not run. Runtime profile tree
  before/after: **76 files / 50,335,199 bytes**, same sorted relative-path + SHA-256
  aggregate `93DC68D4FEB07DA5EED0036FCA09B685C53672E974A253031DBE683EFF4DF58F`.
  Existing NPC stats, appearances, profiles and voice recordings were not modified.

### Connected approval checklist — 1920×1080 and 2560×1440

1. Confirm HUD R151; enter server, run `/npc update Hoit`, open Appearance. Repeat
   with Mara/Jonalith as useful. Confirm UI documents load without disconnect.
2. Inspect native frame, icon-only rails/dividers, breadcrumb, search, graphical
   cards, subdued selection, full palette and dominant preview. No controls or
   palette rows should clip/overlap. Scrolling cards should remain inside their viewport.
3. Visit **every primary/subcategory**, including empty Skin Features if applicable.
   Scroll to the last cosmetic, select it, verify correct selected underline and
   live preview; check scroll retention. Hover for cosmetic/category names.
4. Search a known late-list cosmetic, no-match text, then clear search. Confirm all
   catalog entries remain reachable and no stale events select the previous category.
5. Select colors from the first and last swatch rows, including previously paged
   colors. No color paging UI should exist. Exercise multi-color swatches and every
   available variant; Randomize then switch to a cosmetic with fewer variants.
6. Randomize → Reset must restore open-time saved appearance. Change → Back, test
   staying and confirming discard. Change → Discard Changes, confirm, reopen and
   verify no unsaved changes persisted. Clean Back must return to Studio.
7. Change → Save → close/reopen → restart/reopen: verify appearance and stable NPC
   identity persist. Verify new `/npc create` characters still reopen validly.
8. Use only a designated disposable/degraded fixture for unavailable cosmetic
   recovery; preserve its malformed data until an explicit valid save. Do not corrupt
   established NPC files or delete established voice recordings.
9. Compare player skin/equipment/held item/offhand/selected hotbar before and after
   selecting, saving, discarding and closing. Restoration and authority must remain
   unchanged. Smoke-test R149 stats, coupled inventory/gear moves, Profile Editor,
   and non-destructive Voice Recorder playback.

**STOP for connected visual approval. Do not begin Profile Editor polish or another stage.**

## R152 — Appearance category-rig thumbnail repair (2026-09-05)

**Implemented, full deterministic PASS, deployed; connected approval pending.**
Baseline: clean main/remote `d7780c47e8bbcbe9b60c7f5e9bfdfd7dca7f8d9d` (R151).
This pass changes only offline reference-card composition, generated assets,
provenance/regression gates, reports and monotonic release/HUD counters.
No UI layout or appearance/backend authority rewrite. No Profile Editor polish.

The baker no longer measures each cosmetic's AABB to choose center/scale. Nine
fixed rigs cover all twenty categories: head/shoulders, tight face, three-quarter
ears, torso, lower body, feet, torso/hands, rear body and full body. Each rig pins
camera angles, world target, orthographic span, crop and neutral context mask.
No per-item safety expansion is used. Clothing context excludes head and facial
features; Overtop/Undertop use identical neck-to-thigh composition, preserving
fixed landmarks across small shirts, coats, straps and large tunics.

All **590** cards rebaked at **184×298**, retaining the unchanged **92×149** UI
pipeline, cosmetic IDs, lookup/fallback, search/scroll, palette and selection.
The thumbnails still use **baked representative colors, not active draft colors**.
No all-colors asset expansion. Full rig/mask table, provenance, reproduction steps,
deployment evidence and connected checklist are in the
[R152 implementation report](persistent-npcs/docs/R152_APPEARANCE_CATEGORY_RIGS.md).
[Forty grouped contact sheets](persistent-npcs/docs/R152_CATEGORY_CONTACT_SHEETS/README.md)
cover every entry. All 105 Overtop cards were visually reviewed against the native
MyAvatar screenshot; other category rigs were reviewed using grouped sheets.

Validation: full `test.ps1 -SkipLive` suite passed, including R149 S1, inventory,
gear, voice isolation, appearance authority and the 8,100-case conversation matrix.
Four new offline renderer tests passed, including actual render equality after
injecting enormous outlying geometry, context masks, all PNG/rig hashes and exact
contact-sheet coverage. Two independent full bakes matched all images, provenance
and sheets. SDK build/resource gates and `git diff --check` passed. Native-client
rendering/hit testing at 1080p/1440p remains pending connected approval.

Deployment:

- Active: `C:\Users\Zemio\AppData\Roaming\Hytale\UserData\Saves\NPC\mods\ImmersiveNPCs-0.6.3-R152-NPC-APPEARANCE-CATEGORY-RIGS.jar`.
- **8,451,441 bytes**, exactly **one active project JAR**, 590 packaged thumbnails.
- SHA-256: `DB6B87CFD9B49A813AC7E25C392AB9C13666B1990FF81C4616EDC3D93946F3F3`.
- R151 rollback: `C:\HytaleRollback\NpcAuthoringStudio-Appearance-R151-2026-09-05\ImmersiveNPCs-0.6.3-R151-NPC-APPEARANCE-NATIVE-CARDS.jar`.
- R151 SHA-256: `B7B328F2DFF926BB4BADF72435A59EE76F5764CE85B1E7F9E0A0EBE7C5F23A98`.
- Existing accepted R146, retained R149/R150, SkinSwap and HYTALEDEVLIB unchanged.
- Runtime profiles: **76 files / 50,335,199 bytes** before/after; identical aggregate
  `93DC68D4FEB07DA5EED0036FCA09B685C53672E974A253031DBE683EFF4DF58F`.
- HUD/build/installer/manifest counters identify R152. No broad installer or runtime
  data migration ran. No existing audio, NPC state, worlds or archives were changed.

**STOP for connected approval. Do not begin Profile Editor polish.**
