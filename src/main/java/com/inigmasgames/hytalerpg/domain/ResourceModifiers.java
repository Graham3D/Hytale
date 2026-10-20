package com.inigmasgames.hytalerpg.domain;

import java.util.List;

/** Named resource exceptions, never changes to authored resource types or upkeep. */
public record ResourceModifiers(boolean lifeblood, boolean attunement, boolean leeching) {
    public static ResourceModifiers from(List<PassiveId> order) {
        return new ResourceModifiers(order.stream().anyMatch(p->p.value().equals("lifeblood")),
                order.stream().anyMatch(p->p.value().equals("attunement")),order.stream().anyMatch(p->p.value().equals("leeching")));
    }
}
