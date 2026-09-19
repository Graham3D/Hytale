package com.inigmasgames.persistentnpcs.llm;

import java.util.Optional;
import java.util.UUID;

public interface LlmAttributionSource {
    Optional<LlmInferenceAttribution> attribution(UUID conversationId);
    Optional<LlmInferenceAttribution> latestForNpc(UUID npcId);
}
