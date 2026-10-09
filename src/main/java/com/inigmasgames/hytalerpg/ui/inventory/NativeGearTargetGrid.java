package com.inigmasgames.hytalerpg.ui.inventory;

import com.hypixel.hytale.server.core.ui.ItemGridSlot;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.ui.PatchStyle;
import com.hypixel.hytale.server.core.ui.Value;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import org.bson.BsonArray;
import org.bson.BsonBoolean;
import org.bson.BsonDocument;
import org.bson.BsonInt32;

import java.util.Map;

/** Native equipment tooltip projections; backing cursor targets remain empty and denied. */
final class NativeGearTargetGrid {
    // Ten equipment cursor cells and one transparent outside-window drop target.
    static final int CAPACITY = InventoryGridGeometry.CELLS + 11;
    private static final Map<String, Integer> FIRST = Map.of(
            "Weapon", 75, "Offhand", 77, "Head", 79, "Chest", 80,
            "Hands", 81, "Legs", 82, "RingLeft", 83, "RingRight", 84);

    private NativeGearTargetGrid() { }

    static int first(String slot) { return FIRST.getOrDefault(slot, -1); }
    static String slotAt(int index) {
        for (var entry : FIRST.entrySet())
            if (contains(entry.getKey(), index)) return entry.getKey();
        return null;
    }
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
        write(commands, slot, null);
    }

    static void write(UICommandBuilder commands, String slot, ItemStack stack) {
        String selector = "#GearTarget" + slot;
        boolean occupied = !ItemStack.isEmpty(stack);
        // Equipment still uses its existing protected click/place transfer.
        commands.set(selector + ".AreItemsDraggable", !occupied);
        ItemGridSlot[] slots = new ItemGridSlot[count(slot)];
        for (int i = 0; i < slots.length; i++) {
            slots[i] = occupied ? new ItemGridSlot(stack) : new ItemGridSlot();
            // Keep the existing single centered equipment artwork. The native
            // grid supplies only hit targets and the full base-game tooltip.
            if (occupied) slots[i].setIcon(Value.of(new PatchStyle().setTexturePath(
                    Value.of("InventoryGearTargets/TransparentSlot.png"))));
            slots[i].setActivatable(true);
        }
        int before = commands.getCommands().length;
        NativeItemGrid.writeSlots(commands, selector, slots);
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
