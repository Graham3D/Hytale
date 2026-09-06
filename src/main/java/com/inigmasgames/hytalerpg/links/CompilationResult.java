package com.inigmasgames.hytalerpg.links;

import com.inigmasgames.hytalerpg.domain.CompiledSkillPlan;
import com.inigmasgames.hytalerpg.domain.SkillSlot;

import java.util.Map;

public record CompilationResult(boolean success, ValidationCode code, String message,
                                Map<SkillSlot, CompiledSkillPlan> plans) {
    public CompilationResult { plans = Map.copyOf(plans); }
    public static CompilationResult success(Map<SkillSlot, CompiledSkillPlan> plans) {
        return new CompilationResult(true, ValidationCode.ACCEPTED, "Compile: PASS", plans);
    }
    public static CompilationResult failure(ValidationCode code, String message) {
        return new CompilationResult(false, code, message, Map.of());
    }
}
