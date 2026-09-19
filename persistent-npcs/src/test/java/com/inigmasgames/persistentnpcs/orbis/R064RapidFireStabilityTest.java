package com.inigmasgames.persistentnpcs.orbis;

import com.inigmasgames.persistentnpcs.cognition.CognitionTurn;
import com.inigmasgames.persistentnpcs.cognition.GroundedIntent;
import com.inigmasgames.persistentnpcs.cognition.GroundedNpcDecision;
import com.inigmasgames.persistentnpcs.cognition.IntentCandidate;
import com.inigmasgames.persistentnpcs.cognition.NpcGroundingClaimValidator;
import com.inigmasgames.persistentnpcs.config.FrameworkConfig;
import com.inigmasgames.persistentnpcs.conversation.AdaptiveReasoningPolicy;
import com.inigmasgames.persistentnpcs.conversation.AdaptiveReasoningRouter;
import com.inigmasgames.persistentnpcs.conversation.CognitiveContextPlan;
import com.inigmasgames.persistentnpcs.conversation.CognitiveContextRouter;
import com.inigmasgames.persistentnpcs.conversation.ConversationSession;
import com.inigmasgames.persistentnpcs.conversation.DialogueMode;
import com.inigmasgames.persistentnpcs.llm.ChatMessage;
import com.inigmasgames.persistentnpcs.llm.LlmRequest;
import com.inigmasgames.persistentnpcs.llm.OpenAiCompatibleProvider;
import com.inigmasgames.persistentnpcs.profile.NpcProfile;
import com.inigmasgames.persistentnpcs.voice.VocalEmotion;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/** Permanent regression extracted from Mara_2026-08-30_14-29-07.jsonl. */
public final class R064RapidFireStabilityTest {
    private R064RapidFireStabilityTest() { }

    public static void main(String[] args) throws Exception {
        staleCompletionCannotReleaseNewSessionOwner();
        followQuestionIsAuthorityFirst();
        currentActivityQuestionIsBoundedGrounded();
        socialDialogueCannotInventRelationshipsOrPossessions();
        cancellationClosesTransportAndNextRequestSucceeds();
        System.out.println("R064 rapid-fire/provider-cancellation tests passed.");
    }

    private static void staleCompletionCannotReleaseNewSessionOwner() {
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        ConversationSession session = new ConversationSession(UUID.randomUUID(),
                UUID.randomUUID(), UUID.randomUUID(), java.time.Instant.now());
        assert session.beginRequest(first);
        assert !session.beginRequest(second);
        assert session.finishRequest(first);
        assert session.beginRequest(second);
        assert !session.finishRequest(first) : "stale completion cleared the new owner";
        assert second.equals(session.requestOwner());
        assert session.finishRequest(second);
    }

    private static void followQuestionIsAuthorityFirst() {
        UUID response = UUID.randomUUID();
        IntentCandidate action = new IntentCandidate(GroundedIntent.EXECUTE_DIRECT_REQUEST,
                60, 0.70, "validated action", List.of("ACTION:FOLLOW_PLAYER"));
        IntentCandidate genericQuestion = new IntentCandidate(GroundedIntent.PROCESS_INFORMATION,
                57, 0.61, "generic question", List.of("CONVERSATION:current"));
        GroundedNpcDecision decision = new GroundedNpcDecision(response, List.of(), List.of(),
                List.of(), List.of(), GroundedIntent.EXECUTE_DIRECT_REQUEST, 60,
                List.of("FOLLOW_PLAYER"), "", VocalEmotion.CALM, Optional.empty(),
                List.of("ACTION:FOLLOW_PLAYER"), List.of(action, genericQuestion), "");
        CognitionTurn turn = new CognitionTurn(null, null, null, 0, null, decision);
        CognitiveContextPlan routed = CognitiveContextPlan.full("REQUEST_ACTION_OR_COMMITMENT");
        var reasoning = AdaptiveReasoningRouter.route(routed,
                DialogueMode.ORDINARY_CONVERSATION, turn, 1, "Can you follow me?");
        assert reasoning.policy() == AdaptiveReasoningPolicy.DIRECT_ACTION : reasoning;
        assert reasoning.reasonCodes().contains("GENERIC_QUESTION_CANDIDATE_IGNORED");
    }

    private static void currentActivityQuestionIsBoundedGrounded() {
        NpcProfile mara = new NpcProfile(UUID.randomUUID(), "Mara", "smith", "curious",
                "A smith.", "Work well.", "home", "forge", List.of(), List.of(),
                List.of(), List.of("FOLLOW_PLAYER"), 5);
        String input = "Hello, where are you going?";
        DialogueMode mode = DialogueMode.classify(input, false, false);
        assert mode == DialogueMode.CURRENT_WORLD_STATE;
        CognitiveContextPlan plan = CognitiveContextRouter.route(mara, input, mode, null, null);
        assert plan.detectedIntent().equals("QUERY_CURRENT_ACTIVITY") : plan;
        assert plan.depth() == com.inigmasgames.persistentnpcs.conversation
                .CognitiveDepth.CONTEXTUAL_CONVERSATION;
        assert plan.includes("TASKS") && plan.includes("SEMANTIC_WORLD");
    }

    private static void socialDialogueCannotInventRelationshipsOrPossessions() {
        NpcGroundingClaimValidator validator = new NpcGroundingClaimValidator();
        var inventedFriends = validator.validate(
                "I've got a whole crew of critters who'd trade their tails for a good bolt.",
                List.of("RELATIONSHIP:player"));
        assert inventedFriends.stream().anyMatch(value ->
                value.category().equals("AUTOBIOGRAPHICAL_RELATIONSHIP") && !value.valid());
        var inventedPossession = validator.validate("I own a fox.",
                List.of("RELATIONSHIP:player"));
        assert inventedPossession.stream().anyMatch(value ->
                value.category().equals("AUTOBIOGRAPHICAL_POSSESSION") && !value.valid());
        assert validator.validate("I like practical work.", List.of()).stream()
                .allMatch(NpcGroundingClaimValidator.ClaimAssessment::valid);
        assert validator.validate("I have friends in town.", List.of(
                "RELATIONSHIP:player", "RELATIONSHIP:knownNpc")).stream()
                .allMatch(NpcGroundingClaimValidator.ClaimAssessment::valid);
    }

    private static void cancellationClosesTransportAndNextRequestSucceeds()
            throws Exception {
        AtomicInteger calls = new AtomicInteger();
        CountDownLatch firstStreaming = new CountDownLatch(1);
        CountDownLatch firstClosed = new CountDownLatch(1);
        List<String> logs = new CopyOnWriteArrayList<>();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/chat/completions", exchange -> {
            int call = calls.incrementAndGet();
            try (exchange) {
                exchange.getRequestBody().readAllBytes();
                exchange.getResponseHeaders().set("Content-Type", "text/event-stream");
                exchange.sendResponseHeaders(200, 0);
                if (call == 1) {
                    firstStreaming.countDown();
                    try {
                        while (true) {
                            exchange.getResponseBody().write(("data: {\"choices\":[{"
                                    + "\"delta\":{\"content\":\"\"}}]}\n\n")
                                    .getBytes(StandardCharsets.UTF_8));
                            exchange.getResponseBody().flush();
                            Thread.sleep(20);
                        }
                    } catch (IOException | InterruptedException closed) {
                        firstClosed.countDown();
                    }
                    return;
                }
                exchange.getResponseBody().write(("data: {\"choices\":[{\"delta\":{"
                        + "\"content\":\"Recovered.\"}}]}\n\n"
                        + "data: {\"choices\":[{\"delta\":{},\"finish_reason\":\"stop\"}]}\n\n"
                        + "data: [DONE]\n\n").getBytes(StandardCharsets.UTF_8));
                exchange.getResponseBody().flush();
            }
        });
        server.setExecutor(command -> Thread.ofVirtual().start(command));
        server.start();
        try (OpenAiCompatibleProvider provider = new OpenAiCompatibleProvider(
                config(server.getAddress().getPort()), logs::add)) {
            UUID firstId = UUID.randomUUID();
            var first = provider.generateResponse(request(firstId));
            assert firstStreaming.await(1, TimeUnit.SECONDS);
            long cancelStarted = System.nanoTime();
            provider.cancel(firstId);
            assert first.isCancelled() || first.isCompletedExceptionally();
            assert firstClosed.await(2, TimeUnit.SECONDS)
                    : "cancel did not close the real SSE transport";
            long drainMillis = TimeUnit.NANOSECONDS.toMillis(
                    System.nanoTime() - cancelStarted);
            var second = provider.generateResponse(request(UUID.randomUUID()))
                    .get(3, TimeUnit.SECONDS);
            assert second.text().equals("Recovered.") : second.text();
            assert drainMillis < 2_000 : drainMillis;
            assert calls.get() == 2 : calls;
            assert logs.stream().anyMatch(value -> value.contains("state=DRAINING"));
        } finally {
            server.stop(0);
        }
    }

    private static FrameworkConfig config(int port) {
        return new FrameworkConfig("http://127.0.0.1:" + port + "/v1/chat/completions",
                "nemotron-test", "", 1_000, 5_000, 0.2, 80, 600, 2, 20, 60,
                true, 2_000, 2_000, "none");
    }

    private static LlmRequest request(UUID providerRequestId) {
        return new LlmRequest(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                List.of(new ChatMessage("system", "Reply briefly."),
                        new ChatMessage("user", "Hello.")), List.of())
                .withProviderRequestId(providerRequestId);
    }
}
