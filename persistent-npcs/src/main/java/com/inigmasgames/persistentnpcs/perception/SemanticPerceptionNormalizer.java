package com.inigmasgames.persistentnpcs.perception;

import com.inigmasgames.persistentnpcs.profile.NpcProfile;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;

/** Deterministic raw-engine-observation to compact NPC-understanding boundary. */
public final class SemanticPerceptionNormalizer {
    private static final int MAX_OBJECTS = 6;
    private static final int MAX_ENTITIES = 4;
    private static final int MAX_ITEMS = 4;

    public SemanticWorldModel normalize(
            RawPerceptionSnapshot raw, NpcProfile profile, String playerMessage) {
        NpcPerceptionSnapshot snapshot = raw.engineSnapshot();
        EnvironmentSnapshot environment = snapshot.environment();
        boolean loaded = snapshot.npcEntityId() != null;
        String location = semanticLocation(environment);
        List<String> players = concepts(snapshot.nearbyPlayers(), "player");
        List<String> npcs = concepts(snapshot.nearbyNpcs(), "NPC");
        List<String> hostiles = concepts(snapshot.nearbyHostiles(), "hostile creature");
        java.util.ArrayList<String> objects = new java.util.ArrayList<>(environment == null
                ? List.of() : environment.importantObjects().stream()
                        .map(this::featureConcept).distinct().limit(MAX_OBJECTS).toList());
        List<String> nature = environment == null ? List.of()
                : environment.naturalFeatures().stream()
                        .map(this::featureConcept).distinct().limit(MAX_OBJECTS).toList();
        LinkedHashSet<String> items = new LinkedHashSet<>();
        if (snapshot.focusedPlayerHeldItem() != null) {
            items.add("the player is holding " + clean(snapshot.focusedPlayerHeldItem().displayName()));
        }
        snapshot.nearbyItems().stream().limit(MAX_ITEMS)
                .map(item -> clean(item.displayName()) + " is " + band(item.distanceMeters()))
                .forEach(items::add);
        snapshot.nearbyCraftingStations().stream().limit(2)
                .map(value -> semanticEntity(value, "workstation")).forEach(objects::add);
        snapshot.nearbyInteractables().stream().limit(2)
                .map(value -> semanticEntity(value, "usable object")).forEach(objects::add);
        String target = players.stream().filter(value -> value.contains("focused player"))
                .findFirst().orElse(players.isEmpty() ? "none" : players.getFirst());
        SemanticSelfState self = new SemanticSelfState(
                profile == null ? "unknown NPC" : profile.name(), loaded,
                loaded ? "standing at the captured ECS position" : "physical position unavailable",
                location, loaded ? "present and able to converse" : "not physically loaded",
                "social attention", "none", target,
                java.util.stream.Stream.concat(players.stream(), npcs.stream())
                        .limit(MAX_ENTITIES).toList());
        return new SemanticWorldModel(self, snapshot.gameTime(),
                environment == null ? "surroundings unclear" : clean(environment.terrain()),
                List.copyOf(objects), players, npcs, hostiles,
                List.copyOf(items), nature, loaded);
    }

    private List<String> concepts(List<PerceivedEntity> values, String fallback) {
        return values.stream().map(value -> semanticEntity(value, fallback)).distinct()
                .limit(MAX_ENTITIES).toList();
    }

    private String semanticEntity(PerceivedEntity value, String fallback) {
        String label = clean(value.name());
        if (label.isBlank() || looksTechnical(label)) label = fallback;
        return label + " is " + band(value.distanceMeters());
    }

    private String featureConcept(EnvironmentFeature feature) {
        String label = clean(feature.label());
        if (label.isBlank() || looksTechnical(label)) label = clean(feature.category());
        String direction = feature.direction() == null || feature.direction().isBlank()
                ? "" : " to the " + clean(feature.direction());
        return label + " is " + band(feature.distanceMeters()) + direction;
    }

    private static String semanticLocation(EnvironmentSnapshot environment) {
        if (environment == null || !environment.isUsable()) return "location name unknown";
        for (EnvironmentFeature feature : environment.importantObjects()) {
            if (feature.category().equals("crafting_station")) return "near a workstation";
            if (feature.category().equals("door")) return "near a doorway";
        }
        String terrain = clean(environment.terrain());
        if (terrain.contains("constructed")) return "in a constructed area";
        if (terrain.contains("grassy")) return "in an open grassy area";
        if (terrain.contains("water")) return "near water";
        return "in the currently visible surroundings";
    }

    private static String band(double distance) {
        if (distance <= 2.0) return "within reach";
        if (distance <= 5.0) return "close by";
        if (distance <= 10.0) return "nearby";
        return "at the edge of view";
    }

    private static String clean(String value) {
        return value == null ? "" : value.replaceAll("[_/]+", " ")
                .replaceAll("(?<=[a-z])(?=[A-Z])", " ")
                .replaceAll("[^\\p{L}\\p{N}' -]", " ").replaceAll("\\s+", " ")
                .strip().toLowerCase(Locale.ROOT);
    }

    private static boolean looksTechnical(String value) {
        return value.contains("component") || value.contains("entity ref")
                || value.contains("block type") || value.contains("persistent npcs")
                || value.contains("immersive npcs");
    }
}
