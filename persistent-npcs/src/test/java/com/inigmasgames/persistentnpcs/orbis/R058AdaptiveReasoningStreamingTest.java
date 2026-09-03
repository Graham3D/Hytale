package com.inigmasgames.persistentnpcs.orbis;

import com.inigmasgames.persistentnpcs.conversation.AdaptiveReasoningPolicy;
import com.inigmasgames.persistentnpcs.conversation.AdaptiveReasoningRouter;
import com.inigmasgames.persistentnpcs.conversation.CognitiveContextPlan;
import com.inigmasgames.persistentnpcs.conversation.CognitiveDepth;
import com.inigmasgames.persistentnpcs.conversation.DialogueMode;
import com.inigmasgames.persistentnpcs.llm.ChatMessage;
import com.inigmasgames.persistentnpcs.llm.LlmExecutionPolicy;
import com.inigmasgames.persistentnpcs.llm.LlmRequest;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Deterministic R058 policy, authority, streaming, and diagnostics coverage. */
public final class R058AdaptiveReasoningStreamingTest {
    private R058AdaptiveReasoningStreamingTest() { }

    public static void main(String[] args) throws Exception {
        policyRoutingIsDeterministicAndDualTimescale();
        requestPolicySurvivesWireTransformations();
        fastSpeechRemainsOrbisOwnedAndCancellationBound();
        promptsAndDiagnosticsAreBounded();
        System.out.println("R058 adaptive reasoning and safe streaming tests passed.");
    }

    private static void policyRoutingIsDeterministicAndDualTimescale() {
        var social = AdaptiveReasoningRouter.route(plan(CognitiveDepth.SIMPLE_SOCIAL),
                DialogueMode.ORDINARY_CONVERSATION, null, 0, "Hello there.");
        assert social.policy() == AdaptiveReasoningPolicy.FAST_DIALOGUE;
        assert !social.policy().reasoningEnabled() && social.policy().earlySpeechEligible();
        assert social.policy().providerTokenBudget() == 56;

        var grounded = AdaptiveReasoningRouter.route(plan(CognitiveDepth.DIRECT_FACT),
                DialogueMode.ORDINARY_CONVERSATION, null, 0, "What is your name?");
        assert grounded.policy() == AdaptiveReasoningPolicy.GROUNDED_DIALOGUE;
        assert !grounded.policy().reasoningEnabled();

        var direct = AdaptiveReasoningRouter.route(plan(CognitiveDepth.COMPLEX_INTENT),
                DialogueMode.ORDINARY_CONVERSATION, null, 1, "Please pick up the hammer.");
        assert direct.policy() == AdaptiveReasoningPolicy.DIRECT_ACTION : direct;

        var complex = AdaptiveReasoningRouter.route(plan(CognitiveDepth.COMPLEX_INTENT),
                DialogueMode.PROPOSED_PLAN, null, 0,
                "We need a plan: first find Mara, then decide whether to risk the bridge.");
        assert complex.policy() == AdaptiveReasoningPolicy.DELIBERATIVE;
        assert complex.policy().reasoningEnabled();

        var autonomous = AdaptiveReasoningRouter.route(plan(CognitiveDepth.SIMPLE_SOCIAL),
                DialogueMode.NPC_INITIATED_CURIOSITY, null, 0,
                "NPC_INITIATED_CURIOSITY: consider the current situation");
        assert autonomous.policy() == AdaptiveReasoningPolicy.AUTONOMOUS_DELIBERATION;
        assert autonomous.reasonCodes().contains("BACKGROUND_PRIORITY_YIELDS_TO_PLAYER");
    }

    private static void requestPolicySurvivesWireTransformations() {
        LlmExecutionPolicy policy = new LlmExecutionPolicy("FAST_DIALOGUE",
                LlmExecutionPolicy.ReasoningMode.DISABLED,
                List.of("SIMPLE_SOCIAL_NO_ACTION_OR_CONFLICT"), 56);
        LlmRequest request = new LlmRequest(UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), List.of(new ChatMessage("system", "identity"),
                        new ChatMessage("user", "hello")), List.of())
                .withExecutionPolicy(policy).withProviderRequestId(UUID.randomUUID())
                .withSystemInstruction("authority").withGenerationParameters(0.3, 56);
        assert request.executionPolicy().equals(policy);
        assert request.maxTokensOverride() == 56;
        assert request.canonicalMessages().stream().filter(value ->
                "system".equals(value.role())).count() == 1;
    }

    private static void fastSpeechRemainsOrbisOwnedAndCancellationBound() throws Exception {
        String conversation = Files.readString(Path.of(
                "src/main/java/com/inigmasgames/persistentnpcs/conversation/ConversationService.java"));
        String coordinator = Files.readString(Path.of(
                "src/main/java/com/inigmasgames/persistentnpcs/orbis/OrbisTurnCoordinator.java"));
        String speech = Files.readString(Path.of(
                "src/main/java/com/inigmasgames/persistentnpcs/orbis/OrbisSpeechCoordinator.java"));
        assert conversation.contains("EarlyPhraseGate")
                && conversation.contains("NpcGroundingClaimValidator")
                && conversation.contains("ActionPromiseGuard.violation(exact, List.of())")
                && conversation.contains("reconcileCanonical")
                && conversation.contains("retainCommittedPrefix");
        assert coordinator.contains("IMMUTABLE_FAST_PHRASE")
                && coordinator.contains("submitStreaming")
                && coordinator.contains("speechCoordinator.append(branch.responseId()");
        assert speech.contains("sealed") && speech.contains("appendOnControl")
                && speech.contains("cancelOnControl");
    }

    private static void promptsAndDiagnosticsAreBounded() throws Exception {
        String builder = Files.readString(Path.of(
                "src/main/java/com/inigmasgames/persistentnpcs/conversation/ConversationContextBuilder.java"));
        String provider = Files.readString(Path.of(
                "src/main/java/com/inigmasgames/persistentnpcs/llm/OpenAiCompatibleProvider.java"));
        String diagnostics = Files.readString(Path.of(
                "src/main/java/com/inigmasgames/persistentnpcs/orbis/OrbisDiagnostics.java"));
        assert builder.contains("compactField(profile.personality(), 360)")
                && builder.contains("StaticPrefetch")
                && builder.contains("partialTranscriptAuthority") == false;
        assert provider.contains("case DISABLED -> \"none\"")
                && provider.contains("case ENABLED -> \"low\"")
                && provider.contains("reasoningCharacters")
                && !provider.contains("StringBuilder reasoning");
        assert diagnostics.contains("reasoningPolicy")
                && diagnostics.contains("firstValidatedPhraseMs")
                && diagnostics.contains("PIPELINE");
    }

    private static CognitiveContextPlan plan(CognitiveDepth depth) {
        return new CognitiveContextPlan(depth, depth.name(), Set.of("PROFILE"),
                Set.of(), List.of());
    }
}
