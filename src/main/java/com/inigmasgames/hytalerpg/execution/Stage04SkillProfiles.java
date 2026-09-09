package com.inigmasgames.hytalerpg.execution;

import com.google.gson.Gson;
import com.inigmasgames.hytalerpg.content.RpgCatalog;
import com.inigmasgames.hytalerpg.domain.SkillId;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Versioned runtime profile registry; adding a skill is a data change, not a new executor. */
public final class Stage04SkillProfiles {
    public static final int EXPECTED_STAGE04_PILOTS = 6;
    public static final int EXPECTED_STAGE05_PILOTS = 6;
    public static final int EXPECTED_STAGE06_PROFILES = 15;
    public static final int EXPECTED_STAGE08_PROFILES = 8;
    public static final int EXPECTED_STAGE09_PROFILES = 16;
    public static final int EXPECTED_STAGE10_PROFILES = 9;
    public static final int EXPECTED_STAGE13_PROFILES = 12;
    private final Map<String, Stage04SkillProfile> profiles;

    public Stage04SkillProfiles(List<Stage04SkillProfile> profiles) {
        Map<String, Stage04SkillProfile> indexed = new LinkedHashMap<>();
        for (Stage04SkillProfile profile : profiles)
            if (indexed.put(profile.skillId(), profile) != null)
                throw new IllegalArgumentException("Duplicate runtime skill: " + profile.skillId());
        this.profiles = Map.copyOf(indexed);
    }

    public static Stage04SkillProfiles loadCanonical(RpgCatalog catalog) {
        try {
            List<Stage04SkillProfile> profiles = new ArrayList<>();
            profiles.addAll(load("/rpg/runtime/stage-04-skills.json", EXPECTED_STAGE04_PILOTS));
            profiles.addAll(load("/rpg/runtime/stage-05-projectiles.json", EXPECTED_STAGE05_PILOTS));
            profiles.addAll(load("/rpg/runtime/stage-06-area-cohort-a.json", 3));
            profiles.addAll(load("/rpg/runtime/stage-06-area-cohort-b.json", 6));
            profiles.addAll(load("/rpg/runtime/stage-06-area-cohort-c.json", 6));
            profiles.addAll(load("/rpg/runtime/stage-08-connections-cohort-a.json", 3));
            profiles.addAll(load("/rpg/runtime/stage-08-connections-cohort-b.json", 5));
            profiles.addAll(load("/rpg/runtime/stage-09-support-cohort-a.json", 3));
            profiles.addAll(load("/rpg/runtime/stage-09-support-cohort-b.json", 5));
            profiles.addAll(load("/rpg/runtime/stage-09-support-cohort-c.json", 4));
            profiles.addAll(load("/rpg/runtime/stage-09-support-cohort-d.json", 4));
            profiles.addAll(load("/rpg/runtime/stage-10-summons-cohort-a.json", 1));
            profiles.addAll(load("/rpg/runtime/stage-10-summons-cohort-b.json", 1));
            profiles.addAll(load("/rpg/runtime/stage-10-summons-cohort-c.json", 2));
            profiles.addAll(load("/rpg/runtime/stage-10-summons-cohort-d.json", 2));
            profiles.addAll(load("/rpg/runtime/stage-10-summons-cohort-e.json", 1));
            profiles.addAll(load("/rpg/runtime/stage-10-summons-cohort-f.json", 1));
            profiles.addAll(load("/rpg/runtime/stage-10-summons-cohort-g.json", 1));
            profiles.addAll(load("/rpg/runtime/stage-13-strikes-cohort-a.json", 6));
            profiles.addAll(load("/rpg/runtime/stage-13-projectiles-cohort-b.json", 6));
            Stage04SkillProfiles loaded = new Stage04SkillProfiles(profiles);
            int expected = EXPECTED_STAGE04_PILOTS + EXPECTED_STAGE05_PILOTS + EXPECTED_STAGE06_PROFILES + EXPECTED_STAGE08_PROFILES + EXPECTED_STAGE09_PROFILES + EXPECTED_STAGE10_PROFILES + EXPECTED_STAGE13_PROFILES;
            if (loaded.profiles.size() != expected)
                throw new IllegalStateException("Expected " + expected + " runtime pilot skills, got " + loaded.profiles.size());
            loaded.profiles.keySet().forEach(id -> catalog.skill(new SkillId(id)).orElseThrow(
                    () -> new IllegalStateException("Runtime skill is absent from catalog: " + id)));
            return loaded;
        } catch (RuntimeException error) { throw error; }
        catch (Exception error) { throw new IllegalStateException("Cannot load runtime skill data", error); }
    }

    public Stage04SkillProfile require(String skillId) {
        Stage04SkillProfile value = profiles.get(skillId);
        if (value == null) throw new IllegalArgumentException("No runtime executor profile for " + skillId);
        return value;
    }
    public boolean supports(String skillId) { return profiles.containsKey(skillId); }
    public Map<String, Stage04SkillProfile> all() { return profiles; }

    private static List<Stage04SkillProfile> load(String resource, int expected) throws Exception {
        try (var stream = Stage04SkillProfiles.class.getResourceAsStream(resource)) {
            if (stream == null) throw new IllegalStateException("Missing " + resource);
            Data data = new Gson().fromJson(new InputStreamReader(stream, StandardCharsets.UTF_8), Data.class);
            if (data == null || data.schemaVersion != 1 || data.skills == null || data.skills.size() != expected)
                throw new IllegalStateException("Unsupported or unexpected runtime data in " + resource);
            return data.skills;
        }
    }

    private static final class Data { int schemaVersion; List<Stage04SkillProfile> skills; }
}
