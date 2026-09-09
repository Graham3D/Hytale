package com.inigmasgames.hytalerpg.commands;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.GameMode;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.RequiredArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.inigmasgames.hytalerpg.execution.hytale.HytaleSupportSystem;

/** Interim configuration command, not a new HUD; normal Aura validation/payment remains authoritative. */
public final class RpgManaguardCommand extends AbstractPlayerCommand {
    private final HytaleSupportSystem support;
    private final RequiredArg<Integer> percent=withRequiredArg("percent","Integer Mana allocation 1..50",ArgTypes.INTEGER);
    public RpgManaguardCommand(HytaleSupportSystem support){
        super("managuard","Set Managuard reservation percentage without refilling Mana or shield.");
        this.support=support;setPermissionGroup(GameMode.Adventure);
    }
    @Override protected void execute(CommandContext context,Store<EntityStore> store,Ref<EntityStore> ref,PlayerRef player,World world){
        try{
            int value=context.get(percent);
            support.runtime().allocateManaguard(player.getUuid(),value,support.port(store,ref));
            context.sendMessage(Message.raw("Managuard allocation requested: "+value+"%. Pending durable authorization; Mana is not refunded and shield deficit is retained."));
        }catch(RuntimeException error){context.sendMessage(Message.raw("Managuard allocation rejected: "+error.getMessage()));}
    }
}
