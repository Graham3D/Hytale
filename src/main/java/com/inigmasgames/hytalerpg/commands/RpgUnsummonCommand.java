package com.inigmasgames.hytalerpg.commands;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.GameMode;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.inigmasgames.hytalerpg.execution.hytale.HytaleSummonSystem;
import com.inigmasgames.hytalerpg.execution.hytale.HytaleConversionSystem;

/** Player-owned dismissal; the durable Iron Sentinel source stays consumed. */
public final class RpgUnsummonCommand extends AbstractPlayerCommand {
    private final HytaleSummonSystem summons;
    private final HytaleConversionSystem conversions;
    public RpgUnsummonCommand(HytaleSummonSystem summons,HytaleConversionSystem conversions){
        super("unsummon","Dismiss all of your active summons, including Iron Sentinel.");
        this.summons=summons;this.conversions=conversions;setPermissionGroup(GameMode.Adventure);
    }
    @Override protected void execute(CommandContext context,Store<EntityStore> store,Ref<EntityStore> actor,
                                     PlayerRef player,World world){
        try{int count=summons.unsummon(store,player.getUuid())+conversions.dismissOwned(player.getUuid());
            context.sendMessage(Message.raw(count==0?"You have no summons to dismiss.":
                    "Dismissed "+count+" summon"+(count==1?"":"s")+". Bound Iron Sentinel gear remains consumed."));}
        catch(RuntimeException error){context.sendMessage(Message.raw("Unsummon failed: "+error.getMessage()));}
    }
}
