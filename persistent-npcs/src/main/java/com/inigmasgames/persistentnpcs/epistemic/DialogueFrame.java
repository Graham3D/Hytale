package com.inigmasgames.persistentnpcs.epistemic;

import java.util.List;

/** Stable semantic DTO; contains no ECS/provider handles. */
public record DialogueFrame(int schemaVersion, DialogueAct act,
        ExpectedAnswerKind expectedAnswer, String subjectKey, String predicateKey,
        String objectKey, String targetKey, List<ReferentBinding> referentBindings,
        boolean ambiguous, String ambiguityReason, String priorPropositionBinding,
        String requestedAction, boolean inputQualityConcern, double confidence,
        List<String> signals) {
    public static final int SCHEMA_VERSION = 1;
    public DialogueFrame {
        if (schemaVersion < 1 || act == null || expectedAnswer == null) {
            throw new IllegalArgumentException("versioned dialogue frame required");
        }
        subjectKey = clean(subjectKey); predicateKey = clean(predicateKey);
        objectKey = clean(objectKey); targetKey = clean(targetKey);
        ambiguityReason = clean(ambiguityReason);
        priorPropositionBinding = clean(priorPropositionBinding);
        requestedAction = clean(requestedAction);
        referentBindings = List.copyOf(referentBindings == null ? List.of() : referentBindings);
        confidence = Math.max(0.0, Math.min(1.0, confidence));
        signals = List.copyOf(signals == null ? List.of() : signals);
    }
    private static String clean(String value) { return value == null ? "" : value.strip(); }
}
