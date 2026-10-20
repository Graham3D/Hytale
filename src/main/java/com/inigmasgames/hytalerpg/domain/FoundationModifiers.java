package com.inigmasgames.hytalerpg.domain;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/** Component-scoped numeric modifiers. Cost remains owned by KernelModifiers. */
public record FoundationModifiers(boolean longReach, boolean rapidInvocation,boolean concentration,boolean lingering,boolean secondWind,boolean reversal,boolean momentum) {
    public static FoundationModifiers from(List<PassiveId> order) {
        Set<String> ids=order.stream().map(PassiveId::value).collect(Collectors.toSet());
        return new FoundationModifiers(ids.contains("long_reach"),ids.contains("rapid_invocation"),ids.contains("concentration"),ids.contains("lingering"),ids.contains("second_wind"),ids.contains("reversal"),ids.contains("momentum"));
    }
    public double rangeFactor(){return longReach?1.25:1;}
    public double windup(double authored){return authored==0?0:rapidInvocation?Math.max(.05,authored*.8):authored;}
    public int chargeCapacity(){return secondWind?2:1;}
    public double rechargeFactor(){return secondWind?1.3:1;}
}
