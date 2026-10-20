package com.inigmasgames.hytalerpg.ui.inventory;

import com.hypixel.hytale.protocol.Packet;
import com.hypixel.hytale.protocol.packets.window.ClientOpenWindow;
import com.hypixel.hytale.protocol.packets.window.WindowType;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Small, synchronized gate between a network packet and a world-thread page open. */
final class InventoryEntryGate {
    enum State { CLOSED, OPEN_PENDING, OPEN }
    record Request(boolean consumed, long generation, boolean schedule) { }
    private record Entry(State state, long generation) { }
    private final Map<UUID, Entry> entries = new HashMap<>();
    private long nextGeneration;
    private boolean enabled = true;

    static boolean isPocketCrafting(Packet packet) {
        return packet instanceof ClientOpenWindow window && window.type == WindowType.PocketCrafting;
    }

    synchronized Request request(UUID player, Packet packet) {
        if (!enabled || !isPocketCrafting(packet)) return new Request(false, 0, false);
        Entry current = entries.get(player);
        if (current != null) return new Request(true, current.generation(), false);
        long generation = ++nextGeneration;
        entries.put(player, new Entry(State.OPEN_PENDING, generation));
        return new Request(true, generation, true);
    }

    synchronized boolean pending(UUID player, long generation) {
        Entry current = entries.get(player);
        return current != null && current.generation() == generation && current.state() == State.OPEN_PENDING;
    }

    synchronized boolean opened(UUID player, long generation) {
        if (!pending(player, generation)) return false;
        entries.put(player, new Entry(State.OPEN, generation));
        return true;
    }

    synchronized boolean dismiss(UUID player, long generation) {
        Entry current = entries.get(player);
        if (current == null || current.generation() != generation) return false;
        entries.remove(player);
        return true;
    }

    synchronized void detach(UUID player) { entries.remove(player); }
    synchronized void clear() { entries.clear(); }
    synchronized void setEnabled(boolean enabled) { this.enabled = enabled; if (!enabled) entries.clear(); }
    synchronized State state(UUID player) { return entries.getOrDefault(player, new Entry(State.CLOSED, 0)).state(); }
}
