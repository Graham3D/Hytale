package com.inigmasgames.persistentnpcs;

import com.google.gson.JsonObject;
import com.inigmasgames.persistentnpcs.json.JsonFiles;
import com.inigmasgames.persistentnpcs.llm.ChatMessage;
import com.inigmasgames.persistentnpcs.llm.LlmExecutionPolicy;
import com.inigmasgames.persistentnpcs.llm.LlmRequest;
import com.inigmasgames.persistentnpcs.llm.LlmResult;
import com.inigmasgames.persistentnpcs.llm.orbisllm.OrbisLlamaCppProvider;
import com.inigmasgames.persistentnpcs.llm.orbisllm.OrbisLlmProtocol;
import com.inigmasgames.persistentnpcs.llm.orbisllm.OrbisLlmRuntimeManifest;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CancellationException;
import java.util.concurrent.TimeUnit;

/** Phase 1 protocol, manifest, real-provider, structured, cancellation and recovery gates. */
public final class R065OrbisLlamaCppPhase1Test {
    private R065OrbisLlamaCppPhase1Test() { }

    public static void main(String[] arguments) throws Exception {
        protocolRoundTripIsBoundedAndVersioned();
        invalidProtocolAndMissingRuntimeFailExplicitly();
        Path manifest = arguments.length == 0 ? null : Path.of(arguments[0]);
        if (manifest != null) realSidecarLifecycle(manifest);
        System.out.println("R065 Orbis llama.cpp Phase 1 tests passed. real=" + (manifest != null));
    }

    private static void protocolRoundTripIsBoundedAndVersioned() {
        UUID request = UUID.randomUUID();
        JsonObject payload = new JsonObject();
        payload.addProperty("value", "grounded");
        byte[] frame = OrbisLlmProtocol.encode(OrbisLlmProtocol.Type.GENERATE,
                request, 7, payload);
        byte[] headerBytes = java.util.Arrays.copyOfRange(frame, 0,
                OrbisLlmProtocol.HEADER_BYTES);
        OrbisLlmProtocol.Header header = OrbisLlmProtocol.decodeHeader(headerBytes);
        byte[] body = java.util.Arrays.copyOfRange(frame,
                OrbisLlmProtocol.HEADER_BYTES, frame.length);
        OrbisLlmProtocol.Frame decoded = OrbisLlmProtocol.decode(header, body);
        assert decoded.type() == OrbisLlmProtocol.Type.GENERATE;
        assert decoded.requestId().equals(request);
        assert decoded.sequence() == 7;
        assert "grounded".equals(decoded.body().get("value").getAsString());
        boolean bounded = false;
        try {
            JsonObject tooLarge = new JsonObject();
            tooLarge.addProperty("data", "x".repeat(OrbisLlmProtocol.MAX_REQUEST_BYTES + 1));
            OrbisLlmProtocol.encode(OrbisLlmProtocol.Type.GENERATE, request, 1, tooLarge);
        } catch (IllegalArgumentException expected) { bounded = true; }
        assert bounded;
    }

    private static void invalidProtocolAndMissingRuntimeFailExplicitly() throws Exception {
        UUID request = UUID.randomUUID();
        byte[] frame = OrbisLlmProtocol.encode(OrbisLlmProtocol.Type.HELLO,
                request, 1, new JsonObject());
        frame[0] = 0;
        boolean malformed = false;
        try { OrbisLlmProtocol.decodeHeader(java.util.Arrays.copyOf(frame,
                OrbisLlmProtocol.HEADER_BYTES)); }
        catch (IllegalArgumentException expected) { malformed = true; }
        assert malformed;
        Path missing = Files.createTempDirectory("orbisllm-missing-").resolve("missing.json");
        boolean absent = false;
        try { OrbisLlmRuntimeManifest.loadVerified(missing); }
        catch (IllegalStateException expected) { absent = expected.getMessage().contains("missing"); }
        assert absent;
    }

    private static void realSidecarLifecycle(Path manifest) throws Exception {
        OrbisLlmRuntimeManifest.Loaded verified =
                OrbisLlmRuntimeManifest.loadVerified(manifest);
        assert verified.manifest().profiles().get("BALANCED").gpuLayers() == 4;
        Path data = Files.createTempDirectory("orbisllm-r065-");
        OrbisLlamaCppProvider provider = new OrbisLlamaCppProvider(data, manifest,
                System.out::println);
        try {
            long coldStarted = System.nanoTime();
            provider.warmUp().get(120, TimeUnit.SECONDS);
            long coldLoadMillis = elapsed(coldStarted);
            long warmStarted = System.nanoTime();
            assert provider.checkStatus().get(30, TimeUnit.SECONDS).reachable();
            long warmCheckMillis = elapsed(warmStarted);
            StringBuilder streamed = new StringBuilder();
            LlmResult plain = provider.generateResponse(request(
                    "Reply with exactly one short friendly sentence introducing yourself as Mara.",
                    false, false, 48), streamed::append).get(30, TimeUnit.SECONDS);
            assert !plain.text().isBlank();
            assert plain.text().equals(streamed.toString())
                    : "final result diverged from ordered final deltas";
            assert plain.latency().timeToFirstTokenMillis() >= 0;
            assert plain.reasoningTelemetry().promptEvaluationMillis() >= 0;

            LlmResult structured = provider.generateResponse(request(
                    "Return this decision: intent AMBIENT_RESPONSE, spokenText Hello there., "
                            + "emotion CALM, paralinguisticEvent NONE, actions empty, "
                            + "groundingEvidenceRefs empty.", true, false, 160))
                    .get(30, TimeUnit.SECONDS);
            JsonObject decision = JsonFiles.GSON.fromJson(structured.text(), JsonObject.class);
            assert "AMBIENT_RESPONSE".equals(decision.get("intent").getAsString());
            assert decision.getAsJsonArray("actions").isEmpty();

            LlmRequest cancelled = request("Think carefully and write a very long explanation "
                    + "about blacksmithing, village life, relationships, weather, and tools.",
                    false, true, 512);
            var cancelledFuture = provider.generateResponse(cancelled, ignored -> { });
            Thread.sleep(100);
            long cancelStarted = System.nanoTime();
            provider.cancel(cancelled.providerRequestId());
            try { cancelledFuture.get(5, TimeUnit.SECONDS); }
            catch (java.util.concurrent.ExecutionException failure) {
                assert failure.getCause() instanceof CancellationException;
            } catch (CancellationException expected) { }

            long drainDeadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
            while (System.nanoTime() < drainDeadline && !"READY".equals(
                    provider.runtimeDiagnostics(UUID.randomUUID())
                            .get("orbisLlmState").getAsString())) Thread.sleep(2);
            long cancelDrainMillis = elapsed(cancelStarted);
            assert cancelDrainMillis < 2_000;

            long nextStarted = System.nanoTime();
            LlmResult recovered = provider.generateResponse(request(
                    "Reply with exactly: Ready for the next turn.", false, false, 32))
                    .get(30, TimeUnit.SECONDS);
            assert !recovered.text().isBlank();
            long nextCompletionMillis = elapsed(nextStarted);

            long pid = provider.runtimeDiagnostics(UUID.randomUUID()).get("pid").getAsLong();
            assert pid > 0;
            ProcessHandle.of(pid).orElseThrow().destroyForcibly();
            long restartStarted = System.nanoTime();
            long crashDeadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
            while (System.nanoTime() < crashDeadline && !"CRASHED".equals(
                    provider.runtimeDiagnostics(UUID.randomUUID())
                            .get("orbisLlmState").getAsString())) Thread.sleep(20);
            assert "CRASHED".equals(provider.runtimeDiagnostics(UUID.randomUUID())
                    .get("orbisLlmState").getAsString());
            LlmResult restarted = provider.generateResponse(request(
                    "Reply with exactly: Restart recovered.", false, false, 32))
                    .get(120, TimeUnit.SECONDS);
            assert !restarted.text().isBlank();
            long restartMillis = elapsed(restartStarted);
            JsonObject diagnostics = provider.runtimeDiagnostics(UUID.randomUUID());
            System.out.println("R065_REAL_METRICS coldLoadMs=" + coldLoadMillis
                    + " warmCheckMs=" + warmCheckMillis + " cancelDrainMs="
                    + cancelDrainMillis + " nextCompletionMs=" + nextCompletionMillis
                    + " crashRestartMs=" + restartMillis + " resources="
                    + diagnostics.get("resources"));

            var shutdownInFlight = provider.generateResponse(request(
                    "Give a long deliberative account of every tool in the forge.",
                    false, true, 512));
            Thread.sleep(50);
            provider.close();
            long shutdownDeadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
            while (!shutdownInFlight.isDone() && System.nanoTime() < shutdownDeadline) {
                Thread.sleep(10);
            }
            assert shutdownInFlight.isDone() : "shutdown leaked an in-flight provider future";
        } finally {
            provider.close();
        }
        Thread.sleep(200);
        assert ProcessHandle.allProcesses().noneMatch(process -> process.info().command()
                .orElse("").toLowerCase().contains("orbisllm.exe"));
    }

    private static long elapsed(long started) {
        return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started);
    }

    private static LlmRequest request(String user, boolean structured,
            boolean reasoning, int maximumTokens) {
        UUID conversation = UUID.randomUUID();
        UUID providerRequest = UUID.randomUUID();
        JsonObject format = structured ? new JsonObject() : null;
        if (structured) format.addProperty("contract", "npc-decision-v1");
        LlmExecutionPolicy policy = new LlmExecutionPolicy(
                reasoning ? "DELIBERATIVE" : "REALTIME",
                reasoning ? LlmExecutionPolicy.ReasoningMode.ENABLED
                        : LlmExecutionPolicy.ReasoningMode.DISABLED,
                List.of("R065_TEST"), maximumTokens);
        return new LlmRequest(conversation, UUID.randomUUID(), UUID.randomUUID(), List.of(
                new ChatMessage("system", "You are Mara, a grounded curious Hytale NPC. Be concise."),
                new ChatMessage("user", user)), List.of(), format,
                structured ? 0.0 : 0.3, maximumTokens, providerRequest, policy);
    }
}
