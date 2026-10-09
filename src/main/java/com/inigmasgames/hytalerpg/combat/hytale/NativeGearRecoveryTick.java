package com.inigmasgames.hytalerpg.combat.hytale;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.tick.EntityTickingSystem;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatMap;
import com.hypixel.hytale.server.core.modules.entitystats.asset.DefaultEntityStatTypes;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.inigmasgames.hytalerpg.combat.resource.GearRecoveryRuntime;
import com.inigmasgames.hytalerpg.combat.resource.GearRecoveryPayout;
import com.inigmasgames.hytalerpg.combat.resource.RpgResourceService;
import java.util.Objects;
import java.util.UUID;
import java.util.function.DoubleSupplier;

/** Pays pending recovery on the native world thread through support and resource owners. */
public final class NativeGearRecoveryTick extends EntityTickingSystem<EntityStore> {
    @FunctionalInterface public interface HealthCredit {
        /** Must use the support owner's self-heal admission and return actual native HP gain. */
        double credit(Store<EntityStore> store,CommandBuffer<EntityStore> buffer,Ref<EntityStore> actor,
                      double requested,double normalMaximum);
    }
    @FunctionalInterface public interface NormalHealthMaximum {
        double maximum(Store<EntityStore> store,Ref<EntityStore> actor,EntityStatMap stats);
    }
    private final GearRecoveryRuntime recovery;
    private final GearAppliedHitRecovery applied;
    private final GearRecoveryPayout payout;
    private final HealthCredit health;
    private final NormalHealthMaximum normalHealth;
    private final DoubleSupplier clock;
    private double nextMaintenance;
    public NativeGearRecoveryTick(GearRecoveryRuntime recovery,GearAppliedHitRecovery applied,
                                  RpgResourceService resources,HealthCredit health,
                                  NormalHealthMaximum normalHealth) {
        this(recovery,applied,resources,health,normalHealth,()->System.nanoTime()/1e9);
    }
    NativeGearRecoveryTick(GearRecoveryRuntime recovery,GearAppliedHitRecovery applied,
                           RpgResourceService resources,HealthCredit health,
                           NormalHealthMaximum normalHealth,DoubleSupplier clock) {
        this.recovery=Objects.requireNonNull(recovery);this.applied=Objects.requireNonNull(applied);
        this.payout=new GearRecoveryPayout(recovery,Objects.requireNonNull(resources));this.health=Objects.requireNonNull(health);
        this.normalHealth=Objects.requireNonNull(normalHealth);this.clock=Objects.requireNonNull(clock);
    }
    @Override public Query<EntityStore> getQuery(){
        return Query.and(UUIDComponent.getComponentType(),EntityStatMap.getComponentType());
    }
    @Override public void tick(float delta,int index,ArchetypeChunk<EntityStore> chunk,
                               Store<EntityStore> store,CommandBuffer<EntityStore> buffer){
        double now=clock.getAsDouble();
        if(!Double.isFinite(now)||now<0)return;
        if(now>=nextMaintenance){
            recovery.maintain(now);applied.maintain(now);nextMaintenance=now+1;
        }
        var identity=chunk.getComponent(index,UUIDComponent.getComponentType());
        var stats=chunk.getComponent(index,EntityStatMap.getComponentType());
        if(identity==null||stats==null)return;
        UUID actor=identity.getUuid();
        var hp=stats.get(DefaultEntityStatTypes.getHealth());
        if(hp==null)return;
        if(hp.get()<=hp.getMin()){recovery.cancel(actor);applied.cancel(actor);return;}
        String world=store.getExternalData().getWorld().getWorldConfig().getUuid().toString();
        boolean hitHealth=recovery.hasPending(actor,world,GearRecoveryRuntime.Pool.HIT_HEALTH);
        boolean killHealth=recovery.hasPending(actor,world,GearRecoveryRuntime.Pool.KILL_HEALTH);
        boolean hitMana=recovery.hasPending(actor,world,GearRecoveryRuntime.Pool.HIT_MANA);
        if(!hitHealth&&!killHealth&&!hitMana)return;
        Ref<EntityStore> ref=chunk.getReferenceTo(index);
        double maximum=hitHealth||killHealth?normalHealth.maximum(store,ref,stats):0;
        payout.pay(actor,world,maximum,now,new EntityStatResourcePort(stats),
                (requested,cap)->health.credit(store,buffer,ref,requested,cap));
    }
}
