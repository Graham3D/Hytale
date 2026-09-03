package com.inigmasgames.persistentnpcs.cognition;

import com.inigmasgames.persistentnpcs.action.NpcActionResult;

/** Deterministic failure wording; it cannot preserve an uncommitted action promise. */
public final class NpcDecisionCommitPolicy {
    private NpcDecisionCommitPolicy() { }

    public static NpcDecision truthfulFailure(NpcDecision rejected,
            NpcActionResult result) {
        if (rejected == null || result == null || result.success()) {
            throw new IllegalArgumentException("A failed decision/action result is required");
        }
        return rejected.withoutActions("I can't do that right now.");
    }
}
