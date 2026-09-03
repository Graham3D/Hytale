package com.inigmasgames.persistentnpcs.profile;

import com.inigmasgames.persistentnpcs.voice.VoicePresetRepository;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.EnumMap;
import java.util.Arrays;
import java.util.Locale;
import java.util.Map;

/** Transaction boundary for the native profile page; delegates to existing profile/voice/skin systems. */
public final class NpcProfileEditorService {
    private final ProfileRepository profiles;
    private final NpcProfileRegistry registry;
    private final AppearanceRepository appearances;
    private final NpcInventoryRepository inventories;
    private final VoicePresetRepository voices;

    public NpcProfileEditorService(
            ProfileRepository profiles,
            NpcProfileRegistry registry,
            AppearanceRepository appearances) {
        this(profiles, registry, appearances, new NpcInventoryRepository(profiles),
                new VoicePresetRepository(profiles.profilesDirectory().getParent()));
    }

    public NpcProfileEditorService(
            ProfileRepository profiles,
            NpcProfileRegistry registry,
            AppearanceRepository appearances,
            NpcInventoryRepository inventories) {
        this(profiles, registry, appearances, inventories,
                new VoicePresetRepository(profiles.profilesDirectory().getParent()));
    }

    public NpcProfileEditorService(
            ProfileRepository profiles,
            NpcProfileRegistry registry,
            AppearanceRepository appearances,
            NpcInventoryRepository inventories,
            VoicePresetRepository voices) {
        this.profiles = profiles;
        this.registry = registry;
        this.appearances = appearances;
        this.inventories = inventories;
        this.voices = voices;
    }

    public Path beginCreate(String name) {
        String safe = ProfileRepository.sanitizeProfileName(name);
        NpcProfile template = profiles.createTemplate(safe);
        inventories.save(safe, NpcInventoryState.empty().withStableNpcId(template.stableId()));
        return profiles.profileDirectory(safe);
    }

    public Path requireExisting(String name) {
        profiles.load(name);
        return profiles.profileDirectory(name);
    }

    public NpcInventoryRepository.Session openInventory(String name) {
        return inventories.open(ProfileRepository.sanitizeProfileName(name));
    }

    /** Existing profile directory, resolved to the filesystem's authoritative casing. */
    public Path profileDirectoryForBrowsing(String name) {
        Path directory = profiles.createProfileDirectory(
                ProfileRepository.sanitizeProfileName(name));
        try {
            return directory.toRealPath();
        } catch (IOException failure) {
            throw new IllegalStateException("Could not open the NPC profile directory", failure);
        }
    }

    public NpcInventoryRepository inventories() {
        return inventories;
    }

    public java.util.Optional<NpcProfile> currentProfile(String name) {
        return profiles.find(ProfileRepository.sanitizeProfileName(name));
    }

    public java.util.Optional<AppearanceRepository.PreviewAppearance> previewAppearance(
            String name) {
        NpcProfile profile = profiles.load(ProfileRepository.sanitizeProfileName(name));
        java.util.Optional<AppearanceRepository.PreviewAppearance> named =
                appearances.resolvePreviewAppearance(profile.name());
        if (named.isPresent()) return named;
        return appearances.resolvePreviewAppearance(profile.appearancePreset());
    }

    /** Authoritative profile-local visible equipment for the isolated preview probe. */
    public PreviewEquipment previewEquipment(String name) {
        String safe = ProfileRepository.sanitizeProfileName(name);
        profiles.load(safe);
        return previewEquipmentFrom(inventories.load(safe));
    }

    public static PreviewEquipment previewEquipmentFrom(NpcInventoryState state) {
        if (state == null) throw new IllegalArgumentException("NPC inventory state is required");
        String[] armorIds = new String[NpcInventoryState.ARMOR_CAPACITY];
        Arrays.fill(armorIds, "");
        for (NpcInventoryState.PersistedItemStack item : state.armor()) {
            if (!state.armorHidden(item.slot())) armorIds[item.slot()] = item.itemId();
        }
        String rightHand = state.loadout().stream()
                .filter(item -> item.slot() == NpcInventoryRepository.Session.PRIMARY_SLOT)
                .map(NpcInventoryState.PersistedItemStack::itemId)
                .findFirst().orElse("Empty");
        String leftHand = state.loadout().stream()
                .filter(item -> item.slot() == NpcInventoryRepository.Session.OFFHAND_SLOT)
                .map(NpcInventoryState.PersistedItemStack::itemId)
                .findFirst().orElse("Empty");
        return new PreviewEquipment(armorIds, rightHand, leftHand);
    }

    public record PreviewEquipment(
            String[] visibleArmorIds, String rightHandItemId, String leftHandItemId) {
        public PreviewEquipment {
            visibleArmorIds = visibleArmorIds == null ? new String[0]
                    : visibleArmorIds.clone();
            rightHandItemId = rightHandItemId == null ? "Empty" : rightHandItemId;
            leftHandItemId = leftHandItemId == null ? "Empty" : leftHandItemId;
        }

        @Override public String[] visibleArmorIds() { return visibleArmorIds.clone(); }
    }

    public NpcProfile commit(
            String name, boolean update, Map<ProfileFileField, Path> selections) {
        String safe = ProfileRepository.sanitizeProfileName(name);
        EnumMap<ProfileFileField, Path> chosen = new EnumMap<>(ProfileFileField.class);
        if (selections != null) chosen.putAll(selections);
        Path selectedProfile = chosen.get(ProfileFileField.PROFILE);
        if (!update && selectedProfile == null && profiles.find(safe).isEmpty()) {
            throw new IllegalArgumentException("Profile JSON is required.");
        }
        if (update && selectedProfile == null && profiles.find(safe).isEmpty()) {
            throw new IllegalArgumentException("Existing profile JSON is missing.");
        }
        chosen.forEach(this::validateSelection);

        NpcProfile committed = selectedProfile == null
                ? profiles.load(safe)
                : profiles.importProfile(safe, selectedProfile,
                        update || profiles.find(safe).isPresent());
        Path directory = profiles.createProfileDirectory(safe);
        for (var entry : chosen.entrySet()) {
            if (entry.getKey() == ProfileFileField.PROFILE) continue;
            copyAtomic(entry.getValue(), directory.resolve(entry.getKey().canonicalFilename()));
        }
        voices.scan(committed.name());
        registry.register(committed);
        return committed;
    }

    public void deleteProfile(String name) {
        profiles.deleteProfileDirectory(ProfileRepository.sanitizeProfileName(name));
    }

    public VoicePresetRepository.VoiceSampleScan rescanVoiceSamples(String name) {
        return voices.scan(ProfileRepository.sanitizeProfileName(name));
    }

    /** Used by any future profile rename transaction before the old directory is retired. */
    public int migrateVoiceAssetsForRename(String oldName, String newName) {
        return voices.migrateManagedVoiceFiles(oldName, newName);
    }

    public String presentFilename(String name, ProfileFileField field) {
        Path file = field == ProfileFileField.PROFILE
                ? profiles.profilePath(name)
                : profiles.profileDirectory(name).resolve(field.canonicalFilename());
        if (field == ProfileFileField.PROFILE && !Files.isRegularFile(file)) {
            // Surface the legacy file only when no authoritative named document exists.
            Path legacy = profiles.profileDirectory(name).resolve("profile.json");
            if (Files.isRegularFile(legacy)) return "profile.json (legacy fallback)";
        }
        return Files.isRegularFile(file) ? file.getFileName().toString() : "Not selected";
    }

    private void validateSelection(ProfileFileField field, Path selected) {
        if (selected == null || selected.getFileName() == null || !Files.isRegularFile(selected)) {
            throw new IllegalArgumentException(field.label() + " must be a regular file.");
        }
        String filename = selected.getFileName().toString().toLowerCase(Locale.ROOT);
        if (!filename.endsWith(field.extension())) {
            throw new IllegalArgumentException(field.label() + " must be a "
                    + field.extension() + " file.");
        }
        if (field == ProfileFileField.SKIN) {
            appearances.validateSkinFile(selected);
        } else if (field == ProfileFileField.PROFILE) {
            // Full schema and name/identity validation occurs in ProfileRepository.importProfile.
            com.inigmasgames.persistentnpcs.json.JsonFiles
                    .read(selected, NpcProfile.class).validated();
        }
    }

    private static void copyAtomic(Path source, Path destination) {
        try {
            Files.createDirectories(destination.getParent());
            Path temporary = destination.resolveSibling(destination.getFileName() + ".tmp");
            Files.copy(source, temporary, StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.COPY_ATTRIBUTES);
            try {
                Files.move(temporary, destination, StandardCopyOption.REPLACE_EXISTING,
                        StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException ignored) {
                Files.move(temporary, destination, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException failure) {
            throw new IllegalStateException("Could not store " + destination.getFileName(), failure);
        }
    }

    public enum ProfileFileField {
        PROFILE("Profile", "<name>.json", ".json"),
        SKIN("Skin", "SS_Skin_Character.json", ".json");

        private final String label;
        private final String canonicalFilename;
        private final String extension;

        ProfileFileField(String label, String canonicalFilename, String extension) {
            this.label = label;
            this.canonicalFilename = canonicalFilename;
            this.extension = extension;
        }

        public String label() { return label; }
        public String canonicalFilename() { return canonicalFilename; }
        public String extension() { return extension; }
    }
}
