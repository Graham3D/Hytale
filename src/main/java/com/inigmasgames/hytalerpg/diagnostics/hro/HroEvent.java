package com.inigmasgames.hytalerpg.diagnostics.hro;

import java.util.Map;

/** Scalar-only immutable owner snapshot. Unknown native identities remain null. */
public record HroEvent(int schemaVersion, String runId, long sequence, long utcMillis, long monotonicNanos,
                       String contract, String provider, String boundary, String operationId,
                       String worldSegmentId, Long connectionId, Long nativeTick, Long stateRevision,
                       Map<String, String> facts) {
    public HroEvent { facts = Map.copyOf(facts); }
}
