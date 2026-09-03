package com.inigmasgames.persistentnpcs.conversation;

import com.inigmasgames.persistentnpcs.voice.VocalState;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

/** Test-only compatibility fixture retired from the production canonical speech path. */
public final class CommittedDialogueResponse {
    private final UUID responseId;
    private final Consumer<CommittedChunk> consumer;
    private final List<CommittedChunk> chunks = new ArrayList<>();
    private final StringBuilder text = new StringBuilder();
    private boolean cancelled;

    public CommittedDialogueResponse(UUID responseId, Consumer<CommittedChunk> consumer) {
        this.responseId = java.util.Objects.requireNonNull(responseId, "responseId");
        this.consumer = consumer == null ? ignored -> { } : consumer;
    }
    public synchronized CommittedChunk commit(String exactText, VocalState state) {
        if (cancelled) throw new IllegalStateException("Response is cancelled: " + responseId);
        if (exactText == null || exactText.isBlank()) throw new IllegalArgumentException(
                "Committed dialogue chunk cannot be blank");
        CommittedChunk chunk = new CommittedChunk(responseId, chunks.size(), exactText, state);
        chunks.add(chunk);
        if (!text.isEmpty()) text.append(' ');
        text.append(exactText);
        consumer.accept(chunk);
        return chunk;
    }
    public synchronized void cancel() { cancelled = true; }
    public synchronized boolean cancelled() { return cancelled; }
    public synchronized List<CommittedChunk> chunks() { return List.copyOf(chunks); }
    public synchronized String text() { return text.toString(); }
    public UUID responseId() { return responseId; }
    public record CommittedChunk(UUID responseId, int chunkIndex, String text,
            VocalState vocalState) { }
}
