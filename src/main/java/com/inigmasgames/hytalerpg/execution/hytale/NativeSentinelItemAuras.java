package com.inigmasgames.hytalerpg.execution.hytale;

import com.hypixel.hytale.component.*;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.modules.entity.component.BoundingBox;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatMap;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.inigmasgames.hytalerpg.combat.RpgCombatKernel;
import com.inigmasgames.hytalerpg.combat.attribute.RpgAttribute;
import com.inigmasgames.hytalerpg.combat.diagnostics.CombatTrace;
import com.inigmasgames.hytalerpg.diagnostics.RpgTraceEventType;
import com.inigmasgames.hytalerpg.execution.*;
import com.inigmasgames.hytalerpg.execution.math.Vec3;
import com.inigmasgames.hytalerpg.execution.summon.SummonRegistry;
import com.inigmasgames.hytalerpg.execution.support.SupportProfile;
import com.inigmasgames.hytalerpg.execution.support.FiniteSupportEffects;
import com.inigmasgames.hytalerpg.execution.support.SupportWorldPort;
import com.inigmasgames.hytalerpg.gear.GearAffixRuntime;
import java.util.*;

/** Bound-item D08 activation on the actual Sentinel actor. Uses the shared SupportRuntime
 * field, overlap and sustain owner; the NPC receives no player attributes or Mana bill. */
public final class NativeSentinelItemAuras {
    private static final Map<String,String> SKILLS=Map.of("WA-148","emanatism", "WA-149","thorns_aura", "WA-150","pedanticism");
    private final HytaleSupportSystem support;
    private final RpgCombatKernel kernel;
    private final HytaleBossBarTracker bosses;
    private final CombatTrace trace;
    private SkillExecutionService executions;
    private final Map<String,SkillExecutionContext> contexts=new java.util.concurrent.ConcurrentHashMap<>();
    private final Map<UUID,UUID> activeWorld=new java.util.concurrent.ConcurrentHashMap<>();
    private record Contribution(SkillExecutionContext context,Set<UUID> members,double until){}
    private final Map<String,Contribution> contributions=new java.util.concurrent.ConcurrentHashMap<>();

    public NativeSentinelItemAuras(HytaleSupportSystem support,RpgCombatKernel kernel,HytaleBossBarTracker bosses,CombatTrace trace){
        this.support=Objects.requireNonNull(support);this.kernel=Objects.requireNonNull(kernel);
        this.bosses=Objects.requireNonNull(bosses);this.trace=Objects.requireNonNull(trace);
    }
    public void configureExecutions(SkillExecutionService service){executions=Objects.requireNonNull(service);}
    /** Actual unexpired membership projections, not a prediction based on affix presence. */
    public List<Map<String,Object>> qaSnapshot(SummonRegistry.Lease lease,double now){
        if(!lease.world().equals(activeWorld.get(lease.entity())))return List.of();
        return contributions.values().stream().filter(c->now<=c.until()
                &&c.context().request().actorId().equals(lease.entity()))
                .map(c->Map.<String,Object>of("skillId",c.context().profile().skillId(),"instance",c.context().skillInstanceId(),
                        "members",c.members(),"validUntil",c.until(),"coefficient",c.context().profile().support().coefficient(),
                        "sourceItem",lease.boundItem().identity(),"supportModifiers",c.context().compiledPlan().supportModifiers())).toList();
    }

    public void tick(Store<EntityStore> store,CommandBuffer<EntityStore> buffer,Ref<EntityStore> owner,
                     Ref<EntityStore> sentinel,SummonRegistry.Lease lease,double now){
        if(!lease.ironSentinel()||!lease.entity().equals(id(store,sentinel))||!HytaleSummonSystem.alive(store,owner)
                ||!HytaleSummonSystem.alive(store,sentinel))return;
        var port=new Port(store,buffer,owner,sentinel,lease);
        for(var source:lease.boundEffects().sources(com.inigmasgames.hytalerpg.gear.GearEffectSnapshot.Operator.ITEM_AURA)){
            String skill=SKILLS.get(source.affixId());
            if(skill==null||!GearAffixRuntime.ENABLED.contains(source.affixId()))continue;
            var context=contexts.computeIfAbsent(lease.token()+"/"+skill,ignored->context(store,sentinel,lease,skill));
            String result=support.activateSentinelItemAura(context,lease.boundItem().identity(),now,port);
            if(!result.equals("ACTIVE"))throw new IllegalStateException("SENTINEL_ITEM_AURA_"+result);
            activeWorld.put(lease.entity(),lease.world());
        }
        support.tickSentinelItemAuras(lease.entity(),now,true,port);
    }

    public void end(Store<EntityStore> store,CommandBuffer<EntityStore> buffer,Ref<EntityStore> owner,
                    Ref<EntityStore> sentinel,SummonRegistry.Lease lease,String reason){
        if(lease==null||!lease.ironSentinel()||lease.entity()==null)return;
        try{support.detachSentinelItemAuras(lease.entity(),reason,new Port(store,buffer,owner,sentinel,lease));}
        finally{activeWorld.remove(lease.entity());for(var skill:SKILLS.values())contexts.remove(lease.token()+"/"+skill);}
    }
    /** World removal may skip entity callbacks; discard recipient projections immediately. */
    public void worldUnload(UUID world){
        var actors=activeWorld.entrySet().stream().filter(value->value.getValue().equals(world))
                .map(Map.Entry::getKey).toList();
        RuntimeException failed=null;
        for(var actor:actors)try{support.detachSentinelItemAuras(actor,"WORLD_UNLOAD",WORLD_END);}
        catch(RuntimeException error){if(failed==null)failed=error;else failed.addSuppressed(error);}
        activeWorld.entrySet().removeIf(value->value.getValue().equals(world));
        contributions.values().removeIf(value->value.context().target().worldId().equals(world));
        contexts.values().removeIf(value->value.target().worldId().equals(world));
        if(failed!=null)throw failed;
    }
    /** D08 item Auras are free NPC sustain, so world teardown needs no native stat port. */
    private static final SupportWorldPort WORLD_END=new SupportWorldPort(){
        public com.inigmasgames.hytalerpg.combat.resource.NativeResourcePort resources(){
            throw new IllegalStateException("SENTINEL_WORLD_END_RESOURCE_DEBIT");
        }
        public String valid(SkillExecutionContext context){return "WORLD_REMOVED";}
        public List<UUID> allies(SkillExecutionContext context,double radius){return List.of();}
        public double heal(SkillExecutionContext context,UUID target,double requested){return 0;}
        public void present(SkillExecutionContext context,double radius,double duration){}
        public void trace(SkillExecutionContext context,String event,Map<String,?> details){}
    };

    /** Combine with the existing player Aura owner using its strongest-valid policy. */
    public double manaRegenerationIncreased(UUID recipient,double now){
        return Math.max(support.runtime().manaRegenerationIncreased(recipient,now),
                strongest(recipient,now,SupportProfile.Kind.MANA_REGEN));
    }
    public double cooldownRecoveryIncreased(UUID recipient,double now){
        return Math.max(support.runtime().cooldownRecoveryIncreased(recipient,now),
                strongest(recipient,now,SupportProfile.Kind.COOLDOWN_AURA));
    }
    private double strongest(UUID recipient,double now,SupportProfile.Kind kind){
        double strongest=0;
        for(var active:contributions.values())if(now<=active.until()&&active.members().contains(recipient)
                &&active.context().profile().support().kind()==kind)
            strongest=Math.max(strongest,active.context().profile().support().coefficient()*
                    active.context().compiledPlan().supportModifiers().beneficialFactor());
        return strongest;
    }
    public Optional<FiniteSupportEffects.Effect> thorns(UUID world,UUID recipient,double now){
        var player=support.runtime().thorns(world,recipient,now);
        FiniteSupportEffects.Effect strongest=player.orElse(null);
        for(var active:contributions.values()){
            var context=active.context();
            if(now>active.until()||!active.members().contains(recipient)||!context.target().worldId().equals(world)
                    ||context.profile().support().kind()!=SupportProfile.Kind.THORNS)continue;
            double magnitude=context.profile().support().coefficient()*context.snapshot().modifiers().factor()*
                    context.compiledPlan().supportModifiers().effectFactor()*
                    (context.compiledPlan().supportModifiers().selflessness()?1.35:1);
            if(strongest==null||magnitude>strongest.magnitude())strongest=new FiniteSupportEffects.Effect(
                    new FiniteSupportEffects.Key(world,context.request().actorId(),context.profile().skillId(),recipient),
                    SupportProfile.Kind.REFLECT,magnitude,0,0,active.until(),context.rootCastId(),
                    context.skillInstanceId(),context.request().correlationId(),context,0);
        }
        return Optional.ofNullable(strongest);
    }

    private SkillExecutionContext context(Store<EntityStore> store,Ref<EntityStore> sentinel,SummonRegistry.Lease lease,String skill){
        if(executions==null)throw new IllegalStateException("SENTINEL_ITEM_EXECUTIONS_MISSING");
        var position=store.getComponent(sentinel,TransformComponent.getComponentType()).getPosition();
        return boundContext(executions,kernel,lease,new Vec3(position.x(),position.y(),position.z()),skill);
    }
    /** Production D08 context factory over the existing rank-one item skill owner. */
    public static SkillExecutionContext boundContext(SkillExecutionService executions,RpgCombatKernel kernel,
            SummonRegistry.Lease lease,Vec3 point,String skill){
        Objects.requireNonNull(executions);Objects.requireNonNull(kernel);Objects.requireNonNull(lease);
        Objects.requireNonNull(point);
        if(!lease.ironSentinel())throw new IllegalArgumentException("SENTINEL_AURA_LEASE_REQUIRED");
        var raw=new EnumMap<RpgAttribute,Integer>(RpgAttribute.class);
        for(var attribute:RpgAttribute.values())raw.put(attribute,10);
        var derived=kernel.derivedStats().derive(raw);
        var target=new CommittedTarget(lease.world(),point,point,Vec3.FORWARD,lease.entity());
        return executions.boundItemAura(lease.world(),lease.entity(),lease.boundItem().identity(),skill,
                lease.boundEffects(),derived,new SkillExecutionPort.Equipment(null,null),target);
    }

    private static UUID id(Store<EntityStore> store,Ref<EntityStore> actor){
        if(actor==null||!actor.isValid())return null;
        var component=store.getComponent(actor,UUIDComponent.getComponentType());return component==null?null:component.getUuid();
    }

    private final class Port implements SupportWorldPort {
        private final Store<EntityStore> store;private final CommandBuffer<EntityStore> buffer;
        private final Ref<EntityStore> owner,sentinel;private final SummonRegistry.Lease lease;
        Port(Store<EntityStore> store,CommandBuffer<EntityStore> buffer,Ref<EntityStore> owner,
             Ref<EntityStore> sentinel,SummonRegistry.Lease lease){
            this.store=store;this.buffer=buffer;this.owner=owner;this.sentinel=sentinel;this.lease=lease;
        }
        public com.inigmasgames.hytalerpg.combat.resource.NativeResourcePort resources(){
            return new com.inigmasgames.hytalerpg.combat.hytale.EntityStatResourcePort(
                    store.getComponent(sentinel,EntityStatMap.getComponentType()));
        }
        public String valid(SkillExecutionContext context){
            return lease.entity()!=null&&lease.entity().equals(context.request().actorId())
                    &&lease.world().equals(context.target().worldId())
                    &&lease.boundItem().identity().equals(context.gearSnapshot().items().getFirst().identity())
                    &&lease.entity().equals(id(store,sentinel))&&HytaleSummonSystem.alive(store,owner)
                    &&HytaleSummonSystem.alive(store,sentinel)?"PASS":"SENTINEL_AURA_SOURCE_ENDED";
        }
        public List<UUID> allies(SkillExecutionContext context,double radius){
            var query=Query.and(UUIDComponent.getComponentType(),EntityStatMap.getComponentType(),
                    TransformComponent.getComponentType(),BoundingBox.getComponentType());
            if(store.getEntityCountFor(query)>4096)throw new IllegalStateException("SENTINEL_AURA_SCAN_BUDGET");
            var selected=new ArrayList<UUID>();selected.add(lease.entity());
            if(HytaleSummonSystem.alive(store,owner)
                    &&store.getComponent(owner,com.hypixel.hytale.server.core.modules.entity.component.Invulnerable.getComponentType())==null
                    &&HytaleSupportSystem.auraInRange(store,sentinel,owner,radius))selected.add(lease.owner());
            boolean overflow=store.forEachChunk(query,(chunk,commands)->{
                for(int index=0;index<chunk.size();index++){
                    var ref=chunk.getReferenceTo(index);
                    if(ref.equals(sentinel)||ref.equals(owner)||!HytaleSupportSystem.eligibleAlly(store,sentinel,ref)
                            ||!HytaleSupportSystem.auraInRange(store,sentinel,ref,radius))continue;
                    if(selected.size()>=64)return true;
                    selected.add(chunk.getComponent(index,UUIDComponent.getComponentType()).getUuid());
                }
                return false;
            });
            if(overflow)throw new IllegalStateException("SENTINEL_AURA_TARGET_BUDGET");
            return List.copyOf(selected);
        }
        public List<UUID> enemies(SkillExecutionContext context,double radius){return List.of();}
        public void auraMembership(SkillExecutionContext context,List<UUID> allies,List<UUID> enemies){
            contributions.put(context.skillInstanceId(),new Contribution(context,Set.copyOf(allies),System.nanoTime()/1e9+.25));
        }
        public void auraEnded(SkillExecutionContext context){contributions.remove(context.skillInstanceId());}
        public double heal(SkillExecutionContext context,UUID target,double requested){throw new IllegalStateException("SENTINEL_ITEM_AURA_CANNOT_HEAL");}
        public void present(SkillExecutionContext context,double radius,double duration){}
        public void trace(SkillExecutionContext context,String event,Map<String,?> details){
            var values=new HashMap<String,Object>(details);values.put("boundItem",lease.boundItem().identity());
            values.put("skillId",context.profile().skillId());
            var type=event.equals("ITEM_AURA_ACTIVATED")?RpgTraceEventType.AURA_ACTIVATED:RpgTraceEventType.valueOf(event);
            trace.emit(lease.entity(),type,new CombatTrace.Context(context.rootCastId(),context.skillInstanceId(),
                    context.request().correlationId()),values);
        }
    }
}
