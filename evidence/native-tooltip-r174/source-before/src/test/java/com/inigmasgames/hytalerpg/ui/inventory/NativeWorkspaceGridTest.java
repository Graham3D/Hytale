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
import static org.junit.jupiter.api.Assertions.*;

/** Regression for every covered cell resolving to one authoritative source identity. */
final class NativeWorkspaceGridTest {
    private static HytaleAssetStore<String, Item, DefaultAssetMap<String, Item>> fixture;
    @BeforeAll static void setup() throws Exception {
        com.hypixel.hytale.server.core.Options.parse(new String[]{"--bare"});
        var builder = HytaleAssetStore.builder(Item.class,
                new DefaultAssetMap<String, Item>(Map.of("Weapon_Shortbow_Copper", new Item("Weapon_Shortbow_Copper"))))
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
            assertFalse(slot.containsKey("Metadata"));
            assertTrue(slot.toJson().contains("7"), "The cursor alias must carry the actual stack quantity");
        }
    }
}
