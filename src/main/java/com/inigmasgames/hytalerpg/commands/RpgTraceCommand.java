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
import com.inigmasgames.hytalerpg.diagnostics.*;

/** Admin-only session diagnostic switch; no save mutation, gameplay or native input changes. */
public final class RpgTraceCommand extends AbstractPlayerCommand {
    private final RpgSkillTraceService trace;
    private final RequiredArg<String> mode;
    public RpgTraceCommand(RpgSkillTraceService trace){
        super("rpg-trace","Set NORMAL, DETAILED or PERFORMANCE skill telemetry; STATUS inspects current mode.");
        this.trace=trace;mode=withRequiredArg("mode","normal, detailed, performance or status",ArgTypes.STRING);
        // Deliberately keep the native explicit/admin command permission.
    }
    @Override protected void execute(CommandContext context,Store<EntityStore> store,Ref<EntityStore> ref,PlayerRef player,World world){
        String requested=context.get(mode);
        try{if(!requested.equalsIgnoreCase("status"))trace.setLevel(SkillTraceLevel.parse(requested));
            context.sendMessage(Message.raw("RPG trace="+trace.level()+" metrics="+trace.metrics()+". PERFORMANCE raw ticks required for percentile qualification; TRACE_GAP invalidates completeness."));
        }catch(IllegalArgumentException|IllegalStateException error){context.sendMessage(Message.raw("Trace mode unavailable. Use normal, detailed, performance or status. No gameplay changed."));}
    }
}
