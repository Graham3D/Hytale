package com.inigmasgames.hytalerpg.domain;

/** Authored active-effect concurrency contract. Cooldown readiness remains a separate gate. */
public record ConcurrentInstancePolicy(Mode mode, int maximum) {
    public enum Mode { UNRESTRICTED, MAX_N, SINGLETON, REPLACE_EXISTING, COMMAND_EXISTING }

    public ConcurrentInstancePolicy {
        if (mode == null) throw new IllegalArgumentException("INSTANCE_POLICY_MODE_MISSING");
        if (mode == Mode.MAX_N && maximum < 1) throw new IllegalArgumentException("INSTANCE_POLICY_MAX_INVALID");
        if (mode != Mode.MAX_N && maximum != 0) throw new IllegalArgumentException("INSTANCE_POLICY_UNEXPECTED_MAX");
    }

    public static ConcurrentInstancePolicy unrestricted() { return new ConcurrentInstancePolicy(Mode.UNRESTRICTED, 0); }
    public static ConcurrentInstancePolicy singleton() { return new ConcurrentInstancePolicy(Mode.SINGLETON, 0); }
    public static ConcurrentInstancePolicy max(int maximum) { return new ConcurrentInstancePolicy(Mode.MAX_N, maximum); }

    /** Central typed contract until the broader authored skill-data schema owns this field. */
    public static ConcurrentInstancePolicy forSkill(String skillId) {
        if (skillId == null || skillId.isBlank()) throw new IllegalArgumentException("INSTANCE_POLICY_SKILL_MISSING");
        return switch (skillId) {
            case "bomb_toss" -> new ConcurrentInstancePolicy(Mode.COMMAND_EXISTING, 0);
            case "lightning_coil" -> unrestricted(); // Lightning Spire: overlapping committed instances are intentional.
            default -> unrestricted();
        };
    }
}
