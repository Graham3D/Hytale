package com.inigmasgames.persistentnpcs;

import com.inigmasgames.persistentnpcs.ai.AiProviderCapabilities;
import com.inigmasgames.persistentnpcs.ai.AiProviderDefinition;
import com.inigmasgames.persistentnpcs.ai.AiProviderHealth;
import com.inigmasgames.persistentnpcs.ai.LlmProviderCatalog;
import com.inigmasgames.persistentnpcs.ai.LlmProviderCatalogRepository;
import com.inigmasgames.persistentnpcs.ai.ProviderExecutionMode;
import com.inigmasgames.persistentnpcs.llm.ChatMessage;
import com.inigmasgames.persistentnpcs.llm.LlmLatency;
import com.inigmasgames.persistentnpcs.llm.LlmProvider;
import com.inigmasgames.persistentnpcs.llm.LlmProviderStatus;
import com.inigmasgames.persistentnpcs.llm.LlmRequest;
import com.inigmasgames.persistentnpcs.llm.LlmResult;
import com.inigmasgames.persistentnpcs.llm.SelectableLlmProvider;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;

public final class R037SelectableLlmProviderTest {
    private R037SelectableLlmProviderTest() { }

    public static void main(String[] args) throws Exception {
        catalogDefaultsToStableNemotronAndPersistsSelection();
        liveSelectionControlsRealGenerationAndAttribution();
        pinnedSelectionSurvivesRuntimeSwitch();
        unavailableProviderFailsClearlyWithoutFallback();
        sourceWiresNativeOperatorUiAndTraceAttribution();
        System.out.println("R037 selectable Qwen/Nemotron provider tests passed.");
    }

    private static void pinnedSelectionSurvivesRuntimeSwitch() {
        FakeLlm qwen = new FakeLlm("qwen-pinned", true);
        FakeLlm nemotron = new FakeLlm("nemotron-new", true);
        SelectableLlmProvider selector = selector(qwen, nemotron, ignored -> { });
        var pinned = selector.pinActive();
        selector.select("NEMOTRON").join();
        UUID requestId = UUID.randomUUID();
        LlmRequest request = new LlmRequest(UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), List.of(new ChatMessage("user", "hello")))
                .withProviderRequestId(requestId);
        assert pinned.delegate().generateResponse(request).join().text().equals("qwen-pinned");
        assert "QWEN".equals(pinned.provider());
        assert selector.attribution(requestId).orElseThrow().provider().equals("QWEN");
        assert qwen.calls == 1 && nemotron.calls == 0;
    }

    private static void catalogDefaultsToStableNemotronAndPersistsSelection()
            throws Exception {
        Path root = Files.createTempDirectory("r037-catalog-");
        AiProviderDefinition nemotron = definition("nemotron-3-nano:4b");
        LlmProviderCatalogRepository repository = new LlmProviderCatalogRepository(root);
        LlmProviderCatalog catalog = repository.load(nemotron);
        assert catalog.activeProvider().equals("NEMOTRON");
        assert catalog.providers().get("QWEN").model().equals(
                LlmProviderCatalog.QWEN_MODEL);
        assert catalog.providers().get("NEMOTRON").model().equals("nemotron-3-nano:4b");
        assert !catalog.providers().containsKey("ORBIS_LLAMA_CPP_NEMOTRON")
                : "shelved experimental backend must not be production-selectable";
        assert repository.path().getParent().equals(root);
        assert !repository.path().toString().contains("profiles");
        repository.select("NEMOTRON");
        assert repository.load(nemotron).activeProvider().equals("NEMOTRON");
    }

    private static void liveSelectionControlsRealGenerationAndAttribution() {
        FakeLlm qwen = new FakeLlm("qwen-output", true);
        FakeLlm nemotron = new FakeLlm("nemotron-output", true);
        AtomicReference<String> persisted = new AtomicReference<>();
        SelectableLlmProvider selector = selector(qwen, nemotron, persisted::set);
        UUID conversation = UUID.randomUUID();
        UUID npc = UUID.randomUUID();
        LlmRequest request = new LlmRequest(conversation, npc, UUID.randomUUID(),
                List.of(new ChatMessage("user", "hello")));
        assert selector.generateResponse(request).join().text().equals("qwen-output");
        var qwenAttribution = selector.attribution(conversation).orElseThrow();
        assert qwenAttribution.provider().equals("QWEN");
        assert qwenAttribution.model().equals(LlmProviderCatalog.QWEN_MODEL);

        selector.select("NEMOTRON").join();
        assert persisted.get().equals("NEMOTRON");
        assert selector.generateResponse(request).join().text().equals("nemotron-output");
        assert selector.latestForNpc(npc).orElseThrow().provider().equals("NEMOTRON");
        assert qwen.calls == 1 && nemotron.calls == 1;
    }

    private static void unavailableProviderFailsClearlyWithoutFallback() {
        FakeLlm qwen = new FakeLlm("qwen-output", true);
        FakeLlm nemotron = new FakeLlm("nemotron-output", false);
        SelectableLlmProvider selector = selector(qwen, nemotron, ignored -> { });
        boolean failed = false;
        try {
            selector.select("NEMOTRON").join();
        } catch (RuntimeException expected) {
            failed = root(expected).getMessage().contains("No fallback was used");
        }
        assert failed : "unavailable exact model did not fail clearly";
        assert selector.activeProviderName().equals("QWEN");
        assert nemotron.calls == 0 : "health failure invoked cognition";
    }

    private static void sourceWiresNativeOperatorUiAndTraceAttribution() throws Exception {
        String page = Files.readString(Path.of("src/main/java/com/inigmasgames/"
                + "persistentnpcs/ui/CognitionInspectorPage.java"));
        String command = Files.readString(Path.of("src/main/java/com/inigmasgames/"
                + "persistentnpcs/command/CognitionInspectorCommand.java"));
        String ui = Files.readString(Path.of("src/main/resources/Common/UI/Custom/Pages/"
                + "ImmersiveNpcCognitionInspector.ui"));
        String trace = Files.readString(Path.of("src/main/java/com/inigmasgames/"
                + "persistentnpcs/diagnostics/NpcTurnAuditLog.java"));
        assert command.contains("requirePermission");
        assert page.contains("selectLlmProvider(requested)");
        assert ui.contains("#QwenButton") && ui.contains("#NemotronButton");
        assert trace.contains("llmProvider") && trace.contains("llmModel");
        assert !page.toLowerCase().contains("html");
    }

    private static SelectableLlmProvider selector(
            FakeLlm qwen, FakeLlm nemotron, java.util.function.Consumer<String> persist) {
        LinkedHashMap<String, SelectableLlmProvider.Entry> entries = new LinkedHashMap<>();
        entries.put("QWEN", new SelectableLlmProvider.Entry(qwen,
                LlmProviderCatalog.QWEN_MODEL, LlmProviderCatalog.OLLAMA_CHAT_ENDPOINT));
        entries.put("NEMOTRON", new SelectableLlmProvider.Entry(nemotron,
                "nemotron-3-nano:4b", LlmProviderCatalog.OLLAMA_CHAT_ENDPOINT));
        return new SelectableLlmProvider(entries, "QWEN", persist, ignored -> { });
    }

    private static AiProviderDefinition definition(String model) {
        return new AiProviderDefinition("OPENAI_COMPATIBLE",
                LlmProviderCatalog.OLLAMA_CHAT_ENDPOINT, model, 12_000, 2,
                "LOCAL", false, null);
    }

    private static Throwable root(Throwable failure) {
        Throwable value = failure;
        while (value.getCause() != null) value = value.getCause();
        return value;
    }

    private static final class FakeLlm implements LlmProvider {
        private final String text;
        private final boolean available;
        private int calls;
        private FakeLlm(String text, boolean available) {
            this.text = text;
            this.available = available;
        }
        @Override public CompletableFuture<LlmResult> generateResponse(LlmRequest request) {
            calls++;
            return CompletableFuture.completedFuture(new LlmResult(text,
                    new LlmLatency(Instant.now(), 2, 4, true)));
        }
        @Override public CompletableFuture<LlmProviderStatus> checkStatus() {
            return CompletableFuture.completedFuture(new LlmProviderStatus(
                    "local", "model", true, true, true, available
                            ? "Connected successfully; the configured model is available."
                            : "Server reachable; configured model is not listed."));
        }
        @Override public ProviderExecutionMode executionMode() {
            return ProviderExecutionMode.LOCAL;
        }
        @Override public AiProviderCapabilities capabilities() {
            return new AiProviderCapabilities(true, true, true, Set.of("test"));
        }
        @Override public CompletableFuture<AiProviderHealth> health() {
            return CompletableFuture.completedFuture(available
                    ? AiProviderHealth.healthy("ready")
                    : AiProviderHealth.unavailable("missing"));
        }
        @Override public String backendDescription() { return text; }
    }
}
