package com.inigmasgames.persistentnpcs.voice;

import com.inigmasgames.persistentnpcs.profile.ProfileRepository;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.time.Instant;
import java.nio.file.attribute.FileTime;
import java.util.HexFormat;
import java.util.UUID;
import java.util.function.Consumer;

/** Atomic draft/save/delete owner for profile-local canonical voice samples. */
public final class NpcVoiceSamplePersistenceService {
    private final VoicePresetRepository repository;
    private final Consumer<Path> cacheInvalidator;
    private final Consumer<String> diagnostics;

    public NpcVoiceSamplePersistenceService(VoicePresetRepository repository,
            Consumer<Path> cacheInvalidator, Consumer<String> diagnostics) {
        this.repository = repository;
        this.cacheInvalidator = cacheInvalidator == null ? ignored -> { } : cacheInvalidator;
        this.diagnostics = diagnostics == null ? ignored -> { } : diagnostics;
    }

    public Draft writeDraft(String npcName, UUID recordingId, VoiceSampleType type,
            VoiceDraftAudio audio) {
        Path root = repository.profileVoiceDirectory(npcName);
        Path draftRoot = contained(root, root.resolve(".voice-drafts"));
        Path target = contained(draftRoot, draftRoot.resolve(recordingId + "-"
                + type.filenameToken() + ".wav"));
        try {
            Files.createDirectories(draftRoot);
            Files.write(target, audio.wav());
            if (!VoicePresetRepository.validWave(target)) {
                Files.deleteIfExists(target);
                throw new IllegalStateException(
                        "Record a longer sample with natural speech.");
            }
            return new Draft(recordingId, type, target, sha256(target), audio);
        } catch (IOException failure) {
            throw new IllegalStateException("Could not create the temporary voice draft.", failure);
        }
    }

    /** Removes only expired recorder-owned drafts; canonical and rollback files are untouched. */
    public int cleanupStaleDrafts(String npcName, long maximumAgeMillis) {
        Path root = repository.profileVoiceDirectory(npcName);
        Path draftRoot = contained(root, root.resolve(".voice-drafts"));
        if (!Files.isDirectory(draftRoot)) return 0;
        long cutoff = System.currentTimeMillis() - Math.max(60_000, maximumAgeMillis);
        int removed = 0;
        try (var drafts = Files.list(draftRoot)) {
            for (Path draft : drafts.filter(Files::isRegularFile).toList()) {
                if (!draft.getFileName().toString().endsWith(".wav")) continue;
                FileTime modified = Files.getLastModifiedTime(draft);
                if (modified.toMillis() <= cutoff && Files.deleteIfExists(draft)) removed++;
            }
        } catch (IOException failure) {
            diagnostics.accept("NPC_AUTHORING_VOICE_STALE_DRAFT_CLEANUP_FAILED timestamp="
                    + Instant.now() + " npc=" + ProfileRepository.sanitizeProfileName(npcName)
                    + " reason=" + failure.getClass().getSimpleName());
        }
        if (removed > 0) diagnostics.accept("NPC_AUTHORING_VOICE_STALE_DRAFT_CLEANUP"
                + " timestamp=" + Instant.now() + " npc="
                + ProfileRepository.sanitizeProfileName(npcName) + " removed=" + removed);
        return removed;
    }

    public SaveResult save(String npcName, UUID stableNpcId, Draft draft,
            String expectedRevision) {
        Path root = repository.profileVoiceDirectory(npcName);
        Path target = repository.canonicalSamplePath(npcName, draft.type());
        if (!target.startsWith(root) || !draft.path().startsWith(root)) {
            throw new IllegalArgumentException("Voice sample path escaped its NPC profile.");
        }
        String current = repository.sampleRevision(npcName, draft.type());
        if (!current.equals(expectedRevision == null ? "MISSING" : expectedRevision)) {
            throw new IllegalStateException(
                    "The saved sample changed while this draft was open. Reopen Voice Recorder.");
        }
        UUID operation = UUID.randomUUID();
        Path sibling = contained(root, root.resolve("." + target.getFileName()
                + "." + operation + ".tmp"));
        Path rollback = contained(root, root.resolve(".voice-rollback")
                .resolve(target.getFileName() + "." + operation + ".bak"));
        String priorHash = Files.isRegularFile(target) ? sha256(target) : "MISSING";
        try {
            Files.createDirectories(root);
            Files.write(sibling, Files.readAllBytes(draft.path()));
            if (!VoicePresetRepository.validWave(sibling)) {
                throw new IllegalStateException("Temporary voice sample failed validation.");
            }
            if (Files.isRegularFile(target)) {
                Files.createDirectories(rollback.getParent());
                Files.copy(target, rollback, StandardCopyOption.REPLACE_EXISTING,
                        StandardCopyOption.COPY_ATTRIBUTES);
            }
            atomicReplace(sibling, target);
            if (!VoicePresetRepository.validWave(target)) {
                if (Files.isRegularFile(rollback)) atomicReplace(rollback, target);
                throw new IllegalStateException("Saved voice sample failed authoritative reread.");
            }
            String hash = sha256(target);
            repository.scan(npcName);
            cacheInvalidator.accept(target);
            appendAudit(root, "SAVE", stableNpcId, draft.type(), operation,
                    priorHash, hash, draft.audio());
            Files.deleteIfExists(draft.path());
            diagnostics.accept("NPC_AUTHORING_VOICE_SAVE timestamp=" + Instant.now()
                    + " npcStableId=" + stableNpcId + " emotion=" + draft.type()
                    + " revision=" + hash + " priorHash=" + priorHash
                    + " rollback=" + (Files.isRegularFile(rollback) ? rollback : "NONE"));
            return new SaveResult(target, hash, priorHash, rollback);
        } catch (IOException failure) {
            try { Files.deleteIfExists(sibling); } catch (IOException ignored) { }
            throw new IllegalStateException("Voice sample save failed; prior sample is unchanged.",
                    failure);
        }
    }

    public DeleteResult deleteSaved(String npcName, UUID stableNpcId, VoiceSampleType type,
            String expectedRevision) {
        Path root = repository.profileVoiceDirectory(npcName);
        Path target = repository.canonicalSamplePath(npcName, type);
        String current = repository.sampleRevision(npcName, type);
        if (!current.equals(expectedRevision == null ? "MISSING" : expectedRevision)) {
            throw new IllegalStateException("The saved sample changed. Reopen Voice Recorder.");
        }
        if (!Files.isRegularFile(target)) return new DeleteResult(false, "MISSING", null);
        UUID operation = UUID.randomUUID();
        Path trash = contained(root, root.resolve(".voice-trash")
                .resolve(target.getFileName() + "." + operation + ".wav"));
        String prior = sha256(target);
        try {
            Files.createDirectories(trash.getParent());
            atomicReplace(target, trash);
            repository.scan(npcName);
            cacheInvalidator.accept(target);
            appendAudit(root, "DELETE", stableNpcId, type, operation, prior, "MISSING", null);
            diagnostics.accept("NPC_AUTHORING_VOICE_DELETE timestamp=" + Instant.now()
                    + " npcStableId=" + stableNpcId + " emotion=" + type
                    + " priorHash=" + prior + " recoverableTrash=" + trash);
            return new DeleteResult(true, prior, trash);
        } catch (IOException failure) {
            throw new IllegalStateException("Could not delete the saved voice sample.", failure);
        }
    }

    public void discard(Draft draft) {
        if (draft == null) return;
        try { Files.deleteIfExists(draft.path()); }
        catch (IOException failure) {
            diagnostics.accept("NPC_AUTHORING_VOICE_CLEANUP_FAILED draftId="
                    + draft.recordingId() + " reason=" + failure.getClass().getSimpleName());
        }
    }

    private static void appendAudit(Path root, String action, UUID stableId,
            VoiceSampleType type, UUID operation, String prior, String current,
            VoiceDraftAudio audio) throws IOException {
        String line = "{\"at\":\"" + Instant.now() + "\",\"action\":\"" + action
                + "\",\"npcStableId\":\"" + stableId + "\",\"emotion\":\"" + type
                + "\",\"operationId\":\"" + operation + "\",\"priorHash\":\""
                + prior + "\",\"hash\":\"" + current + "\",\"durationMillis\":"
                + (audio == null ? 0 : audio.durationMillis()) + "}" + System.lineSeparator();
        Files.writeString(contained(root, root.resolve("voice-authoring-audit.jsonl")), line,
                StandardCharsets.UTF_8, java.nio.file.StandardOpenOption.CREATE,
                java.nio.file.StandardOpenOption.APPEND);
    }

    private static Path contained(Path root, Path candidate) {
        Path normalizedRoot = root.toAbsolutePath().normalize();
        Path normalized = candidate.toAbsolutePath().normalize();
        if (!normalized.startsWith(normalizedRoot)) {
            throw new IllegalArgumentException("Voice path escaped its NPC profile.");
        }
        return normalized;
    }

    private static void atomicReplace(Path source, Path target) throws IOException {
        try {
            Files.move(source, target, StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException unsupported) {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static String sha256(Path path) {
        if (path == null || !Files.isRegularFile(path)) return "MISSING";
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (var input = Files.newInputStream(path)) {
                byte[] buffer = new byte[8192];
                int read;
                while ((read = input.read(buffer)) >= 0) digest.update(buffer, 0, read);
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (Exception failure) {
            throw new IllegalStateException("Could not hash voice sample.", failure);
        }
    }

    public record Draft(UUID recordingId, VoiceSampleType type, Path path, String hash,
            VoiceDraftAudio audio) { }
    public record SaveResult(Path path, String revision, String priorHash, Path rollback) { }
    public record DeleteResult(boolean deleted, String priorHash, Path recoverableTrash) { }
}
