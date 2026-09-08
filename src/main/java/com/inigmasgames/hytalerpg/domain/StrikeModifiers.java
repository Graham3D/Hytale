package com.inigmasgames.hytalerpg.domain;

import java.util.List;

public record StrikeModifiers(boolean multistrike,boolean ruthless,boolean cleavingEdge,boolean phantomReach,boolean shockwave) {
    public static StrikeModifiers from(List<PassiveId> order){
        return new StrikeModifiers(order.stream().anyMatch(p->p.value().equals("multistrike")),
                order.stream().anyMatch(p->p.value().equals("ruthless")),order.stream().anyMatch(p->p.value().equals("cleaving_edge")),
                order.stream().anyMatch(p->p.value().equals("phantom_reach")),order.stream().anyMatch(p->p.value().equals("shockwave")));
    }
    public java.util.Set<String> introducedTags(){return shockwave?java.util.Set.of("COMPONENT_SHOCKWAVE","BURST","AREA","DAMAGE","HAS_RADIUS","HAS_AREA_GEOMETRY"):java.util.Set.of();}
}
