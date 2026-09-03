package com.inigmasgames.persistentnpcs.diagnostics;

import com.google.gson.JsonObject;
import com.inigmasgames.persistentnpcs.action.NpcActionResult;
import com.inigmasgames.persistentnpcs.cognition.CognitionTurn;
import com.inigmasgames.persistentnpcs.cognition.NpcDecisionDiagnostics;
import com.inigmasgames.persistentnpcs.cognition.NpcDecision;
import com.inigmasgames.persistentnpcs.cognition.PlayerFactMemoryService;
import com.inigmasgames.persistentnpcs.cognition.ResponseLatencyTraceStore;
import com.inigmasgames.persistentnpcs.conversation.ConversationSession;
import com.inigmasgames.persistentnpcs.conversation.DialogueRequestState;
import com.inigmasgames.persistentnpcs.conversation.CognitiveContextPlan;
import com.inigmasgames.persistentnpcs.json.JsonFiles;
import com.inigmasgames.persistentnpcs.llm.LlmLatency;
import com.inigmasgames.persistentnpcs.llm.LlmInferenceAttribution;
import com.inigmasgames.persistentnpcs.profile.NpcProfile;
import com.inigmasgames.persistentnpcs.voice.EligibleNpcListener;
import com.inigmasgames.persistentnpcs.voice.PlayerUtteranceAudienceService;
import java.time.Instant;
import java.util.UUID;
import com.inigmasgames.persistentnpcs.epistemic.EpistemicContract;
import com.inigmasgames.persistentnpcs.epistemic.EpistemicClaimFirewall;

/**
 * Response-correlated event adapter for operator trace sessions. This stores structured
 * conclusions and authoritative inputs, never hidden chain-of-thought. The manager is the
 * only file sink and discards events when no operator is tracing the NPC.
 */
public final class NpcTurnAuditLog {
    private final NpcTraceManager traces;

    public NpcTurnAuditLog(NpcTraceManager traces) {
        this.traces = java.util.Objects.requireNonNull(traces, "traces");
    }

    public void input(NpcProfile profile, ConversationSession session, UUID responseId,
            String playerMessage) {
        if (!enabled(profile)) return;
        JsonObject record = base(profile, session, responseId, playerMessage, null);
        record.addProperty("event", "INPUT_RECEIVED");
        append(profile, record);
    }

    /** One per-listener black-box event; speaker suppression never erases hearing. */
    public void hearing(NpcProfile profile,
            PlayerUtteranceAudienceService.Resolution resolution,
            EligibleNpcListener listener,
            PlayerFactMemoryService.PersistenceResult observation) {
        if (!enabled(profile) || resolution == null || listener == null) return;
        var event = resolution.event();
        JsonObject record = base(profile, null, null, event.transcript(), null);
        record.addProperty("event", "PLAYER_UTTERANCE_HEARD");
        record.addProperty("utteranceId", text(event.utteranceId()));
        record.addProperty("canonicalTranscript", clean(event.transcript(), 2_000));
        record.addProperty("playerId", text(event.playerId()));
        record.add("eligibleListeners", JsonFiles.GSON.toJsonTree(
                event.eligibleNpcListeners().stream().map(value -> value.npcName()
                        + ":" + value.npcId()).toList()));
        record.add("deliveredListeners", JsonFiles.GSON.toJsonTree(
                event.eligibleNpcListeners().stream().map(EligibleNpcListener::npcId).toList()));
        record.add("directAddressTargets", JsonFiles.GSON.toJsonTree(
                event.directAddressTargets()));
        record.add("responseCandidates", JsonFiles.GSON.toJsonTree(
                resolution.responseOwners().stream().map(EligibleNpcListener::npcId).toList()));
        record.add("selectedResponseOwners", JsonFiles.GSON.toJsonTree(
                resolution.responseOwners().stream().map(EligibleNpcListener::npcId).toList()));
        record.addProperty("heardUtterance", true);
        record.addProperty("ownsNextSpeechTurn", resolution.responseOwners().stream()
                .anyMatch(value -> value.npcId().equals(listener.npcId())));
        record.addProperty("suppressionReason", resolution.suppressionReasons()
                .getOrDefault(listener.npcId(), ""));
        if (observation != null) {
            record.addProperty("classification",
                    observation.analysis().classification().name());
            record.add("extractedPropositions", JsonFiles.GSON.toJsonTree(
                    observation.analysis().propositions()));
            record.add("acceptedBeliefUpdates", JsonFiles.GSON.toJsonTree(
                    observation.beliefWrites()));
            record.addProperty("beliefRejectionReason", observation.rejectionReason());
            record.add("memoryWrites", JsonFiles.GSON.toJsonTree(
                    observation.memoryWrites()));
        }
        append(profile, record);
    }

    public void cognition(NpcProfile profile, ConversationSession session, UUID responseId,
            String playerMessage, DialogueRequestState requestState, CognitionTurn cognition) {
        if (!enabled(profile)) return;
        JsonObject record = base(profile, session, responseId, playerMessage, cognition);
        record.addProperty("event", "COGNITION_DECISION");
        addInputClassification(record, requestState, cognition);
        append(profile, record);
    }

    public void contextRouted(NpcProfile profile, ConversationSession session, UUID responseId,
            CognitiveContextPlan plan, int promptCharacters) {
        if (!enabled(profile) || plan == null) return;
        JsonObject record = base(profile, session, responseId, "", null);
        record.addProperty("event", "CONTEXT_ROUTED");
        record.addProperty("cognitiveDepth", plan.depth().name());
        record.addProperty("detectedIntent", plan.detectedIntent());
        record.add("includedContextSections", JsonFiles.GSON.toJsonTree(
                plan.includedSections()));
        record.add("excludedContextSections", JsonFiles.GSON.toJsonTree(
                plan.excludedSections()));
        record.add("authoritativeConstraints", JsonFiles.GSON.toJsonTree(
                plan.authoritativeConstraints()));
        record.addProperty("promptCharacters", Math.max(0, promptCharacters));
        record.addProperty("estimatedPromptTokens", Math.max(0, (promptCharacters + 3) / 4));
        append(profile, record);
    }

    /** E0 shadow events share the operator trace sink and cannot affect a live turn. */
    public void epistemicShadow(NpcProfile profile, ConversationSession session,
            UUID responseId, EpistemicContract contract) {
        if (!enabled(profile) || contract == null) return;
        JsonObject common = base(profile, session, responseId, "", null);
        common.addProperty("epistemicSchemaVersion", contract.schemaVersion());
        common.addProperty("epistemicMode", contract.mode().name());
        common.addProperty("epistemicPlanningMicros", contract.planningMicros());
        common.add("diagnoses", JsonFiles.GSON.toJsonTree(contract.diagnoses()));

        JsonObject frame = common.deepCopy();
        frame.addProperty("event", "DIALOGUE_FRAME_BUILT");
        frame.add("dialogueFrame", JsonFiles.GSON.toJsonTree(contract.dialogueFrame()));
        append(profile, frame);

        JsonObject query = common.deepCopy();
        query.addProperty("event", "EPISTEMIC_QUERY_PLANNED");
        query.add("queryPlan", JsonFiles.GSON.toJsonTree(contract.queryPlan()));
        append(profile, query);

        JsonObject evidence = common.deepCopy();
        evidence.addProperty("event", "EVIDENCE_RETRIEVED");
        evidence.add("evidencePacket", JsonFiles.GSON.toJsonTree(contract.evidence()));
        evidence.addProperty("evidenceCount", contract.evidence().supporting().size()
                + contract.evidence().contextual().size());
        evidence.addProperty("contradictionCount", contract.evidence().contradicting().size());
        evidence.addProperty("prunedCount", contract.evidence().prunedCount());
        evidence.addProperty("retrievalMicros", contract.evidence().retrievalMicros());
        evidence.add("retrievalDiagnostics", JsonFiles.GSON.toJsonTree(
                contract.evidence().retrievalDiagnostics()));
        evidence.addProperty("evidenceSufficiency",
                contract.evidence().sufficiency().name());
        evidence.add("sourceClasses", JsonFiles.GSON.toJsonTree(
                contract.evidence().provenanceSummary()));
        evidence.add("evidenceIds", JsonFiles.GSON.toJsonTree(
                java.util.stream.Stream.of(contract.evidence().supporting(),
                                contract.evidence().contextual())
                        .flatMap(java.util.Collection::stream)
                        .map(com.inigmasgames.persistentnpcs.epistemic.EvidenceRef::stableId)
                        .toList()));
        append(profile, evidence);

        JsonObject answerability = common.deepCopy();
        answerability.addProperty("event", "ANSWERABILITY_CLASSIFIED");
        answerability.addProperty("answerability", contract.answerability().name());
        answerability.add("supportingEvidenceIds", JsonFiles.GSON.toJsonTree(
                contract.evidence().supporting().stream()
                        .map(com.inigmasgames.persistentnpcs.epistemic.EvidenceRef::stableId)
                        .toList()));
        answerability.add("conflictingEvidenceIds", JsonFiles.GSON.toJsonTree(
                contract.evidence().contradicting().stream()
                        .map(com.inigmasgames.persistentnpcs.epistemic.EvidenceRef::stableId)
                        .toList()));
        answerability.add("unresolvedReasons", JsonFiles.GSON.toJsonTree(
                contract.answerPlan().uncertaintyReasons()));
        append(profile, answerability);

        JsonObject answer = common.deepCopy();
        answer.addProperty("event", "ANSWER_PLAN_COMPILED");
        answer.add("answerPlan", JsonFiles.GSON.toJsonTree(contract.answerPlan()));
        answer.addProperty("answerType", contract.answerPlan().answerKind());
        answer.addProperty("permittedPropositionCount",
                contract.answerPlan().authorizedPropositions().size());
        answer.add("unsupportedRequestedProperties", JsonFiles.GSON.toJsonTree(
                contract.answerPlan().unsupportedRequestedProperties()));
        answer.addProperty("requestedAction", contract.answerPlan().requestedAction());
        answer.add("claimPolicy", JsonFiles.GSON.toJsonTree(contract.claimPolicy()));
        answer.add("epistemicBudget", JsonFiles.GSON.toJsonTree(contract.budget()));
        append(profile, answer);
    }

    public void canonical(NpcProfile profile, ConversationSession session, UUID responseId,
            String rawModelOutput, String canonicalResponse, String groundingSafetyDecision) {
        if (!enabled(profile)) return;
        JsonObject record = base(profile, session, responseId, "", null);
        record.addProperty("event", "CANONICAL_RESPONSE");
        record.addProperty("rawModelOutput", clean(rawModelOutput, 8_000));
        record.addProperty("groundingSafetyDecision", clean(groundingSafetyDecision, 1_000));
        record.addProperty("canonicalResponse", clean(canonicalResponse, 2_000));
        append(profile, record);
    }

    /** Compact E3 claim decisions; no hidden reasoning and no duplicate raw context. */
    public void epistemicClaims(NpcProfile profile, ConversationSession session,
            UUID responseId, EpistemicClaimFirewall.Result result) {
        if (!enabled(profile) || result == null) return;
        for (var assessed : result.claims()) {
            JsonObject extracted = base(profile, session, responseId, "", null);
            extracted.addProperty("event", "ATOMIC_CLAIM_EXTRACTED");
            extracted.add("claim", JsonFiles.GSON.toJsonTree(assessed.claim()));
            append(profile, extracted);

            JsonObject decision = base(profile, session, responseId, "", null);
            decision.addProperty("event", assessed.releasable()
                    ? "ATOMIC_CLAIM_SUPPORTED" : "ATOMIC_CLAIM_REJECTED");
            decision.addProperty("claimId", assessed.claim().claimId());
            decision.addProperty("supportStatus", assessed.status().name());
            decision.add("evidenceIds", JsonFiles.GSON.toJsonTree(assessed.evidenceIds()));
            decision.addProperty("reason", clean(assessed.reason(), 500));
            append(profile, decision);
        }
        JsonObject summary = base(profile, session, responseId, "", null);
        summary.addProperty("event", result.repaired()
                ? "SPEECH_REPAIRED_EPISTEMICALLY" : "ANSWER_PLAN_VALIDATED");
        summary.addProperty("validationReason", result.reason());
        summary.addProperty("originalDialogue", clean(result.originalDialogue(), 2_000));
        summary.addProperty("releasedDialogue", clean(result.dialogue(), 2_000));
        summary.addProperty("extractionMicros", result.extractionMicros());
        summary.addProperty("validationMicros", result.validationMicros());
        summary.addProperty("repairMicros", result.repairMicros());
        summary.addProperty("totalMicros", result.totalMicros());
        append(profile, summary);
        if (result.reason().equals("DIRECT_ANSWER_OR_REQUIRED_SLOT_MISSING")
                || result.reason().equals("OBJECTIVE_CLAIM_BUDGET_EXCEEDED")) {
            JsonObject rejected = base(profile, session, responseId, "", null);
            rejected.addProperty("event", "ANSWER_PLAN_REJECTED");
            rejected.addProperty("reason", result.reason());
            rejected.addProperty("safeReplacement", clean(result.dialogue(), 2_000));
            append(profile, rejected);
        }
    }

    public void modelOutput(NpcProfile profile, ConversationSession session, UUID responseId,
            String rawModelText, java.util.List<?> toolCalls, String finishReason) {
        modelOutput(profile, session, responseId, rawModelText, toolCalls, finishReason, null);
    }

    public void modelOutput(NpcProfile profile, ConversationSession session, UUID responseId,
            String rawModelText, java.util.List<?> toolCalls, String finishReason,
            LlmInferenceAttribution attribution) {
        if (!enabled(profile)) return;
        JsonObject record = base(profile, session, responseId, "", null);
        record.addProperty("event", "MODEL_OUTPUT");
        addAttribution(record, attribution);
        record.addProperty("rawModelOutput", clean(rawModelText, 8_000));
        record.add("modelToolCalls", JsonFiles.GSON.toJsonTree(
                toolCalls == null ? java.util.List.of() : toolCalls));
        record.addProperty("finishReason", clean(finishReason, 200));
        append(profile, record);
    }

    public void runtime(NpcProfile profile, ConversationSession session, UUID responseId,
            JsonObject runtimeDiagnostics) {
        if (!enabled(profile) || runtimeDiagnostics == null) return;
        JsonObject record = base(profile, session, responseId, "", null);
        record.addProperty("event", "RUNTIME_DIAGNOSTICS");
        record.add("runtime", runtimeDiagnostics.deepCopy());
        append(profile, record);
    }

    public void structuredDecision(NpcProfile profile, ConversationSession session,
            UUID responseId, CognitionTurn cognition,
            NpcDecisionDiagnostics diagnostics) {
        if (!enabled(profile) || diagnostics == null) return;
        JsonObject record = base(profile, session, responseId, "", cognition);
        record.addProperty("event", "STRUCTURED_NPC_DECISION");
        record.add("actionsOfferedToModel", JsonFiles.GSON.toJsonTree(
                diagnostics.offeredActions()));
        record.addProperty("structuredRawDecision",
                clean(diagnostics.rawStructuredDecision(), 8_000));
        record.addProperty("schemaValidationResult",
                clean(diagnostics.schemaValidationResult(), 500));
        record.add("rejectedFieldsOrActions", JsonFiles.GSON.toJsonTree(
                diagnostics.rejectedFieldsOrActions()));
        record.add("groundingValidation", JsonFiles.GSON.toJsonTree(
                diagnostics.groundingValidation()));
        record.addProperty("actionValidationResult",
                clean(diagnostics.actionValidationResult(), 1_000));
        record.addProperty("committedAgentOperation",
                clean(diagnostics.committedAgentOperation(), 1_000));
        record.addProperty("canonicalSpokenText",
                clean(diagnostics.canonicalSpokenText(), 2_000));
        record.addProperty("finalActionResult",
                clean(diagnostics.finalActionResult(), 1_000));
        if (diagnostics.decision() != null) record.add("npcDecision",
                decisionJson(diagnostics.decision()));
        append(profile, record);
    }

    /**
     * Explicit trace DTO. Gson must never reflect into JDK implementation details such as
     * Optional#value; that is both unsupported on modern Java and unsafe at a diagnostic
     * boundary.
     */
    private static JsonObject decisionJson(NpcDecision decision) {
        JsonObject value = new JsonObject();
        value.addProperty("responseId", text(decision.responseId()));
        value.addProperty("npcStableId", text(decision.npcStableId()));
        value.addProperty("intent", decision.intent() == null ? "" : decision.intent().name());
        value.addProperty("spokenText", clean(decision.spokenText(), 2_000));
        value.addProperty("emotion", decision.emotion().name());
        value.addProperty("paralinguisticEvent", decision.paralinguisticEvent()
                .map(event -> event.tag()).orElse(""));
        var actions = new com.google.gson.JsonArray();
        decision.actions().forEach(action -> {
            JsonObject item = new JsonObject();
            item.addProperty("actionId", action.actionId());
            item.addProperty("actorStableId", text(action.actorStableId()));
            item.addProperty("targetStableId", text(action.targetStableId()));
            item.add("parameters", action.parameters().deepCopy());
            actions.add(item);
        });
        value.add("actions", actions);
        value.add("groundingEvidenceRefs", JsonFiles.GSON.toJsonTree(
                decision.groundingEvidenceRefs()));
        return value;
    }

    /** Primitive-only failure event used when diagnostics themselves fail. */
    public void diagnosticFailure(NpcProfile profile, ConversationSession session,
            UUID responseId, String stage, Throwable failure) {
        if (!enabled(profile)) return;
        JsonObject record = base(profile, session, responseId, "", null);
        record.addProperty("event", "TRACE_DIAGNOSTIC_FAILED");
        record.addProperty("diagnosticStage", clean(stage, 200));
        record.addProperty("failureType", failure == null ? "Unknown"
                : failure.getClass().getSimpleName());
        record.addProperty("reason", clean(failure == null ? "unknown" : failure.getMessage(),
                1_000));
        append(profile, record);
    }

    public synchronized void completed(NpcProfile profile, ConversationSession session,
            UUID responseId, String playerMessage, String rawModelText, String dialogue,
            DialogueRequestState requestState, CognitionTurn cognition,
            NpcActionResult actionResult, LlmLatency llmLatency, long totalMillis,
            ResponseLatencyTraceStore.Trace latency) {
        if (!enabled(profile)) return;
        JsonObject record = base(profile, session, responseId, playerMessage, cognition);
        record.addProperty("event", "TURN_COMPLETED");
        record.addProperty("dialogueMode", requestState == null ? "UNKNOWN"
                : requestState.mode().name());
        addInputClassification(record, requestState, cognition);
        record.addProperty("rawModelOutput", clean(rawModelText, 8_000));
        record.addProperty("spokenText", clean(dialogue, 2_000));
        if (actionResult != null) record.add("actionResult",
                JsonFiles.GSON.toJsonTree(actionResult));
        addTiming(record, llmLatency, totalMillis, latency);
        append(profile, record);
    }

    public synchronized void rejected(NpcProfile profile, ConversationSession session,
            UUID responseId, String playerMessage, String rawModelText,
            String rejectionReason, CognitionTurn cognition) {
        if (!enabled(profile)) return;
        JsonObject record = base(profile, session, responseId, playerMessage, cognition);
        record.addProperty("event", "DIALOGUE_REJECTED");
        record.addProperty("rejectionReason", clean(rejectionReason, 500));
        record.addProperty("rawModelOutput", clean(rawModelText, 8_000));
        append(profile, record);
    }

    public void failed(NpcProfile profile, ConversationSession session, UUID responseId,
            String playerMessage, Throwable failure, ResponseLatencyTraceStore.Trace latency) {
        failed(profile, session, responseId, playerMessage, failure, latency, null);
    }

    public void failed(NpcProfile profile, ConversationSession session, UUID responseId,
            String playerMessage, Throwable failure, ResponseLatencyTraceStore.Trace latency,
            LlmInferenceAttribution attribution) {
        if (!enabled(profile)) return;
        JsonObject record = base(profile, session, responseId, playerMessage, null);
        addAttribution(record, attribution);
        record.addProperty("event", failure instanceof java.util.concurrent.CancellationException
                ? "RESPONSE_CANCELLED" : "TURN_FAILED");
        record.addProperty("failureType", failure == null ? "Unknown"
                : failure.getClass().getSimpleName());
        record.addProperty("reason", clean(failure == null ? "unknown" : failure.getMessage(),
                1_000));
        if (latency != null) record.add("latencyStages",
                JsonFiles.GSON.toJsonTree(latency.stages()));
        append(profile, record);
    }

    private JsonObject base(NpcProfile profile, ConversationSession session, UUID responseId,
            String playerMessage, CognitionTurn cognition) {
        JsonObject record = new JsonObject();
        record.addProperty("at", Instant.now().toString());
        record.addProperty("responseId", text(responseId));
        record.addProperty("sessionId", session == null ? "" : text(session.sessionId()));
        record.addProperty("npcId", profile == null ? "" : text(profile.id()));
        record.addProperty("npcName", profile == null ? "unknown" : profile.name());
        record.addProperty("playerId", session == null ? "" : text(session.playerId()));
        ConversationSession.PlayerUtteranceContext utterance = session == null
                ? null : session.playerUtteranceContext();
        record.addProperty("inputMode", utterance == null ? "TEXT" : "VOICE");
        if (utterance != null) {
            record.addProperty("utteranceId", text(utterance.utteranceId()));
            record.addProperty("utteranceRange", utterance.rangeClass().name());
            record.addProperty("directAddress", utterance.directAddress());
        }
        record.addProperty("playerText", clean(playerMessage, 2_000));
        if (cognition == null || cognition.decision() == null) return record;

        var decision = cognition.decision();
        record.addProperty("selectedIntent", decision.selectedIntent().name());
        record.addProperty("intentPriority", decision.intentPriority());
        record.add("candidateIntents", JsonFiles.GSON.toJsonTree(
                decision.candidateIntents()));
        record.add("beliefUpdates", JsonFiles.GSON.toJsonTree(
                decision.beliefUpdates().stream().map(value -> new BeliefSummary(
                        value.beliefId(), value.subject(), value.predicate(),
                        value.proposition(), value.confidence(), value.urgency())).toList()));
        record.add("extractedPropositions", JsonFiles.GSON.toJsonTree(
                decision.beliefUpdates().stream().map(value -> value.proposition()).toList()));
        record.addProperty("beliefUpdateDisposition", decision.beliefUpdates().isEmpty()
                && PlayerFactMemoryService.classify(playerMessage)
                        == com.inigmasgames.persistentnpcs.cognition.PlayerInputKind.DECLARATIVE_FACT
                                ? "REJECTED_DUPLICATE_OR_PREVIOUSLY_KNOWN"
                                : decision.beliefUpdates().isEmpty()
                                        ? "NOT_A_DECLARATIVE_FACT" : "ACCEPTED");
        record.add("actionRequests", JsonFiles.GSON.toJsonTree(decision.actionRequests()));
        record.add("evidenceRefs", JsonFiles.GSON.toJsonTree(
                decision.groundingEvidenceRefs()));
        record.addProperty("emotion", decision.emotion().name());
        record.addProperty("paralinguisticEvent", decision.paralinguisticEvent()
                .map(value -> value.tag()).orElse(""));
        if (cognition.context() != null) {
            var context = cognition.context();
            record.addProperty("currentActivity", context.currentActivity());
            record.add("validActions", JsonFiles.GSON.toJsonTree(context.validActions()));
            record.add("relevantMemories", JsonFiles.GSON.toJsonTree(context.memories().stream()
                    .map(value -> new MemorySummary(value.memoryId(), value.type().name(),
                            value.summary(), value.confidence(), value.source())).toList()));
            record.add("memoryWrites", JsonFiles.GSON.toJsonTree(context.memories().stream()
                    .filter(value -> value.source().contains("response=" + responseId)
                            || utterance != null && value.source().contains(
                                    "utterance=" + utterance.utteranceId()))
                    .map(value -> value.memoryId()).toList()));
            record.addProperty("retrievalQuery", context.memoryRetrievalQuery());
            record.add("retrievedMemoryScores", JsonFiles.GSON.toJsonTree(
                    context.scoredMemories().stream().map(value -> new ScoredMemorySummary(
                            value.memory().memoryId(), value.score(),
                            value.memory().importance(), value.memory().durability().name(),
                            value.memory().emotionalIntensity(),
                            value.memory().relationshipImpact(), value.memory().goalImpact(),
                            value.memory().rehearsalCount(), value.breakdown())).toList()));
            record.add("rejectedMemories", JsonFiles.GSON.toJsonTree(
                    context.rejectedMemories()));
            record.addProperty("cognitiveDepth", context.contextPlan().depth().name());
            record.addProperty("detectedIntent", context.contextPlan().detectedIntent());
            record.add("includedContextSections", JsonFiles.GSON.toJsonTree(
                    context.contextPlan().includedSections()));
            record.add("excludedContextSections", JsonFiles.GSON.toJsonTree(
                    context.contextPlan().excludedSections()));
            record.add("authoritativeConstraints", JsonFiles.GSON.toJsonTree(
                    context.contextPlan().authoritativeConstraints()));
            record.add("relationships", JsonFiles.GSON.toJsonTree(context.relationships()));
            if (context.activeOperation() != null) record.add("activeAgentOperation",
                    JsonFiles.GSON.toJsonTree(context.activeOperation()));
            if (context.semanticWorld() != null) {
                record.addProperty("semanticWorld",
                        clean(context.semanticWorld().inspectorBlock(), 8_000));
            }
        }
        return record;
    }

    private static void addInputClassification(JsonObject record,
            DialogueRequestState requestState, CognitionTurn cognition) {
        JsonObject classification = new JsonObject();
        classification.addProperty("dialogueMode", requestState == null ? "UNKNOWN"
                : requestState.mode().name());
        classification.addProperty("playerInputKind",
                PlayerFactMemoryService.classify(record.has("playerText")
                        ? record.get("playerText").getAsString() : "").name());
        if (cognition != null && cognition.appraisal() != null) {
            classification.addProperty("requestedAction",
                    clean(cognition.appraisal().requestedAction(), 200));
            classification.addProperty("actionAuthorized",
                    cognition.appraisal().actionAuthorized());
            classification.addProperty("authorizationReason",
                    clean(cognition.appraisal().authorizationReason(), 500));
        }
        record.add("inputClassification", classification);
    }

    private static void addTiming(JsonObject record, LlmLatency llmLatency, long totalMillis,
            ResponseLatencyTraceStore.Trace latency) {
        JsonObject timing = new JsonObject();
        if (llmLatency != null) {
            timing.addProperty("nemotronTtftMillis", llmLatency.timeToFirstTokenMillis());
            timing.addProperty("nemotronCompletionMillis", llmLatency.completionMillis());
            timing.addProperty("llmTtftMillis", llmLatency.timeToFirstTokenMillis());
            timing.addProperty("llmCompletionMillis", llmLatency.completionMillis());
            timing.addProperty("streaming", llmLatency.streaming());
        }
        timing.addProperty("conversationTotalMillis", Math.max(0, totalMillis));
        if (latency != null) timing.add("stages", JsonFiles.GSON.toJsonTree(latency.stages()));
        record.add("timing", timing);
    }

    private static void addAttribution(JsonObject record,
            LlmInferenceAttribution attribution) {
        if (attribution == null) {
            record.addProperty("llmProvider", "UNKNOWN");
            record.addProperty("llmModel", "UNKNOWN");
            return;
        }
        record.addProperty("llmProvider", attribution.provider());
        record.addProperty("llmModel", attribution.model());
        record.addProperty("llmEndpoint", attribution.endpoint());
        record.addProperty("llmDispatchedAt", attribution.dispatchedAt().toString());
    }

    private void append(NpcProfile profile, JsonObject record) {
        if (profile != null) traces.record(profile.id(), record);
    }

    private boolean enabled(NpcProfile profile) {
        return profile != null && traces.isNpcTraced(profile.id());
    }

    private static String text(UUID value) { return value == null ? "" : value.toString(); }

    private static String clean(String value, int maximum) {
        String text = value == null ? "" : value.replaceAll("[\\r\\n]+", " ")
                .replaceAll("\\s+", " ").strip();
        return text.length() <= maximum ? text : text.substring(0, maximum);
    }

    private record BeliefSummary(UUID beliefId, String subject, String predicate,
            String proposition, double confidence, double urgency) { }

    private record MemorySummary(UUID memoryId, String type, String summary,
            double confidence, String source) { }

    private record ScoredMemorySummary(UUID memoryId, double score, double importance,
            String durability, double emotionalIntensity, double relationshipImpact,
            double goalImpact, int rehearsalCount, Object breakdown) { }
}
