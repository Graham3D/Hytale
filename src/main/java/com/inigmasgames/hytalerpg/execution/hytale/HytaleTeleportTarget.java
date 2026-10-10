package com.inigmasgames.hytalerpg.execution.hytale;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.shape.Box;
import com.hypixel.hytale.server.core.modules.collision.BlockCollisionData;
import com.hypixel.hytale.server.core.modules.collision.CollisionConfig;
import com.hypixel.hytale.server.core.modules.collision.CollisionModule;
import com.hypixel.hytale.server.core.modules.collision.CollisionResult;
import com.hypixel.hytale.server.core.modules.entity.component.BoundingBox;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.inigmasgames.hytalerpg.execution.TeleportScaling;
import com.inigmasgames.hytalerpg.execution.math.Vec3;
import java.util.Optional;
import org.joml.Vector3d;

/** First-hit native terrain selection with full-body landing checks. */
final class HytaleTeleportTarget {
    private HytaleTeleportTarget() { }

    enum Failure {
        PASS, ORIGIN_UNLOADED, NO_TERRAIN_HIT, UNDERSIDE_HIT,
        RAY_PATH_UNLOADED, SIDE_GROUND_UNAVAILABLE, SIDE_TOP_BELOW_CONTACT,
        OUT_OF_RANGE_OR_ELEVATION, DESTINATION_UNLOADED, BOUNDING_BOX_MISSING,
        SUPPORT_UNLOADED, GROUND_SUPPORT_MISSING, BODY_BLOCKED
    }

    record Hit(double fraction, Vec3 normal, boolean willDamage, int fluidId, boolean fluidPresent) { }
    record Selection(Vec3 landing, Failure failure, Vec3 contact, Vec3 normal, double hitFraction, Hit hit) {
        boolean valid() { return failure == Failure.PASS; }
    }
    interface Geometry {
        boolean loaded(Vec3 point);
        Hit firstHit(Vec3 origin, Vec3 displacement);
        boolean bodyClear(Box bounds, Vec3 landing);
    }

    static Selection select(Store<EntityStore> store, Ref<EntityStore> player, Vec3 feet, Vec3 aim, double range) {
        var bounds = store.getComponent(player, BoundingBox.getComponentType());
        return select(new NativeGeometry(store), bounds == null ? null : bounds.getBoundingBox(), feet, aim, range);
    }

    /** Package-visible geometry boundary keeps the native selection rules deterministic in tests. */
    static Selection select(Geometry geometry, Box bounds, Vec3 feet, Vec3 aim, double range) {
        if (!geometry.loaded(feet)) return rejected(Failure.ORIGIN_UNLOADED, null, null, Double.NaN, null);
        Vec3 eye = feet.add(new Vec3(0, 1.35, 0));
        Vec3 ray = aim.normalized().multiply(range + 1.35);
        Hit hit = geometry.firstHit(eye, ray);
        if (hit == null) return rejected(Failure.NO_TERRAIN_HIT, null, null, Double.NaN, null);
        Vec3 contact = eye.add(ray.multiply(hit.fraction()));
        if (hit.normal().y() < -.5) return rejected(Failure.UNDERSIDE_HIT, contact, hit.normal(), hit.fraction(), hit);
        Vec3 segment = contact.subtract(eye);
        for (int i = 0, n = Math.max(1, (int) Math.ceil(segment.length())); i <= n; i++)
            if (!geometry.loaded(eye.add(segment.multiply((double) i / n))))
                return rejected(Failure.RAY_PATH_UNLOADED, contact, hit.normal(), hit.fraction(), hit);
        double x = contact.x(), z = contact.z();
        Vec3 landing;
        if (hit.normal().y() > .5) {
            landing = new Vec3(x, contact.y() + .01, z);
        } else {
            // Probe the solid column that supplied the visible first wall face.
            x = Math.floor(x - hit.normal().x() * .08) + .5;
            z = Math.floor(z - hit.normal().z() * .08) + .5;
            Vec3 top = new Vec3(x, feet.y() + 7, z);
            landing = safeGround(geometry, top, 14).orElse(null);
            if (landing == null) return rejected(Failure.SIDE_GROUND_UNAVAILABLE, contact, hit.normal(), hit.fraction(), hit);
            if (landing.y() + .01 < contact.y())
                return rejected(Failure.SIDE_TOP_BELOW_CONTACT, contact, hit.normal(), hit.fraction(), hit);
        }
        Failure validation = validate(geometry, bounds, feet, landing, range);
        return new Selection(validation == Failure.PASS ? landing : null, validation, contact, hit.normal(), hit.fraction(), hit);
    }

    private static Selection rejected(Failure failure, Vec3 contact, Vec3 normal, double fraction, Hit hit) {
        return new Selection(null, failure, contact, normal, fraction, hit);
    }

    static boolean valid(Store<EntityStore> store, Ref<EntityStore> actor, Vec3 start, Vec3 landing, double range) {
        var bounds = store.getComponent(actor, BoundingBox.getComponentType());
        return validate(new NativeGeometry(store), bounds == null ? null : bounds.getBoundingBox(), start, landing, range) == Failure.PASS;
    }

    static Failure validate(Geometry geometry, Box bounds, Vec3 start, Vec3 landing, double range) {
        if (!TeleportScaling.withinBounds(start, landing, range)) return Failure.OUT_OF_RANGE_OR_ELEVATION;
        if (!geometry.loaded(landing)) return Failure.DESTINATION_UNLOADED;
        if (bounds == null) return Failure.BOUNDING_BOX_MISSING;
        for (double x : new double[]{bounds.min.x(), bounds.max.x()})
            for (double z : new double[]{bounds.min.z(), bounds.max.z()}) {
                Vec3 corner = landing.add(new Vec3(x, 0, z));
                if (!geometry.loaded(corner)) return Failure.SUPPORT_UNLOADED;
                if (safeGround(geometry, corner.add(new Vec3(0, bounds.min.y() + .15, 0)), .35).isEmpty())
                    return Failure.GROUND_SUPPORT_MISSING;
            }
        return geometry.bodyClear(bounds, landing) ? Failure.PASS : Failure.BODY_BLOCKED;
    }

    static boolean clearBody(Store<EntityStore> store, Ref<EntityStore> actor, Vec3 landing) {
        var bounds = store.getComponent(actor, BoundingBox.getComponentType());
        return bounds != null && HytaleAreaQueries.loaded(store, landing)
                && nativeBodyClear(store, bounds.getBoundingBox(), landing);
    }

    static Optional<Vec3> safeGround(Store<EntityStore> store, Vec3 origin, double depth) {
        return safeGround(new NativeGeometry(store), origin, depth);
    }

    private static Optional<Vec3> safeGround(Geometry geometry, Vec3 origin, double depth) {
        if (!geometry.loaded(origin)) return Optional.empty();
        Vec3 down = new Vec3(0, -depth, 0);
        Hit hit = geometry.firstHit(origin, down);
        if (hit == null || hit.normal().y() < .5) return Optional.empty();
        return Optional.of(origin.add(down.multiply(hit.fraction())).add(new Vec3(0, .01, 0)));
    }

    private static boolean nativeBodyClear(Store<EntityStore> store, Box bounds, Vec3 landing) {
        var collision = bodyCollisionSettings();
        CollisionModule.findCollisions(new Box(bounds), new Vector3d(landing.x(), landing.y(), landing.z()),
                new Vector3d(0, .01, 0), collision, store);
        return collision.getBlockCollisionCount() == 0;
    }

    static CollisionResult bodyCollisionSettings() {
        var collision = new CollisionResult();
        collision.setDefaultPlayerSettings();
        collision.disableCharacterCollisions();
        collision.setDamageBlocking(false);
        return collision;
    }

    static CollisionResult targetCollisionSettings() {
        var collision = new CollisionResult();
        collision.setDefaultPlayerSettings();
        collision.disableCharacterCollisions();
        collision.enableDamageBlocks();
        // Include native fluid surfaces, but let damage-only volumes pass through to geometry.
        collision.setCollisionByMaterial(CollisionConfig.MATERIAL_SOLID | CollisionConfig.MATERIAL_FLUID);
        collision.setDamageBlocking(false);
        return collision;
    }

    private static final class NativeGeometry implements Geometry {
        private final Store<EntityStore> store;
        private NativeGeometry(Store<EntityStore> store) { this.store = store; }
        @Override public boolean loaded(Vec3 point) { return HytaleAreaQueries.loaded(store, point); }
        @Override public boolean bodyClear(Box bounds, Vec3 landing) { return nativeBodyClear(store, bounds, landing); }
        @Override public Hit firstHit(Vec3 origin, Vec3 displacement) {
            var collision = targetCollisionSettings();
            CollisionModule.findCollisions(new Box(-.01, -.01, -.01, .01, .01, .01),
                    new Vector3d(origin.x(), origin.y(), origin.z()),
                    new Vector3d(displacement.x(), displacement.y(), displacement.z()), collision, store);
            BlockCollisionData first = null;
            for (int i = 0; i < collision.getBlockCollisionCount(); i++) {
                var next = collision.getBlockCollision(i);
                if (next.collisionStart >= 0 && next.collisionStart <= 1
                        && (first == null || next.collisionStart < first.collisionStart)) first = next;
            }
            if (first == null) return null;
            return new Hit(first.collisionStart,
                    new Vec3(first.collisionNormal.x(), first.collisionNormal.y(), first.collisionNormal.z()),
                    first.willDamage, first.fluidId, first.fluid != null);
        }
    }
}
