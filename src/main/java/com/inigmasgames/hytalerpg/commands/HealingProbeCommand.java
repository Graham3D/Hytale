package com.inigmasgames.hytalerpg.commands;

import com.hypixel.hytale.component.*;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.RequiredArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.inigmasgames.hytalerpg.execution.hytale.HealingPresentationProbe;

/** Registered only under the explicit disposable-world probe opt-in. */
public final class HealingProbeCommand extends AbstractPlayerCommand {
    private final HealingPresentationProbe probe;
    private final RequiredArg<String> mode,target;
    public HealingProbeCommand(HealingPresentationProbe probe){
        super("rpg-heal-probe","Ten-second visual-only control. Modes: world, empty, visible, recipient-once, recipient-overwrite, staff-once, staff-overwrite, beam, stop.");
        this.probe=probe;requirePermission("inigmasgames.rpg.healingprobe");
        mode=withRequiredArg("mode","Control layer or stop",ArgTypes.STRING);
        target=withRequiredArg("target","none for standalone; self or runtime UUID for live recipient; native only in isolated fixture; ignored by stop",ArgTypes.STRING);
    }
    @Override protected void execute(CommandContext context,Store<EntityStore> store,Ref<EntityStore> ref,PlayerRef player,World world){
        try{
            if(context.get(mode).equalsIgnoreCase("stop")){probe.stop(store,player.getUuid());context.sendMessage(Message.raw("Healing probe cleanup requested."));return;}
            probe.start(store,ref,player,context.get(mode),context.get(target));
            context.sendMessage(Message.raw("R032-AJ probe requested. Wait for STARTED or FAILED. Live-test: recipient uses self or existing UUID; cosmetic effect packets are client-only. No healing/cost. Observe for 10s, wait 12s between controls."));
        }catch(java.io.IOException|RuntimeException error){
            context.sendMessage(Message.raw("Probe rejected: "+error.getClass().getSimpleName()+" "+String.valueOf(error.getMessage()).replaceAll("[\\r\\n]"," ").substring(0,Math.min(160,String.valueOf(error.getMessage()).length()))+". No skill was cast."));
        }
    }
}
