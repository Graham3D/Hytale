package com.inigmasgames.persistentnpcs.autonomy;

import com.inigmasgames.persistentnpcs.event.NpcFrameworkEvent;
import java.util.List;

public record AutonomousOpportunity(
        NpcFrameworkEvent event,
        String unresolvedNeed,
        boolean unfinishedBusiness,
        boolean usefulGossip,
        boolean inDanger,
        boolean scheduleProblem,
        int relationshipMotivation,
        List<String> eligibleActionIds,
        List<String> availableQuestTypes) {

    public int score() {
        int score = 0;
        if (unresolvedNeed != null && !unresolvedNeed.isBlank()) score += 3;
        if (unfinishedBusiness) score += 3;
        if (usefulGossip) score += 2;
        if (inDanger) score += 5;
        if (scheduleProblem) score += 2;
        score += Math.max(0, Math.min(4, relationshipMotivation));
        return score;
    }
}
