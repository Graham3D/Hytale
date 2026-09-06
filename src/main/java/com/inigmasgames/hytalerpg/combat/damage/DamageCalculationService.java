package com.inigmasgames.hytalerpg.combat.damage;

/** Pure pre-mitigation calculation. Hytale is the only authority that mutates Health. */
public final class DamageCalculationService {
    private final SkillScalingService scaling;
    private final CriticalRoller critical;
    public DamageCalculationService(SkillScalingService scaling, CriticalRoller critical) {
        this.scaling = scaling; this.critical = critical;
    }

    public Result calculate(Request request) {
        validate(request);
        double attributeMultiplier = scaling.attributeMultiplier(request.effectiveAttribute());
        double scaledBasePower = request.basePower() * attributeMultiplier;
        double skillRawDamage = scaledBasePower * request.skillCoefficient();
        double modifierFactor = request.modifiers().factor();
        double preCritDamage = skillRawDamage * modifierFactor;
        boolean criticalHit = critical.roll(request.criticalChance(), request.canCrit());
        double preMitigationDamage = preCritDamage * (criticalHit ? request.criticalMultiplier() : 1.0);
        return new Result(request.basePower(), attributeMultiplier, scaledBasePower, skillRawDamage,
                modifierFactor, preCritDamage, criticalHit, preMitigationDamage);
    }
    private static void validate(Request request) {
        if (request.basePower() < 0.0 || request.skillCoefficient() < 0.0 || request.effectiveAttribute() < 0.0
                || request.criticalChance() < 0.0 || request.criticalChance() > 1.0 || request.criticalMultiplier() < 1.0)
            throw new IllegalArgumentException("Damage inputs are outside the kernel contract");
    }
    public record Request(double basePower, double effectiveAttribute, double skillCoefficient,
                          ModifierBuckets modifiers, boolean canCrit, double criticalChance,
                          double criticalMultiplier) {
        public Request { if (modifiers == null) modifiers = ModifierBuckets.NONE; }
        public static Request direct(double basePower, double effectiveAttribute, double coefficient,
                                     ModifierBuckets modifiers, double chance, double multiplier) {
            return new Request(basePower, effectiveAttribute, coefficient, modifiers, true, chance, multiplier);
        }
        public static Request periodic(double basePower, double effectiveAttribute, double coefficient,
                                       ModifierBuckets modifiers, double chance, double multiplier) {
            return new Request(basePower, effectiveAttribute, coefficient, modifiers, false, chance, multiplier);
        }
    }
    public record Result(double basePower, double attributeMultiplier, double scaledBasePower,
                         double skillRawDamage, double modifierFactor, double preCritDamage,
                         boolean critical, double preMitigationDamage) {
        /** The sole numeric narrowing boundary: double kernel output to Hytale's float Damage value. */
        public float toHytaleDamageFloat() { return (float) preMitigationDamage; }
    }
}
