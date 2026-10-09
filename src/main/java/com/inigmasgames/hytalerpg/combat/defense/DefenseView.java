package com.inigmasgames.hytalerpg.combat.defense;

/** Shared Master Affix v1.0 section 7 D01 rating/protection bridge.
 * Values are fractions, never percentage points. This view does not apply damage. */
public record DefenseView(double k, double armorRating, double shieldRating, double otherRating,
                          double totalRating, double effectiveRating,
                          double uncappedProtection, double managedProtection) {
    public static final double MANAGED_PROTECTION_CAP = .60;

    public record Contributions(double shieldRating, double otherRating,
                                double globalDefenseIncreased, double winningDefenseBreakFraction) {
        public static final Contributions NONE = new Contributions(0, 0, 0, 0);
        public Contributions {
            nonnegative(shieldRating, "shieldRating");
            nonnegative(otherRating, "otherRating");
            finite(globalDefenseIncreased, "globalDefenseIncreased");
            fraction(winningDefenseBreakFraction, "winningDefenseBreakFraction", true);
        }
    }

    public static double scale(int authoritativeCombatLevel) {
        return 100 + 10 * Math.clamp(authoritativeCombatLevel, 1, 99);
    }

    public static double rating(int authoritativeCombatLevel, double protection) {
        fraction(protection, "protection", false);
        double value = scale(authoritativeCombatLevel) * protection / (1 - protection);
        finite(value, "rating");
        return value;
    }

    public static DefenseView managed(int authoritativeCombatLevel, double resolvedArmorProtection,
                                      Contributions contributions) {
        fraction(resolvedArmorProtection, "resolvedArmorProtection", true);
        // Match the existing managed armor cap before converting its baseline into a rating.
        return resolve(authoritativeCombatLevel, Math.min(MANAGED_PROTECTION_CAP, resolvedArmorProtection),
                contributions, MANAGED_PROTECTION_CAP);
    }

    /** A native cause's baseline and cap are supplied by its existing mitigation owner.
     * Explicit immunity is not a finite Defense rating and must bypass this conversion. */
    public static DefenseView resolve(int authoritativeCombatLevel, double armorProtection,
                                      Contributions contributions, double protectionCap) {
        java.util.Objects.requireNonNull(contributions);
        fraction(protectionCap, "protectionCap", true);
        double k = scale(authoritativeCombatLevel);
        double armor = rating(authoritativeCombatLevel, armorProtection);
        double total = Math.max(0, (armor + contributions.shieldRating + contributions.otherRating)
                * (1 + contributions.globalDefenseIncreased));
        finite(total, "totalRating");
        double effective = total * (1 - contributions.winningDefenseBreakFraction);
        // Preserve the input exactly at the zero-contribution boundary, including native rounding ties.
        double protection = contributions.equals(Contributions.NONE) ? armorProtection : effective / (k + effective);
        return new DefenseView(k, armor, contributions.shieldRating, contributions.otherRating,
                total, effective, protection, Math.clamp(protection, 0, protectionCap));
    }

    /** Existing RPG_Gear_Protection_* assets resolve protection to 0.1 percentage point. */
    public int nativeProtectionTenth() { return (int) Math.round(managedProtection * 1000); }

    private static void fraction(double value, String name, boolean inclusiveOne) {
        nonnegative(value, name);
        if (inclusiveOne ? value > 1 : value >= 1) throw new IllegalArgumentException(name);
    }
    private static void nonnegative(double value, String name) {
        finite(value, name);
        if (value < 0) throw new IllegalArgumentException(name);
    }
    private static void finite(double value, String name) {
        if (!Double.isFinite(value)) throw new IllegalArgumentException(name);
    }
}
