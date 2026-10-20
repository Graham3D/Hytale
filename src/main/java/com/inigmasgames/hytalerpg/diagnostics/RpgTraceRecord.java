package com.inigmasgames.hytalerpg.diagnostics;

import com.inigmasgames.hytalerpg.phase00.BuildIdentity;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

public record RpgTraceRecord(String timestamp, String rpgRevision, String buildVersion, String hytaleBuild,
                             UUID playerUuid, RpgTraceEventType eventType, String correlationId,
                             Map<String, Object> details) {
    public RpgTraceRecord { details = Map.copyOf(details); }
    public static RpgTraceRecord create(UUID playerUuid, RpgTraceEventType eventType, String correlationId,
                                        Map<String, ?> details) {
        return new RpgTraceRecord(java.time.Instant.now().toString(), BuildIdentity.REVISION, BuildIdentity.VERSION,
                BuildIdentity.HYTALE_VERSION, playerUuid, eventType, correlationId,
                new LinkedHashMap<>(details));
    }
}
