package com.inigmasgames.hytalerpg.combat.hytale;

import com.hypixel.hytale.component.*;
import com.hypixel.hytale.component.dependency.*;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.server.core.meta.MetaKey;
import com.hypixel.hytale.server.core.modules.entity.damage.*;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatMap;
import com.hypixel.hytale.server.core.modules.entitystats.asset.DefaultEntityStatTypes;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.*;

/** Extends the existing synchronous native leaf scope; never submits or computes damage. */
public final class NativeDamageLeafReceipts {
    private NativeDamageLeafReceipts(){}
    public interface Scope extends NativeDamageLeafInteraction.Scope {
        /** Called only for the active native leaf's own actor/target and non-RPG component. */
        java.util.function.Consumer<HytaleDamageAdapter.NativeResult> component(Damage damage);
        default boolean sourceFactorsResolved(){return false;}
    }
    private static final class Observation {
        final java.util.function.Consumer<HytaleDamageAdapter.NativeResult> consumer;
        final double sourceAmount;
        final boolean sourceFactorsResolved;
        double before=Double.NaN;boolean sampled,completed;
        Observation(java.util.function.Consumer<HytaleDamageAdapter.NativeResult> consumer,double amount,boolean resolved){this.consumer=consumer;sourceAmount=amount;sourceFactorsResolved=resolved;}
    }
    private static final MetaKey<Observation> RECEIPT=Damage.META_REGISTRY.registerMetaObject(ignored->null,false,"InigmasGames:NativeLeafReceipt",null);
    public static boolean sourceFactorsResolved(Damage damage){var observation=damage.getIfPresentMetaObject(RECEIPT);return observation!=null&&observation.sourceFactorsResolved;}
    /** Reuse the same native post-Apply Health observer for a certified original projectile packet. */
    public static void observeProjectile(Damage damage,java.util.function.Consumer<HytaleDamageAdapter.NativeResult> consumer){
        Objects.requireNonNull(damage);Objects.requireNonNull(consumer);
        if(damage.getIfPresentMetaObject(RECEIPT)!=null)throw new IllegalStateException("NATIVE_PROJECTILE_PACKET_REPLAY");
        damage.putMetaObject(RECEIPT,new Observation(consumer,damage.getAmount(),true));
    }
    private static double health(ArchetypeChunk<EntityStore> chunk,int index){
        var stats=chunk.getComponent(index,EntityStatMap.getComponentType());
        var hp=stats==null?null:stats.get(DefaultEntityStatTypes.getHealth());return hp==null?Double.NaN:hp.get();
    }
    public static final class Gather extends DamageEventSystem {
        @Override public Query<EntityStore> getQuery(){return Query.any();}
        @Override public SystemGroup<EntityStore> getGroup(){return DamageModule.get().getGatherDamageGroup();}
        @Override public void handle(int index,ArchetypeChunk<EntityStore> chunk,Store<EntityStore> store,CommandBuffer<EntityStore> buffer,Damage damage){
            var call=NativeDamageLeafInteraction.currentInvocation();
            if(call==null||!(call.scope() instanceof Scope scope)||HytaleDamageAdapter.metadata(damage)!=null
                    ||damage.getIfPresentMetaObject(Damage.INTERACTION_TYPE)==null
                    ||!(damage.getSource() instanceof Damage.EntitySource source))return;
            var context=call.context();
            if(context.getCommandBuffer()!=buffer||context.getTargetEntity()!=chunk.getReferenceTo(index)
                    ||context.getOwningEntity()!=source.getRef())return;
            if(damage.getIfPresentMetaObject(RECEIPT)!=null)throw new IllegalStateException("NATIVE_LEAF_PACKET_REPLAY");
            var consumer=scope.component(damage);
            if(consumer!=null)damage.putMetaObject(RECEIPT,new Observation(consumer,damage.getAmount(),scope.sourceFactorsResolved()));
        }
    }
    public static final class Before extends DamageEventSystem {
        @Override public Query<EntityStore> getQuery(){return Query.any();}
        @Override public Set<Dependency<EntityStore>> getDependencies(){return Set.of(
                new SystemGroupDependency<>(Order.AFTER,DamageModule.get().getFilterDamageGroup()),
                new SystemDependency<>(Order.AFTER,HytaleDamageLifecycleSystems.Filter.class),
                new SystemDependency<>(Order.BEFORE,DamageSystems.ApplyDamage.class));}
        @Override public void handle(int index,ArchetypeChunk<EntityStore> chunk,Store<EntityStore> store,CommandBuffer<EntityStore> buffer,Damage damage){
            var observation=damage.getIfPresentMetaObject(RECEIPT);if(observation==null)return;
            if(observation.sampled)throw new IllegalStateException("NATIVE_LEAF_APPLICATION_REPLAY");
            observation.before=health(chunk,index);observation.sampled=true;
        }
    }
    public static final class After extends DamageEventSystem {
        @Override public Query<EntityStore> getQuery(){return Query.any();}
        @Override public Set<Dependency<EntityStore>> getDependencies(){return Set.of(
                new SystemDependency<>(Order.AFTER,DamageSystems.ApplyDamage.class),
                new SystemGroupDependency<>(Order.BEFORE,DamageModule.get().getInspectDamageGroup()));}
        @Override public void handle(int index,ArchetypeChunk<EntityStore> chunk,Store<EntityStore> store,CommandBuffer<EntityStore> buffer,Damage damage){
            var observation=damage.getIfPresentMetaObject(RECEIPT);if(observation==null)return;
            if(!observation.sampled||observation.completed)throw new IllegalStateException("NATIVE_LEAF_APPLICATION_ORDER");
            observation.completed=true;
            observation.consumer.accept(new HytaleDamageAdapter.NativeResult(damage.isCancelled(),damage.getAmount(),
                    observation.before,health(chunk,index),observation.sourceAmount));
        }
    }
}
