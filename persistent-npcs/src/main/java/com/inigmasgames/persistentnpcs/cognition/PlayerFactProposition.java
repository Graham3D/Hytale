package com.inigmasgames.persistentnpcs.cognition;

import java.util.UUID;

/** Compact semantic form of an explicit player-reported proposition. */
public record PlayerFactProposition(
        UUID subjectEntityId,
        String subject,
        String predicate,
        String object,
        String semanticLocation,
        String temporalReference,
        String proposition,
        double confidence) {
}
