package com.inigmasgames.persistentnpcs.evaluation;

import com.inigmasgames.persistentnpcs.orbis.CanonicalSpeechChunk;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** No-audio canonical sink. It captures exactly what the Orbis ledger committed. */
public final class EvaluationSpeechSink {
    private final ConcurrentHashMap<UUID, CanonicalCapture> captures =
            new ConcurrentHashMap<>();

    public void commit(UUID responseId, UUID npcId, String npcName,
            List<CanonicalSpeechChunk> chunks, String finalDialogue) {
        if (responseId == null) throw new IllegalArgumentException("responseId required");
        List<CanonicalSpeechChunk> immutable = List.copyOf(chunks == null ? List.of() : chunks);
        for (int index = 0; index < immutable.size(); index++) {
            if (immutable.get(index).index() != index) throw new IllegalStateException(
                    "Evaluation canonical chunks are not ordered");
        }
        String joined = immutable.stream().map(CanonicalSpeechChunk::text)
                .collect(java.util.stream.Collectors.joining(" "));
        if (!joined.equals(finalDialogue == null ? "" : finalDialogue)) {
            throw new IllegalStateException(
                    "Evaluation canonical sink differs from final decision text");
        }
        CanonicalCapture prior = captures.putIfAbsent(responseId,
                new CanonicalCapture(responseId, npcId, npcName, immutable, joined,
                        Instant.now()));
        if (prior != null && !prior.text().equals(joined)) throw new IllegalStateException(
                "Immutable response was committed with divergent text");
    }

    public CanonicalCapture capture(UUID responseId) { return captures.get(responseId); }
    public Map<UUID, CanonicalCapture> captures() { return Map.copyOf(captures); }
    public void clear() { captures.clear(); }

    public record CanonicalCapture(UUID responseId, UUID npcId, String npcName,
            List<CanonicalSpeechChunk> chunks, String text, Instant committedAt) {
        public CanonicalCapture { chunks = List.copyOf(chunks); }
    }
}
