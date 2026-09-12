package com.inigmasgames.hytalerpg.execution;

import com.inigmasgames.hytalerpg.combat.attribute.DerivedStats;
import com.inigmasgames.hytalerpg.domain.CompiledSkillPlan;

/** Keeps Blizzard's paid cooldown and active storm on one authored clock. */
public final class BlizzardCooldownPolicy {
    private BlizzardCooldownPolicy() { }

    public record Terms(double baseSeconds,double durationFactor,double recovery,
                        CompiledSkillPlan.KernelModifiers modifiers) { }

    public static Terms terms(Stage04SkillProfile profile,CompiledSkillPlan plan,DerivedStats attributes) {
        if (profile.skillId().equals("blizzard") && profile.area()!=null) {
            return new Terms(profile.area().lifetimeSeconds(),1,0,CompiledSkillPlan.KernelModifiers.NONE);
        }
        return new Terms(profile.cooldownSeconds(),plan.foundationModifiers().rechargeFactor(),
                attributes.cooldownRecovery(),plan.kernelModifiers());
    }
}
