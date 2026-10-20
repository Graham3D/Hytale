# R175 native tooltip payload correction

The R174 client disconnected when Tab opened the inventory. The client log
`2026-10-01_09-00-57_client.log`, at 09:01:32.8298, identifies the failing
selector and conversion:

`#WorkspaceNativeGrid.Slots -> Failed to convert JSON value (String) to specified type (ClientItemMetadata)`

`NativeItemGrid.writeSlots` had converted the metadata document produced by
`ItemGridSlot` / `ItemStack.CODEC` into a JSON string, following the inventory
network packet's `ItemStack.toPacket().metadata` contract. CustomUI has a
different contract: its slot metadata is an object. The installed client binary
also contains the ClientItemMetadata and ItemDisplayMetadata serializer types.

The shared encoder now retains nonempty metadata documents and omits empty
metadata. The existing native tooltip ItemDisplay name/description, durability,
quality, quantities, source-cell aliases, and gear click/place protection are
preserved. Rendering does not mutate the authoritative item payload.

Regression assertions now inspect the emitted CustomUI object and formatted
item name, rather than expecting the network packet's string representation.
They cover each occupied cell of a spatial bow, empty cells, one-slot equipment,
and equipped cursor target grids. The object assertions fail against R174.

Validation logs: `build/r175-focused.log` and `build/r175-check.log`.
Deployment receipt is written by `tools/Deploy-HyArpgJar.ps1` in its timestamped
evidence directory. No game or server was started; connected acceptance remains
for the user's Tab / hover / Escape QA pass.
