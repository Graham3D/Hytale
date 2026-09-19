package com.inigmasgames.persistentnpcs.conversation;

import java.util.List;

public record ConversationGrounding(
        String requestedOrDesiredThing,
        ContentValidationStatus contentValidation,
        String invalidatedIntent,
        String contextConstraint,
        String playerClaim,
        List<String> availableRelevantItems) {

    public ConversationGrounding {
        requestedOrDesiredThing = text(requestedOrDesiredThing);
        contentValidation = contentValidation == null
                ? ContentValidationStatus.UNKNOWN : contentValidation;
        invalidatedIntent = text(invalidatedIntent);
        contextConstraint = text(contextConstraint);
        playerClaim = text(playerClaim);
        availableRelevantItems = availableRelevantItems == null
                ? List.of() : List.copyOf(availableRelevantItems);
    }

    public static ConversationGrounding none() {
        return new ConversationGrounding("", ContentValidationStatus.UNKNOWN,
                "", "No new content constraint for this turn.", "", List.of());
    }

    private static String text(String value) {
        return value == null ? "" : value.strip();
    }
}
