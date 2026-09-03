package com.inigmasgames.persistentnpcs.llm;

import java.time.Instant;
import java.util.UUID;

/** Observable response attribution; contains no prompt or hidden reasoning. */
public record LlmInferenceAttribution(
        UUID conversationId,
        UUID npcId,
        String provider,
        String model,
        String endpoint,
        Instant dispatchedAt) {
}
