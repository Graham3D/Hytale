package com.inigmasgames.hytalerpg.commands;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.OptionalArg;
import com.hypixel.hytale.server.core.command.system.arguments.system.RequiredArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.NPCPlugin;
import com.inigmasgames.hytalerpg.enemies.EnemyQaSpawnRequest;
import com.inigmasgames.hytalerpg.execution.hytale.NativeEnemyBirthOwner;
import java.util.*;
import java.util.function.Supplier;

/** Explicit owner QA request; execution remains with the native factory and Master Enemies birth owner. */
public final class RpgSpawnCommand extends AbstractPlayerCommand {
    private final RequiredArg<String> monster=withRequiredArg("monster","Exact native NPC role ID",ArgTypes.STRING);
    private final RequiredArg<String> family=withRequiredArg("family","champion, unique, or superunique",ArgTypes.STRING);
    private final OptionalArg<List<String>> aliases=withListOptionalArg("affixAlias","Explicit Master Enemies affixes",ArgTypes.STRING);
    private final Supplier<NativeEnemyBirthOwner> owner;

    public RpgSpawnCommand(Supplier<NativeEnemyBirthOwner> owner){
        super("spawn","Spawn one zero-economy Master Enemy for owner QA.");
        this.owner=Objects.requireNonNull(owner);
        requirePermission(RpgEnemiesCommand.AUTHOR_PERMISSION);
        monster.suggest((sender,input,count,result)->{
            var npc=NPCPlugin.get();
            for(var role:npc.getRoleTemplateNames(false))if(role.regionMatches(true,0,input,0,input.length())){
                try{npc.validateSpawnableRole(role);result.suggest(role);}catch(RuntimeException ignored){}
            }
        });
        family.suggest((sender,input,count,result)->EnemyQaSpawnRequest.familyTokens().stream()
                .filter(token->token.regionMatches(true,0,input,0,input.length())).forEach(result::suggest));
        aliases.suggest((sender,input,count,result)->{
            String[] parts=input.split("\\s+",-1);
            var used=new HashSet<String>();
            for(int i=0;i<parts.length-1;i++){
                try{used.addAll(EnemyQaSpawnRequest.parse("native","unique",List.of(parts[i])).affixIds());}
                catch(IllegalArgumentException ignored){}
            }
            String partial=parts.length==0?"":parts[parts.length-1];
            for(var alias:EnemyQaSpawnRequest.canonicalAliases()){
                if(!alias.regionMatches(true,0,partial,0,partial.length()))continue;
                String id=EnemyQaSpawnRequest.parse("native","unique",List.of(alias)).affixIds().getFirst();
                if(!used.contains(id))result.suggest(alias);
            }
        });
    }

    @Override protected void execute(CommandContext context,Store<EntityStore> store,Ref<EntityStore> ref,
            PlayerRef player,World world){
        try{
            var npc=NPCPlugin.get();
            var role=npc.getRoleTemplateNames(false).stream()
                    .filter(name->name.equalsIgnoreCase(context.get(monster))).findFirst()
                    .orElseThrow(()->new IllegalArgumentException("Unknown native NPC role: "+context.get(monster)));
            npc.validateSpawnableRole(role);
            var request=EnemyQaSpawnRequest.parse(role,context.get(family),
                    context.provided(aliases)?context.get(aliases):List.of());
            var transform=store.getComponent(ref,TransformComponent.getComponentType());
            if(transform==null)throw new IllegalStateException("Player position unavailable.");
            var birth=owner.get();
            if(birth==null)throw new IllegalStateException("Master Enemies birth owner unavailable.");
            birth.qaSpawn(store,world,transform.getPosition(),transform.getRotation(),request)
                    .whenComplete((encounter,error)->context.sendMessage(Message.raw(error==null
                            ?"QA Master Enemy spawned: "+role+" "+request.rarity()+"; affixes="+request.affixIds()
                                    +"; encounter="+encounter+"; rewards=0"
                            :"Master Enemies QA spawn failed: "+error.getMessage())));
        }catch(RuntimeException failure){context.sendMessage(Message.raw("Master Enemies QA spawn: "+failure.getMessage()));}
    }
}
