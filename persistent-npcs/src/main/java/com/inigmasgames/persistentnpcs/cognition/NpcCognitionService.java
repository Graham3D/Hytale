package com.inigmasgames.persistentnpcs.cognition;

import com.google.gson.JsonObject;
import com.inigmasgames.persistentnpcs.action.NpcActionRequest;
import com.inigmasgames.persistentnpcs.conversation.ConversationSession;
import com.inigmasgames.persistentnpcs.conversation.DialogueMode;
import com.inigmasgames.persistentnpcs.conversation.CognitiveContextPlan;
import com.inigmasgames.persistentnpcs.conversation.CognitiveContextRouter;
import com.inigmasgames.persistentnpcs.perception.EnvironmentSnapshot;
import com.inigmasgames.persistentnpcs.perception.NpcPerceptionSnapshot;
import com.inigmasgames.persistentnpcs.perception.RawPerceptionSnapshot;
import com.inigmasgames.persistentnpcs.perception.SemanticPerceptionNormalizer;
import com.inigmasgames.persistentnpcs.perception.SemanticWorldModel;
import com.inigmasgames.persistentnpcs.perception.KnownNpcLocatorResult;
import com.inigmasgames.persistentnpcs.perception.KnownNpcLocationStatus;
import com.inigmasgames.persistentnpcs.profile.NpcProfile;
import com.inigmasgames.persistentnpcs.relationship.RelationshipRecord;
import com.inigmasgames.persistentnpcs.relationship.RelationshipStore;
import com.inigmasgames.persistentnpcs.profile.NpcProfileRegistry;
import com.inigmasgames.persistentnpcs.memory.MemoryRecord;
import com.inigmasgames.persistentnpcs.memory.MemoryStore;
import com.inigmasgames.persistentnpcs.memory.MemoryType;
import com.inigmasgames.persistentnpcs.economy.ObligationStore;
import com.inigmasgames.persistentnpcs.plan.SharedPlanStore;
import com.inigmasgames.persistentnpcs.autonomy.AgentOperationStore;
import com.inigmasgames.persistentnpcs.epistemic.EpistemicContract;
import com.inigmasgames.persistentnpcs.epistemic.EpistemicEvidenceRetriever;
import com.inigmasgames.persistentnpcs.epistemic.ActorModelService;
import com.inigmasgames.persistentnpcs.epistemic.ReflectionService;
import com.inigmasgames.persistentnpcs.task.NpcTask;
import com.inigmasgames.persistentnpcs.task.NpcTaskStore;
import com.inigmasgames.persistentnpcs.voice.VocalState;
import com.inigmasgames.persistentnpcs.voice.VocalEmotion;
import com.inigmasgames.persistentnpcs.voice.LysanderVoiceBehavior;
import com.inigmasgames.persistentnpcs.voice.ParalinguisticEventPolicy;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

/** One cheap structured appraisal per meaningful turn; it never runs on a world tick. */
public final class NpcCognitionService {
    private final RelationshipStore relationships;
    private final NpcTaskStore tasks;
    private final NpcEmotionStore emotions;
    private final NpcProfileRegistry profiles;
    private final MemoryStore memories;
    private final ObligationStore obligations;
    private final SharedPlanStore sharedPlans;
    private final AgentOperationStore operations;
    private final SourcedBeliefStore sourcedBeliefs;
    private final CognitionTraceStore traces;
    private final ResponseLatencyTraceStore responseLatency;
    private final PlayerFactMemoryService playerFacts;
    private final EpistemicEvidenceRetriever epistemicEvidence;
    private final ActorModelService actorModels;
    private final ReflectionService reflections;
    private final ParalinguisticEventPolicy paralinguisticEvents =
            new ParalinguisticEventPolicy();

    public NpcCognitionService(
            RelationshipStore relationships, NpcTaskStore tasks, NpcEmotionStore emotions) {
        this(relationships, tasks, emotions, null, null, null, null, null, null,
                new CognitionTraceStore(), new ResponseLatencyTraceStore());
    }

    public NpcCognitionService(
            RelationshipStore relationships,
            NpcTaskStore tasks,
            NpcEmotionStore emotions,
            NpcProfileRegistry profiles,
            MemoryStore memories,
            ObligationStore obligations,
            SharedPlanStore sharedPlans,
            AgentOperationStore operations,
            SourcedBeliefStore sourcedBeliefs,
            CognitionTraceStore traces) {
        this(relationships, tasks, emotions, profiles, memories, obligations, sharedPlans,
                operations, sourcedBeliefs, traces, new ResponseLatencyTraceStore());
    }

    public NpcCognitionService(
            RelationshipStore relationships,
            NpcTaskStore tasks,
            NpcEmotionStore emotions,
            NpcProfileRegistry profiles,
            MemoryStore memories,
            ObligationStore obligations,
            SharedPlanStore sharedPlans,
            AgentOperationStore operations,
            SourcedBeliefStore sourcedBeliefs,
            CognitionTraceStore traces,
            ResponseLatencyTraceStore responseLatency) {
        this.relationships = relationships;
        this.tasks = tasks;
        this.emotions = emotions;
        this.profiles = profiles;
        this.memories = memories;
        this.obligations = obligations;
        this.sharedPlans = sharedPlans;
        this.operations = operations;
        this.sourcedBeliefs = sourcedBeliefs;
        this.traces = traces == null ? new CognitionTraceStore() : traces;
        this.responseLatency = responseLatency == null
                ? new ResponseLatencyTraceStore() : responseLatency;
        this.playerFacts = new PlayerFactMemoryService(profiles, sourcedBeliefs, memories);
        this.actorModels = new ActorModelService(sourcedBeliefs, relationships, profiles);
        this.reflections = new ReflectionService(sourcedBeliefs, ignored -> { });
        this.epistemicEvidence = new EpistemicEvidenceRetriever(memories, sourcedBeliefs,
                relationships, tasks, operations, profiles, actorModels);
    }

    /**
     * Builds the authoritative conversational decision before Nemotron is asked to phrase it.
     * The existing appraisal and action authorization remain the low-cost execution policy.
     */
    public CognitionTurn evaluateGrounded(
            UUID responseId,
            NpcProfile profile,
            ConversationSession session,
            String playerMessage,
            NpcPerceptionSnapshot perception,
            DialogueMode mode,
            List<String> validActions) {
        RawPerceptionSnapshot raw = RawPerceptionSnapshot.fromLegacy(responseId, perception);
        SemanticWorldModel semantic = new SemanticPerceptionNormalizer()
                .normalize(raw, profile, playerMessage);
        return evaluateGrounded(responseId, profile, session, playerMessage, raw, semantic,
                mode, validActions, CognitiveContextPlan.full(mode.name()));
    }

    public CognitionTurn evaluateGrounded(
            UUID responseId,
            NpcProfile profile,
            ConversationSession session,
            String playerMessage,
            RawPerceptionSnapshot rawPerception,
            SemanticWorldModel semanticWorld,
            DialogueMode mode,
            List<String> validActions) {
        return evaluateGrounded(responseId, profile, session, playerMessage, rawPerception,
                semanticWorld, mode, validActions, CognitiveContextPlan.full(mode.name()));
    }

    public CognitionTurn evaluateGrounded(
            UUID responseId,
            NpcProfile profile,
            ConversationSession session,
            String playerMessage,
            RawPerceptionSnapshot rawPerception,
            SemanticWorldModel semanticWorld,
            DialogueMode mode,
            List<String> validActions,
            CognitiveContextPlan contextPlan) {
        CognitiveContextPlan routed = contextPlan == null
                ? CognitiveContextPlan.full(mode.name()) : contextPlan;
        NpcPerceptionSnapshot perception = rawPerception.engineSnapshot();
        CognitionTurn base = evaluate(profile, session, playerMessage, perception, mode);
        Instant now = Instant.now();

        List<UUID> mentionedIds = routed.includes("RELATIONSHIPS")
                ? resolveMentionedEntities(playerMessage) : List.of();
        long stageStarted = System.nanoTime();
        List<SourcedBelief> updates = new ArrayList<>(extractBeliefUpdates(responseId,
                profile, session, playerMessage, mentionedIds, now, mode));
        locatorBelief(responseId, profile, session, semanticWorld.knownNpcLocator(), now)
                .ifPresent(updates::add);
        updates = List.copyOf(updates);
        recordStage(responseId, ResponseLatencyStage.BELIEF_UPDATE, stageStarted);
        List<UUID> focusIds = new ArrayList<>(mentionedIds);
        focusIds.add(session.playerId());

        stageStarted = System.nanoTime();
        List<RelationshipRecord> relevantRelationships = routed.includes("RELATIONSHIPS")
                || routed.includes("PLAYER_RELATIONSHIP")
                        ? relationships.forNpc(profile.id()).stream()
                                .filter(record -> focusIds.contains(record.playerId())).toList()
                        : List.of();
        if (routed.includes("PLAYER_RELATIONSHIP")
                && relevantRelationships.stream().noneMatch(
                record -> record.playerId().equals(session.playerId()))) {
            relevantRelationships = new ArrayList<>(relevantRelationships);
            relevantRelationships.add(relationships.getOrDefault(profile.id(),
                    session.playerId(), profile.defaultDisposition()));
            relevantRelationships = List.copyOf(relevantRelationships);
        }
        recordStage(responseId, ResponseLatencyStage.RELATIONSHIP_RETRIEVAL, stageStarted);
        stageStarted = System.nanoTime();
        MemoryStore.RetrievalResult memoryRetrieval = memories == null
                || !routed.includes("MEMORIES")
                        ? new MemoryStore.RetrievalResult(List.of(), List.of())
                        : memories.retrieveDetailedForCognition(profile.id(), session.playerId(),
                                playerMessage, 8, base.appraisal().emotionalState().name(),
                        switch (base.responsePlan().vocalState().intensity()) {
                            case LOW -> 0.25;
                            case MEDIUM -> 0.55;
                            case HIGH -> 0.85;
                        });
        List<MemoryStore.ScoredMemory> scoredMemories = memoryRetrieval.selected();
        List<MemoryRecord> relevantMemories = scoredMemories.stream()
                .map(MemoryStore.ScoredMemory::memory).toList();
        recordStage(responseId, ResponseLatencyStage.MEMORY_RETRIEVAL, stageStarted);
        var activeTasks = tasks == null || !routed.includes("TASKS")
                ? List.<NpcTask>of() : tasks.activeFor(profile.id());
        var activeObligations = obligations == null || !routed.includes("OBLIGATIONS")
                ? List.<com.inigmasgames.persistentnpcs.economy.ObligationRecord>of()
                : obligations.activeFor(profile.id());
        var activePlans = sharedPlans == null || !routed.includes("SHARED_PLANS")
                ? List.<com.inigmasgames.persistentnpcs.plan.SharedPlan>of()
                : sharedPlans.activeFor(profile.id());
        var activeOperation = operations == null || !routed.includes("ACTIONS") ? null
                : operations.activeFor(profile.id(), now).orElse(null);
        List<SourcedBelief> beliefContext = new ArrayList<>(sourcedBeliefs == null
                || !routed.includes("BELIEFS") ? List.of()
                : sourcedBeliefs.relevant(profile.id(), focusIds, 8));
        for (SourcedBelief update : updates) {
            if (beliefContext.stream().noneMatch(value -> value.beliefId()
                    .equals(update.beliefId()))) beliefContext.add(update);
        }
        beliefContext = List.copyOf(beliefContext.stream().limit(10).toList());

        Set<String> unknowns = new LinkedHashSet<>();
        if (routed.includes("SEMANTIC_WORLD") && perception.gameTime() == null) {
            unknowns.add("CURRENT_TIME");
        }
        if (routed.includes("SEMANTIC_WORLD")
                && (perception.environment() == null || !perception.environment().isUsable()
                || perception.environment().biomeOrZone().equalsIgnoreCase("unknown")
                || perception.environment().biomeOrZone().equalsIgnoreCase("not exposed"))) {
            unknowns.add("CURRENT_LOCATION_NAME");
        }
        if (routed.includes("WEATHER") && (perception.environment() == null
                || !perception.environment().supports("weather"))) {
            unknowns.add("CURRENT_WEATHER");
        }
        if (mentionsLocationDependentRequest(playerMessage)
                && !hasGroundedTargetLocation(playerMessage, perception)
                && (semanticWorld.knownNpcLocator() == null
                        || !semanticWorld.knownNpcLocator().found())) {
            unknowns.add("TARGET_LOCATION");
        }

        List<String> evidence = new ArrayList<>();
        if (routed.includes("SEMANTIC_WORLD")) {
            evidence.add("PERCEPTION:npc=" + value(perception.npcEntityId()));
            evidence.add(perception.gameTime() == null ? "WORLD_TIME:UNKNOWN"
                    : "WORLD_TIME:WorldTimeResource=" + perception.gameTime());
        }
        if (routed.includes("SEMANTIC_WORLD") && perception.environment() != null
                && perception.environment().isUsable()) {
            evidence.add("ENVIRONMENT:snapshot=" + perception.environment().capturedAt());
        }
        updates.forEach(belief -> evidence.add("BELIEF:" + belief.beliefId()));
        if (semanticWorld.knownNpcLocator() != null) {
            evidence.add("KNOWN_NPC_LOCATOR:" + semanticWorld.knownNpcLocator().status());
        }
        relevantRelationships.forEach(relationship -> evidence.add(
                "RELATIONSHIP:" + relationship.playerId()));
        relevantMemories.forEach(memory -> evidence.add("MEMORY:" + memory.memoryId()));
        activePlans.forEach(plan -> evidence.add("SHARED_PLAN:" + plan.id()));
        activeObligations.forEach(obligation -> evidence.add(
                "OBLIGATION:" + obligation.obligationId()));

        String activity = activeTasks.isEmpty() ? "social attention"
                : natural(activeTasks.getFirst().type());
        String operation = activeOperation == null ? "none"
                : natural(activeOperation.kind()) + " (" + natural(activeOperation.status()) + ")";
        SemanticWorldModel enrichedSemantic = semanticWorld.withSelfState(
                semanticWorld.selfState().withRuntime(activity, operation,
                        !routed.includes("SEMANTIC_WORLD")
                                ? "physical state was not queried for this routed turn"
                                : perception.npcEntityId() == null ? "not physically loaded"
                                        : "present at the authoritative ECS position"));
        CognitionContext context = new CognitionContext(responseId, session.sessionId(),
                session.playerId(), now, profile, perception, perception.gameTime(),
                perception.gameTime() == null ? "UNKNOWN" : "WorldTimeResource",
                activity, activeTasks, activeObligations,
                relevantRelationships, relevantMemories, activePlans, beliefContext,
                validActions, activeOperation, unknowns, evidence, rawPerception,
                enrichedSemantic, playerMessage, scoredMemories, routed,
                memoryRetrieval.rejected());
        stageStarted = System.nanoTime();
        GroundedNpcDecision decision = selectDecision(context, session, updates, playerMessage,
                base.responsePlan().vocalState());
        KnownNpcLocatorResult locator = enrichedSemantic.knownNpcLocator();
        if (locator != null && decision.selectedIntent() == GroundedIntent.OFFER_GUIDE_TO_NPC) {
            session.offerGuide(locator.targetStableId(), locator.targetName(),
                    now.plus(java.time.Duration.ofMinutes(2)));
        } else if (decision.selectedIntent() == GroundedIntent.GUIDE_PLAYER_TO_NPC) {
            session.clearPendingGuideOffer();
        }
        recordStage(responseId, ResponseLatencyStage.INTENT_SELECTION, stageStarted);
        traces.record(profile.id(), context, decision);
        return base.withDecision(context, decision);
    }

    /** Deterministic pre-context router shared by conversation, traces, and inspector. */
    public CognitiveContextPlan routeContext(NpcProfile profile, String playerMessage,
            DialogueMode mode) {
        return CognitiveContextRouter.route(profile, playerMessage, mode, profiles,
                relationships);
    }

    /** E2 SHADOW read: enriches the contract without changing prompt, actions, or persistence. */
    public EpistemicContract enrichEpistemicShadow(EpistemicContract base,
            UUID responseId, NpcProfile profile, ConversationSession session,
            String playerMessage, RawPerceptionSnapshot rawPerception,
            CognitionTurn cognitionTurn, List<String> validActions) {
        if (sourcedBeliefs != null && rawPerception != null) {
            sourcedBeliefs.ingestPerception(profile.id(), session.playerId(), rawPerception);
        }
        return epistemicEvidence.enrich(base, responseId, profile, session.playerId(),
                playerMessage, rawPerception,
                cognitionTurn == null ? null : cognitionTurn.context(),
                cognitionTurn == null ? null : cognitionTurn.selfModel(),
                session.epistemicWorkspace(), validActions);
    }

    /** E4 consumes only validated action results; failed/model-proposed actions add no truth. */
    public java.util.Optional<com.inigmasgames.persistentnpcs.epistemic.BeliefAssertion>
            ingestActionResult(UUID npcId, UUID playerId,
            com.inigmasgames.persistentnpcs.action.NpcActionRequest request,
            com.inigmasgames.persistentnpcs.action.NpcActionResult result) {
        if (sourcedBeliefs == null) return java.util.Optional.empty();
        Instant now = Instant.now();
        var assertion = sourcedBeliefs.ingestActionResult(npcId, playerId, request, result, now);
        if (request != null) reflections.onActionResult(npcId, request.id(), now);
        return assertion;
    }

    /** E8: only an authoritative action result may become autonomous experience. */
    public java.util.Optional<com.inigmasgames.persistentnpcs.epistemic.BeliefAssertion>
            ingestAutonomousActionResult(UUID npcId, UUID playerId, UUID counterpartyNpcId,
            com.inigmasgames.persistentnpcs.action.NpcActionRequest request,
            com.inigmasgames.persistentnpcs.action.NpcActionResult result) {
        var assertion = ingestActionResult(npcId, playerId, request, result);
        if (result == null || !result.success()) return assertion;
        Instant now = Instant.now();
        List<UUID> involved = counterpartyNpcId == null ? List.of() : List.of(counterpartyNpcId);
        if (memories != null) memories.append(new MemoryRecord(UUID.randomUUID(), npcId,
                playerId, now, MemoryType.EPISODIC, .62, result.eventDescription(), 1.0,
                "AUTHORITATIVE_ACTION_RESULT:" + request.toolCallId(), involved, "",
                "I remember what actually happened: " + result.eventDescription()));
        if (counterpartyNpcId != null && sourcedBeliefs != null) {
            sourcedBeliefs.ingestActionResult(counterpartyNpcId, playerId, request, result, now);
            if (memories != null) memories.append(new MemoryRecord(UUID.randomUUID(),
                    counterpartyNpcId, playerId, now, MemoryType.EPISODIC, .60,
                    result.eventDescription(), 1.0,
                    "AUTHORITATIVE_ACTION_RESULT:" + request.toolCallId(), List.of(npcId), "",
                    "I remember the transaction that actually occurred: "
                            + result.eventDescription()));
        }
        return assertion;
    }

    public GroundedNpcDecision finalizeDecision(
            UUID npcId, CognitionTurn turn, String canonicalSpokenText) {
        if (turn == null || turn.decision() == null) return null;
        GroundedNpcDecision finalized = turn.decision().withSpokenText(canonicalSpokenText);
        traces.record(npcId, turn.context(), finalized);
        return finalized;
    }

    public GroundedNpcDecision finalizeDecision(UUID npcId, CognitionTurn turn,
            NpcDecision modelDecision, String canonicalSpokenText) {
        if (turn == null || turn.decision() == null || modelDecision == null) {
            return finalizeDecision(npcId, turn, canonicalSpokenText);
        }
        if (!npcId.equals(modelDecision.npcStableId())
                || !turn.decision().responseId().equals(modelDecision.responseId())) {
            throw new java.util.concurrent.CancellationException(
                    "Mismatched structured decision cannot commit spoken text");
        }
        GroundedNpcDecision prior = turn.decision();
        GroundedNpcDecision finalized = new GroundedNpcDecision(modelDecision.responseId(),
                prior.beliefUpdates(), prior.attendedEntities(), prior.attendedTopics(),
                prior.relevantRelationshipIds(), modelDecision.intent(), prior.intentPriority(),
                modelDecision.actions().stream().map(NpcDecisionAction::actionId).toList(),
                canonicalSpokenText, modelDecision.emotion(),
                modelDecision.paralinguisticEvent(), modelDecision.groundingEvidenceRefs(),
                prior.candidateIntents(), prior.fallbackOrRejectionReason());
        traces.record(npcId, turn.context(), finalized);
        return finalized;
    }

    public CognitionTraceStore traces() { return traces; }

    public ActorModelService actorModels() { return actorModels; }

    public ReflectionService reflections() { return reflections; }

    public void configureReflectionScheduling(
            com.inigmasgames.persistentnpcs.orbis.OrbisResourceScheduler scheduler) {
        reflections.scheduler(scheduler);
    }

    public void configureReflectionDiagnostics(java.util.function.Consumer<String> diagnostics) {
        reflections.diagnostics(diagnostics);
    }

    /**
     * E6 communication ingress. Callers must supply the immutable canonical-delivery event;
     * generated-but-unplayed wording is deliberately rejected by ActorModelService.
     */
    public ActorModelService.TestimonyResult recordDeliveredNpcTestimony(UUID recipientNpcId,
            UUID senderNpcId,
            com.inigmasgames.persistentnpcs.epistemic.BeliefAssertion authorizedProposition,
            UUID responseId, java.time.Instant deliveredAt) {
        if (responseId == null) throw new IllegalArgumentException("responseId is required");
        return actorModels.ingestDeliveredTestimony(recipientNpcId, senderNpcId,
                authorizedProposition, "CANONICAL_DELIVERY:" + responseId, deliveredAt);
    }

    public ResponseLatencyTraceStore responseLatency() { return responseLatency; }

    public java.util.Optional<com.inigmasgames.persistentnpcs.autonomy.AgentOperation>
            activeOperation(UUID npcId) {
        return operations == null ? java.util.Optional.empty()
                : operations.activeFor(npcId, Instant.now());
    }

    /** Reconciles the trace with the immutable chunks actually committed to chat and TTS. */
    public void recordCommittedText(UUID npcId, UUID responseId, String committedText) {
        traces.latest(npcId).filter(trace -> trace.decision() != null
                && responseId.equals(trace.decision().responseId()))
                .ifPresent(trace -> traces.record(npcId, trace.context(),
                        trace.decision().withSpokenText(committedText)));
    }

    public CognitionTurn evaluate(
            NpcProfile profile,
            ConversationSession session,
            String playerMessage,
            NpcPerceptionSnapshot perception,
            DialogueMode mode) {
        long started = System.nanoTime();
        Instant now = Instant.now();
        RelationshipRecord relationship = relationships.getOrDefault(
                profile.id(), session.playerId(), profile.defaultDisposition());
        List<NpcTask> active = tasks.activeFor(profile.id());
        String requestedAction = requestedAction(playerMessage);
        EnvironmentSnapshot environment = perception.environment();
        boolean unknownLocation = environment == null || !environment.isUsable()
                || environment.biomeOrZone().toLowerCase(Locale.ROOT).contains("not exposed")
                || environment.biomeOrZone().equalsIgnoreCase("unknown");
        boolean environmentQuestion = mode == DialogueMode.ENVIRONMENT_QUERY;
        boolean danger = !perception.nearbyHostiles().isEmpty()
                || isDangerousAction(requestedAction);
        boolean exceptionalTopic = contains(normalize(playerMessage),
                "goblin flamethrower", "rare ore", "lightning discovery",
                "lightning discoveries", "fox", "foxes");
        VocalEmotion lysanderEmotion = LysanderVoiceBehavior.appliesTo(profile)
                ? LysanderVoiceBehavior.select(playerMessage, danger,
                        environmentQuestion, unknownLocation)
                : null;
        boolean significant = environmentQuestion || !requestedAction.isBlank()
                || danger || exceptionalTopic || lysanderEmotion != null
                        && lysanderEmotion != VocalEmotion.CALM
                || session.recentTurns(1).isEmpty();

        Decision decision = decide(profile, relationship, requestedAction,
                playerMessage, danger, !active.isEmpty());
        NpcEmotion desiredEmotion = lysanderEmotion != null
                ? npcEmotion(lysanderEmotion)
                : danger ? NpcEmotion.UNEASY
                : exceptionalTopic ? NpcEmotion.EXCITED
                : environmentQuestion && unknownLocation ? NpcEmotion.CURIOUS
                : decision.intent == SocialIntent.CLARIFY ? NpcEmotion.SUSPICIOUS
                : NpcEmotion.CALM;
        double desiredIntensity = lysanderEmotion != null
                ? lysanderIntensity(lysanderEmotion)
                : danger ? 0.62 : exceptionalTopic ? 0.46
                : environmentQuestion ? 0.42
                : decision.intent == SocialIntent.CLARIFY ? 0.36 : 0.12;
        NpcEmotionalState emotion = significant
                ? emotions.update(profile.id(), desiredEmotion, desiredIntensity, now,
                        environmentQuestion ? "environment appraisal" : "social appraisal")
                : emotions.get(profile.id(), now);

        String familiarity = band(relationship.familiarity(), 15, 50);
        String trust = band(relationship.trust(), 10, 45);
        String risk = danger ? "HIGH" : environmentQuestion && unknownLocation
                ? "MODERATE" : "LOW";
        String uncertainty = environmentQuestion && unknownLocation
                ? "Current location has no authoritative name; describe only perceived features."
                : decision.uncertainty;
        String immediateGoal = environmentQuestion
                ? "Understand the visible surroundings without inventing a location name."
                : !requestedAction.isBlank() ? decision.goal
                : "Respond directly and maintain social attention.";
        String summary = environmentQuestion
                ? (unknownLocation ? "Unfamiliar location; assess visible features and POIs."
                        : "Player asks about the current known environment.")
                : !requestedAction.isBlank() ? "Player requested " + requestedAction + "."
                : "Conversation began with the focused player.";
        NpcAppraisal appraisal = new NpcAppraisal(significant, summary, familiarity, trust,
                risk, emotion.emotion(), immediateGoal, decision.intent, uncertainty,
                requestedAction, decision.authorized, decision.reason);

        String task = active.isEmpty() ? "none" : active.stream().limit(2)
                .map(value -> value.type() + ":" + value.state()).toList().toString();
        String awareness = unknownLocation
                ? "UNKNOWN name; perceived terrain/objects only"
                : "KNOWN from authoritative current context: " + environment.biomeOrZone();
        NpcSelfModel self = new NpcSelfModel(profile.selfIdentity(),
                profile.speciesArchetype(), profile.role(), profile.values(),
                profile.personalityTraits(), profile.fears(), profile.goals(),
                immediateGoal, currentNeed(danger, decision), emotion,
                relationship.naturalSummary("the focused player"), task, awareness,
                perception.npcEntityId() == null ? "unloaded/unknown" : danger
                        ? "loaded; danger perceived" : "loaded; normal locomotion");

        List<AttentionAction> attention = environmentQuestion
                ? List.of(AttentionAction.LOOK_AROUND, AttentionAction.LOOK_AT_POINT,
                        AttentionAction.EMOTE, AttentionAction.RETURN_LOOK_TO_PLAYER)
                : List.of(AttentionAction.LOOK_AT_PLAYER);
        String question = decision.intent == SocialIntent.CLARIFY
                ? clarificationFor(requestedAction)
                : environmentQuestion && unknownLocation && profile.curiosity() >= 0.55
                        ? "Ask whether the player knows this place, only if it fits naturally."
                        : "";
        VocalState vocalState = VocalState.forEmotion(vocalEmotion(emotion.emotion()));
        vocalState = paralinguisticEvents.select(
                profile.id(), vocalState, playerMessage, now);
        NpcResponsePlan plan = new NpcResponsePlan(attention,
                environmentQuestion && unknownLocation ? "UNCERTAIN" : "",
                question, decision.authorized ? requestedAction : "",
                vocalState);
        long elapsed = Math.max(0, (System.nanoTime() - started) / 1_000_000L);
        return new CognitionTurn(self, appraisal, plan, elapsed);
    }

    public boolean allowTool(String actionId, CognitionTurn cognition) {
        if (actionId == null || cognition == null) return false;
        String normalized = actionId.strip().toUpperCase(Locale.ROOT);
        if (cognition.context() != null
                && !cognition.context().validActions().contains(normalized)) return false;
        if (cognition.context() != null && cognition.context().activeOperation() != null
                && !Set.of("STOP_FOLLOWING", "CANCEL_TASK").contains(normalized)) {
            return false;
        }
        NpcAppraisal appraisal = cognition.appraisal();
        if (!appraisal.requestedAction().isBlank()) {
            return appraisal.requestedAction().equalsIgnoreCase(normalized)
                    && appraisal.actionAuthorized();
        }
        return cognition.decision() == null
                || cognition.decision().actionRequests().isEmpty()
                || cognition.decision().actionRequests().contains(normalized);
    }

    public Optional<NpcActionRequest> fallbackAction(
            String playerMessage, String modelText, CognitionTurn cognition) {
        NpcAppraisal appraisal = cognition.appraisal();
        boolean follow = "FOLLOW_PLAYER".equals(appraisal.requestedAction());
        boolean stop = "STOP_FOLLOWING".equals(appraisal.requestedAction());
        boolean guide = cognition.decision() != null
                && cognition.decision().selectedIntent() == GroundedIntent.GUIDE_PLAYER_TO_NPC;
        if ((!guide && !appraisal.actionAuthorized()) || (!follow && !stop && !guide)
                || ((follow || guide) && !looksLikeAgreement(modelText))) {
            return Optional.empty();
        }
        JsonObject parameters = new JsonObject();
        if ("STOP_FOLLOWING".equals(appraisal.requestedAction())) {
            parameters.addProperty("waitHere", waitHereIntent(playerMessage));
        }
        if (guide && cognition.context() != null
                && cognition.context().semanticWorld().knownNpcLocator() != null) {
            parameters.addProperty("targetName", cognition.context().semanticWorld()
                    .knownNpcLocator().targetName());
        }
        return Optional.of(new NpcActionRequest(guide ? "GUIDE_PLAYER_TO_NPC"
                : appraisal.requestedAction(), parameters,
                "cognition-fallback"));
    }

    public String enforceFollowUp(String dialogue, CognitionTurn cognition) {
        if (cognition.appraisal().socialIntent() != SocialIntent.CLARIFY
                || dialogue.contains("?")) {
            return dialogue;
        }
        String question = clarificationFor(cognition.appraisal().requestedAction());
        return question.isBlank() ? dialogue : dialogue.strip() + " " + question;
    }

    private List<UUID> resolveMentionedEntities(String message) {
        if (profiles == null || message == null || message.isBlank()) return List.of();
        String normalized = " " + normalize(message) + " ";
        return profiles.profiles().stream()
                .filter(profile -> normalized.contains(" " + normalize(profile.name()) + " "))
                .map(NpcProfile::stableId).filter(java.util.Objects::nonNull).distinct().toList();
    }

    private List<SourcedBelief> extractBeliefUpdates(
            UUID responseId, NpcProfile profile, ConversationSession session,
            String message, List<UUID> mentionedIds, Instant now, DialogueMode mode) {
        if (sourcedBeliefs == null || mode == DialogueMode.NPC_INITIATED_CURIOSITY) {
            return List.of();
        }
        ConversationSession.PlayerUtteranceContext utterance =
                session.playerUtteranceContext();
        return playerFacts.persist(profile.id(), session.playerId(), session.sessionId(),
                responseId, utterance == null ? null : utterance.utteranceId(),
                message, now).beliefWrites();
    }

    private static Optional<SourcedBelief> locatorBelief(
            UUID responseId, NpcProfile profile, ConversationSession session,
            KnownNpcLocatorResult locator, Instant now) {
        if (locator == null || locator.targetStableId() == null) return Optional.empty();
        return Optional.of(new SourcedBelief(UUID.randomUUID(), profile.id(),
                locator.targetStableId(), locator.targetStableId(), locator.targetName(),
                "KNOWN_NPC_LOCATION_" + locator.status(), locator.semanticBlock(), now,
                1.0, locator.found() ? 0.65 : 0.45, session.sessionId(), responseId,
                List.of("AUTHORITATIVE_LOCATOR:" + locator.status())).normalized());
    }

    private GroundedNpcDecision selectDecision(CognitionContext context,
            ConversationSession session,
            List<SourcedBelief> updates, String message, VocalState vocalState) {
        long opportunityStarted = System.nanoTime();
        List<IntentCandidate> candidates = new ArrayList<>();
        ConversationSession.PlayerUtteranceContext utterance =
                session.playerUtteranceContext();
        if (utterance != null && utterance.remoteHail() && utterance.directAddress()) {
            candidates.add(candidate(GroundedIntent.RESPOND_TO_REMOTE_HAIL, 96, 0.94,
                    "The player directly hailed this NPC inside the configured remote range",
                    List.of("PLAYER_UTTERANCE:" + utterance.utteranceId(),
                            "RANGE:REMOTE_HAIL", "DIRECT_ADDRESS:true")));
        }
        boolean danger = !context.perception().nearbyHostiles().isEmpty()
                || updates.stream().anyMatch(belief -> belief.urgency() >= 0.80);
        if (danger) candidates.add(candidate(GroundedIntent.RESPOND_TO_DANGER,
                100, 1.0, "Perceived or player-reported urgent danger",
                context.evidenceRefs()));
        if (!context.obligations().isEmpty()) candidates.add(candidate(
                GroundedIntent.HONOR_OBLIGATION, 90, 0.82,
                "Active persistent obligation", context.evidenceRefs()));
        double relationshipUtility = context.relationships().stream()
                .mapToDouble(NpcCognitionService::relationshipUtility).max().orElse(0.0);
        KnownNpcLocatorResult locator = context.semanticWorld() == null ? null
                : context.semanticWorld().knownNpcLocator();
        if (locator != null) {
            if (locator.status() == KnownNpcLocationStatus.FOUND) {
                candidates.add(candidate(GroundedIntent.REPORT_KNOWN_NPC_LOCATION, 92, 0.78,
                        "A relationship-gated bounded lookup found the known NPC",
                        List.of("KNOWN_NPC_LOCATOR:FOUND")));
                double guideUtility = 0.45 + relationshipUtility * 0.30
                        + context.profile().sociability() * 0.12
                        + context.profile().trustDisposition() * 0.08
                        - (context.activeTasks().isEmpty() ? 0.0 : 0.16)
                        - (context.activeOperation() == null ? 0.0 : 0.30)
                        - (danger ? 0.50 : 0.0);
                boolean canGuide = locator.navigationPossible() && !danger
                        && context.activeOperation() == null;
                if (canGuide && (locator.directGuideRequest() || guideUtility >= 0.66)) {
                    GroundedIntent guideIntent = locator.directGuideRequest()
                            ? GroundedIntent.GUIDE_PLAYER_TO_NPC
                            : GroundedIntent.OFFER_GUIDE_TO_NPC;
                    candidates.add(candidate(guideIntent, locator.directGuideRequest() ? 94 : 93,
                            Math.max(0, Math.min(1, guideUtility)),
                            locator.directGuideRequest()
                                    ? "The player accepted or directly requested an available guide"
                                    : "Relationship and current obligations permit offering guidance",
                            List.of("KNOWN_NPC_LOCATOR:FOUND", "RELATIONSHIP:KNOWN")));
                }
            } else {
                candidates.add(candidate(GroundedIntent.REPORT_UNABLE_TO_LOCATE, 92, 0.86,
                        locator.status() == KnownNpcLocationStatus.UNKNOWN_RELATIONSHIP
                                ? "No established relationship permits social location knowledge"
                                : "The bounded authoritative lookup could not locate the known NPC",
                        List.of("KNOWN_NPC_LOCATOR:" + locator.status())));
            }
        }
        boolean thirdPartyRelationship = context.relationships().stream()
                .anyMatch(record -> !record.playerId().equals(context.playerId()));
        if (locator == null
                && (thirdPartyRelationship || relationshipUtility >= 0.35
                        && !updates.isEmpty())) {
            candidates.add(candidate(GroundedIntent.RESPOND_TO_RELATIONSHIP, 80,
                    0.55 + relationshipUtility * 0.45,
                    "New information involves an explicit relationship",
                    context.evidenceRefs()));
        }
        if (!context.sharedPlans().isEmpty()) candidates.add(candidate(
                GroundedIntent.CONTINUE_SHARED_PLAN, 70, 0.72,
                "Active shared plan", context.evidenceRefs()));
        String requested = requestedAction(message);
        boolean requestedEligible = !requested.isBlank() && context.validActions().stream()
                .anyMatch(value -> value.equalsIgnoreCase(requested));
        if (!requested.isBlank() && context.unknownWorldFacts().contains("TARGET_LOCATION")) {
            candidates.add(candidate(GroundedIntent.SEEK_INFORMATION, 65, 0.76,
                    "Requested action lacks an authoritative target location",
                    List.of("UNKNOWN:TARGET_LOCATION")));
        } else if (requestedEligible) {
            candidates.add(candidate(GroundedIntent.EXECUTE_DIRECT_REQUEST, 60, 0.70,
                    "Direct request maps to an eligible registered action",
                    List.of("ACTION:" + requested)));
        } else if (!requested.isBlank()) {
            candidates.add(candidate(GroundedIntent.REFUSE_UNGROUNDED_ACTION, 60, 0.65,
                    "Requested action is not currently eligible",
                    List.of("ACTION_REJECTED:" + requested)));
        }
        if (!updates.isEmpty()) candidates.add(candidate(GroundedIntent.PROCESS_INFORMATION,
                58, 0.62 + updates.stream().mapToDouble(SourcedBelief::urgency)
                        .max().orElse(0) * 0.25,
                "Player supplied a new sourced proposition", context.evidenceRefs()));
        if (message != null && message.contains("?")) candidates.add(candidate(
                GroundedIntent.PROCESS_INFORMATION, 57, 0.61,
                "Direct information request", context.evidenceRefs()));
        if (matchesAny(context.profile().goals(), message)) candidates.add(candidate(
                GroundedIntent.PURSUE_GOAL, 50, 0.54,
                "Message is relevant to an authored goal", List.of("PROFILE:goals")));
        if (!context.unknownWorldFacts().isEmpty() && message != null
                && message.contains("?")) candidates.add(candidate(GroundedIntent.SEEK_INFORMATION,
                45, 0.51, "Question depends on unavailable world facts",
                context.unknownWorldFacts().stream().map(value -> "UNKNOWN:" + value).toList()));
        candidates.add(candidate(GroundedIntent.AMBIENT_RESPONSE, 10, 0.20,
                "No higher-priority opportunity", List.of("CONVERSATION:current")));
        recordStage(context.responseId(), ResponseLatencyStage.OPPORTUNITY_GENERATION,
                opportunityStarted);
        candidates.sort(Comparator.comparingInt(IntentCandidate::priority).reversed()
                .thenComparing(Comparator.comparingDouble(
                        IntentCandidate::utility).reversed()));
        IntentCandidate selected = candidates.getFirst();
        List<String> actionRequests = selected.intent() == GroundedIntent.GUIDE_PLAYER_TO_NPC
                ? List.of("GUIDE_PLAYER_TO_NPC")
                : selected.intent() == GroundedIntent.EXECUTE_DIRECT_REQUEST
                        && requestedEligible ? List.of(requested) : List.of();
        List<String> attendedEntities = context.relationships().stream()
                .map(record -> record.playerId().toString()).distinct().toList();
        List<String> topics = updates.isEmpty() ? topicTerms(message)
                : updates.stream().map(SourcedBelief::predicate).distinct().toList();
        String reason = selected.intent() == GroundedIntent.SEEK_INFORMATION
                ? "Missing authoritative information; ask rather than invent it."
                : selected.intent() == GroundedIntent.REFUSE_UNGROUNDED_ACTION
                        ? "No eligible registered action supports the request." : "";
        return new GroundedNpcDecision(context.responseId(), updates, attendedEntities,
                topics, context.relationships().stream().map(RelationshipRecord::playerId)
                        .distinct().toList(),
                selected.intent(), selected.priority(), actionRequests, "",
                vocalState.emotion(), vocalState.paralinguisticEvent(),
                selected.evidenceRefs(), candidates, reason);
    }

    private static IntentCandidate candidate(GroundedIntent intent, int priority,
            double utility, String basis, List<String> refs) {
        return new IntentCandidate(intent, priority, Math.max(0, Math.min(1, utility)),
                basis, refs == null ? List.of() : refs.stream().limit(12).toList());
    }

    private void recordStage(UUID responseId, ResponseLatencyStage stage, long startedNanos) {
        responseLatency.recordDuration(responseId, stage,
                java.util.concurrent.TimeUnit.NANOSECONDS.toMillis(
                        Math.max(0, System.nanoTime() - startedNanos)));
    }

    private static String natural(String value) {
        return value == null || value.isBlank() ? "none"
                : value.replace('_', ' ').toLowerCase(Locale.ROOT);
    }

    private static double relationshipUtility(RelationshipRecord relationship) {
        double positive = value(relationship.affection()) * 0.22
                + value(relationship.trust()) * 0.22
                + value(relationship.respect()) * 0.18
                + value(relationship.obligation()) * 0.20
                + value(relationship.familiarity()) * 0.08;
        double threat = value(relationship.fear()) * 0.16
                + value(relationship.hostility()) * 0.20;
        return Math.max(0, Math.min(1, (positive + threat) / 100.0));
    }

    private static int value(Integer value) { return value == null ? 0 : Math.abs(value); }

    /**
     * A sourced belief must be an actual declarative report. Questions, commands, and
     * conversational acknowledgements are dialogue acts, not facts to persist.
     */
    public static boolean isDeclarativePlayerReport(String message) {
        return PlayerFactMemoryService.classify(message) == PlayerInputKind.DECLARATIVE_FACT;
    }

    private static String predicate(String message) {
        String value = normalize(message);
        if (contains(value, "danger", "attack", "hurt", "missing")) return "DANGER_REPORT";
        if (contains(value, "wants", "asked", "needs", "tell")) return "REQUEST_REPORT";
        if (contains(value, "will", "promise", "agreed")) return "COMMITMENT_REPORT";
        if (contains(value, " at ", " near ", " inside ", " outside ")) return "LOCATION_REPORT";
        if (contains(value, "changed", "arrived", "left", "became")) return "STATE_CHANGE";
        return "PLAYER_REPORT";
    }

    private static double urgency(String message) {
        String value = normalize(message);
        if (contains(value, "emergency", "danger", "attack", "dying", "hurt", "missing")) {
            return 0.90;
        }
        if (contains(value, "urgent", "right now", "needs", "asked", "wants")) return 0.68;
        return 0.38;
    }

    private static boolean mentionsLocationDependentRequest(String message) {
        String value = normalize(message);
        return contains(value, "go to", "walk to", "move to", "find ", "bring me to",
                "take me to", "meet ");
    }

    private static boolean hasGroundedTargetLocation(
            String message, NpcPerceptionSnapshot perception) {
        String value = normalize(message);
        if (contains(value, " here", "there", "to me", "with me")) return true;
        return perception.nearbyNpcs().stream().anyMatch(entity ->
                !entity.name().isBlank() && value.contains(normalize(entity.name())))
                || perception.nearbyInteractables().stream().anyMatch(entity ->
                        !entity.name().isBlank() && value.contains(normalize(entity.name())));
    }

    private static boolean matchesAny(List<String> values, String message) {
        String text = normalize(message);
        return values != null && values.stream().filter(java.util.Objects::nonNull)
                .map(NpcCognitionService::normalize).flatMap(value ->
                        java.util.Arrays.stream(value.split(" ")))
                .filter(word -> word.length() >= 4).anyMatch(text::contains);
    }

    private static List<String> topicTerms(String message) {
        return java.util.Arrays.stream(normalize(message).split(" "))
                .filter(word -> word.length() >= 4).distinct().limit(5).toList();
    }

    private static String compactClaim(String message) {
        String value = message == null ? "" : message.replaceAll("\\s+", " ").strip();
        return value.length() <= 600 ? value : value.substring(0, 600);
    }

    private static String value(Object value) { return value == null ? "UNKNOWN" : value.toString(); }

    private static Decision decide(
            NpcProfile profile, RelationshipRecord relationship, String action,
            String message, boolean danger, boolean occupied) {
        if (action.isBlank()) {
            return new Decision(false, SocialIntent.RESPOND, "No action requested.", "", "");
        }
        if ("STOP_FOLLOWING".equals(action)) {
            return new Decision(true, SocialIntent.ACCEPT_ACTION,
                    "The player may always end an active follow commitment.",
                    "Stop following and apply the requested anchor semantics.", "LOW");
        }
        double socialScore = relationship.trust()
                + Math.max(0, relationship.familiarity()) * 0.25
                + profile.trustDisposition() * 45.0
                + profile.defaultDisposition() * 0.15;
        boolean established = relationship.interactionCount() >= 4
                || relationship.familiarity() >= 15;
        boolean contextGiven = contains(message, "because", "so that", "to help",
                "we need", "danger", "safe", "over here", "where");
        if (relationship.hostility() >= 35 || relationship.fear() >= 60) {
            return new Decision(false, SocialIntent.REFUSE,
                    "Hostility or fear is too high for voluntary compliance.",
                    "Maintain safety and decline.", "Trust is insufficient.");
        }
        if (danger && profile.riskTolerance() < 0.55 && socialScore < 50) {
            return new Decision(false, SocialIntent.CLARIFY,
                    "The request appears risky and requires a reason.",
                    "Learn the risk and purpose before deciding.", "Purpose and safety are unclear.");
        }
        if (occupied && !"FOLLOW_PLAYER".equals(action)
                && !"STOP_FOLLOWING".equals(action) && socialScore < 42) {
            return new Decision(false, SocialIntent.CLARIFY,
                    "An existing commitment conflicts with the request.",
                    "Clarify priority before changing tasks.", "Task priority is unclear.");
        }
        boolean willing = established || socialScore >= 28
                || (profile.trustDisposition() >= 0.72 && profile.riskTolerance() >= 0.55);
        if (!willing && !contextGiven) {
            return new Decision(false, SocialIntent.CLARIFY,
                    "The relationship is new and the request lacks context.",
                    "Ask for destination or purpose.", "Destination or purpose is unclear.");
        }
        return new Decision(true, SocialIntent.ACCEPT_ACTION,
                established ? "Established familiarity supports this low-risk request."
                        : "Disposition and supplied context support this request.",
                "Respond naturally and execute only through the validated action.", "LOW");
    }

    private static String requestedAction(String message) {
        String value = normalize(message);
        if (contains(value, "take me to", "lead me to", "guide me to",
                "bring me to", "show me where")) return "GUIDE_PLAYER_TO_NPC";
        if (contains(value, "stop following me", "wait here", "stay here",
                "you can stop", "hold position")) return "STOP_FOLLOWING";
        if (contains(value, "follow me", "come with me", "come along",
                "stay with me")) return "FOLLOW_PLAYER";
        if (contains(value, "go to", "walk to", "move to")) return "GO_TO";
        if (value.matches(".*\\btell (?!me\\b|you\\b)[\\p{L}][\\p{L}'-]* .+"))
            return "DELIVER_MESSAGE";
        if (contains(value, "give me", "hand me")) return "GIVE_ITEM";
        if (contains(value, "take this", "accept this")) return "TAKE_ITEM";
        if (contains(value, "meet me")) return "SCHEDULE_MEETING";
        if (contains(value, "help me", "help us")) return "HELP_PLAYER";
        if (contains(value, "enter the dangerous", "go into danger", "dangerous area"))
            return "ENTER_DANGEROUS_AREA";
        if (contains(value, "attack", "fight", "kill")) return "ATTACK";
        if (contains(value, "truce", "cease fire")) return "TEMPORARY_TRUCE";
        return "";
    }

    private static boolean isDangerousAction(String action) {
        return "ATTACK".equals(action) || "ENTER_DANGEROUS_AREA".equals(action);
    }

    private static boolean looksLikeAgreement(String text) {
        String value = normalize(text);
        if (contains(value, "no", "won't", "will not", "can't", "cannot", "why",
                "where are", "where to", "not yet", "do not", "don't")
                || value.contains("?")) {
            return false;
        }
        return contains(value, "yes", "sure", "all right", "alright", "okay", "fine",
                "i'll follow", "i will follow", "lead on", "let's go", "coming",
                "i'll lead", "i will lead", "i can lead", "follow me", "guide you",
                "show you", "take you there")
                || value.equals("i'll stop") || value.equals("i will stop")
                || value.equals("i'll wait here") || value.equals("i will wait here")
                || value.equals("follow me") || value.equals("follow")
                || value.equals("i follow you") || value.startsWith("i'm following")
                || value.startsWith("i am following")
                || (value.contains("follow") && value.split(" ").length <= 5);
    }

    private static boolean waitHereIntent(String message) {
        String value = normalize(message);
        return contains(value, "wait here", "stay here", "hold position");
    }

    private static String clarificationFor(String action) {
        return "FOLLOW_PLAYER".equals(action) ? "Where are we going?"
                : action == null || action.isBlank() ? "" : "Why do you need me to do that?";
    }

    private static String currentNeed(boolean danger, Decision decision) {
        if (danger) return "safety and reliable information";
        if (decision.intent == SocialIntent.CLARIFY) return "context before commitment";
        return "clear, grounded conversation";
    }

    private static NpcEmotion npcEmotion(VocalEmotion emotion) {
        return switch (emotion) {
            case CURIOUS -> NpcEmotion.CURIOUS;
            case EXCITED -> NpcEmotion.EXCITED;
            case UNEASY -> NpcEmotion.UNEASY;
            case ANGRY -> NpcEmotion.ANGRY;
            case AFRAID -> NpcEmotion.AFRAID;
            case SAD -> NpcEmotion.SAD;
            case TENDER -> NpcEmotion.TENDER;
            case AMUSED -> NpcEmotion.AMUSED;
            default -> NpcEmotion.CALM;
        };
    }

    private static VocalEmotion vocalEmotion(NpcEmotion emotion) {
        if (emotion == null) return VocalEmotion.CALM;
        return switch (emotion) {
            case CURIOUS -> VocalEmotion.CURIOUS;
            case EXCITED -> VocalEmotion.EXCITED;
            case UNEASY, SUSPICIOUS -> VocalEmotion.UNEASY;
            case ANGRY -> VocalEmotion.ANGRY;
            case AFRAID -> VocalEmotion.AFRAID;
            case SAD -> VocalEmotion.SAD;
            case TENDER -> VocalEmotion.TENDER;
            case AMUSED -> VocalEmotion.AMUSED;
            case CALM -> VocalEmotion.CALM;
        };
    }

    private static double lysanderIntensity(VocalEmotion emotion) {
        return switch (emotion) {
            case ANGRY -> 0.72;
            case SAD -> 0.52;
            case UNEASY, AFRAID -> 0.58;
            case EXCITED -> 0.48;
            case CURIOUS -> 0.38;
            case TENDER, AMUSED -> 0.30;
            default -> 0.12;
        };
    }

    private static String band(int value, int moderate, int high) {
        return value >= high ? "HIGH(" + value + ")"
                : value >= moderate ? "MODERATE(" + value + ")" : "LOW(" + value + ")";
    }

    private static String normalize(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9' ]", " ").replaceAll("\\s+", " ").strip();
    }

    private static boolean contains(String value, String... needles) {
        for (String needle : needles) if (value.contains(needle)) return true;
        return false;
    }

    private record Decision(
            boolean authorized, SocialIntent intent, String reason, String goal,
            String uncertainty) { }
}
