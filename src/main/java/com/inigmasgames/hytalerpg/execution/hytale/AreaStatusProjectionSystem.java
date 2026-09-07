package com.inigmasgames.hytalerpg.execution.hytale;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.tick.EntityTickingSystem;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.entity.effect.EffectControllerComponent;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.inigmasgames.hytalerpg.combat.status.StatusService;

/** Strongest Slow is reprojected at 5 Hz; weaker still-live effects resume when a stronger one expires. */
public final class AreaStatusProjectionSystem extends EntityTickingSystem<EntityStore> {
    private static final com.hypixel.hytale.logger.HytaleLogger LOGGER = com.hypixel.hytale.logger.HytaleLogger.forEnclosingClass();
    private final StatusService statuses;
    public AreaStatusProjectionSystem(StatusService statuses) { this.statuses = statuses; }
    public static void requireAssets() {
        if (!HytaleAreaStatuses.available()) throw new IllegalStateException("Stage 06 native status assets missing");
        for (String id : java.util.List.of("RPG_Nature", "RPG_Void"))
            if (com.hypixel.hytale.server.core.modules.entity.damage.DamageCause.getAssetMap().getAsset(id) == null)
                throw new IllegalStateException("Stage 06 native damage channel missing: " + id);
    }
    @Override public Query<EntityStore> getQuery() {
        return Query.and(AreaStatusProjection.getComponentType(), UUIDComponent.getComponentType(), EffectControllerComponent.getComponentType());
    }
    @Override public void tick(float deltaSeconds, int index, ArchetypeChunk<EntityStore> chunk,
                               Store<EntityStore> store, CommandBuffer<EntityStore> buffer) {
        var marker = chunk.getComponent(index, AreaStatusProjection.getComponentType());
        marker.retryAfter = Math.max(0, marker.retryAfter - deltaSeconds);
        marker.elapsed += deltaSeconds;
        if (marker.elapsed < .2 || marker.retryAfter > 0) return;
        marker.elapsed %= .2;
        var ref = chunk.getReferenceTo(index); var id = chunk.getComponent(index, UUIDComponent.getComponentType()).getUuid();
        try {
            HytaleAreaStatuses.synchronize(statuses, id, ref, store, null);
        } catch (RuntimeException error) {
            // Never throw from every world tick or turn one native rejection into unbounded logging.
            // Retain the entity-owned marker so expired effects can still be removed on a later retry.
            marker.retryAfter = 2;
            if (!marker.failureLogged) {
                marker.failureLogged = true;
                LOGGER.atWarning().withCause(error).log("RPG_NATIVE_STATUS_PROJECTION_FAILED target=%s retrySeconds=2 connectedProof=false", id);
            }
            return;
        }
        if (statuses.inspect(id).active().isEmpty() && statuses.strongestSlow(id).magnitude() == 0)
            buffer.removeComponent(ref, AreaStatusProjection.getComponentType());
    }
}
