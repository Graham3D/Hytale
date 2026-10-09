package com.inigmasgames.hytalerpg.ui.inventory;

import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import org.bson.BsonDocument;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeAll;

import static org.junit.jupiter.api.Assertions.*;

final class NativeGearTargetGridTest {
    @BeforeAll static void setup() throws Exception {
        com.hypixel.hytale.server.core.Options.parse(new String[]{"--bare"});
    }

    @Test void targetsUseDistinctDeniedAliasSlotsBeyondTheBag() {
        assertEquals(86, NativeGearTargetGrid.CAPACITY);
        for (String slot : new String[]{"Weapon", "Offhand", "Head", "Chest", "Hands", "Legs",
                "RingLeft", "RingRight"}) {
            var commands = new UICommandBuilder();
            NativeGearTargetGrid.append(commands, slot, 317);
            var encoded = commands.getCommands();
            var cells = BsonDocument.parse(encoded[encoded.length - 1].data).getArray("0");
            assertEquals(NativeGearTargetGrid.count(slot), cells.size());
            for (int i = 0; i < cells.size(); i++) {
                int index = cells.get(i).asDocument().getInt32("InventorySlotIndex").getValue();
                assertEquals(NativeGearTargetGrid.first(slot) + i, index);
                assertTrue(index >= InventoryGridGeometry.CELLS && index < NativeGearTargetGrid.CAPACITY);
                assertTrue(cells.get(i).asDocument().getBoolean("IsActivatable").getValue());
                assertTrue(NativeGearTargetGrid.contains(slot, index));
                assertEquals(slot, NativeGearTargetGrid.slotAt(index));
            }
        }
        assertFalse(NativeGearTargetGrid.contains("Head", 75));
        assertFalse(NativeGearTargetGrid.contains("Unknown", 75));
        assertNull(NativeGearTargetGrid.slotAt(74));
        assertNull(NativeGearTargetGrid.slotAt(85));
    }

    @Test void transparentOutsideTargetIsTheOnlyRemainingAliasSlot() {
        var commands = new UICommandBuilder();
        NativeOutsideDropTarget.append(commands, 317);
        var encoded = commands.getCommands();
        var cells = BsonDocument.parse(encoded[encoded.length - 1].data).getArray("0");
        assertEquals(1, cells.size());
        assertEquals(85, cells.get(0).asDocument().getInt32("InventorySlotIndex").getValue());
        assertEquals(NativeGearTargetGrid.CAPACITY - 1, NativeOutsideDropTarget.SLOT);
        assertTrue(NativeOutsideDropTarget.isTarget(85));
        assertTrue(NativeOutsideDropTarget.isTarget(0)); // target binding may report local index
        assertFalse(NativeOutsideDropTarget.isTarget(84));
    }
}
