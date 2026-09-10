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
import java.util.function.Predicate;
import java.util.function.BiConsumer;

/** Observes Hytale's native ability interaction chains; it does not own physical keys or HUD controls. */
public final class HytaleAbilitySkillInputAdapter {
    private final ConcurrentLinkedQueue<Request> requests = new ConcurrentLinkedQueue<>();
    private final ConcurrentHashMap<Key, Long> seen = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, Vec3> desiredMovement = new ConcurrentHashMap<>();
    private final Consumer<Observation> observations;
    private Predicate<UUID> executionSuppressed = ignored -> false;
    private BiConsumer<UUID, Packet> rawObserver = (player, packet) -> { };
    private boolean nativeExecutionOnly;
    private final ConcurrentHashMap<UUID, java.util.Map<Object, Boolean>> executedChains = new ConcurrentHashMap<>();

    /** Production uses the native server interaction callback; packet observation is diagnostic only. */
    public void useNativeExecution() { nativeExecutionOnly = true; }

    public void acceptNativeExecution(UUID player, InteractionType action, int chainId, Object chainIdentity,
                                      String itemId, int nativeSlot) {
        SkillSlot slot = slot(action);
        if (slot == null || chainIdentity == null || itemId == null
                || !itemId.startsWith(NativeAbilityProjectionService.OWNED_ITEM_PREFIX)
                || nativeSlot != (action == InteractionType.Ability2 ? 0 : 3)) return;
        // Native chains own their lifetime. Weak keys retain deduplication while the actual chain exists,
        // without a time-window expiry permitting a long-lived chain to activate twice.
        var ledger = executedChains.computeIfAbsent(player,
                ignored -> java.util.Collections.synchronizedMap(new java.util.WeakHashMap<>()));
        synchronized (ledger) {
            if (ledger.containsKey(chainIdentity) || ledger.size() >= 256) return;
            ledger.put(chainIdentity, Boolean.TRUE);
        }
        String correlation = UUID.randomUUID().toString();
        boolean suppressed = executionSuppressed.test(player);
        boolean full = requests.stream().filter(request -> request.player().equals(player)).limit(64).count() >= 64;
        String result = suppressed ? "NATIVE_CONTROL_RPG_SUPPRESSED"
                : full ? "NATIVE_INPUT_QUEUE_FULL" : "NATIVE_EXECUTION_MAPPED";
        observations.accept(new Observation(player, slot, action.name(), chainId, correlation, result));
        if (!suppressed && !full) requests.add(new Request(player, slot, action.name(), chainId, correlation,
                desiredMovement.getOrDefault(player, new Vec3(0, 0, 0)),
                "RPG_Ability_Snipe".equals(itemId) ? "snipe" : ""));
    }

    /** Installed once during setup; the control observes the same inbound watcher before filtering. */
    public void configureControl(Predicate<UUID> suppressed, BiConsumer<UUID, Packet> observer) {
        executionSuppressed = suppressed;
        rawObserver = observer;
    }

    public HytaleAbilitySkillInputAdapter() { this(ignored -> { }); }
    public HytaleAbilitySkillInputAdapter(Consumer<Observation> observations) { this.observations = observations; }

    public void observe(PlayerRef player, Packet packet) {
        observe(player.getUuid(), packet);
    }

    public void observe(UUID player, Packet packet) {
        rawObserver.accept(player, packet);
        if (packet instanceof ClientMovement movement && movement.wishMovement != null) {
            desiredMovement.put(player, new Vec3(movement.wishMovement.x, 0.0, movement.wishMovement.z));
            return;
        }
        if (!(packet instanceof SyncInteractionChains chains) || chains.updates == null) return;
        if (nativeExecutionOnly) return;
        for (SyncInteractionChain chain : chains.updates) observe(player, chain);
    }

    private void observe(UUID player, SyncInteractionChain chain) {
        if (chain == null) return;
        SkillSlot slot = slot(chain.interactionType);
        if (isAbilityAction(chain.interactionType)) {
            Key key = new Key(player, chain.chainId, chain.interactionType);
            long now = System.nanoTime();
            seen.entrySet().removeIf(entry -> now - entry.getValue() > 10_000_000_000L);
            if (chain.initial && seen.putIfAbsent(key, now) == null) {
                String correlation = UUID.randomUUID().toString();
                String result = switch (chain.interactionType) {
                    case Ability1 -> "NATIVE_SIGNATURE_PRESERVED";
                    case Ability2, Ability3 -> "MAPPED";
                    case Ability4 -> NativeAbilityProjectionService.ABILITY4_UNAVAILABLE;
                    default -> "IGNORED";
                };
                if (slot != null && executionSuppressed.test(player)) result = "NATIVE_CONTROL_RPG_SUPPRESSED";
                observations.accept(new Observation(player, slot, chain.interactionType.name(), chain.chainId,
                        correlation, result));
                if (slot != null && !executionSuppressed.test(player)) requests.add(new Request(player, slot, chain.interactionType.name(),
                        chain.chainId, correlation, desiredMovement.getOrDefault(player, new Vec3(0, 0, 0))));
            }
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
            if (!executionSuppressed.test(request.player())) consumer.accept(request);
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
            if (request.player().equals(player)) {
                if (!executionSuppressed.test(player)) { consumer.accept(request); accepted++; }
            }
            else requests.add(request);
        }
        return accepted;
    }

    public void clear(UUID player) {
        requests.removeIf(request -> request.player().equals(player));
        seen.keySet().removeIf(key -> key.player().equals(player));
        desiredMovement.remove(player);
        executedChains.remove(player);
    }

    public static SkillSlot slot(InteractionType type) {
        if (type == null) return null;
        return switch (type) {
            case Ability2 -> SkillSlot.SKILL01;
            case Ability3 -> SkillSlot.SKILL02;
            default -> null;
        };
    }

    private static boolean isAbilityAction(InteractionType type) {
        return type == InteractionType.Ability1 || type == InteractionType.Ability2
                || type == InteractionType.Ability3 || type == InteractionType.Ability4;
    }

    public record Request(UUID player, SkillSlot slot, String action, int chainId, String correlationId,
                          Vec3 desiredMovement, String expectedSkill) {
        public Request(UUID player, SkillSlot slot, String action, int chainId, String correlationId, Vec3 movement) {
            this(player, slot, action, chainId, correlationId, movement, "");
        }
        public Request(UUID player, SkillSlot slot, String action, int chainId, String correlationId) {
            this(player, slot, action, chainId, correlationId, new Vec3(0, 0, 0));
        }
    }
    public record Observation(UUID player, SkillSlot slot, String action, int chainId,
                              String correlationId, String result) { }
    private record Key(UUID player, int chainId, InteractionType action) { }
}
