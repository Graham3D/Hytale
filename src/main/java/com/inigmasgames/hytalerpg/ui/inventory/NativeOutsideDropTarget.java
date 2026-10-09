package com.inigmasgames.hytalerpg.ui.inventory;

import com.hypixel.hytale.server.core.ui.ItemGridSlot;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import org.bson.BsonArray;
import org.bson.BsonBoolean;
import org.bson.BsonDocument;
import org.bson.BsonInt32;

/** A single empty native cursor target behind the Inventory workspace. */
final class NativeOutsideDropTarget {
    static final int SLOT = InventoryGridGeometry.CELLS + 10;
    static final String SELECTOR = "#OutsideDropTarget";

    private NativeOutsideDropTarget() { }

    static void append(UICommandBuilder commands, int section) {
        commands.clear("#OutsideDropHost");
        commands.append("#OutsideDropHost", "InventoryDropTargets/OutsideSection" + section + ".ui");
        var slot = new ItemGridSlot();
        slot.setActivatable(true);
        int before = commands.getCommands().length;
        NativeItemGrid.writeSlots(commands, SELECTOR, new ItemGridSlot[]{slot});
        var encoded = commands.getCommands();
        if (encoded.length != before + 1)
            throw new IllegalStateException("Outside cursor target slot encoding changed");
        var command = encoded[encoded.length - 1];
        BsonDocument data = BsonDocument.parse(command.data);
        BsonArray array = data.getArray("0");
        if (array == null || array.size() != 1)
            throw new IllegalStateException("Outside cursor target cell count changed");
        var cell = array.get(0).asDocument();
        cell.put("InventorySlotIndex", new BsonInt32(SLOT));
        cell.put("IsActivatable", BsonBoolean.TRUE);
        command.data = data.toJson();
    }

    static boolean isTarget(int slot) { return slot == SLOT || slot == 0; }
}
