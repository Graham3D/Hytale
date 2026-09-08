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
public final class HytaleEncounterRewards {
    private final EnemyRewardRegistry registry=EnemyRewardRegistry.load();
    private final PersistentEncounterRuntime runtime;
    private final RpgLoadoutService loadouts;
    private final RpgSkillTracer trace;
    private boolean failureLogged;
    private long nextDeliveryNanos;
    private volatile PartyMembershipProvider parties=PartyMembershipProvider.UNAVAILABLE;
    public HytaleEncounterRewards(FileEncounterStore store,RpgLoadoutService loadouts,RpgSkillTracer trace){
        this.runtime=new PersistentEncounterRuntime(store,loadouts::awardEarned);this.loadouts=loadouts;this.trace=trace;
    }
    public void invalidateConverted(UUID world,UUID enemy){runtime.disqualify(world,enemy);}
    public void configurePartyProvider(PartyMembershipProvider provider){parties=Objects.requireNonNull(provider);}
    public String partyAvailability(){return parties.availability();}
    /** Called only after the existing native Health write; does not add healing or award mastery. */
    public void healingResolved(Store<EntityStore> store,Ref<EntityStore> beneficiary,com.inigmasgames.hytalerpg.execution.SkillExecutionContext context,double before,double after){
        safely("NATIVE_HEAL_CONTRIBUTION",()->{
            if(!Double.isFinite(before)||!Double.isFinite(after)||after<=before)return;
            var healer=store.getExternalData().getRefFromUUID(context.request().actorId());
            if(healer==null||!healer.isValid()||store.getComponent(healer,PlayerRef.getComponentType())==null||!HytaleSupportSystem.eligibleAlly(store,healer,beneficiary))return;
            var recipient=id(store,beneficiary);if(recipient==null)return;
            int count=runtime.heal(world(store),context.request().actorId(),recipient,after-before,true,System.currentTimeMillis());
            if(count>0)emit(context.request().actorId(),RpgTraceEventType.ENCOUNTER_CONTRIBUTION_OBSERVED,context.request().correlationId(),
                    Map.of("kind","HEAL","beneficiary",recipient,"healthBefore",before,"healthAfter",after,"actualHealing",after-before,"eligibleEncounters",count,"rootCastId",context.rootCastId(),"skillInstanceId",context.skillInstanceId(),"masteryAwarded",false));
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
            if(runtime.absorb(world(store),enemy,actor,absorption.amount(),true,System.currentTimeMillis()))
                emit(actor,RpgTraceEventType.ENCOUNTER_CONTRIBUTION_OBSERVED,c.request().correlationId(),Map.of("kind","ABSORB","enemy",enemy,"beneficiary",id(store,recipient),"actuallyAbsorbed",absorption.amount(),"rootCastId",c.rootCastId(),"skillInstanceId",c.skillInstanceId(),"masteryAwarded",false));
        });
    }
    private static UUID world(Store<EntityStore> store){return store.getExternalData().getWorld().getWorldConfig().getUuid();}
    private static UUID id(Store<EntityStore> store,Ref<EntityStore> ref){var component=ref==null||!ref.isValid()?null:store.getComponent(ref,UUIDComponent.getComponentType());return component==null?null:component.getUuid();}
    private static Vec3 position(Store<EntityStore> store,Ref<EntityStore> ref){var p=store.getComponent(ref,TransformComponent.getComponentType()).getPosition();return new Vec3(p.x(),p.y(),p.z());}
    private static boolean excluded(Store<EntityStore> store,Ref<EntityStore> ref){
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
    private void added(Ref<EntityStore> ref,AddReason reason,Store<EntityStore> store){
        if(excluded(store,ref))return;
        var npc=store.getComponent(ref,NPCEntity.getComponentType());var world=world(store);var enemy=id(store,ref);
        // WorldSpawnJobSystems initializes these before NPCPlugin.Store.addEntity(SPAWN).
        // Manual NPCPlugin spawns retain Integer.MIN_VALUE and cannot masquerade as natural spawns.
        Optional<EnemyRewardRegistry.Spawn> spawn=Optional.empty();
        if(nativeWorldSpawnEvidence(reason,npc.getEnvironment(),npc.getSpawnConfiguration())
                &&registry.roles().stream().anyMatch(r->r.roleId().equals(npc.getRoleName())))
            spawn=registry.classify(world,enemy,npc.getRoleName(),biome(store,ref),EnemyRewardRegistry.Origin.WILD_WORLD_SPAWN,System.currentTimeMillis());
        if(runtime.attach(world,enemy,npc.getRoleName(),spawn)){
            var captured=runtime.spawn(world,enemy).orElseThrow();
            emit(null,RpgTraceEventType.ENCOUNTER_CONTEXT_RESTORED,enemy.toString(),Map.of("world",world,"enemy",enemy,"role",captured.roleId(),"biome",captured.biomeKey(),"combatLevel",captured.level(),"nativeAddReason",reason.name(),"awardCreated",false));
        }
    }
    private void damage(int index,ArchetypeChunk<EntityStore> chunk,Store<EntityStore> store,Damage damage){
        var target=chunk.getReferenceTo(index);var world=world(store);var enemy=id(store,target);
        if(!runtime.contains(world,enemy)||damage.isCancelled())return;
        if(excluded(store,target)||!runtime.spawn(world,enemy).orElseThrow().roleId().equals(store.getComponent(target,NPCEntity.getComponentType()).getRoleName())){runtime.disqualify(world,enemy);return;}
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
        if(actor==null||!actor.isValid()||store.getComponent(actor,PlayerRef.getComponentType())==null||!HytaleAreaQueries.hostile(store,target,actor))return;
        var hp=chunk.getComponent(index,EntityStatMap.getComponentType()).get(DefaultEntityStatTypes.getHealth());if(hp==null)return;
        double before=SupportDamageSystems.observedHealthBefore(damage);var player=id(store,actor);
        if(runtime.damage(world,enemy,player,before,hp.get(),hp.getMax(),true,System.currentTimeMillis())){
            var details=new LinkedHashMap<String,Object>();details.put("world",world);details.put("enemy",enemy);details.put("kind","DAMAGE");
            details.put("healthBefore",before);details.put("healthAfter",hp.get());details.put("actualHealthLost",before-hp.get());details.put("nativeAmount",damage.getAmount());
            details.put("rootCastId",metadata==null?"":metadata.rootCastId());details.put("skillInstanceId",metadata==null?"":metadata.skillInstanceId());
            emit(player,RpgTraceEventType.ENCOUNTER_CONTRIBUTION_OBSERVED,metadata==null?enemy.toString():metadata.correlationId(),details);
        }
    }
    private void died(Ref<EntityStore> ref,DeathComponent death,Store<EntityStore> store){
        if(death.getDeathInfo()==null)return;
        var hp=store.getComponent(ref,EntityStatMap.getComponentType()).get(DefaultEntityStatTypes.getHealth());if(hp==null||hp.get()>hp.getMin())return;
        var world=world(store);var enemy=id(store,ref);if(!runtime.contains(world,enemy))return;
        if(excluded(store,ref)||!runtime.spawn(world,enemy).orElseThrow().roleId().equals(store.getComponent(ref,NPCEntity.getComponentType()).getRoleName())){runtime.disqualify(world,enemy);return;}
        var participants=new ArrayList<EncounterContributions.Participant>();
        // Query only previously credited identities, at most 256. No all-player/world scan.
        for(var player:runtime.contributors(world,enemy)){
            var actor=store.getExternalData().getRefFromUUID(player);
            if(actor==null||!actor.isValid()||store.getComponent(actor,PlayerRef.getComponentType())==null||store.getComponent(actor,TransformComponent.getComponentType())==null)continue;
            participants.add(new EncounterContributions.Participant(player,world,position(store,actor),loadouts.characterLevel(player),true,null));
        }
        var provider=parties;
        var plan=runtime.death(world,enemy,position(store,ref),System.currentTimeMillis(),PartyMembershipProvider.apply(world,participants,provider));
        plan.ifPresent(p->emit(null,RpgTraceEventType.ENCOUNTER_DEATH_FROZEN,enemy.toString(),Map.of("world",world,"enemy",enemy,"eventId",p.spawn().eventId(),"recipients",p.shares().size(),"nativeDeath",true,"partyProvider",provider.availability())));
    }
    private synchronized void deliver(){
        long now=System.nanoTime();if(now<nextDeliveryNanos)return;nextDeliveryNanos=now+1_000_000_000L;
        runtime.drain(8); // Global, not multiplied by world/player count. Existing player ledger traces actual commits.
    }
    private void safely(String boundary,Runnable operation){
        try{operation.run();}catch(RuntimeException error){
            synchronized(this){if(failureLogged)return;failureLogged=true;}
            com.hypixel.hytale.logger.HytaleLogger.getLogger().atWarning().log("RPG_ENCOUNTER_FAILURE boundary=%s error=%s detail=%s awardsUnavailable=%s nativeCombatUnchanged=true",boundary,error.getClass().getName(),String.valueOf(error.getMessage()),runtime.unavailable());
            emit(null,RpgTraceEventType.ENCOUNTER_REWARD_REJECTED,"",Map.of("boundary",boundary,"error",String.valueOf(error.getMessage()),"awardsUnavailable",runtime.unavailable()));
        }
    }
    private void emit(UUID actor,RpgTraceEventType event,String correlation,Map<String,?> details){trace.trace(RpgTraceRecord.create(actor,event,correlation,details));}
    public static final class Tracking extends RefSystem<EntityStore>{
        private final HytaleEncounterRewards rewards;public Tracking(HytaleEncounterRewards rewards){this.rewards=rewards;}
        @Override public Query<EntityStore> getQuery(){return Query.and(NPCEntity.getComponentType(),UUIDComponent.getComponentType(),TransformComponent.getComponentType());}
        @Override public void onEntityAdded(Ref<EntityStore> ref,AddReason reason,Store<EntityStore> store,CommandBuffer<EntityStore> buffer){rewards.safely("NATIVE_SPAWN_CONTEXT",()->rewards.added(ref,reason,store));}
        @Override public void onEntityRemove(Ref<EntityStore> ref,RemoveReason reason,Store<EntityStore> store,CommandBuffer<EntityStore> buffer){rewards.runtime.detach(world(store),id(store,ref));}
    }
    public static final class Inspect extends DamageEventSystem{
        private static final com.hypixel.hytale.server.core.meta.MetaKey<Boolean> OBSERVED=Damage.META_REGISTRY.registerMetaObject(ignored->false,false,"InigmasGames:EncounterObserved",Codec.BOOLEAN);
        private final HytaleEncounterRewards rewards;public Inspect(HytaleEncounterRewards rewards){this.rewards=rewards;}
        @Override public Query<EntityStore> getQuery(){return Query.and(NPCEntity.getComponentType(),UUIDComponent.getComponentType(),EntityStatMap.getComponentType());}
        @Override public SystemGroup<EntityStore> getGroup(){return DamageModule.get().getInspectDamageGroup();}
        @Override public Set<Dependency<EntityStore>> getDependencies(){return Set.of(new SystemDependency<>(Order.AFTER,DamageSystems.ApplyDamage.class),new SystemDependency<>(Order.BEFORE,SupportDamageSystems.Reflect.class));}
        @Override public void handle(int i,ArchetypeChunk<EntityStore> chunk,Store<EntityStore> store,CommandBuffer<EntityStore> buffer,Damage damage){
            if(Boolean.TRUE.equals(damage.getIfPresentMetaObject(OBSERVED)))return;damage.putMetaObject(OBSERVED,true);rewards.safely("NATIVE_CONTRIBUTION_INSPECT",()->rewards.damage(i,chunk,store,damage));
        }
    }
    public static final class Death extends DeathSystems.OnDeathSystem{
        private final HytaleEncounterRewards rewards;public Death(HytaleEncounterRewards rewards){this.rewards=rewards;}
        @Override public Query<EntityStore> getQuery(){return Query.and(NPCEntity.getComponentType(),UUIDComponent.getComponentType(),EntityStatMap.getComponentType(),TransformComponent.getComponentType());}
        @Override public void onComponentAdded(Ref<EntityStore> ref,DeathComponent death,Store<EntityStore> store,CommandBuffer<EntityStore> buffer){rewards.safely("NATIVE_DEATH_FREEZE",()->rewards.died(ref,death,store));}
    }
    public static final class Delivery extends TickingSystem<EntityStore>{
        private final HytaleEncounterRewards rewards;public Delivery(HytaleEncounterRewards rewards){this.rewards=rewards;}
        @Override public void tick(float delta,int systemIndex,Store<EntityStore> store){rewards.safely("DURABLE_DEATH_DELIVERY",rewards::deliver);}
    }
}
