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
    public void configureGearLoot(com.inigmasgames.hytalerpg.gear.GearLootService service,com.inigmasgames.hytalerpg.gear.HytaleGearEquipment equipment){gearLoot=Objects.requireNonNull(service);gearEquipment=Objects.requireNonNull(equipment);}
    /** Durable reward worker callback; the native world projection has its own acknowledgement. */
    public void lootDelivered(EncounterContributions.DeathPlan plan,com.inigmasgames.hytalerpg.gear.GearLootService.Loot loot){
        var spawn=plan.spawn();String correlation=spawn.enemy().toString();
        var details=new LinkedHashMap<String,Object>();details.put("eventId",spawn.eventId());details.put("runtimeRole",spawn.roleId());
        details.put("canonicalRole",spawn.combatIdentity());details.put("rewardDecision",loot.state());details.put("reason",loot.reason());
        details.put("eligibleContributors",plan.shares().stream().map(s->s.player().toString()).toList());
        if(loot.allocation()!=null)details.put("player",loot.allocation().sponsor());
        emit(null,RpgTraceEventType.GEAR_ROLL,correlation,details);
        if(loot.result()!=null&&loot.result().item()!=null){var item=loot.result().item();
            emit(loot.allocation().sponsor(),RpgTraceEventType.GEAR_GENERATED,correlation,Map.of("eventId",spawn.eventId(),"itemId",item.identity(),
                    "baseId",item.baseId(),"rarity",item.rarity().name(),"affixes",item.affixes().size(),"deliveryState",loot.state()));}
        emit(null,RpgTraceEventType.ENCOUNTER_CLOSED,correlation,Map.of("eventId",spawn.eventId(),"decision",loot.state(),"reason",loot.reason()));
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
    @Override public void close(){closed=true;try{durableEffects.close();}finally{runtime.close();}}
    public void configurePartyProvider(PartyMembershipProvider provider){parties=Objects.requireNonNull(provider);}
    public String partyAvailability(){return parties.availability();}
    public java.util.concurrent.CompletionStage<Boolean> attachObserved(UUID world,UUID enemy,String role,Optional<EnemyRewardRegistry.Spawn> spawn){return runtime.attachNative(world,enemy,role,spawn);}
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
            if(enemy==null||owner==null||!owner.isValid()||store.getComponent(owner,PlayerRef.getComponentType())==null)return;
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
                ||store.getComponent(ref,EntityStore.REGISTRY.getNonSerializedComponentType())!=null;
    }
    private static String biome(Store<EntityStore> store,Ref<EntityStore> ref){
        var nativeWorld=store.getExternalData().getWorld();var generator=nativeWorld.getChunkStore().getGenerator();
        if(!(generator instanceof ChunkGenerator legacy)||!"Default".equals(generatorIdentity(nativeWorld.getWorldConfig().getWorldGenProvider())))return "UNSUPPORTED_WORLD_GENERATOR";
        var p=position(store,ref);
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
    /** Absent optional native health snapshots are an uncredited hit, never a persistence failure. */
    public static boolean creditableNativeHealth(UUID player,double before,double after,double maximum,double nativeAmount){
        return player!=null&&Double.isFinite(before)&&Double.isFinite(after)&&Double.isFinite(maximum)&&Double.isFinite(nativeAmount)
                &&before>=after&&after>=0&&maximum>0&&nativeAmount>=0;
    }
    private void added(Ref<EntityStore> ref,AddReason reason,Store<EntityStore> store){
        if(excluded(store,ref))return;
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
        if(nativeWorldSpawnEvidence(reason,npc.getEnvironment(),npc.getSpawnConfiguration())
                &&resolvedRole.isPresent())
            spawn=difficulty==null?Optional.empty():difficulty.classifyAuthored(registry,world,enemy,roleId,biome(store,ref),EnemyRewardRegistry.Origin.WILD_WORLD_SPAWN,System.currentTimeMillis());
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
            if(gearEquipment!=null)gearMf.put(player,gearEquipment.magicFind(actor,store));
        }
        var provider=parties;
        var facts=PartyMembershipProvider.apply(world,participants,provider);var availability=provider.availability();
        captureDeath(ticket,world,enemy,position(store,ref),System.currentTimeMillis(),facts,availability,Map.copyOf(gearMf));
        }
    }
    public synchronized void captureDeath(UUID world,UUID enemy,Vec3 position,long observedAt,List<EncounterContributions.Participant> eventFacts,String partyAvailability){
        if(eventFacts.size()>EncounterContributions.MAX_CONTRIBUTORS||partyAvailability.length()>512)throw new FileEncounterStore.CapacityRejected();
        try(var ticket=durableEffects.reserve()){captureDeath(ticket,world,enemy,position,observedAt,List.copyOf(eventFacts),partyAvailability,Map.of());}
    }
    private void captureDeath(DurableEncounterEffects.Reservation ticket,UUID world,UUID enemy,Vec3 position,long observedAt,List<EncounterContributions.Participant> facts,String availability,Map<UUID,Double> gearMf){
        long observedNanos=System.nanoTime();
        var prepared=runtime.prepareDeathNative(world,enemy,position,observedAt);
        ticket.submit(prepared,maybe->{
            if(maybe.isEmpty())return;var observation=maybe.get();
            var spawn=observation.snapshot().spawn();
            synchronized(deathOrigins){if(deathOrigins.size()>=512)deathOrigins.remove(deathOrigins.keySet().iterator().next());deathOrigins.put(spawn.eventId(),observedNanos);}
            var resolved=facts.stream().filter(p->spawn.combat()!=null?loadouts.getPresentationView(p.player()).state().difficulty.unlocked(spawn.combat().difficulty()):spawn.milestone()==null||loadouts.getPresentationView(p.player()).state().difficulty.unlocked(spawn.milestone().difficulty())).map(p->{
                double wisdom=kernel.effectiveAttributes().effective(loadouts.rawAttribute(p.player(),com.inigmasgames.hytalerpg.combat.attribute.RpgAttribute.WIS));
                return new EncounterContributions.Participant(p.player(),p.world(),p.position(),loadouts.characterLevel(p.player()),p.loaded(),p.partyId(),
                        spawn.milestone()==null?learning.resolve(spawn.combatIdentity(),spawn.rank(),wisdom).orElse(null):null);
            }).toList();
            if(gearLoot!=null)gearLoot.freezeMagicFind(spawn.eventId(),gearMf);
            var p=runtime.finishDeath(observation,resolved);
            emit(null,RpgTraceEventType.ENCOUNTER_DEATH_FROZEN,enemy.toString(),Map.of("world",world,"enemy",enemy,"eventId",p.spawn().eventId(),"recipients",p.shares().size(),"nativeDeath",true,"partyProvider",availability));
            emit(null,p.shares().isEmpty()?RpgTraceEventType.ENCOUNTER_CLOSED:RpgTraceEventType.REWARD_ELIGIBLE,enemy.toString(),Map.of(
                    "eventId",p.spawn().eventId(),"runtimeRole",p.spawn().roleId(),"canonicalRole",p.spawn().combatIdentity(),
                    "reason",p.shares().isEmpty()?"NO_ELIGIBLE_CONTRIBUTOR":"ELIGIBLE_SHARES","recipients",p.shares().size()));
        });
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
        try(var rpgTickSpan=com.inigmasgames.hytalerpg.diagnostics.NativeRpgTickMetrics.enter(store,com.inigmasgames.hytalerpg.diagnostics.NativeRpgTickMetrics.Phase.PROGRESSION)){rewards.safely("ENCOUNTER_DETACH",()->rewards.detachObserved(world(store),id(store,ref)));
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
        try(var rpgTickSpan=com.inigmasgames.hytalerpg.diagnostics.NativeRpgTickMetrics.enter(store,com.inigmasgames.hytalerpg.diagnostics.NativeRpgTickMetrics.Phase.PROGRESSION)){rewards.safely("NATIVE_DEATH_FREEZE",()->rewards.died(ref,death,store));
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
