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
    /** Skill path: use this owner's critical RNG for one committed multi-channel direct hit. */
    public com.inigmasgames.hytalerpg.gear.GearCombatEffects.Hit calculateGear(
            com.inigmasgames.hytalerpg.gear.GearEffectSnapshot equipped,java.util.UUID contributingItem,
            String rootId,double scaledPhysicalPower,double coefficient,boolean attack,boolean spell,
            double skillConvertedFraction,com.inigmasgames.hytalerpg.gear.GearCombatEffects.Channel skillDestination,
            double baselineCritChance,double baselineCritMultiplier,boolean canCrit,
            com.inigmasgames.hytalerpg.execution.math.Vec3 origin){
        return com.inigmasgames.hytalerpg.gear.GearCombatEffects.attack(equipped,contributingItem,rootId,
                scaledPhysicalPower,coefficient,attack,spell,skillConvertedFraction,skillDestination,
                baselineCritChance,baselineCritMultiplier,canCrit,origin,critical);
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
        /** Value of +1 Increased before native mitigation, retaining this contact's existing crit and More/Less. */
        public double increasedUnit(ModifierBuckets buckets,double criticalMultiplier){
            return skillRawDamage*(critical?criticalMultiplier:1)*new ModifierBuckets(java.util.List.of(),java.util.List.of(),buckets.more(),buckets.less()).factor();
        }
        /** The sole numeric narrowing boundary: double kernel output to Hytale's float Damage value. */
        public float toHytaleDamageFloat() { return (float) preMitigationDamage; }
    }
}
