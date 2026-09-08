package com.inigmasgames.hytalerpg.execution.hytale;

import com.hypixel.hytale.component.*;
import com.hypixel.hytale.component.dependency.*;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.tick.EntityTickingSystem;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.modules.entity.component.*;
import com.hypixel.hytale.server.core.modules.entity.damage.*;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatMap;
import com.hypixel.hytale.server.core.modules.entitystats.asset.DefaultEntityStatTypes;
import com.hypixel.hytale.server.core.modules.entitystats.modifier.*;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.NPCPlugin;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import com.hypixel.hytale.server.npc.role.support.MarkedEntitySupport;
import com.hypixel.hytale.server.npc.systems.RoleSystems;
import com.hypixel.hytale.server.spawning.SpawnTestResult;
import com.inigmasgames.hytalerpg.combat.diagnostics.CombatTrace;
import com.inigmasgames.hytalerpg.diagnostics.RpgTraceEventType;
import com.inigmasgames.hytalerpg.execution.*;
import com.inigmasgames.hytalerpg.execution.area.AreaGeometry;
import com.inigmasgames.hytalerpg.execution.math.Vec3;
import com.inigmasgames.hytalerpg.execution.summon.SummonRegistry;
import com.inigmasgames.hytalerpg.execution.summon.CorpseLedger;
import java.util.*;
import org.joml.Vector3d;

/** Native motion and Health, RPG targeting/damage/ownership. No inherited native combat or loot scripts. */
public final class HytaleSummonSystem extends EntityTickingSystem<EntityStore> {
    @FunctionalInterface public interface Attack {
        void apply(Store<EntityStore> store,CommandBuffer<EntityStore> buffer,Ref<EntityStore> owner,
                   Ref<EntityStore> target,SummonRegistry.Lease lease,int attack);
    }
    private final SummonRegistry registry=new SummonRegistry();
    private final CorpseLedger corpses;
    private final CombatTrace trace;
    private final Attack attack;
    public HytaleSummonSystem(CombatTrace trace,Attack attack,CorpseLedger corpses){this.trace=trace;this.attack=attack;this.corpses=corpses;}
    public SummonRegistry registry(){return registry;}
    public CorpseLedger corpses(){return corpses;}
    public SkillExecutionPort.Validation preflight(Store<EntityStore> store,Ref<EntityStore> owner,Stage04SkillProfile profile,com.inigmasgames.hytalerpg.domain.CompiledSkillPlan plan,Vec3 aim){
        var spec=profile.summon();
        if(!NPCPlugin.get().hasRoleName(spec.roleId()))return SkillExecutionPort.Validation.reject("SUMMON_ROLE_UNAVAILABLE");
        String admission=registry.admission(store.getComponent(owner,PlayerRef.getComponentType()).getUuid(),plan.summonModifiers().count(spec.count()));
        if(!admission.equals("PASS"))return SkillExecutionPort.Validation.reject(admission);
        if(spec.corpseRequired())return selectCorpse(store,owner,aim,spec.range()).isPresent()?SkillExecutionPort.Validation.pass():
                SkillExecutionPort.Validation.reject("NO_ELIGIBLE_CLASSIFIED_NATIVE_CORPSE");
        return placement(store,owner,aim,spec.range()).isPresent()?SkillExecutionPort.Validation.pass():
                SkillExecutionPort.Validation.reject("SUMMON_NO_VALID_GROUND");
    }
    public CommittedTarget capture(Store<EntityStore> store,Ref<EntityStore> owner,Stage04SkillProfile profile,Vec3 aim){
        if(profile.summon().corpseRequired()){
            var corpse=selectCorpse(store,owner,aim,profile.summon().range()).orElseThrow();
            return new CommittedTarget(corpse.world(),position(store,owner),corpse.anchor(),aim,corpse.entity());
        }
        var origin=position(store,owner);var point=placement(store,owner,aim,profile.summon().range()).orElseThrow();
        return new CommittedTarget(store.getComponent(owner,PlayerRef.getComponentType()).getWorldUuid(),origin,point,aim,null);
    }
    private Optional<CorpseLedger.Source> selectCorpse(Store<EntityStore> store,Ref<EntityStore> owner,Vec3 aim,double range){
        var origin=position(store,owner).add(new Vec3(0,1.35,0));var direction=aim.normalized();
        var world=store.getComponent(owner,PlayerRef.getComponentType()).getWorldUuid();
        return corpses.available(world).stream().filter(c->c.anchor().subtract(origin).length()<=range)
                .filter(c->{var delta=c.anchor().add(new Vec3(0,.4,0)).subtract(origin);double dot=delta.x()*direction.x()+delta.y()*direction.y()+delta.z()*direction.z();
                    return dot>=0&&delta.subtract(direction.multiply(dot)).length()<=1.25;})
                .filter(c->HytaleCorpseSystem.valid(store,owner,c)&&HytaleAreaQueries.clear(store,origin,c.anchor().add(new Vec3(0,.1,0))))
                .min(Comparator.<CorpseLedger.Source>comparingDouble(c->c.anchor().subtract(origin).length()).thenComparing(CorpseLedger.Source::entity));
    }
    private Optional<Vec3> placement(Store<EntityStore> store,Ref<EntityStore> owner,Vec3 aim,double range){
        var origin=position(store,owner);
        return HytaleAreaQueries.ground(store,origin.add(new Vec3(0,1.35,0)),aim,range)
                .filter(p->p.subtract(origin).length()<=range);
    }
    public SkillExecutionResult execute(Store<EntityStore> store,CommandBuffer<EntityStore> buffer,Ref<EntityStore> owner,SkillExecutionContext context){
        if(buffer==null)throw new IllegalStateException("SUMMON_WORLD_COMMAND_BUFFER_REQUIRED");
        CorpseLedger.Claim claim=null;
        if(context.profile().summon().corpseRequired()){
            var source=corpses.available(context.target().entityId(),context.target().worldId()).orElseThrow(()->new IllegalStateException("CORPSE_UNAVAILABLE"));
            if(!HytaleCorpseSystem.valid(store,owner,source))throw new IllegalStateException("CORPSE_NO_LONGER_VALID");
            claim=corpses.reserve(source.entity(),source.world(),context.request().actorId(),context.rootCastId());
        }
        List<SummonRegistry.Lease> leases;
        try{leases=registry.reserve(context,now(),claim==null?null:claim.source());}
        catch(RuntimeException failure){if(claim!=null)corpses.release(claim);throw failure;}
        try{
            if(claim!=null){
                emit(leases.getFirst(),RpgTraceEventType.CORPSE_CLAIMED,Map.of("corpse",claim.entity(),"claim",claim.nonce()));
                if(!corpses.consume(claim))throw new IllegalStateException("CORPSE_COMMIT_REJECTED");
                emit(leases.getFirst(),RpgTraceEventType.CORPSE_CONSUMED,Map.of("corpse",claim.entity(),"claim",claim.nonce(),"rewardCreated",false));
            }
            emit(leases.getFirst(),RpgTraceEventType.SUMMON_SPAWN_REQUEST,Map.of("count",leases.size(),"role",context.profile().summon().roleId()));
            // NPCPlugin uses Store.addEntity: execute outside entity iteration, on this SAME native world thread.
            buffer.run(actual->spawnBatch(actual,owner,context,leases));
        }catch(RuntimeException failure){leases.forEach(lease->registry.remove(lease.token()));if(claim!=null)corpses.release(claim);throw failure;}
        return SkillExecutionResult.committed("SUMMON_SPAWN_QUEUED",0,0);
    }
    private void spawnBatch(Store<EntityStore> store,Ref<EntityStore> owner,SkillExecutionContext context,List<SummonRegistry.Lease> leases){
        List<Ref<EntityStore>> created=new ArrayList<>();
        try{
            if(!alive(store,owner)||!context.target().worldId().equals(store.getComponent(owner,PlayerRef.getComponentType()).getWorldUuid()))
                throw new IllegalStateException("SUMMON_OWNER_GONE");
            var point=context.target().point();var origin=position(store,owner);var spec=context.profile().summon();
            if(origin.subtract(point).length()>spec.range()||!HytaleAreaQueries.clear(store,origin.add(new Vec3(0,1.35,0)),point))
                throw new IllegalStateException("SUMMON_COMMITTED_PLACEMENT_INVALID");
            var points=new ArrayList<Vec3>();
            for(var offset:com.inigmasgames.hytalerpg.execution.summon.SummonFormation.points(point,leases.size())){
                var grounded=leases.size()==1?offset:HytaleAreaQueries.ground(store,offset.add(new Vec3(0,1,0)),new Vec3(0,-1,0),2)
                        .orElseThrow(()->new IllegalStateException("SUMMON_BATCH_NO_GROUND"));
                if(origin.subtract(grounded).length()>spec.range()||!HytaleAreaQueries.clear(store,origin.add(new Vec3(0,1.35,0)),grounded))
                    throw new IllegalStateException("SUMMON_BATCH_RANGE_OR_LOS");
                points.add(grounded);
            }
            for(int index=0;index<leases.size();index++){
                var lease=leases.get(index);
                if(registry.find(lease.token()).isEmpty()||now()>=lease.expires())throw new IllegalStateException("SUMMON_RESERVATION_CANCELLED");
                var at=points.get(index);
                var result=NPCPlugin.get().spawnNPCWithSpaceValidation(store,lease.roleId(),null,vector(at),
                        store.getComponent(owner,TransformComponent.getComponentType()).getRotation(),(npc,ref,actual)->{
                            created.add(ref); // Track first; any subsequent failure has an exact rollback target.
                            actual.addComponent(ref,EntityStore.REGISTRY.getNonSerializedComponentType(),NonSerialized.get());
                            npc.getRole().setDeathItemsDropped();
                            actual.addComponent(ref,SummonProjection.getComponentType(),new SummonProjection(lease.token()));
                            var stats=actual.getComponent(ref,EntityStatMap.getComponentType());
                            int health=DefaultEntityStatTypes.getHealth();var nativeHealth=stats.get(health);
                            double maximum=lease.maximumHealth();
                            stats.putModifier(health,"RPG_SUMMON_MAX",new StaticModifier(Modifier.ModifierTarget.MAX,
                                    StaticModifier.CalculationType.ADDITIVE,(float)(maximum-nativeHealth.getMax())));
                            stats.update();stats.setStatValue(health,(float)maximum);
                            if(Math.abs(stats.get(health).getMax()-maximum)>.001)throw new IllegalStateException("SUMMON_HEALTH_PROJECTION_MISMATCH");
                            var id=actual.getComponent(ref,UUIDComponent.getComponentType()).getUuid();
                            if(!registry.activate(lease,id,now()))throw new IllegalStateException("SUMMON_ACTIVATION_REJECTED");
                        });
                if(result!=SpawnTestResult.TEST_OK)throw new IllegalStateException("NATIVE_"+result);
            }
            for(var lease:leases)emit(lease,RpgTraceEventType.SUMMON_SPAWNED,Map.of("entity",lease.entity(),"rewardEligible",false,
                    "lifetime",Math.max(1,context.profile().summon().lifetime()*context.compiledPlan().summonModifiers().lifetimeFactor()),"nativeDamageInteractions",0,"serialized",false));
        }catch(RuntimeException failure){
            for(var ref:created)if(ref.isValid())store.removeEntity(ref,RemoveReason.REMOVE);
            for(var lease:leases)registry.remove(lease.token());
            emit(leases.getFirst(),RpgTraceEventType.SUMMON_REJECTED,Map.of("boundary",String.valueOf(failure.getMessage()),
                    "rollbackEntities",created.size(),"resourceRefund",false));
        }
    }
    public void cancel(UUID owner,String reason){corpses.cancelUncommitted(owner);for(var lease:registry.cancel(owner))emit(lease,RpgTraceEventType.SUMMON_TERMINATED,Map.of("reason",reason));}
    @Override public Query<EntityStore> getQuery(){return Query.and(SummonProjection.getComponentType(),NPCEntity.getComponentType(),TransformComponent.getComponentType());}
    @Override public Set<Dependency<EntityStore>> getDependencies(){return Set.of(new SystemDependency<>(Order.BEFORE,RoleSystems.BehaviourTickSystem.class));}
    @Override public void tick(float delta,int index,ArchetypeChunk<EntityStore> chunk,Store<EntityStore> store,CommandBuffer<EntityStore> buffer){
        var ref=chunk.getReferenceTo(index);var marker=chunk.getComponent(index,SummonProjection.getComponentType());
        var lease=registry.find(marker.token).orElse(null);
        if(lease==null){buffer.tryRemoveEntity(ref,RemoveReason.REMOVE);return;}
        var owner=store.getExternalData().getRefFromUUID(lease.owner());
        double now=now();String ended=null;
        if(!alive(store,owner))ended="OWNER_GONE";
        else if(!alive(store,ref))ended="SUMMON_DIED";
        else if(now>=lease.expires())ended="EXPIRED";
        else if(position(store,owner).subtract(position(store,ref)).length()>lease.context().profile().summon().leash())ended="LEASH_EXCEEDED";
        if(ended!=null){registry.remove(lease.token());emit(lease,RpgTraceEventType.SUMMON_TERMINATED,Map.of("reason",ended));buffer.tryRemoveEntity(ref,RemoveReason.REMOVE);return;}
        if(now<marker.nextQuery)return;marker.nextQuery=now+.1;
        try{
            var origin=position(store,owner);var here=position(store,ref);
            var shape=new AreaGeometry(AreaGeometry.Kind.DISC,origin.add(new Vec3(0,-1,0)),new Vec3(1,0,0),24,0,0,0,3);
            var result=HytaleAreaQueries.query(store,owner,shape,256);
            if(result.overflow())throw new IllegalStateException("SUMMON_TARGET_QUERY_CAP");
            var candidates=result.candidates().stream().filter(c->alive(store,c.ref()))
                    .filter(c->store.getComponent(c.ref(),SummonProjection.getComponentType())==null)
                    .filter(c->HytaleAreaQueries.clear(store,here.add(new Vec3(0,.5,0)),c.bounds().centre()))
                    .sorted(Comparator.<HytaleAreaQueries.Candidate>comparingDouble(c->c.bounds().centre().subtract(here).length())
                            .thenComparing(c->store.getComponent(c.ref(),UUIDComponent.getComponentType()).getUuid())).toList();
            if(candidates.size()>64)throw new IllegalStateException("SUMMON_ACCEPTED_TARGET_CAP");
            var target=candidates.isEmpty()?owner:candidates.getFirst().ref();
            var marked=store.getComponent(ref,MarkedEntitySupport.getComponentType());
            marked.setMarkedEntity(MarkedEntitySupport.DEFAULT_TARGET_SLOT,target);
            if(!target.equals(owner)&&position(store,target).subtract(here).length()<=2.5){
                int claimed=registry.claimAttack(lease.token(),now);
                if(claimed>0)attack.apply(store,buffer,owner,target,lease,claimed);
            }
        }catch(RuntimeException failure){
            registry.remove(lease.token());buffer.tryRemoveEntity(ref,RemoveReason.REMOVE);
            emit(lease,RpgTraceEventType.SUMMON_REJECTED,Map.of("boundary",String.valueOf(failure.getMessage()),"phase","TICK","quarantined",true));
        }
    }
    public static boolean alive(Store<EntityStore> store,Ref<EntityStore> ref){
        if(ref==null||!ref.isValid()||store.getComponent(ref,DeathComponent.getComponentType())!=null)return false;
        var stats=store.getComponent(ref,EntityStatMap.getComponentType());var hp=stats==null?null:stats.get(DefaultEntityStatTypes.getHealth());
        return hp!=null&&hp.get()>0;
    }
    private static Vec3 position(Store<EntityStore> store,Ref<EntityStore> ref){var v=store.getComponent(ref,TransformComponent.getComponentType()).getPosition();return new Vec3(v.x(),v.y(),v.z());}
    private static Vector3d vector(Vec3 v){return new Vector3d(v.x(),v.y(),v.z());}
    private static double now(){return System.nanoTime()/1e9;}
    void emit(SummonRegistry.Lease lease,RpgTraceEventType event,Map<String,?> details){
        var context=lease.context();trace.emit(lease.owner(),event,new CombatTrace.Context(context.rootCastId(),context.skillInstanceId(),context.request().correlationId()),details);
    }
    public static final class Removal extends com.hypixel.hytale.component.system.RefSystem<EntityStore>{
        private final HytaleSummonSystem summons;
        public Removal(HytaleSummonSystem summons){this.summons=summons;}
        @Override public Query<EntityStore> getQuery(){return SummonProjection.getComponentType();}
        @Override public void onEntityAdded(Ref<EntityStore> ref,AddReason reason,Store<EntityStore> store,CommandBuffer<EntityStore> buffer){}
        @Override public void onEntityRemove(Ref<EntityStore> ref,RemoveReason reason,Store<EntityStore> store,CommandBuffer<EntityStore> buffer){
            var marker=store.getComponent(ref,SummonProjection.getComponentType());
            summons.registry.remove(marker.token).ifPresent(lease->summons.emit(lease,RpgTraceEventType.SUMMON_TERMINATED,Map.of("reason","NATIVE_REMOVE_"+reason)));
        }
    }
    /** Defense in depth: owned actors have no native attack roots, and cannot damage or farm each other. */
    public static final class DamageGuard extends DamageEventSystem {
        private final HytaleSummonSystem summons;
        public DamageGuard(HytaleSummonSystem summons){this.summons=summons;}
        @Override public Query<EntityStore> getQuery(){return Query.any();}
        @Override public SystemGroup<EntityStore> getGroup(){return DamageModule.get().getFilterDamageGroup();}
        @Override public void handle(int index,ArchetypeChunk<EntityStore> chunk,Store<EntityStore> store,CommandBuffer<EntityStore> buffer,Damage damage){
            if(damage.getSource() instanceof Damage.EntitySource source){
                var attacker=source.getRef();
                if(attacker!=null&&attacker.isValid()&&store.getComponent(attacker,SummonProjection.getComponentType())!=null)
                    damage.setCancelled(true);
                var target=chunk.getReferenceTo(index);var marker=store.getComponent(target,SummonProjection.getComponentType());
                if(marker!=null){
                    var lease=summons.registry.find(marker.token).orElse(null);
                    var owner=lease==null?null:store.getExternalData().getRefFromUUID(lease.owner());
                    if(owner==null||attacker==null||!attacker.isValid()||!HytaleAreaQueries.hostile(store,attacker,owner))damage.setCancelled(true);
                }
            }
        }
    }
}
