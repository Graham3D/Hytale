package com.inigmasgames.hytalerpg.domain;

import java.util.List;
import java.util.Map;
import java.util.Set;

/** Pure compiler output. Stage 01B does not execute this plan. */
public record CompiledSkillPlan(
        int schemaVersion,
        SkillSlot skillSlot,
        SkillId skillId,
        String planHash,
        String finalFamily,
        Set<String> finalTags,
        List<PassiveId> passiveOrder,
        Map<PassiveId, List<LinkNodeId>> graphRoutes,
        List<String> targetingModifiers,
        List<String> geometryModifiers,
        List<String> multiplicity,
        List<String> continuation,
        List<String> resourceCooldownModifiers,
        List<String> powerModifiers,
        KernelModifiers kernelModifiers,
        List<String> triggerHooks,
        String vfxRecipeId,
        String soundRecipeId,
        SafetyBudgets safetyBudgets,
        boolean degraded,
        List<String> degradedReasons) {
    public static final int CURRENT_SCHEMA = 2;
    public CompiledSkillPlan {
        finalTags = Set.copyOf(finalTags);
        passiveOrder = List.copyOf(passiveOrder);
        graphRoutes = Map.copyOf(graphRoutes);
        targetingModifiers = List.copyOf(targetingModifiers);
        geometryModifiers = List.copyOf(geometryModifiers);
        multiplicity = List.copyOf(multiplicity);
        continuation = List.copyOf(continuation);
        resourceCooldownModifiers = List.copyOf(resourceCooldownModifiers);
        powerModifiers = List.copyOf(powerModifiers);
        triggerHooks = List.copyOf(triggerHooks);
        degradedReasons = List.copyOf(degradedReasons);
        if (kernelModifiers == null) kernelModifiers = KernelModifiers.NONE;
    }

    /** Typed subset consumed by the Stage 02 kernel. Descriptive operations remain for later executors. */
    public record KernelModifiers(double scalablePayloadIncreased, double resourceCostMultiplier,
                                  double cooldownRecoveryBonus) {
        public static final KernelModifiers NONE = new KernelModifiers(0.0, 1.0, 0.0);
        public KernelModifiers {
            if (!Double.isFinite(scalablePayloadIncreased) || scalablePayloadIncreased < 0.0)
                throw new IllegalArgumentException("scalablePayloadIncreased must be finite and non-negative");
            if (!Double.isFinite(resourceCostMultiplier) || resourceCostMultiplier < 0.0)
                throw new IllegalArgumentException("resourceCostMultiplier must be finite and non-negative");
            if (!Double.isFinite(cooldownRecoveryBonus) || cooldownRecoveryBonus < 0.0)
                throw new IllegalArgumentException("cooldownRecoveryBonus must be finite and non-negative");
        }
    }

    public record SafetyBudgets(int maxGeneration, int maxSpawnedEffects, int maxTriggeredSecondaries,
                                int maxLiveSummons, int maxLiveProjectiles, int maxPersistentFields,
                                int maxActiveAuras, int passiveSpawnCost) {
        public static SafetyBudgets baseline(int passiveSpawnCost) {
            return new SafetyBudgets(3, 48, 16, 8, 24, 8, 4, passiveSpawnCost);
        }
    }
}
