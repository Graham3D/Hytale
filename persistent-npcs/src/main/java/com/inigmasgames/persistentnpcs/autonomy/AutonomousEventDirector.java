package com.inigmasgames.persistentnpcs.autonomy;

import java.util.Locale;
import java.util.Optional;

/** Event scorer and cooldown gate. It never starts a continuous reasoning loop. */
public final class AutonomousEventDirector {
    private final AutonomyGate gate;
    private final int minimumScore;

    public AutonomousEventDirector(AutonomyGate gate, int minimumScore) {
        this.gate = gate;
        this.minimumScore = Math.max(1, minimumScore);
    }

    public Optional<AutonomousNpcIntent> evaluate(AutonomousOpportunity opportunity) {
        if (opportunity == null || opportunity.event() == null
                || opportunity.score() < minimumScore
                || !gate.claim(opportunity.event(), true)) {
            return Optional.empty();
        }
        String action = selectAction(opportunity);
        String quest = selectQuest(opportunity);
        String intent = !quest.isBlank() ? "OFFER_QUEST"
                : opportunity.inDanger() ? "REQUEST_HELP"
                : opportunity.usefulGossip() ? "WARN" : "APPROACH_AND_TALK";
        return Optional.of(new AutonomousNpcIntent(intent, action, quest,
                groundedReason(opportunity), opportunity.score()));
    }

    private static String selectAction(AutonomousOpportunity opportunity) {
        if (opportunity.inDanger() && contains(opportunity, "FLEE")) return "FLEE";
        if (contains(opportunity, "GO_TO")) return "GO_TO";
        if (contains(opportunity, "FOLLOW_PLAYER")) return "FOLLOW_PLAYER";
        return "";
    }

    private static String selectQuest(AutonomousOpportunity opportunity) {
        if (opportunity.unresolvedNeed() == null || opportunity.unresolvedNeed().isBlank()
                || opportunity.availableQuestTypes() == null) {
            return "";
        }
        return opportunity.availableQuestTypes().stream().findFirst().orElse("");
    }

    private static boolean contains(AutonomousOpportunity opportunity, String id) {
        return opportunity.eligibleActionIds() != null && opportunity.eligibleActionIds().stream()
                .map(value -> value.toUpperCase(Locale.ROOT)).anyMatch(id::equals);
    }

    private static String groundedReason(AutonomousOpportunity opportunity) {
        if (opportunity.inDanger()) return "Authoritative event indicates immediate danger";
        if (opportunity.unfinishedBusiness()) return "Persisted unfinished business exists";
        if (opportunity.unresolvedNeed() != null && !opportunity.unresolvedNeed().isBlank()) {
            return "Validated unresolved need: " + opportunity.unresolvedNeed();
        }
        if (opportunity.usefulGossip()) return "NPC possesses sourced gossip";
        return "Scored authoritative opportunity";
    }
}
