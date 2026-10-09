package com.inigmasgames.hytalerpg.commands;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.RequiredArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.NPCPlugin;
import com.inigmasgames.hytalerpg.enemies.EnemyQaSpawnRequest;
import com.inigmasgames.hytalerpg.enemies.EnemyNativeBindings;
import com.inigmasgames.hytalerpg.enemies.EnemyAffixRegistry;
import com.inigmasgames.hytalerpg.enemies.EnemyAffixSelection;
import com.inigmasgames.hytalerpg.enemies.EnemyBalance;
import com.inigmasgames.hytalerpg.enemies.EnemyRarity;
import com.inigmasgames.hytalerpg.difficulty.DifficultyId;
import com.inigmasgames.hytalerpg.progress.EnemyRewardRegistry;
import com.inigmasgames.hytalerpg.execution.hytale.NativeEnemyBirthOwner;
import java.util.*;
import java.util.function.Supplier;

/** Explicit owner QA request; execution remains with the native factory and Master Enemies birth owner. */
public final class RpgSpawnCommand extends AbstractPlayerCommand {
    private final RequiredArg<String> monster=withRequiredArg("monster","Exact native NPC role ID",ArgTypes.STRING);
    private final RequiredArg<String> family=withRequiredArg("family","champion, unique, or superunique",ArgTypes.STRING);
    private final RequiredArg<String> era=withRequiredArg("era","normal, nightmare, or hell",ArgTypes.STRING);
    private final Supplier<NativeEnemyBirthOwner> owner;
    private final EnemyRewardRegistry catalog=EnemyRewardRegistry.load();
    private final EnemyNativeBindings bindings=EnemyNativeBindings.load();
    private final EnemyAffixSelection selector=new EnemyAffixSelection(EnemyAffixRegistry.canonical());

    private List<String> concreteRoles(){
        var ids=new TreeSet<String>();
        catalog.roles().forEach(row->ids.add(row.roleId()));
        catalog.aliases().forEach(row->ids.add(row.roleId()));
        return List.copyOf(ids);
    }
    private boolean supportsAutomaticPlan(EnemyNativeBindings.Role role,EnemyRarity rarity,DifficultyId mode){
        var qa=new EnemyQaSpawnRequest(role.canonicalRoleId(),rarity,mode,List.of());
        var ids=new HashSet<String>();
        for(var operator:EnemyAffixRegistry.Operator.values())ids.add(operator.id());
        if(role.actions().isEmpty())ids.retainAll(Set.of("ME-003","ME-004","ME-022","ME-023","ME-024","ME-026","ME-027"));
        for(var action:role.actions())if(action.supportedAffixIds()!=null)ids.retainAll(action.supportedAffixIds());
        var binding=new EnemyAffixSelection.Binding(bindings.revision(),role.capabilities(),0,true,
                role.nativeStunStaggerImmune(),role.nativeSlowImmune(),qa.baseMinions(),
                role.distanceDisplacementDelivery(),ids);
        var request=new EnemyAffixSelection.Request(binding,mode,rarity,qa.requiredAffixes(EnemyBalance.canonical()),
                qa.authoredSuperUnique().map(com.inigmasgames.hytalerpg.enemies.SuperUniqueTemplates.Template::fixedAffixes)
                        .orElse(List.of()),true);
        return selector.select(request,"qa-random-role-compatibility").isPresent();
    }

    public RpgSpawnCommand(Supplier<NativeEnemyBirthOwner> owner){
        super("spawn","Spawn one zero-economy Master Enemy for owner QA.");
        this.owner=Objects.requireNonNull(owner);
        requirePermission(RpgEnemiesCommand.AUTHOR_PERMISSION);
        addSubCommand(new AbstractPlayerCommand("clear","Remove loaded Master Enemies QA spawns in this world.") {
            {
                requirePermission(RpgEnemiesCommand.AUTHOR_PERMISSION);
            }
            @Override protected void execute(CommandContext context,Store<EntityStore> store,
                    Ref<EntityStore> ref,PlayerRef player,World world) {
                var birth=RpgSpawnCommand.this.owner.get();
                if(birth==null)throw new IllegalStateException("Master Enemies birth owner unavailable.");
                int removed=birth.clearQa(store);
                context.sendMessage(Message.raw("Removed "+removed+" loaded QA Master Enemies actors."));
            }
        });
        // The SDK's list optional arg is a named --affixAlias flag. Owner QA uses
        // trailing positional aliases, so admit extra tokens and parse only that tail.
        setAllowsExtraArguments(true);
        monster.suggest((sender,input,count,result)->{
            var npc=NPCPlugin.get();
            if("random".startsWith(input.toLowerCase(Locale.ROOT)))result.suggest("random");
            for(var role:concreteRoles())if(role.regionMatches(true,0,input,0,input.length())){
                try{npc.validateSpawnableRole(role);result.suggest(role);}catch(RuntimeException ignored){}
            }
        });
        family.suggest((sender,input,count,result)->EnemyQaSpawnRequest.familyTokens().stream()
                .filter(token->token.regionMatches(true,0,input,0,input.length())).forEach(result::suggest));
        era.suggest((sender,input,count,result)->EnemyQaSpawnRequest.eraTokens().stream()
                .filter(token->token.regionMatches(true,0,input,0,input.length())).forEach(result::suggest));
    }

    @Override protected void execute(CommandContext context,Store<EntityStore> store,Ref<EntityStore> ref,
            PlayerRef player,World world){
        try{
            var npc=NPCPlugin.get();
            String input=context.get(monster);
            String role;
            if(input.equalsIgnoreCase("random")){
                var parsed=EnemyQaSpawnRequest.parse("Larva_Void",context.get(family),context.get(era),List.of());
                var rarity=parsed.rarity();
                var mode=parsed.era();
                var choices=concreteRoles().stream()
                        .filter(id->bindings.qaRole(id).map(binding->supportsAutomaticPlan(binding,rarity,mode)).orElse(false))
                        .filter(id->{try{npc.validateSpawnableRole(id);return true;}catch(RuntimeException ignored){return false;}})
                        .sorted().toList();
                if(choices.isEmpty())throw new IllegalArgumentException("No catalog QA roles are spawnable on this server");
                role=choices.get(java.util.concurrent.ThreadLocalRandom.current().nextInt(choices.size()));
            }else role=concreteRoles().stream().filter(name->name.equalsIgnoreCase(input)).findFirst()
                    .orElseThrow(()->{
                        var matches=concreteRoles().stream().filter(name->name.regionMatches(true,0,input,0,input.length()))
                                .limit(8).toList();
                        return new IllegalArgumentException(matches.isEmpty()?"Unknown concrete monster role: "+input
                                :"Use a concrete role, e.g. "+String.join(", ",matches));
                    });
            npc.validateSpawnableRole(role);
            var request=EnemyQaSpawnRequest.parse(role,context.get(family),context.get(era),
                    QaSpawnAliasSyntax.positionalAliases(context.getInputString(),input,context.get(family),context.get(era)));
            request.validateExplicitCount(EnemyBalance.canonical());
            var transform=store.getComponent(ref,TransformComponent.getComponentType());
            if(transform==null)throw new IllegalStateException("Player position unavailable.");
            var birth=owner.get();
            if(birth==null)throw new IllegalStateException("Master Enemies birth owner unavailable.");
            com.hypixel.hytale.logger.HytaleLogger.getLogger().atInfo().log(
                    "RPG_ENEMY_QA_SPAWN_REQUEST role=%s family=%s era=%s affixes=%s world=%s",
                    role,request.rarity(),request.era(),request.affixIds(),world.getName());
            birth.qaSpawn(store,world,transform.getPosition(),transform.getRotation(),request)
                    .whenComplete((result,error)->{
                        if(error!=null){
                            String reason=QaSpawnFeedback.diagnostic(error);
                            com.hypixel.hytale.logger.HytaleLogger.getLogger().atWarning().log(
                                    "RPG_ENEMY_QA_SPAWN_REJECTED role=%s affixes=%s reason=%s",role,request.affixIds(),reason);
                            context.sendMessage(Message.raw(QaSpawnFeedback.failure(error,role,request.era().name())));
                        }else{
                            com.hypixel.hytale.logger.HytaleLogger.getLogger().atInfo().log(
                                    "RPG_ENEMY_QA_SPAWN_ACCEPTED role=%s encounter=%s affixes=%s",
                                    role,result.encounter(),result.affixIds());
                            context.sendMessage(Message.raw(QaSpawnFeedback.success(role,result.affixIds())));
                        }
                    });
        }catch(RuntimeException failure){
            String reason=QaSpawnFeedback.diagnostic(failure);
            com.hypixel.hytale.logger.HytaleLogger.getLogger().atWarning().log(
                    "RPG_ENEMY_QA_SPAWN_REJECTED reason=%s",reason);
            context.sendMessage(Message.raw(QaSpawnFeedback.failure(failure,context.get(monster),context.get(era))));
        }
    }
}
