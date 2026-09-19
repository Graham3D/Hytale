package com.inigmasgames.persistentnpcs.autonomy;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

public record AgentOperation(
        UUID operationId,
        String kind,
        Set<UUID> npcIds,
        Instant startedAt,
        Instant deadline,
        String authoritativeInput,
        String status,
        String result) {

    public boolean active(Instant now) {
        return "IN_PROGRESS".equals(status) && now.isBefore(deadline);
    }
}
