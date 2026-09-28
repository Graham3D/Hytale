package com.inigmasgames.hytalerpg.commands;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.inigmasgames.hytalerpg.execution.hytale.HytaleSummonSystem;

/** Operator QA toggle for the bound Sentinel's world-space affix list. */
public final class RpgSentinelAffixesCommand extends AbstractPlayerCommand {
    private final HytaleSummonSystem summons;
    public RpgSentinelAffixesCommand(HytaleSummonSystem summons){
        super("sentinel-affixes","Toggle the bound Iron Sentinel's inherited affix list.");
        this.summons=summons;requirePermission(RpgGearCommand.AUTHOR_PERMISSION);
    }
    @Override protected void execute(CommandContext context,Store<EntityStore> store,Ref<EntityStore> actor,
                                     PlayerRef player,World world){
        try{context.sendMessage(Message.raw("Iron Sentinel affixes: "+
                (summons.toggleSentinelAffixes(store,player.getUuid())?"ON":"OFF")));}
        catch(RuntimeException error){context.sendMessage(Message.raw("Iron Sentinel affixes: "+error.getMessage()));}
    }
}
