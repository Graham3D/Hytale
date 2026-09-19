package com.inigmasgames.persistentnpcs.conversation;

import com.google.gson.JsonObject;

import com.inigmasgames.persistentnpcs.action.NpcActionContext;
import com.inigmasgames.persistentnpcs.action.NpcActionRegistry;
import com.inigmasgames.persistentnpcs.action.NpcActionRequest;
import com.inigmasgames.persistentnpcs.action.NpcActionResult;
import com.inigmasgames.persistentnpcs.json.JsonFiles;
import com.inigmasgames.persistentnpcs.llm.ChatMessage;
import com.inigmasgames.persistentnpcs.llm.LlmRequest;
import com.inigmasgames.persistentnpcs.llm.LlmResult;
import com.inigmasgames.persistentnpcs.llm.LlmLatency;
import com.inigmasgames.persistentnpcs.llm.LlmProviderException;
import com.inigmasgames.persistentnpcs.llm.LlmReasoningTelemetry;
import com.inigmasgames.persistentnpcs.llm.LlmUsage;
import com.inigmasgames.persistentnpcs.llm.LlmProvider;
import com.inigmasgames.persistentnpcs.llm.LlmProviderStatus;
import com.inigmasgames.persistentnpcs.llm.ConversationRateLimiter;
import com.inigmasgames.persistentnpcs.llm.ConversationModelRoutingProvider;
import com.inigmasgames.persistentnpcs.llm.LlmAttributionSource;
import com.inigmasgames.persistentnpcs.llm.LlmInferenceAttribution;
import com.inigmasgames.persistentnpcs.llm.LlmRuntimeDiagnosticSource;
import com.inigmasgames.persistentnpcs.llm.LlmExecutionPolicy;
import com.inigmasgames.persistentnpcs.memory.MemoryRecord;
import com.inigmasgames.persistentnpcs.memory.MemoryStore;
import com.inigmasgames.persistentnpcs.memory.MemoryType;
import com.inigmasgames.persistentnpcs.perception.NpcPerceptionGateway;
import com.inigmasgames.persistentnpcs.perception.NpcPerceptionSnapshot;
import com.inigmasgames.persistentnpcs.perception.RawPerceptionSnapshot;
import com.inigmasgames.persistentnpcs.perception.SemanticPerceptionNormalizer;
import com.inigmasgames.persistentnpcs.perception.SemanticWorldModel;
import com.inigmasgames.persistentnpcs.perception.KnownNpcLocatorResult;
import com.inigmasgames.persistentnpcs.perception.KnownNpcLocatorService;
import com.inigmasgames.persistentnpcs.profile.NpcProfile;
import com.inigmasgames.persistentnpcs.relationship.RelationshipStore;
import java.time.Duration;
import java.time.Instant;
import java.util.Set;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.BiConsumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import com.inigmasgames.persistentnpcs.voice.VocalState;
import com.inigmasgames.persistentnpcs.cognition.CognitionTurn;
import com.inigmasgames.persistentnpcs.cognition.NpcCognitionService;
import com.inigmasgames.persistentnpcs.cognition.NpcSocialPerformance;
import com.inigmasgames.persistentnpcs.cognition.MaterialInformationGuard;
import com.inigmasgames.persistentnpcs.cognition.GroundedNpcDecision;
import com.inigmasgames.persistentnpcs.cognition.NpcDecision;
import com.inigmasgames.persistentnpcs.cognition.NpcDecisionAction;
import com.inigmasgames.persistentnpcs.cognition.NpcDecisionDiagnostics;
import com.inigmasgames.persistentnpcs.cognition.NpcDecisionCommitPolicy;
import com.inigmasgames.persistentnpcs.cognition.NpcDecisionSchema;
import com.inigmasgames.persistentnpcs.conversation.contract.ContractPromptBuilder;
import com.inigmasgames.persistentnpcs.conversation.contract.ContractMessagePruner;
import com.inigmasgames.persistentnpcs.conversation.contract.ProviderOutcomeClassifier;
import com.inigmasgames.persistentnpcs.conversation.contract.RecoverySupervisor;
import com.inigmasgames.persistentnpcs.conversation.contract.TurnExecutionPlan;
import com.inigmasgames.persistentnpcs.conversation.contract.TurnPlanCompiler;
import com.inigmasgames.persistentnpcs.cognition.NpcDecisionValidator;
import com.inigmasgames.persistentnpcs.cognition.ActionPromiseGuard;
import com.inigmasgames.persistentnpcs.cognition.NpcGroundingClaimValidator;
import com.inigmasgames.persistentnpcs.cognition.ResponseLatencyStage;
import com.inigmasgames.persistentnpcs.cognition.ResponseLatencyTraceStore;
import com.inigmasgames.persistentnpcs.diagnostics.NpcTurnAuditLog;
import com.inigmasgames.persistentnpcs.voice.SpeechPhraseChunker;
import com.inigmasgames.persistentnpcs.epistemic.EpistemicShadowAnalyzer;
import com.inigmasgames.persistentnpcs.epistemic.EpistemicFeatureMode;
import com.inigmasgames.persistentnpcs.epistemic.EpistemicProductionRoute;
import com.inigmasgames.persistentnpcs.epistemic.EpistemicClaimFirewall;
import com.inigmasgames.persistentnpcs.epistemic.EpistemicContract;
import com.inigmasgames.persistentnpcs.sentinel.OrbisDegradationSentinel;
import com.inigmasgames.persistentnpcs.sentinel.SentinelContracts;
import com.inigmasgames.persistentnpcs.sentinel.SentinelObservation;
import com.inigmasgames.persistentnpcs.sentinel.SentinelPromptIdentity;

public final class ConversationService {
    private static final Pattern STATED_NAME = Pattern.compile(
            "(?i)\\bmy\\s+name\\s+is\\s+([\\p{L}][\\p{L}'-]{0,31})(?=\\s*[.!?,;:]?\\s*$)");
    private final ConversationContextBuilder contextBuilder;
    private final LlmProvider provider;
    private final RelationshipStore relationships;
    private final MemoryStore memories;
    private final NpcActionRegistry actions;
    private final NpcPerceptionGateway perception;
    private final int maximumDialogueCharacters;
    private final Consumer<String> latencyLog;
    private final ConversationRateLimiter rateLimiter;
    private final ConversationGroundingService grounding;
    private final NpcCognitionService cognition;
    private final NpcSocialPerformance socialPerformance;
    private final SemanticPerceptionNormalizer semanticNormalizer;
    private final ResponseLatencyTraceStore responseLatency;
    private final KnownNpcLocatorService knownNpcLocator;
    private final boolean preferCompactOrdinaryPrompt;
    private final DialogueClaimValidator claimValidator = new DialogueClaimValidator();
    private final AuthoritativeDialogueValidator authoritativeValidator =
            new AuthoritativeDialogueValidator();
    private final NpcDecisionValidator decisionValidator = new NpcDecisionValidator();
    private final Set<CompletableFuture<?>> inFlight = ConcurrentHashMap.newKeySet();
    private final ConcurrentHashMap<UUID, ActiveInvocation> activeInvocations =
            new ConcurrentHashMap<>();
    private volatile NpcTurnAuditLog turnAuditLog;
    private volatile OrbisDegradationSentinel degradationSentinel;
    private volatile ConversationOutcome lastOutcome;

    public ConversationService(
            ConversationContextBuilder contextBuilder,
            LlmProvider provider,
            RelationshipStore relationships,
            MemoryStore memories,
            int maximumDialogueCharacters,
            Consumer<String> latencyLog) {
        this.contextBuilder = contextBuilder;
        this.provider = provider;
        this.relationships = relationships;
        this.memories = memories;
        this.maximumDialogueCharacters = maximumDialogueCharacters;
        this.latencyLog = latencyLog;
        this.actions = null;
        this.perception = null;
        this.rateLimiter = new ConversationRateLimiter(120);
        this.grounding = new ConversationGroundingService(ContentCatalog.unavailable());
        this.cognition = null;
        this.socialPerformance = NpcSocialPerformance.unavailable();
        this.semanticNormalizer = new SemanticPerceptionNormalizer();
        this.responseLatency = new ResponseLatencyTraceStore();
        this.knownNpcLocator = null;
        this.preferCompactOrdinaryPrompt = false;
    }

    public ConversationService(
            ConversationContextBuilder contextBuilder,
            LlmProvider provider,
            RelationshipStore relationships,
            MemoryStore memories,
            NpcActionRegistry actions,
            NpcPerceptionGateway perception,
            int maximumDialogueCharacters,
            Consumer<String> latencyLog) {
        this(contextBuilder, provider, relationships, memories, actions, perception,
                maximumDialogueCharacters, latencyLog,
                new ConversationRateLimiter(120),
                new ConversationGroundingService(ContentCatalog.unavailable()));
    }

    public ConversationService(
            ConversationContextBuilder contextBuilder,
            LlmProvider provider,
            RelationshipStore relationships,
            MemoryStore memories,
            NpcActionRegistry actions,
            NpcPerceptionGateway perception,
            int maximumDialogueCharacters,
            Consumer<String> latencyLog,
            ConversationRateLimiter rateLimiter) {
        this(contextBuilder, provider, relationships, memories, actions, perception,
                maximumDialogueCharacters, latencyLog, rateLimiter,
                new ConversationGroundingService(ContentCatalog.unavailable()));
    }

    public ConversationService(
            ConversationContextBuilder contextBuilder,
            LlmProvider provider,
            RelationshipStore relationships,
            MemoryStore memories,
            NpcActionRegistry actions,
            NpcPerceptionGateway perception,
            int maximumDialogueCharacters,
            Consumer<String> latencyLog,
            ConversationRateLimiter rateLimiter,
            ConversationGroundingService grounding) {
        this(contextBuilder, provider, relationships, memories, actions, perception,
                maximumDialogueCharacters, latencyLog, rateLimiter, grounding,
                null, NpcSocialPerformance.unavailable());
    }

    public ConversationService(
            ConversationContextBuilder contextBuilder,
            LlmProvider provider,
            RelationshipStore relationships,
            MemoryStore memories,
            NpcActionRegistry actions,
            NpcPerceptionGateway perception,
            int maximumDialogueCharacters,
            Consumer<String> latencyLog,
            ConversationRateLimiter rateLimiter,
            ConversationGroundingService grounding,
            NpcCognitionService cognition,
            NpcSocialPerformance socialPerformance) {
        this(contextBuilder, provider, relationships, memories, actions, perception,
                maximumDialogueCharacters, latencyLog, rateLimiter, grounding, cognition,
                socialPerformance, new SemanticPerceptionNormalizer(),
                cognition == null ? new ResponseLatencyTraceStore()
                        : cognition.responseLatency());
    }

    public ConversationService(
            ConversationContextBuilder contextBuilder,
            LlmProvider provider,
            RelationshipStore relationships,
            MemoryStore memories,
            NpcActionRegistry actions,
            NpcPerceptionGateway perception,
            int maximumDialogueCharacters,
            Consumer<String> latencyLog,
            ConversationRateLimiter rateLimiter,
            ConversationGroundingService grounding,
            NpcCognitionService cognition,
            NpcSocialPerformance socialPerformance,
            KnownNpcLocatorService knownNpcLocator) {
        this(contextBuilder, provider, relationships, memories, actions, perception,
                maximumDialogueCharacters, latencyLog, rateLimiter, grounding, cognition,
                socialPerformance, new SemanticPerceptionNormalizer(),
                cognition == null ? new ResponseLatencyTraceStore()
                        : cognition.responseLatency(), knownNpcLocator);
    }

    public ConversationService(
            ConversationContextBuilder contextBuilder,
            LlmProvider provider,
            RelationshipStore relationships,
            MemoryStore memories,
            NpcActionRegistry actions,
            NpcPerceptionGateway perception,
            int maximumDialogueCharacters,
            Consumer<String> latencyLog,
            ConversationRateLimiter rateLimiter,
            ConversationGroundingService grounding,
            NpcCognitionService cognition,
            NpcSocialPerformance socialPerformance,
            SemanticPerceptionNormalizer semanticNormalizer,
            ResponseLatencyTraceStore responseLatency) {
        this(contextBuilder, provider, relationships, memories, actions, perception,
                maximumDialogueCharacters, latencyLog, rateLimiter, grounding, cognition,
                socialPerformance, semanticNormalizer, responseLatency, null);
    }

    public ConversationService(
            ConversationContextBuilder contextBuilder,
            LlmProvider provider,
            RelationshipStore relationships,
            MemoryStore memories,
            NpcActionRegistry actions,
            NpcPerceptionGateway perception,
            int maximumDialogueCharacters,
            Consumer<String> latencyLog,
            ConversationRateLimiter rateLimiter,
            ConversationGroundingService grounding,
            NpcCognitionService cognition,
            NpcSocialPerformance socialPerformance,
            SemanticPerceptionNormalizer semanticNormalizer,
            ResponseLatencyTraceStore responseLatency,
            KnownNpcLocatorService knownNpcLocator) {
        this.contextBuilder = contextBuilder;
        this.provider = provider;
        this.relationships = relationships;
        this.memories = memories;
        this.actions = actions;
        this.perception = perception;
        this.maximumDialogueCharacters = maximumDialogueCharacters;
        this.latencyLog = latencyLog;
        this.rateLimiter = rateLimiter;
        this.grounding = grounding;
        this.cognition = cognition;
        this.socialPerformance = socialPerformance == null
                ? NpcSocialPerformance.unavailable() : socialPerformance;
        this.semanticNormalizer = semanticNormalizer == null
                ? new SemanticPerceptionNormalizer() : semanticNormalizer;
        this.responseLatency = responseLatency == null
                ? new ResponseLatencyTraceStore() : responseLatency;
        this.knownNpcLocator = knownNpcLocator;
        this.preferCompactOrdinaryPrompt = cognition != null;
    }

    public CompletableFuture<ConversationOutcome> converse(
            ConversationSession session,
            NpcProfile profile,
            String playerMessage,
            MinimalWorldContext worldContext) {
        return converse(session, profile, playerMessage, worldContext, ignored -> { });
    }

    public void setTurnAuditLog(NpcTurnAuditLog turnAuditLog) {
        this.turnAuditLog = turnAuditLog;
    }

    public void setDegradationSentinel(OrbisDegradationSentinel degradationSentinel) {
        this.degradationSentinel = degradationSentinel;
    }

    public void prefetchStaticContext(ConversationSession session, NpcProfile profile) {
        contextBuilder.prefetchStatic(session, profile);
    }

    public CompletableFuture<ConversationOutcome> converse(
            ConversationSession session,
            NpcProfile profile,
            String playerMessage,
            MinimalWorldContext worldContext,
            Consumer<String> tokenConsumer) {
        UUID testResponseId = UUID.randomUUID();
        return converseStreaming(session, profile, playerMessage, worldContext,
                testResponseId, (delta, ignored) -> tokenConsumer.accept(delta))
                .thenApply(outcome -> {
                    // Provider/brain harness compatibility only. Live Hytale ingress calls
                    // converseForOrbis and records history from native playback completion.
                    recordDeliveredConversation(session, profile, playerMessage,
                            testResponseId, outcome.dialogue(), outcome.dialogueMode(), false);
                    return outcome;
                });
    }

    /**
     * Phase 2 service entry point. Orbis supplies all branch identity,
     * cancellation authority, and the exact pinned provider.
     */
    public CompletableFuture<ConversationOutcome> converseForOrbis(
            ConversationSession session,
            NpcProfile profile,
            String playerMessage,
            MinimalWorldContext worldContext,
            ConversationInvocation invocation,
            BiConsumer<String, VocalState> tokenConsumer) {
        if (invocation == null) {
            return CompletableFuture.failedFuture(new IllegalArgumentException(
                    "Orbis conversation invocation required"));
        }
        return converseStreaming(session, profile, playerMessage, worldContext,
                invocation, tokenConsumer);
    }

    private CompletableFuture<ConversationOutcome> converseStreaming(
            ConversationSession session,
            NpcProfile profile,
            String playerMessage,
            MinimalWorldContext worldContext,
            UUID responseId,
            BiConsumer<String, VocalState> tokenConsumer) {
        ConversationInvocation invocation = new ConversationInvocation(responseId, responseId,
                provider, () -> true, ConversationLifecycleObserver.none());
        return converseStreaming(session, profile, playerMessage, worldContext,
                invocation, tokenConsumer);
    }

    private CompletableFuture<ConversationOutcome> converseStreaming(
            ConversationSession session,
            NpcProfile profile,
            String playerMessage,
            MinimalWorldContext worldContext,
            ConversationInvocation invocation,
            BiConsumer<String, VocalState> tokenConsumer) {
        UUID responseId = invocation.responseId();
        if (!session.beginRequest(responseId)) {
            return CompletableFuture.failedFuture(
                    new ConversationBusyException(profile.name() + " is still thinking; activeResponseId="
                            + session.requestOwner()));
        }
        long totalStartedNanos = System.nanoTime();
        responseLatency.begin(responseId, profile.id(), session.playerId());
        ConversationSession.PlayerUtteranceContext utteranceContext =
                session.playerUtteranceContext();
        if (utteranceContext != null) {
            responseLatency.recordDuration(responseId,
                    ResponseLatencyStage.VOICE_FRAME_CAPTURE, 0);
            if (utteranceContext.endpointMillis() >= 0) responseLatency.recordDuration(
                    responseId, ResponseLatencyStage.UTTERANCE_ENDPOINT,
                    utteranceContext.endpointMillis());
            if (utteranceContext.sttMillis() >= 0) responseLatency.recordDuration(
                    responseId, ResponseLatencyStage.STT_TRANSCRIPTION,
                    utteranceContext.sttMillis());
            if (utteranceContext.audienceResolutionMillis() >= 0) {
                responseLatency.recordDuration(responseId,
                        ResponseLatencyStage.AUDIENCE_RESOLUTION,
                        utteranceContext.audienceResolutionMillis());
            }
        }
        observe(invocation, ConversationLifecycleObserver.Stage.CONTEXT_BUILDING,
                java.util.Map.of("npcId", profile.id().toString()));
        var priorWorkspace = session.epistemicWorkspace().snapshot(Instant.now());
        // Audience resolution has already selected this NPC. Remove only that exact leading
        // vocative for deterministic semantic routing; retain the authoritative utterance for
        // memory, tracing, context and model input.
        String epistemicUtterance = stripLeadingNpcVocative(playerMessage, profile.name());
        var initialEpistemicShadow = EpistemicShadowAnalyzer.analyzeInitial(epistemicUtterance,
                session.epistemicWorkspace());
        persistNewWorkspaceState(profile, session, priorWorkspace.commitments(),
                priorWorkspace.openTopics());
        DialogueRequestState routedRequestState = contextBuilder.requestState(
                session, profile, playerMessage);
        CognitiveContextPlan legacyContextPlan = cognition == null
                ? CognitiveContextPlan.full("LEGACY_PROVIDER_COMPATIBILITY")
                : cognition.routeContext(profile, playerMessage, routedRequestState.mode());
        // One semantic authority owns the live route. The legacy router still supplies profile
        // constraints, but a supported authoritative E0-E8 query cannot simultaneously enter
        // cognition as SIMPLE_SOCIAL while TurnPlanCompiler dispatches it as GROUNDED.
        CognitiveContextPlan contextPlan = cognition == null ? legacyContextPlan
                : EpistemicProductionRoute.context(initialEpistemicShadow,
                        legacyContextPlan);
        var epistemicShadow = EpistemicShadowAnalyzer.withRouteDiagnostics(
                initialEpistemicShadow, legacyContextPlan);
        Instant requestStarted = Instant.now();
        session.touch(requestStarted);
        if (invocation.provider() instanceof ConversationModelRoutingProvider router) {
            latencyLog.accept("LLM model tier session=" + session.sessionId()
                    + " selected=" + router.selectTier(session, profile, playerMessage));
        }
        latencyLog.accept("LLM request start session=" + session.sessionId()
                + " npc=" + session.npcId() + " player=" + session.playerId()
                + " at=" + requestStarted);
        safeAudit(profile, session, responseId, "input",
                audit -> audit.input(profile, session, responseId, playerMessage));

        CompletableFuture<ConversationRateLimiter.Permit> admission =
                rateLimiter.acquire(session.playerId());
        CompletableFuture<ConversationOutcome> future = admission.thenCompose(permit -> {
            if (!invocation.isCurrent()) {
                permit.close();
                return CompletableFuture.failedFuture(
                        new java.util.concurrent.CancellationException(
                                "Orbis branch is no longer current"));
            }
            observe(invocation, ConversationLifecycleObserver.Stage.LLM_QUEUED,
                    java.util.Map.of("queueMs", Long.toString(permit.queueLatencyMillis())));
            latencyLog.accept("LLM queue latency session=" + session.sessionId()
                    + " queueMs=" + permit.queueLatencyMillis());
            responseLatency.recordDuration(responseId, ResponseLatencyStage.LLM_QUEUE_WAIT,
                    permit.queueLatencyMillis());
            long perceptionStartedNanos = System.nanoTime();
            CompletableFuture<RawPerceptionSnapshot> facts = perception == null
                    || !contextPlan.includes("SEMANTIC_WORLD")
                    ? CompletableFuture.completedFuture(
                            RawPerceptionSnapshot.unavailable(responseId, profile.id()))
                    : perception.captureRaw(profile, session.playerId(), responseId);
            return facts.thenApplyAsync(raw -> {
                responseLatency.recordDuration(responseId,
                        ResponseLatencyStage.PERCEPTION_CAPTURE,
                        Duration.ofNanos(Math.max(0, System.nanoTime()
                                - perceptionStartedNanos)).toMillis());
                long normalizationStarted = System.nanoTime();
                SemanticWorldModel semantic = semanticNormalizer.normalize(
                        raw, profile, playerMessage);
                responseLatency.recordDuration(responseId,
                        ResponseLatencyStage.SEMANTIC_NORMALIZATION,
                        Duration.ofNanos(Math.max(0, System.nanoTime()
                                - normalizationStarted)).toMillis());
                return new PerceptionBundle(raw, semantic, null);
            }).thenCompose(perceptionBundle -> {
                if (knownNpcLocator == null
                        || contextPlan.depth() != CognitiveDepth.COMPLEX_INTENT) {
                    return CompletableFuture.completedFuture(perceptionBundle);
                }
                long locatorStarted = System.nanoTime();
                return knownNpcLocator.locate(profile, session, playerMessage)
                        .thenApply(locator -> {
                            responseLatency.recordDuration(responseId,
                                    ResponseLatencyStage.KNOWN_NPC_LOCATION_QUERY,
                                    Duration.ofNanos(Math.max(0, System.nanoTime()
                                            - locatorStarted)).toMillis());
                            return new PerceptionBundle(perceptionBundle.raw(),
                                    perceptionBundle.semantic().withKnownNpcLocator(locator),
                                    locator);
                        });
            }).thenCompose(perceptionBundle -> {
            RawPerceptionSnapshot rawPerception = perceptionBundle.raw();
            SemanticWorldModel semanticWorld = perceptionBundle.semantic();
            NpcPerceptionSnapshot snapshot = rawPerception.engineSnapshot();
            latencyLog.accept("Held-item trace session=" + session.sessionId()
                    + " slot=" + (snapshot.focusedPlayerHotbarSlot() == null
                            ? "unknown" : snapshot.focusedPlayerHotbarSlot())
                    + " actual=" + snapshot.heldItemContextId()
                    + " context=" + snapshot.heldItemContextId());
            NpcActionContext actionContext = new NpcActionContext(
                    profile, session, snapshot, playerMessage, perceptionBundle.locator());
            ConversationGrounding groundingContext = grounding.analyze(
                    session, playerMessage, snapshot);
            latencyLog.accept("Grounding trace session=" + session.sessionId()
                    + " playerMessage=" + compactMessage(playerMessage, 400)
                    + " requested/desire=" + valueOrNone(
                            groundingContext.requestedOrDesiredThing())
                    + " contentValidation=" + groundingContext.contentValidation()
                    + " invalidatedIntent=" + valueOrNone(
                            groundingContext.invalidatedIntent())
                    + " contextConstraint=" + compactMessage(
                            groundingContext.contextConstraint(), 500));
            DialogueRequestState requestState = routedRequestState;
            var rawTools = actions == null
                    ? List.<com.inigmasgames.persistentnpcs.llm.LlmToolDefinition>of()
                    : actions.toolsFor(actionContext, playerMessage);
            CognitionTurn cognitionTurn = cognition == null ? null
                    : cognition.evaluateGrounded(responseId, profile, session, playerMessage,
                            rawPerception, semanticWorld, requestState.mode(), rawTools.stream()
                                    .map(tool -> tool.function().name()).toList(), contextPlan);
            // E2 is a read-only SHADOW consumer of authoritative state already captured for
            // this turn. Its result is carried only in diagnostics/TurnExecutionPlan.
            var completedEpistemicShadow = cognition == null
                    ? new com.inigmasgames.persistentnpcs.epistemic.EpistemicEvidenceRetriever(
                            memories, null, relationships, null, null, null).enrich(
                                    legacyEpistemicShadow(epistemicShadow), responseId,
                                    profile, session.playerId(),
                                    playerMessage, rawPerception, null,
                                    session.epistemicWorkspace(), rawTools.stream()
                                            .map(tool -> tool.function().name()).toList())
                    : cognition.enrichEpistemicShadow(epistemicShadow, responseId, profile,
                            session, playerMessage, rawPerception, cognitionTurn,
                            rawTools.stream().map(tool -> tool.function().name()).toList());
            safeAudit(profile, session, responseId, "epistemic-contract",
                    audit -> audit.epistemicShadow(profile, session, responseId,
                            completedEpistemicShadow));
            safeAudit(profile, session, responseId, "cognition",
                    audit -> audit.cognition(profile, session, responseId,
                            playerMessage, requestState, cognitionTurn));
            if (cognitionTurn != null) {
                latencyLog.accept("NPC cognition session=" + session.sessionId()
                        + " appraisalMs=" + cognitionTurn.appraisalLatencyMillis()
                        + " appraisal={" + cognitionTurn.appraisal().compact() + "}"
                        + " responsePlan=" + cognitionTurn.responsePlan());
                if (cognitionTurn.decision() != null) {
                    latencyLog.accept("NPC_DECISION responseId="
                            + cognitionTurn.decision().responseId()
                            + " beliefUpdates="
                            + cognitionTurn.decision().beliefUpdates().stream()
                                    .map(value -> value.beliefId().toString()).toList()
                            + " selectedIntent="
                            + cognitionTurn.decision().selectedIntent()
                            + " priority=" + cognitionTurn.decision().intentPriority()
                            + " actionRequests="
                            + cognitionTurn.decision().actionRequests()
                            + " relationshipIds="
                            + cognitionTurn.decision().relevantRelationshipIds()
                            + " evidence="
                            + cognitionTurn.decision().groundingEvidenceRefs());
                }
                socialPerformance.perform(profile.id(), session.playerId(),
                        cognitionTurn.responsePlan(), snapshot.environment());
            }
            var tools = rawTools.stream()
                            .filter(tool -> cognitionTurn == null
                                    || cognition.allowTool(tool.function().name(), cognitionTurn))
                            .toList();
            AdaptiveReasoningDecision legacyReasoning = cognitionTurn == null
                    ? new AdaptiveReasoningDecision(
                            AdaptiveReasoningPolicy.GROUNDED_DIALOGUE,
                            List.of("LEGACY_PROVIDER_COMPATIBILITY_NO_HIDDEN_REASONING"))
                    : AdaptiveReasoningRouter.route(contextPlan, requestState.mode(),
                            cognitionTurn, tools.size(), playerMessage);
            AdaptiveReasoningDecision reasoning = EpistemicProductionRoute.reasoning(
                    completedEpistemicShadow, legacyReasoning);
            java.util.Optional<NpcActionRequest> deterministicActionRequest =
                    cognition != null && cognitionTurn != null
                    && reasoning.policy() == AdaptiveReasoningPolicy.DIRECT_ACTION
                    && tools.size() == 1
                            ? cognition.fallbackAction(playerMessage, "I agree.", cognitionTurn)
                            : java.util.Optional.empty();
            boolean deterministicAction = deterministicActionRequest.isPresent();
            boolean discretionaryChoice = !deterministicAction && !tools.isEmpty()
                    && (reasoning.policy() == AdaptiveReasoningPolicy.DIRECT_ACTION
                            || reasoning.policy() == AdaptiveReasoningPolicy.DELIBERATIVE);
            CognitiveContextPlan epistemicContextPlan = EpistemicProductionRoute.context(
                    completedEpistemicShadow, contextPlan);
            TurnPlanCompiler.Draft planDraft = TurnPlanCompiler.draft(epistemicContextPlan, reasoning,
                    deterministicAction, discretionaryChoice,
                    requestState.mode() != DialogueMode.NPC_INITIATED_CURIOSITY);
            CognitiveContextPlan dispatchContextPlan = planDraft.restriction().plan();
            boolean wordingPlan = wordingOnlyContract(cognitionTurn != null,
                    deterministicAction, planDraft.decisionContract());
            // DIALOGUE_TEXT is an immutable plain-text contract. Do not expose action tools to
            // that provider request merely because the NPC has capabilities; doing so can make
            // a grounded question return a tool/JSON-shaped value. Deterministic actions and
            // structured choice contracts retain their existing authoritative paths.
            var dispatchTools = planDraft.decisionContract().structured() || wordingPlan
                    ? List.<com.inigmasgames.persistentnpcs.llm.LlmToolDefinition>of() : tools;
            var contractTools = tools;
            long promptStarted = System.nanoTime();
            LlmRequest baseRequest = contextBuilder.build(
                    session, profile, playerMessage, worldContext, snapshot, dispatchTools,
                    groundingContext, requestState, cognitionTurn,
                    preferCompactOrdinaryPrompt, dispatchContextPlan)
                    .withProviderRequestId(invocation.providerRequestId());
            if (planDraft.decisionContract().structured()) {
                baseRequest = ContractPromptBuilder.compact(baseRequest, profile,
                        playerMessage, cognitionTurn, planDraft, contractTools)
                        .withProviderRequestId(invocation.providerRequestId());
            }
            baseRequest = contextBuilder.applyEpistemicContract(baseRequest,
                    completedEpistemicShadow, profile,
                    session.recentConversationBlock(profile.name(), 2),
                    planDraft.decisionContract().structured());
            baseRequest = ContractMessagePruner.prune(baseRequest,
                    planDraft.contextProfile());
            // One responseId now correlates Orbis branch -> cognition -> provider request.
            NpcDecisionSchema.Contract decisionContract = cognitionTurn == null ? null
                    : NpcDecisionSchema.build(responseId, cognitionTurn, actionContext,
                            contractTools);
            boolean wordingOnly = decisionContract != null && wordingPlan;
            LlmRequest unplannedRequest = decisionContract == null
                    ? generationRequest(baseRequest, reasoning)
                    : !planDraft.decisionContract().structured()
                            ? generationRequest(baseRequest, reasoning)
                            : structuredDecisionRequest(baseRequest, decisionContract, reasoning);
            TurnExecutionPlan executionPlan = TurnPlanCompiler.compile(responseId,
                    invocation.providerRequestId(), invocation.branchEpoch(), planDraft,
                    unplannedRequest.messages(), planDraft.decisionContract().structured()
                            ? decisionContract.schema() : null,
                    cognitionTurn == null || cognitionTurn.context() == null ? List.of()
                            : cognitionTurn.context().evidenceRefs(),
                    completedEpistemicShadow);
            String budgetedPromptHash = SentinelPromptIdentity.hash(
                    unplannedRequest.messages());
            LlmRequest request = unplannedRequest.withGenerationParameters(
                            unplannedRequest.temperatureOverride() == null ? 0.30
                                    : unplannedRequest.temperatureOverride(),
                            executionPlan.decisionContract().maximumOutputTokens())
                    .withTurnExecutionPlan(executionPlan);
            observeSentinel(profile, responseId,
                    SentinelContracts.Boundary.TURN_PLAN_COMPILE,
                    "TURN:" + responseId, java.util.Map.of(
                            "planValid", "true",
                            "route", dispatchContextPlan.detectedIntent(),
                            "outputContract", executionPlan.decisionContract().kind().name()));
            observeSentinel(profile, responseId,
                    SentinelContracts.Boundary.CONTEXT_RENDER_COMPLETE,
                    "TURN:" + responseId, java.util.Map.of(
                            "budgetedPromptHash", budgetedPromptHash,
                            "dispatchedPromptHash", SentinelPromptIdentity.hash(
                                    request.messages())));
            requireSentinel(profile, responseId,
                    SentinelContracts.Boundary.PROVIDER_DISPATCH,
                    completedEpistemicShadow == null ? "TURN:" + responseId
                            : "ROUTE:" + completedEpistemicShadow.queryPlan().queryKind(),
                    java.util.Map.ofEntries(
                            java.util.Map.entry("planValid", "true"),
                            java.util.Map.entry("budgetedPromptHash", budgetedPromptHash),
                            java.util.Map.entry("dispatchedPromptHash",
                                    SentinelPromptIdentity.hash(request.messages())),
                            java.util.Map.entry("actualDispatchFitsBudget",
                                    Boolean.toString(executionPlan.budgets().fits())),
                            java.util.Map.entry("authoritativeMode", Boolean.toString(
                                    completedEpistemicShadow != null
                                    && completedEpistemicShadow.mode()
                                            == EpistemicFeatureMode.AUTHORITATIVE)),
                            java.util.Map.entry("supportedEpistemicRoute", Boolean.toString(
                                    EpistemicProductionRoute.authoritative(
                                            completedEpistemicShadow))),
                            java.util.Map.entry("authoritativeEpistemicContract",
                                    Boolean.toString(EpistemicProductionRoute.authoritative(
                                            executionPlan.epistemicContract()))),
                            java.util.Map.entry("route", dispatchContextPlan.detectedIntent()),
                            java.util.Map.entry("outputContract",
                                    executionPlan.decisionContract().kind().name()),
                            java.util.Map.entry("policyVersion",
                                    com.inigmasgames.persistentnpcs.sentinel.InvariantRegistry
                                            .VERSION)));
            observe(invocation, ConversationLifecycleObserver.Stage.TURN_PLAN_COMPILED,
                    planFacts(executionPlan));
            observe(invocation, ConversationLifecycleObserver.Stage.CONTRACT_BUDGET_PLANNED,
                    budgetFacts(executionPlan));
            responseLatency.recordDuration(responseId,
                    ResponseLatencyStage.PROMPT_CONTEXT_CONSTRUCTION,
                    Duration.ofNanos(Math.max(0, System.nanoTime() - promptStarted)).toMillis());
            int promptCharacters = request.messages().stream()
                    .mapToInt(message -> message.content() == null ? 0 : message.content().length())
                    .sum();
            if (cognition != null) cognition.traces().recordPrompt(
                    profile.id(), responseId, promptCharacters);
            safeAudit(profile, session, responseId, "context-routed",
                    audit -> audit.contextRouted(profile, session, responseId,
                            dispatchContextPlan, promptCharacters));
            latencyLog.accept("LLM_PROMPT_PROFILE session=" + session.sessionId()
                    + " characters=" + promptCharacters + " messages="
                    + request.messages().size() + " offeredActions=" + tools.size()
                    + " structuredDecision=" + (decisionContract != null)
                    + " wordingOnly=" + wordingOnly
                    + " reasoningPolicy=" + reasoning.policy()
                    + " reasoningEnabled=" + reasoning.policy().reasoningEnabled()
                    + " outputBudget=" + reasoning.policy().finalAnswerTokens()
                    + " mode=" + requestState.mode() + " cognitiveDepth="
                    + dispatchContextPlan.depth() + " detectedIntent="
                    + dispatchContextPlan.detectedIntent() + " included="
                    + dispatchContextPlan.includedSections()
                    + " contract=" + executionPlan.decisionContract().kind()
                    + " contextProfile=" + executionPlan.contextProfile().id());
            latencyLog.accept("LLM recent conversation session=" + session.sessionId()
                    + " npc=" + session.npcId() + " player=" + session.playerId()
                    + " block=\n" + session.recentConversationBlock(profile.name(), 6));
            VocalState streamingVocalState = cognitionTurn == null
                    || cognitionTurn.responsePlan() == null
                            ? profile == null ? VocalState.infer("")
                                    : VocalState.infer(playerMessage)
                            : cognitionTurn.responsePlan().vocalState();
            Consumer<String> safeTokenConsumer = decisionContract != null
                    || requestState.mode().buffersStreaming()
                    || !contextPlan.authoritativeConstraints().isEmpty()
                    || !session.invalidatedIntents().isEmpty()
                            ? ignored -> { }
                            : delta -> tokenConsumer.accept(delta, streamingVocalState);
            boolean earlySpeech = wordingOnly
                    && executionPlan.speechContract().earlySpeech()
                    && requestState.mode() == DialogueMode.ORDINARY_CONVERSATION
                    && contextPlan.authoritativeConstraints().isEmpty()
                    && session.invalidatedIntents().isEmpty();
            EarlyPhraseGate phraseGate = earlySpeech ? new EarlyPhraseGate(
                    invocation, responseId, profile, playerMessage, session,
                    snapshot, requestState, contextPlan, cognitionTurn,
                    streamingVocalState, completedEpistemicShadow,
                    totalStartedNanos) : null;
            responseLatency.mark(responseId, ResponseLatencyStage.NEMOTRON_REQUEST_START);
            observe(invocation, ConversationLifecycleObserver.Stage.LLM_DISPATCHED,
                    java.util.Map.ofEntries(
                            java.util.Map.entry("providerRequestId",
                                    invocation.providerRequestId().toString()),
                            java.util.Map.entry("promptCharacters",
                                    Integer.toString(promptCharacters)),
                            java.util.Map.entry("estimatedPromptTokens",
                                    Integer.toString(Math.max(1, (promptCharacters + 3) / 4))),
                            java.util.Map.entry("reasoningPolicy", reasoning.policy().name()),
                            java.util.Map.entry("requestedReasoningMode",
                                    reasoning.policy().reasoningEnabled()
                                            ? "ENABLED" : "DISABLED"),
                            java.util.Map.entry("thinkingEnabled",
                                    Boolean.toString(reasoning.policy().reasoningEnabled())),
                            java.util.Map.entry("routeReasonCodes",
                                    reasoning.reasonCodes().toString()),
                            java.util.Map.entry("outputTokenBudget",
                                    Integer.toString(reasoning.policy().finalAnswerTokens())),
                            java.util.Map.entry("providerTokenBudget",
                                    Integer.toString(reasoning.policy().providerTokenBudget())),
                            java.util.Map.entry("contextSections",
                                    dispatchContextPlan.includedSections().toString()),
                            java.util.Map.entry("epistemicEvidenceIds",
                                    epistemicEvidenceIds(executionPlan).toString()),
                            java.util.Map.entry("epistemicEvidenceSources",
                                    epistemicEvidenceSources(executionPlan).toString()),
                            java.util.Map.entry("turnContract",
                                    executionPlan.decisionContract().kind().name()),
                            java.util.Map.entry("contextProfile",
                                    executionPlan.contextProfile().id()),
                            java.util.Map.entry("memoryCount", Integer.toString(cognitionTurn == null
                                    || cognitionTurn.context() == null ? 0
                                            : cognitionTurn.context().memories().size())),
                            java.util.Map.entry("relationshipCount", Integer.toString(
                                    cognitionTurn == null || cognitionTurn.context() == null ? 0
                                            : cognitionTurn.context().relationships().size())),
                            java.util.Map.entry("recentTurnCount", Integer.toString(
                                    session.recentTurns(contextPlan.depth()
                                            == CognitiveDepth.SIMPLE_SOCIAL ? 2 : 6).size())),
                            java.util.Map.entry("earlyPhraseEligible",
                                    Boolean.toString(earlySpeech))));
            java.util.concurrent.atomic.AtomicBoolean firstStreamToken =
                    new java.util.concurrent.atomic.AtomicBoolean();
            Consumer<String> observedTokenConsumer = delta -> {
                if (!invocation.isCurrent()) return;
                if (delta != null && !delta.isEmpty()
                        && firstStreamToken.compareAndSet(false, true)) {
                    observe(invocation, ConversationLifecycleObserver.Stage.LLM_STREAMING,
                            java.util.Map.of("firstDeltaCharacters",
                                    Integer.toString(delta.length())));
                }
                if (phraseGate != null) phraseGate.accept(delta);
                else safeTokenConsumer.accept(delta);
            };
            CompletableFuture<ResponseBundle> providerTurn;
            if (deterministicActionRequest.isPresent()) {
                if (actions == null || !invocation.isCurrent()) {
                    return CompletableFuture.failedFuture(new IllegalStateException(
                            "Deterministic direct action lost its authoritative executor"));
                }
                NpcActionRequest committedAction = deterministicActionRequest.get();
                // Orbis validates and commits deterministic direct actions before asking the
                // model to describe the authoritative result. The model never chooses or
                // executes an action on this path.
                providerTurn = actions.execute(committedAction, actionContext)
                        .thenCompose(actionResult -> completeAction(request, committedAction,
                                actionResult, actionContext, safeTokenConsumer, requestState,
                                playerMessage, cognitionTurn, invocation));
            } else {
                java.util.function.Supplier<CompletableFuture<PlannedProviderResult>>
                        providerAttempt;
                if (executionPlan.cognitionMode()
                        == TurnExecutionPlan.CognitionMode.DELIBERATIVE) {
                    // Every dispatch for a branch shares the provider request identity. This
                    // makes Orbis cancellation reach a deliberative memo as well as its final.
                    UUID memoRequestId = invocation.providerRequestId();
                    LlmRequest memoBase = request.withSystemInstruction(
                            "Produce a bounded decision memo only: relevant evidence, constraints, "
                            + "and the recommended intent in at most three short sentences. Do not "
                            + "write dialogue, actions, JSON, or hidden step-by-step reasoning.");
                    LlmRequest memoUnplanned = new LlmRequest(memoBase.conversationId(),
                            memoBase.npcId(), memoBase.playerId(), memoBase.messages(), List.of(),
                            null, 0.15, 112, memoRequestId,
                            new LlmExecutionPolicy("DELIBERATIVE_MEMO",
                                    LlmExecutionPolicy.ReasoningMode.ENABLED,
                                    reasoning.reasonCodes(), 112));
                    LlmRequest memoRequest = memoUnplanned.withTurnExecutionPlan(
                            TurnPlanCompiler.deliberativeMemo(executionPlan, memoRequestId,
                                    memoUnplanned.messages()));
                    providerAttempt = () -> invocation.provider().generateResponse(memoRequest,
                                    ignored -> { })
                            .thenCompose(memo -> {
                                if (!invocation.isCurrent()) return CompletableFuture.failedFuture(
                                        new java.util.concurrent.CancellationException(
                                                "Stale deliberative memo completion"));
                                String boundedMemo = compactMessage(memo.text(), 640);
                                LlmRequest finalBase = request.withSystemInstruction(
                                        "ORBIS_BOUNDED_DECISION_MEMO=" + boundedMemo
                                        + "\nNow return only the strict final contract. Do not "
                                        + "continue reasoning or repeat this memo.");
                                LlmRequest finalRequest = finalBase.withTurnExecutionPlan(
                                        TurnPlanCompiler.recompile(executionPlan,
                                                finalBase.messages(), finalBase.responseFormat()));
                                return invocation.provider().generateResponse(finalRequest,
                                                observedTokenConsumer)
                                        .thenApply(result -> new PlannedProviderResult(
                                                finalRequest, result));
                            });
                } else {
                    providerAttempt = () -> invocation.provider().generateResponse(request,
                                    observedTokenConsumer)
                            .thenApply(result -> new PlannedProviderResult(request, result));
                }
                CompletableFuture<PlannedProviderResult> generated = recoverProviderDispatch(
                        providerAttempt.get(), providerAttempt, request, invocation,
                        firstStreamToken);
                providerTurn = generated.thenCompose(dispatch -> {
                        LlmRequest effectiveRequest = dispatch.request();
                        LlmResult result = dispatch.result();
                        if (!invocation.isCurrent()) {
                            return CompletableFuture.failedFuture(
                                    new java.util.concurrent.CancellationException(
                                            "Late provider completion for stale Orbis branch"));
                        }
                        responseLatency.recordDuration(responseId,
                                ResponseLatencyStage.NEMOTRON_TTFT,
                                result.latency().timeToFirstTokenMillis());
                        responseLatency.recordDuration(responseId,
                                ResponseLatencyStage.LLM_GENERATION,
                                Math.max(0, result.latency().completionMillis()
                                        - result.latency().timeToFirstTokenMillis()));
                        if (phraseGate != null) phraseGate.complete(result.text());
                        safeAudit(profile, session, responseId, "model-output",
                                audit -> audit.modelOutput(profile, session, responseId,
                                        result.text(), result.toolCalls(), result.finishReason(),
                                        inferenceAttribution(invocation.provider(),
                                                invocation.providerRequestId())));
                        if (invocation.provider() instanceof LlmRuntimeDiagnosticSource runtimeSource) {
                            JsonObject runtimeDiagnostics = runtimeSource.runtimeDiagnostics(
                                    profile.id());
                            responseLatency.trace(responseId).flatMap(trace -> trace.stages()
                                    .stream().filter(stage -> stage.stage()
                                            == ResponseLatencyStage.LLM_QUEUE_WAIT)
                                    .findFirst()).ifPresent(stage -> runtimeDiagnostics.addProperty(
                                            "queueWaitMillis", stage.durationMillis()));
                            safeAudit(profile, session, responseId, "runtime-diagnostics",
                                    audit -> audit.runtime(profile, session, responseId,
                                            runtimeDiagnostics));
                        }
                        if (cognition != null) cognition.traces().recordModelOutput(
                                profile.id(), responseId, result.text());
                        observe(invocation,
                                ConversationLifecycleObserver.Stage.DECISION_VALIDATING,
                                java.util.Map.ofEntries(
                                        java.util.Map.entry("rawCharacters",
                                                Integer.toString(result.text() == null
                                                        ? 0 : result.text().length())),
                                        java.util.Map.entry("toolCallCount",
                                                Integer.toString(result.toolCalls().size())),
                                        java.util.Map.entry("reasoningPolicy",
                                                reasoning.policy().name()),
                                        java.util.Map.entry("requestedReasoningMode",
                                                result.reasoningTelemetry().requestedMode()),
                                        java.util.Map.entry("actualReasoningMode",
                                                result.reasoningTelemetry().actualMode()),
                                        java.util.Map.entry("thinkingEnabled",
                                                Boolean.toString(result.reasoningTelemetry()
                                                        .thinkingEnabled())),
                                        java.util.Map.entry("reasoningEventCount",
                                                Integer.toString(result.reasoningTelemetry()
                                                        .reasoningEventCount())),
                                        java.util.Map.entry("reasoningTokenCount",
                                                Integer.toString(result.reasoningTelemetry()
                                                        .reasoningTokenCount())),
                                        java.util.Map.entry("finalAnswerTokenCount",
                                                Integer.toString(result.reasoningTelemetry()
                                                        .finalAnswerTokenCount())),
                                        java.util.Map.entry("promptEvaluationMillis",
                                                Long.toString(result.reasoningTelemetry()
                                                        .promptEvaluationMillis()))));
                        return wordingOnly
                                ? resolveWordingDecision(result, actionContext, requestState,
                                        playerMessage, cognitionTurn, decisionContract,
                                        completedEpistemicShadow)
                                : decisionContract == null
                                ? resolveActions(effectiveRequest, result, actionContext,
                                        safeTokenConsumer, requestState, playerMessage,
                                        cognitionTurn, invocation)
                                : resolveStructuredDecision(effectiveRequest, result, actionContext,
                                        requestState, playerMessage, cognitionTurn,
                                        decisionContract, invocation);
                    });
            }
            return providerTurn.thenApply(bundle -> bundle.withPromptCharacters(promptCharacters)
                    .withEarlyPhraseGate(phraseGate)
                    .withEpistemicContract(completedEpistemicShadow));
            }).thenApply(bundle -> {
                    String dialogue;
                    DialogueClaimValidation claimValidation;
                    try {
                        String modelDialogue = bundle.modelDecision() == null
                                ? bundle.result().text() : bundle.modelDecision().spokenText();
                        claimValidation = sanitizeDialogue(profile.name(), playerMessage,
                                modelDialogue, session, bundle.perception(),
                                bundle.requestState(), contextPlan);
                        dialogue = claimValidation.dialogue();
                        AuthoritativeDialogueValidator.Result authoritative =
                                authoritativeValidator.validate(dialogue, contextPlan);
                        if (authoritative.rewritten()) {
                            latencyLog.accept("LLM authoritative fact rewritten session="
                                    + session.sessionId() + " reason="
                                    + authoritative.reason() + " raw="
                                    + compactMessage(dialogue, 400) + " replacement="
                                    + compactMessage(authoritative.dialogue(), 400));
                        }
                        dialogue = authoritative.dialogue();
                        String priorPlayerMessage = session.recentTurns(1).stream()
                                .map(ConversationSession.ConversationTurn::playerMessage)
                                .findFirst().orElse("");
                        boolean materialUpdate = MaterialInformationGuard.containsMaterialUpdate(
                                playerMessage, priorPlayerMessage)
                                || bundle.cognition() != null
                                && bundle.cognition().decision() != null
                                && !bundle.cognition().decision().beliefUpdates().isEmpty();
                        dialogue = DialogueNaturalnessFilter.filterResponse(dialogue,
                                session.recentTurns(3).stream()
                                        .map(ConversationSession.ConversationTurn::npcReply)
                                        .toList(), materialUpdate);
                        dialogue = eliminateGenericAssistantFallback(dialogue,
                                bundle.cognition() == null ? null
                                        : bundle.cognition().decision());
                        EpistemicClaimFirewall.Result epistemicValidation =
                                claimValidator.validateEpistemic(dialogue,
                                        bundle.epistemicContract(),
                                        bundle.actionResult() != null
                                                && bundle.actionResult().success()
                                                        ? bundle.actionResult().code() + " "
                                                                + bundle.actionResult()
                                                                        .eventDescription()
                                                        : "",
                                        true);
                        boolean objectiveClaim = epistemicValidation.claims().stream()
                                .anyMatch(value -> value.claim().objective());
                        boolean propertyAssertion = epistemicValidation.claims().stream()
                                .anyMatch(value -> value.claim().predicateKey()
                                        .startsWith("PROPERTY:"));
                        // Consume the firewall's final repaired verdict; rejected draft clauses
                        // must not make an already-safe deterministic fallback fail again.
                        boolean propertySupported = epistemicValidation.valid();
                        boolean restrictedAnswerability = bundle.epistemicContract() != null
                                && java.util.Set.of("UNKNOWN", "CONFLICTED",
                                        "NEEDS_CLARIFICATION", "NEEDS_CURRENT_PERCEPTION",
                                        "AMBIGUOUS", "UNRESOLVED")
                                        .contains(bundle.epistemicContract().answerability().name());
                        boolean unqualifiedCertainty = restrictedAnswerability
                                && epistemicValidation.claims().stream()
                                        .filter(value -> value.claim().objective())
                                        .anyMatch(com.inigmasgames.persistentnpcs.epistemic
                                                .AtomicClaimResult::releasable);
                        safeAudit(profile, session, responseId, "epistemic-claims",
                                audit -> audit.epistemicClaims(profile, session, responseId,
                                        epistemicValidation));
                        // Invalid drafts belong to the firewall/recovery owner. Sentinel guards
                        // the final commit candidate; calling it with a verdict already known to
                        // be false converts a recoverable EPI-001 containment into provider
                        // failure and a silent NPC turn.
                        if (!epistemicValidation.valid()) {
                            throw new InvalidDialogueException(
                                    "E3 answer plan rejected: " + epistemicValidation.reason());
                        }
                        requireSentinel(profile, responseId,
                                SentinelContracts.Boundary.CLAIM_VALIDATION,
                                bundle.epistemicContract() == null ? "TURN:" + responseId
                                        : "ROUTE:" + bundle.epistemicContract().queryPlan()
                                                .queryKind(),
                                java.util.Map.of(
                                        "objectiveClaim", Boolean.toString(objectiveClaim),
                                        "compatibleClaimVerdict", Boolean.toString(
                                                epistemicValidation.valid()),
                                        "propertyAssertion", Boolean.toString(propertyAssertion),
                                        "propertyLevelSupport", Boolean.toString(
                                                propertySupported),
                                        "answerabilityRestricted", Boolean.toString(
                                                restrictedAnswerability),
                                        "unqualifiedCertainty", Boolean.toString(
                                                unqualifiedCertainty)));
                        dialogue = epistemicValidation.dialogue();
                        dialogue = CanonicalDialogueAssembler.assemble(dialogue);
                        dialogue = SpokenTextSafetyValidator.requireSafe(dialogue);
                        requireSentinel(profile, responseId,
                                SentinelContracts.Boundary.SPEECH_LEDGER_APPEND,
                                "TURN:" + responseId, java.util.Map.of(
                                        "objectiveClaim", Boolean.toString(objectiveClaim),
                                        "compatibleClaimVerdict", Boolean.toString(
                                                epistemicValidation.valid()),
                                        "canonicalSpansValid", "true"));
                        if (bundle.earlyPhraseGate() != null) {
                            dialogue = bundle.earlyPhraseGate().reconcileCanonical(dialogue);
                        }
                        if (bundle.modelDecision() != null) {
                            String committedCandidate = dialogue;
                            ActionPromiseGuard.violation(committedCandidate,
                                    bundle.modelDecision().actions()).ifPresent(reason -> {
                                        throw new InvalidDialogueException(reason);
                                    });
                        }
                        if (cognition != null) cognition.traces().recordCanonical(profile.id(),
                                responseId, "scene=" + claimValidation.reason()
                                        + "; authority=" + authoritative.reason()
                                        + "; epistemic=" + epistemicValidation.reason()
                                        + "; spokenSafety=accepted",
                                dialogue);
                        String canonicalDialogue = dialogue;
                        safeAudit(profile, session, responseId, "canonical-response",
                                audit -> audit.canonical(profile, session, responseId,
                                        bundle.rawModelText(), canonicalDialogue,
                                        "scene=" + claimValidation.reason() + "; authority="
                                                + authoritative.reason()
                                                + "; epistemic=" + epistemicValidation.reason()
                                                + "; spokenSafety=accepted"));
                        if (cognition != null && bundle.cognition() != null) {
                            cognition.finalizeDecision(profile.id(), bundle.cognition(),
                                    bundle.modelDecision(), dialogue);
                            if (bundle.decisionDiagnostics() != null) cognition.traces()
                                    .recordStructuredDecision(profile.id(), responseId,
                                            bundle.decisionDiagnostics()
                                                    .withCanonicalSpokenText(dialogue));
                        }
                    } catch (InvalidDialogueException failure) {
                        latencyLog.accept("LLM dialogue rejected session=" + session.sessionId()
                                + " reason=" + failure.getMessage()
                                + " raw=" + compactMessage(bundle.rawModelText(), 400));
                        safeAudit(profile, session, responseId, "dialogue-rejected",
                                audit -> audit.rejected(profile, session, responseId,
                                        playerMessage, bundle.rawModelText(),
                                        failure.getMessage(), bundle.cognition()));
                        if (diagnosticProbe(playerMessage)) {
                            latencyLog.accept("DIALOGUE_DIAG stage=6 session="
                                    + session.sessionId() + " parsedDialogue=INVALID reason="
                                    + failure.getMessage());
                            latencyLog.accept("DIALOGUE_DIAG stage=7 session="
                                    + session.sessionId()
                                    + " finalHytaleChat=NO_NPC_DIALOGUE");
                        }
                        throw failure;
                    }
                    latencyLog.accept("Dialogue grounding trace session=" + session.sessionId()
                            + " dialogueMode=" + bundle.requestState().mode()
                            + " hasActiveTask=" + bundle.requestState().hasActiveTask()
                            + " hasActiveQuest=" + bundle.requestState().hasActiveQuest()
                            + " directorContextIncluded="
                            + bundle.requestState().directorContextIncluded()
                            + " fictionalStoryMode="
                            + (bundle.requestState().mode() == DialogueMode.FICTIONAL_STORY)
                            + " authoritativeLocation="
                            + authoritativeLocation(bundle.perception())
                            + " claimedCurrentAction="
                            + claimValidation.claimedCurrentAction()
                            + " rewritten=" + claimValidation.rewritten()
                            + " reason=" + compactMessage(claimValidation.reason(), 240));
                    if (diagnosticProbe(playerMessage)) {
                        latencyLog.accept("DIALOGUE_DIAG stage=6 session="
                                + session.sessionId() + " parsedDialogue=" + dialogue);
                        latencyLog.accept("DIALOGUE_DIAG stage=7 session="
                                + session.sessionId() + " finalHytaleChat="
                                + profile.name() + ": " + dialogue);
                    }
                    Instant completedAt = Instant.now();
                    relationships.recordCompletedInteraction(profile.id(), session.playerId(),
                            profile.defaultDisposition(), completedAt);
                    if (cognition == null && bundle.requestState().mode()
                            != DialogueMode.NPC_INITIATED_CURIOSITY) {
                        persistExplicitPlayerFact(profile.id(), session.playerId(),
                                playerMessage, completedAt);
                    }
                    session.touch(completedAt);
                    long totalMillis = Duration.ofNanos(
                            Math.max(0, System.nanoTime() - totalStartedNanos)).toMillis();
                    VocalState vocalState = bundle.modelDecision() != null
                            ? bundle.modelDecision().paralinguisticEvent()
                                    .map(event -> VocalState.forEmotion(
                                            bundle.modelDecision().emotion()).withEvent(event))
                                    .orElseGet(() -> VocalState.forEmotion(
                                            bundle.modelDecision().emotion()))
                            : bundle.cognition() == null
                            || bundle.cognition().responsePlan() == null
                                    ? VocalState.infer(dialogue)
                                    : bundle.cognition().responsePlan().vocalState();
                    NpcDecision committedDecision = bundle.modelDecision() == null ? null
                            : bundle.modelDecision().withSpokenText(dialogue);
                    ConversationOutcome outcome = new ConversationOutcome(session.sessionId(),
                            dialogue, bundle.result().latency(), totalMillis,
                            vocalState, bundle.requestState().mode(), responseId,
                            invocation.providerRequestId(), committedDecision,
                            bundle.decisionDiagnostics(), bundle.result().usage(),
                            bundle.rawModelText(), contextPlan.depth(),
                            contextPlan.includedSections().stream().sorted().toList(),
                            bundle.promptCharacters(),
                            bundle.cognition() == null || bundle.cognition().context() == null
                                    ? 0 : bundle.cognition().context().scoredMemories().size(),
                            bundle.cognition() == null || bundle.cognition().context() == null
                                    ? 0 : bundle.cognition().context().relationships().size());
                    lastOutcome = outcome;
                    latencyLog.accept("LLM response session=" + session.sessionId()
                            + " requestStart=" + bundle.result().latency().requestStartedAt()
                            + " streaming=" + bundle.result().latency().streaming()
                            + " timeToFirstTokenMs="
                            + bundle.result().latency().timeToFirstTokenMillis()
                            + " completionMs=" + bundle.result().latency().completionMillis()
                            + " totalConversationMs=" + totalMillis);
                    responseLatency.complete(responseId);
                    String completedDialogue = dialogue;
                    safeAudit(profile, session, responseId, "turn-completed",
                            audit -> audit.completed(profile, session, responseId,
                                    playerMessage, bundle.rawModelText(), completedDialogue,
                                    bundle.requestState(), bundle.cognition(),
                                    bundle.actionResult(), bundle.result().latency(), totalMillis,
                                    responseLatency.trace(responseId).orElse(null)));
                    latencyLog.accept("R030_LATENCY responseId=" + responseId + " "
                            + responseLatency.compact(responseId));
                    return outcome;
                }).whenComplete((ignored, failure) -> permit.close());
        });
        ActiveInvocation activeInvocation = new ActiveInvocation(session,
                invocation.provider(), invocation.providerRequestId(), future);
        activeInvocations.put(responseId, activeInvocation);
        inFlight.add(future);
        future.whenComplete((ignored, failure) -> {
            if (failure != null) {
                Throwable cause = unwrap(failure);
                long failedAfterMillis = Duration.ofNanos(
                        Math.max(0, System.nanoTime() - totalStartedNanos)).toMillis();
                latencyLog.accept("LLM request failed session=" + session.sessionId()
                        + " elapsedMs=" + failedAfterMillis
                        + " type=" + cause.getClass().getSimpleName()
                        + " reason=" + compactMessage(cause.getMessage(), 400));
                safeAudit(profile, session, responseId, "turn-failed",
                        audit -> audit.failed(profile, session, responseId, playerMessage, cause,
                                responseLatency.trace(responseId).orElse(null),
                                inferenceAttribution(invocation.provider(),
                                        invocation.providerRequestId())));
            }
            if (failure != null) responseLatency.complete(responseId);
            session.finishRequest(responseId);
            activeInvocations.remove(responseId, activeInvocation);
            inFlight.remove(future);
            observeSentinel(profile, responseId,
                    SentinelContracts.Boundary.TERMINAL_CLEANUP,
                    "TURN:" + responseId, java.util.Map.of(
                            "terminalTransitionCount", "1",
                            "cleanupAcquireCount", "1",
                            "cleanupReleaseCount", "1",
                            "historyWritten", "false",
                            "playbackConfirmed", "false"));
        });
        return future;
    }

    /**
     * One bounded recovery for a provider failure that produced no dialogue token. Contract or
     * claim failures occur after this boundary and are deliberately not handled here. If the
     * same provider fails twice before producing content, the plan's explicit deterministic
     * recovery dialogue proceeds through the normal realization/firewall/ledger path.
     */
    private CompletableFuture<PlannedProviderResult> recoverProviderDispatch(
            CompletableFuture<PlannedProviderResult> initial,
            java.util.function.Supplier<CompletableFuture<PlannedProviderResult>> retry,
            LlmRequest request, ConversationInvocation invocation,
            java.util.concurrent.atomic.AtomicBoolean firstStreamToken) {
        return initial.exceptionallyCompose(failure -> {
            Throwable cause = unwrap(failure);
            if (!retryableProviderFailure(cause) || firstStreamToken.get()
                    || !invocation.isCurrent()) {
                return CompletableFuture.failedFuture(cause);
            }
            TurnExecutionPlan plan = request.turnExecutionPlan();
            String category = cause.getClass().getSimpleName();
            if (!RecoverySupervisor.tryAcquire(plan, "PROVIDER_ZERO_TOKEN_" + category)) {
                return deterministicProviderRecovery(request, invocation, cause,
                        "RECOVERY_ALLOWANCE_ALREADY_CONSUMED");
            }
            observe(invocation, ConversationLifecycleObserver.Stage.RECOVERY_ATTEMPTED,
                    java.util.Map.of(
                            "reason", "PROVIDER_ZERO_TOKEN_" + category,
                            "attempt", "1", "maximumAttempts", "1",
                            "failureReason", compactMessage(cause.getMessage(), 240)));
            CompletableFuture<PlannedProviderResult> retried;
            try {
                retried = retry.get();
            } catch (RuntimeException retryDispatchFailure) {
                return deterministicProviderRecovery(request, invocation,
                        unwrap(retryDispatchFailure), "RETRY_DISPATCH_FAILED");
            }
            return retried.thenApply(result -> {
                observe(invocation, ConversationLifecycleObserver.Stage.RECOVERY_SUCCEEDED,
                        java.util.Map.of("reason", "PROVIDER_ZERO_TOKEN_" + category,
                                "attempt", "1", "recoveryMode", "SAME_MODEL_RETRY"));
                return result;
            }).exceptionallyCompose(secondFailure -> {
                Throwable second = unwrap(secondFailure);
                if (!retryableProviderFailure(second) || firstStreamToken.get()
                        || !invocation.isCurrent()) {
                    return CompletableFuture.failedFuture(second);
                }
                return deterministicProviderRecovery(request, invocation, second,
                        "SAME_MODEL_RETRY_FAILED");
            });
        });
    }

    private CompletableFuture<PlannedProviderResult> deterministicProviderRecovery(
            LlmRequest request, ConversationInvocation invocation, Throwable failure,
            String reason) {
        TurnExecutionPlan plan = request.turnExecutionPlan();
        String dialogue = plan == null ? "" : plan.recoveryPolicy()
                .deterministicRecoveryDialogue();
        if (dialogue.isBlank()) return CompletableFuture.failedFuture(failure);
        String category = failure == null ? "UNKNOWN" : failure.getClass().getSimpleName();
        observe(invocation, ConversationLifecycleObserver.Stage.RECOVERY_EXHAUSTED,
                java.util.Map.of("reason", reason, "providerFailureCategory", category,
                        "providerFailureReason", compactMessage(
                                failure == null ? "unknown" : failure.getMessage(), 240),
                        "attempts", Integer.toString(RecoverySupervisor.attempts(
                                plan == null ? null : plan.responseId())),
                        "terminalFallback", "DETERMINISTIC_RECOVERY_DIALOGUE"));
        observe(invocation, ConversationLifecycleObserver.Stage.RECOVERY_SUCCEEDED,
                java.util.Map.of("reason", reason, "recoveryMode",
                        "DETERMINISTIC_RECOVERY_DIALOGUE"));
        Instant now = Instant.now();
        LlmResult result = new LlmResult(dialogue,
                new LlmLatency(now, 0, 0, false), List.of(), "orbis_recovery",
                LlmUsage.unknown(), LlmReasoningTelemetry.unknown());
        return CompletableFuture.completedFuture(new PlannedProviderResult(request, result));
    }

    private static boolean retryableProviderFailure(Throwable failure) {
        return failure instanceof LlmProviderException
                || failure instanceof java.io.IOException;
    }

    /**
     * Orbis cancellation hook. It is thread-safe and performs no Hytale/world work.
     * Session ownership is released immediately; the provider closes the matching transport.
     */
    public void cancelForOrbis(UUID responseId, UUID providerRequestId) {
        if (responseId == null) return;
        ActiveInvocation active = activeInvocations.remove(responseId);
        if (active == null) return;
        UUID requestId = providerRequestId == null
                ? active.providerRequestId() : providerRequestId;
        try { active.provider().cancel(requestId); }
        catch (RuntimeException failure) {
            latencyLog.accept("LLM cancellation failed responseId=" + responseId
                    + " providerRequestId=" + requestId + " reason="
                    + compactMessage(failure.getMessage(), 240));
        }
        active.session().finishRequest(responseId);
        active.future().cancel(true);
    }

    private static LlmInferenceAttribution inferenceAttribution(
            LlmProvider selectedProvider, UUID providerRequestId) {
        return selectedProvider instanceof LlmAttributionSource source
                ? source.attribution(providerRequestId).orElse(null) : null;
    }

    private record ActiveInvocation(ConversationSession session, LlmProvider provider,
            UUID providerRequestId, CompletableFuture<ConversationOutcome> future) { }

    private void observe(ConversationInvocation invocation,
            ConversationLifecycleObserver.Stage stage, java.util.Map<String, String> facts) {
        try {
            invocation.observer().onStage(stage, facts == null ? java.util.Map.of() : facts);
        } catch (RuntimeException failure) {
            latencyLog.accept("ORBIS_DIAGNOSTIC_OBSERVER_FAILED responseId="
                    + invocation.responseId() + " stage=" + stage + " reason="
                    + compactMessage(failure.getMessage(), 240));
        }
    }

    private void observeSentinel(NpcProfile profile, UUID responseId,
            SentinelContracts.Boundary boundary, String scopeKey,
            java.util.Map<String, String> facts) {
        OrbisDegradationSentinel sentinel = degradationSentinel;
        if (sentinel == null) return;
        try {
            sentinel.observe(new SentinelObservation(boundary, scopeKey,
                    profile == null ? null : profile.id(),
                    responseId == null ? java.util.List.of()
                            : java.util.List.of("responseId=" + responseId), facts));
        } catch (RuntimeException failure) {
            latencyLog.accept("ORBIS_SENTINEL_OBSERVER_FAILED boundary=" + boundary
                    + " reason=" + compactMessage(failure.getMessage(), 240));
        }
    }

    private void requireSentinel(NpcProfile profile, UUID responseId,
            SentinelContracts.Boundary boundary, String scopeKey,
            java.util.Map<String, String> facts) {
        OrbisDegradationSentinel sentinel = degradationSentinel;
        if (sentinel == null) return;
        sentinel.requireAllowed(new SentinelObservation(boundary, scopeKey,
                profile == null ? null : profile.id(),
                responseId == null ? java.util.List.of()
                        : java.util.List.of("responseId=" + responseId), facts));
    }

    private static java.util.Map<String, String> planFacts(TurnExecutionPlan plan) {
        var epistemic = plan.epistemicContract();
        return java.util.Map.ofEntries(
                java.util.Map.entry("cognitionMode", plan.cognitionMode().name()),
                java.util.Map.entry("contextProfile", plan.contextProfile().id()),
                java.util.Map.entry("decisionContract", plan.decisionContract().kind().name()),
                java.util.Map.entry("speechContract", plan.speechContract().mode().name()),
                java.util.Map.entry("schemaVersion", plan.decisionContract().schemaVersion()),
                java.util.Map.entry("branchEpoch", Long.toString(plan.branchEpoch())),
                java.util.Map.entry("earlySpeech",
                        Boolean.toString(plan.speechContract().earlySpeech())),
                java.util.Map.entry("firstTokenDeadlineMs",
                        Long.toString(plan.deadlines().firstTokenMillis())),
                java.util.Map.entry("reasoningDeadlineMs",
                        Long.toString(plan.deadlines().reasoningMillis())),
                java.util.Map.entry("providerHardDeadlineMs",
                        Long.toString(plan.deadlines().providerHardMillis())),
                java.util.Map.entry("recoveryAttempts",
                        Integer.toString(plan.recoveryPolicy().maximumAttempts())),
                java.util.Map.entry("includedSections",
                        plan.contextProfile().allowedSections().toString()),
                java.util.Map.entry("omittedSections",
                        plan.omittedContextSections().toString()),
                java.util.Map.entry("pruningReason", plan.pruningReason()),
                java.util.Map.entry("evidenceIds", plan.evidenceIds().toString()),
                java.util.Map.entry("epistemicMode", epistemic == null ? "NONE"
                        : epistemic.mode().name()),
                java.util.Map.entry("epistemicQueryKind", epistemic == null ? "NONE"
                        : epistemic.queryPlan().queryKind()),
                java.util.Map.entry("epistemicAnswerability", epistemic == null ? "NONE"
                        : epistemic.answerability().name()),
                java.util.Map.entry("epistemicAnswerPlanStatus", epistemic == null ? "NONE"
                        : epistemic.answerPlan().status()),
                java.util.Map.entry("epistemicEvidenceIds",
                        epistemicEvidenceIds(plan).toString()),
                java.util.Map.entry("epistemicEvidenceSources",
                        epistemicEvidenceSources(plan).toString()),
                java.util.Map.entry("epistemicRequestedAction", epistemic == null ? ""
                        : epistemic.answerPlan().requestedAction()),
                java.util.Map.entry("epistemicEvidenceCount", Integer.toString(
                        epistemic == null ? 0 : epistemic.evidence().supporting().size()
                                + epistemic.evidence().contextual().size())));
    }

    private static java.util.Set<String> epistemicEvidenceIds(TurnExecutionPlan plan) {
        if (plan == null || plan.epistemicContract() == null) return java.util.Set.of();
        return java.util.stream.Stream.concat(
                        plan.epistemicContract().evidence().supporting().stream(),
                        plan.epistemicContract().evidence().contextual().stream())
                .map(com.inigmasgames.persistentnpcs.epistemic.EvidenceRef::stableId)
                .filter(value -> value != null && !value.isBlank())
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
    }

    private static java.util.Set<String> epistemicEvidenceSources(TurnExecutionPlan plan) {
        if (plan == null || plan.epistemicContract() == null) return java.util.Set.of();
        return java.util.stream.Stream.concat(
                        plan.epistemicContract().evidence().supporting().stream(),
                        plan.epistemicContract().evidence().contextual().stream())
                .map(value -> value.sourceKind().name())
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
    }

    private static java.util.Map<String, String> budgetFacts(TurnExecutionPlan plan) {
        var budget = plan.budgets();
        return java.util.Map.ofEntries(
                java.util.Map.entry("contextWindowTokens",
                        Integer.toString(budget.contextWindowTokens())),
                java.util.Map.entry("promptTokens", Integer.toString(budget.promptTokens())),
                java.util.Map.entry("schemaTokens", Integer.toString(budget.schemaTokens())),
                java.util.Map.entry("reasoningReserveTokens",
                        Integer.toString(budget.reasoningReserveTokens())),
                java.util.Map.entry("finalAnswerReserveTokens",
                        Integer.toString(budget.finalAnswerReserveTokens())),
                java.util.Map.entry("safetyMarginTokens",
                        Integer.toString(budget.safetyMarginTokens())),
                java.util.Map.entry("worstCaseSerializedTokens",
                        Integer.toString(budget.boundedWorstCaseSerializedTokens())),
                java.util.Map.entry("requiredOutputTokens",
                        Integer.toString(budget.requiredOutputTokens())),
                java.util.Map.entry("totalReservedTokens",
                        Integer.toString(budget.totalReservedTokens())),
                java.util.Map.entry("fits", Boolean.toString(budget.fits())),
                java.util.Map.entry("rejectionReason", budget.rejectionReason()));
    }

    /** A diagnostic sink is never allowed to fail, cancel, or rewrite a live NPC turn. */
    private void safeAudit(NpcProfile profile, ConversationSession session, UUID responseId,
            String stage, java.util.function.Consumer<NpcTurnAuditLog> operation) {
        NpcTurnAuditLog audit = turnAuditLog;
        if (audit == null) return;
        try {
            operation.accept(audit);
        } catch (RuntimeException failure) {
            latencyLog.accept("TRACE_DIAGNOSTIC_FAILED responseId=" + responseId
                    + " stage=" + stage + " type=" + failure.getClass().getSimpleName()
                    + " reason=" + compactMessage(failure.getMessage(), 400));
            try {
                audit.diagnosticFailure(profile, session, responseId, stage, failure);
            } catch (RuntimeException ignored) {
                // The primitive fallback diagnostic is best-effort; the NPC turn continues.
            }
        }
    }

    private static LlmRequest generationRequest(LlmRequest base,
            AdaptiveReasoningDecision reasoning) {
        LlmExecutionPolicy policy = reasoning.llmPolicy();
        return new LlmRequest(base.conversationId(), base.npcId(), base.playerId(),
                base.messages(), base.tools(), null, 0.30,
                reasoning.policy().providerTokenBudget(), base.providerRequestId(), policy);
    }

    private static LlmRequest structuredDecisionRequest(LlmRequest base,
            NpcDecisionSchema.Contract contract, AdaptiveReasoningDecision reasoning) {
        String instruction = "Return exactly one JSON object matching the supplied NPC_DECISION "
                + "schema. spokenText contains only natural words the NPC says. Choose only an "
                + "offered action and grounded IDs. Ordinary conversation uses actions=[]. "
                + "Classify claims by meaning: SAFE_SOCIAL/SUBJECTIVE includes preferences, "
                + "desires, opinions, emotions, reactions, hypotheticals, and social invitations; "
                + "those need no world or memory evidence. MEMORY_FACT requires MEMORY or BELIEF. "
                + "WORLD_FACT requires PERCEPTION or ENVIRONMENT. ACTION/EXECUTION_CLAIM requires "
                + "an authoritative action result/state. Claims about your friends, relatives, "
                + "possessions, or personal past are objective autobiographical facts and require "
                + "the matching authored RELATIONSHIP/PROFILE or MEMORY/BELIEF reference. A "
                + "RELATIONSHIP reference cannot support a witnessed event, named location, "
                + "object, creature, shared history, or current world fact. When no MEMORY, "
                + "BELIEF, PERCEPTION, or ENVIRONMENT reference is offered, keep the response "
                + "social and do not invent those facts. "
                + "Never promise a concrete action unless that matching action is present in the "
                + "same object. Do not output markdown or reasoning. NPC_DECISION_SCHEMA="
                + JsonFiles.GSON.toJson(contract.schema());
        return base.withSystemInstruction(instruction)
                .constrained(contract.responseFormat(), 0.0,
                        reasoning.policy().providerTokenBudget())
                .withExecutionPolicy(new LlmExecutionPolicy(
                        reasoning.policy().name() + "_FINAL",
                        LlmExecutionPolicy.ReasoningMode.DISABLED,
                        reasoning.reasonCodes(), reasoning.policy().finalAnswerTokens()));
    }

    private CompletableFuture<ResponseBundle> resolveWordingDecision(
            LlmResult result, NpcActionContext context, DialogueRequestState requestState,
            String playerMessage, CognitionTurn cognitionTurn,
            NpcDecisionSchema.Contract contract, EpistemicContract epistemicContract) {
        GroundedNpcDecision grounded = cognitionTurn == null ? null : cognitionTurn.decision();
        if (grounded == null) {
            return CompletableFuture.failedFuture(new IllegalStateException(
                    "Orbis wording-only request had no deterministic cognition decision"));
        }
        String spoken = CanonicalDialogueAssembler.assemble(result.text());
        NpcDecision decision = new NpcDecision(grounded.responseId(), context.profile().id(),
                grounded.selectedIntent(), spoken, grounded.emotion(),
                grounded.paralinguisticEvent(), List.of(),
                grounded.groundingEvidenceRefs());
        NpcGroundingClaimValidator groundingValidator = new NpcGroundingClaimValidator();
        List<NpcGroundingClaimValidator.ClaimAssessment> claims = groundingValidator.validate(
                spoken, decision.groundingEvidenceRefs());
        List<String> diagnostics = claims.stream()
                .map(NpcGroundingClaimValidator.ClaimAssessment::diagnostic).toList();
        List<String> unsupported = claims.stream().filter(value -> !value.valid())
                .map(NpcGroundingClaimValidator.ClaimAssessment::diagnostic).toList();
        boolean typedSubjectiveAuthority = EpistemicProductionRoute.authoritative(
                epistemicContract) && epistemicContract.answerability()
                        == com.inigmasgames.persistentnpcs.epistemic.Answerability.SUBJECTIVE;
        if (!unsupported.isEmpty() && !typedSubjectiveAuthority) {
            String truthful = safeGroundingFallback(playerMessage);
            decision = decision.withGroundedFallback(truthful);
            NpcDecisionDiagnostics fallback = new NpcDecisionDiagnostics(
                    offeredActionNames(contract), result.text(),
                    "ORBIS_WORDING_GROUNDING_FALLBACK", unsupported, "NO_ACTION", "none",
                    truthful, "UNSUPPORTED_CLAIM_REPLACED", decision, diagnostics);
            recordDecisionDiagnostics(context, cognitionTurn, fallback);
            return CompletableFuture.completedFuture(new ResponseBundle(
                    withText(result, truthful), null, context.perception(), requestState,
                    cognitionTurn, decision, fallback, result.text()));
        }
        if (!unsupported.isEmpty() && typedSubjectiveAuthority) {
            diagnostics = new java.util.ArrayList<>(diagnostics);
            diagnostics.add("DEFERRED_TO_AUTHORITATIVE_EPISTEMIC_CLAIM_FIREWALL");
            diagnostics = List.copyOf(diagnostics);
        }
        java.util.Optional<String> promise = ActionPromiseGuard.violation(spoken, List.of());
        if (promise.isPresent()) {
            return CompletableFuture.failedFuture(new InvalidDialogueException(promise.get()));
        }
        NpcDecisionDiagnostics accepted = new NpcDecisionDiagnostics(
                offeredActionNames(contract), result.text(), "ORBIS_WORDING_ONLY_VALID",
                List.of(), "NO_ACTION", "none", spoken, "NO_ACTION", decision, diagnostics);
        recordDecisionDiagnostics(context, cognitionTurn, accepted);
        return CompletableFuture.completedFuture(new ResponseBundle(
                withText(result, spoken), null, context.perception(), requestState,
                cognitionTurn, decision, accepted, result.text()));
    }

    static boolean wordingOnlyContract(boolean hasCognition, boolean deterministicAction,
            com.inigmasgames.persistentnpcs.conversation.contract.DecisionContract contract) {
        return hasCognition && !deterministicAction && contract != null
                && contract.kind()
                        == com.inigmasgames.persistentnpcs.conversation.contract.DecisionContract
                                .Kind.DIALOGUE_TEXT
                && !contract.structured();
    }

    private CompletableFuture<ResponseBundle> resolveStructuredDecision(
            LlmRequest initialRequest,
            LlmResult initialResult,
            NpcActionContext context,
            DialogueRequestState requestState,
            String playerMessage,
            CognitionTurn cognitionTurn,
            NpcDecisionSchema.Contract contract,
            ConversationInvocation invocation) {
        ProviderOutcomeClassifier.Outcome providerOutcome =
                ProviderOutcomeClassifier.classify(initialResult,
                        initialRequest.turnExecutionPlan());
        if (providerOutcome == ProviderOutcomeClassifier.Outcome.TRUNCATED_OUTPUT) {
            observe(invocation, ConversationLifecycleObserver.Stage.TRUNCATED_OUTPUT,
                    java.util.Map.of("classification", providerOutcome.name(),
                            "finishReason", String.valueOf(initialResult.finishReason()),
                            "completionTokens", Integer.toString(
                                    initialResult.usage().completionTokens())));
        }
        NpcDecisionValidator.Validation validation = decisionValidator.validate(
                initialResult.text(), cognitionTurn.decision().responseId(),
                context.profile().id(), contract);
        if (!validation.valid()) {
            observe(invocation, ConversationLifecycleObserver.Stage.CONTRACT_INVALID,
                    java.util.Map.of("classification", providerOutcome.name(),
                            "validation", validation.result()));
            boolean retryable = !"GROUNDING_REJECTED".equals(validation.result())
                    && initialRequest.turnExecutionPlan() != null
                    && initialRequest.turnExecutionPlan().recoveryPolicy()
                            .plannerCorrectedStructuredRetry();
            if (retryable && RecoverySupervisor.tryAcquire(
                    initialRequest.turnExecutionPlan(), providerOutcome.name())) {
                observe(invocation, ConversationLifecycleObserver.Stage.RECOVERY_ATTEMPTED,
                        java.util.Map.of("reason", providerOutcome.name(),
                                "attempt", "1", "maximumAttempts", "1"));
                LlmRequest correctedBase = initialRequest.withSystemInstruction(
                        "The prior strict output was incomplete or invalid. Retry once from the "
                        + "same evidence. Return one complete JSON object matching the schema. "
                        + "Do not continue the prior text, add commentary, or output reasoning.");
                LlmRequest corrected = correctedBase.withTurnExecutionPlan(
                        TurnPlanCompiler.recompile(initialRequest.turnExecutionPlan(),
                                correctedBase.messages(), correctedBase.responseFormat()));
                return invocation.provider().generateResponse(corrected, ignored -> { })
                        .thenCompose(retry -> resolveStructuredDecision(corrected, retry,
                                context, requestState, playerMessage, cognitionTurn, contract,
                                invocation))
                        .thenApply(bundle -> {
                            observe(invocation,
                                    ConversationLifecycleObserver.Stage.RECOVERY_SUCCEEDED,
                                    java.util.Map.of("reason", providerOutcome.name(),
                                            "attempt", "1"));
                            return bundle;
                        });
            }
            if (retryable) observe(invocation,
                    ConversationLifecycleObserver.Stage.RECOVERY_EXHAUSTED,
                    java.util.Map.of("reason", providerOutcome.name(),
                            "attempts", Integer.toString(RecoverySupervisor.attempts(
                                    initialRequest.turnExecutionPlan().responseId()))));
            NpcDecisionDiagnostics diagnostics = new NpcDecisionDiagnostics(
                    offeredActionNames(contract), initialResult.text(), validation.result(),
                    validation.rejectedFieldsOrActions(), "NOT_RUN", "none", "", "", null,
                    validation.groundingValidation());
            if ("GROUNDING_REJECTED".equals(validation.result())
                    && validation.decision() != null) {
                String truthful = safeGroundingFallback(playerMessage);
                NpcDecision safe = validation.decision().withGroundedFallback(truthful);
                NpcDecisionDiagnostics fallbackDiagnostics = new NpcDecisionDiagnostics(
                        offeredActionNames(contract), initialResult.text(),
                        "GROUNDING_REJECTED_SAFE_FALLBACK",
                        validation.rejectedFieldsOrActions(), "NO_ACTION", "none",
                        truthful, "UNSUPPORTED_CLAIM_REPLACED", safe,
                        validation.groundingValidation());
                recordDecisionDiagnostics(context, cognitionTurn, fallbackDiagnostics);
                return CompletableFuture.completedFuture(new ResponseBundle(
                        withText(initialResult, truthful), null, context.perception(),
                        requestState, cognitionTurn, safe, fallbackDiagnostics,
                        initialResult.text()));
            }
            recordDecisionDiagnostics(context, cognitionTurn, diagnostics);
            return CompletableFuture.failedFuture(new InvalidDialogueException(
                    "structured NPC decision rejected: "
                            + String.join("; ", validation.rejectedFieldsOrActions())));
        }
        observe(invocation, ConversationLifecycleObserver.Stage.CONTRACT_VALID,
                java.util.Map.of("classification", providerOutcome.name(),
                        "validation", validation.result()));
        NpcDecision decision = validation.decision();
        if (decision.actions().isEmpty()) {
            NpcDecisionDiagnostics diagnostics = new NpcDecisionDiagnostics(
                    offeredActionNames(contract), initialResult.text(), "VALID", List.of(),
                    "NO_ACTION", "none", decision.spokenText(), "NO_ACTION", decision,
                    validation.groundingValidation());
            recordDecisionDiagnostics(context, cognitionTurn, diagnostics);
            return CompletableFuture.completedFuture(new ResponseBundle(
                    withText(initialResult, decision.spokenText()), null, context.perception(),
                    requestState, cognitionTurn, decision, diagnostics, initialResult.text()));
        }
        if (actions == null) {
            return CompletableFuture.failedFuture(new IllegalStateException(
                    "Structured decision emitted an action while actions are disabled"));
        }
        NpcDecisionAction selected = decision.actions().getFirst();
        NpcActionRequest request = new NpcActionRequest(selected.actionId(),
                selected.parameters(), "decision:" + decision.responseId(),
                decision.responseId(), selected.actorStableId(), selected.targetStableId());
        NpcActionResult freshValidation = actions.validate(request, context);
        if (!freshValidation.success()) {
            return CompletableFuture.completedFuture(failedActionBundle(initialResult, context,
                    requestState, cognitionTurn, contract, decision, freshValidation,
                    "REJECTED:" + freshValidation.code()));
        }
        if (!invocation.isCurrent()) {
            return CompletableFuture.failedFuture(new java.util.concurrent.CancellationException(
                    "Superseded structured decision cannot execute actions"));
        }
        return actions.execute(request, context).thenApply(actionResult -> {
            if (!actionResult.success()) return failedActionBundle(initialResult, context,
                    requestState, cognitionTurn, contract, decision, actionResult,
                    "EXECUTION_FAILED:" + actionResult.code());
            rememberActionResult(context, actionResult);
            if (cognition != null) cognition.ingestActionResult(context.profile().id(),
                    context.session().playerId(), request, actionResult);
            String operation = committedOperation(context.profile().id(), decision.responseId(),
                    selected.actionId());
            NpcDecisionDiagnostics diagnostics = new NpcDecisionDiagnostics(
                    offeredActionNames(contract), initialResult.text(), "VALID", List.of(),
                    "VALID:OK", operation, decision.spokenText(),
                    actionResult.code() + ":" + actionResult.eventDescription(), decision,
                    validation.groundingValidation());
            recordDecisionDiagnostics(context, cognitionTurn, diagnostics);
            return new ResponseBundle(withText(initialResult, decision.spokenText()),
                    actionResult, context.perception(), requestState, cognitionTurn, decision,
                    diagnostics, initialResult.text());
        });
    }

    private ResponseBundle failedActionBundle(LlmResult initialResult, NpcActionContext context,
            DialogueRequestState requestState, CognitionTurn cognitionTurn,
            NpcDecisionSchema.Contract contract, NpcDecision decision,
            NpcActionResult actionResult, String actionValidation) {
        rememberActionResult(context, actionResult);
        NpcDecision truthfulDecision = NpcDecisionCommitPolicy.truthfulFailure(
                decision, actionResult);
        String truthful = truthfulDecision.spokenText();
        NpcDecisionDiagnostics diagnostics = new NpcDecisionDiagnostics(
                offeredActionNames(contract), initialResult.text(), "VALID", List.of(),
                actionValidation, "none", truthful,
                actionResult.code() + ":" + actionResult.eventDescription(), truthfulDecision);
        recordDecisionDiagnostics(context, cognitionTurn, diagnostics);
        return new ResponseBundle(withText(initialResult, truthful), actionResult,
                context.perception(), requestState, cognitionTurn, truthfulDecision,
                diagnostics, initialResult.text());
    }

    private static String safeGroundingFallback(String playerMessage) {
        String text = playerMessage == null ? "" : playerMessage.toLowerCase(
                java.util.Locale.ROOT);
        if (text.matches(".*\\b(?:hello|hi|hey|greetings|how are you)\\b.*")) {
            return "I'm doing all right. How about you?";
        }
        if (text.contains("?")) return "I'm not certain enough to answer that.";
        return "I don't know enough to say that for certain.";
    }

    private static com.inigmasgames.persistentnpcs.epistemic.EpistemicContract
            legacyEpistemicShadow(
                    com.inigmasgames.persistentnpcs.epistemic.EpistemicContract value) {
        if (value == null || value.mode() != EpistemicFeatureMode.AUTHORITATIVE) return value;
        return new com.inigmasgames.persistentnpcs.epistemic.EpistemicContract(
                value.schemaVersion(), EpistemicFeatureMode.SHADOW, value.dialogueFrame(),
                value.queryPlan(), value.evidence(), value.answerability(), value.answerPlan(),
                value.claimPolicy(), value.budget(), value.diagnoses(), value.planningMicros(),
                value.compiledAt());
    }

    private void persistNewWorkspaceState(NpcProfile profile,
            ConversationSession session, List<String> prior, List<String> priorTopics) {
        if (memories == null || profile == null || session == null) return;
        Set<String> existing = new java.util.LinkedHashSet<>(prior == null ? List.of() : prior);
        for (String value : session.epistemicWorkspace().snapshot(Instant.now()).commitments()) {
            if (existing.contains(value)) continue;
            memories.append(new MemoryRecord(UUID.randomUUID(), profile.id(),
                    session.playerId(), Instant.now(), MemoryType.COMMITMENT, .72,
                    value, .9, "E5_CONVERSATION_WORKSPACE_COMMITMENT",
                    List.of(session.playerId()), "", "Open conversational commitment."));
        }
        Set<String> topics = new java.util.LinkedHashSet<>(priorTopics == null
                ? List.of() : priorTopics);
        for (String value : session.epistemicWorkspace().snapshot(Instant.now()).openTopics()) {
            if (topics.contains(value)) continue;
            memories.append(new MemoryRecord(UUID.randomUUID(), profile.id(),
                    session.playerId(), Instant.now(), MemoryType.COMMITMENT, .68,
                    value, .9, "E5_CONVERSATION_WORKSPACE_OPEN_TOPIC",
                    List.of(session.playerId()), "", "Important unresolved conversation topic."));
        }
    }

    private void rememberActionResult(NpcActionContext context, NpcActionResult actionResult) {
        memories.append(new MemoryRecord(UUID.randomUUID(), context.profile().id(),
                context.session().playerId(), Instant.now(), MemoryType.ACTION_RESULT,
                actionResult.success() ? 0.75 : 0.55, actionResult.eventDescription()));
    }

    private String committedOperation(UUID npcId, UUID responseId, String actionId) {
        if (cognition == null) return "action=" + actionId + ";responseId=" + responseId;
        return cognition.activeOperation(npcId)
                .map(value -> value.operationId() + " " + value.kind() + " " + value.status()
                        + " responseId=" + responseId)
                .orElse("none (immediate action=" + actionId + ";responseId="
                        + responseId + ")");
    }

    private void recordDecisionDiagnostics(NpcActionContext context, CognitionTurn cognitionTurn,
            NpcDecisionDiagnostics diagnostics) {
        if (cognition != null) cognition.traces().recordStructuredDecision(
                context.profile().id(), cognitionTurn.decision().responseId(), diagnostics);
        safeAudit(context.profile(), context.session(), cognitionTurn.decision().responseId(),
                "structured-decision", audit -> audit.structuredDecision(context.profile(),
                        context.session(), cognitionTurn.decision().responseId(), cognitionTurn,
                        diagnostics));
    }

    private static List<String> offeredActionNames(NpcDecisionSchema.Contract contract) {
        return contract.offeredTools().stream().map(value -> value.function().name()).toList();
    }

    private static LlmResult withText(LlmResult source, String text) {
        return new LlmResult(text, source.latency(), List.of(), source.finishReason(),
                source.usage(), source.reasoningTelemetry());
    }

    private CompletableFuture<ResponseBundle> resolveActions(
            LlmRequest initialRequest,
            LlmResult initialResult,
            NpcActionContext context,
            Consumer<String> tokenConsumer,
            DialogueRequestState requestState,
            String playerMessage,
            CognitionTurn cognitionTurn,
            ConversationInvocation invocation) {
        if (!invocation.isCurrent()) {
            return CompletableFuture.failedFuture(new java.util.concurrent.CancellationException(
                    "Superseded response cannot execute actions"));
        }
        if (initialResult.toolCalls().isEmpty()) {
            if (actions != null && cognition != null && cognitionTurn != null) {
                var fallback = cognition.fallbackAction(
                        playerMessage, initialResult.text(), cognitionTurn);
                if (fallback.isPresent()) {
                    latencyLog.accept("Follow trace session=" + context.session().sessionId()
                            + " followAuthorized=true actionRequested=true"
                            + " source=cognition-agreement-fallback");
                    if (!invocation.isCurrent()) {
                        return CompletableFuture.failedFuture(
                                new java.util.concurrent.CancellationException(
                                        "Superseded response cannot execute actions"));
                    }
                    return actions.execute(fallback.get(), context).thenCompose(actionResult ->
                            completeAction(initialRequest, fallback.get(), actionResult, context,
                                    tokenConsumer, requestState, playerMessage, cognitionTurn,
                                    invocation));
                }
                if ("FOLLOW_PLAYER".equals(cognitionTurn.appraisal().requestedAction())) {
                    latencyLog.accept("Follow trace session=" + context.session().sessionId()
                            + " followAuthorized="
                            + cognitionTurn.appraisal().actionAuthorized()
                            + " actionRequested=false failureReason="
                            + (cognitionTurn.appraisal().actionAuthorized()
                                    ? "model did not call tool or clearly agree"
                                    : cognitionTurn.appraisal().authorizationReason()));
                }
            }
            return CompletableFuture.completedFuture(new ResponseBundle(
                    initialResult, null, context.perception(), requestState, cognitionTurn));
        }
        if (actions == null) {
            return CompletableFuture.failedFuture(
                    new IllegalStateException("LLM emitted a tool call while actions are disabled"));
        }
        if (initialResult.toolCalls().size() != 1) {
            NpcActionRequest rejected = new NpcActionRequest(
                    "MULTIPLE_ACTIONS", new com.google.gson.JsonObject(), "");
            return completeAction(initialRequest, rejected,
                    NpcActionResult.failure("MULTIPLE_ACTIONS",
                            "The server rejected a batch of multiple actions; request one action at a time."),
                    context, tokenConsumer, requestState, playerMessage, invocation);
        }
        var tool = initialResult.toolCalls().get(0);
        com.google.gson.JsonObject parameters;
        try {
            parameters = tool.arguments() == null || tool.arguments().isBlank()
                    ? new com.google.gson.JsonObject()
                    : JsonFiles.GSON.fromJson(tool.arguments(), com.google.gson.JsonObject.class);
            if (parameters == null) {
                throw new IllegalArgumentException("Tool parameters were JSON null");
            }
        } catch (RuntimeException failure) {
            NpcActionRequest rejected = new NpcActionRequest(
                    tool.name(), new com.google.gson.JsonObject(), tool.id());
            return completeAction(initialRequest, rejected,
                    NpcActionResult.failure("INVALID_PARAMETERS",
                            "The server rejected malformed JSON parameters for " + tool.name() + "."),
                    context, tokenConsumer, requestState, playerMessage, invocation);
        }
        NpcActionRequest request = new NpcActionRequest(
                tool.name(), parameters, tool.id()).normalized();
        if (cognition != null && cognitionTurn != null
                && !cognition.allowTool(request.id(), cognitionTurn)) {
            return completeAction(initialRequest, request,
                    NpcActionResult.failure("SOCIAL_DECISION_DENIED",
                            cognitionTurn.appraisal().authorizationReason()), context,
                    tokenConsumer, requestState, playerMessage, cognitionTurn, invocation);
        }
        if ("FOLLOW_PLAYER".equals(request.id())) {
            latencyLog.accept("Follow trace session=" + context.session().sessionId()
                    + " followAuthorized=" + (cognitionTurn == null
                            || cognitionTurn.appraisal().actionAuthorized())
                    + " actionRequested=true source=model-tool");
        }
        if (!invocation.isCurrent()) {
            return CompletableFuture.failedFuture(new java.util.concurrent.CancellationException(
                    "Superseded response cannot execute actions"));
        }
        return actions.execute(request, context).thenCompose(actionResult ->
                completeAction(initialRequest, request, actionResult, context, tokenConsumer,
                        requestState, playerMessage, cognitionTurn, invocation));
    }

    private CompletableFuture<ResponseBundle> completeAction(
            LlmRequest initialRequest,
            NpcActionRequest request,
            NpcActionResult actionResult,
            NpcActionContext context,
            Consumer<String> tokenConsumer,
            DialogueRequestState requestState,
            String playerMessage,
            ConversationInvocation invocation) {
        return completeAction(initialRequest, request, actionResult, context, tokenConsumer,
                requestState, playerMessage, null, invocation);
    }

    private CompletableFuture<ResponseBundle> completeAction(
            LlmRequest initialRequest,
            NpcActionRequest request,
            NpcActionResult actionResult,
            NpcActionContext context,
            Consumer<String> tokenConsumer,
            DialogueRequestState requestState,
            String playerMessage,
            CognitionTurn cognitionTurn,
            ConversationInvocation invocation) {
            Instant now = Instant.now();
            memories.append(new MemoryRecord(UUID.randomUUID(), context.profile().id(),
                    context.session().playerId(), now, MemoryType.ACTION_RESULT,
                    actionResult.success() ? 0.75 : 0.55,
                    actionResult.eventDescription()));
            LlmRequest followUp = initialRequest.withSystemInstruction(
                    "The authoritative game server processed action " + request.id()
                            + ". success=" + actionResult.success()
                            + ", code=" + actionResult.code()
                            + ", result=" + actionResult.eventDescription()
                            + " Respond in character. State failures honestly; do not claim "
                            + "anything beyond this server result.");
            if (followUp.turnExecutionPlan() != null) {
                followUp = followUp.withTurnExecutionPlan(TurnPlanCompiler.recompile(
                        followUp.turnExecutionPlan(), followUp.messages(),
                        followUp.responseFormat()));
            }
            if (!invocation.isCurrent()) return CompletableFuture.failedFuture(
                    new java.util.concurrent.CancellationException(
                            "Superseded response cannot request action follow-up"));
            return invocation.provider().generateResponse(followUp, tokenConsumer)
                    .thenApply(result -> new ResponseBundle(
                            result, actionResult, context.perception(),
                            actionResult.success()
                                    ? contextBuilder.requestState(context.session(),
                                            context.profile(), playerMessage)
                                    : requestState,
                            cognitionTurn));
    }

    public ConversationOutcome lastOutcome() {
        return lastOutcome;
    }

    public void recordCommittedSpokenText(
            UUID npcId, UUID responseId, String committedText) {
        if (cognition != null && committedText != null && !committedText.isBlank()) {
            cognition.recordCommittedText(npcId, responseId, committedText);
        }
    }

    /** Phase 4 history boundary: invoked only from native playback terminal truth. */
    public void recordDeliveredConversation(ConversationSession session, NpcProfile profile,
            String playerMessage, UUID responseId, String deliveredText,
            DialogueMode mode, boolean interrupted) {
        if (session == null || profile == null || responseId == null) return;
        DialogueMode safeMode = mode == null ? DialogueMode.ORDINARY_CONVERSATION : mode;
        String exactDelivered = deliveredText == null ? "" : deliveredText.strip();
        String historyReply = exactDelivered;
        if (interrupted) {
            historyReply += (historyReply.isBlank() ? "" : " ")
                    + "[Speech interrupted; no later generated chunks were delivered.]";
        }
        if (historyReply.isBlank()) return;
        Instant now = Instant.now();
        if (safeMode != DialogueMode.FICTIONAL_STORY
                && safeMode != DialogueMode.NPC_INITIATED_CURIOSITY) {
            memories.append(new MemoryRecord(UUID.randomUUID(), profile.id(),
                    session.playerId(), now, MemoryType.CONVERSATION, 0.35,
                    summarize(playerMessage, historyReply), 0.45,
                    "CONVERSATION_HISTORY:ACTUALLY_DELIVERED;response=" + responseId
                            + ";interrupted=" + interrupted,
                    List.of(session.playerId()), "",
                    "This records native-playback delivery, not verified world knowledge."));
        }
        session.appendTurn(safeMode == DialogueMode.NPC_INITIATED_CURIOSITY
                        ? "" : playerMessage,
                historyReply, safeMode, now);
        session.touch(now);
    }

    public void markFirstCanonicalSpeechChunk(UUID responseId) {
        responseLatency.mark(responseId, ResponseLatencyStage.FIRST_CANONICAL_SPEECH_CHUNK);
    }

    public ResponseLatencyTraceStore responseLatency() {
        return responseLatency;
    }

    public String providerDescription() {
        return provider.description();
    }

    public CompletableFuture<Void> warmUpProvider() {
        return provider.warmUp();
    }

    public CompletableFuture<LlmProviderStatus> checkProviderStatus() {
        return provider.checkStatus();
    }

    public void shutdown() {
        inFlight.forEach(future -> future.cancel(true));
        inFlight.clear();
        rateLimiter.close();
    }

    public void endSession(UUID sessionId) {
        provider.endSession(sessionId);
    }

    private static String summarize(String playerMessage, String dialogue) {
        String summary = "Player said: \"" + compact(playerMessage, 220)
                + "\" NPC replied: \"" + compact(dialogue, 320) + "\"";
        return compact(summary, 600);
    }

    private static String eliminateGenericAssistantFallback(
            String dialogue, GroundedNpcDecision decision) {
        String value = dialogue == null ? "" : dialogue.strip();
        String lower = value.toLowerCase(java.util.Locale.ROOT);
        boolean generic = value.isBlank() || lower.contains("what would you like to explore")
                || lower.contains("how can i help you")
                || lower.contains("is there anything else")
                || lower.contains("what can i assist you with");
        if (!generic) return value;
        if (decision == null) return "I have nothing grounded to add yet.";
        return switch (decision.selectedIntent()) {
            case SEEK_INFORMATION -> "I don't know that yet. Tell me what you know.";
            case RESPOND_TO_DANGER -> "That's serious. Stay alert while I verify what I can.";
            case RESPOND_TO_RELATIONSHIP ->
                    "That matters to me. Tell me exactly what you know.";
            case RESPOND_TO_REMOTE_HAIL -> "I'm here. Follow my voice.";
            case HONOR_OBLIGATION -> "I haven't forgotten what I owe.";
            case CONTINUE_SHARED_PLAN -> "Our plan still stands.";
            case REFUSE_UNGROUNDED_ACTION -> "I can't do that from what I know right now.";
            case PROCESS_INFORMATION ->
                    "I heard you. I'll treat that as your report until I can confirm it.";
            case EXECUTE_DIRECT_REQUEST -> "Understood.";
            case PURSUE_GOAL, INVESTIGATE -> "That's worth looking into.";
            case REPORT_KNOWN_NPC_LOCATION ->
                    "I found them nearby, but I have nothing more useful to add.";
            case OFFER_GUIDE_TO_NPC -> "I can lead you to them, if you want.";
            case GUIDE_PLAYER_TO_NPC -> "Stay close. I'll lead you there.";
            case REPORT_UNABLE_TO_LOCATE ->
                    "I can't locate them from what I can verify right now.";
            case AMBIENT_RESPONSE -> "I've nothing useful to add to that.";
        };
    }

    private void persistExplicitPlayerFact(
            UUID npcId, UUID playerId, String playerMessage, Instant at) {
        Matcher matcher = STATED_NAME.matcher(playerMessage == null ? "" : playerMessage.strip());
        if (matcher.find()) {
            memories.append(new MemoryRecord(UUID.randomUUID(), npcId, playerId, at,
                    MemoryType.PLAYER_FACT, 0.95,
                    "Player fact: stated name=" + matcher.group(1) + ".", 0.82,
                    "DIRECT", List.of(playerId), "",
                    "The player stated this; it was not independently verified."));
        }
    }

    private DialogueClaimValidation sanitizeDialogue(
            String npcName,
            String playerMessage,
            String modelText,
            ConversationSession session,
            NpcPerceptionSnapshot perceptionSnapshot,
            DialogueRequestState requestState,
            CognitiveContextPlan contextPlan) {
        String dialogue = modelText == null ? "" : modelText.strip();
        String speakerPrefix = npcName + ":";
        if (dialogue.regionMatches(true, 0, speakerPrefix, 0, speakerPrefix.length())) {
            dialogue = dialogue.substring(speakerPrefix.length()).strip();
        }
        if (dialogue.isBlank()) {
            throw new InvalidDialogueException("model returned empty dialogue");
        }
        String normalizedDialogue = dialogue.replaceAll("[.!?,;:]+$", "").strip();
        if (normalizedDialogue.equalsIgnoreCase(npcName)
                && !asksNpcName(playerMessage)) {
            throw new InvalidDialogueException(
                    "model returned only the NPC identity for a non-name question");
        }
        String groundedDialogue = contextPlan.depth() == CognitiveDepth.COMPLEX_INTENT
                ? grounding.enforceModelDialogue(session, dialogue, perceptionSnapshot)
                : dialogue;
        if (!groundedDialogue.equals(dialogue)) {
            latencyLog.accept("LLM repeated invalidated desire session=" + session.sessionId()
                    + " raw=" + compactMessage(dialogue, 400));
            dialogue = groundedDialogue;
        }
        if (dialogue.length() > maximumDialogueCharacters) {
            dialogue = dialogue.substring(0, maximumDialogueCharacters).stripTrailing() + "...";
        }
        DialogueClaimValidation validation = claimValidator.validate(
                requestState.mode(), playerMessage, dialogue, requestState,
                perceptionSnapshot.environment());
        if (validation.rewritten()) {
            latencyLog.accept("LLM current-scene claim rewritten session="
                    + session.sessionId() + " reason=" + validation.reason()
                    + " raw=" + compactMessage(dialogue, 400)
                    + " replacement=" + compactMessage(validation.dialogue(), 400));
        }
        return validation;
    }

    private static boolean asksNpcName(String message) {
        String normalized = message == null ? ""
                : message.toLowerCase(java.util.Locale.ROOT)
                        .replaceAll("[^a-z0-9' ]", " ").replaceAll("\\s+", " ").strip();
        return normalized.contains("your name")
                || normalized.contains("who are you")
                || normalized.contains("what are you called");
    }

    private static boolean diagnosticProbe(String message) {
        String normalized = message == null ? "" : message.toLowerCase(java.util.Locale.ROOT)
                .strip().replaceAll("[.!?]+$", "").strip();
        return normalized.equals("greetings") || normalized.equals("how are you");
    }

    private static String compact(String text, int maximum) {
        String normalized = text.replaceAll("\\s+", " ").trim();
        return normalized.length() <= maximum ? normalized : normalized.substring(0, maximum) + "...";
    }

    static String stripLeadingNpcVocative(String utterance, String npcName) {
        String value = utterance == null ? "" : utterance.strip()
                .replaceFirst("^[\\\"'“”‘’]+", "");
        String name = npcName == null ? "" : npcName.strip();
        if (name.isBlank()) return value;
        return value.replaceFirst("(?iu)^" + Pattern.quote(name)
                + "\\s*[,.:;-]\\s*", "").strip();
    }

    private static String compactMessage(String text, int maximum) {
        String normalized = text == null ? "no message"
                : text.replaceAll("\\s+", " ").trim();
        return normalized.length() <= maximum
                ? normalized : normalized.substring(0, maximum) + "...";
    }

    private static String valueOrNone(String value) {
        return value == null || value.isBlank() ? "none" : value;
    }

    private static String authoritativeLocation(NpcPerceptionSnapshot perception) {
        if (perception == null || perception.npcEntityId() == null) return "unavailable";
        return "world=" + perception.worldId() + "@"
                + "%.1f,%.1f,%.1f".formatted(perception.x(), perception.y(), perception.z());
    }

    private static Throwable unwrap(Throwable failure) {
        Throwable current = failure;
        while ((current instanceof java.util.concurrent.CompletionException
                || current instanceof java.util.concurrent.ExecutionException)
                && current.getCause() != null) {
            current = current.getCause();
        }
        return current;
    }

    /** Releases only complete, deterministically safe FAST dialogue phrases to Orbis. */
    private final class EarlyPhraseGate {
        private final ConversationInvocation invocation;
        private final UUID responseId;
        private final NpcProfile profile;
        private final String playerMessage;
        private final ConversationSession session;
        private final NpcPerceptionSnapshot perception;
        private final DialogueRequestState requestState;
        private final CognitiveContextPlan contextPlan;
        private final CognitionTurn cognitionTurn;
        private final VocalState vocalState;
        private final com.inigmasgames.persistentnpcs.epistemic.EpistemicContract
                epistemicContract;
        private final long requestStartedNanos = System.nanoTime();
        private final List<String> committed = new ArrayList<>();
        private final SpeechPhraseChunker chunker;
        private boolean blocked;

        private EarlyPhraseGate(ConversationInvocation invocation, UUID responseId,
                NpcProfile profile, String playerMessage, ConversationSession session,
                NpcPerceptionSnapshot perception, DialogueRequestState requestState,
                CognitiveContextPlan contextPlan, CognitionTurn cognitionTurn,
                VocalState vocalState,
                com.inigmasgames.persistentnpcs.epistemic.EpistemicContract epistemicContract,
                long ignoredConversationStartedNanos) {
            this.invocation = invocation;
            this.responseId = responseId;
            this.profile = profile;
            this.playerMessage = playerMessage;
            this.session = session;
            this.perception = perception;
            this.requestState = requestState;
            this.contextPlan = contextPlan;
            this.cognitionTurn = cognitionTurn;
            this.vocalState = vocalState;
            this.epistemicContract = epistemicContract;
            this.chunker = SpeechPhraseChunker.exact((index, phrase, state) ->
                    validateAndCommit(index, phrase));
        }

        private void accept(String delta) {
            if (!blocked && invocation.isCurrent()) chunker.accept(delta, vocalState);
        }

        private void complete(String finalText) {
            if (!blocked && invocation.isCurrent()) chunker.complete(finalText, vocalState);
        }

        private void validateAndCommit(int index, String phrase) {
            if (blocked || !invocation.isCurrent()) return;
            long completeMillis = Duration.ofNanos(Math.max(0,
                    System.nanoTime() - requestStartedNanos)).toMillis();
            if (index == 0) responseLatency.recordDuration(responseId,
                    ResponseLatencyStage.FIRST_COMPLETE_PHRASE, completeMillis);
            try {
                String exact = SpokenTextSafetyValidator.requireSafe(phrase);
                DialogueClaimValidation scene = sanitizeDialogue(profile.name(), playerMessage,
                        exact, session, perception, requestState, contextPlan);
                AuthoritativeDialogueValidator.Result authority =
                        authoritativeValidator.validate(scene.dialogue(), contextPlan);
                if (!exact.equals(scene.dialogue()) || !exact.equals(authority.dialogue())) {
                    blocked = true;
                    return;
                }
                List<String> evidence = cognitionTurn == null || cognitionTurn.decision() == null
                        ? List.of() : cognitionTurn.decision().groundingEvidenceRefs();
                List<NpcGroundingClaimValidator.ClaimAssessment> claims =
                        new NpcGroundingClaimValidator().validate(exact, evidence);
                if (claims.stream().anyMatch(value -> !value.valid())
                        || ActionPromiseGuard.violation(exact, List.of()).isPresent()) {
                    blocked = true;
                    return;
                }
                EpistemicClaimFirewall.Result epistemic = claimValidator.validateEpistemic(
                        exact, epistemicContract, false, index == 0);
                safeAudit(profile, session, responseId, "early-epistemic-phrase",
                        audit -> audit.epistemicClaims(profile, session,
                                responseId, epistemic));
                // Immutable speech may be released only if the firewall accepts it verbatim.
                if (!epistemic.valid() || epistemic.repaired()
                        || !exact.equals(epistemic.dialogue())) {
                    blocked = true;
                    latencyLog.accept("E3 early phrase held response=" + responseId
                            + " index=" + index + " reason=" + epistemic.reason());
                    return;
                }
                committed.add(exact);
                long validatedMillis = Duration.ofNanos(Math.max(0,
                        System.nanoTime() - requestStartedNanos)).toMillis();
                if (index == 0) responseLatency.recordDuration(responseId,
                        ResponseLatencyStage.FIRST_VALIDATED_PHRASE, validatedMillis);
                String authorityClass = claims.stream().map(
                                NpcGroundingClaimValidator.ClaimAssessment::category)
                        .distinct().collect(java.util.stream.Collectors.joining(","));
                observe(invocation, ConversationLifecycleObserver.Stage.PHRASE_VALIDATED,
                        java.util.Map.ofEntries(
                                java.util.Map.entry("chunkIndex", Integer.toString(index)),
                                java.util.Map.entry("canonicalPhrase", exact),
                                java.util.Map.entry("authorityClass", authorityClass),
                                java.util.Map.entry("firstPhrase",
                                        Boolean.toString(index == 0)),
                                java.util.Map.entry("firstCompletePhraseMs",
                                        Long.toString(completeMillis)),
                                java.util.Map.entry("firstValidatedPhraseMs",
                                        Long.toString(validatedMillis)),
                                java.util.Map.entry("emotion", vocalState.emotion().name()),
                                java.util.Map.entry("paralinguisticEvent", index == 0
                                        ? vocalState.paralinguisticEvent()
                                                .map(value -> value.name()).orElse("") : "")));
            } catch (RuntimeException rejected) {
                blocked = true;
                latencyLog.accept("R058 early phrase held response=" + responseId
                        + " index=" + index + " reason="
                        + compactMessage(rejected.getMessage(), 240));
            }
        }

        private String reconcileCanonical(String canonicalDialogue) {
            String reconciled = retainCommittedPrefix(committed, canonicalDialogue);
            if (!reconciled.equals(canonicalDialogue)) {
                latencyLog.accept("R059 immutable early prefix retained response=" + responseId
                        + " committedChunks=" + committed.size()
                        + " action=DISCARD_REJECTED_OR_REWRITTEN_SUFFIX");
            }
            return reconciled;
        }
    }

    /** Already displayed/audible lexical truth wins over any later whole-response rewrite. */
    static String retainCommittedPrefix(List<String> committed, String proposedCanonical) {
        String proposed = CanonicalDialogueAssembler.assemble(proposedCanonical);
        if (committed == null || committed.isEmpty()) return proposed;
        String prefix = committed.stream().filter(value -> value != null && !value.isBlank())
                .map(CanonicalDialogueAssembler::assemble)
                .collect(java.util.stream.Collectors.joining(" "));
        if (prefix.isBlank()) return proposed;
        return proposed.equals(prefix) || proposed.startsWith(prefix + " ")
                ? proposed : prefix;
    }

    private record PlannedProviderResult(LlmRequest request, LlmResult result) { }

    private record ResponseBundle(
            LlmResult result,
            NpcActionResult actionResult,
            NpcPerceptionSnapshot perception,
            DialogueRequestState requestState,
            CognitionTurn cognition,
            NpcDecision modelDecision,
            NpcDecisionDiagnostics decisionDiagnostics,
            String rawModelText,
            int promptCharacters,
            EarlyPhraseGate earlyPhraseGate,
            com.inigmasgames.persistentnpcs.epistemic.EpistemicContract epistemicContract) {

        private ResponseBundle(LlmResult result, NpcActionResult actionResult,
                NpcPerceptionSnapshot perception, DialogueRequestState requestState,
                CognitionTurn cognition, NpcDecision modelDecision,
                NpcDecisionDiagnostics decisionDiagnostics, String rawModelText) {
            this(result, actionResult, perception, requestState, cognition, modelDecision,
                    decisionDiagnostics, rawModelText, 0, null, null);
        }

        private ResponseBundle(LlmResult result, NpcActionResult actionResult,
                NpcPerceptionSnapshot perception, DialogueRequestState requestState,
                CognitionTurn cognition) {
            this(result, actionResult, perception, requestState, cognition, null, null,
                    result == null ? "" : result.text(), 0, null, null);
        }

        public ResponseBundle {
            rawModelText = rawModelText == null ? "" : rawModelText;
            promptCharacters = Math.max(0, promptCharacters);
        }

        private ResponseBundle withPromptCharacters(int value) {
            return new ResponseBundle(result, actionResult, perception, requestState,
                    cognition, modelDecision, decisionDiagnostics, rawModelText, value,
                    earlyPhraseGate, epistemicContract);
        }

        private ResponseBundle withEarlyPhraseGate(EarlyPhraseGate value) {
            return new ResponseBundle(result, actionResult, perception, requestState,
                    cognition, modelDecision, decisionDiagnostics, rawModelText,
                    promptCharacters, value, epistemicContract);
        }

        private ResponseBundle withEpistemicContract(
                com.inigmasgames.persistentnpcs.epistemic.EpistemicContract value) {
            return new ResponseBundle(result, actionResult, perception, requestState,
                    cognition, modelDecision, decisionDiagnostics, rawModelText,
                    promptCharacters, earlyPhraseGate, value);
        }
    }

    private record PerceptionBundle(
            RawPerceptionSnapshot raw, SemanticWorldModel semantic,
            KnownNpcLocatorResult locator) { }
}
