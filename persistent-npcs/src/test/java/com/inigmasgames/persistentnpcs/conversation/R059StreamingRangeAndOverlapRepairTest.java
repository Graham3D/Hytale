package com.inigmasgames.persistentnpcs.conversation;

import com.inigmasgames.persistentnpcs.ai.AiProvider;
import com.inigmasgames.persistentnpcs.ai.AiProviderCapabilities;
import com.inigmasgames.persistentnpcs.ai.AiResourceRequirements;
import com.inigmasgames.persistentnpcs.ai.AiServiceKind;
import com.inigmasgames.persistentnpcs.ai.ExecutionPlacement;
import com.inigmasgames.persistentnpcs.ai.ProviderExecutionMode;
import com.inigmasgames.persistentnpcs.diagnostics.RuntimeResourceMonitor;
import com.inigmasgames.persistentnpcs.orbis.OrbisResourceConfig;
import com.inigmasgames.persistentnpcs.orbis.OrbisResourceEvent;
import com.inigmasgames.persistentnpcs.orbis.OrbisResourceRequest;
import com.inigmasgames.persistentnpcs.orbis.OrbisResourceScheduler;
import com.inigmasgames.persistentnpcs.orbis.ResourcePolicy;
import com.inigmasgames.persistentnpcs.orbis.ResourcePriority;
import com.inigmasgames.persistentnpcs.orbis.ResourceWorkload;
import com.inigmasgames.persistentnpcs.profile.NpcProfile;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/** Deterministic regression coverage for failures observed in Mara's R058 trace. */
public final class R059StreamingRangeAndOverlapRepairTest {
    private R059StreamingRangeAndOverlapRepairTest() { }

    public static void main(String[] args) throws Exception {
        immutablePrefixSurvivesRejectedSuffix();
        streamedChunksAppendWithoutRecreatingInitialRequest();
        heldItemPerceptionRoutesGroundedWithoutReasoning();
        focusLossIsWiredToProviderCancellation();
        firstPhraseMayOverlapOnlyUnderMeasuredSafeHeadroom();
        System.out.println("R059 streaming, range cancellation, and safe overlap tests passed.");
    }

    private static void immutablePrefixSurvivesRejectedSuffix() {
        String committed = ConversationService.retainCommittedPrefix(
                List.of("Oh, show me?"), "I cannot verify that.");
        assert committed.equals("Oh, show me?") : committed;
        String unchanged = ConversationService.retainCommittedPrefix(
                List.of("Oh, a secret?", "What is it?"),
                "Oh, a secret? What is it? Tell me when you're ready.");
        assert unchanged.equals("Oh, a secret? What is it? Tell me when you're ready.");
    }

    private static void streamedChunksAppendWithoutRecreatingInitialRequest() throws Exception {
        String coordinator = Files.readString(Path.of(
                "src/main/java/com/inigmasgames/persistentnpcs/orbis/OrbisTurnCoordinator.java"));
        int method = coordinator.indexOf("void commitEarlyPhrase");
        int guard = coordinator.indexOf("if (index == 0) {", method);
        int request = coordinator.indexOf("OrbisSpeechRequest request", guard);
        int append = coordinator.indexOf("speechCoordinator.append(branch.responseId()", request);
        assert guard >= 0 && request > guard && append > request;
        assert coordinator.substring(guard, append).contains("} else {");
    }

    private static void heldItemPerceptionRoutesGroundedWithoutReasoning() {
        UUID id = UUID.randomUUID();
        NpcProfile mara = new NpcProfile(id, "Mara", "blacksmith", "direct",
                "A practical smith.", "Do good work.", "home", "forge",
                List.of("craft"), List.of("lies"), List.of(), List.of(), 5);
        CognitiveContextPlan plan = CognitiveContextRouter.route(mara,
                "Can you see what's in my hand?", DialogueMode.ORDINARY_CONVERSATION,
                null, null);
        assert plan.depth() == CognitiveDepth.CONTEXTUAL_CONVERSATION : plan;
        assert plan.detectedIntent().equals("QUERY_HELD_ITEM") : plan;
        AdaptiveReasoningDecision decision = AdaptiveReasoningRouter.route(plan,
                DialogueMode.ORDINARY_CONVERSATION, null, 0,
                "Can you see what's in my hand?");
        assert decision.policy() == AdaptiveReasoningPolicy.GROUNDED_DIALOGUE : decision;
        assert !decision.policy().reasoningEnabled();
    }

    private static void focusLossIsWiredToProviderCancellation() throws Exception {
        String attention = Files.readString(Path.of(
                "src/main/java/com/inigmasgames/persistentnpcs/social/NpcSocialAttentionService.java"));
        String coordinator = Files.readString(Path.of(
                "src/main/java/com/inigmasgames/persistentnpcs/orbis/OrbisTurnCoordinator.java"));
        String plugin = Files.readString(Path.of(
                "src/main/java/com/inigmasgames/persistentnpcs/PersistentNpcsPlugin.java"));
        assert attention.contains("focusLostConsumer.accept(current.id(), focused)");
        assert coordinator.contains("CancellationReason.CONVERSATION_RANGE_LOST")
                && coordinator.contains("cancelTurnOnCoordinator(turn");
        assert plugin.contains("attention.setFocusLostConsumer");
    }

    private static void firstPhraseMayOverlapOnlyUnderMeasuredSafeHeadroom() throws Exception {
        FakeProvider llm = new FakeProvider("NEMOTRON", AiServiceKind.LANGUAGE_MODEL,
                new AiResourceRequirements(ExecutionPlacement.LOCAL_PARTIAL_GPU,
                        "Ollama partial GPU", 3_200, 1_150, 1, true, true, 1_000,
                        1_150, 96, 64));
        FakeProvider tts = new FakeProvider("chatterbox-turbo-local-worker",
                AiServiceKind.TEXT_TO_SPEECH,
                new AiResourceRequirements(ExecutionPlacement.LOCAL_GPU,
                        "Chatterbox resident CUDA", 3_200, 2_900, 1, true, true, 500,
                        0, 192, 128));
        List<OrbisResourceEvent> events = java.util.Collections.synchronizedList(
                new ArrayList<>());
        try (OrbisResourceScheduler scheduler = scheduler(host(60, 2_400, false))) {
            OrbisResourceScheduler.Lease llmLease = scheduler.admit(request(llm,
                    ResourceWorkload.LLM, ResourcePriority.NORMAL), events::add)
                    .get(1, TimeUnit.SECONDS);
            OrbisResourceScheduler.Lease firstPhrase = scheduler.admit(request(tts,
                    ResourceWorkload.TTS, ResourcePriority.HIGH), events::add)
                    .get(1, TimeUnit.SECONDS);
            assert events.stream().anyMatch(event -> event.type()
                    == OrbisResourceEvent.Type.RESOURCE_ADMITTED
                    && "FIRST_PHRASE_SAFE_GPU_OVERLAP".equals(
                            event.facts().get("admissionMode")));
            firstPhrase.close();
            llmLease.close();
        }

        try (OrbisResourceScheduler scheduler = scheduler(host(95, 2_400, false))) {
            OrbisResourceScheduler.Lease llmLease = scheduler.admit(request(llm,
                    ResourceWorkload.LLM, ResourcePriority.NORMAL), ignored -> { })
                    .get(1, TimeUnit.SECONDS);
            CompletableFuture<OrbisResourceScheduler.Lease> blocked = scheduler.admit(
                    request(tts, ResourceWorkload.TTS, ResourcePriority.HIGH), ignored -> { });
            Thread.sleep(60);
            assert !blocked.isDone() : "High GPU pressure incorrectly admitted overlap";
            llmLease.close();
            blocked.get(1, TimeUnit.SECONDS).close();
        }
    }

    private static OrbisResourceScheduler scheduler(RuntimeResourceMonitor.Snapshot host) {
        OrbisResourceConfig base = OrbisResourceConfig.defaults();
        OrbisResourceConfig config = new OrbisResourceConfig(base.schemaVersion(),
                ResourcePolicy.BALANCED, Map.of(), 16, 2, 1, 1, 1, 1,
                92, 88, 512, 2_000, 512);
        return new OrbisResourceScheduler(config, () -> host, ignored -> { });
    }

    private static RuntimeResourceMonitor.Snapshot host(int gpu, long free,
            boolean framePressure) {
        return new RuntimeResourceMonitor.Snapshot(Instant.now(), 20, 10_000, 64_000,
                2_000, 16_000, 0, gpu, 12_000 - free, free, 12_000,
                "test-cpu", 24, "test-gpu", "", true, true, "",
                List.of(), List.of(), List.of(), new RuntimeResourceMonitor.FramePressure(
                        Instant.now(), framePressure ? 90 : 20,
                        framePressure ? 11 : 50, framePressure, 75,
                        -1, -1, "UNKNOWN", "deterministic-test"), "TEST");
    }

    private static OrbisResourceRequest request(FakeProvider provider,
            ResourceWorkload workload, ResourcePriority priority) {
        return new OrbisResourceRequest(UUID.randomUUID(), workload, priority,
                provider, true, 2_000);
    }

    private record FakeProvider(String providerId, AiServiceKind serviceKind,
            AiResourceRequirements resourceRequirements) implements AiProvider {
        @Override public ProviderExecutionMode executionMode() {
            return ProviderExecutionMode.LOCAL;
        }
        @Override public AiProviderCapabilities capabilities() {
            return new AiProviderCapabilities(true, true, true, Set.of("test"));
        }
    }
}
