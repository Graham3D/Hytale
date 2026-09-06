package com.inigmasgames.hytalerpg.phase00;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.packets.player.MouseInteraction;
import com.hypixel.hytale.server.core.event.events.player.PlayerMouseButtonEvent;
import com.hypixel.hytale.server.core.event.events.player.PlayerMouseMotionEvent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAdder;

final class MouseProbeService {
    private static final Map<UUID, MouseProbePage> ACTIVE = new ConcurrentHashMap<>();
    private static final Map<UUID, LongAdder> RAW_PACKETS = new ConcurrentHashMap<>();
    private static Path dataDirectory;

    private MouseProbeService() {
    }

    static void initialize(Path directory) {
        dataDirectory = directory;
    }

    static Path dataDirectory() {
        if (dataDirectory == null) {
            throw new IllegalStateException("MouseProbeService not initialized");
        }
        return dataDirectory;
    }

    static void activate(PlayerRef playerRef, MouseProbePage page) {
        ACTIVE.put(playerRef.getUuid(), page);
    }

    static void deactivate(UUID playerId, MouseProbePage page) {
        ACTIVE.remove(playerId, page);
    }

    static void observeRaw(PlayerRef playerRef, Object packet) {
        if (packet instanceof MouseInteraction) {
            RAW_PACKETS.computeIfAbsent(playerRef.getUuid(), ignored -> new LongAdder()).increment();
        }
    }

    static long rawCount(UUID playerId) {
        LongAdder count = RAW_PACKETS.get(playerId);
        return count == null ? 0 : count.sum();
    }

    static void onButton(PlayerMouseButtonEvent event) {
        PlayerRef playerRef = event.getPlayerRefComponent();
        MouseProbePage page = ACTIVE.get(playerRef.getUuid());
        if (page != null) {
            Ref<EntityStore> ref = event.getPlayerRef();
            page.onMouseButton(ref, ref.getStore(), event);
        }
    }

    static void onMotion(PlayerMouseMotionEvent event) {
        Ref<EntityStore> ref = event.getPlayerRef();
        Store<EntityStore> store = ref.getStore();
        PlayerRef playerRef = store.getComponent(ref, PlayerRef.getComponentType());
        if (playerRef == null) {
            return;
        }
        MouseProbePage page = ACTIVE.get(playerRef.getUuid());
        if (page != null) {
            page.onMouseMotion(ref, store, event);
        }
    }

    static void clear() {
        ACTIVE.clear();
        RAW_PACKETS.clear();
    }
}
