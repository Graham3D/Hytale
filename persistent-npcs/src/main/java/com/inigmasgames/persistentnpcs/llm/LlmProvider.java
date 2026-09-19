package com.inigmasgames.persistentnpcs.llm;

import com.inigmasgames.persistentnpcs.ai.AiProviderCapabilities;
import com.inigmasgames.persistentnpcs.ai.AiServiceKind;
import com.inigmasgames.persistentnpcs.ai.LanguageModelProvider;
import com.inigmasgames.persistentnpcs.ai.ProviderExecutionMode;
import java.util.Set;

/** Compatibility name retained for existing cognition code and third-party integrations. */
public interface LlmProvider extends LanguageModelProvider {
    @Override
    default String providerId() { return getClass().getSimpleName(); }

    @Override
    default AiServiceKind serviceKind() { return AiServiceKind.LANGUAGE_MODEL; }

    @Override
    default ProviderExecutionMode executionMode() { return ProviderExecutionMode.LOCAL; }

    @Override
    default AiProviderCapabilities capabilities() {
        return new AiProviderCapabilities(streamingEnabled(), true, true, Set.of("text"));
    }

    @Override
    default boolean streamingEnabled() { return false; }

    @Override
    default String backendDescription() { return description(); }
}
