package com.inigmasgames.hytalerpg.input;

import com.hypixel.hytale.server.core.modules.interaction.interaction.config.RootInteraction;
import com.hypixel.hytale.server.core.modules.interaction.interaction.operation.Operation;
import com.hypixel.hytale.server.core.modules.interaction.interaction.operation.JumpOperation;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.SimpleInteraction;
import com.hypixel.hytale.server.core.asset.type.particle.config.ParticleSystem;
import com.hypixel.hytale.server.core.asset.type.entityeffect.config.EntityEffect;
import com.hypixel.hytale.builtin.beam.asset.Beam;
import com.hypixel.hytale.protocol.WaitForDataFrom;

/** Actual resolved native assets. Deliberately not a certificate of connected input or presentation. */
public final class NativeSupportTetherAudit {
    private NativeSupportTetherAudit(){}
    public static void requireAssets(){
        var root=RootInteraction.getAssetMap().getAsset(NativeAbilityBridgeAudit.HEALING_ROOT_ID);
        if(root==null||!root.needsRemoteSync())throw new IllegalStateException("HEALING_HELD_ROOT_UNRESOLVED");
        int held=0;
        for(int i=0;i<root.getOperationMax();i++){
            Operation op=root.getOperation(i);while(op instanceof Operation.NestedOperation n)op=n.inner();
            if(op instanceof NativeHeldChannelInteraction channel){
                held++;var packet=(com.hypixel.hytale.protocol.ChargingInteraction)channel.toPacket();
                if(packet.waitForDataFrom!=WaitForDataFrom.Client||!packet.allowIndefiniteHold||packet.displayProgress
                        ||packet.effects==null||!packet.effects.clearAnimationOnFinish||!"Spellbook".equals(packet.effects.itemPlayerAnimationsId)
                        ||!"CastPushCharging".equals(packet.effects.itemAnimationId))throw new IllegalStateException("HEALING_NATIVE_HOLD_PACKET_INVALID");
            }else if(!(op instanceof JumpOperation)&&op.getClass()!=SimpleInteraction.class)throw new IllegalStateException("HEALING_ROOT_GAMEPLAY_OPERATION:"+op.getClass().getName());
        }
        if(held!=1)throw new IllegalStateException("HEALING_ROOT_HOLD_COUNT");
        for(String id:java.util.List.of("RPG_Blizzard_Trail","Impact_Ice","RPG_Blizzard_Snow"))
            if(ParticleSystem.getAssetMap().getAsset(id)==null)throw new IllegalStateException("BLIZZARD_PARTICLE_MISSING:"+id);
        var shard=com.hypixel.hytale.server.core.asset.type.model.config.ModelAsset.getAssetMap().getAsset("RPG_Blizzard_Shard");
        if(shard==null||!"VFX/RPG/Blizzard/Portal_Shard.blockymodel".equals(shard.getModel()))throw new IllegalStateException("BLIZZARD_MODEL_MISSING");
        var model=com.hypixel.hytale.server.core.asset.type.model.config.Model.createStaticScaledModel(shard,1);
        if(model.toPacket()==null||model.getParticles()==null||model.getParticles().length!=1)throw new IllegalStateException("BLIZZARD_MODEL_PARTICLE_CONTRACT");
        for(String id:java.util.List.of("CombatText","Healthbar"))if(com.hypixel.hytale.server.core.modules.entityui.asset.EntityUIComponent.getAssetMap().getAsset(id)==null)
            throw new IllegalStateException("NATIVE_ACTOR_UI_MISSING:"+id);
        if(com.hypixel.hytale.server.core.asset.type.soundevent.config.SoundEvent.getAssetMap().getAsset("SFX_Ice_Ball_Death")==null)throw new IllegalStateException("BLIZZARD_SOUND_MISSING");
        com.hypixel.hytale.logger.HytaleLogger.getLogger().atInfo().log("RPG_HEAL_BLIZZARD_ASSETS cohort=AC model=RPG_Blizzard_Shard particles=3 nativeActorUI=Healthbar+CombatText sound=SFX_Ice_Ball_Death result=PASS connectedProof=false");
        var healingBeam=Beam.getAssetMap().getAsset("RPG_Healing");
        if(healingBeam==null||!"Trails/Void_Green.png".equals(healingBeam.getTexture()))
            throw new IllegalStateException("SUPPORT_NATIVE_BEAM_MISSING:RPG_Healing");
        if(ParticleSystem.getAssetMap().getAsset("RPG_Protection_Glow")==null)throw new IllegalStateException("SUPPORT_TETHER_PARTICLE_MISSING:RPG_Protection_Glow");
        if(EntityEffect.getAssetMap().getAsset("RPG_Protection_Visual")==null)throw new IllegalStateException("PROTECTION_VISUAL_MISSING");
        var animations=com.hypixel.hytale.server.core.asset.type.itemanimation.config.ItemPlayerAnimations.getAssetMap().getAsset("Spellbook");
        for(String id:java.util.List.of("CastPushCharging","CastPushCharged"))if(animations==null||!animations.getAnimations().containsKey(id))throw new IllegalStateException("SUPPORT_NATIVE_ANIMATION_MISSING:"+id);
        com.hypixel.hytale.logger.HytaleLogger.getLogger().atInfo().log("RPG_SUPPORT_TETHER_ASSETS cohort=AC skills=89 passives=67 heldRoot=RESOLVED nativeGameplay=false beam=RPG_Healing texture=Void_Green persistent=true connectedProof=false");
    }
}
