package com.inigmasgames.hytalerpg.commands;

import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.RequiredArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractAsyncCommand;
import com.inigmasgames.hytalerpg.diagnostics.MonsterSpawnTrace;
import com.inigmasgames.hytalerpg.spawning.NativeWorldSpawnDensity;
import java.util.concurrent.CompletableFuture;

/** Operator-only bounded observation; never changes a spawn decision. */
public final class RpgSpawnTraceCommand extends AbstractAsyncCommand {
    private final MonsterSpawnTrace trace;
    private final NativeWorldSpawnDensity density;
    private final RequiredArg<String> action;
    public RpgSpawnTraceCommand(MonsterSpawnTrace trace,NativeWorldSpawnDensity density){
        super("spawntrace","Capture native monster spawn and Elite birth decisions for two minutes.");
        this.trace=trace;
        this.density=density;
        action=withRequiredArg("start|stop|status","Start, stop, or inspect the bounded trace",ArgTypes.STRING);
        requirePermission("inigmasgames.rpg.spawntrace");
    }
    @Override protected CompletableFuture<Void> executeAsync(CommandContext context){
        String input=context.get(action).trim().toLowerCase(java.util.Locale.ROOT);
        try{
            var state=switch(input){
                case "start"->{trace.start();MonsterSpawnTrace.event("CONFIG",null,-1,"all",
                        "nativeDensityMultiplier="+density.multiplier());yield trace.status();}
                case "stop"->trace.stop();
                case "status"->trace.status();
                default->null;
            };
            if(state==null)context.sendMessage(Message.raw("Use /rpg spawntrace start, stop, or status."));
            else context.sendMessage(Message.raw("Monster spawn trace: "+(state.active()?"running ("+state.secondsRemaining()+"s left)":"stopped")
                    +"; events="+state.events()+", detailed="+state.detailedEvents()+"/"+MonsterSpawnTrace.MAX_DETAILS
                    +"; density="+density.multiplier()+"x; file="+state.lastFile()+"; error="+state.lastError()));
        }catch(IllegalStateException error){context.sendMessage(Message.raw(error.getMessage()));}
        return CompletableFuture.completedFuture(null);
    }
}
