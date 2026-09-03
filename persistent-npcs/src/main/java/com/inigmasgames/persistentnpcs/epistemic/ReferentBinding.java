package com.inigmasgames.persistentnpcs.epistemic;

public record ReferentBinding(String expression, String entityKey, double confidence,
        boolean ambiguous, String reason) {
    public ReferentBinding {
        expression = clean(expression); entityKey = clean(entityKey); reason = clean(reason);
        confidence = Math.max(0.0, Math.min(1.0, confidence));
    }
    private static String clean(String value) { return value == null ? "" : value.strip(); }
}
