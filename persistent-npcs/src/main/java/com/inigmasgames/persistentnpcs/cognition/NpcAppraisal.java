package com.inigmasgames.persistentnpcs.cognition;

/** Concise derived conclusion, deliberately not free-form hidden reasoning. */
public record NpcAppraisal(
        boolean significant,
        String situationSummary,
        String familiarity,
        String trust,
        String perceivedRisk,
        NpcEmotion emotionalState,
        String immediateGoal,
        SocialIntent socialIntent,
        String uncertainty,
        String requestedAction,
        boolean actionAuthorized,
        String authorizationReason) {

    public String compact() {
        if (!significant) {
            return "No special appraisal required; respond naturally.";
        }
        return "situation=" + situationSummary + "; familiarity=" + familiarity
                + "; trust=" + trust + "; risk=" + perceivedRisk
                + "; emotion=" + emotionalState + "; immediateGoal=" + immediateGoal
                + "; socialIntent=" + socialIntent + "; uncertainty=" + uncertainty
                + "; requestedAction=" + none(requestedAction)
                + "; actionAuthorized=" + actionAuthorized
                + "; authorizationReason=" + authorizationReason;
    }

    private static String none(String value) {
        return value == null || value.isBlank() ? "NONE" : value;
    }
}
