package com.inigmasgames.persistentnpcs.orbis;

import java.util.List;

/** Delivery provenance passed to conversation history only after native playback is terminal. */
public record SpeechDeliveryReport(
        List<CanonicalSpeechChunk> delivered,
        CanonicalSpeechChunk partial,
        List<CanonicalSpeechChunk> notDelivered,
        String reason) {

    public SpeechDeliveryReport {
        delivered = List.copyOf(delivered == null ? List.of() : delivered);
        notDelivered = List.copyOf(notDelivered == null ? List.of() : notDelivered);
        reason = reason == null ? "" : reason;
    }

    public String deliveredText() {
        return delivered.stream().map(CanonicalSpeechChunk::text)
                .collect(java.util.stream.Collectors.joining(" "));
    }

    public boolean interrupted() {
        return partial != null || !notDelivered.isEmpty();
    }
}
