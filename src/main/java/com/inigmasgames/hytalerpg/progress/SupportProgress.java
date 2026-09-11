package com.inigmasgames.hytalerpg.progress;

import com.inigmasgames.hytalerpg.combat.barrier.ManaguardLedger;
import java.util.LinkedHashMap;
import java.util.Map;

/** Saved deficit/locks/order, deliberately no saved active Aura or native resource values. */
public record SupportProgress(long revision,long lastAuraEpoch,ManaguardLedger managuard,Map<String,Double> toggleLocks) {
    public static final SupportProgress INITIAL=new SupportProgress(0,0,ManaguardLedger.INITIAL,Map.of());
    public SupportProgress {
        if(revision<0||lastAuraEpoch<0||managuard==null||toggleLocks==null||toggleLocks.size()>89)
            throw new IllegalArgumentException("Invalid durable support state");
        for(var entry:toggleLocks.entrySet())
            if(entry.getKey()==null||entry.getKey().isBlank()||entry.getValue()==null
                    ||!Double.isFinite(entry.getValue())||entry.getValue()<0||entry.getValue()>120)
                throw new IllegalArgumentException("Invalid durable Aura toggle lock");
        toggleLocks=Map.copyOf(toggleLocks);
    }
    public SupportProgress guard(ManaguardLedger value){return new SupportProgress(revision,lastAuraEpoch,value,toggleLocks);}
    public SupportProgress toggle(String skill,double seconds,boolean activation){
        if(toggleLocks.getOrDefault(skill,0.0)>1e-9)throw new IllegalStateException("Aura toggle locked");
        Map<String,Double> locks=new LinkedHashMap<>(toggleLocks);locks.put(skill,seconds);
        return new SupportProgress(revision,activation?Math.addExact(lastAuraEpoch,1):lastAuraEpoch,managuard,locks);
    }
    public SupportProgress observedTime(double seconds,double eligibleRechargeSeconds){
        if(!Double.isFinite(seconds)||seconds<0||!Double.isFinite(eligibleRechargeSeconds)
                ||eligibleRechargeSeconds<0||eligibleRechargeSeconds>seconds)throw new IllegalArgumentException("Invalid observed support time");
        Map<String,Double> locks=new LinkedHashMap<>();
        toggleLocks.forEach((key,value)->{if(value>seconds)locks.put(key,value-seconds);});
        return new SupportProgress(revision,lastAuraEpoch,managuard.recharge(eligibleRechargeSeconds),locks);
    }
    public SupportProgress nextRevision(){return new SupportProgress(Math.addExact(revision,1),lastAuraEpoch,managuard,toggleLocks);}
}
