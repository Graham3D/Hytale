package com.inigmasgames.persistentnpcs.evaluation;

import com.inigmasgames.persistentnpcs.action.NpcActionRegistry;
import com.inigmasgames.persistentnpcs.ai.AiProviderDefinition;
import com.inigmasgames.persistentnpcs.ai.LlmProviderCatalog;
import com.inigmasgames.persistentnpcs.cognition.CognitionTraceStore;
import com.inigmasgames.persistentnpcs.cognition.NpcCognitionService;
import com.inigmasgames.persistentnpcs.cognition.PlayerFactMemoryService;
import com.inigmasgames.persistentnpcs.cognition.ResponseLatencyTraceStore;
import com.inigmasgames.persistentnpcs.conversation.ContentCatalog;
import com.inigmasgames.persistentnpcs.conversation.ConversationContextBuilder;
import com.inigmasgames.persistentnpcs.conversation.ConversationGroundingService;
import com.inigmasgames.persistentnpcs.conversation.ConversationInvocation;
import com.inigmasgames.persistentnpcs.conversation.ConversationLifecycleObserver;
import com.inigmasgames.persistentnpcs.conversation.ConversationOutcome;
import com.inigmasgames.persistentnpcs.llm.ConversationRateLimiter;
import com.inigmasgames.persistentnpcs.conversation.ConversationService;
import com.inigmasgames.persistentnpcs.conversation.ConversationSession;
import com.inigmasgames.persistentnpcs.conversation.ConversationSessionManager;
import com.inigmasgames.persistentnpcs.conversation.MinimalWorldContext;
import com.inigmasgames.persistentnpcs.epistemic.Answerability;
import com.inigmasgames.persistentnpcs.json.JsonFiles;
import com.inigmasgames.persistentnpcs.llm.LlmProvider;
import com.inigmasgames.persistentnpcs.llm.OpenAiCompatibleProvider;
import com.inigmasgames.persistentnpcs.llm.PinnedLlmProvider;
import com.inigmasgames.persistentnpcs.orbis.BranchCognitionSnapshot;
import com.inigmasgames.persistentnpcs.orbis.CancellationReason;
import com.inigmasgames.persistentnpcs.orbis.CanonicalSpeechChunk;
import com.inigmasgames.persistentnpcs.orbis.OrbisAudienceGateway;
import com.inigmasgames.persistentnpcs.orbis.OrbisCognitionGateway;
import com.inigmasgames.persistentnpcs.orbis.OrbisDiagnostics;
import com.inigmasgames.persistentnpcs.orbis.OrbisEvent;
import com.inigmasgames.persistentnpcs.orbis.OrbisEventType;
import com.inigmasgames.persistentnpcs.orbis.OrbisRuntimeFactory;
import com.inigmasgames.persistentnpcs.orbis.OrbisTurnCoordinator;
import com.inigmasgames.persistentnpcs.profile.NpcProfile;
import com.inigmasgames.persistentnpcs.profile.ProfileRepository;
import com.inigmasgames.persistentnpcs.voice.EligibleNpcListener;
import com.inigmasgames.persistentnpcs.voice.PlayerSpeechIntent;
import com.inigmasgames.persistentnpcs.voice.PlayerUtteranceAudienceService;
import com.inigmasgames.persistentnpcs.voice.PlayerUtteranceEvent;
import com.inigmasgames.persistentnpcs.voice.TranscribedPlayerUtterance;
import com.inigmasgames.persistentnpcs.voice.UtteranceRangeClass;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * Campaign-scoped, production-parity Orbis host. Only Hytale boundary adapters are replaced.
 */
public final class OrbisEvaluationHost implements AutoCloseable {
    private final Path productionRoot;
    private final ScenarioSandbox sandbox;
    private final EvaluationContracts.EvaluationMode mode;
    private final LlmProvider provider;
    private final PinnedLlmProvider pinned;
    private final OrbisDiagnostics diagnostics = new OrbisDiagnostics(8_192);
    private final EvaluationObservationBus observations = new EvaluationObservationBus(8_192);
    private final EvaluationSpeechSink speech = new EvaluationSpeechSink();
    private final ConcurrentHashMap<UUID, PendingTurn> pendingByUtterance =
            new ConcurrentHashMap<>();
    private final Consumer<String> log;
    private ScenarioSandbox.State state;
    private ConversationSessionManager sessions;
    private ConversationService conversations;
    private EvaluationTextIngress ingress;
    private OrbisTurnCoordinator coordinator;
    private EvaluationContracts.ConversationScenario scenario;
    private UUID worldId;
    private final Instant startedAt = Instant.now();

    public OrbisEvaluationHost(Path evaluationRoot, Path productionRoot, String runId,
            EvaluationContracts.EvaluationMode mode, LlmProvider provider,
            String providerName, String model, String endpoint, Consumer<String> log) {
        this.productionRoot = productionRoot.toAbsolutePath().normalize();
        this.sandbox = new ScenarioSandbox(evaluationRoot, productionRoot, runId);
        this.mode = mode;
        this.provider = java.util.Objects.requireNonNull(provider, "provider");
        this.pinned = new PinnedLlmProvider(providerName, model, endpoint, provider);
        this.log = log == null ? ignored -> { } : log;
        diagnostics.subscribe(observations);
        diagnostics.subscribe(this::observeTerminal);
    }

    public static OrbisEvaluationHost live(Path evaluationRoot, Path productionRoot,
            String runId, Consumer<String> log) {
        var framework = JsonFiles.read(productionRoot.resolve("config.json"),
                com.inigmasgames.persistentnpcs.config.FrameworkConfig.class).validated();
        LlmProviderCatalog catalog = JsonFiles.read(productionRoot.resolve(
                "llm-providers.json"), LlmProviderCatalog.class).validated();
        AiProviderDefinition definition = catalog.providers().get(catalog.activeProvider());
        if (definition == null || !LlmProviderCatalog.NEMOTRON.equals(
                catalog.activeProvider())) throw new IllegalStateException(
                        "LIVE_HEADLESS requires the active production Nemotron provider");
        var selected = framework.forAiProvider(definition).validated();
        LlmProvider provider = new OpenAiCompatibleProvider(selected,
                OpenAiCompatibleProvider.ToolChoicePolicy.parse(
                        definition.effectiveToolChoiceMode("NAMED_SINGLE")), log,
                definition.ollamaGpuLayers(), definition.effectiveOllamaKeepAlive());
        return new OrbisEvaluationHost(evaluationRoot, productionRoot, runId,
                EvaluationContracts.EvaluationMode.LIVE_HEADLESS, provider,
                catalog.activeProvider(), definition.effectiveModel(framework.model()),
                definition.effectiveEndpoint(framework.endpoint()), log);
    }

    public synchronized EvaluationRunHandle start(
            EvaluationContracts.ConversationScenario value) {
        if (scenario != null) throw new IllegalStateException("evaluation run already started");
        scenario = java.util.Objects.requireNonNull(value, "scenario");
        if (value.resetPolicy() != EvaluationContracts.ResetPolicy.RESET_EACH_SCENARIO) {
            throw new IllegalArgumentException(
                    "H0-H8 host supports RESET_EACH_SCENARIO only; no policy is silently ignored");
        }
        state = sandbox.initialize(value, 2_000);
        worldId = UUID.nameUUIDFromBytes(("orbis-eval-world:" + value.world().worldName())
                .getBytes(java.nio.charset.StandardCharsets.UTF_8));
        sessions = new ConversationSessionManager(Duration.ofMinutes(30), state.memories());
        ConversationContextBuilder context = new ConversationContextBuilder(
                state.relationships(), state.memories(), state.tasks(), state.quests(),
                state.plans(), 8);
        NpcCognitionService cognition = new NpcCognitionService(state.relationships(),
                state.tasks(), state.emotions(), state.profiles(), state.memories(),
                state.obligations(), state.plans(), state.operations(), state.beliefs(),
                new CognitionTraceStore(), new ResponseLatencyTraceStore());
        conversations = OrbisRuntimeFactory.createConversation(
                new OrbisRuntimeFactory.ConversationComposition(context, provider,
                        state.relationships(), state.memories(),
                        EvaluationActionRegistry.create(state.profiles().profiles()),
                        new EvaluationPerceptionGateway(value.world(), worldId), 1_200, log,
                        new ConversationRateLimiter(600),
                        new ConversationGroundingService(ContentCatalog.unavailable()),
                        cognition, null, null));
        EvaluationAudienceGateway audience = new EvaluationAudienceGateway();
        EvaluationCognitionGateway cognitionGateway = new EvaluationCognitionGateway();
        coordinator = OrbisRuntimeFactory.create(new OrbisRuntimeFactory.Composition(
                null, audience, cognitionGateway, null, ignored -> true, () -> pinned,
                null, diagnostics, 120, 3_000, 30_000, log));
        ingress = new EvaluationTextIngress(coordinator);
        return new EvaluationRunHandle(value.id(), mode, sandbox.root(), runtimeIdentity(),
                sandbox.snapshot());
    }

    public CompletableFuture<TurnEvaluationResult> submit(
            EvaluationContracts.ScenarioTurn turn) {
        if (scenario == null || ingress == null) return CompletableFuture.failedFuture(
                new IllegalStateException("evaluation run is not started"));
        if (turn.ingress() != EvaluationContracts.IngressKind.AUTHORITATIVE_EVALUATION_TEXT) {
            return CompletableFuture.failedFuture(new UnsupportedOperationException(
                    "RECORDED_VOICE requires connected/recorded ingress and is not text replay"));
        }
        if (turn.pacing() != EvaluationContracts.PacingPolicy.WAIT_FOR_TERMINAL
                && turn.pacing() != EvaluationContracts.PacingPolicy.CONCURRENT_SCENE) {
            return CompletableFuture.failedFuture(new UnsupportedOperationException(
                    "Pacing policy is not implemented by this host: " + turn.pacing()));
        }
        CognitiveSnapshot before = cognitiveSnapshot();
        long sequence = observations.latestSequence();
        long startedNanos = System.nanoTime();
        UUID utteranceId = UUID.randomUUID();
        PendingTurn pending = new PendingTurn(utteranceId, turn, before, sequence,
                startedNanos, new CompletableFuture<>());
        pendingByUtterance.put(utteranceId, pending);
        ingress.submit(utteranceId, turn.speaker(), worldId, scenario.world().x(),
                scenario.world().y(), scenario.world().z(), turn.utterance(), turn.audience());
        return pending.future.orTimeout(turn.expected().maximumLatencyMillis() + 2_000,
                TimeUnit.MILLISECONDS);
    }

    public EvaluationStateSnapshot snapshot() {
        return new EvaluationStateSnapshot(sandbox.snapshot(), observations.snapshot(),
                speech.captures(), pendingByUtterance.size(), runtimeIdentity());
    }

    public synchronized void reset(EvaluationContracts.ConversationScenario value) {
        closeRuntime();
        scenario = null;
        observations.clear(); speech.clear(); pendingByUtterance.clear();
        start(value);
    }

    public EvaluationRunReport finish() {
        sandbox.assertNoProductionWriteEscape();
        return new EvaluationRunReport(scenario == null ? "not-started" : scenario.id(),
                mode, runtimeIdentity(), startedAt, Instant.now(), observations.snapshot(),
                sandbox.snapshot(), pendingByUtterance.isEmpty(),
                "tools/orbis-eval/run-campaign.ps1 -Scenario "
                        + (scenario == null ? "unknown" : scenario.id()) + " -Mode " + mode);
    }

    private void observeTerminal(OrbisEvent event) {
        String utterance = event.facts().get("utteranceId");
        if (utterance == null) return;
        UUID utteranceId;
        try { utteranceId = UUID.fromString(utterance); }
        catch (IllegalArgumentException ignored) { return; }
        PendingTurn pending = pendingByUtterance.get(utteranceId);
        if (pending == null) return;
        if (event.type() == OrbisEventType.TURN_CREATED) pending.turnId = event.turnId().value();
        if (event.type() == OrbisEventType.DECISION_COMMITTED) {
            pending.responses.put(event.facts().getOrDefault("npc", "unknown"),
                    event.facts().getOrDefault("canonicalSpokenText", ""));
        }
        if (event.type() != OrbisEventType.TURN_COMPLETED
                && event.type() != OrbisEventType.TURN_FAILED
                && event.type() != OrbisEventType.TURN_CANCELLED) return;
        if (!pendingByUtterance.remove(utteranceId, pending)) return;
        try {
            ingress.completed(utteranceId);
            long elapsed = TimeUnit.NANOSECONDS.toMillis(
                    Math.max(0L, System.nanoTime() - pending.startedNanos));
            List<EvaluationContracts.StageObservation> turnObservations = observations
                    .since(pending.startSequence).stream().filter(value -> utterance.equals(
                            value.facts().get("utteranceId"))).toList();
            String canonical = String.join("\n", pending.responses.values());
            var stateDelta = stateDelta(pending.before, cognitiveSnapshot());
            List<EvaluationContracts.StageVerdict> verdicts = new ExpectedTurnOracle().evaluate(
                    pending.turn.expected(), turnObservations, canonical, stateDelta, elapsed);
            var diagnosis = new EarliestBoundaryDiagnoser().diagnose(verdicts, turnObservations);
            pending.future.complete(new TurnEvaluationResult(pending.turnId, utteranceId,
                    event.type().name(), Map.copyOf(pending.responses), turnObservations,
                    verdicts, diagnosis, stateDelta, elapsed));
        } catch (RuntimeException failure) {
            pending.future.completeExceptionally(failure);
        }
    }

    private ExpectedTurnOracle.StateDeltaSnapshot stateDelta(
            CognitiveSnapshot before, CognitiveSnapshot after) {
        Set<String> memoriesAdded = difference(after.memories(), before.memories());
        Set<String> beliefsAdded = difference(after.beliefs(), before.beliefs());
        Set<String> relationshipsChanged = difference(after.relationships(),
                before.relationships());
        Set<String> forbidden = beliefsAdded.stream().filter(value ->
                value.contains("generatedSpeechOnly=true")
                        || value.contains("source=CONVERSATION_WORKSPACE"))
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
        return new ExpectedTurnOracle.StateDeltaSnapshot(memoriesAdded, beliefsAdded,
                relationshipsChanged, forbidden);
    }

    private CognitiveSnapshot cognitiveSnapshot() {
        if (state == null || scenario == null) return CognitiveSnapshot.empty();
        LinkedHashSet<String> memories = new LinkedHashSet<>();
        LinkedHashSet<String> beliefs = new LinkedHashSet<>();
        LinkedHashSet<String> relationships = new LinkedHashSet<>();
        for (var actor : scenario.actors()) {
            state.memories().forNpc(actor.stableId()).forEach(value -> memories.add(
                    value.memoryId() + "|" + value.type() + "|" + value.summary()));
            state.beliefs().current(actor.stableId(), null, "").forEach(value -> beliefs.add(
                    value.assertionId() + "|" + value.statement() + "|source="
                            + value.provenance().sourceKind() + "|generatedSpeechOnly="
                            + value.provenance().generatedSpeechOnly()));
            state.relationships().forNpc(actor.stableId()).forEach(value -> relationships.add(
                    value.npcId() + ":" + value.playerId() + "|interactions="
                            + value.interactionCount() + "|disposition=" + value.disposition()
                            + "|trust=" + value.trust() + "|familiarity="
                            + value.familiarity() + "|type=" + value.relationshipType()));
        }
        return new CognitiveSnapshot(Set.copyOf(memories), Set.copyOf(beliefs),
                Set.copyOf(relationships));
    }

    private static Set<String> difference(Set<String> after, Set<String> before) {
        LinkedHashSet<String> values = new LinkedHashSet<>(after);
        values.removeAll(before);
        return Set.copyOf(values);
    }

    private Map<String, String> runtimeIdentity() {
        LinkedHashMap<String, String> values = new LinkedHashMap<>();
        values.put("provider", pinned.provider()); values.put("model", pinned.model());
        values.put("endpoint", pinned.endpoint()); values.put("mode", mode.name());
        values.put("schemaVersion", Integer.toString(EvaluationContracts.SCHEMA_VERSION));
        values.put("productionRootHash", hashText(productionRoot.toString()));
        values.put("profileHash", state == null ? "pending" : sandbox.snapshot().contentHash());
        return Map.copyOf(values);
    }

    @Override public synchronized void close() {
        closeRuntime();
        sandbox.close();
    }

    private void closeRuntime() {
        if (coordinator != null) coordinator.close();
        if (sessions != null) sessions.clear();
        coordinator = null; ingress = null; sessions = null; conversations = null;
    }

    private static String hashText(String text) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(
                    text.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    private final class EvaluationAudienceGateway implements OrbisAudienceGateway {
        private final PlayerFactMemoryService facts = new PlayerFactMemoryService(
                state.profiles(), state.beliefs(), state.memories());

        @Override public CompletableFuture<PlayerUtteranceAudienceService.Resolution> resolve(
                TranscribedPlayerUtterance utterance) {
            List<UUID> requested = ingress.audience(utterance.utteranceId());
            List<NpcProfile> actors = requested.isEmpty() ? scenario.actors().stream()
                    .map(actor -> state.profiles().byId(actor.stableId()).orElseThrow()).toList()
                    : requested.stream().map(id -> state.profiles().byId(id).orElseThrow()).toList();
            ArrayList<EligibleNpcListener> listeners = new ArrayList<>();
            LinkedHashMap<UUID, com.inigmasgames.persistentnpcs.cognition.GroundedIntent>
                    intents = new LinkedHashMap<>();
            LinkedHashMap<UUID, PlayerFactMemoryService.PersistenceResult> writes =
                    new LinkedHashMap<>();
            LinkedHashSet<UUID> direct = new LinkedHashSet<>();
            for (int index = 0; index < actors.size(); index++) {
                NpcProfile actor = actors.get(index);
                boolean addressed = utterance.transcript().toLowerCase(java.util.Locale.ROOT)
                        .contains(actor.name().toLowerCase(java.util.Locale.ROOT));
                if (addressed) direct.add(actor.id());
                listeners.add(new EligibleNpcListener(actor.id(), actor.name(), 2.0 + index,
                        "nearby", index == 0 ? "ahead" : "nearby",
                        UtteranceRangeClass.ORDINARY, addressed, true, 1_000 - index));
                intents.put(actor.id(), com.inigmasgames.persistentnpcs.cognition.GroundedIntent
                        .PROCESS_INFORMATION);
                ConversationSession session = sessions.focus(actor.id(), utterance.playerId(),
                        utterance.timestamp());
                boolean npcSpeaker = scenario.actors().stream().anyMatch(value ->
                        value.stableId().equals(utterance.playerId()));
                if (!npcSpeaker) writes.put(actor.id(), facts.persist(actor.id(),
                        utterance.playerId(), session.sessionId(), null,
                        utterance.utteranceId(), utterance.transcript(), utterance.timestamp()));
            }
            List<EligibleNpcListener> owners = direct.isEmpty() ? List.of(listeners.getFirst())
                    : listeners.stream().filter(value -> direct.contains(value.npcId())).toList();
            PlayerUtteranceEvent event = new PlayerUtteranceEvent(utterance.utteranceId(),
                    utterance.playerId(), utterance.transcript(), utterance.worldId(),
                    utterance.playerX(), utterance.playerY(), utterance.playerZ(),
                    utterance.timestamp(), Set.copyOf(direct), PlayerSpeechIntent.CONVERSATION,
                    listeners, 0, 0, 0);
            return CompletableFuture.completedFuture(new PlayerUtteranceAudienceService
                    .Resolution(event, owners, Map.copyOf(intents), Map.of(), Map.copyOf(writes)));
        }
    }

    private final class EvaluationCognitionGateway implements OrbisCognitionGateway {
        @Override public CompletableFuture<ConversationOutcome> begin(
                BranchCognitionSnapshot snapshot, PinnedLlmProvider selected,
                ConversationLifecycleObserver observer) {
            NpcProfile profile = state.profiles().byId(snapshot.npcStableId()).orElseThrow();
            ConversationSession session = sessions.focus(profile.id(),
                    snapshot.playerStableId(), Instant.now());
            session.setDeferredConversationContext(snapshot.deferredConversationContext());
            session.setPlayerUtteranceContext(new ConversationSession.PlayerUtteranceContext(
                    snapshot.utteranceId(), snapshot.speechIntent(), snapshot.rangeClass(),
                    snapshot.directAddress(), snapshot.distanceBand(),
                    snapshot.directionFromPlayer(), snapshot.projection(),
                    snapshot.endpointMillis(), snapshot.sttMillis(),
                    snapshot.audienceResolutionMillis()));
            ConversationInvocation invocation = new ConversationInvocation(
                    snapshot.responseId().value(), snapshot.providerRequestId().value(),
                    selected.delegate(), () -> !snapshot.cancellation().isCancelled(),
                    observer == null ? ConversationLifecycleObserver.none() : observer,
                    snapshot.branchEpoch());
            MinimalWorldContext world = new MinimalWorldContext(state.world().worldName(),
                    state.world().x(), state.world().y(), state.world().z());
            return conversations.converseForOrbis(session, profile,
                    snapshot.canonicalTranscript(), world, invocation, (delta, vocal) -> { });
        }

        @Override public CompletableFuture<Void> commit(BranchCognitionSnapshot snapshot,
                ConversationOutcome outcome) {
            return finalizeCommit(snapshot, outcome, List.of(), 0);
        }

        @Override public CompletableFuture<Void> finalizeCommit(
                BranchCognitionSnapshot snapshot, ConversationOutcome outcome,
                List<CanonicalSpeechChunk> chunks, int alreadyCommittedCount) {
            speech.commit(snapshot.responseId().value(), snapshot.npcStableId(),
                    snapshot.npcName(), chunks, outcome.dialogue());
            ConversationSession session = sessions.focus(snapshot.npcStableId(),
                    snapshot.playerStableId(), Instant.now());
            conversations.recordDeliveredConversation(session,
                    state.profiles().byId(snapshot.npcStableId()).orElseThrow(),
                    snapshot.canonicalTranscript(), snapshot.responseId().value(),
                    outcome.dialogue(), outcome.dialogueMode(), false);
            conversations.recordCommittedSpokenText(snapshot.npcStableId(),
                    snapshot.responseId().value(), outcome.dialogue());
            return CompletableFuture.completedFuture(null);
        }

        @Override public void failed(BranchCognitionSnapshot snapshot,
                CancellationReason reason, Throwable failure) {
            if (snapshot != null) conversations.cancelForOrbis(
                    snapshot.responseId().value(), snapshot.providerRequestId().value());
        }
    }

    private static final class PendingTurn {
        private final UUID utteranceId;
        private final EvaluationContracts.ScenarioTurn turn;
        private final CognitiveSnapshot before;
        private final long startSequence;
        private final long startedNanos;
        private final CompletableFuture<TurnEvaluationResult> future;
        private final LinkedHashMap<String, String> responses = new LinkedHashMap<>();
        private volatile UUID turnId;
        private PendingTurn(UUID utteranceId, EvaluationContracts.ScenarioTurn turn,
                CognitiveSnapshot before, long startSequence,
                long startedNanos, CompletableFuture<TurnEvaluationResult> future) {
            this.utteranceId = utteranceId; this.turn = turn; this.before = before;
            this.startSequence = startSequence; this.startedNanos = startedNanos;
            this.future = future;
        }
    }

    private record CognitiveSnapshot(Set<String> memories, Set<String> beliefs,
            Set<String> relationships) {
        private static CognitiveSnapshot empty() {
            return new CognitiveSnapshot(Set.of(), Set.of(), Set.of());
        }
    }

    public record EvaluationRunHandle(String scenarioId,
            EvaluationContracts.EvaluationMode mode, Path sandboxRoot,
            Map<String, String> runtimeIdentity,
            ScenarioSandbox.SandboxSnapshot initialSnapshot) { }
    public record EvaluationStateSnapshot(ScenarioSandbox.SandboxSnapshot sandbox,
            List<EvaluationContracts.StageObservation> observations,
            Map<UUID, EvaluationSpeechSink.CanonicalCapture> canonicalResponses,
            int pendingTurns, Map<String, String> runtimeIdentity) { }
    public record TurnEvaluationResult(UUID turnId, UUID utteranceId, String terminalState,
            Map<String, String> canonicalResponses,
            List<EvaluationContracts.StageObservation> observations,
            List<EvaluationContracts.StageVerdict> verdicts,
            EvaluationContracts.RootCauseDiagnosis diagnosis,
            ExpectedTurnOracle.StateDeltaSnapshot stateDelta, long elapsedMillis) {
        public boolean passed() { return diagnosis == null; }
    }
    public record EvaluationRunReport(String scenarioId,
            EvaluationContracts.EvaluationMode mode, Map<String, String> runtimeIdentity,
            Instant startedAt, Instant finishedAt,
            List<EvaluationContracts.StageObservation> observations,
            ScenarioSandbox.SandboxSnapshot finalSnapshot, boolean cleanTerminal,
            String reproductionCommand) { }
}
