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
import com.inigmasgames.persistentnpcs.voice.TranscribedPlayerUtterance;
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
import java.util.concurrent.atomic.AtomicReference;

/** Deterministic R052 ingress provenance and provider admission timeline tests. */
public final class R052IngressAdmissionTraceTest {
    private R052IngressAdmissionTraceTest() { }

    public static void main(String[] args) throws Exception {
        ingressProvenanceAndPhysicalUtteranceIdReachTrace();
        admissionFailureIncludesEveryTimelineStage();
        deferredProviderEventuallyRecordsAdmissionSuccess();
        System.out.println("R052 ingress/admission trace tests passed.");
    }

    private static void ingressProvenanceAndPhysicalUtteranceIdReachTrace()
            throws Exception {
        UUID physicalId = UUID.randomUUID();
        UUID playerId = UUID.randomUUID();
        UUID npcId = UUID.randomUUID();
        TranscribedPlayerUtterance voice = new TranscribedPlayerUtterance(
                physicalId, playerId, "Hello Mara", UUID.randomUUID(), 0, 0, 0,
                Instant.now(), 1, 2, 3, 4, TurnIngressSource.VOICE_CAPTURE,
                physicalId);
        assert "VOICE_CAPTURE -> STT -> AUTHORITATIVE_TRANSCRIPT".equals(
                voice.ingressProvenance());
        assert physicalId.equals(voice.originalPhysicalUtteranceId());
        assert "NATIVE_TEXT_CHAT -> AUTHORITATIVE_TRANSCRIPT".equals(
                TurnIngressSource.NATIVE_TEXT_CHAT.chain());
        assert "MANUAL_SUBMISSION -> AUTHORITATIVE_TRANSCRIPT".equals(
                TurnIngressSource.MANUAL_SUBMISSION.chain());

        Path root = Files.createTempDirectory("r052-ingress-trace-");
        ProfileRepository profiles = new ProfileRepository(root);
        NpcProfile mara = profile(npcId, "Mara");
        try (NpcTraceManager traces = new NpcTraceManager(profiles,
                Clock.fixed(Instant.parse("2026-08-30T02:03:04Z"), ZoneOffset.UTC),
                ignored -> { }, JsonObject::new)) {
            var session = traces.toggle(playerId, mara);
            traces.recordOrbis(new OrbisEvent(1, Instant.now(),
                    OrbisEventType.AUTHORITATIVE_TRANSCRIPT_ACCEPTED,
                    TurnId.create(), null, null, 1, ProviderRequestId.create(), Map.of(
                            "npcId", npcId.toString(),
                            "playerId", playerId.toString(),
                            "utteranceId", physicalId.toString(),
                            "ingressSource", TurnIngressSource.VOICE_CAPTURE.name(),
                            "ingressProvenance", voice.ingressProvenance(),
                            "originalPhysicalUtteranceId", physicalId.toString(),
                            "transcript", voice.transcript())));
            traces.awaitIdle();
            JsonObject event = Files.readAllLines(session.path()).stream()
                    .map(line -> JsonFiles.GSON.fromJson(line, JsonObject.class))
                    .filter(line -> line.has("orbisType")
                            && "AUTHORITATIVE_TRANSCRIPT_ACCEPTED".equals(
                                    line.get("orbisType").getAsString()))
                    .findFirst().orElseThrow();
            assert voice.ingressProvenance().equals(
                    event.get("ingressProvenance").getAsString());
            assert physicalId.toString().equals(
                    event.get("originalPhysicalUtteranceId").getAsString());
            assert physicalId.toString().equals(event.get("utteranceId").getAsString());
        }
    }

    private static void admissionFailureIncludesEveryTimelineStage() throws Exception {
        List<OrbisResourceEvent> events = java.util.Collections.synchronizedList(
                new ArrayList<>());
        try (OrbisResourceScheduler scheduler = scheduler(() -> pressureSnapshot(), 700)) {
            boolean starved = false;
            try {
                scheduler.admit(request(), events::add).get(2, TimeUnit.SECONDS);
            } catch (ExecutionException expected) {
                starved = expected.getCause() instanceof ResourceStarvedException;
            }
            assert starved;
            OrbisResourceEvent queued = first(events,
                    OrbisResourceEvent.Type.RESOURCE_REQUESTED);
            OrbisResourceEvent pressure = first(events,
                    OrbisResourceEvent.Type.RESOURCE_DEFERRED);
            OrbisResourceEvent reclaim = first(events,
                    OrbisResourceEvent.Type.RESOURCE_RECLAIM_ATTEMPT);
            OrbisResourceEvent failed = first(events,
                    OrbisResourceEvent.Type.RESOURCE_ADMISSION_FAILED);
            long rechecks = events.stream().filter(event -> event.type()
                    == OrbisResourceEvent.Type.RESOURCE_RECHECK).count();
            assert "QUEUED".equals(queued.facts().get("timelineStage"));
            assert !queued.facts().get("queuedAt").isBlank();
            assert "FIRST_PRESSURE_SAMPLE".equals(
                    pressure.facts().get("timelineStage"));
            assert !pressure.facts().get("firstPressureAt").isBlank();
            assert rechecks >= 2 : "periodic admission rechecks were not traced";
            assert "RECLAIM_ATTEMPT".equals(reclaim.facts().get("timelineStage"));
            assert reclaim.facts().get("reclaimActionAttempted")
                    .contains("PROVIDER_LIFECYCLE_ADAPTER");
            assert "FAILED".equals(failed.facts().get("admissionOutcome"));
            assert Long.parseLong(failed.facts().get("totalDeferDurationMs")) >= 600;
        }
    }

    private static void deferredProviderEventuallyRecordsAdmissionSuccess()
            throws Exception {
        AtomicReference<RuntimeResourceMonitor.Snapshot> host = new AtomicReference<>(
                pressureSnapshot());
        List<OrbisResourceEvent> events = java.util.Collections.synchronizedList(
                new ArrayList<>());
        try (OrbisResourceScheduler scheduler = scheduler(host::get, 1_500)) {
            var future = scheduler.admit(request(), events::add);
            waitFor(() -> events.stream().anyMatch(event -> event.type()
                    == OrbisResourceEvent.Type.RESOURCE_RECHECK));
            host.set(healthySnapshot());
            OrbisResourceScheduler.Lease lease = future.get(2, TimeUnit.SECONDS);
            OrbisResourceEvent admitted = first(events,
                    OrbisResourceEvent.Type.RESOURCE_ADMITTED);
            assert "ADMISSION_SUCCEEDED".equals(
                    admitted.facts().get("timelineStage"));
            assert "SUCCESS".equals(admitted.facts().get("admissionOutcome"));
            assert Integer.parseInt(admitted.facts().get("pressureSampleCount")) >= 2;
            assert Long.parseLong(admitted.facts().get("totalDeferDurationMs")) >= 150;
            lease.close();
        }
    }

    private static OrbisResourceScheduler scheduler(
            java.util.function.Supplier<RuntimeResourceMonitor.Snapshot> telemetry,
            long timeout) {
        OrbisResourceConfig base = OrbisResourceConfig.defaults();
        OrbisResourceConfig config = new OrbisResourceConfig(base.schemaVersion(),
                ResourcePolicy.BALANCED, Map.of(), 32, 2, 1, 1, 1, 1,
                92, 88, 512, timeout).validated();
        return new OrbisResourceScheduler(config, telemetry, ignored -> { });
    }

    private static OrbisResourceRequest request() {
        return new OrbisResourceRequest(UUID.randomUUID(), ResourceWorkload.LLM,
                ResourcePriority.HIGH, new FakeNemotron(), true, 5_000);
    }

    private static RuntimeResourceMonitor.Snapshot pressureSnapshot() {
        return snapshot(11_000, 12_000, 40);
    }

    private static RuntimeResourceMonitor.Snapshot healthySnapshot() {
        return snapshot(1_000, 12_000, 10);
    }

    private static RuntimeResourceMonitor.Snapshot snapshot(long vramUsed,
            long vramTotal, int gpuUtilization) {
        return new RuntimeResourceMonitor.Snapshot(Instant.now(), 20, 4_000, 32_000,
                500, 4_000, 0, gpuUtilization, vramUsed, vramTotal - vramUsed,
                vramTotal, "test-cpu", 16, "test-gpu", "", true, false, "");
    }

    private static OrbisResourceEvent first(List<OrbisResourceEvent> events,
            OrbisResourceEvent.Type type) {
        return events.stream().filter(event -> event.type() == type)
                .findFirst().orElseThrow();
    }

    private static void waitFor(java.util.function.BooleanSupplier condition)
            throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
        while (!condition.getAsBoolean() && System.nanoTime() < deadline) {
            Thread.sleep(5);
        }
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
