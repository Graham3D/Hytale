package com.inigmasgames.hytalerpg.domain;

import java.util.Locale;

/** The complete, permanent gameplay node surface. Canvas coordinates are deliberately absent. */
public enum LinkNodeId {
    SKILL01(NodeKind.SKILL), SKILL02(NodeKind.SKILL), SKILL03(NodeKind.SKILL), SKILL04(NodeKind.SKILL),
    PASSIVE01(NodeKind.PASSIVE), PASSIVE02(NodeKind.PASSIVE), PASSIVE03(NodeKind.PASSIVE),
    PASSIVE04(NodeKind.PASSIVE), PASSIVE05(NodeKind.PASSIVE), PASSIVE06(NodeKind.PASSIVE),
    JOINT01(NodeKind.JOINT), JOINT02(NodeKind.JOINT);

    public enum NodeKind { SKILL, PASSIVE, JOINT }
    private final NodeKind kind;
    LinkNodeId(NodeKind kind) { this.kind = kind; }
    public NodeKind kind() { return kind; }
    public String externalId() { return name().toLowerCase(Locale.ROOT); }
    public SkillSlot skillSlot() { return SkillSlot.valueOf(name()); }
    public PassiveSlot passiveSlot() { return PassiveSlot.valueOf(name()); }
    public static LinkNodeId parse(String value) {
        String normalized = value == null ? "" : value.replaceAll("[^A-Za-z0-9]", "").toUpperCase(Locale.ROOT);
        try { return valueOf(normalized); }
        catch (IllegalArgumentException error) {
            if (normalized.matches("SKILL[1-4]")) normalized = "SKILL0" + normalized.substring(5);
            else if (normalized.matches("PASSIVE[1-6]")) normalized = "PASSIVE0" + normalized.substring(7);
            else if (normalized.matches("JOINT[1-2]")) normalized = "JOINT0" + normalized.substring(5);
            try { return valueOf(normalized); }
            catch (IllegalArgumentException ignored) {
                throw new IllegalArgumentException("Unknown Link node: " + value + " (expected skill01..04, passive01..06, or joint01..02)");
            }
        }
    }
}
