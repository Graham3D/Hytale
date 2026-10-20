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
    public static final String SLOWED_ID="RPG_Strike_Action_Lock_Slowed";
    private NativeStrikeActionLock(){}
    public static void requireAsset(){
        requireAsset(ID,1);
        requireAsset(SLOWED_ID,.7);
    }
    private static void requireAsset(String id,double movementFactor){
        var effect=EntityEffect.getAssetMap().getAsset(id);
        if(effect==null||effect.getApplicationEffects()==null)throw new IllegalStateException("MULTISTRIKE_ACTION_LOCK_ASSET_MISSING");
        var app=effect.getApplicationEffects().toPacket();
        if(app.abilityEffects==null||app.abilityEffects.disabled==null||!Set.copyOf(Arrays.asList(app.abilityEffects.disabled)).equals(
                Set.of(InteractionType.Primary,InteractionType.Secondary,InteractionType.Ability1,InteractionType.Ability2,InteractionType.Ability3,InteractionType.Ability4))
                ||app.movementEffects!=null||Math.abs(app.horizontalSpeedMultiplier-movementFactor)>1e-6||effect.getDamageCalculator()!=null)
            throw new IllegalStateException("MULTISTRIKE_ACTION_LOCK_ASSET_INVALID");
    }
    static boolean required(com.inigmasgames.hytalerpg.execution.SkillExecutionContext context){
        return context.compiledPlan().strikes().multistrike() || context.profile().strike()!=null && context.profile().strike().details().actionLockSeconds()>0;
    }
    static boolean available(Store<EntityStore> store,Ref<EntityStore> actor){return EntityEffect.getAssetMap().getAsset(ID)!=null&&store.getComponent(actor,EffectControllerComponent.getComponentType())!=null;}
    static boolean available(Store<EntityStore> store,Ref<EntityStore> actor,double movementFactor){
        return available(store,actor) && (movementFactor==1 || movementFactor==.7 && EntityEffect.getAssetMap().getAsset(SLOWED_ID)!=null);
    }
    static void acquire(Store<EntityStore> store,Ref<EntityStore> actor,com.inigmasgames.hytalerpg.execution.SkillExecutionContext context){
        var details=context.profile().strike().details();
        String id=details.movementFactor()==1?ID:SLOWED_ID;
        var controller=store.getComponent(actor,EffectControllerComponent.getComponentType());var effect=EntityEffect.getAssetMap().getAsset(id);
        // Explicit owner removal at the authored window end; expiry is a finite crash/unload fallback only.
        float fallback=(float)Math.min(10,Math.max(1,details.actionLockSeconds())+1);
        if(controller==null||effect==null||!controller.addEffect(actor,effect,fallback,OverlapBehavior.OVERWRITE,store,actor))
            throw new IllegalStateException("MULTISTRIKE_NATIVE_ACTION_LOCK_REJECTED");
    }
    static void clear(Store<EntityStore> store,Ref<EntityStore> actor){
        if(actor==null||!actor.isValid())return;
        var controller=store.getComponent(actor,EffectControllerComponent.getComponentType());
        for(String id:List.of(ID,SLOWED_ID)){
            var effect=EntityEffect.getAssetMap().getAsset(id);
            if(controller!=null&&effect!=null&&controller.hasEffect(effect))controller.removeEffect(actor,EntityEffect.getAssetMap().getIndex(id),store);
        }
    }
}
