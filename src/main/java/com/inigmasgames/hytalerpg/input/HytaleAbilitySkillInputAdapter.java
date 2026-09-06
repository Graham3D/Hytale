package com.inigmasgames.hytalerpg.input;

import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.protocol.Packet;
import com.hypixel.hytale.protocol.packets.interaction.SyncInteractionChain;
import com.hypixel.hytale.protocol.packets.interaction.SyncInteractionChains;
import com.hypixel.hytale.protocol.packets.player.ClientMovement;
import com.inigmasgames.hytalerpg.execution.math.Vec3;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.inigmasgames.hytalerpg.domain.SkillSlot;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.Consumer;

/** Maps Hytale's configured Ability1..Ability4 actions to logical RPG slots without key assumptions. */
public final class HytaleAbilitySkillInputAdapter {
    private final ConcurrentLinkedQueue<Request> requests = new ConcurrentLinkedQueue<>();
    private final ConcurrentHashMap<Key, Long> seen = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, Vec3> desiredMovement = new ConcurrentHashMap<>();

    public void observe(PlayerRef player, Packet packet) {
        if (packet instanceof ClientMovement movement && movement.wishMovement != null) {
            desiredMovement.put(player.getUuid(), new Vec3(movement.wishMovement.x, 0.0, movement.wishMovement.z));
            return;
        }
        if (!(packet instanceof SyncInteractionChains chains) || chains.updates == null) return;
        for (SyncInteractionChain chain : chains.updates) observe(player.getUuid(), chain);
    }

    private void observe(UUID player, SyncInteractionChain chain) {
        if (chain == null) return;
        SkillSlot slot = slot(chain.interactionType);
        if (slot != null) {
            Key key = new Key(player, chain.chainId, chain.interactionType);
            long now = System.nanoTime();
            seen.entrySet().removeIf(entry -> now - entry.getValue() > 10_000_000_000L);
            if (chain.initial && seen.putIfAbsent(key, now) == null)
                requests.add(new Request(player, slot, chain.interactionType.name(),
                    chain.chainId, UUID.randomUUID().toString(), desiredMovement.getOrDefault(player, new Vec3(0, 0, 0))));
            if (!chain.initial && chain.state != null && switch (chain.state) {
                case Finished, Skip, ItemChanged, Failed -> true;
                default -> false;
            }) seen.remove(key);
        }
        if (chain.newForks != null) for (SyncInteractionChain fork : chain.newForks) observe(player, fork);
    }

    public int drain(Consumer<Request> consumer, int limit) {
        int count = 0;
        while (count < limit) {
            Request request = requests.poll();
            if (request == null) break;
            consumer.accept(request);
            count++;
        }
        return count;
    }

    /** Drains only this player's requests so entity tick order cannot dispatch on the wrong actor ref. */
    public int drainFor(UUID player, Consumer<Request> consumer, int limit) {
        int accepted = 0, scanned = 0, initial = requests.size();
        while (accepted < limit && scanned++ < initial) {
            Request request = requests.poll();
            if (request == null) break;
            if (request.player().equals(player)) { consumer.accept(request); accepted++; }
            else requests.add(request);
        }
        return accepted;
    }

    public void clear(UUID player) {
        requests.removeIf(request -> request.player().equals(player));
        seen.keySet().removeIf(key -> key.player().equals(player));
        desiredMovement.remove(player);
    }

    public static SkillSlot slot(InteractionType type) {
        if (type == null) return null;
        return switch (type) {
            case Ability1 -> SkillSlot.SKILL01;
            case Ability2 -> SkillSlot.SKILL02;
            case Ability3 -> SkillSlot.SKILL03;
            case Ability4 -> SkillSlot.SKILL04;
            default -> null;
        };
    }

    public record Request(UUID player, SkillSlot slot, String action, int chainId, String correlationId,
                          Vec3 desiredMovement) {
        public Request(UUID player, SkillSlot slot, String action, int chainId, String correlationId) {
            this(player, slot, action, chainId, correlationId, new Vec3(0, 0, 0));
        }
    }
    private record Key(UUID player, int chainId, InteractionType action) { }
}
