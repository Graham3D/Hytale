package com.inigmasgames.hytalerpg.commands;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.GameMode;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.OptionalArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractCommandCollection;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.modules.entity.damage.DamageCause;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.util.TargetUtil;
import com.inigmasgames.hytalerpg.difficulty.DifficultyRuntime;
import com.inigmasgames.hytalerpg.enemies.EnemyAffixRegistry;
import com.inigmasgames.hytalerpg.enemies.EnemyDisplayDto;
import com.inigmasgames.hytalerpg.enemies.EnemyNativeBindings;
import com.inigmasgames.hytalerpg.enemies.EnemyWorldAdmission;
import com.inigmasgames.hytalerpg.enemies.MonsterVisualProfile;
import com.inigmasgames.hytalerpg.execution.hytale.HytaleDifficultyCombat;
import com.inigmasgames.hytalerpg.execution.hytale.NativeEnemyArmor;
import com.inigmasgames.hytalerpg.execution.support.FiniteSupportEffects;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.function.BooleanSupplier;

/** Public, read-only view of the currently installed Master Enemies authoring and gate. */
public final class RpgEnemiesCommand extends AbstractCommandCollection {
    public static final String AUTHOR_PERMISSION="inigmasgames.rpg.enemies.author";
    private final EnemyAffixRegistry affixes;
    private final EnemyNativeBindings bindings;
    private final FiniteSupportEffects effects;
    public RpgEnemiesCommand(DifficultyRuntime difficulty,EnemyAffixRegistry affixes,
            EnemyNativeBindings bindings,EnemyWorldAdmission admission,BooleanSupplier nativePatchReady,
            HytaleDifficultyCombat combat,FiniteSupportEffects effects){
        super("enemies","Inspect Master Enemies authoring and availability.");
        Objects.requireNonNull(difficulty);this.affixes=Objects.requireNonNull(affixes);
        this.bindings=Objects.requireNonNull(bindings);Objects.requireNonNull(admission);Objects.requireNonNull(nativePatchReady);
        Objects.requireNonNull(combat);this.effects=Objects.requireNonNull(effects);
        addSubCommand(new AbstractPlayerCommand("status","Show the current Master Enemies registry and production gate."){
            {setPermissionGroup(GameMode.Adventure);}
            @Override protected void execute(CommandContext context,Store<EntityStore> store,Ref<EntityStore> ref,
                    PlayerRef player,World world){
                try{
                    var mode=difficulty.currentWorld(player.getWorldUuid()).difficulty();
                    long enabled=RpgEnemiesCommand.this.bindings.roles().stream()
                            .filter(EnemyNativeBindings.Role::productionPromotionEnabled).count();
                    boolean patched=nativePatchReady.getAsBoolean();
                    boolean worldAdmitted=admission.admits(player.getWorldUuid());
                    boolean ready=enabled>0&&patched&&worldAdmitted;
                    context.sendMessage(Message.raw("Master Enemies: "+(ready?"enabled":"disabled")
                            +"; mode="+mode+"; affixRegistry="+RpgEnemiesCommand.this.affixes.revision()
                            +"; nativeBinding="+RpgEnemiesCommand.this.bindings.revision()
                            +"; enabledRoles="+enabled+"; nativeHooks="+(patched?"ready":"unavailable")
                            +"; worldAdmission="+(worldAdmitted?"open":"closed")));
                }catch(RuntimeException failure){context.sendMessage(Message.raw("Master Enemies status unavailable: "+failure.getMessage()));}
            }
        });
        addSubCommand(new AbstractPlayerCommand("affixes","List the 27 authored Master Enemies affixes."){
            {setPermissionGroup(GameMode.Adventure);}
            @Override protected void execute(CommandContext context,Store<EntityStore> store,Ref<EntityStore> ref,
                    PlayerRef player,World world){
                RpgEnemiesCommand.this.affixes.definitions().stream().sorted(Comparator.comparing(EnemyAffixRegistry.Definition::id))
                        .forEach(card->context.sendMessage(Message.raw(card.id()+" "+card.displayName()+": "+summary(card.operator()))));
            }
        });
        addSubCommand(new AbstractPlayerCommand("inspect","Inspect a targeted Master Enemy within 24 m; operators may specify a loaded entity UUID."){
            final OptionalArg<String> entityId=withOptionalArg("entityUuid","Loaded native entity UUID (operator only)",ArgTypes.STRING);
            {setPermissionGroup(GameMode.Adventure);}
            @Override protected void execute(CommandContext context,Store<EntityStore> store,Ref<EntityStore> ref,
                    PlayerRef player,World world){
                try{
                    boolean operator=context.sender().hasPermission(AUTHOR_PERMISSION);
                    Ref<EntityStore> target;
                    if(context.provided(entityId)){
                        if(!operator){context.sendMessage(Message.raw("Master Enemies inspect: operator permission required for UUID lookup."));return;}
                        UUID requested;
                        try{requested=UUID.fromString(context.get(entityId));}
                        catch(IllegalArgumentException invalid){
                            context.sendMessage(Message.raw("Master Enemies inspect: invalid entity UUID."));return;
                        }
                        target=world.getEntityRef(requested);
                    }else target=TargetUtil.getTargetEntity(ref,24.0f,store);
                    if(target==null||!target.isValid()||target.getStore()!=store){
                        context.sendMessage(Message.raw("Master Enemies inspect: no loaded target."));return;
                    }
                    var targetId=store.getComponent(target,UUIDComponent.getComponentType());
                    if(targetId==null){context.sendMessage(Message.raw("Master Enemies inspect: target has no native identity."));return;}
                    var worldId=player.getWorldUuid();var nativeId=targetId.getUuid();
                    context.sendMessage(Message.raw("Native presentation: entity="+nativeId+" "+combat.names().inspect(worldId,nativeId)
                            +" nativeText="+java.util.Optional.ofNullable(store.getComponent(target,com.hypixel.hytale.server.core.entity.nameplate.Nameplate.getComponentType())).map(com.hypixel.hytale.server.core.entity.nameplate.Nameplate::getText).orElse("")
                            +" profile="+combat.snapshot(worldId,nativeId).map(s->s.roleId()+"/level="+s.level()).orElse("UNPROFILED")
                            +" staged="+(store.getComponent(target,com.inigmasgames.hytalerpg.execution.hytale.EnemyStaging.getComponentType())!=null)));
                    var state=combat.enemyState(worldId,nativeId).orElse(null);
                    var display=combat.enemyDisplay(worldId,nativeId).orElse(null);
                    var pack=combat.enemyPack(worldId,nativeId).orElse(null);
                    if(state==null||display==null||pack==null){
                        context.sendMessage(Message.raw("Master Enemies inspect: target has no published Master Enemies state."));return;
                    }
                    var descriptor=state.descriptor();
                    if(!descriptor.entityId().equals(nativeId)||!display.logicalActorId().equals(descriptor.logicalActorId())
                            ||display.generation()!=descriptor.encounterGeneration()||!pack.contains(descriptor.logicalActorId())){
                        context.sendMessage(Message.raw("Master Enemies inspect: target state is being rebound; retry after publication."));return;
                    }
                    if(!context.provided(entityId)){
                        var origin=store.getComponent(ref,TransformComponent.getComponentType());
                        var position=store.getComponent(target,TransformComponent.getComponentType());
                        if(origin==null||position==null||position.getPosition().distanceSquared(
                                origin.getPosition().x(),origin.getPosition().y(),origin.getPosition().z())>24*24){
                            context.sendMessage(Message.raw("Master Enemies inspect: target is outside 24 m."));return;
                        }
                    }
                    context.sendMessage(Message.raw(display.name()+" / "+nativeId+": "+display.rarityLabel()+" "
                            +display.packRoleLabel()+"; level "+display.combatLevel()+"; rank "+descriptor.encounterRank()
                            +"; guards "+(display.remainingGuardCount()==null?"n/a":display.remainingGuardCount())
                            +"; pack "+pack.state()));
                    context.sendMessage(Message.raw("Tags: "+String.join(", ",display.orderedTags().stream()
                            .map(EnemyDisplayDto.EnemyTag::fallbackText).toList())));
                    var visual=MonsterVisualProfile.from(descriptor);
                    context.sendMessage(Message.raw("Rarity Visual: "+descriptor.enemyRarity()));
                    context.sendMessage(Message.raw("Skin Tint: "+(visual.skinTint()==null?"NONE":visual.skinTint())));
                    context.sendMessage(Message.raw("Weapon Visual Owner: "+MonsterVisualProfile.display(visual.weaponVisualOwner())));
                    context.sendMessage(Message.raw("Armor Visual Owner: "+MonsterVisualProfile.display(visual.armorVisualOwner())));
                    for(var affix:descriptor.ownAffixes()){
                        var card=RpgEnemiesCommand.this.affixes.require(affix.affixId());
                        String detail=card.operator()==EnemyAffixRegistry.Operator.EXTRA_FAST
                                &&affix.value("recoveryRateIncrease")==0
                                ?"faster movement":summary(card.operator());
                        context.sendMessage(Message.raw(card.displayName()+" ("+card.id()+"): "+detail));
                    }
                    for(var affix:descriptor.inheritedAffixes()){
                        var card=RpgEnemiesCommand.this.affixes.require(affix.affixId());
                        context.sendMessage(Message.raw("Inherited "+card.displayName()+" ("+card.id()+"): "
                                +inheritedSummary(card.operator())));
                    }
                    if(operator){
                        context.sendMessage(Message.raw("Source: role="+descriptor.canonicalRoleId()+"; native="+descriptor.nativeRoleId()
                                +"; encounter="+descriptor.encounterId()+"; generation="+descriptor.encounterGeneration()
                                +"; descriptorRev="+descriptor.descriptorRevision()+"; displayRev="+display.stateRevision()));
                        context.sendMessage(Message.raw("Providers: Health x"+state.providers().rarityHealthFactor()
                                +"; direct x"+state.providers().rarityDirectFactor()+"; movement x"+state.providers().movementMultiplier()
                                +"; recovery x"+state.providers().recoveryRateMultiplier()
                                +"; Stone Skin rating="+state.providers().stoneSkinDefenseRating()
                                +"; elemental resistance adds="+state.providers().resistanceAdds()
                                +"; immunity="+descriptor.activeImmuneChannels()));
                        var physical=combat.physicalDefenseForInspection(store,target,RpgEnemiesCommand.this.effects);
                        if(physical!=null){
                            for(var cause:new DamageCause[]{DamageCause.PHYSICAL,DamageCause.PROJECTILE}){
                                if(cause==null){context.sendMessage(Message.raw("Physical/Projectile defense: native cause unavailable."));continue;}
                                try{
                                    var defense=NativeEnemyArmor.inspectPhysical(store,target,cause,physical);
                                    context.sendMessage(Message.raw(cause.getId()+" defense: "+(defense.immune()?"immune"
                                            :"Dtotal="+defense.totalDefenseRating()+"; Deffective="+defense.effectiveDefenseRating()
                                            +"; managed mitigation="+defense.managedProtection()
                                            +"; final native multiplier protection="+defense.packetMultiplierProtection()
                                            +"; native flat reduction="+defense.nativeFlatReduction())));
                                }catch(RuntimeException unavailable){
                                    context.sendMessage(Message.raw(cause.getId()+" defense: unavailable ("+unavailable.getMessage()+")."));
                                }
                            }
                        }
                        var elemental=combat.elementalDefenseForInspection(store,target);
                        if(elemental!=null){
                            for(var nativeCause:List.of("Fire","Ice","Lightning","Wind","Earth","RPG_Void")){
                                var cause=DamageCause.getAssetMap().getAsset(nativeCause);
                                if(cause==null){
                                    context.sendMessage(Message.raw("Elemental defense "+nativeCause+": native cause unavailable."));
                                    continue;
                                }
                                try{
                                    var defense=NativeEnemyArmor.inspectElemental(store,target,cause,elemental);
                                    context.sendMessage(Message.raw("Elemental defense "+defense.channel()+": "
                                            +(defense.immune()?"immune":"packet multiplier protection="
                                            +defense.packetMultiplierProtection()+"; native flat reduction="
                                            +defense.nativeFlatReduction())));
                                }catch(RuntimeException unavailable){
                                    context.sendMessage(Message.raw("Elemental defense "+nativeCause+": unavailable ("
                                            +unavailable.getMessage()+")."));
                                }
                            }
                        }
                        context.sendMessage(Message.raw("Reward provenance: "+descriptor.spawnOrigin()
                                +"; loot source="+descriptor.lootSourceId()+"; pack="+pack.packId()));
                    }
                }catch(RuntimeException failure){
                    context.sendMessage(Message.raw("Master Enemies inspect unavailable: "+failure.getMessage()));
                }
            }
        });
    }
    private static String summary(EnemyAffixRegistry.Operator operator){return switch(operator){
        case EXTRA_FAST->"faster movement and attack recovery";
        case EXTRA_STRONG->"stronger direct Physical hits";
        case MAGIC_RESISTANT->"more elemental resistance";
        case STONE_SKIN->"added Physical Defense rating";
        case FIRE_ENCHANTED->"Fire damage on direct hits and Fire resistance";
        case COLD_ENCHANTED->"Water damage on direct hits and Water resistance";
        case LIGHTNING_ENCHANTED->"Lightning damage on direct hits and Lightning resistance";
        case POISON_ENCHANTED->"Earth damage on direct hits and Earth resistance";
        case WIND_ENCHANTED->"Wind damage on direct hits and Wind resistance";
        case EARTH_ENCHANTED->"Earth damage on direct hits and Earth resistance";
        case VOID_ENCHANTED->"Void damage on direct hits and Void resistance";
        case SPECTRAL_HIT->"extra direct damage in a selected elemental channel";
        case MANA_BURN->"drains spendable Mana after direct Health damage";
        case CURSED->"four-second outgoing damage reduction on hit";
        case KNOCKBACK->"stronger existing native knockback on direct hits";
        case VAMPIRIC->"heals from direct Health damage dealt";
        case UNWAVERING->"immune to Stun and Stagger";
        case UNSTOPPABLE->"ignores movement slow";
        case FRENZIED->"periodic damage, speed and recovery surge";
        case AVENGER->"grows stronger as initial minions fall";
        case EMPOWERED_MINIONS->"initial minions gain Health, direct damage and speed";
        case HORDE->"more initial pack members";
        case AURA_ENCHANTED->"one selected pack aura: Might, Ward or Haste";
        case PACKBOUND->"leader protected until bound guards fall";
        case ARMOR_BREAKER->"four-second Physical/Projectile vulnerability on hit";
        case REFLECTIVE->"returns live direct Health loss as Physical damage";
        case BULWARK->"one finite intrinsic shield";
    };}
    private static String inheritedSummary(EnemyAffixRegistry.Operator operator){return switch(operator){
        case EXTRA_FAST->"movement speed only";
        case EXTRA_STRONG->"direct Physical damage only";
        case FIRE_ENCHANTED,COLD_ENCHANTED,LIGHTNING_ENCHANTED,POISON_ENCHANTED,WIND_ENCHANTED,EARTH_ENCHANTED,
                VOID_ENCHANTED,SPECTRAL_HIT->"extra direct elemental power only";
        case EMPOWERED_MINIONS->"Max Health, direct damage and movement";
        case AURA_ENCHANTED->"current pack aura membership";
        default->throw new IllegalStateException("ENEMY_UNSUPPORTED_INHERITED_AFFIX:"+operator);
    };}
}
