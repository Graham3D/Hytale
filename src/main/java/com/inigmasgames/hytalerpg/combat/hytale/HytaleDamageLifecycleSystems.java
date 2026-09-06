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

/** Evidence hooks around Hytale's native Gather -> Filter -> Apply -> Inspect sequence. */
public final class HytaleDamageLifecycleSystems {
    private HytaleDamageLifecycleSystems() { }
    private abstract static class TraceSystem extends DamageEventSystem {
        final CombatTrace trace;
        TraceSystem(CombatTrace trace) { this.trace = trace; }
        @Override public Query<EntityStore> getQuery() { return Query.any(); }
        void emit(Damage damage, RpgTraceEventType type, Map<String, ?> details) {
            HytaleDamageMetadata metadata = HytaleDamageAdapter.metadata(damage);
            if (metadata != null) trace.emit(metadata.actorId(), type,
                    new CombatTrace.Context(metadata.rootCastId(), metadata.skillInstanceId(), metadata.correlationId()), details);
        }
    }
    public static final class Gather extends TraceSystem {
        public Gather(CombatTrace trace) { super(trace); }
        @Override public SystemGroup<EntityStore> getGroup() { return DamageModule.get().getGatherDamageGroup(); }
        @Override public void handle(int index, ArchetypeChunk<EntityStore> chunk, Store<EntityStore> store,
                                     CommandBuffer<EntityStore> buffer, Damage damage) {
            emit(damage, RpgTraceEventType.DAMAGE_GATHERED, Map.of("amount", damage.getAmount()));
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
                            "cancelled", damage.isCancelled()));
        }
    }
}
