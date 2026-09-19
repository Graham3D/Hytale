package com.inigmasgames.persistentnpcs.authoring;

import java.util.Locale;
import java.util.UUID;

/** Versioned, server-validated envelope carried by every Authoring Studio event. */
public record NpcAuthoringEventEnvelope(
        int schemaVersion,
        UUID sessionId,
        UUID viewerPlayerId,
        UUID npcStableId,
        long pageGeneration,
        NpcAuthoringSession.EditorKind editor,
        long editorGeneration,
        String action) {
    public static final int CURRENT_SCHEMA_VERSION = 1;

    public NpcAuthoringEventEnvelope {
        if (sessionId == null || viewerPlayerId == null || npcStableId == null) {
            throw new IllegalArgumentException("Authoring envelope identity is required.");
        }
        if (pageGeneration <= 0 || editorGeneration <= 0) {
            throw new IllegalArgumentException("Authoring envelope generation is invalid.");
        }
        editor = editor == null ? NpcAuthoringSession.EditorKind.NONE : editor;
        action = action == null ? "" : action.strip().toUpperCase(Locale.ROOT);
        if (action.isBlank()) throw new IllegalArgumentException(
                "Authoring envelope action is required.");
    }

    public static NpcAuthoringEventEnvelope parse(
            Integer schemaVersion, String sessionId, String viewerPlayerId,
            String npcStableId, Long pageGeneration, String editor,
            Long editorGeneration, String action) {
        try {
            return new NpcAuthoringEventEnvelope(
                    schemaVersion == null ? -1 : schemaVersion,
                    UUID.fromString(sessionId == null ? "" : sessionId),
                    UUID.fromString(viewerPlayerId == null ? "" : viewerPlayerId),
                    UUID.fromString(npcStableId == null ? "" : npcStableId),
                    pageGeneration == null ? -1 : pageGeneration,
                    NpcAuthoringSession.EditorKind.valueOf(
                            editor == null ? "NONE" : editor.toUpperCase(Locale.ROOT)),
                    editorGeneration == null ? -1 : editorGeneration,
                    action);
        } catch (RuntimeException invalid) {
            throw new IllegalArgumentException("Malformed NPC Authoring Studio event envelope.",
                    invalid);
        }
    }
}
