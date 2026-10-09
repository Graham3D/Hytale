package com.inigmasgames.hytalerpg.execution;

import com.inigmasgames.hytalerpg.combat.resource.ResourceType;
import com.inigmasgames.hytalerpg.gear.GearEffectSnapshot;

/** Frozen equipment terms for eligible skill payments. Reservations and drains never call this evaluator. */
public final class GearResourceModifiers {
    private GearResourceModifiers() { }

    /** Shared finite activation quote for execution and UI; base skill values remain authoritative. */
    public static com.inigmasgames.hytalerpg.combat.resource.ResourceCost activation(
            com.inigmasgames.hytalerpg.combat.resource.RpgResourceService owner,
            Stage04SkillProfile profile,com.inigmasgames.hytalerpg.domain.CompiledSkillPlan plan,
            int attunementStacks,GearEffectSnapshot gear,
            com.inigmasgames.hytalerpg.combat.resource.NativeResourcePort resources) {
        ResourceType type=ResourceType.valueOf(profile.resourceType());
        return owner.evaluateActivation(new com.inigmasgames.hytalerpg.combat.resource.ResourceCost(type,profile.resourceCost()),
                plan,attunementStacks,factor(gear,type,false,profile.summon()!=null));
    }

    public static double reduction(GearEffectSnapshot gear, ResourceType type, boolean upkeep, boolean summon) {
        if (gear == null) throw new IllegalArgumentException("Missing committed equipment snapshot");
        double result = switch (type) {
            case MANA -> gear.percent(GearEffectSnapshot.Operator.MANA_COST)
                    + (upkeep ? gear.percent(GearEffectSnapshot.Operator.CHANNEL_COST) : 0)
                    + (summon ? gear.percent(GearEffectSnapshot.Operator.SUMMON_COST) : 0);
            case STAMINA -> gear.percent(GearEffectSnapshot.Operator.STAMINA_COST);
            default -> 0;
        };
        return Math.min(1, Math.max(0, result));
    }

    public static double factor(GearEffectSnapshot gear, ResourceType type, boolean upkeep, boolean summon) {
        return 1 - reduction(gear, type, upkeep, summon);
    }
}
