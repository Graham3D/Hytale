package com.inigmasgames.persistentnpcs.ai;

/** Physical inference placement reported by a provider or observed at runtime. */
public enum ExecutionPlacement {
    LOCAL_CPU,
    LOCAL_GPU,
    LOCAL_PARTIAL_GPU,
    REMOTE_LAN,
    REMOTE_CLOUD,
    UNKNOWN;

    public boolean usesLocalGpu() {
        return this == LOCAL_GPU || this == LOCAL_PARTIAL_GPU;
    }

    public boolean remote() {
        return this == REMOTE_LAN || this == REMOTE_CLOUD;
    }
}
