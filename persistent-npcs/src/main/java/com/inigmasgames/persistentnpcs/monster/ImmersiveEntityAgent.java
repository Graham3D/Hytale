package com.inigmasgames.persistentnpcs.monster;

import java.time.Instant;
import java.util.UUID;

public record ImmersiveEntityAgent(
        UUID stableId,
        UUID currentEntityId,
        String archetype,
        String personalitySeed,
        AgentPersistence persistence,
        HighLevelIntent currentIntent,
        boolean nativeHostile,
        boolean nativeCombatSuspended,
        Instant lastReasonedAt,
        String promotionReason) {

    public ImmersiveEntityAgent normalized() {
        return new ImmersiveEntityAgent(stableId == null ? UUID.randomUUID() : stableId,
                currentEntityId, archetype == null ? "UNKNOWN_MONSTER" : archetype.strip(),
                personalitySeed == null ? "wary" : personalitySeed.strip(),
                persistence == null ? AgentPersistence.EPHEMERAL : persistence,
                currentIntent == null ? HighLevelIntent.CONTINUE_NATIVE_BEHAVIOR : currentIntent,
                nativeHostile, nativeCombatSuspended, lastReasonedAt,
                promotionReason == null ? "" : promotionReason.strip());
    }

    public ImmersiveEntityAgent withIntent(
            HighLevelIntent intent, boolean hostile, boolean combatSuspended, Instant now) {
        return new ImmersiveEntityAgent(stableId, currentEntityId, archetype, personalitySeed,
                persistence, intent, hostile, combatSuspended, now, promotionReason);
    }

    public ImmersiveEntityAgent promote(String reason) {
        return new ImmersiveEntityAgent(stableId, currentEntityId, archetype, personalitySeed,
                AgentPersistence.PERSISTENT, currentIntent, nativeHostile,
                nativeCombatSuspended, lastReasonedAt, reason);
    }
}
