package com.inigmasgames.persistentnpcs.cognition;

import com.google.gson.JsonObject;
import java.util.UUID;

/** One model-selected action, bound to the actor and authoritative response. */
public record NpcDecisionAction(
        String actionId,
        UUID actorStableId,
        UUID targetStableId,
        JsonObject parameters) {

    public NpcDecisionAction {
        actionId = actionId == null ? "" : actionId.strip().toUpperCase(
                java.util.Locale.ROOT);
        parameters = parameters == null ? new JsonObject() : parameters.deepCopy();
    }
}
