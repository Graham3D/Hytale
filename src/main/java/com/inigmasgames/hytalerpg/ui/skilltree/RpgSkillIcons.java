package com.inigmasgames.hytalerpg.ui.skilltree;

import com.google.gson.Gson;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Predicate;

/** Optional packaged PNG overrides. Never reads owner files or mutates assets on the world thread. */
public final class RpgSkillIcons {
    private static final Map<String, String> PATHS = load(RpgSkillIcons.class.getClassLoader());
    private RpgSkillIcons() {}

    public static String forSkill(String id) {
        return PATHS.getOrDefault("Skill:" + id, RpgSkillTreeProjectionService.PLACEHOLDER_ICON);
    }

    public static String forPassive(String id) {
        return PATHS.getOrDefault("Passive:" + id, RpgSkillTreeProjectionService.PLACEHOLDER_ICON);
    }

    static Map<String, String> load(ClassLoader loader) {
        try (var stream = loader.getResourceAsStream("rpg/presentation/icon-index.json")) {
            if (stream == null) throw new IllegalStateException("Missing icon index");
            var index = new Gson().fromJson(new InputStreamReader(stream, StandardCharsets.UTF_8), Index.class);
            return resolve(index, path -> loader.getResource(path) != null);
        } catch (java.io.IOException e) {
            throw new IllegalStateException("Cannot read icon index", e);
        }
    }

    static Map<String, String> resolve(Index index, Predicate<String> exists) {
        if (index == null || index.schemaVersion != 1 || index.entries == null)
            throw new IllegalStateException("Unsupported icon index");
        Map<String, String> result = new HashMap<>();
        for (Entry entry : index.entries) {
            if (entry == null || !("Skill".equals(entry.kind) || "Passive".equals(entry.kind))
                    || entry.id == null || !entry.id.matches("[a-z0-9_]+")
                    || entry.fileName == null || !entry.fileName.matches(entry.kind + "[A-Z][A-Za-z0-9]*\\.png"))
                throw new IllegalStateException("Invalid icon index entry");
            String path = "Icons/RPG/" + entry.fileName;
            String key = entry.kind + ":" + entry.id;
            if (result.putIfAbsent(key, exists.test("Common/UI/Custom/" + path)
                    ? path : RpgSkillTreeProjectionService.PLACEHOLDER_ICON) != null)
                throw new IllegalStateException("Duplicate icon ID");
        }
        return Map.copyOf(result);
    }
    record Index(int schemaVersion, Entry[] entries) {}
    record Entry(String kind, String id, String name, String fileName, String itemAsset) {}
}
