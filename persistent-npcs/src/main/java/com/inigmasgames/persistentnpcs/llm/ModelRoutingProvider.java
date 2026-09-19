package com.inigmasgames.persistentnpcs.llm;

import com.inigmasgames.persistentnpcs.conversation.ConversationSession;
import com.inigmasgames.persistentnpcs.profile.NpcProfile;
import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import com.inigmasgames.persistentnpcs.ai.AiProviderCapabilities;
import com.inigmasgames.persistentnpcs.ai.AiProviderHealth;
import com.inigmasgames.persistentnpcs.ai.ProviderExecutionMode;

/** Sticky per-session model routing with failure fallback to the generic local provider. */
public final class ModelRoutingProvider implements LlmProvider, ConversationModelRoutingProvider {
    private final LlmProvider generic;
    private final Map<ModelTier, LlmProvider> providers;
    private final int deepTurnThreshold;
    private final ConcurrentHashMap<UUID, ModelTier> sessionTiers = new ConcurrentHashMap<>();

    public ModelRoutingProvider(
            LlmProvider generic, Map<ModelTier, LlmProvider> configuredProviders,
            int deepTurnThreshold) {
        this.generic = generic;
        EnumMap<ModelTier, LlmProvider> routes = new EnumMap<>(ModelTier.class);
        routes.put(ModelTier.GENERIC, generic);
        if (configuredProviders != null) routes.putAll(configuredProviders);
        this.providers = Map.copyOf(routes);
        this.deepTurnThreshold = Math.max(2, deepTurnThreshold);
    }

    public ModelTier selectTier(
            ConversationSession session, NpcProfile profile, String playerMessage) {
        ModelTier desired = requestedProfileTier(profile.modelTier());
        String message = playerMessage == null ? "" : playerMessage.toLowerCase(Locale.ROOT);
        if (containsPlanningIntent(message)) {
            desired = maximum(desired, ModelTier.IMPORTANT);
        }
        if (session.recentTurns(100).size() >= deepTurnThreshold) {
            desired = ModelTier.DEEP_CONVERSATION;
        }
        ModelTier selected = sessionTiers.merge(session.sessionId(), desired,
                ModelRoutingProvider::maximum);
        if (!providers.containsKey(selected)) {
            selected = strongestConfiguredAtOrBelow(selected);
            sessionTiers.put(session.sessionId(), selected);
        }
        return selected;
    }

    public ModelTier selectedTier(UUID sessionId) {
        return sessionTiers.getOrDefault(sessionId, ModelTier.GENERIC);
    }

    public void endSession(UUID sessionId) {
        sessionTiers.remove(sessionId);
        providers.values().forEach(provider -> provider.cancel(sessionId));
    }

    @Override
    public CompletableFuture<LlmResult> generateResponse(LlmRequest request) {
        return generateResponse(request, ignored -> { });
    }

    @Override
    public CompletableFuture<LlmResult> generateResponse(
            LlmRequest request, Consumer<String> tokenConsumer) {
        ModelTier tier = selectedTier(request.conversationId());
        LlmProvider selected = providers.getOrDefault(tier, generic);
        AtomicBoolean emitted = new AtomicBoolean();
        Consumer<String> trackingConsumer = token -> {
            if (token != null && !token.isEmpty()) emitted.set(true);
            tokenConsumer.accept(token);
        };
        CompletableFuture<LlmResult> primary = selected.generateResponse(request, trackingConsumer);
        if (selected == generic) return primary;
        return primary.handle((result, failure) -> failure == null
                        ? CompletableFuture.completedFuture(result)
                        : emitted.get()
                                ? CompletableFuture.<LlmResult>failedFuture(failure)
                                : generic.generateResponse(request, tokenConsumer))
                .thenCompose(value -> value);
    }

    @Override
    public boolean streamingEnabled() {
        return generic.streamingEnabled();
    }

    @Override
    public CompletableFuture<Void> warmUp() {
        return generic.warmUp();
    }

    @Override
    public CompletableFuture<LlmProviderStatus> checkStatus() {
        return generic.checkStatus();
    }

    @Override
    public String description() {
        return "model router (generic=" + generic.description()
                + ", configuredTiers=" + providers.keySet() + ")";
    }

    @Override public String providerId() { return "model-router:" + generic.providerId(); }
    @Override public ProviderExecutionMode executionMode() { return generic.executionMode(); }
    @Override public AiProviderCapabilities capabilities() { return generic.capabilities(); }
    @Override public CompletableFuture<AiProviderHealth> health() { return generic.health(); }
    @Override public int concurrencyLimit() { return generic.concurrencyLimit(); }
    @Override public String backendDescription() { return description(); }
    @Override public void cancel(UUID requestOrSessionId) {
        providers.values().forEach(provider -> provider.cancel(requestOrSessionId));
    }
    @Override public void close() {
        providers.values().stream().distinct().forEach(LlmProvider::close);
    }

    private ModelTier strongestConfiguredAtOrBelow(ModelTier desired) {
        if (desired == ModelTier.DEEP_CONVERSATION
                && providers.containsKey(ModelTier.IMPORTANT)) return ModelTier.IMPORTANT;
        return ModelTier.GENERIC;
    }

    private static ModelTier requestedProfileTier(String value) {
        if (value == null) return ModelTier.GENERIC;
        return switch (value.strip().toUpperCase(Locale.ROOT)) {
            case "DEEP", "DEEP_CONVERSATION" -> ModelTier.DEEP_CONVERSATION;
            case "IMPORTANT" -> ModelTier.IMPORTANT;
            default -> ModelTier.GENERIC;
        };
    }

    private static boolean containsPlanningIntent(String message) {
        return message.contains("quest") || message.contains("plan")
                || message.contains("negotiate") || message.contains("investigate")
                || message.contains("why are you here") || message.contains("help you");
    }

    private static ModelTier maximum(ModelTier first, ModelTier second) {
        return first.ordinal() >= second.ordinal() ? first : second;
    }
}
