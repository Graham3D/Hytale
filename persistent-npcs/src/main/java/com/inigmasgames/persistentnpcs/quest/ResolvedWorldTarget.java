package com.inigmasgames.persistentnpcs.quest;

import java.util.Set;
import java.util.UUID;

/** Target identity and coordinates originate from the server snapshot, never the LLM. */
public record ResolvedWorldTarget(
        QuestTargetKind kind,
        String authoritativeId,
        String displayName,
        UUID worldId,
        Double x,
        Double y,
        Double z,
        Set<String> tags) {

    public ResolvedWorldTarget normalized() {
        if (kind == null || authoritativeId == null || authoritativeId.isBlank()) {
            throw new IllegalArgumentException("Resolved target requires kind and authoritative ID");
        }
        return new ResolvedWorldTarget(kind, authoritativeId.strip(),
                displayName == null || displayName.isBlank()
                        ? authoritativeId.strip() : displayName.strip(),
                worldId, x, y, z,
                tags == null ? Set.of() : tags.stream()
                        .map(value -> value.toUpperCase(java.util.Locale.ROOT))
                        .collect(java.util.stream.Collectors.toUnmodifiableSet()));
    }
}
