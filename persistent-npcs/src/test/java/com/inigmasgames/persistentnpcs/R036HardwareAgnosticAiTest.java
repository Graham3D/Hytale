package com.inigmasgames.persistentnpcs;

import com.google.gson.JsonObject;
import com.inigmasgames.persistentnpcs.ai.AiProviderCapabilities;
import com.inigmasgames.persistentnpcs.ai.AiProviderConfigRepository;
import com.inigmasgames.persistentnpcs.ai.AiProviderConfig;
import com.inigmasgames.persistentnpcs.ai.AiProviderDefinition;
import com.inigmasgames.persistentnpcs.ai.AiServiceRouterFactory;
import com.inigmasgames.persistentnpcs.ai.AiProviderHealth;
import com.inigmasgames.persistentnpcs.ai.AiServiceKind;
import com.inigmasgames.persistentnpcs.ai.AiServiceRouter;
import com.inigmasgames.persistentnpcs.ai.ProviderExecutionMode;
import com.inigmasgames.persistentnpcs.ai.RemoteInferenceTransport;
import com.inigmasgames.persistentnpcs.config.FrameworkConfig;
import com.inigmasgames.persistentnpcs.llm.ChatMessage;
import com.inigmasgames.persistentnpcs.llm.LlmLatency;
import com.inigmasgames.persistentnpcs.llm.LlmProvider;
import com.inigmasgames.persistentnpcs.llm.LlmProviderStatus;
import com.inigmasgames.persistentnpcs.llm.LlmRequest;
import com.inigmasgames.persistentnpcs.llm.LlmResult;
import com.inigmasgames.persistentnpcs.llm.ConversationModelRoutingProvider;
import com.inigmasgames.persistentnpcs.voice.OpusClip;
import com.inigmasgames.persistentnpcs.voice.SpeechToTextProvider;
import com.inigmasgames.persistentnpcs.voice.SpeechTranscript;
import com.inigmasgames.persistentnpcs.voice.TextToSpeechProvider;
import com.inigmasgames.persistentnpcs.voice.VoiceRenderPlan;
import com.inigmasgames.persistentnpcs.voice.VoiceRuntimeConfig;
import com.inigmasgames.persistentnpcs.voice.VoicePresetRepository;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

public final class R036HardwareAgnosticAiTest {
    private R036HardwareAgnosticAiTest() { }

    public static void main(String[] args) throws Exception {
        existingConfigurationMigratesWithoutProfileSecrets();
        routerKeepsServicesIndependentAndFallbackExplicit();
        fullyRemoteConfigurationDoesNotProbeLocalHardwareAtConstruction();
        remoteTransportIsAsyncAndCancellationPropagates();
        architectureIsHardwareNeutralAndDiagnosticsAreNative();
        System.out.println("R036 hardware-agnostic AI provider tests passed.");
    }

    private static void fullyRemoteConfigurationDoesNotProbeLocalHardwareAtConstruction()
            throws Exception {
        AiProviderDefinition stt = new AiProviderDefinition("IMMERSIVE_HTTP",
                "http://192.0.2.10:9001", "moonshine", 500, 2, "REMOTE", false, null);
        AiProviderDefinition llm = new AiProviderDefinition("OPENAI_COMPATIBLE",
                "http://192.0.2.11:9002/v1/chat/completions", "nemotron", 500, 2,
                "REMOTE", false, null);
        AiProviderDefinition tts = new AiProviderDefinition("IMMERSIVE_HTTP",
                "http://192.0.2.12:9003", "chatterbox-turbo", 500, 1,
                "REMOTE", false, null);
        Path root = Files.createTempDirectory("r036-remote-only-");
        long started = System.nanoTime();
        try (AiServiceRouter router = AiServiceRouterFactory.create(
                new AiProviderConfig(stt, llm, tts), framework(llm.endpoint(), llm.model()),
                voice(), new VoicePresetRepository(root), ignored -> { })) {
            long elapsed = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started);
            assert elapsed < 1_500
                    : "remote-only construction attempted local inference: " + elapsed;
            assert router.diagnostic(AiServiceKind.SPEECH_TO_TEXT).mode()
                    == ProviderExecutionMode.REMOTE;
            assert router.diagnostic(AiServiceKind.LANGUAGE_MODEL).mode()
                    == ProviderExecutionMode.REMOTE;
            assert router.diagnostic(AiServiceKind.TEXT_TO_SPEECH).mode()
                    == ProviderExecutionMode.REMOTE;
        }
    }

    private static void existingConfigurationMigratesWithoutProfileSecrets() throws Exception {
        Path root = Files.createTempDirectory("r036-config-");
        FrameworkConfig framework = framework("http://10.0.0.8:1234/v1/chat/completions",
                "nemotron-existing");
        VoiceRuntimeConfig voice = voice();
        var repository = new AiProviderConfigRepository(root);
        var migrated = repository.load(framework, voice);
        assert migrated.stt().effectiveType("").equals("LOCAL_WORKER");
        assert migrated.llm().effectiveEndpoint("").contains("10.0.0.8");
        assert migrated.llm().executionMode() == ProviderExecutionMode.REMOTE;
        assert migrated.llm().effectiveModel("").equals("nemotron-existing");
        assert migrated.tts().effectiveType("").equals("LOCAL_WORKER");
        String stored = Files.readString(repository.path());
        assert !stored.toLowerCase().contains("apikey") : stored;
        assert repository.path().getParent().equals(root);
        assert !repository.path().toString().contains("profiles");
    }

    private static void routerKeepsServicesIndependentAndFallbackExplicit() {
        FakeStt stt = new FakeStt("remote-stt", ProviderExecutionMode.REMOTE, false);
        FakeLlm llm = new FakeLlm("local-llm", ProviderExecutionMode.LOCAL, false);
        FakeTts failingTts = new FakeTts("remote-tts", ProviderExecutionMode.REMOTE, true);
        FakeTts fallbackTts = new FakeTts("local-tts", ProviderExecutionMode.LOCAL, false);
        try (AiServiceRouter router = new AiServiceRouter(stt, null, llm, null,
                failingTts, fallbackTts, ignored -> { })) {
            router.probeAvailability().join();
            SpeechTranscript transcript = router.speechToText().transcribe(
                    UUID.randomUUID(), List.of(new byte[] {1, 2, 3})).join();
            assert transcript.text().equals("heard");
            LlmResult result = router.languageModel().generateResponse(new LlmRequest(
                    UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                    List.of(new ChatMessage("user", "hello")))).join();
            assert result.text().equals("answer");
            assert router.languageModel() instanceof ConversationModelRoutingProvider
                    : "provider decoration hid semantic tier routing";
            UUID endedSession = UUID.randomUUID();
            router.languageModel().endSession(endedSession);
            assert llm.cancelled.get() : "session cancellation did not reach LLM provider";
            OpusClip clip = router.textToSpeech().synthesize(UUID.randomUUID(),
                    UUID.randomUUID(), null, "hello").join();
            assert clip.frames().size() == 1;

            assert router.diagnostic(AiServiceKind.SPEECH_TO_TEXT).mode()
                    == ProviderExecutionMode.REMOTE;
            assert router.diagnostic(AiServiceKind.LANGUAGE_MODEL).mode()
                    == ProviderExecutionMode.LOCAL;
            var tts = router.diagnostic(AiServiceKind.TEXT_TO_SPEECH);
            assert tts.mode() == ProviderExecutionMode.LOCAL : tts;
            assert tts.metrics().fallbackActive();
            assert tts.fallbackStatus().equals("configured");
            assert router.diagnosticsText().contains("latestTotalMs=");
            assert router.diagnosticsText().contains("networkMs=");
        }

        FakeTts noFallbackFailure = new FakeTts("failed", ProviderExecutionMode.REMOTE, true);
        try (AiServiceRouter router = new AiServiceRouter(new FakeStt("stt",
                ProviderExecutionMode.LOCAL, false), null,
                new FakeLlm("llm", ProviderExecutionMode.LOCAL, false), null,
                noFallbackFailure, null, ignored -> { })) {
            boolean failed = false;
            try {
                router.textToSpeech().synthesize(UUID.randomUUID(), UUID.randomUUID(),
                        null, "hello").join();
            } catch (RuntimeException expected) {
                failed = true;
            }
            assert failed : "A provider must not silently fall back without configuration";
        }
    }

    private static void remoteTransportIsAsyncAndCancellationPropagates() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        var serverExecutor = Executors.newCachedThreadPool();
        server.setExecutor(serverExecutor);
        CountDownLatch requestStarted = new CountDownLatch(1);
        CountDownLatch cancellationReceived = new CountDownLatch(1);
        server.createContext("/v1/stt/transcribe", exchange -> {
            requestStarted.countDown();
            try { Thread.sleep(2_000); } catch (InterruptedException ignored) { }
            byte[] response = "{\"text\":\"late\"}".getBytes(StandardCharsets.UTF_8);
            try {
                exchange.sendResponseHeaders(200, response.length);
                exchange.getResponseBody().write(response);
            } finally { exchange.close(); }
        });
        server.createContext("/v1/cancel", exchange -> {
            cancellationReceived.countDown();
            exchange.sendResponseHeaders(204, -1);
            exchange.close();
        });
        server.start();
        UUID requestId = UUID.randomUUID();
        long started = System.nanoTime();
        try (RemoteInferenceTransport transport = new RemoteInferenceTransport(
                "http://127.0.0.1:" + server.getAddress().getPort(), 5_000)) {
            JsonObject body = new JsonObject();
            body.addProperty("requestId", requestId.toString());
            CompletableFuture<JsonObject> future = transport.post(
                    requestId, "/v1/stt/transcribe", body);
            long returnedMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started);
            assert returnedMillis < 250 : "remote dispatch blocked caller for " + returnedMillis;
            assert requestStarted.await(1, TimeUnit.SECONDS);
            transport.cancel(requestId);
            assert cancellationReceived.await(2, TimeUnit.SECONDS)
                    : "remote cancellation endpoint was not called";
            assert future.isCancelled() || future.isCompletedExceptionally();
        } finally {
            server.stop(0);
            serverExecutor.shutdownNow();
        }
    }

    private static void architectureIsHardwareNeutralAndDiagnosticsAreNative() throws Exception {
        Path source = Path.of("src/main/java/com/inigmasgames/persistentnpcs");
        for (String area : List.of("ai", "cognition", "conversation", "memory", "action")) {
            try (var paths = Files.walk(source.resolve(area))) {
                for (Path path : paths.filter(value -> value.toString().endsWith(".java")).toList()) {
                    String code = Files.readString(path).toLowerCase();
                    assert !code.contains("import cuda") : path;
                    assert !code.contains("com.nvidia") : path;
                }
            }
        }
        String pipeline = Files.readString(source.resolve("orbis/OrbisTurnCoordinator.java"))
                + Files.readString(source.resolve("orbis/OrbisSpeechCoordinator.java"));
        assert pipeline.contains("SpeechToTextProvider stt");
        assert pipeline.contains("TextToSpeechProvider tts");
        assert !pipeline.contains("private final TurboVoiceWorker");
        String plugin = Files.readString(source.resolve("PersistentNpcsPlugin.java"));
        assert plugin.contains("AiServiceRouterFactory.create");
        assert plugin.contains("aiServices.authoritativeSpeechToText()");
        assert plugin.contains("aiServices.authoritativeTextToSpeech()");
        String page = Files.readString(source.resolve("ui/CognitionInspectorPage.java"));
        String ui = Files.readString(Path.of("src/main/resources/Common/UI/Custom/Pages/"
                + "ImmersiveNpcCognitionInspector.ui"));
        assert page.contains("aiServices.diagnosticsText()");
        assert ui.contains("#AiBackends");
        assert !ui.toLowerCase().contains("html");
        String worker = Files.readString(Path.of(
                "src/main/resources/tools/immersive_voice_worker.py"));
        assert worker.contains("--transport");
        assert worker.contains("/v1/stt/transcribe");
        assert worker.contains("/v1/tts/synthesize");
        assert worker.contains("/v1/cancel");
        assert worker.contains("ThreadingHTTPServer");
        assert worker.contains("class RemoteVoiceServer");
    }

    private static FrameworkConfig framework(String endpoint, String model) {
        return new FrameworkConfig(endpoint, model, "secret-that-must-not-migrate",
                500, 5_000, 0.5, 100, 500, 5, 100, 300,
                true, 5_000, 5_000, "none");
    }

    private static VoiceRuntimeConfig voice() {
        return new VoiceRuntimeConfig(true, "", "auto", "base.en", "cpu", "int8",
                "AUTO", "TINY_STREAMING", 250, 24_000, true, false, true,
                5.0, 15.0, 15.0);
    }

    private abstract static class FakeProvider {
        final String id;
        final ProviderExecutionMode mode;
        FakeProvider(String id, ProviderExecutionMode mode) { this.id = id; this.mode = mode; }
        public String providerId() { return id; }
        public ProviderExecutionMode executionMode() { return mode; }
        public AiProviderCapabilities capabilities() {
            return new AiProviderCapabilities(true, true, true, Set.of("test"));
        }
        public CompletableFuture<AiProviderHealth> health() {
            return CompletableFuture.completedFuture(AiProviderHealth.healthy("test"));
        }
        public String backendDescription() { return id + " backend"; }
    }

    private static final class FakeStt extends FakeProvider implements SpeechToTextProvider {
        final boolean fail;
        FakeStt(String id, ProviderExecutionMode mode, boolean fail) {
            super(id, mode); this.fail = fail;
        }
        @Override public AiServiceKind serviceKind() { return AiServiceKind.SPEECH_TO_TEXT; }
        @Override public CompletableFuture<SpeechTranscript> transcribe(
                UUID id, List<byte[]> frames) {
            return fail ? CompletableFuture.failedFuture(new IllegalStateException("STT failed"))
                    : CompletableFuture.completedFuture(new SpeechTranscript(
                            "heard", 1, 2, "en"));
        }
    }

    private static final class FakeLlm extends FakeProvider implements LlmProvider {
        final boolean fail;
        final AtomicBoolean cancelled = new AtomicBoolean();
        FakeLlm(String id, ProviderExecutionMode mode, boolean fail) {
            super(id, mode); this.fail = fail;
        }
        @Override public AiServiceKind serviceKind() { return AiServiceKind.LANGUAGE_MODEL; }
        @Override public CompletableFuture<LlmResult> generateResponse(LlmRequest request) {
            return fail ? CompletableFuture.failedFuture(new IllegalStateException("LLM failed"))
                    : CompletableFuture.completedFuture(new LlmResult("answer",
                            new LlmLatency(Instant.now(), 1, 2, true)));
        }
        @Override public CompletableFuture<LlmProviderStatus> checkStatus() {
            return CompletableFuture.completedFuture(new LlmProviderStatus(
                    "test", "test", true, true, true, "ready"));
        }
        @Override public String description() { return backendDescription(); }
        @Override public void cancel(UUID id) { cancelled.set(true); }
    }

    private static final class FakeTts extends FakeProvider implements TextToSpeechProvider {
        final boolean fail;
        FakeTts(String id, ProviderExecutionMode mode, boolean fail) {
            super(id, mode); this.fail = fail;
        }
        @Override public AiServiceKind serviceKind() { return AiServiceKind.TEXT_TO_SPEECH; }
        @Override public CompletableFuture<OpusClip> synthesize(UUID requestId, UUID responseId,
                VoiceRenderPlan plan, String text) {
            return fail ? CompletableFuture.failedFuture(new IllegalStateException("TTS failed"))
                    : CompletableFuture.completedFuture(new OpusClip(List.of(new byte[] {1}),
                            48_000, 2, 1, "test", Path.of("reference.wav")));
        }
    }
}
