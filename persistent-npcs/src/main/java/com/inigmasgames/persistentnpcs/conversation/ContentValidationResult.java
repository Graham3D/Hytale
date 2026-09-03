package com.inigmasgames.persistentnpcs.conversation;

import java.util.List;

public record ContentValidationResult(
        String requestedThing,
        ContentValidationStatus status,
        List<String> relevantItems,
        String reason) {

    public ContentValidationResult {
        requestedThing = requestedThing == null ? "" : requestedThing.strip();
        status = status == null ? ContentValidationStatus.UNKNOWN : status;
        relevantItems = relevantItems == null ? List.of() : List.copyOf(relevantItems);
        reason = reason == null ? "validation unavailable" : reason.strip();
    }

    public static ContentValidationResult unknown(String requestedThing, String reason) {
        return new ContentValidationResult(requestedThing, ContentValidationStatus.UNKNOWN,
                List.of(), reason);
    }
}
