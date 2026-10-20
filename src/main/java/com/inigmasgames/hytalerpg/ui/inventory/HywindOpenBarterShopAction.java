package com.inigmasgames.hytalerpg.ui.inventory;

import com.hypixel.hytale.builtin.adventure.npcshop.npc.ActionOpenBarterShop;
import com.hypixel.hytale.builtin.adventure.npcshop.npc.builders.BuilderActionOpenBarterShop;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.asset.builder.BuilderSupport;
import com.hypixel.hytale.server.npc.instructions.Action;
import com.hypixel.hytale.server.npc.instructions.ExecutionSupport;
import com.hypixel.hytale.server.npc.sensorinfo.InfoProvider;

/** The three stock barter NPC roles use this producer gate instead of OpenBarterShop. */
public final class HywindOpenBarterShopAction extends ActionOpenBarterShop {
    public HywindOpenBarterShopAction(BuilderActionOpenBarterShop builder, BuilderSupport support) {
        super(builder, support);
    }

    @Override public boolean execute(Ref<EntityStore> npc, ExecutionSupport support,
                                     InfoProvider info, double delta, Store<EntityStore> store) {
        Ref<EntityStore> target = support.getStateSupport().getInteractionIterationTarget();
        if (target == null) return false;
        PlayerRef player = store.getComponent(target, PlayerRef.getComponentType());
        if (player == null) return false;
        var type = SpatialBagComponent.getComponentType();
        if (type == null) return false; // Fail closed if Hywind's owner is unavailable.
        SpatialBagComponent bag = store.getComponent(target, type);
        boolean nativeMode;
        try { nativeMode = bag == null || bag.mode(player.getUuid()) == SpatialBagComponent.OwnershipMode.NATIVE; }
        catch (RuntimeException invalidOwner) { nativeMode = false; }
        if (!nativeMode) {
            player.sendMessage(Message.raw("Barter purchases are disabled while spatial inventory owns items."));
            return true;
        }
        return super.execute(npc, support, info, delta, store);
    }

    public static final class Builder extends BuilderActionOpenBarterShop {
        @Override public Action build(BuilderSupport support) {
            return new HywindOpenBarterShopAction(this, support);
        }
    }
}
