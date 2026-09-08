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
    @FunctionalInterface public interface Benefit {
        void apply(Store<EntityStore> store,CommandBuffer<EntityStore> buffer,Ref<EntityStore> owner,SkillExecutionContext context);
    }
    @FunctionalInterface public interface Burst {
        int apply(Store<EntityStore> store,CommandBuffer<EntityStore> buffer,Ref<EntityStore> owner,SkillExecutionContext context,
                  Vec3 point,double radius,double coefficient,String effect,boolean frozenSnapshot);
    }
    private final SummonRegistry registry=new SummonRegistry();
    private final DecoyNativeAttraction decoyAttraction;
    private final CorpseLedger corpses;
    private final CombatTrace trace;
    private final Attack attack;
    private final Benefit benefit;
    private final Burst burst;
    public HytaleSummonSystem(CombatTrace trace,HytaleBossBarTracker bosses,Attack attack,CorpseLedger corpses,Benefit benefit,Burst burst){
        decoyAttraction=new DecoyNativeAttraction(registry,bosses);
        this.trace=trace;this.attack=attack;this.corpses=corpses;this.benefit=benefit;this.burst=burst;
    }
    public SummonRegistry registry(){return registry;}
    public CorpseLedger corpses(){return corpses;}
    public void commitConsumable(Store<EntityStore> store,Ref<EntityStore> owner,SkillExecutionContext context){
        boolean consumes=context.profile().summon()!=null&&context.profile().summon().corpseRequired()||context.profile().summonAction()!=null&&
                context.profile().summonAction().kind()==com.inigmasgames.hytalerpg.execution.summon.SummonActionProfile.Kind.CORPSE_BURST;
        if(!consumes)return;
        var source=corpses.available(context.target().entityId(),context.target().worldId()).orElseThrow(()->new IllegalStateException("CORPSE_UNAVAILABLE"));
        if(!HytaleCorpseSystem.valid(store,owner,source))throw new IllegalStateException("CORPSE_NO_LONGER_VALID");
        var claim=corpses.reserve(source.entity(),source.world(),context.request().actorId(),context.rootCastId());
        emitContext(context,RpgTraceEventType.CORPSE_CLAIMED,Map.of("corpse",claim.entity(),"claim",claim.nonce()));
        try{if(!corpses.commit(claim,context.skillInstanceId()))throw new IllegalStateException("CORPSE_COMMIT_REJECTED");}
        catch(RuntimeException failure){corpses.release(claim);throw failure;}
        emitContext(context,RpgTraceEventType.CORPSE_CONSUMED,Map.of("corpse",claim.entity(),"claim",claim.nonce(),"rewardCreated",false,"atPaidCommit",true));
    }
    public Optional<CorpseLedger.Claim> committedCorpse(SkillExecutionContext context){
        return corpses.committed(context.request().actorId(),context.target().worldId(),context.rootCastId(),context.skillInstanceId())
                .filter(c->c.entity().equals(context.target().entityId()));
    }
    public SkillExecutionPort.Validation preflightAction(Store<EntityStore> store,Ref<EntityStore> owner,Stage04SkillProfile profile,Vec3 aim){
        var p=profile.summonAction();boolean found=p.kind()==com.inigmasgames.hytalerpg.execution.summon.SummonActionProfile.Kind.CORPSE_BURST?
                selectCorpse(store,owner,aim,p.range()).isPresent():selectOwned(store,owner,aim,p.range()).isPresent();
        return found?SkillExecutionPort.Validation.pass():SkillExecutionPort.Validation.reject("NO_VALID_CONSUMABLE_"+p.kind());
    }
    public CommittedTarget captureAction(Store<EntityStore> store,Ref<EntityStore> owner,Stage04SkillProfile profile,Vec3 aim){
        var p=profile.summonAction();
        if(p.kind()==com.inigmasgames.hytalerpg.execution.summon.SummonActionProfile.Kind.CORPSE_BURST){
            var source=selectCorpse(store,owner,aim,p.range()).orElseThrow();return new CommittedTarget(source.world(),position(store,owner),source.anchor(),aim,source.entity());
        }
        var lease=selectOwned(store,owner,aim,p.range()).orElseThrow();var target=store.getExternalData().getRefFromUUID(lease.entity());
        return new CommittedTarget(lease.world(),position(store,owner),position(store,target),aim,lease.entity());
    }
    private Optional<SummonRegistry.Lease> selectOwned(Store<EntityStore> store,Ref<EntityStore> owner,Vec3 aim,double range){
        var player=store.getComponent(owner,PlayerRef.getComponentType());var origin=position(store,owner).add(new Vec3(0,1.35,0));var direction=aim.normalized();
        return registry.owned(player.getUuid(),player.getWorldUuid()).stream().filter(l->l.context().compiledPlan().finalTags().contains("TEMPORARY_COMBAT_SUMMON")&&now()<l.expires())
                .filter(l->{var ref=store.getExternalData().getRefFromUUID(l.entity());if(!alive(store,ref))return false;
                    var point=position(store,ref).add(new Vec3(0,.4,0));var delta=point.subtract(origin);double dot=delta.x()*direction.x()+delta.y()*direction.y()+delta.z()*direction.z();
                    return delta.length()<=range&&dot>=0&&delta.subtract(direction.multiply(dot)).length()<=1.25&&HytaleAreaQueries.clear(store,origin,point);})
                .min(Comparator.comparing(SummonRegistry.Lease::entity));
    }
    public SkillExecutionPort.Validation validateAction(Store<EntityStore> store,Ref<EntityStore> owner,SkillExecutionContext context){
        if(context.derivedRelease()||context.target()==null)return SkillExecutionPort.Validation.reject("CONSUMER_REPLAY_FORBIDDEN");
        var target=context.target();var player=store.getComponent(owner,PlayerRef.getComponentType());
        if(!target.worldId().equals(player.getWorldUuid()))return SkillExecutionPort.Validation.reject("CONSUMER_WORLD_CHANGED");
        Vec3 point=target.point();
        if(context.profile().summonAction().kind()==com.inigmasgames.hytalerpg.execution.summon.SummonActionProfile.Kind.CORPSE_BURST){
            if(committedCorpse(context).isEmpty())return SkillExecutionPort.Validation.reject("COMMITTED_CORPSE_PERMIT_UNAVAILABLE");
        }else{
            var lease=registry.owned(player.getUuid(),target.worldId(),target.entityId()).orElse(null);var ref=store.getExternalData().getRefFromUUID(target.entityId());
            if(lease==null||now()>=lease.expires()||!alive(store,ref)||!lease.context().compiledPlan().finalTags().contains("TEMPORARY_COMBAT_SUMMON"))
                return SkillExecutionPort.Validation.reject("OWNED_COMBAT_SUMMON_UNAVAILABLE");
            point=position(store,ref);
        }
        return point.subtract(position(store,owner)).length()<=context.profile().summonAction().range()&&HytaleAreaQueries.clear(store,position(store,owner).add(new Vec3(0,1.35,0)),point.add(new Vec3(0,.1,0)))?
                SkillExecutionPort.Validation.pass():SkillExecutionPort.Validation.reject("CONSUMER_RANGE_OR_LOS");
    }
    public SkillExecutionResult executeAction(Store<EntityStore> store,CommandBuffer<EntityStore> buffer,Ref<EntityStore> owner,SkillExecutionContext context){
        if(buffer==null)throw new IllegalStateException("SUMMON_WORLD_COMMAND_BUFFER_REQUIRED");
        var verdict=validateAction(store,owner,context);if(!verdict.accepted())throw new IllegalStateException(verdict.code());
        var p=context.profile().summonAction();
        if(p.kind()==com.inigmasgames.hytalerpg.execution.summon.SummonActionProfile.Kind.CONSUME_MINION){
            var lease=registry.consume(context.request().actorId(),context.target().worldId(),context.target().entityId(),now(),
                    ()->benefit.apply(store,buffer,owner,context)).orElseThrow(()->new IllegalStateException("SUMMON_ALREADY_CONSUMED"));
            var target=store.getExternalData().getRefFromUUID(lease.entity());if(target!=null&&target.isValid())buffer.tryRemoveEntity(target,RemoveReason.REMOVE);
            emitContext(context,RpgTraceEventType.SUMMON_CONSUMED,Map.of("entity",lease.entity(),"summonRoot",lease.context().rootCastId(),"deathPact",false,"corpseCreated",false));
            return SkillExecutionResult.committed("SUMMON_CONSUMED",1,0);
        }
        var claim=corpses.takeCommitted(context.request().actorId(),context.target().worldId(),context.rootCastId(),context.skillInstanceId())
                .orElseThrow(()->new IllegalStateException("COMMITTED_CORPSE_PERMIT_UNAVAILABLE"));
        int hits=burst.apply(store,buffer,owner,context,claim.source().anchor(),p.radius()*context.compiledPlan().executionModifiers().radiusFactor(),p.coefficient(),"corpse/"+claim.nonce(),false);
        emitContext(context,RpgTraceEventType.CORPSE_BURST,Map.of("corpse",claim.entity(),"targets",hits,"anchor",claim.source().anchor(),"canProc",false));
        return SkillExecutionResult.committed("CORPSE_BURST",hits,0);
    }
    public SkillExecutionPort.Validation preflight(Store<EntityStore> store,Ref<EntityStore> owner,Stage04SkillProfile profile,com.inigmasgames.hytalerpg.domain.CompiledSkillPlan plan,Vec3 aim){
        var spec=profile.summon();
        if(!NPCPlugin.get().hasRoleName(spec.roleId()))return SkillExecutionPort.Validation.reject("SUMMON_ROLE_UNAVAILABLE");
        String admission=registry.admission(store.getComponent(owner,PlayerRef.getComponentType()).getUuid(),plan.summonModifiers().count(spec.count()),spec.decoy());
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
            if(committedCorpse(context).isEmpty())throw new IllegalStateException("COMMITTED_CORPSE_PERMIT_UNAVAILABLE");
            claim=corpses.takeCommitted(context.request().actorId(),context.target().worldId(),context.rootCastId(),context.skillInstanceId()).orElseThrow();
        }
        List<SummonRegistry.Lease> leases;
        try{leases=registry.reserve(context,now(),claim==null?null:claim.source());}
        catch(RuntimeException failure){if(claim!=null)corpses.release(claim);throw failure;}
        try{
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
        if(lease==null){decoyAttraction.release(store,marker.token);buffer.tryRemoveEntity(ref,RemoveReason.REMOVE);return;}
        var owner=store.getExternalData().getRefFromUUID(lease.owner());
        double now=now();String ended=null;
        if(!alive(store,owner))ended="OWNER_GONE";
        else if(!alive(store,ref))ended="SUMMON_DIED";
        else if(now>=lease.expires())ended="EXPIRED";
        else if(position(store,owner).subtract(position(store,ref)).length()>lease.context().profile().summon().leash())ended="LEASH_EXCEEDED";
        if(ended!=null){
            var reason=switch(ended){case "EXPIRED"->SummonRegistry.EndReason.NATURAL_EXPIRY;case "OWNER_GONE"->SummonRegistry.EndReason.OWNER_GONE;
                case "LEASH_EXCEEDED"->SummonRegistry.EndReason.LEASH;default->enemyDeath(store,ref,owner)?SummonRegistry.EndReason.ENEMY_KILL:SummonRegistry.EndReason.OTHER_DEATH;};
            endNative(store,buffer,ref,lease,reason);return;
        }
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
            if(lease.context().profile().summon().decoy()){
                for(var candidate:candidates)if(decoyAttraction.request(store,owner,ref,candidate.ref(),lease))
                    emit(lease,RpgTraceEventType.DECOY_ATTRACT_REQUEST,Map.of("target",store.getComponent(candidate.ref(),UUIDComponent.getComponentType()).getUuid(),"nativeAttackObserved",false,"encounterRole","Wolf_Black"));
                return; // Static idle: never seek, claim an attack, or invoke the damage adapter.
            }
            var target=candidates.isEmpty()?owner:candidates.getFirst().ref();
            var marked=store.getComponent(ref,MarkedEntitySupport.getComponentType());
            marked.setMarkedEntity(MarkedEntitySupport.DEFAULT_TARGET_SLOT,target);
            if(!target.equals(owner)&&position(store,target).subtract(here).length()<=2.5){
                int claimed=registry.claimAttack(lease.token(),now);
                if(claimed>0)attack.apply(store,buffer,owner,target,lease,claimed);
            }
        }catch(RuntimeException failure){
            decoyAttraction.release(store,lease.token());
            registry.remove(lease.token());buffer.tryRemoveEntity(ref,RemoveReason.REMOVE);
            emit(lease,RpgTraceEventType.SUMMON_REJECTED,Map.of("boundary",String.valueOf(failure.getMessage()),"phase","TICK","quarantined",true));
        }
    }
    public static boolean alive(Store<EntityStore> store,Ref<EntityStore> ref){
        if(ref==null||!ref.isValid()||store.getComponent(ref,DeathComponent.getComponentType())!=null)return false;
        var stats=store.getComponent(ref,EntityStatMap.getComponentType());var hp=stats==null?null:stats.get(DefaultEntityStatTypes.getHealth());
        return hp!=null&&hp.get()>0;
    }
    private void endNative(Store<EntityStore> store,CommandBuffer<EntityStore> buffer,Ref<EntityStore> ref,SummonRegistry.Lease lease,SummonRegistry.EndReason reason){
        decoyAttraction.release(store,lease.token());
        Vec3 anchor=position(store,ref);var end=registry.end(lease.token(),reason);
        buffer.tryRemoveEntity(ref,RemoveReason.REMOVE);
        if(end.isEmpty())return;
        if(end.get().deathPact())buffer.run(actual->{
            var owner=actual.getExternalData().getRefFromUUID(lease.owner());
            try{int hits=burst.apply(actual,null,owner,lease.context(),anchor,3,.6,"death-pact/"+lease.token(),true);
                emit(lease,RpgTraceEventType.DEATH_PACT_BURST,Map.of("entity",lease.entity(),"reason",reason,"targets",hits,"anchor",anchor,"canProc",false));}
            catch(RuntimeException failure){emit(lease,RpgTraceEventType.SUMMON_ACTION_REJECTED,Map.of("action","DEATH_PACT","boundary",String.valueOf(failure.getMessage())));}
        });
        emit(lease,RpgTraceEventType.SUMMON_TERMINATED,Map.of("reason",reason));
    }
    private static boolean enemyDeath(Store<EntityStore> store,Ref<EntityStore> ref,Ref<EntityStore> owner){
        if(owner==null||!owner.isValid())return false;var death=store.getComponent(ref,DeathComponent.getComponentType());
        var damage=death==null?null:death.getDeathInfo();
        return damage!=null&&damage.getSource() instanceof Damage.EntitySource source&&source.getRef()!=null&&source.getRef().isValid()
                &&HytaleAreaQueries.hostile(store,source.getRef(),owner);
    }
    private static Vec3 position(Store<EntityStore> store,Ref<EntityStore> ref){var v=store.getComponent(ref,TransformComponent.getComponentType()).getPosition();return new Vec3(v.x(),v.y(),v.z());}
    private static Vector3d vector(Vec3 v){return new Vector3d(v.x(),v.y(),v.z());}
    private static double now(){return System.nanoTime()/1e9;}
    void emit(SummonRegistry.Lease lease,RpgTraceEventType event,Map<String,?> details){
        emitContext(lease.context(),event,details);
    }
    private void emitContext(SkillExecutionContext context,RpgTraceEventType event,Map<String,?> details){
        trace.emit(context.request().actorId(),event,new CombatTrace.Context(context.rootCastId(),context.skillInstanceId(),context.request().correlationId()),details);
    }
    public static final class Removal extends com.hypixel.hytale.component.system.RefSystem<EntityStore>{
        private final HytaleSummonSystem summons;
        public Removal(HytaleSummonSystem summons){this.summons=summons;}
        @Override public Query<EntityStore> getQuery(){return SummonProjection.getComponentType();}
        @Override public void onEntityAdded(Ref<EntityStore> ref,AddReason reason,Store<EntityStore> store,CommandBuffer<EntityStore> buffer){}
        @Override public void onEntityRemove(Ref<EntityStore> ref,RemoveReason reason,Store<EntityStore> store,CommandBuffer<EntityStore> buffer){
            var marker=store.getComponent(ref,SummonProjection.getComponentType());
            summons.decoyAttraction.release(store,marker.token);
            summons.registry.remove(marker.token).ifPresent(lease->summons.emit(lease,RpgTraceEventType.SUMMON_TERMINATED,Map.of("reason","NATIVE_REMOVE_"+reason)));
        }
    }
    /** Native death event claims termination before native corpse removal can win the next tick. */
    public static final class Death extends DeathSystems.OnDeathSystem {
        private final HytaleSummonSystem summons;
        public Death(HytaleSummonSystem summons){this.summons=summons;}
        @Override public Query<EntityStore> getQuery(){return Query.and(SummonProjection.getComponentType(),TransformComponent.getComponentType());}
        @Override public void onComponentAdded(Ref<EntityStore> ref,DeathComponent death,Store<EntityStore> store,CommandBuffer<EntityStore> buffer){
            var marker=store.getComponent(ref,SummonProjection.getComponentType());var lease=summons.registry.find(marker.token).orElse(null);
            if(lease==null)return;var owner=store.getExternalData().getRefFromUUID(lease.owner());
            var damage=death.getDeathInfo();boolean enemy=alive(store,owner)&&damage!=null&&damage.getSource() instanceof Damage.EntitySource source
                    &&source.getRef()!=null&&source.getRef().isValid()&&HytaleAreaQueries.hostile(store,source.getRef(),owner);
            summons.endNative(store,buffer,ref,lease,enemy?SummonRegistry.EndReason.ENEMY_KILL:SummonRegistry.EndReason.OTHER_DEATH);
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
