package com.inigmasgames.hytalerpg.progress;

import com.inigmasgames.hytalerpg.domain.CompiledSkillPlan;
import com.inigmasgames.hytalerpg.domain.SkillSlot;
import com.inigmasgames.hytalerpg.links.ValidationCode;

import java.util.Map;

public record MutationResult(boolean success, ValidationCode code, String message, String traceReference,
                             long revision, Map<SkillSlot, CompiledSkillPlan> compiledPlans) {
    public MutationResult { compiledPlans = Map.copyOf(compiledPlans); }
    public static MutationResult failure(ValidationCode code, String message, String trace, long revision) {
        return new MutationResult(false, code, message, trace, revision, Map.of());
    }
}
