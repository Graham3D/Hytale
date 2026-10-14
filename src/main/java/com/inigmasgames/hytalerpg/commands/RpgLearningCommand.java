package com.inigmasgames.hytalerpg.commands;

import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.*;
import com.inigmasgames.hytalerpg.progress.LearningSourceConfiguration;
import java.util.concurrent.CompletableFuture;

/** Server-only configuration authority, independent of client progression payloads. */
public final class RpgLearningCommand extends AbstractCommandCollection {
    public static final String PERMISSION="inigmasgames.rpg.learning.author";
    public RpgLearningCommand(LearningSourceConfiguration configuration,java.util.function.Supplier<java.util.concurrent.Executor> io){
        super("rpg-learning","Validate and reload owner-authored monster learning assignments.");requirePermission(PERMISSION);setPermissionGroups();
        for(String action:java.util.List.of("status","validate","reload"))addSubCommand(new AbstractAsyncCommand(action,"Learning assignments: "+action){
            {requirePermission(PERMISSION);setPermissionGroups();}
            @Override protected CompletableFuture<Void> executeAsync(CommandContext context){
                var executor=io.get();
                if(executor==null){context.sendMessage(Message.raw("REJECTED; configuration IO is unavailable"));return CompletableFuture.completedFuture(null);}
                try{return runAsync(context,()->{
                try{
                    if(action.equals("validate"))context.sendMessage(Message.raw("VALID: "+configuration.validate().revision()+" (not activated)"));
                    else if(action.equals("reload"))context.sendMessage(Message.raw("ACTIVATED: "+configuration.reload().revision()+"; existing opportunities unchanged"));
                    else context.sendMessage(Message.raw(configuration.status()));
                }catch(java.io.IOException|RuntimeException failure){context.sendMessage(Message.raw("REJECTED; previous mapping retained: "+failure.getMessage()));}
                },executor);}catch(java.util.concurrent.RejectedExecutionException busy){context.sendMessage(Message.raw("REJECTED; configuration IO is busy; previous mapping retained"));return CompletableFuture.completedFuture(null);}
            }
        });
    }
}
