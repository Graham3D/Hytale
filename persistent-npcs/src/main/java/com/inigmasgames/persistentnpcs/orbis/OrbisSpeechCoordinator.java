package com.inigmasgames.persistentnpcs.orbis;

import com.inigmasgames.persistentnpcs.cognition.ResponseLatencyStage;
import com.inigmasgames.persistentnpcs.cognition.ResponseLatencyTraceStore;
import com.inigmasgames.persistentnpcs.profile.NpcProfile;
import com.inigmasgames.persistentnpcs.profile.NpcProfileRegistry;
import com.inigmasgames.persistentnpcs.voice.NpcVoiceService;
import com.inigmasgames.persistentnpcs.voice.OpusClip;
import com.inigmasgames.persistentnpcs.voice.SpatialPlayback;
import com.inigmasgames.persistentnpcs.voice.SpatialPlaybackAdapter;
import com.inigmasgames.persistentnpcs.voice.TextToSpeechProvider;
import com.inigmasgames.persistentnpcs.voice.TtsTextNormalizer;
import com.inigmasgames.persistentnpcs.voice.VoiceRenderPlan;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * Orbis-owned bounded TTS admission and spatial playback coordinator.
 *
 * <p>The Chatterbox provider and Hytale pipeline are deliberately limited services:
 * neither can create response/chunk/playback ownership. Every provider/native callback
 * is re-serialized here and emitted immutably to {@link OrbisTurnCoordinator}.</p>
 */
public final class OrbisSpeechCoordinator implements AutoCloseable {
    public static final int DEFAULT_MAX_GLOBAL_QUEUE = 32;
    public static final int DEFAULT_MAX_PER_NPC_QUEUE = 8;
    private final ExecutorService control = Executors.newSingleThreadExecutor(task -> {
        Thread thread = new Thread(task, "orbis-speech-coordinator");
        thread.setDaemon(true);
        return thread;
    });
    private final NpcProfileRegistry profiles;
    private final NpcVoiceService voicePlans;
    private final TextToSpeechProvider tts;
    private final SpatialPlaybackAdapter playback;
    private final ResponseLatencyTraceStore latency;
    private final Consumer<String> log;
    private final OrbisResourceScheduler resources;
    private final int maximumGlobalQueue;
    private final int maximumPerNpcQueue;
    private final long synthesisTimeoutMillis;
    private final AtomicBoolean closed = new AtomicBoolean();

    // Accessed only on control.
    private final ArrayDeque<ChunkWork> synthesisQueue = new ArrayDeque<>();
    private final Map<ResponseId, ResponseWork> responses = new LinkedHashMap<>();
    private final Map<UUID, PlaybackLane> playbackLanes = new LinkedHashMap<>();
    private ChunkWork activeSynthesis;
    private volatile Snapshot snapshot = new Snapshot(0, 0, 0, "IDLE", "", "");

    public OrbisSpeechCoordinator(NpcProfileRegistry profiles, NpcVoiceService voicePlans,
            TextToSpeechProvider tts, SpatialPlaybackAdapter playback,
            ResponseLatencyTraceStore latency, Consumer<String> log) {
        this(profiles, voicePlans, tts, playback, latency, log,
                null, DEFAULT_MAX_GLOBAL_QUEUE, DEFAULT_MAX_PER_NPC_QUEUE, 30_000);
    }

    public OrbisSpeechCoordinator(NpcProfileRegistry profiles, NpcVoiceService voicePlans,
            TextToSpeechProvider tts, SpatialPlaybackAdapter playback,
            ResponseLatencyTraceStore latency, Consumer<String> log,
            OrbisResourceScheduler resources) {
        this(profiles, voicePlans, tts, playback, latency, log, resources,
                DEFAULT_MAX_GLOBAL_QUEUE, DEFAULT_MAX_PER_NPC_QUEUE, 30_000);
    }

    public OrbisSpeechCoordinator(NpcProfileRegistry profiles, NpcVoiceService voicePlans,
            TextToSpeechProvider tts, SpatialPlaybackAdapter playback,
            ResponseLatencyTraceStore latency, Consumer<String> log,
            int maximumGlobalQueue, int maximumPerNpcQueue,
            long synthesisTimeoutMillis) {
        this(profiles, voicePlans, tts, playback, latency, log, null,
                maximumGlobalQueue, maximumPerNpcQueue, synthesisTimeoutMillis);
    }

    public OrbisSpeechCoordinator(NpcProfileRegistry profiles, NpcVoiceService voicePlans,
            TextToSpeechProvider tts, SpatialPlaybackAdapter playback,
            ResponseLatencyTraceStore latency, Consumer<String> log,
            OrbisResourceScheduler resources, int maximumGlobalQueue,
            int maximumPerNpcQueue, long synthesisTimeoutMillis) {
        this.profiles = java.util.Objects.requireNonNull(profiles, "profiles");
        this.voicePlans = java.util.Objects.requireNonNull(voicePlans, "voicePlans");
        this.tts = java.util.Objects.requireNonNull(tts, "tts");
        this.playback = java.util.Objects.requireNonNull(playback, "playback");
        this.latency = latency == null ? new ResponseLatencyTraceStore() : latency;
        this.log = log == null ? ignored -> { } : log;
        this.resources = resources;
        this.maximumGlobalQueue = Math.max(1, maximumGlobalQueue);
        this.maximumPerNpcQueue = Math.max(1, maximumPerNpcQueue);
        this.synthesisTimeoutMillis = Math.max(100, synthesisTimeoutMillis);
    }

    public void submit(OrbisSpeechRequest request, Consumer<OrbisSpeechEvent> observer) {
        if (request == null || closed.get()) return;
        enqueue(() -> submitOnControl(request, observer, true));
    }

    /** Starts synthesis/playback before the final response is sealed. */
    public void submitStreaming(OrbisSpeechRequest request,
            Consumer<OrbisSpeechEvent> observer) {
        if (request == null || closed.get()) return;
        enqueue(() -> submitOnControl(request, observer, false));
    }

    public void append(ResponseId responseId, List<CanonicalSpeechChunk> chunks) {
        if (responseId == null || chunks == null || chunks.isEmpty() || closed.get()) return;
        enqueue(() -> appendOnControl(responseId, chunks));
    }

    public void seal(ResponseId responseId) {
        if (responseId == null || closed.get()) return;
        enqueue(() -> sealOnControl(responseId));
    }

    private void submitOnControl(OrbisSpeechRequest request,
            Consumer<OrbisSpeechEvent> observer, boolean sealed) {
        if (responses.containsKey(request.responseId())) {
            emitDirect(request, observer, OrbisSpeechEvent.Type.CALLBACK_REJECTED_STALE,
                    null, null, null, Map.of("stage", "duplicate-speech-admission"));
            return;
        }
        if (request.chunks().isEmpty()) {
            emitDirect(request, observer, OrbisSpeechEvent.Type.SPEECH_COMPLETE,
                    null, null, null, Map.of("chunkCount", "0"));
            return;
        }
        long npcQueued = synthesisQueue.stream().filter(value ->
                value.response.request.npcStableId().equals(request.npcStableId())).count();
        if (synthesisQueue.size() + (activeSynthesis == null ? 0 : 1)
                        + request.chunks().size() > maximumGlobalQueue
                || npcQueued + request.chunks().size() > maximumPerNpcQueue) {
            emitDirect(request, observer, OrbisSpeechEvent.Type.TTS_FAILED,
                    null, null, null, Map.of("reason", "orbis-tts-queue-capacity",
                            "globalLimit", Integer.toString(maximumGlobalQueue),
                            "perNpcLimit", Integer.toString(maximumPerNpcQueue)));
            return;
        }
        NpcProfile profile = profiles.byId(request.npcStableId()).orElse(null);
        if (profile == null) {
            emitDirect(request, observer, OrbisSpeechEvent.Type.TTS_FAILED,
                    null, null, null, Map.of("reason", "authored-profile-unavailable"));
            return;
        }
        ResponseWork response = new ResponseWork(request, profile,
                observer == null ? ignored -> { } : observer, sealed);
        responses.put(request.responseId(), response);
        enqueueChunks(response, request.chunks());
        updateSnapshot("SPEECH_QUEUED", response, null);
        pumpSynthesis();
    }

    private void appendOnControl(ResponseId responseId, List<CanonicalSpeechChunk> chunks) {
        ResponseWork response = responses.get(responseId);
        if (response == null || response.cancelled || response.sealed) return;
        long npcQueued = synthesisQueue.stream().filter(value ->
                value.response.request.npcStableId().equals(
                        response.request.npcStableId())).count();
        if (synthesisQueue.size() + (activeSynthesis == null ? 0 : 1) + chunks.size()
                        > maximumGlobalQueue
                || npcQueued + chunks.size() > maximumPerNpcQueue) {
            emitDirect(response.request, response.observer, OrbisSpeechEvent.Type.TTS_FAILED,
                    null, null, null, Map.of("reason", "orbis-streaming-tts-queue-capacity"));
            failResponse(response, "TTS_FAILED");
            return;
        }
        for (CanonicalSpeechChunk chunk : chunks) {
            if (chunk.index() != response.chunks.size()) {
                emitDirect(response.request, response.observer,
                        OrbisSpeechEvent.Type.CALLBACK_REJECTED_STALE, chunk.id(), null, null,
                        Map.of("stage", "streamed-chunk-order"));
                return;
            }
            response.chunks.add(chunk);
        }
        enqueueChunks(response, chunks);
        updateSnapshot("SPEECH_QUEUED", response, null);
        pumpSynthesis();
    }

    private void sealOnControl(ResponseId responseId) {
        ResponseWork response = responses.get(responseId);
        if (response == null || response.cancelled) return;
        response.sealed = true;
        if (response.completedChunks >= response.chunks.size() && response.lastWork != null) {
            completeResponse(response, response.lastWork);
        }
    }

    private void enqueueChunks(ResponseWork response, List<CanonicalSpeechChunk> chunks) {
        for (CanonicalSpeechChunk chunk : chunks) {
            TtsRequestId ttsRequestId = TtsRequestId.create();
            PlaybackId playbackId = PlaybackId.create();
            String performanceText = TtsTextNormalizer.performanceText(chunk.text(),
                    chunk.vocalState(), chunk.index() == 0);
            ChunkWork work = new ChunkWork(response, chunk, ttsRequestId, playbackId,
                    performanceText, System.nanoTime());
            response.lastWork = work;
            synthesisQueue.addLast(work);
            emit(work, OrbisSpeechEvent.Type.SPEECH_QUEUED, Map.of(
                    "chunkIndex", Integer.toString(chunk.index()),
                    "canonicalText", chunk.text(),
                    "ttsText", performanceText,
                    "queueDepth", Integer.toString(synthesisQueue.size()),
                    "emotion", chunk.vocalState().emotion().name(),
                    "paralinguisticEvent", chunk.index() == 0
                            ? chunk.vocalState().paralinguisticEvent()
                                    .map(value -> value.tag()).orElse("") : "",
                    "projection", response.request.projection().name()));
        }
    }

    private void pumpSynthesis() {
        if (activeSynthesis != null || closed.get()) return;
        ChunkWork work;
        do {
            work = synthesisQueue.pollFirst();
        } while (work != null && work.response.cancelled);
        if (work == null) {
            updateSnapshot("IDLE", null, null);
            return;
        }
        final ChunkWork selectedWork = work;
        activeSynthesis = selectedWork;
        selectedWork.providerQueueWaitMillis = elapsed(selectedWork.queuedAtNanos);
        latency.recordDuration(selectedWork.response.request.responseId().value(),
                ResponseLatencyStage.TTS_QUEUE_WAIT, selectedWork.providerQueueWaitMillis);
        if (resources == null) {
            beginSynthesis(selectedWork, null);
            return;
        }
        ResourcePriority priority = selectedWork.chunk.index() == 0
                ? ResourcePriority.HIGH : ResourcePriority.NORMAL;
        OrbisResourceRequest resourceRequest = new OrbisResourceRequest(
                selectedWork.ttsRequestId.value(), ResourceWorkload.TTS,
                priority, tts, true, synthesisTimeoutMillis);
        resources.admit(resourceRequest, event -> enqueue(() ->
                        resourceProgress(selectedWork, event)))
                .whenComplete((lease, failure) -> enqueue(() -> {
                    if (failure != null) {
                        synthesisCompleted(selectedWork, null, failure);
                    } else {
                        beginSynthesis(selectedWork, lease);
                    }
                }));
    }

    private void beginSynthesis(ChunkWork selectedWork,
            OrbisResourceScheduler.Lease lease) {
        if (activeSynthesis != selectedWork || selectedWork.response.cancelled) {
            if (lease != null) lease.close();
            emit(selectedWork, OrbisSpeechEvent.Type.CALLBACK_REJECTED_STALE,
                    Map.of("stage", "resource-admission"));
            if (activeSynthesis == selectedWork) {
                activeSynthesis = null;
                pumpSynthesis();
            }
            return;
        }
        selectedWork.resourceLease = lease;
        selectedWork.resourceAdmissionWaitMillis = lease == null
                ? 0 : lease.admissionWaitMillis();
        selectedWork.synthesisStartedNanos = System.nanoTime();
        VoiceRenderPlan plan;
        try {
            plan = voicePlans.plan(selectedWork.response.profile,
                    selectedWork.chunk.vocalState(),
                    selectedWork.response.request.projection());
        } catch (RuntimeException failure) {
            synthesisCompleted(selectedWork, null, failure);
            return;
        }
        selectedWork.plan = plan;
        latency.mark(selectedWork.response.request.responseId().value(),
                ResponseLatencyStage.TTS_SYNTHESIS_START);
        emit(selectedWork, OrbisSpeechEvent.Type.TTS_SYNTHESIZING, Map.of(
                "provider", tts.providerId(),
                "backend", tts.backendDescription(),
                "queueWaitMs", Long.toString(selectedWork.providerQueueWaitMillis),
                "resourceAdmissionWaitMs", Long.toString(
                        selectedWork.resourceAdmissionWaitMillis),
                "executionPlacement", lease == null ? "UNKNOWN" : lease.placement().name(),
                "queueDepth", Integer.toString(synthesisQueue.size()),
                "voicePreset", plan.voicePresetId(),
                "emotion", plan.vocalState().emotion().name(),
                "reference", plan.referenceAudio().map(java.nio.file.Path::toString)
                        .orElse("CHATTERBOX_BUILT_IN_FALLBACK")));
        updateSnapshot("TTS_SYNTHESIZING", selectedWork.response, selectedWork);
        CompletableFuture<OpusClip> future;
        try {
            future = tts.synthesize(selectedWork.ttsRequestId.value(),
                    selectedWork.response.request.responseId().value(), plan,
                    selectedWork.performanceText)
                    .orTimeout(synthesisTimeoutMillis, TimeUnit.MILLISECONDS);
        } catch (RuntimeException failure) {
            synthesisCompleted(selectedWork, null, failure);
            return;
        }
        future.whenComplete((clip, failure) -> enqueue(() ->
                synthesisCompleted(selectedWork, clip, failure)));
    }

    private void resourceProgress(ChunkWork work, OrbisResourceEvent event) {
        LinkedHashMap<String, String> facts = new LinkedHashMap<>(event.facts());
        facts.put("resourceEventType", event.type().name());
        facts.put("resourceRequestId", event.requestId().toString());
        facts.put("placement", event.placement().name());
        facts.put("admissionWaitMs", Long.toString(event.admissionWaitMillis()));
        emit(work, OrbisSpeechEvent.Type.RESOURCE_SCHEDULE_EVENT, Map.copyOf(facts));
    }

    private void synthesisCompleted(ChunkWork work, OpusClip clip, Throwable failure) {
        if (activeSynthesis != work) {
            emit(work, OrbisSpeechEvent.Type.CALLBACK_REJECTED_STALE,
                    Map.of("stage", "synthesis-completion"));
            return;
        }
        activeSynthesis = null;
        if (work.resourceLease != null) {
            work.resourceLease.close();
            work.resourceLease = null;
        }
        if (work.response.cancelled || responses.get(work.response.request.responseId())
                != work.response) {
            emit(work, OrbisSpeechEvent.Type.TTS_RESULT_DISCARDED_STALE,
                    Map.of("stage", "synthesized-unplayed"));
            pumpSynthesis();
            return;
        }
        if (failure != null || clip == null || clip.frames().isEmpty()) {
            Throwable cause = rootCause(failure);
            boolean timeout = cause instanceof java.util.concurrent.TimeoutException;
            boolean admissionFailed = work.synthesisStartedNanos == 0;
            emit(work, timeout ? OrbisSpeechEvent.Type.TIMED_OUT
                            : OrbisSpeechEvent.Type.TTS_FAILED,
                    Map.of("stage", admissionFailed ? "resource-admission" : "tts",
                            "reason", root(failure),
                            "synthesisWallMs", admissionFailed ? "0"
                                    : Long.toString(elapsed(work.synthesisStartedNanos))));
            failResponse(work.response, admissionFailed ? "RESOURCE_ADMISSION_FAILED"
                    : timeout ? "PROVIDER_TIMEOUT" : "PROVIDER_FAILURE");
            pumpSynthesis();
            return;
        }
        work.clip = clip;
        long generatedAudioMillis = clip.frames().size() * 20L;
        double rtf = generatedAudioMillis <= 0 ? 0.0
                : (double) clip.ttsMillis() / generatedAudioMillis;
        latency.recordDuration(work.response.request.responseId().value(),
                ResponseLatencyStage.TTS_CONDITIONING_LOOKUP, clip.conditioningMillis());
        latency.recordDuration(work.response.request.responseId().value(),
                ResponseLatencyStage.TTS_WORKER_QUEUE_WAIT, clip.workerQueueMillis());
        latency.recordDuration(work.response.request.responseId().value(),
                ResponseLatencyStage.TTS_SYNTHESIS_DURATION, clip.ttsMillis());
        latency.mark(work.response.request.responseId().value(),
                ResponseLatencyStage.FIRST_PCM_OPUS_AVAILABILITY);
        emit(work, OrbisSpeechEvent.Type.AUDIO_READY, Map.ofEntries(
                Map.entry("provider", tts.providerId()),
                Map.entry("conditioningMs", Long.toString(clip.conditioningMillis())),
                Map.entry("conditioningCache", clip.conditioningCached() ? "HIT" : "MISS"),
                Map.entry("workerQueueWaitMs", Long.toString(clip.workerQueueMillis())),
                Map.entry("synthesisMs", Long.toString(clip.ttsMillis())),
                Map.entry("encodeMs", Long.toString(clip.encodeMillis())),
                Map.entry("generatedAudioMs", Long.toString(generatedAudioMillis)),
                Map.entry("realTimeFactor", String.format(java.util.Locale.ROOT, "%.3f", rtf)),
                Map.entry("opusFrames", Integer.toString(clip.frames().size())),
                Map.entry("device", clip.device()),
                Map.entry("cudaAllocatedMb", Long.toString(clip.cudaAllocatedMegabytes())),
                Map.entry("cudaReservedMb", Long.toString(clip.cudaReservedMegabytes())),
                Map.entry("cudaPeakAllocatedMb",
                        Long.toString(clip.cudaPeakAllocatedMegabytes())),
                Map.entry("cudaPeakReservedMb",
                        Long.toString(clip.cudaPeakReservedMegabytes())),
                Map.entry("workerPid", Long.toString(clip.workerPid())),
                Map.entry("modelResident", Boolean.toString(clip.modelResident())),
                Map.entry("conditioningCacheEntries",
                        Integer.toString(clip.conditioningCacheEntries())),
                Map.entry("modelLoadCount", Integer.toString(clip.modelLoadCount()))));
        enqueuePlayback(work);
        pumpSynthesis();
    }

    private void enqueuePlayback(ChunkWork work) {
        if (work.response.cancelled) return;
        PlaybackLane lane = playbackLanes.computeIfAbsent(
                work.response.request.npcStableId(), ignored -> new PlaybackLane());
        lane.queue.addLast(work);
        emit(work, OrbisSpeechEvent.Type.PLAYBACK_QUEUED, Map.of(
                "playbackQueueDepth", Integer.toString(lane.queue.size()),
                "opusFrames", Integer.toString(work.clip.frames().size())));
        updateSnapshot("PLAYBACK_QUEUED", work.response, work);
        pumpPlayback(work.response.request.npcStableId(), lane);
    }

    private void pumpPlayback(UUID npcId, PlaybackLane lane) {
        if (lane.active != null || closed.get()) return;
        ChunkWork work;
        do {
            work = lane.queue.pollFirst();
        } while (work != null && work.response.cancelled);
        if (work == null) {
            if (lane.queue.isEmpty()) playbackLanes.remove(npcId, lane);
            return;
        }
        final ChunkWork selectedWork = work;
        try {
            SpatialPlayback handle = playback.playOrbis(npcId,
                    selectedWork.playbackId, selectedWork.clip.frames());
            lane.active = selectedWork;
            selectedWork.playbackHandle = handle;
            selectedWork.playbackStartedNanos = System.nanoTime();
            latency.mark(selectedWork.response.request.responseId().value(),
                    ResponseLatencyStage.FIRST_AUDIBLE_HYTALE_VOICE_FRAME);
            emit(selectedWork, OrbisSpeechEvent.Type.SPEAKING, Map.of(
                    "entitySpeaker", handle.speakerId().toString(),
                    "firstOpusSubmitted", "true",
                    "decisionToFirstAudioMs", Long.toString(Math.max(0,
                            java.time.Duration.between(
                                    selectedWork.response.request.decisionCommittedAt(),
                                    Instant.now()).toMillis()))));
            updateSnapshot("SPEAKING", selectedWork.response, selectedWork);
            handle.completion().whenComplete((ignored, failure) -> enqueue(() ->
                    playbackCompleted(npcId, lane, selectedWork, failure)));
        } catch (RuntimeException failure) {
            emit(selectedWork, OrbisSpeechEvent.Type.PLAYBACK_FAILED,
                    Map.of("reason", root(failure), "stage", "playback-submit"));
            failResponse(selectedWork.response, "PLAYBACK_FAILED");
            pumpPlayback(npcId, lane);
        }
    }

    private void playbackCompleted(UUID npcId, PlaybackLane lane, ChunkWork work,
            Throwable failure) {
        if (lane.active != work) {
            emit(work, OrbisSpeechEvent.Type.CALLBACK_REJECTED_STALE,
                    Map.of("stage", "playback-completion"));
            return;
        }
        lane.active = null;
        work.playbackHandle = null;
        if (work.response.cancelled || responses.get(work.response.request.responseId())
                != work.response) {
            emit(work, OrbisSpeechEvent.Type.CALLBACK_REJECTED_STALE,
                    Map.of("stage", "cancelled-playback-completion"));
            pumpPlayback(npcId, lane);
            return;
        }
        if (failure != null) {
            emit(work, OrbisSpeechEvent.Type.PLAYBACK_FAILED, Map.of(
                    "reason", root(failure), "stage", "playback-completion"));
            failResponse(work.response, "PLAYBACK_FAILED");
            pumpPlayback(npcId, lane);
            return;
        }
        work.response.completedChunks++;
        work.response.deliveredChunkIds.add(work.chunk.id());
        emit(work, OrbisSpeechEvent.Type.CHUNK_PLAYBACK_COMPLETE, Map.of(
                "playbackDurationMs", Long.toString(elapsed(work.playbackStartedNanos)),
                "completedChunks", Integer.toString(work.response.completedChunks),
                "chunkCount", Integer.toString(work.response.chunks.size())));
        if (work.response.sealed
                && work.response.completedChunks >= work.response.chunks.size()) {
            completeResponse(work.response, work);
        }
        pumpPlayback(npcId, lane);
    }

    private void completeResponse(ResponseWork response, ChunkWork work) {
        if (!responses.remove(response.request.responseId(), response)) return;
        latency.complete(response.request.responseId().value());
        emit(work, OrbisSpeechEvent.Type.SPEECH_COMPLETE, Map.of(
                "chunkCount", Integer.toString(response.completedChunks),
                "completion", "ClipPlayback.completion"));
        updateSnapshot("SPEECH_COMPLETE", response, work);
    }

    public void cancel(ResponseId responseId, CancellationReason reason) {
        cancel(responseId, reason, 0);
    }

    public void cancel(ResponseId responseId, CancellationReason reason,
            long confirmedSpeechNanos) {
        if (responseId == null || closed.get()) return;
        enqueue(() -> cancelOnControl(responseId,
                reason == null ? CancellationReason.ADMIN_CANCEL : reason,
                confirmedSpeechNanos));
    }

    private void cancelOnControl(ResponseId responseId, CancellationReason reason) {
        cancelOnControl(responseId, reason, 0);
    }

    private void cancelOnControl(ResponseId responseId, CancellationReason reason,
            long confirmedSpeechNanos) {
        ResponseWork response = responses.remove(responseId);
        if (response == null || response.cancelled) return;
        response.cancelled = true;
        if (resources != null) {
            synthesisQueue.stream().filter(work -> work.response == response)
                    .forEach(work -> resources.cancel(work.ttsRequestId.value(), reason.name()));
            if (activeSynthesis != null && activeSynthesis.response == response) {
                resources.cancel(activeSynthesis.ttsRequestId.value(), reason.name());
            }
        }
        int queuedTts = (int) synthesisQueue.stream()
                .filter(work -> work.response == response).count();
        synthesisQueue.removeIf(work -> work.response == response);
        try { tts.cancel(responseId.value()); }
        catch (RuntimeException failure) {
            log.accept("Orbis TTS cancellation failed response=" + responseId.value()
                    + " reason=" + root(failure));
        }
        emitDirect(response.request, response.observer,
                OrbisSpeechEvent.Type.TTS_CANCELLED, null, null, null,
                Map.of("reason", reason.name(), "queuedChunksRemoved",
                        Integer.toString(queuedTts), "activeSynthesisMarkedStale",
                        Boolean.toString(activeSynthesis != null
                                && activeSynthesis.response == response)));
        PlaybackLane lane = playbackLanes.get(response.request.npcStableId());
        ChunkWork partial = null;
        int queuedPlayback = 0;
        if (lane != null) {
            queuedPlayback = (int) lane.queue.stream()
                    .filter(work -> work.response == response).count();
            lane.queue.removeIf(work -> work.response == response);
            if (lane.active != null && lane.active.response == response
                    && lane.active.playbackHandle != null) {
                partial = lane.active;
                SpatialPlayback activeHandle = lane.active.playbackHandle;
                lane.active = null; // release immediately; late completion is stale by identity.
                try { activeHandle.cancel(); }
                catch (RuntimeException failure) {
                    log.accept("Orbis native playback cancellation failed response="
                            + responseId.value() + " reason=" + root(failure));
                }
                long stopMs = confirmedSpeechNanos <= 0 ? -1
                        : TimeUnit.NANOSECONDS.toMillis(Math.max(0,
                                System.nanoTime() - confirmedSpeechNanos));
                emit(partial, OrbisSpeechEvent.Type.PLAYBACK_INTERRUPTED, Map.of(
                        "reason", reason.name(),
                        "nativeMechanism", "ClipPlayback.cancel",
                        "confirmedSpeechToPlaybackStopMs", Long.toString(stopMs)));
                pumpPlayback(response.request.npcStableId(), lane);
            }
        }
        String delivered = response.deliveredChunkIds.stream()
                .map(id -> id.value().toString()).collect(
                        java.util.stream.Collectors.joining(","));
        String partialId = partial == null ? "" : partial.chunk.id().value().toString();
        ChunkWork partialChunk = partial;
        java.util.Set<SpeechChunkId> unheard = new java.util.LinkedHashSet<>();
        response.chunks.forEach(chunk -> {
            if (!response.deliveredChunkIds.contains(chunk.id())
                    && (partialChunk == null || !partialChunk.chunk.id().equals(chunk.id()))) {
                unheard.add(chunk.id());
            }
        });
        String undelivered = unheard.stream().map(id -> id.value().toString())
                .collect(java.util.stream.Collectors.joining(","));
        emitDirect(response.request, response.observer,
                reason == CancellationReason.USER_BARGE_IN
                        ? OrbisSpeechEvent.Type.SPEECH_INTERRUPTED
                        : OrbisSpeechEvent.Type.SPEECH_CANCELLED,
                partial == null ? null : partial.chunk.id(),
                partial == null ? null : partial.ttsRequestId,
                partial == null ? null : partial.playbackId,
                Map.ofEntries(
                        Map.entry("reason", reason.name()),
                        Map.entry("deliveredChunkIds", delivered),
                        Map.entry("partialChunkId", partialId),
                        Map.entry("undeliveredChunkIds", undelivered),
                        Map.entry("deliveredChunkCount", Integer.toString(
                                response.deliveredChunkIds.size())),
                        Map.entry("partialChunkCount", partial == null ? "0" : "1"),
                        Map.entry("undeliveredChunkCount", Integer.toString(unheard.size())),
                        Map.entry("queuedTtsRemoved", Integer.toString(queuedTts)),
                        Map.entry("queuedPlaybackRemoved", Integer.toString(queuedPlayback))));
        updateSnapshot(reason == CancellationReason.USER_BARGE_IN
                ? "SPEECH_INTERRUPTED" : "SPEECH_CANCELLED", response, partial);
    }

    public void npcUnloaded(UUID npcId, CancellationReason reason) {
        if (npcId == null || closed.get()) return;
        enqueue(() -> List.copyOf(responses.values()).stream()
                .filter(value -> npcId.equals(value.request.npcStableId()))
                .forEach(value -> cancelOnControl(value.request.responseId(), reason)));
    }

    private void failResponse(ResponseWork response, String reason) {
        if (response.cancelled) return;
        response.cancelled = true;
        responses.remove(response.request.responseId(), response);
        if (resources != null) {
            synthesisQueue.stream().filter(value -> value.response == response)
                    .forEach(value -> resources.cancel(value.ttsRequestId.value(), reason));
            if (activeSynthesis != null && activeSynthesis.response == response) {
                resources.cancel(activeSynthesis.ttsRequestId.value(), reason);
            }
        }
        synthesisQueue.removeIf(value -> value.response == response);
        tts.cancel(response.request.responseId().value());
        PlaybackLane lane = playbackLanes.get(response.request.npcStableId());
        if (lane != null) lane.queue.removeIf(value -> value.response == response);
        updateSnapshot(reason, response, null);
    }

    private void emit(ChunkWork work, OrbisSpeechEvent.Type type,
            Map<String, String> facts) {
        emitDirect(work.response.request, work.response.observer, type,
                work.chunk.id(), work.ttsRequestId, work.playbackId, facts);
    }

    private static void emitDirect(OrbisSpeechRequest request,
            Consumer<OrbisSpeechEvent> observer, OrbisSpeechEvent.Type type,
            SpeechChunkId chunkId, TtsRequestId ttsId, PlaybackId playbackId,
            Map<String, String> facts) {
        if (observer == null) return;
        try {
            observer.accept(new OrbisSpeechEvent(type, request.turnId(), request.branchId(),
                    request.responseId(), request.npcStableId(), request.branchEpoch(),
                    chunkId, ttsId, playbackId, Instant.now(), facts));
        } catch (RuntimeException ignored) { }
    }

    private void updateSnapshot(String state, ResponseWork response, ChunkWork work) {
        int playbackDepth = playbackLanes.values().stream()
                .mapToInt(value -> value.queue.size() + (value.active == null ? 0 : 1)).sum();
        snapshot = new Snapshot(synthesisQueue.size(), activeSynthesis == null ? 0 : 1,
                playbackDepth, state,
                response == null ? "" : response.request.responseId().value().toString(),
                work == null ? "" : work.ttsRequestId.value().toString());
    }

    public Snapshot snapshot() { return snapshot; }

    private void enqueue(Runnable task) {
        if (closed.get()) return;
        try { control.execute(task); } catch (RuntimeException ignored) { }
    }

    @Override public void close() {
        if (!closed.compareAndSet(false, true)) return;
        try {
            control.execute(() -> {
                for (ResponseWork response : List.copyOf(responses.values())) {
                    cancelOnControl(response.request.responseId(),
                            CancellationReason.SERVER_SHUTDOWN);
                }
            });
        } catch (RuntimeException ignored) { }
        control.shutdown();
        try { control.awaitTermination(2, TimeUnit.SECONDS); }
        catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); }
        control.shutdownNow();
    }

    private static long elapsed(long startedNanos) {
        return TimeUnit.NANOSECONDS.toMillis(Math.max(0, System.nanoTime() - startedNanos));
    }

    private static String root(Throwable failure) {
        Throwable value = rootCause(failure);
        return value == null ? "unknown" : value.getClass().getSimpleName()
                + (value.getMessage() == null ? "" : ": " + value.getMessage());
    }

    private static Throwable rootCause(Throwable failure) {
        Throwable value = failure;
        while (value != null && value.getCause() != null) value = value.getCause();
        return value;
    }

    public record Snapshot(int synthesisQueueDepth, int activeSynthesis,
            int playbackQueueDepth, String state, String responseId,
            String ttsRequestId) { }

    private static final class ResponseWork {
        private final OrbisSpeechRequest request;
        private final NpcProfile profile;
        private final Consumer<OrbisSpeechEvent> observer;
        private final java.util.ArrayList<CanonicalSpeechChunk> chunks;
        private int completedChunks;
        private boolean cancelled;
        private boolean sealed;
        private ChunkWork lastWork;
        private final java.util.Set<SpeechChunkId> deliveredChunkIds =
                new java.util.LinkedHashSet<>();

        private ResponseWork(OrbisSpeechRequest request, NpcProfile profile,
                Consumer<OrbisSpeechEvent> observer, boolean sealed) {
            this.request = request;
            this.profile = profile;
            this.observer = observer;
            this.chunks = new java.util.ArrayList<>(request.chunks());
            this.sealed = sealed;
        }
    }

    private static final class ChunkWork {
        private final ResponseWork response;
        private final CanonicalSpeechChunk chunk;
        private final TtsRequestId ttsRequestId;
        private final PlaybackId playbackId;
        private final String performanceText;
        private final long queuedAtNanos;
        private long synthesisStartedNanos;
        private long providerQueueWaitMillis;
        private long resourceAdmissionWaitMillis;
        private long playbackStartedNanos;
        private VoiceRenderPlan plan;
        private OpusClip clip;
        private SpatialPlayback playbackHandle;
        private OrbisResourceScheduler.Lease resourceLease;

        private ChunkWork(ResponseWork response, CanonicalSpeechChunk chunk,
                TtsRequestId ttsRequestId, PlaybackId playbackId,
                String performanceText, long queuedAtNanos) {
            this.response = response;
            this.chunk = chunk;
            this.ttsRequestId = ttsRequestId;
            this.playbackId = playbackId;
            this.performanceText = performanceText;
            this.queuedAtNanos = queuedAtNanos;
        }
    }

    private static final class PlaybackLane {
        private final ArrayDeque<ChunkWork> queue = new ArrayDeque<>();
        private ChunkWork active;
    }
}
