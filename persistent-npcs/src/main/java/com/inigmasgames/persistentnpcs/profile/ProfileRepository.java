package com.inigmasgames.persistentnpcs.profile;

import com.inigmasgames.persistentnpcs.json.JsonFiles;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

public final class ProfileRepository {
    private static final List<String> MARA_FRAMEWORK_CAPABILITIES = List.of(
            "FOLLOW_PLAYER", "STOP_FOLLOWING", "GO_TO", "WAIT",
            "PICK_UP_ITEM", "TAKE_ITEM", "GIVE_ITEM", "DROP_ITEM",
            "INSPECT_ITEM", "SCHEDULE_MEETING", "SCHEDULE_TASK", "CRAFT_ITEM", "PROCESS_ITEM",
            "PATROL", "WANDER", "FLEE", "CANCEL_TASK", "BRING_ITEM",
            "EQUIP_ITEM", "UNEQUIP_ITEM", "ADJUST_RELATIONSHIP",
            "CREATE_OBLIGATION", "ADD_TO_OBLIGATION", "FORGIVE_OBLIGATION",
            "INSPECT_WEAPON", "SHARED_PLAN");
    private final Path directory;
    private final OccupationCatalog occupations;

    public ProfileRepository(Path dataDirectory) {
        this.directory = dataDirectory.resolve("profiles");
        this.occupations = new OccupationCatalog(dataDirectory);
    }

    public NpcProfile loadTestProfile() {
        Path path = testProfilePath();
        migrateLegacyFlatProfile("Mara", directory.resolve("mara.json"), path);
        JsonFiles.copyResourceIfMissing(ProfileRepository.class, "/defaults/profiles/mara.json", path);
        NpcProfile loaded = JsonFiles.read(path, NpcProfile.class).validated();
        boolean mara = "Mara".equalsIgnoreCase(loaded.name());
        boolean oldForgeHeavyDefault = mara
                && "Village blacksmith".equalsIgnoreCase(loaded.role());
        if ((loaded.roleIds().isEmpty() && loaded.capabilities().isEmpty() && mara)
                || oldForgeHeavyDefault) {
            List<String> roleIds = loaded.roleIds().isEmpty()
                    ? List.of("BLACKSMITH") : loaded.roleIds();
            List<String> capabilities = loaded.capabilities().isEmpty()
                    ? MARA_FRAMEWORK_CAPABILITIES
                    : loaded.capabilities();
            loaded = new NpcProfile(loaded.id(), loaded.name(),
                    "Villager with blacksmith training",
                    "Direct, observant, dryly funny, attentive, and slow to trust flattery.",
                    "Mara lives in the village and has practical blacksmith training. "
                            + "This is background, not her default conversation topic.",
                    "Listen carefully and respond to the player and the current situation "
                            + "without inventing details.",
                    "A small room in the village", "Village workshop",
                    List.of("honest people", "practical solutions", "quiet mornings"),
                    List.of("boasting", "being ignored", "being rushed"),
                    roleIds, capabilities, loaded.defaultDisposition(), loaded.schemaVersion(),
                    loaded.selfIdentity(), loaded.ageCategory(), loaded.speakingStyle(),
                    loaded.knowledgeDomains(), loaded.defaultSchedule(), loaded.appearancePreset(),
                    loaded.stableId(), loaded.speciesArchetype(), loaded.personalityTraits(),
                    loaded.values(), loaded.fears(), loaded.goals(), loaded.voicePreset(),
                    loaded.voiceEffectPreset(), loaded.modelTier(), loaded.riskTolerance(),
                    loaded.sociability(), loaded.curiosity(), loaded.trustDisposition(),
                    loaded.relationships(), loaded.summary(), loaded.creatorNotes()).validated();
            JsonFiles.writeAtomic(path, loaded);
        }
        if (mara && !loaded.capabilities().containsAll(MARA_FRAMEWORK_CAPABILITIES)) {
            java.util.LinkedHashSet<String> capabilities =
                    new java.util.LinkedHashSet<>(loaded.capabilities());
            capabilities.addAll(MARA_FRAMEWORK_CAPABILITIES);
            loaded = new NpcProfile(loaded.id(), loaded.name(), loaded.role(),
                    loaded.personality(), loaded.biography(), loaded.purpose(), loaded.home(),
                    loaded.workplace(), loaded.likes(), loaded.dislikes(), loaded.roleIds(),
                    List.copyOf(capabilities), loaded.defaultDisposition(), loaded.schemaVersion(),
                    loaded.selfIdentity(), loaded.ageCategory(), loaded.speakingStyle(),
                    loaded.knowledgeDomains(), loaded.defaultSchedule(),
                    loaded.appearancePreset(), loaded.stableId(), loaded.speciesArchetype(),
                    loaded.personalityTraits(), loaded.values(), loaded.fears(), loaded.goals(),
                    loaded.voicePreset(), loaded.voiceEffectPreset(), loaded.modelTier(),
                    loaded.riskTolerance(),
                    loaded.sociability(), loaded.curiosity(), loaded.trustDisposition(),
                    loaded.relationships(), loaded.summary(), loaded.creatorNotes()).validated();
            JsonFiles.writeAtomic(path, loaded);
        }
        if (mara && !"Mara".equals(loaded.appearancePreset())) {
            loaded = new NpcProfile(loaded.id(), loaded.name(), loaded.role(),
                    loaded.personality(), loaded.biography(), loaded.purpose(), loaded.home(),
                    loaded.workplace(), loaded.likes(), loaded.dislikes(), loaded.roleIds(),
                    loaded.capabilities(), loaded.defaultDisposition(), loaded.schemaVersion(),
                    loaded.selfIdentity(), loaded.ageCategory(), loaded.speakingStyle(),
                    loaded.knowledgeDomains(), loaded.defaultSchedule(), "Mara", loaded.stableId(),
                    loaded.speciesArchetype(), loaded.personalityTraits(), loaded.values(),
                    loaded.fears(), loaded.goals(), loaded.voicePreset(),
                    loaded.voiceEffectPreset(), loaded.modelTier(),
                    loaded.riskTolerance(), loaded.sociability(), loaded.curiosity(),
                    loaded.trustDisposition(), loaded.relationships(), loaded.summary(),
                    loaded.creatorNotes()).validated();
            JsonFiles.writeAtomic(path, loaded);
        }
        if (mara && (loaded.values().isEmpty() || loaded.fears().isEmpty()
                || loaded.goals().isEmpty() || loaded.speakingStyle().isBlank())) {
            loaded = new NpcProfile(loaded.id(), loaded.name(), loaded.role(),
                    loaded.personality(), loaded.biography(), loaded.purpose(), loaded.home(),
                    loaded.workplace(), loaded.likes(), loaded.dislikes(), loaded.roleIds(),
                    loaded.capabilities(), loaded.defaultDisposition(), loaded.schemaVersion(),
                    loaded.selfIdentity(), loaded.ageCategory(),
                    loaded.speakingStyle().isBlank()
                            ? "Direct, natural, concise, and dryly funny"
                            : loaded.speakingStyle(),
                    loaded.knowledgeDomains().isEmpty()
                            ? List.of("the local village", "metalworking", "tools")
                            : loaded.knowledgeDomains(),
                    loaded.defaultSchedule(), "Mara", loaded.stableId(),
                    loaded.speciesArchetype(),
                    loaded.personalityTraits().isEmpty()
                            ? List.of("direct", "observant", "dryly funny", "attentive")
                            : loaded.personalityTraits(),
                    loaded.values().isEmpty()
                            ? List.of("honesty", "practical solutions", "keeping commitments")
                            : loaded.values(),
                    loaded.fears().isEmpty()
                            ? List.of("being manipulated", "failing someone who trusted her")
                            : loaded.fears(),
                    loaded.goals().isEmpty()
                            ? List.of("live independently",
                                    "be useful without being taken for granted")
                            : loaded.goals(),
                    loaded.voicePreset(), loaded.voiceEffectPreset(), loaded.modelTier(),
                    0.32, 0.58, 0.72, 0.38, loaded.relationships(), loaded.summary(),
                    loaded.creatorNotes())
                    .validated();
            JsonFiles.writeAtomic(path, loaded);
        }
        if (mara && (!"mara".equalsIgnoreCase(loaded.voicePreset())
                || !"none".equalsIgnoreCase(loaded.voiceEffectPreset()))) {
            loaded = new NpcProfile(loaded.id(), loaded.name(), loaded.role(),
                    loaded.personality(), loaded.biography(), loaded.purpose(), loaded.home(),
                    loaded.workplace(), loaded.likes(), loaded.dislikes(), loaded.roleIds(),
                    loaded.capabilities(), loaded.defaultDisposition(), loaded.schemaVersion(),
                    loaded.selfIdentity(), loaded.ageCategory(), loaded.speakingStyle(),
                    loaded.knowledgeDomains(), loaded.defaultSchedule(), loaded.appearancePreset(),
                    loaded.stableId(), loaded.speciesArchetype(), loaded.personalityTraits(),
                    loaded.values(), loaded.fears(), loaded.goals(), "mara", "none",
                    loaded.modelTier(), loaded.riskTolerance(), loaded.sociability(),
                    loaded.curiosity(), loaded.trustDisposition(), loaded.relationships(),
                    loaded.summary(), loaded.creatorNotes())
                    .validated();
            JsonFiles.writeAtomic(path, loaded);
        }
        return occupations.apply(loaded);
    }

    public Path testProfilePath() {
        return profilePath("Mara");
    }

    public Path profilesDirectory() {
        return directory;
    }

    /** The authored, authoritative profile document is named after its NPC. */
    public Path profilePath(String name) {
        String safeName = sanitizeProfileName(name);
        return profileDirectory(safeName).resolve(safeName + ".json");
    }

    /** R027-R038 compatibility only; new reads and writes prefer the named profile document. */
    private Path legacyProfilePath(String name) {
        return profileDirectory(name).resolve("profile.json");
    }

    /** Validates a display name before it is ever used as a path segment. */
    public static String sanitizeProfileName(String input) {
        String name = input == null ? "" : input.strip();
        if (name.isBlank() || name.equals(".") || name.equals("..")
                || name.length() > 64
                || name.matches(".*[<>:\"/\\\\|?*\\p{Cntrl}].*")
                || name.endsWith(".") || name.endsWith(" ")) {
            throw new IllegalArgumentException("Invalid NPC name. Use 1-64 path-safe characters.");
        }
        return name;
    }

    public Path profileDirectory(String name) {
        String safeName = sanitizeProfileName(name);
        Path root = directory.toAbsolutePath().normalize();
        Path resolved = root.resolve(safeName).normalize();
        if (!resolved.startsWith(root) || resolved.getParent() == null
                || !resolved.getParent().equals(root)) {
            throw new IllegalArgumentException("Unsafe NPC profile path");
        }
        return resolved;
    }

    public Path createProfileDirectory(String name) {
        Path path = profileDirectory(name);
        try {
            Files.createDirectories(path);
            return path;
        } catch (IOException failure) {
            throw new IllegalStateException("Could not create NPC profile directory", failure);
        }
    }

    /** Creates a valid, editable authored profile immediately for /npc create. */
    public NpcProfile createTemplate(String name) {
        String safeName = sanitizeProfileName(name);
        if (find(safeName).isPresent()) {
            throw new IllegalArgumentException("NPC profile already exists: " + safeName);
        }
        NpcProfile template = new NpcProfile(null, safeName,
                "Unassigned NPC role",
                "Describe " + safeName + "'s personality.",
                "Describe " + safeName + "'s biography.",
                "Describe " + safeName + "'s purpose.",
                "", "", List.of(), List.of(), 0).validated();
        createProfileDirectory(safeName);
        JsonFiles.writeAtomic(profilePath(safeName), template);
        return template;
    }

    /** Deletes exactly one validated profile directory and all profile-local assets. */
    public void deleteProfileDirectory(String name) {
        Path target = profileDirectory(name).toAbsolutePath().normalize();
        Path root = directory.toAbsolutePath().normalize();
        if (!target.startsWith(root) || !root.equals(target.getParent())) {
            throw new IllegalArgumentException("Unsafe NPC profile deletion path");
        }
        if (!Files.exists(target)) return;
        try (var paths = Files.walk(target)) {
            for (Path path : paths.sorted(java.util.Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(path);
            }
        } catch (IOException failure) {
            throw new IllegalStateException("Could not delete NPC profile directory", failure);
        }
    }

    public Optional<NpcProfile> find(String name) {
        Path path = profilePath(name);
        if (!Files.isRegularFile(path)) path = legacyProfilePath(name);
        if (!Files.isRegularFile(path)) return Optional.empty();
        return Optional.of(occupations.apply(readAndMigrateIdentity(path)));
    }

    public NpcProfile load(String name) {
        return find(name).orElseThrow(() -> new IllegalArgumentException(
                "Unknown NPC profile: " + sanitizeProfileName(name)));
    }

    public Map<String, NpcProfile> loadAll() {
        Map<String, NpcProfile> loaded = new LinkedHashMap<>();
        if (!Files.isDirectory(directory)) return Map.of();
        try (var children = Files.list(directory)) {
            children.filter(Files::isDirectory).sorted().forEach(child -> {
                String directoryName = child.getFileName().toString();
                Path profile = child.resolve(directoryName + ".json");
                if (!Files.isRegularFile(profile)) profile = child.resolve("profile.json");
                if (Files.isRegularFile(profile)) {
                    NpcProfile value = occupations.apply(readAndMigrateIdentity(profile));
                    loaded.put(value.name().toLowerCase(Locale.ROOT), value);
                }
            });
        } catch (IOException failure) {
            throw new IllegalStateException("Could not enumerate NPC profiles", failure);
        }
        return Map.copyOf(loaded);
    }

    private static NpcProfile readAndMigrateIdentity(Path profile) {
        NpcProfile stored = JsonFiles.read(profile, NpcProfile.class);
        NpcProfile validated = stored.validated();
        if (stored.id() == null || stored.stableId() == null) {
            Path backup = profile.resolveSibling(profile.getFileName()
                    + ".pre-r094-uuid-migration");
            if (!Files.exists(backup)) {
                try {
                    Files.copy(profile, backup, StandardCopyOption.COPY_ATTRIBUTES);
                } catch (IOException failure) {
                    throw new IllegalStateException(
                            "Could not preserve profile before UUID migration: " + profile,
                            failure);
                }
            }
            JsonFiles.writeAtomic(profile, validated);
        }
        return validated;
    }

    /** Includes preserved source JSON files so pre-R031 authored relationships can migrate. */
    public List<NpcProfile> relationshipSources() {
        if (!Files.isDirectory(directory)) return List.of();
        List<NpcProfile> result = new java.util.ArrayList<>(loadAll().values());
        try (var paths = Files.walk(directory, 2)) {
            paths.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().toLowerCase(Locale.ROOT)
                            .endsWith(".json"))
                    .filter(path -> !path.getFileName().toString().equalsIgnoreCase(
                            "SS_Skin_Character.json"))
                    .forEach(path -> {
                        try {
                            NpcProfile value = JsonFiles.read(path, NpcProfile.class).validated();
                            if (!value.relationships().isEmpty()) result.add(value);
                        } catch (RuntimeException ignored) {
                            // Non-profile JSON assets in profile directories are not sources.
                        }
                    });
        } catch (IOException failure) {
            throw new IllegalStateException("Could not enumerate authored relationships", failure);
        }
        return List.copyOf(result);
    }

    /**
     * Imports the authored schema through the existing profile validator. On update the stable
     * identity is retained even when the selected JSON accidentally contains a different UUID.
     */
    public NpcProfile importProfile(String requestedName, Path selectedJson, boolean update) {
        String safeName = sanitizeProfileName(requestedName);
        validateExtension(selectedJson, ".json");
        NpcProfile selected = JsonFiles.read(selectedJson, NpcProfile.class).validated();
        if (!safeName.equalsIgnoreCase(selected.name())) {
            throw new IllegalArgumentException("Selected profile name '" + selected.name()
                    + "' does not match NPC name '" + safeName + "'.");
        }
        Optional<NpcProfile> existing = find(safeName);
        if (update && existing.isEmpty()) {
            throw new IllegalArgumentException("Unknown NPC profile: " + safeName);
        }
        if (!update && existing.isPresent()) {
            throw new IllegalArgumentException("NPC profile already exists: " + safeName);
        }
        NpcProfile committed = existing.map(value -> withStableIdentity(selected, value))
                .orElse(selected).validated();
        Path destination = profilePath(safeName);
        JsonFiles.writeAtomic(destination, committed);
        return occupations.apply(committed);
    }

    private static NpcProfile withStableIdentity(NpcProfile source, NpcProfile identity) {
        return new NpcProfile(identity.id(), source.name(), source.role(), source.personality(),
                source.biography(), source.purpose(), source.home(), source.workplace(),
                source.likes(), source.dislikes(), source.roleIds(), source.capabilities(),
                source.defaultDisposition(), source.schemaVersion(), source.selfIdentity(),
                source.ageCategory(), source.speakingStyle(), source.knowledgeDomains(),
                source.defaultSchedule(), source.appearancePreset(), identity.stableId(),
                source.speciesArchetype(), source.personalityTraits(), source.values(),
                source.fears(), source.goals(), source.voicePreset(),
                source.voiceEffectPreset(), source.modelTier(), source.riskTolerance(),
                source.sociability(), source.curiosity(), source.trustDisposition(),
                source.relationships(), source.summary(), source.creatorNotes());
    }

    private static void validateExtension(Path path, String required) {
        if (path == null || !Files.isRegularFile(path)
                || path.getFileName() == null
                || !path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(required)) {
            throw new IllegalArgumentException("Expected a valid " + required + " file");
        }
    }

    private static void migrateLegacyFlatProfile(String name, Path legacy, Path canonical) {
        if (!Files.isRegularFile(legacy) || Files.exists(canonical)) return;
        try {
            Files.createDirectories(canonical.getParent());
            Files.copy(legacy, canonical, StandardCopyOption.COPY_ATTRIBUTES);
        } catch (IOException failure) {
            throw new IllegalStateException("Could not migrate legacy profile " + name, failure);
        }
    }
}
