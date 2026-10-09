package com.inigmasgames.hytalerpg.gear;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.hypixel.hytale.server.core.asset.type.item.config.Item;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.Interaction;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.RootInteraction;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Bounded native dagger graph. Per-item carrier variants are published only when used. */
final class NativeTwinDaggersAssets {
    static final String ROOT = "RPG_Twin_Daggers_Root";
    private static final String PREFIX = "/rpg/gear/action-templates/twin-daggers/";
    private static final String ACTION = "RPG_Twin_Daggers_Weapon_Daggers_Primary";
    private static final String[] NODES = {"", "_Chain", "_Pounce", "_Pounce_Force", "_Pounce_StaminaCondition",
            "_Pounce_Stab", "_Pounce_Stab_Damage", "_Pounce_Stab_Force", "_Pounce_Stab_Selector",
            "_Pounce_Sweep", "_Pounce_Sweep_Damage", "_Pounce_Sweep_Effect", "_Pounce_Sweep_Selector",
            "_Stab_Left", "_Stab_Left_Damage", "_Stab_Left_Selector", "_Stab_Right", "_Stab_Right_Damage",
            "_Stab_Right_Selector", "_Swing_Left", "_Swing_Left_Damage", "_Swing_Left_Selector",
            "_Swing_Right", "_Swing_Right_Damage", "_Swing_Right_Selector"};
    private static final Set<String> PUBLISHED = new HashSet<>();
    private NativeTwinDaggersAssets() {}
    static String variantId(String carrier) { return carrier + "__Twin"; }
    static boolean variantOf(String id, String carrier) { return id.equals(variantId(carrier)); }

    static synchronized String publish(String carrier) {
        String variant = variantId(carrier);
        if (Item.getAssetMap().getAsset(variant) != null) return variant;
        if (PUBLISHED.size() >= 256) throw new IllegalStateException("Twin dagger carrier capacity exceeded");
        var assets = new LinkedHashMap<String,String>();
        for (var suffix : NODES) if (Interaction.getAssetMap().getAsset(ACTION + suffix) == null)
            assets.put(ACTION + suffix, template(ACTION + suffix));
        for (var extra : List.of("RPG_Twin_Daggers_Offhand_Selector", "RPG_Twin_Daggers_Skip"))
            if (Interaction.getAssetMap().getAsset(extra) == null) assets.put(extra, template(extra));
        if (!assets.isEmpty()) NativeSwordActionAssets.load(Interaction.getAssetStore(), assets);
        if (RootInteraction.getAssetMap().getAsset(ROOT) == null)
            NativeSwordActionAssets.load(RootInteraction.getAssetStore(), Map.of(ROOT,template(ROOT)));
        if (RootInteraction.getAssetMap().getAsset(ROOT) == null) throw new IllegalStateException("Twin dagger root not loaded");
        NativeSwordActionAssets.load(Item.getAssetStore(), Map.of(variant, renderCarrier(carrier)));
        if (Item.getAssetMap().getAsset(variant) == null) throw new IllegalStateException("Twin dagger carrier not loaded");
        PUBLISHED.add(variant);
        return variant;
    }

    static String renderCarrier(String carrier) {
        var root = read("/Server/Item/Items/RPG/Gear/" + carrier + ".json");
        if (!"Root_Weapon_Daggers_Primary".equals(root.getAsJsonObject("Interactions").get("Primary").getAsString()))
            throw new IllegalArgumentException("Unreviewed dagger carrier: " + carrier);
        root.getAsJsonObject("Interactions").addProperty("Primary", ROOT);
        var utility = root.has("Utility") ? root.getAsJsonObject("Utility") : new JsonObject();
        utility.addProperty("Compatible", true);
        utility.addProperty("Usable", false);
        root.add("Utility", utility);
        var weapon = root.getAsJsonObject("Weapon");
        weapon.addProperty("RenderDualWielded", true);
        return root.toString();
    }

    private static String template(String id) { return read(PREFIX + id + ".json").toString(); }
    private static JsonObject read(String path) {
        try (var input = NativeTwinDaggersAssets.class.getResourceAsStream(path)) {
            if (input == null) throw new IllegalStateException("Twin dagger template missing: " + path);
            return JsonParser.parseReader(new InputStreamReader(input, StandardCharsets.UTF_8)).getAsJsonObject();
        } catch (IOException ex) { throw new UncheckedIOException(ex); }
    }
}
