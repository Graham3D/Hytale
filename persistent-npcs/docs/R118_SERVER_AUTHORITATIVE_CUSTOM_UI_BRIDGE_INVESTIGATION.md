# R118 Server-Authoritative Custom UI Bridge Investigation

Status: **Probe 11 deployed; connected simple-move and UI-reconciliation gate pending.**

Deployed artifact:

```text
C:\Users\Zemio\AppData\Roaming\Hytale\UserData\Saves\NPC\mods\ImmersiveNPCs-0.6.0-pre.13.1-R118-SERVER-AUTHORITATIVE-CUSTOM-UI-BRIDGE.jar
SHA-256 E6E851DFEBD750C8ED2F5E04CF24EA33DB75A5A45BBA2DF90DD4BB2FEC5DC249
```

Exactly one `ImmersiveNPCs-*.jar` is installed. The previously deployed R117 artifact
was moved intact to `dist/rollback` for immediate recovery.

This is an isolated Option 2 investigation. It does not modify the production NPC
Profile, NPC persistence, or the proven `Page.Bench + ContainerWindow` inventory path.

## 1. Frozen production baseline

Before R118 implementation, the deployed R117 artifact was compared to the preserved
R115 production-integration artifact. The following packaged artifacts were byte
identical:

| Artifact | SHA-256 in both R115 and R117 |
|---|---|
| `NpcProfilePage.class` | `5C956E2117B52974552F79FC7CDCA9B79B80BDFF6A20E8C297A2A34EDF5B61C2` |
| `ImmersiveNpcProfile.ui` | `60A7F4810E3A086917DD466C5A24C32864ACBBBD62960087092E7ED3EF2877CF` |
| `NpcInventoryRepository.class` | `22482E926D7657DAF9BB0E6B1355F1B28513136B3EB53A06FE88DDB4E84EF569` |
| `NativeInventoryControlWindow.class` | `988026FB80F8C89154F42F33D7F733A238170957A85DBE1EC9B3D605BD137452` |
| `NativeNpcInventoryController.class` | `0345328D3EDE3FE9EE787BEECAD93890D1C35C19C9258FF624DEB865FB69BFF8` |

R118 adds only isolated Probe 11 classes and command routing. The post-build R118 JAR
was compared against R115 and all five artifacts in the table remained byte identical.

## 2. Exact bridge architecture

Command:

```text
/nativeinventoryprobe 11
```

Runtime topology:

```text
Interactive Custom UI page
├─ LEFT: ephemeral 40-slot SimpleItemContainer
│  └─ registered active ContainerWindow (non-negative section ID)
└─ RIGHT: actual viewing Player Storage (section -2)

Dropped event (untrusted intent)
  -> CustomInventoryTransactionBridge
  -> session/section/slot/source/thread validation
  -> InventoryUtils.moveItem(...)
  -> re-read both ItemContainers
  -> replace both fixed-capacity ItemGrid .Slots arrays in one CustomPage update
```

The page owns presentation and reconciliation. The dedicated bridge owns validation,
replay protection, and invocation of the native transaction API. Movement logic is not
embedded directly in the UI handler.

## 3. Authoritative event trigger

Only `CustomUIEventBindingType.Dropped` can request a move. Probe 11 does not bind
`SlotMouseDragCompleted`, `SlotClickReleaseWhileDragging`, hover events, or cancellation
events as mutation triggers. This prevents the related event family from producing
multiple bridge calls for one release.

The decoded event supplies:

- target section from the server-authored event binding;
- target physical slot from `SlotIndex`;
- source section from `SourceInventorySectionId`;
- source physical slot from `SourceSlotId`;
- mouse button from `PressedMouseButton`;
- client item ID and quantity for diagnostics only.

The event's item ID and quantity are not mutation authority. For the full-stack-only
gate, the page observes the authoritative source quantity when creating the immutable
intent, and the bridge re-reads and validates it immediately before mutation.

## 4. Immutable intent contract

`InventoryMoveIntent` contains:

```text
sessionId
pageGeneration
sourceSectionId
sourceSlotId
targetSectionId
targetSlotId
requestedQuantity
mouseButton
eventSequence
clientItemIdDiagnostic
clientQuantityDiagnostic
```

Each bridge request receives a monotonically increasing `BridgeOperationId` used in
all validation, native-move, result, refresh, and drag-reset records.

## 5. Validation contract

Before `InventoryUtils` is called, Probe 11 verifies:

- the bridge session is active;
- session UUID and page generation match;
- the entity reference is valid and belongs to the viewing `PlayerRef`;
- this exact Custom UI page is still the active custom page;
- the exact `ContainerWindow` remains registered under its allocated ID;
- both section IDs are either Player Storage `-2` or the exact probe window ID;
- `InventoryUtils.getSectionById` resolves both IDs;
- each resolved container is the expected object by identity;
- source and target slots are within their physical container capacities;
- source and destination are different;
- the current Phase 4 operation crosses between the two containers;
- mouse button is left click (`1`);
- the authoritative source is non-empty;
- requested quantity is positive and within the authoritative source quantity;
- the request still equals the complete authoritative stack quantity;
- the destination is empty.

Internal moves, partial stacks, occupied destinations, merges, swaps, right-click, and
shift-click fail closed until the basic gate passes.

`Store.isInThread()` verifies mutation context. An unexpected off-thread callback is
marshalled through `World.execute`, after which the entire validation contract runs
again on the authoritative world thread.

## 6. Replay and duplicate protection

A two-second fingerprint cache uses:

```text
page generation
source section and slot
target section and slot
requested quantity
mouse button
```

Only the first matching release may execute. A repeated release is logged as
`CUSTOM_BRIDGE_DUPLICATE_SUPPRESSED`. Source state is also re-read, providing a second
fail-closed defense against a delayed native packet or stale client replay.

## 7. Native transaction API and mutation proof

The sole ordinary movement call is:

```java
InventoryUtils.moveItem(
    ref,
    sourceSectionId,
    sourceSlotId,
    authoritativeFullStackQuantity,
    targetSectionId,
    targetSlotId,
    store
);
```

`CustomInventoryTransactionBridge` contains no `setItemStackForSlot`, direct remove,
direct add, or copy/delete transfer sequence. Hytale's registered `ItemContainer`s and
`InventoryUtils` remain the sole transaction authority.

Probe teardown retains the already-proven recovery routine required for an ephemeral
container. That recovery path is not used for ordinary bridge movement.

## 8. Native result classification

The installed `InventoryUtils.moveItem` signature returns `void`. For ordinary
storage/window moves, current-build bytecode calls the native container move
synchronously. Probe 11 therefore re-reads the authoritative source and target
immediately after the API returns.

For the Phase 4 empty-slot full-stack operation:

- `COMMITTED`: source is empty and target exactly equals the authoritative source-before stack;
- `REJECTED`: post-state does not match the requested native result;
- `NO_OP`: source and destination are identical;
- `STALE`: authoritative source is empty or its quantity changed;
- `INVALID`: session, section, slot, operation, or security validation failed;
- `DUPLICATE`: replay fingerprint was suppressed.

The UI is never updated from assumed stack arithmetic. Both grids are always rebuilt
from current container contents after the result.

## 9. Custom UI refresh mechanism

Both grids use the Probe 5 fixed-capacity `.Slots` representation. Every physical slot
is encoded with:

```text
ItemStack = authoritative current server value
InventorySlotIndex = physical index
IsActivatable = true
IsItemIncompatible = false
```

NPC and Player arrays are replaced in one `CustomPage` update, NPC first and Player
second. Positional `.ItemStacks` is not used.

## 10. Drag/cursor reconciliation mechanism

The shipped server command set exposes only `Append`, `AppendInline`, `InsertBefore`,
`InsertBeforeInline`, `Remove`, `Set`, and `Clear`. No dedicated server command named
cancel-drag, clear-dragged-item, or release-cursor is exposed. Client strings show
private ItemGrid drag state and a public `MouseDownSlotIndex`, but do not establish that
setting the latter clears the global floating item.

R118 therefore starts with the least-invasive bounded differential required by the
specification:

```text
Differential A = replace both authoritative fixed-capacity .Slots arrays atomically
```

It logs `CUSTOM_BRIDGE_DRAG_RESET` with
`method=DIFFERENTIAL_A_SLOTS_REFRESH_ONLY`. Connected testing must determine whether
this supported slot replacement clears the carried-stack state. No second reset
variable is mixed into this artifact.

## 11. Structured markers

R118 adds:

```text
CUSTOM_BRIDGE_INTENT
CUSTOM_BRIDGE_VALIDATED
CUSTOM_BRIDGE_REJECTED
CUSTOM_BRIDGE_NATIVE_MOVE
CUSTOM_BRIDGE_NATIVE_RESULT
CUSTOM_BRIDGE_REFRESH
CUSTOM_BRIDGE_DRAG_RESET
CUSTOM_BRIDGE_DUPLICATE_SUPPRESSED
CUSTOM_BRIDGE_SESSION_CLOSE
```

## 12. Teardown and recovery

Dismissal order is:

1. mark page dismissed;
2. invalidate the bridge session;
3. reject all subsequent intent;
4. move remaining ephemeral stacks to Player Storage using container transactions;
5. use the established safe add/drop fallback only if Storage cannot receive all stacks;
6. assert/log whether the probe container is empty;
7. finish normal Custom UI/window dismissal.

## 13. Connected acceptance matrix

Only rows 1 and 2 are enabled in the initial artifact. Every later row is deliberately
blocked until the simple reconciliation gate is deterministic.

| Test | Implementation | Connected result |
|---|---|---|
| Player full stack -> empty NPC slot | Enabled | Pending |
| NPC full stack -> empty Player slot | Enabled | Pending |
| Cursor clears after committed move | Differential A | Pending |
| Rejected move snaps back and clears | Snapshot refresh present | Pending |
| Duplicate release suppression | Implemented | Pending |
| Close/recovery | Implemented | Pending |
| Player internal rearrangement | Gated off | Not run |
| NPC internal rearrangement | Gated off | Not run |
| Compatible merge | Gated off | Not run |
| Occupied-slot swap | Gated off | Not run |
| Right-click split | Gated off | Not run |
| Shift-click both directions | Gated off | Not run |
| Stale-intent connected test | Validation implemented | Pending after basic gate |
| 100-operation soak | Not eligible | Pending basic gate |

## 14. Current counters and final classification

These values cannot be assigned until connected testing:

| Metric | Result |
|---|---|
| Simple moves attempted | Pending |
| Simple moves committed | Pending |
| Loss count | Pending |
| Duplication count | Pending |
| Ghost/cursor-state failures | Pending |
| Double transactions | Pending |
| Wrong-slot/wrong-section moves | Pending |
| Final classification | **Pending** |
| Safe for production NPC Profile | **No — gate not yet green** |

Possible final classifications remain:

- `CUSTOM_BRIDGE_SUCCESS`
- `CUSTOM_BRIDGE_TRANSACTION_FAILURE`
- `CUSTOM_BRIDGE_UI_RECONCILIATION_UNSUPPORTED`

If Differential A does not clear the cursor, the next artifact may test exactly one of
the ordered reconciliation differentials B–G. If no supported mechanism converges the
Custom UI reliably, the investigation stops with
`CUSTOM_BRIDGE_UI_RECONCILIATION_UNSUPPORTED`; production remains the Profile/native
Bench two-page architecture.

## 15. Deterministic verification

The complete deterministic suite passed with live local-model tests intentionally
skipped. This includes the new `R118ServerAuthoritativeCustomUiBridgeTest` plus all
existing R092/R101/R102/R107/R113/R114/R115/R117 inventory and NPC Profile gates.
