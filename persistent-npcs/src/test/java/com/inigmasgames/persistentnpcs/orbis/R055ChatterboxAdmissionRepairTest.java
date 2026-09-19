package com.inigmasgames.persistentnpcs.orbis;

import com.inigmasgames.persistentnpcs.ai.AiProvider;
import com.inigmasgames.persistentnpcs.ai.AiProviderCapabilities;
import com.inigmasgames.persistentnpcs.ai.AiResourceRequirements;
import com.inigmasgames.persistentnpcs.ai.AiServiceKind;
import com.inigmasgames.persistentnpcs.ai.ExecutionPlacement;
import com.inigmasgames.persistentnpcs.ai.ProviderExecutionMode;
import com.inigmasgames.persistentnpcs.diagnostics.RuntimeResourceMonitor;
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

/** Regression coverage for R055 warm Chatterbox admission and bounded failure. */
public final class R055ChatterboxAdmissionRepairTest {
    private R055ChatterboxAdmissionRepairTest() { }

    public static void main(String[] args) throws Exception {
        punctuationAgnosticResidencyMatchAvoidsDoubleCounting();
        foregroundTtsUsesBoundedAdmissionDeadline();
        warmBudgetReflectsMeasuredPeakInsteadOfResidentReload();
        speechFailureClearsOnlyAffectedResponse();
        startupCleanupRemainsStrongerThanPressureReclaim();
        System.out.println("R055 Chatterbox admission repair tests passed.");
    }

    private static void punctuationAgnosticResidencyMatchAvoidsDoubleCounting()
            throws Exception {
        FakeChatterbox provider = new FakeChatterbox();
        List<OrbisResourceEvent> events = java.util.Collections.synchronizedList(
                new ArrayList<>());
        try (OrbisResourceScheduler scheduler = new OrbisResourceScheduler(
                shortTimeout(), () -> host(true, 900), ignored -> { })) {
            scheduler.admit(request(provider, 30_000), events::add)
                    .get(1, TimeUnit.SECONDS).close();
        }
        OrbisResourceEvent admitted = events.stream().filter(value -> value.type()
                == OrbisResourceEvent.Type.RESOURCE_ADMITTED).findFirst().orElseThrow();
        assert admitted.facts().get("providerResidencyState").equals("LOADED");
        assert admitted.facts().get("providerExpectedResident").equals("true");
        assert admitted.facts().get("providerWorkerPid").equals("1234");
        assert admitted.admissionWaitMillis() < 100;
    }

    private static void foregroundTtsUsesBoundedAdmissionDeadline() throws Exception {
        FakeChatterbox provider = new FakeChatterbox();
        List<OrbisResourceEvent> events = java.util.Collections.synchronizedList(
                new ArrayList<>());
        long started = System.nanoTime();
        try (OrbisResourceScheduler scheduler = new OrbisResourceScheduler(
                shortTimeout(), () -> host(false, 700), ignored -> { })) {
            boolean starved = false;
            try {
                scheduler.admit(request(provider, 30_000), events::add)
                        .get(1, TimeUnit.SECONDS);
            } catch (ExecutionException expected) {
                starved = expected.getCause() instanceof ResourceStarvedException;
            }
            assert starved;
            long snapshotDeadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(1);
            while (scheduler.snapshot().queueDepth() != 0
                    && System.nanoTime() < snapshotDeadline) Thread.sleep(5);
            assert scheduler.snapshot().queueDepth() == 0;
        }
        long elapsed = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started);
        assert elapsed >= 100 && elapsed < 900 : "unbounded TTS admission " + elapsed;
        assert events.stream().anyMatch(value -> value.type()
                == OrbisResourceEvent.Type.RESOURCE_TIMEOUT);
        assert events.stream().anyMatch(value -> value.type()
                == OrbisResourceEvent.Type.RESOURCE_ADMISSION_FAILED
                && value.facts().getOrDefault("failureReason", "")
                        .startsWith("RESOURCE_STARVED:"));
    }

    private static void warmBudgetReflectsMeasuredPeakInsteadOfResidentReload() {
        AiResourceRequirements value = new FakeChatterbox().resourceRequirements();
        assert value.residentVramMiB() == 2_848;
        assert value.incrementalVramMiB() == 192;
        assert value.temporaryVramMiB() == 128;
        assert value.incrementalVramMiB() + value.temporaryVramMiB() == 320;
    }

    private static void speechFailureClearsOnlyAffectedResponse() throws Exception {
        String source = Files.readString(Path.of(
                "src/main/java/com/inigmasgames/persistentnpcs/orbis/OrbisSpeechCoordinator.java"));
        assert source.contains("admissionFailed ? \"resource-admission\" : \"tts\"");
        assert source.contains("RESOURCE_ADMISSION_FAILED");
        assert source.contains("synthesisQueue.removeIf(value -> value.response == response)");
        assert source.contains("tts.cancel(response.request.responseId().value())");
    }

    private static void startupCleanupRemainsStrongerThanPressureReclaim() throws Exception {
        String selectable = Files.readString(Path.of(
                "src/main/java/com/inigmasgames/persistentnpcs/llm/SelectableLlmProvider.java"));
        String router = Files.readString(Path.of(
                "src/main/java/com/inigmasgames/persistentnpcs/ai/AiServiceRouter.java"));
        assert selectable.contains("prepareActiveResidency()")
                && selectable.contains("return unloadInactiveResidentModels().thenCompose");
        assert selectable.contains("unloadOwnedInactiveResidentModels()")
                && selectable.contains("!ownedOnly || value.residencyPrepared()");
        assert router.contains("selectable.unloadOwnedInactiveResidentModels()");
    }

    private static OrbisResourceRequest request(FakeChatterbox provider, long timeout) {
        return new OrbisResourceRequest(UUID.randomUUID(), ResourceWorkload.TTS,
                ResourcePriority.HIGH, provider, true, timeout);
    }

    private static OrbisResourceConfig shortTimeout() {
        OrbisResourceConfig base = OrbisResourceConfig.defaults();
        return new OrbisResourceConfig(base.schemaVersion(), base.policy(), Map.of(),
                32, 2, 1, 1, 1, 1, 92, 88, 1_024, 160, 512).validated();
    }

    private static RuntimeResourceMonitor.Snapshot host(boolean resident, long free) {
        RuntimeResourceMonitor.ModelResidency chatterbox =
                new RuntimeResourceMonitor.ModelResidency("CHATTERBOX",
                        "chatterbox-turbo", resident ? "LOADED" : "UNLOADED",
                        true, resident ? 1_234 : -1, resident ? 2_880 : 2_848,
                        resident ? ExecutionPlacement.LOCAL_GPU
                                : ExecutionPlacement.UNKNOWN,
                        "authoritative TurboVoiceWorker telemetry");
        RuntimeResourceMonitor.FramePressure frame =
                new RuntimeResourceMonitor.FramePressure(Instant.now(), 33.3, 30.0,
                        false, 75.0, -1, -1, "UNKNOWN", "test");
        long total = 12_282;
        return new RuntimeResourceMonitor.Snapshot(Instant.now(), 20,
                8_000, 32_000, 512, 4_096, 0, 40,
                total - free, free, total, "cpu", 16, "gpu", "", true, true, "",
                List.of(), List.of(chatterbox), List.of(), frame, "UNKNOWN");
    }

    private record FakeChatterbox() implements AiProvider {
        @Override public String providerId() { return "chatterbox-turbo-local-worker"; }
        @Override public AiServiceKind serviceKind() { return AiServiceKind.TEXT_TO_SPEECH; }
        @Override public ProviderExecutionMode executionMode() {
            return ProviderExecutionMode.LOCAL;
        }
        @Override public AiProviderCapabilities capabilities() {
            return new AiProviderCapabilities(false, true, true, Set.of("text", "opus"));
        }
        @Override public AiResourceRequirements resourceRequirements() {
            return new AiResourceRequirements(ExecutionPlacement.LOCAL_GPU,
                    "Chatterbox Turbo external worker", 3_072, 3_168, 1,
                    false, true, 1_200, 2_848, 192, 128);
        }
    }
}
