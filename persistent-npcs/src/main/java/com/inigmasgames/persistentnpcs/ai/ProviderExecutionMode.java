package com.inigmasgames.persistentnpcs.ai;

public enum ProviderExecutionMode {
    LOCAL,
    REMOTE;

    public static ProviderExecutionMode parse(String value) {
        return value == null || value.isBlank() ? LOCAL
                : valueOf(value.strip().toUpperCase(java.util.Locale.ROOT));
    }
}
