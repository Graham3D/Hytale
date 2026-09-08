package com.inigmasgames.hytalerpg.domain;

import java.util.List;

public record StrikeModifiers(boolean multistrike,boolean ruthless) {
    public static StrikeModifiers from(List<PassiveId> order){
        return new StrikeModifiers(order.stream().anyMatch(p->p.value().equals("multistrike")),
                order.stream().anyMatch(p->p.value().equals("ruthless")));
    }
}
