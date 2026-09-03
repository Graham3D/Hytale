package com.inigmasgames.persistentnpcs.orbis;

import com.inigmasgames.persistentnpcs.voice.VocalState;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Append-only lexical authority shared by display, TTS, playback and history. */
public final class CanonicalSpeechLedger {
    public enum State { COMMITTED, QUEUED, DELIVERED, PARTIAL, DISCARDED }

    public record Segment(CanonicalSpeechChunk chunk, int charStartInclusive,
            int charEndExclusive, State state) {
        public Segment {
            if (chunk == null || charStartInclusive < 0
                    || charEndExclusive <= charStartInclusive || state == null) {
                throw new IllegalArgumentException("complete canonical speech segment required");
            }
        }
        Segment withState(State value) {
            return new Segment(chunk, charStartInclusive, charEndExclusive, value);
        }
    }

    private final ResponseId responseId;
    private final ArrayList<Segment> segments = new ArrayList<>();
    private final Map<SpeechChunkId, Integer> indexes = new LinkedHashMap<>();
    private final StringBuilder canonical = new StringBuilder();
    private boolean sealed;

    public CanonicalSpeechLedger(ResponseId responseId) {
        if (responseId == null) throw new IllegalArgumentException("responseId required");
        this.responseId = responseId;
    }

    public synchronized Segment append(SpeechChunkId id, int index, String exactText,
            VocalState vocalState) {
        if (sealed) throw new IllegalStateException("canonical speech ledger is sealed");
        if (index != segments.size()) {
            throw new IllegalArgumentException("canonical segments must be ordered");
        }
        String text = exactText == null ? "" : exactText.strip();
        if (text.isBlank()) throw new IllegalArgumentException("canonical segment is blank");
        int start = canonical.length();
        if (!canonical.isEmpty()) canonical.append(' ');
        canonical.append(text);
        CanonicalSpeechChunk chunk = new CanonicalSpeechChunk(id, index, text, vocalState);
        Segment segment = new Segment(chunk, start, canonical.length(), State.COMMITTED);
        segments.add(segment);
        indexes.put(id, index);
        return segment;
    }

    /** Seal only when the final response preserves every already-committed phrase. */
    public synchronized List<CanonicalSpeechChunk> seal(List<CanonicalSpeechChunk> completed) {
        List<CanonicalSpeechChunk> proposed = List.copyOf(completed == null ? List.of() : completed);
        for (int index = 0; index < segments.size(); index++) {
            CanonicalSpeechChunk committed = segments.get(index).chunk();
            if (index >= proposed.size() || proposed.get(index).index() != index
                    || !committed.text().equals(proposed.get(index).text())) {
                throw new IllegalArgumentException(
                        "final canonical response diverged from immutable speech prefix");
            }
        }
        for (int index = segments.size(); index < proposed.size(); index++) {
            CanonicalSpeechChunk value = proposed.get(index);
            append(value.id(), index, value.text(), value.vocalState());
        }
        sealed = true;
        return chunks();
    }

    public synchronized void queued(SpeechChunkId id) { transition(id, State.QUEUED); }
    public synchronized void delivered(SpeechChunkId id) { transition(id, State.DELIVERED); }
    public synchronized void partial(SpeechChunkId id) {
        Integer index = indexes.get(id);
        if (index != null && segments.get(index).state() != State.DELIVERED) {
            replace(index, State.PARTIAL);
        }
    }
    public synchronized void discardUndelivered() {
        for (int index = 0; index < segments.size(); index++) {
            State state = segments.get(index).state();
            if (state != State.DELIVERED && state != State.PARTIAL) replace(index, State.DISCARDED);
        }
    }

    private void transition(SpeechChunkId id, State state) {
        Integer index = indexes.get(id);
        if (index != null && segments.get(index).state() != State.DELIVERED) replace(index, state);
    }
    private void replace(int index, State state) {
        segments.set(index, segments.get(index).withState(state));
    }

    public ResponseId responseId() { return responseId; }
    public synchronized boolean sealed() { return sealed; }
    public synchronized String canonicalText() { return canonical.toString(); }
    public synchronized List<Segment> segments() { return List.copyOf(segments); }
    public synchronized List<CanonicalSpeechChunk> chunks() {
        return segments.stream().map(Segment::chunk).toList();
    }
}
