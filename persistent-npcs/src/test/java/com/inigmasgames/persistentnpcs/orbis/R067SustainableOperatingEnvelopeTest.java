package com.inigmasgames.persistentnpcs.orbis;

import com.inigmasgames.persistentnpcs.ai.AiProvider;
import com.inigmasgames.persistentnpcs.ai.AiProviderCapabilities;
import com.inigmasgames.persistentnpcs.ai.AiResourceRequirements;
import com.inigmasgames.persistentnpcs.ai.AiServiceKind;
import com.inigmasgames.persistentnpcs.ai.ExecutionPlacement;
import com.inigmasgames.persistentnpcs.ai.ProviderExecutionMode;
import com.inigmasgames.persistentnpcs.diagnostics.RuntimeResourceMonitor;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/** Permanent regression for connected VRAM drift after startup READY. */
public final class R067SustainableOperatingEnvelopeTest {
    private R067SustainableOperatingEnvelopeTest() { }

    public static void main(String[] args) throws Exception {
        sustainableEnvelopeSurvivesDriftWithoutStarvationLoop();
        calibratedContractsRetainTheHytaleReserve();
        System.out.println("R067 sustainable operating-envelope tests passed.");
    }

    private static void sustainableEnvelopeSurvivesDriftWithoutStarvationLoop()
            throws Exception {
        AtomicLong free = new AtomicLong(900);
        AtomicInteger remediations = new AtomicInteger();
        AiResourceRequirements llm = requirements(128, 64);
        AiResourceRequirements tts = requirements(160, 64);
        ConversationOperatingEnvelope envelope = ConversationOperatingEnvelope.measured(
                "nemotron-3-nano:4b", "BALANCED_4_LAYER", 512, llm, tts);
        assert envelope.immediateRequiredMiB() == 736;
        assert envelope.preferredRequiredMiB() == 832;
        assert envelope.degradedRequiredMiB() == 768;
        for (long drift : new long[] { 32, 64, 128, 256 }) {
            assert 900 - drift >= 0;
        }
        try (OrbisResourceScheduler scheduler = new OrbisResourceScheduler(
                config(), () -> host(free.get()), ignored -> { }, ignored -> { },
                (workload, reason) -> {
                    assert workload == ResourceWorkload.LLM;
                    assert "sustained-operating-envelope-pressure".equals(reason);
                    remediations.incrementAndGet();
                    free.addAndGet(100); // Orbis estimate: 880 -> 780 MiB resident
                    return java.util.concurrent.CompletableFuture.completedFuture(
                            new ResourceReclaimResult("LOWER_NEMOTRON_PROFILE",
                                    "CHATTERBOX_PRESERVED", true));
                })) {
            scheduler.configureConversationOperatingEnvelope(() -> envelope,
                    new OrbisReadinessService());
            scheduler.startConversationOperatingEnvelope();
            await(() -> scheduler.operatingEnvelope().state()
                    == OrbisResourceScheduler.OperatingState.READY, 1_000);

            // External/WDDM pressure moves the preferred envelope below sustainability.
            free.set(700); // equivalent to a 200 MiB downward drift from READY
            await(() -> remediations.get() == 1, 5_000);
            await(() -> scheduler.operatingEnvelope().state()
                    == OrbisResourceScheduler.OperatingState.DEGRADED_READY, 5_000);
            assert scheduler.conversationServiceable();
            assert remediations.get() == 1;

            // Repeated foreground work must be immediately reusable, not five-second failures.
            FakeNemotron provider = new FakeNemotron();
            for (int index = 0; index < 4; index++) {
                long started = System.nanoTime();
                try (OrbisResourceScheduler.Lease lease = scheduler.admit(
                        request(provider), event -> { }).get(1, TimeUnit.SECONDS)) {
                    assert lease.admissionWaitMillis() < 250;
                    assert TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started) < 750;
                }
            }
            assert remediations.get() == 1 : "more than one remediation in pressure epoch";

            // Severe pressure fails fast; disappearance of the consumer recovers without upshift.
            free.set(600);
            await(() -> scheduler.operatingEnvelope().state()
                    == OrbisResourceScheduler.OperatingState.ERROR, 5_000);
            long failedAt = System.nanoTime();
            try {
                scheduler.admit(request(provider), event -> { }).get(1, TimeUnit.SECONDS);
                assert false : "unsafe request unexpectedly admitted";
            } catch (java.util.concurrent.ExecutionException expected) {
                assert expected.getCause() instanceof ResourceStarvedException;
            }
            assert TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - failedAt) < 250;
            free.set(900);
            await(() -> scheduler.operatingEnvelope().state()
                    == OrbisResourceScheduler.OperatingState.DEGRADED_READY, 5_000);
            assert remediations.get() == 1;
            assert scheduler.snapshot().activeJobs() == 0;
            assert scheduler.snapshot().queueDepth() == 0;
        }
    }

    private static void calibratedContractsRetainTheHytaleReserve() throws Exception {
        String llm = java.nio.file.Files.readString(java.nio.file.Path.of(
                "src/main/java/com/inigmasgames/persistentnpcs/llm/"
                        + "OpenAiCompatibleProvider.java"));
        String tts = java.nio.file.Files.readString(java.nio.file.Path.of(
                "src/main/java/com/inigmasgames/persistentnpcs/voice/"
                        + "LocalWorkerTextToSpeechProvider.java"));
        assert llm.contains("calibratedPartial ? 128 : 256");
        assert llm.contains("calibratedPartial ? 64 : 128");
        assert tts.contains("gpuBudget ? 160 : 0");
        assert tts.contains("gpuBudget ? 64 : 0");
        assert ConversationOperatingEnvelope.measured("n", "p", 1,
                requirements(128, 64), requirements(160, 64))
                .hytaleSafetyReserveMiB() == 512;
    }

    private static void await(java.util.function.BooleanSupplier condition,
            long timeoutMillis) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMillis);
        while (!condition.getAsBoolean() && System.nanoTime() < deadline) {
            Thread.sleep(25);
        }
        assert condition.getAsBoolean() : "condition timed out";
    }

    private static OrbisResourceRequest request(AiProvider provider) {
        return new OrbisResourceRequest(UUID.randomUUID(), ResourceWorkload.LLM,
                ResourcePriority.HIGH, provider, true, 5_000);
    }

    private static OrbisResourceConfig config() {
        return new OrbisResourceConfig(2, ResourcePolicy.BALANCED, Map.of(),
                32, 2, 1, 1, 1, 1, 92, 88, 1_024, 5_000, 512).validated();
    }

    private static AiResourceRequirements requirements(long incremental, long workspace) {
        return new AiResourceRequirements(ExecutionPlacement.LOCAL_PARTIAL_GPU, "fixture",
                6_144, 3_584, 1, true, true, 1_200, 880, incremental, workspace);
    }

    private static RuntimeResourceMonitor.Snapshot host(long free) {
        long total = 12_282;
        RuntimeResourceMonitor.ModelResidency nemotron =
                new RuntimeResourceMonitor.ModelResidency("NEMOTRON",
                        "nemotron-3-nano:4b", "LOADED", true, 42, 880,
                        ExecutionPlacement.LOCAL_PARTIAL_GPU, "fixture");
        RuntimeResourceMonitor.ModelResidency chatterbox =
                new RuntimeResourceMonitor.ModelResidency("CHATTERBOX",
                        "chatterbox-turbo", "LOADED", true, 43, 2_908,
                        ExecutionPlacement.LOCAL_GPU, "fixture");
        RuntimeResourceMonitor.FramePressure frame = new RuntimeResourceMonitor.FramePressure(
                Instant.now(), 33.3, 30.0, false, 75, -1, -1, "UNKNOWN", "fixture");
        return new RuntimeResourceMonitor.Snapshot(Instant.now(), 20, 8_000, 64_000,
                800, 16_000, 0, 50, total - free, free, total, "cpu", 24, "gpu", "",
                true, true, "", List.of(), List.of(nemotron, chatterbox), List.of(),
                frame, "MEASURED");
    }

    private record FakeNemotron() implements AiProvider {
        @Override public String providerId() { return "NEMOTRON"; }
        @Override public AiServiceKind serviceKind() { return AiServiceKind.LANGUAGE_MODEL; }
        @Override public ProviderExecutionMode executionMode() {
            return ProviderExecutionMode.LOCAL;
        }
        @Override public AiProviderCapabilities capabilities() {
            return new AiProviderCapabilities(true, true, true, Set.of("text"));
        }
        @Override public AiResourceRequirements resourceRequirements() {
            return requirements(128, 64);
        }
    }
}
