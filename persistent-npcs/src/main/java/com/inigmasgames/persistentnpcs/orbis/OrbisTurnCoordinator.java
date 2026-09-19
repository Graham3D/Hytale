package com.inigmasgames.persistentnpcs.orbis;

import com.inigmasgames.persistentnpcs.cognition.ActionPromiseGuard;
import com.inigmasgames.persistentnpcs.conversation.ConversationLifecycleObserver;
import com.inigmasgames.persistentnpcs.conversation.ConversationOutcome;
import com.inigmasgames.persistentnpcs.conversation.SpokenTextSafetyValidator;
import com.inigmasgames.persistentnpcs.llm.PinnedLlmProvider;
import com.inigmasgames.persistentnpcs.voice.EligibleNpcListener;
import com.inigmasgames.persistentnpcs.voice.PlayerUtteranceAudienceService;
import com.inigmasgames.persistentnpcs.voice.SpeechToTextProvider;
import com.inigmasgames.persistentnpcs.voice.SpeechTranscript;
import com.inigmasgames.persistentnpcs.voice.SttSemanticCorrector;
import com.inigmasgames.persistentnpcs.voice.SpeechPhraseChunker;
import com.inigmasgames.persistentnpcs.voice.TranscribedPlayerUtterance;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * The sole writer for capture, STT, audience, branch identity, and cancellation state.
 * Provider and world-thread callbacks enqueue immutable results back onto this executor.
 */
public final class OrbisTurnCoordinator implements AutoCloseable {
    private static final int MAX_RETAINED_TURNS = 64;
    private static final int BARGE_IN_CONFIRMED_FRAMES = 5; // ~100 ms at Update 6's 20 ms frames.
    private static final long DUPLICATE_TEXT_WINDOW_MILLIS = 15_000L;
    private static final long RECENTLY_COMPLETED_DUPLICATE_WINDOW_MILLIS = 1_500L;
    private final ExecutorService coordinator = Executors.newSingleThreadExecutor(task -> {
        Thread thread = new Thread(task, "orbis-turn-coordinator");
        thread.setDaemon(true);
        return thread;
    });
    private final ScheduledExecutorService boundaryTimer =
            Executors.newSingleThreadScheduledExecutor(task -> {
                Thread thread = new Thread(task, "orbis-ptt-boundary-adapter");
                thread.setDaemon(true);
                return thread;
            });
    private final SpeechToTextProvider stt;
    private final OrbisAudienceGateway audience;
    private final Predicate<UUID> eligiblePlayer;
    private final OrbisCognitionGateway cognitionGateway;
    private final Supplier<PinnedLlmProvider> pinnedLlmProvider;
    private final OrbisDiagnostics diagnostics;
    private final Consumer<String> log;
    private final OrbisSpeechCoordinator speechCoordinator;
    private final OrbisResourceScheduler resources;
    private final long packetRunReleaseMillis;
    private final long providerTimeoutMillis;
    private final int maximumFrames;
    private final AtomicLong eventSequence = new AtomicLong();
    private final AtomicBoolean closed = new AtomicBoolean();

    // Every collection below is touched only on coordinator.
    private final Map<UUID, PlayerCaptureSession> captures = new LinkedHashMap<>();
    private final Map<TurnId, OrbisTurn> turns = new LinkedHashMap<>();
    private final ArrayDeque<TurnId> turnOrder = new ArrayDeque<>();
    private final Map<UUID, Long> playerEpochs = new LinkedHashMap<>();
    private final CancellationScope serverCancellation = new CancellationScope();
    private final Map<UUID, CancellationScope> playerCancellations = new LinkedHashMap<>();
    private final Map<UUID, Long> npcEpochs = new LinkedHashMap<>();
    private final Map<UUID, BranchLocator> responseBranches = new LinkedHashMap<>();
    private final Map<UUID, PinnedLlmProvider> branchProviders = new LinkedHashMap<>();
    private final Map<UUID, BranchCognitionSnapshot> cognitionSnapshots =
            new LinkedHashMap<>();
    private final Map<ConversationKey, ConversationFloor> floors = new LinkedHashMap<>();
    private final DeferredTopicStore deferredTopics = new DeferredTopicStore();
    private final Map<UUID, RecentVoiceTranscript> recentVoiceTranscripts =
            new LinkedHashMap<>();

    /** Phase 2 production constructor with Orbis-owned cognition and exact LLM pinning. */
    public OrbisTurnCoordinator(SpeechToTextProvider stt, OrbisAudienceGateway audience,
            OrbisCognitionGateway cognitionGateway,
            Predicate<UUID> eligiblePlayer,
            Supplier<PinnedLlmProvider> pinnedLlmProvider,
            OrbisDiagnostics diagnostics, long packetRunReleaseMillis, int maximumFrames,
            long providerTimeoutMillis, Consumer<String> log) {
        this.stt = stt;
        this.audience = audience;
        this.cognitionGateway = java.util.Objects.requireNonNull(
                cognitionGateway, "cognitionGateway");
        this.pinnedLlmProvider = java.util.Objects.requireNonNull(
                pinnedLlmProvider, "pinnedLlmProvider");
        this.speechCoordinator = null;
        this.resources = null;
        this.eligiblePlayer = eligiblePlayer == null ? ignored -> true : eligiblePlayer;
        this.diagnostics = diagnostics == null ? new OrbisDiagnostics() : diagnostics;
        this.packetRunReleaseMillis = Math.max(80, packetRunReleaseMillis);
        this.providerTimeoutMillis = Math.max(50, providerTimeoutMillis);
        this.maximumFrames = Math.max(1, maximumFrames);
        this.log = log == null ? ignored -> { } : log;
    }

    /** Phase 3 production constructor with Orbis-owned TTS admission and playback. */
    public OrbisTurnCoordinator(SpeechToTextProvider stt, OrbisAudienceGateway audience,
            OrbisCognitionGateway cognitionGateway,
            OrbisSpeechCoordinator speechCoordinator,
            Predicate<UUID> eligiblePlayer,
            Supplier<PinnedLlmProvider> pinnedLlmProvider,
            OrbisDiagnostics diagnostics, long packetRunReleaseMillis, int maximumFrames,
            long providerTimeoutMillis, Consumer<String> log) {
        this(stt, audience, cognitionGateway, speechCoordinator, eligiblePlayer,
                pinnedLlmProvider, null, diagnostics, packetRunReleaseMillis,
                maximumFrames, providerTimeoutMillis, log);
    }

    /** Phase 5 production constructor with centralized asynchronous resource admission. */
    public OrbisTurnCoordinator(SpeechToTextProvider stt, OrbisAudienceGateway audience,
            OrbisCognitionGateway cognitionGateway,
            OrbisSpeechCoordinator speechCoordinator,
            Predicate<UUID> eligiblePlayer,
            Supplier<PinnedLlmProvider> pinnedLlmProvider,
            OrbisResourceScheduler resources,
            OrbisDiagnostics diagnostics, long packetRunReleaseMillis, int maximumFrames,
            long providerTimeoutMillis, Consumer<String> log) {
        this.stt = stt;
        this.audience = audience;
        this.cognitionGateway = java.util.Objects.requireNonNull(
                cognitionGateway, "cognitionGateway");
        this.speechCoordinator = java.util.Objects.requireNonNull(
                speechCoordinator, "speechCoordinator");
        this.resources = resources;
        this.pinnedLlmProvider = java.util.Objects.requireNonNull(
                pinnedLlmProvider, "pinnedLlmProvider");
        this.eligiblePlayer = eligiblePlayer == null ? ignored -> true : eligiblePlayer;
        this.diagnostics = diagnostics == null ? new OrbisDiagnostics() : diagnostics;
        this.packetRunReleaseMillis = Math.max(80, packetRunReleaseMillis);
        this.providerTimeoutMillis = Math.max(50, providerTimeoutMillis);
        this.maximumFrames = Math.max(1, maximumFrames);
        this.log = log == null ? ignored -> { } : log;
    }

    public void accept(CapturedVoiceFrame frame) {
        if (frame == null || closed.get()) return;
        enqueue(() -> acceptOnCoordinator(frame));
    }

    /** Authoritative text ingress. Text bypasses STT but uses the same audience and branch path. */
    public void accept(TranscribedPlayerUtterance utterance) {
        if (utterance == null || utterance.transcript().isBlank() || closed.get()) return;
        enqueue(() -> acceptTranscriptOnCoordinator(utterance));
    }

    private void acceptTranscriptOnCoordinator(TranscribedPlayerUtterance utterance) {
        RecentVoiceTranscript recent = recentVoiceTranscripts.get(utterance.playerId());
        if (recent != null) {
            long ageMillis = Math.max(0L, Instant.now().toEpochMilli()
                    - recent.completedAt().toEpochMilli());
            OrbisTurn sourceTurn = turns.get(recent.turnId());
            boolean sourceActive = sourceTurn != null && !terminal(sourceTurn.state());
            boolean duplicate = normalizeTranscript(utterance.transcript())
                    .equals(recent.normalizedText())
                    && ageMillis <= DUPLICATE_TEXT_WINDOW_MILLIS
                    && (sourceActive
                            || ageMillis <= RECENTLY_COMPLETED_DUPLICATE_WINDOW_MILLIS);
            if (duplicate) {
                emit(OrbisEventType.DUPLICATE_UTTERANCE_SUPPRESSED, sourceTurn, null,
                        Map.of("source", "AUTHORITATIVE_TEXT",
                                "duplicateOf", recent.utteranceId().value().toString(),
                                "transcript", compact(utterance.transcript(), 4_000),
                                "ageMs", Long.toString(ageMillis),
                                "sourceTurnActive", Boolean.toString(sourceActive)));
                return;
            }
            if (ageMillis > DUPLICATE_TEXT_WINDOW_MILLIS) {
                recentVoiceTranscripts.remove(utterance.playerId());
            }
        }
        PlayerCaptureSession capture = captures.remove(utterance.playerId());
        if (capture != null) {
            OrbisTurn captured = turns.get(capture.turnId());
            if (captured != null && !terminal(captured.state())) {
                cancelTurnOnCoordinator(captured, CancellationReason.SUPERSEDED);
            }
        }
        ConversationKey key = new ConversationKey(utterance.playerId(), utterance.worldId());
        ConversationFloor priorFloor = floors.get(key);
        if (priorFloor != null && priorFloor.activeTurnId != null
                && priorFloor.owner != ConversationFloorOwner.NONE) {
            OrbisTurn prior = turns.get(priorFloor.activeTurnId);
            if (prior != null && !terminal(prior.state())) {
                cancelTurnOnCoordinator(prior, CancellationReason.USER_BARGE_IN);
            }
        }
        long epoch = playerEpochs.merge(utterance.playerId(), 1L, Long::sum);
        CancellationScope playerScope = playerCancellations.computeIfAbsent(
                utterance.playerId(), ignored -> serverCancellation.child());
        OrbisTurn turn = new OrbisTurn(TurnId.create(),
                new UtteranceId(utterance.utteranceId()), utterance.playerId(),
                utterance.worldId(), epoch, utterance.ingressSource(),
                utterance.originalPhysicalUtteranceId(), playerScope.child());
        turn.transcript(utterance.transcript());
        turn.state(OrbisTurn.State.RESOLVING_AUDIENCE);
        retain(turn);
        ConversationFloor floor = new ConversationFloor(ConversationFloorOwner.PLAYER);
        floor.activeTurnId = turn.turnId();
        floor.lastTransition = Instant.now();
        floors.put(key, floor);
        emit(OrbisEventType.TURN_CREATED, turn, null, Map.of(
                "playerId", utterance.playerId().toString(),
                "ingress", utterance.ingressSource().name()));
        emit(OrbisEventType.FLOOR_GRANTED, turn, null, Map.of(
                "floorOwner", "PLAYER", "reason", "AUTHORITATIVE_TEXT_INGRESS"));
        emit(OrbisEventType.STT_COMPLETED, turn, null, Map.ofEntries(
                Map.entry("requestedEngine", "BYPASSED"),
                Map.entry("actualEngine", "BYPASSED"),
                Map.entry("fallback", "false"),
                Map.entry("transcript", compact(utterance.transcript(), 4_000)),
                Map.entry("source", "AUTHORITATIVE_TEXT"),
                Map.entry("sttBypassed", "true"), Map.entry("wallMs", "0"),
                Map.entry("decodeMs", "0"), Map.entry("inferenceMs", "0"),
                Map.entry("language", "unknown")));
        emit(OrbisEventType.AUTHORITATIVE_TRANSCRIPT_ACCEPTED, turn, null, Map.of(
                "transcript", compact(utterance.transcript(), 4_000),
                "source", utterance.ingressSource().name(),
                "sttBypassed", "true"));
        resolveAudience(turn, utterance);
    }

    private void resolveAudience(OrbisTurn turn, TranscribedPlayerUtterance utterance) {
        emit(OrbisEventType.AUDIENCE_STARTED, turn, null, Map.of());
        CompletableFuture<PlayerUtteranceAudienceService.Resolution> resolved;
        try {
            resolved = audience.resolve(utterance);
        } catch (RuntimeException failure) {
            audienceCompleted(new AudienceCallback(turn.turnId(), turn.epoch(), utterance,
                    null, failure));
            return;
        }
        resolved.whenComplete((value, failure) -> enqueue(() -> audienceCompleted(
                new AudienceCallback(turn.turnId(), turn.epoch(), utterance,
                        value, failure))));
    }

    private void acceptOnCoordinator(CapturedVoiceFrame frame) {
        ConversationFloor existingFloor = floors.get(
                new ConversationKey(frame.playerId(), frame.worldId()));
        boolean activeConversation = existingFloor != null
                && existingFloor.responseId != null
                && existingFloor.owner != ConversationFloorOwner.NONE;
        if (!activeConversation && !eligiblePlayer.test(frame.playerId())) {
            emit(OrbisEventType.DIAGNOSTIC, null, null, null, 0,
                    Map.of("reason", "no-eligible-npc-in-hearing-range",
                            "playerId", frame.playerId().toString()));
            return;
        }
        PlayerCaptureSession capture = captures.get(frame.playerId());
        OrbisTurn turn;
        if (capture == null) {
            TurnId turnId = TurnId.create();
            long epoch = playerEpochs.merge(frame.playerId(), 1L, Long::sum);
            CancellationScope playerScope = playerCancellations.computeIfAbsent(
                    frame.playerId(), ignored -> serverCancellation.child());
            turn = new OrbisTurn(turnId, UtteranceId.create(), frame.playerId(),
                    frame.worldId(), epoch,
                    playerScope.child());
            capture = new PlayerCaptureSession(turnId, frame.playerId(), frame.worldId(),
                    frame.x(), frame.y(), frame.z(), frame.receivedAt(),
                    frame.receivedNanos(), maximumFrames);
            captures.put(frame.playerId(), capture);
            retain(turn);
            emit(OrbisEventType.TURN_CREATED, turn, null, Map.of(
                    "playerId", frame.playerId().toString(),
                    "ingress", TurnIngressSource.VOICE_CAPTURE.name()));
            emit(OrbisEventType.CAPTURE_STARTED, turn, null, Map.of(
                    "boundarySource", "HYTALE_PTT_PACKET_RUN",
                    "sequence", Short.toString(frame.sequenceNumber()),
                    "clientTimestamp", Integer.toString(frame.clientTimestamp())));
            beginStreamingStt(turn, capture);
            audience.prefetch(frame.playerId(), frame.worldId()).whenComplete((facts, failure) ->
                    enqueue(() -> emit(OrbisEventType.DIAGNOSTIC, turn, null,
                            failure == null ? mergePrefetchFacts(facts)
                                    : Map.of("stage", "context-prefetch",
                                            "status", "FAILED",
                                            "reason", root(failure)))));
        } else {
            turn = turns.get(capture.turnId());
            if (turn == null || turn.cancellation().isCancelled()) return;
        }
        if (!capture.append(frame.opus(), frame.receivedNanos())) {
            emit(OrbisEventType.CAPTURE_OVERFLOW, turn, null,
                    Map.of("maximumFrames", Integer.toString(maximumFrames)));
            cancelTurnOnCoordinator(turn, CancellationReason.CAPTURE_OVERFLOW);
            captures.remove(frame.playerId());
            return;
        }
        if (capture.streaming()) capture.queueUnsentFrames(stt);
        emit(OrbisEventType.CAPTURE_FRAME_ACCEPTED, turn, null,
                Map.of("frameCount", Integer.toString(capture.frameCount())));
        updatePlayerFloorAndBargeIn(turn, capture, frame);
        long generation = capture.boundaryGeneration();
        TurnId turnId = capture.turnId();
        boundaryTimer.schedule(() -> enqueue(() -> releaseIfCurrent(
                        frame.playerId(), turnId, generation)),
                packetRunReleaseMillis, TimeUnit.MILLISECONDS);
    }

    private void beginStreamingStt(OrbisTurn turn, PlayerCaptureSession capture) {
        if (stt == null || !stt.available() || !stt.streamingTranscriptionEnabled()) return;
        UUID sessionId = turn.sttRequestId().value();
        if (resources == null) {
            capture.beginStreamDirect(sessionId, stt);
            return;
        }
        OrbisResourceRequest request = new OrbisResourceRequest(sessionId,
                ResourceWorkload.STT, ResourcePriority.HIGH, stt, true,
                providerTimeoutMillis);
        capture.beginStream(sessionId, resources.admit(request,
                event -> enqueue(() -> resourceProgress(turn.turnId(), null,
                        turn.epoch(), event))), stt);
    }

    private static Map<String, String> mergePrefetchFacts(Map<String, String> supplied) {
        LinkedHashMap<String, String> facts = new LinkedHashMap<>();
        facts.put("stage", "context-prefetch");
        facts.put("commitAuthority", "NONE_SPECULATIVE_ONLY");
        if (supplied != null) facts.putAll(supplied);
        return Map.copyOf(facts);
    }

    private void updatePlayerFloorAndBargeIn(OrbisTurn incomingTurn,
            PlayerCaptureSession capture, CapturedVoiceFrame frame) {
        ConversationKey key = new ConversationKey(frame.playerId(), frame.worldId());
        ConversationFloor floor = floors.get(key);
        if (floor == null || floor.owner == ConversationFloorOwner.NONE) {
            floor = new ConversationFloor(ConversationFloorOwner.PLAYER);
            floor.activeTurnId = incomingTurn.turnId();
            floor.lastTransition = Instant.now();
            floors.put(key, floor);
            emit(OrbisEventType.FLOOR_GRANTED, incomingTurn, null, Map.of(
                    "floorOwner", "PLAYER", "reason", "PLAYER_CAPTURE_STARTED"));
            return;
        }
        if ((floor.owner == ConversationFloorOwner.NPC
                || floor.owner == ConversationFloorOwner.TRANSITION)
                && floor.responseId != null && floor.bargeCandidateTurnId == null
                && capture.frameCount() == 1) {
            floor.owner = ConversationFloorOwner.TRANSITION;
            floor.bargeCandidateTurnId = incomingTurn.turnId();
            floor.bargeCandidateNanos = frame.receivedNanos();
            floor.lastTransition = Instant.now();
            OrbisTurn speakingTurn = turns.get(floor.activeTurnId);
            NpcTurnBranch speaker = speakingTurn == null || floor.npcId == null ? null
                    : speakingTurn.branches().get(floor.npcId);
            emit(OrbisEventType.BARGE_IN_CANDIDATE, speakingTurn, speaker, Map.of(
                    "candidateTurnId", incomingTurn.turnId().value().toString(),
                    "confirmedFrames", "1", "requiredFrames",
                    Integer.toString(BARGE_IN_CONFIRMED_FRAMES),
                    "floorOwner", "TRANSITION"));
        }
        if (floor.owner == ConversationFloorOwner.TRANSITION
                && incomingTurn.turnId().equals(floor.bargeCandidateTurnId)
                && capture.frameCount() >= BARGE_IN_CONFIRMED_FRAMES) {
            confirmBargeIn(key, floor, incomingTurn, frame.receivedNanos());
        }
    }

    private void confirmBargeIn(ConversationKey key, ConversationFloor floor,
            OrbisTurn incomingTurn, long confirmedNanos) {
        OrbisTurn speakingTurn = turns.get(floor.activeTurnId);
        NpcTurnBranch speaker = speakingTurn == null || floor.npcId == null ? null
                : speakingTurn.branches().get(floor.npcId);
        floor.owner = ConversationFloorOwner.PLAYER;
        floor.bargeConfirmedAt = Instant.now();
        floor.lastTransition = floor.bargeConfirmedAt;
        floor.activeTurnId = incomingTurn.turnId();
        floor.responseId = null;
        floor.npcId = null;
        emit(OrbisEventType.BARGE_IN_CONFIRMED, speakingTurn, speaker, Map.of(
                "incomingTurnId", incomingTurn.turnId().value().toString(),
                "confirmedSpeechMs", Long.toString(BARGE_IN_CONFIRMED_FRAMES * 20L),
                "reason", CancellationReason.USER_BARGE_IN.name()));
        if (speaker != null && !NpcTurnBranch.terminal(speaker.state())) {
            if (speechCommitted(speaker.state())) {
                speaker.speechInterruptionPending(confirmedNanos);
                if (speechCoordinator != null) speechCoordinator.cancel(
                        speaker.responseId(), CancellationReason.USER_BARGE_IN, confirmedNanos);
            } else {
                speaker.cancellation().cancel(CancellationReason.USER_BARGE_IN);
                cancelCognitionProvider(speaker, CancellationReason.USER_BARGE_IN);
                terminalResponse(speaker.responseId().value(),
                        CancellationReason.USER_BARGE_IN, true);
            }
        }
        emit(OrbisEventType.FLOOR_RELEASED, speakingTurn, speaker, Map.of(
                "priorOwner", "NPC", "newOwner", "PLAYER",
                "reason", CancellationReason.USER_BARGE_IN.name()));
        emit(OrbisEventType.FLOOR_GRANTED, incomingTurn, null, Map.of(
                "floorOwner", "PLAYER", "reason", CancellationReason.USER_BARGE_IN.name()));
    }

    private void releaseIfCurrent(UUID playerId, TurnId turnId, long generation) {
        PlayerCaptureSession capture = captures.get(playerId);
        if (capture == null || !capture.turnId().equals(turnId)
                || capture.boundaryGeneration() != generation) return;
        captures.remove(playerId);
        OrbisTurn turn = turns.get(turnId);
        if (!current(turn, turn.epoch()) || turn.cancellation().isCancelled()) return;
        restoreFalseBargeCandidate(turn, capture);
        turn.state(OrbisTurn.State.FINALIZING_INPUT);
        long endpointNanos = System.nanoTime();
        List<byte[]> frames = capture.snapshotFrames();
        emit(OrbisEventType.CAPTURE_FINALIZED, turn, null, Map.of(
                "boundarySource", "HYTALE_PTT_PACKET_RUN_RELEASE_INFERRED",
                "frameCount", Integer.toString(frames.size()),
                "durationApproxMs", Long.toString(frames.size() * 20L),
                "releaseDelayMs", Long.toString(TimeUnit.NANOSECONDS.toMillis(
                        Math.max(0, endpointNanos - capture.lastFrameNanos())))));
        if (frames.isEmpty()) {
            cancelTurnOnCoordinator(turn, CancellationReason.EMPTY_TRANSCRIPT);
            return;
        }
        if (stt == null || !stt.available()) {
            cancelTurnOnCoordinator(turn, CancellationReason.STT_UNAVAILABLE);
            return;
        }
        turn.state(OrbisTurn.State.TRANSCRIBING);
        long sttStarted = System.nanoTime();
        emit(OrbisEventType.STT_SELECTED, turn, null, Map.of(
                "provider", stt.providerId(), "mode", stt.executionMode().name(),
                "backend", stt.backendDescription(), "exactlyOnce", "true",
                "timeoutMs", Long.toString(providerTimeoutMillis)));
        emit(OrbisEventType.STT_STARTED, turn, null,
                Map.of("frameCount", Integer.toString(frames.size()),
                        "streaming", Boolean.toString(capture.streaming()),
                        "stablePartialCharacters", Integer.toString(
                                capture.stablePartial().length())));
        final long epoch = turn.epoch();
        CompletableFuture<SpeechTranscript> future;
        try {
            if (capture.streaming()) {
                future = capture.finishStream(stt).thenCompose(transcript -> {
                    TranscriptIntegrityGuard.Assessment integrity =
                            TranscriptIntegrityGuard.assess(capture.stablePartial(),
                                    transcript == null ? "" : transcript.text());
                    enqueue(() -> emit(OrbisEventType.DIAGNOSTIC, turn, null, Map.of(
                            "stage", "stt-integrity", "suspect",
                            Boolean.toString(integrity.suspect()), "reason",
                            integrity.reason(), "stablePartialCharacters",
                            Integer.toString(capture.stablePartial().length()))));
                    if (!integrity.suspect()) return CompletableFuture.completedFuture(transcript);
                    // Exactly one bounded re-transcription uses the preserved authoritative
                    // capture. Partial and final text are never concatenated or manufactured.
                    return stt.transcribe(turn.sttRequestId().value(), frames);
                }).exceptionallyCompose(failure -> {
                    enqueue(() -> emit(OrbisEventType.DIAGNOSTIC, turn, null, Map.of(
                            "stage", "stt-stream-fallback", "reason", root(failure),
                            "action", "ONE_BOUNDED_BATCH_RETRANSCRIPTION")));
                    return stt.transcribe(turn.sttRequestId().value(), frames);
                }).whenComplete((ignored, failure) -> capture.closeStreamLease())
                        .orTimeout(providerTimeoutMillis, TimeUnit.MILLISECONDS);
            } else if (resources == null) {
                future = stt.transcribe(turn.sttRequestId().value(), frames)
                        .orTimeout(providerTimeoutMillis, TimeUnit.MILLISECONDS);
            } else {
                OrbisResourceRequest resourceRequest = new OrbisResourceRequest(
                        turn.sttRequestId().value(), ResourceWorkload.STT,
                        ResourcePriority.HIGH, stt, true, providerTimeoutMillis);
                future = resources.admit(resourceRequest,
                                event -> enqueue(() -> resourceProgress(turn.turnId(), null,
                                        turn.epoch(), event)))
                        .thenCompose(lease -> stt.transcribe(turn.sttRequestId().value(), frames)
                                .orTimeout(providerTimeoutMillis, TimeUnit.MILLISECONDS)
                                .whenComplete((ignored, failure) -> lease.close()));
            }
        } catch (RuntimeException failure) {
            sttCompleted(new SttCallback(turn.turnId(), epoch, capture, endpointNanos,
                    sttStarted, System.nanoTime(), null, failure));
            return;
        }
        future.whenComplete((transcript, failure) -> enqueue(() -> sttCompleted(
                new SttCallback(turn.turnId(), epoch, capture, endpointNanos,
                        sttStarted, System.nanoTime(), transcript, failure))));
    }

    private void restoreFalseBargeCandidate(OrbisTurn incomingTurn,
            PlayerCaptureSession capture) {
        if (capture.frameCount() >= BARGE_IN_CONFIRMED_FRAMES) return;
        ConversationKey key = new ConversationKey(incomingTurn.playerId(),
                incomingTurn.worldId());
        ConversationFloor floor = floors.get(key);
        if (floor == null || !incomingTurn.turnId().equals(floor.bargeCandidateTurnId)
                || floor.responseId == null) return;
        OrbisTurn speakingTurn = turns.get(floor.activeTurnId);
        NpcTurnBranch speaker = speakingTurn == null || floor.npcId == null ? null
                : speakingTurn.branches().get(floor.npcId);
        floor.owner = speaker != null && speechCommitted(speaker.state())
                ? ConversationFloorOwner.NPC : ConversationFloorOwner.TRANSITION;
        floor.bargeCandidateTurnId = null;
        floor.bargeCandidateNanos = 0;
        floor.lastTransition = Instant.now();
        emit(OrbisEventType.FLOOR_GRANTED, speakingTurn, speaker, Map.of(
                "floorOwner", floor.owner.name(),
                "reason", "FALSE_BARGE_IN_SUBMINIMUM_AUDIO",
                "observedFrames", Integer.toString(capture.frameCount())));
    }

    private void sttCompleted(SttCallback callback) {
        OrbisTurn turn = turns.get(callback.turnId());
        if (!current(turn, callback.epoch())) {
            stale(callback.turnId(), callback.epoch(), "stt");
            return;
        }
        if (callback.failure() != null) {
            emit(OrbisEventType.STT_FAILED, turn, null,
                    Map.of("reason", root(callback.failure())));
            cancelTurnOnCoordinator(turn, rootCause(callback.failure())
                    instanceof java.util.concurrent.TimeoutException
                            ? CancellationReason.PROVIDER_TIMEOUT
                            : CancellationReason.STT_FAILED);
            return;
        }
        SpeechTranscript transcript = callback.transcript();
        String text = transcript == null ? "" : transcript.text().replaceAll("\\s+", " ").strip();
        if (text.isBlank()) {
            emit(OrbisEventType.STT_FAILED, turn, null,
                    Map.of("reason", "empty-transcript"));
            cancelTurnOnCoordinator(turn, CancellationReason.EMPTY_TRANSCRIPT);
            return;
        }
        turn.rawTranscript(text);
        SttSemanticCorrector.Correction correction = audience.correctTranscript(
                turn.playerId(), text);
        String authoritativeText = correction.correctedTranscript();
        turn.transcript(authoritativeText);
        recentVoiceTranscripts.put(turn.playerId(), new RecentVoiceTranscript(
                turn.turnId(), turn.utteranceId(), normalizeTranscript(authoritativeText), Instant.now()));
        emit(OrbisEventType.STT_COMPLETED, turn, null, Map.ofEntries(
                Map.entry("transcript", compact(text, 4_000)),
                Map.entry("rawTranscript", compact(text, 4_000)),
                Map.entry("correctedTranscript", compact(authoritativeText, 4_000)),
                Map.entry("semanticCorrectionApplied", Boolean.toString(correction.applied())),
                Map.entry("semanticCorrectionConfidence", Double.toString(correction.confidence())),
                Map.entry("semanticCorrectionReason", correction.reason()),
                Map.entry("source", "STT"),
                Map.entry("wallMs", Long.toString(elapsed(callback.sttStartedNanos(),
                        callback.completedNanos()))),
                Map.entry("decodeMs", Long.toString(transcript.decodeMillis())),
                Map.entry("inferenceMs", Long.toString(transcript.whisperMillis())),
                Map.entry("language", transcript.language()),
                Map.entry("requestedEngine", transcript.requestedEngine()),
                Map.entry("actualEngine", transcript.actualEngine()),
                Map.entry("fallback", Boolean.toString(transcript.fallback())),
                Map.entry("fallbackReason", transcript.fallbackReason()),
                Map.entry("device", transcript.device()),
                Map.entry("computeMode", transcript.computeMode()),
                Map.entry("workerPid", Long.toString(transcript.workerPid()))));
        TranscribedPlayerUtterance utterance = new TranscribedPlayerUtterance(
                turn.utteranceId().value(), turn.playerId(), authoritativeText,
                callback.capture().worldId(), callback.capture().x(),
                callback.capture().y(), callback.capture().z(),
                callback.capture().startedAt(), callback.capture().firstFrameNanos(),
                callback.endpointNanos(), callback.sttStartedNanos(),
                callback.completedNanos(), TurnIngressSource.VOICE_CAPTURE,
                turn.originalPhysicalUtteranceId());
        turn.state(OrbisTurn.State.RESOLVING_AUDIENCE);
        emit(OrbisEventType.AUTHORITATIVE_TRANSCRIPT_ACCEPTED, turn, null, Map.ofEntries(
                Map.entry("transcript", compact(authoritativeText, 4_000)),
                Map.entry("rawTranscript", compact(text, 4_000)),
                Map.entry("correctedTranscript", compact(authoritativeText, 4_000)),
                Map.entry("semanticCorrectionApplied", Boolean.toString(correction.applied())),
                Map.entry("semanticCorrectionConfidence", Double.toString(correction.confidence())),
                Map.entry("semanticCorrectionReason", correction.reason()),
                Map.entry("source", "STT"), Map.entry("sttBypassed", "false"),
                Map.entry("requestedEngine", transcript.requestedEngine()),
                Map.entry("actualEngine", transcript.actualEngine()),
                Map.entry("fallback", Boolean.toString(transcript.fallback())),
                Map.entry("physicalUtteranceId",
                        turn.originalPhysicalUtteranceId().toString())));
        emit(OrbisEventType.AUDIENCE_STARTED, turn, null, Map.of());
        CompletableFuture<PlayerUtteranceAudienceService.Resolution> resolved;
        try {
            resolved = audience.resolve(utterance);
        } catch (RuntimeException failure) {
            audienceCompleted(new AudienceCallback(turn.turnId(), turn.epoch(), utterance,
                    null, failure));
            return;
        }
        resolved.whenComplete((value, failure) -> enqueue(() -> audienceCompleted(
                new AudienceCallback(turn.turnId(), turn.epoch(), utterance,
                        value, failure))));
    }

    private void audienceCompleted(AudienceCallback callback) {
        OrbisTurn turn = turns.get(callback.turnId());
        if (!current(turn, callback.epoch())) {
            stale(callback.turnId(), callback.epoch(), "audience");
            return;
        }
        if (callback.failure() != null || callback.resolution() == null) {
            emit(OrbisEventType.TURN_FAILED, turn, null,
                    Map.of("stage", "audience", "reason", root(callback.failure())));
            cancelTurnOnCoordinator(turn, CancellationReason.AUDIENCE_FAILED);
            return;
        }
        PlayerUtteranceAudienceService.Resolution resolution = callback.resolution();
        turn.audienceSnapshot(AudienceSnapshot.from(resolution));
        List<EligibleNpcListener> listeners = resolution.event().eligibleNpcListeners();
        Set<UUID> owners = resolution.responseOwners().stream()
                .map(EligibleNpcListener::npcId).collect(java.util.stream.Collectors.toSet());
        emit(OrbisEventType.AUDIENCE_RESOLVED, turn, null, Map.of(
                "heard", Integer.toString(listeners.size()),
                "owners", Integer.toString(owners.size()),
                "intent", resolution.event().speechIntent().name(),
                "audienceMs", Long.toString(
                        resolution.event().audienceResolutionMillis())));
        if (listeners.isEmpty()) {
            cancelTurnOnCoordinator(turn, CancellationReason.NO_AUDIENCE);
            return;
        }
        Map<UUID, UUID> responseIds = new LinkedHashMap<>();
        for (EligibleNpcListener listener : listeners) {
            boolean owner = owners.contains(listener.npcId());
            if (owner) supersedeNpcBranch(listener.npcId());
            PinnedLlmProvider pinned = null;
            String provider = "none";
            String model = "none";
            try {
                pinned = owner ? pinnedLlmProvider.get() : null;
                if (pinned != null) {
                    provider = pinned.provider();
                    model = pinned.model();
                }
                if (owner && pinned == null) {
                    throw new IllegalStateException("No pinned LLM provider available");
                }
            } catch (RuntimeException failure) {
                emit(OrbisEventType.TURN_FAILED, turn, null, Map.of(
                        "stage", "provider-pin", "npcId", listener.npcId().toString(),
                        "reason", root(failure)));
                cancelTurnOnCoordinator(turn, CancellationReason.PROVIDER_FAILURE);
                return;
            }
            long branchEpoch = npcEpochs.merge(listener.npcId(), 1L, Long::sum);
            NpcTurnBranch branch = new NpcTurnBranch(BranchId.create(), ResponseId.create(),
                    ProviderRequestId.create(), listener.npcId(), listener.npcName(), branchEpoch,
                    provider, model,
                    turn.cancellation().child(), owner);
            turn.addBranch(branch);
            if (owner) {
                responseBranches.put(branch.responseId().value(),
                        new BranchLocator(turn.turnId(), branch.npcId(), branch.epoch()));
                if (pinned != null) branchProviders.put(branch.responseId().value(), pinned);
            }
            Map<String, String> facts = Map.of(
                    "npcId", listener.npcId().toString(),
                    "npc", listener.npcName(),
                    "distanceBand", listener.distanceBand(),
                    "rangeClass", listener.rangeClass().name(),
                    "directAddress", Boolean.toString(listener.directAddress()));
            emit(OrbisEventType.LISTENER_HEARD, turn, branch, facts);
            emit(OrbisEventType.LISTENER_DELIVERED, turn, branch, facts);
            emit(OrbisEventType.RESPONSE_CANDIDATE, turn, branch, facts);
            emit(OrbisEventType.BRANCH_CREATED, turn, branch, Map.of(
                    "npcId", listener.npcId().toString(),
                    "npc", listener.npcName(),
                    "provider", branch.provider(), "model", branch.model(),
                    "responseOwner", Boolean.toString(owner)));
            if (owner) {
                responseIds.put(listener.npcId(), branch.responseId().value());
                emit(OrbisEventType.RESPONSE_OWNER_SELECTED, turn, branch, facts);
                BranchCognitionSnapshot snapshot = cognitionSnapshot(
                        turn, branch, callback.utterance(), resolution, listener, pinned);
                branch.cognitionSnapshot(snapshot);
                cognitionSnapshots.put(branch.responseId().value(), snapshot);
            } else {
                emit(OrbisEventType.RESPONSE_SUPPRESSED, turn, branch, Map.of(
                        "reason", resolution.suppressionReasons().getOrDefault(
                                listener.npcId(), "LISTENER_NOT_SELECTED_TO_SPEAK")));
            }
        }
        if (responseIds.isEmpty()) {
            cancelTurnOnCoordinator(turn, CancellationReason.NO_AUDIENCE);
            return;
        }
        turn.state(OrbisTurn.State.DISPATCHED);
        for (NpcTurnBranch branch : turn.branches().values()) {
            if (!branch.responseOwner()) continue;
            emit(OrbisEventType.BRANCH_DISPATCHED, turn, branch, Map.of(
                    "npcId", branch.npcId().toString(), "npc", branch.npcName(),
                    "provider", branch.provider(), "model", branch.model()));
        }
        try {
            for (NpcTurnBranch branch : turn.branches().values()) {
                if (branch.responseOwner()) startCognition(turn, branch);
            }
            turn.state(OrbisTurn.State.ACTIVE);
        } catch (RuntimeException failure) {
            emit(OrbisEventType.TURN_FAILED, turn, null,
                    Map.of("stage", "dispatch", "reason", root(failure)));
            cancelTurnOnCoordinator(turn, CancellationReason.PROVIDER_FAILURE);
        }
    }

    private BranchCognitionSnapshot cognitionSnapshot(OrbisTurn turn,
            NpcTurnBranch branch, TranscribedPlayerUtterance utterance,
            PlayerUtteranceAudienceService.Resolution resolution,
            EligibleNpcListener listener, PinnedLlmProvider pinned) {
        var event = resolution.event();
        Map<String, String> audienceState = Map.of(
                "eligibleListeners", Integer.toString(event.eligibleNpcListeners().size()),
                "responseOwners", Integer.toString(resolution.responseOwners().size()),
                "responseOwner", Boolean.toString(branch.responseOwner()),
                "directAddress", Boolean.toString(listener.directAddress()),
                "speechIntent", event.speechIntent().name());
        com.inigmasgames.persistentnpcs.voice.SpeechProjection projection =
                listener.rangeClass()
                        == com.inigmasgames.persistentnpcs.voice.UtteranceRangeClass.ORDINARY
                                ? com.inigmasgames.persistentnpcs.voice.SpeechProjection.NORMAL
                                : com.inigmasgames.persistentnpcs.voice.SpeechProjection.CALL;
        String worldRef = "world:" + utterance.worldId() + "/utterance:"
                + event.utteranceId() + "/audience-captured:" + event.timestamp();
        DeferredTopicStore.Result deferred = deferredTopics.context(
                turn.playerId(), branch.npcId(), Instant.now());
        for (DeferredTopic value : deferred.expired()) {
            emit(OrbisEventType.DEFERRED_TOPIC_EXPIRED, turn, branch, Map.of(
                    "sourceTurnId", value.sourceTurnId().value().toString(),
                    "sourceResponseId", value.sourceResponseId().value().toString(),
                    "reason", "TTL_OR_TURN_LIMIT"));
        }
        for (DeferredTopic value : deferred.consumed()) {
            emit(OrbisEventType.DEFERRED_TOPIC_CONSUMED, turn, branch, Map.of(
                    "sourceTurnId", value.sourceTurnId().value().toString(),
                    "sourceResponseId", value.sourceResponseId().value().toString(),
                    "mode", "COGNITION_CONTEXT_PRESENTED"));
        }
        return new BranchCognitionSnapshot(turn.turnId(), branch.branchId(),
                branch.responseId(), branch.providerRequestId(), event.utteranceId(), branch.npcId(),
                branch.npcName(), turn.playerId(), utterance.worldId(), turn.transcript(),
                listener.directAddress(), branch.responseOwner(), listener.distanceBand(),
                listener.directionFromPlayer(), listener.rangeClass(), projection,
                event.speechIntent(), audienceState, worldRef,
                event.endpointMillis(), event.sttMillis(), event.audienceResolutionMillis(),
                pinned.provider(), pinned.model(), pinned.endpoint(), deferred.summary(),
                branch.epoch(),
                branch.cancellation(), Instant.now());
    }

    private void startCognition(OrbisTurn turn, NpcTurnBranch branch) {
        BranchCognitionSnapshot snapshot = cognitionSnapshots.get(branch.responseId().value());
        PinnedLlmProvider pinned = branchProviders.get(branch.responseId().value());
        if (snapshot == null || pinned == null) {
            failCognition(turn, branch, CancellationReason.PROVIDER_FAILURE,
                    new IllegalStateException("Missing Orbis cognition snapshot/provider pin"));
            return;
        }
        branch.state(NpcTurnBranch.State.COGNITION_PENDING);
        grantNpcThinkingFloor(turn, branch);
        emit(OrbisEventType.COGNITION_PENDING, turn, branch, Map.of(
                "npcId", branch.npcId().toString(), "npc", branch.npcName(),
                "provider", branch.provider(), "model", branch.model(),
                "worldSnapshotRef", snapshot.authoritativeWorldSnapshotRef()));
        CompletableFuture<ConversationOutcome> future;
        try {
            java.util.function.Supplier<CompletableFuture<ConversationOutcome>> execution = () ->
                    cognitionGateway.begin(snapshot, pinned,
                            (stage, facts) -> enqueue(() -> cognitionProgress(
                                    turn.turnId(), branch.npcId(), branch.epoch(), stage, facts)))
                            .orTimeout(providerTimeoutMillis, TimeUnit.MILLISECONDS);
            if (resources == null) {
                future = execution.get();
            } else {
                OrbisResourceRequest resourceRequest = new OrbisResourceRequest(
                        branch.providerRequestId().value(), ResourceWorkload.LLM,
                        ResourcePriority.HIGH, pinned.delegate(), true,
                        providerTimeoutMillis);
                future = resources.admit(resourceRequest,
                                event -> enqueue(() -> resourceProgress(turn.turnId(),
                                        branch.npcId(), branch.epoch(), event)))
                        .thenCompose(lease -> execution.get().whenComplete(
                                (ignored, failure) -> lease.close()));
            }
        } catch (RuntimeException failure) {
            failCognition(turn, branch, CancellationReason.PROVIDER_FAILURE, failure);
            return;
        }
        future.whenComplete((outcome, failure) -> enqueue(() -> cognitionCompleted(
                turn.turnId(), branch.npcId(), branch.epoch(), outcome, failure)));
    }

    private void cognitionProgress(TurnId turnId, UUID npcId, long branchEpoch,
            ConversationLifecycleObserver.Stage stage, Map<String, String> suppliedFacts) {
        OrbisTurn turn = turns.get(turnId);
        NpcTurnBranch branch = turn == null ? null : turn.branches().get(npcId);
        if (!currentBranch(branch, branchEpoch)) {
            stale(turnId, branchEpoch, "cognition-" + stage.name().toLowerCase());
            return;
        }
        if (stage == ConversationLifecycleObserver.Stage.PHRASE_VALIDATED) {
            commitEarlyPhrase(turn, branch, suppliedFacts);
            return;
        }
        OrbisEventType contractEvent = switch (stage) {
            case TURN_PLAN_COMPILED -> OrbisEventType.TURN_PLAN_COMPILED;
            case CONTRACT_BUDGET_PLANNED -> OrbisEventType.CONTRACT_BUDGET_PLANNED;
            case CONTRACT_VALID -> OrbisEventType.CONTRACT_VALID;
            case CONTRACT_INVALID -> OrbisEventType.CONTRACT_INVALID;
            case TRUNCATED_OUTPUT -> OrbisEventType.TRUNCATED_OUTPUT;
            case RECOVERY_ATTEMPTED -> OrbisEventType.RECOVERY_ATTEMPTED;
            case RECOVERY_SUCCEEDED -> OrbisEventType.RECOVERY_SUCCEEDED;
            case RECOVERY_EXHAUSTED -> OrbisEventType.RECOVERY_EXHAUSTED;
            default -> null;
        };
        if (contractEvent != null) {
            LinkedHashMap<String, String> facts = new LinkedHashMap<>();
            facts.put("npcId", branch.npcId().toString());
            facts.put("npc", branch.npcName());
            if (suppliedFacts != null) facts.putAll(suppliedFacts);
            emit(contractEvent, turn, branch, Map.copyOf(facts));
            return;
        }
        NpcTurnBranch.State branchState = switch (stage) {
            case CONTEXT_BUILDING -> NpcTurnBranch.State.CONTEXT_BUILDING;
            case LLM_QUEUED, LLM_DISPATCHED -> NpcTurnBranch.State.LLM_QUEUED;
            case LLM_STREAMING -> NpcTurnBranch.State.LLM_STREAMING;
            case DECISION_VALIDATING -> NpcTurnBranch.State.DECISION_VALIDATING;
            case PHRASE_VALIDATED, TURN_PLAN_COMPILED, CONTRACT_BUDGET_PLANNED,
                    CONTRACT_VALID, CONTRACT_INVALID, TRUNCATED_OUTPUT,
                    RECOVERY_ATTEMPTED, RECOVERY_SUCCEEDED, RECOVERY_EXHAUSTED ->
                        branch.state();
        };
        OrbisEventType type = switch (stage) {
            case CONTEXT_BUILDING -> OrbisEventType.CONTEXT_BUILDING;
            case LLM_QUEUED -> OrbisEventType.LLM_QUEUED;
            case LLM_DISPATCHED -> OrbisEventType.LLM_DISPATCHED;
            case LLM_STREAMING -> OrbisEventType.LLM_STREAMING;
            case DECISION_VALIDATING -> OrbisEventType.DECISION_VALIDATING;
            case PHRASE_VALIDATED -> OrbisEventType.PHRASE_VALIDATED;
            case TURN_PLAN_COMPILED -> OrbisEventType.TURN_PLAN_COMPILED;
            case CONTRACT_BUDGET_PLANNED -> OrbisEventType.CONTRACT_BUDGET_PLANNED;
            case CONTRACT_VALID -> OrbisEventType.CONTRACT_VALID;
            case CONTRACT_INVALID -> OrbisEventType.CONTRACT_INVALID;
            case TRUNCATED_OUTPUT -> OrbisEventType.TRUNCATED_OUTPUT;
            case RECOVERY_ATTEMPTED -> OrbisEventType.RECOVERY_ATTEMPTED;
            case RECOVERY_SUCCEEDED -> OrbisEventType.RECOVERY_SUCCEEDED;
            case RECOVERY_EXHAUSTED -> OrbisEventType.RECOVERY_EXHAUSTED;
        };
        branch.state(branchState);
        LinkedHashMap<String, String> facts = new LinkedHashMap<>();
        facts.put("npcId", branch.npcId().toString());
        facts.put("npc", branch.npcName());
        facts.put("provider", branch.provider());
        facts.put("model", branch.model());
        if (suppliedFacts != null) facts.putAll(suppliedFacts);
        emit(type, turn, branch, Map.copyOf(facts));
    }

    private void commitEarlyPhrase(OrbisTurn turn, NpcTurnBranch branch,
            Map<String, String> suppliedFacts) {
        if (speechCoordinator == null || branch.cognitionSnapshot() == null) return;
        int index;
        try {
            index = Integer.parseInt(suppliedFacts.getOrDefault("chunkIndex", "-1"));
        } catch (NumberFormatException invalid) {
            index = -1;
        }
        String text = suppliedFacts.getOrDefault("canonicalPhrase", "").strip();
        if (index != branch.speechChunks().size() || text.isBlank()) {
            emit(OrbisEventType.CALLBACK_REJECTED_STALE, turn, branch, Map.of(
                    "stage", "early-phrase-order", "chunkIndex", Integer.toString(index)));
            return;
        }
        try {
            text = SpokenTextSafetyValidator.requireSafe(text);
            com.inigmasgames.persistentnpcs.voice.VocalEmotion emotion;
            try {
                emotion = com.inigmasgames.persistentnpcs.voice.VocalEmotion.valueOf(
                        suppliedFacts.getOrDefault("emotion", "CALM"));
            } catch (IllegalArgumentException invalid) {
                emotion = com.inigmasgames.persistentnpcs.voice.VocalEmotion.CALM;
            }
            com.inigmasgames.persistentnpcs.voice.VocalState state =
                    com.inigmasgames.persistentnpcs.voice.VocalState.forEmotion(emotion);
            String eventName = suppliedFacts.getOrDefault("paralinguisticEvent", "");
            if (!eventName.isBlank()) try {
                state = state.withEvent(com.inigmasgames.persistentnpcs.voice
                        .ParalinguisticEvent.valueOf(eventName));
            } catch (IllegalArgumentException ignored) { }
            CanonicalSpeechChunk chunk = new CanonicalSpeechChunk(SpeechChunkId.create(),
                    index, text, state);
            branch.appendSpeechChunk(chunk);
            LinkedHashMap<String, String> facts = new LinkedHashMap<>(suppliedFacts);
            facts.put("speechChunkId", chunk.id().value().toString());
            facts.put("canonicalCommit", "IMMUTABLE_FAST_PHRASE");
            CanonicalSpeechLedger.Segment segment = branch.speechLedger().segments().get(index);
            facts.put("charStart", Integer.toString(segment.charStartInclusive()));
            facts.put("charEnd", Integer.toString(segment.charEndExclusive()));
            facts.put("ledgerState", segment.state().name());
            emit(OrbisEventType.CANONICAL_SPEECH_SEGMENT_APPENDED, turn, branch,
                    Map.copyOf(facts));
            emit(OrbisEventType.CANONICAL_SPEECH_SEGMENT_COMMITTED, turn, branch,
                    Map.copyOf(facts));
            emit(OrbisEventType.PHRASE_VALIDATED, turn, branch, Map.copyOf(facts));
            cognitionGateway.commitPhrase(branch.cognitionSnapshot(), chunk)
                    .exceptionally(failure -> {
                        enqueue(() -> emit(OrbisEventType.DIAGNOSTIC, turn, branch, Map.of(
                                "stage", "early-phrase-display", "reason", root(failure))));
                        return null;
                    });
            if (index == 0) {
                // Only the first chunk creates the response-scoped admission request. Later
                // chunks retain their absolute response index and use append(); wrapping an
                // index-1 chunk in a new one-element request violates that request's local
                // zero-based ordering contract.
                OrbisSpeechRequest request = new OrbisSpeechRequest(turn.turnId(),
                        branch.branchId(), branch.responseId(), branch.npcId(),
                        branch.npcName(), branch.epoch(), turn.playerId(),
                        branch.cognitionSnapshot().projection(), List.of(chunk), Instant.now());
                speechCoordinator.submitStreaming(request,
                        event -> enqueue(() -> speechProgress(event)));
            } else {
                speechCoordinator.append(branch.responseId(), List.of(chunk));
            }
        } catch (RuntimeException failure) {
            emit(OrbisEventType.DIAGNOSTIC, turn, branch, Map.of(
                    "stage", "early-phrase-commit", "reason", root(failure)));
        }
    }

    private void cognitionCompleted(TurnId turnId, UUID npcId, long branchEpoch,
            ConversationOutcome outcome, Throwable failure) {
        OrbisTurn turn = turns.get(turnId);
        NpcTurnBranch branch = turn == null ? null : turn.branches().get(npcId);
        if (!currentBranch(branch, branchEpoch)) {
            stale(turnId, branchEpoch, "cognition-completion");
            return;
        }
        if (failure != null) {
            Throwable cause = rootCause(failure);
            failCognition(turn, branch,
                    cause instanceof ResourceStarvedException
                            ? CancellationReason.RESOURCE_STARVED
                            : cause instanceof java.util.concurrent.TimeoutException
                                    ? CancellationReason.PROVIDER_TIMEOUT
                            : cause instanceof java.util.concurrent.CancellationException
                                    ? CancellationReason.SUPERSEDED
                                    : CancellationReason.PROVIDER_FAILURE,
                    cause);
            return;
        }
        String rejection = validateDecision(branch, outcome);
        if (!rejection.isBlank()) {
            emit(OrbisEventType.DECISION_REJECTED, turn, branch, Map.of(
                    "npcId", branch.npcId().toString(), "npc", branch.npcName(),
                    "reason", rejection));
            failCognition(turn, branch, CancellationReason.INVALID_DECISION,
                    new IllegalStateException(rejection));
            return;
        }
        branch.decision(outcome.decision(), "VALID");
        branch.outcome(outcome);
        branch.state(NpcTurnBranch.State.DECISION_COMMITTED);
        emit(OrbisEventType.DECISION_COMMITTED, turn, branch, decisionFacts(branch, outcome));
        List<CanonicalSpeechChunk> speechChunks;
        int earlyChunkCount = branch.speechChunks().size();
        try {
            speechChunks = canonicalSpeechChunks(outcome, branch.speechChunks());
        } catch (RuntimeException invalidCanonicalSpeech) {
            failCognition(turn, branch, CancellationReason.INVALID_DECISION,
                    invalidCanonicalSpeech);
            return;
        }
        branch.finalizeSpeechChunks(speechChunks);
        for (int index = earlyChunkCount; index < branch.speechLedger().segments().size(); index++) {
            CanonicalSpeechLedger.Segment segment = branch.speechLedger().segments().get(index);
            Map<String, String> facts = speechSegmentFacts(segment);
            emit(OrbisEventType.CANONICAL_SPEECH_SEGMENT_APPENDED, turn, branch, facts);
            emit(OrbisEventType.CANONICAL_SPEECH_SEGMENT_COMMITTED, turn, branch, facts);
        }
        CompletableFuture<Void> delivery;
        try {
            delivery = cognitionGateway.finalizeCommit(branch.cognitionSnapshot(), outcome,
                    speechChunks, earlyChunkCount);
        } catch (RuntimeException diagnosticOrAdapterFailure) {
            delivery = CompletableFuture.failedFuture(diagnosticOrAdapterFailure);
        }
        delivery.whenComplete((ignored, deliveryFailure) -> enqueue(() -> {
            if (deliveryFailure != null) {
                // A downstream display/TTS adapter failure cannot revoke a valid decision.
                emit(OrbisEventType.DIAGNOSTIC, turn, branch, Map.of(
                        "npcId", branch.npcId().toString(),
                        "stage", "post-decision-adapter",
                        "reason", root(deliveryFailure)));
            }
            if (speechCoordinator == null || speechChunks.isEmpty()) {
                terminalResponse(branch.responseId().value(), null, false);
            }
        }));
        if (speechCoordinator != null && !speechChunks.isEmpty() && earlyChunkCount == 0) {
            OrbisSpeechRequest request = new OrbisSpeechRequest(turn.turnId(),
                    branch.branchId(), branch.responseId(), branch.npcId(), branch.npcName(),
                    branch.epoch(), turn.playerId(), branch.cognitionSnapshot().projection(),
                    speechChunks, Instant.now());
            speechCoordinator.submit(request, event -> enqueue(() -> speechProgress(event)));
        } else if (speechCoordinator != null && earlyChunkCount > 0) {
            if (speechChunks.size() > earlyChunkCount) {
                speechCoordinator.append(branch.responseId(),
                        speechChunks.subList(earlyChunkCount, speechChunks.size()));
            }
            speechCoordinator.seal(branch.responseId());
        }
    }

    private static List<CanonicalSpeechChunk> canonicalSpeechChunks(
            ConversationOutcome outcome) {
        return canonicalSpeechChunks(outcome, List.of());
    }

    private static List<CanonicalSpeechChunk> canonicalSpeechChunks(
            ConversationOutcome outcome, List<CanonicalSpeechChunk> immutablePrefix) {
        java.util.ArrayList<CanonicalSpeechChunk> chunks = new java.util.ArrayList<>();
        List<CanonicalSpeechChunk> prefix = immutablePrefix == null
                ? List.of() : List.copyOf(immutablePrefix);
        SpeechPhraseChunker chunker = SpeechPhraseChunker.exact((index, phrase, state) -> {
            String exact = SpokenTextSafetyValidator.requireSafe(phrase);
            if (index < prefix.size()) {
                CanonicalSpeechChunk committed = prefix.get(index);
                if (!committed.text().equals(exact)) throw new IllegalStateException(
                        "Final NpcDecision diverged from immutable early phrase");
                chunks.add(committed);
            } else {
                chunks.add(new CanonicalSpeechChunk(SpeechChunkId.create(), index, exact, state));
            }
        });
        chunker.complete(outcome.dialogue(), outcome.vocalState());
        String joined = chunks.stream().map(CanonicalSpeechChunk::text)
                .collect(java.util.stream.Collectors.joining(" "));
        if (!joined.equals(outcome.dialogue())) {
            throw new IllegalStateException(
                    "Canonical speech chunks differ from committed NpcDecision text");
        }
        return List.copyOf(chunks);
    }

    private void speechProgress(OrbisSpeechEvent event) {
        BranchLocator locator = event == null ? null
                : responseBranches.get(event.responseId().value());
        OrbisTurn turn = locator == null ? null : turns.get(locator.turnId());
        NpcTurnBranch branch = turn == null ? null : turn.branches().get(locator.npcId());
        if (event == null || locator == null || branch == null
                || !event.turnId().equals(locator.turnId())
                || !event.branchId().equals(branch.branchId())
                || !event.npcStableId().equals(branch.npcId())
                || event.branchEpoch() != locator.branchEpoch()
                || event.branchEpoch() != branch.epoch()) {
            if (event != null) stale(event.turnId(), event.branchEpoch(),
                    "speech-" + event.type().name().toLowerCase());
            return;
        }
        if (event.type() == OrbisSpeechEvent.Type.RESOURCE_SCHEDULE_EVENT) {
            String type = event.facts().get("resourceEventType");
            try {
                OrbisEventType resourceType = OrbisEventType.valueOf(type);
                emit(resourceType, turn, branch, event.facts());
            } catch (RuntimeException invalidType) {
                emit(OrbisEventType.DIAGNOSTIC, turn, branch, Map.of(
                        "stage", "resource-event", "reason", root(invalidType)));
            }
            return;
        }
        boolean observerOnly = event.type() == OrbisSpeechEvent.Type.TTS_CANCELLED
                || event.type() == OrbisSpeechEvent.Type.TTS_RESULT_DISCARDED_STALE
                || event.type() == OrbisSpeechEvent.Type.PLAYBACK_INTERRUPTED
                || event.type() == OrbisSpeechEvent.Type.CALLBACK_REJECTED_STALE;
        if ((branch.cancellation().isCancelled() || NpcTurnBranch.terminal(branch.state()))
                && !observerOnly) {
            stale(event.turnId(), event.branchEpoch(),
                    "speech-" + event.type().name().toLowerCase());
            return;
        }
        if (branch.speechInterruptionPending()
                && event.type() != OrbisSpeechEvent.Type.SPEECH_INTERRUPTED
                && !observerOnly) {
            stale(event.turnId(), event.branchEpoch(),
                    "speech-after-barge-in-" + event.type().name().toLowerCase());
            return;
        }
        if (event.type() == OrbisSpeechEvent.Type.CHUNK_PLAYBACK_COMPLETE) {
            branch.delivered(event.speechChunkId());
            branch.speechLedger().segments().stream()
                    .filter(segment -> segment.chunk().id().equals(event.speechChunkId()))
                    .findFirst().ifPresent(segment -> emit(
                            OrbisEventType.CANONICAL_SPEECH_SEGMENT_DELIVERED,
                            turn, branch, speechSegmentFacts(segment)));
        } else if (event.type() == OrbisSpeechEvent.Type.SPEECH_INTERRUPTED) {
            branch.partial(parseSpeechChunkId(event.facts().get("partialChunkId")));
            for (SpeechChunkId id : parseSpeechChunkIds(
                    event.facts().get("deliveredChunkIds"))) branch.delivered(id);
        }
        NpcTurnBranch.State next = switch (event.type()) {
            case SPEECH_QUEUED -> NpcTurnBranch.State.SPEECH_QUEUED;
            case TTS_SYNTHESIZING -> NpcTurnBranch.State.TTS_SYNTHESIZING;
            case AUDIO_READY -> NpcTurnBranch.State.AUDIO_READY;
            case PLAYBACK_QUEUED -> NpcTurnBranch.State.PLAYBACK_QUEUED;
            case SPEAKING -> NpcTurnBranch.State.SPEAKING;
            case CHUNK_PLAYBACK_COMPLETE -> branch.state();
            case SPEECH_COMPLETE -> NpcTurnBranch.State.SPEECH_COMPLETE;
            case SPEECH_CANCELLED -> NpcTurnBranch.State.SPEECH_CANCELLED;
            case SPEECH_INTERRUPTED -> NpcTurnBranch.State.INTERRUPTED;
            case TTS_FAILED -> NpcTurnBranch.State.TTS_FAILED;
            case PLAYBACK_FAILED -> NpcTurnBranch.State.PLAYBACK_FAILED;
            case TIMED_OUT -> NpcTurnBranch.State.TIMED_OUT;
            case TTS_CANCELLED, TTS_RESULT_DISCARDED_STALE, PLAYBACK_INTERRUPTED,
                    RESOURCE_SCHEDULE_EVENT, CALLBACK_REJECTED_STALE -> branch.state();
        };
        branch.state(next);
        Map<String, String> supplied = event.facts();
        branch.speechProgress(event.speechChunkId(), event.ttsRequestId(),
                event.playbackId(), supplied.get("provider"), supplied.get("voicePreset"),
                supplied.get("reference"), supplied.toString());
        LinkedHashMap<String, String> facts = new LinkedHashMap<>(supplied);
        facts.put("speechState", next.name());
        facts.put("canonicalChunkCount", Integer.toString(branch.speechChunks().size()));
        if (event.speechChunkId() != null) facts.put("speechChunkId",
                event.speechChunkId().value().toString());
        if (event.ttsRequestId() != null) facts.put("ttsRequestId",
                event.ttsRequestId().value().toString());
        if (event.playbackId() != null) facts.put("playbackId",
                event.playbackId().value().toString());
        OrbisEventType type = switch (event.type()) {
            case SPEECH_QUEUED -> OrbisEventType.SPEECH_QUEUED;
            case TTS_SYNTHESIZING -> OrbisEventType.TTS_SYNTHESIZING;
            case AUDIO_READY -> OrbisEventType.AUDIO_READY;
            case PLAYBACK_QUEUED -> OrbisEventType.PLAYBACK_QUEUED;
            case SPEAKING -> OrbisEventType.SPEAKING;
            case CHUNK_PLAYBACK_COMPLETE -> OrbisEventType.CHUNK_PLAYBACK_COMPLETE;
            case SPEECH_COMPLETE -> OrbisEventType.SPEECH_COMPLETE;
            case SPEECH_CANCELLED -> OrbisEventType.SPEECH_CANCELLED;
            case SPEECH_INTERRUPTED -> OrbisEventType.SPEECH_INTERRUPTED;
            case TTS_CANCELLED -> OrbisEventType.TTS_CANCELLED;
            case TTS_RESULT_DISCARDED_STALE -> OrbisEventType.TTS_RESULT_DISCARDED_STALE;
            case PLAYBACK_INTERRUPTED -> OrbisEventType.PLAYBACK_INTERRUPTED;
            case TTS_FAILED -> OrbisEventType.TTS_FAILED;
            case PLAYBACK_FAILED -> OrbisEventType.PLAYBACK_FAILED;
            case TIMED_OUT -> OrbisEventType.SPEECH_TIMED_OUT;
            case RESOURCE_SCHEDULE_EVENT -> OrbisEventType.DIAGNOSTIC;
            case CALLBACK_REJECTED_STALE -> OrbisEventType.CALLBACK_REJECTED_STALE;
        };
        emit(type, turn, branch, Map.copyOf(facts));
        if (event.type() == OrbisSpeechEvent.Type.SPEECH_QUEUED) {
            grantNpcFloor(turn, branch);
        }
        switch (event.type()) {
            case SPEECH_COMPLETE -> terminalSpeech(turn, branch,
                    NpcTurnBranch.State.SPEECH_COMPLETE, null);
            case SPEECH_CANCELLED -> terminalSpeech(turn, branch,
                    NpcTurnBranch.State.SPEECH_CANCELLED,
                    event.facts().getOrDefault("reason", "SPEECH_CANCELLED"));
            case SPEECH_INTERRUPTED -> terminalSpeech(turn, branch,
                    NpcTurnBranch.State.INTERRUPTED,
                    event.facts().getOrDefault("reason", "USER_BARGE_IN"));
            case TTS_FAILED -> terminalSpeech(turn, branch,
                    NpcTurnBranch.State.TTS_FAILED,
                    event.facts().getOrDefault("reason", "TTS_FAILED"));
            case PLAYBACK_FAILED -> terminalSpeech(turn, branch,
                    NpcTurnBranch.State.PLAYBACK_FAILED,
                    event.facts().getOrDefault("reason", "PLAYBACK_FAILED"));
            case TIMED_OUT -> terminalSpeech(turn, branch,
                    NpcTurnBranch.State.TIMED_OUT,
                    event.facts().getOrDefault("reason", "PROVIDER_TIMEOUT"));
            default -> { }
        }
    }

    private void terminalSpeech(OrbisTurn turn, NpcTurnBranch branch,
            NpcTurnBranch.State terminalState, String reason) {
        boolean interrupted = terminalState == NpcTurnBranch.State.INTERRUPTED;
        if (!interrupted) {
            responseBranches.remove(branch.responseId().value());
        } else {
            UUID interruptedResponseId = branch.responseId().value();
            boundaryTimer.schedule(() -> enqueue(() ->
                            responseBranches.remove(interruptedResponseId)),
                    providerTimeoutMillis + 1_000, TimeUnit.MILLISECONDS);
        }
        branchProviders.remove(branch.responseId().value());
        com.inigmasgames.persistentnpcs.conversation.contract.RecoverySupervisor.complete(
                branch.responseId().value());
        BranchCognitionSnapshot snapshot = cognitionSnapshots.remove(
                branch.responseId().value());
        branch.state(terminalState);
        branch.terminalResult(reason == null ? terminalState.name() : reason);
        boolean failed = terminalState != NpcTurnBranch.State.SPEECH_COMPLETE && !interrupted;
        if (failed) {
            branch.speechLedger().discardUndelivered();
            branch.cancellation().cancel(terminalState == NpcTurnBranch.State.TIMED_OUT
                    ? CancellationReason.PROVIDER_TIMEOUT
                    : terminalState == NpcTurnBranch.State.SPEECH_CANCELLED
                            ? CancellationReason.ADMIN_CANCEL
                             : CancellationReason.PROVIDER_FAILURE);
        }
        SpeechDeliveryReport delivery = branch.deliveryReport(branch.terminalResult());
        if (interrupted) createDeferredTopic(turn, branch, delivery);
        if (snapshot != null && branch.outcome() != null && cognitionGateway != null) {
            try {
                cognitionGateway.deliveryCompleted(snapshot, branch.outcome(), delivery)
                        .exceptionally(failure -> {
                            log.accept("Orbis delivery history observer failed response="
                                    + branch.responseId().value() + " reason=" + root(failure));
                            return null;
                        });
            } catch (RuntimeException deliveryFailure) {
                log.accept("Orbis delivery history observer failed response="
                        + branch.responseId().value() + " reason=" + root(deliveryFailure));
            }
        }
        emit(failed ? OrbisEventType.BRANCH_CANCELLED : OrbisEventType.BRANCH_COMPLETED,
                turn, branch, failed ? Map.of("reason", branch.terminalResult())
                        : Map.of("completion", interrupted
                                ? "speech-interrupted-action-preserved"
                                : "hytale-playback-terminal",
                                "deliveredChunkCount", Integer.toString(
                                        delivery.delivered().size()),
                                "partialChunkCount", delivery.partial() == null ? "0" : "1",
                                "undeliveredChunkCount", Integer.toString(
                                        delivery.notDelivered().size())));
        releaseNpcFloor(turn, branch, interrupted ? "USER_BARGE_IN" : terminalState.name());
        boolean anyActive = turn.branches().values().stream().anyMatch(value ->
                value.responseOwner() && !NpcTurnBranch.terminal(value.state()));
        if (!anyActive) {
            turn.state(failed ? OrbisTurn.State.FAILED : OrbisTurn.State.COMPLETED);
            emit(failed ? OrbisEventType.TURN_FAILED : OrbisEventType.TURN_COMPLETED,
                    turn, branch, failed ? Map.of("reason", branch.terminalResult()) : Map.of());
        }
    }

    private static Map<String, String> speechSegmentFacts(CanonicalSpeechLedger.Segment segment) {
        return Map.ofEntries(
                Map.entry("speechChunkId", segment.chunk().id().value().toString()),
                Map.entry("chunkIndex", Integer.toString(segment.chunk().index())),
                Map.entry("canonicalPhrase", segment.chunk().text()),
                Map.entry("charStart", Integer.toString(segment.charStartInclusive())),
                Map.entry("charEnd", Integer.toString(segment.charEndExclusive())),
                Map.entry("ledgerState", segment.state().name()));
    }

    private void grantNpcFloor(OrbisTurn turn, NpcTurnBranch branch) {
        ConversationKey key = new ConversationKey(turn.playerId(), turn.worldId());
        ConversationFloor floor = floors.computeIfAbsent(key,
                ignored -> new ConversationFloor(ConversationFloorOwner.NONE));
        floor.owner = ConversationFloorOwner.NPC;
        floor.activeTurnId = turn.turnId();
        floor.responseId = branch.responseId();
        floor.npcId = branch.npcId();
        floor.lastTransition = Instant.now();
        emit(OrbisEventType.FLOOR_GRANTED, turn, branch, Map.of(
                "floorOwner", "NPC", "activeSpeakerNpcId", branch.npcId().toString(),
                "speechState", branch.state().name()));
    }

    private void grantNpcThinkingFloor(OrbisTurn turn, NpcTurnBranch branch) {
        ConversationKey key = new ConversationKey(turn.playerId(), turn.worldId());
        ConversationFloor floor = floors.computeIfAbsent(key,
                ignored -> new ConversationFloor(ConversationFloorOwner.NONE));
        floor.owner = ConversationFloorOwner.TRANSITION;
        floor.activeTurnId = turn.turnId();
        floor.responseId = branch.responseId();
        floor.npcId = branch.npcId();
        floor.bargeCandidateTurnId = null;
        floor.lastTransition = Instant.now();
        emit(OrbisEventType.FLOOR_GRANTED, turn, branch, Map.of(
                "floorOwner", "TRANSITION", "activeSpeakerNpcId",
                branch.npcId().toString(), "speechState", "COGNITION_PENDING"));
    }

    private static boolean speechCommitted(NpcTurnBranch.State state) {
        return state == NpcTurnBranch.State.DECISION_COMMITTED
                || state == NpcTurnBranch.State.SPEECH_QUEUED
                || state == NpcTurnBranch.State.TTS_SYNTHESIZING
                || state == NpcTurnBranch.State.AUDIO_READY
                || state == NpcTurnBranch.State.PLAYBACK_QUEUED
                || state == NpcTurnBranch.State.SPEAKING;
    }

    private void releaseNpcFloor(OrbisTurn turn, NpcTurnBranch branch, String reason) {
        ConversationKey key = new ConversationKey(turn.playerId(), turn.worldId());
        ConversationFloor floor = floors.get(key);
        if (floor == null || !branch.responseId().equals(floor.responseId)) return;
        ConversationFloorOwner priorOwner = floor.owner;
        floor.owner = ConversationFloorOwner.NONE;
        floor.responseId = null;
        floor.npcId = null;
        floor.lastTransition = Instant.now();
        emit(OrbisEventType.FLOOR_RELEASED, turn, branch, Map.of(
                "priorOwner", priorOwner.name(), "newOwner", "NONE", "reason", reason));
    }

    private void createDeferredTopic(OrbisTurn turn, NpcTurnBranch branch,
            SpeechDeliveryReport delivery) {
        try {
            String selectedIntent = branch.decision() == null ? "UNKNOWN"
                    : branch.decision().intent().name();
            DeferredTopic topic = new DeferredTopic(branch.npcId(), turn.playerId(),
                    turn.turnId(), branch.responseId(),
                    "player request: " + compact(turn.transcript(), 300)
                            + "; interrupted intent: " + selectedIntent,
                    selectedIntent,
                    delivery.delivered().stream().map(CanonicalSpeechChunk::text).toList(),
                    delivery.partial() == null ? "" : delivery.partial().text(),
                    delivery.notDelivered().stream().map(CanonicalSpeechChunk::text).toList(),
                    Instant.now(), CancellationReason.USER_BARGE_IN.name(),
                    Instant.now().plusSeconds(DeferredTopicStore.TTL_SECONDS),
                    DeferredTopicStore.MAX_TURNS);
            deferredTopics.add(topic);
            emit(OrbisEventType.DEFERRED_TOPIC_CREATED, turn, branch, Map.ofEntries(
                    Map.entry("sourceResponseId", branch.responseId().value().toString()),
                    Map.entry("selectedIntent", selectedIntent),
                    Map.entry("deliveredChunkCount", Integer.toString(
                            delivery.delivered().size())),
                    Map.entry("partialChunkCount", delivery.partial() == null ? "0" : "1"),
                    Map.entry("undeliveredChunkCount", Integer.toString(
                            delivery.notDelivered().size())),
                    Map.entry("activeDeferredTopicCount", Integer.toString(
                            deferredTopics.count(turn.playerId(), branch.npcId(), Instant.now()))),
                    Map.entry("expiresSeconds", Long.toString(
                            DeferredTopicStore.TTL_SECONDS))));
        } catch (RuntimeException failure) {
            emit(OrbisEventType.DIAGNOSTIC, turn, branch, Map.of(
                    "stage", "deferred-topic", "reason", root(failure)));
        }
    }

    private static SpeechChunkId parseSpeechChunkId(String value) {
        if (value == null || value.isBlank()) return null;
        try { return new SpeechChunkId(UUID.fromString(value)); }
        catch (IllegalArgumentException ignored) { return null; }
    }

    private static List<SpeechChunkId> parseSpeechChunkIds(String csv) {
        if (csv == null || csv.isBlank()) return List.of();
        return java.util.Arrays.stream(csv.split(","))
                .map(String::strip).map(OrbisTurnCoordinator::parseSpeechChunkId)
                .filter(java.util.Objects::nonNull).toList();
    }

    private String validateDecision(NpcTurnBranch branch, ConversationOutcome outcome) {
        if (outcome == null) return "cognition returned no outcome";
        if (!branch.responseId().value().equals(outcome.responseId())) {
            return "responseId mismatch";
        }
        if (!branch.providerRequestId().value().equals(outcome.providerRequestId())) {
            return "providerRequestId mismatch";
        }
        if (outcome.decision() == null) return "structured NpcDecision missing";
        if (!branch.responseId().value().equals(outcome.decision().responseId())) {
            return "decision responseId mismatch";
        }
        if (!branch.npcId().equals(outcome.decision().npcStableId())) {
            return "decision NPC identity mismatch";
        }
        if (!outcome.dialogue().equals(outcome.decision().spokenText())) {
            return "canonical dialogue differs from NpcDecision spokenText";
        }
        String safety = SpokenTextSafetyValidator.rejectionReason(outcome.dialogue());
        if (safety != null) return "spoken-text safety rejected: " + safety;
        return ActionPromiseGuard.violation(outcome.dialogue(), outcome.decision().actions())
                .orElse("");
    }

    private Map<String, String> decisionFacts(NpcTurnBranch branch,
            ConversationOutcome outcome) {
        LinkedHashMap<String, String> facts = new LinkedHashMap<>();
        facts.put("npcId", branch.npcId().toString());
        facts.put("npc", branch.npcName());
        facts.put("provider", branch.provider());
        facts.put("model", branch.model());
        facts.put("selectedIntent", outcome.decision().intent().name());
        facts.put("actionCount", Integer.toString(outcome.decision().actions().size()));
        facts.put("validation", "VALID");
        facts.put("canonicalSpokenText", compact(outcome.dialogue(), 2_000));
        facts.put("cognitiveDepth", outcome.cognitiveDepth().name());
        facts.put("contextCharacters", Integer.toString(outcome.contextCharacters()));
        facts.put("contextSections", outcome.contextSections().toString());
        facts.put("relevantMemories", Integer.toString(outcome.relevantMemoryCount()));
        facts.put("relevantRelationships",
                Integer.toString(outcome.relevantRelationshipCount()));
        facts.put("ttftMs", Long.toString(outcome.llmLatency().timeToFirstTokenMillis()));
        facts.put("generationMs", Long.toString(outcome.llmLatency().completionMillis()));
        facts.put("promptTokens", Integer.toString(outcome.usage().promptTokens()));
        facts.put("outputTokens", Integer.toString(outcome.usage().completionTokens()));
        facts.put("tokensPerSecond", Double.toString(outcome.usage().tokensPerSecond(
                Math.max(1, outcome.llmLatency().completionMillis()
                        - outcome.llmLatency().timeToFirstTokenMillis()))));
        if (outcome.decisionDiagnostics() != null) {
            facts.put("parseStatus", outcome.decisionDiagnostics().schemaValidationResult());
            facts.put("actionValidation",
                    outcome.decisionDiagnostics().actionValidationResult());
            facts.put("agentOperation",
                    compact(outcome.decisionDiagnostics().committedAgentOperation(), 600));
        }
        return Map.copyOf(facts);
    }

    private void failCognition(OrbisTurn turn, NpcTurnBranch branch,
            CancellationReason reason, Throwable failure) {
        branch.failureReason(root(failure));
        Throwable cause = rootCause(failure);
        emit(OrbisEventType.DIAGNOSTIC, turn, branch, Map.ofEntries(
                Map.entry("stage", "cognition-provider-failure"),
                Map.entry("failureCategory", cause == null
                        ? "UNKNOWN" : cause.getClass().getSimpleName()),
                Map.entry("failureReason", compact(root(failure), 600)),
                Map.entry("providerRequestId", branch.providerRequestId().value().toString())));
        if (resources != null) resources.cancel(branch.providerRequestId().value(),
                reason.name());
        PinnedLlmProvider pinned = branchProviders.remove(branch.responseId().value());
        if (pinned != null) pinned.delegate().cancel(branch.providerRequestId().value());
        BranchCognitionSnapshot snapshot = cognitionSnapshots.remove(
                branch.responseId().value());
        terminalResponse(branch.responseId().value(), reason, true);
        if (snapshot != null) {
            try { cognitionGateway.failed(snapshot, reason, failure); }
            catch (RuntimeException ignored) { }
        }
    }

    private void supersedeNpcBranch(UUID npcId) {
        for (OrbisTurn priorTurn : List.copyOf(turns.values())) {
            NpcTurnBranch prior = priorTurn.branches().get(npcId);
            if (prior == null || !prior.responseOwner()
                    || NpcTurnBranch.terminal(prior.state())) continue;
            if (prior.speechInterruptionPending()) continue;
            prior.cancellation().cancel(CancellationReason.SUPERSEDED);
            if (resources != null) resources.cancel(prior.providerRequestId().value(),
                    CancellationReason.SUPERSEDED.name());
            PinnedLlmProvider pinned = branchProviders.remove(prior.responseId().value());
            if (pinned != null) pinned.delegate().cancel(prior.providerRequestId().value());
            if (speechCoordinator != null) speechCoordinator.cancel(
                    prior.responseId(), CancellationReason.SUPERSEDED);
            terminalResponse(prior.responseId().value(), CancellationReason.SUPERSEDED, true);
        }
    }

    private static boolean currentBranch(NpcTurnBranch branch, long epoch) {
        return branch != null && branch.epoch() == epoch
                && !branch.cancellation().isCancelled()
                && !NpcTurnBranch.terminal(branch.state());
    }

    private void cancelCognitionProvider(NpcTurnBranch branch, CancellationReason reason) {
        if (resources != null) resources.cancel(branch.providerRequestId().value(),
                reason.name());
        PinnedLlmProvider pinned = branchProviders.remove(branch.responseId().value());
        if (pinned != null) pinned.delegate().cancel(branch.providerRequestId().value());
        BranchCognitionSnapshot snapshot = cognitionSnapshots.remove(
                branch.responseId().value());
        if (snapshot != null && cognitionGateway != null) {
            try {
                cognitionGateway.failed(snapshot, reason,
                        new java.util.concurrent.CancellationException(reason.name()));
            } catch (RuntimeException ignored) { }
        }
    }

    private void terminalResponse(UUID responseId, CancellationReason reason, boolean failed) {
        BranchLocator locator = responseBranches.remove(responseId);
        if (locator == null) return;
        branchProviders.remove(responseId);
        cognitionSnapshots.remove(responseId);
        OrbisTurn turn = turns.get(locator.turnId());
        NpcTurnBranch branch = turn == null ? null : turn.branches().get(locator.npcId());
        if (branch == null || branch.epoch() != locator.branchEpoch()) {
            stale(locator.turnId(), locator.branchEpoch(), "response");
            return;
        }
        branch.state(failed ? NpcTurnBranch.State.FAILED : NpcTurnBranch.State.COMPLETED);
        branch.terminalResult(failed ? reason.name() : "COMPLETED");
        if (failed) {
            branch.cancellation().cancel(reason);
            String failureReason = compact(branch.failureReason(), 600);
            emit(OrbisEventType.BRANCH_CANCELLED, turn, branch,
                    Map.of("npcId", branch.npcId().toString(),
                            "reason", reason.name(),
                            "failureReason", failureReason.isBlank()
                                    ? "UNKNOWN" : failureReason));
        } else {
            emit(OrbisEventType.BRANCH_COMPLETED, turn, branch,
                    Map.of("npcId", branch.npcId().toString(),
                            "npc", branch.npcName()));
        }
        releaseNpcFloor(turn, branch, failed ? reason.name() : "COMPLETED");
        boolean anyActive = turn.branches().values().stream().anyMatch(value ->
                value.responseOwner() && !NpcTurnBranch.terminal(value.state()));
        if (!anyActive) {
            turn.state(failed ? OrbisTurn.State.FAILED : OrbisTurn.State.COMPLETED);
            emit(failed ? OrbisEventType.TURN_FAILED : OrbisEventType.TURN_COMPLETED,
                    turn, branch, failed ? Map.of(
                            "reason", reason.name(),
                            "failureReason", branch.failureReason().isBlank()
                                    ? "UNKNOWN" : compact(branch.failureReason(), 600))
                            : Map.of());
        }
    }

    public void playerDisconnected(UUID playerId) {
        enqueue(() -> {
            PlayerCaptureSession capture = captures.remove(playerId);
            if (capture != null) {
                OrbisTurn turn = turns.get(capture.turnId());
                if (turn != null) cancelTurnOnCoordinator(turn,
                        CancellationReason.PLAYER_DISCONNECT);
            }
            for (OrbisTurn turn : List.copyOf(turns.values())) {
                if (turn.playerId().equals(playerId) && !terminal(turn.state())) {
                    cancelTurnOnCoordinator(turn, CancellationReason.PLAYER_DISCONNECT);
                }
            }
            CancellationScope playerScope = playerCancellations.remove(playerId);
            if (playerScope != null) playerScope.cancel(CancellationReason.PLAYER_DISCONNECT);
            floors.keySet().removeIf(key -> key.playerId().equals(playerId));
            deferredTopics.removePlayer(playerId);
            recentVoiceTranscripts.remove(playerId);
        });
    }

    /**
     * Called from the native attention tick when the authoritative player/NPC focus leaves
     * ordinary listening range or line of sight. The callback only enqueues; it never blocks
     * the Hytale world thread.
     */
    public void conversationFocusLost(UUID npcId, UUID playerId) {
        if (npcId == null || playerId == null || closed.get()) return;
        enqueue(() -> {
            for (OrbisTurn turn : List.copyOf(turns.values())) {
                if (!playerId.equals(turn.playerId()) || terminal(turn.state())) continue;
                NpcTurnBranch branch = turn.branches().get(npcId);
                if (branch == null || !branch.responseOwner()
                        || NpcTurnBranch.terminal(branch.state())) continue;
                emit(OrbisEventType.DIAGNOSTIC, turn, branch, Map.of(
                        "stage", "conversation-focus",
                        "reason", "AUTHORITATIVE_RANGE_OR_VISIBILITY_LOST",
                        "action", "CANCEL_FOREGROUND_COGNITION_AND_SPEECH"));
                cancelTurnOnCoordinator(turn, CancellationReason.CONVERSATION_RANGE_LOST);
            }
        });
    }

    public void cancelTurn(TurnId turnId, CancellationReason reason) {
        enqueue(() -> {
            OrbisTurn turn = turns.get(turnId);
            if (turn != null) cancelTurnOnCoordinator(turn, reason);
        });
    }

    public void cancelNpc(UUID npcId, CancellationReason reason) {
        enqueue(() -> {
            for (OrbisTurn turn : List.copyOf(turns.values())) {
                NpcTurnBranch branch = turn.branches().get(npcId);
                if (branch != null && !branch.cancellation().isCancelled()) {
                    branch.cancellation().cancel(reason);
                    branch.state(NpcTurnBranch.State.CANCELLED);
                    branch.terminalResult(reason.name());
                    responseBranches.remove(branch.responseId().value());
                    cancelCognitionProvider(branch, reason);
                    if (speechCoordinator != null) speechCoordinator.cancel(
                            branch.responseId(), reason);
                    emit(OrbisEventType.BRANCH_CANCELLED, turn, branch,
                            Map.of("npcId", branch.npcId().toString(),
                                    "reason", reason.name()));
                }
            }
        });
    }

    public void worldUnloaded(UUID worldId) {
        if (worldId == null) return;
        enqueue(() -> {
            for (OrbisTurn turn : List.copyOf(turns.values())) {
                if (worldId.equals(turn.worldId()) && !terminal(turn.state())) {
                    cancelTurnOnCoordinator(turn, CancellationReason.WORLD_UNLOAD);
                }
            }
        });
    }

    private void cancelTurnOnCoordinator(OrbisTurn turn, CancellationReason reason) {
        if (!turn.cancellation().cancel(reason)) return;
        turn.state(OrbisTurn.State.CANCELLED);
        if (resources != null) resources.cancel(turn.sttRequestId().value(), reason.name());
        if (stt != null) stt.cancel(turn.sttRequestId().value());
        captures.entrySet().removeIf(entry -> entry.getValue().turnId().equals(turn.turnId()));
        for (NpcTurnBranch branch : turn.branches().values()) {
            branch.state(NpcTurnBranch.State.CANCELLED);
            branch.terminalResult(reason.name());
            responseBranches.remove(branch.responseId().value());
            if (branch.responseOwner()) {
                cancelCognitionProvider(branch, reason);
                if (speechCoordinator != null) speechCoordinator.cancel(
                        branch.responseId(), reason);
                emit(OrbisEventType.BRANCH_CANCELLED, turn, branch,
                        Map.of("npcId", branch.npcId().toString(),
                                "reason", reason.name()));
            }
        }
        emit(OrbisEventType.TURN_CANCELLED, turn, null,
                Map.of("reason", reason.name()));
    }

    private boolean current(OrbisTurn turn, long epoch) {
        return turn != null && turn.epoch() == epoch && !turn.cancellation().isCancelled();
    }

    private static String normalizeTranscript(String text) {
        return text == null ? "" : text.replaceAll("\\s+", " ").strip().toLowerCase(
                java.util.Locale.ROOT);
    }

    private void stale(TurnId turnId, long epoch, String stage) {
        emit(OrbisEventType.CALLBACK_REJECTED_STALE, turns.get(turnId), null,
                turnId, epoch, Map.of("stage", stage));
    }

    private void resourceProgress(TurnId turnId, UUID npcId, long epoch,
            OrbisResourceEvent resourceEvent) {
        OrbisTurn turn = turns.get(turnId);
        NpcTurnBranch branch = turn == null || npcId == null ? null
                : turn.branches().get(npcId);
        if (turn == null || branch == null && turn.epoch() != epoch
                || branch != null && branch.epoch() != epoch) {
            stale(turnId, epoch, "resource-" + resourceEvent.type().name().toLowerCase());
            return;
        }
        LinkedHashMap<String, String> facts = new LinkedHashMap<>(resourceEvent.facts());
        facts.put("resourceRequestId", resourceEvent.requestId().toString());
        if (resourceEvent.type() != OrbisResourceEvent.Type.RESOURCE_RECHECK) {
            facts.put("placement", resourceEvent.placement().name());
            facts.put("admissionWaitMs", Long.toString(
                    resourceEvent.admissionWaitMillis()));
        }
        if (branch != null) facts.put("npcId", branch.npcId().toString());
        emit(OrbisEventType.valueOf(resourceEvent.type().name()), turn, branch,
                Map.copyOf(facts));
    }

    private void retain(OrbisTurn turn) {
        turns.put(turn.turnId(), turn);
        turnOrder.addLast(turn.turnId());
        while (turnOrder.size() > MAX_RETAINED_TURNS) {
            TurnId oldest = turnOrder.removeFirst();
            OrbisTurn removed = turns.get(oldest);
            if (removed != null && terminal(removed.state())) turns.remove(oldest);
        }
    }

    private static boolean terminal(OrbisTurn.State state) {
        return state == OrbisTurn.State.COMPLETED || state == OrbisTurn.State.CANCELLED
                || state == OrbisTurn.State.FAILED;
    }

    private void emit(OrbisEventType type, OrbisTurn turn, NpcTurnBranch branch,
            Map<String, String> facts) {
        emit(type, turn, branch, turn == null ? null : turn.turnId(),
                turn == null ? 0 : turn.epoch(), facts);
    }

    private void emit(OrbisEventType type, OrbisTurn turn, NpcTurnBranch branch,
            TurnId turnId, long epoch, Map<String, String> facts) {
        Map<String, String> eventFacts = new LinkedHashMap<>(
                facts == null ? Map.of() : facts);
        if (turn != null) {
            eventFacts.putIfAbsent("playerId", turn.playerId().toString());
            eventFacts.putIfAbsent("utteranceId", turn.utteranceId().value().toString());
            eventFacts.putIfAbsent("ingressSource", turn.ingressSource().name());
            eventFacts.putIfAbsent("ingressProvenance", turn.ingressProvenance());
            eventFacts.putIfAbsent("originalPhysicalUtteranceId",
                    turn.originalPhysicalUtteranceId() == null ? ""
                            : turn.originalPhysicalUtteranceId().toString());
        }
        if (branch != null) {
            eventFacts.putIfAbsent("npcId", branch.npcId().toString());
            eventFacts.putIfAbsent("npc", branch.npcName());
        }
        OrbisEvent event = new OrbisEvent(eventSequence.incrementAndGet(), Instant.now(),
                type, turnId, branch == null ? null : branch.branchId(),
                branch == null ? null : branch.responseId(),
                branch == null ? epoch : branch.epoch(),
                branch == null ? turn == null ? null : turn.sttRequestId()
                        : branch.providerRequestId(), eventFacts);
        diagnostics.observe(event);
        if (type != OrbisEventType.CAPTURE_FRAME_ACCEPTED) {
            log.accept("ORBIS_EVENT seq=" + event.sequence() + " type=" + type
                    + " turn=" + (turnId == null ? "none" : turnId.value())
                    + " epoch=" + event.epoch() + " facts=" + eventFacts);
        }
    }

    private void enqueue(Runnable mutation) {
        if (closed.get()) return;
        try { coordinator.execute(mutation); } catch (RuntimeException ignored) { }
    }

    public OrbisDiagnostics diagnostics() { return diagnostics; }

    @Override public void close() {
        if (!closed.compareAndSet(false, true)) return;
        try {
            coordinator.execute(() -> {
                for (OrbisTurn turn : List.copyOf(turns.values())) {
                    if (!terminal(turn.state())) cancelTurnOnCoordinator(
                            turn, CancellationReason.SERVER_SHUTDOWN);
                }
                serverCancellation.cancel(CancellationReason.SERVER_SHUTDOWN);
            });
        } catch (RuntimeException ignored) { }
        boundaryTimer.shutdownNow();
        coordinator.shutdown();
        if (speechCoordinator != null) speechCoordinator.close();
    }

    private static long elapsed(long start, long end) {
        return TimeUnit.NANOSECONDS.toMillis(Math.max(0, end - start));
    }

    private static String compact(String value, int maximum) {
        String text = value == null ? "" : value.replaceAll("[\\r\\n]+", " ")
                .replaceAll("\\s+", " ").strip();
        return text.length() <= maximum ? text : text.substring(0, maximum);
    }

    private static String root(Throwable failure) {
        if (failure == null) return "unknown";
        Throwable current = failure;
        while (current.getCause() != null) current = current.getCause();
        String message = current.getMessage();
        return current.getClass().getSimpleName()
                + (message == null ? "" : ": " + compact(message, 1_000));
    }

    private static Throwable rootCause(Throwable failure) {
        Throwable current = failure;
        while (current != null && current.getCause() != null) current = current.getCause();
        return current == null ? failure : current;
    }

    private record SttCallback(TurnId turnId, long epoch,
            PlayerCaptureSession capture, long endpointNanos, long sttStartedNanos,
            long completedNanos, SpeechTranscript transcript, Throwable failure) { }
    private record AudienceCallback(TurnId turnId, long epoch,
            TranscribedPlayerUtterance utterance,
            PlayerUtteranceAudienceService.Resolution resolution, Throwable failure) { }
    private record RecentVoiceTranscript(TurnId turnId, UtteranceId utteranceId,
            String normalizedText, Instant completedAt) { }
    private record BranchLocator(TurnId turnId, UUID npcId, long branchEpoch) { }
    private record ConversationKey(UUID playerId, UUID worldId) { }
    private static final class ConversationFloor {
        private ConversationFloorOwner owner;
        private TurnId activeTurnId;
        private ResponseId responseId;
        private UUID npcId;
        private TurnId bargeCandidateTurnId;
        private long bargeCandidateNanos;
        private Instant bargeConfirmedAt;
        private Instant lastTransition;

        private ConversationFloor(ConversationFloorOwner owner) {
            this.owner = owner == null ? ConversationFloorOwner.NONE : owner;
            this.lastTransition = Instant.now();
        }
    }
}
