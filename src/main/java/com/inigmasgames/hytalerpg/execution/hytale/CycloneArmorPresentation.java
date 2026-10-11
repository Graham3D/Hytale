package com.inigmasgames.hytalerpg.execution.hytale;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.asset.type.entityeffect.config.EntityEffect;
import com.hypixel.hytale.server.core.asset.type.entityeffect.config.OverlapBehavior;
import com.hypixel.hytale.server.core.entity.effect.EffectControllerComponent;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

/** Cosmetic entity-bound lease; the finite RPG shield remains the sole protection authority. */
final class CycloneArmorPresentation {
    static final String EFFECT_ID="RPG_Cyclone_Armor";
    private CycloneArmorPresentation() { }

    static void apply(Store<EntityStore> store,Ref<EntityStore> actor,float seconds){
        var controller=store.getComponent(actor,EffectControllerComponent.getComponentType());
        var effect=EntityEffect.getAssetMap().getAsset(EFFECT_ID);
        if(controller==null||effect==null)throw new IllegalStateException("CYCLONE_VISUAL_ASSET_UNAVAILABLE");
        if(!controller.addEffect(actor,effect,seconds,OverlapBehavior.OVERWRITE,store))
            throw new IllegalStateException("CYCLONE_VISUAL_REJECTED");
    }

    static void remove(Store<EntityStore> store,Ref<EntityStore> actor){
        if(actor==null||!actor.isValid())return;
        var controller=store.getComponent(actor,EffectControllerComponent.getComponentType());
        int index=EntityEffect.getAssetMap().getIndex(EFFECT_ID);
        if(controller!=null&&index>=0&&controller.hasEffect(index))controller.removeEffect(actor,index,store);
    }
}
