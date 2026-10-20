package com.inigmasgames.hytalerpg.domain;

import java.util.Objects;
import java.util.UUID;

/** Stable identity for one persisted gameplay graph edge. */
public record EdgeId(String value) implements Comparable<EdgeId> {
    public EdgeId {
        value = Objects.requireNonNull(value, "value").trim();
        if (value.isBlank()) throw new IllegalArgumentException("Edge ID cannot be blank");
    }
    public static EdgeId create() { return new EdgeId(UUID.randomUUID().toString()); }
    @Override public int compareTo(EdgeId other) { return value.compareTo(other.value); }
}
