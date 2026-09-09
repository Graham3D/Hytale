package com.inigmasgames.hytalerpg.diagnostics;

public enum SkillTraceLevel {
    NORMAL, DETAILED, PERFORMANCE;
    public static SkillTraceLevel parse(String value) {
        String normalized=java.util.Objects.requireNonNull(value).trim().toUpperCase(java.util.Locale.ROOT);
        return valueOf(normalized.equals("DEBUG")?"DETAILED":normalized);
    }
}
