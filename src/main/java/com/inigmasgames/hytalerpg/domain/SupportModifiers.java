package com.inigmasgames.hytalerpg.domain;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/** Typed component operators. Never folds ongoing commitment into a one-time resource cost. */
public record SupportModifiers(boolean selflessness,boolean conservation,boolean resonance,boolean overflow,
                               boolean triage,boolean sharedAegis,boolean reflectiveWard) {
    public static final SupportModifiers NONE=new SupportModifiers(false,false,false,false,false,false,false);
    public static SupportModifiers from(List<PassiveId> order){
        Set<String> ids=order.stream().map(PassiveId::value).collect(Collectors.toSet());
        return new SupportModifiers(ids.contains("selflessness"),ids.contains("conservation"),ids.contains("resonance"),ids.contains("overflow"),
                ids.contains("triage"),ids.contains("shared_aegis"),ids.contains("reflective_ward"));
    }
    public double commitmentFactor(){return (conservation?.8:1)*(resonance?1.15:1);}
    public double radiusFactor(){return resonance?1.4:1;}
    public double effectFactor(){return conservation?.9:1;}
    public double beneficialFactor(){return effectFactor()*(selflessness?1.35:1);}
    public double barrierFactor(){return reflectiveWard?.8:1;}
    public double healingIncreased(double current,double maximum){
        if(!Double.isFinite(current)||!Double.isFinite(maximum)||current<0||maximum<=0||current>maximum)
            throw new IllegalArgumentException("INVALID_HEAL_TARGET_HEALTH");
        return triage&&current<maximum*.35?.35:0;
    }
}
