package com.inigmasgames.hytalerpg.ui.inventory;

import com.hypixel.hytale.server.core.ui.ItemGridSlot;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import org.bson.BsonArray;
import org.bson.BsonBoolean;
import org.bson.BsonDocument;
import org.bson.BsonInt32;

import java.util.Map;

/** Empty, denied native cursor targets. Equipment writes remain with HytaleGearLoot. */
final class NativeGearTargetGrid {
    static final int CAPACITY = InventoryGridGeometry.CELLS + 10;
    private static final Map<String, Integer> FIRST = Map.of(
            "Weapon", 75, "Offhand", 77, "Head", 79, "Chest", 80,
            "Hands", 81, "Legs", 82, "RingLeft", 83, "RingRight", 84);

    private NativeGearTargetGrid() { }

    static int first(String slot) { return FIRST.getOrDefault(slot, -1); }
    static int count(String slot) { return slot.equals("Weapon") || slot.equals("Offhand") ? 2 : 1; }
    static boolean contains(String slot, int index) {
        int first = first(slot);
        return first >= 0 && index >= first && index < first + count(slot);
    }

    static void append(UICommandBuilder commands, String slot, int section) {
        String selector = "#GearTarget" + slot;
        commands.clear("#EquipDrop" + slot);
        commands.append("#EquipDrop" + slot,
                "InventoryGearTargets/" + slot + "Section" + section + ".ui");
        ItemGridSlot[] slots = new ItemGridSlot[count(slot)];
        for (int i = 0; i < slots.length; i++) {
            slots[i] = new ItemGridSlot();
            slots[i].setActivatable(true);
        }
        int before = commands.getCommands().length;
        commands.set(selector + ".Slots", slots);
        var encoded = commands.getCommands();
        if (encoded.length != before + 1)
            throw new IllegalStateException("Gear cursor target slot encoding changed");
        var command = encoded[encoded.length - 1];
        BsonDocument data = BsonDocument.parse(command.data);
        BsonArray array = data.getArray("0");
        if (array == null || array.size() != slots.length)
            throw new IllegalStateException("Gear cursor target cell count changed");
        for (int i = 0; i < array.size(); i++) {
            var cell = array.get(i).asDocument();
            cell.put("InventorySlotIndex", new BsonInt32(first(slot) + i));
            cell.put("IsActivatable", BsonBoolean.TRUE);
        }
        command.data = data.toJson();
    }
}
