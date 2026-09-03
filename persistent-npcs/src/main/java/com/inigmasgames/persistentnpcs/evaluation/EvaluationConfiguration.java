package com.inigmasgames.persistentnpcs.evaluation;

import java.util.Locale;

/** Explicit development-only switch. Shipping runtime is OFF unless a tool opts in. */
public final class EvaluationConfiguration {
    public static final String MODE_PROPERTY = "immersivenpcs.evaluation.mode";

    private EvaluationConfiguration() { }

    public static EvaluationContracts.EvaluationMode configuredMode() {
        String raw = System.getProperty(MODE_PROPERTY, "OFF").strip();
        try {
            return EvaluationContracts.EvaluationMode.valueOf(raw.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException invalid) {
            throw new IllegalStateException("Invalid evaluation mode: " + raw, invalid);
        }
    }

    public static void requireToolMode(EvaluationContracts.EvaluationMode requested) {
        if (requested == null || requested == EvaluationContracts.EvaluationMode.OFF) {
            throw new IllegalArgumentException("An explicit evaluation mode is required");
        }
        // This method is called only by project-owned evaluation entry points. Production
        // plugin composition never consults it and never starts an evaluation worker.
    }
}
