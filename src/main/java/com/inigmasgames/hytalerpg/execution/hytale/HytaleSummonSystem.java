package com.inigmasgames.hytalerpg.execution.hytale;

import com.hypixel.hytale.component.*;
import com.hypixel.hytale.component.dependency.*;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.tick.EntityTickingSystem;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.entity.nameplate.Nameplate;
import com.hypixel.hytale.server.core.asset.type.attitude.Attitude;
import com.hypixel.hytale.server.core.modules.entity.component.*;
import com.hypixel.hytale.server.core.modules.i18n.I18nModule;
import com.hypixel.hytale.server.core.modules.entity.damage.*;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatMap;
import com.hypixel.hytale.server.core.modules.entitystats.asset.DefaultEntityStatTypes;
import com.hypixel.hytale.server.core.modules.entitystats.modifier.*;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.NPCPlugin;
import com.hypixel.hytale.server.npc.blackboard.Blackboard;
import com.hypixel.hytale.server.npc.blackboard.view.attitude.AttitudeView;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import com.hypixel.hytale.server.npc.role.support.MarkedEntitySupport;
import com.hypixel.hytale.server.npc.role.support.StateSupport;
import com.hypixel.hytale.server.npc.role.support.WorldSupport;
import com.hypixel.hytale.server.npc.systems.RoleSystems;
import com.hypixel.hytale.server.spawning.SpawnTestResult;
import com.inigmasgames.hytalerpg.combat.diagnostics.CombatTrace;
import com.inigmasgames.hytalerpg.diagnostics.RpgTraceEventType;
import com.inigmasgames.hytalerpg.execution.*;
import com.inigmasgames.hytalerpg.execution.area.AreaGeometry;
import com.inigmasgames.hytalerpg.execution.math.Vec3;
import com.inigmasgames.hytalerpg.execution.summon.SummonRegistry;
import com.inigmasgames.hytalerpg.execution.summon.CorpseLedger;
import com.inigmasgames.hytalerpg.execution.summon.IronSentinelBinding;
import com.inigmasgames.hytalerpg.execution.summon.SummonDistanceRecovery;
import com.inigmasgames.hytalerpg.gear.HytaleGearLoot;
import java.util.*;
import org.joml.Vector3d;

/** Native motion and Health, RPG targeting/damage/ownership. No inherited native combat or loot scripts. */
public final class HytaleSummonSystem extends EntityTickingSystem<EntityStore> {
    private static final String SKELETON_ARCHERS="summon_skeleton_archers";
    /** 20-30 feet in Hytale's metre-scale coordinates. */
    public static final double OWNER_IDLE_RADIUS=8.0;
    /** Hysteresis prevents repeated Idle/ReturnHome transitions at the outer radius. */
    public static final double OWNER_FOLLOW_STOP_RADIUS=6.0;
    private static final int PENDING_ARROW_CAP=256,PENDING_ARROW_DRAIN=64;
    @FunctionalInterface public interface Attack {
        void apply(Store<EntityStore> store,CommandBuffer<EntityStore> buffer,Ref<EntityStore> owner,
                   Ref<EntityStore> target,SummonRegistry.Lease lease,int attack,double nativeAmount,int nativeCauseIndex);
    }
    @FunctionalInterface public interface Benefit {
        void apply(Store<EntityStore> store,CommandBuffer<EntityStore> buffer,Ref<EntityStore> owner,SkillExecutionContext context);
    }
    @FunctionalInterface public interface Burst {
        int apply(Store<EntityStore> store,CommandBuffer<EntityStore> buffer,Ref<EntityStore> owner,SkillExecutionContext context,
                  Vec3 point,double radius,double coefficient,String effect,boolean frozenSnapshot);
    }
    private final SummonRegistry registry=new SummonRegistry();
    private volatile HytaleGearLoot gearLoot;
    private final Map<String,IronSentinelBinding> preparedIron=new java.util.concurrent.ConcurrentHashMap<>();
    private final Map<UUID,IronSentinelBinding> spawningIron=new java.util.concurrent.ConcurrentHashMap<>();
    private final Map<UUID,IronSentinelBinding> activeIron=new java.util.concurrent.ConcurrentHashMap<>();
    private final Set<UUID> pendingIronOwners=java.util.concurrent.ConcurrentHashMap.newKeySet();
    private final Set<UUID> sentinelAffixLabels=java.util.concurrent.ConcurrentHashMap.newKeySet();
    private final Set<String> preparedReplacementCasts=java.util.concurrent.ConcurrentHashMap.newKeySet();
    private final Set<UUID> replacingIronTokens=java.util.concurrent.ConcurrentHashMap.newKeySet();
    private final Map<UUID,SummonRegistry.Lease> outgoingIron=new java.util.concurrent.ConcurrentHashMap<>();
    private final Set<UUID> restoreInFlight=java.util.concurrent.ConcurrentHashMap.newKeySet();
    private final Map<UUID,Double> restoreRetryAt=new java.util.concurrent.ConcurrentHashMap<>();
    private final Map<UUID,Double> lastHealthCheckpoint=new java.util.concurrent.ConcurrentHashMap<>();
    private final Set<String> abandonedIron=java.util.concurrent.ConcurrentHashMap.newKeySet();
    private final DecoyNativeAttraction decoyAttraction;
    private final CorpseLedger corpses;
    private final CombatTrace trace;
    private final Attack attack;
    private final Benefit benefit;
    private final Burst burst;
    private final Map<AttitudeView,Boolean> allegianceInstalled=Collections.synchronizedMap(new WeakHashMap<>());
    private final java.util.concurrent.ConcurrentLinkedQueue<PendingArrowHit> pendingArrowHits=new java.util.concurrent.ConcurrentLinkedQueue<>();
    private final java.util.concurrent.atomic.AtomicInteger pendingArrowCount=new java.util.concurrent.atomic.AtomicInteger();
    private record PendingArrowHit(UUID owner,UUID target,UUID lease,int attack,double nativeAmount,int nativeCauseIndex){}
    public HytaleSummonSystem(CombatTrace trace,HytaleBossBarTracker bosses,Attack attack,CorpseLedger corpses,Benefit benefit,Burst burst){
        decoyAttraction=new DecoyNativeAttraction(registry,bosses);
        this.trace=trace;this.attack=attack;this.corpses=corpses;this.benefit=benefit;this.burst=burst;
    }
    public SummonRegistry registry(){return registry;}
    public CorpseLedger corpses(){return corpses;}
    public void configureIronSentinel(HytaleGearLoot loot){gearLoot=Objects.requireNonNull(loot);}
    private Optional<HytaleGearLoot.GroundSource> ironSource(Store<EntityStore> store,Ref<EntityStore> owner,Vec3 aim){
        var loot=gearLoot;if(loot==null||aim==null||aim.length()<1e-6)return Optional.empty();
        var player=store.getComponent(owner,PlayerRef.getComponentType());
        var origin=position(store,owner).add(new Vec3(0,1.35,0));
        var direction=aim.normalized();
        return loot.visibleGroundSources(player.getWorldUuid(),store).stream().filter(candidate->{
            var target=candidate.entity();var transform=store.getComponent(target,TransformComponent.getComponentType());
            if(transform==null)return false;
            var p=position(store,target);var centre=p.add(new Vec3(0,.55,0));var delta=centre.subtract(origin);
            double dot=delta.x()*direction.x()+delta.y()*direction.y()+delta.z()*direction.z();
            if(delta.length()>6||dot<0||delta.subtract(direction.multiply(dot)).length()>1.5
                    ||!HytaleAreaQueries.clear(store,origin,centre))return false;
            return true;
        }).min(Comparator.<HytaleGearLoot.GroundSource>comparingDouble(candidate->position(store,candidate.entity()).subtract(origin).length())
                .thenComparing(HytaleGearLoot.GroundSource::event));
    }
    public java.util.concurrent.CompletionStage<Void> prepareIron(Store<EntityStore> store,Ref<EntityStore> owner,SkillExecutionContext context){
        var loot=gearLoot;if(loot==null)return java.util.concurrent.CompletableFuture.failedStage(new IllegalStateException("IRON_SENTINEL_GEAR_UNAVAILABLE"));
        var player=store.getComponent(owner,PlayerRef.getComponentType());
        if(context.target()==null||!context.target().worldId().equals(player.getWorldUuid()))
            return java.util.concurrent.CompletableFuture.failedStage(new IllegalStateException("IRON_SENTINEL_TARGET_WORLD_CHANGED"));
        var source=loot.visibleGroundSources(player.getWorldUuid(),store).stream().filter(candidate->{
            var id=store.getComponent(candidate.entity(),UUIDComponent.getComponentType());
            return id!=null&&id.getUuid().equals(context.target().entityId());
        }).findFirst().orElse(null);
        if(source==null)return java.util.concurrent.CompletableFuture.failedStage(new IllegalStateException("IRON_SENTINEL_WORLD_ITEM_GONE"));
        try{loot.requireForgeEligibility(source.row().result().item(),player.getUuid(),store,owner);}
        catch(RuntimeException rejected){return java.util.concurrent.CompletableFuture.failedStage(rejected);}
        if(!pendingIronOwners.add(player.getUuid()))return java.util.concurrent.CompletableFuture.failedStage(
                new IllegalStateException("IRON_SENTINEL_CAST_IN_PROGRESS"));
        UUID instance=UUID.randomUUID();
        boolean replacing=loot.sentinel(player.getUuid()).filter(row->row.state()!=IronSentinelBinding.State.DEAD
                &&row.state()!=IronSentinelBinding.State.ABORTED).isPresent();
        var preparation=replacing?loot.prepareReplacingSentinel(source,player.getUuid(),instance,player.getWorldUuid(),context.target().point(),
                context.effectiveSkillLevel(),context.profile().summon().attackInterval(),
                context.compiledPlan().summonModifiers().healthAndPowerFactor()):
                loot.prepareSentinel(source,player.getUuid(),instance,player.getWorldUuid(),context.target().point(),
                context.effectiveSkillLevel(),context.profile().summon().attackInterval(),
                context.compiledPlan().summonModifiers().healthAndPowerFactor());
        return preparation.thenAccept(binding->{
            if(abandonedIron.remove(context.skillInstanceId())){loot.abandonSentinel(binding.ownerId(),binding.instanceId());
                throw new IllegalStateException("IRON_SENTINEL_CAST_ABANDONED");}
            preparedIron.put(context.skillInstanceId(),binding);
            if(replacing)preparedReplacementCasts.add(context.skillInstanceId());
        }).whenComplete((ignored,error)->{if(error!=null)pendingIronOwners.remove(player.getUuid());});
    }
    public SkillExecutionPort.Validation validateIron(Store<EntityStore> store,Ref<EntityStore> owner,SkillExecutionContext context){
        if(context.derivedRelease())return SkillExecutionPort.Validation.reject("IRON_SENTINEL_REPEAT_FORBIDDEN");
        var bound=preparedIron.get(context.skillInstanceId());
        if(bound==null||!bound.ownerId().equals(context.request().actorId())
                ||!bound.worldId().equals(context.target().worldId())||!bound.position().equals(context.target().point()))
            return SkillExecutionPort.Validation.reject("IRON_SENTINEL_PREPARED_BINDING_MISSING");
        var origin=position(store,owner).add(new Vec3(0,1.35,0));
        if(origin.subtract(bound.position()).length()>6||!HytaleAreaQueries.clear(store,origin,bound.position().add(new Vec3(0,.2,0))))
            return SkillExecutionPort.Validation.reject("IRON_SENTINEL_TARGET_RANGE_OR_LOS");
        try{var loot=gearLoot;var view=loot==null?null:store.getComponent(owner,PlayerRef.getComponentType());
            if(view==null||!view.getWorldUuid().equals(bound.worldId()))return SkillExecutionPort.Validation.reject("IRON_SENTINEL_OWNER_CHANGED");
        }catch(RuntimeException rejected){return SkillExecutionPort.Validation.reject("IRON_SENTINEL_OWNER_CHANGED");}
        return SkillExecutionPort.Validation.pass();
    }
    public void abandonIron(SkillExecutionContext context){
        pendingIronOwners.remove(context.request().actorId());
        preparedReplacementCasts.remove(context.skillInstanceId());
        var row=preparedIron.remove(context.skillInstanceId());
        if(row!=null){var loot=gearLoot;if(loot!=null)loot.abandonSentinel(row.ownerId(),row.instanceId());}
        else abandonedIron.add(context.skillInstanceId());
    }
    private boolean enqueueArrowHit(SummonRegistry.Lease lease,UUID target,int ordinal,double nativeAmount,int nativeCauseIndex){
        while(true){int current=pendingArrowCount.get();if(current>=PENDING_ARROW_CAP)return false;
            if(pendingArrowCount.compareAndSet(current,current+1))break;}
        pendingArrowHits.add(new PendingArrowHit(lease.owner(),target,lease.token(),ordinal,nativeAmount,nativeCauseIndex));return true;
    }
    private void drainArrowHits(Store<EntityStore> store,CommandBuffer<EntityStore> buffer){
        for(int drained=0;drained<PENDING_ARROW_DRAIN;drained++){
            var pending=pendingArrowHits.poll();if(pending==null)return;pendingArrowCount.decrementAndGet();
            var lease=registry.find(pending.lease).orElse(null);if(lease==null)continue;
            var owner=store.getExternalData().getRefFromUUID(pending.owner);var target=store.getExternalData().getRefFromUUID(pending.target);
            if(!alive(store,owner)||!alive(store,target)||!HytaleAreaQueries.hostile(store,target,owner))continue;
            try{attack.apply(store,buffer,owner,target,lease,pending.attack,pending.nativeAmount,pending.nativeCauseIndex);}
            catch(RuntimeException failure){quarantineAttack(store,buffer,lease,failure);}
        }
    }
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
        return registry.owned(player.getUuid(),player.getWorldUuid()).stream().filter(l->l.context()!=null&&l.context().compiledPlan().finalTags().contains("TEMPORARY_COMBAT_SUMMON")&&now()<l.expires())
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
            if(lease==null||now()>=lease.expires()||!alive(store,ref)||lease.context()==null||!lease.context().compiledPlan().finalTags().contains("TEMPORARY_COMBAT_SUMMON"))
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
    public SkillExecutionPort.Validation preflight(Store<EntityStore> store,Ref<EntityStore> owner,Stage04SkillProfile profile,com.inigmasgames.hytalerpg.domain.CompiledSkillPlan plan,int effectiveSkillLevel,Vec3 aim){
        var spec=profile.summon();
        if(!NPCPlugin.get().hasRoleName(spec.roleId()))return SkillExecutionPort.Validation.reject("SUMMON_ROLE_UNAVAILABLE");
        if(spec.ironSentinel()){
            if(gearLoot==null)return SkillExecutionPort.Validation.reject("IRON_SENTINEL_GEAR_UNAVAILABLE");
            var player=store.getComponent(owner,PlayerRef.getComponentType());
            if(pendingIronOwners.contains(player.getUuid()))return SkillExecutionPort.Validation.reject("IRON_SENTINEL_CAST_IN_PROGRESS");
            var existing=gearLoot.sentinel(player.getUuid()).orElse(null);
            if(existing!=null&&existing.state()==IronSentinelBinding.State.PREPARED)
                return SkillExecutionPort.Validation.reject("SENTINEL_REPLACEMENT_WAIT_FOR_ACTIVE_ACTOR");
            var aimed=ironSource(store,owner,aim).orElse(null);
            if(aimed==null)return SkillExecutionPort.Validation.reject("NO_AIMED_GROUND_ITEM");
            String denial=aimed.row().allocation().denial(player.getUuid(),System.currentTimeMillis());
            if(!denial.isEmpty())return SkillExecutionPort.Validation.reject("TARGET_ALREADY_CLAIMED: "+denial);
            try{gearLoot.requireForgeEligibility(aimed.row().result().item(),player.getUuid(),store,owner);}
            catch(RuntimeException rejected){
                String reason=rejected.getMessage();
                return SkillExecutionPort.Validation.reject(reason!=null&&reason.startsWith("TARGET_")?reason:
                        "TARGET_UNSUPPORTED_GEAR: "+reason);
            }
            return SkillExecutionPort.Validation.pass();
        }
        var player=store.getComponent(owner,PlayerRef.getComponentType());int count=plan.summonModifiers().count(spec.baseCount(effectiveSkillLevel));
        String admission=replacesBatch(profile)?registry.admissionReplacing(player.getUuid(),player.getWorldUuid(),profile.skillId(),count,spec.decoy()):
                registry.admission(player.getUuid(),count,spec.decoy());
        if(!admission.equals("PASS"))return SkillExecutionPort.Validation.reject(admission);
        if(spec.corpseRequired())return selectCorpse(store,owner,aim,spec.range()).isPresent()?SkillExecutionPort.Validation.pass():
                SkillExecutionPort.Validation.reject("NO_ELIGIBLE_CLASSIFIED_NATIVE_CORPSE");
        return placement(store,owner,aim,spec.range()).isPresent()?SkillExecutionPort.Validation.pass():
                SkillExecutionPort.Validation.reject("SUMMON_NO_VALID_GROUND");
    }
    public CommittedTarget capture(Store<EntityStore> store,Ref<EntityStore> owner,Stage04SkillProfile profile,Vec3 aim){
        if(profile.summon().ironSentinel()){
            var source=ironSource(store,owner,aim).orElseThrow(()->new IllegalStateException("IRON_SENTINEL_GROUND_ITEM_GONE"));
            var player=store.getComponent(owner,PlayerRef.getComponentType());
            return new CommittedTarget(player.getWorldUuid(),position(store,owner),position(store,source.entity()),aim,
                    store.getComponent(source.entity(),UUIDComponent.getComponentType()).getUuid());
        }
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
        if(context.profile().summon().ironSentinel()){
            var binding=preparedIron.remove(context.skillInstanceId());
            if(binding==null)throw new IllegalStateException("IRON_SENTINEL_BINDING_NOT_PREPARED");
            boolean replacing=preparedReplacementCasts.remove(context.skillInstanceId());
            SummonRegistry.Lease lease;
            try{
                if(replacing&&registry.iron(binding.ownerId()).isPresent()){
                    var replacement=registry.reserveReplacingIronSentinel(context,now(),binding.boundItem());
                    lease=replacement.reserved().getFirst();outgoingIron.put(lease.token(),replacement.replaced().getFirst());
                }else lease=registry.reserveIronSentinel(context,now(),binding.boundItem());
            }catch(RuntimeException failure){pendingIronOwners.remove(binding.ownerId());gearLoot.abandonSentinel(binding.ownerId(),binding.instanceId());throw failure;}
            if(replacing)replacingIronTokens.add(lease.token());
            spawningIron.put(lease.token(),binding);
            try{buffer.run(actual->spawnBatch(actual,owner,context,List.of(lease)));}
            catch(RuntimeException failure){spawningIron.remove(lease.token());registry.remove(lease.token());
                outgoingIron.remove(lease.token());replacingIronTokens.remove(lease.token());pendingIronOwners.remove(binding.ownerId());
                gearLoot.abandonSentinel(binding.ownerId(),binding.instanceId());throw failure;}
            return SkillExecutionResult.committed("IRON_SENTINEL_SPAWN_QUEUED",0,0);
        }
        CorpseLedger.Claim claim=null;
        if(context.profile().summon().corpseRequired()){
            if(committedCorpse(context).isEmpty())throw new IllegalStateException("COMMITTED_CORPSE_PERMIT_UNAVAILABLE");
            claim=corpses.takeCommitted(context.request().actorId(),context.target().worldId(),context.rootCastId(),context.skillInstanceId()).orElseThrow();
        }
        List<SummonRegistry.Lease> leases;List<SummonRegistry.Lease> replaced=List.of();
        try{
            if(replacesBatch(context.profile())){var replacement=registry.replaceAndReserve(context,now(),claim==null?null:claim.source());
                replaced=replacement.replaced();leases=replacement.reserved();}
            else leases=registry.reserve(context,now(),claim==null?null:claim.source());
        }
        catch(RuntimeException failure){if(claim!=null)corpses.release(claim);throw failure;}
        for(var old:replaced){
            decoyAttraction.release(store,old.token());var oldRef=old.entity()==null?null:store.getExternalData().getRefFromUUID(old.entity());
            if(oldRef!=null&&oldRef.isValid())buffer.tryRemoveEntity(oldRef,RemoveReason.REMOVE);
            emit(old,RpgTraceEventType.SUMMON_TERMINATED,Map.of("reason","REPLACED","replacementRoot",context.rootCastId(),"deathPact",false));
        }
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
            if(spec.ironSentinel())point=HytaleAreaQueries.ground(store,point.add(new Vec3(0,1,0)),new Vec3(0,-1,0),3)
                    .orElseThrow(()->new IllegalStateException("IRON_SENTINEL_NO_GROUND"));
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
                            var projection=new SummonProjection(lease.token());
                            projection.awaitingForgeCommit=replacingIronTokens.contains(lease.token());
                            actual.addComponent(ref,SummonProjection.getComponentType(),projection);
                            presentSummonNameplate(actual,ref,npc,lease,context.effectiveSkillLevel());
                            var stats=actual.getComponent(ref,EntityStatMap.getComponentType());
                            int health=DefaultEntityStatTypes.getHealth();var nativeHealth=stats.get(health);
                            double maximum=lease.maximumHealth();
                            stats.putModifier(health,"RPG_SUMMON_MAX",new StaticModifier(Modifier.ModifierTarget.MAX,
                                    StaticModifier.CalculationType.ADDITIVE,(float)(maximum-nativeHealth.getMax())));
                            stats.update();stats.setStatValue(health,(float)maximum);
                            if(Math.abs(stats.get(health).getMax()-maximum)>.001)throw new IllegalStateException("SUMMON_HEALTH_PROJECTION_MISMATCH");
                            var id=actual.getComponent(ref,UUIDComponent.getComponentType()).getUuid();
                            if(!registry.activate(lease,id,now()))throw new IllegalStateException("SUMMON_ACTIVATION_REJECTED");
                            installAllegiance(actual,owner);
                        });
                if(result!=SpawnTestResult.TEST_OK)throw new IllegalStateException("NATIVE_"+result);
            }
            for(var lease:leases)emit(lease,RpgTraceEventType.SUMMON_SPAWNED,Map.of("entity",lease.entity(),"rewardEligible",false,
                    "lifetime",Math.max(1,context.profile().summon().lifetime()*context.compiledPlan().summonModifiers().lifetimeFactor()),
                    "nativeDamageInteractions",lease.nativeRanged(),"nativeSpawnPresentation",lease.nativeRanged(),"spawnLockSeconds",lease.nativeRanged()?1.5:0,"serialized",false));
            if(spec.ironSentinel()){
                var lease=leases.getFirst();var source=spawningIron.remove(lease.token());
                if(source==null)throw new IllegalStateException("IRON_SENTINEL_BINDING_CACHE_MISSING");
                activeIron.put(lease.token(),source);
                if(replacingIronTokens.contains(lease.token()))activateReplacementAfterSpawn(store,lease,source,points.getFirst());
                else{gearLoot.activateSentinel(lease.owner(),source.instanceId(),source.sourceEvent(),lease.boundItem().identity(),
                        context.target().worldId(),points.getFirst(),lease.maximumHealth());pendingIronOwners.remove(lease.owner());}
            }
        }catch(RuntimeException failure){
            if(context.profile().summon().ironSentinel())for(var lease:leases){
                var binding=spawningIron.remove(lease.token());var active=activeIron.remove(lease.token());
                if(binding==null)binding=active;
                if(binding!=null&&gearLoot!=null)gearLoot.abandonSentinel(binding.ownerId(),binding.instanceId());
                outgoingIron.remove(lease.token());replacingIronTokens.remove(lease.token());pendingIronOwners.remove(lease.owner());
            }
            for(var ref:created)if(ref.isValid())store.removeEntity(ref,RemoveReason.REMOVE);
            for(var lease:leases)registry.remove(lease.token());
            emit(leases.getFirst(),RpgTraceEventType.SUMMON_REJECTED,Map.of("boundary",String.valueOf(failure.getMessage()),
                    "rollbackEntities",created.size(),"resourceRefund",false));
        }
    }
    private void activateReplacementAfterSpawn(Store<EntityStore> store,SummonRegistry.Lease lease,
                                               IronSentinelBinding source,Vec3 point){
        activateReplacementAfterSpawn(store,lease,source,point,0);
    }
    private void activateReplacementAfterSpawn(Store<EntityStore> store,SummonRegistry.Lease lease,
                                               IronSentinelBinding source,Vec3 point,int attempt){
        gearLoot.activateReplacingSentinel(lease.owner(),source.instanceId(),source.sourceEvent(),
                lease.boundItem().identity(),lease.world(),point,lease.maximumHealth()).whenComplete((ignored,error)->{
            if(error!=null){
                if(attempt<4){java.util.concurrent.CompletableFuture.delayedExecutor(1,java.util.concurrent.TimeUnit.SECONDS)
                        .execute(()->activateReplacementAfterSpawn(store,lease,source,point,attempt+1));}
                else emit(lease,RpgTraceEventType.SUMMON_REJECTED,Map.of("phase","REPLACEMENT_ACTIVATION",
                        "boundary",String.valueOf(error.getMessage()),"nativeActorSuspended",true));
                return;
            }
            var world=store.getExternalData().getWorld();
            try{world.execute(()->{
                var actual=world.getEntityStore().getStore();
                var old=outgoingIron.remove(lease.token());
                if(old!=null){
                    registry.remove(old.token());activeIron.remove(old.token());lastHealthCheckpoint.remove(old.token());
                    var oldRef=actual.getExternalData().getRefFromUUID(old.entity());
                    if(oldRef!=null&&oldRef.isValid())actual.removeEntity(oldRef,RemoveReason.REMOVE);
                    emit(old,RpgTraceEventType.SUMMON_TERMINATED,Map.of("reason","REPLACED",
                            "replacementRoot",lease.rootCastId(),"deathPact",false));
                }
                var newRef=actual.getExternalData().getRefFromUUID(lease.entity());
                if(newRef!=null&&newRef.isValid()){
                    var marker=actual.getComponent(newRef,SummonProjection.getComponentType());
                    if(marker!=null){marker.awaitingForgeCommit=false;marker.nextQuery=0;}
                }
                replacingIronTokens.remove(lease.token());pendingIronOwners.remove(lease.owner());
            });}catch(RuntimeException worldClosing){
                replacingIronTokens.remove(lease.token());pendingIronOwners.remove(lease.owner());
            }
        });
    }
    /** Rebuilds a native projection only after the gear ledger has claimed the one durable binding. */
    public void restoreIfNeeded(Store<EntityStore> store,Ref<EntityStore> owner){
        var loot=gearLoot;if(loot==null||!alive(store,owner))return;
        var player=store.getComponent(owner,PlayerRef.getComponentType());if(player==null)return;
        UUID id=player.getUuid(),worldId=player.getWorldUuid();double clock=now();
        var visible=loot.sentinel(id).orElse(null);
        if(visible==null||visible.state()==IronSentinelBinding.State.DEAD||visible.state()==IronSentinelBinding.State.ABORTED)return;
        if(visible.state()==IronSentinelBinding.State.PREPARED
                &&System.currentTimeMillis()-visible.createdAt()<30_000)return;
        if(preparedIron.values().stream().anyMatch(row->row.ownerId().equals(id))
                ||spawningIron.values().stream().anyMatch(row->row.ownerId().equals(id)))return;
        if(registry.iron(id).isPresent()||clock<restoreRetryAt.getOrDefault(id,0d)||!restoreInFlight.add(id))return;
        var world=store.getExternalData().getWorld();Vec3 at=position(store,owner);
        loot.claimSentinelRestore(id,worldId,at).whenComplete((claimed,error)->{
            if(error!=null||claimed==null||claimed.isEmpty()){
                if(error!=null)restoreFailure(visible,"CLAIM",error);
                restoreInFlight.remove(id);restoreRetryAt.put(id,now()+3);
                return;
            }
            try{world.execute(()->{
                var actual=world.getEntityStore().getStore();var live=actual.getExternalData().getRefFromUUID(id);
                if(!owner.isValid()||!owner.equals(live)||!alive(actual,live)||!worldId.equals(actual.getComponent(live,PlayerRef.getComponentType()).getWorldUuid())
                        ||pendingIronOwners.contains(id)||loot.sentinel(id).filter(row->row.instanceId().equals(claimed.get().instanceId())
                        &&row.state()==IronSentinelBinding.State.RESTORING).isEmpty()){
                    loot.terminateSentinel(id,claimed.get().instanceId(),false,worldId,at,claimed.get().currentHealth());
                    restoreInFlight.remove(id);restoreRetryAt.put(id,now()+3);return;
                }
                spawnRestored(actual,live,claimed.get());
            });}catch(RuntimeException scheduling){
                restoreFailure(claimed.get(),"SCHEDULE",scheduling);
                loot.terminateSentinel(id,claimed.get().instanceId(),false,worldId,at,claimed.get().currentHealth());
                restoreInFlight.remove(id);restoreRetryAt.put(id,now()+3);
            }
        });
    }
    private void spawnRestored(Store<EntityStore> store,Ref<EntityStore> owner,IronSentinelBinding binding){
        SummonRegistry.Lease lease=null;var created=new ArrayList<Ref<EntityStore>>();Vec3 point=position(store,owner);
        try{
            lease=registry.restoreIronSentinel(binding,now());
            point=HytaleAreaQueries.ground(store,point.add(new Vec3(2,2,0)),new Vec3(0,-1,0),4)
                    .orElseThrow(()->new IllegalStateException("SENTINEL_RESTORE_NO_GROUND"));
            var selected=lease;var current=Math.min(binding.currentHealth(),lease.maximumHealth());
            var result=NPCPlugin.get().spawnNPCWithSpaceValidation(store,lease.roleId(),null,vector(point),
                    store.getComponent(owner,TransformComponent.getComponentType()).getRotation(),(npc,ref,actual)->{
                        created.add(ref);
                        actual.addComponent(ref,EntityStore.REGISTRY.getNonSerializedComponentType(),NonSerialized.get());
                        npc.getRole().setDeathItemsDropped();
                        actual.addComponent(ref,SummonProjection.getComponentType(),new SummonProjection(selected.token()));
                        presentSummonNameplate(actual,ref,npc,selected,binding.restoredLevel());
                        var stats=actual.getComponent(ref,EntityStatMap.getComponentType());int health=DefaultEntityStatTypes.getHealth();
                        var nativeHealth=stats.get(health);
                        stats.putModifier(health,"RPG_SUMMON_MAX",new StaticModifier(Modifier.ModifierTarget.MAX,
                                StaticModifier.CalculationType.ADDITIVE,(float)(selected.maximumHealth()-nativeHealth.getMax())));
                        stats.update();stats.setStatValue(health,(float)current);
                        if(Math.abs(stats.get(health).getMax()-selected.maximumHealth())>.001)
                            throw new IllegalStateException("SENTINEL_RESTORE_HEALTH_PROJECTION_MISMATCH");
                        if(!registry.activate(selected,actual.getComponent(ref,UUIDComponent.getComponentType()).getUuid(),now()))
                            throw new IllegalStateException("SENTINEL_RESTORE_ACTIVATION_REJECTED");
                        installAllegiance(actual,owner);
                    });
            if(result!=SpawnTestResult.TEST_OK)throw new IllegalStateException("SENTINEL_RESTORE_NATIVE_"+result);
            activeIron.put(lease.token(),binding);
            UUID worldId=binding.worldId();Vec3 placed=point;
            lootFinishRestore(binding,current,placed,lease.token(),store.getExternalData().getWorld());
            emit(lease,RpgTraceEventType.SUMMON_SPAWNED,Map.of("entity",lease.entity(),"restored",true,
                    "boundItem",binding.boundItem().identity(),"currentHealth",current,"world",worldId));
        }catch(RuntimeException failure){
            if(lease!=null){activeIron.remove(lease.token());lastHealthCheckpoint.remove(lease.token());registry.remove(lease.token());}
            for(var ref:created)if(ref.isValid())store.removeEntity(ref,RemoveReason.REMOVE);
            restoreFailure(binding,"SPAWN",failure);
            gearLoot.terminateSentinel(binding.ownerId(),binding.instanceId(),false,binding.worldId(),point,binding.currentHealth());
            restoreInFlight.remove(binding.ownerId());restoreRetryAt.put(binding.ownerId(),now()+3);
        }
    }
    private void lootFinishRestore(IronSentinelBinding binding,double health,Vec3 point,UUID token,
            com.hypixel.hytale.server.core.universe.world.World world){
        gearLoot.finishSentinelRestore(binding.ownerId(),binding.instanceId(),health,binding.worldId(),point)
                .whenComplete((result,error)->{
                    restoreInFlight.remove(binding.ownerId());
                    if(error==null)return;
                    restoreFailure(binding,"COMMIT",error);
                    restoreRetryAt.put(binding.ownerId(),now()+3);
                    world.execute(()->{var actual=world.getEntityStore().getStore();var lease=registry.remove(token).orElse(null);
                        activeIron.remove(token);lastHealthCheckpoint.remove(token);
                        if(lease!=null){var ref=actual.getExternalData().getRefFromUUID(lease.entity());
                            if(ref!=null&&ref.isValid())actual.removeEntity(ref,RemoveReason.REMOVE);}
                    });
                    gearLoot.terminateSentinel(binding.ownerId(),binding.instanceId(),false,binding.worldId(),point,health);
                });
    }
    private void restoreFailure(IronSentinelBinding binding,String phase,Throwable error){
        String message=String.valueOf(error.getMessage());if(message.length()>200)message=message.substring(0,200);
        var identity="iron-sentinel-"+binding.instanceId();
        trace.emit(binding.ownerId(),RpgTraceEventType.SUMMON_REJECTED,
                new CombatTrace.Context(identity,identity,identity),Map.of("phase","RESTORE_"+phase,"boundary",message));
    }
    public void cancel(UUID owner,String reason){corpses.cancelUncommitted(owner);for(var lease:registry.cancel(owner))emit(lease,RpgTraceEventType.SUMMON_TERMINATED,Map.of("reason",reason));}
    /** Explicit player dismissal is terminal for the bound source and never awards death benefits. */
    public int unsummon(Store<EntityStore> store,UUID owner){
        if(pendingIronOwners.contains(owner))throw new IllegalStateException("Iron Sentinel cast is still in progress");
        var loot=gearLoot;var binding=loot==null?null:loot.sentinel(owner).orElse(null);
        if(binding!=null&&binding.state()!=IronSentinelBinding.State.DEAD&&binding.state()!=IronSentinelBinding.State.ABORTED){
            if(binding.state()==IronSentinelBinding.State.PREPARED)throw new IllegalStateException("Iron Sentinel forge is still in progress");
            loot.terminateSentinel(owner,binding.instanceId(),true,binding.worldId(),binding.position(),binding.currentHealth());
        }
        int count=0;
        for(var lease:registry.owned(owner,store.getExternalData().getWorld().getWorldConfig().getUuid())){
            registry.remove(lease.token());decoyAttraction.release(store,lease.token());
            activeIron.remove(lease.token());spawningIron.remove(lease.token());lastHealthCheckpoint.remove(lease.token());
            var ref=store.getExternalData().getRefFromUUID(lease.entity());
            if(ref!=null&&ref.isValid())store.removeEntity(ref,RemoveReason.REMOVE);
            emit(lease,RpgTraceEventType.SUMMON_TERMINATED,Map.of("reason","VOLUNTARY","deathPact",false));count++;
        }
        // Reserved non-Sentinel leases have no native actor yet, but still consume ownership.
        for(var lease:registry.cancel(owner)){
            emit(lease,RpgTraceEventType.SUMMON_TERMINATED,Map.of("reason","VOLUNTARY","deathPact",false));count++;
        }
        sentinelAffixLabels.remove(owner);restoreRetryAt.remove(owner);
        return count+(binding!=null&&binding.state()!=IronSentinelBinding.State.DEAD&&binding.state()!=IronSentinelBinding.State.ABORTED&&count==0?1:0);
    }
    public boolean toggleSentinelAffixes(Store<EntityStore> store,UUID owner){
        boolean enabled=sentinelAffixLabels.add(owner);if(!enabled)sentinelAffixLabels.remove(owner);
        for(var lease:registry.owned(owner,store.getExternalData().getWorld().getWorldConfig().getUuid()))if(lease.ironSentinel()){
            var ref=store.getExternalData().getRefFromUUID(lease.entity());var npc=ref==null?null:store.getComponent(ref,NPCEntity.getComponentType());
            if(npc!=null)presentSummonNameplate(store,ref,npc,lease,activeIron.get(lease.token())==null?1:activeIron.get(lease.token()).restoredLevel());
        }
        return enabled;
    }
    /** A world handoff removes only the native projection; the bound receipt remains durable. */
    public void dormancyForTransfer(Store<EntityStore> store,UUID owner){
        for(var lease:registry.owned(owner,store.getExternalData().getWorld().getWorldConfig().getUuid()))if(lease.ironSentinel()){
            var ref=store.getExternalData().getRefFromUUID(lease.entity());
            if(ref!=null&&ref.isValid()){
                var binding=activeIron.remove(lease.token());lastHealthCheckpoint.remove(lease.token());registry.remove(lease.token());
                if(binding!=null&&gearLoot!=null){var stats=store.getComponent(ref,EntityStatMap.getComponentType());
                    var hp=stats==null?null:stats.get(DefaultEntityStatTypes.getHealth());
                    gearLoot.terminateSentinel(owner,binding.instanceId(),false,lease.world(),position(store,ref),
                            hp==null?binding.currentHealth():Math.max(0,hp.get()));}
                store.removeEntity(ref,RemoveReason.REMOVE);
                emit(lease,RpgTraceEventType.SUMMON_FOLLOW_STATE,Map.of("state","DORMANT_FOR_TRANSFER"));
            }
        }
    }
    /** Relocates existing native entities only; ownership, Health, expiry and attack clocks remain untouched. */
    public int relocateOwned(Store<EntityStore> store,UUID owner,UUID world,Vec3 destination){
        var leases=registry.owned(Objects.requireNonNull(owner),Objects.requireNonNull(world));
        var points=com.inigmasgames.hytalerpg.execution.summon.SummonFormation.points(Objects.requireNonNull(destination),Math.max(1,leases.size()));
        int moved=0;
        for(int i=0;i<leases.size();i++){
            var lease=leases.get(i);var ref=store.getExternalData().getRefFromUUID(lease.entity());
            if(!alive(store,ref))continue;
            Vec3 requested=points.get(i),ground=HytaleAreaQueries.ground(store,requested.add(new Vec3(0,2,0)),new Vec3(0,-1,0),4).orElse(destination);
            var transform=store.getComponent(ref,TransformComponent.getComponentType());
            if(transform==null)continue;
            transform.teleportPosition(vector(ground));
            var marker=store.getComponent(ref,SummonProjection.getComponentType());if(marker!=null)marker.nextQuery=0;
            var npc=store.getComponent(ref,NPCEntity.getComponentType());if(npc!=null)npc.saveLeashInformation(vector(destination),transform.getRotation());
            moved++;emit(lease,RpgTraceEventType.SUMMON_FOLLOW_STATE,Map.of("state","TELEPORT_WITH_OWNER","destination",ground.toString(),"preservedState",true));
        }
        return moved;
    }
    @Override public Query<EntityStore> getQuery(){return Query.and(SummonProjection.getComponentType(),NPCEntity.getComponentType(),TransformComponent.getComponentType());}
    @Override public Set<Dependency<EntityStore>> getDependencies(){return Set.of(new SystemDependency<>(Order.BEFORE,RoleSystems.BehaviourTickSystem.class));}
    @Override public void tick(float delta,int index,ArchetypeChunk<EntityStore> chunk,Store<EntityStore> store,CommandBuffer<EntityStore> buffer){
        try(var rpgTickSpan=com.inigmasgames.hytalerpg.diagnostics.NativeRpgTickMetrics.enter(store,com.inigmasgames.hytalerpg.diagnostics.NativeRpgTickMetrics.Phase.SUMMON)){
        drainArrowHits(store,buffer);
        var ref=chunk.getReferenceTo(index);var marker=chunk.getComponent(index,SummonProjection.getComponentType());
        var lease=registry.find(marker.token).orElse(null);
        if(lease==null){decoyAttraction.release(store,marker.token);buffer.tryRemoveEntity(ref,RemoveReason.REMOVE);return;}
        if(marker.awaitingForgeCommit)return;
        var owner=store.getExternalData().getRefFromUUID(lease.owner());
        double now=now();String ended=null;
        if(!alive(store,owner))ended="OWNER_GONE";
        else if(!alive(store,ref))ended="SUMMON_DIED";
        else if(now>=lease.expires())ended="EXPIRED";
        if(ended!=null){
            var reason=switch(ended){case "EXPIRED"->SummonRegistry.EndReason.NATURAL_EXPIRY;case "OWNER_GONE"->SummonRegistry.EndReason.OWNER_GONE;
                default->enemyDeath(store,ref,owner)?SummonRegistry.EndReason.ENEMY_KILL:SummonRegistry.EndReason.OTHER_DEATH;};
            endNative(store,buffer,ref,lease,reason);return;
        }
        if(!decoy(lease))try{recoverDistantMinion(store,ref,owner,lease,marker,now);}
        catch(RuntimeException recoveryFailure){
            marker.farSince=now;
            emit(lease,RpgTraceEventType.SUMMON_REJECTED,Map.of("phase","DISTANCE_RECOVERY",
                    "boundary",String.valueOf(recoveryFailure.getMessage())));
        }
        if(lease.ironSentinel()){
            if(now>=lastHealthCheckpoint.getOrDefault(lease.token(),0d)+1){
                lastHealthCheckpoint.put(lease.token(),now);
                var hp=store.getComponent(ref,EntityStatMap.getComponentType()).get(DefaultEntityStatTypes.getHealth());
                var binding=activeIron.get(lease.token());
                if(hp!=null&&binding!=null&&gearLoot!=null)
                    gearLoot.checkpointSentinel(lease.owner(),binding.instanceId(),hp.get(),lease.world(),position(store,ref));
            }
        }
        // Native NPC AddedSystem owns Spawn presentation and NewSpawnComponent. RPG must remain dormant too.
        if(store.getComponent(ref,NewSpawnComponent.getComponentType())!=null)return;
        if(!marker.nameplateReady){
            var npc=store.getComponent(ref,NPCEntity.getComponentType());
            presentSummonNameplate(store,ref,npc,lease,lease.ironSentinel()?
                    (activeIron.get(lease.token())==null?1:activeIron.get(lease.token()).restoredLevel()):
                    lease.context().effectiveSkillLevel());
            marker.nameplateReady=true;
        }
        if(now<marker.nextQuery)return;marker.nextQuery=now+.1;
        try{
            var origin=position(store,owner);var here=position(store,ref);
            if(lease.nativeRanged()||lease.ironSentinel()){
                var npc=store.getComponent(ref,NPCEntity.getComponentType());var ownerTransform=store.getComponent(owner,TransformComponent.getComponentType());
                npc.saveLeashInformation(ownerTransform.getPosition(),ownerTransform.getRotation());
            }
            var shape=new AreaGeometry(AreaGeometry.Kind.DISC,origin.add(new Vec3(0,-1,0)),new Vec3(1,0,0),24,0,0,0,3);
            var result=HytaleAreaQueries.query(store,owner,shape,256);
            if(result.overflow())throw new IllegalStateException("SUMMON_TARGET_QUERY_CAP");
            var candidates=result.candidates().stream().filter(c->alive(store,c.ref()))
                    .filter(c->store.getComponent(c.ref(),SummonProjection.getComponentType())==null)
                    .filter(c->HytaleAreaQueries.hostile(store,c.ref(),owner))
                    .filter(c->HytaleAreaQueries.clear(store,here.add(new Vec3(0,.5,0)),c.bounds().centre()))
                    .sorted(Comparator.<HytaleAreaQueries.Candidate>comparingDouble(c->c.bounds().centre().subtract(here).length())
                            .thenComparing(c->store.getComponent(c.ref(),UUIDComponent.getComponentType()).getUuid())).toList();
            if(candidates.size()>64)throw new IllegalStateException("SUMMON_ACCEPTED_TARGET_CAP");
            if(!lease.ironSentinel()&&lease.context().profile().summon().decoy()){
                for(var candidate:candidates)if(decoyAttraction.request(store,owner,ref,candidate.ref(),lease))
                    emit(lease,RpgTraceEventType.DECOY_ATTRACT_REQUEST,Map.of("target",store.getComponent(candidate.ref(),UUIDComponent.getComponentType()).getUuid(),"nativeAttackObserved",false,"encounterRole","Wolf_Black"));
                return; // Static idle: never seek, claim an attack, or invoke the damage adapter.
            }
            var marked=store.getComponent(ref,MarkedEntitySupport.getComponentType());
            if((lease.nativeRanged()||lease.ironSentinel())&&candidates.isEmpty()){
                // The stock ReturnHome body motion seeks NPCEntity.leashPoint. The leash point
                // above is refreshed from the owner every sample, so this is native pathfinding
                // follow behavior and never a player teleport or friendly combat target.
                marked.setMarkedEntity(MarkedEntitySupport.DEFAULT_TARGET_SLOT,null);
                double ownerDistance=here.subtract(origin).length();var state=StateSupport.get(ref,store);
                if(ownerDistance>OWNER_IDLE_RADIUS&&!marker.followingOwner){
                    state.setState(ref,"ReturnHome",null,store);marker.followingOwner=true;
                    marker.combatEngaged=false;marker.followState="FOLLOW_OWNER";
                    emit(lease,RpgTraceEventType.SUMMON_FOLLOW_STATE,Map.of("state","FOLLOW_OWNER","distance",ownerDistance,
                             "startRadius",OWNER_IDLE_RADIUS,"stopRadius",OWNER_FOLLOW_STOP_RADIUS));
                }else if(ownerDistance<=OWNER_FOLLOW_STOP_RADIUS&&(marker.followingOwner||marker.combatEngaged)){
                    state.setState(ref,"Idle",null,store);marker.followingOwner=false;
                    marker.combatEngaged=false;marker.followState="IDLE_NEAR_OWNER";
                    emit(lease,RpgTraceEventType.SUMMON_FOLLOW_STATE,Map.of("state","IDLE_NEAR_OWNER","distance",ownerDistance,
                             "startRadius",OWNER_IDLE_RADIUS,"stopRadius",OWNER_FOLLOW_STOP_RADIUS));
                }else if(marker.followState.isEmpty()&&ownerDistance<=OWNER_IDLE_RADIUS){
                    marker.followState="IDLE_NEAR_OWNER";
                    emit(lease,RpgTraceEventType.SUMMON_FOLLOW_STATE,Map.of("state","IDLE_NEAR_OWNER","distance",ownerDistance,
                            "startRadius",OWNER_IDLE_RADIUS,"stopRadius",OWNER_FOLLOW_STOP_RADIUS));
                }
                return;
            }
            if(marker.followingOwner||!marker.combatEngaged){marker.followingOwner=false;marker.combatEngaged=true;marker.followState="COMBAT";
                StateSupport.get(ref,store).setState(ref,"Combat",null,store);
                emit(lease,RpgTraceEventType.SUMMON_FOLLOW_STATE,Map.of("state","COMBAT","distance",here.subtract(origin).length()));}
            var target=candidates.isEmpty()?owner:candidates.getFirst().ref();
            marked.setMarkedEntity(MarkedEntitySupport.DEFAULT_TARGET_SLOT,target);
            if(!lease.nativeRanged()&&target!=null&&!target.equals(owner)&&position(store,target).subtract(here).length()<=2.5){
                int claimed=registry.claimAttack(lease.token(),now);
                if(claimed>0)attack.apply(store,buffer,owner,target,lease,claimed,0,-1);
            }
        }catch(RuntimeException failure){
            if(failure instanceof IllegalStateException&&String.valueOf(failure.getMessage()).contains("Store is currently processing")){
                // An ECS operation refused this tick. The durable companion remains alive;
                // removing it here caused the false death/restore loop seen in connected QA.
                marker.nextQuery=now+5;
                emit(lease,RpgTraceEventType.SUMMON_REJECTED,Map.of("boundary",failure.getMessage(),
                        "phase","TICK_RETRY","nativeActorPreserved",true));
            }else{
                decoyAttraction.release(store,lease.token());
                endNative(store,buffer,ref,lease,SummonRegistry.EndReason.NATIVE_REMOVAL);
                emit(lease,RpgTraceEventType.SUMMON_REJECTED,Map.of("boundary",String.valueOf(failure.getMessage()),"phase","TICK","quarantined",true));
            }
        }

        }
    }
    public static boolean alive(Store<EntityStore> store,Ref<EntityStore> ref){
        if(ref==null||!ref.isValid()||store.getComponent(ref,DeathComponent.getComponentType())!=null)return false;
        var stats=store.getComponent(ref,EntityStatMap.getComponentType());var hp=stats==null?null:stats.get(DefaultEntityStatTypes.getHealth());
        return hp!=null&&hp.get()>0;
    }
    private void endNative(Store<EntityStore> store,CommandBuffer<EntityStore> buffer,Ref<EntityStore> ref,SummonRegistry.Lease lease,SummonRegistry.EndReason reason){
        decoyAttraction.release(store,lease.token());
        Vec3 anchor=position(store,ref);var end=registry.end(lease.token(),reason);lastHealthCheckpoint.remove(lease.token());
        if(lease.ironSentinel()){
            var binding=activeIron.remove(lease.token());
            if(binding!=null&&gearLoot!=null){
                var stats=store.getComponent(ref,EntityStatMap.getComponentType());
                var hp=stats==null?null:stats.get(DefaultEntityStatTypes.getHealth());
                double current=hp==null?binding.currentHealth():Math.max(0,hp.get());
                boolean death=reason==SummonRegistry.EndReason.ENEMY_KILL||reason==SummonRegistry.EndReason.OTHER_DEATH;
                gearLoot.terminateSentinel(lease.owner(),binding.instanceId(),death,lease.world(),anchor,current);
            }
        }
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
    private static boolean decoy(SummonRegistry.Lease lease){return !lease.ironSentinel()&&lease.context().profile().summon().decoy();}
    private void recoverDistantMinion(Store<EntityStore> store,Ref<EntityStore> ref,Ref<EntityStore> owner,
                                      SummonRegistry.Lease lease,SummonProjection marker,double now){
        Vec3 ownerPoint=position(store,owner);
        double distance=ownerPoint.subtract(position(store,ref)).length();
        var observed=SummonDistanceRecovery.observe(distance,marker.farSince,now);
        marker.farSince=observed.farSince();
        if(!observed.recover())return;
        // Try several nearby ground points. A closed doorway need not have line of sight
        // from the minion, but the destination must have room for its body.
        var offsets=List.of(new Vec3(1.5,0,0),new Vec3(-1.5,0,0),new Vec3(0,0,1.5),new Vec3(0,0,-1.5),
                new Vec3(2,0,2),new Vec3(-2,0,2),new Vec3(2,0,-2),new Vec3(-2,0,-2));
        for(int i=0;i<offsets.size();i++){
            Vec3 offset=offsets.get(Math.floorMod(lease.token().hashCode()+i,offsets.size()));
            var ground=HytaleAreaQueries.ground(store,ownerPoint.add(offset).add(new Vec3(0,2,0)),new Vec3(0,-1,0),4);
            if(ground.isEmpty())continue;
            Vec3 landing=ground.get();
            if(landing.subtract(ownerPoint).length()>4||!HytaleAreaQueries.projectileClear(store,
                    landing.add(new Vec3(0,.6,0)),landing.add(new Vec3(0,1.6,0)),.35))continue;
            var transform=store.getComponent(ref,TransformComponent.getComponentType());
            transform.teleportPosition(vector(landing));
            var npc=store.getComponent(ref,NPCEntity.getComponentType());
            if(npc!=null)npc.saveLeashInformation(vector(ownerPoint),transform.getRotation());
            marker.nextQuery=0;marker.farSince=0;
            emit(lease,RpgTraceEventType.SUMMON_FOLLOW_STATE,Map.of("state","RECOVERED_OWNER_DISTANCE",
                    "distance",distance,"delaySeconds",SummonDistanceRecovery.DELAY_SECONDS,"destination",landing.toString()));
            return;
        }
    }
    private void presentSummonNameplate(Store<EntityStore> store,Ref<EntityStore> ref,NPCEntity npc,
                                               SummonRegistry.Lease lease,int level){
        try{
        String key=npc.getRole()==null?null:npc.getRole().getNameTranslationKey();
        String name=key==null?null:I18nModule.get().getMessage("en-US",key);
        if(name==null||name.isBlank()||name.startsWith("server."))
            name=lease.roleId().replace("RPG_Summon_","").replace("RPG_","").replace('_',' ');
        String shown=EnemyNameplateText.format(name,level);
        if(lease.ironSentinel()&&sentinelAffixLabels.contains(lease.owner())){
            var rows=com.inigmasgames.hytalerpg.ui.hud.SentinelAffixPresentation.of(lease.boundItem()).rows();
            shown=shown+"\n"+String.join("\n",rows);
        }
        var plate=store.getComponent(ref,Nameplate.getComponentType());
        if(plate==null)store.addComponent(ref,Nameplate.getComponentType(),new Nameplate(shown));
        else plate.setText(shown);
        }catch(RuntimeException presentationFailure){
            com.hypixel.hytale.logger.HytaleLogger.getLogger().atWarning().log(
                    "RPG_SUMMON_NAMEPLATE_FAILED role=%s error=%s",lease.roleId(),presentationFailure.toString());
        }
    }
    private static Vector3d vector(Vec3 v){return new Vector3d(v.x(),v.y(),v.z());}
    private static double now(){return System.nanoTime()/1e9;}
    public static boolean replacesBatch(Stage04SkillProfile profile){return profile!=null&&SKELETON_ARCHERS.equals(profile.skillId());}
    private SummonRegistry.Lease active(ComponentAccessor<EntityStore> accessor,Ref<EntityStore> ref){
        if(ref==null||!ref.isValid())return null;var projection=accessor.getComponent(ref,SummonProjection.getComponentType());
        if(projection==null)return null;var lease=registry.find(projection.token).orElse(null);
        return lease!=null&&now()<lease.expires()?lease:null;
    }
    private void installAllegiance(Store<EntityStore> store,Ref<EntityStore> owner){
        var view=store.getResource(Blackboard.getResourceType()).getView(AttitudeView.class,owner,store);
        synchronized(allegianceInstalled){if(allegianceInstalled.containsKey(view))return;
            view.registerProvider(-10,(source,role,target,accessor)->{
                var a=active(accessor,source);var b=active(accessor,target);if(a==null&&b==null)return null;
                if(a!=null&&b!=null)return Attitude.FRIENDLY;
                var summon=a!=null?a:b;var other=a!=null?target:source;
                if(accessor.getComponent(other,PlayerRef.getComponentType())!=null)return Attitude.FRIENDLY;
                var caster=accessor.getExternalData().getRefFromUUID(summon.owner());
                if(caster==null||!caster.isValid())return Attitude.IGNORE;
                var nativeSupport=accessor.getComponent(other,WorldSupport.getComponentType());
                return nativeSupport==null?Attitude.IGNORE:NativeNpcAttitudes.prepared(nativeSupport).getAttitude(other,caster,accessor);
            });allegianceInstalled.put(view,true);
        }
    }
    /** Deferred native-arrow conversion must never unwind through Store.consume and stop the
     * world. Fail closed by revoking this lease and removing only its native actor. */
    private void quarantineAttack(Store<EntityStore> store,CommandBuffer<EntityStore> buffer,SummonRegistry.Lease lease,RuntimeException failure){
        registry.remove(lease.token());decoyAttraction.release(store,lease.token());
        var ref=lease.entity()==null?null:store.getExternalData().getRefFromUUID(lease.entity());
        if(ref!=null&&ref.isValid())buffer.tryRemoveEntity(ref,RemoveReason.REMOVE);
        String boundary=String.valueOf(failure.getMessage());if(boundary.length()>160)boundary=boundary.substring(0,160);
        emit(lease,RpgTraceEventType.SUMMON_REJECTED,Map.of("phase","NATIVE_ARROW_CONVERSION","quarantined",true,
                "error",failure.getClass().getSimpleName(),"boundary",boundary));
    }
    void emit(SummonRegistry.Lease lease,RpgTraceEventType event,Map<String,?> details){
        if(lease.context()!=null)emitContext(lease.context(),event,details);
        else trace.emit(lease.owner(),event,new CombatTrace.Context(lease.rootCastId(),lease.skillInstanceId(),lease.correlationId()),details);
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
        try(var rpgTickSpan=com.inigmasgames.hytalerpg.diagnostics.NativeRpgTickMetrics.enter(store,com.inigmasgames.hytalerpg.diagnostics.NativeRpgTickMetrics.Phase.SUMMON)){
            var marker=store.getComponent(ref,SummonProjection.getComponentType());
            summons.decoyAttraction.release(store,marker.token);
            summons.registry.remove(marker.token).ifPresent(lease->{
                summons.lastHealthCheckpoint.remove(lease.token());
                if(lease.ironSentinel()){
                    var binding=summons.activeIron.remove(lease.token());
                    if(binding!=null&&summons.gearLoot!=null&&!marker.awaitingForgeCommit){
                        var stats=store.getComponent(ref,EntityStatMap.getComponentType());
                        var hp=stats==null?null:stats.get(DefaultEntityStatTypes.getHealth());
                        var transform=store.getComponent(ref,TransformComponent.getComponentType());
                        Vec3 at=transform==null?binding.position():position(store,ref);
                        summons.gearLoot.terminateSentinel(lease.owner(),binding.instanceId(),false,lease.world(),at,
                                hp==null?binding.currentHealth():Math.max(0,hp.get()));
                    }
                    summons.emit(lease,RpgTraceEventType.SUMMON_TERMINATED,Map.of("reason",
                            marker.awaitingForgeCommit?"REPLACEMENT_PENDING_NATIVE_REMOVE_"+reason:"DORMANT_NATIVE_REMOVE_"+reason));
                }else summons.emit(lease,RpgTraceEventType.SUMMON_TERMINATED,Map.of("reason","NATIVE_REMOVE_"+reason));
            });

        }
    }
    }
    /** Native death event claims termination before native corpse removal can win the next tick. */
    public static final class Death extends DeathSystems.OnDeathSystem {
        private final HytaleSummonSystem summons;
        public Death(HytaleSummonSystem summons){this.summons=summons;}
        @Override public Query<EntityStore> getQuery(){return Query.and(SummonProjection.getComponentType(),TransformComponent.getComponentType());}
        @Override public void onComponentAdded(Ref<EntityStore> ref,DeathComponent death,Store<EntityStore> store,CommandBuffer<EntityStore> buffer){
        try(var rpgTickSpan=com.inigmasgames.hytalerpg.diagnostics.NativeRpgTickMetrics.enter(store,com.inigmasgames.hytalerpg.diagnostics.NativeRpgTickMetrics.Phase.SUMMON)){
            var marker=store.getComponent(ref,SummonProjection.getComponentType());var lease=summons.registry.find(marker.token).orElse(null);
            if(lease==null)return;var owner=store.getExternalData().getRefFromUUID(lease.owner());
            var damage=death.getDeathInfo();boolean enemy=alive(store,owner)&&damage!=null&&damage.getSource() instanceof Damage.EntitySource source
                    &&source.getRef()!=null&&source.getRef().isValid()&&HytaleAreaQueries.hostile(store,source.getRef(),owner);
            summons.endNative(store,buffer,ref,lease,enemy?SummonRegistry.EndReason.ENEMY_KILL:SummonRegistry.EndReason.OTHER_DEATH);

        }
    }
    }
    /** Defense in depth: owned actors have no native attack roots, and cannot damage or farm each other. */
    public static final class DamageGuard extends DamageEventSystem {
        private final HytaleSummonSystem summons;
        public DamageGuard(HytaleSummonSystem summons){this.summons=summons;}
        @Override public Query<EntityStore> getQuery(){return Query.any();}
        @Override public SystemGroup<EntityStore> getGroup(){return DamageModule.get().getFilterDamageGroup();}
        @Override public void handle(int index,ArchetypeChunk<EntityStore> chunk,Store<EntityStore> store,CommandBuffer<EntityStore> buffer,Damage damage){
        try(var rpgTickSpan=com.inigmasgames.hytalerpg.diagnostics.NativeRpgTickMetrics.enter(store,com.inigmasgames.hytalerpg.diagnostics.NativeRpgTickMetrics.Phase.SUMMON)){
            if(damage.getSource() instanceof Damage.EntitySource source){
                var attacker=source.getRef();
                if(attacker!=null&&attacker.isValid()&&store.getComponent(attacker,SummonProjection.getComponentType())!=null){
                    var projection=store.getComponent(attacker,SummonProjection.getComponentType());
                    var lease=summons.registry.find(projection.token).orElse(null);var target=chunk.getReferenceTo(index);
                    com.inigmasgames.hytalerpg.combat.hytale.HytaleDamageMetadata metadata;
                    try{metadata=com.inigmasgames.hytalerpg.combat.hytale.HytaleDamageAdapter.metadata(damage);}
                    catch(RuntimeException malformed){metadata=null;damage.setCancelled(true);}
                    var sentinelOwner=lease==null?null:store.getExternalData().getRefFromUUID(lease.owner());
                    boolean authored=lease!=null&&lease.ironSentinel()&&metadata!=null
                            &&lease.owner().equals(metadata.actorId())&&metadata.effectInstanceId()!=null
                            &&metadata.effectInstanceId().startsWith(lease.token()+"/attack/")
                            &&alive(store,sentinelOwner)&&alive(store,target)&&HytaleAreaQueries.hostile(store,target,sentinelOwner);
                    if(!authored)damage.setCancelled(true);
                    var owner=lease==null?null:store.getExternalData().getRefFromUUID(lease.owner());
                    if(!authored&&lease!=null&&lease.nativeRanged()&&alive(store,owner)&&alive(store,target)&&HytaleAreaQueries.hostile(store,target,owner)){
                        int ordinal=summons.registry.claimAttack(lease.token(),now());
                        if(ordinal>0){UUID targetId=store.getComponent(target,UUIDComponent.getComponentType()).getUuid();
                            if(!summons.enqueueArrowHit(lease,targetId,ordinal,damage.getAmount(),damage.getDamageCauseIndex()))summons.emit(lease,RpgTraceEventType.SUMMON_REJECTED,
                                    Map.of("phase","NATIVE_ARROW_QUEUE","boundary","OVERLOAD","stateMutated",false));}
                    }
                }
                var target=chunk.getReferenceTo(index);var marker=store.getComponent(target,SummonProjection.getComponentType());
                if(marker!=null){
                    var lease=summons.registry.find(marker.token).orElse(null);
                    var owner=lease==null?null:store.getExternalData().getRefFromUUID(lease.owner());
                    if(owner==null||attacker==null||!attacker.isValid()||!HytaleAreaQueries.hostile(store,attacker,owner))damage.setCancelled(true);
                }
            }
            var victim=chunk.getReferenceTo(index);var marker=store.getComponent(victim,SummonProjection.getComponentType());
            var lease=marker==null?null:summons.registry.find(marker.token).orElse(null);
            if(lease!=null&&lease.ironSentinel()&&!damage.isCancelled()){
                var cause=DamageCause.getAssetMap().getAsset(damage.getDamageCauseIndex());
                String id=cause==null?"":cause.getId();
                double reduction=id.equals("Physical")||id.equals("Projectile")?lease.sentinelStats().finalProtection():
                        com.inigmasgames.hytalerpg.execution.summon.IronSentinelAffixes.resistances(lease.boundItem()).getOrDefault(id,0d);
                damage.setAmount((float)(damage.getAmount()*(1-reduction)));
            }

        }
    }
    }
}
