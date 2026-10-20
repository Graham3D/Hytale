package com.inigmasgames.hytalerpg.execution.projectile;

import com.inigmasgames.hytalerpg.combat.status.*;
import com.inigmasgames.hytalerpg.execution.Stage04SkillProfile;
import java.util.UUID;

/** Authored Root policy before native projection. Boss substitution is an explicit encounter opt-in. */
public final class ProjectileControlPolicy {
    private ProjectileControlPolicy() {}
    public static StatusService.Result root(StatusService statuses, Stage04SkillProfile.Projectile payload,
            UUID target, String source, String role, ControlProfile control) {
        if (!payload.statusId().equals("ROOT")) throw new IllegalArgumentException("ROOT payload required");
        if (control.protectedEntity() || control.boss() && !payload.details().allowsBossSlow(role))
            return new StatusService.Result(StatusService.Outcome.REJECTED, RpgStatusType.ROOT, 0, 0,
                    control.protectedEntity() ? "PROTECTED_TARGET" : "BOSS_ROOT_SLOW_REQUIRES_ENCOUNTER_OPT_IN");
        if (control.boss()) {
            statuses.applySlow(target, source, payload.details().bossRootSlow(), payload.statusSeconds());
            return new StatusService.Result(StatusService.Outcome.APPLIED, RpgStatusType.FROZEN_SUBSTITUTE_SLOW,
                    1, payload.statusSeconds(), "AUTHORED_OPTED_IN_ROOT_SLOW");
        }
        return statuses.apply(target, RpgStatusType.ROOT, control, payload.statusSeconds());
    }
}
