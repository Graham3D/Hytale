package com.inigmasgames.persistentnpcs.sentinel;

import static com.inigmasgames.persistentnpcs.sentinel.SentinelContracts.Boundary;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Disposable immutable proof projection supplied by current authoritative owners. */
public record SentinelObservation(Boundary boundary, String scopeKey, UUID npcId,
        List<String> correlationIds, Map<String, String> facts) {
    public SentinelObservation {
        if (boundary == null) throw new IllegalArgumentException("boundary required");
        scopeKey = scopeKey == null || scopeKey.isBlank() ? "GLOBAL:ORBIS" : scopeKey;
        correlationIds = List.copyOf(correlationIds == null ? List.of() : correlationIds);
        facts = Map.copyOf(facts == null ? Map.of() : facts);
    }
    public String fact(String key) { return facts.get(key); }
    public boolean bool(String key) { return Boolean.parseBoolean(facts.get(key)); }
    public int integer(String key, int fallback) {
        try { return Integer.parseInt(facts.get(key)); }
        catch (RuntimeException ignored) { return fallback; }
    }
}
