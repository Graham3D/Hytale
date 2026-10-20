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
            assertFalse(stack.containsKey("Metadata"));
            assertEquals("Copper Bow of Might", slot.getString("Name").getValue());
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

    @Test void saveOnlyQualityOverrideIsProjectedAsClientQuality() {
        var item = new ItemStack("Weapon_Shortbow_Copper", 1).withQuality(7);
        var commands = new UICommandBuilder();
        NativeItemGrid.writeItem(commands, "#EquipWeapon", item);
        var encoded = BsonDocument.parse(commands.getCommands()[0].data).getArray("0").get(0)
                .asDocument().getDocument("ItemStack");
        assertFalse(encoded.containsKey("QualityOverride"));
        assertEquals(item.toPacket().quality, encoded.getInt32("Quality").getValue());
    }

    @Test void emptyMetadataIsOmittedInsteadOfSendingAClientInvalidObject() {
        var commands = new UICommandBuilder();
        NativeItemGrid.writeItem(commands, "#EquipWeapon", new ItemStack("Weapon_Shortbow_Copper", 1, new BsonDocument()));
        var stack = BsonDocument.parse(commands.getCommands()[0].data).getArray("0").get(0)
                .asDocument().getDocument("ItemStack");
        assertFalse(stack.containsKey("Metadata"));
    }
}
