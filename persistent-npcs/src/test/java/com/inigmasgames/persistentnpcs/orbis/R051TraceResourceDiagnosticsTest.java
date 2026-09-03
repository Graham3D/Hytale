package com.inigmasgames.persistentnpcs.orbis;

import com.google.gson.JsonObject;
import com.inigmasgames.persistentnpcs.ai.AiProvider;
import com.inigmasgames.persistentnpcs.ai.AiProviderCapabilities;
import com.inigmasgames.persistentnpcs.ai.AiResourceRequirements;
import com.inigmasgames.persistentnpcs.ai.AiServiceKind;
import com.inigmasgames.persistentnpcs.ai.ExecutionPlacement;
import com.inigmasgames.persistentnpcs.ai.ProviderExecutionMode;
import com.inigmasgames.persistentnpcs.diagnostics.NpcTraceManager;
import com.inigmasgames.persistentnpcs.diagnostics.RuntimeResourceMonitor;
import com.inigmasgames.persistentnpcs.json.JsonFiles;
import com.inigmasgames.persistentnpcs.profile.NpcProfile;
import com.inigmasgames.persistentnpcs.profile.ProfileRepository;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

/** Deterministic R051 operator trace and scheduler resource telemetry tests. */
public final class R051TraceResourceDiagnosticsTest {
    private R051TraceResourceDiagnosticsTest() { }

    public static void main(String[] args) throws Exception {
        traceStartsWithCachedResourceSnapshot();
        vramDeferralExplainsDecisionAndReclaimPolicy();
        windowsProbeAndWorkerReadinessRemainAuthoritative();
        System.out.println("R051 trace resource diagnostics tests passed.");
    }

    private static void traceStartsWithCachedResourceSnapshot() throws Exception {
        Path root = Files.createTempDirectory("r051-trace-resource-");
        ProfileRepository profiles = new ProfileRepository(root);
        NpcProfile mara = profile("Mara");
        JsonObject resource = resourceJson();
        try (NpcTraceManager traces = new NpcTraceManager(profiles,
                Clock.fixed(Instant.parse("2026-08-30T01:02:03Z"), ZoneOffset.UTC),
                ignored -> { }, () -> resource)) {
            var session = traces.toggle(UUID.randomUUID(), mara);
            traces.awaitIdle();
            List<JsonObject> lines = Files.readAllLines(session.path()).stream()
                    .map(line -> JsonFiles.GSON.fromJson(line, JsonObject.class)).toList();
            JsonObject snapshot = lines.stream().filter(line ->
                    "RESOURCE_SNAPSHOT".equals(line.get("event").getAsString()))
                    .findFirst().orElseThrow();
            assert snapshot.has("gpuProcesses");
            assert snapshot.has("modelResidencies");
            assert snapshot.has("orbisEstimatedVramByProvider");
            assert snapshot.has("residencyTransitions");
            assert snapshot.has("framePressure");
            assert snapshot.get("hytaleClientGpuVramMiB").getAsLong() == -1;
        }
    }

    private static void vramDeferralExplainsDecisionAndReclaimPolicy() throws Exception {
        RuntimeResourceMonitor.Snapshot host = snapshot();
        List<OrbisResourceEvent> events = java.util.Collections.synchronizedList(
                new ArrayList<>());
        OrbisResourceConfig base = OrbisResourceConfig.defaults();
        OrbisResourceConfig config = new OrbisResourceConfig(base.schemaVersion(),
                ResourcePolicy.BALANCED, Map.of(), 32, 2, 1, 1, 1, 1,
                92, 88, 512, 180).validated();
        try (OrbisResourceScheduler scheduler = new OrbisResourceScheduler(
                config, () -> host, ignored -> { })) {
            boolean starved = false;
            try {
                scheduler.admit(new OrbisResourceRequest(UUID.randomUUID(),
                        ResourceWorkload.LLM, ResourcePriority.HIGH,
                        new FakeNemotron(), true, 5_000), events::add)
                        .get(1, TimeUnit.SECONDS);
            } catch (ExecutionException expected) {
                starved = expected.getCause() instanceof ResourceStarvedException;
            }
            assert starved;
            OrbisResourceEvent deferred = events.stream().filter(event -> event.type()
                    == OrbisResourceEvent.Type.RESOURCE_DEFERRED).findFirst().orElseThrow();
            assert "BLOCKED_BECAUSE_VRAM".equals(
                    deferred.facts().get("schedulerDecision"));
            assert deferred.facts().get("reclaimActionAttempted")
                    .contains("PROVIDER_LIFECYCLE_RECLAIM_REQUESTED");
            assert "DELTA".equals(deferred.facts().get("resourceSnapshotMode"));
            assert !deferred.facts().containsKey("perProcessGpuVram");
            OrbisResourceEvent reclaim = events.stream().filter(event -> event.type()
                    == OrbisResourceEvent.Type.RESOURCE_RECLAIM_ATTEMPT)
                    .findFirst().orElseThrow();
            assert "FULL".equals(reclaim.facts().get("resourceSnapshotMode"));
            assert "3584".equals(reclaim.facts().get("orbisProviderEstimatedVramMiB"));
            assert reclaim.facts().get("perProcessGpuVram").contains("4242");
            assert "true".equals(reclaim.facts().get("providerExpectedResident"));
            assert "true".equals(reclaim.facts().get("hytaleServerFramePressure"));
            assert "UNKNOWN".equals(reclaim.facts().get("hytaleClientFramePressure"));
            assert reclaim.facts().get("recentResidencyTransitions")
                    .contains("freeVramBeforeMiB=7000");
        }
    }

    private static void windowsProbeAndWorkerReadinessRemainAuthoritative()
            throws Exception {
        String monitor = Files.readString(Path.of(
                "src/main/java/com/inigmasgames/persistentnpcs/diagnostics/"
                        + "RuntimeResourceMonitor.java"));
        String worker = Files.readString(Path.of(
                "src/main/java/com/inigmasgames/persistentnpcs/voice/TurboVoiceWorker.java"));
        assert monitor.contains("--query-compute-apps=pid,process_name,used_gpu_memory");
        assert monitor.contains("NOT_REPORTED_BY_NVIDIA_SMI");
        assert monitor.contains("client FPS is not exposed");
        assert worker.contains("updateResidency(\"LOADED\", true");
        assert worker.contains("process.pid()");
    }

    private static JsonObject resourceJson() {
        JsonObject value = new JsonObject();
        value.add("gpuProcesses", JsonFiles.GSON.toJsonTree(snapshot().gpuProcesses()));
        value.add("modelResidencies", JsonFiles.GSON.toJsonTree(
                snapshot().modelResidencies()));
        value.add("residencyTransitions", JsonFiles.GSON.toJsonTree(
                snapshot().residencyTransitions()));
        value.add("framePressure", JsonFiles.GSON.toJsonTree(snapshot().framePressure()));
        value.add("orbisEstimatedVramByProvider",
                JsonFiles.GSON.toJsonTree(Map.of("NEMOTRON", 3584, "CHATTERBOX", 4096)));
        value.addProperty("hytaleClientGpuVramMiB", -1);
        value.addProperty("hytaleServerGpuVramMiB", -1);
        return value;
    }

    private static RuntimeResourceMonitor.Snapshot snapshot() {
        var process = new RuntimeResourceMonitor.GpuProcess(4242, "ollama_llama_server.exe",
                3_400, "MEASURED", "OLLAMA");
        var model = new RuntimeResourceMonitor.ModelResidency("NEMOTRON",
                "nemotron-3-nano:4b", "LOADED", true, 4242, 3584,
                ExecutionPlacement.LOCAL_GPU, "ollama ps");
        var transition = new RuntimeResourceMonitor.ResidencyTransition("NEMOTRON",
                "nemotron-3-nano:4b", "LOADED", Instant.now(), 7_000, 4_000, 4242);
        var frame = new RuntimeResourceMonitor.FramePressure(Instant.now(), 91.0,
                10.98, true, 75.0, -1, -1, "UNKNOWN",
                "Update 6 server delta; client FPS is not exposed");
        return new RuntimeResourceMonitor.Snapshot(Instant.now(), 30, 4_000, 32_000,
                500, 4_000, 0, 40, 11_000, 1_000, 12_000, "test-cpu", 16,
                "test-gpu", "nemotron-3-nano:4b 100% GPU", true, false, "",
                List.of(process), List.of(model), List.of(transition), frame,
                "MEASURED_WHERE_EXPOSED");
    }

    private static NpcProfile profile(String name) {
        return new NpcProfile(UUID.randomUUID(), name, "villager", "grounded",
                "A practical authored NPC.", "Live a grounded life.", "", "",
                List.of(), List.of(), List.of(), List.of(), 0).validated();
    }

    private record FakeNemotron() implements AiProvider {
        @Override public String providerId() { return "NEMOTRON"; }
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
                    "nemotron-3-nano:4b", 6_144, 3_584, 1, true, true, 500);
        }
    }
}
