package com.inigmasgames.hytalerpg.phase00;

import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.protocol.Packet;
import com.hypixel.hytale.protocol.packets.interaction.SyncInteractionChain;
import com.hypixel.hytale.protocol.packets.interaction.SyncInteractionChains;
import com.hypixel.hytale.server.core.universe.PlayerRef;

import java.util.EnumMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAdder;

final class AbilityInputObserver {
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    private static final Map<UUID, EnumMap<InteractionType, LongAdder>> COUNTS = new ConcurrentHashMap<>();

    private AbilityInputObserver() {
    }

    static void observe(PlayerRef playerRef, Packet packet) {
        if (!(packet instanceof SyncInteractionChains chains) || chains.updates == null) {
            return;
        }
        for (SyncInteractionChain chain : chains.updates) {
            observeChain(playerRef, chain);
        }
    }

    private static void observeChain(PlayerRef playerRef, SyncInteractionChain chain) {
        if (chain == null) {
            return;
        }
        InteractionType type = chain.interactionType;
        if (type == InteractionType.Ability1 || type == InteractionType.Ability2
                || type == InteractionType.Ability3 || type == InteractionType.Ability4) {
            EnumMap<InteractionType, LongAdder> values = COUNTS.computeIfAbsent(
                    playerRef.getUuid(), ignored -> new EnumMap<>(InteractionType.class));
            values.computeIfAbsent(type, ignored -> new LongAdder()).increment();
            LOGGER.atInfo().log("PHASE00_ABILITY_INPUT revision=%s player=%s type=%s state=%s chainId=%d",
                    BuildIdentity.REVISION, playerRef.getUuid(), type, chain.state, chain.chainId);
        }
        if (chain.newForks != null) {
            for (SyncInteractionChain fork : chain.newForks) {
                observeChain(playerRef, fork);
            }
        }
    }

    static String reportAndReset(UUID playerId) {
        EnumMap<InteractionType, LongAdder> values = COUNTS.remove(playerId);
        StringBuilder result = new StringBuilder("Phase 00 observed SyncInteractionChains: ");
        for (InteractionType type : new InteractionType[]{InteractionType.Ability1,
                InteractionType.Ability2, InteractionType.Ability3, InteractionType.Ability4}) {
            LongAdder count = values == null ? null : values.get(type);
            result.append(type).append('=').append(count == null ? 0 : count.sum()).append(' ');
        }
        return result.append("(counters reset after this report)").toString();
    }
}
