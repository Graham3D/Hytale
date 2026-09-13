package com.inigmasgames.hytalerpg.input;

import com.hypixel.hytale.server.core.modules.interaction.interaction.config.RootInteraction;
import com.hypixel.hytale.server.core.modules.interaction.interaction.operation.Operation;
import com.hypixel.hytale.server.core.modules.interaction.interaction.operation.JumpOperation;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.SimpleInteraction;
import com.hypixel.hytale.server.core.asset.type.particle.config.ParticleSystem;
import com.hypixel.hytale.server.core.asset.type.entityeffect.config.EntityEffect;
import com.hypixel.hytale.builtin.beam.asset.Beam;
import com.hypixel.hytale.protocol.WaitForDataFrom;
import com.inigmasgames.hytalerpg.execution.hytale.NativeHealingBeamVisuals;

/** Actual resolved native assets. Deliberately not a certificate of connected input or presentation. */
public final class NativeSupportTetherAudit {
    private NativeSupportTetherAudit(){}
    public static void requireAssets(){
        NativeHealingParticlePathAudit.requireAssets();
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
        for(String id:java.util.List.of("RPG_Blizzard_Trail","Impact_Ice","Snow_Heavy","Beam_Heal_Green2","Effect_Health_Pack","Staff_Bronze"))
            if(ParticleSystem.getAssetMap().getAsset(id)==null)throw new IllegalStateException("BLIZZARD_PARTICLE_MISSING:"+id);
        var shard=com.hypixel.hytale.server.core.asset.type.model.config.ModelAsset.getAssetMap().getAsset("RPG_Blizzard_Shard");
        if(shard==null||!"VFX/RPG/Blizzard/Portal_Shard.blockymodel".equals(shard.getModel()))throw new IllegalStateException("BLIZZARD_MODEL_MISSING");
        var model=com.hypixel.hytale.server.core.asset.type.model.config.Model.createStaticScaledModel(shard,1);
        if(model.toPacket()==null||model.getParticles()==null||model.getParticles().length!=1)throw new IllegalStateException("BLIZZARD_MODEL_PARTICLE_CONTRACT");
        for(String id:java.util.List.of("CombatText","Healthbar"))if(com.hypixel.hytale.server.core.modules.entityui.asset.EntityUIComponent.getAssetMap().getAsset(id)==null)
            throw new IllegalStateException("NATIVE_ACTOR_UI_MISSING:"+id);
        if(com.hypixel.hytale.server.core.asset.type.soundevent.config.SoundEvent.getAssetMap().getAsset("SFX_Ice_Ball_Death")==null)throw new IllegalStateException("BLIZZARD_SOUND_MISSING");
        com.hypixel.hytale.logger.HytaleLogger.getLogger().atInfo().log("RPG_HEAL_BLIZZARD_ASSETS cohort=AH model=RPG_Blizzard_Shard snow=Snow_Heavy impact=Impact_Ice sound=SFX_Ice_Ball_Death result=PASS connectedProof=false");
        var healingBeam=Beam.getAssetMap().getAsset(NativeHealingBeamVisuals.ASSET_ID);
        if(healingBeam==null)throw new IllegalStateException("SUPPORT_NATIVE_BEAM_MISSING:"+NativeHealingBeamVisuals.ASSET_ID);
        if(!"Trails/RPG_Healing_Core.png".equals(healingBeam.getTexture()))throw new IllegalStateException("SUPPORT_NATIVE_BEAM_TEXTURE_MISMATCH");
        var healingFlow=com.hypixel.hytale.builtin.beam.asset.Beam.getAssetMap().getAsset(com.inigmasgames.hytalerpg.execution.hytale.NativeHealingBeamVisuals.FLOW_ASSET_ID);
        if(healingFlow==null||!"Trails/RPG_Healing_Flow.png".equals(healingFlow.getTexture()))throw new IllegalStateException("SUPPORT_NATIVE_BEAM_FLOW_TEXTURE_MISMATCH");
        if(ParticleSystem.getAssetMap().getAsset("RPG_Protection_Glow")==null)throw new IllegalStateException("SUPPORT_TETHER_PARTICLE_MISSING:RPG_Protection_Glow");
        if(EntityEffect.getAssetMap().getAsset("RPG_Protection_Visual")==null)throw new IllegalStateException("PROTECTION_VISUAL_MISSING");
        var animations=com.hypixel.hytale.server.core.asset.type.itemanimation.config.ItemPlayerAnimations.getAssetMap().getAsset("Spellbook");
        for(String id:java.util.List.of("CastPushCharging","CastPushCharged"))if(animations==null||!animations.getAnimations().containsKey(id))throw new IllegalStateException("SUPPORT_NATIVE_ANIMATION_MISSING:"+id);
        var stream=com.hypixel.hytale.server.core.asset.type.model.config.ModelAsset.getAssetMap().getAsset("RPG_Healing_Stream");
        if(stream==null)throw new IllegalStateException("HEAL_STREAM_MODEL_MISSING");
        var streamModel=com.hypixel.hytale.server.core.asset.type.model.config.Model.createStaticScaledModel(stream,1);
        if(!"Items/Projectiles/Projectile_default.png".equals(streamModel.toPacket().texture))
            throw new IllegalStateException("HEAL_STREAM_TEXTURE_CONTRACT");
        var particles=streamModel.getParticles();
        if(particles==null||particles.length!=1||!"Beam_Heal_Green2".equals(particles[0].getSystemId())||!particles[0].isClearParticlesOnRemove())
            throw new IllegalStateException("HEAL_STREAM_MODEL_PARTICLE_CONTRACT");
        var recipient=EntityEffect.getAssetMap().getAsset("RPG_Healing_Recipient");
        if(recipient==null||recipient.getDamageCalculator()!=null||recipient.getStatModifiers()!=null&&!recipient.getStatModifiers().isEmpty()
                ||recipient.getEntityStats()!=null&&!recipient.getEntityStats().isEmpty()||recipient.isInfinite()||recipient.isInvulnerable())
            throw new IllegalStateException("HEAL_RECIPIENT_COSMETIC_CONTRACT");
        var recipientParticles=recipient.getApplicationEffects().toPacket().particles;
        if(recipientParticles==null||recipientParticles.length!=1||!"RPG_Heal_Red_Recipient".equals(recipientParticles[0].systemId)||!recipientParticles[0].clearParticlesOnRemove)
            throw new IllegalStateException("HEAL_RECIPIENT_PARTICLE_CONTRACT");
        try(var input=NativeSupportTetherAudit.class.getResourceAsStream("/rpg/presentation/staff-heads-ag.json")){
            if(input==null)throw new IllegalStateException("STAFF_PRESENTATION_MANIFEST_MISSING");
            var entries=com.google.gson.JsonParser.parseReader(new java.io.InputStreamReader(input,java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
            if(entries.size()!=26)throw new IllegalStateException("STAFF_PRESENTATION_COVERAGE");
            for(var entry:entries.entrySet()){
                var item=com.hypixel.hytale.server.core.asset.type.item.config.Item.getAssetMap().getAsset(entry.getKey());
                var expected=entry.getValue().getAsJsonObject();
                if(item==null||!expected.get("model").getAsString().equals(item.getModel()))throw new IllegalStateException("STAFF_PRESENTATION_MODEL:"+entry.getKey());
                var staffParticles=item.toPacket().particles;
                if(staffParticles!=null&&java.util.Arrays.stream(staffParticles).anyMatch(p->"Staff_Bronze".equals(p.systemId)))
                    throw new IllegalStateException("STAFF_PERMANENT_PARTICLE:"+entry.getKey());
                var effect=EntityEffect.getAssetMap().getAsset(com.inigmasgames.hytalerpg.execution.hytale.HealingParticleVisuals.staffEffect(entry.getKey()));
                if(effect==null||effect.isInfinite()||effect.getDamageCalculator()!=null
                        ||effect.getStatModifiers()!=null&&!effect.getStatModifiers().isEmpty()
                        ||effect.getEntityStats()!=null&&!effect.getEntityStats().isEmpty())
                    throw new IllegalStateException("STAFF_CHANNEL_EFFECT_INVALID:"+entry.getKey());
                var p=effect.getApplicationEffects().toPacket().particles;
                if(p==null||p.length!=1||!"RPG_Heal_Red_Staff".equals(p[0].systemId)
                        ||p[0].targetEntityPart!=com.hypixel.hytale.protocol.EntityPart.PrimaryItem
                        ||!expected.get("node").getAsString().equals(p[0].targetNodeName)||!p[0].clearParticlesOnRemove)
                    throw new IllegalStateException("STAFF_CHANNEL_ATTACHMENT:"+entry.getKey());
            }
            com.hypixel.hytale.logger.HytaleLogger.getLogger().atInfo().log("RPG_STAFF_HEAD_ASSETS cohort=AH staffs=26 system=RPG_Heal_Red_Staff attachment=CHANNEL_PRIMARY_ITEM_NODE result=PASS connectedProof=false");
        }catch(java.io.IOException error){throw new IllegalStateException("STAFF_PRESENTATION_MANIFEST_READ",error);}
        com.hypixel.hytale.logger.HytaleLogger.getLogger().atInfo().log("RPG_SUPPORT_TETHER_ASSETS cohort=AH skills=89 passives=67 heldRoot=RESOLVED nativeGameplay=false particles=VERIFIED_ASSET connectedProof=false");
    }
}
