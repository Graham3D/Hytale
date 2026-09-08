package com.inigmasgames.hytalerpg.domain;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/** Typed component operators. Never folds ongoing commitment into a one-time resource cost. */
public record SupportModifiers(boolean selflessness,boolean conservation,boolean resonance,boolean overflow) {
    public static final SupportModifiers NONE=new SupportModifiers(false,false,false,false);
    public static SupportModifiers from(List<PassiveId> order){
        Set<String> ids=order.stream().map(PassiveId::value).collect(Collectors.toSet());
        return new SupportModifiers(ids.contains("selflessness"),ids.contains("conservation"),ids.contains("resonance"),ids.contains("overflow"));
    }
    public double commitmentFactor(){return (conservation?.8:1)*(resonance?1.15:1);}
    public double radiusFactor(){return resonance?1.4:1;}
    public double effectFactor(){return conservation?.9:1;}
    public double beneficialFactor(){return effectFactor()*(selflessness?1.35:1);}
}
