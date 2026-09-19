package com.inigmasgames.persistentnpcs.cognition;

import java.util.List;

/** Inspectable utility result; concise facts only, never a reasoning transcript. */
public record IntentCandidate(
        GroundedIntent intent,
        int priority,
        double utility,
        String basis,
        List<String> evidenceRefs) {
    public IntentCandidate {
        evidenceRefs = List.copyOf(evidenceRefs == null ? List.of() : evidenceRefs);
        basis = basis == null ? "" : basis;
    }
}
