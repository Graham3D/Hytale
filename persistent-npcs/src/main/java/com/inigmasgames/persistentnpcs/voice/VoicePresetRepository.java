package com.inigmasgames.persistentnpcs.voice;

import com.inigmasgames.persistentnpcs.json.JsonFiles;
import com.inigmasgames.persistentnpcs.profile.NpcProfile;
import com.inigmasgames.persistentnpcs.profile.ProfileRepository;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/** Existing voice-preset owner, extended with deterministic profile-local sample discovery. */
public final class VoicePresetRepository {
    private static final Map<VoiceSampleType, List<String>> LEGACY_FILENAMES = Map.of(
            VoiceSampleType.REFERENCE, List.of("reference.wav"),
            VoiceSampleType.AFFECTIONATE, List.of("sample-affectionate.wav", "sample-tender.wav"),
            VoiceSampleType.AMUSED, List.of("sample-amused.wav"),
            VoiceSampleType.EXCITED, List.of("sample-excited.wav"),
            VoiceSampleType.ANGRY, List.of("sample-angry.wav"),
            VoiceSampleType.SAD, List.of("sample-sad.wav"),
            VoiceSampleType.SCARED, List.of("sample-scared.wav", "sample-uneasy.wav"));

    private final Path voicesDirectory;
    private final Path profilesDirectory;
    private final Map<String, Path> profileVoiceDirectories = new ConcurrentHashMap<>();
    private final Map<String, String> profileVoiceStems = new ConcurrentHashMap<>();

    public VoicePresetRepository(Path dataDirectory) {
        profilesDirectory = dataDirectory.resolve("profiles").toAbsolutePath().normalize();
        Path mods = dataDirectory.toAbsolutePath().normalize().getParent();
        Path save = mods != null && mods.getFileName() != null
                && "mods".equalsIgnoreCase(mods.getFileName().toString())
                        ? mods.getParent() : null;
        voicesDirectory = (save == null ? dataDirectory.resolve("exports")
                : save.resolve("exports")).resolve("voices").toAbsolutePath().normalize();
    }

    public VoicePreset loadMaraPreset() {
        Path path = presetDirectory("mara").resolve("preset.json");
        JsonFiles.copyResourceIfMissing(VoicePresetRepository.class,
                "/defaults/voices/mara.json", path);
        installWorkerScript();
        VoicePreset stored = JsonFiles.read(path, VoicePreset.class);
        VoicePreset preset = stored.normalized();
        if (stored.outputGainDb() == null) JsonFiles.writeAtomic(path, preset);
        if (!"mara".equals(preset.id())) {
            throw new IllegalStateException("Mara voice preset file must have id=mara");
        }
        return preset;
    }

    public VoicePreset resolve(NpcProfile profile) {
        String presetId = profile == null || profile.voicePreset() == null
                ? "" : profile.voicePreset().strip().toLowerCase(Locale.ROOT);
        Path canonical = profile == null ? null : canonicalProfileDirectory(profile.name());
        String stem = profile == null ? "" : voiceFileStem(profile.name());
        VoiceSampleScan scan = profile == null ? null : scan(profile.name());
        boolean ownsProfileVoice = scan != null && (scan.reference().present()
                || containsAnyManagedVoice(canonical));
        if (presetId.isBlank() && ownsProfileVoice) presetId = stem;
        if (presetId.isBlank()) throw new IllegalStateException("NPC profile has no voicePreset");

        if (ownsProfileVoice) {
            profileVoiceDirectories.put(presetId, canonical);
            profileVoiceStems.put(presetId, stem);
            return new VoicePreset(presetId, VoiceProvider.CHATTERBOX,
                    expectedFilename(profile.name(), VoiceSampleType.REFERENCE),
                    VocalEmotion.CALM, VocalIntensity.LOW, VocalPace.NORMAL, 4.0).normalized();
        }
        if ("mara".equals(presetId)) return loadMaraPreset();
        Path path = presetDirectory(presetId).resolve("preset.json");
        if (!Files.isRegularFile(path)) {
            throw new IllegalStateException("Voice preset not found: " + presetId);
        }
        VoicePreset loaded = JsonFiles.read(path, VoicePreset.class).normalized();
        if (!presetId.equals(loaded.id())) {
            throw new IllegalStateException("Voice preset id does not match folder: " + presetId);
        }
        return loaded;
    }

    public Optional<Path> referenceAudio(VoicePreset preset) {
        return resolveSample(preset, VocalEmotion.CALM).path();
    }

    /** Resolves exactly one supported sample type, with Reference as the sole fallback. */
    public Optional<Path> referenceAudio(VoicePreset preset, VocalEmotion emotion) {
        return resolveSample(preset, emotion).path();
    }

    public ResolvedVoiceSample resolveSample(VoicePreset preset, VocalEmotion emotion) {
        VoiceSampleType requested = VoiceSampleType.forEmotion(emotion);
        Path root = voiceDirectory(preset.id());
        String stem = profileVoiceStems.getOrDefault(preset.id(), voiceFileStem(preset.id()));
        Path canonicalReference = root.resolve(stem + "-reference.wav").normalize();
        if (!Files.exists(canonicalReference)) {
            firstLegacyFile(root, VoiceSampleType.REFERENCE)
                    .ifPresent(source -> copyIfMissing(source, canonicalReference));
        }
        Path configuredReference = root.resolve(preset.referenceAudioPath()).normalize();
        Path reference = validWave(canonicalReference) ? canonicalReference
                : configuredReference.startsWith(root) && validWave(configuredReference)
                        ? configuredReference : null;
        if (reference == null) {
            return new ResolvedVoiceSample(requested, VoiceSampleType.REFERENCE,
                    Optional.empty(), "MISSING", requested != VoiceSampleType.REFERENCE);
        }
        Path selected = root.resolve(stem + "-" + requested.filenameToken() + ".wav").normalize();
        if (!Files.exists(selected)) {
            firstLegacyFile(root, requested).ifPresent(source -> copyIfMissing(source, selected));
        }
        if (selected.startsWith(root) && validWave(selected)) {
            return new ResolvedVoiceSample(requested, requested, Optional.of(selected),
                    fileRevision(selected), false);
        }

        return new ResolvedVoiceSample(requested, VoiceSampleType.REFERENCE,
                Optional.of(reference), fileRevision(reference),
                requested != VoiceSampleType.REFERENCE);
    }

    /** Scans and safely copies known legacy names into the canonical convention. */
    public VoiceSampleScan scan(String profileName) {
        String safe = ProfileRepository.sanitizeProfileName(profileName);
        Path directory = canonicalProfileDirectory(safe);
        String stem = voiceFileStem(safe);
        EnumMap<VoiceSampleType, VoiceSampleStatus> statuses = new EnumMap<>(VoiceSampleType.class);
        java.util.ArrayList<String> migrations = new java.util.ArrayList<>();
        for (VoiceSampleType type : VoiceSampleType.values()) {
            Path expected = directory.resolve(expectedFilename(safe, type)).normalize();
            String migratedFrom = "";
            if (!Files.exists(expected)) {
                for (String legacyName : LEGACY_FILENAMES.get(type)) {
                    Path legacy = directory.resolve(legacyName).normalize();
                    if (!legacy.equals(expected) && Files.isRegularFile(legacy)) {
                        copyIfMissing(legacy, expected);
                        if (Files.isRegularFile(expected)) {
                            migratedFrom = legacyName;
                            migrations.add(legacyName + " -> " + expected.getFileName());
                        }
                        break;
                    }
                }
            }
            SampleState state = !Files.isRegularFile(expected) ? SampleState.MISSING
                    : validWave(expected) ? SampleState.FOUND : SampleState.INVALID;
            statuses.put(type, new VoiceSampleStatus(type, expected.getFileName().toString(),
                    expected, state, migratedFrom));
        }
        return new VoiceSampleScan(safe, stem, directory, statuses, migrations);
    }

    /** Explicit rename hook: copy managed assets to the new identity, never overwrite or delete. */
    public int migrateManagedVoiceFiles(String oldName, String newName) {
        String oldSafe = ProfileRepository.sanitizeProfileName(oldName);
        String newSafe = ProfileRepository.sanitizeProfileName(newName);
        Path oldDirectory = canonicalProfileDirectory(oldSafe);
        Path newDirectory = canonicalProfileDirectory(newSafe);
        int copied = 0;
        try { Files.createDirectories(newDirectory); }
        catch (IOException failure) { throw new IllegalStateException("Could not create renamed voice directory", failure); }
        for (VoiceSampleType type : VoiceSampleType.values()) {
            Path source = oldDirectory.resolve(expectedFilename(oldSafe, type));
            if (!Files.isRegularFile(source)) source = firstLegacyFile(oldDirectory, type).orElse(source);
            Path target = newDirectory.resolve(expectedFilename(newSafe, type));
            if (Files.isRegularFile(source) && !Files.exists(target)) {
                copyIfMissing(source, target);
                if (Files.isRegularFile(target)) copied++;
            }
        }
        return copied;
    }

    public static String voiceFileStem(String npcName) {
        String safe = ProfileRepository.sanitizeProfileName(npcName);
        String stem = java.text.Normalizer.normalize(safe, java.text.Normalizer.Form.NFKD)
                .toLowerCase(Locale.ROOT).replaceAll("\\p{M}+", "")
                .replaceAll("[^a-z0-9_-]+", "-").replaceAll("-+", "-")
                .replaceAll("^[-_]+|[-_]+$", "");
        if (stem.isBlank()) throw new IllegalArgumentException(
                "NPC name cannot produce a safe voice filename");
        return stem;
    }

    public static String expectedFilename(String npcName, VoiceSampleType type) {
        return voiceFileStem(npcName) + "-" + type.filenameToken() + ".wav";
    }

    public static boolean validWave(Path path) {
        try {
            if (!Files.isRegularFile(path) || Files.size(path) < 44) return false;
            byte[] header = new byte[12];
            try (var input = Files.newInputStream(path)) {
                if (input.readNBytes(header, 0, header.length) != header.length) return false;
            }
            boolean wave = header[0] == 'R' && header[1] == 'I'
                    && header[2] == 'F' && header[3] == 'F' && header[8] == 'W'
                    && header[9] == 'A' && header[10] == 'V' && header[11] == 'E';
            if (!wave) return false;
            try (var audio = javax.sound.sampled.AudioSystem.getAudioInputStream(path.toFile())) {
                double frameRate = audio.getFormat().getFrameRate();
                double durationSeconds = frameRate <= 0 ? 0 : audio.getFrameLength() / frameRate;
                return durationSeconds > 5.0;
            }
        } catch (IOException | javax.sound.sampled.UnsupportedAudioFileException ignored) {
            return false;
        }
    }

    public Path presetDirectory(String presetId) {
        String safe = presetId == null ? "" : presetId.strip().toLowerCase(Locale.ROOT);
        if (!safe.matches("[a-z0-9_-]+")) throw new IllegalArgumentException("Unsafe voice preset id");
        String folder = "mara".equals(safe) ? "Mara"
                : Character.toUpperCase(safe.charAt(0)) + safe.substring(1);
        Path resolved = voicesDirectory.resolve(folder).normalize();
        if (!resolved.startsWith(voicesDirectory)) throw new IllegalArgumentException("Unsafe voice preset directory");
        return resolved;
    }

    public Path voicesDirectory() { return voicesDirectory; }
    public Path workerScript() { return voicesDirectory.resolve("immersive_voice_worker.py"); }

    /** Public, sanitizer-backed ownership boundary used by the in-game recorder. */
    public Path profileVoiceDirectory(String profileName) {
        return canonicalProfileDirectory(profileName);
    }

    public Path canonicalSamplePath(String profileName, VoiceSampleType type) {
        Path root = canonicalProfileDirectory(profileName);
        Path resolved = root.resolve(expectedFilename(profileName,
                java.util.Objects.requireNonNull(type, "voice sample type"))).normalize();
        if (!resolved.startsWith(root) || !root.equals(resolved.getParent())) {
            throw new IllegalArgumentException("Unsafe canonical voice sample path");
        }
        return resolved;
    }

    /** Content revision, rather than timestamp identity, for stale-write protection. */
    public String sampleRevision(String profileName, VoiceSampleType type) {
        Path path = canonicalSamplePath(profileName, type);
        if (!Files.isRegularFile(path)) return "MISSING";
        try {
            java.security.MessageDigest digest = java.security.MessageDigest
                    .getInstance("SHA-256");
            try (var input = Files.newInputStream(path)) {
                byte[] buffer = new byte[8192];
                int read;
                while ((read = input.read(buffer)) >= 0) digest.update(buffer, 0, read);
            }
            return java.util.HexFormat.of().formatHex(digest.digest());
        } catch (Exception failure) {
            throw new IllegalStateException("Could not revise voice sample", failure);
        }
    }

    public void installWorkerScript() {
        JsonFiles.copyResourceReplacing(VoicePresetRepository.class,
                "/tools/immersive_voice_worker.py", workerScript());
    }

    private Path voiceDirectory(String presetId) {
        return profileVoiceDirectories.getOrDefault(presetId, presetDirectory(presetId));
    }

    private Path canonicalProfileDirectory(String profileName) {
        String safe = ProfileRepository.sanitizeProfileName(profileName);
        Path resolved = profilesDirectory.resolve(safe).normalize();
        if (!resolved.startsWith(profilesDirectory) || !profilesDirectory.equals(resolved.getParent())) {
            throw new IllegalArgumentException("Unsafe profile voice directory");
        }
        return resolved;
    }

    private static boolean containsAnyManagedVoice(Path directory) {
        if (directory == null || !Files.isDirectory(directory)) return false;
        try (var files = Files.list(directory)) {
            return files.anyMatch(path -> Files.isRegularFile(path)
                    && path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".wav"));
        } catch (IOException ignored) { return false; }
    }

    private static Optional<Path> firstLegacyFile(Path directory, VoiceSampleType type) {
        return LEGACY_FILENAMES.get(type).stream().map(directory::resolve)
                .filter(Files::isRegularFile).findFirst();
    }

    private static void copyIfMissing(Path source, Path target) {
        if (!Files.isRegularFile(source) || Files.exists(target)) return;
        try {
            Files.createDirectories(target.getParent());
            Files.copy(source, target, StandardCopyOption.COPY_ATTRIBUTES);
        } catch (IOException failure) {
            throw new IllegalStateException("Could not migrate voice sample "
                    + source.getFileName() + " to " + target.getFileName(), failure);
        }
    }

    private static String fileRevision(Path path) {
        try { return Files.size(path) + ":" + Files.getLastModifiedTime(path).toMillis(); }
        catch (IOException failure) { return "UNREADABLE"; }
    }

    public enum SampleState { FOUND, MISSING, INVALID }

    public record VoiceSampleStatus(VoiceSampleType type, String filename, Path path,
            SampleState state, String migratedFrom) {
        public boolean present() { return state == SampleState.FOUND; }
    }

    public record VoiceSampleScan(String profileName, String filenameStem, Path directory,
            Map<VoiceSampleType, VoiceSampleStatus> samples, List<String> migrations) {
        public VoiceSampleScan {
            EnumMap<VoiceSampleType, VoiceSampleStatus> copy = new EnumMap<>(VoiceSampleType.class);
            if (samples != null) copy.putAll(samples);
            samples = Collections.unmodifiableMap(copy);
            migrations = migrations == null ? List.of() : List.copyOf(migrations);
        }
        public VoiceSampleStatus reference() { return samples.get(VoiceSampleType.REFERENCE); }
        public boolean ready() { return reference() != null && reference().present(); }
    }

    public record ResolvedVoiceSample(VoiceSampleType requestedType,
            VoiceSampleType resolvedType, Optional<Path> path, String revision,
            boolean fellBackToReference) { }
}
