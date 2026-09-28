package com.inigmasgames.hytalerpg.difficulty;

import com.google.gson.Gson;
import com.inigmasgames.hytalerpg.progress.ProgressionProfiles;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Versioned proposed campaign bands coexist with, and do not overwrite, the retained Normal pilot. */
public record DifficultyDefinitions(int schemaVersion, String profileId, List<Region> regions) {
    public record Region(String id, Map<DifficultyId, Band> bands) {
        public Region {
            if (id == null || id.isBlank()) throw new IllegalArgumentException("INVALID_REGION");
            bands = Map.copyOf(bands);
            if (!bands.keySet().equals(EnumSet.allOf(DifficultyId.class))) throw new IllegalArgumentException("INCOMPLETE_REGION_MODES");
        }
    }
    public record Band(int minimum, int maximum) {
        public Band { if (minimum < 1 || maximum > 99 || maximum < minimum) throw new IllegalArgumentException("INVALID_DIFFICULTY_BAND"); }
        public boolean contains(int level) { return level >= minimum && level <= maximum; }
    }
    public DifficultyDefinitions {
        if (schemaVersion != 1 || profileId == null || profileId.isBlank()) throw new IllegalArgumentException("DIFFICULTY_DEFINITION_SCHEMA");
        regions = List.copyOf(regions);
        if (regions.size() != 4 || regions.stream().map(Region::id).distinct().count() != regions.size()) throw new IllegalArgumentException("INVALID_DIFFICULTY_REGIONS");
    }
    public static DifficultyDefinitions load() {
        try (var stream = DifficultyDefinitions.class.getResourceAsStream("/rpg/progression/difficulty-regions.json")) {
            if (stream == null) throw new IllegalStateException("MISSING_DIFFICULTY_REGIONS");
            return new Gson().fromJson(new InputStreamReader(stream, StandardCharsets.UTF_8), DifficultyDefinitions.class);
        } catch (java.io.IOException error) { throw new IllegalStateException(error); }
    }
    public Band band(String region, DifficultyId mode) {
        return regions.stream().filter(r -> r.id().equals(region)).findFirst().orElseThrow().bands().get(mode);
    }
    public ProgressionProfiles.Difficulty scalar(DifficultyId mode) { return ProgressionProfiles.load().difficulty(mode.name()); }
}
