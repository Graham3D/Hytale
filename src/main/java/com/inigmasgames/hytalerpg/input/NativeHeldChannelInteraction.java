package com.inigmasgames.hytalerpg.input;

import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.protocol.*;
import com.hypixel.hytale.server.core.entity.InteractionContext;
import com.hypixel.hytale.server.core.modules.interaction.interaction.CooldownHandler;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.client.ChargingInteraction;
import com.hypixel.hytale.server.core.universe.PlayerRef;

/** Uses the shipped Charging packet/simulation; server observes its validated held state only. */
public final class NativeHeldChannelInteraction extends ChargingInteraction {
    public static final String TYPE="RPG_HeldChannel";
    private final HytaleAbilitySkillInputAdapter inputs;
    public NativeHeldChannelInteraction(HytaleAbilitySkillInputAdapter inputs){this.inputs=inputs;}
    public static BuilderCodec<NativeHeldChannelInteraction> codec(HytaleAbilitySkillInputAdapter inputs){
        return BuilderCodec.builder(NativeHeldChannelInteraction.class,()->new NativeHeldChannelInteraction(inputs),ChargingInteraction.CODEC).build();
    }
    @Override protected void tick0(boolean first,float time,InteractionType type,InteractionContext context,CooldownHandler cooldowns){
        super.tick0(first,time,type,context,cooldowns);
        var buffer=context.getCommandBuffer();var owner=context.getOwningEntity();var chain=context.getChain();
        if(buffer==null||owner==null||!owner.isValid()||chain==null||chain.getForkedChainId()!=null||!owner.equals(context.getEntity()))return;
        var player=buffer.getComponent(owner,PlayerRef.getComponentType());var item=context.getOriginalItemType();
        if(player==null||item==null||!"RPG_Ability_Healing_Beam".equals(item.getId())||item.getAbility()==null
                ||!NativeAbilityBridgeAudit.HEALING_ROOT_ID.equals(item.getAbility().getCastRootId()))return;
        boolean held=context.getClientState().chargeValue==-1f&&context.getClientState().state!=InteractionState.Failed
                &&context.getState().state==InteractionState.NotFinished;
        inputs.nativeHold(player.getUuid(),type,chain.getChainId(),chain,item.getId(),context.getHeldItemSlot(),held);
    }
}
