package com.inigmasgames.persistentnpcs.autonomy;

public record AutonomousNpcIntent(
        String intent,
        String registeredActionId,
        String questType,
        String groundedReason,
        int opportunityScore) {
}
