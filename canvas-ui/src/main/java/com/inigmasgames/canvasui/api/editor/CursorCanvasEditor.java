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

    record LibraryEntry(String id, String name, String category, String iconPath, LibraryKind kind) {
        public LibraryEntry {
            if (id == null || id.isBlank()) throw new IllegalArgumentException("library id is blank");
            name = name == null ? id : name;
            category = category == null ? "" : category;
            iconPath = iconPath == null ? "" : iconPath;
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

    record DetailRow(String label, String value, String semanticKind) {
        public DetailRow {
            label = label == null ? "" : label;
            value = value == null ? "" : value;
            semanticKind = semanticKind == null ? "TEXT" : semanticKind;
        }
    }

    record Inspector(String kind, String id, String name, String descriptor, String description,
                     String iconPath, List<DetailRow> rows, String footer) {
        public Inspector {
            kind = kind == null ? "NONE" : kind;
            id = id == null ? "" : id;
            name = name == null || name.isBlank() ? "Select a Skill or Passive" : name;
            descriptor = descriptor == null ? "" : descriptor;
            description = description == null ? "Select a Skill or Passive to view details." : description;
            iconPath = iconPath == null ? "" : iconPath;
            rows = rows == null ? List.of() : List.copyOf(rows);
            footer = footer == null ? "" : footer;
        }
        public static Inspector neutral() { return new Inspector("NONE", "", "Select a Skill or Passive",
                "", "Select a Skill or Passive to view details.", "", List.of(), ""); }
    }

    String editorId();
    String title();
    Canvas canvas();
    List<LibraryEntry> library(LibraryKind kind);

    /** Assigns one catalog entry to a compatible content node. */
    Result assign(String entryId, String nodeId, CanvasSnapshot presentationSnapshot);

    /** Breaks exactly one persistent graph edge by its stable authority-owned identity. */
    Result breakLink(String linkId, CanvasSnapshot presentationSnapshot);

    /** Clears one occupied content node through the consumer's authority. */
    default Result clearNode(String nodeId, CanvasSnapshot presentationSnapshot) {
        return new Result(false, "This editor does not support clearing nodes", presentationSnapshot);
    }

    /** Clears equipped tree content and topology without touching ownership or progression. */
    default Result resetTree(CanvasSnapshot presentationSnapshot) {
        return new Result(false, "This editor does not support resetting the tree", presentationSnapshot);
    }

    /** Immutable contextual detail projection. Empty arguments request the neutral state. */
    default Inspector inspect(String entryId, String nodeId) { return Inspector.neutral(); }

    /** Commits a candidate topology or accepts a presentation-only node move. */
    Result commit(CanvasSnapshot candidateSnapshot);

    /** Called exactly once when CanvasUI releases input/display ownership. */
    default void closed(String reason) { }
}
