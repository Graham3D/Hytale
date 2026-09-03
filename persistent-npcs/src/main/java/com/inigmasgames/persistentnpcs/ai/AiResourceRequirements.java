package com.inigmasgames.persistentnpcs.ai;

/** Secret-free provider resource metadata consumed by the Orbis admission authority. */
public record AiResourceRequirements(
        ExecutionPlacement placement,
        String backend,
        long estimatedRamMiB,
        long estimatedVramMiB,
        int concurrencyLimit,
        boolean streaming,
        boolean cancellable,
        long expectedLatencyMillis,
        long residentVramMiB,
        long incrementalVramMiB,
        long temporaryVramMiB) {

    public AiResourceRequirements {
        placement = placement == null ? ExecutionPlacement.UNKNOWN : placement;
        backend = backend == null || backend.isBlank() ? "UNKNOWN" : backend;
        estimatedRamMiB = Math.max(0, estimatedRamMiB);
        estimatedVramMiB = Math.max(0, estimatedVramMiB);
        concurrencyLimit = Math.max(1, concurrencyLimit);
        expectedLatencyMillis = Math.max(0, expectedLatencyMillis);
        residentVramMiB = Math.max(0, residentVramMiB);
        incrementalVramMiB = Math.max(0, incrementalVramMiB);
        temporaryVramMiB = Math.max(0, temporaryVramMiB);
    }

    public AiResourceRequirements(ExecutionPlacement placement, String backend,
            long estimatedRamMiB, long estimatedVramMiB, int concurrencyLimit,
            boolean streaming, boolean cancellable, long expectedLatencyMillis) {
        this(placement, backend, estimatedRamMiB, estimatedVramMiB, concurrencyLimit,
                streaming, cancellable, expectedLatencyMillis, estimatedVramMiB,
                estimatedVramMiB, 0);
    }
}
