# R179-U7P5 inventory client metadata hotfix

The user reached the world on R178-U7P5 and pressed Tab. The client disconnected
while applying `#WorkspaceNativeGrid.Slots`. The latest client log
(`data/pre-release/Logs/2026-10-01_12-35-09_client.log`, line 38706) gives the
reason:

```text
CustomUI Set command couldn't set value. Selector: #WorkspaceNativeGrid.Slots
-> Failed to convert JSON value (Object) to specified type (ClientItemMetadata)
```

The paired server log shows `INVENTORY_POST_OPEN_BRIDGED` followed by the client
leaving and the singleplayer server shutting down. There is no server exception
at that point. R178 fixed the first client schema error (`QualityOverride`) and
exposed this second, independent error in the same slot update.

## Boundary and fix

U7P5's server `ItemGridSlot.CODEC` serializes the authoritative server
`ItemStack`, including its arbitrary BSON `Metadata`. The client CustomUI
deserializer expects its typed `ClientItemMetadata` instead. The installed
client's reflection strings show named metadata members (`Adventure`,
`CapturedEntity`, `ItemDisplay`, `Extra`, `Ephemeral`), but do not define a
verified lossless conversion for Hywind's private `RpgGearV1` payload and
formatted server messages. Sending the entire server metadata document is not
a valid UI projection.

The shared native ItemGrid writer now removes `Metadata` from the outgoing UI
slot value. It reads the server's `ItemDisplay` `Name` and `Description`,
flattens their text, and supplies it through the native `ItemGridSlot.Name` and
`ItemGridSlot.Description` fields. The R178 `QualityOverride` to `Quality`
translation remains. This applies to bag cells, all gear targets and the alias
probe. Empty cells are unchanged. The actual `ItemStack`, its saved
`RpgGearV1` data, durability, quality override and transaction ownership are
not mutated.

The slot `Description` field is plain text. Affix values and requirements
remain readable, while per-line colors and emphasis need connected-client
inspection before any later styling work. No client or server was launched in
offline validation; the user performs connected QA in the existing RPG save.

## Verification and user QA

Focused native tests reproduce both server-only fields (`QualityOverride` and
arbitrary BSON metadata), assert they are absent from the client slot, verify
the display name and affix description text, and confirm that the original
stack's persistence encoding stays unchanged. Coverage includes spatially
aliased bag cells and all eight gear targets.

The full build runs JUnit, native codec tests, package validation and the
frozen R176 U7P5 asset/reference audit. Results and deployment receipt are in
`evidence/hyarpg/R179-U7P5-inventory/`.

**Passed:** 3,152 main, 394 native control, 49 CanvasUI and 5 Tavern JUnit
tests (3,600 total; no failures, errors or skips). The asset audit passed for
4,556 packaged JSON assets and 40,440 references. **Deployed:** R179-U7P5 to
the canonical RPG save, with R178 backed up outside `Saves` and the installed
JAR SHA-256 verified against the build. The receipt records both hashes.

In game, join the canonical **RPG** save, confirm **R179-U7P5**, press Tab,
hover a normal item and affixed gear, then close and reopen with Escape. If the
inventory opens, check name, rarity, affix text, quantity and durability;
then equip/unequip and drop/pick up one item.
