package com.inigmasgames.persistentnpcs.evaluation;

import com.inigmasgames.persistentnpcs.llm.LlmLatency;
import com.inigmasgames.persistentnpcs.llm.LlmProvider;
import com.inigmasgames.persistentnpcs.llm.LlmProviderException;
import com.inigmasgames.persistentnpcs.llm.LlmProviderStatus;
import com.inigmasgames.persistentnpcs.llm.LlmRequest;
import com.inigmasgames.persistentnpcs.llm.LlmResult;
import com.inigmasgames.persistentnpcs.orbis.OrbisEventType;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/** Regression for a zero-token provider failure escaping as an empty TURN_FAILED. */
public final class R090ProviderZeroTokenRecoveryTest {
    private R090ProviderZeroTokenRecoveryTest() { }

    public static void main(String[] args) throws Exception {
        sameModelRetryCommitsCanonicalResponse();
        repeatedZeroTokenFailureUsesValidatedDeterministicRecovery();
        System.out.println("R090 zero-token provider recovery regressions passed.");
    }

    private static void sameModelRetryCommitsCanonicalResponse() throws Exception {
        FailingProvider provider = new FailingProvider(1);
        var result = run("retry-success", provider);
        assert result.terminalState().equals("TURN_COMPLETED") : result;
        assert provider.calls.get() == 2 : provider.calls;
        assert !result.canonicalResponses().isEmpty() : result;
        assert observed(result, OrbisEventType.RECOVERY_ATTEMPTED) : result.observations();
        assert observed(result, OrbisEventType.RECOVERY_SUCCEEDED) : result.observations();
        assert !observed(result, OrbisEventType.RECOVERY_EXHAUSTED) : result.observations();
    }

    private static void repeatedZeroTokenFailureUsesValidatedDeterministicRecovery()
            throws Exception {
        FailingProvider provider = new FailingProvider(Integer.MAX_VALUE);
        var result = run("deterministic-fallback", provider);
        assert result.terminalState().equals("TURN_COMPLETED") : result;
        assert provider.calls.get() == 2 : provider.calls;
        String dialogue = result.canonicalResponses().values().stream().findFirst().orElse("");
        assert dialogue.toLowerCase(java.util.Locale.ROOT).startsWith("i want ")
                : dialogue;
        assert observed(result, OrbisEventType.RECOVERY_ATTEMPTED) : result.observations();
        assert observed(result, OrbisEventType.RECOVERY_EXHAUSTED) : result.observations();
        assert observed(result, OrbisEventType.RECOVERY_SUCCEEDED) : result.observations();
    }

    private static OrbisEvaluationHost.TurnEvaluationResult run(String id,
            FailingProvider provider) throws Exception {
        Path production = EvaluationTestRoots.profileSnapshot("Lycander");
        var scenario = EvaluationScenarioCatalog.singleActor(production, "Lycander",
                "zero-token-" + id, List.of("What do you want?"),
                java.util.Set.of("PROVIDER_RECOVERY"));
        Path evaluation = Path.of("build", "orbis-eval", "provider-recovery")
                .toAbsolutePath().normalize();
        try (var host = new OrbisEvaluationHost(evaluation, production, id,
                EvaluationContracts.EvaluationMode.STATIC_REPLAY, provider,
                "FAULT_INJECTION", "nemotron-3-nano:4b", "in-process", ignored -> { })) {
            host.start(scenario);
            var result = host.submit(scenario.turns().getFirst()).get(10, TimeUnit.SECONDS);
            host.finish();
            return result;
        }
    }

    private static boolean observed(OrbisEvaluationHost.TurnEvaluationResult result,
            OrbisEventType type) {
        return result.observations().stream().anyMatch(value -> value.eventType() == type);
    }

    private static final class FailingProvider implements LlmProvider {
        private final int failures;
        private final AtomicInteger calls = new AtomicInteger();
        private FailingProvider(int failures) { this.failures = failures; }

        @Override public CompletableFuture<LlmResult> generateResponse(LlmRequest request) {
            return call(ignored -> { });
        }
        @Override public CompletableFuture<LlmResult> stream(LlmRequest request,
                Consumer<String> tokens) {
            return call(tokens);
        }
        private CompletableFuture<LlmResult> call(Consumer<String> tokens) {
            if (calls.incrementAndGet() <= failures) return CompletableFuture.failedFuture(
                    new LlmProviderException(
                            "SSE request completed without dialogue tokens; events=1 done=true"));
            String text = "I want to keep my work in order.";
            tokens.accept(text);
            return CompletableFuture.completedFuture(new LlmResult(text,
                    new LlmLatency(Instant.now(), 1, 2, true)));
        }
        @Override public boolean streamingEnabled() { return true; }
        @Override public CompletableFuture<LlmProviderStatus> checkStatus() {
            return CompletableFuture.completedFuture(new LlmProviderStatus("in-process",
                    "fault-injection", true, true, true, "test"));
        }
        @Override public String description() { return "zero-token fault injection"; }
    }
}
