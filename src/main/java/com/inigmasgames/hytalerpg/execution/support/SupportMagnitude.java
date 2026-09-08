package com.inigmasgames.hytalerpg.execution.support;

import com.inigmasgames.hytalerpg.execution.SkillExecutionContext;
import com.inigmasgames.hytalerpg.combat.healing.HealingCalculationService;

public final class SupportMagnitude {
    private SupportMagnitude(){}
    public static double shield(SkillExecutionContext context,double mastery){
        return new HealingCalculationService().direct(20,context.profile().support().coefficient(),
                context.snapshot().derivedStats().healingMultiplier(),mastery,0)*context.snapshot().modifiers().factor();
    }
}
