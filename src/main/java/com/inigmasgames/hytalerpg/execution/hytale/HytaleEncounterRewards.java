package com.inigmasgames.hytalerpg.execution.hytale;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.component.*;
import com.hypixel.hytale.component.dependency.*;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.RefSystem;
import com.hypixel.hytale.component.system.tick.TickingSystem;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.modules.entity.component.*;
import com.hypixel.hytale.server.core.modules.entity.damage.*;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatMap;
import com.hypixel.hytale.server.core.modules.entitystats.asset.DefaultEntityStatTypes;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import com.hypixel.hytale.server.worldgen.chunk.ChunkGenerator;
import com.inigmasgames.hytalerpg.combat.hytale.*;
import com.inigmasgames.hytalerpg.diagnostics.*;
import com.inigmasgames.hytalerpg.execution.math.Vec3;
import com.inigmasgames.hytalerpg.progress.*;
import java.util.*;

/** Exact 0.7 legacy natural spawn -> real post-Apply contribution -> native DeathComponent -> durable awards. */
public final class HytaleEncounterRewards implements AutoCloseable {
    private final EnemyRewardRegistry registry=EnemyRewardRegistry.load();
    private com.inigmasgames.hytalerpg.difficulty.EncounterProfileResolver difficulty;
    private HytaleDifficultyCombat difficultyCombat;
    public void configureCombat(HytaleDifficultyCombat combat){difficultyCombat=Objects.requireNonNull(combat);}
    private TransientQaEncounters qaEncounters;
    public void configureQaEncounters(TransientQaEncounters owner){qaEncounters=Objects.requireNonNull(owner);}
    private com.inigmasgames.hytalerpg.enemies.EnemyWorldAdmission enemyAdmission;
    public void configureEnemyAdmission(com.inigmasgames.hytalerpg.enemies.EnemyWorldAdmission admission){enemyAdmission=Objects.requireNonNull(admission);}
    private com.inigmasgames.hytalerpg.difficulty.GolemEncounterBinding golemBindings;
    private final com.inigmasgames.hytalerpg.difficulty.GolemMilestones golemCatalog=com.inigmasgames.hytalerpg.difficulty.GolemMilestones.load();
    private java.util.function.BiConsumer<UUID,com.inigmasgames.hytalerpg.difficulty.DifficultyId> unlocked=(player,mode)->{};
    public void configureUnlockNotification(java.util.function.BiConsumer<UUID,com.inigmasgames.hytalerpg.difficulty.DifficultyId> callback){unlocked=Objects.requireNonNull(callback);}
    public void configureGolems(com.inigmasgames.hytalerpg.difficulty.WorldDifficultyRegistry worlds){golemBindings=new com.inigmasgames.hytalerpg.difficulty.GolemEncounterBinding(worlds);}
    public void configureDifficulty(com.inigmasgames.hytalerpg.difficulty.EncounterProfileResolver resolver){
        if(difficulty!=null)throw new IllegalStateException("DIFFICULTY_ALREADY_CONFIGURED");
        difficulty=Objects.requireNonNull(resolver);
    }
    private final PersistentEncounterRuntime runtime;
    private final RpgLoadoutService loadouts;
    private final RpgSkillTracer trace;
    private final LearningSources learning=LearningSources.load(com.inigmasgames.hytalerpg.content.RpgCatalog.loadCanonical());
    private final com.inigmasgames.hytalerpg.combat.RpgCombatKernel kernel;
    private final HostileInjuryLedger injuries=new HostileInjuryLedger();
    private final DurableEncounterEffects durableEffects=new DurableEncounterEffects();
    private record ExclusionKey(UUID world,UUID enemy){}
    private final Map<ExclusionKey,java.util.concurrent.CompletionStage<Void>> exclusionTickets=new HashMap<>();
    /** World-thread guard while a pre-root native roster sheds its staging components. */
    private final Set<ExclusionKey> releasingPreRoot=new HashSet<>();
    private final ProgressionHandoffMetrics progressionLatency=new ProgressionHandoffMetrics();
    private final Map<String,Long> deathOrigins=Collections.synchronizedMap(new LinkedHashMap<>());
    public void invalidateHealthCredit(UUID world,UUID recipient){injuries.invalidate(world,recipient);}
    public void forgetPlayer(UUID actor){injuries.forget(actor);}
    private volatile java.util.function.BooleanSupplier active=()->true;
    private volatile boolean closed;
    public void configureActive(java.util.function.BooleanSupplier gate){active=Objects.requireNonNull(gate);}
    private boolean running(){return !closed&&active.getAsBoolean();}
    private boolean failureLogged;
    private long nextDeliveryNanos;
    private final java.util.function.LongSupplier nanoTime;
    private Runnable ownerMaintenance=()->{};
    public void configureOwnerMaintenance(Runnable maintenance){ownerMaintenance=Objects.requireNonNull(maintenance);}
    private volatile PartyMembershipProvider parties=PartyMembershipProvider.UNAVAILABLE;
    private com.inigmasgames.hytalerpg.gear.GearLootService gearLoot;
    private com.inigmasgames.hytalerpg.gear.HytaleGearEquipment gearEquipment;
    private com.inigmasgames.hytalerpg.combat.hytale.GearCreditedDeathRecovery recovery;
    @FunctionalInterface public interface SignatureKillCallback {void accepted(UUID world,UUID enemy);}
    private volatile SignatureKillCallback signatureKill;
    public void configureSignatureKill(SignatureKillCallback callback){signatureKill=Objects.requireNonNull(callback);}
    public void configureRecovery(com.inigmasgames.hytalerpg.combat.hytale.GearCreditedDeathRecovery callback){
        recovery=Objects.requireNonNull(callback);
    }
    record RecoveryCapture(com.inigmasgames.hytalerpg.gear.GearEffectSnapshot snapshot,double maximum){}
    static void dispatchRecovery(List<EncounterContributions.Share> shares,UUID world,String eventId,
                                 Map<UUID,RecoveryCapture> captured,
                                 java.util.function.Consumer<com.inigmasgames.hytalerpg.combat.hytale.GearCreditedDeathRecovery.Credit> sink){
        for(var share:shares){
            var value=captured.get(share.player());
            if(value!=null)sink.accept(new com.inigmasgames.hytalerpg.combat.hytale.GearCreditedDeathRecovery.Credit(
                    share.player(),world.toString(),eventId,value.snapshot(),value.maximum()));
        }
    }
    public void configureGearLoot(com.inigmasgames.hytalerpg.gear.GearLootService service,com.inigmasgames.hytalerpg.gear.HytaleGearEquipment equipment){gearLoot=Objects.requireNonNull(service);gearEquipment=Objects.requireNonNull(equipment);}
    /** Durable reward worker callback; the native world projection has its own acknowledgement. */
    public void lootDelivered(EncounterContributions.DeathPlan plan,com.inigmasgames.hytalerpg.gear.GearLootService.Loot loot,boolean finalPick){
        var spawn=plan.spawn();String correlation=spawn.enemy().toString();
        var details=new LinkedHashMap<String,Object>();details.put("eventId",loot.source()==null?spawn.eventId():loot.source().eventId());details.put("runtimeRole",spawn.roleId());
        details.put("canonicalRole",spawn.combatIdentity());details.put("rewardDecision",loot.state());details.put("reason",loot.reason());
        details.put("eligibleContributors",plan.shares().stream().map(s->s.player().toString()).toList());
        if(loot.allocation()!=null)details.put("player",loot.allocation().sponsor());
        emit(null,RpgTraceEventType.GEAR_ROLL,correlation,details);
        if(loot.result()!=null&&loot.result().item()!=null){var item=loot.result().item();
            emit(loot.allocation().sponsor(),RpgTraceEventType.GEAR_GENERATED,correlation,Map.of("eventId",loot.source().eventId(),"itemId",item.identity(),
                    "baseId",item.baseId(),"rarity",item.rarity().name(),"affixes",item.affixes().size(),"deliveryState",loot.state()));}
        if(finalPick)emit(null,RpgTraceEventType.ENCOUNTER_CLOSED,correlation,Map.of("eventId",spawn.eventId(),"decision","EQUIPMENT_PICKS_FINISHED","reason",loot.reason()));
    }
    public void gearProjected(com.inigmasgames.hytalerpg.gear.GearLootService.Loot loot){
        var item=loot.result().item();
        emit(loot.allocation().sponsor(),RpgTraceEventType.REWARD_DELIVERED,loot.source().enemy().toString(),Map.of(
                "eventId",loot.source().eventId(),"itemId",item.identity(),"baseId",item.baseId(),"rarity",item.rarity().name(),
                "state","NATIVE_WORLD_ITEM_PROJECTED","delivery","WORLD"));
    }
    private com.inigmasgames.hytalerpg.gear.GearClaims.Policy gearPolicy(UUID world,UUID actor){
        if(gearLoot==null)return null;
        try{return parties.lootPolicy(world,actor);}catch(IllegalStateException unavailable){return null;} // Party items blocked; XP remains independent.
    }
    public HytaleEncounterRewards(FileEncounterStore store,RpgLoadoutService loadouts,RpgSkillTracer trace,com.inigmasgames.hytalerpg.combat.RpgCombatKernel kernel){
        this(store,loadouts,trace,kernel,System::nanoTime);
    }
    public HytaleEncounterRewards(FileEncounterStore store,RpgLoadoutService loadouts,RpgSkillTracer trace,com.inigmasgames.hytalerpg.combat.RpgCombatKernel kernel,java.util.function.LongSupplier nanoTime){
        this.nanoTime=Objects.requireNonNull(nanoTime);
        this.runtime=new PersistentEncounterRuntime(store,(player,reward,opportunity)->{
            var priorUnlocks=reward.milestone()==null?Set.<com.inigmasgames.hytalerpg.difficulty.DifficultyId>of():loadouts.getPresentationView(player).state().difficulty.unlocks();
            if(opportunity==null)loadouts.awardEarned(player,reward);
            else loadouts.awardGenerated(player,reward.eventId(),before->opportunity.decide(reward,before,Math::random));
            if(reward.milestone()!=null)for(var mode:loadouts.getPresentationView(player).state().difficulty.unlocks())if(!priorUnlocks.contains(mode))
                try{unlocked.accept(player,mode);}catch(RuntimeException notificationFailure){/* Earned persistence already succeeded; presentation cannot roll it back. */}
            progressionLatency.committed(player,reward.eventId(),deathOrigins.getOrDefault(reward.eventId(),-1L));
        });this.loadouts=loadouts;this.trace=trace;this.kernel=Objects.requireNonNull(kernel);
    }
    public int verifiedLearningBindings(){return learning.verifiedBindings();}
    public synchronized java.util.concurrent.CompletionStage<Void> invalidateConverted(UUID world,UUID enemy){
        if(difficultyCombat!=null)difficultyCombat.detach(world,enemy);
        var key=new ExclusionKey(world,enemy);var existing=exclusionTickets.get(key);if(existing!=null)return existing;
        if(exclusionTickets.size()>=EncounterContributions.MAX_ENCOUNTERS)throw new FileEncounterStore.CapacityRejected();
        try(var lease=durableEffects.reserve()){
            var prerequisite=runtime.excludeNow(world,enemy);var result=new java.util.concurrent.CompletableFuture<Void>();
            var task=lease.submit(prerequisite,ignored->{try{runtime.persistExclusion(world,enemy,prerequisite);result.complete(null);}catch(RuntimeException error){result.completeExceptionally(error);throw error;}});
            task.whenComplete((ignored,error)->{if(error!=null)result.completeExceptionally(error);});
            var receipt=result.minimalCompletionStage();exclusionTickets.put(key,receipt);return receipt;
        }
    }
    @Override public void close(){closed=true;if(qaEncounters!=null)qaEncounters.clear();try{durableEffects.close();}finally{runtime.close();}}
    public void configurePartyProvider(PartyMembershipProvider provider){parties=Objects.requireNonNull(provider);}
    public String partyAvailability(){return parties.availability();}
    public java.util.concurrent.CompletionStage<Boolean> attachObserved(UUID world,UUID enemy,String role,Optional<EnemyRewardRegistry.Spawn> spawn){return runtime.attachNative(world,enemy,role,spawn);}
    public java.util.concurrent.CompletionStage<com.inigmasgames.hytalerpg.enemies.EnemyBirthPlan> reserveEnemyBirth(com.inigmasgames.hytalerpg.enemies.EnemyBirthPlan plan){return runtime.reserveEnemyBirth(plan);}
    public java.util.concurrent.CompletionStage<com.inigmasgames.hytalerpg.enemies.EnemyBirthPlan> reserveEnemyBirthRoot(com.inigmasgames.hytalerpg.enemies.EnemyBirthRoot root){return runtime.reserveEnemyBirthRoot(root);}
    public java.util.concurrent.CompletionStage<Optional<com.inigmasgames.hytalerpg.enemies.EnemyBirthRoot>> enemyBirthRoot(UUID world,UUID encounter){return runtime.enemyBirthRoot(world,encounter);}
    public java.util.concurrent.CompletionStage<Optional<com.inigmasgames.hytalerpg.enemies.EnemyBirthCompensation>> enemyBirthCompensation(UUID world,UUID encounter){return runtime.enemyBirthCompensation(world,encounter);}
    public java.util.concurrent.CompletionStage<Optional<com.inigmasgames.hytalerpg.enemies.EnemyBirthPlan>> enemyBirth(UUID world,UUID encounter){return runtime.enemyBirth(world,encounter);}
    public java.util.concurrent.CompletionStage<List<com.inigmasgames.hytalerpg.enemies.EnemyBirthPlan>> enemyBirths(UUID world){return runtime.enemyBirths(world);}
    public java.util.concurrent.CompletionStage<FileEncounterStore.EnemyWorldInventory> recoverEnemyWorld(UUID world){return runtime.recoverEnemyWorld(world);}
    public java.util.concurrent.CompletionStage<FileEncounterStore.EnemyActionRootBlock> reserveEnemyActionRoots(
            UUID world,UUID encounter,long generation,UUID logicalActor,int count){
        return runtime.reserveEnemyActionRoots(world,encounter,generation,logicalActor,count);
    }
    public java.util.concurrent.CompletionStage<FileEncounterStore.PlayerHitRootBlock> reservePlayerHitRoots(
            UUID world,UUID player,int count){return runtime.reservePlayerHitRoots(world,player,count);}
    public java.util.concurrent.CompletionStage<String> bindPlayerActionRoot(UUID world,UUID player,String action){
        return runtime.bindPlayerActionRoot(world,player,action);
    }
    public java.util.concurrent.CompletionStage<Optional<com.inigmasgames.hytalerpg.enemies.EnemyPackRecord>> enemyPack(UUID world,UUID pack){return runtime.enemyPack(world,pack);}
    public java.util.concurrent.CompletionStage<List<com.inigmasgames.hytalerpg.enemies.EnemyPackRecord>> enemyPacks(UUID world){return runtime.enemyPacks(world);}
    public java.util.concurrent.CompletionStage<List<com.inigmasgames.hytalerpg.enemies.SuperUniqueAnchor>> enemyAnchors(UUID world){return runtime.enemyAnchors(world);}
    public java.util.concurrent.CompletionStage<com.inigmasgames.hytalerpg.enemies.EnemyPackRecord> transitionEnemyPack(UUID world,UUID pack,
            java.util.function.UnaryOperator<com.inigmasgames.hytalerpg.enemies.EnemyPackRecord> transition){return runtime.transitionEnemyPack(world,pack,transition);}
    public java.util.concurrent.CompletionStage<com.inigmasgames.hytalerpg.enemies.EnemyPackRecord> reconcileEnemyPackDefeats(UUID world,UUID pack){return runtime.reconcileEnemyPackDefeats(world,pack);}
    /** Called only after the complete birth root and its indexes have been durably reserved. */
    public java.util.concurrent.CompletionStage<Void> attachStaged(Store<EntityStore> store,
            com.inigmasgames.hytalerpg.enemies.EnemyDescriptor descriptor,
            EnemyRewardRegistry.Spawn nativeSpawn,com.inigmasgames.hytalerpg.enemies.EnemyAffixSnapshot providers){
        Objects.requireNonNull(descriptor);Objects.requireNonNull(nativeSpawn);Objects.requireNonNull(providers);
        if(!store.isInThread()||!world(store).equals(descriptor.worldId())||difficultyCombat==null)
            throw new IllegalStateException("ENEMY_STAGED_ATTACH_OWNER");
        var ref=store.getExternalData().getRefFromUUID(descriptor.entityId());
        var marker=ref==null||!ref.isValid()?null:store.getComponent(ref,EnemyStaging.getComponentType());
        if(marker==null||!marker.state().world().equals(descriptor.worldId())
                ||!marker.state().encounter().equals(descriptor.encounterId())
                ||marker.state().generation()!=descriptor.encounterGeneration()
                ||!marker.state().entity().equals(descriptor.entityId())
                ||!nativeSpawn.world().equals(descriptor.worldId())||!nativeSpawn.enemy().equals(descriptor.entityId())
                ||!nativeSpawn.roleId().equals(descriptor.nativeRoleId())||nativeSpawn.level()!=descriptor.combatLevel()
                ||nativeSpawn.combat()==null||nativeSpawn.combat().difficulty()!=descriptor.difficulty()
                ||nativeSpawn.enemyRewards()!=null&&!nativeSpawn.enemyRewards().equals(descriptor.immutableRewardContext()))
            throw new IllegalStateException("ENEMY_STAGED_ATTACH_BINDING");
        var enriched=new EnemyRewardRegistry.Spawn(nativeSpawn.world(),nativeSpawn.enemy(),nativeSpawn.roleId(),
                nativeSpawn.combatIdentity(),nativeSpawn.biomeKey(),nativeSpawn.level(),nativeSpawn.rank(),nativeSpawn.rarity(),
                nativeSpawn.registryProfile(),nativeSpawn.spawnedAtMillis(),nativeSpawn.milestone(),nativeSpawn.combat(),
                descriptor.immutableRewardContext());
        var ticket=difficultyCombat.begin(descriptor.worldId(),descriptor.entityId());
        var result=new java.util.concurrent.CompletableFuture<Void>();
        var nativeWorld=store.getExternalData().getWorld();
        // A root file alone is insufficient after an interrupted index write. Replaying the
        // idempotent reservation repairs every descriptor/pack index before actor attachment.
        try{enemyBirth(descriptor.worldId(),descriptor.encounterId()).thenCompose(existing->{
            var birth=existing.orElseThrow(()->new IllegalStateException("ENEMY_STAGED_BIRTH_NOT_SEALED"));
            if(birth.generation()!=descriptor.encounterGeneration()||!birth.actors().contains(descriptor))
                throw new IllegalStateException("ENEMY_STAGED_BIRTH_ACTOR_MISMATCH");
            return reserveEnemyBirth(birth);
        }).thenCompose(ignored->attachObserved(descriptor.worldId(),descriptor.entityId(),descriptor.nativeRoleId(),Optional.of(enriched)))
                .whenComplete((attached,error)->{
                    if(error!=null||!Boolean.TRUE.equals(attached)){
                        difficultyCombat.detach(descriptor.worldId(),descriptor.entityId());
                        result.completeExceptionally(error!=null?error:new IllegalStateException("ENEMY_STAGED_DURABLE_ATTACH_REJECTED"));return;
                    }
                    try{nativeWorld.execute(()->{
                        try{
                            var current=nativeWorld.getEntityStore().getStore();
                            var actor=nativeWorld.getEntityRef(descriptor.entityId());
                            var staged=actor==null||!actor.isValid()?null:current.getComponent(actor,EnemyStaging.getComponentType());
                            if(staged==null||!staged.state().equals(marker.state()))throw new IllegalStateException("ENEMY_STAGED_ACTOR_CHANGED");
                            difficultyCombat.ready(current,descriptor.entityId(),ticket,Optional.of(enriched),true,descriptor,providers);
                            var projected=difficultyCombat.enemyState(descriptor.worldId(),descriptor.entityId());
                            if(projected.isEmpty()||!projected.get().descriptor().equals(descriptor))
                                throw new IllegalStateException("ENEMY_STAGED_PROJECTION_NOT_READY");
                            result.complete(null);
                        }catch(RuntimeException failure){difficultyCombat.detach(descriptor.worldId(),descriptor.entityId());result.completeExceptionally(failure);}
                    });}catch(RuntimeException closing){difficultyCombat.detach(descriptor.worldId(),descriptor.entityId());result.completeExceptionally(closing);}
                });
        }catch(RuntimeException rejected){difficultyCombat.detach(descriptor.worldId(),descriptor.entityId());result.completeExceptionally(rejected);}
        return result.minimalCompletionStage();
    }
    /** Install saved rebind identities only after every actor's durable context and projection are ready. */
    public void bindStagedNativeIdentities(Store<EntityStore> store,
            com.inigmasgames.hytalerpg.enemies.EnemyBirthPlan birth){
        if(!store.isInThread()||!world(store).equals(birth.world())||birth.pack()==null
                ||birth.pack().state()!=com.inigmasgames.hytalerpg.enemies.EnemyPackRecord.State.RESERVED)
            throw new IllegalStateException("ENEMY_BIRTH_IDENTITY_OWNER");
        var refs=new java.util.ArrayList<Ref<EntityStore>>();
        var type=com.inigmasgames.hytalerpg.enemies.EnemyActorIdentity.getComponentType();
        for(var actor:birth.actors()){
            var ref=store.getExternalData().getRefFromUUID(actor.entityId());
            var staged=ref==null||!ref.isValid()?null:store.getComponent(ref,EnemyStaging.getComponentType());
            var projected=difficultyCombat==null?Optional.<HytaleDifficultyCombat.EnemyState>empty():
                    difficultyCombat.enemyState(birth.world(),actor.entityId());
            var existing=ref==null||!ref.isValid()?null:store.getComponent(ref,type);
            if(staged==null||!staged.state().world().equals(birth.world())
                    ||!staged.state().encounter().equals(birth.encounter())
                    ||staged.state().generation()!=birth.generation()
                    ||!staged.state().entity().equals(actor.entityId())
                    ||projected.isEmpty()||!projected.get().descriptor().equals(actor)
                    ||existing!=null&&!existing.state().equals(com.inigmasgames.hytalerpg.enemies.EnemyActorIdentity.State.of(actor)))
                throw new IllegalStateException("ENEMY_BIRTH_IDENTITY_ROSTER_INCOMPLETE");
            refs.add(ref);
        }
        for(int i=0;i<birth.actors().size();i++){
            var ref=refs.get(i);if(store.getComponent(ref,type)==null)
                store.addComponent(ref,type,new com.inigmasgames.hytalerpg.enemies.EnemyActorIdentity(
                        com.inigmasgames.hytalerpg.enemies.EnemyActorIdentity.State.of(birth.actors().get(i))));
        }
    }
    /** All actor projections must acknowledge before saved identities and the sealed pack can be bound. */
    public java.util.concurrent.CompletionStage<Void> attachStagedGroup(Store<EntityStore> store,
            NativeEnemyBirthDecision.Selected selected,
            com.inigmasgames.hytalerpg.enemies.EnemyBalance balance,
            com.inigmasgames.hytalerpg.enemies.EnemyNativeBindings nativeBindings){
        Objects.requireNonNull(selected);Objects.requireNonNull(balance);
        Objects.requireNonNull(nativeBindings);
        var birth=selected.root().plan();
        if(!store.isInThread()||!world(store).equals(birth.world())||birth.pack()==null
                ||birth.pack().state()!=com.inigmasgames.hytalerpg.enemies.EnemyPackRecord.State.RESERVED
                ||selected.sources().size()!=birth.actors().size())
            throw new IllegalStateException("ENEMY_STAGED_GROUP_ATTACH_OWNER");
        var snapshots=new java.util.ArrayList<com.inigmasgames.hytalerpg.enemies.EnemyAffixSnapshot>();
        for(int i=0;i<birth.actors().size();i++){
            var actor=birth.actors().get(i);var source=selected.sources().get(i).spawn();
            var ref=store.getExternalData().getRefFromUUID(actor.entityId());
            var marker=ref==null||!ref.isValid()?null:store.getComponent(ref,EnemyStaging.getComponentType());
            if(marker==null||!marker.state().world().equals(birth.world())
                    ||!marker.state().encounter().equals(birth.encounter())
                    ||marker.state().generation()!=birth.generation()
                    ||!marker.state().entity().equals(actor.entityId())
                    ||!source.enemy().equals(actor.entityId())||!source.roleId().equals(actor.nativeRoleId()))
                throw new IllegalStateException("ENEMY_STAGED_GROUP_ATTACH_ROSTER");
            var actorRole=nativeBindings.requireActorRole(actor);
            snapshots.add(com.inigmasgames.hytalerpg.enemies.EnemyAffixSnapshot.resolve(actor,balance,birth.pack(),0,
                    actorRole.capabilities().contains(com.inigmasgames.hytalerpg.enemies.EnemyAffixRegistry.Capability.MOBILE),
                    actorRole.capabilities().contains(com.inigmasgames.hytalerpg.enemies.EnemyAffixRegistry.Capability.RECOVERY_TIMELINE)));
        }
        var tickets=new java.util.ArrayList<java.util.concurrent.CompletableFuture<Void>>();
        for(int i=0;i<birth.actors().size();i++)tickets.add(attachStaged(store,birth.actors().get(i),
                selected.sources().get(i).spawn(),snapshots.get(i)).toCompletableFuture());
        var result=new java.util.concurrent.CompletableFuture<Void>();
        var nativeWorld=store.getExternalData().getWorld();
        java.util.concurrent.CompletableFuture.allOf(tickets.toArray(java.util.concurrent.CompletableFuture[]::new))
                .whenComplete((ignored,error)->{
                    if(error!=null){result.completeExceptionally(error);return;}
                    try{nativeWorld.execute(()->{
                        try{
                            var current=nativeWorld.getEntityStore().getStore();
                            bindStagedNativeIdentities(current,birth);
                            for(var actor:birth.actors())difficultyCombat.bindEnemyPack(current,actor,birth.pack());
                            result.complete(null);
                        }catch(RuntimeException rejected){result.completeExceptionally(rejected);}
                    });}catch(RuntimeException closing){result.completeExceptionally(closing);}
                });
        return result.minimalCompletionStage();
    }
    /** LOAD rebind consumes the saved special contexts and native Health snapshots; it never creates a birth. */
    public java.util.concurrent.CompletionStage<Void> reattachStagedEnemyGroup(Store<EntityStore> store,
            com.inigmasgames.hytalerpg.enemies.EnemyBirthPlan birth,
            com.inigmasgames.hytalerpg.enemies.EnemyPackRecord pack,
            com.inigmasgames.hytalerpg.enemies.EnemyBalance balance,
            com.inigmasgames.hytalerpg.enemies.EnemyNativeBindings nativeBindings){
        return reattachStagedEnemyGroup(store,birth,pack,balance,nativeBindings,birth.activeActors(pack));
    }
    public java.util.concurrent.CompletionStage<Void> reattachStagedEnemyGroup(Store<EntityStore> store,
            com.inigmasgames.hytalerpg.enemies.EnemyBirthPlan birth,
            com.inigmasgames.hytalerpg.enemies.EnemyPackRecord pack,
            com.inigmasgames.hytalerpg.enemies.EnemyBalance balance,
            com.inigmasgames.hytalerpg.enemies.EnemyNativeBindings nativeBindings,
            List<com.inigmasgames.hytalerpg.enemies.EnemyDescriptor> requested){
        Objects.requireNonNull(nativeBindings);
        if(!store.isInThread()||!world(store).equals(birth.world())||difficultyCombat==null
                ||birth.pack()==null||!pack.packId().equals(birth.pack().packId())
                ||!pack.worldId().equals(birth.world())||!pack.encounterId().equals(birth.encounter())
                ||pack.generation()!=birth.generation()
                ||pack.state()==com.inigmasgames.hytalerpg.enemies.EnemyPackRecord.State.ABORTED
                 ||pack.state()==com.inigmasgames.hytalerpg.enemies.EnemyPackRecord.State.DEFEATED)
            throw new IllegalStateException("ENEMY_REBIND_PACK_IDENTITY");
        var actors=birth.activeSubset(pack,requested);
        if(actors.isEmpty())throw new IllegalStateException("ENEMY_REBIND_NO_LIVING_ACTORS");
        for(var actor:actors){
            var ref=store.getExternalData().getRefFromUUID(actor.entityId());
            var staged=ref==null||!ref.isValid()?null:store.getComponent(ref,EnemyStaging.getComponentType());
            var identity=ref==null||!ref.isValid()?null:store.getComponent(ref,
                    com.inigmasgames.hytalerpg.enemies.EnemyActorIdentity.getComponentType());
            var health=ref==null||!ref.isValid()?null:store.getComponent(ref,
                    com.inigmasgames.hytalerpg.difficulty.DifficultyHealthProjection.getComponentType());
            var npc=ref==null||!ref.isValid()?null:store.getComponent(ref,NPCEntity.getComponentType());
            if(staged==null||identity==null||health==null||npc==null
                    ||!staged.state().world().equals(birth.world())
                    ||!staged.state().encounter().equals(birth.encounter())
                    ||staged.state().generation()!=birth.generation()
                    ||!staged.state().entity().equals(actor.entityId())
                    ||!identity.state().equals(com.inigmasgames.hytalerpg.enemies.EnemyActorIdentity.State.of(actor))
                    ||!npc.getRoleName().equals(actor.nativeRoleId())
                    ||!nativeBindings.requireActorRole(actor).nativeRoleIds().contains(actor.nativeRoleId()))
                throw new IllegalStateException("ENEMY_REBIND_SAVED_ROSTER_INCOMPLETE");
        }
        var tickets=new java.util.ArrayList<Object>();
        try{for(var actor:actors)tickets.add(difficultyCombat.begin(birth.world(),actor.entityId()));}
        catch(RuntimeException rejected){
            for(int i=0;i<tickets.size();i++)difficultyCombat.detach(birth.world(),actors.get(i).entityId());
            throw rejected;
        }
        var loads=new java.util.ArrayList<java.util.concurrent.CompletableFuture<Boolean>>();
        for(var actor:actors){
            try{loads.add(attachObserved(birth.world(),actor.entityId(),actor.nativeRoleId(),Optional.empty())
                    .toCompletableFuture());}
            catch(RuntimeException rejected){loads.add(java.util.concurrent.CompletableFuture.failedFuture(rejected));}
        }
        var result=new java.util.concurrent.CompletableFuture<Void>();
        var nativeWorld=store.getExternalData().getWorld();
        java.util.concurrent.CompletableFuture.allOf(loads.toArray(new java.util.concurrent.CompletableFuture[0]))
                .whenComplete((ignored,error)->{
                    if(error!=null){
                        try{nativeWorld.execute(()->{
                            for(var actor:actors)difficultyCombat.detach(birth.world(),actor.entityId());
                            result.completeExceptionally(error);
                        });}catch(RuntimeException closing){error.addSuppressed(closing);result.completeExceptionally(error);}
                        return;
                    }
                    try{nativeWorld.execute(()->{
                        try{
                            var current=nativeWorld.getEntityStore().getStore();
                            var savedSpawns=new java.util.ArrayList<EnemyRewardRegistry.Spawn>();
                            var snapshots=new java.util.ArrayList<com.inigmasgames.hytalerpg.enemies.EnemyAffixSnapshot>();
                            for(int i=0;i<actors.size();i++){
                                var actor=actors.get(i);
                                if(!loads.get(i).join())throw new IllegalStateException("ENEMY_REBIND_CONTEXT_NOT_LOADED");
                                var ref=current.getExternalData().getRefFromUUID(actor.entityId());
                                var marker=ref==null||!ref.isValid()?null:current.getComponent(ref,EnemyStaging.getComponentType());
                                var identity=ref==null||!ref.isValid()?null:current.getComponent(ref,
                                        com.inigmasgames.hytalerpg.enemies.EnemyActorIdentity.getComponentType());
                                if(marker==null||identity==null||!marker.state().world().equals(birth.world())
                                        ||!marker.state().encounter().equals(birth.encounter())
                                        ||marker.state().generation()!=birth.generation()
                                        ||!marker.state().entity().equals(actor.entityId())
                                        ||!identity.state().equals(com.inigmasgames.hytalerpg.enemies.EnemyActorIdentity.State.of(actor)))
                                    throw new IllegalStateException("ENEMY_REBIND_ACTOR_CHANGED");
                                var saved=runtime.spawn(birth.world(),actor.entityId());
                                if(saved.isEmpty()||saved.get().enemyRewards()==null
                                        ||!saved.get().enemyRewards().equals(actor.immutableRewardContext()))
                                    throw new IllegalStateException("ENEMY_REBIND_SPECIAL_CONTEXT_MISMATCH");
                                savedSpawns.add(saved.get());
                                var clock=current.getComponent(ref,com.inigmasgames.hytalerpg.enemies.EnemyEngagementClock.getComponentType());
                                long phase=clock==null?0:clock.state().phaseMillis();
                                var actorRole=nativeBindings.requireActorRole(actor);
                                snapshots.add(com.inigmasgames.hytalerpg.enemies.EnemyAffixSnapshot.resolve(actor,balance,pack,phase,
                                        actorRole.capabilities().contains(com.inigmasgames.hytalerpg.enemies.EnemyAffixRegistry.Capability.MOBILE),
                                        actorRole.capabilities().contains(com.inigmasgames.hytalerpg.enemies.EnemyAffixRegistry.Capability.RECOVERY_TIMELINE)));
                            }
                            for(int i=0;i<actors.size();i++){
                                var actor=actors.get(i);
                                difficultyCombat.ready(current,actor.entityId(),tickets.get(i),Optional.of(savedSpawns.get(i)),
                                        false,actor,snapshots.get(i));
                                if(!difficultyCombat.enemyState(birth.world(),actor.entityId())
                                        .map(HytaleDifficultyCombat.EnemyState::descriptor).filter(actor::equals).isPresent())
                                    throw new IllegalStateException("ENEMY_REBIND_HEALTH_PROJECTION_MISSING");
                                difficultyCombat.bindEnemyPack(current,actor,pack);
                            }
                            difficultyCombat.refreshEnemyPack(current,pack);
                            result.complete(null);
                        }catch(RuntimeException failure){
                            for(var actor:actors)difficultyCombat.detach(birth.world(),actor.entityId());
                            result.completeExceptionally(failure);
                        }
                    });}catch(RuntimeException queueFailure){result.completeExceptionally(queueFailure);}
                });
        return result.minimalCompletionStage();
    }
    public void detachObserved(UUID world,UUID enemy){if(difficultyCombat!=null)difficultyCombat.forget(world,enemy);runtime.detachNative(world,enemy);}
    public record DamageObservation(UUID world,UUID enemy,UUID actor,double before,double after,double maximum,double nativeAmount,long observedAt,
                                    String root,String instance,String correlation){
        public DamageObservation{
            Objects.requireNonNull(world);Objects.requireNonNull(enemy);Objects.requireNonNull(actor);
            for(String id:List.of(root,instance,correlation))if(id.length()>512)throw new IllegalArgumentException("DAMAGE_OBSERVATION_ID_LIMIT");
            for(double value:new double[]{before,after,maximum,nativeAmount})if(!Double.isFinite(value)||value<0)throw new IllegalArgumentException("DAMAGE_OBSERVATION_VALUE");
        }
    }
    /** Production handoff seam: the factory executes NOW, never on a persistence thread. */
    public synchronized PersistentEncounterRuntime.Submission<Boolean> observeDamage(DamageObservation o,java.util.function.Supplier<Runnable> captureMastery){
        if(qaEncounters!=null&&qaEncounters.contains(o.world(),o.enemy()))
            return new PersistentEncounterRuntime.Submission<>(false,java.util.concurrent.CompletableFuture.completedStage(false));
        var gearPolicy=gearPolicy(o.world(),o.actor());
        try(var effect=durableEffects.reserve()){
            var submission=runtime.submitDamage(o.world(),o.enemy(),o.actor(),o.before(),o.after(),o.maximum(),true,o.observedAt());
            if(!submission.provisional())return submission;
            var award=captureMastery.get();var details=new LinkedHashMap<String,Object>();
            details.put("world",o.world());details.put("enemy",o.enemy());details.put("kind","DAMAGE");details.put("healthBefore",o.before());details.put("healthAfter",o.after());
            details.put("actualHealthLost",o.before()-o.after());details.put("nativeAmount",o.nativeAmount());details.put("rootCastId",o.root());details.put("skillInstanceId",o.instance());
            details.put("eventId","enemy-death/"+o.world()+"/"+o.enemy());details.put("player",o.actor());
            details.put("source",o.root().isBlank()?"NATIVE_ENTITY_SOURCE":"HYWIND_DAMAGE_METADATA");
            var immutable=Map.copyOf(details);
            effect.submit(submission.durable(),accepted->{if(accepted){emit(o.actor(),RpgTraceEventType.ENCOUNTER_CONTRIBUTION_OBSERVED,o.correlation(),immutable);award.run();
                if(gearLoot!=null&&gearPolicy!=null)gearLoot.contribute(o.world(),o.enemy(),o.actor(),gearPolicy,o.observedAt());}});
            return submission;
        }
    }
    private Runnable prepareMastery(UUID world,UUID enemy,com.inigmasgames.hytalerpg.execution.SkillExecutionContext context,boolean meaningful){
        var actor=context.request().actorId();long now=System.currentTimeMillis();var p=context.profile();
        var budget=context.effects().mastery();boolean sustained=budget.sustained();
        boolean manual=context.request().origin()==com.inigmasgames.hytalerpg.execution.SkillExecutionRequest.Origin.MANUAL;
        var eligibility=runtime.captureMasteryEligibility(world,enemy,actor,loadouts.characterLevel(actor),now);
        long observed=System.nanoTime();String skill=p.skillId(),root=context.rootCastId(),primary=budget.primaryInstance(),correlation=context.request().correlationId();
        // Capture the observation-time predicate before death/detach; only the durable worker awards it.
        // No Store, Ref, context/effect graph, position or other ECS reference crosses this boundary.
        return ()->budget.award(manual,meaningful,completedEligibility(eligibility),sustained,observed,ordinal->{
            // Primary and every derived child share rootCastId + ordinal; never use victim/child/tick as the dedup identity.
            loadouts.awardEarned(actor,new EarnedReward("mastery/"+primary+"/"+ordinal,0,0,Map.of(skill,1L),
                    "MEANINGFUL_MANUAL_ROOT",root,primary,correlation,ProgressionDelta.meaningful(skill)));
            progressionLatency.committed(actor,"mastery/"+primary+"/"+ordinal,observed);
        });
    }
    private static boolean completedEligibility(java.util.concurrent.CompletionStage<Boolean> eligibility){
        try{return eligibility.toCompletableFuture().get(5,java.util.concurrent.TimeUnit.SECONDS);}
        catch(Exception error){if(error instanceof InterruptedException)Thread.currentThread().interrupt();throw new IllegalStateException("ENCOUNTER_MASTERY_PREDECESSOR_FAILED",error);}
    }
    public void controlResolved(Store<EntityStore> store,Ref<EntityStore> target,com.inigmasgames.hytalerpg.execution.SkillExecutionContext context,boolean taunt,String evidence){
        if(qaActor(store,target))return;
        safely("NATIVE_EFFECTIVE_CONTROL",()->{
            var actor=store.getExternalData().getRefFromUUID(context.request().actorId());var enemy=id(store,target);
            if(actor==null||!actor.isValid()||enemy==null||store.getComponent(actor,PlayerRef.getComponentType())==null
                    ||!HytaleAreaQueries.hostile(store,target,actor)||excluded(store,target))return;
            try(var effect=durableEffects.reserve()){
                var gearPolicy=gearPolicy(world(store),context.request().actorId());var gearWorld=world(store);long gearObserved=System.currentTimeMillis();
                var submission=runtime.submitControl(world(store),enemy,context.request().actorId(),true,taunt,true,System.currentTimeMillis());
                if(!submission.provisional())return;
                var award=prepareMastery(world(store),enemy,context,true);var player=context.request().actorId();var correlation=context.request().correlationId();
                var details=Map.of("kind",taunt?"TAUNT":"CONTROL","enemy",enemy,"evidence",evidence,"rootCastId",context.rootCastId(),"skillInstanceId",context.skillInstanceId(),"connectedProof",false);
                effect.submit(submission.durable(),accepted->{if(accepted){emit(player,RpgTraceEventType.ENCOUNTER_CONTRIBUTION_OBSERVED,correlation,details);award.run();
                    if(gearLoot!=null&&gearPolicy!=null)gearLoot.contribute(gearWorld,enemy,player,gearPolicy,gearObserved);}});
            }
        });
    }
    /** Called only after the existing native Health write; does not add healing or award mastery. */
    public void healingResolved(Store<EntityStore> store,Ref<EntityStore> beneficiary,com.inigmasgames.hytalerpg.execution.SkillExecutionContext context,double before,double after){
        safely("NATIVE_HEAL_CONTRIBUTION",()->{
            if(!Double.isFinite(before)||!Double.isFinite(after)||after<=before)return;
            var healer=store.getExternalData().getRefFromUUID(context.request().actorId());
            if(healer==null||!healer.isValid()||store.getComponent(healer,PlayerRef.getComponentType())==null||!HytaleSupportSystem.eligibleAlly(store,healer,beneficiary))return;
            var recipient=id(store,beneficiary);if(recipient==null)return;
            long now=System.currentTimeMillis();
            try(var effect=durableEffects.reserve();var admission=runtime.reserveHealing(world(store),recipient,now)){
            var restored=injuries.healed(world(store),recipient,before,after,now);
            double eligibleHealing=restored.entrySet().stream().filter(e->runtime.observing(world(store),e.getKey())).mapToDouble(Map.Entry::getValue).sum();
            var submission=runtime.submitHeal(admission,context.request().actorId(),recipient,eligibleHealing,true,now);
            if(submission.provisional()<=0)return;
            var awards=restored.keySet().stream().map(enemy->prepareMastery(world(store),enemy,context,true)).toList();
            var player=context.request().actorId();var correlation=context.request().correlationId();
            var details=Map.of("kind","HEAL","beneficiary",recipient,"healthBefore",before,"healthAfter",after,"actualHealing",after-before,"hostileInjuryRestored",eligibleHealing,"eligibleEncounters",submission.provisional(),"rootCastId",context.rootCastId(),"skillInstanceId",context.skillInstanceId());
            effect.submit(submission.durable(),count->{if(count>0){var actual=new LinkedHashMap<String,Object>(details);actual.put("eligibleEncounters",count);emit(player,RpgTraceEventType.ENCOUNTER_CONTRIBUTION_OBSERVED,correlation,Map.copyOf(actual));awards.forEach(Runnable::run);}});
            }
        });
    }
    /** Actual post-filter consumed shield amount, regardless of whether its optional reflection is configured. */
    public void absorptionResolved(Store<EntityStore> store,Ref<EntityStore> recipient,Damage damage,com.inigmasgames.hytalerpg.execution.support.FiniteSupportEffects.Absorption absorption){
        if(absorption==null||absorption.amount()<=0)return;
        safely("NATIVE_ABSORB_CONTRIBUTION",()->{
            var metadata=HytaleDamageAdapter.metadata(damage);if(damage.isCancelled()||metadata!=null&&metadata.noCredit()||!(damage.getSource() instanceof Damage.EntitySource source))return;
            var enemyRef=source.getRef();if(enemyRef==null||!enemyRef.isValid()||!HytaleAreaQueries.hostile(store,enemyRef,recipient))return;
            var enemy=id(store,enemyRef);var actor=absorption.effect().key().owner();var owner=store.getExternalData().getRefFromUUID(actor);
            if(enemy==null||qaActor(store,enemyRef)||owner==null||!owner.isValid()||store.getComponent(owner,PlayerRef.getComponentType())==null)return;
            var c=absorption.effect().context();
            try(var effect=durableEffects.reserve()){
                var submission=runtime.submitAbsorb(world(store),enemy,actor,absorption.amount(),true,System.currentTimeMillis());
                if(!submission.provisional())return;
                var award=prepareMastery(world(store),enemy,c,true);var correlation=c.request().correlationId();
                var details=Map.of("kind","ABSORB","enemy",enemy,"beneficiary",id(store,recipient),"actuallyAbsorbed",absorption.amount(),"rootCastId",c.rootCastId(),"skillInstanceId",c.skillInstanceId());
                effect.submit(submission.durable(),accepted->{if(accepted){emit(actor,RpgTraceEventType.ENCOUNTER_CONTRIBUTION_OBSERVED,correlation,details);award.run();}});
            }
        });
    }
    private static UUID world(Store<EntityStore> store){return store.getExternalData().getWorld().getWorldConfig().getUuid();}
    private static UUID id(Store<EntityStore> store,Ref<EntityStore> ref){var component=ref==null||!ref.isValid()?null:store.getComponent(ref,UUIDComponent.getComponentType());return component==null?null:component.getUuid();}
    private static Vec3 position(Store<EntityStore> store,Ref<EntityStore> ref){var p=store.getComponent(ref,TransformComponent.getComponentType()).getPosition();return new Vec3(p.x(),p.y(),p.z());}
    static boolean excluded(Store<EntityStore> store,Ref<EntityStore> ref){
        var npc=store.getComponent(ref,NPCEntity.getComponentType());
        return npc==null||npc.isReserved()||store.getComponent(ref,PlayerRef.getComponentType())!=null
                ||store.getComponent(ref,SummonProjection.getComponentType())!=null||store.getComponent(ref,ConversionProjection.getComponentType())!=null
                ||store.getComponent(ref,EntityStore.REGISTRY.getNonSerializedComponentType())!=null&&!qaActor(store,ref);
    }
    static boolean qaActor(Store<EntityStore> store,Ref<EntityStore> ref){
        return ref!=null&&QaTransientMarker.getComponentType()!=null
                &&store.getComponent(ref,QaTransientMarker.getComponentType())!=null;
    }
    private static String biome(Store<EntityStore> store,Ref<EntityStore> ref){
        return biome(store,position(store,ref));
    }
    private static String biome(Store<EntityStore> store,Vec3 p){
        var nativeWorld=store.getExternalData().getWorld();var generator=nativeWorld.getChunkStore().getGenerator();
        if(!(generator instanceof ChunkGenerator legacy)||!"Default".equals(generatorIdentity(nativeWorld.getWorldConfig().getWorldGenProvider())))return "UNSUPPORTED_WORLD_GENERATOR";
        var result=legacy.getZoneBiomeResultAt((int)nativeWorld.getWorldConfig().getSeed(),(int)Math.floor(p.x()),(int)Math.floor(p.z()));
        return "Default/"+result.getZoneResult().getZone().name()+"/"+result.getBiome().getName();
    }
    /** Read the actual public native codec, not a folder-name/toString guess or private reflection. */
    public static String generatorIdentity(com.hypixel.hytale.server.core.universe.world.worldgen.provider.IWorldGenProvider provider){
        if(!(provider instanceof com.hypixel.hytale.server.worldgen.HytaleWorldGenProvider legacy))return "UNSUPPORTED_WORLD_GENERATOR";
        var encoded=com.hypixel.hytale.server.worldgen.HytaleWorldGenProvider.CODEC.encode(legacy,new com.hypixel.hytale.codec.ExtraInfo());
        if(encoded.containsKey("Path")&&!encoded.get("Path").isNull())return "CUSTOM_WORLDGEN_PATH_UNAUDITED";
        return encoded.getString("Name",new org.bson.BsonString("Default")).getValue();
    }
    public static boolean nativeWorldSpawnEvidence(AddReason reason,int environment,int spawnConfiguration){
        return reason==AddReason.SPAWN&&environment!=Integer.MIN_VALUE&&spawnConfiguration!=Integer.MIN_VALUE;
    }
    /** One authored source classifier for both ordinary natural spawns and captured native flocks. */
    public Optional<EnemyRewardRegistry.Spawn> classifyNatural(Store<EntityStore> store,Ref<EntityStore> ref){
        if(!store.isInThread()||ref==null||!ref.isValid()||ref.getStore()!=store)throw new IllegalStateException("NATIVE_ENEMY_CLASSIFY_OWNER");
        if(excluded(store,ref)||difficulty==null)return Optional.empty();
        var npc=store.getComponent(ref,NPCEntity.getComponentType());
        if(npc==null||!nativeWorldSpawnEvidence(AddReason.SPAWN,npc.getEnvironment(),npc.getSpawnConfiguration())
                ||registry.resolveRole(npc.getRoleName()).isEmpty())return Optional.empty();
        return difficulty.classifyAuthored(registry,world(store),id(store,ref),npc.getRoleName(),biome(store,ref),
                EnemyRewardRegistry.Origin.WILD_WORLD_SPAWN,System.currentTimeMillis());
    }
    /** Read a staged native member through the ordinary authored classifier and native name resolver. */
    public Optional<com.inigmasgames.hytalerpg.enemies.EnemyNativeGroupPreparation.MemberSource> classifyStagedNative(
            Store<EntityStore> store,Ref<EntityStore> ref,com.inigmasgames.hytalerpg.enemies.EnemyNativeBindings.Role binding){
        if(!store.isInThread()||ref==null||!ref.isValid()||ref.getStore()!=store)
            throw new IllegalStateException("ENEMY_STAGED_CLASSIFY_OWNER");
        var npc=store.getComponent(ref,NPCEntity.getComponentType());
        var marker=store.getComponent(ref,EnemyStaging.getComponentType());
        var allegiance=store.getComponent(ref,com.hypixel.hytale.server.npc.role.support.WorldSupport.getComponentType());
        if(npc==null||marker==null||qaActor(store,ref)
                ||allegiance==null||allegiance.getDefaultPlayerAttitude()
                    !=com.hypixel.hytale.server.core.asset.type.attitude.Attitude.HOSTILE
                ||!binding.nativeRoleIds().contains(npc.getRoleName())
                ||!marker.state().world().equals(world(store))||!marker.state().entity().equals(id(store,ref)))
            return Optional.empty();
        var classified=classifyNatural(store,ref);if(classified.isEmpty())return Optional.empty();
        var name=HytaleDifficultyCombat.nativeDisplayName(store,ref,npc);
        if(name==null||name.isBlank()||name.startsWith("server.")||name.contains("_"))return Optional.empty();
        return Optional.of(new com.inigmasgames.hytalerpg.enemies.EnemyNativeGroupPreparation.MemberSource(classified.get(),name));
    }
    /** Operator staging admits a real NPC through the authored profile without claiming a world-spawn job. */
    public Optional<com.inigmasgames.hytalerpg.enemies.EnemyNativeGroupPreparation.MemberSource> classifyStagedQa(
            Store<EntityStore> store,Ref<EntityStore> ref,com.inigmasgames.hytalerpg.enemies.EnemyNativeBindings.Role binding){
        if(!store.isInThread()||ref==null||!ref.isValid()||ref.getStore()!=store)
            throw new IllegalStateException("ENEMY_QA_CLASSIFY_OWNER");
        var npc=store.getComponent(ref,NPCEntity.getComponentType());
        var marker=store.getComponent(ref,EnemyStaging.getComponentType());
        if(npc==null||marker==null||!binding.nativeRoleIds().contains(npc.getRoleName())
                ||!marker.state().world().equals(world(store))||!marker.state().entity().equals(id(store,ref))
                ||difficulty==null){
            com.hypixel.hytale.logger.HytaleLogger.getLogger().atWarning().log(
                    "RPG_ENEMY_QA_PROFILE_REJECTED reason=STAGED_ROLE_OR_WORLD_MISMATCH expectedRole=%s actualRole=%s marker=%s difficultyReady=%s",
                    binding.canonicalRoleId(),npc==null?"<none>":npc.getRoleName(),marker!=null,difficulty!=null);
            return Optional.empty();
        }
        String roleId=npc.getRoleName();
        var authored=com.inigmasgames.hytalerpg.difficulty.AuthoredEncounterCatalog.load().roles().stream()
                .filter(row->row.id().equals(roleId)&&!row.campaignRegion().isBlank()).findFirst();
        Optional<EnemyRewardRegistry.Spawn> classified;
        if(authored.isPresent()){
            var resolvedRole=registry.resolveRole(roleId).orElse(null);
            if(resolvedRole==null)return Optional.empty();
            var resolved=difficulty.resolveAuthored(world(store),id(store,ref),roleId,
                    com.inigmasgames.hytalerpg.difficulty.AuthoredEncounterCatalog.CAMPAIGN_GOLEM);
            classified=Optional.of(new EnemyRewardRegistry.Spawn(world(store),id(store,ref),roleId,
                    resolvedRole.canonical().combatIdentity(),resolved.biomeKey(),resolved.sourceCombatLevel(),
                    resolvedRole.canonical().rank(),resolvedRole.canonical().rarity(),resolved.profileId(),
                    System.currentTimeMillis(),null,resolved));
        }else{
            var resolvedRole=registry.resolveRole(roleId).orElse(null);
            if(resolvedRole==null)return Optional.empty();
            String nativeBiome=biome(store,ref);
            String qaBiome=qaAuthoredBiome(registry,nativeBiome).orElse(null);
            if(qaBiome==null){
                com.hypixel.hytale.logger.HytaleLogger.getLogger().atWarning().log(
                        "RPG_ENEMY_QA_PROFILE_REJECTED reason=NO_AUTHORED_QA_PROFILE role=%s nativeBiome=%s",
                        roleId,nativeBiome);
                return Optional.empty();
            }
            var resolved=difficulty.resolveAuthored(world(store),id(store,ref),roleId,qaBiome);
            classified=Optional.of(new EnemyRewardRegistry.Spawn(world(store),id(store,ref),roleId,
                    resolvedRole.canonical().combatIdentity(),qaBiome,resolved.sourceCombatLevel(),
                    resolvedRole.canonical().rank(),resolvedRole.canonical().rarity(),resolved.profileId(),
                    System.currentTimeMillis(),null,resolved));
            if(!qaBiome.equals(nativeBiome))com.hypixel.hytale.logger.HytaleLogger.getLogger().atInfo().log(
                    "RPG_ENEMY_QA_PROFILE_ZONE_FALLBACK role=%s nativeBiome=%s authoredBiome=%s",
                    roleId,nativeBiome,qaBiome);
        }
        if(classified.isEmpty())return Optional.empty();
        var name=HytaleDifficultyCombat.nativeDisplayName(store,ref,npc);
        if(name==null||name.isBlank()||name.startsWith("server.")||name.contains("_"))return Optional.empty();
        return Optional.of(new com.inigmasgames.hytalerpg.enemies.EnemyNativeGroupPreparation.MemberSource(classified.get(),name));
    }
    /** QA placement reuses an authored zone profile; unsupported generators use the authored spawn-zone profile. */
    static Optional<String> qaAuthoredBiome(EnemyRewardRegistry registry,String nativeBiome){
        if(nativeBiome!=null){
            for(var biome:registry.biomes())if(biome.key().equals(nativeBiome))return Optional.of(nativeBiome);
            int lastSlash=nativeBiome.lastIndexOf('/');
            if(lastSlash>=0){
                String zone=nativeBiome.substring(0,lastSlash+1);
                var sameZone=registry.biomes().stream().map(EnemyRewardRegistry.Biome::key)
                        .filter(key->key.startsWith(zone)).findFirst();
                if(sameZone.isPresent())return sameZone;
            }
        }
        return registry.biomes().stream().map(EnemyRewardRegistry.Biome::key)
                .filter("Default/Zone1_Spawn/Plains_Spawn"::equals).findFirst();
    }
    /** Freeze the authored monster profile before the QA NPCs enter the native store. */
    public record QaProfile(String roleId,String combatIdentity,String biomeKey,
            ProgressionMath.Rank rank,ProgressionMath.Rarity rarity,
            com.inigmasgames.hytalerpg.difficulty.EncounterProfileResolver.Resolved combat) {
        public QaProfile {Objects.requireNonNull(combat);}
        public EnemyRewardRegistry.Spawn forActor(UUID actor) {
            var c=combat;
            var bound=new com.inigmasgames.hytalerpg.difficulty.EncounterProfileResolver.Resolved(
                    c.worldId(),actor,c.difficulty(),c.profileId(),c.worldProfileId(),c.roleId(),c.biomeKey(),
                    c.sourceCombatLevel(),c.maxHealth(),c.attackBasis(),c.difficultyHealthFactor(),
                    c.difficultyDamageFactor(),c.resistance(),c.evidence(),c.progression());
            return new EnemyRewardRegistry.Spawn(c.worldId(),actor,roleId,combatIdentity,biomeKey,
                    c.sourceCombatLevel(),rank,rarity,c.profileId(),System.currentTimeMillis(),null,bound);
        }
    }
    public QaProfile planQaProfile(Store<EntityStore> store,String roleId,Vec3 position,UUID previewActor,
            com.inigmasgames.hytalerpg.difficulty.DifficultyId era) {
        if(!store.isInThread()||difficulty==null)throw new IllegalStateException("QA_PROFILE_OWNER_UNAVAILABLE");
        var resolvedRole=registry.resolveRole(roleId).orElseThrow(()->new IllegalArgumentException("QA_ROLE_NOT_AUTHORED:"+roleId));
        boolean campaignFinal=com.inigmasgames.hytalerpg.difficulty.AuthoredEncounterCatalog.load().roles().stream()
                .anyMatch(row->row.id().equals(roleId)&&!row.campaignRegion().isBlank());
        String qaBiome=campaignFinal?com.inigmasgames.hytalerpg.difficulty.AuthoredEncounterCatalog.CAMPAIGN_GOLEM
                :qaAuthoredBiome(registry,biome(store,position)).orElseThrow(()->new IllegalArgumentException("QA_PROFILE_UNAVAILABLE:"+roleId));
        var resolved=difficulty.resolveAuthoredQa(world(store),previewActor,roleId,qaBiome,era);
        return new QaProfile(roleId,resolvedRole.canonical().combatIdentity(),qaBiome,
                resolvedRole.canonical().rank(),resolvedRole.canonical().rarity(),resolved);
    }
    /** No birth root was submitted: give the original actors back to Hytale without
     *  enrolling them in a second encounter. This path must work even if the
     *  encounter writer/effects lane is unavailable. */
    public void releasePreRootNativeGroup(Store<EntityStore> store,List<EnemyStaging.State> members){
        var keys=members.stream().map(member->new ExclusionKey(member.world(),member.entity())).toList();
        if(keys.stream().anyMatch(releasingPreRoot::contains))
            throw new IllegalStateException("ENEMY_PRE_ROOT_RELEASE_REENTRY");
        releasingPreRoot.addAll(keys);
        try{EnemyStaging.releaseGroup(store,members);}
        finally{releasingPreRoot.removeAll(keys);}
    }
    /** After durable compensation, ordinary contexts have been restored by the writer. */
    public void restoreStagedNativeGroup(Store<EntityStore> store,List<EnemyStaging.State> members){
        EnemyStaging.releaseGroup(store,members);
        for(var member:members){
            var ref=store.getExternalData().getRefFromUUID(member.entity());
            if(ref!=null&&ref.isValid())added(ref,AddReason.SPAWN,store);
        }
    }
    /** Failed pre-publication special attachment: commit ordinary contexts before releasing any native actor. */
    public java.util.concurrent.CompletionStage<Void> compensateStagedNativeGroup(Store<EntityStore> store,
            NativeEnemySpawnGroups.Group group,com.inigmasgames.hytalerpg.enemies.EnemyBirthRoot root){
        return compensateStagedNativeGroup(store,group,null,root);
    }
    /** Added native flock actors have no ordinary context; remove them before restoring the original roster. */
    public java.util.concurrent.CompletionStage<Void> compensateStagedNativeGroup(Store<EntityStore> store,
            NativeEnemySpawnGroups.Group group,NativeEnemySpawnGroups.Group additional,
            com.inigmasgames.hytalerpg.enemies.EnemyBirthRoot root){
        var originalIds=new HashSet<>(root.plan().originalNativeEntities());
        var additionalActors=root.plan().actors().stream()
                .filter(actor->!originalIds.contains(actor.entityId())).toList();
        var originalActors=root.plan().actors().stream()
                .filter(actor->originalIds.contains(actor.entityId())).toList();
        var expectedAdditional=additionalActors.stream()
                .map(com.inigmasgames.hytalerpg.enemies.EnemyDescriptor::entityId).toList();
        if(!store.isInThread()||!world(store).equals(root.world())||!group.reservation().world().equals(root.world())
                ||!group.reservation().encounter().equals(root.encounter())
                ||group.reservation().generation()!=root.plan().generation()
                ||!expectedAdditional.equals(additional==null?List.of():additional.members().stream()
                        .map(NativeEnemySpawnGroups.Member::entity).toList())
                ||!group.members().stream().map(NativeEnemySpawnGroups.Member::entity).toList().equals(root.plan().originalNativeEntities()))
            throw new IllegalStateException("ENEMY_BIRTH_COMPENSATION_GROUP_BINDING");
        var nativeWorld=store.getExternalData().getWorld();var result=new java.util.concurrent.CompletableFuture<Void>();
        try{runtime.compensateEnemyBirth(root).whenComplete((decision,error)->{
            if(error!=null){result.completeExceptionally(error);return;}
            try{nativeWorld.execute(()->{
                try{
                    var current=nativeWorld.getEntityStore().getStore();
                    var identity=com.inigmasgames.hytalerpg.enemies.EnemyActorIdentity.getComponentType();
                    for(var actor:root.plan().actors()){
                        var ref=current.getExternalData().getRefFromUUID(actor.entityId());
                        var staged=ref==null||!ref.isValid()?null:current.getComponent(ref,EnemyStaging.getComponentType());
                        var saved=ref==null||!ref.isValid()?null:current.getComponent(ref,identity);
                        if(staged==null||!staged.state().world().equals(root.world())
                                ||!staged.state().encounter().equals(root.encounter())
                                ||staged.state().generation()!=root.plan().generation()
                                ||!staged.state().entity().equals(actor.entityId())
                                ||saved!=null&&!saved.state().equals(com.inigmasgames.hytalerpg.enemies.EnemyActorIdentity.State.of(actor)))
                            throw new IllegalStateException("ENEMY_BIRTH_COMPENSATION_NATIVE_GENERATION_CHANGED");
                    }
                    if(additional!=null){
                        for(var actor:additionalActors)
                            if(difficultyCombat!=null)difficultyCombat.detach(root.world(),actor.entityId());
                        NativeEnemyFlockExtension.discard(current,group,additional);
                    }
                    for(var actor:originalActors){
                        if(difficultyCombat!=null)difficultyCombat.detach(root.world(),actor.entityId());
                        var ref=current.getExternalData().getRefFromUUID(actor.entityId());
                        if(current.getComponent(ref,identity)!=null)current.removeComponent(ref,identity);
                    }
                    restoreStagedNativeGroup(current,group.members().stream().map(NativeEnemySpawnGroups.Member::staging).toList());
                    if(enemyAdmission!=null)enemyAdmission.releaseTerminal(root.plan().pack().abort("BIRTH_COMPENSATED"));
                    result.complete(null);
                }catch(RuntimeException failure){result.completeExceptionally(failure);}
            });}catch(RuntimeException closing){result.completeExceptionally(closing);}
        });}catch(RuntimeException rejected){result.completeExceptionally(rejected);}
        return result.minimalCompletionStage();
    }
    /** Absent optional native health snapshots are an uncredited hit, never a persistence failure. */
    public static boolean creditableNativeHealth(UUID player,double before,double after,double maximum,double nativeAmount){
        return player!=null&&Double.isFinite(before)&&Double.isFinite(after)&&Double.isFinite(maximum)&&Double.isFinite(nativeAmount)
                &&before>=after&&after>=0&&maximum>0&&nativeAmount>=0;
    }
    private void added(Ref<EntityStore> ref,AddReason reason,Store<EntityStore> store){
        if(qaActor(store,ref))return;
        if(excluded(store,ref))return;
        if(releasingPreRoot.contains(new ExclusionKey(world(store),id(store,ref))))return;
        // The native flock capture marks members before they enter the entity store. Its birth
        // owner attaches and publishes the sealed group together; the ordinary per-NPC path
        // must not install an independent reward/Health projection while that transaction waits.
        var staging=EnemyStaging.getComponentType();
        if(staging!=null&&store.getComponent(ref,staging)!=null)return;
        var identity=com.inigmasgames.hytalerpg.enemies.EnemyActorIdentity.getComponentType();
        if(identity!=null&&store.getComponent(ref,identity)!=null)return;
        var npc=store.getComponent(ref,NPCEntity.getComponentType());var world=world(store);var enemy=id(store,ref);
        var roleId=npc.getRoleName();
        boolean golem=golemCatalog.role(roleId).isPresent();
        var resolvedRole=registry.resolveRole(roleId);
        if(!golem&&resolvedRole.isEmpty())return; // Unknown native NPCs never create reward attachments.
        Object combatTicket=difficultyCombat==null?null:difficultyCombat.begin(world,enemy);
        if(reason==AddReason.SPAWN&&golemBindings!=null&&golem){
            // Native SpawnMarkerEntity sets its owner reference in NPCPlugin's POST-add callback.
            // Defer to the world's queue, but only for this captured SPAWN event; LOAD never mints evidence.
            var nativeWorld=store.getExternalData().getWorld();
            nativeWorld.execute(()->safely("GOLEM_SOURCE_BINDING",()->{
                var current=nativeWorld.getEntityRef(enemy);if(current==null||!current.isValid()||excluded(store,current))return;
                var currentNpc=store.getComponent(current,NPCEntity.getComponentType());if(currentNpc==null)return;
                String marker="";UUID markerId=null;
                var source=store.getComponent(current,com.hypixel.hytale.server.npc.components.SpawnMarkerReference.getComponentType());
                if(source!=null){var owner=source.getReference().getEntity(store);
                    if(owner!=null&&owner.isValid()){var component=store.getComponent(owner,com.hypixel.hytale.server.spawning.spawnmarkers.SpawnMarkerEntity.getComponentType());
                        if(component!=null){marker=component.getSpawnMarkerId();markerId=id(store,owner);}}}
                var placement=store.getComponent(current,com.inigmasgames.hytalerpg.difficulty.CampaignEncounterProjection.getComponentType());
                var candidate=golemBindings.classify(world,enemy,currentNpc.getRoleName(),marker,markerId,placement,System.currentTimeMillis());
                if(difficulty!=null)candidate=candidate.flatMap(s->difficulty.author(s,com.inigmasgames.hytalerpg.difficulty.AuthoredEncounterCatalog.CAMPAIGN_GOLEM));
                attachNativeCombat(store,world,enemy,currentNpc.getRoleName(),candidate,reason,combatTicket);
            }));return;
        }
        // WorldSpawnJobSystems initializes these before NPCPlugin.Store.addEntity(SPAWN).
        // Manual NPCPlugin spawns retain Integer.MIN_VALUE and cannot masquerade as natural spawns.
        Optional<EnemyRewardRegistry.Spawn> spawn=Optional.empty();
        if(reason==AddReason.SPAWN)spawn=classifyNatural(store,ref);
        if(reason==AddReason.SPAWN&&resolvedRole.isPresent())emit(null,RpgTraceEventType.ENEMY_RESOLVED,enemy.toString(),Map.of(
                "world",world,"enemy",enemy,"runtimeRole",roleId,"canonicalRole",resolvedRole.get().canonical().roleId(),
                "alias",resolvedRole.get().alias(),"rewardEligible",spawn.isPresent(),"source","NATIVE_SPAWN"));
        attachNativeCombat(store,world,enemy,roleId,spawn,reason,combatTicket);
    }
    private void attachNativeCombat(Store<EntityStore> store,UUID world,UUID enemy,String role,Optional<EnemyRewardRegistry.Spawn> spawn,AddReason reason,Object ticket){
        var nativeWorld=store.getExternalData().getWorld();
        attachObserved(world,enemy,role,spawn).whenComplete((attached,error)->{
            // The asynchronous closure carries immutable snapshots/IDs only; native work is reacquired on its world queue.
            var saved=error==null&&attached?runtime.spawn(world,enemy):Optional.<EnemyRewardRegistry.Spawn>empty();
            if(error==null&&attached)emit(null,RpgTraceEventType.ENCOUNTER_STARTED,enemy.toString(),Map.of("world",world,"enemy",enemy,"runtimeRole",role,
                    "canonicalRole",saved.map(EnemyRewardRegistry.Spawn::combatIdentity).orElse("UNKNOWN"),"nativeAddReason",reason.name(),"eventId",saved.map(EnemyRewardRegistry.Spawn::eventId).orElse("")));
            if(difficultyCombat!=null&&error==null)nativeWorld.execute(()->safely("DIFFICULTY_NATIVE_PROJECTION",()->{
                try{difficultyCombat.ready(nativeWorld.getEntityStore().getStore(),enemy,ticket,saved,reason==AddReason.SPAWN);}
                catch(IllegalStateException local){String message=String.valueOf(local.getMessage());
                    if(!message.startsWith("DIFFICULTY_NATIVE_BASELINE_MISMATCH")&&!message.equals("DIFFICULTY_NATIVE_HEALTH_MISSING"))throw local;
                    emit(null,RpgTraceEventType.ENCOUNTER_REWARD_REJECTED,enemy.toString(),Map.of("enemy",enemy,"runtimeRole",role,
                            "reason",message,"scope","LOCAL_NATIVE_PROJECTION"));
                    invalidateConverted(world,enemy);
                }
            }));
        });
    }
    private void damage(int index,ArchetypeChunk<EntityStore> chunk,Store<EntityStore> store,Damage damage){
        var target=chunk.getReferenceTo(index);var world=world(store);var enemy=id(store,target);
        if(qaActor(store,target))return;
        if(!runtime.observing(world,enemy)||damage.isCancelled())return;
        if(excluded(store,target)){invalidateConverted(world,enemy);return;}
        if(!runtime.attaching(world,enemy)){
            var spawn=runtime.spawn(world,enemy);
            if(spawn.isEmpty())return; // An unclassified native NPC has no reward context to inspect.
            if(!spawn.get().roleId().equals(store.getComponent(target,NPCEntity.getComponentType()).getRoleName())){invalidateConverted(world,enemy);return;}
        }
        var metadata=HytaleDamageAdapter.metadata(damage);if(metadata!=null&&metadata.noCredit())return;
        Ref<EntityStore> actor=null;
        if(metadata!=null)actor=store.getExternalData().getRefFromUUID(metadata.actorId());
        else if(damage.getSource() instanceof Damage.EntitySource source){
            actor=source.getRef();
            if(actor!=null&&actor.isValid()){
                var conversion=store.getComponent(actor,ConversionProjection.getComponentType());
                if(conversion!=null&&conversion.lease!=null)actor=store.getExternalData().getRefFromUUID(conversion.lease.owner());
            }
        }
        // A registered natural spawn and a native-applied hit from a real player are
        // sufficient. Provokable animals can lack an attitude view at hit time;
        // contribution must not depend on that optional targeting state.
        if(actor==null||!actor.isValid()||store.getComponent(actor,PlayerRef.getComponentType())==null)return;
        var hp=chunk.getComponent(index,EntityStatMap.getComponentType()).get(DefaultEntityStatTypes.getHealth());if(hp==null)return;
        double before=SupportDamageSystems.observedHealthBefore(damage);var player=id(store,actor);
        if(!creditableNativeHealth(player,before,hp.get(),hp.getMax(),damage.getAmount())){
            emit(player,RpgTraceEventType.ENCOUNTER_CONTRIBUTION_SKIPPED,enemy.toString(),Map.of("world",world,"enemy",enemy,
                    "reason",player==null?"NO_PLAYER_ID":"NATIVE_HEALTH_BEFORE_UNAVAILABLE","source",metadata==null?"NATIVE_ENTITY_SOURCE":"HYWIND_METADATA"));
            return;
        }
        var context=HytaleDamageAdapter.executionContext(damage);
        observeDamage(new DamageObservation(world,enemy,player,before,hp.get(),hp.getMax(),damage.getAmount(),System.currentTimeMillis(),
                metadata==null?"":metadata.rootCastId(),metadata==null?"":metadata.skillInstanceId(),metadata==null?enemy.toString():metadata.correlationId()),
                ()->context!=null&&context.request().actorId().equals(player)?prepareMastery(world,enemy,context,true):()->{});
    }
    private void playerDamaged(int index,ArchetypeChunk<EntityStore> chunk,Store<EntityStore> store,Damage damage){
        var ref=chunk.getReferenceTo(index);var hp=chunk.getComponent(index,EntityStatMap.getComponentType()).get(DefaultEntityStatTypes.getHealth());
        if(hp==null||damage.isCancelled())return;
        UUID enemy=null;var meta=HytaleDamageAdapter.metadata(damage);
        if((meta==null||!meta.noCredit())&&damage.getSource() instanceof Damage.EntitySource source){
            var attacker=source.getRef();var candidate=id(store,attacker);
            if(candidate!=null&&runtime.observing(world(store),candidate)&&!excluded(store,attacker)&&HytaleAreaQueries.hostile(store,attacker,ref))enemy=candidate;
        }
        injuries.damage(world(store),id(store,ref),enemy,SupportDamageSystems.observedHealthBefore(damage),hp.get(),System.currentTimeMillis());
    }
    private void died(Ref<EntityStore> ref,DeathComponent death,Store<EntityStore> store){
        if(qaActor(store,ref))return;
        if(death.getDeathInfo()==null)return;
        var hp=store.getComponent(ref,EntityStatMap.getComponentType()).get(DefaultEntityStatTypes.getHealth());if(hp==null||hp.get()>hp.getMin())return;
        var world=world(store);var enemy=id(store,ref);if(!runtime.observing(world,enemy))return;
        if(excluded(store,ref)){invalidateConverted(world,enemy);return;}
        if(!runtime.attaching(world,enemy)){
            var spawn=runtime.spawn(world,enemy);
            if(spawn.isEmpty())return;
            if(!spawn.get().roleId().equals(store.getComponent(ref,NPCEntity.getComponentType()).getRoleName())){invalidateConverted(world,enemy);return;}
        }
        try(var ticket=durableEffects.reserve()){
        var participants=new ArrayList<EncounterContributions.Participant>();
        var gearMf=new HashMap<UUID,Double>();
        var gearGold=new HashMap<UUID,Double>();
        var recoveryAtDeath=new HashMap<UUID,RecoveryCapture>();
        // Normal path queries credited identities only. While a checkpoint is loading, its old
        // contributor IDs are not yet known: capture a bounded presence superset NOW, then the
        // deterministic ledger admits only persisted/observed contributors after recovery.
        List<UUID> candidates;
        if(runtime.attaching(world,enemy)){
            var present=store.getExternalData().getWorld().getPlayerRefs();
            if(present.size()>EncounterContributions.MAX_CONTRIBUTORS)throw new FileEncounterStore.CapacityRejected();
            candidates=present.stream().map(PlayerRef::getUuid).toList();
        }else candidates=runtime.provisionalContributors(world,enemy);
        for(var player:candidates){
            var actor=store.getExternalData().getRefFromUUID(player);
            if(actor==null||!actor.isValid()||store.getComponent(actor,PlayerRef.getComponentType())==null||store.getComponent(actor,TransformComponent.getComponentType())==null)continue;
            // Only event-time presence/position/party facts here. Progression is resolved AFTER
            // causally prior mastery on the ordered effects worker, never from this placeholder.
            participants.add(new EncounterContributions.Participant(player,world,position(store,actor),1,true,null,null));
            var stats=store.getComponent(actor,EntityStatMap.getComponentType());
            double normalMaximum=GearRecoveryBindings.normalHealthMaximum(store,actor,stats);
            if(normalMaximum>0)recoveryAtDeath.put(player,new RecoveryCapture(
                    com.inigmasgames.hytalerpg.gear.GearNativeItems.effects(actor,store).snapshot(),normalMaximum));
            if(gearEquipment!=null)gearMf.put(player,gearEquipment.magicFind(actor,store));
            if(gearEquipment!=null){
                var snapshot=gearEquipment.effects(actor,store).snapshot();
                double percent=com.inigmasgames.hytalerpg.gear.HytaleGearEquipment.goldFind(snapshot);
                gearGold.put(player,percent);
            }
        }
        var provider=parties;
        var facts=PartyMembershipProvider.apply(world,participants,provider);var availability=provider.availability();
        captureDeath(ticket,world,enemy,position(store,ref),System.currentTimeMillis(),facts,availability,
                Map.copyOf(gearMf),Map.copyOf(gearGold),Map.copyOf(recoveryAtDeath),
                store.getExternalData().getWorld()::execute);
        }
    }
    private void qaDeath(Ref<EntityStore> ref,Store<EntityStore> store){
        if(qaEncounters==null)return;
        try{
            var actor=id(store,ref);
            if(actor!=null)qaEncounters.defeat(store,actor);
        }catch(RuntimeException local){
            com.hypixel.hytale.logger.HytaleLogger.getLogger().atWarning().log(
                    "RPG_ENEMY_QA_DEATH_FAILED actor=%s reason=%s",id(store,ref),String.valueOf(local.getMessage()));
        }
    }
    public synchronized void captureDeath(UUID world,UUID enemy,Vec3 position,long observedAt,List<EncounterContributions.Participant> eventFacts,String partyAvailability){
        if(qaEncounters!=null&&qaEncounters.contains(world,enemy))return;
        if(eventFacts.size()>EncounterContributions.MAX_CONTRIBUTORS||partyAvailability.length()>512)throw new FileEncounterStore.CapacityRejected();
        try(var ticket=durableEffects.reserve()){captureDeath(ticket,world,enemy,position,observedAt,List.copyOf(eventFacts),partyAvailability,Map.of(),Map.of(),Map.of(),Runnable::run);}
    }
    private void captureDeath(DurableEncounterEffects.Reservation ticket,UUID world,UUID enemy,Vec3 position,long observedAt,List<EncounterContributions.Participant> facts,String availability,Map<UUID,Double> gearMf,Map<UUID,Double> gearGold,
                              Map<UUID,RecoveryCapture> recoveryAtDeath,java.util.function.Consumer<Runnable> recoveryDispatcher){
        long observedNanos=System.nanoTime();
        var prepared=runtime.prepareDeathNative(world,enemy,position,observedAt);
        ticket.submit(prepared,maybe->{
            if(maybe.isEmpty())return;var observation=maybe.get();
            var spawn=observation.snapshot().spawn();
            synchronized(deathOrigins){if(deathOrigins.size()>=512)deathOrigins.remove(deathOrigins.keySet().iterator().next());deathOrigins.put(spawn.eventId(),observedNanos);}
            var resolved=facts.stream().filter(p->spawn.combat()!=null?loadouts.getPresentationView(p.player()).state().difficulty.unlocked(spawn.combat().difficulty()):spawn.milestone()==null||loadouts.getPresentationView(p.player()).state().difficulty.unlocked(spawn.milestone().difficulty())).map(p->{
                double wisdom=kernel.effectiveAttributes().effective(loadouts.rawAttribute(p.player(),com.inigmasgames.hytalerpg.combat.attribute.RpgAttribute.WIS));
                return new EncounterContributions.Participant(p.player(),p.world(),p.position(),loadouts.characterLevel(p.player()),p.loaded(),p.partyId(),
                        spawn.milestone()==null?learning.resolve(spawn.combatIdentity(),spawn.rank(),wisdom)
                                .map(opportunity->opportunity.withEnemyRewards(spawn.enemyRewards())).orElse(null):null);
            }).toList();
            if(gearLoot!=null)gearLoot.freezeMagicFind(spawn.eventId(),gearMf);
            var finalized=runtime.finishDeathOrDisqualify(observation,resolved,gearGold);
            if(finalized.isEmpty()){
                emit(null,RpgTraceEventType.ENCOUNTER_REWARD_REJECTED,enemy.toString(),Map.of(
                        "world",world,"enemy",enemy,"reason","LOCAL_DEATH_PLAN_REJECTED","awardsUnavailable",runtime.unavailable()));
                return;
            }
            var p=finalized.get();
            if(p.spawn().enemyRewards()!=null)refreshPackAfterDeath(world,enemy);
            var acceptedSignature=signatureKill;
            if(acceptedSignature!=null)recoveryDispatcher.accept(()->acceptedSignature.accepted(world,enemy));
            var acceptedRecovery=recovery;
            if(acceptedRecovery!=null&&!recoveryAtDeath.isEmpty())recoveryDispatcher.accept(()->
                    dispatchRecovery(p.shares(),world,p.spawn().eventId(),recoveryAtDeath,acceptedRecovery::accepted));
            emit(null,RpgTraceEventType.ENCOUNTER_DEATH_FROZEN,enemy.toString(),Map.of("world",world,"enemy",enemy,"eventId",p.spawn().eventId(),"recipients",p.shares().size(),"nativeDeath",true,"partyProvider",availability));
            emit(null,p.shares().isEmpty()?RpgTraceEventType.ENCOUNTER_CLOSED:RpgTraceEventType.REWARD_ELIGIBLE,enemy.toString(),Map.of(
                    "eventId",p.spawn().eventId(),"runtimeRole",p.spawn().roleId(),"canonicalRole",p.spawn().combatIdentity(),
                    "reason",p.shares().isEmpty()?"NO_ELIGIBLE_CONTRIBUTOR":"ELIGIBLE_SHARES","recipients",p.shares().size()));
        });
    }
    /** Durable guard receipts precede this world-thread protection/nameplate refresh. */
    private void refreshPackAfterDeath(UUID world,UUID nativeEntity){
        if(difficultyCombat==null)return;
        try{runtime.enemyPackForNativeEntity(world,nativeEntity).whenComplete((maybe,error)->{
            if(error!=null){warnPackRefresh(nativeEntity,error);return;}
            if(maybe.isEmpty())return;
            var pack=maybe.get();
            var nativeWorld=com.hypixel.hytale.server.core.universe.Universe.get().getWorld(world);
            if(nativeWorld==null)return;
            try{nativeWorld.execute(()->{
                try{difficultyCombat.refreshEnemyPack(nativeWorld.getEntityStore().getStore(),pack);}
                catch(RuntimeException failure){warnPackRefresh(nativeEntity,failure);}
                finally{if(pack.state()==com.inigmasgames.hytalerpg.enemies.EnemyPackRecord.State.DEFEATED&&enemyAdmission!=null)
                    enemyAdmission.releaseTerminal(pack);}
            });}catch(RuntimeException closing){warnPackRefresh(nativeEntity,closing);}
        });}catch(RuntimeException rejected){warnPackRefresh(nativeEntity,rejected);}
    }
    private static void warnPackRefresh(UUID entity,Throwable failure){
        com.hypixel.hytale.logger.HytaleLogger.getLogger().atWarning().log(
                "RPG_ENEMY_PACK_REFRESH_PENDING entity=%s error=%s detail=%s",entity,
                failure.getClass().getName(),String.valueOf(failure.getMessage()));
    }
    /** Tests may fence this finite snapshot off the native thread; native callbacks never wait on it. */
    public java.util.concurrent.CompletionStage<Void> durableFrontier(){return durableEffects.frontier();}
    public Map<String,Object> handoffMetrics(){return Map.of("effects",durableEffects.metrics(),"runtime",runtime.handoffMetrics(),"players",loadouts.persistenceMetrics(),"publication",progressionLatency.snapshot());}
    public void ownerPublished(UUID player){if(loadouts.ready(player))progressionLatency.published(player,trace);}
    public void deliveryTick(){if(!running())return;safely("DURABLE_DEATH_DELIVERY",this::deliver);}
    private synchronized void deliver(){
        ownerMaintenance.run();
        long now=nanoTime.getAsLong();if(now<nextDeliveryNanos)return;nextDeliveryNanos=now+1_000_000_000L;
        emit(null,RpgTraceEventType.PERSISTENCE_HANDOFF_METRICS,"",handoffMetrics());
        // A single globally scheduled slow cycle. Native ticking never performs a new store read/write.
        if(delivery!=null&&!delivery.toCompletableFuture().isDone())return;
        delivery=durableEffects.submit(()->runtime.drainReadyPlans(8));
    }
    private java.util.concurrent.CompletionStage<Integer> delivery;
    private synchronized void safely(String boundary,Runnable operation){
        if(!running())return;
        try{operation.run();}catch(RuntimeException error){
            // These callbacks observe an already native-applied event. A rejected capture is
            // explicit incomplete evidence, not permission to reward from a partial ledger.
            runtime.rejectIncompleteNativeObservation();
            synchronized(this){if(failureLogged)return;failureLogged=true;}
            com.hypixel.hytale.logger.HytaleLogger.getLogger().atWarning().log("RPG_ENCOUNTER_FAILURE boundary=%s error=%s detail=%s awardsUnavailable=%s failClosed=true",boundary,error.getClass().getName(),String.valueOf(error.getMessage()),runtime.unavailable());
            emit(null,RpgTraceEventType.ENCOUNTER_REWARD_REJECTED,"",Map.of("boundary",boundary,"error",String.valueOf(error.getMessage()),"awardsUnavailable",runtime.unavailable()));
        }
    }
    private void emit(UUID actor,RpgTraceEventType event,String correlation,Map<String,?> details){trace.trace(RpgTraceRecord.create(actor,event,correlation,details));}
    public static final class Tracking extends RefSystem<EntityStore>{
        private final HytaleEncounterRewards rewards;public Tracking(HytaleEncounterRewards rewards){this.rewards=rewards;}
        @Override public Query<EntityStore> getQuery(){return Query.and(NPCEntity.getComponentType(),UUIDComponent.getComponentType(),TransformComponent.getComponentType());}
        @Override public void onEntityAdded(Ref<EntityStore> ref,AddReason reason,Store<EntityStore> store,CommandBuffer<EntityStore> buffer){
        try(var rpgTickSpan=com.inigmasgames.hytalerpg.diagnostics.NativeRpgTickMetrics.enter(store,com.inigmasgames.hytalerpg.diagnostics.NativeRpgTickMetrics.Phase.PROGRESSION)){rewards.safely("NATIVE_SPAWN_CONTEXT",()->rewards.added(ref,reason,store));
        }
    }
        @Override public void onEntityRemove(Ref<EntityStore> ref,RemoveReason reason,Store<EntityStore> store,CommandBuffer<EntityStore> buffer){
        try(var rpgTickSpan=com.inigmasgames.hytalerpg.diagnostics.NativeRpgTickMetrics.enter(store,com.inigmasgames.hytalerpg.diagnostics.NativeRpgTickMetrics.Phase.PROGRESSION)){if(!qaActor(store,ref))rewards.safely("ENCOUNTER_DETACH",()->rewards.detachObserved(world(store),id(store,ref)));
        }
    }
    }
    public static final class Inspect extends DamageEventSystem{
        private static final com.hypixel.hytale.server.core.meta.MetaKey<Boolean> OBSERVED=Damage.META_REGISTRY.registerMetaObject(ignored->false,false,"InigmasGames:EncounterObserved",Codec.BOOLEAN);
        private final HytaleEncounterRewards rewards;public Inspect(HytaleEncounterRewards rewards){this.rewards=rewards;}
        @Override public Query<EntityStore> getQuery(){return Query.and(NPCEntity.getComponentType(),UUIDComponent.getComponentType(),EntityStatMap.getComponentType());}
        @Override public SystemGroup<EntityStore> getGroup(){return DamageModule.get().getInspectDamageGroup();}
        @Override public Set<Dependency<EntityStore>> getDependencies(){return Set.of(new SystemDependency<>(Order.AFTER,DamageSystems.ApplyDamage.class),new SystemDependency<>(Order.BEFORE,SupportDamageSystems.Reflect.class));}
        @Override public void handle(int i,ArchetypeChunk<EntityStore> chunk,Store<EntityStore> store,CommandBuffer<EntityStore> buffer,Damage damage){
        try(var rpgTickSpan=com.inigmasgames.hytalerpg.diagnostics.NativeRpgTickMetrics.enter(store,com.inigmasgames.hytalerpg.diagnostics.NativeRpgTickMetrics.Phase.PROGRESSION)){
            if(Boolean.TRUE.equals(damage.getIfPresentMetaObject(OBSERVED)))return;damage.putMetaObject(OBSERVED,true);rewards.safely("NATIVE_CONTRIBUTION_INSPECT",()->rewards.damage(i,chunk,store,damage));

        }
    }
    }
    public static final class Death extends DeathSystems.OnDeathSystem{
        private final HytaleEncounterRewards rewards;public Death(HytaleEncounterRewards rewards){this.rewards=rewards;}
        @Override public Query<EntityStore> getQuery(){return Query.and(NPCEntity.getComponentType(),UUIDComponent.getComponentType(),EntityStatMap.getComponentType(),TransformComponent.getComponentType());}
        @Override public void onComponentAdded(Ref<EntityStore> ref,DeathComponent death,Store<EntityStore> store,CommandBuffer<EntityStore> buffer){
        try(var rpgTickSpan=com.inigmasgames.hytalerpg.diagnostics.NativeRpgTickMetrics.enter(store,com.inigmasgames.hytalerpg.diagnostics.NativeRpgTickMetrics.Phase.PROGRESSION)){if(qaActor(store,ref))rewards.qaDeath(ref,store);else rewards.safely("NATIVE_DEATH_FREEZE",()->rewards.died(ref,death,store));
        }
    }
    }
    public static final class PlayerInjuries extends DamageEventSystem{
        private static final com.hypixel.hytale.server.core.meta.MetaKey<Boolean> OBSERVED=Damage.META_REGISTRY.registerMetaObject(ignored->false,false,"InigmasGames:HostileInjuryObserved",Codec.BOOLEAN);
        private final HytaleEncounterRewards rewards;public PlayerInjuries(HytaleEncounterRewards rewards){this.rewards=rewards;}
        @Override public Query<EntityStore> getQuery(){return Query.and(PlayerRef.getComponentType(),UUIDComponent.getComponentType(),EntityStatMap.getComponentType());}
        @Override public SystemGroup<EntityStore> getGroup(){return DamageModule.get().getInspectDamageGroup();}
        @Override public Set<Dependency<EntityStore>> getDependencies(){return Set.of(new SystemDependency<>(Order.AFTER,DamageSystems.ApplyDamage.class),new SystemDependency<>(Order.BEFORE,SupportDamageSystems.Reflect.class));}
        @Override public void handle(int i,ArchetypeChunk<EntityStore> chunk,Store<EntityStore> store,CommandBuffer<EntityStore> buffer,Damage damage){
        try(var rpgTickSpan=com.inigmasgames.hytalerpg.diagnostics.NativeRpgTickMetrics.enter(store,com.inigmasgames.hytalerpg.diagnostics.NativeRpgTickMetrics.Phase.PROGRESSION)){if(Boolean.TRUE.equals(damage.getIfPresentMetaObject(OBSERVED)))return;damage.putMetaObject(OBSERVED,true);rewards.safely("NATIVE_HOSTILE_INJURY",()->rewards.playerDamaged(i,chunk,store,damage));
        }
    }
    }
    /** Read-only reconciliation of native regeneration; never consumes or rewrites native stat updates. */
    public static final class HealthObservation extends com.hypixel.hytale.component.system.tick.EntityTickingSystem<EntityStore>{
        private final HytaleEncounterRewards rewards;public HealthObservation(HytaleEncounterRewards rewards){this.rewards=rewards;}
        @Override public Query<EntityStore> getQuery(){return Query.and(PlayerRef.getComponentType(),UUIDComponent.getComponentType(),EntityStatMap.getComponentType());}
        @Override public Set<Dependency<EntityStore>> getDependencies(){return Set.of(new SystemDependency<>(Order.BEFORE,com.hypixel.hytale.server.core.modules.entitystats.EntityStatsSystems.ClearChanges.class),new SystemDependency<>(Order.AFTER,com.hypixel.hytale.server.core.modules.entitystats.EntityStatsModule.PlayerRegenerateStatsSystem.class));}
        @Override public void tick(float delta,int i,ArchetypeChunk<EntityStore> chunk,Store<EntityStore> store,CommandBuffer<EntityStore> buffer){
        try(var rpgTickSpan=com.inigmasgames.hytalerpg.diagnostics.NativeRpgTickMetrics.enter(store,com.inigmasgames.hytalerpg.diagnostics.NativeRpgTickMetrics.Phase.PROGRESSION)){
            var hp=chunk.getComponent(i,EntityStatMap.getComponentType()).get(DefaultEntityStatTypes.getHealth());
            if(hp!=null)rewards.injuries.observedHealth(world(store),chunk.getComponent(i,UUIDComponent.getComponentType()).getUuid(),hp.get(),System.currentTimeMillis());

        }
    }
    }
    public static final class Delivery extends TickingSystem<EntityStore>{
        private final HytaleEncounterRewards rewards;public Delivery(HytaleEncounterRewards rewards){this.rewards=rewards;}
        @Override public void tick(float delta,int systemIndex,Store<EntityStore> store){
        try(var rpgTickSpan=com.inigmasgames.hytalerpg.diagnostics.NativeRpgTickMetrics.enter(store,com.inigmasgames.hytalerpg.diagnostics.NativeRpgTickMetrics.Phase.PROGRESSION)){rewards.deliveryTick();
        }
    }
    }
}
