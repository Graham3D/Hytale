package com.inigmasgames.persistentnpcs.perception;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;

/** Compact NPC-facing understanding. It intentionally contains no engine IDs or raw samples. */
public record SemanticWorldModel(
        SemanticSelfState selfState,
        LocalDateTime worldTime,
        String terrain,
        List<String> meaningfulObjects,
        List<String> visiblePlayers,
        List<String> visibleNpcs,
        List<String> visibleHostiles,
        List<String> usableItems,
        List<String> naturalFeatures,
        boolean authoritative,
        KnownNpcLocatorResult knownNpcLocator) {

    public SemanticWorldModel(
            SemanticSelfState selfState, LocalDateTime worldTime, String terrain,
            List<String> meaningfulObjects, List<String> visiblePlayers,
            List<String> visibleNpcs, List<String> visibleHostiles, List<String> usableItems,
            List<String> naturalFeatures, boolean authoritative) {
        this(selfState, worldTime, terrain, meaningfulObjects, visiblePlayers, visibleNpcs,
                visibleHostiles, usableItems, naturalFeatures, authoritative, null);
    }

    public SemanticWorldModel {
        meaningfulObjects = immutable(meaningfulObjects);
        visiblePlayers = immutable(visiblePlayers);
        visibleNpcs = immutable(visibleNpcs);
        visibleHostiles = immutable(visibleHostiles);
        usableItems = immutable(usableItems);
        naturalFeatures = immutable(naturalFeatures);
        terrain = terrain == null || terrain.isBlank() ? "surroundings unclear" : terrain.strip();
    }

    public SemanticWorldModel withSelfState(SemanticSelfState state) {
        return new SemanticWorldModel(state, worldTime, terrain, meaningfulObjects,
                visiblePlayers, visibleNpcs, visibleHostiles, usableItems, naturalFeatures,
                authoritative, knownNpcLocator);
    }

    public SemanticWorldModel withKnownNpcLocator(KnownNpcLocatorResult result) {
        return new SemanticWorldModel(selfState, worldTime, terrain, meaningfulObjects,
                visiblePlayers, visibleNpcs, visibleHostiles, usableItems, naturalFeatures,
                authoritative, result);
    }

    /** Question-relevant prose only; no UUIDs, coordinates, sample counts, or class names. */
    public String promptBlock(String playerMessage, boolean includeHeldItem) {
        String query = playerMessage == null ? "" : playerMessage.toLowerCase(Locale.ROOT);
        boolean surroundings = contains(query, "see", "around", "near", "where", "place",
                "environment", "surround", "door", "forge", "water", "plant", "outside",
                "inside", "street");
        StringBuilder text = new StringBuilder("AUTHORITATIVE SELF STATE:\n")
                .append(selfState.promptBlock());
        if (worldTime != null) {
            text.append("\nCurrent game time: ")
                    .append("%02d:%02d".formatted(worldTime.getHour(), worldTime.getMinute()))
                    .append('.');
        }
        if (!visibleHostiles.isEmpty()) {
            text.append("\nImmediate danger: ").append(String.join(", ", visibleHostiles))
                    .append('.');
        }
        if (surroundings) {
            text.append("\nSEMANTIC SURROUNDINGS:\nTerrain: ").append(terrain).append('.');
            append(text, "Meaningful nearby objects", meaningfulObjects);
            append(text, "Visible people", visiblePlayers);
            append(text, "Visible NPCs", visibleNpcs);
            append(text, "Natural features", naturalFeatures);
        } else {
            append(text, "Relevant visible people", visiblePlayers.stream().limit(2).toList());
        }
        if (includeHeldItem) append(text, "Relevant usable items", usableItems);
        if (knownNpcLocator != null) {
            text.append("\nKNOWN NPC LOCATOR: ").append(knownNpcLocator.semanticBlock());
        }
        if (!authoritative) text.append("\nSome physical details are unavailable; admit uncertainty.");
        return text.toString();
    }

    public String inspectorBlock() {
        return selfState.promptBlock() + "\nTerrain: " + terrain
                + "\nObjects: " + list(meaningfulObjects)
                + "\nPlayers: " + list(visiblePlayers)
                + "\nNPCs: " + list(visibleNpcs)
                + "\nHostiles: " + list(visibleHostiles)
                + "\nUsable items: " + list(usableItems)
                + "\nNatural features: " + list(naturalFeatures)
                + "\nKnown NPC locator: " + (knownNpcLocator == null
                        ? "not requested" : knownNpcLocator.semanticBlock());
    }

    private static void append(StringBuilder target, String label, List<String> values) {
        if (!values.isEmpty()) target.append("\n").append(label).append(": ")
                .append(String.join(", ", values)).append('.');
    }

    private static String list(List<String> values) {
        return values.isEmpty() ? "none" : String.join(", ", values);
    }

    private static boolean contains(String text, String... tokens) {
        for (String token : tokens) if (text.contains(token)) return true;
        return false;
    }

    private static List<String> immutable(List<String> values) {
        return List.copyOf(values == null ? List.of() : values);
    }
}
