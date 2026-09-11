package com.inigmasgames.hytalerpg.diagnostics;

/** Integrity metadata for one immutable trace archive segment. */
public record TraceArchiveManifest(
        int schemaVersion,
        String traceKind,
        String segmentId,
        long segmentSequence,
        String createdAt,
        String closedAt,
        long eventCount,
        String firstEventTimestamp,
        String lastEventTimestamp,
        Long firstTraceSequence,
        Long lastTraceSequence,
        long uncompressedBytes,
        long compressedBytes,
        String compression,
        String uncompressedSha256,
        String compressedSha256,
        String rpgRevision,
        String buildVersion,
        String hytaleBuild,
        String archiveState) {
    public static final int SCHEMA_VERSION = 1;
    public static final String VERIFIED = "VERIFIED";
}
