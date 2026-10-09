package com.inigmasgames.hytalerpg.combat.hytale;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.SystemGroup;
import com.hypixel.hytale.component.dependency.Dependency;
import com.hypixel.hytale.component.dependency.Order;
import com.hypixel.hytale.component.dependency.SystemDependency;
import com.hypixel.hytale.component.dependency.SystemGroupDependency;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.server.core.modules.entity.damage.Damage;
import com.hypixel.hytale.server.core.modules.entity.damage.DamageCause;
import com.hypixel.hytale.server.core.modules.entity.damage.DamageEventSystem;
import com.hypixel.hytale.server.core.modules.entity.damage.DamageModule;
import com.hypixel.hytale.server.core.modules.entity.damage.DamageSystems;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatMap;
import com.hypixel.hytale.server.core.modules.entitystats.asset.DefaultEntityStatTypes;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.inigmasgames.hytalerpg.combat.diagnostics.CombatTrace;
import com.inigmasgames.hytalerpg.diagnostics.RpgTraceEventType;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.DoubleSupplier;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.inigmasgames.hytalerpg.combat.resource.HostileCombatTracker;
import com.inigmasgames.hytalerpg.execution.hytale.HytaleSkillExecutionSystem;
import com.inigmasgames.hytalerpg.execution.hytale.EnemyHealthBarPresentation;

/** Evidence hooks around Hytale's native Gather -> Filter -> Apply -> Inspect sequence. */
public final class HytaleDamageLifecycleSystems {
    private HytaleDamageLifecycleSystems() { }
    private static final com.hypixel.hytale.server.core.meta.MetaKey<Double> HEALTH_BEFORE_APPLY =
            Damage.META_REGISTRY.registerMetaObject(ignored -> null,false,
                    "InigmasGames:RpgHealthImmediatelyBeforeApply",null);
    private static final com.hypixel.hytale.server.core.meta.MetaKey<Double> HEALTH_BAR_BEFORE =
            Damage.META_REGISTRY.registerMetaObject(ignored -> null,false,
                    "InigmasGames:EnemyHealthBarBefore",null);
    private static final com.hypixel.hytale.server.core.meta.MetaKey<Double> ACCEPTED_BEFORE_ABSORPTION =
            Damage.META_REGISTRY.registerMetaObject(ignored -> null,false,
                    "InigmasGames:RpgAcceptedBeforeAbsorption",null);
    public record AppliedReceipt(HytaleDamageMetadata metadata,HytaleDamageAdapter.GearHitSource gearHit,
                                 UUID worldId,UUID targetId,double before,double after,double actualHealthLoss,
                                 boolean cancelled,boolean blocked,double acceptedDamage,
                                 com.inigmasgames.hytalerpg.execution.SkillExecutionContext executionContext,
                                 Damage nativeDamage,double itemProcCredit) {
        /** Item-only share committed once before all applied observers run. */
        public double procCoefficient() {return itemProcCredit;}
        public AppliedReceipt(HytaleDamageMetadata metadata,HytaleDamageAdapter.GearHitSource gearHit,
                              UUID worldId,UUID targetId,double before,double after,double actualHealthLoss,
                              boolean cancelled,boolean blocked,double acceptedDamage,
                              com.inigmasgames.hytalerpg.execution.SkillExecutionContext executionContext,
                              Damage nativeDamage){
            this(metadata,gearHit,worldId,targetId,before,after,actualHealthLoss,cancelled,blocked,
                    acceptedDamage,executionContext,nativeDamage,0);
        }
        public AppliedReceipt(HytaleDamageMetadata metadata,HytaleDamageAdapter.GearHitSource gearHit,
                              UUID targetId,double before,double after,double actualHealthLoss,boolean cancelled,boolean blocked) {
            this(metadata,gearHit,null,targetId,before,after,actualHealthLoss,cancelled,blocked,
                    actualHealthLoss,null,null,0);
        }
    }
    /** Records an admitted contact before barriers consume it. Absorption is not a failed hit. */
    public static final class BeforeAbsorption extends DamageEventSystem {
        @Override public Query<EntityStore> getQuery(){return Query.any();}
        @Override public Set<Dependency<EntityStore>> getDependencies(){return Set.of(
                new SystemGroupDependency<>(Order.AFTER,DamageModule.get().getFilterDamageGroup()),
                new SystemDependency<>(Order.AFTER,HytaleDamageAdapter.GearResistanceFilter.class),
                new SystemDependency<>(Order.AFTER,com.inigmasgames.hytalerpg.execution.hytale.SupportDamageSystems.HealthCap.class),
                new SystemDependency<>(Order.BEFORE,com.inigmasgames.hytalerpg.execution.hytale.SupportDamageSystems.Shield.class),
                new SystemDependency<>(Order.BEFORE,com.inigmasgames.hytalerpg.execution.hytale.HytaleSupportSystem.Absorb.class),
                new SystemDependency<>(Order.BEFORE,DamageSystems.ApplyDamage.class));}
        @Override public void handle(int index,ArchetypeChunk<EntityStore> chunk,Store<EntityStore> store,
                                     CommandBuffer<EntityStore> buffer,Damage damage){
            if(HytaleDamageAdapter.metadata(damage)!=null)
                damage.putMetaObject(ACCEPTED_BEFORE_ABSORPTION,damage.isCancelled()?0:Math.max(0,(double)damage.getAmount()));
        }
    }
    /** Called after native ApplyDamage with one correlated HP observation, before Inspect callbacks. */
    public interface AppliedObserver {
        void observed(AppliedReceipt receipt,com.hypixel.hytale.component.Ref<EntityStore> target,
                      com.hypixel.hytale.component.Ref<EntityStore> source,
                      CommandBuffer<EntityStore> buffer);
    }
    /** Captures Health after all Filters and directly before native ApplyDamage. */
    public static final class BeforeApplication extends DamageEventSystem {
        @Override public Query<EntityStore> getQuery(){return Query.any();}
        @Override public Set<Dependency<EntityStore>> getDependencies(){return Set.of(
                new SystemGroupDependency<>(Order.AFTER,DamageModule.get().getFilterDamageGroup()),
                new SystemDependency<>(Order.AFTER,com.inigmasgames.hytalerpg.execution.hytale.SupportDamageSystems.Shield.class),
                new SystemDependency<>(Order.AFTER,com.inigmasgames.hytalerpg.execution.hytale.HytaleSupportSystem.Absorb.class),
                new SystemDependency<>(Order.BEFORE,DamageSystems.ApplyDamage.class));}
        @Override public void handle(int index,ArchetypeChunk<EntityStore> chunk,Store<EntityStore> store,
                                     CommandBuffer<EntityStore> buffer,Damage damage){
            if(HytaleDamageAdapter.metadata(damage)==null)return;
            var stats=chunk.getComponent(index,EntityStatMap.getComponentType());
            var health=stats==null?null:stats.get(DefaultEntityStatTypes.getHealth());
            if(health!=null)damage.putMetaObject(HEALTH_BEFORE_APPLY,(double)health.get());
        }
    }
    private abstract static class TraceSystem extends DamageEventSystem {
        final CombatTrace trace;
        TraceSystem(CombatTrace trace) { this.trace = trace; }
        @Override public Query<EntityStore> getQuery() { return Query.any(); }
        void emit(Damage damage, RpgTraceEventType type, Map<String, ?> details) {
            HytaleDamageMetadata metadata = HytaleDamageAdapter.metadata(damage);
            if (metadata != null) {
                var values=new java.util.HashMap<String,Object>(details);values.put("effectInstanceId",metadata.effectInstanceId());values.put("canProc",metadata.canProc());
                if(metadata.monsterAffix()!=null){values.put("sourceKind","MONSTER_AFFIX");values.put("affixId",metadata.monsterAffix().affixId());
                    values.put("encounterGeneration",metadata.monsterAffix().encounterGeneration());values.put("logicalActorId",metadata.monsterAffix().logicalActorId());}
                trace.emit(metadata.actorId(), type,
                    new CombatTrace.Context(metadata.rootCastId(), metadata.skillInstanceId(), metadata.correlationId()), values);
            }
        }
    }
    public static final class Gather extends TraceSystem {
        private final com.inigmasgames.hytalerpg.combat.status.StatusService statuses;
        private final EnemyHealthBarPresentation healthBars;
        public Gather(CombatTrace trace) { this(trace,null); }
        public Gather(CombatTrace trace,com.inigmasgames.hytalerpg.combat.status.StatusService statuses) { this(trace,statuses,null); }
        public Gather(CombatTrace trace,com.inigmasgames.hytalerpg.combat.status.StatusService statuses,EnemyHealthBarPresentation healthBars) {
            super(trace);this.statuses=statuses;this.healthBars=healthBars;
        }
        @Override public SystemGroup<EntityStore> getGroup() { return DamageModule.get().getGatherDamageGroup(); }
        @Override public void handle(int index, ArchetypeChunk<EntityStore> chunk, Store<EntityStore> store,
                                     CommandBuffer<EntityStore> buffer, Damage damage) {
        try(var rpgTickSpan=com.inigmasgames.hytalerpg.diagnostics.NativeRpgTickMetrics.enter(store,com.inigmasgames.hytalerpg.diagnostics.NativeRpgTickMetrics.Phase.DAMAGE)){
            if(healthBars!=null && damage.getSource() instanceof Damage.EntitySource source
                    && source.getRef()!=null && source.getRef().isValid()
                    && buffer.getComponent(source.getRef(),PlayerRef.getComponentType())!=null){
                var stats=chunk.getComponent(index,EntityStatMap.getComponentType());
                var hp=stats==null?null:stats.get(DefaultEntityStatTypes.getHealth());
                if(hp!=null)damage.putMetaObject(HEALTH_BAR_BEFORE,(double)hp.get());
            }
            var details=new java.util.HashMap<String,Object>();
            if(HytaleConditionalDamage.pending(damage)){
                var stats=chunk.getComponent(index,EntityStatMap.getComponentType());var health=stats==null?null:stats.get(DefaultEntityStatTypes.getHealth());
                var id=chunk.getComponent(index,com.hypixel.hytale.server.core.entity.UUIDComponent.getComponentType());
                var active=new java.util.HashSet<String>();
                if(id!=null&&statuses!=null)for(var type:statuses.inspect(id.getUuid()).active().keySet())
                    if(Set.of(com.inigmasgames.hytalerpg.combat.status.RpgStatusType.ROOT,com.inigmasgames.hytalerpg.combat.status.RpgStatusType.FROZEN,
                            com.inigmasgames.hytalerpg.combat.status.RpgStatusType.FEAR).contains(type))active.add(type.name());
                // STAGGER is not a semantic synonym for STUN. Require the actual installed stun effect.
                var effects=chunk.getComponent(index,com.hypixel.hytale.server.core.entity.effect.EffectControllerComponent.getComponentType());
                var stun=com.hypixel.hytale.server.core.asset.type.entityeffect.config.EntityEffect.getAssetMap().getAsset("Stun");
                if(effects!=null&&stun!=null&&effects.hasEffect(stun))active.add("STUN");
                com.inigmasgames.hytalerpg.execution.math.Vec3 forward=null,offset=null;
                var victim=chunk.getComponent(index,com.hypixel.hytale.server.core.modules.entity.component.TransformComponent.getComponentType());
                if(HytaleConditionalDamage.requiresFacing(damage)&&victim!=null&&damage.getSource() instanceof Damage.EntitySource source&&source.getRef()!=null&&source.getRef().isValid()){
                    var attacker=buffer.getComponent(source.getRef(),com.hypixel.hytale.server.core.modules.entity.component.TransformComponent.getComponentType());
                    if(attacker!=null){
                        var direction=victim.getRotation().transform(new org.joml.Vector3d(0,0,1));
                        var delta=new org.joml.Vector3d(attacker.getPosition()).sub(victim.getPosition());
                        if(direction.isFinite()&&delta.isFinite()){
                            forward=new com.inigmasgames.hytalerpg.execution.math.Vec3(direction.x,direction.y,direction.z);
                            offset=new com.inigmasgames.hytalerpg.execution.math.Vec3(delta.x,delta.y,delta.z);
                        } // Missing/nonfinite native geometry is rejected by the conditional Gather, not a world-thread exception.
                    }
                }
                details.putAll(HytaleConditionalDamage.gather(damage,health==null?Double.NaN:health.get(),health==null?Double.NaN:health.getMax(),active,forward,offset));
            }
            details.put("amount",damage.getAmount());details.put("cancelled",damage.isCancelled());
            emit(damage, RpgTraceEventType.DAMAGE_GATHERED, details);

        }
    }
    }
    public static final class Filter extends TraceSystem {
        public Filter(CombatTrace trace) { super(trace); }
        @Override public Set<Dependency<EntityStore>> getDependencies() {
            return Set.of(new SystemGroupDependency<>(Order.AFTER, DamageModule.get().getFilterDamageGroup()),
                    new SystemDependency<>(Order.BEFORE, DamageSystems.ApplyDamage.class));
        }
        @Override public void handle(int index, ArchetypeChunk<EntityStore> chunk, Store<EntityStore> store,
                                     CommandBuffer<EntityStore> buffer, Damage damage) {
        try(var rpgTickSpan=com.inigmasgames.hytalerpg.diagnostics.NativeRpgTickMetrics.enter(store,com.inigmasgames.hytalerpg.diagnostics.NativeRpgTickMetrics.Phase.DAMAGE)){
            emit(damage, RpgTraceEventType.DAMAGE_FILTERED,
                    Map.of("amount", damage.getAmount(), "cancelled", damage.isCancelled()));

        }
    }
    }
    /** Applies the shared Electrified Physical-miss contract at the native post-filter boundary. */
    public static final class ElectrifiedPhysicalMiss extends DamageEventSystem {
        private final com.inigmasgames.hytalerpg.combat.status.StatusService statuses;
        private final DoubleSupplier roll;
        public ElectrifiedPhysicalMiss(com.inigmasgames.hytalerpg.combat.status.StatusService statuses){this(statuses,Math::random);}
        ElectrifiedPhysicalMiss(com.inigmasgames.hytalerpg.combat.status.StatusService statuses,DoubleSupplier roll){
            this.statuses=java.util.Objects.requireNonNull(statuses);this.roll=java.util.Objects.requireNonNull(roll);
        }
        @Override public Query<EntityStore> getQuery(){return com.hypixel.hytale.server.core.entity.UUIDComponent.getComponentType();}
        @Override public Set<Dependency<EntityStore>> getDependencies(){return Set.of(
                new SystemGroupDependency<>(Order.AFTER,DamageModule.get().getFilterDamageGroup()),
                new SystemDependency<>(Order.BEFORE,Filter.class));}
        @Override public void handle(int index,ArchetypeChunk<EntityStore> chunk,Store<EntityStore> store,
                                     CommandBuffer<EntityStore> buffer,Damage damage){
            if(damage.isCancelled()||damage.getAmount()<=0||damage.getCause()!=DamageCause.PHYSICAL
                    ||!(damage.getSource() instanceof Damage.EntitySource source)||source.getRef()==chunk.getReferenceTo(index))return;
            UUID id=chunk.getComponent(index,com.hypixel.hytale.server.core.entity.UUIDComponent.getComponentType()).getUuid();
            double chance=statuses.physicalMissChance(id),sample=roll.getAsDouble();
            if(shouldMiss(chance,sample))damage.setCancelled(true);
        }
        public static boolean shouldMiss(double chance,double roll){
            if(!Double.isFinite(chance)||chance<0||chance>.25||!Double.isFinite(roll)||roll<0||roll>=1)
                throw new IllegalArgumentException("INVALID_PHYSICAL_MISS_INPUT");
            return roll<chance;
        }
    }
    public static final class Application extends TraceSystem {
        private final AppliedObserver observer;
        public Application(CombatTrace trace) { this(trace,null); }
        public Application(CombatTrace trace,AppliedObserver observer) { super(trace);this.observer=observer; }
        @Override public Set<Dependency<EntityStore>> getDependencies() {
            return Set.of(new SystemDependency<>(Order.AFTER, DamageSystems.ApplyDamage.class),
                    new SystemGroupDependency<>(Order.BEFORE, DamageModule.get().getInspectDamageGroup()));
        }
        @Override public void handle(int index, ArchetypeChunk<EntityStore> chunk, Store<EntityStore> store,
                                     CommandBuffer<EntityStore> buffer, Damage damage) {
        try(var rpgTickSpan=com.inigmasgames.hytalerpg.diagnostics.NativeRpgTickMetrics.enter(store,com.inigmasgames.hytalerpg.diagnostics.NativeRpgTickMetrics.Phase.DAMAGE)){
            emit(damage, RpgTraceEventType.DAMAGE_APPLIED,
                    Map.of("nativeAmount", damage.getAmount(), "cancelled", damage.isCancelled()));
            if(observer!=null){
                var metadata=HytaleDamageAdapter.metadata(damage);
                var before=damage.getIfPresentMetaObject(HEALTH_BEFORE_APPLY);
                var stats=chunk.getComponent(index,EntityStatMap.getComponentType());
                var health=stats==null?null:stats.get(DefaultEntityStatTypes.getHealth());
                var id=chunk.getComponent(index,com.hypixel.hytale.server.core.entity.UUIDComponent.getComponentType());
                if(metadata!=null&&before!=null&&health!=null&&id!=null){
                    double after=health.get();
                    double loss=damage.isCancelled()?0:Math.min(Math.max(0,before),Math.max(0,before-after));
                    var source=damage.getSource() instanceof Damage.EntitySource entity?entity.getRef():null;
                    var gear=HytaleDamageAdapter.gearHit(damage);
                    var world=store.getExternalData().getWorld().getWorldConfig().getUuid();
                    boolean blocked=Boolean.TRUE.equals(damage.getIfPresentMetaObject(Damage.BLOCKED));
                    double accepted=java.util.Objects.requireNonNullElse(
                            damage.getIfPresentMetaObject(ACCEPTED_BEFORE_ABSORPTION),0d);
                    double itemCredit=0;
                    if(gear!=null&&source!=null&&source.isValid()&&metadata.origin()==HytaleDamageMetadata.Origin.DIRECT
                            &&!damage.isCancelled()&&!blocked&&accepted>0
                            &&hostile(store,chunk.getReferenceTo(index),source)){
                        var sourceId=buffer.getComponent(source,
                                com.hypixel.hytale.server.core.entity.UUIDComponent.getComponentType());
                        var npc=chunk.getComponent(index,com.hypixel.hytale.server.npc.entities.NPCEntity.getComponentType());
                        var effects=chunk.getComponent(index,
                                com.hypixel.hytale.server.core.entity.effect.EffectControllerComponent.getComponentType());
                        boolean protectedTarget=chunk.getComponent(index,
                                com.hypixel.hytale.server.core.modules.entity.component.Invulnerable.getComponentType())!=null
                                ||npc!=null&&npc.getRole()!=null&&npc.getRole().isInvulnerable()
                                ||effects!=null&&effects.isInvulnerable();
                        if(!protectedTarget&&sourceId!=null&&sourceId.getUuid().equals(metadata.actorId())
                                &&gear.hit()!=null&&gear.hit().itemId()!=null
                                &&gear.hit().rootId().equals(metadata.rootCastId())){
                            var hit=gear.hit();
                            double authored=0;
                            if(HytaleDamageAdapter.executionContext(damage)!=null){
                                var context=HytaleDamageAdapter.executionContext(damage);
                                if(!context.derivedRelease())
                                    authored=com.inigmasgames.hytalerpg.execution.HitProcRuntime.coefficient(context);
                            }
                            else{
                                var frozen=com.inigmasgames.hytalerpg.gear.NativeGearAttackAcceptance.find(
                                        world,metadata.actorId(),hit);
                                if(frozen!=null&&!frozen.noProc())authored=frozen.procCoefficient();
                            }
                            itemCredit=com.inigmasgames.hytalerpg.gear.NativeGearAttackAcceptance.itemProcBudget()
                                    .nativeCredit(world,metadata.actorId(),hit.rootId(),hit.itemId(),
                                            hit.snapshot().revision(),hit,id.getUuid(),gear.contactId(),
                                            gear.channel().name(),authored,true);
                        }
                    }
                    observer.observed(new AppliedReceipt(metadata,gear,
                            world,id.getUuid(),
                            before,after,loss,damage.isCancelled(),
                            blocked,accepted,
                            HytaleDamageAdapter.executionContext(damage),damage,itemCredit),
                            chunk.getReferenceTo(index),source,buffer);
                }
            }

        }
    }
    }
    private static boolean hostile(Store<EntityStore> store,com.hypixel.hytale.component.Ref<EntityStore> target,
                                   com.hypixel.hytale.component.Ref<EntityStore> source){
        if(!target.isValid()||!source.isValid()||target.equals(source))return false;
        var support=store.getComponent(target,
                com.hypixel.hytale.server.npc.role.support.WorldSupport.getComponentType());
        if(support==null)return false;
        try{
            return com.inigmasgames.hytalerpg.execution.hytale.NativeNpcAttitudes.prepared(support).getAttitude(target,source,store)==com.hypixel.hytale.server.core.asset.type.attitude.Attitude.HOSTILE;
        }catch(java.util.NoSuchElementException unavailable){return false;}
    }
    /** Same target gates used by the post-Apply item receipt, for producer admission. */
    public static boolean eligibleItemProcTarget(CommandBuffer<EntityStore> buffer,
            com.hypixel.hytale.component.Ref<EntityStore> target,
            com.hypixel.hytale.component.Ref<EntityStore> source){
        return target!=null&&source!=null&&target.isValid()&&source.isValid()
                &&hostile(buffer.getStore(),target,source)&&!protectedTarget(buffer,target);
    }
    private static boolean protectedTarget(CommandBuffer<EntityStore> buffer,
            com.hypixel.hytale.component.Ref<EntityStore> target){
        var npc=buffer.getComponent(target,com.hypixel.hytale.server.npc.entities.NPCEntity.getComponentType());
        var effects=buffer.getComponent(target,
                com.hypixel.hytale.server.core.entity.effect.EffectControllerComponent.getComponentType());
        return buffer.getComponent(target,
                com.hypixel.hytale.server.core.modules.entity.component.Invulnerable.getComponentType())!=null
                ||npc!=null&&npc.getRole()!=null&&npc.getRole().isInvulnerable()
                ||effects!=null&&effects.isInvulnerable();
    }
    public static final class Inspect extends TraceSystem {
        private final HostileCombatTracker combat;
        private final EnemyHealthBarPresentation healthBars;
        private final com.inigmasgames.hywind.compat.RpgGameplayEventPublisher gameplayEvents;
        public Inspect(CombatTrace trace, HostileCombatTracker combat) { this(trace,combat,null); }
        public Inspect(CombatTrace trace, HostileCombatTracker combat,EnemyHealthBarPresentation healthBars) {
            this(trace,combat,healthBars,com.inigmasgames.hywind.compat.RpgGameplayEventPublisher.NO_OP);
        }
        public Inspect(CombatTrace trace, HostileCombatTracker combat,EnemyHealthBarPresentation healthBars,
                com.inigmasgames.hywind.compat.RpgGameplayEventPublisher gameplayEvents) {
            super(trace); this.combat = combat;this.healthBars=healthBars;
            this.gameplayEvents=gameplayEvents==null
                    ? com.inigmasgames.hywind.compat.RpgGameplayEventPublisher.NO_OP : gameplayEvents;
        }
        @Override public SystemGroup<EntityStore> getGroup() { return DamageModule.get().getInspectDamageGroup(); }
        @Override public void handle(int index, ArchetypeChunk<EntityStore> chunk, Store<EntityStore> store,
                                     CommandBuffer<EntityStore> buffer, Damage damage) {
        try(var rpgTickSpan=com.inigmasgames.hytalerpg.diagnostics.NativeRpgTickMetrics.enter(store,com.inigmasgames.hytalerpg.diagnostics.NativeRpgTickMetrics.Phase.DAMAGE)){
            if (!damage.isCancelled() && damage.getAmount() > 0.0f) {
                PlayerRef targetPlayer = chunk.getComponent(index, PlayerRef.getComponentType());
                if (targetPlayer != null) combat.markHostile(targetPlayer.getUuid());
                if (damage.getSource() instanceof Damage.EntitySource source
                        && source.getRef() != chunk.getReferenceTo(index)) {
                    PlayerRef sourcePlayer = buffer.getComponent(source.getRef(), PlayerRef.getComponentType());
                    if (sourcePlayer != null) combat.markHostile(sourcePlayer.getUuid());
                }
            }
            HytaleDamageMetadata metadata = HytaleDamageAdapter.metadata(damage);
            if(healthBars!=null)try{
                var before=damage.getIfPresentMetaObject(HEALTH_BAR_BEFORE);
                if(before==null&&metadata!=null)before=damage.getIfPresentMetaObject(HEALTH_BEFORE_APPLY);
                if(before!=null){
                    var stats=chunk.getComponent(index,EntityStatMap.getComponentType());
                    var hp=stats==null?null:stats.get(DefaultEntityStatTypes.getHealth());
                    if(hp!=null){
                        double after=hp.get();
                        var target=chunk.getReferenceTo(index);
                        if(after<=0)healthBars.hideDead(store,target,buffer);
                        else if(!damage.isCancelled() && before>after){
                            com.hypixel.hytale.component.Ref<EntityStore> playerSource=null;
                            if(damage.getSource() instanceof Damage.EntitySource source)playerSource=source.getRef();
                            if((playerSource==null||!playerSource.isValid()
                                    ||store.getComponent(playerSource,PlayerRef.getComponentType())==null)
                                    &&metadata!=null&&!metadata.noCredit()&&metadata.actorId()!=null)
                                playerSource=store.getExternalData().getRefFromUUID(metadata.actorId());
                            healthBars.reveal(store,buffer,playerSource,target,before-after);
                        }
                    }
                }
            }catch(RuntimeException presentationFailure){
                com.hypixel.hytale.logger.HytaleLogger.getLogger().atWarning().log(
                        "RPG_ENEMY_HEALTHBAR outcome=PRESENTATION_FAILED reason=%s",
                        String.valueOf(presentationFailure));
            }
            if (metadata == null) return;
            EntityStatMap stats = chunk.getComponent(index, EntityStatMap.getComponentType());
            double after = stats == null || stats.get(DefaultEntityStatTypes.getHealth()) == null ? Double.NaN
                    : stats.get(DefaultEntityStatTypes.getHealth()).get();
            Double beforeApply=damage.getIfPresentMetaObject(HEALTH_BEFORE_APPLY);
            double before=beforeApply==null?metadata.targetHealthBefore():beforeApply;
            double actualHealthLoss = Double.isFinite(after) && Double.isFinite(before)
                    ? Math.min(Math.max(0,before),Math.max(0.0, before - after)) : -1.0;
            trace.emit(metadata.actorId(), RpgTraceEventType.DAMAGE_INSPECTED,
                    new CombatTrace.Context(metadata.rootCastId(), metadata.skillInstanceId(), metadata.correlationId()),
                    Map.of("preMitigation", metadata.preMitigationDamage(), "filteredAmount", damage.getAmount(),
                            "healthBefore", before, "healthAfter", after,
                            "actualHealthLoss", actualHealthLoss,
                            "cancelled", damage.isCancelled(),"effectInstanceId",metadata.effectInstanceId(),"canProc",metadata.canProc()));
            if (!damage.isCancelled() && Double.isFinite(after) && Double.isFinite(metadata.targetHealthBefore())) {
                if(actualHealthLoss>0.0){
                    var targetIdentity=chunk.getComponent(index,
                            com.hypixel.hytale.server.core.entity.UUIDComponent.getComponentType());
                    gameplayEvents.entityDamaged(
                            new com.inigmasgames.hywind.compat.RpgGameplayEventPublisher.DamageObservation(
                                    metadata.actorId(), targetIdentity==null?null:targetIdentity.getUuid(),
                                    metadata.skillInstanceId(),metadata.correlationId(),actualHealthLoss,after));
                }
            }

        }
    }
    }

    /** Observes Hytale's authenticated block result after native stamina handling. */
    public static final class ReactionObserver extends DamageEventSystem {
        private final HytaleSkillExecutionSystem skills;
        public ReactionObserver(HytaleSkillExecutionSystem skills) { this.skills = skills; }
        @Override public SystemGroup<EntityStore> getGroup() { return DamageModule.get().getInspectDamageGroup(); }
        @Override public Query<EntityStore> getQuery() { return PlayerRef.getComponentType(); }
        @Override public Set<Dependency<EntityStore>> getDependencies() {
            return Set.of(new SystemDependency<>(Order.AFTER, DamageSystems.DamageStamina.class));
        }
        @Override public void handle(int index, ArchetypeChunk<EntityStore> chunk, Store<EntityStore> store,
                                     CommandBuffer<EntityStore> buffer, Damage damage) {
        try(var rpgTickSpan=com.inigmasgames.hytalerpg.diagnostics.NativeRpgTickMetrics.enter(store,com.inigmasgames.hytalerpg.diagnostics.NativeRpgTickMetrics.Phase.DAMAGE)){
            PlayerRef defender = chunk.getComponent(index, PlayerRef.getComponentType());
            if (defender != null && !damage.isCancelled() && damage.getAmount() > 0.0f)
                skills.onIncomingDamage(defender.getUuid());
            if (!Boolean.TRUE.equals(damage.getIfPresentMetaObject(Damage.BLOCKED))
                    || !(damage.getSource() instanceof Damage.EntitySource source)
                    || source.getRef() == chunk.getReferenceTo(index)) return;
            String eventId = Integer.toHexString(System.identityHashCode(damage)) + ':' + index;
            skills.onNativeBlocked(chunk.getReferenceTo(index), source.getRef(), store, eventId);

        }
    }
    }
}
