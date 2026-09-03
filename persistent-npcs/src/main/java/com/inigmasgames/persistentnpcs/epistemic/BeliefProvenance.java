package com.inigmasgames.persistentnpcs.epistemic;

import java.util.List;
import java.util.UUID;

/** Immutable evidence authority; confidence never substitutes for this source class. */
public record BeliefProvenance(EvidenceSourceKind sourceKind, UUID sourceActorId,
        List<String> evidenceIds, boolean generatedSpeechOnly,
        boolean authoritativeActionResult) {
    public BeliefProvenance {
        if (sourceKind == null) throw new IllegalArgumentException("source kind required");
        evidenceIds = List.copyOf(evidenceIds == null ? List.of() : evidenceIds);
    }
    public int authorityRank() {
        if (generatedSpeechOnly) return 0;
        return switch (sourceKind) {
            case AUTHORED_CANON -> 100;
            case ACTION_RESULT, PROCEDURAL_OUTCOME -> authoritativeActionResult ? 95 : 60;
            case DIRECT_OBSERVATION, CURRENT_WORLD_STATE, SELF_STATE -> 90;
            case RELATIONSHIP_STATE, PERSISTENT_FACT -> 80;
            case EPISODIC_MEMORY, DOCUMENTED_WORLD_LORE -> 65;
            case PLAYER_TESTIMONY, NPC_TESTIMONY -> 50;
            case DERIVED_REFLECTION -> 40;
            case CONVERSATION_WORKSPACE, ACTION_CAPABILITY -> 20;
        };
    }
}
