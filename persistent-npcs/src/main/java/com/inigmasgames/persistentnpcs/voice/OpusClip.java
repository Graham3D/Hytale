package com.inigmasgames.persistentnpcs.voice;

import java.nio.file.Path;
import java.util.List;

public record OpusClip(
        List<byte[]> frames,
        int sourceRate,
        long ttsMillis,
        long encodeMillis,
        long conditioningMillis,
        boolean conditioningCached,
        long workerQueueMillis,
        long cudaAllocatedMegabytes,
        long cudaReservedMegabytes,
        long cudaPeakAllocatedMegabytes,
        long cudaPeakReservedMegabytes,
        int modelLoadCount,
        String device,
        Path reference,
        long workerPid,
        boolean modelResident,
        int conditioningCacheEntries) {

    public OpusClip {
        frames = frames == null ? List.of() : frames.stream()
                .map(byte[]::clone).toList();
    }

    public OpusClip(List<byte[]> frames, int sourceRate, long ttsMillis, long encodeMillis,
            String device, Path reference) {
        this(frames, sourceRate, ttsMillis, encodeMillis, 0, false,
                0, 0, 0, 0, 0, 1, device, reference, -1, false, 0);
    }

    /** Source compatibility for callers compiled against the pre-telemetry shape. */
    public OpusClip(List<byte[]> frames, int sourceRate, long ttsMillis, long encodeMillis,
            long conditioningMillis, boolean conditioningCached, long workerQueueMillis,
            long cudaAllocatedMegabytes, long cudaReservedMegabytes, int modelLoadCount,
            String device, Path reference) {
        this(frames, sourceRate, ttsMillis, encodeMillis, conditioningMillis,
                conditioningCached, workerQueueMillis, cudaAllocatedMegabytes,
                cudaReservedMegabytes, 0, 0, modelLoadCount, device, reference,
                -1, false, 0);
    }
}
