package com.inigmasgames.hytalerpg.domain;

import java.util.Locale;

public enum SkillSlot {
    SKILL01(0), SKILL02(1), SKILL03(2), SKILL04(3);
    private final int index;
    SkillSlot(int index) { this.index = index; }
    public int index() { return index; }
    public String externalId() { return name().toLowerCase(Locale.ROOT); }
    public static SkillSlot parse(String value) {
        String normalized = value == null ? "" : value.replaceAll("[^A-Za-z0-9]", "").toUpperCase(Locale.ROOT);
        return switch (normalized) {
            case "SKILL1", "SKILL01" -> SKILL01;
            case "SKILL2", "SKILL02" -> SKILL02;
            case "SKILL3", "SKILL03" -> SKILL03;
            case "SKILL4", "SKILL04" -> SKILL04;
            default -> throw new IllegalArgumentException("Unknown Skill slot: " + value + " (expected skill01..skill04)");
        };
    }
}
