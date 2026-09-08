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
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.inigmasgames.hytalerpg.combat.resource.HostileCombatTracker;
import com.inigmasgames.hytalerpg.execution.hytale.HytaleSkillExecutionSystem;

/** Evidence hooks around Hytale's native Gather -> Filter -> Apply -> Inspect sequence. */
public final class HytaleDamageLifecycleSystems {
    private HytaleDamageLifecycleSystems() { }
    private abstract static class TraceSystem extends DamageEventSystem {
        final CombatTrace trace;
        TraceSystem(CombatTrace trace) { this.trace = trace; }
        @Override public Query<EntityStore> getQuery() { return Query.any(); }
        void emit(Damage damage, RpgTraceEventType type, Map<String, ?> details) {
            HytaleDamageMetadata metadata = HytaleDamageAdapter.metadata(damage);
            if (metadata != null) {
                var values=new java.util.HashMap<String,Object>(details);values.put("effectInstanceId",metadata.effectInstanceId());values.put("canProc",metadata.canProc());
                trace.emit(metadata.actorId(), type,
                    new CombatTrace.Context(metadata.rootCastId(), metadata.skillInstanceId(), metadata.correlationId()), values);
            }
        }
    }
    public static final class Gather extends TraceSystem {
        private final com.inigmasgames.hytalerpg.combat.status.StatusService statuses;
        public Gather(CombatTrace trace) { this(trace,null); }
        public Gather(CombatTrace trace,com.inigmasgames.hytalerpg.combat.status.StatusService statuses) { super(trace);this.statuses=statuses; }
        @Override public SystemGroup<EntityStore> getGroup() { return DamageModule.get().getGatherDamageGroup(); }
        @Override public void handle(int index, ArchetypeChunk<EntityStore> chunk, Store<EntityStore> store,
                                     CommandBuffer<EntityStore> buffer, Damage damage) {
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
                details.putAll(HytaleConditionalDamage.gather(damage,health==null?Double.NaN:health.get(),health==null?Double.NaN:health.getMax(),active));
            }
            details.put("amount",damage.getAmount());details.put("cancelled",damage.isCancelled());
            emit(damage, RpgTraceEventType.DAMAGE_GATHERED, details);
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
            emit(damage, RpgTraceEventType.DAMAGE_FILTERED,
                    Map.of("amount", damage.getAmount(), "cancelled", damage.isCancelled()));
        }
    }
    public static final class Application extends TraceSystem {
        public Application(CombatTrace trace) { super(trace); }
        @Override public Set<Dependency<EntityStore>> getDependencies() {
            return Set.of(new SystemDependency<>(Order.AFTER, DamageSystems.ApplyDamage.class),
                    new SystemGroupDependency<>(Order.BEFORE, DamageModule.get().getInspectDamageGroup()));
        }
        @Override public void handle(int index, ArchetypeChunk<EntityStore> chunk, Store<EntityStore> store,
                                     CommandBuffer<EntityStore> buffer, Damage damage) {
            emit(damage, RpgTraceEventType.DAMAGE_APPLIED,
                    Map.of("nativeAmount", damage.getAmount(), "cancelled", damage.isCancelled()));
        }
    }
    public static final class Inspect extends TraceSystem {
        private final HostileCombatTracker combat;
        public Inspect(CombatTrace trace, HostileCombatTracker combat) { super(trace); this.combat = combat; }
        @Override public SystemGroup<EntityStore> getGroup() { return DamageModule.get().getInspectDamageGroup(); }
        @Override public void handle(int index, ArchetypeChunk<EntityStore> chunk, Store<EntityStore> store,
                                     CommandBuffer<EntityStore> buffer, Damage damage) {
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
            if (metadata == null) return;
            EntityStatMap stats = chunk.getComponent(index, EntityStatMap.getComponentType());
            double after = stats == null || stats.get(DefaultEntityStatTypes.getHealth()) == null ? Double.NaN
                    : stats.get(DefaultEntityStatTypes.getHealth()).get();
            trace.emit(metadata.actorId(), RpgTraceEventType.DAMAGE_INSPECTED,
                    new CombatTrace.Context(metadata.rootCastId(), metadata.skillInstanceId(), metadata.correlationId()),
                    Map.of("preMitigation", metadata.preMitigationDamage(), "filteredAmount", damage.getAmount(),
                            "healthBefore", metadata.targetHealthBefore(), "healthAfter", after,
                            "actualHealthLoss", Double.isFinite(after) && Double.isFinite(metadata.targetHealthBefore())
                                    ? Math.max(0.0, metadata.targetHealthBefore() - after) : -1.0,
                            "cancelled", damage.isCancelled(),"effectInstanceId",metadata.effectInstanceId(),"canProc",metadata.canProc()));
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
