package com.inigmasgames.hytalerpg.domain;

import java.util.List;

public record StrikeModifiers(boolean multistrike,boolean ruthless,boolean cleavingEdge,boolean phantomReach) {
    public static StrikeModifiers from(List<PassiveId> order){
        return new StrikeModifiers(order.stream().anyMatch(p->p.value().equals("multistrike")),
                order.stream().anyMatch(p->p.value().equals("ruthless")),order.stream().anyMatch(p->p.value().equals("cleaving_edge")),
                order.stream().anyMatch(p->p.value().equals("phantom_reach")));
    }
}
