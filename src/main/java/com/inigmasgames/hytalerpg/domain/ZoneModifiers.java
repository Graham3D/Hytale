package com.inigmasgames.hytalerpg.domain;

import java.util.List;

/** Finite zone ownership is independent of Aura resource/reservation ownership. */
public record ZoneModifiers(boolean mobileDomain) {
    public static ZoneModifiers from(List<PassiveId> order) {
        return new ZoneModifiers(order.stream().anyMatch(id -> id.value().equals("mobile_domain")));
    }
}
