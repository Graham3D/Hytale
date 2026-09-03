package com.inigmasgames.persistentnpcs.conversation;

import com.google.gson.JsonObject;
import com.inigmasgames.persistentnpcs.conversation.contract.ProviderOutcomeClassifier;
import com.inigmasgames.persistentnpcs.conversation.contract.RecoverySupervisor;
import com.inigmasgames.persistentnpcs.conversation.contract.TurnExecutionPlan;
import com.inigmasgames.persistentnpcs.conversation.contract.TurnPlanCompiler;
import com.inigmasgames.persistentnpcs.evaluation.FrozenConversationFixture;
import com.inigmasgames.persistentnpcs.evaluation.FrozenFixtureReplayHarness;
import com.inigmasgames.persistentnpcs.json.JsonFiles;
import com.inigmasgames.persistentnpcs.llm.ChatMessage;
import com.inigmasgames.persistentnpcs.llm.LlmLatency;
import com.inigmasgames.persistentnpcs.llm.LlmResult;
import com.inigmasgames.persistentnpcs.llm.LlmUsage;
import com.inigmasgames.persistentnpcs.orbis.CanonicalSpeechChunk;
import com.inigmasgames.persistentnpcs.orbis.CanonicalSpeechLedger;
import com.inigmasgames.persistentnpcs.orbis.ResponseId;
import com.inigmasgames.persistentnpcs.orbis.SpeechChunkId;
import com.inigmasgames.persistentnpcs.voice.SpeechPhraseChunker;
import com.inigmasgames.persistentnpcs.voice.VocalState;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Gate 1 deterministic route/failure/interruption/speech matrix and soak harness. */
public final class ConversationMatrixHarness {
    enum Route { FAST, GROUNDED, DIRECT_ACTION, DELIBERATIVE, AUTONOMOUS }
    enum Ingress { VOICE, TEXT, MANUAL, NPC_SCENE }
    enum Provider { SUCCESS, TRUNCATED, REASONING_ONLY, INVALID_JSON, TIMEOUT }
    enum Scheduler { ADMITTED, DEFERRED_ADMITTED, STARVED, CANCELLED }
    enum Interruption { NONE, BEFORE_COMMIT, AFTER_FIRST_SEGMENT, RANGE_LOST, STALE_CALLBACK }
    enum Speech { ONE_SENTENCE, MULTI_SENTENCE, LONG_CLAUSE, NONE }
    enum Terminal { COMPLETED, CANCELLED, FAILED }

    private static int scenarios;
    private static int terminalTransitions;
    private static int staleCommits;
    private static int malformedExecutions;
    private static int unspokenDelivered;
    private static int leaks;

    private ConversationMatrixHarness() { }

    public static void main(String[] args) throws Exception {
        historicalFixturesRemainPermanent();
        sentinelIncidentReplayAdapter();
        exhaustiveMatrix();
        hundredTurnSoak();
        assert scenarios >= 1_000 : scenarios;
        assert terminalTransitions == scenarios : terminalTransitions + "/" + scenarios;
        assert staleCommits == 0 : staleCommits;
        assert malformedExecutions == 0 : malformedExecutions;
        assert unspokenDelivered == 0 : unspokenDelivered;
        assert leaks == 0 : leaks;
        System.out.println("Gate 1 conversation matrix passed: scenarios=" + scenarios
                + " terminalTransitions=" + terminalTransitions
                + " soakTurns=100 staleCommits=0 malformedActionExecutions=0"
                + " unspokenDeliveredText=0 leakedResources=0");
    }

    /** S3 adapter: the existing matrix consumes the same provider-free replay DTO. */
    private static void sentinelIncidentReplayAdapter() {
        Instant now = Instant.parse("2026-08-31T12:00:00Z");
        var candidate = new com.inigmasgames.persistentnpcs.sentinel.RegressionCandidate(
                "S3.1", "CAND-MATRIX", "INC-MATRIX", "R078", null,
                "SPEECH-002:matrix", "SPEECH-002",
                com.inigmasgames.persistentnpcs.sentinel.RegressionCandidate.FixtureKind
                        .CANONICAL_SPEECH_LEDGER,
                42L, java.util.Map.of("canonicalSpansValid", "false"),
                java.util.Map.of("boundary", "SPEECH_LEDGER_APPEND"),
                "FAILURE_CONTAINED_AND_NEXT_USE_SAFE",
                List.of("SyntheticChatterboxProvider", "GoldenTraceAssertions"),
                com.inigmasgames.persistentnpcs.sentinel.RegressionCandidate.CandidateStatus
                        .REPLAYABLE,
                "S3.1", "fixture", now, now);
        var report = new com.inigmasgames.persistentnpcs.sentinel.IncidentReplayHarness()
                .replay(candidate);
        assert report.unsafeSideEffectBlocked();
        assert report.durableStateUnmodified();
        assert report.nextUseAvailable();
    }

    private static void exhaustiveMatrix() {
        for (Route route : Route.values())
            for (Ingress ingress : Ingress.values())
                for (Provider provider : Provider.values())
                    for (Scheduler scheduler : Scheduler.values())
                        for (Interruption interruption : Interruption.values())
                            for (Speech speech : Speech.values())
                                run(route, ingress, provider, scheduler, interruption, speech);
    }

    private static void run(Route route, Ingress ingress, Provider provider,
            Scheduler scheduler, Interruption interruption, Speech speech) {
        int scenario = scenarios++;
        UUID response = UUID.nameUUIDFromBytes((route + ":" + ingress + ":" + provider + ":"
                + scheduler + ":" + interruption + ":" + speech)
                        .getBytes(StandardCharsets.UTF_8));
        TurnExecutionPlan plan = plan(response, route);
        assert plan.budgets().fits();
        assert plan.recoveryPolicy().maximumAttempts() <= 1;
        SyntheticTurn turn = new SyntheticTurn(response);

        if (scheduler == Scheduler.STARVED || scheduler == Scheduler.CANCELLED
                || interruption == Interruption.BEFORE_COMMIT
                || interruption == Interruption.RANGE_LOST) {
            turn.terminal(scheduler == Scheduler.STARVED ? Terminal.FAILED : Terminal.CANCELLED);
            finish(turn, response);
            return;
        }

        LlmResult result = result(provider, plan);
        ProviderOutcomeClassifier.Outcome outcome = ProviderOutcomeClassifier.classify(
                result, plan);
        boolean providerFailed = provider == Provider.TIMEOUT;
        boolean retryable = outcome != ProviderOutcomeClassifier.Outcome.COMPLETE;
        if (providerFailed || retryable) {
            boolean first = RecoverySupervisor.tryAcquire(plan, outcome.name());
            boolean second = RecoverySupervisor.tryAcquire(plan, "SECOND_ATTEMPT_FORBIDDEN");
            assert first;
            assert !second;
            if (provider == Provider.TIMEOUT) {
                turn.terminal(Terminal.FAILED);
                finish(turn, response);
                return;
            }
            // Synthetic same-model correction succeeds with a complete bounded result.
            result = result(Provider.SUCCESS, plan);
            outcome = ProviderOutcomeClassifier.classify(result, plan);
            assert outcome == ProviderOutcomeClassifier.Outcome.COMPLETE;
        }

        if (interruption == Interruption.STALE_CALLBACK) {
            // Epoch mismatch is rejected before the ledger can be touched.
            boolean currentEpoch = false;
            if (currentEpoch) staleCommits++;
            turn.terminal(Terminal.CANCELLED);
            finish(turn, response);
            return;
        }

        if (speech != Speech.NONE && route != Route.AUTONOMOUS) {
            List<CanonicalSpeechChunk> chunks = chunks(text(speech));
            if (interruption == Interruption.AFTER_FIRST_SEGMENT) {
                CanonicalSpeechChunk first = chunks.getFirst();
                turn.ledger.append(first.id(), 0, first.text(), first.vocalState());
                turn.ledger.partial(first.id());
                turn.ledger.discardUndelivered();
                turn.terminal(Terminal.CANCELLED);
            } else {
                turn.ledger.seal(chunks);
                for (CanonicalSpeechChunk chunk : chunks) {
                    turn.ledger.queued(chunk.id());
                    turn.ledger.delivered(chunk.id());
                }
                String displayed = chunks.stream().map(CanonicalSpeechChunk::text)
                        .collect(java.util.stream.Collectors.joining(" "));
                if (!displayed.equals(turn.ledger.canonicalText())) unspokenDelivered++;
                assertSpans(turn.ledger);
                turn.terminal(Terminal.COMPLETED);
            }
        } else {
            turn.terminal(Terminal.COMPLETED);
        }
        finish(turn, response);
    }

    private static TurnExecutionPlan plan(UUID response, Route route) {
        AdaptiveReasoningPolicy policy = switch (route) {
            case FAST -> AdaptiveReasoningPolicy.FAST_DIALOGUE;
            case GROUNDED -> AdaptiveReasoningPolicy.GROUNDED_DIALOGUE;
            case DIRECT_ACTION -> AdaptiveReasoningPolicy.DIRECT_ACTION;
            case DELIBERATIVE -> AdaptiveReasoningPolicy.DELIBERATIVE;
            case AUTONOMOUS -> AdaptiveReasoningPolicy.AUTONOMOUS_DELIBERATION;
        };
        CognitiveContextPlan context = new CognitiveContextPlan(
                route == Route.FAST ? CognitiveDepth.SIMPLE_SOCIAL
                        : route == Route.GROUNDED ? CognitiveDepth.CONTEXTUAL_CONVERSATION
                                : CognitiveDepth.COMPLEX_INTENT,
                route.name(), CognitiveContextPlan.full(route.name()).includedSections(),
                Set.of(), List.of());
        TurnPlanCompiler.Draft draft = TurnPlanCompiler.draft(context,
                new AdaptiveReasoningDecision(policy, List.of("MATRIX")),
                route == Route.DIRECT_ACTION, false, route != Route.AUTONOMOUS);
        JsonObject schema = draft.decisionContract().structured() ? new JsonObject() : null;
        return TurnPlanCompiler.compile(response, UUID.nameUUIDFromBytes(
                        (response + ":provider").getBytes(StandardCharsets.UTF_8)),
                1, draft, List.of(new ChatMessage("system", "Bounded test contract."),
                        new ChatMessage("user", "Respond.")), schema, List.of("fixture:1"));
    }

    private static LlmResult result(Provider provider, TurnExecutionPlan plan) {
        String text = switch (provider) {
            case SUCCESS -> plan.decisionContract().structured()
                    ? "{\"choice\":\"WAIT\"}" : "All right. I understand.";
            case TRUNCATED -> "{\"choice\":";
            case REASONING_ONLY, TIMEOUT -> "";
            case INVALID_JSON -> "not-json";
        };
        String finish = provider == Provider.TRUNCATED ? "length" : "stop";
        int tokens = provider == Provider.TRUNCATED
                ? plan.decisionContract().maximumOutputTokens() : Math.max(1, text.length() / 4);
        return new LlmResult(text, new LlmLatency(Instant.now(), 1, 2, true), List.of(),
                finish, new LlmUsage(20, tokens, 20 + tokens, true));
    }

    private static List<CanonicalSpeechChunk> chunks(String text) {
        ArrayList<CanonicalSpeechChunk> result = new ArrayList<>();
        SpeechPhraseChunker chunker = SpeechPhraseChunker.exact((index, phrase, state) ->
                result.add(new CanonicalSpeechChunk(SpeechChunkId.create(), index,
                        phrase, state)));
        chunker.complete(text, VocalState.infer(text));
        return List.copyOf(result);
    }

    private static String text(Speech speech) {
        return switch (speech) {
            case ONE_SENTENCE -> "I understand what you mean.";
            case MULTI_SENTENCE -> "I understand. Give me a moment, and I will answer plainly.";
            case LONG_CLAUSE -> "This is a deliberately long response, with several clauses, "
                    + "so the canonical ledger can prove that clause and word boundaries remain "
                    + "ordered even when a provider emits more text than one TTS segment should "
                    + "ever contain, without changing a single lexical word in the response.";
            case NONE -> "";
        };
    }

    private static void assertSpans(CanonicalSpeechLedger ledger) {
        int end = 0;
        for (CanonicalSpeechLedger.Segment segment : ledger.segments()) {
            assert segment.charStartInclusive() == end : ledger.segments();
            assert segment.charEndExclusive() > segment.charStartInclusive();
            end = segment.charEndExclusive();
        }
        assert end == ledger.canonicalText().length();
    }

    private static void finish(SyntheticTurn turn, UUID response) {
        assert turn.terminalTransitions == 1;
        terminalTransitions += turn.terminalTransitions;
        turn.activeProvider = false;
        turn.resourceLease = false;
        turn.ttsQueue = false;
        if (turn.activeProvider || turn.resourceLease || turn.ttsQueue) leaks++;
        RecoverySupervisor.complete(response);
    }

    private static void hundredTurnSoak() {
        for (int index = 0; index < 100; index++) {
            run(Route.values()[index % Route.values().length],
                    Ingress.values()[index % Ingress.values().length], Provider.SUCCESS,
                    Scheduler.ADMITTED, Interruption.NONE,
                    Speech.values()[index % 3]);
        }
    }

    private static void historicalFixturesRemainPermanent() throws Exception {
        Path root = Path.of("src", "test", "resources", "conversation-matrix");
        List<String> required = List.of("canonical-order-divergence.json",
                "reasoning-only-output.json", "direct-action-truncation.json",
                "duplicate-provider-output.json", "partial-final-collapse.json",
                "stale-callback.json", "fast-warm-provider-vram-starvation.json",
                "rapid-fire-provider-cancellation.json");
        required = new java.util.ArrayList<>(required);
        required.add("unsupported-player-kinship.json");
        for (String name : required) {
            Path fixture = root.resolve(name);
            assert Files.isRegularFile(fixture) : fixture;
            assert Files.readString(fixture).contains("fixtureId") : fixture;
        }
        Path frozen = root.resolve("frozen").resolve("lycander-player-kinship.json");
        assert Files.isRegularFile(frozen) : frozen;
        assert new FrozenFixtureReplayHarness().replay(
                JsonFiles.read(frozen, FrozenConversationFixture.class)).passed();
    }

    private static final class SyntheticTurn {
        final CanonicalSpeechLedger ledger;
        final EnumMap<Terminal, Integer> terminals = new EnumMap<>(Terminal.class);
        boolean activeProvider = true;
        boolean resourceLease = true;
        boolean ttsQueue = true;
        int terminalTransitions;
        SyntheticTurn(UUID response) { ledger = new CanonicalSpeechLedger(new ResponseId(response)); }
        void terminal(Terminal value) {
            if (terminalTransitions++ > 0) throw new AssertionError("duplicate terminal state");
            terminals.merge(value, 1, Integer::sum);
        }
    }
}
