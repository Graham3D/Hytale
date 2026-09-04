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
| A6 / P1 — voice recorder polish | R141 focused UX connected-validation candidate | PASS | R139/R140 not approved; R141 pending | HOLD FOR P1 APPROVAL |
| A7 — integration/polish | P1 only; P2/P3 not activated | P1 PASS | P1 pending; P2/P3 not run | BLOCKED AFTER P1 |

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
