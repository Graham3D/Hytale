package com.inigmasgames.hytalerpg.ui.inventory;

import org.junit.jupiter.api.Test;
import org.bson.BsonDocument;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.assetstore.AssetRegistry;
import com.hypixel.hytale.assetstore.map.DefaultAssetMap;
import com.hypixel.hytale.event.EventBus;
import com.hypixel.hytale.server.core.asset.HytaleAssetStore;
import com.hypixel.hytale.server.core.asset.type.item.config.Item;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.AfterAll;
import java.util.Map;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.asset.type.item.config.metadata.ItemDisplayMetadata;
import static org.junit.jupiter.api.Assertions.*;

/** Regression for every covered cell resolving to one authoritative source identity. */
final class NativeWorkspaceGridTest {
    private static HytaleAssetStore<String, Item, DefaultAssetMap<String, Item>> fixture;
    @BeforeAll static void setup() throws Exception {
        com.hypixel.hytale.server.core.Options.parse(new String[]{"--bare"});
        var builder = HytaleAssetStore.builder(Item.class,
                new DefaultAssetMap<String, Item>(Map.of("Weapon_Shortbow_Copper", new Item("Weapon_Shortbow_Copper"),
                        "RPG_Gear_shortbow_copper_n", new Item("RPG_Gear_shortbow_copper_n"))))
                .setPath("Item/Items").setCodec(Item.CODEC).setKeyFunction(Item::getId);
        fixture = new HytaleAssetStore<>(builder) {
            private final EventBus events = new EventBus(false);
            @Override protected EventBus getEventBus() { return events; }
        };
        AssetRegistry.register(fixture);
    }
    @AfterAll static void teardown() { if (fixture != null) AssetRegistry.unregister(fixture); }
    @Test void allocationControlsFollowActualBalanceIncludingZeroAndRefundedPoints() {
        for (int points : new int[]{0, 13}) for (boolean owner : new boolean[]{false, true}) {
            var commands = new UICommandBuilder();
            InventoryProbePage.renderAttributeAvailability(commands, points, owner);
            var values = new java.util.HashMap<String, org.bson.BsonValue>();
            for (var command : commands.getCommands())
                values.put(command.selector, BsonDocument.parse(command.data).get("0"));
            assertEquals(points + " points remaining", values.get("#Points.Text").asString().getValue());
            assertTrue(values.get("#Points.Visible").asBoolean().getValue());
            for (int i = 0; i < 5; i++) {
                assertEquals(owner && points > 0, values.get("#AttributePlus" + i + ".Visible").asBoolean().getValue());
                assertEquals(!owner || points == 0, values.get("#AttributePlus" + i + ".Disabled").asBoolean().getValue());
            }
        }
    }

    @Test void tooltipHostsOverlayOneCenteredWorkspaceInsteadOfTakingTwoLayoutCells() throws Exception {
        var source = java.nio.file.Files.readString(java.nio.file.Path.of(
                "src/main/resources/Common/UI/Custom/RpgInventoryProbe.ui"));
        var overlay = groupBody(source, "#TooltipWorkspace");
        assertTrue(overlay.contains("Anchor: (Full: 16, MaxWidth: 1164)"));
        assertFalse(overlay.contains("LayoutMode:"), "The hosts must overlay, not flow beside one another");
        for (String host : new String[]{"#ManagedTooltipHost", "#ComparisonTooltipHost"}) {
            assertTrue(groupBody(overlay, host).contains("Anchor: (Full: 0)"));
            assertEquals(1, source.split(host, -1).length - 1);
        }
        assertFalse(source.contains("#Compare {"));
        var levelRow = groupBody(source, "#LevelRow");
        assertTrue(levelRow.indexOf("#Level ") < levelRow.indexOf("#Points "));
    }

    private static String groupBody(String source, String id) {
        int start = source.indexOf("{", source.indexOf(id)), depth = 1, end = start + 1;
        assertTrue(start >= 0, id);
        for (; end < source.length() && depth > 0; end++) {
            if (source.charAt(end) == '{') depth++;
            if (source.charAt(end) == '}') depth--;
        }
        assertEquals(0, depth, id);
        return source.substring(start + 1, end - 1);
    }

    @Test void outsideDropTargetHasTransparentPaintMaskButRemainsAnActiveNativeTarget() throws Exception {
        var root = java.nio.file.Path.of("src/main/resources/Common/UI/Custom/InventoryDropTargets");
        var source = java.nio.file.Files.readString(root.resolve("OutsideSection1.ui"));
        assertTrue(source.contains("MaskTexturePath: \"TransparentSlot.png\";"));
        assertFalse(source.contains("HitTestVisible: false"));
        assertFalse(source.contains("Visible: false"));
        var image = javax.imageio.ImageIO.read(root.resolve("TransparentSlot.png").toFile());
        assertNotNull(image);
        for (int y = 0; y < image.getHeight(); y++) for (int x = 0; x < image.getWidth(); x++)
            assertEquals(0, image.getRGB(x, y) >>> 24, "Mask must suppress every hover pixel");
        var commands = new UICommandBuilder();
        NativeOutsideDropTarget.append(commands, 1);
        var set = java.util.Arrays.stream(commands.getCommands())
                .filter(c -> (NativeOutsideDropTarget.SELECTOR + ".Slots").equals(c.selector)).findFirst().orElseThrow();
        var slot = BsonDocument.parse(set.data).getArray("0").getFirst().asDocument();
        assertTrue(slot.getBoolean("IsActivatable").getValue());
        assertEquals(NativeOutsideDropTarget.SLOT, slot.getInt32("InventorySlotIndex").getValue());
    }

    @Test void comparisonPanelsAreScopedIndependentlyAndCloseTogether() {
        var hovered = ManagedGearTooltip.nativeModel("Hovered blade", "Common", "Common", "Weapon",
                java.util.List.of("Damage: 10"), 10, 20);
        var equipped = ManagedGearTooltip.nativeModel("Equipped blade", "Rare", "Rare", "Weapon",
                java.util.List.of("Damage: 8"), 7, 20);
        var commands = new com.hypixel.hytale.server.core.ui.builder.UICommandBuilder();
        var rect = new ManagedGearTooltip.Rect(1100, 600, 64, 128);
        ManagedGearTooltip.compare(commands, hovered, equipped, rect);
        var all = java.util.Arrays.asList(commands.getCommands());
        assertTrue(all.stream().allMatch(c -> c.selector.startsWith("#ManagedTooltipHost ")
                || c.selector.startsWith("#ComparisonTooltipHost ")));
        assertTrue(all.stream().anyMatch(c -> c.selector.startsWith("#ManagedTooltipHost ")
                && c.data != null && c.data.contains("Hovered blade")));
        assertTrue(all.stream().anyMatch(c -> c.selector.startsWith("#ComparisonTooltipHost ")
                && c.data != null && c.data.contains("Equipped blade")));
        var placement = ManagedGearTooltip.pairPosition(rect, Math.max(hovered.height(), equipped.height()));
        assertTrue(placement.x() >= 8);
        assertTrue(placement.x() + ManagedGearTooltip.WIDTH * 2 + 12 <= 1164 - 8);
        var close = new com.hypixel.hytale.server.core.ui.builder.UICommandBuilder();
        ManagedGearTooltip.hide(close);
        assertEquals(2, close.getCommands().length);
        for (var command : close.getCommands()) {
            assertTrue(command.selector.endsWith("#ManagedGearTooltip.Visible"));
            assertFalse(org.bson.BsonDocument.parse(command.data).getBoolean("0").getValue());
        }
    }

    @Test void ownedStacksMustNotUseCreativeMaximumStackDragging() throws Exception {
        var source = java.nio.file.Path.of("src/main/resources/Common/UI/Custom");
        var config = java.nio.file.Files.readString(source.resolve("RpgInventory/GridCommon.ui"));
        assertTrue(config.contains("AllowMaxStackDraggableItems: false"));
        assertFalse(config.contains("AllowMaxStackDraggableItems: true"));
        var layout = new SpatialLayout(InventoryGridGeometry.COLUMNS, InventoryGridGeometry.ROWS);
        assertTrue(layout.add("meat", new SpatialLayout.Size(1, 1), new SpatialLayout.Position(6, 0)));
        var commands = new UICommandBuilder();
        NativeWorkspaceGrid.write(commands, "#WorkspaceNativeGrid", layout,
                ignored -> new ItemStack("Weapon_Shortbow_Copper", 23));
        var cell = BsonDocument.parse(commands.getCommands()[0].data).getArray("0").get(6).asDocument();
        assertEquals(23, cell.getDocument("ItemStack").getInt32("Quantity").getValue());
        assertEquals(6, cell.getInt32("InventorySlotIndex").getValue());
    }
    @Test void coveredCellsShareOneNativeSourceAndKeepGrabOffset() {
        var layout = new SpatialLayout(InventoryGridGeometry.COLUMNS, InventoryGridGeometry.ROWS);
        assertTrue(layout.add("bow", new SpatialLayout.Size(2, 4), new SpatialLayout.Position(3, 0)));
        for (int y = 0; y < 4; y++) for (int x = 3; x < 5; x++)
            assertEquals(3, NativeWorkspaceGrid.sourceCell(layout, y * InventoryGridGeometry.COLUMNS + x));
        assertEquals(-1, NativeWorkspaceGrid.sourceCell(layout, 2));
        var grab = layout.grab(4, 2).orElseThrow();
        assertTrue(layout.moveOrSwapSnapped(grab, 10, 2).accepted());
        for (int y = 0; y < 4; y++) for (int x = 9; x < 11; x++)
            assertEquals(9, NativeWorkspaceGrid.sourceCell(layout, y * InventoryGridGeometry.COLUMNS + x));
    }

    @Test void encodedGridAliasesEightVisualCellsToOneEmptyVirtualSlot() {
        var layout = new SpatialLayout(InventoryGridGeometry.COLUMNS, InventoryGridGeometry.ROWS);
        assertTrue(layout.add("bow", new SpatialLayout.Size(2, 4), new SpatialLayout.Position(3, 0)));
        var commands = new UICommandBuilder();
        NativeWorkspaceGrid.write(commands, "#Grid", layout, ignored -> new com.hypixel.hytale.server.core.inventory.ItemStack("Weapon_Shortbow_Copper", 7));
        var packets = commands.getCommands();
        assertEquals(1, packets.length);
        var slots = BsonDocument.parse(packets[0].data).getArray("0");
        assertEquals(InventoryGridGeometry.CELLS, slots.size());
        for (int y = 0; y < 4; y++) for (int x = 3; x < 5; x++) {
            var slot = slots.get(y * InventoryGridGeometry.COLUMNS + x).asDocument();
            assertEquals(3, slot.getInt32("InventorySlotIndex").getValue());
            var stack = slot.getDocument("ItemStack");
            assertFalse(stack.containsKey("Metadata"));
            assertEquals(7, stack.getInt32("Quantity").getValue());
        }
    }

    @Test void everyCoveredCellCarriesTheRealItemDetailsForTheBaseGameTooltip() {
        var layout = new SpatialLayout(InventoryGridGeometry.COLUMNS, InventoryGridGeometry.ROWS);
        assertTrue(layout.add("bow", new SpatialLayout.Size(2, 4), new SpatialLayout.Position(3, 0)));
        var item = new ItemStack("RPG_Gear_shortbow_copper_n", 1, 23, 90, 17,
                BsonDocument.parse("{\"RpgGearV1\":\"frozen-instance\"}"))
                .withMetadata(ItemDisplayMetadata.KEYED_CODEC, new ItemDisplayMetadata(
                        Message.raw("Copper Bow of Might").color("#1d4dff").bold(true),
                        Message.empty().insert(Message.raw("Damage: 17–24"))
                                .insert(Message.raw("\n+8 Strength").color("#1d4dff"))));
        var before = item.getMetadata().clone();
        var commands = new UICommandBuilder();
        NativeWorkspaceGrid.write(commands, "#Grid", layout, ignored -> item);
        var slots = BsonDocument.parse(commands.getCommands()[0].data).getArray("0");
        for (int y = 0; y < 4; y++) for (int x = 3; x < 5; x++) {
            var slot = slots.get(y * InventoryGridGeometry.COLUMNS + x).asDocument();
            var stack = slot.getDocument("ItemStack");
            assertEquals(3, slot.getInt32("InventorySlotIndex").getValue());
            assertEquals(item.getItemId(), stack.getString("Id").getValue());
            assertEquals(23, stack.getDouble("Durability").getValue());
            assertEquals(90, stack.getDouble("MaxDurability").getValue());
            assertEquals(17, stack.getInt32("Quality").getValue());
            assertClientStackContract(stack);
            assertFalse(stack.containsKey("Metadata"),
                    "The server metadata document is not a ClientItemMetadata UI value");
            assertEquals("Copper Bow of Might", slot.getString("Name").getValue());
            assertTrue(slot.getString("Description").getValue().contains("Damage: 17–24"));
            assertTrue(slot.getString("Description").getValue().contains("+8 Strength"));
        }
        assertEquals(before, item.getMetadata(), "Rendering must not rewrite the authoritative payload");
        assertFalse(slots.get(2).asDocument().containsKey("ItemStack"));
    }

    @Test void equipmentUsesTheSameNativeTooltipPayloadAndClearsEmptySlots() {
        var item = new ItemStack("Weapon_Shortbow_Copper", 1, 23, 90, 17,
                BsonDocument.parse("{\"ItemDisplay\":{\"Name\":{\"RawText\":\"Named bow\"}}}"));
        var commands = new UICommandBuilder();
        NativeItemGrid.writeItem(commands, "#EquipWeapon", item);
        NativeItemGrid.writeItem(commands, "#RingItemLeft", null);
        var packets = commands.getCommands();
        var slot = BsonDocument.parse(packets[0].data).getArray("0").get(0).asDocument();
        assertFalse(slot.getDocument("ItemStack").containsKey("Metadata"));
        assertEquals("Named bow", slot.getString("Name").getValue());
        assertFalse(BsonDocument.parse(packets[1].data).getArray("0").get(0).asDocument().containsKey("ItemStack"));
    }

    @Test void emptyMetadataIsOmitted() {
        var commands = new UICommandBuilder();
        NativeItemGrid.writeItem(commands, "#EquipWeapon", new ItemStack("Weapon_Shortbow_Copper", 1, new BsonDocument()));
        var stack = BsonDocument.parse(commands.getCommands()[0].data).getArray("0").get(0)
                .asDocument().getDocument("ItemStack");
        assertFalse(stack.containsKey("Metadata"));
    }

    @Test void equippedTooltipKeepsDetailsWithoutEnablingNativeEquipmentPickup() {
        var item = new ItemStack("Weapon_Shortbow_Copper", 1, 23, 90, 17,
                BsonDocument.parse("{\"ItemDisplay\":{\"Name\":{\"RawText\":\"Named bow\"}}}"));
        for (String equipment : new String[]{"Weapon", "Offhand", "Head", "Chest", "Hands", "Legs",
                "RingLeft", "RingRight"}) {
            var commands = new UICommandBuilder();
            NativeGearTargetGrid.write(commands, equipment, item);
            var packets = commands.getCommands();
            assertFalse(BsonDocument.parse(packets[0].data).getBoolean("0").getValue(),
                    "Equipment keeps its protected click/place transfer");
            var slots = BsonDocument.parse(packets[1].data).getArray("0");
            assertEquals(NativeGearTargetGrid.count(equipment), slots.size());
            for (int i = 0; i < slots.size(); i++) {
                var slot = slots.get(i).asDocument();
                assertEquals(NativeGearTargetGrid.first(equipment) + i,
                        slot.getInt32("InventorySlotIndex").getValue());
                assertFalse(slot.getDocument("ItemStack").containsKey("Metadata"));
                assertEquals("Named bow", slot.getString("Name").getValue());
                assertEquals(17, slot.getDocument("ItemStack").getInt32("Quality").getValue());
                assertClientStackContract(slot.getDocument("ItemStack"));
                assertTrue(slot.getDocument("Icon").toJson().contains("TransparentSlot.png"),
                        "Native tooltip slots must not duplicate the existing equipment artwork");
            }
        }
    }

    @Test void serverOnlyQualityAndPrivateMetadataStayOutOfTheClientSlot() {
        var item = new ItemStack("Weapon_Shortbow_Copper", 7, 23, 90, 17,
                BsonDocument.parse("{\"RpgGearV1\":\"frozen-instance\"}"));
        var before = com.hypixel.hytale.server.core.inventory.ItemStack.CODEC
                .encode(item, new com.hypixel.hytale.codec.ExtraInfo()).asDocument();
        assertEquals(17, before.getInt32("QualityOverride").getValue(),
                "Reproduce the server serialization which the client rejected");
        var commands = new UICommandBuilder();
        NativeItemGrid.writeItem(commands, "#Grid", item);
        var shown = BsonDocument.parse(commands.getCommands()[0].data)
                .getArray("0").get(0).asDocument().getDocument("ItemStack");
        assertClientStackContract(shown);
        assertEquals(17, shown.getInt32("Quality").getValue());
        var expected = before.clone();
        expected.put("Quality", expected.remove("QualityOverride"));
        expected.remove("Metadata");
        assertEquals(expected, shown, "Only client display fields belong in the slot");
        assertEquals(before, ItemStack.CODEC.encode(item,
                new com.hypixel.hytale.codec.ExtraInfo()).asDocument(),
                "UI projection must not change the authoritative stack or save encoding");
    }

    @Test void inheritedQualityIsResolvedForNativeTooltipsAndEmptySlotsStayEmpty() {
        var item = new ItemStack("Weapon_Shortbow_Copper", 4);
        var persisted = ItemStack.CODEC.encode(item,
                new com.hypixel.hytale.codec.ExtraInfo()).asDocument();
        assertFalse(persisted.containsKey("QualityOverride"));
        var commands = new UICommandBuilder();
        NativeItemGrid.writeSlots(commands, "#Grid", new com.hypixel.hytale.server.core.ui.ItemGridSlot[]{
                new com.hypixel.hytale.server.core.ui.ItemGridSlot(item),
                new com.hypixel.hytale.server.core.ui.ItemGridSlot()});
        var slots = BsonDocument.parse(commands.getCommands()[0].data).getArray("0");
        var shown = slots.get(0).asDocument().getDocument("ItemStack");
        assertClientStackContract(shown);
        assertEquals(item.getQualityIndex(), shown.getInt32("Quality").getValue());
        assertEquals(4, shown.getInt32("Quantity").getValue());
        assertFalse(slots.get(1).asDocument().containsKey("ItemStack"));
    }

    private static void assertClientStackContract(BsonDocument stack) {
        // U7P5 ClientItemStack reflection metadata and the exact client diagnostic.
        // This intentionally tests the client projection, not a server-codec round trip.
        var fields = java.util.Set.of("Id", "Quantity", "Durability", "MaxDurability",
                "Quality", "OverrideDroppedItemAnimation");
        assertTrue(fields.containsAll(stack.keySet()), () -> "Unsupported client fields: " + stack.keySet());
        assertFalse(stack.containsKey("QualityOverride"));
        assertFalse(stack.containsKey("Metadata"));
        assertTrue(stack.get("Quality").isInt32());
    }
}
