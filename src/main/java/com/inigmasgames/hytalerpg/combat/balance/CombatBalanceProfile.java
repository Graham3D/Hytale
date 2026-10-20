package com.inigmasgames.hytalerpg.combat.balance;

import com.google.gson.Gson;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/** Single versioned source for Stage 02 coefficients and breakpoints. */
public final class CombatBalanceProfile {
    public int schemaVersion;
    public String profileId;
    public AttributeCurve attributeCurve;
    public double startingRawAttribute;
    public double startingResourceMaximum;
    public double healthPerEffectiveStrength;
    public double staminaPerEffectiveDexterity;
    public double manaPerEffectiveIntelligence;
    public double primaryScalingPerEffectivePoint;
    public double potencyIncreased;
    public double wisdomCooldownNumerator;
    public double wisdomCooldownDenominator;
    public double wisdomLearnPerPoint;
    public double wisdomLearnCap;
    public double luckCritNumerator;
    public double luckCritDenominator;
    public double luckUpgradeNumerator;
    public double luckUpgradeDenominator;
    public double luckMagicFindPerPoint;
    public double baseCriticalChance;
    public double criticalChanceCap;
    public double baseCriticalMultiplier;
    public double cooldownRecoveryCap;
    public double minimumCooldownSeconds;
    public double passiveRegenerationPerSecond;
    public double normalHostileHitRecovery;
    public double chargedHostileHitRecovery;
    public double homeSeconds;
    public double outOfHostileCombatSeconds;
    public double chillMovementPenaltyPerStack;
    public int chillMaximumStacks;
    public double chillDurationSeconds;
    public double frozenDurationSeconds;
    public double frozenImmunitySeconds;
    public double protectedFrozenSlow;
    public double burnDurationSeconds;
    public double poisonDurationSeconds;

    public static CombatBalanceProfile loadCanonical() {
        try (var stream = CombatBalanceProfile.class.getResourceAsStream("/rpg/balance/combat-kernel-v1.json")) {
            if (stream == null) throw new IllegalStateException("Missing combat-kernel-v1.json");
            CombatBalanceProfile profile = new Gson().fromJson(new InputStreamReader(stream, StandardCharsets.UTF_8), CombatBalanceProfile.class);
            profile.validate();
            return profile;
        } catch (RuntimeException error) { throw error; }
        catch (Exception error) { throw new IllegalStateException("Cannot load combat balance profile", error); }
    }

    public void validate() {
        if (schemaVersion != 1) throw new IllegalStateException("Unsupported combat balance schema: " + schemaVersion);
        if (profileId == null || profileId.isBlank()) throw new IllegalStateException("Missing combat balance profileId");
        if (attributeCurve == null || attributeCurve.breakpoints == null || attributeCurve.slopes == null
                || attributeCurve.breakpoints.length != 4 || attributeCurve.slopes.length != 5)
            throw new IllegalStateException("Attribute curve must define four breakpoints and five slopes");
        for (int index = 1; index < attributeCurve.breakpoints.length; index++)
            if (attributeCurve.breakpoints[index] <= attributeCurve.breakpoints[index - 1])
                throw new IllegalStateException("Attribute breakpoints must be strictly increasing");
        if (Arrays.stream(attributeCurve.slopes).anyMatch(value -> !Double.isFinite(value) || value < 0.0))
            throw new IllegalStateException("Attribute slopes must be finite and non-negative");
        if (!Double.isFinite(potencyIncreased) || potencyIncreased < 0.0)
            throw new IllegalStateException("Potency increased magnitude must be finite and non-negative");
    }

    public static final class AttributeCurve {
        public double[] breakpoints;
        public double[] slopes;
    }
}
