package com.inigmasgames.persistentnpcs;

import com.inigmasgames.persistentnpcs.ai.LlmProviderCatalog;
import com.inigmasgames.persistentnpcs.config.FrameworkConfig;
import com.inigmasgames.persistentnpcs.llm.ChatMessage;
import com.inigmasgames.persistentnpcs.llm.LlmRequest;
import com.inigmasgames.persistentnpcs.llm.OpenAiCompatibleProvider;
import com.inigmasgames.persistentnpcs.llm.SelectableLlmProvider;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.UUID;

/** Live provider-boundary verification; no game persistence stores are opened. */
public final class R037LiveProviderSelectionTest {
    private R037LiveProviderSelectionTest() { }

    public static void main(String[] args) {
        String endpoint = LlmProviderCatalog.OLLAMA_CHAT_ENDPOINT;
        String qwenModel = LlmProviderCatalog.QWEN_MODEL;
        String nemotronModel = "nemotron-3-nano:4b";
        OpenAiCompatibleProvider qwen = new OpenAiCompatibleProvider(
                config(endpoint, qwenModel));
        OpenAiCompatibleProvider nemotron = new OpenAiCompatibleProvider(
                config(endpoint, nemotronModel));
        LinkedHashMap<String, SelectableLlmProvider.Entry> entries = new LinkedHashMap<>();
        entries.put("QWEN", new SelectableLlmProvider.Entry(qwen, qwenModel, endpoint));
        entries.put("NEMOTRON", new SelectableLlmProvider.Entry(
                nemotron, nemotronModel, endpoint));
        try (SelectableLlmProvider selector = new SelectableLlmProvider(
                entries, "QWEN", ignored -> { }, ignored -> { })) {
            UUID npc = UUID.fromString("3f84ec9e-37c5-4f11-9a74-106cd3bc04da");
            var qwenRequest = request(npc);
            var qwenResult = selector.generateResponse(qwenRequest).join();
            var qwenAttribution = selector.attribution(qwenRequest.conversationId()).orElseThrow();
            assert !qwenResult.text().isBlank();
            assert qwenAttribution.provider().equals("QWEN");
            assert qwenAttribution.model().equals(qwenModel);

            selector.select("NEMOTRON").join();
            var nemotronRequest = request(npc);
            var nemotronResult = selector.generateResponse(nemotronRequest).join();
            var nemotronAttribution = selector.attribution(
                    nemotronRequest.conversationId()).orElseThrow();
            assert !nemotronResult.text().isBlank();
            assert nemotronAttribution.provider().equals("NEMOTRON");
            assert nemotronAttribution.model().equals(nemotronModel);

            selector.select("QWEN").join();
            assert selector.activeProviderName().equals("QWEN");
            System.out.println("R037 live selectable provider test passed; QWEN ttft="
                    + qwenResult.latency().timeToFirstTokenMillis() + "ms NEMOTRON ttft="
                    + nemotronResult.latency().timeToFirstTokenMillis() + "ms active=QWEN");
        }
    }

    private static LlmRequest request(UUID npc) {
        return new LlmRequest(UUID.randomUUID(), npc, UUID.randomUUID(), List.of(
                new ChatMessage("system", "You are Mara, an apprentice blacksmith. "
                        + "Reply naturally in one short sentence. Do not invent current events."),
                new ChatMessage("user", "Hello Mara. What is your name?")));
    }

    private static FrameworkConfig config(String endpoint, String model) {
        return new FrameworkConfig(endpoint, model, "", 1_500, 12_000,
                0.7, 180, 600, 6, 2_000, 300,
                true, 60_000, 15_000, "none");
    }
}
