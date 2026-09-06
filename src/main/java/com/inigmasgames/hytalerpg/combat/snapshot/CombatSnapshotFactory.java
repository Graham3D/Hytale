package com.inigmasgames.hytalerpg.combat.snapshot;

import com.inigmasgames.hytalerpg.combat.attribute.DerivedStats;
import com.inigmasgames.hytalerpg.combat.damage.ModifierBuckets;
import com.inigmasgames.hytalerpg.combat.power.BasePowerResolver;
import com.inigmasgames.hytalerpg.combat.resource.ResourceCost;
import com.inigmasgames.hytalerpg.domain.CompiledSkillPlan;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** The one commit-time snapshot constructor shared by all future SkillInstance executors. */
public final class CombatSnapshotFactory {
    public CombatSnapshot capture(String rootCastId, String skillInstanceId, UUID actor,
                                  DerivedStats attributes, BasePowerResolver.Resolution power,
                                  CompiledSkillPlan plan, double skillCoefficient,
                                  ModifierBuckets authoredModifiers, ResourceCost evaluatedCost,
                                  double evaluatedCooldownSeconds, Map<String, Double> statusModifiers) {
        List<Double> increased = new ArrayList<>(authoredModifiers.increased());
        if (plan != null && plan.kernelModifiers().scalablePayloadIncreased() > 0.0)
            increased.add(plan.kernelModifiers().scalablePayloadIncreased());
        ModifierBuckets compiledModifiers = new ModifierBuckets(increased, authoredModifiers.reduced(),
                authoredModifiers.more(), authoredModifiers.less());
        return new CombatSnapshot(rootCastId, skillInstanceId, actor, attributes.rawAttributes(),
                attributes.effectiveAttributes(), attributes, power.itemId(), power.weaponClass(), power.source(),
                power.basePower(), plan == null ? "" : plan.planHash(), skillCoefficient,
                attributes.criticalChance(), attributes.criticalMultiplier(), compiledModifiers,
                evaluatedCost, evaluatedCooldownSeconds, statusModifiers);
    }
}
