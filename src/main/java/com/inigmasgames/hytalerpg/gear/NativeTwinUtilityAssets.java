package com.inigmasgames.hytalerpg.gear;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.hypixel.hytale.server.core.asset.type.item.config.Item;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/** A same-base owned dagger may enter Utility while a valid WA-141 mainhand is active. */
final class NativeTwinUtilityAssets {
    private NativeTwinUtilityAssets() {}
    static boolean eligible(GearEffectSnapshot accepted, java.util.UUID mainId, GearInstance candidate) {
        if (mainId == null || candidate == null || mainId.equals(candidate.identity())
                || !candidate.baseId().startsWith("gm.daggers_")) return false;
        var local = accepted.forItem(mainId);
        return !local.empty() && local.items().getFirst().baseId().equals(candidate.baseId())
                && local.value("WA-141") > 0;
    }
    static String variantId(String carrier) { return carrier + "__Offhand"; }
    static boolean variantOf(String id, String carrier) { return id.equals(variantId(carrier)); }
    static synchronized String publish(String carrier) {
        String id = variantId(carrier);
        if (Item.getAssetMap().getAsset(id) != null) return id;
        NativeSwordActionAssets.load(Item.getAssetStore(), Map.of(id, renderCarrier(carrier)));
        if (Item.getAssetMap().getAsset(id) == null) throw new IllegalStateException("Twin Utility carrier not loaded " + id);
        return id;
    }
    static String renderCarrier(String carrier) {
        try (var stream = NativeTwinUtilityAssets.class.getResourceAsStream(
                "/Server/Item/Items/RPG/Gear/" + carrier + ".json")) {
            if (stream == null) throw new IllegalArgumentException("Missing dagger carrier " + carrier);
            JsonObject item = JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
            if (!carrier.startsWith("RPG_Gear_daggers_") || !"Root_Weapon_Daggers_Primary".equals(
                    item.getAsJsonObject("Interactions").get("Primary").getAsString()))
                throw new IllegalArgumentException("Unreviewed twin Utility carrier " + carrier);
            JsonObject utility = item.has("Utility") ? item.getAsJsonObject("Utility") : new JsonObject();
            utility.addProperty("Compatible", true);
            utility.addProperty("Usable", false);
            item.add("Utility", utility);
            return item.toString();
        } catch (java.io.IOException failure) { throw new java.io.UncheckedIOException(failure); }
    }
}
