package com.inigmasgames.hytalerpg.execution.hytale;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.math.shape.Box;
import com.hypixel.hytale.server.core.asset.type.attitude.Attitude;
import com.hypixel.hytale.server.core.modules.collision.BlockCollisionData;
import com.hypixel.hytale.server.core.modules.collision.CollisionModule;
import com.hypixel.hytale.server.core.modules.collision.CollisionResult;
import com.hypixel.hytale.server.core.modules.entity.component.BoundingBox;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import com.hypixel.hytale.server.npc.role.support.WorldSupport;
import com.inigmasgames.hytalerpg.execution.area.AreaGeometry;
import com.inigmasgames.hytalerpg.execution.math.Vec3;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.joml.Vector3d;

/** Pinned 0.7 spatial/attitude adapters. A point-index radius query would miss large intersecting bounds. */
final class HytaleAreaQueries {
    private static final int MAX_SCANNED_NPCS = 4096;
    private static final Box RAY = new Box(-.01, -.01, -.01, .01, .01, .01);
    record Candidate(Ref<EntityStore> ref, AreaGeometry.Bounds bounds) { }
    record Result(List<Candidate> candidates, boolean overflow) { }

    static Result query(Store<EntityStore> store, Ref<EntityStore> owner, AreaGeometry geometry, int budget) {
        Query<EntityStore> query = Query.and(NPCEntity.getComponentType(), TransformComponent.getComponentType(),
                BoundingBox.getComponentType(), com.hypixel.hytale.server.core.entity.UUIDComponent.getComponentType());
        if (store.getEntityCountFor(query) > MAX_SCANNED_NPCS) return new Result(List.of(), true);
        List<Candidate> selected = new ArrayList<>();
        boolean overflow = store.forEachChunk(query, (chunk, buffer) -> {
            for (int index = 0; index < chunk.size(); index++) {
                Ref<EntityStore> target = chunk.getReferenceTo(index);
                if (target.equals(owner) || !target.isValid()) continue;
                var box = chunk.getComponent(index, BoundingBox.getComponentType()).getBoundingBox();
                var position = chunk.getComponent(index, TransformComponent.getComponentType()).getPosition();
                var bounds = new AreaGeometry.Bounds(vec(box.min).add(vec(position)), vec(box.max).add(vec(position)));
                if (!geometry.intersects(bounds) || !hostile(store, target, owner)) continue;
                if (selected.size() == budget) return true;
                selected.add(new Candidate(target, bounds));
            }
            return false;
        });
        return new Result(overflow ? List.of() : List.copyOf(selected), overflow);
    }
    static boolean hostile(Store<EntityStore> store, Ref<EntityStore> target, Ref<EntityStore> owner) {
        if (!target.isValid() || !owner.isValid() || target.equals(owner)) return false;
        WorldSupport support = store.getComponent(target, WorldSupport.getComponentType());
        // NPC is the first argument/attitude owner, verified in WorldSupport.getAttitude bytecode.
        return support != null && support.getAttitude(target, owner, store) == Attitude.HOSTILE;
    }
    static Optional<Vec3> ground(Store<EntityStore> store, Vec3 origin, Vec3 direction, double range) {
        Vec3 displacement = direction.normalized().multiply(range);
        BlockCollisionData hit = first(store, origin, displacement);
        if (hit == null || hit.collisionNormal.y() < .5 || hit.collisionStart < 0 || hit.collisionStart > 1)
            return Optional.empty();
        return Optional.of(origin.add(displacement.multiply(hit.collisionStart)).add(new Vec3(0, .01, 0)));
    }
    static boolean clear(Store<EntityStore> store, Vec3 origin, Vec3 destination) {
        BlockCollisionData hit = first(store, origin, destination.subtract(origin));
        return hit == null || hit.collisionStart >= 1 - 1e-6;
    }
    /** Saves the original aiming ray's world endpoint; does not acquire an enemy or cross its first blocking surface. */
    static Vec3 rayEndpoint(Store<EntityStore> store,Vec3 origin,Vec3 direction,double range) {
        Vec3 delta=direction.normalized().multiply(range);var hit=first(store,origin,delta);
        double fraction=hit==null?1:Math.clamp(hit.collisionStart-.03/Math.max(.03,range),0,1);
        return origin.add(delta.multiply(fraction));
    }
    private static BlockCollisionData first(Store<EntityStore> store, Vec3 origin, Vec3 displacement) {
        CollisionResult result = new CollisionResult(); result.setDefaultPlayerSettings();
        result.disableCharacterCollisions(); result.disableTriggerBlocks(); result.disableDamageBlocks();
        CollisionModule.findCollisions(new Box(RAY), vector(origin), vector(displacement), result, store);
        BlockCollisionData closest = null;
        for (int i = 0; i < result.getBlockCollisionCount(); i++) {
            BlockCollisionData value = result.getBlockCollision(i);
            if (closest == null || value.collisionStart < closest.collisionStart) closest = value;
        }
        return closest;
    }
    private static Vec3 vec(org.joml.Vector3dc v) { return new Vec3(v.x(), v.y(), v.z()); }
    private static Vector3d vector(Vec3 v) { return new Vector3d(v.x(), v.y(), v.z()); }
}
