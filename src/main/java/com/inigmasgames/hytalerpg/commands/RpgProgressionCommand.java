package com.inigmasgames.hytalerpg.commands;

import com.hypixel.hytale.component.*;
import com.hypixel.hytale.protocol.GameMode;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.RequiredArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.hypixel.hytale.server.core.command.system.basecommands.*;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.inigmasgames.hytalerpg.domain.PassiveId;
import com.inigmasgames.hytalerpg.progress.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Bounded self-only interim frontend. No public XP, ownership or learning grant command. */
public final class RpgProgressionCommand extends AbstractCommandCollection {
    public RpgProgressionCommand(RpgLoadoutService service){
        super("progress","Inspect progression, buy eligible passives, respec or transfer a build.");
        addSubCommand(new Action("status",service));addSubCommand(new Action("export",service));
        addSubCommand(new Action("respec",service));addSubCommand(new Action("buy",service));addSubCommand(new Action("import",service));
    }
    private static final class Action extends AbstractPlayerCommand {
        final String action;final RpgLoadoutService service;
        final RequiredArg<String> value,revision,request;
        Action(String action,RpgLoadoutService service){
            super(action,"Server-authoritative progression "+action);this.action=action;this.service=service;setPermissionGroup(GameMode.Adventure);
            value=Set.of("buy","import").contains(action)?withRequiredArg("value",action.equals("buy")?"Canonical passive ID":"Base64url build from export",ArgTypes.STRING):null;
            revision=Set.of("buy","import","respec").contains(action)?withRequiredArg("revision","Authoritative RPG revision from status",ArgTypes.STRING):null;
            request=action.equals("buy")?withRequiredArg("request","Unique UUID; reuse to retry the same purchase",ArgTypes.STRING):null;
        }
        @Override protected void execute(CommandContext context,Store<EntityStore> store,Ref<EntityStore> ref,PlayerRef player,World world){
            try{
                UUID actor=player.getUuid();String message;
                switch(action){
                    case "status" -> {var s=service.getPresentationView(actor).state();message="RPG revision "+s.revision+"; level "+s.level+"; total XP "+s.currentXp+"; Insight available "+s.acquisition.availableInsight(s.rewards.insight())+" (earned "+s.rewards.insight()+", spent "+s.acquisition.spentInsight()+"). Eligible-use skills: "+s.acquisition.meaningfulSkills()+". Pity: "+s.acquisition.pity()+". Public learning requires a VERIFIED_CONNECTED source.";}
                    case "export" -> message=Base64.getUrlEncoder().withoutPadding().encodeToString(service.exportBuild(actor).getBytes(StandardCharsets.UTF_8));
                    case "buy" -> {var result=service.purchasePassive(actor,new PassiveId(context.get(value)),Long.parseLong(context.get(revision)),UUID.fromString(context.get(request)));message="Passive purchase "+result.outcome()+"; receipt "+result.receiptHash();}
                    case "respec" -> message=service.respecAttributes(actor,Long.parseLong(context.get(revision)),UUID.randomUUID().toString()).message();
                    case "import" -> {
                        String encoded=context.get(value);if(encoded.length()>22000)throw new IllegalArgumentException("BUILD_TRANSFER_BUDGET");
                        String json=new String(Base64.getUrlDecoder().decode(encoded),StandardCharsets.UTF_8);
                        message=service.importBuild(actor,Long.parseLong(context.get(revision)),json,UUID.randomUUID().toString()).message();
                    }
                    default -> throw new IllegalStateException("UNKNOWN_PROGRESSION_ACTION");
                }
                context.sendMessage(Message.raw(message));
            }catch(RuntimeException error){context.sendMessage(Message.raw("Progression request rejected: "+error.getMessage()));}
        }
    }
}
