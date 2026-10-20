package com.inigmasgames.hytalerpg.execution.projectile;

import com.inigmasgames.hytalerpg.execution.math.Vec3;

/** Small adapter seam between native collision callbacks and RPG lifecycle decisions. */
@FunctionalInterface
public interface ProjectileCollisionAdapter<T> {
    void onCollision(ProjectileInstance instance, Contact<T> contact);

    record Contact<T>(Kind kind, Vec3 position, T target, String nativeInteraction) { }
    enum Kind { ENTITY, TERRAIN }
}
