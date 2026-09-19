package com.inigmasgames.persistentnpcs.evaluation;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Provider-free reviewed boundary contract promoted from a verified live repair. */
public record FrozenConversationFixture(int schemaVersion, String fixtureId,
        String sourceFailureId, String sourceRunId, Instant frozenAt,
        Set<String> coverageTags, Map<String, String> input,
        Map<String, String> expectedBoundaries, Set<String> requiredPropositions,
        Set<String> forbiddenClaims, List<String> requiredVariants,
        String productionGraphHash, String reviewStatus) {
    public FrozenConversationFixture {
        if (schemaVersion != EvaluationContracts.SCHEMA_VERSION
                || fixtureId == null || !fixtureId.matches("[A-Za-z0-9_.-]{1,96}")) {
            throw new IllegalArgumentException("valid frozen fixture identity required");
        }
        coverageTags = Set.copyOf(coverageTags == null ? Set.of() : coverageTags);
        input = Map.copyOf(input == null ? Map.of() : input);
        expectedBoundaries = Map.copyOf(expectedBoundaries == null ? Map.of()
                : expectedBoundaries);
        requiredPropositions = Set.copyOf(requiredPropositions == null ? Set.of()
                : requiredPropositions);
        forbiddenClaims = Set.copyOf(forbiddenClaims == null ? Set.of() : forbiddenClaims);
        requiredVariants = List.copyOf(requiredVariants == null ? List.of() : requiredVariants);
        reviewStatus = reviewStatus == null ? "CANDIDATE" : reviewStatus;
    }
}
