package com.inigmasgames.hytalerpg.commands;

import com.hypixel.hytale.component.*;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.*;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.inigmasgames.hytalerpg.execution.hytale.HytaleSummonSystem;
import com.inigmasgames.hytalerpg.gear.GearQaTrace;

public final class RpgSentinelTraceCommand extends AbstractCommandCollection {
    public RpgSentinelTraceCommand(GearQaTrace trace,HytaleSummonSystem summons){
        super("sentineltrace","Bounded read-only Iron Sentinel snapshots.");requirePermission(RpgGearCommand.AUTHOR_PERMISSION);
        for(String action:java.util.List.of("on","snapshot","off"))addSubCommand(new AbstractPlayerCommand(action,"Sentinel trace "+action){
            @Override protected void execute(CommandContext c,Store<EntityStore> store,Ref<EntityStore> actor,PlayerRef p,World w){
                try{
                    String message=switch(action){case "on"->trace.on(p.getUuid());case "off"->trace.off(p.getUuid());
                        default->trace.snapshot(p.getUuid(),summons.qaSnapshot(store,p.getUuid()));};
                    c.sendMessage(Message.raw(message));
                }catch(RuntimeException error){c.sendMessage(Message.raw("Sentinel snapshot unavailable: "+error.getMessage()));}
            }
        });
    }
}
