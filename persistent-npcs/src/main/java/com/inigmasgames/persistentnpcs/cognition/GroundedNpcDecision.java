package com.inigmasgames.persistentnpcs.cognition;

import com.inigmasgames.persistentnpcs.voice.ParalinguisticEvent;
import com.inigmasgames.persistentnpcs.voice.VocalEmotion;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** One authoritative semantic result. spokenText is finalized once and shared by all sinks. */
public record GroundedNpcDecision(
        UUID responseId,
        List<SourcedBelief> beliefUpdates,
        List<String> attendedEntities,
        List<String> attendedTopics,
        List<UUID> relevantRelationshipIds,
        GroundedIntent selectedIntent,
        int intentPriority,
        List<String> actionRequests,
        String spokenText,
        VocalEmotion emotion,
        Optional<ParalinguisticEvent> paralinguisticEvent,
        List<String> groundingEvidenceRefs,
        List<IntentCandidate> candidateIntents,
        String fallbackOrRejectionReason) {

    public GroundedNpcDecision {
        beliefUpdates = List.copyOf(beliefUpdates == null ? List.of() : beliefUpdates);
        attendedEntities = List.copyOf(attendedEntities == null ? List.of() : attendedEntities);
        attendedTopics = List.copyOf(attendedTopics == null ? List.of() : attendedTopics);
        relevantRelationshipIds = List.copyOf(
                relevantRelationshipIds == null ? List.of() : relevantRelationshipIds);
        actionRequests = List.copyOf(actionRequests == null ? List.of() : actionRequests);
        spokenText = spokenText == null ? "" : spokenText.strip();
        emotion = emotion == null ? VocalEmotion.CALM : emotion;
        paralinguisticEvent = paralinguisticEvent == null ? Optional.empty()
                : paralinguisticEvent;
        groundingEvidenceRefs = List.copyOf(
                groundingEvidenceRefs == null ? List.of() : groundingEvidenceRefs);
        candidateIntents = List.copyOf(candidateIntents == null ? List.of() : candidateIntents);
        fallbackOrRejectionReason = fallbackOrRejectionReason == null
                ? "" : fallbackOrRejectionReason;
    }

    public GroundedNpcDecision withSpokenText(String canonicalText) {
        if (canonicalText == null || canonicalText.isBlank()) {
            throw new IllegalArgumentException("Canonical spoken text is required");
        }
        return new GroundedNpcDecision(responseId, beliefUpdates, attendedEntities,
                attendedTopics, relevantRelationshipIds, selectedIntent, intentPriority,
                actionRequests, canonicalText, emotion, paralinguisticEvent,
                groundingEvidenceRefs, candidateIntents, fallbackOrRejectionReason);
    }
}
