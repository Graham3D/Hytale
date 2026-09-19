package com.inigmasgames.persistentnpcs.orbis;

import com.inigmasgames.persistentnpcs.ai.AiProvider;
import com.inigmasgames.persistentnpcs.ai.AiProviderCapabilities;
import com.inigmasgames.persistentnpcs.ai.AiResourceRequirements;
import com.inigmasgames.persistentnpcs.ai.AiServiceKind;
import com.inigmasgames.persistentnpcs.ai.ExecutionPlacement;
import com.inigmasgames.persistentnpcs.ai.ProviderExecutionMode;
import com.inigmasgames.persistentnpcs.diagnostics.RuntimeResourceMonitor;
import com.inigmasgames.persistentnpcs.voice.SpeechTranscript;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/** Deterministic regression coverage for the Moonshine/Nemotron/Chatterbox repair. */
public final class R054CascadeResourceRepairTest {
    private R054CascadeResourceRepairTest() { }

    public static void main(String[] args) throws Exception {
        speechTranscriptPreservesActualEngineTruth();
        warmResidentInferenceUsesIncrementalHeadroom();
        unloadedProviderIncludesResidentFootprint();
        reclaimUsesExplicitProviderLifecycle();
        workerSourceUsesMoonshineForCompletedPttAudio();
        System.out.println("R054 cascade and resource repair tests passed.");
    }

    private static void speechTranscriptPreservesActualEngineTruth() {
        SpeechTranscript transcript = new SpeechTranscript("Hello", 9, 206, "en",
                "AUTO", "MOONSHINE", false, "", "cpu",
                "moonshine-quantized", 42);
        assert transcript.requestedEngine().equals("AUTO");
        assert transcript.actualEngine().equals("MOONSHINE");
        assert !transcript.fallback();
        assert transcript.workerPid() == 42;
    }

    private static void warmResidentInferenceUsesIncrementalHeadroom() throws Exception {
        FakeNemotron provider = new FakeNemotron();
        RuntimeResourceMonitor.Snapshot host = host(true, 1_000);
        try (OrbisResourceScheduler scheduler = new OrbisResourceScheduler(
                OrbisResourceConfig.defaults(), () -> host, ignored -> { })) {
            OrbisResourceScheduler.Lease lease = scheduler.admit(request(provider, 800),
                    ignored -> { }).get(1, TimeUnit.SECONDS);
            lease.close();
        }
    }

    private static void unloadedProviderIncludesResidentFootprint() throws Exception {
        FakeNemotron provider = new FakeNemotron();
        RuntimeResourceMonitor.Snapshot host = host(false, 1_000);
        try (OrbisResourceScheduler scheduler = new OrbisResourceScheduler(
                shortTimeout(), () -> host, ignored -> { })) {
            boolean starved = false;
            try {
                scheduler.admit(request(provider, 160), ignored -> { })
                        .get(1, TimeUnit.SECONDS);
            } catch (ExecutionException expected) {
                starved = expected.getCause() instanceof ResourceStarvedException;
            }
            assert starved;
        }
    }

    private static void reclaimUsesExplicitProviderLifecycle() throws Exception {
        FakeNemotron provider = new FakeNemotron();
        RuntimeResourceMonitor.Snapshot host = host(false, 128);
        AtomicInteger calls = new AtomicInteger();
        List<OrbisResourceEvent> events = java.util.Collections.synchronizedList(
                new ArrayList<>());
        ResourceReclaimer reclaimer = (workload, reason) -> {
            calls.incrementAndGet();
            return java.util.concurrent.CompletableFuture.completedFuture(
                    new ResourceReclaimResult("OLLAMA_KEEP_ALIVE_ZERO",
                            "MODEL_UNLOADED", true));
        };
        try (OrbisResourceScheduler scheduler = new OrbisResourceScheduler(shortTimeout(),
                () -> host, ignored -> { }, ignored -> { }, reclaimer)) {
            try {
                scheduler.admit(request(provider, 160), events::add)
                        .get(1, TimeUnit.SECONDS);
            } catch (ExecutionException expected) {
                assert expected.getCause() instanceof ResourceStarvedException;
            }
        }
        assert calls.get() == 1;
        OrbisResourceEvent reclaim = events.stream().filter(event -> event.type()
                == OrbisResourceEvent.Type.RESOURCE_RECLAIM_ATTEMPT).findFirst().orElseThrow();
        assert reclaim.facts().get("reclaimActionAttempted")
                .equals("PROVIDER_LIFECYCLE_ADAPTER");
        assert events.stream().noneMatch(event -> event.facts().toString()
                .contains("MODEL_EVICTION_NOT_OWNED_BY_ORBIS"));
    }

    private static void workerSourceUsesMoonshineForCompletedPttAudio() throws Exception {
        String worker = Files.readString(Path.of(
                "src/main/resources/tools/immersive_voice_worker.py"));
        assert worker.contains("actual_engine = \"MOONSHINE\"");
        assert worker.contains("stream.add_audio(audio16.tolist(), 16000)");
        assert worker.contains("Moonshine inference failed; using Faster-Whisper");
        assert worker.contains("\"cudaPeakAllocatedMb\"");
        assert worker.contains("def unload_tts");
    }

    private static OrbisResourceRequest request(FakeNemotron provider, long timeout) {
        return new OrbisResourceRequest(UUID.randomUUID(), ResourceWorkload.LLM,
                ResourcePriority.HIGH, provider, true, timeout);
    }

    private static OrbisResourceConfig shortTimeout() {
        OrbisResourceConfig base = OrbisResourceConfig.defaults();
        return new OrbisResourceConfig(base.schemaVersion(), base.policy(), Map.of(),
                32, 2, 1, 1, 1, 1, 92, 88, 1_024, 160, 512).validated();
    }

    private static RuntimeResourceMonitor.Snapshot host(boolean resident, long free) {
        RuntimeResourceMonitor.ModelResidency model = new RuntimeResourceMonitor.ModelResidency(
                "NEMOTRON", "nemotron-3-nano:4b", resident ? "LOADED" : "UNLOADED",
                true, resident ? 8_896 : -1, 1_280,
                resident ? ExecutionPlacement.LOCAL_PARTIAL_GPU : ExecutionPlacement.UNKNOWN,
                "deterministic test");
        RuntimeResourceMonitor.FramePressure frame = new RuntimeResourceMonitor.FramePressure(
                Instant.now(), 33.3, 30.0, false, 75.0,
                -1, -1, "UNKNOWN", "test");
        long total = 12_282;
        return new RuntimeResourceMonitor.Snapshot(Instant.now(), 20,
                8_000, 32_000, 512, 4_096, 0, 50,
                total - free, free, total, "cpu", 16, "gpu", "", true, true, "",
                List.of(), List.of(model), List.of(), frame, "UNKNOWN");
    }

    private record FakeNemotron() implements AiProvider {
        @Override public String providerId() { return "NEMOTRON"; }
        @Override public AiServiceKind serviceKind() { return AiServiceKind.LANGUAGE_MODEL; }
        @Override public ProviderExecutionMode executionMode() {
            return ProviderExecutionMode.LOCAL;
        }
        @Override public AiProviderCapabilities capabilities() {
            return new AiProviderCapabilities(true, true, true, Set.of("json"));
        }
        @Override public AiResourceRequirements resourceRequirements() {
            return new AiResourceRequirements(ExecutionPlacement.LOCAL_PARTIAL_GPU,
                    "nemotron-3-nano:4b", 6_144, 3_584, 1, true, true, 1_200,
                    1_280, 256, 128);
        }
    }
}
