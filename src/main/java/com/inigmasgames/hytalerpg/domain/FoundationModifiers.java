package com.inigmasgames.hytalerpg.domain;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/** Component-scoped numeric modifiers. Cost remains owned by KernelModifiers. */
public record FoundationModifiers(boolean longReach, boolean rapidInvocation,boolean concentration,boolean lingering) {
    public static FoundationModifiers from(List<PassiveId> order) {
        Set<String> ids=order.stream().map(PassiveId::value).collect(Collectors.toSet());
        return new FoundationModifiers(ids.contains("long_reach"),ids.contains("rapid_invocation"),ids.contains("concentration"),ids.contains("lingering"));
    }
    public double rangeFactor(){return longReach?1.25:1;}
    public double windup(double authored){return authored==0?0:rapidInvocation?Math.max(.05,authored*.8):authored;}
}
