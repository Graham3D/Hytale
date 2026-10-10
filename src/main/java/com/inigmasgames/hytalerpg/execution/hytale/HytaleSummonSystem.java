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
    private volatile NativeSentinelItemAuras sentinelItemAuras;
    private volatile SkillExecutionService itemSkillExecutions;
    private volatile NativeSentinelItemChildren sentinelItemChildren;
    private volatile java.util.function.Consumer<UUID> sentinelChildStatusCleanup;
    private volatile NativeItemAffixBindings sentinelItemBindings;
    private final Map<UUID,Double> auraRetryAt=new java.util.concurrent.ConcurrentHashMap<>();
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
    /** Executed on the caller's world thread; no casts, projections or persistence writes. */
    public Map<String,Object> qaSnapshot(Store<EntityStore> store,UUID owner){
        var lease=registry.iron(owner).orElse(null);
        if(lease==null||lease.entity()==null||!lease.world().equals(store.getExternalData().getWorld().getWorldConfig().getUuid()))
            return Map.of("status","NO_ACTIVE_SENTINEL_IN_THIS_WORLD");
        var ref=store.getExternalData().getRefFromUUID(lease.entity());
        if(!alive(store,ref)||sentinelEffects(store,ref).empty())return Map.of("status","SENTINEL_NOT_ACTIVE","entity",lease.entity());
        var row=new LinkedHashMap<>(com.inigmasgames.hytalerpg.execution.summon.SentinelQaSnapshot.resolved(lease));
        var hp=store.getComponent(ref,EntityStatMap.getComponentType()).get(DefaultEntityStatTypes.getHealth());
        row.put("status","ACTIVE");row.put("nativeHealth",Map.of("current",hp.get(),"maximum",hp.getMax()));
        var npc=store.getComponent(ref,NPCEntity.getComponentType());
        var motion=npc==null||npc.getRole()==null?null:npc.getRole().getActiveMotionController();
        row.put("movement",motion==null?Map.of("status","NATIVE_CONTROLLER_UNAVAILABLE"):
                Map.of("currentSpeed",motion.getCurrentSpeed(),"maximumSpeed",motion.getMaximumSpeed()));
        var controller=store.getComponent(ref,com.hypixel.hytale.server.core.entity.effect.EffectControllerComponent.getComponentType());
        var nativeEffects=new ArrayList<Map<String,Object>>();
        if(controller!=null)for(int index:controller.getActiveEffectIndexes()){
            var effect=com.hypixel.hytale.server.core.asset.type.entityeffect.config.EntityEffect.getAssetMap().getAsset(index);
            if(effect!=null&&effect.getApplicationEffects()!=null)nativeEffects.add(Map.of("id",effect.getId(),
                    "horizontalSpeedMultiplier",effect.getApplicationEffects().getHorizontalSpeedMultiplier()));
        }
        row.put("activeNativeEffects",nativeEffects);
        row.put("activeInheritedAuras",sentinelItemAuras==null?List.of():sentinelItemAuras.qaSnapshot(lease,System.nanoTime()/1e9));
        return row;
    }
    public CorpseLedger corpses(){return corpses;}
    /** Exact live bound source for native recipient adapters; an inactive ledger is empty. */
    public com.inigmasgames.hytalerpg.gear.GearEffectSnapshot sentinelEffects(
            Store<EntityStore> store,Ref<EntityStore> actor){
        if(store==null||actor==null||!actor.isValid()||gearLoot==null)
            return com.inigmasgames.hytalerpg.gear.GearEffectSnapshot.EMPTY;
        var marker=store.getComponent(actor,SummonProjection.getComponentType());
        var id=store.getComponent(actor,UUIDComponent.getComponentType());
        var lease=marker==null?null:registry.find(marker.token).orElse(null);
        if(lease==null||!lease.ironSentinel()||id==null||!id.getUuid().equals(lease.entity())
                ||!lease.world().equals(store.getExternalData().getWorld().getWorldConfig().getUuid())
                ||!alive(store,actor))return com.inigmasgames.hytalerpg.gear.GearEffectSnapshot.EMPTY;
        var owner=store.getExternalData().getRefFromUUID(lease.owner());
        if(!alive(store,owner))return com.inigmasgames.hytalerpg.gear.GearEffectSnapshot.EMPTY;
        var binding=activeIron.get(lease.token());
        if(binding==null||gearLoot.sentinel(lease.owner()).filter(row->row.instanceId().equals(binding.instanceId())
                &&row.state()==IronSentinelBinding.State.ACTIVE).isEmpty())
            return com.inigmasgames.hytalerpg.gear.GearEffectSnapshot.EMPTY;
        return lease.boundEffects();
    }
    public void configureIronSentinel(HytaleGearLoot loot){gearLoot=Objects.requireNonNull(loot);}
    public void configureSentinelItemExecutions(SkillExecutionService executions){
        itemSkillExecutions=Objects.requireNonNull(executions);
    }
    public void configureSentinelItemAuras(NativeSentinelItemAuras auras){
        auras.configureExecutions(Objects.requireNonNull(itemSkillExecutions,"SENTINEL_ITEM_EXECUTIONS_MISSING"));
        sentinelItemAuras=Objects.requireNonNull(auras);
    }
    public void configureSentinelItemChildren(NativeSentinelItemChildren children){sentinelItemChildren=Objects.requireNonNull(children);}
    public void configureSentinelChildStatusCleanup(java.util.function.Consumer<UUID> cleanup){
        sentinelChildStatusCleanup=Objects.requireNonNull(cleanup);
    }
    public void configureSentinelItemBindings(NativeItemAffixBindings bindings){sentinelItemBindings=Objects.requireNonNull(bindings);}
    public SentinelBlock sentinelBlockObserver(){return new SentinelBlock(this);}
    public SentinelBlockCost sentinelBlockCostObserver(){return new SentinelBlockCost(this);}
    public void worldUnload(UUID world){
        RuntimeException failed=null;
        if(sentinelItemChildren!=null&&sentinelChildStatusCleanup!=null)
            for(var actor:sentinelItemChildren.actorsInWorld(world))try{sentinelChildStatusCleanup.accept(actor);}
            catch(RuntimeException error){if(failed==null)failed=error;else failed.addSuppressed(error);}
        if(sentinelItemChildren!=null)sentinelItemChildren.worldUnload(world);
        if(sentinelItemAuras!=null)sentinelItemAuras.worldUnload(world);
        if(failed!=null)throw failed;
    }
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
                context.compiledPlan().summonModifiers().healthAndPowerFactor(),context.gearSnapshot()):
                loot.prepareSentinel(source,player.getUuid(),instance,player.getWorldUuid(),context.target().point(),
                context.effectiveSkillLevel(),context.profile().summon().attackInterval(),
                context.compiledPlan().summonModifiers().healthAndPowerFactor(),context.gearSnapshot());
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
        // SkillExecutionService accepted this valid-equipment snapshot at preparation.
        // A weapon/armor swap during wind-up must not rewrite a committed summon.
        var ownerEffects=context.gearSnapshot();
        if(context.profile().summon().ironSentinel()){
            var binding=preparedIron.remove(context.skillInstanceId());
            if(binding==null)throw new IllegalStateException("IRON_SENTINEL_BINDING_NOT_PREPARED");
            boolean replacing=preparedReplacementCasts.remove(context.skillInstanceId());
            SummonRegistry.Lease lease;
            try{
                if(replacing&&registry.iron(binding.ownerId()).isPresent()){
                    var replacement=registry.reserveReplacingIronSentinel(context,now(),binding.boundItem(),ownerEffects);
                    lease=replacement.reserved().getFirst();outgoingIron.put(lease.token(),replacement.replaced().getFirst());
                }else lease=registry.reserveIronSentinel(context,now(),binding.boundItem(),ownerEffects);
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
            if(replacesBatch(context.profile())){var replacement=registry.replaceAndReserve(context,now(),claim==null?null:claim.source(),ownerEffects);
                replaced=replacement.replaced();leases=replacement.reserved();}
            else leases=registry.reserve(context,now(),claim==null?null:claim.source(),ownerEffects);
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
                if(lease.ironSentinel())com.inigmasgames.hytalerpg.execution.summon.NativeSentinelActionAssets.publish(lease);
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
                            projectNativeHealth(actual.getComponent(ref,EntityStatMap.getComponentType()),lease,(float)lease.maximumHealth());
                            captureNativeDefense(actual,ref,lease);
                            var id=actual.getComponent(ref,UUIDComponent.getComponentType()).getUuid();
                            if(!registry.activate(lease,id,now()))throw new IllegalStateException("SUMMON_ACTIVATION_REJECTED");
                            installAllegiance(actual,owner);
                        });
                if(result!=SpawnTestResult.TEST_OK)throw new IllegalStateException("NATIVE_"+result);
            }
            for(var lease:leases)emit(lease,RpgTraceEventType.SUMMON_SPAWNED,Map.of("entity",lease.entity(),"rewardEligible",false,
                    "lifetime",lease.ironSentinel()?-1:Math.max(0,lease.expires()-now()),
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
                    endItemAuras(actual,null,old,"REPLACED");
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
            com.inigmasgames.hytalerpg.execution.summon.NativeSentinelActionAssets.publish(lease);
            var result=NPCPlugin.get().spawnNPCWithSpaceValidation(store,lease.roleId(),null,vector(point),
                    store.getComponent(owner,TransformComponent.getComponentType()).getRotation(),(npc,ref,actual)->{
                        created.add(ref);
                        actual.addComponent(ref,EntityStore.REGISTRY.getNonSerializedComponentType(),NonSerialized.get());
                        npc.getRole().setDeathItemsDropped();
                        actual.addComponent(ref,SummonProjection.getComponentType(),new SummonProjection(selected.token()));
                        presentSummonNameplate(actual,ref,npc,selected,binding.restoredLevel());
                        projectNativeHealth(actual.getComponent(ref,EntityStatMap.getComponentType()),selected,(float)current);
                        captureNativeDefense(actual,ref,selected);
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
            if(lease!=null){endItemAuras(store,null,lease,"RESTORE_FAILED");activeIron.remove(lease.token());lastHealthCheckpoint.remove(lease.token());registry.remove(lease.token());}
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
                        if(lease!=null){endItemAuras(actual,null,lease,"RESTORE_COMMIT_FAILED");var ref=actual.getExternalData().getRefFromUUID(lease.entity());
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
            endItemAuras(store,null,lease,"VOLUNTARY");
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
                endItemAuras(store,null,lease,"WORLD_TRANSFER");
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
    public record Relocation(SummonRegistry.Lease lease,Ref<EntityStore> entity,Vec3 landing) { }
    private static final Set<String> TELEPORT_GROUNDED_ROLES=Set.of("RPG_Summon_Wolf","RPG_Summon_Crawler",
            "RPG_Summon_Broodling","RPG_Summon_Decoy","RPG_Summon_Skeleton_Archer");

    /** Place every live owned summon before a Teleport cost can commit. */
    public Optional<List<Relocation>> preflightRelocation(Store<EntityStore> store,UUID owner,UUID world,Vec3 destination){
        var leases=registry.owned(Objects.requireNonNull(owner),Objects.requireNonNull(world));
        if(leases.isEmpty())return Optional.of(List.of());
        int count=leases.size();double radius=count==1?2:Math.max(2,1.4/(2*Math.sin(Math.PI/count)));
        var ownerRef=store.getExternalData().getRefFromUUID(owner);
        if(ownerRef==null||!ownerRef.isValid())return Optional.empty();
        var placed=new ArrayList<Relocation>(count);
        for(int i=0;i<count;i++){
            var lease=leases.get(i);var ref=store.getExternalData().getRefFromUUID(lease.entity());
            if(!alive(store,ref))return Optional.empty();
            var npc=store.getComponent(ref,NPCEntity.getComponentType());
            if(npc==null||npc.getRole()==null)return Optional.empty();
            double angle=2*Math.PI*i/count;
            Vec3 requested=destination.add(new Vec3(radius*Math.cos(angle),0,radius*Math.sin(angle)));
            boolean grounded=lease.ironSentinel()||TELEPORT_GROUNDED_ROLES.contains(lease.roleId())
                    ||npc.getRole().isOnGround();
            Vec3 landing=grounded
                    ?HytaleTeleportTarget.safeGround(store,requested.add(new Vec3(0,2,0)),4).orElse(null)
                    :requested.add(new Vec3(0,1.5,0));
            if(landing==null||Math.abs(landing.y()-destination.y())>2
                    ||!HytaleTeleportTarget.clearBody(store,ref,landing))return Optional.empty();
            if(overlap(store,ref,landing,ownerRef,destination))return Optional.empty();
            for(var earlier:placed)if(overlap(store,ref,landing,earlier.entity(),earlier.landing()))return Optional.empty();
            placed.add(new Relocation(lease,ref,landing));
        }
        return Optional.of(List.copyOf(placed));
    }

    /** Native entities and leases remain intact, preserving Health, expiry and attack clocks. */
    public int relocateOwned(Store<EntityStore> store,Vec3 destination,List<Relocation> placed){
        for(var row:placed)if(!alive(store,row.entity())||!HytaleTeleportTarget.clearBody(store,row.entity(),row.landing()))
            throw new IllegalStateException("TELEPORT_SUMMON_PLACEMENT_CHANGED");
        int moved=0;
        for(var row:placed){
            var lease=row.lease();var ref=row.entity();Vec3 ground=row.landing();
            var transform=store.getComponent(ref,TransformComponent.getComponentType());
            if(transform==null)throw new IllegalStateException("TELEPORT_SUMMON_TRANSFORM_MISSING");
            transform.teleportPosition(vector(ground));
            var marker=store.getComponent(ref,SummonProjection.getComponentType());if(marker!=null)marker.nextQuery=0;
            var npc=store.getComponent(ref,NPCEntity.getComponentType());if(npc!=null)npc.saveLeashInformation(vector(destination),transform.getRotation());
            moved++;emit(lease,RpgTraceEventType.SUMMON_FOLLOW_STATE,Map.of("state","TELEPORT_WITH_OWNER","destination",ground.toString(),"preservedState",true));
        }
        return moved;
    }
    private static boolean overlap(Store<EntityStore> store,Ref<EntityStore> a,Vec3 pa,Ref<EntityStore> b,Vec3 pb){
        var aa=store.getComponent(a,BoundingBox.getComponentType()).getBoundingBox();
        var bb=store.getComponent(b,BoundingBox.getComponentType()).getBoundingBox();
        return pa.x()+aa.min.x()<pb.x()+bb.max.x()&&pa.x()+aa.max.x()>pb.x()+bb.min.x()
                &&pa.y()+aa.min.y()<pb.y()+bb.max.y()&&pa.y()+aa.max.y()>pb.y()+bb.min.y()
                &&pa.z()+aa.min.z()<pb.z()+bb.max.z()&&pa.z()+aa.max.z()>pb.z()+bb.min.z();
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
        if(lease.ironSentinel()&&now>=auraRetryAt.getOrDefault(lease.token(),0d)
                &&gearLoot!=null&&gearLoot.sentinel(lease.owner()).filter(row->row.instanceId().equals(activeIron.get(lease.token())==null?
                        null:activeIron.get(lease.token()).instanceId())&&row.state()==IronSentinelBinding.State.ACTIVE).isPresent()){
            var auras=sentinelItemAuras;
            if(auras!=null)try{auras.tick(store,buffer,owner,ref,lease,now);}
            catch(RuntimeException failure){auraRetryAt.put(lease.token(),now+3);
                emit(lease,RpgTraceEventType.SUMMON_REJECTED,Map.of("phase","SENTINEL_ITEM_AURA",
                        "boundary",String.valueOf(failure.getMessage()),"nativeActorPreserved",true));}
        }
        if(lease.ironSentinel()&&sentinelItemChildren!=null&&gearLoot!=null&&activeIron.get(lease.token())!=null
                &&gearLoot.sentinel(lease.owner()).filter(row->row.instanceId().equals(activeIron.get(lease.token()).instanceId())
                        &&row.state()==IronSentinelBinding.State.ACTIVE).isPresent())
            sentinelItemChildren.drain(store,buffer,ref,owner,lease);
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
            try{com.inigmasgames.hytalerpg.execution.summon.SummonNativeMovement.refresh(store,ref,lease);}
            catch(RuntimeException movementFailure){emit(lease,RpgTraceEventType.SUMMON_REJECTED,
                    Map.of("phase","NATIVE_PURSUIT","boundary",String.valueOf(movementFailure.getMessage()),"nativeActorPreserved",true));}
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
            if(!lease.nativeRanged()&&target!=null&&!target.equals(owner)
                    &&position(store,target).subtract(here).length()<=sentinelMeleeReach(lease)){
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
    /** The bound weapon extends the Sentinel's actual claim geometry by the authored metres. */
    public static double sentinelMeleeReach(SummonRegistry.Lease lease){
        return 2.5+(lease.ironSentinel()?Math.max(0,lease.boundEffects().value("WA-014")):0);
    }
    /** Recipient factor for a live bound-item Minor Heal; callers enforce lease authority. */
    public static double sentinelReceivedHealing(SummonRegistry.Lease lease,double requested){
        if(lease==null||!lease.ironSentinel()||!Double.isFinite(requested)||requested<0)
            throw new IllegalArgumentException("INVALID_SENTINEL_HEAL");
        return requested*(1+lease.boundEffects().percent(
                com.inigmasgames.hytalerpg.gear.GearEffectSnapshot.Operator.HEALING_RECEIVED));
    }
    /** The shared Defense owner removes only the critical excess of a managed incoming hit. */
    public static double sentinelCriticalTaken(SummonRegistry.Lease lease,double ordinary,double critical){
        if(lease==null||!lease.ironSentinel())throw new IllegalArgumentException("SENTINEL_CRITICAL_SOURCE_REQUIRED");
        return com.inigmasgames.hytalerpg.gear.GearDefenseEffects.criticalAmount(
                lease.boundEffects(),ordinary,critical);
    }
    public static boolean alive(Store<EntityStore> store,Ref<EntityStore> ref){
        if(ref==null||!ref.isValid()||store.getComponent(ref,DeathComponent.getComponentType())!=null)return false;
        var stats=store.getComponent(ref,EntityStatMap.getComponentType());var hp=stats==null?null:stats.get(DefaultEntityStatTypes.getHealth());
        return hp!=null&&hp.get()>0;
    }
    /** The bound Sentinel has an audited chassis/source Physical protection value. Convert it
     * to D01 rating before the owner's WA-115 rating increase, then project it once in Filter. */
    public static com.inigmasgames.hytalerpg.gear.GearDefenseEffects.View sentinelDefenseView(SummonRegistry.Lease lease){
        if(!lease.ironSentinel())throw new IllegalArgumentException("SENTINEL_DEFENSE_VIEW_REQUIRES_SENTINEL");
        double bonus=lease.ownerEffects().percent("WA-115");
        var local=com.inigmasgames.hytalerpg.gear.GearDefenseEffects.resolve(lease.boundEffects(),
                lease.sentinelStats().effectiveLevel(),lease.sentinelStats().finalProtection(),0,0);
        return sentinelDefenseRating(local.totalRating(),lease.sentinelStats().effectiveLevel(),bonus,
                lease.sentinelStats().finalProtection());
    }
    public static com.inigmasgames.hytalerpg.gear.GearDefenseEffects.View sentinelDefenseView(double base,int level,double bonus){
        if(!Double.isFinite(bonus)||bonus<0)throw new IllegalArgumentException("Invalid Sentinel defense bonus");
        var baseView=com.inigmasgames.hytalerpg.gear.GearDefenseEffects.resolve(
                com.inigmasgames.hytalerpg.gear.GearEffectSnapshot.EMPTY,level,base,0,0);
        return sentinelDefenseRating(baseView.totalRating(),level,bonus,base);
    }
    private static com.inigmasgames.hytalerpg.gear.GearDefenseEffects.View sentinelDefenseRating(
            double baseRating,int level,double bonus,double chassisProtection){
        double rating=baseRating*(1+bonus);
        double k=100+10*Math.clamp(level,1,99);
        double protection=Math.max(chassisProtection,Math.min(.60,rating/(k+rating)));
        return new com.inigmasgames.hytalerpg.gear.GearDefenseEffects.View(
                baseRating,0,rating,rating,protection);
    }
    private static void captureNativeDefense(Store<EntityStore> store,Ref<EntityStore> ref,SummonRegistry.Lease lease){
        if(lease.ironSentinel())return; // Sentinel protection is owned by its bound chassis projection.
        var armor=store.getComponent(ref,com.hypixel.hytale.server.core.inventory.InventoryComponent.Armor.getComponentType());
        var effects=store.getComponent(ref,com.hypixel.hytale.server.core.entity.effect.EffectControllerComponent.getComponentType());
        if(armor==null||effects==null){lease.captureNativeProtection(Map.of());return;}
        var values=new HashMap<String,Double>();
        for(String id:List.of("Physical","Projectile")){
            var cause=DamageCause.getAssetMap().getAsset(id);
            if(cause==null)throw new IllegalStateException("SUMMON_DEFENSE_CAUSE_MISSING: "+id);
            double protection=com.inigmasgames.hytalerpg.combat.hytale.HytaleDamageAdapter.nativeResistance(
                    store.getExternalData().getWorld(),armor.getInventory(),false,effects,cause);
            if(protection>0&&protection<1)values.put(id,protection);
        }
        lease.captureNativeProtection(values);
    }
    /** Project the lease into the native component while retaining damage already taken by a spawned actor. */
    public static void projectNativeHealth(EntityStatMap stats,SummonRegistry.Lease lease,Float restoredCurrent){
        Objects.requireNonNull(stats);Objects.requireNonNull(lease);
        int health=DefaultEntityStatTypes.getHealth();var nativeHealth=Objects.requireNonNull(stats.get(health));
        float deficit=Math.max(0,nativeHealth.getMax()-nativeHealth.get());
        float maximum=(float)lease.maximumHealth();
        stats.putModifier(health,"RPG_SUMMON_MAX",new StaticModifier(Modifier.ModifierTarget.MAX,
                StaticModifier.CalculationType.ADDITIVE,maximum-nativeHealth.getMax()));
        stats.update();
        stats.setStatValue(health,restoredCurrent==null?Math.max(0,maximum-deficit):Math.min(maximum,restoredCurrent));
        if(Math.abs(stats.get(health).getMax()-maximum)>.001)
            throw new IllegalStateException("SUMMON_HEALTH_PROJECTION_MISMATCH");
    }
    /** Filter adds only the delta above native armor already applied before this system. */
    public static double ordinaryDefenseContribution(double nativeProtection,int combatLevel,double bonus){
        if(!Double.isFinite(nativeProtection)||nativeProtection<0||nativeProtection>=1||!Double.isFinite(bonus)||bonus<0)
            throw new IllegalArgumentException("Invalid summon defense input");
        if(nativeProtection==0||bonus==0)return 0;
        double rating=com.inigmasgames.hytalerpg.gear.GearDefenseEffects.resolve(
                com.inigmasgames.hytalerpg.gear.GearEffectSnapshot.EMPTY,combatLevel,nativeProtection,0,0).totalRating();
        double k=100+10*Math.clamp(combatLevel,1,99);
        double desired=Math.max(nativeProtection,Math.min(.60,rating*(1+bonus)/(k+rating*(1+bonus))));
        return (desired-nativeProtection)/(1-nativeProtection);
    }
    /** The value consumed by DamageGuard for one native cause, after the summon source cap. */
    public static double incomingResistance(SummonRegistry.Lease lease,String causeId){
        Objects.requireNonNull(lease);Objects.requireNonNull(causeId);
        var channel=com.inigmasgames.hytalerpg.gear.GearCombatEffects.nativeChannel(causeId);
        boolean elemental=channel!=null&&channel!=com.inigmasgames.hytalerpg.gear.GearCombatEffects.Channel.PHYSICAL
                ||causeId.equals("Earth");
        double owner=elemental&&(lease.ironSentinel()||!lease.context().profile().summon().decoy())
                ?lease.ownerEffects().percent("WA-116"):0;
        if(!lease.ironSentinel()){
            if(causeId.equals("Physical")||causeId.equals("Projectile"))
                return ordinaryDefenseContribution(lease.nativeProtection().getOrDefault(causeId,0d),
                        lease.context().effectiveSkillLevel(),lease.ownerEffects().percent("WA-115"));
            return Math.min(.75,owner);
        }
        double bound=causeId.equals("Physical")||causeId.equals("Projectile")?sentinelDefenseView(lease).managedProtection():
                com.inigmasgames.hytalerpg.execution.summon.IronSentinelAffixes.resistances(lease.boundEffects()).getOrDefault(causeId,0d);
        return owner>0?Math.min(.75,bound+owner):bound;
    }
    /** Final native FilterDamage write, shared by DamageGuard and offline synthetic contacts. */
    public static void applyIncomingDamage(SummonRegistry.Lease lease,Damage damage){
        if(lease==null||damage==null||damage.isCancelled()||damage.getCause()==null)return;
        double reduction=incomingResistance(lease,damage.getCause().getId());
        if(reduction>0)damage.setAmount((float)(damage.getAmount()*(1-reduction)));
    }
    /** Select the projected recipient and direct attacker before the native FilterDamage amount write. */
    public static void filterIncoming(SummonRegistry registry,UUID projectedToken,boolean boundSourceEligible,Damage damage){
        if(registry==null||projectedToken==null||damage==null||damage.isCancelled())return;
        var lease=registry.find(projectedToken).orElse(null);
        if(lease==null||lease.ironSentinel()&&!boundSourceEligible)return;
        // Ordinary WA-115/116 are direct hostile-contact bonuses. Preserve the bound Sentinel's
        // preexisting all-origin protection when its live binding is eligible.
        if(!lease.ironSentinel()&&!(damage.getSource() instanceof Damage.EntitySource))return;
        com.inigmasgames.hytalerpg.combat.hytale.HytaleDamageMetadata metadata;
        try{metadata=com.inigmasgames.hytalerpg.combat.hytale.HytaleDamageAdapter.metadata(damage);}
        catch(RuntimeException malformed){damage.setCancelled(true);return;}
        if(!lease.ironSentinel()&&metadata!=null
                &&metadata.origin()!=com.inigmasgames.hytalerpg.combat.hytale.HytaleDamageMetadata.Origin.DIRECT)return;
        applyIncomingDamage(lease,damage);
    }
    private void endNative(Store<EntityStore> store,CommandBuffer<EntityStore> buffer,Ref<EntityStore> ref,SummonRegistry.Lease lease,SummonRegistry.EndReason reason){
        endItemAuras(store,buffer,lease,reason.name());
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
    private void endItemAuras(Store<EntityStore> store,CommandBuffer<EntityStore> buffer,SummonRegistry.Lease lease,String reason){
        if(lease.ironSentinel()&&lease.entity()!=null&&sentinelChildStatusCleanup!=null)
            try{sentinelChildStatusCleanup.accept(lease.entity());}
            catch(RuntimeException failure){emit(lease,RpgTraceEventType.SUMMON_REJECTED,
                    Map.of("phase","SENTINEL_CHILD_STATUS_CLEANUP","boundary",String.valueOf(failure.getMessage()),
                            "reason",reason));}
        if(lease.entity()!=null&&sentinelItemChildren!=null)sentinelItemChildren.detach(lease.entity());
        auraRetryAt.remove(lease.token());
        var auras=sentinelItemAuras;
        if(auras==null||!lease.ironSentinel()||lease.entity()==null)return;
        var owner=store.getExternalData().getRefFromUUID(lease.owner());
        var sentinel=store.getExternalData().getRefFromUUID(lease.entity());
        try{auras.end(store,buffer,owner,sentinel,lease,reason);}
        catch(RuntimeException failure){emit(lease,RpgTraceEventType.SUMMON_REJECTED,Map.of("phase","SENTINEL_ITEM_AURA_CLEANUP",
                "boundary",String.valueOf(failure.getMessage()),"reason",reason));}
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
                summons.endItemAuras(store,buffer,lease,"NATIVE_REMOVAL");
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
    /** Only the native post-stamina BLOCKED flag can admit the bound guard-item heal. */
    public static final class SentinelBlock extends DamageEventSystem {
        private final HytaleSummonSystem summons;
        public SentinelBlock(HytaleSummonSystem summons){this.summons=summons;}
        @Override public Query<EntityStore> getQuery(){return SummonProjection.getComponentType();}
        @Override public SystemGroup<EntityStore> getGroup(){return DamageModule.get().getInspectDamageGroup();}
        @Override public Set<Dependency<EntityStore>> getDependencies(){return Set.of(
                new SystemDependency<>(Order.AFTER,DamageSystems.DamageStamina.class));}
        @Override public void handle(int index,ArchetypeChunk<EntityStore> chunk,Store<EntityStore> store,
                                     CommandBuffer<EntityStore> buffer,Damage damage){
            var bindings=summons.sentinelItemBindings;
            if(bindings==null||damage.isCancelled()||!Boolean.TRUE.equals(damage.getIfPresentMetaObject(Damage.BLOCKED)))return;
            var ref=chunk.getReferenceTo(index);var marker=chunk.getComponent(index,SummonProjection.getComponentType());
            var lease=summons.registry.find(marker.token).orElse(null);
            if(lease==null||!lease.ironSentinel()||lease.entity()==null||!alive(store,ref)
                    ||summons.activeIron.get(lease.token())==null||summons.gearLoot==null
                    ||summons.gearLoot.sentinel(lease.owner()).filter(row->row.instanceId().equals(
                            summons.activeIron.get(lease.token()).instanceId())
                            &&row.state()==IronSentinelBinding.State.ACTIVE).isEmpty())return;
            var source=store.getComponent(ref,UUIDComponent.getComponentType());
            if(source==null||!lease.entity().equals(source.getUuid()))return;
            var bound=summons.sentinelEffects(store,ref);
            if(bound.empty())return; // The same live world/owner/custody check as all recipient affixes.
            var metadata=com.inigmasgames.hytalerpg.combat.hytale.HytaleDamageAdapter.metadata(damage);
            String receipt=Integer.toHexString(System.identityHashCode(damage))+":"+index;
            bindings.sentinelBlock(new com.inigmasgames.hytalerpg.gear.ItemSkillTriggerRuntime.Block(
                    lease.world(),lease.entity(),lease.boundItem().identity(),
                    metadata==null||metadata.rootCastId()==null?receipt:metadata.rootCastId(),receipt,
                    bound,true,metadata!=null&&!metadata.canProc(),
                    metadata!=null&&metadata.origin()==com.inigmasgames.hytalerpg.combat.hytale.HytaleDamageMetadata.Origin.REFLECTED),now());
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
                    var gearSource=com.inigmasgames.hytalerpg.combat.hytale.HytaleDamageAdapter.gearHit(damage);
                    boolean authored=authorizedSentinelAttack(lease,metadata,gearSource)
                            &&damage.getCause()!=null
                            &&com.inigmasgames.hytalerpg.gear.GearCombatEffects.nativeChannel(damage.getCause().getId())==gearSource.channel()
                            &&gearSource.hit().amount(gearSource.channel())>0
                            &&alive(store,sentinelOwner)&&alive(store,target)&&HytaleAreaQueries.hostile(store,target,sentinelOwner);
                    boolean itemChild=authorizedSentinelChild(lease,metadata,
                            com.inigmasgames.hytalerpg.combat.hytale.HytaleDamageAdapter.executionContext(damage))
                            &&alive(store,sentinelOwner)&&alive(store,target)&&HytaleAreaQueries.hostile(store,target,sentinelOwner);
                    if(!authored&&!itemChild)damage.setCancelled(true);
                    var owner=lease==null?null:store.getExternalData().getRefFromUUID(lease.owner());
                    if(!authored&&!itemChild&&lease!=null&&lease.nativeRanged()&&alive(store,owner)&&alive(store,target)&&HytaleAreaQueries.hostile(store,target,owner)){
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
            if(marker!=null)filterIncoming(summons.registry,marker.token,
                    !summons.sentinelEffects(store,victim).empty(),damage);

        }
    }
    }
    /** Native BLOCKED and active Wielding state are the guard witness before Stamina debit. */
    public static final class SentinelBlockCost extends DamageEventSystem {
        private final HytaleSummonSystem summons;
        public SentinelBlockCost(HytaleSummonSystem summons){this.summons=Objects.requireNonNull(summons);}
        @Override public Query<EntityStore> getQuery(){return Query.and(SummonProjection.getComponentType(),
                com.hypixel.hytale.server.core.entity.damage.DamageDataComponent.getComponentType());}
        @Override public SystemGroup<EntityStore> getGroup(){return DamageModule.get().getInspectDamageGroup();}
        @Override public Set<Dependency<EntityStore>> getDependencies(){return Set.of(
                new SystemDependency<>(Order.BEFORE,DamageSystems.DamageStamina.class));}
        @Override public void handle(int index,ArchetypeChunk<EntityStore> chunk,Store<EntityStore> store,
                                     CommandBuffer<EntityStore> buffer,Damage damage){
            if(damage.isCancelled()||!Boolean.TRUE.equals(damage.getIfPresentMetaObject(Damage.BLOCKED)))return;
            var data=chunk.getComponent(index,com.hypixel.hytale.server.core.entity.damage.DamageDataComponent.getComponentType());
            if(data==null||data.getCurrentWielding()==null||data.getCurrentWielding().getStaminaCost()==null)return;
            var source=summons.sentinelEffects(store,chunk.getReferenceTo(index));
            if(!source.empty())com.inigmasgames.hytalerpg.gear.NativeAffixBlockCostSystem.apply(damage,source);
        }
    }
    /** The native actor, exact bound source and one attack root must agree before Filter admits it. */
    public static boolean authorizedSentinelAttack(SummonRegistry.Lease lease,
            com.inigmasgames.hytalerpg.combat.hytale.HytaleDamageMetadata metadata,
            com.inigmasgames.hytalerpg.combat.hytale.HytaleDamageAdapter.GearHitSource source){
        if(lease==null||!lease.ironSentinel()||lease.entity()==null||metadata==null||source==null
                ||!lease.entity().equals(metadata.actorId())||metadata.origin()!=com.inigmasgames.hytalerpg.combat.hytale.HytaleDamageMetadata.Origin.DIRECT
                ||!metadata.canProc()||!lease.boundItem().identity().equals(source.hit().itemId())
                ||!source.hit().snapshot().revision().equals(lease.boundEffects().revision())
                ||!source.hit().snapshot().items().equals(lease.boundEffects().items())
                ||!source.hit().rootId().equals(metadata.rootCastId())
                ||!source.contactId().equals(metadata.correlationId())||metadata.effectInstanceId()==null)return false;
        String prefix=lease.token()+"/attack/";
        if(!metadata.effectInstanceId().startsWith(prefix))return false;
        String ordinal=metadata.effectInstanceId().substring(prefix.length());
        if(ordinal.isEmpty()||ordinal.length()>9||!ordinal.chars().allMatch(Character::isDigit))return false;
        return metadata.rootCastId().equals(lease.rootCastId()+"/attack/"+ordinal)
                &&metadata.correlationId().equals(lease.correlationId()+"/attack/"+ordinal);
    }
    /** A canonical triggered item context is the only NPC projectile child admitted by Filter. */
    public static boolean authorizedSentinelChild(SummonRegistry.Lease lease,
            com.inigmasgames.hytalerpg.combat.hytale.HytaleDamageMetadata metadata,
            SkillExecutionContext context){
        if(lease==null||!lease.ironSentinel()||lease.entity()==null||metadata==null||context==null
                ||metadata.origin()!=com.inigmasgames.hytalerpg.combat.hytale.HytaleDamageMetadata.Origin.TRIGGERED
                ||metadata.canProc()||!lease.entity().equals(metadata.actorId())
                ||!lease.entity().equals(context.request().actorId())
                ||!context.request().action().equals("ITEM_TRIGGER")||context.effectiveSkillLevel()!=1
                ||!Set.of("fire_bolt","frost_bolt").contains(context.profile().skillId())
                ||!lease.boundItem().identity().equals(context.gearSourceItemId())
                ||!lease.boundEffects().items().equals(context.gearSnapshot().items())
                ||!lease.boundEffects().revision().equals(context.gearSnapshot().revision()))return false;
        String prefix="sentinel-item/"+lease.token()+"/";
        return context.rootCastId().startsWith(prefix)&&metadata.rootCastId().equals(context.rootCastId())
                &&metadata.skillInstanceId().equals(context.skillInstanceId());
    }
    /** Receipt adapters call this only after DamageGuard admitted a completed native hit. */
    public com.inigmasgames.hytalerpg.combat.hytale.GearAppliedHitRecovery.Eligibility.SentinelAttack
            recoveryAttack(Ref<EntityStore> source,
                    com.inigmasgames.hytalerpg.combat.hytale.HytaleDamageMetadata metadata,
                    CommandBuffer<EntityStore> buffer){
        if(source==null||!source.isValid()||metadata==null)return null;
        var marker=buffer.getComponent(source,SummonProjection.getComponentType());
        if(marker==null)return null;
        var lease=registry.find(marker.token).orElse(null);
        if(lease==null||!lease.ironSentinel()||lease.entity()==null)return null;
        var identity=buffer.getComponent(source,UUIDComponent.getComponentType());
        if(identity==null||!identity.getUuid().equals(lease.entity())||!metadata.actorId().equals(lease.entity()))return null;
        String prefix=lease.token()+"/attack/";
        if(metadata.effectInstanceId()==null||!metadata.effectInstanceId().startsWith(prefix))return null;
        String ordinal=metadata.effectInstanceId().substring(prefix.length());
        if(ordinal.isEmpty()||ordinal.length()>9||!ordinal.chars().allMatch(Character::isDigit)
                ||!metadata.rootCastId().equals(lease.rootCastId()+"/attack/"+ordinal)
                ||!metadata.correlationId().equals(lease.correlationId()+"/attack/"+ordinal))return null;
        return new com.inigmasgames.hytalerpg.combat.hytale.GearAppliedHitRecovery.Eligibility.SentinelAttack(
                lease.entity(),lease.boundEffects());
    }
}
