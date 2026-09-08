package com.inigmasgames.hytalerpg.domain;

import java.util.List;

/** Finite zone ownership is independent of Aura resource/reservation ownership. */
public record ZoneModifiers(boolean mobileDomain,boolean cascade,boolean aftermath) {
    public ZoneModifiers(boolean mobileDomain){this(mobileDomain,false,false);}
    public ZoneModifiers(boolean mobileDomain,boolean cascade){this(mobileDomain,cascade,false);}
    public static ZoneModifiers from(List<PassiveId> order) {
        return new ZoneModifiers(order.stream().anyMatch(id -> id.value().equals("mobile_domain")),order.stream().anyMatch(id -> id.value().equals("cascade")),order.stream().anyMatch(id -> id.value().equals("aftermath")));
    }
}
