package com.inigmasgames.canvasui.runtime.cursor;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.EntityEventSystem;
import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.server.core.event.events.ecs.DropItemEvent;
import com.hypixel.hytale.server.core.event.events.ecs.InventoryActiveSlotRequestEvent;
import com.hypixel.hytale.server.core.event.events.player.PlayerInteractEvent;
import com.hypixel.hytale.server.core.modules.interaction.event.InteractionChainStartEvent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import java.util.EnumSet;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * Session-scoped gameplay-input ownership guard for a passive cursor HUD.
 * Interaction chains are cancelled at their start event, before the first
 * native operation tick.  Cancellable inventory requests are rejected at their
 * request event.  No rollback or after-the-fact world repair is performed.
 */
public final class CanvasInputGuard {
    private static final EnumSet<InteractionType> GUARDED_INTERACTIONS = EnumSet.of(
            InteractionType.Primary, InteractionType.Secondary,
            InteractionType.Ability1, InteractionType.Ability2,
            InteractionType.Ability3, InteractionType.Ability4,
            InteractionType.Use, InteractionType.Pick, InteractionType.Dodge);

    public record Observation(String route, String action, String rootInteractionId,
                              boolean guarded) { }

    private record Lease(String token, Consumer<Observation> observer) { }

    private final Map<UUID, Lease> leases = new ConcurrentHashMap<>();

    public void acquire(UUID playerId, String token, Consumer<Observation> observer) {
        Objects.requireNonNull(playerId, "playerId");
        Lease lease = new Lease(Objects.requireNonNull(token, "token"),
                Objects.requireNonNull(observer, "observer"));
        Lease prior = leases.putIfAbsent(playerId, lease);
        if (prior != null) throw new IllegalStateException("Canvas input guard is already leased for player");
    }

    /** Releases only the matching owner; callers cannot tear down a replacement session. */
    public boolean release(UUID playerId, String token) {
        Lease current = leases.get(playerId);
        return current != null && current.token().equals(token) && leases.remove(playerId, current);
    }

    public boolean active(UUID playerId) { return leases.containsKey(playerId); }
    public int activeLeaseCount() { return leases.size(); }
    public void clear() { leases.clear(); }

    public boolean guardInteraction(UUID playerId, InteractionType type, String rootInteractionId) {
        Lease lease = leases.get(playerId);
        if (lease == null || type == null || !GUARDED_INTERACTIONS.contains(type)) return false;
        observe(lease, new Observation("INTERACTION_CHAIN_START", type.name(), safe(rootInteractionId), true));
        return true;
    }

    public boolean guardClientHotbar(UUID playerId, InventoryActiveSlotRequestEvent event) {
        Lease lease = leases.get(playerId);
        if (lease == null || event == null || !event.isClientRequest()) return false;
        observe(lease, new Observation("INVENTORY_ACTIVE_SLOT_REQUEST",
                event.getPreviousSlot() + "->" + event.getNewSlot(), "", true));
        return true;
    }

    public boolean guardDrop(UUID playerId) {
        Lease lease = leases.get(playerId);
        if (lease == null) return false;
        observe(lease, new Observation("DROP_ITEM_EVENT", "DROP", "", true));
        return true;
    }

    public boolean guardPlayerInteract(UUID playerId, PlayerInteractEvent event) {
        Lease lease = leases.get(playerId);
        if (lease == null || event == null || !GUARDED_INTERACTIONS.contains(event.getActionType())) return false;
        observe(lease, new Observation("PLAYER_INTERACT_EVENT", event.getActionType().name(), "", true));
        return true;
    }

    public static boolean guardedInteraction(InteractionType type) {
        return type != null && GUARDED_INTERACTIONS.contains(type);
    }

    private static void observe(Lease lease, Observation observation) {
        try { lease.observer().accept(observation); }
        catch (RuntimeException ignored) { /* Input cancellation must not depend on telemetry. */ }
    }

    private static String safe(String value) {
        if (value == null) return "";
        return value.length() <= 128 ? value : value.substring(0, 128);
    }

    public static final class InteractionStartSystem
            extends EntityEventSystem<EntityStore, InteractionChainStartEvent> {
        private final CanvasInputGuard guard;
        public InteractionStartSystem(CanvasInputGuard guard) {
            super(InteractionChainStartEvent.class);
            this.guard = Objects.requireNonNull(guard);
        }
        @Override public Query<EntityStore> getQuery() { return PlayerRef.getComponentType(); }
        @Override public void handle(int index, ArchetypeChunk<EntityStore> chunk, Store<EntityStore> store,
                                     CommandBuffer<EntityStore> buffer, InteractionChainStartEvent event) {
            PlayerRef player = chunk.getComponent(index, PlayerRef.getComponentType());
            if (player == null || !guard.guardInteraction(player.getUuid(), event.getType(), event.getRootInteractionId())) return;
            event.getContext().getInteractionManager().cancelChains(event.getChain());
        }
    }

    public static final class ActiveSlotRequestSystem
            extends EntityEventSystem<EntityStore, InventoryActiveSlotRequestEvent> {
        private final CanvasInputGuard guard;
        public ActiveSlotRequestSystem(CanvasInputGuard guard) {
            super(InventoryActiveSlotRequestEvent.class);
            this.guard = Objects.requireNonNull(guard);
        }
        @Override public Query<EntityStore> getQuery() { return PlayerRef.getComponentType(); }
        @Override public void handle(int index, ArchetypeChunk<EntityStore> chunk, Store<EntityStore> store,
                                     CommandBuffer<EntityStore> buffer, InventoryActiveSlotRequestEvent event) {
            PlayerRef player = chunk.getComponent(index, PlayerRef.getComponentType());
            if (player != null && guard.guardClientHotbar(player.getUuid(), event)) event.setCancelled(true);
        }
    }

    public static final class DropItemSystem extends EntityEventSystem<EntityStore, DropItemEvent> {
        private final CanvasInputGuard guard;
        public DropItemSystem(CanvasInputGuard guard) {
            super(DropItemEvent.class);
            this.guard = Objects.requireNonNull(guard);
        }
        @Override public Query<EntityStore> getQuery() { return PlayerRef.getComponentType(); }
        @Override public void handle(int index, ArchetypeChunk<EntityStore> chunk, Store<EntityStore> store,
                                     CommandBuffer<EntityStore> buffer, DropItemEvent event) {
            PlayerRef player = chunk.getComponent(index, PlayerRef.getComponentType());
            if (player != null && guard.guardDrop(player.getUuid())) event.setCancelled(true);
        }
    }
}
