package com.inigmasgames.persistentnpcs.sentinel;

import static com.inigmasgames.persistentnpcs.sentinel.SentinelContracts.*;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Versioned deterministic allowlist. Actions are requests to existing authorities. */
public final class RecoveryPolicyRegistry {
    public static final String VERSION = "S2.1";
    public record Policy(String id, int version, Set<String> invariants, Scope maximumScope,
            int maxAttemptsPerTurn, int maxAttemptsPerEpoch, Duration softDeadline,
            Duration hardDeadline, List<RecoveryAction> actions,
            List<String> postconditions) {
        public Policy {
            invariants = Set.copyOf(invariants); actions = List.copyOf(actions);
            postconditions = List.copyOf(postconditions);
            if (maxAttemptsPerTurn < 0 || maxAttemptsPerTurn > 1) throw new
                    IllegalArgumentException("shared recovery allowance permits at most one");
        }
    }
    private final Map<String, Policy> byInvariant;

    public RecoveryPolicyRegistry() {
        var values = List.of(
            p("S2_PLAN_CORRECT", Set.of("PLAN-002", "PLAN-004"), Scope.TURN,
                    RecoveryAction.REJECT_SIDE_EFFECT, RecoveryAction.PRUNE_CONTEXT_AND_RECOMPILE),
            p("S2_ROUTE_AUTHORITY", Set.of("PLAN-003"), Scope.ROUTE,
                    RecoveryAction.REJECT_SIDE_EFFECT, RecoveryAction.QUARANTINE_ROUTE),
            p("S2_PROVIDER_OWNERSHIP", Set.of("PROV-001", "PROV-002"), Scope.PROVIDER,
                    RecoveryAction.REJECT_SIDE_EFFECT, RecoveryAction.CANCEL_PROVIDER_REQUEST,
                    RecoveryAction.WAIT_FOR_PROVIDER_DRAIN, RecoveryAction.QUARANTINE_PROVIDER),
            p("S2_RESOURCE_PROFILE", Set.of("RES-001", "RES-002"), Scope.RESOURCE_PROFILE,
                    RecoveryAction.FAIL_FAST_WITH_SAFE_REASON,
                    RecoveryAction.DOWNGRADE_TO_APPROVED_RESOURCE_PROFILE),
            p("S2_EPISTEMIC_CONTAIN", Set.of("EPI-001", "EPI-002", "EPI-003",
                    "SPEECH-001"), Scope.ROUTE, RecoveryAction.REJECT_SIDE_EFFECT,
                    RecoveryAction.DROP_UNSUPPORTED_CLAUSE,
                    RecoveryAction.REALIZE_SAFE_ANSWERPLAN_FALLBACK),
            p("S2_SPEECH_CONTAIN", Set.of("SPEECH-002"), Scope.TURN,
                    RecoveryAction.REJECT_SIDE_EFFECT,
                    RecoveryAction.CLOSE_PLAYBACK_PRESERVE_PARTIAL),
            p("S2_ACTION_CONTAIN", Set.of("ACT-001"), Scope.TURN,
                    RecoveryAction.REJECT_SIDE_EFFECT),
            p("S2_PERSISTENCE_GATE", Set.of("PERSIST-001", "PERSIST-002", "PERSIST-003",
                    "PERSIST-004", "PERSIST-005"), Scope.PERSISTENCE_STREAM,
                    RecoveryAction.REJECT_DURABLE_WRITE,
                    RecoveryAction.DISABLE_PERSISTENCE_WRITES_READ_ONLY));
        var map = new LinkedHashMap<String, Policy>();
        for (Policy value : values) for (String invariant : value.invariants()) {
            if (map.put(invariant, value) != null) throw new IllegalStateException(
                    "duplicate recovery policy for " + invariant);
        }
        byInvariant = Map.copyOf(map);
    }
    private static Policy p(String id, Set<String> invariants, Scope scope,
            RecoveryAction... actions) {
        return new Policy(id, 1, invariants, scope, 1, 1, Duration.ofMillis(50),
                Duration.ofSeconds(2), List.of(actions), List.of(
                        "ORIGINAL_INVARIANT_PASSES", "EXACT_CLEANUP",
                        "NEXT_USE_AVAILABLE"));
    }
    public Policy forInvariant(String id) { return byInvariant.get(id); }
    public Map<String, Policy> all() { return byInvariant; }
}
