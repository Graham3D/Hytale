# R119 NPC Profile Production Inventory Integration

## Scope and status

R119 promotes the connected-validated R118 server-authoritative Custom UI
transaction bridge into the production `/npc update [name]` NPC Profile page.

The production candidate preserves the existing one-page layout:

```text
NPC Gear                         NPC Profile
NPC model preview                Profile / Skin / Voice Samples

NPC Inventory                   Player Inventory
```

Only the bounded R118 operation is enabled:

- left-click;
- full authoritative stack;
- Player Storage to NPC Storage or NPC Storage to Player Storage;
- empty destination slot only.

No gear-drop behavior, internal rearrangement, occupied-slot swap/merge,
right-click split, or shift-click behavior is enabled by this revision.

The R119 deterministic suite is green. Connected acceptance for the production
page is intentionally limited to one move in each direction plus close/reopen
persistence.

## Frozen rollback artifacts

Before production source changes, the deployed R118 JAR was copied and hashed.

R118 rollback artifact:

```text
G:\My Drive\Inigmas Games\Hytale Persistent NPCs\dist\rollback\ImmersiveNPCs-0.6.0-pre.13.1-R118-SERVER-AUTHORITATIVE-CUSTOM-UI-BRIDGE.jar
SHA-256: E6E851DFEBD750C8ED2F5E04CF24EA33DB75A5A45BBA2DF90DD4BB2FEC5DC249
```

The formerly deployed copy was moved out of `mods` to this additional recoverable
location after R119 deployment:

```text
G:\My Drive\Inigmas Games\Hytale Persistent NPCs\dist\rollback\ImmersiveNPCs-0.6.0-pre.13.1-R118-SERVER-AUTHORITATIVE-CUSTOM-UI-BRIDGE-deployed-retired.jar
SHA-256: E6E851DFEBD750C8ED2F5E04CF24EA33DB75A5A45BBA2DF90DD4BB2FEC5DC249
```

Pre-change production source hashes:

```text
NpcProfilePage.java                         A8D7A8013429FAE3E222243790E9D6E49B5B71A86EE9FD522CA84B63B58F8AD7
ImmersiveNpcProfile.ui                      60A7F4810E3A086917DD466C5A24C32864ACBBBD62960087092E7ED3EF2877CF
NpcInventoryRepository.java                9E17C3D67500FF197847598D2982F33AC3D41B99364FD7B73242479882F9BA14
NativeNpcInventoryController.java           F20FB83BE3DFB1C716D8D57FB31F2731419A3E1AF758135FEC69C2D7B6D479BD
CustomInventoryTransactionBridge.java      7D55460EA9CA402A35F376364D1A57DB8D3C4FC2F1DE97770986BCB8D6C41B76
```

The production layout asset itself was not rewritten in R119. It already contained
the two lower `ItemGrid` controls and the preserved upper profile/gear layout.

## Deployed artifact

Exactly one ImmersiveNPCs JAR is present in the NPC save's `mods` directory:

```text
C:\Users\Zemio\AppData\Roaming\Hytale\UserData\Saves\NPC\mods\ImmersiveNPCs-0.6.0-pre.13.1-R119-NPC-PROFILE-PRODUCTION-INVENTORY-INTEGRATION.jar
Size: 2,122,915 bytes
SHA-256: 68F8F5C6D5332C4BF63E3555B641E2DCFF290A56B3CC5CFF99EBD80E94B1FEE5
```

The server/world must be restarted before connected testing so the Java classes and
manifest revision are reloaded.

## R118 connected evidence used as the production gate

Source log:

```text
C:\Users\Zemio\AppData\Roaming\Hytale\UserData\Saves\NPC\logs\2026-09-01_23-28-03_server.log
```

The log proves both directions committed through `InventoryUtils.moveItem`:

```text
Player -2 slot 11 -> NPC window 1 slot 19
client quantity diagnostic: 100
authoritative requested quantity: 12
before: Soil_Sand_Red x12 -> EMPTY
after:  EMPTY -> Soil_Sand_Red x12
result: COMMITTED

NPC window 1 slot 19 -> Player -2 slot 11
client quantity diagnostic: 100
authoritative requested quantity: 12
before: Soil_Sand_Red x12 -> EMPTY
after:  EMPTY -> Soil_Sand_Red x12
result: COMMITTED

Player -2 slot 13 -> NPC window 1 slot 29
client quantity diagnostic: 100
authoritative requested quantity: 98
before: Weapon_Arrow_Crude x98 -> EMPTY
after:  EMPTY -> Weapon_Arrow_Crude x98
result: COMMITTED

Player -2 slot 20 -> NPC window 1 slot 29
client quantity diagnostic: 1
authoritative requested quantity: 1
before: Armor_Trork_Hands x1 -> EMPTY
after:  EMPTY -> Armor_Trork_Hands x1
result: COMMITTED
```

This demonstrates that the `100` seen for partial-stack drag previews is not the
quantity used by the server move.

## Production architecture

### Open lifecycle

1. `/npc update [name]` resolves the exact `NpcProfile`.
2. `NativeNpcInventoryController.resolve(...)` locates the authoritative live NPC.
3. The profile ID and stable ID must match.
4. The target entity UUID and `Ref<EntityStore>` are captured.
5. `NpcInventoryRepository.ensureRuntimePersistence(...)` hydrates an empty restored
   runtime container when necessary and attaches the normal repository listener.
6. The exact live `InventoryComponent.Storage.getInventory()` object is captured.
7. `NpcInventoryRepository.openWithLiveStorage(...)` verifies the live Storage
   snapshot equals the persisted profile inventory before the page may open.
8. The existing armor and loadout authoring containers are retained.
9. The lower NPC grid's `ContainerWindow` wraps the exact live Storage object.
10. The page opens through `PageManager.openCustomPageWithWindows(...)` with the
    existing equipment windows plus that live Storage window.
11. The mesh preview is applied only after page/window construction, preserving the
    validated restoration lifecycle.

If the profile exists but is not spawned, the NPC Profile remains accessible with a
read-only persisted storage snapshot. No bridge is created and no inventory drop
events are accepted. Identity mismatches other than the explicit not-spawned case
fail the command rather than silently selecting another container.

### Transaction lifecycle

1. The two Custom UI grids are presentation surfaces only.
2. Each grid emits only a `Dropped` intent.
3. The server discards the client quantity for authority decisions and rereads the
   source slot from the resolved container.
4. `CustomInventoryTransactionBridge` performs all identity, section, slot, operation,
   and duplicate checks.
5. The sole mutation call is `InventoryUtils.moveItem(...)`.
6. Both source and destination containers are reread after the call.
7. A move is classified `COMMITTED` only if the source became empty and the
   destination exactly equals the pre-move authoritative stack.
8. Both complete fixed-capacity slot arrays are refreshed atomically from current
   server state, whether the request committed or was rejected.

There is no `setItemStackForSlot`, manual remove/add pair, client-side inventory
mutation, or custom close recovery in the bridge.

## Exact Custom UI event schema

Both production grids use the same event contract as R118 Probe 11:

```json
{
  "Marker": "CUSTOM_BRIDGE_DROP",
  "Event": "Dropped",
  "Section": "<destination section id>",
  "SlotIndex": "<destination slot supplied by ItemGrid>",
  "SourceSlotId": "<source slot supplied by ItemGrid>",
  "SourceInventorySectionId": "<source section supplied by ItemGrid>",
  "ItemStackId": "<diagnostic only>",
  "ItemStackQuantity": "<diagnostic only>",
  "PressedMouseButton": "<mouse button supplied by ItemGrid>"
}
```

`Section` is a constant bound by the server to the destination grid. The source
section/slot and destination slot are treated as untrusted input until resolved
against the current page/window/container state.

## Validation rules

Every intent must pass all of the following before mutation:

1. bridge session remains active;
2. event session UUID equals the active page session UUID;
3. event page generation equals the active page generation;
4. viewer reference is valid and is the captured viewer reference;
5. the active Custom UI page is the exact `NpcProfilePage` instance;
6. captured NPC `Ref<EntityStore>` remains valid;
7. current NPC UUID equals the captured entity UUID;
8. current NPC ECS Storage object is the captured Storage object by identity;
9. persisted stable NPC ID still equals the captured profile ID;
10. any runtime-registry profile mapping, when present, equals that profile ID;
11. the NPC `ContainerWindow` is still registered and active;
12. both section IDs are either Player Storage `-2` or the active NPC window ID;
13. `InventoryUtils.getSectionById(...)` resolves those sections to the exact captured
    player/NPC container objects;
14. both slot IDs are within current container capacity;
15. source and destination are different containers;
16. mouse button is left-click (`1` in the connected event contract);
17. source is non-empty at validation time;
18. requested quantity is positive and no greater than the authoritative source;
19. requested quantity equals the complete authoritative source stack;
20. destination is empty;
21. the request is not a duplicate release fingerprint within two seconds.

After those checks, `InventoryUtils.moveItem` still performs Hytale's native
transaction validation.

## Supported behavior matrix

| Operation | R119 behavior |
|---|---|
| Player Storage -> NPC Storage, left-click full stack, empty target | Enabled |
| NPC Storage -> Player Storage, left-click full stack, empty target | Enabled |
| Accurate authoritative quantity | Enabled; source is reread server-side |
| Item metadata/durability/quality preservation | Native `ItemStack` and `InventoryUtils` path |
| Duplicate drop release | Suppressed and refreshed from authority |
| Close/reopen NPC persistence | Repository runtime listener plus terminal repository flush |
| Native Bench fallback | Preserved through `Open Native Inventory` |

## Rejected behavior matrix

| Operation | R119 result |
|---|---|
| Player internal rearrangement | Rejected: `OPERATION_NOT_ENABLED_INTERNAL_MOVE` |
| NPC internal rearrangement | Rejected: `OPERATION_NOT_ENABLED_INTERNAL_MOVE` |
| Drop onto occupied slot | Rejected: `OPERATION_NOT_ENABLED_OCCUPIED_DESTINATION` |
| Swap | Rejected by occupied-destination gate |
| Merge | Rejected by occupied-destination gate |
| Partial-stack move | Rejected: `OPERATION_NOT_ENABLED_PARTIAL_STACK` |
| Right-click split | Rejected: `OPERATION_NOT_ENABLED_MOUSE_BUTTON` |
| Shift-click | No production binding/handler |
| Drop onto armor/loadout/gear UI | No production storage-drop binding |
| Foreign/stale section or window | Rejected before mutation |
| Stale/empty source | Rejected before mutation |
| Closed or replaced page | Rejected before mutation |
| NPC identity/storage drift | Rejected before mutation |

Rejected operations immediately refresh both inventories from authority. They do
not leave the Custom UI snapshot as the source of truth.

## UI reconciliation

R119 retains R118 Differential A:

```text
authoritative move/rejection
-> reread exact NPC and Player containers
-> replace complete NPC .Slots array
-> replace complete Player .Slots array
-> send both replacements in one Custom Page update
```

Every physical slot is encoded, including empty slots. Each slot receives its exact
`InventorySlotIndex`, `IsActivatable=true`, and `IsItemIncompatible=false` fields.
Occupied slots retain the full native `ItemStack` encoding.

The grids keep their real section IDs because those values are needed in the drop
intent and in `InventoryUtils.getSectionById`. The slot arrays remain presentation,
not mutation authority.

## Quantity-preview diagnosis

Connected R118 evidence shows a consistent client-only discrepancy:

- the drag payload/ghost may report the item's maximum stack quantity (`100`);
- the authoritative source can actually contain `12` or `98`;
- the bridge rereads the source and requests exactly `12` or `98`;
- the native move result contains exactly that authoritative quantity;
- both reconciled inventories are correct.

The likely client trigger is the current ItemGrid drag presentation combined with
`AllowMaxStackDraggableItems`, but changing that property was not connected-validated
and could regress the newly proven pickup/drop event path. R119 therefore does not
speculatively change it. The discrepancy is treated as a known visual-only
limitation; it never controls mutation quantity.

## Persistence lifecycle

The production candidate does not introduce a second persistence implementation.

- The live target is the actual NPC ECS `InventoryComponent.Storage` container.
- `NpcInventoryRepository.ensureRuntimePersistence(...)` remains the runtime change
  observer and disk writer.
- `openWithLiveStorage(...)` makes the existing NPC Profile authoring session snapshot
  the same live Storage object.
- That authoring session intentionally does not attach a second change listener to
  live Storage.
- The repository's single-thread writer preserves write order.
- On dismiss, the bridge is invalidated first, then the existing inventory session
  performs its normal terminal flush.
- No item is returned to the viewer on close; the NPC retains it.
- The native Bench fallback continues to open the same live Storage authority.

The persistence file remains:

```text
<save>\mods\ImmersiveNPCs\profiles\<NPC>\npc-inventory.json
```

## Diagnostics

R119 retains the R118 markers:

```text
CUSTOM_BRIDGE_INTENT
CUSTOM_BRIDGE_VALIDATED
CUSTOM_BRIDGE_REJECTED
CUSTOM_BRIDGE_NATIVE_MOVE
CUSTOM_BRIDGE_NATIVE_RESULT
CUSTOM_BRIDGE_DUPLICATE_SUPPRESSED
CUSTOM_BRIDGE_SESSION_CLOSE
```

It adds production-context markers:

```text
NPC_PROFILE_INVENTORY_BRIDGE_BUILD
NPC_PROFILE_INVENTORY_RAW_EVENT
NPC_PROFILE_INVENTORY_REFRESH
NPC_PROFILE_LIVE_STORAGE_UNAVAILABLE
NPC_PROFILE_NATIVE_WINDOWS_BOUND
```

These include the profile ID, NPC entity UUID, viewer UUID, bridge session ID, page
generation, window/section IDs, operation ID, result/reason, and authoritative
before/after summaries where applicable.

## Changed classes and assets

Production/runtime changes:

```text
src/main/java/com/inigmasgames/persistentnpcs/command/AbstractImmersiveNpcProfileCommand.java
src/main/java/com/inigmasgames/persistentnpcs/profile/NpcInventoryRepository.java
src/main/java/com/inigmasgames/persistentnpcs/ui/CustomInventoryTransactionBridge.java
src/main/java/com/inigmasgames/persistentnpcs/ui/CustomInventoryBridgeUi.java
src/main/java/com/inigmasgames/persistentnpcs/ui/CustomInventoryBridgeProbePage.java
src/main/java/com/inigmasgames/persistentnpcs/ui/NativeNpcInventoryController.java
src/main/java/com/inigmasgames/persistentnpcs/ui/NpcProfilePage.java
src/main/java/com/inigmasgames/persistentnpcs/PersistentNpcsPlugin.java
src/main/resources/manifest.json
```

Build/install/test/report changes:

```text
build.ps1
install.ps1
test.ps1
src/test/java/com/inigmasgames/persistentnpcs/R092NpcCreateInventoryUiTest.java
src/test/java/com/inigmasgames/persistentnpcs/R101NpcProfileTargetedRepairTest.java
src/test/java/com/inigmasgames/persistentnpcs/R102NpcProfileNativeWindowTest.java
src/test/java/com/inigmasgames/persistentnpcs/R103NpcProfileGridMaterializationTest.java
src/test/java/com/inigmasgames/persistentnpcs/R118ServerAuthoritativeCustomUiBridgeTest.java
src/test/java/com/inigmasgames/persistentnpcs/R119NpcProfileProductionInventoryIntegrationTest.java
docs/R119_NPC_PROFILE_PRODUCTION_INVENTORY_INTEGRATION.md
```

Unchanged production UI layout asset:

```text
src/main/resources/Common/UI/Custom/Pages/ImmersiveNpcProfile.ui
```

The existing mesh preview, voice table, gear UI, profile fields, file browser, and
footer layout were not replaced.

## Verification performed

```text
.\test.ps1 -SkipLive
Result: PASS
```

This includes all deterministic project gates plus:

```text
R118 isolated server-authoritative Custom UI bridge gate passed.
R119 NPC Profile production inventory integration gate passed.
R105 NPC Profile mesh preview integration tests passed.
R115 native NPC inventory Profile integration gate passed.
```

## Remaining risks

1. R119's production page still requires connected-client acceptance; the exact bridge
   is proven, but its production selectors and coexistence with the full page need the
   requested two-move validation.
2. The floating drag preview may display maximum stack quantity rather than the
   authoritative quantity. Server state remains correct.
3. Internal moves, merges, swaps, splits, and shift-click are deliberately disabled.
4. A non-spawned update target exposes a read-only persisted storage snapshot. It does
   not allow an ambiguous detached transaction.
5. If the live entity UUID, profile identity, window, page, or Storage object changes
   while the page is open, the operation is rejected. The user must close and reopen.
6. Gear transactions are not routed through the R119 storage bridge and are outside
   this acceptance gate.

## Connected acceptance checklist

After restarting the world/local server:

1. Run `/npc update Jonalith` (or another spawned target).
2. Confirm NPC Gear, NPC Profile, mesh preview, voice table, NPC Inventory, and Player
   Inventory are visible on the same page.
3. Choose one complete stack and an empty destination.
4. Move exactly one stack from Player Inventory to NPC Inventory.
5. Confirm the source becomes empty and the exact stack appears in the NPC slot.
6. Choose another complete stack and an empty Player destination.
7. Move exactly one stack from NPC Inventory to Player Inventory.
8. Confirm both inventories are correct; do not test internal moves, occupied targets,
   splitting, merging, shift-click, or gear drops in this gate.
9. Close the NPC Profile.
10. Reopen it with `/npc update Jonalith`.
11. Confirm the retained NPC item is still in its exact authoritative slot and neither
    inventory contains a duplicate or missing stack.
12. If any step fails, stop and preserve the server log containing
    `NPC_PROFILE_INVENTORY_*` and `CUSTOM_BRIDGE_*` markers.
