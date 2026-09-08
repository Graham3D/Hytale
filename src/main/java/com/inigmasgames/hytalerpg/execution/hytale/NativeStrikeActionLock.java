package com.inigmasgames.hytalerpg.execution.hytale;

import com.hypixel.hytale.component.*;
import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.server.core.asset.type.entityeffect.config.*;
import com.hypixel.hytale.server.core.entity.effect.EffectControllerComponent;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.*;

/** Temporary native interaction restriction, never a HUD, hotkey or Signature Move replacement. */
public final class NativeStrikeActionLock {
    public static final String ID="RPG_Strike_Action_Lock";
    private NativeStrikeActionLock(){}
    public static void requireAsset(){
        var effect=EntityEffect.getAssetMap().getAsset(ID);
        if(effect==null||effect.getApplicationEffects()==null)throw new IllegalStateException("MULTISTRIKE_ACTION_LOCK_ASSET_MISSING");
        var app=effect.getApplicationEffects().toPacket();
        if(app.abilityEffects==null||app.abilityEffects.disabled==null||!Set.copyOf(Arrays.asList(app.abilityEffects.disabled)).equals(
                Set.of(InteractionType.Primary,InteractionType.Secondary,InteractionType.Ability1,InteractionType.Ability2,InteractionType.Ability3,InteractionType.Ability4))
                ||app.movementEffects!=null||app.horizontalSpeedMultiplier!=1||effect.getDamageCalculator()!=null)
            throw new IllegalStateException("MULTISTRIKE_ACTION_LOCK_ASSET_INVALID");
    }
    static boolean available(Store<EntityStore> store,Ref<EntityStore> actor){return EntityEffect.getAssetMap().getAsset(ID)!=null&&store.getComponent(actor,EffectControllerComponent.getComponentType())!=null;}
    static void acquire(Store<EntityStore> store,Ref<EntityStore> actor){
        var controller=store.getComponent(actor,EffectControllerComponent.getComponentType());var effect=EntityEffect.getAssetMap().getAsset(ID);
        // Explicit removal follows the last .50s repeat. Five seconds is a finite crash/unload fallback, not authored attack duration.
        if(controller==null||effect==null||!controller.addEffect(actor,effect,5,OverlapBehavior.OVERWRITE,store,actor))
            throw new IllegalStateException("MULTISTRIKE_NATIVE_ACTION_LOCK_REJECTED");
    }
    static void clear(Store<EntityStore> store,Ref<EntityStore> actor){
        if(actor==null||!actor.isValid())return;
        var controller=store.getComponent(actor,EffectControllerComponent.getComponentType());var effect=EntityEffect.getAssetMap().getAsset(ID);
        if(controller!=null&&effect!=null&&controller.hasEffect(effect))controller.removeEffect(actor,EntityEffect.getAssetMap().getIndex(ID),store);
    }
}
