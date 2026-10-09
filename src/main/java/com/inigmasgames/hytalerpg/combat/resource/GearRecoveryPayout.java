package com.inigmasgames.hytalerpg.combat.resource;

import java.util.Objects;
import java.util.UUID;

/** Shared production drain used by the native recovery tick. */
public final class GearRecoveryPayout {
    @FunctionalInterface public interface Health {
        double admit(double requested,double normalMaximum);
    }
    public record Result(double hitHealth,double killHealth,double hitMana) { }
    private final GearRecoveryRuntime recovery;
    private final RpgResourceService resources;
    public GearRecoveryPayout(GearRecoveryRuntime recovery,RpgResourceService resources){
        this.recovery=Objects.requireNonNull(recovery);this.resources=Objects.requireNonNull(resources);
    }
    public Result pay(UUID actor,String world,double normalHealthMaximum,double now,
                      NativeResourcePort nativeResources,Health health){
        Objects.requireNonNull(actor);Objects.requireNonNull(world);
        Objects.requireNonNull(nativeResources);Objects.requireNonNull(health);
        double hitHealth=0,killHealth=0,hitMana=0;
        if(Double.isFinite(normalHealthMaximum)&&normalHealthMaximum>0){
            if(recovery.hasPending(actor,world,GearRecoveryRuntime.Pool.HIT_HEALTH))
                hitHealth=recovery.pay(actor,world,GearRecoveryRuntime.Pool.HIT_HEALTH,normalHealthMaximum,now,
                        (pool,amount,maximum)->health.admit(amount,maximum)).amount();
            if(recovery.hasPending(actor,world,GearRecoveryRuntime.Pool.KILL_HEALTH))
                killHealth=recovery.pay(actor,world,GearRecoveryRuntime.Pool.KILL_HEALTH,normalHealthMaximum,now,
                        (pool,amount,maximum)->health.admit(amount,maximum)).amount();
        }
        if(recovery.hasPending(actor,world,GearRecoveryRuntime.Pool.HIT_MANA)){
            double maximum=resources.spendableMaximum(actor,ResourceType.MANA,nativeResources);
            if(Double.isFinite(maximum)&&maximum>=0)
                hitMana=recovery.pay(actor,world,GearRecoveryRuntime.Pool.HIT_MANA,maximum,now,
                        (pool,amount,cap)->nativeResources.restoreResourceAtMost(ResourceType.MANA,amount,cap)).amount();
        }
        return new Result(hitHealth,killHealth,hitMana);
    }
}
