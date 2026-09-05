package com.inigmasgames.persistentnpcs.ui;

import com.hypixel.hytale.protocol.Asset;
import com.hypixel.hytale.protocol.ToClientPacket;
import com.hypixel.hytale.protocol.packets.setup.*;
import com.hypixel.hytale.server.core.asset.common.CommonAsset;
import java.util.*;
import java.util.function.Consumer;

/** Private packet sink only. Never enters CommonAssetRegistry or broadcasts to players.
 * Two banks of fixed slot names bound resident names across arbitrarily many color changes.
 * Client rebuild/readiness is a CONNECTED gate, not a claimed server-side acknowledgment.
 */
public final class PrivateAppearanceCardAssets implements AutoCloseable {
    private final Consumer<ToClientPacket> sink;
    private final String prefix = "UI/Custom/Pages/ImmersiveNpcAppearance/Live/" + UUID.randomUUID() + "/";
    private final Map<String, Asset> owned = new LinkedHashMap<>();
    private int bank;
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
            if (slot < 0 || slot >= AppearanceCardJobs.MAX_CARDS || !slots.add(slot)
                    || card.image().png().length > AppearanceColorCards.MAX_CARD_BYTES)
                throw new IllegalArgumentException("Invalid private card batch");
            bytes += card.image().png().length;
        }
        if (bytes > 4 * 1024 * 1024) throw new IllegalArgumentException("Private batch byte budget exceeded");
        bank = 1 - bank;
        Map<Integer, String> paths = new LinkedHashMap<>();
        for (var card : cards) {
            String name = prefix + bank + "/" + card.slot() + ".png";
            Asset asset = new Asset(CommonAsset.hash(card.image().png()), name);
            Asset previous = owned.get(name);
            // Same-bank texture replacements need a changed content hash; no player state packets.
            if (previous == null || !previous.hash.equals(asset.hash)) {
                owned.put(name, asset); // Track before sending so partial failures remain cleanable.
                sink.accept(new AssetInitialize(asset, card.image().png().length));
                sink.accept(new AssetPart(card.image().png()));
                sink.accept(new AssetFinalize());
            }
            paths.put(card.slot(), name.substring("UI/Custom/".length()));
        }
        if (!cards.isEmpty()) sink.accept(new RequestCommonAssetsRebuild());
        return Map.copyOf(paths);
    }
    public int residentNames() { return owned.size(); }
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
