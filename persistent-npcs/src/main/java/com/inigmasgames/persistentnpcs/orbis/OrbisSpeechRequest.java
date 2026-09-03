package com.inigmasgames.persistentnpcs.orbis;

import com.inigmasgames.persistentnpcs.voice.SpeechProjection;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Complete immutable speech admission request for one current NPC branch epoch. */
public record OrbisSpeechRequest(TurnId turnId, BranchId branchId, ResponseId responseId,
        UUID npcStableId, String npcName, long branchEpoch, UUID playerStableId,
        SpeechProjection projection, List<CanonicalSpeechChunk> chunks,
        Instant decisionCommittedAt) {
    public OrbisSpeechRequest {
        if (turnId == null || branchId == null || responseId == null
                || npcStableId == null || branchEpoch < 1 || decisionCommittedAt == null) {
            throw new IllegalArgumentException("complete Orbis speech ownership required");
        }
        npcName = npcName == null ? "" : npcName;
        projection = projection == null ? SpeechProjection.NORMAL : projection;
        chunks = List.copyOf(chunks == null ? List.of() : chunks);
        for (int index = 0; index < chunks.size(); index++) {
            if (chunks.get(index).index() != index) {
                throw new IllegalArgumentException("canonical chunks must be ordered");
            }
        }
    }
}
