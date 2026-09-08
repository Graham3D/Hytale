package com.inigmasgames.hytalerpg.execution.hytale;

import com.hypixel.hytale.component.*;
import com.hypixel.hytale.component.dependency.*;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.tick.EntityTickingSystem;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.asset.type.attitude.Attitude;
import com.hypixel.hytale.server.core.modules.entity.component.*;
import com.hypixel.hytale.server.core.modules.entity.damage.*;
import com.hypixel.hytale.server.core.modules.entitystats.*;
import com.hypixel.hytale.server.core.modules.entitystats.asset.DefaultEntityStatTypes;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.role.support.WorldSupport;
import com.inigmasgames.hytalerpg.combat.RpgCombatKernel;
import com.inigmasgames.hytalerpg.combat.diagnostics.CombatTrace;
import com.inigmasgames.hytalerpg.combat.hytale.*;
import com.inigmasgames.hytalerpg.combat.resource.NativeResourcePort;
import com.inigmasgames.hytalerpg.diagnostics.RpgTraceEventType;
import com.inigmasgames.hytalerpg.domain.CompiledSkillPlan;
import com.inigmasgames.hytalerpg.execution.*;
import com.inigmasgames.hytalerpg.execution.area.AreaGeometry;
import com.inigmasgames.hytalerpg.execution.connection.ConnectionShape;
import com.inigmasgames.hytalerpg.execution.math.Vec3;
import com.inigmasgames.hytalerpg.execution.support.*;
import com.inigmasgames.hytalerpg.progress.*;
import com.inigmasgames.hytalerpg.vfx.LinkTreeVfxService;
import java.util.*;

/** Native stat/ally adapters and player-owned Aura tick. No packet interception or HUD ownership. */
public final class HytaleSupportSystem extends EntityTickingSystem<EntityStore> {
    private final RpgLoadoutService loadouts;
    private final RpgCombatKernel kernel;
    private final CombatTrace trace;
    private final LinkTreeVfxService vfx;
    private final SupportRuntime runtime;
    private final HytaleBossBarTracker bosses;
    @FunctionalInterface public interface AuraPayload {void apply(Store<EntityStore> store,CommandBuffer<EntityStore> buffer,Ref<EntityStore> actor,SkillExecutionContext context,List<UUID> targets,int tick,boolean chill);}
    private final AuraPayload auraPayload;
    public HytaleSupportSystem(RpgLoadoutService loadouts,RpgCombatKernel kernel,OwnedFieldBudget fields,CombatTrace trace,LinkTreeVfxService vfx,HytaleBossBarTracker bosses,AuraPayload auraPayload){
        this.auraPayload=auraPayload;
        this.bosses=bosses;
        this.loadouts=loadouts;this.kernel=kernel;this.trace=trace;this.vfx=vfx;
        runtime=new SupportRuntime(kernel.reservations(),fields,new SupportProgressStore(){
            public SupportProgress read(UUID actor){return loadouts.getPresentationView(actor).state().support;}
            public SupportProgress save(UUID actor,SupportProgress next){return loadouts.mutateSupport(actor,next.revision(),ignored->next);}
        });
    }
    public SupportRuntime runtime(){return runtime;}
    RpgCombatKernel kernel(){return kernel;}
    HytaleBossBarTracker bosses(){return bosses;}
    void traceFinite(FiniteSupportEffects.Effect effect,RpgTraceEventType event,Map<String,?> details){
        var values=new HashMap<String,Object>(details);values.put("skillId",effect.key().skill());values.put("target",effect.key().target().toString());
        trace.emit(effect.key().owner(),event,new CombatTrace.Context(effect.rootCastId(),effect.skillInstanceId(),effect.correlationId()),values);
    }
    @Override public Query<EntityStore> getQuery(){return Query.and(PlayerRef.getComponentType(),EntityStatMap.getComponentType(),TransformComponent.getComponentType());}
    @Override public Set<Dependency<EntityStore>> getDependencies(){
        return Set.of(new SystemDependency<>(Order.AFTER,EntityStatsSystems.Recalculate.class),
                new SystemDependency<>(Order.BEFORE,EntityStatsModule.PlayerRegenerateStatsSystem.class),
                new SystemDependency<>(Order.BEFORE,HytaleSkillExecutionSystem.class));
    }
    @Override public void tick(float delta,int index,ArchetypeChunk<EntityStore> chunk,Store<EntityStore> store,CommandBuffer<EntityStore> buffer){
        var ref=chunk.getReferenceTo(index);var player=chunk.getComponent(index,PlayerRef.getComponentType());
        var stats=chunk.getComponent(index,EntityStatMap.getComponentType());
        NativeManaRegenerationAdapter.install(stats,()->runtime.manaRegenerationIncreased(player.getUuid(),System.nanoTime()/1e9));
        runtime.tick(player.getUuid(),System.nanoTime()/1e9,alive(store,ref),new Port(store,ref,buffer));
        kernel.cooldowns().setAuraRate(player.getUuid(),runtime.cooldownRecoveryIncreased(player.getUuid(),System.nanoTime()/1e9),1,.25);
    }
    public void ready(Store<EntityStore> store,Ref<EntityStore> actor){
        var player=store.getComponent(actor,PlayerRef.getComponentType());
        runtime.detach(player.getUuid(),"PLAYER_READY_RESET",port(store,actor));
        // A persisted native max modifier is capacity, not proof that a paid Aura may resume after reconnect.
        kernel.reservations().removeAll(player.getUuid(),new EntityStatResourcePort(store.getComponent(actor,EntityStatMap.getComponentType())));
    }
    public void detach(Store<EntityStore> store,Ref<EntityStore> actor,String reason){
        var player=store.getComponent(actor,PlayerRef.getComponentType());
        if(player!=null)runtime.detach(player.getUuid(),reason,port(store,actor));
    }
    public SkillExecutionPort.Validation preflight(Store<EntityStore> store,Ref<EntityStore> actor,Stage04SkillProfile profile,CompiledSkillPlan plan){
        var id=store.getComponent(actor,PlayerRef.getComponentType()).getUuid();
        try{
            NativeManaReservationProjection.maximumMultiplier(store.getComponent(actor,EntityStatMap.getComponentType()).get(DefaultEntityStatTypes.getMana()));
            String result=runtime.preflight(id,profile.skillId(),profile.support(),port(store,actor));
            if(!result.equals("PASS"))return SkillExecutionPort.Validation.reject(result);
            if(profile.support().allyTarget())selectHealTarget(store,actor,profile.support().range());
            if(profile.support().hostileTarget())SupportNativeEffects.requireTarget(store,actor,selectHostileTarget(store,actor,profile.support().range()),profile.support(),bosses);
            if(profile.support().recipientBurst()){
                SupportNativeEffects.requireAssets();
                for(var ref:allyRefs(store,actor,profile.support().radius()*plan.executionModifiers().radiusFactor(),true))SupportNativeEffects.requireRallyRecipient(store,ref);
            }
            if(profile.support().allyAura())allyRefs(store,actor,profile.support().radius()*plan.executionModifiers().radiusFactor(),true);
            if(profile.support().hostileAura())hostileRefs(store,actor,profile.support().radius()*plan.executionModifiers().radiusFactor());
            return SkillExecutionPort.Validation.pass();
        }catch(RuntimeException error){return SkillExecutionPort.Validation.reject("SUPPORT_PREFLIGHT_"+error.getMessage());}
    }
    public CommittedTarget capture(Store<EntityStore> store,Ref<EntityStore> actor,Stage04SkillProfile profile){
        var owner=store.getComponent(actor,PlayerRef.getComponentType());
        var chosen=profile.support().allyTarget()?selectHealTarget(store,actor,profile.support().range()):
                profile.support().hostileTarget()?selectHostileTarget(store,actor,profile.support().range()):actor;
        var id=store.getComponent(chosen,UUIDComponent.getComponentType());
        UUID target=chosen.equals(actor)?owner.getUuid():id.getUuid();
        return new CommittedTarget(owner.getWorldUuid(),position(store,actor),position(store,chosen),direction(store,actor),target);
    }
    public SkillExecutionResult execute(Store<EntityStore> store,Ref<EntityStore> actor,SkillExecutionContext context,CommandBuffer<EntityStore> buffer){
        return runtime.execute(context,System.nanoTime()/1e9,new Port(store,actor,buffer));
    }
    public SkillExecutionPort.Validation validateRelease(Store<EntityStore> store,Ref<EntityStore> actor,SkillExecutionContext context){
        String valid=port(store,actor).valid(context);
        if(!valid.equals("PASS"))return SkillExecutionPort.Validation.reject(valid);
        if(context.profile().support().aura())return SkillExecutionPort.Validation.reject("AURA_CANNOT_SCHEDULE_REPEAT");
        if(context.profile().support().recipientBurst())return SkillExecutionPort.Validation.pass();
        var owner=store.getComponent(actor,PlayerRef.getComponentType());
        var target=context.target().entityId();
        var ref=owner.getUuid().equals(target)?actor:target==null?null:store.getExternalData().getRefFromUUID(target);
        if(context.profile().support().hostileTarget()){
            try{SupportNativeEffects.requireTarget(store,actor,ref,context.profile().support(),bosses);return SkillExecutionPort.Validation.pass();}
            catch(RuntimeException error){return SkillExecutionPort.Validation.reject("COMMITTED_SUPPORT_TARGET_"+error.getMessage());}
        }
        return ref!=null&&eligibleAlly(store,actor,ref)&&inRange(store,actor,ref,context.profile().support().range())
                ?SkillExecutionPort.Validation.pass():SkillExecutionPort.Validation.reject("COMMITTED_HEAL_TARGET_INVALID");
    }
    public SupportWorldPort port(Store<EntityStore> store,Ref<EntityStore> actor){return new Port(store,actor);}
    private final class Port implements SupportWorldPort {
        final Store<EntityStore> store;final Ref<EntityStore> actor;final PlayerRef player;final CommandBuffer<EntityStore> buffer;
        Port(Store<EntityStore> store,Ref<EntityStore> actor){this(store,actor,null);}
        Port(Store<EntityStore> store,Ref<EntityStore> actor,CommandBuffer<EntityStore> buffer){this.store=store;this.actor=actor;this.buffer=buffer;player=store.getComponent(actor,PlayerRef.getComponentType());}
        public NativeResourcePort resources(){return new EntityStatResourcePort(store.getComponent(actor,EntityStatMap.getComponentType()));}
        public String valid(SkillExecutionContext context){
            if(!alive(store,actor))return "OWNER_DEAD";
            if(context.target()==null||!context.target().worldId().equals(player.getWorldUuid()))return "WORLD_CHANGED";
            var view=loadouts.getPresentationView(player.getUuid());var plan=view.plans().get(context.request().slot());
            if(plan==null||!plan.planHash().equals(context.compiledPlan().planHash()))return "LOADOUT_CHANGED";
            if(!context.profile().allowedMainHandKinds().isEmpty()){
                var current=new HytaleEquipmentAdapter().read(actor,store).mainHand();var prior=context.equipment().mainHand();
                if(current==null||prior==null||!current.itemId().equals(prior.itemId()))return "EQUIPMENT_CHANGED";
            }
            return "PASS";
        }
        public List<UUID> allies(SkillExecutionContext context,double radius){
            var list=allyRefs(store,actor,radius,true);var result=new ArrayList<UUID>();
            for(var ref:list){
                var id=ref.equals(actor)?player.getUuid():store.getComponent(ref,UUIDComponent.getComponentType()).getUuid();
                var stats=store.getComponent(ref,EntityStatMap.getComponentType());
                NativeManaRegenerationAdapter.install(stats,()->runtime.manaRegenerationIncreased(id,System.nanoTime()/1e9));
                result.add(id);
            }
            return result;
        }
        public List<UUID> enemies(SkillExecutionContext context,double radius){return hostileRefs(store,actor,radius).stream()
                .map(ref->store.getComponent(ref,UUIDComponent.getComponentType()).getUuid()).toList();}
        public boolean upkeep(SkillExecutionContext context,double seconds,int quantum){
            var cost=kernel.resources().evaluateUpkeep(new com.inigmasgames.hytalerpg.combat.resource.ResourceCost(
                    com.inigmasgames.hytalerpg.combat.resource.ResourceType.MANA,context.profile().support().upkeepPerSecond()*seconds),context.compiledPlan().kernelModifiers());
            var resources=resources();double before=resources.current(com.inigmasgames.hytalerpg.combat.resource.ResourceType.MANA);
            if(!kernel.resources().canAfford(player.getUuid(),cost,resources))return false;
            var token=kernel.resources().reserveCost(player.getUuid(),cost,resources);
            try{
                kernel.resources().commitCost(token,resources);double after=resources.current(com.inigmasgames.hytalerpg.combat.resource.ResourceType.MANA);
                boolean observed=Math.abs(before-after-cost.amount())<=1e-4;
                trace(context,"AURA_UPKEEP",Map.of("quantum",quantum,"seconds",seconds,"before",before,"after",after,"cost",cost.amount(),"nativeWriteObserved",observed));
                return observed;
            }finally{kernel.resources().finish(token);}
        }
        public void auraPulse(SkillExecutionContext context,List<UUID> targets,int tick,boolean chill){auraPayload.apply(store,buffer,actor,context,targets,tick,chill);}
        public void auraMembership(SkillExecutionContext context,List<UUID> allies,List<UUID> enemies){
            if(context.profile().support().kind()!=SupportProfile.Kind.COOLDOWN_AURA)return;
            var affected=new HashSet<>(allies);var previous=cooldownRecipients.put(context.skillInstanceId(),Set.copyOf(allies));
            if(previous!=null)affected.addAll(previous);
            for(var id:affected)kernel.cooldowns().setAuraRate(id,runtime.cooldownRecoveryIncreased(id,System.nanoTime()/1e9),1,.25);
            // Native Cooldown.getCooldown() returns maximum, not remaining work. No exported remaining/charge progress getter exists.
            // Setting maximum or restarting native cooldowns would violate the elapsed-work contract.
            if(warnedCooldownRoots.add(context.skillInstanceId()))trace(context,"AURA_CAPABILITY_BLOCKED",Map.of(
                    "component","enemy.explicitNativeCooldown","reason","NATIVE_COOLDOWN_REMAINING_WORK_NOT_EXPOSED",
                    "allyRpgRecoveryImplemented",true,"enemyCount",enemies.size(),"animationModified",false));
        }
        public void auraEnded(SkillExecutionContext context){
            warnedCooldownRoots.remove(context.skillInstanceId());var previous=cooldownRecipients.remove(context.skillInstanceId());
            if(previous!=null)for(var id:previous)kernel.cooldowns().setAuraRate(id,runtime.cooldownRecoveryIncreased(id,System.nanoTime()/1e9),1,.25);
        }
        public double heal(SkillExecutionContext context,UUID target,double requested){
            if(!valid(context).equals("PASS"))throw new IllegalStateException("HEAL_OWNER_INVALID");
            var ref=target.equals(player.getUuid())?actor:store.getExternalData().getRefFromUUID(target);
            if(ref==null||!eligibleAlly(store,actor,ref)||!inRange(store,actor,ref,context.profile().support().range()))
                throw new IllegalStateException("HEAL_TARGET_INVALID");
            var stats=store.getComponent(ref,EntityStatMap.getComponentType());int index=DefaultEntityStatTypes.getHealth();
            double before=stats.get(index).get();stats.setStatValue(index,(float)Math.min(stats.get(index).getMax(),before+requested));
            double after=stats.get(index).get();
            trace(context,"HEAL_APPLIED",Map.of("target",target.toString(),"requested",requested,"healthBefore",before,"healthAfter",after,"actualHealing",Math.max(0,after-before)));
            return Math.max(0,after-before);
        }
        public void finiteEffect(SkillExecutionContext context,FiniteSupportEffects effects,double now){
            if(!valid(context).equals("PASS"))throw new IllegalStateException("SUPPORT_OWNER_INVALID");
            var p=context.profile().support();
            var refs=p.recipientBurst()?allyRefs(store,actor,p.radius()*context.compiledPlan().executionModifiers().radiusFactor(),true):
                    List.of(context.target().entityId().equals(player.getUuid())?actor:store.getExternalData().getRefFromUUID(context.target().entityId()));
            double seconds=p.durationSeconds();
            if(p.hostileTarget())SupportNativeEffects.requireTarget(store,actor,refs.getFirst(),p,bosses);
            if(p.recipientBurst())for(var ref:refs)SupportNativeEffects.requireRallyRecipient(store,ref);
            if(p.allyTarget()&&(!eligibleAlly(store,actor,refs.getFirst())||!inRange(store,actor,refs.getFirst(),p.range())))
                throw new IllegalStateException("SHIELD_ALLY_TARGET_INVALID");
            var ids=refs.stream().map(ref->ref.equals(actor)?player.getUuid():store.getComponent(ref,UUIDComponent.getComponentType()).getUuid()).toList();
            effects.requireAdmission(context,ids,seconds,now);
            if(p.kind()==SupportProfile.Kind.FEAR){
                var control=SupportNativeEffects.control(store,refs.getFirst(),bosses);
                var result=kernel.statuses().apply(context.target().entityId(),com.inigmasgames.hytalerpg.combat.status.RpgStatusType.FEAR,control,seconds);
                if(result.outcome()==com.inigmasgames.hytalerpg.combat.status.StatusService.Outcome.REJECTED)throw new IllegalStateException(result.detail());
                seconds=result.remainingSeconds();
            }
            try{
                if(p.kind()==SupportProfile.Kind.SHIELD){
                    double capacity=SupportMagnitude.shield(context,masteryMultiplier(context));
                    effects.applyShield(context,ids,seconds,capacity,now);
                }else effects.apply(context,ids,seconds,now);
                for(var ref:refs){
                    if(buffer!=null)buffer.ensureComponent(ref,SupportEffectProjection.getComponentType());
                    else store.ensureComponent(ref,SupportEffectProjection.getComponentType());
                }
                for(var id:ids)trace(context,"FINITE_SUPPORT_APPLIED",Map.of("target",id.toString(),"kind",p.kind().name(),
                        "durationSeconds",seconds,"magnitude",p.coefficient(),"movementIncreased",p.movementIncreased(),"authority","RPG_EFFECT_LEDGER","nativeBehaviorVerified",false));
            }catch(RuntimeException failure){
                if(p.kind()==SupportProfile.Kind.FEAR)kernel.statuses().remove(context.target().entityId(),com.inigmasgames.hytalerpg.combat.status.RpgStatusType.FEAR);
                throw failure;
            }
        }
        public void present(SkillExecutionContext context,double radius,double duration){
            // Tracking uses an entity-bound tint lease, never a world-broadcast debug outline.
            if(context.profile().support().kind()==SupportProfile.Kind.MARK)return;
            var origin=position(store,actor).add(new Vec3(0,1,0));
            if(!context.profile().support().aura()&&context.target()!=null)origin=context.target().point().add(new Vec3(0,1,0));
            try{vfx.presentConnection(store.getExternalData().getWorld(),ConnectionShape.cylinder(origin,Math.max(.5,radius),radius>0?3:2),
                    context.profile().support().kind()==SupportProfile.Kind.HEAL?"HOLY":"MAGIC","SUPPORT",duration);}
            catch(RuntimeException ignored){/* Missing presentation cannot refund applied healing or reservation. */}
        }
        public void trace(SkillExecutionContext context,String event,Map<String,?> details){
            var values=new HashMap<String,Object>(details);values.put("skillId",context.profile().skillId());
            trace.emit(context.request().actorId(),RpgTraceEventType.valueOf(event),
                    new CombatTrace.Context(context.rootCastId(),context.skillInstanceId(),context.request().correlationId()),values);
        }
    }
    private static List<Ref<EntityStore>> allyRefs(Store<EntityStore> store,Ref<EntityStore> actor,double range){
        return allyRefs(store,actor,range,false);
    }
    private static List<Ref<EntityStore>> allyRefs(Store<EntityStore> store,Ref<EntityStore> actor,double range,boolean cylinder){
        var query=Query.and(UUIDComponent.getComponentType(),EntityStatMap.getComponentType(),TransformComponent.getComponentType(),BoundingBox.getComponentType());
        if(store.getEntityCountFor(query)>4096)throw new IllegalStateException("SUPPORT_SCAN_BUDGET");
        var selected=new ArrayList<Ref<EntityStore>>();selected.add(actor);
        boolean overflow=store.forEachChunk(query,(chunk,buffer)->{
            for(int index=0;index<chunk.size();index++){
                var ref=chunk.getReferenceTo(index);
                if(ref.equals(actor)||!eligibleAlly(store,actor,ref)||!(cylinder?auraInRange(store,actor,ref,range):inRange(store,actor,ref,range)))continue;
                if(selected.size()>=64)return true;selected.add(ref);
            }
            return false;
        });
        if(overflow)throw new IllegalStateException("SUPPORT_TARGET_BUDGET");
        return List.copyOf(selected);
    }
    private final Set<String> warnedCooldownRoots=java.util.Collections.newSetFromMap(new java.util.concurrent.ConcurrentHashMap<>());
    private final Map<String,Set<UUID>> cooldownRecipients=new java.util.concurrent.ConcurrentHashMap<>();
    private List<Ref<EntityStore>> hostileRefs(Store<EntityStore> store,Ref<EntityStore> actor,double radius){
        var shape=ConnectionShape.cylinder(position(store,actor).add(new Vec3(0,1.5,0)),radius,3);
        var found=HytaleAreaQueries.query(store,actor,shape::intersects,256);
        if(found.overflow())throw new IllegalStateException("AURA_CANDIDATE_BUDGET");
        var list=found.candidates().stream().filter(c->alive(store,c.ref())&&!SupportNativeEffects.control(store,c.ref(),bosses).protectedEntity()
                &&HytaleAreaQueries.clear(store,position(store,actor).add(new Vec3(0,1.35,0)),c.bounds().centre())).map(HytaleAreaQueries.Candidate::ref).toList();
        if(list.size()>64)throw new IllegalStateException("AURA_TARGET_BUDGET");return list;
    }
    static boolean auraInRange(Store<EntityStore> store,Ref<EntityStore> actor,Ref<EntityStore> target,double radius){
        return actor.equals(target)||ConnectionShape.cylinder(position(store,actor).add(new Vec3(0,1.5,0)),radius,3).intersects(bounds(store,target))
                &&HytaleAreaQueries.clear(store,position(store,actor).add(new Vec3(0,1.35,0)),bounds(store,target).centre());
    }
    static boolean eligibleAlly(Store<EntityStore> store,Ref<EntityStore> actor,Ref<EntityStore> target){
        if(target==null||!target.isValid()||!alive(store,target))return false;
        if(actor.equals(target))return true;
        if(store.getComponent(target,Invulnerable.getComponentType())!=null)return false;
        var faction=store.getComponent(target,WorldSupport.getComponentType());
        // No native Party/Team membership API was found. NEUTRAL/no-PvP is not affirmative ally membership.
        if(faction==null)return false;var attitude=faction.getAttitude(target,actor,store);
        return attitude==Attitude.FRIENDLY||attitude==Attitude.REVERED;
    }
    static boolean inRange(Store<EntityStore> store,Ref<EntityStore> actor,Ref<EntityStore> target,double range){
        if(actor.equals(target))return true;var origin=position(store,actor).add(new Vec3(0,1.35,0));
        var bounds=bounds(store,target);
        return ConnectionShape.pointDistanceSquared(origin,bounds)<=range*range+1e-9&&HytaleAreaQueries.clear(store,origin,bounds.centre());
    }
    private static Ref<EntityStore> selectHealTarget(Store<EntityStore> store,Ref<EntityStore> actor,double range){
        var origin=position(store,actor).add(new Vec3(0,1.35,0));
        var ray=ConnectionShape.line(origin,origin.add(direction(store,actor).multiply(range)),.02,.02);
        return allyRefs(store,actor,range).stream().filter(ref->!ref.equals(actor)&&ray.intersects(bounds(store,ref)))
                .min(Comparator.<Ref<EntityStore>>comparingDouble(ref->ray.entryDistance(bounds(store,ref)))
                        .thenComparing(ref->store.getComponent(ref,UUIDComponent.getComponentType()).getUuid().toString())).orElse(actor);
    }
    private static Ref<EntityStore> selectHostileTarget(Store<EntityStore> store,Ref<EntityStore> actor,double range){
        var origin=position(store,actor).add(new Vec3(0,1.35,0));
        var ray=ConnectionShape.line(origin,origin.add(direction(store,actor).multiply(range)),.02,.02);
        var found=HytaleAreaQueries.query(store,actor,ray::intersects,64);
        if(found.overflow())throw new IllegalStateException("SUPPORT_TARGET_BUDGET");
        return found.candidates().stream().filter(c->alive(store,c.ref())&&inRange(store,actor,c.ref(),range))
                .min(Comparator.comparingDouble((HytaleAreaQueries.Candidate c)->ray.entryDistance(c.bounds()))
                        .thenComparing(c->store.getComponent(c.ref(),UUIDComponent.getComponentType()).getUuid().toString()))
                .orElseThrow(()->new IllegalStateException("NO_VALID_HOSTILE_TARGET")).ref();
    }
    static boolean alive(Store<EntityStore> store,Ref<EntityStore> ref){
        if(ref==null)return false;
        if(!ref.isValid()||store.getComponent(ref,DeathComponent.getComponentType())!=null)return false;
        var stats=store.getComponent(ref,EntityStatMap.getComponentType());var health=stats==null?null:stats.get(DefaultEntityStatTypes.getHealth());
        return health!=null&&health.get()>health.getMin();
    }
    private static AreaGeometry.Bounds bounds(Store<EntityStore> store,Ref<EntityStore> ref){
        var box=store.getComponent(ref,BoundingBox.getComponentType()).getBoundingBox();var p=position(store,ref);
        return new AreaGeometry.Bounds(vec(box.min).add(p),vec(box.max).add(p));
    }
    static Vec3 position(Store<EntityStore> store,Ref<EntityStore> ref){return vec(store.getComponent(ref,TransformComponent.getComponentType()).getPosition());}
    private static Vec3 direction(Store<EntityStore> store,Ref<EntityStore> ref){
        var head=store.getComponent(ref,HeadRotation.getComponentType());
        return head!=null?vec(head.getDirection()).normalized():Vec3.FORWARD;
    }
    private static Vec3 vec(org.joml.Vector3dc v){return new Vec3(v.x(),v.y(),v.z());}
    public static final class Absorb extends DamageEventSystem {
        private final HytaleSupportSystem support;
        public Absorb(HytaleSupportSystem support){this.support=support;}
        @Override public Query<EntityStore> getQuery(){return PlayerRef.getComponentType();}
        @Override public Set<Dependency<EntityStore>> getDependencies(){return Set.of(
                new SystemGroupDependency<>(Order.AFTER,DamageModule.get().getFilterDamageGroup()),
                new SystemDependency<>(Order.BEFORE,HytaleDamageLifecycleSystems.Filter.class),
                new SystemDependency<>(Order.BEFORE,DamageSystems.ApplyDamage.class));}
        @Override public void handle(int index,ArchetypeChunk<EntityStore> chunk,Store<EntityStore> store,CommandBuffer<EntityStore> buffer,Damage damage){
            if(damage.isCancelled()||damage.getAmount()<=0)return;
            var metadata=HytaleDamageAdapter.metadata(damage);
            if(metadata!=null&&metadata.origin()==HytaleDamageMetadata.Origin.REDIRECTED)return;
            var ref=chunk.getReferenceTo(index);var actor=chunk.getComponent(index,PlayerRef.getComponentType()).getUuid();double now=System.nanoTime()/1e9;
            if(damage.getSource() instanceof Damage.EntitySource source&&HytaleAreaQueries.hostile(store,source.getRef(),ref)){
                support.runtime.hostileDamage(actor,now);support.kernel.hostileCombat().markHostile(actor);
            }
            try{damage.setAmount((float)support.runtime.absorb(actor,damage.getAmount(),now,support.port(store,ref)));}
            catch(RuntimeException failure){
                // Failed persistence leaves the original native amount unchanged; never grant unrecorded shielding.
                com.hypixel.hytale.logger.HytaleLogger.getLogger().atWarning().withCause(failure).log("RPG_BARRIER_REJECTED player=%s",actor);
            }
        }
    }
    public static final class Removal extends com.hypixel.hytale.component.system.RefSystem<EntityStore>{
        private final HytaleSupportSystem support;
        public Removal(HytaleSupportSystem support){this.support=support;}
        @Override public Query<EntityStore> getQuery(){return Query.and(PlayerRef.getComponentType(),EntityStatMap.getComponentType());}
        @Override public void onEntityAdded(Ref<EntityStore> ref,AddReason reason,Store<EntityStore> store,CommandBuffer<EntityStore> buffer){}
        @Override public void onEntityRemove(Ref<EntityStore> ref,RemoveReason reason,Store<EntityStore> store,CommandBuffer<EntityStore> buffer){
            try{support.detach(store,ref,"NATIVE_ENTITY_REMOVE_"+reason);}
            catch(RuntimeException failure){com.hypixel.hytale.logger.HytaleLogger.getLogger().atWarning().withCause(failure)
                    .log("RPG support teardown failed; no active Aura is retained");}
        }
    }
}
