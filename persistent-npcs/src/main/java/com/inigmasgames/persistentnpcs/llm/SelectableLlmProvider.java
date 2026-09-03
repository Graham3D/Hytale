package com.inigmasgames.persistentnpcs.llm;

import com.inigmasgames.persistentnpcs.ai.AiProviderCapabilities;
import com.inigmasgames.persistentnpcs.ai.AiProviderHealth;
import com.inigmasgames.persistentnpcs.ai.ProviderExecutionMode;
import com.inigmasgames.persistentnpcs.conversation.ConversationSession;
import com.inigmasgames.persistentnpcs.profile.NpcProfile;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/** Runtime selector over existing LLM providers; it never changes cognition or falls back. */
public final class SelectableLlmProvider implements LlmProvider,
        ConversationModelRoutingProvider, LlmAttributionSource, LlmRuntimeDiagnosticSource {
    private final Map<String, Entry> providers;
    private final AtomicReference<String> active;
    private final Consumer<String> persistSelection;
    private final Consumer<String> log;
    private final ConcurrentHashMap<UUID, LlmInferenceAttribution> conversations =
            new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, LlmInferenceAttribution> npcs =
            new ConcurrentHashMap<>();

    public SelectableLlmProvider(Map<String, Entry> providers, String initialProvider,
            Consumer<String> persistSelection, Consumer<String> log) {
        if (providers == null || providers.isEmpty()) {
            throw new IllegalArgumentException("Selectable LLM providers are required");
        }
        this.providers = Map.copyOf(new LinkedHashMap<>(providers));
        String initial = normalize(initialProvider);
        if (!this.providers.containsKey(initial)) {
            throw new IllegalArgumentException("Unknown initial LLM provider: " + initialProvider);
        }
        active = new AtomicReference<>(initial);
        this.persistSelection = persistSelection == null ? ignored -> { } : persistSelection;
        this.log = log == null ? ignored -> { } : log;
    }

    public String activeProviderName() { return active.get(); }
    public String activeModel() { return selected().model(); }
    public String activeEndpoint() { return selected().endpoint(); }
    public java.util.Set<String> providerNames() { return providers.keySet(); }

    public CompletableFuture<Boolean> unloadActiveResidentModel() {
        LlmProvider provider = selected().provider();
        if (provider instanceof ManagedLlmResidency managed) {
            return managed.unloadResident();
        }
        return CompletableFuture.completedFuture(false);
    }

    public CompletableFuture<Void> ensureActiveResident() {
        LlmProvider provider = selected().provider();
        return provider instanceof ManagedLlmResidency managed
                ? managed.ensureResident() : provider.warmUp();
    }

    public CompletableFuture<Boolean> activateLowerMemoryProfile() {
        LlmProvider provider = selected().provider();
        return provider instanceof OpenAiCompatibleProvider local
                ? local.activateLowerMemoryProfile()
                : CompletableFuture.completedFuture(false);
    }

    public CompletableFuture<Boolean> activateStartupSteadyStateProfile() {
        LlmProvider provider = selected().provider();
        return provider instanceof OpenAiCompatibleProvider local
                ? local.activateStartupSteadyStateProfile()
                : CompletableFuture.completedFuture(false);
    }

    public String activeHardwareProfile() {
        LlmProvider provider = selected().provider();
        return provider instanceof OpenAiCompatibleProvider local
                ? local.activeHardwareProfile() : "UNMANAGED";
    }

    public CompletableFuture<Integer> unloadInactiveResidentModels() {
        return unloadInactiveResidentModels(false);
    }

    /** Pressure reclaim only counts runners whose residency this runtime established. */
    public CompletableFuture<Integer> unloadOwnedInactiveResidentModels() {
        return unloadInactiveResidentModels(true);
    }

    private CompletableFuture<Integer> unloadInactiveResidentModels(boolean ownedOnly) {
        String selectedName = active.get();
        List<CompletableFuture<Boolean>> actions = providers.entrySet().stream()
                .filter(value -> !value.getKey().equals(selectedName))
                .map(Map.Entry::getValue)
                .map(Entry::provider)
                .filter(ManagedLlmResidency.class::isInstance)
                .map(ManagedLlmResidency.class::cast)
                .filter(value -> !ownedOnly || value.residencyPrepared())
                .map(ManagedLlmResidency::unloadResident)
                .toList();
        if (actions.isEmpty()) return CompletableFuture.completedFuture(0);
        return CompletableFuture.allOf(actions.toArray(CompletableFuture[]::new))
                .thenApply(ignored -> (int) actions.stream().filter(future ->
                        Boolean.TRUE.equals(future.getNow(false))).count());
    }

    public CompletableFuture<Void> prepareActiveResidency() {
        return unloadInactiveResidentModels().thenCompose(ignored -> ensureActiveResident());
    }

    /** Secret-free configured residency expectations for cached runtime diagnostics. */
    public java.util.List<ConfiguredProvider> configuredProviders() {
        String selectedName = active.get();
        return providers.entrySet().stream().map(value -> new ConfiguredProvider(
                value.getKey(), value.getValue().model(),
                value.getKey().equals(selectedName),
                value.getValue().provider().resourceRequirements().residentVramMiB()))
                .toList();
    }

    /** Captures an exact delegate; later operator selections cannot reroute it. */
    public PinnedLlmProvider pinActive() {
        String providerName = active.get();
        Entry entry = providers.get(providerName);
        if (entry == null) throw new IllegalStateException(
                "Active LLM provider disappeared: " + providerName);
        return new PinnedLlmProvider(providerName, entry.model(), entry.endpoint(),
                new PinnedDelegate(providerName, entry));
    }

    /** Verifies exact model availability before atomically changing the live provider. */
    public CompletableFuture<Selection> select(String providerName) {
        String requested = normalize(providerName);
        Entry entry = providers.get(requested);
        if (entry == null) {
            return CompletableFuture.failedFuture(new IllegalArgumentException(
                    "Unknown LLM provider '" + providerName + "'. Available: "
                            + providers.keySet()));
        }
        return entry.provider().checkStatus().thenApply(status -> {
            boolean exactModelAvailable = status.configured() && status.reachable()
                    && status.reason() != null
                    && status.reason().contains("configured model is available");
            if (!exactModelAvailable) {
                throw new IllegalStateException("Cannot select " + requested + " ("
                        + entry.model() + "): " + status.reason()
                        + " No fallback was used.");
            }
            persistSelection.accept(requested);
            active.set(requested);
            log.accept("LLM_PROVIDER_SELECTED provider=" + requested + " model="
                    + entry.model() + " endpoint=" + entry.endpoint());
            entry.provider().warmUp();
            return new Selection(requested, entry.model(), entry.endpoint());
        });
    }

    @Override public ModelTier selectTier(
            ConversationSession session, NpcProfile profile, String playerMessage) {
        LlmProvider provider = selected().provider();
        return provider instanceof ConversationModelRoutingProvider router
                ? router.selectTier(session, profile, playerMessage) : ModelTier.GENERIC;
    }

    @Override public CompletableFuture<LlmResult> generateResponse(LlmRequest request) {
        return generateResponse(request, ignored -> { });
    }

    @Override public CompletableFuture<LlmResult> generateResponse(
            LlmRequest request, Consumer<String> tokenConsumer) {
        String providerName = active.get();
        Entry entry = providers.get(providerName);
        LlmInferenceAttribution attribution = new LlmInferenceAttribution(
                request.providerRequestId(), request.npcId(), providerName,
                entry.model(), entry.endpoint(), Instant.now());
        conversations.put(request.providerRequestId(), attribution);
        npcs.put(request.npcId(), attribution);
        log.accept("LLM_RESPONSE_DISPATCH request=" + request.providerRequestId()
                + " npc=" + request.npcId() + " provider=" + providerName
                + " model=" + entry.model());
        return entry.provider().generateResponse(request, tokenConsumer)
                .exceptionallyCompose(failure -> CompletableFuture.failedFuture(
                        explicitFailure(providerName, entry.model(), failure)));
    }

    private static Throwable explicitFailure(
            String provider, String model, Throwable failure) {
        Throwable cause = failure;
        while (cause instanceof java.util.concurrent.CompletionException
                && cause.getCause() != null) cause = cause.getCause();
        if (cause instanceof java.util.concurrent.CancellationException) return cause;
        String message = cause.getMessage() == null
                ? cause.getClass().getSimpleName() : cause.getMessage();
        return new IllegalStateException("Selected LLM " + provider + " (" + model
                + ") failed: " + message + ". No fallback was used.", cause);
    }

    @Override public Optional<LlmInferenceAttribution> attribution(UUID conversationId) {
        return Optional.ofNullable(conversations.get(conversationId));
    }

    @Override public Optional<LlmInferenceAttribution> latestForNpc(UUID npcId) {
        return Optional.ofNullable(npcs.get(npcId));
    }

    @Override public void cancel(UUID id) {
        providers.values().forEach(entry -> entry.provider().cancel(id));
    }

    @Override public void endSession(UUID sessionId) {
        providers.values().forEach(entry -> entry.provider().endSession(sessionId));
        conversations.remove(sessionId);
    }

    @Override public boolean streamingEnabled() { return selected().provider().streamingEnabled(); }
    @Override public CompletableFuture<Void> warmUp() { return selected().provider().warmUp(); }
    @Override public CompletableFuture<LlmProviderStatus> checkStatus() {
        return selected().provider().checkStatus();
    }
    @Override public String providerId() { return active.get(); }
    @Override public ProviderExecutionMode executionMode() {
        return selected().provider().executionMode();
    }
    @Override public AiProviderCapabilities capabilities() {
        return selected().provider().capabilities();
    }
    @Override public CompletableFuture<AiProviderHealth> health() {
        return selected().provider().health();
    }
    @Override public int concurrencyLimit() { return selected().provider().concurrencyLimit(); }
    @Override public com.inigmasgames.persistentnpcs.ai.AiResourceRequirements
            resourceRequirements() { return selected().provider().resourceRequirements(); }
    @Override public String backendDescription() {
        Entry entry = selected();
        return "selected=" + active.get() + " model=" + entry.model()
                + " endpoint=" + entry.endpoint();
    }
    @Override public String description() { return backendDescription(); }
    @Override public com.google.gson.JsonObject runtimeDiagnostics(UUID npcId) {
        LlmProvider provider = selected().provider();
        com.google.gson.JsonObject value = provider instanceof LlmRuntimeDiagnosticSource source
                ? source.runtimeDiagnostics(npcId) : new com.google.gson.JsonObject();
        value.addProperty("selectedProvider", active.get());
        value.addProperty("selectedModel", selected().model());
        return value;
    }
    @Override public void close() { providers.values().forEach(entry -> entry.provider().close()); }

    private Entry selected() { return providers.get(active.get()); }

    private final class PinnedDelegate implements LlmProvider, LlmAttributionSource,
            LlmRuntimeDiagnosticSource {
        private final String providerName;
        private final Entry entry;

        private PinnedDelegate(String providerName, Entry entry) {
            this.providerName = providerName;
            this.entry = entry;
        }

        @Override public CompletableFuture<LlmResult> generateResponse(LlmRequest request) {
            return generateResponse(request, ignored -> { });
        }

        @Override public CompletableFuture<LlmResult> generateResponse(
                LlmRequest request, Consumer<String> tokenConsumer) {
            LlmInferenceAttribution attribution = new LlmInferenceAttribution(
                    request.providerRequestId(), request.npcId(), providerName,
                    entry.model(), entry.endpoint(), Instant.now());
            conversations.put(request.providerRequestId(), attribution);
            npcs.put(request.npcId(), attribution);
            log.accept("LLM_RESPONSE_DISPATCH request=" + request.providerRequestId()
                    + " npc=" + request.npcId() + " provider=" + providerName
                    + " model=" + entry.model() + " pinned=true");
            return entry.provider().generateResponse(request, tokenConsumer)
                    .exceptionallyCompose(failure -> CompletableFuture.failedFuture(
                            explicitFailure(providerName, entry.model(), failure)));
        }

        @Override public Optional<LlmInferenceAttribution> attribution(UUID requestId) {
            return Optional.ofNullable(conversations.get(requestId));
        }

        @Override public Optional<LlmInferenceAttribution> latestForNpc(UUID npcId) {
            return Optional.ofNullable(npcs.get(npcId));
        }

        @Override public void cancel(UUID id) { entry.provider().cancel(id); }
        @Override public void endSession(UUID id) { entry.provider().endSession(id); }
        @Override public boolean streamingEnabled() { return entry.provider().streamingEnabled(); }
        @Override public CompletableFuture<Void> warmUp() { return entry.provider().warmUp(); }
        @Override public CompletableFuture<LlmProviderStatus> checkStatus() {
            return entry.provider().checkStatus();
        }
        @Override public String providerId() { return providerName; }
        @Override public ProviderExecutionMode executionMode() {
            return entry.provider().executionMode();
        }
        @Override public AiProviderCapabilities capabilities() {
            return entry.provider().capabilities();
        }
        @Override public CompletableFuture<AiProviderHealth> health() {
            return entry.provider().health();
        }
        @Override public int concurrencyLimit() { return entry.provider().concurrencyLimit(); }
        @Override public com.inigmasgames.persistentnpcs.ai.AiResourceRequirements
                resourceRequirements() { return entry.provider().resourceRequirements(); }
        @Override public String backendDescription() {
            return "pinned=" + providerName + " model=" + entry.model()
                    + " endpoint=" + entry.endpoint();
        }
        @Override public String description() { return backendDescription(); }
        @Override public com.google.gson.JsonObject runtimeDiagnostics(UUID npcId) {
            com.google.gson.JsonObject value = entry.provider()
                    instanceof LlmRuntimeDiagnosticSource source
                    ? source.runtimeDiagnostics(npcId) : new com.google.gson.JsonObject();
            value.addProperty("pinnedProvider", providerName);
            value.addProperty("pinnedModel", entry.model());
            return value;
        }
        // Provider lifetime belongs to the selector/router, not one branch handle.
        @Override public void close() { }
    }
    private static String normalize(String value) {
        return value == null ? "" : value.strip().toUpperCase(java.util.Locale.ROOT);
    }

    public record Entry(LlmProvider provider, String model, String endpoint) {
        public Entry {
            java.util.Objects.requireNonNull(provider, "provider");
            model = model == null ? "" : model;
            endpoint = endpoint == null ? "" : endpoint;
        }
    }

    public record Selection(String provider, String model, String endpoint) { }
    public record ConfiguredProvider(String provider, String model,
            boolean expectedResident, long estimatedVramMiB) { }
}
