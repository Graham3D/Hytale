package com.inigmasgames.persistentnpcs.monster;

public record MonsterReasoningContext(
        ReasoningTrigger trigger,
        double healthRatio,
        int nearbyAllies,
        int nearbyEnemies,
        boolean playerAttemptedConversation,
        boolean playerSparedNpc,
        boolean resolvableCampExists,
        boolean validatedQuestOpportunity,
        boolean playerAttackedDuringTruce) {
}
