package com.inigmasgames.hytalerpg.execution.hytale;

import com.hypixel.hytale.component.*;
import com.hypixel.hytale.server.core.entity.effect.EffectControllerComponent;
import com.hypixel.hytale.server.core.asset.type.entityeffect.config.*;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.List;

/** Finite native visual-only effect leases. No particle callback can trigger damage or resource work. */
final class MantlePresentation {
    static final float IMPACT_SECONDS=7f;
    /** Existing effect owner, explicitly restarted only by a newly claimed positive-damage pulse. */
    static void impact(Store<EntityStore> store,Ref<EntityStore> target){
        var controller=store.getComponent(target,EffectControllerComponent.getComponentType());
        if(controller==null)throw new IllegalStateException("MANTLE_EFFECT_CONTROLLER_MISSING");
        int index=EntityEffect.getAssetMap().getIndex("RPG_Mantle_Impact");
        if(controller.hasEffect(index))controller.removeEffect(target,index,store);
        apply(store,target,"RPG_Mantle_Impact",IMPACT_SECONDS);
    }
    static void audit(Store<EntityStore> store){
        if(!Boolean.getBoolean("rpg.projectileSpawnAudit"))throw new IllegalStateException("ISOLATED_AUDIT_DISABLED");
        requireAssets();var holder=EntityStore.REGISTRY.newHolder();
        holder.addComponent(com.hypixel.hytale.server.core.entity.UUIDComponent.getComponentType(),new com.hypixel.hytale.server.core.entity.UUIDComponent(java.util.UUID.randomUUID()));
        var effects=new EffectControllerComponent();holder.addComponent(EffectControllerComponent.getComponentType(),effects);
        var stats=new com.hypixel.hytale.server.core.modules.entitystats.EntityStatMap();stats.update();
        holder.addComponent(com.hypixel.hytale.server.core.modules.entitystats.EntityStatMap.getComponentType(),stats);
        holder.ensureComponent(EntityStore.REGISTRY.getNonSerializedComponentType());
        var ref=store.addEntity(holder,AddReason.SPAWN);
        try{
            var ids=List.of("RPG_Mantle_Aura","RPG_Mantle_Pulse","RPG_Mantle_Impact","RPG_Mantle_Flash");
            float[] durations={.75f,.4f,IMPACT_SECONDS,.12f};
            for(int i=0;i<ids.size();i++)apply(store,ref,ids.get(i),durations[i]);
            apply(store,ref,"RPG_Mantle_Aura",.75f);
            if(effects.getActiveEffects().size()!=4)throw new IllegalStateException("MANTLE_NATIVE_EFFECT_COUNT");
            effects.clearChanges();impact(store,ref);
            var changes=effects.consumeChanges();
            if(changes.length!=2||changes[0].type!=com.hypixel.hytale.protocol.EffectOp.Remove
                    ||changes[1].type!=com.hypixel.hytale.protocol.EffectOp.Add)
                throw new IllegalStateException("MANTLE_IMPACT_RESTART_ORDER");
            if(effects.getActiveEffects().size()!=4)throw new IllegalStateException("MANTLE_IMPACT_DUPLICATE_OWNER");
            remove(store,ref);
            if(effects.getActiveEffects().size()!=2)throw new IllegalStateException("MANTLE_NATIVE_AURA_CLEANUP");
            for(var id:ids)effects.removeEffect(ref,EntityEffect.getAssetMap().getIndex(id),store);
            if(!effects.getActiveEffects().isEmpty())throw new IllegalStateException("MANTLE_NATIVE_RESIDUE");
            com.hypixel.hytale.logger.HytaleLogger.getLogger().atInfo().log("RPG_MANTLE_NATIVE_EFFECTS result=PASS attachedEffects=4 overwrite=true removal=true connectedProof=false");
            com.hypixel.hytale.logger.HytaleLogger.getLogger().atInfo().log("RPG_MANTLE_AR_PRESENTATION result=PASS cameraVariants=2 sharedEffectOwners=1 scale=0.5 impactRestart=RemoveThenAdd impactSeconds=7 connectedProof=false");
        }finally{if(ref.isValid())store.removeEntity(ref,RemoveReason.REMOVE);}
    }
    static void requireAssets(){
        for(var id:List.of("RPG_Mantle_Aura","RPG_Mantle_Pulse","RPG_Mantle_Impact","RPG_Mantle_Flash")){
            var effect=EntityEffect.getAssetMap().getAsset(id);
            if(effect==null||effect.getDamageCalculator()!=null||effect.getApplicationEffects()==null)
                throw new IllegalStateException("MANTLE_VISUAL_ASSET_INVALID:"+id);
            var app=effect.getApplicationEffects().toPacket();
            if(app.movementEffects!=null||app.abilityEffects!=null||app.horizontalSpeedMultiplier!=1)
                throw new IllegalStateException("MANTLE_VISUAL_GAMEPLAY_MUTATION:"+id);
        }
        var aura=EntityEffect.getAssetMap().getAsset("RPG_Mantle_Aura").getApplicationEffects().toPacket();
        if(aura.particles==null||aura.particles.length!=1||aura.firstPersonParticles==null||aura.firstPersonParticles.length!=1
                ||aura.screenEffect!=null)throw new IllegalStateException("MANTLE_CAMERA_VARIANTS_MISSING");
        for(var p:List.of(aura.particles[0],aura.firstPersonParticles[0]))
            if(!p.systemId.equals("RPG_Mantle_Aura")||p.scale!=.5f||p.targetEntityPart!=com.hypixel.hytale.protocol.EntityPart.Self
                    ||p.targetNodeName!=null||p.detachedFromModel||!p.clearParticlesOnRemove)
                throw new IllegalStateException("MANTLE_CAMERA_VARIANT_CONTRACT");
        var impact=EntityEffect.getAssetMap().getAsset("RPG_Mantle_Impact").getApplicationEffects().toPacket().particles;
        if(impact==null||impact.length!=1||!impact[0].systemId.equals("Impact_Fire")||impact[0].positionOffset==null
                ||impact[0].positionOffset.y()!=1.25f||impact[0].scale!=1||!impact[0].clearParticlesOnRemove)
            throw new IllegalStateException("MANTLE_IMPACT_CONTRACT");
    }
    static void apply(Store<EntityStore> store,Ref<EntityStore> ref,String id,float seconds){
        if(ref==null||!ref.isValid())return;
        var controller=store.getComponent(ref,EffectControllerComponent.getComponentType());
        if(controller==null)throw new IllegalStateException("MANTLE_EFFECT_CONTROLLER_MISSING");
        if(!controller.addEffect(ref,EntityEffect.getAssetMap().getAsset(id),seconds,OverlapBehavior.OVERWRITE,store))
            throw new IllegalStateException("MANTLE_VISUAL_REJECTED:"+id);
    }
    static void remove(Store<EntityStore> store,Ref<EntityStore> ref){
        if(ref==null||!ref.isValid())return;var controller=store.getComponent(ref,EffectControllerComponent.getComponentType());
        if(controller!=null)for(var id:List.of("RPG_Mantle_Aura","RPG_Mantle_Pulse"))
            controller.removeEffect(ref,EntityEffect.getAssetMap().getIndex(id),store);
    }
}
