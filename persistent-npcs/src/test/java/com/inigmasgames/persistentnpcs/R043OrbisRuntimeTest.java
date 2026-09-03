package com.inigmasgames.persistentnpcs;

import com.inigmasgames.persistentnpcs.ai.*;
import com.inigmasgames.persistentnpcs.cognition.GroundedIntent;
import com.inigmasgames.persistentnpcs.conversation.*;
import com.inigmasgames.persistentnpcs.llm.*;
import com.inigmasgames.persistentnpcs.orbis.*;
import com.inigmasgames.persistentnpcs.voice.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

public final class R043OrbisRuntimeTest {
    private R043OrbisRuntimeTest() { }
    public static void main(String[] args) throws Exception {
        exactlyOneSttAndOneCognitionBranch();
        authoritativeTextBypassesStt();
        providerTimeoutFailsWithoutBlockingCaller();
        legacyVoiceEntryPointsArePhysicallyRemoved();
        System.out.println("R043 Orbis runtime tests passed");
    }

    private static void exactlyOneSttAndOneCognitionBranch() throws Exception {
        MockStt stt = new MockStt(CompletableFuture.completedFuture(
                new SpeechTranscript("Hello Mara", 3, 12, "en")));
        MockCognition cognition = new MockCognition();
        OrbisDiagnostics diagnostics = new OrbisDiagnostics();
        try (OrbisTurnCoordinator coordinator = coordinator(stt, UUID.randomUUID(), cognition,
                diagnostics, 5_000)) {
            coordinator.accept(frame(UUID.randomUUID()));
            waitFor(() -> cognition.begins.get() == 1);
            assert stt.calls.get() == 1;
            assert diagnostics.latest().stream().filter(event ->
                    event.type() == OrbisEventType.STT_STARTED).count() == 1;
            assert diagnostics.latest().stream().filter(event ->
                    event.type() == OrbisEventType.RESPONSE_OWNER_SELECTED).count() == 1;
            assert diagnostics.latest().stream().anyMatch(event ->
                    event.type() == OrbisEventType.BRANCH_CREATED
                            && "QWEN".equals(event.facts().get("provider")));
        }
    }

    private static void authoritativeTextBypassesStt() throws Exception {
        MockStt stt = new MockStt(CompletableFuture.failedFuture(
                new AssertionError("text ingress must not invoke STT")));
        MockCognition cognition = new MockCognition();
        OrbisDiagnostics diagnostics = new OrbisDiagnostics();
        try (OrbisTurnCoordinator coordinator = coordinator(stt, UUID.randomUUID(), cognition,
                diagnostics, 5_000)) {
            long now = System.nanoTime();
            coordinator.accept(new TranscribedPlayerUtterance(UUID.randomUUID(), UUID.randomUUID(),
                    "Hello Mara", UUID.randomUUID(), 1, 2, 3, Instant.now(), now, now, now, now));
            waitFor(() -> cognition.begins.get() == 1);
            assert stt.calls.get() == 0;
            assert diagnostics.latest().stream().anyMatch(event ->
                    event.type() == OrbisEventType.STT_COMPLETED
                            && "AUTHORITATIVE_TEXT".equals(event.facts().get("source")));
        }
    }

    private static void providerTimeoutFailsWithoutBlockingCaller() throws Exception {
        OrbisDiagnostics diagnostics = new OrbisDiagnostics();
        try (OrbisTurnCoordinator coordinator = coordinator(new MockStt(new CompletableFuture<>()),
                UUID.randomUUID(), new MockCognition(), diagnostics, 100)) {
            long started = System.nanoTime();
            coordinator.accept(frame(UUID.randomUUID()));
            assert TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started) < 50;
            waitFor(() -> diagnostics.latest().stream().anyMatch(event ->
                    event.type() == OrbisEventType.TURN_CANCELLED
                            && "PROVIDER_TIMEOUT".equals(event.facts().get("reason"))));
        }
    }

    private static void legacyVoiceEntryPointsArePhysicallyRemoved() throws Exception {
        var source = java.nio.file.Path.of("src/main/java/com/inigmasgames/persistentnpcs");
        assert !java.nio.file.Files.exists(source.resolve("voice/HytaleVoicePipeline.java"));
        assert !java.nio.file.Files.exists(source.resolve("cognition/ResponseAuthorityRegistry.java"));
        assert !java.nio.file.Files.exists(source.resolve("llm/LlmRequestBudget.java"));
        String bridge = java.nio.file.Files.readString(
                source.resolve("hytale/HytaleConversationBridge.java"));
        assert bridge.contains("orbis.submitText(");
        assert !bridge.contains("converseWithVoice(");
    }

    private static OrbisTurnCoordinator coordinator(MockStt stt, UUID npc,
            MockCognition cognition, OrbisDiagnostics diagnostics, long timeout) {
        return new OrbisTurnCoordinator(stt, audience(npc), cognition, ignored -> true,
                () -> new PinnedLlmProvider("QWEN", "qwen-test", "local", new MockLlm()),
                diagnostics, 80, 100, timeout, ignored -> { });
    }

    private static OrbisAudienceGateway audience(UUID npc) {
        return utterance -> {
            EligibleNpcListener listener = new EligibleNpcListener(npc, "Mara", 3,
                    "nearby", "ahead", UtteranceRangeClass.ORDINARY, true, true, 1000);
            PlayerUtteranceEvent event = new PlayerUtteranceEvent(utterance.utteranceId(),
                    utterance.playerId(), utterance.transcript(), utterance.worldId(),
                    utterance.playerX(), utterance.playerY(), utterance.playerZ(),
                    utterance.timestamp(), Set.of(npc), PlayerSpeechIntent.DIRECT_ADDRESS,
                    List.of(listener), utterance.endpointMillis(), utterance.sttMillis(), 1);
            return CompletableFuture.completedFuture(new PlayerUtteranceAudienceService.Resolution(
                    event, List.of(listener), Map.of(npc, GroundedIntent.PROCESS_INFORMATION),
                    Map.of(), Map.of()));
        };
    }

    private static CapturedVoiceFrame frame(UUID player) {
        return new CapturedVoiceFrame(player, UUID.randomUUID(), 1, 2, 3,
                (short) 7, 100, new byte[] {1, 2, 3}, Instant.now(), System.nanoTime());
    }
    private static void waitFor(java.util.function.BooleanSupplier condition) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(4);
        while (!condition.getAsBoolean() && System.nanoTime() < deadline) Thread.sleep(10);
        assert condition.getAsBoolean() : "condition timed out";
    }

    private static final class MockCognition implements OrbisCognitionGateway {
        private final AtomicInteger begins = new AtomicInteger();
        @Override public CompletableFuture<ConversationOutcome> begin(BranchCognitionSnapshot s,
                PinnedLlmProvider p, ConversationLifecycleObserver o) {
            begins.incrementAndGet(); return new CompletableFuture<>();
        }
        @Override public CompletableFuture<Void> commit(BranchCognitionSnapshot s,
                ConversationOutcome o) { return CompletableFuture.completedFuture(null); }
        @Override public void failed(BranchCognitionSnapshot s, CancellationReason r, Throwable f) { }
    }
    private static final class MockStt implements SpeechToTextProvider {
        private final CompletableFuture<SpeechTranscript> result;
        private final AtomicInteger calls = new AtomicInteger();
        private MockStt(CompletableFuture<SpeechTranscript> result) { this.result = result; }
        @Override public CompletableFuture<SpeechTranscript> transcribe(UUID id, List<byte[]> f) {
            calls.incrementAndGet(); return result;
        }
        @Override public String providerId() { return "mock-stt"; }
        @Override public AiServiceKind serviceKind() { return AiServiceKind.SPEECH_TO_TEXT; }
        @Override public ProviderExecutionMode executionMode() { return ProviderExecutionMode.LOCAL; }
        @Override public AiProviderCapabilities capabilities() {
            return new AiProviderCapabilities(false, true, true, Set.of("opus"));
        }
    }
    private static final class MockLlm implements LlmProvider {
        @Override public CompletableFuture<LlmResult> generateResponse(LlmRequest request) {
            return CompletableFuture.failedFuture(new AssertionError("not called"));
        }
        @Override public CompletableFuture<LlmProviderStatus> checkStatus() {
            return CompletableFuture.completedFuture(new LlmProviderStatus(
                    "local", "qwen-test", true, true, true, "available"));
        }
        @Override public String description() { return "mock"; }
    }
}
