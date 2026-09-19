package com.inigmasgames.persistentnpcs.ai;

import java.time.Instant;

public record AiProviderHealth(Status status, String detail, Instant checkedAt) {
    public enum Status { HEALTHY, DEGRADED, UNAVAILABLE, UNKNOWN }

    public AiProviderHealth {
        status = status == null ? Status.UNKNOWN : status;
        detail = detail == null ? "" : detail;
        checkedAt = checkedAt == null ? Instant.now() : checkedAt;
    }

    public static AiProviderHealth healthy(String detail) {
        return new AiProviderHealth(Status.HEALTHY, detail, Instant.now());
    }

    public static AiProviderHealth unavailable(String detail) {
        return new AiProviderHealth(Status.UNAVAILABLE, detail, Instant.now());
    }
}
