# R146 NPC preview contract investigation

Date: 2026-09-04. Scope: isolated analysis only; production held.

## Decision

**Do not hide the preview, deploy a replacement, or resume the other repairs yet.**
The current three-packet overlay is client-local in its delivery and does not mutate
the server's authoritative player equipment. It is **not a private preview target**:
CharacterPreviewComponent consumes the local client player representation. That
representation also has native inventory/selection writers and viewer-owned armor
visibility settings. The current sequence has not proved independent NPC equipment
visibility or exact restoration.

There is a concrete explanation for missing armor: the native armor-composition
function suppresses local-player armor using the viewer's client settings. All four
saved settings are currently true: HideHelmet, HideCuirass, HideGauntlets, HidePants.
No settings were changed during this investigation.

**Do not interpret this as proof that every client-only alternative is impossible.**
The static investigation establishes the existing component's data source and a
visibility limitation. A supported, independently controlled alternative and exact
connected restoration remain unproven. In particular, a local-player equipment
warning is not evidence that the client ignored the packet.

## Preserved checkpoint

- Repository: `C:\HytaleMigration`, branch `main`, clean working tree.
- HEAD: `44d21b1e47303dadab35988f8e5f83d81dd7522c`.
- Active project JAR: `C:\Users\Zemio\AppData\Roaming\Hytale\UserData\Saves\NPC\mods\ImmersiveNPCs-0.6.3-R146-NPC-PROFILE-MAIN-MENU-POLISH.jar`.
- Size: 3,312,226 bytes.
- SHA-256: `38D97B3FFD0143A2282A496DF01C1855C18B9242865F93F41A516BC931A253A3`.
- R145 rollback directory: `C:\HytaleRollback\NpcAuthoringStudio-Profile-R145-2026-09-04`.
- No project code, packaged UI, runtime NPC data, inventory, player settings, installed
  client binaries, archive, or deployment was changed. No packets were injected.
- Analysis scripts, evidence, and an isolated Capstone dependency live only in
  `C:\HytaleInvestigations\R146-PreviewContract`.

## Installed-client evidence

Client: `C:\Users\Zemio\AppData\Roaming\Hytale\install\release\package\game\latest\Client\HytaleClient.exe`.
SHA-256: `4947649CCFF38F37847F78250FC56226B4634FB9D1C4D362E14FCD18F93C55A5`.
The installed executable is native compiled code, not a managed IL assembly suitable
for ordinary Java/.NET decompilation. Addresses below are preferred virtual addresses
with image base `0x140000000`, specific to this binary. Function labels below are
interpretations of disassembly, not recovered official symbols.

Reproduction: use `inspect_client.py` with `disasm`, `window`, `callers`, `refs`,
`strings`, or `managedstring`. It reads the PE bytes and function table; it does not
load or execute game code. Selected output is retained in `native-evidence.txt` and
`packet-and-armor-evidence.txt` beside this report.

### Inventory / CharacterPreviewComponent

Shipped UI root: `...\Client\Data\Game\Interface`.

- `InGame\Pages\Inventory\CharacterPanel.ui:58` declares
  `CharacterPreviewComponent #PreviewContainer`. The markup provides geometry and
  padding, not an entity, skin, model, equipment, or visibility target.
- Recursive search of the shipped Interface tree found no other declaration of
  CharacterPreviewComponent. This is a declaration search, not a claim that no
  component is ever created programmatically.
- At `0x14038e220`, component setup builds a preview configuration containing an ID,
  camera values, scale and viewport/clipping geometry. It calls the preview manager
  at `0x140521ee0`; no arbitrary target is supplied in this path.
- That manager creates the CharacterPreview renderer. Its identity is anchored by
  the compiled error string `Maximum preview limit reached ({0}), cannot add
  CharacterPreview` at object address `0x14324d080`, referenced at `0x140521fbd`.
- Renderer refresh `0x140521820` reads its context's local player (`context+0x10`),
  copies the model renderer (`player+0xA8`) and attachment list (`player+0xB0`), and
  rebuilds its render resources when source generation IDs differ. The render method
  at `0x140521400` calls that refresh and synchronizes the source model.

### Skin, armor, hands and visibility

| Concern | Installed-client path | Consequence |
| --- | --- | --- |
| Skin/model | Entity update dispatch feeds the model/skin composition path; `0x140541190` builds the Player-skin model from entity skin state at `+0x1C0`. The preview copies the resulting model renderer. | The current NPC skin packets can affect the preview by changing its shared local-client source. |
| Armor IDs | Dispatch at `0x1409ed9a2..0x1409eda00` passes EquipmentUpdate armor IDs to model composition `0x14053f3e0`; that path stores the array at entity `+0xC8`. `0x1405412a0` resolves those IDs against item assets and combines armor models. | Armor is not simply rejected because the entity is local. |
| Armor visibility | Before each armor slot, `0x1405412cd` calls `0x140541880`. For non-local entities it returns false. For the local player it reads four client-setting bytes and skips hidden slots. | NPC visibility cannot be independently guaranteed by the EquipmentUpdate armor array. The viewer's saved four hide flags are all true. |
| Held weapon/offhand | `0x1409eda76..0x1409edb0b` requires both hand fields non-null, normalizes literal `Empty` to null, then invokes `0x140543e00`. That helper has an additional active-item/override branch; it is not an unconditional direct assignment. | Both IDs should be explicit. A successful send or warning does not establish the final visible hands. |
| Native inventory selection | The native selection path `0x1404898b0` reads selected items from its inventory arrays and calls `0x140489d10`, which calls the same hand-update helper `0x140543e00`. | There are competing native writers to the same client representation. This is a concrete route for local equipment to reassert; exact ordering in the reported sand-item incident was not observed. |

The local-player EquipmentUpdate diagnostic is emitted **after** the hand-update
call. Treating it as an early rejection was an incorrect inference. It also does not
constitute an acknowledgement that an arbitrary NPC overlay has rendered correctly.

Client settings inspected read-only: `...\Hytale\UserData\Settings.json`, lines
344 onward. A future controlled test can distinguish the visibility gate by comparing
clients with different hide settings, but changing the user's settings is not a
production solution and was not done here.

### MainMenu / MyAvatar and other native mechanisms

- `MainMenu\MyAvatar\MyAvatarPage.ui:355` uses **PlayerPreviewComponent**, not
  CharacterPreviewComponent. It has a Scale value and geometry. `MainMenu\HomePage.ui:137`
  also uses PlayerPreviewComponent.
- MyAvatar exposes cosmetic selection/reset controls. Its presence does not prove
  that server Custom UI can supply an arbitrary skin/equipment object to it. The
  complete compiled MyAvatar controller-to-renderer implementation has **not** been
  recovered in this pass; no supported server binding to it was demonstrated.
- Another compiled renderer accepts a network ID (`0x140522ec0`, configuration
  `+0x4C`) and resolves a local/remote entity (`0x140525100`). Its caller at
  `0x140436900` is identified by `[VoicePortrait]` diagnostics. It creates a 96x96
  head-oriented render viewport. The shipped voice entry has a programmatic portrait
  attachment area. This is a real native entity-targeted mechanism, but no supported
  server Custom UI API to instantiate/configure it was found. Its inspected render
  refresh copies the body model, not the CharacterPreview attachment-list path.
- The nearby ItemPreview renderer is for item assets; it is not an arbitrary NPC
  equipment preview. Its manager's compiled diagnostic explicitly says ItemPreview.
- The installed server protocol contains AssetEditorUpdateModelPreview (asset path,
  model/block, camera) and SetMachinimaActorModel (scene/actor/model). Their schemas do
  not address a CharacterPreviewComponent selector or preview instance. They are not
  demonstrated replacements for the in-page preview. ModelDisplay is a node attachment
  transform structure, not a UI preview target packet.
- Official generated Custom UI documentation lists CharacterPreviewComponent with
  generic layout properties and no target property. PlayerPreviewComponent is absent
  from that published list. This corroborates, but does not replace, shipped-code
  inspection: [Hypixel-authored type documentation](https://hytalemodding.dev/en/docs/official-documentation/custom-ui/type-documentation),
  [CharacterPreviewComponent properties](https://github.com/HytaleModding/site/blob/main/content/docs/en/official-documentation/custom-ui/type-documentation/elements/characterpreviewcomponent.md).

## Current R146 sequence and restoration audit

Source: `C:\HytaleMigration\persistent-npcs\src\main\java\com\inigmasgames\persistentnpcs\ui\NpcMeshPreviewSession.java`.

The class reads authoritative viewer ModelComponent, PlayerSkinComponent and equipment
for its baseline. It sends EntityUpdates containing ModelUpdate, PlayerSkinUpdate and
EquipmentUpdate to the **viewer network ID**, through that viewer's packet handler
using writeNoCache. These are outbound visual updates, not authoritative server
inventory/container/ECS mutations. Native client handling is broader than a private
widget override, however: the target remains the local client entity.

On close the class sends baseline model, skin and equipment and explicitly logs
`RESTORATION_COMPLETED_ASSUMED` / `WRITE_NO_CACHE_NO_CLIENT_ACK`. The baseline equipment
is captured once at opening. No selected-hotbar presentation snapshot is captured or
verified by this class. Its lack of an authoritative inventory write is good, but is
not proof of exact client visual restoration, especially after legitimate player
inventory transactions during the page's lifetime.

Existing safety tests principally verify source structure, packet ordering and the
absence of authoritative player mutations. They cannot prove rendered armor, hand
selection, visibility independence or connected restoration.

Correlated existing logs (read-only):

- Client `...\UserData\Logs\2026-09-04_16-33-25_client.log`.
- Server `...\UserData\Saves\NPC\logs\2026-09-04_16-33-30_server.log`.
- Hoit's R146 session logged explicit empty hands, then Trork armor updates, then
  restoration of the viewer's Soil_Sand_White hand baseline. These prove server intent
  and the restoration-send path, not what appeared on the client at each frame.

## Gates and next isolated test

| Gate | Result |
| --- | --- |
| Authoritative player mutation required by current preview class | No such mutation found in that class; packets are viewer-scoped. |
| Inventory preview source identified | Yes: local client player model and attachments. |
| Reason NPC armor can disappear | Yes: local-player client visibility filter; all four saved hide flags enabled. |
| Warning proves EquipmentUpdate rejected | No; compiled handler attempts application before warning. |
| Existing packet sequence establishes private NPC preview ownership | No. |
| Arbitrary equipment works independently of viewer settings/native inventory | Not established. |
| Full MyAvatar implementation / server-addressable alternative | Incomplete / not established. |
| Exact skin, armor, hands, visibility and hotbar restoration | Connected validation pending. |
| Safe to resume production preview repairs or deploy | No. |

The next meaningful runtime experiment must be in a separate disposable test world
and non-production build, with explicit before/after authoritative inventory/ECS
fingerprints and client observation. Test model-only, skin-only, equipment-only and
the combined sequence; empty/weapon/shield hands; all four armor slots; ordinary
native inventory refresh; close, child-editor transitions and disconnect. Compare
visibility-enabled and visibility-hidden clients. Observe both the page and the
player's first/third-person presentation. Do not implement a packet rewrite loop or
send fake inventory containers merely to make this pass.

No connected runtime experiment was conducted in this pass. Do not label the above
matrix PASS, and do not conclude universal lack of support from incomplete MyAvatar
tracing. The production hold remains the safe checkpoint while those gates are open.

## Deferred repairs retained, not implemented

1. Native inventory cell size is **74px**, with **2px** spacing, in shipped
   `InGame\Common.ui:4-5`; current Profile compact 48px cells do not match native size.
2. `NpcStatsSnapshotService.capture` rejects null live authority or a missing live
   EntityStatMap before calculating armor Defense. `NpcProfilePage.captureStats`
   clears the entire snapshot for an unspawned NPC. Armor-derived Defense should be
   calculated from authoritative persisted equipped armor independently of live stats.
   Do not fabricate Health/Stamina/Mana when their live authority is unavailable.

These repairs stay queued. The preview is not hidden or substituted, and the R146
checkpoint remains intact.
