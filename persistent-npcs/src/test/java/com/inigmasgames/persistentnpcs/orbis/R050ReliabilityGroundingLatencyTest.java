package com.inigmasgames.persistentnpcs.orbis;

import com.inigmasgames.persistentnpcs.ai.AiProvider;
import com.inigmasgames.persistentnpcs.ai.AiProviderCapabilities;
import com.inigmasgames.persistentnpcs.ai.AiResourceRequirements;
import com.inigmasgames.persistentnpcs.ai.AiServiceKind;
import com.inigmasgames.persistentnpcs.ai.ExecutionPlacement;
import com.inigmasgames.persistentnpcs.ai.ProviderExecutionMode;
import com.inigmasgames.persistentnpcs.cognition.NpcGroundingClaimValidator;
import com.inigmasgames.persistentnpcs.diagnostics.RuntimeResourceMonitor;
import com.inigmasgames.persistentnpcs.voice.SpeechPhraseChunker;
import com.inigmasgames.persistentnpcs.voice.VocalEmotion;
import com.inigmasgames.persistentnpcs.voice.VocalState;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/** Trace-derived R050 grounding, first-audio, and pressure admission regressions. */
public final class R050ReliabilityGroundingLatencyTest {
    private R050ReliabilityGroundingLatencyTest() { }

    public static void main(String[] args) throws Exception {
        unrelatedRelationshipEvidenceCannotAuthorizeWorldClaims();
        firstCommittedSentenceBecomesTheFirstTtsChunk();
        pressureIsReevaluatedAndForegroundLlmIsBounded();
        duplicateIngressGuardRemainsWiredToOrbisEvents();
        System.out.println("R050 reliability, grounding, and latency tests passed.");
    }

    private static void unrelatedRelationshipEvidenceCannotAuthorizeWorldClaims() {
        NpcGroundingClaimValidator validator = new NpcGroundingClaimValidator();
        String invented = "I just got back from the old mill. I saw a fox carrying a gear.";
        var rejected = validator.validate(invented, List.of("RELATIONSHIP:mara:lycander"));
        assert rejected.stream().anyMatch(value -> !value.valid())
                : "relationship evidence authorized an invented world event";
        var remembered = validator.validate(invented, List.of("MEMORY:old-mill-fox"));
        assert remembered.stream().allMatch(value -> value.valid())
                : "type-compatible memory evidence was rejected";
        var social = validator.validate("I'm doing all right. How about you?",
                List.of("RELATIONSHIP:mara:player"));
        assert social.stream().allMatch(value -> value.valid());
    }

    private static void firstCommittedSentenceBecomesTheFirstTtsChunk() {
        String dialogue = "I'm fine, thanks! Just keeping busy at the forge. How about you?";
        List<String> chunks = new ArrayList<>();
        SpeechPhraseChunker chunker = SpeechPhraseChunker.exact(
                (index, phrase, state) -> chunks.add(phrase));
        chunker.complete(dialogue, VocalState.forEmotion(VocalEmotion.CALM));
        assert chunks.size() == 3 : chunks;
        assert chunks.getFirst().equals("I'm fine, thanks!") : chunks;
        assert String.join(" ", chunks).equals(dialogue)
                : "chunking changed canonical wording";
    }

    private static void pressureIsReevaluatedAndForegroundLlmIsBounded()
            throws Exception {
        AtomicReference<RuntimeResourceMonitor.Snapshot> host = new AtomicReference<>(
                snapshot(98, 11_000));
        List<OrbisResourceEvent> events = java.util.Collections.synchronizedList(
                new ArrayList<>());
        FakeLlm provider = new FakeLlm();
        try (OrbisResourceScheduler scheduler = new OrbisResourceScheduler(
                config(500), host::get, ignored -> { })) {
            long started = System.nanoTime();
            var pending = scheduler.admit(request(provider, 10_000), events::add);
            waitFor(() -> events.stream().anyMatch(event -> event.type()
                    == OrbisResourceEvent.Type.RESOURCE_DEFERRED));
            host.set(snapshot(20, 2_000));
            try (OrbisResourceScheduler.Lease lease = pending.get(1, TimeUnit.SECONDS)) {
                long waitMillis = TimeUnit.NANOSECONDS.toMillis(
                        System.nanoTime() - started);
                assert waitMillis < 900 : "pressure was not periodically re-evaluated";
            }
            OrbisResourceEvent deferred = events.stream().filter(event -> event.type()
                    == OrbisResourceEvent.Type.RESOURCE_DEFERRED).findFirst().orElseThrow();
            assert deferred.facts().containsKey("sampledPressure");
            assert deferred.facts().containsKey("pressureThreshold");
            assert deferred.facts().containsKey("queueDepth");
            assert deferred.facts().containsKey("nextReevaluationMs");
        }

        host.set(snapshot(99, 11_500));
        events.clear();
        try (OrbisResourceScheduler scheduler = new OrbisResourceScheduler(
                config(300), host::get, ignored -> { })) {
            boolean resourceStarved = false;
            try {
                scheduler.admit(request(provider, 10_000), events::add)
                        .get(1, TimeUnit.SECONDS);
            } catch (ExecutionException expected) {
                resourceStarved = expected.getCause() instanceof ResourceStarvedException;
            }
            assert resourceStarved : "foreground LLM did not fail as RESOURCE_STARVED";
            assert events.stream().anyMatch(event -> event.type()
                    == OrbisResourceEvent.Type.RESOURCE_TIMEOUT
                    && "RESOURCE_STARVED".equals(event.facts().get("terminalReason")));
        }
    }

    private static void duplicateIngressGuardRemainsWiredToOrbisEvents() throws Exception {
        String source = java.nio.file.Files.readString(java.nio.file.Path.of(
                "src/main/java/com/inigmasgames/persistentnpcs/orbis/OrbisTurnCoordinator.java"));
        assert source.contains("DUPLICATE_UTTERANCE_SUPPRESSED");
        assert source.contains("recentVoiceTranscripts");
        assert source.contains("ResourcePriority.HIGH")
                : "foreground cognition was not promoted";
    }

    private static OrbisResourceConfig config(long admissionTimeoutMillis) {
        OrbisResourceConfig base = OrbisResourceConfig.defaults();
        return new OrbisResourceConfig(base.schemaVersion(), ResourcePolicy.BALANCED,
                Map.of(), base.maximumQueuedRequests(), 2, 1, 1, 1, 1,
                92, 88, 512, admissionTimeoutMillis).validated();
    }

    private static OrbisResourceRequest request(FakeLlm provider, long timeoutMillis) {
        return new OrbisResourceRequest(UUID.randomUUID(), ResourceWorkload.LLM,
                ResourcePriority.HIGH, provider, true, timeoutMillis);
    }

    private static RuntimeResourceMonitor.Snapshot snapshot(int gpu, long vramUsed) {
        return new RuntimeResourceMonitor.Snapshot(Instant.now(), 25, 4_000, 32_000,
                500, 4_000, 0, gpu, vramUsed, 12_000 - vramUsed, 12_000,
                "test-cpu", 16, "test-gpu", "", true, true, "");
    }

    private static void waitFor(java.util.function.BooleanSupplier condition)
            throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
        while (!condition.getAsBoolean() && System.nanoTime() < deadline) Thread.sleep(5);
        assert condition.getAsBoolean() : "condition timed out";
    }

    private record FakeLlm() implements AiProvider {
        @Override public String providerId() { return "nemotron-3-nano:4b"; }
        @Override public AiServiceKind serviceKind() {
            return AiServiceKind.LANGUAGE_MODEL;
        }
        @Override public ProviderExecutionMode executionMode() {
            return ProviderExecutionMode.LOCAL;
        }
        @Override public AiProviderCapabilities capabilities() {
            return new AiProviderCapabilities(true, true, true, Set.of("json"));
        }
        @Override public AiResourceRequirements resourceRequirements() {
            return new AiResourceRequirements(ExecutionPlacement.LOCAL_GPU,
                    "Ollama", 256, 2_048, 1, true, true, 500);
        }
    }
}
