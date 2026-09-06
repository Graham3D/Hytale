package com.inigmasgames.hytalerpg.phase00;

import com.hypixel.hytale.protocol.packets.interface_.HudComponent;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

final class HudProbeState {
    private static final Map<UUID, Set<HudComponent>> ORIGINAL = new HashMap<>();

    private HudProbeState() {}

    static void remember(UUID playerId, Set<HudComponent> visible) {
        ORIGINAL.putIfAbsent(playerId, new HashSet<>(visible));
    }

    static Set<HudComponent> take(UUID playerId) {
        return ORIGINAL.remove(playerId);
    }
}

