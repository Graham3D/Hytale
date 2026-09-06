package com.inigmasgames.hytalerpg.execution.hytale;

import com.hypixel.hytale.protocol.Packet;
import com.hypixel.hytale.protocol.packets.interface_.UpdateBossBar;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Authoritative boss classification from Hytale's outbound boss-bar entity reference. */
public final class HytaleBossBarTracker {
    private final Map<UUID, BossRef> visibleBossByPlayer = new ConcurrentHashMap<>();

    public void observe(PlayerRef player, Packet packet) {
        if (player == null || !(packet instanceof UpdateBossBar update)) return;
        observe(player.getUuid(), player.getWorldUuid(), update.entityNetworkId, update.hide);
    }

    public void observe(UUID player, UUID world, int networkId, boolean hidden) {
        if (player == null) return;
        if (hidden || world == null || networkId == 0) visibleBossByPlayer.remove(player);
        else visibleBossByPlayer.put(player, new BossRef(world, networkId));
    }

    public boolean isBoss(UUID world, int networkId) {
        return world != null && networkId != 0
                && visibleBossByPlayer.containsValue(new BossRef(world, networkId));
    }

    public void clear(UUID player) { visibleBossByPlayer.remove(player); }
    private record BossRef(UUID world, int networkId) { }
}
