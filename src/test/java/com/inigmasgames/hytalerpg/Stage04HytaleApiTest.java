package com.inigmasgames.hytalerpg;

import com.hypixel.hytale.component.ComponentAccessor;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.math.shape.Box;
import com.hypixel.hytale.protocol.packets.player.ClientMovement;
import com.hypixel.hytale.server.core.asset.type.entityeffect.config.EntityEffect;
import com.hypixel.hytale.server.core.asset.type.entityeffect.config.OverlapBehavior;
import com.hypixel.hytale.server.core.entity.effect.EffectControllerComponent;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.inventory.InventoryComponent;
import com.hypixel.hytale.server.core.modules.collision.CollisionModule;
import com.hypixel.hytale.server.core.modules.collision.CollisionResult;
import com.hypixel.hytale.server.core.modules.entity.damage.Damage;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.lang.reflect.Method;
import java.util.Arrays;
import org.joml.Vector3d;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class Stage04HytaleApiTest {
    @Test void pinnedBuildExposesNativeDamageBlockMetadata() {
        assertNotNull(Damage.BLOCKED);
        assertTrue(Arrays.stream(Damage.class.getMethods()).anyMatch(method ->
                method.getName().equals("getIfPresentMetaObject")));
    }

    @Test void pinnedBuildExposesSweptCollisionAndAuthoritativeMove() throws Exception {
        Method collision = CollisionModule.class.getMethod("findCollisions", Box.class, Vector3d.class,
                Vector3d.class, CollisionResult.class, ComponentAccessor.class);
        assertNotNull(collision);
        assertTrue(Arrays.stream(Player.class.getMethods()).anyMatch(method ->
                method.getName().equals("moveTo") && method.getParameterCount() == 5));
        assertNotNull(CollisionResult.class.getMethod("getBlockCollisionCount"));
    }

    @Test void pinnedBuildExposesMovementIntentAndOffhandUtility() throws Exception {
        assertNotNull(ClientMovement.class.getField("wishMovement"));
        assertNotNull(InventoryComponent.Utility.class.getMethod("getActiveItem"));
    }

    @Test void pinnedBuildExposesDurationOverriddenNativeEntityEffect() throws Exception {
        assertNotNull(EntityEffect.class.getMethod("getAssetMap"));
        assertNotNull(EffectControllerComponent.class.getMethod("addEffect", Ref.class, EntityEffect.class,
                float.class, OverlapBehavior.class, ComponentAccessor.class, Ref.class));
    }
}
