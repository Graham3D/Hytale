package com.inigmasgames.hytalerpg.commands;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.GameMode;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.NameMatching;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.RequiredArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractCommandCollection;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.inigmasgames.hytalerpg.execution.support.TrustedPlayerAllies;
import java.util.UUID;

/** Explicit mutual player consent for Shared Aegis. The command only stores UUIDs. */
public final class RpgTrustedAllyCommand extends AbstractCommandCollection {
    public RpgTrustedAllyCommand(TrustedPlayerAllies allies){
        super("trusted","Manage mutual Shared Aegis player allies.");
        addSubCommand(new Change("request",allies));
        addSubCommand(new Change("accept",allies));
        addSubCommand(new Change("revoke",allies));
        addSubCommand(new AbstractPlayerCommand("list","Show incoming requests and trusted player UUIDs."){
            {setPermissionGroup(GameMode.Adventure);}
            @Override protected void execute(CommandContext context,Store<EntityStore> store,Ref<EntityStore> ref,PlayerRef player,World world){
                context.sendMessage(Message.raw("Incoming trusted ally requests: "+allies.pendingFor(player.getUuid())));
                context.sendMessage(Message.raw("Trusted player allies: "+allies.trustedFor(player.getUuid())));
            }
        });
    }
    private static final class Change extends AbstractPlayerCommand {
        private final String action;
        private final TrustedPlayerAllies allies;
        private final RequiredArg<String> target;
        Change(String action,TrustedPlayerAllies allies){
            super(action,switch(action){case "request"->"Request mutual Shared Aegis ally trust.";
                case "accept"->"Accept a stored ally request.";default->"Revoke trust or a pending request.";});
            this.action=action;this.allies=allies;
            target=withRequiredArg("player","Exact online name or player UUID",ArgTypes.STRING);
            setPermissionGroup(GameMode.Adventure);
        }
        @Override protected void execute(CommandContext context,Store<EntityStore> store,Ref<EntityStore> ref,PlayerRef player,World world){
            try{
                UUID other=resolve(context.get(target));
                boolean changed=switch(action){
                    case "request"->allies.request(player.getUuid(),other);
                    case "accept"->allies.accept(player.getUuid(),other);
                    case "revoke"->allies.revoke(player.getUuid(),other);
                    default->throw new IllegalStateException("UNKNOWN_TRUST_ACTION");
                };
                context.sendMessage(Message.raw("Trusted ally "+action+(changed?" saved for ":" already settled for ")+other+"."));
                if(action.equals("request")&&changed){
                    var recipient=Universe.get().getPlayer(other);
                    if(recipient!=null)recipient.sendMessage(Message.raw("Trusted ally request from "+player.getUuid()
                            +". Use /rpg trusted accept "+player.getUuid()+" to consent."));
                }
            }catch(RuntimeException error){context.sendMessage(Message.raw("Trusted ally "+action+" rejected: "+error.getMessage()));}
        }
        private static UUID resolve(String input){
            try{return UUID.fromString(input);}catch(IllegalArgumentException notUuid){
                var online=Universe.get().getPlayerByUsername(input,NameMatching.EXACT_IGNORE_CASE);
                if(online==null)throw new IllegalArgumentException("Player is offline or name is not exact; use the player UUID");
                return online.getUuid();
            }
        }
    }
}
