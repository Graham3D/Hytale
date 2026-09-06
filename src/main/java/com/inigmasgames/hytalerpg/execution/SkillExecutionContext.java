package com.inigmasgames.hytalerpg.execution;

import com.inigmasgames.hytalerpg.combat.snapshot.CombatSnapshot;
import com.inigmasgames.hytalerpg.domain.CompiledSkillPlan;

/** Immutable context inherited by every effect produced by one committed activation. */
public record SkillExecutionContext(SkillExecutionRequest request, String rootCastId, String skillInstanceId,
                                    Stage04SkillProfile profile, CompiledSkillPlan compiledPlan,
                                    CombatSnapshot snapshot, SkillExecutionPort.Equipment equipment) {
    public SkillExecutionContext {
        if (request == null || rootCastId == null || skillInstanceId == null || profile == null
                || compiledPlan == null || snapshot == null)
            throw new IllegalArgumentException("Committed execution context is incomplete");
    }
}
