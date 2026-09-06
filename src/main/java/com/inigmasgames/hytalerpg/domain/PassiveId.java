package com.inigmasgames.hytalerpg.domain;

/** Stable content identity for a data-driven RPG passive. */
public record PassiveId(String value) implements Comparable<PassiveId> {
    public PassiveId { value = SkillId.normalize(value, "rpg.passive."); }
    public String canonical() { return "rpg.passive." + value; }
    @Override public int compareTo(PassiveId other) { return value.compareTo(other.value); }
}
