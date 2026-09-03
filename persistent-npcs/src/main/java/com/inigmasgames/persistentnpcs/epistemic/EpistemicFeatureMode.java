package com.inigmasgames.persistentnpcs.epistemic;

import java.util.Locale;

/** Rollout switch. E3 authority remains limited to supported foreground routes. */
public enum EpistemicFeatureMode {
    OFF, SHADOW, AUTHORITATIVE;

    public static final String PROPERTY = "immersivenpcs.epistemic.mode";

    public static EpistemicFeatureMode configured() {
        String value = System.getProperty(PROPERTY, "AUTHORITATIVE");
        try {
            return valueOf(value.strip().toUpperCase(Locale.ROOT));
        } catch (RuntimeException ignored) {
            return SHADOW;
        }
    }

    /** E0-E2 cannot acquire authority even if a future value is configured early. */
    public EpistemicFeatureMode effectiveForE0() {
        return this == AUTHORITATIVE ? SHADOW : this;
    }

    /** E3 authority is deliberately limited to the proven foreground corpus. */
    public EpistemicFeatureMode effectiveForE3(EpistemicQueryKind kind,
            boolean inputQualityConcern) {
        if (this != AUTHORITATIVE) return this;
        if (inputQualityConcern) return AUTHORITATIVE;
        return kind == null || kind == EpistemicQueryKind.UNRESOLVED ? SHADOW : AUTHORITATIVE;
    }
}
