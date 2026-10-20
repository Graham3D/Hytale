package com.inigmasgames.hytalerpg.execution;

public interface SkillFamilyExecutor {
    Stage04SkillProfile.Family family();
    SkillExecutionResult execute(SkillExecutionContext context, SkillExecutionPort port);
}
