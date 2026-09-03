package com.inigmasgames.persistentnpcs.sentinel;

import static com.inigmasgames.persistentnpcs.sentinel.SentinelContracts.*;
import java.util.LinkedHashMap;
import java.util.Map;

/** Bounded, disposable diagnostic projection; never owns gameplay state. */
public final class SentinelStateProjection {
    private static final int MAX_SCOPES = 128;
    private static final int MAX_SIGNATURES = 256;
    private final LinkedHashMap<String, Health> health = bounded(MAX_SCOPES);
    private final LinkedHashMap<String, Integer> occurrences = bounded(MAX_SIGNATURES);
    private final LinkedHashMap<String, Map<String, String>> latestProofs = bounded(MAX_SCOPES);
    private long passCount;
    private long evaluatedCount;
    private long totalEvaluationMicros;
    private String lastSignature = "none";

    public synchronized void observe(SentinelObservation observation) {
        if (observation == null) return;
        // Facts are already bounded semantic proofs (hashes/counts/booleans), never prompts.
        latestProofs.put(observation.scopeKey() + '@' + observation.boundary(),
                observation.facts().entrySet().stream().limit(32).collect(
                        java.util.stream.Collectors.toUnmodifiableMap(
                                Map.Entry::getKey, Map.Entry::getValue)));
    }

    private static <K, V> LinkedHashMap<K, V> bounded(int maximum) {
        return new LinkedHashMap<>(16, .75f, true) {
            @Override protected boolean removeEldestEntry(Map.Entry<K, V> eldest) {
                return size() > maximum;
            }
        };
    }

    public synchronized Health apply(InvariantDefinition definition,
            InvariantVerdict verdict, String scopeKey, String signature) {
        evaluatedCount++;
        totalEvaluationMicros += verdict.evaluationMicros();
        if (verdict.status() == VerdictStatus.PASS
                || verdict.status() == VerdictStatus.NOT_APPLICABLE) passCount++;
        if (verdict.status() != VerdictStatus.FAIL
                && verdict.status() != VerdictStatus.EVALUATOR_ERROR) {
            return health.getOrDefault(scopeKey, Health.HEALTHY);
        }
        Health projected = verdict.status() == VerdictStatus.EVALUATOR_ERROR
                || verdict.confidence() == Confidence.SUSPECT ? Health.SUSPECT
                : definition.severity() == Severity.FATAL_OPERATOR_REQUIRED
                        ? Health.FAILED_OPERATOR_REQUIRED : Health.DEGRADED;
        health.put(scopeKey, projected);
        if (signature != null && !signature.isBlank()) {
            occurrences.merge(signature, 1, Integer::sum);
            lastSignature = signature;
        }
        return projected;
    }

    public synchronized Snapshot snapshot() {
        return new Snapshot(Map.copyOf(health), Map.copyOf(occurrences),
                Map.copyOf(latestProofs), passCount, evaluatedCount,
                totalEvaluationMicros, lastSignature);
    }

    public synchronized void health(String scopeKey, Health value) {
        if (scopeKey != null && value != null) health.put(scopeKey, value);
    }

    public record Snapshot(Map<String, Health> scopedHealth,
            Map<String, Integer> signatureOccurrences,
            Map<String, Map<String, String>> latestProofs, long healthyVerdicts,
            long evaluatedVerdicts, long totalEvaluationMicros, String lastSignature) {
        public int activeViolations() { return (int) scopedHealth.values().stream()
                .filter(value -> value != Health.HEALTHY).count(); }
        public double averageEvaluationMicros() {
            return evaluatedVerdicts == 0 ? 0d
                    : (double) totalEvaluationMicros / evaluatedVerdicts;
        }
        public int lastOccurrenceCount() {
            return signatureOccurrences.getOrDefault(lastSignature, 0);
        }
    }
}
