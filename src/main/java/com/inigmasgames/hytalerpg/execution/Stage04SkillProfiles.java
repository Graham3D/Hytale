package com.inigmasgames.hytalerpg.execution;

import com.google.gson.Gson;
import com.inigmasgames.hytalerpg.content.RpgCatalog;
import com.inigmasgames.hytalerpg.domain.SkillId;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Versioned Stage 04 pilot data; adding a skill is a data change, not a new executor. */
public final class Stage04SkillProfiles {
    public static final int EXPECTED_PILOTS = 6;
    private final Map<String, Stage04SkillProfile> profiles;

    public Stage04SkillProfiles(List<Stage04SkillProfile> profiles) {
        Map<String, Stage04SkillProfile> indexed = new LinkedHashMap<>();
        for (Stage04SkillProfile profile : profiles)
            if (indexed.put(profile.skillId(), profile) != null)
                throw new IllegalArgumentException("Duplicate Stage 04 skill: " + profile.skillId());
        this.profiles = Map.copyOf(indexed);
    }

    public static Stage04SkillProfiles loadCanonical(RpgCatalog catalog) {
        try (var stream = Stage04SkillProfiles.class.getResourceAsStream("/rpg/runtime/stage-04-skills.json")) {
            if (stream == null) throw new IllegalStateException("Missing stage-04-skills.json");
            Data data = new Gson().fromJson(new InputStreamReader(stream, StandardCharsets.UTF_8), Data.class);
            if (data == null || data.schemaVersion != 1 || data.skills == null)
                throw new IllegalStateException("Unsupported or empty Stage 04 runtime data");
            Stage04SkillProfiles loaded = new Stage04SkillProfiles(data.skills);
            if (loaded.profiles.size() != EXPECTED_PILOTS)
                throw new IllegalStateException("Expected six Stage 04 pilot skills, got " + loaded.profiles.size());
            loaded.profiles.keySet().forEach(id -> catalog.skill(new SkillId(id)).orElseThrow(
                    () -> new IllegalStateException("Stage 04 runtime skill is absent from catalog: " + id)));
            return loaded;
        } catch (RuntimeException error) { throw error; }
        catch (Exception error) { throw new IllegalStateException("Cannot load Stage 04 runtime data", error); }
    }

    public Stage04SkillProfile require(String skillId) {
        Stage04SkillProfile value = profiles.get(skillId);
        if (value == null) throw new IllegalArgumentException("No Stage 04 executor profile for " + skillId);
        return value;
    }
    public boolean supports(String skillId) { return profiles.containsKey(skillId); }
    public Map<String, Stage04SkillProfile> all() { return profiles; }

    private static final class Data { int schemaVersion; List<Stage04SkillProfile> skills; }
}
