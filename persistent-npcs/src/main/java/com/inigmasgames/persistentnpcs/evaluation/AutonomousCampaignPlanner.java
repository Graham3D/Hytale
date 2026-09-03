package com.inigmasgames.persistentnpcs.evaluation;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Bounded curriculum and prioritized failure-neighborhood expansion. */
public final class AutonomousCampaignPlanner {
    public enum Capability { IDENTITY, MEMORY, RECALL, PERCEPTION, SELF_STATE, ACTION,
        CORRECTION, UNCERTAINTY, SOCIAL_COGNITION, PERSISTENCE }

    public Plan plan(String id, int deterministicCases, int liveTurns,
            EvaluationContracts.ResourceBudget budget) {
        int deterministic = Math.max(0, deterministicCases);
        int live = Math.max(0, liveTurns);
        if (deterministic + live > budget.maximumTurns()) throw new IllegalArgumentException(
                "campaign exceeds maximum turns");
        if (live > budget.maximumProviderCalls()) throw new IllegalArgumentException(
                "campaign exceeds provider-call budget");
        ArrayList<Probe> probes = new ArrayList<>();
        Capability[] values = Capability.values();
        for (int index = 0; index < deterministic; index++) probes.add(new Probe(index,
                values[index % values.length], false, mutation(index), 100 - index % 7));
        for (int index = 0; index < live; index++) probes.add(new Probe(deterministic + index,
                values[index % values.length], true, mutation(index), 200 - index % 7));
        return new Plan(id, Instant.now(), List.copyOf(probes), budget);
    }

    public Report summarize(Plan plan, List<ProbeResult> results) {
        EnumMap<Capability, Integer> coverage = new EnumMap<>(Capability.class);
        results.forEach(result -> coverage.merge(result.probe().capability(), 1, Integer::sum));
        List<ProbeResult> failures = results.stream().filter(value -> !value.passed())
                .sorted(Comparator.comparingInt((ProbeResult value) ->
                        value.probe().priority()).reversed()).toList();
        return new Report(plan.id(), results.size(), Map.copyOf(coverage), failures,
                failures.isEmpty(), failures.stream().map(ProbeResult::earliestBoundary)
                        .distinct().toList());
    }

    private static String mutation(int index) {
        return switch (index % 5) {
            case 0 -> "BASE"; case 1 -> "PARAPHRASE"; case 2 -> "ENTITY_SWAP";
            case 3 -> "TEMPORAL_SHIFT"; default -> "NEGATIVE_CONTROL";
        };
    }

    public record Probe(int index, Capability capability, boolean live,
            String mutation, int priority) { }
    public record Plan(String id, Instant createdAt, List<Probe> probes,
            EvaluationContracts.ResourceBudget budget) { }
    public record ProbeResult(Probe probe, boolean passed,
            EvaluationContracts.BoundaryId earliestBoundary, String diagnostic) { }
    public record Report(String campaignId, int completed, Map<Capability, Integer> coverage,
            List<ProbeResult> prioritizedFailures, boolean passed,
            List<EvaluationContracts.BoundaryId> failureBoundaries) { }
}
