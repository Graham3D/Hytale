package com.inigmasgames.persistentnpcs.orbis;

import java.time.Instant;
import java.util.Map;

/** Immutable, ordered, observable fact. It never contains hidden model reasoning. */
public record OrbisEvent(long sequence, Instant at, OrbisEventType type,
        TurnId turnId, BranchId branchId, ResponseId responseId, long epoch,
        ProviderRequestId providerRequestId, Map<String, String> facts) {
    public OrbisEvent {
        if (sequence < 1 || at == null || type == null) {
            throw new IllegalArgumentException("sequenced Orbis event required");
        }
        facts = facts == null ? Map.of() : Map.copyOf(facts);
    }
}
