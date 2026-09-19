package com.inigmasgames.persistentnpcs.scene;

import com.inigmasgames.persistentnpcs.llm.ChatMessage;
import com.inigmasgames.persistentnpcs.llm.LlmProvider;
import com.inigmasgames.persistentnpcs.llm.LlmRequest;
import com.inigmasgames.persistentnpcs.llm.LlmExecutionPolicy;
import com.inigmasgames.persistentnpcs.llm.ConversationRateLimiter;
import com.inigmasgames.persistentnpcs.profile.NpcProfile;
import com.inigmasgames.persistentnpcs.memory.MemoryStore;
import com.inigmasgames.persistentnpcs.relationship.RelationshipStore;
import com.inigmasgames.persistentnpcs.social.GossipRecord;
import com.inigmasgames.persistentnpcs.social.GossipStore;
import com.inigmasgames.persistentnpcs.voice.NpcVoiceService;
import com.inigmasgames.persistentnpcs.voice.VocalState;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import com.inigmasgames.persistentnpcs.autonomy.AgentOperation;
import com.inigmasgames.persistentnpcs.autonomy.AgentOperationStore;
import com.inigmasgames.persistentnpcs.memory.MemoryRecord;
import com.inigmasgames.persistentnpcs.memory.MemoryType;
import com.inigmasgames.persistentnpcs.orbis.OrbisResourceRequest;
import com.inigmasgames.persistentnpcs.orbis.OrbisResourceScheduler;
import com.inigmasgames.persistentnpcs.orbis.ResourcePriority;
import com.inigmasgames.persistentnpcs.orbis.ResourceWorkload;
import java.time.Duration;
import java.util.Set;

/** Generates bounded text-to-text NPC scenes; no STT routing and no unbounded loop. */
public final class NpcSceneRunner {
    private final NpcSceneService scenes;
    private final LlmProvider provider;
    private final ConversationRateLimiter budget;
    private final RelationshipStore relationships;
    private final GossipStore gossip;
    private final MemoryStore memories;
    private final NpcVoiceService voice;
    private final NpcSpeechRouter speech;
    private final AgentOperationStore operations;
    private final OrbisResourceScheduler resources;

    public NpcSceneRunner(
            NpcSceneService scenes,
            LlmProvider provider,
            ConversationRateLimiter budget,
            RelationshipStore relationships,
            GossipStore gossip) {
        this(scenes, provider, budget, relationships, gossip, null, null, null, null, null);
    }

    public NpcSceneRunner(
            NpcSceneService scenes,
            LlmProvider provider,
            ConversationRateLimiter budget,
            RelationshipStore relationships,
            GossipStore gossip,
            MemoryStore memories) {
        this(scenes, provider, budget, relationships, gossip, memories, null, null, null, null);
    }

    public NpcSceneRunner(
            NpcSceneService scenes,
            LlmProvider provider,
            ConversationRateLimiter budget,
            RelationshipStore relationships,
            GossipStore gossip,
            MemoryStore memories,
            NpcVoiceService voice) {
        this(scenes, provider, budget, relationships, gossip, memories, voice, null, null, null);
    }

    public NpcSceneRunner(
            NpcSceneService scenes,
            LlmProvider provider,
            ConversationRateLimiter budget,
            RelationshipStore relationships,
            GossipStore gossip,
            MemoryStore memories,
            NpcVoiceService voice,
            NpcSpeechRouter speech) {
        this(scenes, provider, budget, relationships, gossip, memories, voice, speech, null, null);
    }

    public NpcSceneRunner(
            NpcSceneService scenes,
            LlmProvider provider,
            ConversationRateLimiter budget,
            RelationshipStore relationships,
            GossipStore gossip,
            MemoryStore memories,
            NpcVoiceService voice,
            NpcSpeechRouter speech,
            AgentOperationStore operations) {
        this(scenes, provider, budget, relationships, gossip, memories, voice, speech,
                operations, null);
    }

    public NpcSceneRunner(
            NpcSceneService scenes,
            LlmProvider provider,
            ConversationRateLimiter budget,
            RelationshipStore relationships,
            GossipStore gossip,
            MemoryStore memories,
            NpcVoiceService voice,
            NpcSpeechRouter speech,
            AgentOperationStore operations,
            OrbisResourceScheduler resources) {
        this.scenes = scenes;
        this.provider = provider;
        this.budget = budget;
        this.relationships = relationships;
        this.gossip = gossip;
        this.memories = memories;
        this.voice = voice;
        this.speech = speech;
        this.operations = operations;
        this.resources = resources;
    }

    public CompletableFuture<NpcSceneOutcome> run(
            NpcProfile first,
            NpcProfile second,
            String eventContext,
            double distance,
            Instant now) {
        return run(first, second, new NpcSceneContext(
                eventContext, "None.", "None.", distance, now));
    }

    public CompletableFuture<NpcSceneOutcome> run(
            NpcProfile first,
            NpcProfile second,
            NpcSceneContext untrustedContext) {
        NpcSceneContext context = untrustedContext.normalized();
        AgentOperation operation = operations == null ? null : operations.claim(
                "NPC_RADIANT_CONVERSATION", Set.of(first.id(), second.id()),
                context.event(), context.now(), Duration.ofMinutes(2));
        NpcSceneService.Scene scene;
        try {
            scene = scenes.start(first.id(), second.id(), "CONVERSATION",
                    context.distance(), context.now());
        } catch (RuntimeException failure) {
            if (operation != null) operations.complete(
                    operation.operationId(), false, failure.getMessage());
            throw failure;
        }
        String sharedContext = sharedContext(first, second, context);
        NpcSpeechRouter activeSpeech = speech == null
                ? localSpeechRouter(first, second, context) : speech;
        return generate(scene, first, second, sharedContext, context, activeSpeech, 0)
                .thenApply(completed -> {
                    String summary = completed.turns().stream()
                            .map(turn -> turn.utterance()).collect(
                                    java.util.stream.Collectors.joining(" / "));
                    if (!summary.isBlank()) {
                        gossip.append(new GossipRecord(UUID.randomUUID(), summary,
                                scene.sceneId(), first.id(), first.id(), second.id(),
                                Instant.now(), 0.65));
                    }
                    relationships.adjust(first.id(), second.id(), 0,
                            1, 0, 1, 0, 0, 0, Instant.now());
                    relationships.adjust(second.id(), first.id(), 0,
                            1, 0, 1, 0, 0, 0, Instant.now());
                    if (memories != null && !summary.isBlank()) {
                        Instant completedAt = Instant.now();
                        memories.append(new MemoryRecord(UUID.randomUUID(), first.id(),
                                second.id(), completedAt, MemoryType.EPISODIC, 0.55,
                                "Spoke with " + second.name() + ": " + summary,
                                1.0, "NPC_DIRECT_CONVERSATION", List.of(second.id()),
                                context.worldId() + ":" + context.x() + "," + context.y()
                                        + "," + context.z(),
                                "I spoke directly with " + second.name() + "."));
                        memories.append(new MemoryRecord(UUID.randomUUID(), second.id(),
                                first.id(), completedAt, MemoryType.EPISODIC, 0.55,
                                "Spoke with " + first.name() + ": " + summary,
                                1.0, "NPC_DIRECT_CONVERSATION", List.of(first.id()),
                                context.worldId() + ":" + context.x() + "," + context.y()
                                        + "," + context.z(),
                                "I spoke directly with " + first.name() + "."));
                    }
                    return new NpcSceneOutcome(completed, completed.turns().size(),
                            false, summary);
                }).whenComplete((ignored, failure) -> {
                    if (operation != null) {
                        operations.complete(operation.operationId(), failure == null,
                                failure == null ? "bounded scene completed" : failure.getMessage());
                    }
                    activeSpeech.finishConversation(
                            scene.sceneId(), Instant.now(), failure != null);
                });
    }

    public CompletableFuture<NpcSceneOutcome> run(
            NpcProfile first,
            NpcProfile second,
            NpcConversationTrigger trigger,
            NpcSceneContext spatialContext) {
        if (!first.id().equals(trigger.speakerNpcId())
                || !second.id().equals(trigger.listenerNpcId())) {
            return CompletableFuture.failedFuture(new IllegalArgumentException(
                    "Trigger participants do not match scene profiles"));
        }
        NpcSceneContext base = spatialContext.normalized();
        return run(first, second, new NpcSceneContext(
                "Deterministic trigger topic=" + trigger.topic(), base.perception(),
                base.activeTask(), base.distance(), trigger.createdAt(), base.worldId(),
                base.x(), base.y(), base.z(), base.gameTime(), base.firstState(),
                base.secondState(), base.lineOfSight(), trigger.speakerFacts(),
                trigger.listenerFacts()));
    }

    private String sharedContext(
            NpcProfile first, NpcProfile second, NpcSceneContext context) {
        String firstRelationship = relationships.getOrDefault(first.id(), second.id(),
                first.defaultDisposition()).naturalSummary(second.name());
        String secondRelationship = relationships.getOrDefault(second.id(), first.id(),
                second.defaultDisposition()).naturalSummary(first.name());
        String memoryText = memories == null ? "None." : java.util.stream.Stream.concat(
                        memories.retrieveForCognition(
                                first.id(), second.id(), context.event(), 3).stream(),
                        memories.retrieveForCognition(
                                second.id(), first.id(), context.event(), 3).stream())
                .map(memory -> memory.summary()).distinct()
                .collect(java.util.stream.Collectors.collectingAndThen(
                        java.util.stream.Collectors.joining(" / "),
                        value -> value.isBlank() ? "None." : value));
        String gossipText = java.util.stream.Stream.concat(
                        gossip.knownBy(first.id()).stream(), gossip.knownBy(second.id()).stream())
                .limit(4).map(record -> record.fact())
                .collect(java.util.stream.Collectors.collectingAndThen(
                        java.util.stream.Collectors.joining(" / "),
                        value -> value.isBlank() ? "None." : value));
        return "AUTHORITATIVE EVENT: " + context.event()
                + "\nCURRENT FILTERED PERCEPTION: " + context.perception()
                + "\nACTIVE TASK: " + context.activeTask()
                + "\nRELATIONSHIPS: " + firstRelationship + " " + secondRelationship
                + "\nRELEVANT NPC MEMORY: " + memoryText
                + "\nSOURCED GOSSIP: " + gossipText;
    }

    private CompletableFuture<NpcSceneService.Scene> generate(
            NpcSceneService.Scene scene,
            NpcProfile first,
            NpcProfile second,
            String eventContext,
            NpcSceneContext context,
            NpcSpeechRouter activeSpeech,
            int turn) {
        if (scene.complete() || turn >= scene.maximumTurns()) {
            return CompletableFuture.completedFuture(scene);
        }
        NpcProfile speaker = turn % 2 == 0 ? first : second;
        NpcProfile listener = turn % 2 == 0 ? second : first;
        String privateContext = turn % 2 == 0
                ? context.firstPrivateContext() : context.secondPrivateContext();
        String transcript = scene.turns().isEmpty() ? "None."
                : scene.turns().stream().map(value -> value.speakerNpcId() + ": "
                                + value.utterance())
                        .collect(java.util.stream.Collectors.joining("\n"));
        String system = """
                You are %s speaking directly to %s in a bounded NPC-to-NPC scene.
                Personality: %s
                Listener personality: %s
                Authoritative event/task context: %s
                Facts known specifically to you: %s
                Existing transcript: %s
                The most recent transcript line is exact NPC speech delivered directly to your
                cognition. It did not pass through STT. Never infer private intent not stated in
                the transcript or supplied facts.
                Reply with one concise in-character utterance. Do not invent items, locations,
                completed actions, or third-party knowledge. Do not address the player.
                """.formatted(speaker.name(), listener.name(), speaker.personality(),
                listener.personality(), eventContext, privateContext, transcript);
        UUID providerRequestId = UUID.randomUUID();
        LlmRequest unplanned = new LlmRequest(scene.sceneId(), speaker.id(), listener.id(),
                List.of(new ChatMessage("system", system),
                        new ChatMessage("user", "Continue the scene with one grounded turn.")),
                List.of()).withProviderRequestId(providerRequestId)
                .withExecutionPolicy(new LlmExecutionPolicy(
                        "AUTONOMOUS_DELIBERATION",
                        LlmExecutionPolicy.ReasoningMode.ENABLED,
                        List.of("NPC_TO_NPC_BACKGROUND_SCENE",
                                "BACKGROUND_PRIORITY_YIELDS_TO_PLAYER"), 192))
                .withGenerationParameters(0.25, 112);
        var planDraft = com.inigmasgames.persistentnpcs.conversation.contract
                .TurnPlanCompiler.autonomousSceneSpeech(
                        com.inigmasgames.persistentnpcs.conversation.CognitiveContextPlan.full(
                                "NPC_TO_NPC_BACKGROUND_SCENE"));
        LlmRequest request = unplanned.withTurnExecutionPlan(
                com.inigmasgames.persistentnpcs.conversation.contract.TurnPlanCompiler.compile(
                        scene.sceneId(), providerRequestId, turn, planDraft,
                        unplanned.messages(), null, List.of()));
        CompletableFuture<com.inigmasgames.persistentnpcs.llm.LlmResult> generated;
        if (resources == null) {
            generated = budget.acquire(scene.sceneId()).thenCompose(permit ->
                    provider.generateResponse(request).whenComplete(
                            (ignored, failure) -> permit.close()));
        } else {
            generated = resources.admit(new OrbisResourceRequest(providerRequestId,
                            ResourceWorkload.BACKGROUND_COGNITION, ResourcePriority.LOW,
                            provider, false, 5_000), ignored -> { })
                    .thenCompose(lease -> provider.generateResponse(request).whenComplete(
                            (ignored, failure) -> lease.close()));
        }
        return generated
                .thenCompose(result -> {
                    String utterance = safe(result.text());
                    if (utterance.isBlank()) {
                        return CompletableFuture.completedFuture(
                                scenes.interrupt(scene.sceneId(), Instant.now()));
                    }
                    if (voice != null) {
                        voice.plan(speaker, VocalState.infer(utterance));
                    }
                    NpcSpeechEvent event = new NpcSpeechEvent(speaker.id(), listener.id(),
                            java.util.Set.of(listener.id()), utterance, scene.sceneId(),
                            new NpcSpeechLocation(context.worldId(), context.x(),
                                    context.y(), context.z()),
                            Instant.now(), context.gameTime(), "NPC_CONVERSATION",
                            scene.kind(), VocalState.infer(utterance).emotion().name(),
                            Math.max(2.0, Math.min(8.0, context.distance() + 1.0)));
                    NpcSpeechRouter.DeliveryResult delivery = activeSpeech.route(event);
                    if (!delivery.delivered()) {
                        return CompletableFuture.completedFuture(
                                scenes.interrupt(scene.sceneId(), Instant.now()));
                    }
                    NpcSceneService.Scene updated = scenes.append(
                            scene.sceneId(), speaker.id(), utterance);
                    return generate(updated, first, second, eventContext, context,
                            activeSpeech, turn + 1);
                });
    }

    private static NpcSpeechRouter localSpeechRouter(
            NpcProfile first, NpcProfile second, NpcSceneContext context) {
        NpcSpeechLocation firstLocation = new NpcSpeechLocation(
                context.worldId(), context.x(), context.y(), context.z());
        NpcSpeechLocation secondLocation = new NpcSpeechLocation(
                context.worldId(), context.x() + context.distance(), context.y(), context.z());
        return new NpcSpeechRouter(4, 120, listener -> {
            if (listener.equals(first.id())) {
                return new NpcHearingSnapshot(first.id(), firstLocation,
                        context.firstState(), context.lineOfSight(), null);
            }
            if (listener.equals(second.id())) {
                return new NpcHearingSnapshot(second.id(), secondLocation,
                        context.secondState(), context.lineOfSight(), null);
            }
            return null;
        }, (listener, event) -> { }, event -> { }, NpcSpeechAttention.noOp(),
                ignored -> { });
    }

    private static String safe(String value) {
        String text = value == null ? "" : value.replaceAll("\\s+", " ").strip();
        return text.length() <= 400 ? text : text.substring(0, 400);
    }
}
