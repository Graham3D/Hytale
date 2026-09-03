package com.inigmasgames.persistentnpcs.epistemic;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Stable semantic write proposal; contains no runtime/world/provider handles. */
public record BeliefProposal(UUID assertionId, UUID ownerNpcId, UUID subjectId,
        String subject, String predicate, String value, String statement,
        BeliefAssertion.Polarity polarity, EpistemicStatus status, double confidence,
        BeliefProvenance provenance, BeliefAssertion.TemporalScope temporalScope,
        BeliefAssertion.AssertionScope assertionScope, List<UUID> supportIds,
        Instant learnedAt) { }
