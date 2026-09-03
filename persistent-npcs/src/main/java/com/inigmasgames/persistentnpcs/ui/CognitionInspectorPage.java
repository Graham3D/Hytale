package com.inigmasgames.persistentnpcs.ui;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
import com.hypixel.hytale.server.core.entity.entities.player.pages.InteractiveCustomUIPage;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.ui.builder.EventData;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.inigmasgames.persistentnpcs.cognition.CognitionTraceStore;
import com.inigmasgames.persistentnpcs.cognition.ResponseLatencyTraceStore;
import com.inigmasgames.persistentnpcs.profile.NpcProfile;
import com.inigmasgames.persistentnpcs.autonomy.AgentOperation;
import com.inigmasgames.persistentnpcs.voice.VoiceInteractionTraceStore;
import com.inigmasgames.persistentnpcs.ai.AiServiceRouter;
import com.inigmasgames.persistentnpcs.orbis.OrbisRuntime;
import com.inigmasgames.persistentnpcs.diagnostics.NpcTraceManager;
import com.inigmasgames.persistentnpcs.sentinel.OrbisDegradationSentinel;
import java.util.LinkedHashSet;
import java.util.stream.Collectors;

/** Developer-only native Update 6 page for the latest concise cognition trace. */
public final class CognitionInspectorPage
        extends InteractiveCustomUIPage<CognitionInspectorPage.PageData> {
    private final NpcProfile profile;
    private final CognitionTraceStore.Trace trace;
    private final ResponseLatencyTraceStore.Trace latency;
    private final AgentOperation guideOperation;
    private final VoiceInteractionTraceStore.Snapshot voice;
    private final AiServiceRouter aiServices;
    private final PlayerRef playerRef;
    private final OrbisRuntime orbisRuntime;
    private final NpcTraceManager npcTraces;
    private final OrbisDegradationSentinel degradationSentinel;

    public CognitionInspectorPage(
            PlayerRef playerRef, NpcProfile profile, CognitionTraceStore.Trace trace) {
        this(playerRef, profile, trace, null);
    }

    public CognitionInspectorPage(PlayerRef playerRef, NpcProfile profile,
            CognitionTraceStore.Trace trace, ResponseLatencyTraceStore.Trace latency) {
        this(playerRef, profile, trace, latency, null);
    }

    public CognitionInspectorPage(PlayerRef playerRef, NpcProfile profile,
            CognitionTraceStore.Trace trace, ResponseLatencyTraceStore.Trace latency,
            AgentOperation guideOperation) {
        this(playerRef, profile, trace, latency, guideOperation, null);
    }

    public CognitionInspectorPage(PlayerRef playerRef, NpcProfile profile,
            CognitionTraceStore.Trace trace, ResponseLatencyTraceStore.Trace latency,
            AgentOperation guideOperation, VoiceInteractionTraceStore.Snapshot voice) {
        this(playerRef, profile, trace, latency, guideOperation, voice, null);
    }

    public CognitionInspectorPage(PlayerRef playerRef, NpcProfile profile,
            CognitionTraceStore.Trace trace, ResponseLatencyTraceStore.Trace latency,
            AgentOperation guideOperation, VoiceInteractionTraceStore.Snapshot voice,
            AiServiceRouter aiServices) {
        this(playerRef, profile, trace, latency, guideOperation, voice, aiServices,
                null, null, null);
    }

    public CognitionInspectorPage(PlayerRef playerRef, NpcProfile profile,
            CognitionTraceStore.Trace trace, ResponseLatencyTraceStore.Trace latency,
            AgentOperation guideOperation, VoiceInteractionTraceStore.Snapshot voice,
            AiServiceRouter aiServices, OrbisRuntime orbisRuntime,
            NpcTraceManager npcTraces) {
        this(playerRef, profile, trace, latency, guideOperation, voice, aiServices,
                orbisRuntime, npcTraces, null);
    }

    public CognitionInspectorPage(PlayerRef playerRef, NpcProfile profile,
            CognitionTraceStore.Trace trace, ResponseLatencyTraceStore.Trace latency,
            AgentOperation guideOperation, VoiceInteractionTraceStore.Snapshot voice,
            AiServiceRouter aiServices, OrbisRuntime orbisRuntime,
            NpcTraceManager npcTraces, OrbisDegradationSentinel degradationSentinel) {
        super(playerRef, CustomPageLifetime.CanDismiss, PageData.CODEC);
        this.profile = profile;
        this.trace = trace;
        this.latency = latency;
        this.guideOperation = guideOperation;
        this.voice = voice;
        this.aiServices = aiServices;
        this.playerRef = playerRef;
        this.orbisRuntime = orbisRuntime;
        this.npcTraces = npcTraces;
        this.degradationSentinel = degradationSentinel;
    }

    @Override
    public void build(Ref<EntityStore> ref, UICommandBuilder commands,
            UIEventBuilder events, Store<EntityStore> store) {
        commands.append("Pages/ImmersiveNpcCognitionInspector.ui");
        commands.set("#InspectorTitle.Text", profile.name() + " - Cognition Inspector");
        commands.set("#AiBackends.Text", aiServices == null
                ? "AI router diagnostics unavailable in this context."
                : aiServices.diagnosticsText());
        commands.set("#Runtime.Text", aiServices == null
                ? "Runtime diagnostics unavailable in this context."
                : aiServices.runtimeDiagnosticsText(profile.id()));
        commands.set("#Orbis.Text", orbisRuntime == null
                ? "Orbis runtime unavailable in this context."
                : orbisRuntime.diagnostics().turnSummary(profile.id())
                        + (aiServices == null ? "" : "\nselectedTTS="
                                + aiServices.diagnostic(
                                        com.inigmasgames.persistentnpcs.ai.AiServiceKind
                                                .TEXT_TO_SPEECH).providerId()
                                + " / " + aiServices.diagnostic(
                                        com.inigmasgames.persistentnpcs.ai.AiServiceKind
                                                .TEXT_TO_SPEECH).backend()
                                + "\nproviderHealth=" + aiServices.diagnostics().stream()
                                        .map(value -> value.service() + "="
                                                + value.health().status()).toList()
                                + "\nqueueState=" + aiServices.diagnostics().stream()
                                        .map(value -> value.service() + " depth="
                                                + value.metrics().queueDepth() + " active="
                                                + value.metrics().activeRequests()).toList())
                        + (degradationSentinel == null ? ""
                                : "\n" + degradationSentinel.diagnostics()));
        commands.set("#Resources.Text", orbisRuntime == null
                ? "Orbis resource scheduler unavailable in this context."
                : orbisRuntime.resourcesSummary()
                        + (aiServices == null ? "" : "\nproviderPlacements="
                                + aiServices.diagnostics().stream().map(value ->
                                        value.service() + "=" + value.mode() + "/"
                                                + value.backend()).toList()
                                + "\nproviderHealth=" + aiServices.diagnostics().stream()
                                        .map(value -> value.service() + "="
                                                + value.health().status()).toList()));
        if (aiServices == null) {
            commands.set("#LlmSelection.Text", "Runtime provider selection unavailable.");
        } else {
            String active = aiServices.activeLlmSelection()
                    .map(value -> value.provider() + " / " + value.model()
                            + "\nendpoint=" + value.endpoint())
                    .orElse("Legacy single-provider mode");
            String latest = aiServices.latestLlmAttribution(profile.id())
                    .map(value -> value.provider() + " / " + value.model()
                            + "\ndispatchedAt=" + value.dispatchedAt())
                    .orElse("No attributed response in this runtime.");
            commands.set("#LlmSelection.Text", "ACTIVE\n" + active
                    + "\n\nLATEST RESPONSE FOR " + profile.name().toUpperCase(
                            java.util.Locale.ROOT) + "\n" + latest
                    + "\n\nSwitching changes only future LLM turns; NPC memory/state is unchanged.");
        }
        if (trace == null) {
            commands.set("#Perceptions.Text", "No completed cognition trace is available.");
            setRemaining(commands, "None.");
        } else {
            var context = trace.context();
            var decision = trace.decision();
            commands.set("#ContextRouting.Text", "cognitiveDepth=" + context.contextPlan().depth()
                    + "\ndetectedIntent=" + context.contextPlan().detectedIntent()
                    + "\nincluded=" + context.contextPlan().includedSections()
                    + "\nexcluded=" + context.contextPlan().excludedSections()
                    + "\npromptCharacters=" + trace.promptCharacters()
                    + "\nestimatedPromptTokens=" + trace.estimatedPromptTokens()
                    + "\nauthoritativeConstraints=\n"
                    + context.contextPlan().constraintBlock());
            commands.set("#Perceptions.Text", context.semanticWorld() == null
                    ? "Semantic normalization unavailable."
                    : compact(context.semanticWorld().inspectorBlock(), 1600));
            commands.set("#Locator.Text", context.semanticWorld() == null
                    || context.semanticWorld().knownNpcLocator() == null
                            ? "No known-NPC lookup requested."
                            : context.semanticWorld().knownNpcLocator().semanticBlock());
            commands.set("#SelfState.Text", context.semanticWorld() == null
                    ? "Unavailable." : context.semanticWorld().selfState().promptBlock());
            commands.set("#ProfileInfluence.Text", profileInfluence(context, decision));
            commands.set("#Beliefs.Text", lines(decision.beliefUpdates().stream()
                    .map(value -> value.predicate() + ": " + value.proposition()
                            + (value.evidenceRefs().stream().anyMatch(evidenceRef ->
                                    evidenceRef.startsWith("AUTHORITATIVE_LOCATOR:"))
                                            ? " [authoritative bounded locator, confidence="
                                            : " [player-reported, confidence=")
                            + value.confidence() + "]")
                            .toList()));
            commands.set("#Retrieval.Text", "Relationships: " + context.relationships().stream()
                    .map(value -> value.naturalSummary("relevant person")).toList()
                    + "\nMemories: " + context.memories().stream()
                            .map(value -> value.summary()).toList());
            commands.set("#Memory.Text", "classification="
                    + com.inigmasgames.persistentnpcs.cognition.PlayerFactMemoryService
                            .classify(context.memoryRetrievalQuery())
                    + "\npropositionsExtracted=" + decision.beliefUpdates().stream()
                            .map(value -> value.predicate() + ":" + value.proposition()).toList()
                    + "\nbeliefWrites=" + decision.beliefUpdates().stream()
                            .map(value -> value.beliefId()).toList()
                    + "\nmemoryWrites=" + decision.beliefUpdates().stream()
                            .map(value -> "PLAYER_FACT:" + value.beliefId()).toList()
                    + "\nretrievalQuery=" + context.memoryRetrievalQuery()
                    + "\ntopMemories=" + context.scoredMemories().stream()
                            .map(value -> value.memory().memoryId() + " score="
                                    + "%.3f".formatted(value.score())
                                    + " importance=" + "%.3f".formatted(
                                            value.memory().importance())
                                    + " durability=" + value.memory().durability()
                                    + " emotionalIntensity=" + "%.3f".formatted(
                                            value.memory().emotionalIntensity())
                                    + " relationshipImpact=" + "%.3f".formatted(
                                            value.memory().relationshipImpact())
                                    + " goalImpact=" + "%.3f".formatted(
                                            value.memory().goalImpact())
                                    + " rehearsalCount=" + value.memory().rehearsalCount()
                                    + "\n  breakdown=" + value.breakdown()
                                    + "\n  " + compact(value.memory().summary(), 180)).toList()
                    + "\nrejectedMemories=" + context.rejectedMemories().stream()
                            .map(value -> value.memoryId() + " reason=" + value.reason()
                                    + " semantic=" + "%.3f".formatted(
                                            value.semanticRelevance())
                                    + " score=" + "%.3f".formatted(value.score())).toList());
            commands.set("#Attention.Text", lines(decision.candidateIntents().stream()
                    .map(value -> value.intent() + " priority=" + value.priority()
                            + " utility=" + "%.2f".formatted(value.utility())
                            + " basis=" + value.basis()).toList()));
            commands.set("#SelectedIntent.Text", decision.selectedIntent() + " (priority "
                    + decision.intentPriority() + ")");
            commands.set("#Action.Text", "requests=" + decision.actionRequests()
                    + "\nactiveOperation=" + (context.activeOperation() == null ? "none"
                            : context.activeOperation().operationId() + " "
                                    + context.activeOperation().status())
                    + "\nlatestGuideOperation=" + (guideOperation == null ? "none"
                            : guideOperation.kind() + " " + guideOperation.status()
                                    + (guideOperation.result().isBlank() ? ""
                                            : " - " + guideOperation.result())));
            commands.set("#Evidence.Text", lines(decision.groundingEvidenceRefs()));
            commands.set("#SpokenText.Text", decision.spokenText().isBlank()
                    ? "Pending lexical commit." : decision.spokenText());
            commands.set("#Emotion.Text", decision.emotion() + decision.paralinguisticEvent()
                    .map(value -> " + " + value.tag()).orElse(""));
            commands.set("#Fallback.Text", decision.fallbackOrRejectionReason().isBlank()
                    ? "None." : decision.fallbackOrRejectionReason());
            String structuredBoundary = trace.structuredDecision() == null
                    ? "\n\nstructuredDecision=not available for this turn"
                    : "\n\nactionsOffered=" + trace.structuredDecision().offeredActions()
                            + "\nstructuredRawDecision=\n"
                            + compact(trace.structuredDecision().rawStructuredDecision(), 4_000)
                            + "\nschemaValidation="
                            + trace.structuredDecision().schemaValidationResult()
                            + "\nrejectedFieldsOrActions="
                            + trace.structuredDecision().rejectedFieldsOrActions()
                            + "\ngroundingValidation="
                            + trace.structuredDecision().groundingValidation()
                            + "\nactionValidation="
                            + trace.structuredDecision().actionValidationResult()
                            + "\ncommittedAgentOperation="
                            + trace.structuredDecision().committedAgentOperation()
                            + "\nstructuredCanonicalSpeech="
                            + compact(trace.structuredDecision().canonicalSpokenText(), 2_000)
                            + "\nfinalActionResult="
                            + trace.structuredDecision().finalActionResult();
            commands.set("#ModelBoundary.Text", "rawModelOutput=\n"
                    + compact(trace.rawModelOutput(), 2_000)
                    + "\n\ngroundingSafetyDecision="
                    + trace.groundingSafetyDecision()
                    + "\n\ncanonicalResponse=\n"
                    + compact(trace.canonicalResponse(), 2_000)
                    + structuredBoundary);
            commands.set("#RawDiagnostics.Text", context.rawPerception() == null
                    ? "No raw capture retained." : compact(
                            context.rawPerception().debugBlock(), 4500));
        }
        if (trace == null) commands.set("#Locator.Text", "No completed cognition trace.");
        if (latency == null) {
            commands.set("#LatencyStatus.Text", "No response latency trace is available.");
            commands.set("#Latency.Text", "None.");
        } else {
            commands.set("#LatencyStatus.Text", latency.anyOverBudget()
                    ? "OVER BUDGET - inspect highlighted stages below"
                    : "Within configured budgets");
            commands.set("#Latency.Text", lines(latency.stages().stream()
                    .map(value -> (value.overBudget() ? "!! OVER BUDGET  " : "OK  ")
                            + value.stage() + "  elapsed=" + value.elapsedFromStartMillis()
                            + "ms  duration=" + value.durationMillis() + "ms  budget="
                            + value.budgetMillis() + "ms").toList())
                    + "\nTotal: " + latency.totalMillis() + "ms");
        }
        if (voice == null) {
            commands.set("#Hearing.Text", "No player utterance has reached this NPC.");
            commands.set("#VoiceEvent.Text", "No player utterance has reached this NPC.");
            commands.set("#VoiceRouting.Text", "None.");
            commands.set("#VoicePlayback.Text", "None.");
            commands.set("#VoiceTiming.Text", "None.");
        } else {
            commands.set("#Hearing.Text", "currentUtterance=" + voice.transcript()
                    + "\neligibleListeners=" + voice.listenerCandidates()
                    + "\ndeliveredListeners=" + voice.receivedBy()
                    + "\ndirectAddressTargets=" + voice.directTargets()
                    + "\nresponseCandidates=" + voice.responseCandidates()
                    + "\nresponseOwners=" + voice.responseOwners()
                    + "\nsuppressionReason=" + (voice.suppressionReason().isBlank()
                            ? "none" : voice.suppressionReason()));
            commands.set("#VoiceEvent.Text", "utteranceId=" + voice.utteranceId()
                    + "\ntranscript=" + voice.transcript());
            commands.set("#VoiceRouting.Text", "listenerCandidates="
                    + voice.listenerCandidates() + "\nreceivedBy=" + voice.receivedBy()
                    + "\ndirectAddress=" + voice.directTargets()
                    + "\nrange=" + voice.rangeClassification()
                    + "\nresponseCandidates=" + voice.responseCandidates()
                    + "\nresponseOwners=" + voice.responseOwners());
            commands.set("#VoicePlayback.Text", "responseId="
                    + (voice.responseId() == null ? "none" : voice.responseId())
                    + "\nresponseNpcId=" + voice.responseNpcId()
                    + "\nprojection=" + voice.projection()
                    + "\nstate=" + voice.playbackState()
                    + "\ncancellationReason=" + (voice.cancellationReason().isBlank()
                            ? "none" : voice.cancellationReason()));
            commands.set("#VoiceTiming.Text", "utteranceEndpoint="
                    + voice.endpointMillis() + "ms\nSTT=" + voice.sttMillis()
                    + "ms\naudienceResolution=" + voice.audienceMillis()
                    + "ms\ntotalToFirstAudible=" + voice.firstAudioMillis()
                    + "ms\nCognition / selected LLM / TTS stages are listed in LATENCY below.");
        }
        events.addEventBinding(CustomUIEventBindingType.Activating,
                "#CloseButton", EventData.of("Close", "true"));
        if (aiServices != null) {
            events.addEventBinding(CustomUIEventBindingType.Activating,
                    "#QwenButton", EventData.of("SelectProvider", "QWEN"));
            events.addEventBinding(CustomUIEventBindingType.Activating,
                    "#NemotronButton", EventData.of("SelectProvider", "NEMOTRON"));
            events.addEventBinding(CustomUIEventBindingType.Activating,
                    "#ProviderHealthButton", EventData.of("ProbeHealth", "true"));
        }
        if (orbisRuntime != null) events.addEventBinding(
                CustomUIEventBindingType.Activating, "#CancelOrbisButton",
                EventData.of("CancelOrbis", "true"));
        if (orbisRuntime != null) {
            for (String policy : java.util.List.of("GPU_HEAVY", "BALANCED", "CPU_FIRST",
                    "CPU_ONLY", "REMOTE_AI")) {
                events.addEventBinding(CustomUIEventBindingType.Activating,
                        "#Policy" + policy.replace("_", "") + "Button",
                        EventData.of("SelectResourcePolicy", policy));
            }
        }
        if (npcTraces != null) events.addEventBinding(
                CustomUIEventBindingType.Activating, "#TraceToggleButton",
                EventData.of("ToggleTrace", "true"));
    }

    @Override
    public void handleDataEvent(
            Ref<EntityStore> ref, Store<EntityStore> store, PageData data) {
        if (action(data.close)) {
            close();
            return;
        }
        if (data.selectProvider != null && aiServices != null) {
            String requested = data.selectProvider;
            close();
            aiServices.selectLlmProvider(requested).whenComplete((selection, failure) -> {
                if (failure == null) {
                    playerRef.sendMessage(Message.raw("ImmersiveNPCs LLM selected: "
                            + selection.provider() + " / " + selection.model()
                            + ". This controls future live NPC cognition."));
                } else {
                    Throwable cause = failure;
                    while (cause.getCause() != null
                            && cause instanceof java.util.concurrent.CompletionException) {
                        cause = cause.getCause();
                    }
                    playerRef.sendMessage(Message.raw("ImmersiveNPCs LLM selection failed: "
                            + (cause.getMessage() == null
                                    ? cause.getClass().getSimpleName() : cause.getMessage())));
                }
            });
            return;
        }
        if (action(data.cancelOrbis) && orbisRuntime != null) {
            var active = orbisRuntime.diagnostics().activeTurnForNpc(profile.id());
            if (active.isPresent()) {
                orbisRuntime.operatorCancel(active.get());
                playerRef.sendMessage(Message.raw("Cancelled Orbis turn "
                        + active.get().value() + " for " + profile.name() + "."));
            } else {
                playerRef.sendMessage(Message.raw("No active Orbis turn for "
                        + profile.name() + "."));
            }
            close();
            return;
        }
        if (action(data.toggleTrace) && npcTraces != null) {
            close();
            npcTraces.toggleAsync(playerRef.getUuid(), profile).whenComplete((result, failure) ->
                    playerRef.sendMessage(Message.raw(failure == null
                            ? (result.started() ? "Started" : "Stopped") + " trace for "
                                    + profile.name() + ": " + result.path()
                            : "NPC trace failed: " + failure.getMessage())));
            return;
        }
        if (data.selectResourcePolicy != null && orbisRuntime != null) {
            String requested = data.selectResourcePolicy;
            close();
            orbisRuntime.selectResourcePolicy(requested).whenComplete((policy, failure) ->
                    playerRef.sendMessage(Message.raw(failure == null
                            ? "Orbis resource policy selected: " + policy
                                    + ". Existing jobs are unchanged; future jobs use it."
                            : "Orbis resource policy selection failed: "
                                    + failure.getMessage())));
            return;
        }
        if (action(data.probeHealth) && aiServices != null) {
            close();
            aiServices.probeAvailability().whenComplete((ignored, failure) ->
                    playerRef.sendMessage(Message.raw(failure == null
                            ? "AI provider health probe completed."
                            : "AI provider health probe failed: " + failure.getMessage())));
        }
    }

    private static void setRemaining(UICommandBuilder commands, String value) {
        for (String id : new String[] {"ContextRouting", "ModelBoundary", "SelfState", "ProfileInfluence", "Locator", "Beliefs", "Retrieval", "Memory", "Attention",
                "SelectedIntent", "Action", "Evidence", "SpokenText", "Emotion",
                "Fallback", "RawDiagnostics"}) commands.set("#" + id + ".Text", value);
    }

    private static boolean action(String value) {
        return "true".equalsIgnoreCase(value);
    }

    private String profileInfluence(
            com.inigmasgames.persistentnpcs.cognition.CognitionContext context,
            com.inigmasgames.persistentnpcs.cognition.GroundedNpcDecision decision) {
        LinkedHashSet<String> deterministic = new LinkedHashSet<>();
        deterministic.add("id/stableId (runtime identity and keyed state)");
        deterministic.add("defaultDisposition (focused-player relationship baseline)");
        deterministic.add("goals (goal-opportunity match)");
        if (!context.validActions().isEmpty()) {
            deterministic.add("roleIds + capabilities (eligible action set)");
        }
        if (!context.relationships().isEmpty()
                || context.semanticWorld() != null
                && context.semanticWorld().knownNpcLocator() != null) {
            deterministic.add("relationships (retrieval, utility, locator gate)");
        }
        if (context.semanticWorld() != null
                && context.semanticWorld().knownNpcLocator() != null
                && context.semanticWorld().knownNpcLocator().status()
                        == com.inigmasgames.persistentnpcs.perception.KnownNpcLocationStatus.FOUND) {
            deterministic.add("sociability + trustDisposition (guide utility)");
        }
        boolean actionDecision = decision.candidateIntents().stream().anyMatch(candidate ->
                candidate.intent()
                        == com.inigmasgames.persistentnpcs.cognition.GroundedIntent.EXECUTE_DIRECT_REQUEST
                || candidate.intent()
                        == com.inigmasgames.persistentnpcs.cognition.GroundedIntent.REFUSE_UNGROUNDED_ACTION);
        if (actionDecision) {
            deterministic.add("riskTolerance + trustDisposition (action authorization)");
        }
        if (context.unknownWorldFacts().contains("CURRENT_LOCATION_NAME")
                && context.memoryRetrievalQuery().contains("?")) {
            deterministic.add("curiosity (unknown-environment follow-up)");
        }
        return "authoritativeProfile=" + profile.name() + ".json"
                + "\nlatestDeterministicReads=" + deterministic
                + "\nselfModelInputs=[selfIdentity, speciesArchetype, role, values, "
                + "personalityTraits, fears, goals]"
                + "\ndialogueStyleInputs=[name, personality (routed), speakingStyle, "
                + "biography (routed), purpose (routed), likes/dislikes/values/fears/goals "
                + "(routed)]"
                + "\nnonDialogueRuntime=[home, workplace, defaultSchedule, appearancePreset, "
                + "voicePreset, voiceEffectPreset, modelTier]"
                + "\nunboundJsonFields=[loves, hates]";
    }

    private static String lines(java.util.List<String> values) {
        return values == null || values.isEmpty() ? "None." : values.stream()
                .collect(Collectors.joining("\n"));
    }

    private static String compact(String value, int maximum) {
        String text = value == null ? "None." : value.strip();
        return text.length() <= maximum ? text : text.substring(0, maximum) + "...";
    }

    public static final class PageData {
        static final BuilderCodec<PageData> CODEC = BuilderCodec
                .builder(PageData.class, PageData::new)
                .append(new KeyedCodec<>("Close", Codec.STRING),
                        (data, value) -> data.close = value, data -> data.close).add()
                .append(new KeyedCodec<>("SelectProvider", Codec.STRING),
                        (data, value) -> data.selectProvider = value,
                        data -> data.selectProvider).add()
                .append(new KeyedCodec<>("CancelOrbis", Codec.STRING),
                        (data, value) -> data.cancelOrbis = value,
                        data -> data.cancelOrbis).add()
                .append(new KeyedCodec<>("ToggleTrace", Codec.STRING),
                        (data, value) -> data.toggleTrace = value,
                        data -> data.toggleTrace).add()
                .append(new KeyedCodec<>("SelectResourcePolicy", Codec.STRING),
                        (data, value) -> data.selectResourcePolicy = value,
                        data -> data.selectResourcePolicy).add()
                .append(new KeyedCodec<>("ProbeHealth", Codec.STRING),
                        (data, value) -> data.probeHealth = value,
                        data -> data.probeHealth).add()
                .build();
        private String close;
        private String selectProvider;
        private String cancelOrbis;
        private String toggleTrace;
        private String selectResourcePolicy;
        private String probeHealth;
    }
}
