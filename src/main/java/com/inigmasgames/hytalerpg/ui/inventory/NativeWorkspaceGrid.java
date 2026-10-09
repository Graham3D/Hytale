package com.inigmasgames.hytalerpg.ui.inventory;

import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.ui.ItemGridSlot;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import org.bson.BsonArray;
import org.bson.BsonBoolean;
import org.bson.BsonDocument;
import org.bson.BsonInt32;

import java.util.function.Function;

/** Native cursor presentation only. The paired container remains empty and DENY_ALL. */
final class NativeWorkspaceGrid {
    private NativeWorkspaceGrid() { }

    static int sourceCell(SpatialLayout.Entry entry) {
        return entry.position().y() * InventoryGridGeometry.COLUMNS + entry.position().x();
    }

    static int sourceCell(SpatialLayout layout, int visualCell) {
        if (visualCell < 0 || visualCell >= InventoryGridGeometry.CELLS) return -1;
        return layout.at(visualCell % InventoryGridGeometry.COLUMNS,
                visualCell / InventoryGridGeometry.COLUMNS).map(NativeWorkspaceGrid::sourceCell).orElse(-1);
    }

    static void write(UICommandBuilder commands, String selector, SpatialLayout layout,
                      Function<String, ItemStack> item) {
        ItemGridSlot[] slots = new ItemGridSlot[InventoryGridGeometry.CELLS];
        var presentations = new java.util.HashMap<String,ItemStack>();
        for (int visual = 0; visual < slots.length; visual++) {
            var entry = layout.at(visual % InventoryGridGeometry.COLUMNS,
                    visual / InventoryGridGeometry.COLUMNS).orElse(null);
            slots[visual] = entry == null ? new ItemGridSlot() : new ItemGridSlot(
                    presentations.computeIfAbsent(entry.id(), item));
            slots[visual].setActivatable(true);
        }
        int before = commands.getCommands().length;
        NativeItemGrid.writeSlots(commands, selector, slots);
        var encodedCommands = commands.getCommands();
        if (encodedCommands.length != before + 1)
            throw new IllegalStateException("Native workspace grid slot encoding changed");
        var command = encodedCommands[encodedCommands.length - 1];
        BsonDocument data = BsonDocument.parse(command.data);
        BsonArray encoded = data.getArray("0");
        if (encoded == null || encoded.size() != InventoryGridGeometry.CELLS)
            throw new IllegalStateException("Native workspace grid cell count changed");
        for (int visual = 0; visual < encoded.size(); visual++) {
            BsonDocument slot = encoded.get(visual).asDocument();
            int source = sourceCell(layout, visual);
            slot.put("InventorySlotIndex", new BsonInt32(source < 0 ? visual : source));
            slot.put("IsActivatable", BsonBoolean.TRUE);
        }
        command.data = data.toJson();
    }
}
