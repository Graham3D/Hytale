package com.inigmasgames.hytalerpg.enemies;

import com.inigmasgames.hytalerpg.combat.resource.RollingReceiptBudget;
import java.util.*;
import java.util.function.BooleanSupplier;
import java.util.function.DoubleUnaryOperator;

/** ME-026 live per-component Health-loss reaction; native damage delivery remains the owner. */
public final class EnemyReflectiveEffects {
    public record Result(RollingReceiptBudget.Gate gate,double rawRequested,double rawSent){}

    /** The caller supplies a certified original-direct, post-Apply receipt and a later-Gather writer.
     * The writer returns raw power submitted, regardless of the child's mitigated Health loss.
     */
    public synchronized Result offer(EnemyDescriptor defender,UUID attacker,String originalReceipt,
            double actualHealthLoss,double defenderHealthAfter,double attackerMaxHealth,double originDistance,
            boolean originalDirect,long now,BooleanSupplier current,DoubleUnaryOperator submitRaw){
        Objects.requireNonNull(defender);Objects.requireNonNull(attacker);Objects.requireNonNull(current);Objects.requireNonNull(submitRaw);
        if(originalReceipt==null||originalReceipt.isBlank()||originalReceipt.length()>512||now<0
                ||!Double.isFinite(actualHealthLoss)||actualHealthLoss<0
                ||!Double.isFinite(defenderHealthAfter)||defenderHealthAfter<0
                ||!Double.isFinite(attackerMaxHealth)||attackerMaxHealth<=0
                ||!Double.isFinite(originDistance)||originDistance<0)
            throw new IllegalArgumentException("ENEMY_REFLECTIVE_RECEIPT");
        var affix=defender.own(EnemyAffixRegistry.Operator.REFLECTIVE).orElse(null);
        if(affix==null||!originalDirect||actualHealthLoss<=0||!current.getAsBoolean())
            return new Result(RollingReceiptBudget.Gate.EMPTY,0,0);
        double request=Math.min(actualHealthLoss*affix.value("healthDamageReflectFraction"),
                attackerMaxHealth*affix.value("maxVictimHealthFractionPerHit"));
        if(request<=0)return new Result(RollingReceiptBudget.Gate.EMPTY,0,0);
        double sent=current.getAsBoolean()?submitRaw.applyAsDouble(request):0;
        return new Result(sent>0?RollingReceiptBudget.Gate.APPLIED:RollingReceiptBudget.Gate.EMPTY,request,sent);
    }

    public synchronized List<RollingReceiptBudget.Saved<UUID>> snapshot(long now){
        return List.of();
    }
    public synchronized void restore(List<RollingReceiptBudget.Saved<UUID>> saved,long now){
        Objects.requireNonNull(saved);
    }
}
