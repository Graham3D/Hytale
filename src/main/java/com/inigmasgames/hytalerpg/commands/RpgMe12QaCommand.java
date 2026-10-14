package com.inigmasgames.hytalerpg.commands;

import com.hypixel.hytale.component.*;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.RequiredArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractCommandCollection;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.inigmasgames.hytalerpg.enemies.Me12QaAuthority;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

/** Explicit operator lease for one installed candidate and one loaded world. */
public final class RpgMe12QaCommand extends AbstractCommandCollection {
    public static final String PERMISSION="inigmasgames.rpg.me12.qa";
    public RpgMe12QaCommand(Me12QaAuthority authority){
        super("me12qa","Candidate-bound Master Enemies QA authority.");
        Objects.requireNonNull(authority);
        requirePermission(PERMISSION);setPermissionGroups();
        addSubCommand(new AbstractPlayerCommand("activate","Arm natural v2 QA for this exact world and save."){
            final RequiredArg<String> sha=withRequiredArg("candidateSha","Verified 40-character source commit",ArgTypes.STRING);
            final RequiredArg<String> worldId=withRequiredArg("worldUuid","Current native world UUID",ArgTypes.STRING);
            final RequiredArg<String> save=withRequiredArg("saveName","Current save directory name",ArgTypes.STRING);
            final RequiredArg<String> seconds=withRequiredArg("seconds","Lifetime from 1 to 600 seconds",ArgTypes.STRING);
            final RequiredArg<String> receipt=withRequiredArg("guardianReceipt","Absolute Guardian verification JSON path",ArgTypes.STRING);
            {requirePermission(PERMISSION);setPermissionGroups();}
            @Override protected void execute(CommandContext context,Store<EntityStore> store,Ref<EntityStore> actor,PlayerRef player,World world){
                try{
                    var selected=UUID.fromString(context.get(worldId));
                    if(!selected.equals(world.getWorldConfig().getUuid())||!selected.equals(player.getWorldUuid()))
                        throw new IllegalStateException("ME12_QA_WORLD_MISMATCH");
                    var path=Path.of(context.get(receipt));
                    if(!path.isAbsolute())throw new IllegalArgumentException("ME12_QA_RECEIPT_ABSOLUTE_PATH_REQUIRED");
                    var request=new Me12QaAuthority.Request(context.get(sha),selected,context.get(save),
                            Long.parseLong(context.get(seconds)),path);
                    authority.beginVerification(request);
                    context.sendMessage(Message.raw("ME12 QA verification started; authority remains inactive until it passes."));
                    CompletableFuture.supplyAsync(()->authority.verify(request)).whenComplete((proof,error)->{
                        try{world.execute(()->{
                            try{
                                if(error!=null){var cause=error instanceof CompletionException wrapped&&wrapped.getCause()!=null
                                        ?wrapped.getCause():error;
                                    throw new IllegalStateException("ME12_QA_VERIFICATION_FAILED: "+cause.getMessage());}
                                if(!selected.equals(player.getWorldUuid()))throw new IllegalStateException("ME12_QA_PLAYER_WORLD_CHANGED");
                                authority.activateVerified(proof);
                                context.sendMessage(Message.raw("ME12 QA active: "+authority.status(selected)));
                            }catch(RuntimeException rejected){context.sendMessage(Message.raw("ME12 QA activation rejected: "+rejected.getMessage()));}
                        });}catch(RuntimeException unloaded){/* World unload already revokes its pending lease. */}
                    });
                }catch(RuntimeException rejected){context.sendMessage(Message.raw("ME12 QA activation rejected: "+rejected.getMessage()));}
            }
        });
        addSubCommand(new AbstractPlayerCommand("revoke","Stop new natural v2 QA births immediately."){
            {requirePermission(PERMISSION);setPermissionGroups();}
            @Override protected void execute(CommandContext context,Store<EntityStore> store,Ref<EntityStore> actor,PlayerRef player,World world){
                authority.revoke();context.sendMessage(Message.raw("ME12 QA authority revoked; new births use DRAIN/v1."));
            }
        });
        addSubCommand(new AbstractPlayerCommand("status","Inspect the current QA lease without changing gameplay."){
            {requirePermission(PERMISSION);setPermissionGroups();}
            @Override protected void execute(CommandContext context,Store<EntityStore> store,Ref<EntityStore> actor,PlayerRef player,World world){
                context.sendMessage(Message.raw("ME12 QA "+authority.status(player.getWorldUuid())));
            }
        });
    }
}
