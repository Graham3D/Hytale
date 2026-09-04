package com.inigmasgames.persistentnpcs.appearance;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.inigmasgames.persistentnpcs.json.JsonFiles;
import com.inigmasgames.persistentnpcs.profile.AppearanceRepository;
import com.inigmasgames.persistentnpcs.profile.ProfileRepository;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.Random;
import java.util.UUID;
import java.util.function.Consumer;

/** Atomic, optimistic save transaction for the profile-local Hytale skin JSON. */
public final class NpcAppearanceAuthoringService {
    private final AppearanceRepository appearances;
    private final NpcAppearanceCatalogService catalog;
    private final NpcSkinCodecAdapter adapter;
    private final Consumer<String> diagnostics;
    private final Object commitLock = new Object();

    public NpcAppearanceAuthoringService(AppearanceRepository appearances,
            NpcAppearanceCatalogService catalog, NpcSkinCodecAdapter adapter,
            Consumer<String> diagnostics) {
        this.appearances = appearances;
        this.catalog = catalog;
        this.adapter = adapter;
        this.diagnostics = diagnostics == null ? ignored -> { } : diagnostics;
    }

    public NpcAppearanceDraft begin(String npcName, UUID stableNpcId,
            UUID sessionId, long editorGeneration) {
        String safe = ProfileRepository.sanitizeProfileName(npcName);
        catalog.snapshot(); // Hard A5 preflight: enumerate the live registry before editing.
        Path path = appearances.requireAuthoritativeSkinFile(safe);
        NpcSkinCodecAdapter.SkinDocument document = adapter.read(path);
        long revision = readRevision(path);
        diagnostics.accept("NPC_AUTHORING_APPEARANCE_DRAFT_OPENED timestamp=" + Instant.now()
                + " sessionId=" + sessionId + " stableNpcId=" + stableNpcId
                + " npc=" + safe + " baseRevision=" + revision
                + " baseHash=" + document.canonicalHash()
                + " preservedRootFields=" + document.raw().size());
        return new NpcAppearanceDraft(sessionId, stableNpcId, safe, editorGeneration,
                revision, document.canonicalHash(), document);
    }

    public SelectionResult select(NpcAppearanceDraft draft,
            NpcAppearanceCatalogService.Category category, String cosmeticId,
            String colorId, String variantId) {
        requireDraft(draft);
        NpcAppearanceCatalogService.CosmeticOptionDescriptor option =
                catalog.require(category, cosmeticId == null ? "" : cosmeticId);
        String encoded = option.encoded(colorId, variantId);
        com.hypixel.hytale.protocol.PlayerSkin candidate = adapter.with(
                draft.currentSkin(), category, encoded);
        draft.apply(category, candidate);
        diagnostics.accept("NPC_AUTHORING_APPEARANCE_SELECTION_VALIDATED timestamp="
                + Instant.now() + " draftId=" + draft.draftId()
                + " category=" + category + " selection=" + safe(encoded)
                + " source=" + option.source() + " valid=true");
        return new SelectionResult(option, encoded);
    }

    public void randomize(NpcAppearanceDraft draft) {
        requireDraft(draft);
        com.hypixel.hytale.server.core.cosmetics.CosmeticsModule module =
                com.hypixel.hytale.server.core.cosmetics.CosmeticsModule.get();
        if (module == null) throw new IllegalStateException(
                "Hytale cosmetics runtime is not available.");
        com.hypixel.hytale.protocol.PlayerSkin candidate = module.generateRandomSkin(
                new Random(System.nanoTime() ^ draft.draftId().getLeastSignificantBits()));
        adapter.validate(candidate);
        draft.applyRandom(candidate);
        diagnostics.accept("NPC_AUTHORING_APPEARANCE_RANDOMIZED timestamp=" + Instant.now()
                + " draftId=" + draft.draftId() + " valid=true");
    }

    public SaveResult save(NpcAppearanceDraft draft, UUID writerPlayerId) {
        requireDraft(draft);
        synchronized (commitLock) {
            Path path = appearances.requireAuthoritativeSkinFile(draft.npcName());
            NpcSkinCodecAdapter.SkinDocument current = adapter.read(path);
            long currentRevision = readRevision(path);
            if (currentRevision != draft.baseRevision()
                    || !current.canonicalHash().equals(draft.baseHash())) {
                throw new RevisionConflictException("Appearance changed while this draft was open. "
                        + "Draft preserved; reload the editor before saving.");
            }
            NpcSkinCodecAdapter.SkinDocument candidate = draft.candidate(adapter);
            Path temporary = path.resolveSibling(path.getFileName() + ".authoring-"
                    + draft.draftId() + ".tmp");
            Path rollback = path.resolveSibling(path.getFileName() + ".rollback-r"
                    + currentRevision);
            try {
                Files.writeString(temporary, NpcSkinCodecAdapter.serialized(candidate),
                        StandardCharsets.UTF_8);
                NpcSkinCodecAdapter.SkinDocument reread = adapter.readValidated(temporary);
                if (!reread.canonicalHash().equals(candidate.canonicalHash())) {
                    throw new IllegalStateException("Appearance candidate did not round-trip.");
                }
                Files.copy(path, rollback, StandardCopyOption.REPLACE_EXISTING,
                        StandardCopyOption.COPY_ATTRIBUTES);
                try {
                    Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING,
                            StandardCopyOption.ATOMIC_MOVE);
                } catch (AtomicMoveNotSupportedException ignored) {
                    Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING);
                }
                NpcSkinCodecAdapter.SkinDocument committed = adapter.readValidated(path);
                long nextRevision = currentRevision + 1;
                JsonObject revision = new JsonObject();
                revision.addProperty("revision", nextRevision);
                revision.addProperty("hash", committed.canonicalHash());
                revision.addProperty("updatedAt", Instant.now().toString());
                JsonFiles.writeAtomic(revisionPath(path), revision);
                appendAudit(path, draft, writerPlayerId, current.canonicalHash(),
                        committed.canonicalHash(), nextRevision);
                diagnostics.accept("NPC_AUTHORING_APPEARANCE_COMMITTED timestamp=" + Instant.now()
                        + " draftId=" + draft.draftId() + " stableNpcId="
                        + draft.stableNpcId() + " revision=" + nextRevision
                        + " dirtyCategories=" + draft.dirtyCategories()
                        + " rollback=" + rollback);
                return new SaveResult(committed.skin(), adapter.createModel(committed.skin()),
                        nextRevision, committed.canonicalHash(), path, rollback);
            } catch (IOException failure) {
                throw new IllegalStateException("Appearance save transaction failed.", failure);
            } finally {
                try { Files.deleteIfExists(temporary); } catch (IOException ignored) { }
            }
        }
    }

    private void appendAudit(Path skinPath, NpcAppearanceDraft draft, UUID writer,
            String beforeHash, String afterHash, long revision) {
        JsonObject event = new JsonObject();
        event.addProperty("timestamp", Instant.now().toString());
        event.addProperty("event", "NPC_AUTHORING_APPEARANCE_COMMIT");
        event.addProperty("writerPlayerId", writer == null ? "UNKNOWN" : writer.toString());
        event.addProperty("sessionId", draft.sessionId().toString());
        event.addProperty("draftId", draft.draftId().toString());
        event.addProperty("stableNpcId", draft.stableNpcId().toString());
        event.addProperty("revision", revision);
        event.addProperty("beforeHash", beforeHash);
        event.addProperty("afterHash", afterHash);
        var identity = catalog.snapshot().identity();
        event.addProperty("hytaleBuildId", identity.hytaleBuildId());
        event.addProperty("registryHash", identity.registryHash());
        event.addProperty("enabledAssetPackSetHash", identity.enabledAssetPackSetHash());
        event.addProperty("skinCodecAdapterVersion", identity.adapterVersion());
        event.add("dirtyCategories", JsonFiles.GSON.toJsonTree(draft.dirtyCategories()
                .stream().map(Enum::name).sorted().toList()));
        try {
            Files.writeString(skinPath.resolveSibling("appearance-authoring-audit.jsonl"),
                    JsonFiles.GSON.toJson(event) + System.lineSeparator(),
                    StandardCharsets.UTF_8, java.nio.file.StandardOpenOption.CREATE,
                    java.nio.file.StandardOpenOption.APPEND);
        } catch (IOException failure) {
            diagnostics.accept("NPC_AUTHORING_APPEARANCE_AUDIT_FAILED timestamp=" + Instant.now()
                    + " error=" + safe(failure.getMessage()));
        }
    }

    private static long readRevision(Path skinPath) {
        Path revision = revisionPath(skinPath);
        if (!Files.isRegularFile(revision)) return 1L;
        try {
            var parsed = JsonParser.parseString(Files.readString(revision,
                    StandardCharsets.UTF_8));
            return parsed.isJsonObject() && parsed.getAsJsonObject().has("revision")
                    ? Math.max(1L, parsed.getAsJsonObject().get("revision").getAsLong()) : 1L;
        } catch (IOException | RuntimeException invalid) {
            throw new IllegalStateException("Appearance revision metadata is invalid.", invalid);
        }
    }

    private static Path revisionPath(Path skinPath) {
        return skinPath.resolveSibling("skin-authoring-revision.json");
    }

    private static void requireDraft(NpcAppearanceDraft draft) {
        if (draft == null) throw new IllegalArgumentException("Appearance draft is required.");
    }

    private static String safe(String value) {
        return value == null ? "NONE" : value.replaceAll("\\s+", "_");
    }

    public record SelectionResult(
            NpcAppearanceCatalogService.CosmeticOptionDescriptor option,
            String encodedSelection) { }

    public record SaveResult(com.hypixel.hytale.protocol.PlayerSkin skin,
            com.hypixel.hytale.server.core.asset.type.model.config.Model model,
            long revision, String hash, Path path, Path rollback) {
        public SaveResult {
            skin = new com.hypixel.hytale.protocol.PlayerSkin(skin);
        }
        @Override public com.hypixel.hytale.protocol.PlayerSkin skin() {
            return new com.hypixel.hytale.protocol.PlayerSkin(skin);
        }
    }

    public static final class RevisionConflictException extends IllegalStateException {
        public RevisionConflictException(String message) { super(message); }
    }
}
