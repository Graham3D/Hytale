package com.inigmasgames.persistentnpcs.economy;

import java.time.Instant;
import java.util.UUID;

/** Currency-neutral debt/obligation state; payment requires a real economy adapter. */
public record ObligationRecord(
        UUID obligationId,
        UUID creditorEntityId,
        UUID debtorEntityId,
        long amount,
        String unit,
        String reason,
        boolean recurring,
        Integer recurrenceGameDays,
        Instant createdAt,
        boolean settled) {
}
