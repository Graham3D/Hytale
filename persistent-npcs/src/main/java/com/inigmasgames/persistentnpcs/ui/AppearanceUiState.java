package com.inigmasgames.persistentnpcs.ui;

import com.hypixel.hytale.protocol.PlayerSkin;
import com.hypixel.hytale.protocol.packets.interface_.CustomUICommand;
import com.hypixel.hytale.protocol.packets.interface_.CustomUICommandType;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.inigmasgames.persistentnpcs.appearance.NpcAppearanceCatalogService.*;
import com.inigmasgames.persistentnpcs.appearance.NpcSkinCodecAdapter;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;

/** Per-page immutable value fingerprints. Contains no models, images, asset IDs or GPU handles. */
public final class AppearanceUiState {
    public record Hashes(String catalogPageHash, String selectedCosmeticHash,
            String selectedColorHash, String draftSkinHash, String previewHash) { }
    private final Map<String, CustomUICommand> sent = new HashMap<>();
    private boolean degraded;
    private Hashes hashes;

    public static String hash(String value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch (java.security.NoSuchAlgorithmException impossible) { throw new AssertionError(impossible); }
    }
    public static String skinHash(PlayerSkin skin) {
        StringBuilder material = new StringBuilder();
        for (Category category : Category.values()) {
            String part = NpcSkinCodecAdapter.selection(skin, category);
            material.append(category).append(':').append(part == null ? -1 : part.length()).append(':').append(part).append('\n');
        }
        return hash(material.toString());
    }
    public static String pageHash(CatalogPage page) {
        StringBuilder material = new StringBuilder(page.category()+"|"+page.query()+"|"+page.pageIndex()+"|"+page.totalCount());
        for (var option : page.descriptors()) material.append('\n').append(option.cosmeticId()).append('\t').append(option.displayName());
        return hash(material.toString());
    }
    public void updateHashes(CatalogPage page, PlayerSkin skin, String previewHash) {
        String selection = NpcSkinCodecAdapter.selection(skin, page.category());
        hashes = new Hashes(pageHash(page), hash(page.category()+":"+NpcSkinCodecAdapter.partId(selection)),
                hash(page.category()+":"+NpcSkinCodecAdapter.colorId(selection)+":"+NpcSkinCodecAdapter.variantId(selection)),
                skinHash(skin), previewHash == null ? "" : previewHash);
    }
    public Hashes hashes() { return hashes; }
    public boolean degraded() { return degraded; }
    /** Real log/error ingress; no fabricated client telemetry. Latches until this page/session ends. */
    public boolean observeFailure(String message) {
        String lower = message == null ? "" : message.toLowerCase(Locale.ROOT);
        boolean atlas = lower.contains("texture atlas needs")
                || (lower.contains("dropping") && lower.contains("images"))
                || (lower.contains("atlas") && (lower.contains("allocation fail") || lower.contains("out of memory")));
        boolean changed = atlas && !degraded;
        degraded |= atlas;
        return changed;
    }
    public String degradedMessage() { return "UI resource failure: fully exit and restart Hytale. Your appearance draft is retained."; }
    public void remount() { sent.clear(); hashes = null; }
    public void forget(String prefix) { sent.keySet().removeIf(key -> key.startsWith(prefix)); }
    public void seed(UICommandBuilder builder) { filter(builder); }
    public UICommandBuilder filter(UICommandBuilder builder) {
        List<CustomUICommand> changed = new ArrayList<>();
        for (CustomUICommand command : builder.getCommands()) {
            if (command.type == CustomUICommandType.Set && command.selector != null) {
                CustomUICommand previous = sent.put(command.selector, new CustomUICommand(command));
                if (command.equals(previous)) continue;
            }
            changed.add(new CustomUICommand(command));
        }
        CustomUICommand[] result = changed.toArray(CustomUICommand[]::new);
        return new UICommandBuilder() { @Override public CustomUICommand[] getCommands() { return result.clone(); } };
    }
}
