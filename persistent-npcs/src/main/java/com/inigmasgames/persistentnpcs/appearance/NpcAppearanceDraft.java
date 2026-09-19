package com.inigmasgames.persistentnpcs.appearance;

import com.google.gson.JsonObject;
import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;

/** Server-owned A5 appearance draft. UI events contain intent, never draft state. */
public final class NpcAppearanceDraft {
    private final UUID draftId = UUID.randomUUID();
    private final UUID sessionId;
    private final UUID stableNpcId;
    private final String npcName;
    private final long editorGeneration;
    private final long baseRevision;
    private final String baseHash;
    private final JsonObject extensionPreservingBase;
    private final com.hypixel.hytale.protocol.PlayerSkin initialSkin;
    private com.hypixel.hytale.protocol.PlayerSkin currentSkin;
    private final EnumSet<NpcAppearanceCatalogService.Category> dirty =
            EnumSet.noneOf(NpcAppearanceCatalogService.Category.class);
    private long previewGeneration;

    NpcAppearanceDraft(UUID sessionId, UUID stableNpcId, String npcName,
            long editorGeneration, long baseRevision, String baseHash,
            NpcSkinCodecAdapter.SkinDocument document) {
        this.sessionId = sessionId;
        this.stableNpcId = stableNpcId;
        this.npcName = npcName;
        this.editorGeneration = editorGeneration;
        this.baseRevision = baseRevision;
        this.baseHash = baseHash;
        this.extensionPreservingBase = document.raw();
        this.initialSkin = document.skin();
        this.currentSkin = document.skin();
    }

    public UUID draftId() { return draftId; }
    public UUID sessionId() { return sessionId; }
    public UUID stableNpcId() { return stableNpcId; }
    public String npcName() { return npcName; }
    public long editorGeneration() { return editorGeneration; }
    public long baseRevision() { return baseRevision; }
    public String baseHash() { return baseHash; }
    public boolean dirty() { return !dirty.isEmpty(); }
    public Set<NpcAppearanceCatalogService.Category> dirtyCategories() {
        return Set.copyOf(dirty);
    }
    public long previewGeneration() { return previewGeneration; }

    public com.hypixel.hytale.protocol.PlayerSkin currentSkin() {
        return new com.hypixel.hytale.protocol.PlayerSkin(currentSkin);
    }

    JsonObject extensionPreservingBase() { return extensionPreservingBase.deepCopy(); }

    void apply(NpcAppearanceCatalogService.Category category,
            com.hypixel.hytale.protocol.PlayerSkin candidate) {
        currentSkin = new com.hypixel.hytale.protocol.PlayerSkin(candidate);
        String initial = NpcSkinCodecAdapter.selection(initialSkin, category);
        String current = NpcSkinCodecAdapter.selection(currentSkin, category);
        if (java.util.Objects.equals(initial, current)) dirty.remove(category);
        else dirty.add(category);
        previewGeneration++;
    }

    void applyRandom(com.hypixel.hytale.protocol.PlayerSkin candidate) {
        currentSkin = new com.hypixel.hytale.protocol.PlayerSkin(candidate);
        dirty.clear();
        for (NpcAppearanceCatalogService.Category category
                : NpcAppearanceCatalogService.Category.values()) {
            if (!java.util.Objects.equals(
                    NpcSkinCodecAdapter.selection(initialSkin, category),
                    NpcSkinCodecAdapter.selection(currentSkin, category))) {
                dirty.add(category);
            }
        }
        previewGeneration++;
    }

    public void reset() {
        currentSkin = new com.hypixel.hytale.protocol.PlayerSkin(initialSkin);
        dirty.clear();
        previewGeneration++;
    }

    public NpcSkinCodecAdapter.SkinDocument candidate(NpcSkinCodecAdapter adapter) {
        return adapter.merge(extensionPreservingBase, currentSkin);
    }
}
