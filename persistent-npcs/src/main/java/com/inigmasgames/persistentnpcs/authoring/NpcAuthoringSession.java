package com.inigmasgames.persistentnpcs.authoring;

import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * One server-owned authoring lease. The Custom UI is presentation and intent only;
 * this object owns event admission and lifecycle, never any domain data.
 */
public final class NpcAuthoringSession implements AutoCloseable {
    public enum WorkspaceState {
        CLOSED, OPENING, READY, PROFILE_EDIT, APPEARANCE_EDIT, VOICE_EDIT,
        COMMITTING, DEGRADED, CLOSING
    }

    public enum EditorKind { NONE, PROFILE, APPEARANCE, VOICE }

    public enum DirtyDomain { PROFILE, APPEARANCE, VOICE }

    private record Cleanup(String name, Runnable action) { }

    private final UUID sessionId;
    private final UUID viewerPlayerId;
    private final UUID npcStableId;
    private final UUID npcEntityUuid;
    private final long pageGeneration;
    private final Map<String, String> domainRevisionSnapshot;
    private final Predicate<String> permissionCheck;
    private final Consumer<String> diagnostics;
    private final Runnable registryRelease;
    private final List<Cleanup> cleanup = new ArrayList<>();
    private final Set<DirtyDomain> dirtyDomains = EnumSet.noneOf(DirtyDomain.class);
    private final AtomicBoolean acceptingEvents = new AtomicBoolean(true);
    private final AtomicBoolean closed = new AtomicBoolean();
    private WorkspaceState state = WorkspaceState.OPENING;
    private EditorKind activeEditor = EditorKind.NONE;
    private long editorGeneration = 1;

    NpcAuthoringSession(UUID sessionId, UUID viewerPlayerId, UUID npcStableId,
            UUID npcEntityUuid, long pageGeneration,
            Map<String, String> domainRevisionSnapshot,
            Predicate<String> permissionCheck, Consumer<String> diagnostics,
            Runnable registryRelease) {
        this.sessionId = sessionId;
        this.viewerPlayerId = viewerPlayerId;
        this.npcStableId = npcStableId;
        this.npcEntityUuid = npcEntityUuid;
        this.pageGeneration = pageGeneration;
        this.domainRevisionSnapshot = Map.copyOf(domainRevisionSnapshot == null
                ? Map.of() : new LinkedHashMap<>(domainRevisionSnapshot));
        this.permissionCheck = permissionCheck == null ? ignored -> false : permissionCheck;
        this.diagnostics = diagnostics == null ? ignored -> { } : diagnostics;
        this.registryRelease = registryRelease == null ? () -> { } : registryRelease;
        trace("NPC_AUTHORING_SESSION_OPENING", "state=OPENING revisions="
                + compact(this.domainRevisionSnapshot));
    }

    public UUID sessionId() { return sessionId; }
    public UUID viewerPlayerId() { return viewerPlayerId; }
    public UUID npcStableId() { return npcStableId; }
    public UUID npcEntityUuid() { return npcEntityUuid; }
    public long pageGeneration() { return pageGeneration; }
    public synchronized long editorGeneration() { return editorGeneration; }
    public synchronized EditorKind activeEditor() { return activeEditor; }
    public synchronized WorkspaceState state() { return state; }
    public Map<String, String> domainRevisionSnapshot() { return domainRevisionSnapshot; }
    public boolean acceptingEvents() { return acceptingEvents.get() && !closed.get(); }

    public synchronized void ready() {
        requireState(WorkspaceState.OPENING);
        state = WorkspaceState.READY;
        trace("NPC_AUTHORING_SESSION_READY", "state=READY");
    }

    public synchronized void beginCommit() {
        if (!acceptingEvents()) throw new IllegalStateException("Authoring session is closing.");
        state = WorkspaceState.COMMITTING;
        trace("NPC_AUTHORING_SESSION_COMMITTING", "state=COMMITTING");
    }

    public synchronized void degraded(String reason) {
        if (closed.get()) return;
        state = WorkspaceState.DEGRADED;
        trace("NPC_AUTHORING_SESSION_DEGRADED", "reason=" + safe(reason));
    }

    public synchronized long openEditor(EditorKind editor) {
        if (editor == null || editor == EditorKind.NONE) {
            throw new IllegalArgumentException("A contextual editor is required.");
        }
        if (!acceptingEvents()) throw new IllegalStateException("Authoring session is closing.");
        if (activeEditor != EditorKind.NONE && activeEditor != editor) {
            throw new IllegalStateException("Close the current contextual editor first.");
        }
        activeEditor = editor;
        editorGeneration++;
        state = switch (editor) {
            case PROFILE -> WorkspaceState.PROFILE_EDIT;
            case APPEARANCE -> WorkspaceState.APPEARANCE_EDIT;
            case VOICE -> WorkspaceState.VOICE_EDIT;
            case NONE -> WorkspaceState.READY;
        };
        trace("NPC_AUTHORING_SESSION_EDITOR_OPENED", "editor=" + editor
                + " editorGeneration=" + editorGeneration);
        return editorGeneration;
    }

    public synchronized void closeEditor(boolean discardDraft) {
        if (activeEditor == EditorKind.NONE) return;
        DirtyDomain domain = dirtyDomain(activeEditor);
        if (dirtyDomains.contains(domain) && !discardDraft) {
            throw new IllegalStateException("Unsaved editor changes require Save, Discard, or Stay.");
        }
        if (discardDraft) dirtyDomains.remove(domain);
        EditorKind closedEditor = activeEditor;
        activeEditor = EditorKind.NONE;
        editorGeneration++;
        state = WorkspaceState.READY;
        trace("NPC_AUTHORING_SESSION_EDITOR_CLOSED", "editor=" + closedEditor
                + " discard=" + discardDraft
                + " editorGeneration=" + editorGeneration);
    }

    public synchronized void markDirty(DirtyDomain domain) {
        if (domain == null) return;
        dirtyDomains.add(domain);
    }

    /**
     * A domain editor calls this only after its own authoritative Save transaction
     * has completed. The workspace never treats closing an overlay as a save.
     */
    public synchronized void markSaved(EditorKind editor) {
        if (editor == null || editor == EditorKind.NONE || editor != activeEditor) {
            throw new IllegalArgumentException("Only the active contextual editor can be saved.");
        }
        dirtyDomains.remove(dirtyDomain(editor));
        trace("NPC_AUTHORING_SESSION_EDITOR_SAVED", "editor=" + editor);
    }

    public synchronized boolean isDirty(EditorKind editor) {
        return editor != null && editor != EditorKind.NONE
                && dirtyDomains.contains(dirtyDomain(editor));
    }

    public synchronized Set<DirtyDomain> dirtyDomains() {
        return Set.copyOf(dirtyDomains);
    }

    public synchronized void addCleanup(String name, Runnable action) {
        if (closed.get()) throw new IllegalStateException("Authoring session is closed.");
        cleanup.add(new Cleanup(name == null ? "unnamed" : name,
                action == null ? () -> { } : action));
    }

    public void validate(NpcAuthoringEventEnvelope envelope,
            Set<String> allowedActions, String requiredPermission) {
        if (!acceptingEvents()) throw new IllegalStateException("Authoring session is closed.");
        if (envelope.schemaVersion() != NpcAuthoringEventEnvelope.CURRENT_SCHEMA_VERSION) {
            throw new IllegalArgumentException("Unsupported Authoring Studio event schema.");
        }
        if (!sessionId.equals(envelope.sessionId())
                || !viewerPlayerId.equals(envelope.viewerPlayerId())
                || !npcStableId.equals(envelope.npcStableId())
                || pageGeneration != envelope.pageGeneration()) {
            throw new IllegalArgumentException("Stale or foreign Authoring Studio event.");
        }
        synchronized (this) {
            if (activeEditor != envelope.editor()
                    || editorGeneration != envelope.editorGeneration()) {
                throw new IllegalArgumentException("Stale contextual-editor event.");
            }
        }
        String action = envelope.action().toUpperCase(Locale.ROOT);
        if (allowedActions == null || !allowedActions.contains(action)) {
            throw new IllegalArgumentException("Authoring action is not allowed.");
        }
        if (requiredPermission == null || requiredPermission.isBlank()
                || !permissionCheck.test(requiredPermission)) {
            throw new SecurityException("You do not have permission for this authoring action.");
        }
        trace("NPC_AUTHORING_SESSION_EVENT_ACCEPTED", "action=" + action
                + " permission=" + requiredPermission);
    }

    @Override
    public void close() {
        if (!closed.compareAndSet(false, true)) return;
        acceptingEvents.set(false);
        List<Cleanup> steps;
        synchronized (this) {
            state = WorkspaceState.CLOSING;
            activeEditor = EditorKind.NONE;
            editorGeneration++;
            dirtyDomains.clear();
            steps = List.copyOf(cleanup);
            cleanup.clear();
        }
        trace("NPC_AUTHORING_SESSION_CLOSING", "cleanupCount=" + steps.size());
        int failures = 0;
        for (Cleanup step : steps) {
            try {
                step.action().run();
                trace("NPC_AUTHORING_RECOVERY_CLEANUP", "step=" + step.name()
                        + " result=SUCCESS");
            } catch (RuntimeException failure) {
                failures++;
                trace("NPC_AUTHORING_RECOVERY_CLEANUP", "step=" + step.name()
                        + " result=FAILED error=" + safe(failure.getMessage()));
            }
        }
        try {
            registryRelease.run();
        } finally {
            synchronized (this) { state = WorkspaceState.CLOSED; }
            trace("NPC_AUTHORING_SESSION_CLOSED", "cleanupFailures=" + failures);
        }
    }

    private void requireState(WorkspaceState required) {
        if (state != required) throw new IllegalStateException(
                "Authoring session state is " + state + "; expected " + required + '.');
    }

    private static DirtyDomain dirtyDomain(EditorKind editor) {
        return switch (editor) {
            case PROFILE -> DirtyDomain.PROFILE;
            case APPEARANCE -> DirtyDomain.APPEARANCE;
            case VOICE -> DirtyDomain.VOICE;
            case NONE -> throw new IllegalArgumentException("NONE has no dirty domain.");
        };
    }

    private void trace(String family, String detail) {
        diagnostics.accept(family
                + " timestamp=" + Instant.now()
                + " sessionId=" + sessionId
                + " player=" + viewerPlayerId
                + " npcStableId=" + npcStableId
                + " npcEntityUuid=" + (npcEntityUuid == null ? "NOT_SPAWNED" : npcEntityUuid)
                + " pageGeneration=" + pageGeneration
                + " editor=" + activeEditor
                + " editorGeneration=" + editorGeneration
                + " " + detail);
    }

    private static String compact(Map<String, String> revisions) {
        return revisions.toString().replaceAll("\\s+", "_");
    }

    private static String safe(String value) {
        return value == null ? "UNKNOWN" : value.replaceAll("\\s+", "_");
    }
}
