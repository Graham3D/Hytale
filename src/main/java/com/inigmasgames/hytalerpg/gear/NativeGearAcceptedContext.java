package com.inigmasgames.hytalerpg.gear;

import com.hypixel.hytale.server.core.entity.InteractionContext;
import com.hypixel.hytale.server.core.universe.PlayerRef;

/** Reads the pre-chain equipment acceptance, never the actor's later equipment. */
final class NativeGearAcceptedContext {
    private NativeGearAcceptedContext() {}
    static NativeGearAttackAcceptance.Chain require(InteractionContext context,GearInstance item) {
        var buffer=context.getCommandBuffer();
        var actor=buffer.getComponent(context.getOwningEntity(),PlayerRef.getComponentType());
        if(actor==null)throw new IllegalStateException("NATIVE_GEAR_PLAYER_MISSING");
        var world=buffer.getStore().getExternalData().getWorld().getWorldConfig().getUuid();
        var chain=context.getChain();
        var accepted=NativeGearAttackAcceptance.chain(world,actor.getUuid(),Integer.toString(chain.getChainId()));
        if(accepted==null||!accepted.item().equals(item.identity()))
            throw new IllegalStateException("NATIVE_GEAR_ACCEPTED_SOURCE_MISSING");
        return accepted;
    }
}
