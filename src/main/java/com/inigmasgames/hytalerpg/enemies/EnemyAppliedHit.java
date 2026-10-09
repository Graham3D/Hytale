package com.inigmasgames.hytalerpg.enemies;

import com.inigmasgames.hytalerpg.combat.hytale.HytaleDamageAdapter.NativeResult;
import java.util.*;

/** Component receipts from one existing native strike/victim. It owns no Health or damage submission. */
public record EnemyAppliedHit(EnemyOffenseSnapshot offense,UUID victim,Map<String,NativeResult> components,double actualHealthLoss) {
    public EnemyAppliedHit {
        Objects.requireNonNull(offense);Objects.requireNonNull(victim);components=Collections.unmodifiableMap(new TreeMap<>(components));
        if(!components.keySet().equals(offense.affixedVector().keySet()))throw new IllegalArgumentException("INCOMPLETE_ENEMY_HIT_RECEIPT");
        double sum=components.values().stream().mapToDouble(EnemyAppliedHit::healthLoss).sum();
        if(!Double.isFinite(actualHealthLoss)||actualHealthLoss<0||Double.compare(sum,actualHealthLoss)!=0)
            throw new IllegalArgumentException("ENEMY_HIT_HEALTH_LOSS_MISMATCH");
    }
    public static EnemyAppliedHit completed(EnemyOffenseSnapshot offense,UUID victim,Map<String,NativeResult> components){
        var sorted=new TreeMap<>(components);
        return new EnemyAppliedHit(offense,victim,sorted,sorted.values().stream().mapToDouble(EnemyAppliedHit::healthLoss).sum());
    }
    public static double healthLoss(NativeResult result){
        Objects.requireNonNull(result);
        if(!Double.isFinite(result.healthBefore())||!Double.isFinite(result.healthAfter())
                ||!Double.isFinite(result.nativeAmount())||result.nativeAmount()<0)
            throw new IllegalArgumentException("UNAVAILABLE_NATIVE_HEALTH_RECEIPT");
        // Absorb/cancelled components have no credit. Clamp native underflow so overkill cannot leech/reflect.
        return result.cancelled()?0:Math.min(Math.max(0,result.nativeAmount()),
                Math.max(0,Math.max(0,result.healthBefore())-Math.max(0,result.healthAfter())));
    }
}
