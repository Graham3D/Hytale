package com.inigmasgames.persistentnpcs.orbis;

import com.inigmasgames.persistentnpcs.ai.AiProvider;
import com.inigmasgames.persistentnpcs.ai.AiProviderCapabilities;
import com.inigmasgames.persistentnpcs.ai.AiResourceRequirements;
import com.inigmasgames.persistentnpcs.ai.AiServiceKind;
import com.inigmasgames.persistentnpcs.ai.ExecutionPlacement;
import com.inigmasgames.persistentnpcs.ai.ProviderExecutionMode;
import com.inigmasgames.persistentnpcs.diagnostics.RuntimeResourceMonitor;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/** Deterministic Phase 5 admission, priority, cancellation, and CPU-only tests. */
public final class R047OrbisResourceSchedulerTest {
    private R047OrbisResourceSchedulerTest() { }

    public static void main(String[] args) throws Exception {
        foregroundAndFirstTtsWinContention();
        cancellationImmediatelyRemovesPendingReservation();
        cpuOnlyFailsClosedWithoutNvidiaClasses();
        remotePlacementAndProviderPinRemainExplicit();
        unknownTelemetryNeverPreventsStartup();
        sourceRetiresOrbisLlmSemaphoreCompetition();
        System.out.println("R047 Orbis resource scheduler tests passed.");
    }

    private static void foregroundAndFirstTtsWinContention() throws Exception {
        FakeProvider gpu = new FakeProvider("shared-gpu", AiServiceKind.TEXT_TO_SPEECH,
                ExecutionPlacement.LOCAL_GPU, 1);
        List<OrbisResourceEvent> events = java.util.Collections.synchronizedList(
                new ArrayList<>());
        try (OrbisResourceScheduler scheduler = scheduler(ResourcePolicy.BALANCED)) {
            OrbisResourceScheduler.Lease blocker = scheduler.admit(request(gpu,
                    ResourceWorkload.TTS, ResourcePriority.HIGH, true), events::add)
                    .get(1, TimeUnit.SECONDS);
            CompletableFuture<OrbisResourceScheduler.Lease> background = scheduler.admit(
                    request(gpu, ResourceWorkload.BACKGROUND_COGNITION,
                            ResourcePriority.LOW, false), events::add);
            CompletableFuture<OrbisResourceScheduler.Lease> llm = scheduler.admit(
                    request(gpu, ResourceWorkload.LLM, ResourcePriority.NORMAL, true),
                    events::add);
            CompletableFuture<OrbisResourceScheduler.Lease> firstTts = scheduler.admit(
                    request(gpu, ResourceWorkload.TTS, ResourcePriority.HIGH, true),
                    events::add);
            waitFor(() -> scheduler.snapshot().queueDepth() == 3);
            blocker.close();
            OrbisResourceScheduler.Lease ttsLease = firstTts.get(1, TimeUnit.SECONDS);
            assert !llm.isDone() : "LLM jumped ahead of first audible TTS";
            assert !background.isDone() : "background inference jumped ahead of foreground";
            ttsLease.close();
            OrbisResourceScheduler.Lease llmLease = llm.get(1, TimeUnit.SECONDS);
            assert !background.isDone();
            llmLease.close();
            background.get(1, TimeUnit.SECONDS).close();
            assert events.stream().anyMatch(value -> value.type()
                    == OrbisResourceEvent.Type.RESOURCE_DEFERRED);
        }
    }

    private static void cancellationImmediatelyRemovesPendingReservation() throws Exception {
        FakeProvider gpu = new FakeProvider("cancel-gpu", AiServiceKind.LANGUAGE_MODEL,
                ExecutionPlacement.LOCAL_GPU, 1);
        try (OrbisResourceScheduler scheduler = scheduler(ResourcePolicy.BALANCED)) {
            OrbisResourceScheduler.Lease blocker = scheduler.admit(request(gpu,
                    ResourceWorkload.LLM, ResourcePriority.NORMAL, true), ignored -> { })
                    .get(1, TimeUnit.SECONDS);
            OrbisResourceRequest queuedRequest = request(gpu, ResourceWorkload.LLM,
                    ResourcePriority.NORMAL, true);
            CompletableFuture<OrbisResourceScheduler.Lease> queued = scheduler.admit(
                    queuedRequest, ignored -> { });
            waitFor(() -> scheduler.snapshot().queueDepth() == 1);
            scheduler.cancel(queuedRequest.requestId(), "USER_BARGE_IN");
            waitFor(() -> scheduler.snapshot().queueDepth() == 0);
            assert queued.isCancelled() || queued.isCompletedExceptionally();
            blocker.close();
        }
    }

    private static void cpuOnlyFailsClosedWithoutNvidiaClasses() throws Exception {
        String schedulerSource = java.nio.file.Files.readString(java.nio.file.Path.of(
                "src/main/java/com/inigmasgames/persistentnpcs/orbis/OrbisResourceScheduler.java"));
        assert !schedulerSource.contains("com.nvidia");
        assert !schedulerSource.contains("cuda.");
        FakeProvider gpu = new FakeProvider("gpu-only", AiServiceKind.TEXT_TO_SPEECH,
                ExecutionPlacement.LOCAL_GPU, 1);
        try (OrbisResourceScheduler scheduler = scheduler(ResourcePolicy.CPU_ONLY)) {
            boolean failed = false;
            try {
                scheduler.admit(request(gpu, ResourceWorkload.TTS, ResourcePriority.HIGH,
                        true), ignored -> { }).join();
            } catch (RuntimeException expected) { failed = true; }
            assert failed : "CPU_ONLY silently admitted a GPU-only provider";
        }
    }

    private static void remotePlacementAndProviderPinRemainExplicit() throws Exception {
        FakeProvider nemotron = new FakeProvider("NEMOTRON", AiServiceKind.LANGUAGE_MODEL,
                ExecutionPlacement.REMOTE_LAN, 2);
        try (OrbisResourceScheduler scheduler = scheduler(ResourcePolicy.REMOTE_AI)) {
            OrbisResourceScheduler.Lease lease = scheduler.admit(request(nemotron,
                    ResourceWorkload.LLM, ResourcePriority.NORMAL, true), ignored -> { })
                    .get(1, TimeUnit.SECONDS);
            assert lease.placement() == ExecutionPlacement.REMOTE_LAN;
            assert nemotron.providerId().equals("NEMOTRON");
            lease.close();
        }
    }

    private static void unknownTelemetryNeverPreventsStartup() throws Exception {
        FakeProvider cpu = new FakeProvider("moonshine", AiServiceKind.SPEECH_TO_TEXT,
                ExecutionPlacement.LOCAL_CPU, 1);
        try (OrbisResourceScheduler scheduler = new OrbisResourceScheduler(
                OrbisResourceConfig.defaults(), () -> null, ignored -> { })) {
            scheduler.admit(request(cpu, ResourceWorkload.STT, ResourcePriority.HIGH, true),
                    ignored -> { }).get(1, TimeUnit.SECONDS).close();
        }
    }

    private static void sourceRetiresOrbisLlmSemaphoreCompetition() throws Exception {
        String conversation = java.nio.file.Files.readString(java.nio.file.Path.of(
                "src/main/java/com/inigmasgames/persistentnpcs/conversation/ConversationService.java"));
        String plugin = java.nio.file.Files.readString(java.nio.file.Path.of(
                "src/main/java/com/inigmasgames/persistentnpcs/PersistentNpcsPlugin.java"));
        assert conversation.contains("rateLimiter.acquire(session.playerId())");
        assert !java.nio.file.Files.exists(java.nio.file.Path.of(
                "src/main/java/com/inigmasgames/persistentnpcs/llm/LlmRequestBudget.java"));
        assert plugin.contains("resourceScheduler = new OrbisResourceScheduler");
        assert plugin.contains("ResourceWorkload.BACKGROUND_COGNITION")
                || java.nio.file.Files.readString(java.nio.file.Path.of(
                        "src/main/java/com/inigmasgames/persistentnpcs/scene/NpcSceneRunner.java"))
                        .contains("ResourceWorkload.BACKGROUND_COGNITION");
    }

    private static OrbisResourceScheduler scheduler(ResourcePolicy policy) {
        OrbisResourceConfig base = OrbisResourceConfig.defaults();
        OrbisResourceConfig config = new OrbisResourceConfig(base.schemaVersion(), policy,
                Map.of(), base.maximumQueuedRequests(), 2, 2, 2, 1, 1,
                base.gpuPressureUtilizationPercent(), base.vramPressureUsedPercent(),
                base.minimumFreeRamMiB(), 2_000);
        RuntimeResourceMonitor.Snapshot host = new RuntimeResourceMonitor.Snapshot(
                Instant.now(), 20, 4_000, 32_000, 500, 4_000, 0,
                20, 1_000, 11_000, 12_000, "test-cpu", 16, "test-gpu", "",
                true, true, "");
        return new OrbisResourceScheduler(config, () -> host, ignored -> { });
    }

    private static OrbisResourceRequest request(FakeProvider provider,
            ResourceWorkload workload, ResourcePriority priority, boolean foreground) {
        return new OrbisResourceRequest(UUID.randomUUID(), workload, priority, provider,
                foreground, 2_000);
    }

    private static void waitFor(java.util.function.BooleanSupplier condition) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
        while (!condition.getAsBoolean() && System.nanoTime() < deadline) {
            Thread.sleep(5);
        }
        assert condition.getAsBoolean();
    }

    private record FakeProvider(String providerId, AiServiceKind serviceKind,
            ExecutionPlacement placement, int concurrencyLimit) implements AiProvider {
        @Override public ProviderExecutionMode executionMode() {
            return placement.remote() ? ProviderExecutionMode.REMOTE : ProviderExecutionMode.LOCAL;
        }
        @Override public AiProviderCapabilities capabilities() {
            return new AiProviderCapabilities(true, true, true, Set.of("test"));
        }
        @Override public AiResourceRequirements resourceRequirements() {
            return new AiResourceRequirements(placement, providerId + " backend", 128,
                    placement.usesLocalGpu() ? 256 : 0, concurrencyLimit,
                    true, true, 100);
        }
    }
}
