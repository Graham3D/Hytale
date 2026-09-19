package com.inigmasgames.persistentnpcs.social;

import java.time.Instant;
import java.util.UUID;

public record GossipRecord(
        UUID gossipId,
        String fact,
        UUID originalEventId,
        UUID originalSourceEntityId,
        UUID toldByEntityId,
        UUID toldToNpcId,
        Instant receivedAt,
        double confidence) {

    public GossipRecord normalized() {
        return new GossipRecord(gossipId, fact, originalEventId,
                originalSourceEntityId, toldByEntityId, toldToNpcId,
                receivedAt == null ? Instant.now() : receivedAt,
                Math.max(0.0, Math.min(1.0, confidence)));
    }
}
