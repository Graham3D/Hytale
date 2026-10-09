package com.inigmasgames.hytalerpg.ui.inventory;

import com.hypixel.hytale.codec.ExtraInfo;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.builtin.triggervolumes.manager.VolumeEntry;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.inigmasgames.hytalerpg.gear.GearLootService;
import com.inigmasgames.hytalerpg.gear.GearNativeItems;
import java.util.Objects;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.function.Consumer;

/** The managed-world-source to private-bag boundary. Never passes the bag to ItemContainer. */
public final class SpatialInventoryTransferCoordinator {
    private static final java.util.concurrent.ConcurrentHashMap<UUID, PlayerRef> pendingQaGrants =
            new java.util.concurrent.ConcurrentHashMap<>();

    /** Uses the same private-bag owner and durable player save as generated QA gear. */
    public static void grantRingQa(Store<EntityStore> store, Ref<EntityStore> actor,
                                   PlayerRef player, World world, Consumer<String> reply) {
        UUID owner = player.getUuid();
        var component = store.getComponent(actor, SpatialBagComponent.getComponentType());
        var ring = new ItemStack("RPG_Ring_Copper");
        if (component == null || component.mode(owner) == SpatialBagComponent.OwnershipMode.NATIVE) {
            Player.giveItem(ring, actor, store);
            reply.accept("Copper Ring delivered to native inventory.");
            return;
        }
        if (pendingQaGrants.putIfAbsent(owner, player) != null)
            throw new IllegalStateException("A gear grant is still saving; wait or reconnect");
        boolean published = false;
        try {
            var before = component.state(owner);
            var operation = UUID.randomUUID();
            var result = before.offer(operation, before.revision(), ring, FootprintCatalog.loadDefault());
            if (!result.accepted()) throw new IllegalStateException("Ring has no free bag cell: " + result.receipt().outcome());
            var entity = store.getComponent(actor, Player.getComponentType());
            if (entity == null) throw new IllegalStateException("Player save owner unavailable");
            component.publish(owner, before, result.bag());
            published = true;
            entity.saveConfig(world, entity.toHolder(), true).whenComplete((ignored, error) ->
                    world.execute(() -> {
                        pendingQaGrants.remove(owner, player);
                        reply.accept(error == null ? "Copper Ring saved to spatial inventory."
                                : "Ring save uncertain; reconnect before requesting another.");
                    }));
        } catch (RuntimeException failure) {
            if (!published) pendingQaGrants.remove(owner, player);
            throw failure;
        }
    }

    /** Command-generated gear has no world source to reserve. Its frozen instance ID is
     * the durable issuance receipt, so a repeated seed cannot issue a second copy. */
    public static void grantGeneratedQaItem(ItemStack stack, Store<EntityStore> store,
                                             Ref<EntityStore> actor, PlayerRef player,
                                             World world, Consumer<String> reply) {
        UUID owner = player.getUuid();
        var component = store.getComponent(actor, SpatialBagComponent.getComponentType());
        if (component == null || component.mode(owner) == SpatialBagComponent.OwnershipMode.NATIVE)
            throw new IllegalStateException("Spatial inventory is not active");
        var gear = GearNativeItems.read(stack);
        if (gear == null || !gear.qaOnly()) throw new IllegalArgumentException("Expected protected QA gear");
        var previous = pendingQaGrants.putIfAbsent(owner, player);
        if (previous != null && previous != player) {
            pendingQaGrants.replace(owner, previous, player); // A prior session is gone.
            previous = null;
        }
        if (previous != null) throw new IllegalStateException("A gear grant is still saving; wait or reconnect");
        boolean published = false;
        try {
            var before = component.state(owner);
            UUID operation = gear.identity();
            if (before.receipts().stream().anyMatch(receipt -> receipt.operationId().equals(operation)))
                throw new IllegalArgumentException("This seeded gear identity was already issued");
            var planned = before.offer(operation, before.revision(), stack, FootprintCatalog.loadDefault());
            if (!planned.accepted()) throw new IllegalArgumentException(
                    "Spatial bag rejected gear: " + planned.receipt().outcome());
            var entity = store.getComponent(actor, Player.getComponentType());
            if (entity == null) throw new IllegalStateException("Player save owner unavailable");
            component.publish(owner, before, planned.bag());
            published = true;
            entity.saveConfig(world, entity.toHolder(), true).whenComplete((ignored, error) ->
                    world.execute(() -> {
                        if (error == null) {
                            pendingQaGrants.remove(owner, player);
                            reply.accept("Generated gear saved to spatial bag once; instance " + operation + ".");
                        } else {
                            // Publication may already be durable. Never issue a fallback copy.
                            reply.accept("Gear save uncertain; reconnect before issuing more gear. Instance "
                                    + operation + ".");
                        }
                    }));
        } catch (RuntimeException failure) {
            if (!published) pendingQaGrants.remove(owner, player);
            throw failure;
        }
    }

    /** Trigger grants have no durable source receipt yet. Refuse spatial delivery at the producer. */
    public static void routeAuthoredTriggerGrant(Store<EntityStore> store, Ref<EntityStore> actor,
                                                  VolumeEntry volume, String itemId, int quantity) {
        PlayerRef player = store.getComponent(actor, PlayerRef.getComponentType());
        if (player == null) return;
        var type = SpatialBagComponent.getComponentType();
        if (type == null) return;
        var bag = store.getComponent(actor, type);
        if (bag != null && bag.mode(player.getUuid()) != SpatialBagComponent.OwnershipMode.NATIVE) {
            player.sendMessage(Message.raw("Trigger item grant disabled for spatial inventory until its source receipt is qualified."));
            return;
        }
        if (volume == null || !volume.isEnabled() || itemId == null || itemId.isBlank() || quantity <= 0) return;
        Player.giveItem(new ItemStack(itemId, quantity), actor, store);
    }
    private final GearLootService loot;
    private final FootprintCatalog catalog = FootprintCatalog.loadDefault();
    private final Executor io;
    private final Consumer<GearLootService.Loot> sourceChanged;
    private final java.util.concurrent.atomic.AtomicReference<String> copiedFault = new java.util.concurrent.atomic.AtomicReference<>();

    public void armCopiedFault(String boundary) {
        if (!boundary.equals("pickup-after-prepare") && !boundary.equals("pickup-after-save"))
            throw new IllegalArgumentException("Unknown pickup fault");
        if (!copiedFault.compareAndSet(null, boundary)) throw new IllegalStateException("A pickup fault is already armed");
    }

    public SpatialInventoryTransferCoordinator(GearLootService loot, Executor io,
                                               Consumer<GearLootService.Loot> sourceChanged) {
        this.loot = Objects.requireNonNull(loot);
        this.io = Objects.requireNonNull(io);
        this.sourceChanged = Objects.requireNonNull(sourceChanged);
    }

    /** Called on the player's world thread. Completion is asynchronous; the source is never removed first. */
    public void acceptManagedWorldItem(GearLootService.Loot source, ItemStack stack,
                                       Store<EntityStore> store, Ref<EntityStore> actor,
                                       PlayerRef player, World world, Consumer<String> reply, Runnable finished) {
        UUID owner = player.getUuid();
        var component = store.getComponent(actor, SpatialBagComponent.getComponentType());
        if (component == null) { reply.accept("Spatial bag is not attached."); finished.run(); return; }
        var before = component.state(owner);
        UUID operation = UUID.randomUUID();
        var candidate = before.offerStacking(operation, before.revision(), stack, catalog);
        if (!candidate.accepted()) {
            com.inigmasgames.hytalerpg.gear.GearQaTrace.record(owner,"PICKUP_ADMISSION",Map.of(
                    "eventId",source.source().eventId(),"result","REJECTED","reason",candidate.receipt().outcome().name()));
            reply.accept("Spatial pickup rejected: " + candidate.receipt().outcome() + "; source remains protected.");
            finished.run();
            return;
        }
        String event = source.source().eventId();
        String frozenPayload = ItemStack.CODEC.encode(stack, new ExtraInfo()).asDocument().toJson();
        try {
            io.execute(() -> {
                boolean reserved = false;
                try {
                    loot.prepareSpatialPickup(event, operation, owner, source.result().item().identity(),
                            frozenPayload, before.revision());
                    var pending = loot.reserveSpatialPickup(event, owner, source.allocation().revision(),
                            System.currentTimeMillis(), operation);
                    reserved = true;
                    sourceChanged.accept(pending);
                    if (copiedFault.compareAndSet("pickup-after-prepare", null)) {
                        reply.accept("QA fault after protected source prepare. Reconnect for automatic release.");
                        return;
                    }
                    world.execute(() -> {
                        var current = world.getEntityRef(owner);
                        if (current == null || !current.isValid() || player.getReference() != current) {
                            releaseBeforePublication(event, owner, operation, reply, finished);
                            return;
                        }
                        var currentComponent = store.getComponent(current, SpatialBagComponent.getComponentType());
                        if (currentComponent != component || currentComponent.state(owner) != before) {
                            releaseBeforePublication(event, owner, operation, reply, finished);
                            return;
                        }
                        try {
                            component.publish(owner, before, candidate.bag());
                            var entity = store.getComponent(current, Player.getComponentType());
                            if (entity == null) throw new IllegalStateException("Player save owner unavailable");
                            entity.saveConfig(world, entity.toHolder(), true).whenComplete((ignored, error) -> {
                                if (error != null) {
                                    reply.accept("Spatial save uncertain; source stays reserved. Reconnect for recovery.");
                                    return;
                                }
                                if (copiedFault.compareAndSet("pickup-after-save", null)) {
                                    reply.accept("QA fault after private bag save. Reconnect for automatic finalization.");
                                    return;
                                }
                                io.execute(() -> {
                                    GearLootService.Loot acknowledged;
                                    try {
                                        acknowledged = loot.acknowledgeSpatialPickup(event, owner, operation);
                                    } catch (RuntimeException uncertain) {
                                        reply.accept("Spatial pickup saved but receipt remains pending; reconnect for recovery.");
                                        return;
                                    }
                                    // The durable receipt is FINALIZED. Projection or chat failure
                                    // must not retain the player-wide transaction lock.
                                    try {
                                        sourceChanged.accept(acknowledged);
                                        com.inigmasgames.hytalerpg.gear.GearQaTrace.record(owner,"PICKUP_ADMISSION",Map.of(
                                                "eventId",event,"result","COMMITTED","itemIdentity",source.result().item().identity().toString()));
                                        reply.accept("Protected pickup committed to spatial bag once.");
                                    } catch (RuntimeException projection) {
                                        com.hypixel.hytale.logger.HytaleLogger.getLogger().atWarning().log(
                                                "RPG_SPATIAL_PICKUP_FINALIZED_PROJECTION_FAILED player=%s event=%s error=%s",
                                                owner,event,projection);
                                    } finally {
                                        finished.run();
                                    }
                                });
                            });
                        } catch (RuntimeException uncertain) {
                            // Once a candidate has been published, never refund or release by guessing.
                            reply.accept("Spatial pickup is uncertain; source stays reserved. Reconnect for recovery.");
                        }
                    });
                } catch (RuntimeException failure) {
                    if (!reserved) {
                        try { reply.accept("Spatial source reservation failed: " + failure.getMessage()); }
                        finally { finished.run(); }
                    } else reply.accept("Spatial reservation uncertain; source stays reserved. Reconnect for recovery.");
                }
            });
        } catch (RuntimeException queueFailure) {
            reply.accept("Spatial pickup queue unavailable; source unchanged.");
            finished.run();
        }
    }

    private void releaseBeforePublication(String event, UUID owner, UUID operation,
                                          Consumer<String> reply, Runnable finished) {
        try {
            io.execute(() -> {
                try { sourceChanged.accept(loot.releaseUncommittedSpatialPickup(event, owner, operation)); }
                catch (RuntimeException uncertain) {
                    reply.accept("Spatial cancellation uncertain; source stays reserved for recovery.");
                    return;
                }
                try { reply.accept("Spatial pickup cancelled before bag publication; source remains protected."); }
                finally { finished.run(); }
            });
        } catch (RuntimeException queueFailure) {
            reply.accept("Spatial cancellation queue unavailable; source stays reserved for recovery.");
        }
    }
}
