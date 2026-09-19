package com.inigmasgames.persistentnpcs.autonomy;

import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Engine-owned mapping from observed semantic types to validated action affordances. */
public final class AffordanceRegistry {
    private final Map<String, List<String>> affordances = Map.ofEntries(
            Map.entry("FLOWER", List.of("INVESTIGATE", "ADMIRE")),
            Map.entry("BUSH", List.of("INVESTIGATE")),
            Map.entry("PLANT", List.of("INVESTIGATE")),
            Map.entry("ORE", List.of("INSPECT")),
            Map.entry("RARE_ORE", List.of("INSPECT")),
            Map.entry("WORKSTATION", List.of("INSPECT")),
            Map.entry("CHAIR", List.of("INSPECT", "SIT")),
            Map.entry("FOX", List.of("APPROACH_CAUTIOUSLY", "OBSERVE")),
            Map.entry("ANIMAL", List.of("OBSERVE")),
            Map.entry("NPC", List.of("OBSERVE")),
            Map.entry("RAIN", List.of("WATCH_RAIN")),
            Map.entry("STORM", List.of("OBSERVE_DANGER")),
            Map.entry("AMBIENT_STRETCH", List.of("STRETCH")),
            Map.entry("AMBIENT_HUM", List.of("HUM")));

    public List<String> forType(String semanticType) {
        return affordances.getOrDefault(semanticType == null ? ""
                : semanticType.toUpperCase(Locale.ROOT), List.of());
    }
}
