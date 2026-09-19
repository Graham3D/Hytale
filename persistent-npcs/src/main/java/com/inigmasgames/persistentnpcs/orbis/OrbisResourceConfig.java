package com.inigmasgames.persistentnpcs.orbis;

import com.inigmasgames.persistentnpcs.ai.ExecutionPlacement;
import java.util.Map;

/** Small, persistent, hardware-agnostic policy surface. */
public record OrbisResourceConfig(
        int schemaVersion,
        ResourcePolicy policy,
        Map<String, ExecutionPlacement> backendOverrides,
        int maximumQueuedRequests,
        int maximumConcurrentStt,
        int maximumConcurrentLlm,
        int maximumConcurrentTts,
        int maximumConcurrentBackground,
        int maximumConcurrentLocalGpu,
        int gpuPressureUtilizationPercent,
        int vramPressureUsedPercent,
        long minimumFreeRamMiB,
        long defaultAdmissionTimeoutMillis,
        long hytaleGpuSafetyReserveMiB) {

    public OrbisResourceConfig(int schemaVersion, ResourcePolicy policy,
            Map<String, ExecutionPlacement> backendOverrides, int maximumQueuedRequests,
            int maximumConcurrentStt, int maximumConcurrentLlm, int maximumConcurrentTts,
            int maximumConcurrentBackground, int maximumConcurrentLocalGpu,
            int gpuPressureUtilizationPercent, int vramPressureUsedPercent,
            long minimumFreeRamMiB, long defaultAdmissionTimeoutMillis) {
        this(schemaVersion, policy, backendOverrides, maximumQueuedRequests,
                maximumConcurrentStt, maximumConcurrentLlm, maximumConcurrentTts,
                maximumConcurrentBackground, maximumConcurrentLocalGpu,
                gpuPressureUtilizationPercent, vramPressureUsedPercent,
                minimumFreeRamMiB, defaultAdmissionTimeoutMillis, 512);
    }

    public OrbisResourceConfig validated() {
        return new OrbisResourceConfig(Math.max(2, schemaVersion),
                policy == null ? ResourcePolicy.BALANCED : policy,
                backendOverrides == null ? Map.of() : Map.copyOf(backendOverrides),
                Math.max(4, maximumQueuedRequests), Math.max(1, maximumConcurrentStt),
                Math.max(1, maximumConcurrentLlm), Math.max(1, maximumConcurrentTts),
                Math.max(1, maximumConcurrentBackground),
                Math.max(1, maximumConcurrentLocalGpu),
                clamp(gpuPressureUtilizationPercent, 50, 100),
                clamp(vramPressureUsedPercent, 50, 100),
                Math.max(0, minimumFreeRamMiB),
                Math.max(100, defaultAdmissionTimeoutMillis),
                schemaVersion < 2 || hytaleGpuSafetyReserveMiB <= 0
                        ? 512 : hytaleGpuSafetyReserveMiB);
    }

    public static OrbisResourceConfig defaults() {
        return new OrbisResourceConfig(2, ResourcePolicy.BALANCED, Map.of(),
                32, 2, 1, 1, 1, 1, 92, 88, 1024, 5_000, 512).validated();
    }

    private static int clamp(int value, int minimum, int maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }
}
