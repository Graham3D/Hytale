package com.inigmasgames.persistentnpcs.profile;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.inigmasgames.persistentnpcs.json.JsonFiles;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

/** Optimistic, atomic authoring transaction that patches the raw JSON tree. */
public final class NpcProfileAuthoringService {
    private final ProfileRepository profiles;
    private final NpcProfileRegistry registry;
    private final Consumer<String> diagnostics;
    private final Object commitLock = new Object();

    public NpcProfileAuthoringService(ProfileRepository profiles,
            NpcProfileRegistry registry, Consumer<String> diagnostics) {
        this.profiles = profiles;
        this.registry = registry;
        this.diagnostics = diagnostics == null ? ignored -> { } : diagnostics;
    }

    public NpcProfileDraft begin(String name, UUID sessionId, long editorGeneration) {
        Path path = profiles.profilePath(name);
        JsonObject raw = readObject(path);
        NpcProfile profile = JsonFiles.GSON.fromJson(raw, NpcProfile.class).validated();
        Revision revision = readRevision(path);
        String hash = hash(path);
        diagnostics.accept("NPC_PROFILE_DRAFT_OPENED timestamp=" + Instant.now()
                + " sessionId=" + sessionId + " draftBaseRevision=" + revision.value
                + " baseHash=" + hash + " stableNpcId=" + profile.stableId()
                + " preservedRootFields=" + raw.size());
        return new NpcProfileDraft(sessionId, profile.stableId(), editorGeneration,
                revision.value, hash, profile.name(), raw);
    }

    public SaveResult save(NpcProfileDraft draft, UUID writerPlayerId) {
        if (draft == null) throw new IllegalArgumentException("Profile draft is required.");
        synchronized (commitLock) {
            Path path = profiles.profilePath(draft.profileName());
            NpcProfile current = JsonFiles.GSON.fromJson(readObject(path), NpcProfile.class)
                    .validated();
            Revision currentRevision = readRevision(path);
            String currentHash = hash(path);
            if (!current.stableId().equals(draft.stableNpcId())) {
                throw new RevisionConflictException("NPC identity changed while this draft was open.");
            }
            if (currentRevision.value != draft.baseRevision()
                    || !currentHash.equals(draft.baseHash())) {
                throw new RevisionConflictException("Profile changed while this draft was open. "
                        + "Draft preserved; reload the editor before saving.");
            }

            JsonObject candidate = draft.candidateDocument();
            NpcProfile validated = JsonFiles.GSON.fromJson(candidate, NpcProfile.class).validated();
            if (!validated.stableId().equals(draft.stableNpcId())) {
                throw new IllegalArgumentException("Stable NPC identity is immutable.");
            }
            if (!validated.name().equals(draft.profileName())) {
                throw new IllegalArgumentException("Display-name rename requires a dedicated identity migration.");
            }

            Path temporary = path.resolveSibling(path.getFileName() + ".authoring-"
                    + draft.draftId() + ".tmp");
            Path rollback = path.resolveSibling(path.getFileName() + ".rollback-r"
                    + currentRevision.value);
            try {
                Files.writeString(temporary, JsonFiles.GSON.toJson(candidate),
                        StandardCharsets.UTF_8);
                NpcProfile reread = JsonFiles.read(temporary, NpcProfile.class).validated();
                if (!reread.stableId().equals(draft.stableNpcId())) {
                    throw new IllegalArgumentException("Candidate identity validation failed.");
                }
                Files.copy(path, rollback, StandardCopyOption.REPLACE_EXISTING,
                        StandardCopyOption.COPY_ATTRIBUTES);
                try {
                    Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING,
                            StandardCopyOption.ATOMIC_MOVE);
                } catch (AtomicMoveNotSupportedException ignored) {
                    Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING);
                }
                NpcProfile committed = JsonFiles.read(path, NpcProfile.class).validated();
                long nextRevision = currentRevision.value + 1;
                JsonFiles.writeAtomic(revisionPath(path), new Revision(nextRevision,
                        hash(path), Instant.now().toString()));
                registry.register(committed);
                appendAudit(path, draft, writerPlayerId, currentHash, hash(path), nextRevision);
                diagnostics.accept("NPC_PROFILE_DRAFT_COMMITTED timestamp=" + Instant.now()
                        + " draftId=" + draft.draftId() + " stableNpcId=" + draft.stableNpcId()
                        + " revision=" + nextRevision + " dirtyFields=" + draft.dirtyFields());
                return new SaveResult(committed, nextRevision, hash(path), rollback);
            } catch (IOException failure) {
                throw new IllegalStateException("Profile save transaction failed.", failure);
            } finally {
                try { Files.deleteIfExists(temporary); } catch (IOException ignored) { }
            }
        }
    }

    private void appendAudit(Path profilePath, NpcProfileDraft draft, UUID writer,
            String beforeHash, String afterHash, long revision) {
        JsonObject event = new JsonObject();
        event.addProperty("timestamp", Instant.now().toString());
        event.addProperty("event", "NPC_PROFILE_AUTHORED_COMMIT");
        event.addProperty("writerPlayerId", writer == null ? "UNKNOWN" : writer.toString());
        event.addProperty("sessionId", draft.sessionId().toString());
        event.addProperty("draftId", draft.draftId().toString());
        event.addProperty("stableNpcId", draft.stableNpcId().toString());
        event.addProperty("revision", revision);
        event.addProperty("beforeHash", beforeHash);
        event.addProperty("afterHash", afterHash);
        event.addProperty("provenance", draft.provenance().name());
        if (draft.acceptedProposal() != null) {
            event.addProperty("proposalRequestId",
                    draft.acceptedProposal().requestId().toString());
            event.addProperty("proposalScope", draft.acceptedProposal().scope());
            event.addProperty("proposalProvider", draft.acceptedProposal().provider());
            event.addProperty("proposalModel", draft.acceptedProposal().model());
            event.add("acceptedProposalFields", JsonFiles.GSON.toJsonTree(
                    draft.acceptedProposal().changes().keySet().stream()
                            .map(Enum::name).sorted().toList()));
        }
        event.add("dirtyFields", JsonFiles.GSON.toJsonTree(draft.dirtyFields().stream()
                .map(Enum::name).sorted().toList()));
        try {
            Files.writeString(profilePath.resolveSibling("profile-authoring-audit.jsonl"),
                    JsonFiles.GSON.toJson(event) + System.lineSeparator(),
                    StandardCharsets.UTF_8, java.nio.file.StandardOpenOption.CREATE,
                    java.nio.file.StandardOpenOption.APPEND);
        } catch (IOException failure) {
            diagnostics.accept("NPC_PROFILE_AUDIT_FAILED timestamp=" + Instant.now()
                    + " error=" + failure.getMessage());
        }
    }

    private static JsonObject readObject(Path path) {
        try {
            var parsed = JsonParser.parseString(Files.readString(path, StandardCharsets.UTF_8));
            if (!parsed.isJsonObject()) throw new IllegalArgumentException("Profile JSON must be an object.");
            return parsed.getAsJsonObject();
        } catch (IOException failure) {
            throw new IllegalStateException("Could not read profile document.", failure);
        }
    }

    private static Revision readRevision(Path path) {
        Path revision = revisionPath(path);
        if (!Files.isRegularFile(revision)) return new Revision(1, hash(path), Instant.EPOCH.toString());
        try {
            Revision value = JsonFiles.read(revision, Revision.class);
            return value == null || value.value < 1
                    ? new Revision(1, hash(path), Instant.EPOCH.toString()) : value;
        } catch (RuntimeException invalid) {
            return new Revision(1, hash(path), Instant.EPOCH.toString());
        }
    }

    private static Path revisionPath(Path path) {
        return path.resolveSibling("profile-authoring-revision.json");
    }

    private static String hash(Path path) {
        try {
            return HexFormat.of().withUpperCase().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(Files.readAllBytes(path)));
        } catch (Exception failure) {
            throw new IllegalStateException("Could not hash profile document.", failure);
        }
    }

    private record Revision(long value, String hash, String updatedAt) { }
    public record SaveResult(NpcProfile profile, long revision, String hash, Path rollbackPath) { }
    public static final class RevisionConflictException extends IllegalStateException {
        public RevisionConflictException(String message) { super(message); }
    }
}
