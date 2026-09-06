package com.inigmasgames.hytalerpg.execution.projectile;

import com.inigmasgames.hytalerpg.execution.SkillExecutionContext;
import com.inigmasgames.hytalerpg.execution.SkillExecutionPort;
import com.inigmasgames.hytalerpg.execution.SkillExecutionResult;
import com.inigmasgames.hytalerpg.execution.SkillFamilyExecutor;
import com.inigmasgames.hytalerpg.execution.Stage04SkillProfile;

/** Single family executor; individual projectile skills remain data-only profiles. */
public final class ProjectileFamilyExecutor implements SkillFamilyExecutor {
    @Override public Stage04SkillProfile.Family family() { return Stage04SkillProfile.Family.PROJECTILE; }
    @Override public SkillExecutionResult execute(SkillExecutionContext context, SkillExecutionPort port) {
        return port.executeProjectile(context);
    }
}
