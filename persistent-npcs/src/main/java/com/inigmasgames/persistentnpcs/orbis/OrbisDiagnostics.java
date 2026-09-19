package com.inigmasgames.persistentnpcs.orbis;

import java.util.ArrayDeque;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/** Observer-only bounded event history used by native UI and explicit trace sessions. */
public final class OrbisDiagnostics {
    private final int capacity;
    private final ArrayDeque<OrbisEvent> events = new ArrayDeque<>();
    private final CopyOnWriteArrayList<Consumer<OrbisEvent>> observers =
            new CopyOnWriteArrayList<>();
    private final Map<UUID, TurnId> activeTurnByNpc = new LinkedHashMap<>();

    public OrbisDiagnostics() { this(512); }
    public OrbisDiagnostics(int capacity) { this.capacity = Math.max(32, capacity); }

    public synchronized void observe(OrbisEvent event) {
        events.addLast(event);
        while (events.size() > capacity) events.removeFirst();
        String npcValue = event.facts().get("npcId");
        if (npcValue != null && event.turnId() != null) {
            try {
                UUID npcId = UUID.fromString(npcValue);
                if (event.type() == OrbisEventType.BRANCH_DISPATCHED) {
                    activeTurnByNpc.put(npcId, event.turnId());
                } else if (event.type() == OrbisEventType.BRANCH_CANCELLED
                        || event.type() == OrbisEventType.BRANCH_COMPLETED
                        || event.type() == OrbisEventType.TURN_COMPLETED
                        || event.type() == OrbisEventType.TURN_FAILED
                        || event.type() == OrbisEventType.TURN_CANCELLED) {
                    activeTurnByNpc.remove(npcId, event.turnId());
                }
            } catch (IllegalArgumentException ignored) { }
        }
        for (Consumer<OrbisEvent> observer : observers) {
            try { observer.accept(event); } catch (RuntimeException ignored) { }
        }
    }

    public void subscribe(Consumer<OrbisEvent> observer) {
        if (observer != null) observers.add(observer);
    }

    public void unsubscribe(Consumer<OrbisEvent> observer) {
        observers.remove(observer);
    }

    public synchronized List<OrbisEvent> latest() { return List.copyOf(events); }

    public synchronized String latestSummary() {
        OrbisEvent event = events.peekLast();
        if (event == null) return "Orbis: idle (no turns observed)";
        return "Orbis: seq=" + event.sequence() + " type=" + event.type()
                + " turn=" + (event.turnId() == null ? "none" : event.turnId().value())
                + " epoch=" + event.epoch()
                + (event.facts().isEmpty() ? "" : " facts=" + event.facts());
    }

    public synchronized String latestSummary(UUID npcId) {
        if (npcId == null) return latestSummary();
        OrbisEvent event = events.reversed().stream().filter(value ->
                npcId.toString().equals(value.facts().get("npcId"))).findFirst().orElse(null);
        if (event == null) return "Orbis: no branch observed for this NPC";
        return "seq=" + event.sequence() + " type=" + event.type()
                + "\nturnId=" + event.turnId().value()
                + "\nbranchId=" + (event.branchId() == null ? "none" : event.branchId().value())
                + "\nresponseId=" + (event.responseId() == null ? "none" : event.responseId().value())
                + "\nepoch=" + event.epoch() + "\nfacts=" + event.facts();
    }

    public synchronized String turnSummary(UUID npcId) {
        if (npcId == null) return latestSummary();
        String stableId = npcId.toString();
        OrbisEvent npcEvent = events.reversed().stream().filter(value ->
                stableId.equals(value.facts().get("npcId"))).findFirst().orElse(null);
        if (npcEvent == null || npcEvent.turnId() == null) {
            return "activeTurnId=none\nlifecycle=IDLE\nNo Orbis branch observed for this NPC.";
        }
        TurnId turnId = npcEvent.turnId();
        List<OrbisEvent> turnEvents = events.stream()
                .filter(value -> turnId.equals(value.turnId())).toList();
        OrbisEvent last = turnEvents.getLast();
        String player = turnEvents.stream().map(value -> value.facts().get("playerId"))
                .filter(java.util.Objects::nonNull).findFirst().orElse("unknown");
        String listeners = names(turnEvents, OrbisEventType.LISTENER_HEARD);
        String direct = turnEvents.stream()
                .filter(value -> value.type() == OrbisEventType.LISTENER_HEARD
                        && "true".equals(value.facts().get("directAddress")))
                .map(value -> value.facts().getOrDefault("npc", "unknown"))
                .distinct().toList().toString();
        String candidates = names(turnEvents, OrbisEventType.RESPONSE_CANDIDATE);
        String owners = names(turnEvents, OrbisEventType.RESPONSE_OWNER_SELECTED);
        String stt = turnEvents.stream().filter(value ->
                value.type() == OrbisEventType.STT_SELECTED).findFirst()
                .map(value -> value.facts().getOrDefault("provider", "unknown")
                        + " / " + value.facts().getOrDefault("backend", "unknown")
                        + " request=" + (value.providerRequestId() == null ? "none"
                                : value.providerRequestId().value()))
                .orElse("not selected");
        String llm = turnEvents.stream().filter(value ->
                value.type() == OrbisEventType.BRANCH_CREATED
                        && stableId.equals(value.facts().get("npcId"))).findFirst()
                .map(value -> value.facts().getOrDefault("provider", "unknown")
                        + " / " + value.facts().getOrDefault("model", "unknown")
                        + " request=" + (value.providerRequestId() == null ? "none"
                                : value.providerRequestId().value()))
                .orElse("not selected");
        String cancellation = turnEvents.reversed().stream().filter(value ->
                value.type() == OrbisEventType.TURN_CANCELLED
                        || value.type() == OrbisEventType.BRANCH_CANCELLED
                        || value.type() == OrbisEventType.TURN_FAILED).findFirst()
                .map(value -> value.facts().getOrDefault("reason", value.type().name()))
                .orElse("none");
        String timings = turnEvents.stream().filter(value ->
                value.type() == OrbisEventType.CAPTURE_FINALIZED
                        || value.type() == OrbisEventType.STT_COMPLETED
                        || value.type() == OrbisEventType.AUDIENCE_RESOLVED)
                .map(value -> value.type() + "=" + value.facts()).toList().toString();
        List<OrbisEvent> branchEvents = turnEvents.stream().filter(value ->
                stableId.equals(value.facts().get("npcId"))).toList();
        OrbisEvent context = latest(branchEvents, OrbisEventType.LLM_DISPATCHED);
        OrbisEvent executionPlan = latest(branchEvents, OrbisEventType.TURN_PLAN_COMPILED);
        OrbisEvent contractBudget = latest(branchEvents,
                OrbisEventType.CONTRACT_BUDGET_PLANNED);
        OrbisEvent contractState = branchEvents.reversed().stream().filter(value ->
                value.type() == OrbisEventType.CONTRACT_VALID
                        || value.type() == OrbisEventType.CONTRACT_INVALID
                        || value.type() == OrbisEventType.TRUNCATED_OUTPUT)
                .findFirst().orElse(null);
        OrbisEvent recovery = branchEvents.reversed().stream().filter(value ->
                value.type() == OrbisEventType.RECOVERY_ATTEMPTED
                        || value.type() == OrbisEventType.RECOVERY_SUCCEEDED
                        || value.type() == OrbisEventType.RECOVERY_EXHAUSTED)
                .findFirst().orElse(null);
        OrbisEvent ledger = latest(branchEvents,
                OrbisEventType.CANONICAL_SPEECH_SEGMENT_COMMITTED);
        OrbisEvent validation = latest(branchEvents, OrbisEventType.DECISION_VALIDATING);
        OrbisEvent firstPhrase = branchEvents.stream().filter(value ->
                value.type() == OrbisEventType.PHRASE_VALIDATED).findFirst().orElse(null);
        OrbisEvent decision = latest(branchEvents, OrbisEventType.DECISION_COMMITTED);
        OrbisEvent rejected = latest(branchEvents, OrbisEventType.DECISION_REJECTED);
        OrbisEvent speechQueued = latest(branchEvents, OrbisEventType.SPEECH_QUEUED);
        OrbisEvent synthesizing = latest(branchEvents, OrbisEventType.TTS_SYNTHESIZING);
        OrbisEvent audioReady = latest(branchEvents, OrbisEventType.AUDIO_READY);
        OrbisEvent playbackQueued = latest(branchEvents, OrbisEventType.PLAYBACK_QUEUED);
        OrbisEvent speaking = latest(branchEvents, OrbisEventType.SPEAKING);
        OrbisEvent speechComplete = latest(branchEvents, OrbisEventType.SPEECH_COMPLETE);
        OrbisEvent floor = branchEvents.reversed().stream().filter(value ->
                value.type() == OrbisEventType.FLOOR_GRANTED
                        || value.type() == OrbisEventType.FLOOR_RELEASED)
                .findFirst().orElse(null);
        OrbisEvent barge = latest(branchEvents, OrbisEventType.BARGE_IN_CONFIRMED);
        OrbisEvent interrupted = latest(branchEvents, OrbisEventType.SPEECH_INTERRUPTED);
        OrbisEvent playbackInterrupted = latest(branchEvents,
                OrbisEventType.PLAYBACK_INTERRUPTED);
        OrbisEvent deferred = latest(branchEvents, OrbisEventType.DEFERRED_TOPIC_CREATED);
        String cognition = context == null
                ? "state=" + (branchEvents.isEmpty() ? "NOT_STARTED"
                        : branchEvents.getLast().type())
                : "state=" + branchEvents.getLast().type()
                        + "\ncognitiveDepth=" + fact(decision, "cognitiveDepth", "pending")
                        + "\ncontextCharacters=" + fact(context, "promptCharacters", "unknown")
                        + "\ncontextSections=" + fact(decision, "contextSections", "pending")
                        + "\nselectedIntent=" + fact(decision, "selectedIntent", "pending")
                        + "\nrelevantMemories=" + fact(decision, "relevantMemories", "pending")
                        + "\nrelevantRelationships="
                        + fact(decision, "relevantRelationships", "pending")
                        + "\nreasoningPolicy=" + fact(context, "reasoningPolicy", "pending")
                        + "\nreasonCodes=" + fact(context, "routeReasonCodes", "pending");
        String llmDetails = "provider=" + fact(
                branchEvents.stream().filter(value -> value.type()
                        == OrbisEventType.BRANCH_CREATED).findFirst().orElse(null),
                "provider", "unknown")
                + "\nmodel=" + fact(branchEvents.stream().filter(value -> value.type()
                        == OrbisEventType.BRANCH_CREATED).findFirst().orElse(null),
                        "model", "unknown")
                + "\nproviderRequestId=" + (npcEvent.providerRequestId() == null
                        ? "none" : npcEvent.providerRequestId().value())
                + "\nrequestState=" + (branchEvents.isEmpty() ? "NOT_STARTED"
                        : branchEvents.getLast().type())
                + "\nTTFT=" + fact(decision, "ttftMs", "pending") + "ms"
                + " generation=" + fact(decision, "generationMs", "pending") + "ms"
                + "\npromptTokens=" + fact(decision, "promptTokens", "pending")
                + " outputTokens=" + fact(decision, "outputTokens", "pending")
                + " tokensPerSecond=" + fact(decision, "tokensPerSecond", "pending")
                + "\nreasoning=" + fact(context, "reasoningPolicy", "pending")
                + " requested=" + fact(validation, "requestedReasoningMode",
                        fact(context, "requestedReasoningMode", "pending"))
                + " actual=" + fact(validation, "actualReasoningMode", "pending")
                + " thinkingEnabled=" + fact(validation, "thinkingEnabled", "pending")
                + "\noutputBudget=" + fact(context, "outputTokenBudget", "pending")
                + " finalAnswerTokens=" + fact(validation,
                        "finalAnswerTokenCount", "pending")
                + " reasoningTokens=" + fact(validation,
                        "reasoningTokenCount", "UNKNOWN")
                + "\nfirstPhrase=" + fact(firstPhrase, "canonicalPhrase", "pending")
                + " at=" + fact(firstPhrase, "firstValidatedPhraseMs", "pending") + "ms";
        String decisionDetails = decision == null
                ? "parseStatus=" + (rejected == null ? "pending" : "REJECTED")
                        + "\nvalidation=" + (rejected == null ? "pending"
                                : fact(rejected, "reason", "rejected"))
                : "parseStatus=" + fact(decision, "parseStatus", "VALID")
                        + "\nselectedIntent=" + fact(decision, "selectedIntent", "unknown")
                        + "\nactionCount=" + fact(decision, "actionCount", "0")
                        + "\nactionValidation="
                        + fact(decision, "actionValidation", "NO_ACTION")
                        + "\nvalidation=" + fact(decision, "validation", "VALID")
                        + "\ncanonicalSpokenText="
                        + fact(decision, "canonicalSpokenText", "");
        OrbisEvent currentSpeech = branchEvents.reversed().stream().filter(value ->
                value.type() == OrbisEventType.SPEECH_QUEUED
                        || value.type() == OrbisEventType.TTS_SYNTHESIZING
                        || value.type() == OrbisEventType.AUDIO_READY
                        || value.type() == OrbisEventType.PLAYBACK_QUEUED
                        || value.type() == OrbisEventType.SPEAKING
                        || value.type() == OrbisEventType.SPEECH_COMPLETE
                        || value.type() == OrbisEventType.SPEECH_CANCELLED
                        || value.type() == OrbisEventType.TTS_FAILED
                        || value.type() == OrbisEventType.PLAYBACK_FAILED
                        || value.type() == OrbisEventType.SPEECH_TIMED_OUT)
                .findFirst().orElse(null);
        String speechDetails = "state=" + (currentSpeech == null ? "NOT_QUEUED"
                : currentSpeech.type())
                + "\ncanonicalChunkCount=" + fact(currentSpeech,
                        "canonicalChunkCount", "0")
                + "\nspeechChunkId=" + fact(currentSpeech, "speechChunkId", "none")
                + "\nttsRequestId=" + fact(currentSpeech, "ttsRequestId", "none")
                + "\nplaybackId=" + fact(currentSpeech, "playbackId", "none")
                + "\nemotion=" + fact(speechQueued, "emotion", "unknown")
                + "\nparalinguisticEvent="
                + fact(speechQueued, "paralinguisticEvent", "none")
                + "\nvoicePreset=" + fact(synthesizing, "voicePreset", "unknown")
                + "\nreference=" + fact(synthesizing, "reference", "unknown");
        String ttsDetails = "provider=" + fact(synthesizing, "provider", "unknown")
                + "\nqueueDepth=" + fact(speechQueued, "queueDepth", "0")
                + " queueWait=" + fact(synthesizing, "queueWaitMs", "pending") + "ms"
                + "\nconditioningCache=" + fact(audioReady,
                        "conditioningCache", "pending")
                + " conditioning=" + fact(audioReady, "conditioningMs", "pending") + "ms"
                + "\nsynthesis=" + fact(audioReady, "synthesisMs", "pending") + "ms"
                + " generatedAudio=" + fact(audioReady, "generatedAudioMs", "pending") + "ms"
                + " RTF=" + fact(audioReady, "realTimeFactor", "pending")
                + "\nworkerQueue=" + fact(audioReady, "workerQueueWaitMs", "pending") + "ms"
                + " device=" + fact(audioReady, "device", "unknown")
                + "\nmodelLoadCount=" + fact(audioReady, "modelLoadCount", "unknown");
        String playbackDetails = "entitySpeaker=" + fact(speaking,
                "entitySpeaker", "not-open")
                + "\nstate=" + (speechComplete != null ? "SPEECH_COMPLETE"
                        : currentSpeech == null ? "NOT_QUEUED" : currentSpeech.type())
                + "\nplaybackQueueDepth=" + fact(playbackQueued,
                        "playbackQueueDepth", "0")
                + "\nfirstAudio=" + fact(speaking,
                        "decisionToFirstAudioMs", "pending") + "ms after decision"
                + "\ncompletion=" + fact(speechComplete, "completion", "pending")
                + "\ncancellation=" + cancellation;
        String pipelineDetails = "STT=" + turnEvents.stream().filter(value ->
                        value.type() == OrbisEventType.STT_COMPLETED).findFirst()
                .map(value -> value.facts().getOrDefault("latencyMs", "unknown") + "ms")
                .orElse("pending")
                + "\nNemotronTTFT=" + fact(decision, "ttftMs", "pending") + "ms"
                + "\nfirstCompletePhrase=" + fact(firstPhrase,
                        "firstCompletePhraseMs", "pending") + "ms"
                + " firstValidatedPhrase=" + fact(firstPhrase,
                        "firstValidatedPhraseMs", "pending") + "ms"
                + "\nTTS=" + fact(audioReady, "synthesisMs", "pending") + "ms"
                + " firstAudible=" + fact(speaking,
                        "decisionToFirstAudioMs", "pending") + "ms from phrase/decision";
        String contractDetails = "cognitionMode=" + fact(executionPlan,
                "cognitionMode", "pending")
                + "\ncontextProfile=" + fact(executionPlan, "contextProfile", "pending")
                + " decisionContract=" + fact(executionPlan, "decisionContract", "pending")
                + " schemaVersion=" + fact(executionPlan, "schemaVersion", "pending")
                + "\nspeechContract=" + fact(executionPlan, "speechContract", "pending")
                + " earlySpeech=" + fact(executionPlan, "earlySpeech", "pending")
                + "\nprompt/schema/reasoning/final/safety="
                + fact(contractBudget, "promptTokens", "?") + "/"
                + fact(contractBudget, "schemaTokens", "?") + "/"
                + fact(contractBudget, "reasoningReserveTokens", "?") + "/"
                + fact(contractBudget, "finalAnswerReserveTokens", "?") + "/"
                + fact(contractBudget, "safetyMarginTokens", "?")
                + " total=" + fact(contractBudget, "totalReservedTokens", "?")
                + " of " + fact(contractBudget, "contextWindowTokens", "?")
                + "\ncontractState=" + (contractState == null ? "pending"
                        : contractState.type() + " " + contractState.facts())
                + "\nrecovery=" + (recovery == null ? "none"
                        : recovery.type() + " " + recovery.facts())
                + "\nledger=" + (ledger == null ? "no committed segment"
                        : "chunk=" + fact(ledger, "chunkIndex", "?") + " span=["
                                + fact(ledger, "charStart", "?") + ","
                                + fact(ledger, "charEnd", "?") + ") state="
                                + fact(ledger, "ledgerState", "?"));
        String conversationDetails = "currentFloorOwner=" + fact(floor,
                "floorOwner", fact(floor, "newOwner", "NONE"))
                + "\nactiveSpeakerNpc=" + fact(floor, "activeSpeakerNpcId", "none")
                + "\nactiveTurnId=" + (npcEvent.turnId() == null
                        ? "none" : npcEvent.turnId().value())
                + "\nactiveResponseId=" + (npcEvent.responseId() == null
                        ? "none" : npcEvent.responseId().value())
                + "\nspeechState=" + (currentSpeech == null ? "NONE"
                        : currentSpeech.type())
                + "\nbargeInDetected=" + (barge != null)
                + " timestamp=" + (barge == null ? "none" : barge.at())
                + "\ncancellationReason=" + fact(interrupted, "reason", cancellation)
                + "\nconfirmedSpeechToPlaybackStopMs=" + fact(playbackInterrupted,
                        "confirmedSpeechToPlaybackStopMs", "not-measured")
                + "\ndeliveredChunks=" + fact(interrupted,
                        "deliveredChunkCount", fact(speechComplete, "chunkCount", "0"))
                + " partialChunk=" + fact(interrupted, "partialChunkId", "none")
                + " undeliveredChunks=" + fact(interrupted,
                        "undeliveredChunkCount", "0")
                + "\nactiveDeferredTopics=" + fact(deferred,
                        "activeDeferredTopicCount", "0")
                + " deferredAge=" + (deferred == null ? "none"
                        : java.time.Duration.between(deferred.at(),
                                java.time.Instant.now()).toSeconds() + "s")
                + "\nmostRecentFloorTransition=" + (floor == null ? "none"
                        : floor.type() + " " + floor.facts());
        List<OrbisEvent> resourceEvents = turnEvents.stream().filter(value -> switch (
                value.type()) {
            case RESOURCE_REQUESTED, RESOURCE_ADMITTED, RESOURCE_DEFERRED,
                    RESOURCE_RELEASED, RESOURCE_PRESSURE, BACKEND_SELECTED,
                    PROVIDER_BUSY, RESOURCE_TIMEOUT -> true;
            default -> false;
        }).filter(value -> value.facts().get("npcId") == null
                || stableId.equals(value.facts().get("npcId"))).toList();
        OrbisEvent lastResource = resourceEvents.isEmpty() ? null : resourceEvents.getLast();
        String resourceDetails = "provider=" + fact(lastResource, "provider", "unknown")
                + "\nbackend=" + fact(lastResource, "backend", "unknown")
                + "\nplacement=" + fact(lastResource, "placement", "UNKNOWN")
                + " policy=" + fact(lastResource, "policy", "UNKNOWN")
                + "\nadmissionWait=" + fact(lastResource, "admissionWaitMs", "pending")
                + "ms inference=" + fact(decision, "generationMs", "pending") + "ms"
                + "\ncontention=" + resourceEvents.stream().filter(value ->
                        value.type() == OrbisEventType.RESOURCE_DEFERRED
                                || value.type() == OrbisEventType.RESOURCE_PRESSURE
                                || value.type() == OrbisEventType.PROVIDER_BUSY)
                        .map(value -> value.facts().getOrDefault("reason", value.type().name()))
                        .distinct().toList()
                + "\ncancellation=" + cancellation;
        TurnId active = activeTurnByNpc.get(npcId);
        return "activeTurnId=" + (active == null ? "none" : active.value())
                + "\nlastTurnId=" + turnId.value() + "\nlifecycle=" + last.type()
                + "\nplayer=" + player
                + "\nlisteners=" + listeners
                + "\ndirectAddressTargets=" + direct
                + "\nresponseCandidates=" + candidates
                + "\nspeechOwners=" + owners
                + "\nselectedSTT=" + stt
                + "\nselectedLLM=" + llm
                + "\nresponseId=" + (npcEvent.responseId() == null ? "none"
                        : npcEvent.responseId().value())
                + "\ncurrentStage=" + last.type()
                + "\ncancellation=" + cancellation
                + "\nstageTimings=" + timings
                + "\nlastFailure=" + (cancellation.equals("none") ? "none" : cancellation)
                + "\n\nCOGNITION\n" + cognition
                + "\n\nLLM\n" + llmDetails
                + "\n\nDECISION\n" + decisionDetails
                + "\n\nSPEECH\n" + speechDetails
                + "\n\nTTS\n" + ttsDetails
                + "\n\nPLAYBACK\n" + playbackDetails
                + "\n\nPIPELINE\n" + pipelineDetails
                + "\n\nTURN CONTRACT / BUDGET\n" + contractDetails
                + "\n\nRESOURCES\n" + resourceDetails
                + "\n\nCONVERSATION / INTERRUPTION\n" + conversationDetails;
    }

    private static OrbisEvent latest(List<OrbisEvent> events, OrbisEventType type) {
        return events.reversed().stream().filter(value -> value.type() == type)
                .findFirst().orElse(null);
    }

    private static String fact(OrbisEvent event, String key, String fallback) {
        return event == null ? fallback : event.facts().getOrDefault(key, fallback);
    }

    private static String names(List<OrbisEvent> values, OrbisEventType type) {
        return values.stream().filter(value -> value.type() == type)
                .map(value -> value.facts().getOrDefault("npc", "unknown"))
                .distinct().toList().toString();
    }

    public synchronized Optional<TurnId> activeTurnForNpc(UUID npcId) {
        return Optional.ofNullable(activeTurnByNpc.get(npcId));
    }
}
