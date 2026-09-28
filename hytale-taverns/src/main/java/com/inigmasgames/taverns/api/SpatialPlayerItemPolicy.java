package com.inigmasgames.taverns.api;

import com.hypixel.hytale.component.ComponentAccessor;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.Objects;
import java.util.function.BiPredicate;

/**
 * Neutral Tavern-to-host inventory ownership contract.
 *
 * <p>The merged ARPG host supplies the ownership predicate after registering its
 * spatial inventory component. Tavern code remains independent of ImmersiveNPCs.</p>
 */
public final class SpatialPlayerItemPolicy {
    private static volatile BiPredicate<Ref<EntityStore>, ComponentAccessor<EntityStore>> nativeMode =
            (ref, accessor) -> false;

    private SpatialPlayerItemPolicy() { }

    public static void bind(BiPredicate<Ref<EntityStore>, ComponentAccessor<EntityStore>> predicate) {
        nativeMode = Objects.requireNonNull(predicate);
    }

    public static boolean nativeMode(Ref<EntityStore> player, ComponentAccessor<EntityStore> accessor) {
        if (player == null || !player.isValid() || accessor == null) return false;
        try {
            return nativeMode.test(player, accessor);
        } catch (RuntimeException invalidOwner) {
            return false;
        }
    }
}
