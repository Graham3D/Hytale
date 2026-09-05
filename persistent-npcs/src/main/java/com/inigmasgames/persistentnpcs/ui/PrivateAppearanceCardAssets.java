package com.inigmasgames.persistentnpcs.ui;

import com.hypixel.hytale.protocol.Asset;
import com.hypixel.hytale.protocol.ToClientPacket;
import com.hypixel.hytale.protocol.packets.setup.*;
import com.hypixel.hytale.server.core.asset.common.CommonAsset;
import java.util.*;
import java.util.function.Consumer;

/** Private packet sink only. Never enters CommonAssetRegistry or broadcasts to players.
 * One display-resolution slot bank; unchanged batches never rebuild the shared UI atlas.
 * Client rebuild/readiness is a CONNECTED gate, not a claimed server-side acknowledgment.
 */
public final class PrivateAppearanceCardAssets implements AutoCloseable {
    private final Consumer<ToClientPacket> sink;
    private final String prefix = "UI/Custom/Pages/ImmersiveNpcAppearance/Live/" + UUID.randomUUID() + "/";
    private final Map<String, Asset> owned = new LinkedHashMap<>();
    private int lastUploaded, lastRemoved;
    private boolean lastRebuild;
    private boolean closed;
    public PrivateAppearanceCardAssets(Consumer<ToClientPacket> sink) { this.sink = Objects.requireNonNull(sink); }

    /** Must execute on the player's world thread, before the matching Custom UI update. */
    public Map<Integer, String> publish(List<AppearanceCardJobs.Card> cards) {
        if (closed) return Map.of();
        if (cards.size() > AppearanceCardJobs.MAX_CARDS) throw new IllegalArgumentException("Too many cards");
        long bytes = 0;
        Set<Integer> slots = new HashSet<>();
        for (var card : cards) {
            int slot = card.slot();
            byte[] png = card.image().png();
            if (slot < 0 || slot >= AppearanceCardJobs.MAX_CARDS || !slots.add(slot)
                    || png.length > AppearanceColorCards.MAX_CARD_BYTES || png.length < 24
                    || java.nio.ByteBuffer.wrap(png).getLong() != 0x89504e470d0a1a0aL
                    || java.nio.ByteBuffer.wrap(png).getInt(16) != AppearanceColorCards.CLIENT_WIDTH
                    || java.nio.ByteBuffer.wrap(png).getInt(20) != AppearanceColorCards.CLIENT_HEIGHT)
                throw new IllegalArgumentException("Invalid private card batch");
            bytes += card.image().png().length;
        }
        if (bytes > 4 * 1024 * 1024) throw new IllegalArgumentException("Private batch byte budget exceeded");
        lastUploaded = 0; lastRemoved = 0; lastRebuild = false;
        Set<String> desired = new HashSet<>();
        for (var card : cards) desired.add(prefix + card.slot() + ".png");
        Asset[] obsolete = owned.entrySet().stream().filter(entry -> !desired.contains(entry.getKey()))
                .map(Map.Entry::getValue).toArray(Asset[]::new);
        // The enclosing page has already replaced old live card nodes with references.
        if (obsolete.length > 0) {
            sink.accept(new RemoveAssets(obsolete));
            for (Asset asset : obsolete) owned.remove(asset.name);
            lastRemoved = obsolete.length;
        }
        Map<Integer, String> paths = new LinkedHashMap<>();
        for (var card : cards) {
            String name = prefix + card.slot() + ".png";
            Asset asset = new Asset(CommonAsset.hash(card.image().png()), name);
            Asset previous = owned.get(name);
            // Stable path, changed content hash; no duplicate retained bank or player state packets.
            if (previous == null || !previous.hash.equals(asset.hash)) {
                owned.put(name, asset); // Track before sending so partial failures remain cleanable.
                sink.accept(new AssetInitialize(asset, card.image().png().length));
                sink.accept(new AssetPart(card.image().png()));
                sink.accept(new AssetFinalize());
                lastUploaded++;
            }
            paths.put(card.slot(), name.substring("UI/Custom/".length()));
        }
        lastRebuild = lastUploaded > 0 || lastRemoved > 0;
        if (lastRebuild) sink.accept(new RequestCommonAssetsRebuild());
        return Map.copyOf(paths);
    }
    public int residentNames() { return owned.size(); }
    public int lastUploaded() { return lastUploaded; }
    public int lastRemoved() { return lastRemoved; }
    public boolean lastRebuild() { return lastRebuild; }
    public long residentTexels() { return (long) owned.size() * AppearanceColorCards.CLIENT_WIDTH * AppearanceColorCards.CLIENT_HEIGHT; }
    /** Call only after replacement UI no longer references the old thumbnail nodes. */
    public void release() {
        Asset[] assets = owned.values().toArray(Asset[]::new);
        owned.clear();
        if (assets.length > 0) {
            sink.accept(new RemoveAssets(assets));
            sink.accept(new RequestCommonAssetsRebuild());
        }
    }
    @Override public void close() {
        if (closed) return;
        closed = true;
        release();
    }
}
