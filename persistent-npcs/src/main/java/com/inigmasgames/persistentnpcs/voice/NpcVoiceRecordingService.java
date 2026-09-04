package com.inigmasgames.persistentnpcs.voice;

import com.hypixel.hytale.event.EventPriority;
import com.hypixel.hytale.registry.Registration;
import com.hypixel.hytale.server.core.modules.voice.ClipPlayback;
import com.hypixel.hytale.server.core.modules.voice.PlayerVoiceFrame;
import com.hypixel.hytale.server.core.modules.voice.PlayerVoiceInterceptor;
import com.hypixel.hytale.server.core.modules.voice.VoiceModule;
import com.hypixel.hytale.server.core.modules.voice.VoiceSpeaker;
import com.inigmasgames.persistentnpcs.ai.AiServiceRouter;
import com.inigmasgames.persistentnpcs.voice.NpcVoiceSamplePersistenceService.Draft;
import java.nio.file.Files;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/** Exclusive, privacy-preserving NPC voice-sample recorder and private preview owner. */
public final class NpcVoiceRecordingService implements AutoCloseable {
    public enum State {
        IDLE, ARMED, RECORDING, FINALIZING, READY, PLAYING,
        SAVING, SAVED, FAILED, CANCELLED
    }

    public static final long MAX_DURATION_MILLIS = configuredLong(
            "immersivenpcs.voiceRecorder.maxDurationMillis", 30_000, 5_001, 60_000);
    public static final long ARMED_TIMEOUT_MILLIS = configuredLong(
            "immersivenpcs.voiceRecorder.armedTimeoutMillis", 5_000, 1_000, 15_000);
    public static final int MAX_QUEUED_FRAMES = configuredInt(
            "immersivenpcs.voiceRecorder.maxQueuedFrames", 1_600, 64, 5_000);
    public static final int MAX_QUEUED_BYTES = configuredInt(
            "immersivenpcs.voiceRecorder.maxQueuedBytes", 1_048_576, 65_536, 4_194_304);
    public static final int MAX_CONCURRENT_SESSIONS = configuredInt(
            "immersivenpcs.voiceRecorder.maxConcurrentSessions", 4, 1, 16);
    public static final int MAX_SEQUENCE_GAPS = configuredInt(
            "immersivenpcs.voiceRecorder.maxSequenceGaps", 12, 0, 100);
    public static final int WAVEFORM_BUCKETS = configuredInt(
            "immersivenpcs.voiceRecorder.waveformBuckets", 32, 8, 64);
    public static final long MAX_DRAFT_AGE_MILLIS = configuredLong(
            "immersivenpcs.voiceRecorder.maxDraftAgeMillis", 3_600_000,
            60_000, 86_400_000);

    private final VoiceModule voiceModule;
    private final VoiceCaptureLeaseManager leases;
    private final VoicePresetRepository presets;
    private final NpcVoiceSamplePersistenceService persistence;
    private final AiServiceRouter ai;
    private final Consumer<String> diagnostics;
    private final ConcurrentHashMap<UUID, RecorderSession> byPlayer = new ConcurrentHashMap<>();
    private final ScheduledExecutorService scheduler;
    private final Registration interceptor;
    private final PlayerVoiceInterceptor voiceInterceptor;
    private final AtomicBoolean closed = new AtomicBoolean();

    public NpcVoiceRecordingService(VoiceModule voiceModule,
            VoiceCaptureLeaseManager leases, VoicePresetRepository presets,
            AiServiceRouter ai, Consumer<String> diagnostics) {
        this.voiceModule = voiceModule;
        this.leases = leases;
        this.presets = presets;
        this.ai = ai;
        this.diagnostics = diagnostics == null ? ignored -> { } : diagnostics;
        this.persistence = new NpcVoiceSamplePersistenceService(presets,
                path -> ai.invalidateVoiceConditioning(path).whenComplete((cleared, failure) ->
                        this.diagnostics.accept("NPC_AUTHORING_VOICE_CACHE_INVALIDATE"
                                + " timestamp=" + Instant.now() + " path=" + path.getFileName()
                                + " cleared=" + (cleared == null ? 0 : cleared)
                                + " result=" + (failure == null ? "SUCCESS" : "FAILED"))),
                this.diagnostics);
        scheduler = Executors.newSingleThreadScheduledExecutor(task -> {
            Thread thread = new Thread(task, "immersive-npc-voice-recorder");
            thread.setDaemon(true);
            return thread;
        });
        voiceInterceptor = this::acceptVoiceFrame;
        interceptor = voiceModule == null || !voiceModule.isVoiceEnabled() ? null
                : voiceModule.addPlayerVoiceInterceptor(EventPriority.LAST, voiceInterceptor);
        scheduler.scheduleAtFixedRate(this::watchdog, 100, 100, TimeUnit.MILLISECONDS);
        this.diagnostics.accept("NPC_AUTHORING_VOICE_CAPTURE_READY timestamp=" + Instant.now()
                + " priority=LAST proximityPolicy=DROP callbackWork=BOUNDED_COPY_AND_OFFER"
                + " conversationIsolation=SHARED_CAPTURE_LEASE");
    }

    public Handle open(UUID playerId, UUID authoringSessionId, UUID stableNpcId,
            String npcName, long pageGeneration, long editorGeneration) {
        return open(playerId, authoringSessionId, stableNpcId, npcName, pageGeneration,
                editorGeneration, VoiceClientCaptureContract.unknown());
    }

    public Handle open(UUID playerId, UUID authoringSessionId, UUID stableNpcId,
            String npcName, long pageGeneration, long editorGeneration,
            VoiceClientCaptureContract captureContract) {
        if (closed.get()) throw new IllegalStateException("Voice Recorder is shutting down.");
        if (voiceModule == null || !voiceModule.isVoiceEnabled() || interceptor == null
                || !interceptor.isRegistered()) {
            throw new IllegalStateException("Hytale voice input is unavailable on this server.");
        }
        if (byPlayer.size() >= MAX_CONCURRENT_SESSIONS && !byPlayer.containsKey(playerId)) {
            throw new IllegalStateException("Too many voice recorders are currently open.");
        }
        persistence.cleanupStaleDrafts(npcName, MAX_DRAFT_AGE_MILLIS);
        RecorderSession created = new RecorderSession(playerId, authoringSessionId,
                stableNpcId, npcName, pageGeneration, editorGeneration,
                captureContract == null ? VoiceClientCaptureContract.unknown() : captureContract);
        RecorderSession prior = byPlayer.put(playerId, created);
        if (prior != null) cleanup(prior, "REOPENED");
        requestSavedWaveform(created);
        diagnostics.accept("NPC_AUTHORING_VOICE_OPEN timestamp=" + Instant.now()
                + " playerId=" + playerId + " npcStableId=" + stableNpcId
                + " sessionId=" + authoringSessionId + " pageGeneration=" + pageGeneration
                + " editorGeneration=" + editorGeneration);
        return new Handle(created);
    }

    /** Voice executor callback: lookup, generation/state check, bounded copy, drop, offer. */
    private void acceptVoiceFrame(PlayerVoiceFrame frame) {
        if (frame == null || closed.get() || frame.speaker() == null) return;
        RecorderSession session = byPlayer.get(frame.speaker().getUuid());
        if (session == null) return;
        State state = session.state.get();
        if (state == State.PLAYING) {
            frame.drop();
            return;
        }
        if (state != State.ARMED && state != State.RECORDING) return;
        byte[] encoded = frame.opus();
        if (!VoiceRecordingPolicy.validOpusFrame(encoded)) {
            frame.drop();
            session.overflow.set(true);
            session.droppedFrames.incrementAndGet();
            return;
        }
        byte[] copy = encoded.clone();
        frame.drop();
        int bytes = session.queuedBytes.addAndGet(copy.length);
        if (bytes > MAX_QUEUED_BYTES || !session.frames.offer(new CopiedFrame(
                session.captureOrdinal.getAndIncrement(), frame.sequenceNumber(),
                frame.timestamp(), copy))) {
            session.queuedBytes.addAndGet(-copy.length);
            session.overflow.set(true);
            session.droppedFrames.incrementAndGet();
            return;
        }
        if (session.state.compareAndSet(State.ARMED, State.RECORDING)) {
            session.firstFrameNanos = System.nanoTime();
            diagnostics.accept("NPC_AUTHORING_VOICE_FIRST_FRAME timestamp=" + Instant.now()
                    + " recordingId=" + session.recordingId
                    + " recordingGeneration=" + session.recordingGeneration.get()
                    + " emotion=" + session.selected);
            diagnostics.accept("NPC_AUTHORING_VOICE_RECORDING timestamp=" + Instant.now()
                    + " recordingId=" + session.recordingId + " state=RECORDING");
        }
    }

    private void watchdog() {
        if (closed.get()) return;
        long now = System.nanoTime();
        for (RecorderSession session : byPlayer.values()) {
            State state = session.state.get();
            if (session.overflow.get() && (state == State.ARMED || state == State.RECORDING)) {
                failCapture(session, "Recording buffer filled before Stop. Record again.",
                        "QUEUE_OVERFLOW");
            } else if (state == State.ARMED && elapsed(session.armedNanos, now)
                    >= ARMED_TIMEOUT_MILLIS) {
                failCapture(session,
                        noMicrophoneMessage(session),
                        "ARMED_TIMEOUT");
            } else if (state == State.RECORDING && elapsed(session.firstFrameNanos, now)
                    >= MAX_DURATION_MILLIS) {
                stop(session, true);
            }
        }
    }

    private void select(RecorderSession session, VoiceSampleType type) {
        requireCurrent(session);
        if (session.state.get() == State.ARMED || session.state.get() == State.RECORDING
                || session.state.get() == State.FINALIZING || session.state.get() == State.SAVING) {
            throw new IllegalStateException("Stop or finish the current recording first.");
        }
        stopPlayback(session);
        discardDraft(session);
        session.selected = type;
        session.openRevision = presets.sampleRevision(session.npcName, type);
        refreshSavedState(session);
        session.state.set(State.IDLE);
        session.message = "Selected " + type.label() + ". Press Record when ready.";
        session.error = false;
        session.recordingGeneration.incrementAndGet();
        requestSavedWaveform(session);
        diagnostics.accept("NPC_AUTHORING_VOICE_SELECT_EMOTION timestamp=" + Instant.now()
                + " npcStableId=" + session.stableNpcId + " emotion=" + type
                + " revision=" + session.openRevision);
    }

    private void record(RecorderSession session) {
        requireCurrent(session);
        State state = session.state.get();
        if (state == State.ARMED || state == State.RECORDING || state == State.FINALIZING
                || state == State.SAVING) {
            throw new IllegalStateException("The current recording operation is still active.");
        }
        requireFinalRoutingOwnership();
        stopPlayback(session);
        discardDraft(session);
        session.recordingId = UUID.randomUUID();
        session.recordingGeneration.incrementAndGet();
        session.frames.clear();
        session.finalFrames = List.of();
        session.queuedBytes.set(0);
        session.captureOrdinal.set(0);
        session.droppedFrames.set(0);
        session.overflow.set(false);
        session.audio = null;
        session.sequenceGaps = 0;
        session.duplicates = 0;
        session.outOfOrder = 0;
        session.armedNanos = System.nanoTime();
        session.firstFrameNanos = 0;
        session.lease = leases.acquireRecording(session.playerId, session.recordingId);
        session.state.set(State.ARMED);
        session.message = session.captureContract.speakWithoutPushToTalk()
                ? "Recording armed. Speak normally."
                : "Recording armed. Hytale 0.6.3 cannot activate this client's microphone.";
        session.error = false;
        diagnostics.accept("NPC_AUTHORING_VOICE_ARMED timestamp=" + Instant.now()
                + " recordingId=" + session.recordingId + " npcStableId="
                + session.stableNpcId + " emotion=" + session.selected
                + " recordingGeneration=" + session.recordingGeneration.get());
    }

    private void stop(RecorderSession session, boolean maximumDuration) {
        requireCurrent(session);
        State state = session.state.get();
        if (state == State.ARMED) {
            failCapture(session, noMicrophoneMessage(session),
                    "STOP_WITHOUT_FRAMES");
            return;
        }
        if (!session.state.compareAndSet(State.RECORDING, State.FINALIZING)) {
            if (state != State.FINALIZING) {
                throw new IllegalStateException("There is no active recording to stop.");
            }
            return;
        }
        releaseLease(session);
        long generation = session.recordingGeneration.get();
        List<CopiedFrame> ordered = new ArrayList<>(session.frames);
        ordered.sort(java.util.Comparator.comparingLong(CopiedFrame::ordinal));
        session.frames.clear();
        session.finalFrames = ordered.stream().map(value -> value.opus.clone()).toList();
        validateSequences(session, ordered);
        session.message = maximumDuration
                ? "Maximum duration reached. Finalizing..." : "Finalizing recording...";
        diagnostics.accept("NPC_AUTHORING_VOICE_STOP timestamp=" + Instant.now()
                + " recordingId=" + session.recordingId + " frameCount=" + ordered.size()
                + " queuedBytes=" + session.queuedBytes.get() + " automatic="
                + maximumDuration + " sequenceGaps=" + session.sequenceGaps
                + " duplicates=" + session.duplicates + " outOfOrder=" + session.outOfOrder);
        diagnostics.accept("NPC_AUTHORING_VOICE_FRAME_DROP_COUNT timestamp=" + Instant.now()
                + " recordingId=" + session.recordingId + " queueDrops="
                + session.droppedFrames.get() + " sequenceGaps=" + session.sequenceGaps
                + " duplicates=" + session.duplicates + " outOfOrder="
                + session.outOfOrder);
        if (ordered.isEmpty()) {
            failFinalization(session, generation,
                    noMicrophoneMessage(session), "NO_FRAMES");
            return;
        }
        ai.decodeVoiceDraft(session.recordingId, session.finalFrames, WAVEFORM_BUCKETS)
                .whenComplete((audio, failure) -> {
                    if (!currentGeneration(session, generation)) return;
                    if (failure != null) {
                        failFinalization(session, generation,
                                "Could not finalize the recording. Record again.",
                                "DECODE_FAILED:" + root(failure));
                        return;
                    }
                    session.audio = audio;
                    String issue = VoiceRecordingPolicy.qualityIssue(audio,
                            new VoiceRecordingPolicy.SequenceStats(session.sequenceGaps,
                                    session.duplicates, session.outOfOrder),
                            MAX_SEQUENCE_GAPS);
                    diagnostics.accept("NPC_AUTHORING_VOICE_QUALITY timestamp=" + Instant.now()
                            + " recordingId=" + session.recordingId + " durationMillis="
                            + audio.durationMillis() + " peakDbfs=" + audio.peakDbfs()
                            + " rmsDbfs=" + audio.rmsDbfs() + " clippingRatio="
                            + audio.clippingRatio() + " silenceRatio=" + audio.silenceRatio()
                            + " result=" + (issue == null ? "PASS" : "REJECT"));
                    if (issue != null) {
                        failFinalization(session, generation, issue, "QUALITY_REJECTED");
                        return;
                    }
                    try {
                        session.draft = persistence.writeDraft(session.npcName,
                                session.recordingId, session.selected, audio);
                        if (!currentGeneration(session, generation)) {
                            persistence.discard(session.draft);
                            session.draft = null;
                            return;
                        }
                        session.state.set(State.READY);
                        session.message = "Ready to save.";
                        session.error = false;
                        diagnostics.accept("NPC_AUTHORING_VOICE_FINALIZE timestamp=" + Instant.now()
                                + " recordingId=" + session.recordingId + " emotion="
                                + session.selected + " durationMillis=" + audio.durationMillis()
                                + " peakDbfs=" + audio.peakDbfs() + " rmsDbfs=" + audio.rmsDbfs()
                                + " clippingRatio=" + audio.clippingRatio()
                                + " silenceRatio=" + audio.silenceRatio()
                                + " gaps=" + session.sequenceGaps + " result=READY");
                    } catch (RuntimeException persistFailure) {
                        failFinalization(session, generation, root(persistFailure),
                                "DRAFT_WRITE_FAILED");
                    }
                });
    }

    private void play(RecorderSession session, boolean saved) {
        requireCurrent(session);
        requireFinalRoutingOwnership();
        stopPlayback(session);
        long playbackGeneration = session.recordingGeneration.incrementAndGet();
        List<byte[]> frames;
        if (!saved && !session.finalFrames.isEmpty()
                && (session.state.get() == State.READY || session.state.get() == State.SAVED
                        || session.state.get() == State.FAILED)) {
            frames = session.finalFrames;
            beginPlayback(session, frames, playbackGeneration);
            return;
        }
        java.nio.file.Path path = presets.canonicalSamplePath(session.npcName, session.selected);
        if (!Files.isRegularFile(path) || !VoicePresetRepository.validWave(path)) {
            throw new IllegalStateException("No valid saved sample is available to play.");
        }
        session.lease = leases.acquireRecording(session.playerId, session.recordingId);
        session.state.set(State.PLAYING);
        session.message = "Preparing private playback...";
        session.error = false;
        ai.encodeSavedVoice(path).whenComplete((encoded, failure) -> {
            if (!currentGeneration(session, playbackGeneration)
                    || session.state.get() != State.PLAYING) return;
            if (failure != null) {
                releaseLease(session);
                session.state.set(State.FAILED);
                session.message = "Saved sample playback failed.";
                session.error = true;
            } else {
                try { beginPlayback(session, encoded, playbackGeneration); }
                catch (RuntimeException playbackFailure) {
                    releaseLease(session);
                    session.state.set(State.FAILED);
                    session.message = root(playbackFailure);
                    session.error = true;
                }
            }
        });
    }

    private void beginPlayback(RecorderSession session, List<byte[]> frames,
            long playbackGeneration) {
        if (!currentGeneration(session, playbackGeneration)) return;
        if (frames == null || frames.isEmpty()) throw new IllegalStateException(
                "No audio is available for playback.");
        if (session.lease == null || !session.lease.valid()) {
            session.lease = leases.acquireRecording(session.playerId, session.recordingId);
        }
        VoiceSpeaker speaker = voiceModule.openDirectVoice(Set.of(session.playerId));
        session.speaker = speaker;
        session.playback = speaker.play(frames);
        session.state.set(State.PLAYING);
        session.message = "Playing privately to you.";
        session.error = false;
        diagnostics.accept("NPC_AUTHORING_VOICE_PLAYBACK timestamp=" + Instant.now()
                + " recordingId=" + session.recordingId + " emotion=" + session.selected
                + " audience=CREATOR_ONLY frameCount=" + frames.size());
        session.playback.completion().whenComplete((ignored, failure) -> {
            if (session.speaker == speaker
                    && currentGeneration(session, playbackGeneration)) {
                speaker.close();
                session.speaker = null;
                session.playback = null;
                releaseLease(session);
                if (session.state.get() == State.PLAYING) {
                    session.state.set(session.draft == null ? State.SAVED : State.READY);
                    session.message = failure == null ? "Playback finished."
                            : "Playback stopped.";
                }
            }
        });
    }

    private void save(RecorderSession session) {
        requireCurrent(session);
        if (session.state.get() == State.PLAYING) stopPlayback(session);
        if (session.draft == null || session.state.get() != State.READY) {
            throw new IllegalStateException("Finalize a valid recording before saving.");
        }
        session.state.set(State.SAVING);
        try {
            var saved = persistence.save(session.npcName, session.stableNpcId,
                    session.draft, session.openRevision);
            session.draft = null;
            session.openRevision = saved.revision();
            session.state.set(State.SAVED);
            session.message = session.selected.label() + " saved. Voice readiness rescanned.";
            session.error = false;
            refreshSavedState(session);
        } catch (RuntimeException failure) {
            session.state.set(State.READY);
            session.message = root(failure);
            session.error = true;
            throw failure;
        }
    }

    private void deleteSaved(RecorderSession session) {
        requireCurrent(session);
        stopPlayback(session);
        persistence.deleteSaved(session.npcName, session.stableNpcId,
                session.selected, session.openRevision);
        session.openRevision = "MISSING";
        session.recordingGeneration.incrementAndGet();
        session.audio = null;
        session.finalFrames = List.of();
        session.state.set(State.IDLE);
        session.message = session.selected == VoiceSampleType.REFERENCE
                ? "Reference deleted. This NPC voice is not ready until Reference is recorded."
                : session.selected.label() + " deleted. Reference fallback is active.";
        session.error = session.selected == VoiceSampleType.REFERENCE;
        refreshSavedState(session);
    }

    private void failCapture(RecorderSession session, String message, String reason) {
        State prior = session.state.getAndSet(State.FAILED);
        if (prior != State.ARMED && prior != State.RECORDING) return;
        releaseLease(session);
        session.frames.clear();
        session.queuedBytes.set(0);
        session.message = message;
        session.error = true;
        diagnostics.accept("NPC_AUTHORING_VOICE_FAILED timestamp=" + Instant.now()
                + " recordingId=" + session.recordingId + " reason=" + reason);
    }

    private void failFinalization(RecorderSession session, long generation,
            String message, String reason) {
        if (!currentGeneration(session, generation)) return;
        session.state.set(State.FAILED);
        session.message = message;
        session.error = true;
        diagnostics.accept("NPC_AUTHORING_VOICE_FAILED timestamp=" + Instant.now()
                + " recordingId=" + session.recordingId + " reason=" + reason
                + " recordingGeneration=" + generation);
    }

    private void stopPlayback(RecorderSession session) {
        boolean invalidatesPendingPlayback = session.state.get() == State.PLAYING;
        if (invalidatesPendingPlayback) session.recordingGeneration.incrementAndGet();
        ClipPlayback playback = session.playback;
        VoiceSpeaker speaker = session.speaker;
        session.playback = null;
        session.speaker = null;
        if (playback != null && !playback.isDone()) playback.cancel();
        if (speaker != null) speaker.close();
        releaseLease(session);
        if (session.state.get() == State.PLAYING) {
            session.state.set(session.draft == null ? State.SAVED : State.READY);
            session.message = "Playback stopped.";
        }
    }

    private void discardDraft(RecorderSession session) {
        stopPlayback(session);
        persistence.discard(session.draft);
        session.draft = null;
        session.audio = null;
        session.finalFrames = List.of();
        session.frames.clear();
        session.queuedBytes.set(0);
    }

    private void cleanup(RecorderSession session, String reason) {
        if (!session.cleaned.compareAndSet(false, true)) return;
        byPlayer.remove(session.playerId, session);
        session.recordingGeneration.incrementAndGet();
        session.state.set(State.CANCELLED);
        stopPlayback(session);
        releaseLease(session);
        discardDraft(session);
        diagnostics.accept("NPC_AUTHORING_VOICE_CLEANUP timestamp=" + Instant.now()
                + " playerId=" + session.playerId + " npcStableId=" + session.stableNpcId
                + " reason=" + reason + " buffers=0 speakers=0 leases="
                + leases.activeRecordingLeases());
    }

    private void releaseLease(RecorderSession session) {
        VoiceCaptureLeaseManager.RecordingLease lease = session.lease;
        session.lease = null;
        if (lease != null) lease.close();
    }

    private void validateSequences(RecorderSession session, List<CopiedFrame> frames) {
        VoiceRecordingPolicy.SequenceStats result = VoiceRecordingPolicy.sequences(
                frames.stream().map(frame -> Short.toUnsignedInt(frame.sequence)).toList());
        session.sequenceGaps = result.gaps();
        session.duplicates = result.duplicates();
        session.outOfOrder = result.outOfOrder();
    }

    private static long elapsed(long startNanos, long nowNanos) {
        return startNanos <= 0 ? 0 : TimeUnit.NANOSECONDS.toMillis(nowNanos - startNanos);
    }

    private static int configuredInt(String name, int fallback, int minimum, int maximum) {
        int value = Integer.getInteger(name, fallback);
        return Math.max(minimum, Math.min(maximum, value));
    }

    private static long configuredLong(String name, long fallback, long minimum, long maximum) {
        long value = Long.getLong(name, fallback);
        return Math.max(minimum, Math.min(maximum, value));
    }

    private void requireCurrent(RecorderSession session) {
        if (closed.get() || session == null || session.cleaned.get()
                || byPlayer.get(session.playerId) != session) {
            throw new IllegalStateException("Voice Recorder session is no longer current.");
        }
    }

    /** Current 0.6.3 fail-closed privacy gate: no interceptor may run after our drop owner. */
    private void requireFinalRoutingOwnership() {
        try {
            var field = VoiceModule.class.getDeclaredField("playerVoiceInterceptors");
            field.setAccessible(true);
            Object value = field.get(voiceModule);
            if (!(value instanceof List<?> registrations) || registrations.isEmpty()) {
                throw new IllegalStateException("Voice interceptor order is unavailable.");
            }
            Object finalRegistration = registrations.getLast();
            var accessor = finalRegistration.getClass().getDeclaredMethod("interceptor");
            accessor.setAccessible(true);
            if (accessor.invoke(finalRegistration) != voiceInterceptor) {
                diagnostics.accept("NPC_AUTHORING_VOICE_PRIVACY_REJECTED timestamp="
                        + Instant.now() + " reason=RECORDER_NOT_FINAL_INTERCEPTOR");
                throw new IllegalStateException(
                        "Private recording cannot be guaranteed in the current voice pipeline.");
            }
        } catch (IllegalStateException expected) {
            throw expected;
        } catch (ReflectiveOperationException | RuntimeException failure) {
            diagnostics.accept("NPC_AUTHORING_VOICE_PRIVACY_REJECTED timestamp="
                    + Instant.now() + " reason=INTERCEPTOR_ORDER_UNAVAILABLE");
            throw new IllegalStateException(
                    "Private recording cannot be verified on this server build.", failure);
        }
    }

    private static boolean currentGeneration(RecorderSession session, long generation) {
        return !session.cleaned.get() && session.recordingGeneration.get() == generation;
    }

    private static String root(Throwable failure) {
        Throwable current = failure;
        while (current != null && current.getCause() != null) current = current.getCause();
        if (current == null) return "Voice operation failed.";
        String message = current.getMessage();
        return message == null || message.isBlank()
                ? current.getClass().getSimpleName() : message;
    }

    private static String noMicrophoneMessage(RecorderSession session) {
        return session.captureContract.speakWithoutPushToTalk()
                ? "No microphone audio was received by the server."
                : "No audio arrived: Hytale 0.6.3 cannot activate client microphone capture "
                        + "independently of its current Push-to-Talk mode.";
    }

    private void refreshSavedState(RecorderSession session) {
        var scan = presets.scan(session.npcName);
        EnumMap<VoiceSampleType, VoicePresetRepository.SampleState> saved =
                new EnumMap<>(VoiceSampleType.class);
        scan.samples().forEach((type, status) -> saved.put(type, status.state()));
        session.savedStates = Map.copyOf(saved);
        session.profileReady = scan.ready();
        diagnostics.accept("NPC_AUTHORING_VOICE_RESCAN timestamp=" + Instant.now()
                + " npcStableId=" + session.stableNpcId + " ready=" + scan.ready()
                + " found=" + saved.values().stream()
                        .filter(value -> value == VoicePresetRepository.SampleState.FOUND).count());
    }

    private void requestSavedWaveform(RecorderSession session) {
        if (ai == null || session.cleaned.get() || session.draft != null) return;
        java.nio.file.Path path = presets.canonicalSamplePath(
                session.npcName, session.selected);
        if (!Files.isRegularFile(path) || !VoicePresetRepository.validWave(path)) return;
        long generation = session.recordingGeneration.get();
        VoiceSampleType selected = session.selected;
        String revision = presets.sampleRevision(session.npcName, selected);
        ai.analyzeSavedVoice(path, WAVEFORM_BUCKETS).whenComplete((audio, failure) -> {
            if (!savedAnalysisIsCurrent(session.recordingGeneration.get(), generation,
                    session.selected, selected, session.draft == null,
                    presets.sampleRevision(session.npcName, selected), revision)) {
                diagnostics.accept("NPC_AUTHORING_VOICE_WAVEFORM_STALE_REJECTED timestamp="
                        + Instant.now() + " npcStableId=" + session.stableNpcId
                        + " emotion=" + selected + " generation=" + generation);
                return;
            }
            if (failure != null) {
                diagnostics.accept("NPC_AUTHORING_VOICE_WAVEFORM_ANALYSIS_FAILED timestamp="
                        + Instant.now() + " npcStableId=" + session.stableNpcId
                        + " emotion=" + selected + " reason=" + root(failure));
                return;
            }
            session.audio = audio;
            diagnostics.accept("NPC_AUTHORING_VOICE_WAVEFORM_READY timestamp=" + Instant.now()
                    + " npcStableId=" + session.stableNpcId + " emotion=" + selected
                    + " buckets=" + audio.waveform().size() + " source=SAVED_WAV");
        });
    }

    public static boolean savedAnalysisIsCurrent(long currentGeneration,
            long requestedGeneration, VoiceSampleType currentSelection,
            VoiceSampleType requestedSelection, boolean noDraft,
            String currentRevision, String requestedRevision) {
        return currentGeneration == requestedGeneration && currentSelection == requestedSelection
                && noDraft && java.util.Objects.equals(currentRevision, requestedRevision);
    }

    public void closeForPlayer(UUID playerId) {
        RecorderSession session = byPlayer.get(playerId);
        if (session != null) cleanup(session, "PLAYER_DISCONNECTED");
    }

    @Override public void close() {
        if (!closed.compareAndSet(false, true)) return;
        byPlayer.values().forEach(session -> cleanup(session, "PLUGIN_SHUTDOWN"));
        byPlayer.clear();
        scheduler.shutdownNow();
        if (interceptor != null && interceptor.isRegistered()) interceptor.unregister();
        leases.releaseAll();
    }

    private record CopiedFrame(long ordinal, short sequence, int timestamp, byte[] opus) { }

    private final class RecorderSession {
        private final UUID playerId;
        private final UUID authoringSessionId;
        private final UUID stableNpcId;
        private final String npcName;
        private final long pageGeneration;
        private final long editorGeneration;
        private final AtomicReference<State> state = new AtomicReference<>(State.IDLE);
        private final AtomicLong recordingGeneration = new AtomicLong();
        private final ArrayBlockingQueue<CopiedFrame> frames =
                new ArrayBlockingQueue<>(MAX_QUEUED_FRAMES);
        private final AtomicInteger queuedBytes = new AtomicInteger();
        private final AtomicLong captureOrdinal = new AtomicLong();
        private final AtomicInteger droppedFrames = new AtomicInteger();
        private final AtomicBoolean overflow = new AtomicBoolean();
        private final AtomicBoolean cleaned = new AtomicBoolean();
        private volatile VoiceSampleType selected = VoiceSampleType.REFERENCE;
        private volatile UUID recordingId = UUID.randomUUID();
        private volatile long armedNanos;
        private volatile long firstFrameNanos;
        private volatile int sequenceGaps;
        private volatile int duplicates;
        private volatile int outOfOrder;
        private volatile String message = "Select an emotion, then press Record.";
        private volatile boolean error;
        private volatile String openRevision;
        private volatile List<byte[]> finalFrames = List.of();
        private volatile VoiceDraftAudio audio;
        private volatile Draft draft;
        private volatile VoiceCaptureLeaseManager.RecordingLease lease;
        private volatile VoiceSpeaker speaker;
        private volatile ClipPlayback playback;
        private volatile Map<VoiceSampleType, VoicePresetRepository.SampleState> savedStates =
                Map.of();
        private volatile boolean profileReady;
        private final VoiceClientCaptureContract captureContract;

        private RecorderSession(UUID playerId, UUID authoringSessionId, UUID stableNpcId,
                String npcName, long pageGeneration, long editorGeneration,
                VoiceClientCaptureContract captureContract) {
            this.playerId = playerId;
            this.authoringSessionId = authoringSessionId;
            this.stableNpcId = stableNpcId;
            this.npcName = npcName;
            this.pageGeneration = pageGeneration;
            this.editorGeneration = editorGeneration;
            this.captureContract = captureContract;
            this.openRevision = presets.sampleRevision(npcName, selected);
            refreshSavedState(this);
        }
    }

    public final class Handle implements AutoCloseable {
        private final RecorderSession session;
        private Handle(RecorderSession session) { this.session = session; }
        public void select(VoiceSampleType type) { NpcVoiceRecordingService.this.select(session, type); }
        public void record() { NpcVoiceRecordingService.this.record(session); }
        public void stop() { NpcVoiceRecordingService.this.stop(session, false); }
        public void playDraft() { play(session, false); }
        public void playSaved() { play(session, true); }
        public void stopPlayback() { NpcVoiceRecordingService.this.stopPlayback(session); }
        public void recordAgain() {
            discardDraft(session);
            session.state.set(State.IDLE);
            NpcVoiceRecordingService.this.record(session);
        }
        public void deleteDraft() {
            discardDraft(session); session.recordingGeneration.incrementAndGet();
            session.state.set(State.IDLE); session.message = "Draft deleted."; session.error = false;
            requestSavedWaveform(session);
        }
        public void save() { NpcVoiceRecordingService.this.save(session); }
        public void deleteSaved() { NpcVoiceRecordingService.this.deleteSaved(session); }
        public VoiceSampleType selected() { return session.selected; }
        public long generation() { return session.recordingGeneration.get(); }
        public Snapshot snapshot() {
            State state = session.state.get();
            long elapsed = state == State.ARMED ? elapsed(session.armedNanos, System.nanoTime())
                    : state == State.RECORDING ? elapsed(session.firstFrameNanos, System.nanoTime())
                    : session.audio == null ? 0 : session.audio.durationMillis();
            VoiceDraftAudio audio = session.audio;
            return new Snapshot(state, session.selected, session.recordingGeneration.get(),
                    elapsed, MAX_DURATION_MILLIS, session.message, session.error,
                    audio == null ? List.of() : audio.waveform(),
                    audio == null ? 0 : audio.durationMillis(),
                    audio == null ? -120 : audio.peakDbfs(),
                    audio == null ? -120 : audio.rmsDbfs(),
                    audio == null ? 0 : audio.clippingRatio(),
                    audio == null ? 0 : audio.silenceRatio(),
                    session.sequenceGaps, session.droppedFrames.get(), session.savedStates,
                    session.profileReady, session.draft != null,
                    session.captureContract.display());
        }
        @Override public void close() { cleanup(session, "EDITOR_CLOSED"); }
    }

    public record Snapshot(State state, VoiceSampleType selected, long recordingGeneration,
            long elapsedMillis, long maximumMillis, String message, boolean error,
            List<Double> waveform, long durationMillis, double peakDbfs, double rmsDbfs,
            double clippingRatio, double silenceRatio, int sequenceGaps, int droppedFrames,
            Map<VoiceSampleType, VoicePresetRepository.SampleState> savedStates,
            boolean profileReady, boolean draftAvailable, String captureContract) { }
}
