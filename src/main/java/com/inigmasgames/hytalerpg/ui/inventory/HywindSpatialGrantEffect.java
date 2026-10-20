package com.inigmasgames.hytalerpg.ui.inventory;

import com.hypixel.hytale.builtin.triggervolumes.effect.TriggerContext;
import com.hypixel.hytale.builtin.triggervolumes.effect.TriggerEffect;
import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

/** Authored replacement for GiveItem. All execution enters the spatial transfer boundary. */
public final class HywindSpatialGrantEffect extends TriggerEffect {
    public static final String TYPE = "HywindSpatialGrant";
    public static final BuilderCodec<HywindSpatialGrantEffect> CODEC = BuilderCodec.builder(
            HywindSpatialGrantEffect.class, HywindSpatialGrantEffect::new, TriggerEffect.BASE_CODEC)
            .append(new KeyedCodec<>("Item", Codec.STRING),
                    (effect, value) -> effect.itemId = value, effect -> effect.itemId).add()
            .append(new KeyedCodec<>("Quantity", Codec.INTEGER),
                    (effect, value) -> effect.quantity = value, effect -> effect.quantity).add()
            .build();

    private String itemId = "";
    private int quantity = 1;

    @Override public void execute(TriggerContext context) {
        Ref<EntityStore> actor = context.getEntityRef();
        if (actor == null || !actor.isValid()) return;
        SpatialInventoryTransferCoordinator.routeAuthoredTriggerGrant(
                context.getStore(), actor, context.getVolume(), itemId, quantity);
    }
}
