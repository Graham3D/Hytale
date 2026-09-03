package com.inigmasgames.persistentnpcs.orbis;

import com.inigmasgames.persistentnpcs.ai.AiProvider;
import com.inigmasgames.persistentnpcs.ai.AiProviderCapabilities;
import com.inigmasgames.persistentnpcs.ai.AiProviderDefinition;
import com.inigmasgames.persistentnpcs.ai.AiResourceRequirements;
import com.inigmasgames.persistentnpcs.ai.AiServiceKind;
import com.inigmasgames.persistentnpcs.ai.ExecutionPlacement;
import com.inigmasgames.persistentnpcs.ai.LlmProviderCatalog;
import com.inigmasgames.persistentnpcs.ai.LlmProviderCatalogRepository;
import com.inigmasgames.persistentnpcs.ai.ProviderExecutionMode;
import com.inigmasgames.persistentnpcs.diagnostics.RuntimeResourceMonitor;
import com.inigmasgames.persistentnpcs.json.JsonFiles;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/** Permanent target-hardware regression for the R062 connected VRAM starvation. */
public final class R063SteadyStateResourceCalibrationTest {
    private R063SteadyStateResourceCalibrationTest() { }

    public static void main(String[] args) throws Exception {
        failedWarmProviderTurnIsBoundedAndNextTurnRemainsUsable();
        boundedLifecycleReclaimCanAdmitWithoutReducingReserve();
        priorBalancedDefaultMigratesToMeasuredFourLayerProfile();
        startupSteadyStateProofRemainsWired();
        System.out.println("R063 steady-state resource calibration tests passed.");
    }

    private static void failedWarmProviderTurnIsBoundedAndNextTurnRemainsUsable()
            throws Exception {
        AtomicReference<RuntimeResourceMonitor.Snapshot> host =
                new AtomicReference<>(host(747));
        List<OrbisResourceEvent> events = java.util.Collections.synchronizedList(
                new ArrayList<>());
        FakeWarmNemotron provider = new FakeWarmNemotron();
        try (OrbisResourceScheduler scheduler = new OrbisResourceScheduler(
                config(460), host::get, ignored -> { }, ignored -> { },
                (workload, reason) -> java.util.concurrent.CompletableFuture.completedFuture(
                        new ResourceReclaimResult("NO_INACTIVE_RUNNER", "UNLOADED=0", false)))) {
            boolean starved = false;
            try {
                scheduler.admit(request(provider), events::add).get(2, TimeUnit.SECONDS);
            } catch (ExecutionException expected) {
                starved = expected.getCause() instanceof ResourceStarvedException;
            }
            assert starved : "747 MiB incorrectly bypassed the 896 MiB safe requirement";
            OrbisResourceEvent deferred = events.stream().filter(value -> value.type()
                    == OrbisResourceEvent.Type.RESOURCE_DEFERRED).findFirst().orElseThrow();
            assert "896".equals(deferred.facts().get("requiredHeadroomMiB"));
            assert "747".equals(deferred.facts().get("availableHeadroomMiB"));
            assert "512".equals(deferred.facts().get("hytaleGpuSafetyReserveMiB"));
            assert "BLOCKED_BECAUSE_VRAM".equals(
                    deferred.facts().get("schedulerDecision"));
            assert events.stream().anyMatch(value -> value.type()
                    == OrbisResourceEvent.Type.RESOURCE_RECHECK);
            assert scheduler.snapshot().activeJobs() == 0;

            // The terminal failure releases all scheduler state. A later safe turn is usable.
            host.set(host(1_041));
            try (OrbisResourceScheduler.Lease lease = scheduler.admit(request(provider),
                    events::add).get(1, TimeUnit.SECONDS)) {
                assert lease.admissionWaitMillis() < 200;
            }
        }
    }

    private static void boundedLifecycleReclaimCanAdmitWithoutReducingReserve()
            throws Exception {
        AtomicReference<RuntimeResourceMonitor.Snapshot> host =
                new AtomicReference<>(host(747));
        List<OrbisResourceEvent> events = java.util.Collections.synchronizedList(
                new ArrayList<>());
        try (OrbisResourceScheduler scheduler = new OrbisResourceScheduler(
                config(1_000), host::get, ignored -> { }, ignored -> { },
                (workload, reason) -> {
                    assert workload == ResourceWorkload.LLM;
                    assert "vram-headroom-pressure".equals(reason);
                    host.set(host(1_041));
                    return java.util.concurrent.CompletableFuture.completedFuture(
                            new ResourceReclaimResult("IDLE_PROVIDER_UNLOAD",
                                    "RECLAIMED_MEASURED_HEADROOM", true));
                })) {
            try (OrbisResourceScheduler.Lease lease = scheduler.admit(
                    request(new FakeWarmNemotron()), events::add)
                    .get(1, TimeUnit.SECONDS)) {
                assert lease.admissionWaitMillis() < 500;
            }
            assert events.stream().anyMatch(value -> value.type()
                    == OrbisResourceEvent.Type.RESOURCE_RECLAIM_ATTEMPT);
            OrbisResourceEvent admitted = events.stream().filter(value -> value.type()
                    == OrbisResourceEvent.Type.RESOURCE_ADMITTED).findFirst().orElseThrow();
            assert "512".equals(admitted.facts().get("hytaleGpuSafetyReserveMiB"));
        }
    }

    private static void priorBalancedDefaultMigratesToMeasuredFourLayerProfile()
            throws Exception {
        Path root = Files.createTempDirectory("r063-llm-catalog-");
        try {
            AiProviderDefinition current = definition(null);
            LinkedHashMap<String, AiProviderDefinition> providers = new LinkedHashMap<>();
            providers.put(LlmProviderCatalog.NEMOTRON, definition(12));
            providers.put(LlmProviderCatalog.QWEN, new AiProviderDefinition(
                    "OPENAI_COMPATIBLE", LlmProviderCatalog.OLLAMA_CHAT_ENDPOINT,
                    LlmProviderCatalog.QWEN_MODEL, 12_000, 2, "LOCAL", false, null,
                    "REQUIRED", null, null));
            JsonFiles.writeAtomic(root.resolve("llm-providers.json"),
                    new LlmProviderCatalog(LlmProviderCatalog.NEMOTRON, providers));
            LlmProviderCatalog loaded = new LlmProviderCatalogRepository(root).load(current);
            assert loaded.providers().get(LlmProviderCatalog.NEMOTRON).ollamaGpuLayers()
                    == LlmProviderCatalog.NEMOTRON_BALANCED_GPU_LAYERS;
            assert loaded.providers().get(LlmProviderCatalog.NEMOTRON).ollamaGpuLayers() == 4;
            assert loaded.activeProvider().equals(LlmProviderCatalog.NEMOTRON);
        } finally {
            try (var paths = Files.walk(root)) {
                for (Path path : paths.sorted(java.util.Comparator.reverseOrder()).toList()) {
                    Files.deleteIfExists(path);
                }
            }
        }
    }

    private static void startupSteadyStateProofRemainsWired() throws Exception {
        String coordinator = Files.readString(Path.of(
                "src/main/java/com/inigmasgames/persistentnpcs/orbis/OrbisStartupCoordinator.java"));
        String router = Files.readString(Path.of(
                "src/main/java/com/inigmasgames/persistentnpcs/ai/AiServiceRouter.java"));
        assert coordinator.contains("awaitSteadyStateHeadroom(hytaleSafetyReserveMiB)");
        assert router.contains("consecutiveSafe.get() >= 2");
        assert router.contains("Post-warmup steady state cannot admit foreground providers");
        assert router.contains("CHATTERBOX_RESIDENCY=PRESERVED");
        assert router.contains("ORBIS_STEADY_STATE_REMEDIATION");
        assert router.contains("reclaimResources(ResourceWorkload.LLM,");
        assert router.contains("sustained-operating-envelope-pressure");
        assert router.contains("MEASURED_SAFE_HEADROOM_AFTER_BOUNDED_REMEDIATION");
        assert router.contains("remediationAttempts.get() < 1");
        assert router.contains("consecutiveUnsafe.get() >= 2");
    }

    private static AiProviderDefinition definition(Integer layers) {
        return new AiProviderDefinition("OPENAI_COMPATIBLE",
                LlmProviderCatalog.OLLAMA_CHAT_ENDPOINT, "nemotron-3-nano:4b",
                12_000, 2, "LOCAL", false, null, "NAMED_SINGLE", layers, "10m");
    }

    private static OrbisResourceConfig config(long timeoutMillis) {
        return new OrbisResourceConfig(2, ResourcePolicy.BALANCED, Map.of(),
                32, 2, 1, 1, 1, 1, 92, 88, 1_024, timeoutMillis, 512).validated();
    }

    private static OrbisResourceRequest request(AiProvider provider) {
        return new OrbisResourceRequest(UUID.randomUUID(), ResourceWorkload.LLM,
                ResourcePriority.HIGH, provider, true, 30_000);
    }

    private static RuntimeResourceMonitor.Snapshot host(long free) {
        RuntimeResourceMonitor.ModelResidency nemotron =
                new RuntimeResourceMonitor.ModelResidency("NEMOTRON",
                        "nemotron-3-nano:4b", "LOADED", true, 262_340,
                        980, ExecutionPlacement.LOCAL_PARTIAL_GPU,
                        "Ollama /api/ps target-host fixture");
        RuntimeResourceMonitor.ModelResidency chatterbox =
                new RuntimeResourceMonitor.ModelResidency("CHATTERBOX",
                        "chatterbox-turbo", "LOADED", true, 149_056,
                        2_848, ExecutionPlacement.LOCAL_GPU,
                        "TurboVoiceWorker target-host fixture");
        RuntimeResourceMonitor.FramePressure frame =
                new RuntimeResourceMonitor.FramePressure(Instant.now(), 33.3, 30.0,
                        false, 75.0, -1, -1, "UNKNOWN", "connected R062 fixture");
        long total = 12_282;
        return new RuntimeResourceMonitor.Snapshot(Instant.now(), 30, 12_000, 32_000,
                700, 4_096, 0, 80, total - free, free, total, "cpu", 24, "gpu",
                "nemotron-3-nano:4b 3.2 GB 74%/26% CPU/GPU", true, true, "",
                List.of(), List.of(nemotron, chatterbox), List.of(), frame, "UNKNOWN");
    }

    private record FakeWarmNemotron() implements AiProvider {
        @Override public String providerId() { return "NEMOTRON"; }
        @Override public AiServiceKind serviceKind() { return AiServiceKind.LANGUAGE_MODEL; }
        @Override public ProviderExecutionMode executionMode() {
            return ProviderExecutionMode.LOCAL;
        }
        @Override public AiProviderCapabilities capabilities() {
            return new AiProviderCapabilities(true, true, true, Set.of("text", "json"));
        }
        @Override public AiResourceRequirements resourceRequirements() {
            return new AiResourceRequirements(ExecutionPlacement.LOCAL_PARTIAL_GPU,
                    "nemotron-3-nano:4b", 6_144, 3_584, 1, true, true, 1_200,
                    980, 256, 128);
        }
    }
}
