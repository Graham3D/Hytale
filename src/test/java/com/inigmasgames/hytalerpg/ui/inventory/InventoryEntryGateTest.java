package com.inigmasgames.hytalerpg.ui.inventory;

import com.hypixel.hytale.protocol.packets.window.ClientOpenWindow;
import com.hypixel.hytale.protocol.packets.window.WindowType;
import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class InventoryEntryGateTest {
    private static final ClientOpenWindow POCKET = new ClientOpenWindow(WindowType.PocketCrafting);

    @Test void onlyPocketCraftingSchedulesAndDuplicateRequestsCoalesce() {
        var gate = new InventoryEntryGate();
        var player = UUID.randomUUID();
        for (WindowType type : WindowType.values()) {
            if (type != WindowType.PocketCrafting)
                assertFalse(gate.request(player, new ClientOpenWindow(type)).consumed(), type.name());
        }
        var first = gate.request(player, POCKET);
        assertTrue(first.consumed());
        assertTrue(first.schedule());
        assertEquals(InventoryEntryGate.State.OPEN_PENDING, gate.state(player));
        assertFalse(gate.request(player, POCKET).schedule());
        assertTrue(gate.opened(player, first.generation()));
        assertEquals(InventoryEntryGate.State.OPEN, gate.state(player));
        assertFalse(gate.request(player, POCKET).schedule());
        assertTrue(gate.dismiss(player, first.generation()));
        assertEquals(InventoryEntryGate.State.CLOSED, gate.state(player));
        assertTrue(gate.request(player, POCKET).schedule());
    }

    @Test void staleQueuedGenerationCannotOpenAfterCloseOrTransition() {
        var gate = new InventoryEntryGate();
        var player = UUID.randomUUID();
        long old = gate.request(player, POCKET).generation();
        gate.detach(player);
        long current = gate.request(player, POCKET).generation();
        assertNotEquals(old, current);
        assertFalse(gate.pending(player, old));
        assertFalse(gate.opened(player, old));
        assertFalse(gate.dismiss(player, old));
        assertTrue(gate.opened(player, current));
        gate.detach(player);
        assertEquals(InventoryEntryGate.State.CLOSED, gate.state(player));
    }

    @Test void disabledRedirectLeavesNativePacketUntouched() {
        var gate = new InventoryEntryGate();
        var player = UUID.randomUUID();
        gate.setEnabled(false);
        assertFalse(gate.request(player, POCKET).consumed());
        assertEquals(InventoryEntryGate.State.CLOSED, gate.state(player));
        gate.setEnabled(true);
        assertTrue(gate.request(player, POCKET).schedule());
        gate.setEnabled(false);
        assertEquals(InventoryEntryGate.State.CLOSED, gate.state(player));
    }
}
