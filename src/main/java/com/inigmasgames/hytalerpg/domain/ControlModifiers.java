package com.inigmasgames.hytalerpg.domain;

import java.util.List;

public record ControlModifiers(boolean deepFreeze) {
    public static ControlModifiers from(List<PassiveId> order){return new ControlModifiers(order.stream().anyMatch(p->p.value().equals("deep_freeze")));}
}
