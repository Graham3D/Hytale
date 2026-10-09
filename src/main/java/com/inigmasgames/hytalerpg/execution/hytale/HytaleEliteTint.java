package com.inigmasgames.hytalerpg.execution.hytale;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.server.core.asset.type.entityeffect.config.EntityEffect;
import com.hypixel.hytale.server.core.entity.effect.EffectControllerComponent;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.inigmasgames.hytalerpg.enemies.EnemyRarity;
import java.util.Objects;

/** Native per-entity color overlay. The original model, attachments and textures are untouched. */
public final class HytaleEliteTint {
    private static final String CHAMPION="RPG_ME_EliteTint_Champion";
    private static final String UNIQUE="RPG_ME_EliteTint_Unique";
    private static final String SUPER_UNIQUE="RPG_ME_EliteTint_SuperUnique";
    private static final String[] ASSETS={CHAMPION,UNIQUE,SUPER_UNIQUE};

    private HytaleEliteTint(){}

    public static String assetId(EnemyRarity rarity){
        return switch(Objects.requireNonNull(rarity)){
            case CHAMPION->CHAMPION;
            case UNIQUE->UNIQUE;
            case SUPER_UNIQUE->SUPER_UNIQUE;
            case NORMAL,BOSS->null;
        };
    }

    /** Call on the owning world thread at birth/rebind. Repeated calls are idempotent. */
    public static void applyEliteTint(Ref<EntityStore> actor,EnemyRarity rarity){
        Objects.requireNonNull(actor);
        String selected=assetId(rarity);
        if(selected==null)return;
        if(!actor.isValid())throw new IllegalStateException("ELITE_TINT_ACTOR_UNAVAILABLE");
        var store=actor.getStore();
        if(!store.isInThread())throw new IllegalStateException("ELITE_TINT_WRONG_WORLD_THREAD");
        var controller=store.getComponent(actor,EffectControllerComponent.getComponentType());
        if(controller==null)throw new IllegalStateException("ELITE_TINT_EFFECT_CONTROLLER_UNAVAILABLE");
        var map=EntityEffect.getAssetMap();
        var effect=map.getAsset(selected);
        if(effect==null)throw new IllegalStateException("ELITE_TINT_ASSET_UNAVAILABLE:"+selected);
        for(String id:ASSETS){
            if(id.equals(selected))continue;
            var previous=map.getAsset(id);
            if(previous!=null&&controller.hasEffect(previous))controller.removeEffect(actor,map.getIndex(id),store);
        }
        // A recovered actor may already own this effect. Never add a duplicate or refresh it per tick.
        if(controller.hasEffect(effect))return;
        if(!controller.addInfiniteEffect(actor,map.getIndex(selected),effect,store))
            throw new IllegalStateException("ELITE_TINT_NATIVE_APPLICATION_REJECTED:"+selected);
    }
}
