# Taverns Architecture

## Durable layers

Taverns separates durable establishment state from physical world references and temporary simulation.

- `TavernRecord` is the durable establishment identity. Its Tavern UUID remains stable if ownership changes.
- `CoreRecord` is a durable physical Core reference with its own Core UUID, type, world position, cuboid bounds, expansion investment, and intersected chunks.
- Runtime Zoning Editor sessions are held by `CoreModeManager` and are never persisted. Zoning Editor temporarily changes only the client presentation; authoritative game mode and inventory state remain unchanged.

Patrons, active orders, path requests, seat reservations, and employee tasks will be added as disposable runtime simulation. A restart will eventually discard that layer and reload the Tavern closed.

## Core framework

- `CoreDefinition` contains type-specific dimensions, asset IDs, expansion material and exchange rate, and volume limit.
- `CoreDefinitions` registers Core types that currently have working assets. Tavern, Kitchen, and Bedroom are enabled; Bar and Reserved remain unimplemented.
- `CoreRegistry` stores Cores by UUID and maintains a world/chunk index for position and intersection queries.
- `CoreValidator` owns spatial invariants. Tavern Cores cannot overlap other Taverns and must contain all of their specialized Cores. Specialized Cores must remain inside their Tavern and cannot overlap one another.
- `CoreModeManager` provides the shared six-face Zoning Editor and applies the selected Core's definition when calculating costs.

Kitchen and Bedroom use the shared editor and durable `CoreRecord` format. Each specialized Core has an independent UUID and parent Tavern UUID, allowing multiple Bedroom records to coexist and later carry tenant assignments without changing their identity. Bar and Reserved remain stable enum values but deliberately have no definitions or assets.

## Persistence schema 3

`taverns.properties` stores Tavern records and Core records separately. Chunk lists are persisted for inspection but rebuilt from cuboid bounds when loading, preventing a stale index from becoming authoritative.

The first load of schema 1:

1. Parses and validates the complete old file without changing live repository state.
2. Creates `taverns.properties.schema1.bak` once.
3. Preserves the Tavern UUID, owner, world, Core position, bounds, and expansion units.
4. Derives a deterministic stable primary Core UUID from the Tavern UUID.
5. Writes schema 2 atomically and reloads future sessions directly as schema 2.

Invalid or unsupported persisted data fails closed and is reported instead of being partially loaded.

## Validation

Run:

```powershell
.\test.ps1
```

The regression test migrates a schema-1 fixture, verifies all durable fields and spatial lookup, confirms the backup, and reloads the resulting schema-2 file.

## Hytale Inventory UI extension watch

As of the Hytale client build inspected on 2026-08-22, Tavern cannot safely attach Comfort or Relaxed UI to the native Inventory screen. `InventoryPage.ui` and `CharacterPanel.ui` are installation-owned client documents rather than shadowable `Common/UI/Custom` assets; neither exposes a server extension anchor or custom data context. `PageManager` has no supported Inventory open/close hook, and `UpdateAnchorUI` can update only registered anchors (the Inventory has none). Native `EntityEffect` icons are rendered by the gameplay `StatusEffectHud.ui`, not by the Inventory character panel.

The HyUI "parallel page" route was also checked against HyUI 0.9.8 and the current server/client classes. `PageBuilder.open(...)` delegates to `PageManager.openCustomPage(...)`, so it opens a replacement custom page rather than extending the native Inventory page. HyUI's `ItemGridBuilder.withInventorySectionId(...)` only sets the grid's `InventorySectionId` property; HyUI's documented grid flow supplies `ItemGridSlot` snapshots and handles click/drop events in plugin code. It provides no high-level `InventoryComponent` binding or native Inventory controller, and its documented examples do not demonstrate automatic inventory commits. The low-level protocol does reserve negative section IDs for the player's Hotbar, Storage, Armor, Utility, Tools, and Backpack, so a command-opened custom-page prototype could manually supply grid snapshots and test whether the client routes native inventory actions for those IDs. That would still require a separate, in-game validation before it could be considered safe. HyUI's parser recognizes `CharacterPreviewComponent` and `PlayerPreviewComponent` syntax but deliberately produces no builder for either component, while the vanilla `CharacterPanel.ui` preview and equipment grids are populated by installation-owned client code. There is also no server keyboard/keybind event from which Tavern can intercept the Inventory action and conditionally open a replacement page. A command- or interaction-opened Tavern management page remains possible, but it would be a separate custom UI and must not be treated as a transparent Inventory replacement.

After a major Hytale update, recheck:

1. `Client/Data/Game/Interface/InGame/Pages/Inventory/InventoryPage.ui` for a `ServerContent` or other extension anchor.
2. `Client/Data/Game/Interface/InGame/Pages/Inventory/CharacterPanel.ui` for a status-effect container or custom data binding.
3. `PageManager` and related events for a supported Inventory open/close notification.
4. `UpdateAnchorUI` and vanilla callers for a newly registered Inventory anchor.
5. Whether installation-owned `Client/Data/Game/Interface` documents have become officially shadowable by packaged asset packs.
6. Whether HyUI adds a live `InventoryComponent` binding, native character-preview binding, or a supported way to associate a custom page with the Inventory action.

Do not restore Inventory presentation through `CustomUIHud`, input polling, `InventoryChangeEvent`, or permanent edits to installed client files.

## Next framework

The next development layer is the registered-object framework: stable references and lifecycle states for Main Entrance, Chair, and Table, followed by a Tavern readiness report and Closed/Validating/Open lifecycle.
