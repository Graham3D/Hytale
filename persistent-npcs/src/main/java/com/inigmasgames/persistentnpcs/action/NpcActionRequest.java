package com.inigmasgames.persistentnpcs.action;

import com.google.gson.JsonObject;
import java.util.UUID;

public record NpcActionRequest(String id, JsonObject parameters, String toolCallId,
        UUID responseId, UUID actorStableId, UUID targetStableId) {

    public NpcActionRequest(String id, JsonObject parameters, String toolCallId) {
        this(id, parameters, toolCallId, null, null, null);
    }

    public NpcActionRequest normalized() {
        return new NpcActionRequest(
                id == null ? "" : id.strip().replaceAll("[\\s-]+", "_")
                        .replaceAll("_+", "_")
                        .toUpperCase(java.util.Locale.ROOT),
                parameters == null ? new JsonObject() : parameters,
                toolCallId, responseId, actorStableId, targetStableId);
    }
}
