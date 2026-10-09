# R178-U7P5 inventory slot hotfix

## Confirmed cause

The October 1 R177-U7P5 QA session joined successfully. Opening the Hywind
inventory then caused a **client CustomUI disconnect**, after which the
singleplayer server shut down normally. The server log records the inventory
open immediately before the client's departure, without a server exception.

Client log `data/pre-release/Logs/2026-10-01_12-18-12_client.log`, line 38580:

```text
CustomUI Set command couldn't set value. Selector: #WorkspaceNativeGrid.Slots
-> Property QualityOverride does not exist on HytaleClient.Data.Items.ClientItemStack
```

U7P5's server `ItemGridSlot.CODEC` delegates its nested stack to `ItemStack.CODEC`.
That persistence codec writes an optional `QualityOverride`. The installed
client's `ClientItemStack` still uses `Quality`; its native reflection metadata
includes `Durability`, `MaxDurability`, `Quality` and
`OverrideDroppedItemAnimation`, and has no `QualityOverride` field.

R177 validated the new **server persistence** encoding, but did not translate it
to the **client UI** encoding. The original regression test even expected the
server-only property in a UI command. It now tests the client contract instead.

## Fix

`NativeItemGrid.writeSlots` translates `QualityOverride` to `Quality` in the
outgoing UI command only. If the stack inherits its quality, it resolves the
registered Item asset's quality index so the native tooltip retains the proper
rarity. This is shared by the bag, gear, ring and single-item projections. The
native alias probe and outside drop target also use that serializer.

Item IDs, quantities, durability, metadata objects, formatted tooltip messages,
slot indices and equipment drag rules are retained. Empty cells remain empty.
The authoritative stacks, native inventory packets and persistence codec are
unchanged. No save migration, ownership rewrite or gameplay change is made.
All R177 U7P5 asset/API fixes remain in the build.

## Regression coverage

The focused native tests cover:

- Reproducing `QualityOverride` in the installed server codec and excluding it
  from the client slot projection.
- Preserving an explicit quality, quantity, durability and frozen gear metadata.
- Resolving inherited quality and leaving empty targets empty.
- Keeping covered spatial cells aliased to their existing authoritative source.
- Compatible tooltip payloads for all eight equipment targets, without changing
  protected equipment transfer or duplicating equipment artwork.
- Verifying that rendering does not change the original stack or its saved
  `QualityOverride` representation.

The full `gradlew.bat check jar` run retains the U7P5 asset/reference and frozen
R176 JSON semantics guard, native codec checks, CustomUI checks and the existing
inventory/transaction tests. Evidence is retained in
`evidence/hyarpg/R178-U7P5-inventory/`.

**Passed:** the complete build/check run, with 3,152 main, 394 native control,
49 CanvasUI and 5 Tavern JUnit tests (3,600 total, no failures/errors/skips).
The frozen R176 asset guard passes for 4,556 JSON assets and 40,440 references.
**Deployed:** R178-U7P5 to the canonical RPG save after backing up R177 outside
`Saves` and verifying matching built/installed SHA-256 checksums. The deployment
receipt and backup path are in `evidence/hyarpg/R178-U7P5-inventory/deployment.json`.

## User QA

1. Join the existing RPG save and confirm revision **R178-U7P5**.
2. Press Tab with the existing inventory populated. Close and reopen it.
3. Hover a normal item, affixed gear and equipped gear; verify the native
   tooltip's name, rarity, affixes, quantity and durability.
4. Equip/unequip and drop/pick up one item to check the existing controls.

No client or server was launched for offline validation. Connected-client
acceptance remains pending the user's QA.
