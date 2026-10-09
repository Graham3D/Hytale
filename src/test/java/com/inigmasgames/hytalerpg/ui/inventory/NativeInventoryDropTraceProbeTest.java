package com.inigmasgames.hytalerpg.ui.inventory;

import com.hypixel.hytale.protocol.packets.inventory.DropItemStack;
import com.hypixel.hytale.protocol.packets.inventory.MoveItemStack;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

final class NativeInventoryDropTraceProbeTest {
    @Test void recordsExactNativeDropAndMoveFieldsWithoutMutatingPackets() {
        var drop = new DropItemStack(3, 17, 4);
        var captured = NativeInventoryDropTraceProbe.packetDetails("IN", drop);
        assertEquals("IN", captured.get("direction"));
        assertEquals("DropItemStack", captured.get("packet"));
        assertEquals(174, captured.get("packetId"));
        assertEquals(3, captured.get("inventorySectionId"));
        assertEquals(17, captured.get("slotId"));
        assertEquals(4, captured.get("quantity"));
        assertEquals(3, drop.inventorySectionId);
        assertEquals(17, drop.slotId);
        assertEquals(4, drop.quantity);

        var move = new MoveItemStack(1, 2, 6, 3, 4);
        var moved = NativeInventoryDropTraceProbe.packetDetails("IN", move);
        assertEquals(1, moved.get("fromSectionId"));
        assertEquals(2, moved.get("fromSlotId"));
        assertEquals(6, moved.get("quantity"));
        assertEquals(3, moved.get("toSectionId"));
        assertEquals(4, moved.get("toSlotId"));
    }
}
