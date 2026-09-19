package com.inigmasgames.persistentnpcs.conversation;

import com.inigmasgames.persistentnpcs.llm.LlmLatency;
import com.inigmasgames.persistentnpcs.llm.LlmUsage;
import com.inigmasgames.persistentnpcs.cognition.NpcDecision;
import com.inigmasgames.persistentnpcs.cognition.NpcDecisionDiagnostics;
import java.util.List;
import java.util.UUID;
import com.inigmasgames.persistentnpcs.voice.VocalState;

public record ConversationOutcome(
        UUID sessionId,
        String dialogue,
        LlmLatency llmLatency,
        long totalConversationMillis,
        VocalState vocalState,
        DialogueMode dialogueMode,
        UUID responseId,
        UUID providerRequestId,
        NpcDecision decision,
        NpcDecisionDiagnostics decisionDiagnostics,
        LlmUsage usage,
        String rawModelOutput,
        CognitiveDepth cognitiveDepth,
        List<String> contextSections,
        int contextCharacters,
        int relevantMemoryCount,
        int relevantRelationshipCount) {

    public ConversationOutcome {
        dialogue = CanonicalDialogueAssembler.assemble(dialogue);
        if (decision != null) decision = decision.withSpokenText(dialogue);
        usage = usage == null ? LlmUsage.unknown() : usage;
        rawModelOutput = rawModelOutput == null ? "" : rawModelOutput;
        cognitiveDepth = cognitiveDepth == null ? CognitiveDepth.COMPLEX_INTENT : cognitiveDepth;
        contextSections = List.copyOf(contextSections == null ? List.of() : contextSections);
        contextCharacters = Math.max(0, contextCharacters);
        relevantMemoryCount = Math.max(0, relevantMemoryCount);
        relevantRelationshipCount = Math.max(0, relevantRelationshipCount);
    }

    public ConversationOutcome(UUID sessionId, String dialogue, LlmLatency llmLatency,
            long totalConversationMillis, VocalState vocalState, DialogueMode dialogueMode) {
        this(sessionId, dialogue, llmLatency, totalConversationMillis, vocalState,
                dialogueMode, null, null, null, null, LlmUsage.unknown(), "",
                CognitiveDepth.COMPLEX_INTENT, List.of(), 0, 0, 0);
    }

    public ConversationOutcome(
            UUID sessionId,
            String dialogue,
            LlmLatency llmLatency,
            long totalConversationMillis) {
        this(sessionId, dialogue, llmLatency, totalConversationMillis,
                VocalState.infer(dialogue), DialogueMode.ORDINARY_CONVERSATION);
    }

    public ConversationOutcome(
            UUID sessionId,
            String dialogue,
            LlmLatency llmLatency,
            long totalConversationMillis,
            VocalState vocalState) {
        this(sessionId, dialogue, llmLatency, totalConversationMillis,
                vocalState, DialogueMode.ORDINARY_CONVERSATION);
    }
}
