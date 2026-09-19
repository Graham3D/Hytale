package com.inigmasgames.persistentnpcs.profile;

import com.inigmasgames.persistentnpcs.json.JsonFiles;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Data-driven occupation capability packages. */
public final class OccupationCatalog {
    private final Path path;
    private final Map<String, OccupationDefinition> definitions = new LinkedHashMap<>();

    public OccupationCatalog(Path dataDirectory) {
        path = dataDirectory.resolve("occupations.json");
        JsonFiles.copyResourceIfMissing(OccupationCatalog.class,
                "/defaults/occupations.json", path);
        OccupationDefinition[] loaded = JsonFiles.read(path, OccupationDefinition[].class);
        if (loaded != null) {
            Arrays.stream(loaded).forEach(definition -> definitions.put(
                    normalize(definition.id()), definition));
        }
    }

    public NpcProfile apply(NpcProfile profile) {
        LinkedHashSet<String> capabilities = new LinkedHashSet<>(profile.capabilities());
        LinkedHashSet<String> knowledge = new LinkedHashSet<>(profile.knowledgeDomains());
        List<NpcScheduleEntry> schedule = profile.defaultSchedule();
        for (String roleId : profile.roleIds()) {
            OccupationDefinition definition = definitions.get(normalize(roleId));
            if (definition == null) {
                continue;
            }
            if (definition.capabilities() != null) {
                definition.capabilities().stream().map(OccupationCatalog::normalize)
                        .forEach(capabilities::add);
            }
            if (definition.knowledgeDomains() != null) {
                knowledge.addAll(definition.knowledgeDomains());
            }
            if (schedule.isEmpty() && definition.defaultSchedule() != null) {
                schedule = definition.defaultSchedule();
            }
        }
        return new NpcProfile(profile.id(), profile.name(), profile.role(),
                profile.personality(), profile.biography(), profile.purpose(),
                profile.home(), profile.workplace(), profile.likes(), profile.dislikes(),
                profile.roleIds(), List.copyOf(capabilities), profile.defaultDisposition(),
                profile.schemaVersion(), profile.selfIdentity(), profile.ageCategory(),
                profile.speakingStyle(), List.copyOf(knowledge), schedule,
                profile.appearancePreset(), profile.stableId(), profile.speciesArchetype(),
                profile.personalityTraits(), profile.values(), profile.fears(), profile.goals(),
                profile.voicePreset(), profile.voiceEffectPreset(), profile.modelTier(),
                profile.riskTolerance(),
                profile.sociability(), profile.curiosity(), profile.trustDisposition(),
                profile.relationships(), profile.summary(), profile.creatorNotes()).validated();
    }

    public Map<String, OccupationDefinition> definitions() {
        return Map.copyOf(definitions);
    }

    private static String normalize(String value) {
        return value == null ? "" : value.strip().toUpperCase(Locale.ROOT);
    }
}
