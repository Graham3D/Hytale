package com.inigmasgames.hytalerpg.input;

import com.hypixel.hytale.server.core.modules.interaction.interaction.config.RootInteraction;
import com.hypixel.hytale.server.core.modules.interaction.interaction.operation.Operation;
import com.hypixel.hytale.server.core.modules.interaction.interaction.operation.JumpOperation;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.SimpleInteraction;
import com.hypixel.hytale.server.core.asset.type.particle.config.ParticleSystem;
import com.hypixel.hytale.server.core.asset.type.entityeffect.config.EntityEffect;
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
        for(String id:java.util.List.of("Beam_Heal_Green","RPG_Protection_Glow"))if(ParticleSystem.getAssetMap().getAsset(id)==null)throw new IllegalStateException("SUPPORT_TETHER_PARTICLE_MISSING:"+id);
        if(EntityEffect.getAssetMap().getAsset("RPG_Protection_Visual")==null)throw new IllegalStateException("PROTECTION_VISUAL_MISSING");
        var animations=com.hypixel.hytale.server.core.asset.type.itemanimation.config.ItemPlayerAnimations.getAssetMap().getAsset("Spellbook");
        for(String id:java.util.List.of("CastPushCharging","CastPushCharged"))if(animations==null||!animations.getAnimations().containsKey(id))throw new IllegalStateException("SUPPORT_NATIVE_ANIMATION_MISSING:"+id);
        com.hypixel.hytale.logger.HytaleLogger.getLogger().atInfo().log("RPG_SUPPORT_TETHER_ASSETS cohort=V skills=89 passives=67 heldRoot=RESOLVED nativeGameplay=false particles=VERIFIED_ASSET connectedProof=false");
    }
}
