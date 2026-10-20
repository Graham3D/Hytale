package com.inigmasgames.hytalerpg.domain;

import java.util.Locale;
import java.util.Objects;

/** Stable content identity for a data-driven RPG skill. */
public record SkillId(String value) implements Comparable<SkillId> {
    public SkillId {
        value = normalize(value, "rpg.skill.");
    }

    public String canonical() { return "rpg.skill." + value; }

    @Override public int compareTo(SkillId other) { return value.compareTo(other.value); }

    static String normalize(String value, String prefix) {
        Objects.requireNonNull(value, "value");
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        if (normalized.startsWith(prefix)) normalized = normalized.substring(prefix.length());
        normalized = normalized.replaceAll("[^a-z0-9]+", "_").replaceAll("^_+|_+$", "");
        if (normalized.isBlank()) throw new IllegalArgumentException("Content ID cannot be blank");
        return normalized;
    }
}
