package com.inigmasgames.canvasui.api.editor;

import com.inigmasgames.canvasui.api.Canvas;
import com.inigmasgames.canvasui.api.CanvasSnapshot;

import java.util.List;

/**
 * Consumer-owned authority presented by CanvasUI's passive cursor-HUD editor.
 * CanvasUI owns input and rendering; the consuming mod owns every durable mutation.
 */
public interface CursorCanvasEditor {
    enum LibraryKind { SKILL, PASSIVE }

    record LibraryEntry(String id, String name, String category, LibraryKind kind) {
        public LibraryEntry {
            if (id == null || id.isBlank()) throw new IllegalArgumentException("library id is blank");
            name = name == null ? id : name;
            category = category == null ? "" : category;
            if (kind == null) throw new IllegalArgumentException("library kind is required");
        }
    }

    record Result(boolean accepted, String message, CanvasSnapshot authoritativeSnapshot) {
        public Result {
            message = message == null ? "" : message;
            if (authoritativeSnapshot == null)
                throw new IllegalArgumentException("authoritative snapshot is required");
        }
    }

    String editorId();
    String title();
    Canvas canvas();
    List<LibraryEntry> library(LibraryKind kind);

    /** Assigns one catalog entry to a compatible content node. */
    Result assign(String entryId, String nodeId, CanvasSnapshot presentationSnapshot);

    /** Commits a candidate topology or accepts a presentation-only node move. */
    Result commit(CanvasSnapshot candidateSnapshot);

    /** Called exactly once when CanvasUI releases input/display ownership. */
    default void closed(String reason) { }
}
