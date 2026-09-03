package com.inigmasgames.persistentnpcs.director;

import java.util.Map;

/** A grounded proposal only; execution must pass the normal action/task registry. */
public record WorldStoryProposal(
        String eventType,
        String groundingEventId,
        String actionId,
        Map<String, String> arguments,
        String conciseReason) {
}
