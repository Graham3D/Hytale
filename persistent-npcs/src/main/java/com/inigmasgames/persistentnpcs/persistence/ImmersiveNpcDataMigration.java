package com.inigmasgames.persistentnpcs.persistence;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.function.Consumer;

/** Resolves the authoritative data directory and archives imported legacy data outside mods. */
public final class ImmersiveNpcDataMigration {
    public static final String TECHNICAL_NAME = "ImmersiveNPCs";
    public static final String LEGACY_TECHNICAL_NAME = "InigmasGames_PersistentNPCs";
    public static final String LEGACY_BACKUP_DIRECTORY = "ImmersiveNPCs-Legacy-Backups";
    private static final DateTimeFormatter BACKUP_TIMESTAMP =
            DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss");

    private ImmersiveNpcDataMigration() { }

    public static Path resolveAndMigrate(
            Path hytalePluginDataDirectory, Consumer<String> diagnostics) {
        Consumer<String> log = diagnostics == null ? ignored -> { } : diagnostics;
        Path supplied = hytalePluginDataDirectory.toAbsolutePath().normalize();
        Path mods = supplied.getParent();
        if (mods == null || mods.getFileName() == null
                || !"mods".equalsIgnoreCase(mods.getFileName().toString())) {
            // Tests and embedded callers may supply an isolated data root rather than a Hytale
            // generated plugin directory. Preserve that contract.
            return supplied;
        }
        Path authoritative = mods.resolve(TECHNICAL_NAME).toAbsolutePath().normalize();
        if (!authoritative.getParent().equals(mods)) {
            throw new IllegalStateException("Unsafe ImmersiveNPCs data directory: "
                    + authoritative);
        }
        try {
            Files.createDirectories(authoritative);
            Path legacy = mods.resolve(LEGACY_TECHNICAL_NAME).toAbsolutePath().normalize();
            if (Files.isDirectory(legacy)) {
                MigrationResult result = copyMissingTree(legacy, authoritative);
                Path archive = archiveLegacyTree(legacy);
                log.accept("IMMERSIVE_NPC_DATA_MIGRATION source=" + legacy
                        + " target=" + authoritative + " copied=" + result.copied()
                        + " preservedExisting=" + result.preservedExisting()
                        + " archived=" + archive);
            }
            migrateCanonicalProfileAssets(authoritative, log);
            return authoritative;
        } catch (IOException failure) {
            throw new IllegalStateException("Could not prepare ImmersiveNPCs data directory", failure);
        }
    }

    /** Copies only missing files. Existing authoritative files are never overwritten. */
    public static MigrationResult copyMissingTree(Path source, Path target) throws IOException {
        Path safeSource = source.toAbsolutePath().normalize();
        Path safeTarget = target.toAbsolutePath().normalize();
        Files.createDirectories(safeTarget);
        int copied = 0;
        int preserved = 0;
        try (var paths = Files.walk(safeSource)) {
            for (Path path : paths.toList()) {
                Path relative = safeSource.relativize(path);
                Path destination = safeTarget.resolve(relative).normalize();
                if (!destination.startsWith(safeTarget)) {
                    throw new IOException("Unsafe legacy data path: " + path);
                }
                if (Files.isDirectory(path)) {
                    Files.createDirectories(destination);
                } else if (Files.isRegularFile(path)) {
                    if (Files.exists(destination)) {
                        preserved++;
                    } else {
                        Files.createDirectories(destination.getParent());
                        Files.copy(path, destination, StandardCopyOption.COPY_ATTRIBUTES);
                        copied++;
                    }
                }
            }
        }
        return new MigrationResult(copied, preserved);
    }

    /**
     * Moves the fully imported legacy tree to a recoverable save-local archive outside mods.
     * This prevents Hytale and operators from seeing two live mod data identities.
     */
    public static Path archiveLegacyTree(Path legacyDirectory) throws IOException {
        Path legacy = legacyDirectory.toAbsolutePath().normalize();
        Path mods = legacy.getParent();
        if (mods == null || mods.getFileName() == null
                || !"mods".equalsIgnoreCase(mods.getFileName().toString())
                || !LEGACY_TECHNICAL_NAME.equalsIgnoreCase(
                        legacy.getFileName().toString())) {
            throw new IOException("Unsafe legacy archive source: " + legacy);
        }
        Path save = mods.getParent();
        if (save == null) {
            throw new IOException("Legacy data directory has no save root: " + legacy);
        }
        Path backupRoot = save.resolve(LEGACY_BACKUP_DIRECTORY).toAbsolutePath().normalize();
        if (!save.equals(backupRoot.getParent())) {
            throw new IOException("Unsafe legacy backup root: " + backupRoot);
        }
        Files.createDirectories(backupRoot);
        String baseName = LEGACY_TECHNICAL_NAME + "_"
                + BACKUP_TIMESTAMP.format(LocalDateTime.now());
        Path archive = backupRoot.resolve(baseName).normalize();
        int suffix = 2;
        while (Files.exists(archive)) {
            archive = backupRoot.resolve(baseName + "_" + suffix++).normalize();
        }
        if (!backupRoot.equals(archive.getParent())) {
            throw new IOException("Unsafe legacy backup destination: " + archive);
        }
        try {
            return Files.move(legacy, archive, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException unsupported) {
            return Files.move(legacy, archive);
        }
    }

    private static void migrateCanonicalProfileAssets(
            Path dataDirectory, Consumer<String> log) throws IOException {
        Path profiles = dataDirectory.resolve("profiles").normalize();
        Files.createDirectories(profiles);
        try (var flatFiles = Files.list(profiles)) {
            for (Path flat : flatFiles.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString()
                            .toLowerCase(java.util.Locale.ROOT).endsWith(".json"))
                    .toList()) {
                try {
                    com.inigmasgames.persistentnpcs.profile.NpcProfile profile =
                            com.inigmasgames.persistentnpcs.json.JsonFiles.read(flat,
                                    com.inigmasgames.persistentnpcs.profile.NpcProfile.class)
                                    .validated();
                    Path canonical = profiles.resolve(profile.name()).normalize();
                    if (!canonical.getParent().equals(profiles)) continue;
                    Files.createDirectories(canonical);
                    Path namedProfile = canonical.resolve(profile.name() + ".json");
                    if (!Files.exists(namedProfile)) {
                        Files.copy(flat, namedProfile, StandardCopyOption.COPY_ATTRIBUTES);
                    }
                } catch (RuntimeException invalidLegacyProfile) {
                    log.accept("IMMERSIVE_NPC_PROFILE_MIGRATION_SKIPPED file=" + flat
                            + " reason=" + invalidLegacyProfile.getMessage());
                }
            }
        }
        Path mods = dataDirectory.getParent();
        Path save = mods == null ? null : mods.getParent();
        if (save == null) return;
        try (var profileDirs = Files.list(profiles)) {
            for (Path profileDir : profileDirs.filter(Files::isDirectory).toList()) {
                canonicalizeGroupedProfile(profileDir, log);
                String name = profileDir.getFileName().toString();
                Path oldVoice = save.resolve("exports").resolve("voices").resolve(name);
                if (Files.isDirectory(oldVoice)) {
                    for (String voiceFile : java.util.List.of("reference.wav",
                            "sample-calm.wav", "sample-curious.wav", "sample-excited.wav",
                            "sample-uneasy.wav", "sample-angry.wav", "sample-sad.wav",
                            "sample-tender.wav", "sample-amused.wav")) {
                        copyIfMissing(oldVoice.resolve(voiceFile), profileDir.resolve(voiceFile));
                    }
                }
                try {
                    com.inigmasgames.persistentnpcs.voice.VoicePresetRepository.VoiceSampleScan
                            voiceScan = new com.inigmasgames.persistentnpcs.voice
                                    .VoicePresetRepository(dataDirectory).scan(name);
                    for (String migration : voiceScan.migrations()) {
                        log.accept("IMMERSIVE_NPC_VOICE_MIGRATED npc=" + name
                                + " asset=" + migration);
                    }
                } catch (RuntimeException voiceMigrationFailure) {
                    log.accept("IMMERSIVE_NPC_VOICE_MIGRATION_SKIPPED npc=" + name
                            + " reason=" + voiceMigrationFailure.getMessage());
                }
                Path canonicalSkin = profileDir.resolve("SS_Skin_Character.json");
                if (!Files.isRegularFile(canonicalSkin)) {
                    try (var localSkins = Files.list(profileDir)) {
                        Path localSkin = localSkins.filter(Files::isRegularFile)
                                .filter(path -> path.getFileName().toString()
                                        .toUpperCase(java.util.Locale.ROOT)
                                        .startsWith("SS_SKIN_"))
                                .findFirst().orElse(canonicalSkin);
                        copyIfMissing(localSkin, canonicalSkin);
                    }
                }
                Path oldSkinDirectory = save.resolve("exports").resolve("skins").resolve(name);
                Path exactSkin = oldSkinDirectory.resolve("SS_SKIN_" + name + ".json");
                if (!Files.isRegularFile(exactSkin) && Files.isDirectory(oldSkinDirectory)) {
                    try (var skins = Files.list(oldSkinDirectory)) {
                        exactSkin = skins.filter(Files::isRegularFile)
                                .filter(path -> path.getFileName().toString().startsWith("SS_SKIN_"))
                                .findFirst().orElse(exactSkin);
                    }
                }
                copyIfMissing(exactSkin, canonicalSkin);
            }
        }
    }

    private static void canonicalizeGroupedProfile(
            Path profileDirectory, Consumer<String> log) throws IOException {
        String directoryName = profileDirectory.getFileName().toString();
        Path canonical = profileDirectory.resolve(directoryName + ".json");
        if (Files.isRegularFile(canonical)) return;
        try (var jsonFiles = Files.list(profileDirectory)) {
            java.util.List<Path> candidates = jsonFiles.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString()
                            .toLowerCase(java.util.Locale.ROOT).endsWith(".json"))
                    .filter(path -> !path.getFileName().toString().equalsIgnoreCase("preset.json"))
                    .filter(path -> !path.getFileName().toString()
                            .toUpperCase(java.util.Locale.ROOT).startsWith("SS_"))
                    .sorted(java.util.Comparator.comparingInt(path ->
                            path.getFileName().toString().equalsIgnoreCase("profile.json")
                                    ? 0 : 1))
                    .toList();
            for (Path candidate : candidates) {
                try {
                    com.inigmasgames.persistentnpcs.profile.NpcProfile profile =
                            com.inigmasgames.persistentnpcs.json.JsonFiles.read(candidate,
                                    com.inigmasgames.persistentnpcs.profile.NpcProfile.class)
                                    .validated();
                    com.inigmasgames.persistentnpcs.profile.ProfileRepository
                            .sanitizeProfileName(profile.name());
                    Files.copy(candidate, canonical, StandardCopyOption.COPY_ATTRIBUTES);
                    return;
                } catch (RuntimeException invalidProfile) {
                    log.accept("IMMERSIVE_NPC_GROUPED_PROFILE_MIGRATION_SKIPPED file="
                            + candidate + " reason=" + invalidProfile.getMessage());
                }
            }
        }
    }

    private static void copyIfMissing(Path source, Path target) throws IOException {
        if (Files.isRegularFile(source) && !Files.exists(target)) {
            Files.createDirectories(target.getParent());
            Files.copy(source, target, StandardCopyOption.COPY_ATTRIBUTES);
        }
    }

    public record MigrationResult(int copied, int preservedExisting) { }
}
