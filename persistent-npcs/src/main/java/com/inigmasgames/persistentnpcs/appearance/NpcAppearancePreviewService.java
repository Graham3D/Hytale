package com.inigmasgames.persistentnpcs.appearance;

import com.inigmasgames.persistentnpcs.ui.NpcMeshPreviewSession;
import java.time.Instant;
import java.util.UUID;
import java.util.function.Consumer;

/** Generation-gated facade over the already proven client-local preview pipeline. */
public final class NpcAppearancePreviewService implements AutoCloseable {
    private final NpcMeshPreviewSession preview;
    private final NpcSkinCodecAdapter adapter;
    private final Consumer<String> diagnostics;
    private UUID activeDraftId;
    private long newestGeneration = -1;
    private boolean closed;

    public NpcAppearancePreviewService(NpcMeshPreviewSession preview,
            NpcSkinCodecAdapter adapter, Consumer<String> diagnostics) {
        this.preview = preview;
        this.adapter = adapter;
        this.diagnostics = diagnostics == null ? ignored -> { } : diagnostics;
    }

    public synchronized void show(NpcAppearanceDraft draft) {
        requireOpen();
        if (draft == null) throw new IllegalArgumentException("Appearance draft is required.");
        activeDraftId = draft.draftId();
        newestGeneration = draft.previewGeneration();
        var model = adapter.createModel(draft.currentSkin());
        if (!draft.draftId().equals(activeDraftId)
                || draft.previewGeneration() != newestGeneration) {
            diagnostics.accept("NPC_AUTHORING_APPEARANCE_PREVIEW_STALE_REJECTED timestamp="
                    + Instant.now() + " draftId=" + draft.draftId()
                    + " previewGeneration=" + draft.previewGeneration());
            return;
        }
        if (preview != null) preview.applyAppearanceDraft(model, draft.currentSkin(),
                draft.draftId(), newestGeneration);
        diagnostics.accept("NPC_AUTHORING_APPEARANCE_PREVIEW_READY timestamp=" + Instant.now()
                + " draftId=" + draft.draftId() + " previewGeneration=" + newestGeneration
                + " coalescing=NEWEST_GENERATION_ONLY viewerEcsMutation=false");
    }

    public synchronized void restore(NpcAppearanceDraft draft) {
        if (closed || draft == null) return;
        activeDraftId = null;
        newestGeneration++;
        if (preview != null) preview.restoreAuthoritativeTarget(
                draft.draftId(), newestGeneration);
        diagnostics.accept("NPC_AUTHORING_APPEARANCE_PREVIEW_CANCELLED timestamp="
                + Instant.now() + " draftId=" + draft.draftId()
                + " restoredPersistedTarget=true viewerEcsMutation=false");
    }

    public synchronized void commit(NpcAppearanceDraft draft,
            NpcAppearanceAuthoringService.SaveResult result) {
        requireOpen();
        activeDraftId = null;
        if (preview != null) preview.commitAuthoritativeTarget(result.model(), result.skin(),
                draft.draftId(), result.revision());
    }

    @Override public synchronized void close() {
        closed = true;
        activeDraftId = null;
        newestGeneration++;
    }

    private void requireOpen() {
        if (closed) throw new IllegalStateException("Appearance preview service is closed.");
    }
}
