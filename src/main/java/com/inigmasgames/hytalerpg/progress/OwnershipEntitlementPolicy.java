package com.inigmasgames.hytalerpg.progress;

import com.inigmasgames.hytalerpg.domain.PassiveId;
import com.inigmasgames.hytalerpg.domain.SkillId;

/** Production ownership gate with an explicitly labelled engineering override. */
public final class OwnershipEntitlementPolicy implements EntitlementPolicy {
    private final boolean developmentMode;
    public OwnershipEntitlementPolicy(boolean developmentMode) { this.developmentMode = developmentMode; }

    @Override public EntitlementVerdict skill(RpgPlayerState state, SkillId id) {
        if (developmentMode) return EntitlementVerdict.allowed("DEVELOPMENT_ENTITLEMENT_MODE");
        return state.learnedSkills.contains(id.value())
                ? EntitlementVerdict.allowed("LEARNED_SKILL")
                : EntitlementVerdict.denied("Skill is not learned: " + id.value());
    }

    @Override public EntitlementVerdict passive(RpgPlayerState state, PassiveId id) {
        if (developmentMode) return EntitlementVerdict.allowed("DEVELOPMENT_ENTITLEMENT_MODE");
        return state.ownedPassives.getOrDefault(id.value(), 0) > equippedCopies(state, id)
                ? EntitlementVerdict.allowed("OWNED_PASSIVE_COPY")
                : EntitlementVerdict.denied("No unequipped owned copy of Passive: " + id.value());
    }

    private static long equippedCopies(RpgPlayerState state, PassiveId id) {
        return java.util.Arrays.stream(state.equippedPassives).filter(id.value()::equals).count();
    }

    @Override public boolean developmentMode() { return developmentMode; }
}
