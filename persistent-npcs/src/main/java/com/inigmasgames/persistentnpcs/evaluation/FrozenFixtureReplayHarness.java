package com.inigmasgames.persistentnpcs.evaluation;

import com.inigmasgames.persistentnpcs.cognition.NpcGroundingClaimValidator;
import java.util.List;

/** Executes reviewed boundary fixtures against the production validator that owns the boundary. */
public final class FrozenFixtureReplayHarness {
    public ReplayResult replay(FrozenConversationFixture fixture) {
        if (fixture == null || !"PROMOTED_REVIEWED".equals(fixture.reviewStatus())) {
            throw new IllegalArgumentException("reviewed frozen fixture required");
        }
        if (fixture.expectedBoundaries().containsKey("CLAIM_FIREWALL")) {
            String output = required(fixture, "providerOutput");
            List<String> evidence = fixture.input().getOrDefault("groundingEvidence", "")
                    .lines().map(String::strip).filter(value -> !value.isBlank()).toList();
            var assessments = new NpcGroundingClaimValidator().validate(output, evidence);
            boolean rejected = assessments.stream().anyMatch(value -> !value.valid());
            boolean exercisesForbidden = fixture.forbiddenClaims().stream().allMatch(forbidden ->
                    output.toLowerCase(java.util.Locale.ROOT).contains(
                            forbidden.toLowerCase(java.util.Locale.ROOT)));
            return new ReplayResult(rejected && exercisesForbidden, "CLAIM_FIREWALL",
                    assessments.toString());
        }
        throw new UnsupportedOperationException("No replay owner for fixture boundaries: "
                + fixture.expectedBoundaries().keySet());
    }

    private static String required(FrozenConversationFixture fixture, String key) {
        String value = fixture.input().get(key);
        if (value == null || value.isBlank()) throw new IllegalStateException(
                "Frozen fixture " + fixture.fixtureId() + " lacks " + key);
        return value;
    }

    public record ReplayResult(boolean passed, String boundary, String diagnostic) { }
}
