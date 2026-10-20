package com.inigmasgames.hytalerpg.progress;

import com.inigmasgames.hytalerpg.domain.PassiveId;
import com.inigmasgames.hytalerpg.domain.SkillId;

public interface EntitlementPolicy {
    EntitlementVerdict skill(RpgPlayerState state, SkillId id);
    EntitlementVerdict passive(RpgPlayerState state, PassiveId id);
    boolean developmentMode();
    record EntitlementVerdict(boolean allowed, String reason) {
        public static EntitlementVerdict allowed(String reason) { return new EntitlementVerdict(true, reason); }
        public static EntitlementVerdict denied(String reason) { return new EntitlementVerdict(false, reason); }
    }
}
