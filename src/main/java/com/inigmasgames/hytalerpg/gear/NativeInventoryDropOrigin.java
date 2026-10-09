package com.inigmasgames.hytalerpg.gear;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.vector.Transform;
import com.hypixel.hytale.server.core.modules.entity.component.HeadRotation;
import com.hypixel.hytale.server.core.modules.entity.component.ModelComponent;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import org.joml.Vector3d;

/** Uses ItemUtils.throwItem's eye-plus-look origin; no grounded teleport validation. */
final class NativeInventoryDropOrigin {
    private NativeInventoryDropOrigin() { }

    static Vector3d receiptPosition(Store<EntityStore> store, Ref<EntityStore> actor) {
        var transform = store.getComponent(actor, TransformComponent.getComponentType());
        if (transform == null) throw new IllegalStateException("Player position unavailable");
        return receiptPosition(transform.getPosition(), ModelComponent.getEyeHeight(actor, store), direction(store, actor));
    }

    static Vector3d velocity(Store<EntityStore> store, Ref<EntityStore> actor) {
        return actor == null || !actor.isValid() ? new Vector3d() : direction(store, actor).mul(6);
    }

    private static Vector3d direction(Store<EntityStore> store, Ref<EntityStore> actor) {
        var head = store.getComponent(actor, HeadRotation.getComponentType());
        var rotation = head == null ? com.hypixel.hytale.math.vector.Rotation3f.ZERO : head.getRotation();
        return Transform.getDirection(rotation.pitch(), rotation.yaw());
    }

    static Vector3d receiptPosition(Vector3d position, double eyeHeight, Vector3d direction) {
        // Existing durable projections add .5 to receipt Y when creating the native item.
        return new Vector3d(position).add(0, eyeHeight - .5, 0).add(direction);
    }
}
