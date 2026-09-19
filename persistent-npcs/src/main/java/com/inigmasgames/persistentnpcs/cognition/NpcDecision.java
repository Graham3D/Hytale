package com.inigmasgames.persistentnpcs.cognition;

import com.inigmasgames.persistentnpcs.voice.ParalinguisticEvent;
import com.inigmasgames.persistentnpcs.voice.VocalEmotion;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Authoritative model boundary: speech and developer-approved actions are one immutable result.
 * It contains semantic conclusions only, never hidden chain-of-thought.
 */
public record NpcDecision(
        UUID responseId,
        UUID npcStableId,
        GroundedIntent intent,
        String spokenText,
        VocalEmotion emotion,
        Optional<ParalinguisticEvent> paralinguisticEvent,
        List<NpcDecisionAction> actions,
        List<String> groundingEvidenceRefs) {

    public NpcDecision {
        spokenText = spokenText == null ? "" : spokenText.strip();
        emotion = emotion == null ? VocalEmotion.CALM : emotion;
        paralinguisticEvent = paralinguisticEvent == null
                ? Optional.empty() : paralinguisticEvent;
        actions = List.copyOf(actions == null ? List.of() : actions);
        groundingEvidenceRefs = List.copyOf(
                groundingEvidenceRefs == null ? List.of() : groundingEvidenceRefs);
    }

    public NpcDecision withSpokenText(String text) {
        return new NpcDecision(responseId, npcStableId, intent, text, emotion,
                paralinguisticEvent, actions, groundingEvidenceRefs);
    }

    public NpcDecision withoutActions(String truthfulText) {
        return new NpcDecision(responseId, npcStableId,
                GroundedIntent.REFUSE_UNGROUNDED_ACTION, truthfulText, emotion,
                paralinguisticEvent, List.of(), groundingEvidenceRefs);
    }

    /** Deterministic social fallback after unsupported factual speech is rejected. */
    public NpcDecision withGroundedFallback(String truthfulText) {
        return new NpcDecision(responseId, npcStableId,
                GroundedIntent.AMBIENT_RESPONSE, truthfulText, emotion,
                Optional.empty(), List.of(), groundingEvidenceRefs);
    }
}
