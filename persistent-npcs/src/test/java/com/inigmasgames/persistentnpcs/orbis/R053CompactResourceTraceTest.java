package com.inigmasgames.persistentnpcs.orbis;

import com.google.gson.JsonObject;
import com.inigmasgames.persistentnpcs.ai.AiProvider;
import com.inigmasgames.persistentnpcs.ai.AiProviderCapabilities;
import com.inigmasgames.persistentnpcs.ai.AiResourceRequirements;
import com.inigmasgames.persistentnpcs.ai.AiServiceKind;
import com.inigmasgames.persistentnpcs.ai.ExecutionPlacement;
import com.inigmasgames.persistentnpcs.ai.ProviderExecutionMode;
import com.inigmasgames.persistentnpcs.diagnostics.NpcTraceManager;
import com.inigmasgames.persistentnpcs.diagnostics.ResourceSnapshotIdentity;
import com.inigmasgames.persistentnpcs.diagnostics.RuntimeResourceMonitor;
import com.inigmasgames.persistentnpcs.json.JsonFiles;
import com.inigmasgames.persistentnpcs.profile.NpcProfile;
import com.inigmasgames.persistentnpcs.profile.ProfileRepository;
import java.nio.charset.StandardCharsets;
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
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/** Five-second resource-starvation trace compactness and change-emission validation. */
public final class R053CompactResourceTraceTest {
    private R053CompactResourceTraceTest() { }

    public static void main(String[] args) throws Exception {
        fiveSecondStarvationRemainsCompactAndReconstructable();
        materialOwnershipChangeEmitsNewFullSnapshot();
        System.out.println("R053 compact resource trace tests passed.");
    }

    private static void fiveSecondStarvationRemainsCompactAndReconstructable()
            throws Exception {
        RuntimeResourceMonitor.Snapshot host = pressureSnapshot(4_242, 120);
        UUID npcId = UUID.randomUUID();
        UUID playerId = UUID.randomUUID();
        Path root = Files.createTempDirectory("r053-five-second-trace-");
        ProfileRepository profiles = new ProfileRepository(root);
        NpcProfile mara = profile(npcId, "Mara");
        JsonObject traceStart = fullSnapshotJson(host);
        List<OrbisResourceEvent> schedulerEvents = java.util.Collections.synchronizedList(
                new ArrayList<>());
        AtomicLong sequence = new AtomicLong();
        try (NpcTraceManager traces = new NpcTraceManager(profiles, ignored -> { },
                () -> traceStart);
             OrbisResourceScheduler scheduler = scheduler(() -> host, 5_000)) {
            var session = traces.toggle(playerId, mara);
            boolean starved = false;
            try {
                scheduler.admit(request(), event -> {
                    schedulerEvents.add(event);
                    LinkedHashMap<String, String> facts = new LinkedHashMap<>(event.facts());
                    facts.put("npcId", npcId.toString());
                    facts.put("playerId", playerId.toString());
                    traces.recordOrbis(new OrbisEvent(sequence.incrementAndGet(),
                            event.at(), OrbisEventType.valueOf(event.type().name()),
                            TurnId.create(), null, null, 1, ProviderRequestId.create(),
                            Map.copyOf(facts)));
                }).get(7, TimeUnit.SECONDS);
            } catch (ExecutionException expected) {
                starved = expected.getCause() instanceof ResourceStarvedException;
            }
            assert starved;
            traces.awaitIdle();

            List<String> lines = Files.readAllLines(session.path());
            List<JsonObject> json = lines.stream().map(line ->
                    JsonFiles.GSON.fromJson(line, JsonObject.class)).toList();
            List<JsonObject> rechecks = json.stream().filter(line ->
                    line.has("orbisType") && "RESOURCE_RECHECK".equals(
                            line.get("orbisType").getAsString())).toList();
            assert rechecks.size() >= 20 && rechecks.size() <= 27
                    : "unexpected five-second recheck cadence: " + rechecks.size();
            for (JsonObject recheck : rechecks) {
                for (String required : List.of("vramUsedMiB", "vramFreeMiB",
                        "vramTotalMiB", "gpuUtilizationPercent", "pressureSource",
                        "pressureThreshold", "recheckCount", "queuePosition",
                        "queueDepth", "totalDeferDurationMs", "reclaimStatus",
                        "reclaimResult", "schedulerDecision", "resourceSnapshotId",
                        "resourceSnapshotReference")) {
                    assert recheck.has(required) : "missing recheck delta field " + required;
                }
                for (String forbidden : List.of("perProcessGpuVram",
                        "providerResidencyTable", "providerExpectedResident",
                        "providerWorkerPid", "hytaleClientGpuVramMiB",
                        "orbisProviderEstimatedVramMiB")) {
                    assert !recheck.has(forbidden)
                            : "full snapshot field leaked into recheck: " + forbidden;
                }
                assert "DELTA".equals(recheck.get("resourceSnapshotMode").getAsString());
            }

            JsonObject reclaim = first(json, "RESOURCE_RECLAIM_ATTEMPT");
            JsonObject failed = first(json, "RESOURCE_ADMISSION_FAILED");
            assert "FULL".equals(reclaim.get("resourceSnapshotMode").getAsString());
            assert reclaim.get("perProcessGpuVram").getAsString().contains("4242");
            assert "LOADED".equals(reclaim.get("providerResidencyState").getAsString());
            assert "FAILED".equals(failed.get("admissionOutcome").getAsString());
            assert failed.get("failureReason").getAsString().contains("RESOURCE_STARVED");
            assert Long.parseLong(failed.get("totalDeferDurationMs").getAsString())
                    >= 4_900;

            long recheckBytes = lines.stream().filter(line ->
                    line.contains("\"orbisType\":\"RESOURCE_RECHECK\""))
                    .mapToLong(R053CompactResourceTraceTest::utf8LineBytes).sum();
            long actualBytes = Files.size(session.path());
            long legacyRecheckBytes = 0;
            for (JsonObject recheck : rechecks) {
                JsonObject legacy = recheck.deepCopy();
                legacy.add("perProcessGpuVram", reclaim.get("perProcessGpuVram"));
                legacy.add("recentResidencyTransitions",
                        reclaim.get("recentResidencyTransitions"));
                legacy.add("providerExpectedResident",
                        reclaim.get("providerExpectedResident"));
                legacy.add("providerResidencyState",
                        reclaim.get("providerResidencyState"));
                legacy.add("providerWorkerPid", reclaim.get("providerWorkerPid"));
                legacyRecheckBytes += utf8LineBytes(legacy.toString());
            }
            long modeledLegacyBytes = actualBytes - recheckBytes + legacyRecheckBytes;
            assert recheckBytes < legacyRecheckBytes / 5
                    : "rechecks did not compact enough";
            assert actualBytes < modeledLegacyBytes / 2
                    : "representative trace did not materially shrink";
            assert schedulerEvents.stream().anyMatch(event -> event.type()
                    == OrbisResourceEvent.Type.RESOURCE_REQUESTED);
            assert schedulerEvents.stream().anyMatch(event -> event.type()
                    == OrbisResourceEvent.Type.RESOURCE_DEFERRED);
            assert schedulerEvents.stream().anyMatch(event -> event.type()
                    == OrbisResourceEvent.Type.RESOURCE_TIMEOUT);
            System.out.println("R053_5S_TRACE actualBytes=" + actualBytes
                    + " modeledLegacyBytes=" + modeledLegacyBytes
                    + " resourceEvents=" + schedulerEvents.size()
                    + " rechecks=" + rechecks.size()
                    + " recheckBytes=" + recheckBytes);
        }
    }

    private static void materialOwnershipChangeEmitsNewFullSnapshot() throws Exception {
        AtomicReference<RuntimeResourceMonitor.Snapshot> host = new AtomicReference<>(
                pressureSnapshot(1_000, 1));
        List<OrbisResourceEvent> events = java.util.Collections.synchronizedList(
                new ArrayList<>());
        try (OrbisResourceScheduler scheduler = scheduler(host::get, 1_500)) {
            OrbisResourceRequest request = request();
            var future = scheduler.admit(request, events::add);
            waitFor(() -> events.stream().anyMatch(event -> event.type()
                    == OrbisResourceEvent.Type.RESOURCE_RECHECK));
            String priorId = events.stream().filter(event -> event.type()
                    == OrbisResourceEvent.Type.RESOURCE_RECHECK).findFirst().orElseThrow()
                    .facts().get("resourceSnapshotId");
            host.set(pressureSnapshot(2_000, 1));
            waitFor(() -> events.stream().anyMatch(event -> event.type()
                    == OrbisResourceEvent.Type.RESOURCE_SNAPSHOT));
            OrbisResourceEvent changed = events.stream().filter(event -> event.type()
                    == OrbisResourceEvent.Type.RESOURCE_SNAPSHOT).findFirst().orElseThrow();
            assert !priorId.equals(changed.facts().get("resourceSnapshotId"));
            assert "FULL".equals(changed.facts().get("resourceSnapshotMode"));
            assert Set.of("GPU_PROCESS_OWNERSHIP_CHANGED", "PROVIDER_RESIDENCY_CHANGED")
                    .contains(changed.facts().get("snapshotReason"));
            assert changed.facts().get("perProcessGpuVram").contains("pid=2000");
            scheduler.cancel(request.requestId(), "test-complete");
            try { future.get(1, TimeUnit.SECONDS); } catch (Exception ignored) { }
        }
    }

    private static OrbisResourceScheduler scheduler(
            java.util.function.Supplier<RuntimeResourceMonitor.Snapshot> telemetry,
            long timeout) {
        OrbisResourceConfig base = OrbisResourceConfig.defaults();
        return new OrbisResourceScheduler(new OrbisResourceConfig(base.schemaVersion(),
                ResourcePolicy.BALANCED, Map.of(), 32, 2, 1, 1, 1, 1,
                92, 88, 512, timeout).validated(), telemetry, ignored -> { });
    }

    private static OrbisResourceRequest request() {
        return new OrbisResourceRequest(UUID.randomUUID(), ResourceWorkload.LLM,
                ResourcePriority.HIGH, new FakeNemotron(), true, 10_000);
    }

    private static RuntimeResourceMonitor.Snapshot pressureSnapshot(long firstPid,
            int processCount) {
        List<RuntimeResourceMonitor.GpuProcess> processes = new ArrayList<>();
        for (int index = 0; index < processCount; index++) {
            processes.add(new RuntimeResourceMonitor.GpuProcess(firstPid + index,
                    "diagnostic-worker-with-a-deliberately-descriptive-name-" + index
                            + ".exe", 256 + index * 3L, "MEASURED", index == 0
                                    ? "OLLAMA" : "OTHER"));
        }
        var residency = new RuntimeResourceMonitor.ModelResidency("NEMOTRON",
                "nemotron-3-nano:4b", "LOADED", true, firstPid, 3_584,
                ExecutionPlacement.LOCAL_GPU, "ollama ps");
        var transition = new RuntimeResourceMonitor.ResidencyTransition("NEMOTRON",
                "nemotron-3-nano:4b", "LOAD_OBSERVED", Instant.now(),
                7_000, 1_000, firstPid);
        return new RuntimeResourceMonitor.Snapshot(Instant.now(), 25, 4_000, 32_000,
                500, 4_000, 0, 45, 11_000, 1_000, 12_000, "test-cpu", 16,
                "test-gpu", "nemotron-3-nano:4b 100% GPU", true, false, "",
                processes, List.of(residency), List.of(transition),
                new RuntimeResourceMonitor.FramePressure(Instant.now(), 11, 90,
                        false, 75, -1, -1, "UNKNOWN",
                        "client FPS is not exposed"), "MEASURED_WHERE_EXPOSED");
    }

    private static JsonObject fullSnapshotJson(RuntimeResourceMonitor.Snapshot host) {
        JsonObject value = new JsonObject();
        value.addProperty("resourceSnapshotId", ResourceSnapshotIdentity.id(host));
        value.addProperty("vramUsedMiB", host.vramUsedMiB());
        value.addProperty("vramFreeMiB", host.vramFreeMiB());
        value.addProperty("vramTotalMiB", host.vramTotalMiB());
        value.addProperty("gpuUtilizationPercent", host.gpuUtilizationPercent());
        value.add("gpuProcesses", JsonFiles.GSON.toJsonTree(host.gpuProcesses()));
        value.add("modelResidencies", JsonFiles.GSON.toJsonTree(host.modelResidencies()));
        value.addProperty("perProcessGpuProbeStatus", host.perProcessGpuProbeStatus());
        return value;
    }

    private static JsonObject first(List<JsonObject> lines, String type) {
        return lines.stream().filter(line -> line.has("orbisType")
                && type.equals(line.get("orbisType").getAsString()))
                .findFirst().orElseThrow();
    }

    private static long utf8LineBytes(String line) {
        return line.getBytes(StandardCharsets.UTF_8).length + 1L;
    }

    private static void waitFor(java.util.function.BooleanSupplier condition)
            throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
        while (!condition.getAsBoolean() && System.nanoTime() < deadline) Thread.sleep(5);
        assert condition.getAsBoolean();
    }

    private static NpcProfile profile(UUID id, String name) {
        return new NpcProfile(id, name, "villager", "grounded",
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
