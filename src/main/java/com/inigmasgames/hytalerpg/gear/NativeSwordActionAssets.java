package com.inigmasgames.hytalerpg.gear;

import com.google.gson.*;
import com.hypixel.hytale.assetstore.AssetLoadResult;
import com.hypixel.hytale.assetstore.AssetUpdateQuery;
import com.hypixel.hytale.assetstore.RawAsset;
import com.hypixel.hytale.assetstore.codec.ContainedAssetCodec;
import com.hypixel.hytale.server.core.asset.type.item.config.Item;
import com.hypixel.hytale.server.core.asset.type.itemanimation.config.ItemPlayerAnimations;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.Interaction;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.RootInteraction;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.*;

/** Publishes only action profiles actually requested by frozen swords. HytaleAssetStore
 * broadcasts loaded assets to current clients and includes them in later initial sync. */
final class NativeSwordActionAssets {
    private static final String SAMPLE = "RPG_Action_Sword_S115_R40";
    private static final double SAMPLE_FACTOR = 1.15;
    private static final String PACK = "HyArpg";
    private static final String[] NODES = {"", "_Chain", "_Swing_Left", "_Swing_Left_Damage",
            "_Swing_Left_Selector", "_Swing_Right", "_Swing_Right_Damage", "_Swing_Right_Selector",
            "_Swing_Down", "_Swing_Down_Damage", "_Swing_Down_Selector", "_Thrust",
            "_Thrust_Damage", "_Thrust_Force", "_Thrust_Selector", "_Thrust_StaminaCondition"};
    private static final String ACTION_PREFIX = "_Weapon_Sword_Primary";
    private static final int MAX_VARIANTS = 4096;
    private static final Set<String> publishedItems = new HashSet<>();
    private NativeSwordActionAssets() {}

    static String variantId(String carrier, NativeGearActionProfiles.Profile profile) {
        return carrier + "__" + profile.rootId().replace("RPG_Action_Sword_", "").replace("_Root", "");
    }

    static boolean variantOf(String stackId, String carrier) {
        return stackId.startsWith(carrier + "__S") && stackId.matches(
                java.util.regex.Pattern.quote(carrier) + "__S(?:115|1[0-9]{3})_R(?:00|1[2-9]|2[0-9]|3[0-9]|4[0-9]|50)");
    }

    static synchronized String publish(String carrier, NativeGearActionProfiles.Profile profile) {
        String variant = variantId(carrier, profile);
        String stem = profile.rootId().substring(0, profile.rootId().length() - "_Root".length());
        if (Item.getAssetMap().getAsset(variant) != null &&
                RootInteraction.getAssetMap().getAsset(profile.rootId()) != null &&
                ItemPlayerAnimations.getAssetMap().getAsset(profile.animationsId()) != null &&
                Arrays.stream(NODES).allMatch(node -> Interaction.getAssetMap()
                        .getAsset(stem + ACTION_PREFIX + node) != null)) return variant;
        publishedItems.removeIf(id -> Item.getAssetMap().getAsset(id) == null);
        if (publishedItems.size() >= MAX_VARIANTS) throw new IllegalStateException("Gear action variant capacity exceeded");
        // Load dependencies in the order needed by the native codecs and their packet indices.
        if (ItemPlayerAnimations.getAssetMap().getAsset(profile.animationsId()) == null) {
            load(ItemPlayerAnimations.getAssetStore(), Map.of(profile.animationsId(),
                    renderRuntimeAnimation(carrier, stem, profile.frozenRatePercent())));
        }
        {
            var interactions = new LinkedHashMap<String,String>();
            for (String node : NODES) if (Interaction.getAssetMap().getAsset(stem + ACTION_PREFIX + node) == null)
                interactions.put(stem + ACTION_PREFIX + node,
                        renderNode(stem, node, profile.frozenRatePercent(), profile.reachMetres()));
            if (!interactions.isEmpty())
            load(Interaction.getAssetStore(), interactions);
        }
        if (RootInteraction.getAssetMap().getAsset(profile.rootId()) == null)
            load(RootInteraction.getAssetStore(), Map.of(profile.rootId(), renderRoot(stem, profile.frozenRatePercent())));
        profile.requireLoadedRoot();
        load(Item.getAssetStore(), Map.of(variant, renderCarrier(carrier, profile.rootId())));
        if (Item.getAssetMap().getAsset(variant) == null) throw new IllegalStateException("Gear action carrier not loaded: " + variant);
        publishedItems.add(variant);
        return variant;
    }

    static <T extends com.hypixel.hytale.assetstore.map.JsonAssetWithMap<String, M>,
            M extends com.hypixel.hytale.assetstore.AssetMap<String,T>> void load(
            com.hypixel.hytale.assetstore.AssetStore<String,T,M> store, Map<String,String> json) {
        var raw = new ArrayList<RawAsset<String>>();
        for (var entry : json.entrySet()) raw.add(new RawAsset<>(Path.of("Server", "RPG", entry.getKey() + ".json"),
                entry.getKey(), null, 0, entry.getValue().toCharArray(), null, ContainedAssetCodec.Mode.NONE));
        AssetLoadResult<String,T> result = store.loadBuffersWithKeys(PACK, raw, AssetUpdateQuery.DEFAULT, true);
        if (result.hasFailed() || result.getLoadedAssets().size() != json.size())
            throw new IllegalStateException("Gear action asset publication failed: " + result.getFailedToLoadKeys());
    }

    static String renderNode(String stem, String node, double rate, double reach) {
        JsonObject root = template("/Server/Item/Interactions/RPG/ActionProfiles/" + SAMPLE + ACTION_PREFIX + node + ".json");
        // Public dynamic buffers choose a codec before resolving the inherited asset.
        if (!root.has("Type") && root.has("Parent")) root.addProperty("Type", "DamageEntity");
        double factor = 1 + rate / 100;
        scaleTimeline(root, SAMPLE_FACTOR / factor);
        if (root.has("Selector")) {
            var selector = root.getAsJsonObject("Selector");
            selector.addProperty("EndDistance", round(selector.get("EndDistance").getAsDouble() + reach - .4));
            if (root.has("Effects") && root.getAsJsonObject("Effects").has("Trails"))
                for (var trail : root.getAsJsonObject("Effects").getAsJsonArray("Trails")) {
                    var offset = trail.getAsJsonObject().getAsJsonObject("PositionOffset");
                    if (offset != null && offset.has("X")) offset.addProperty("X", round(offset.get("X").getAsDouble() + reach - .4));
                }
        }
        return root.toString().replace(SAMPLE, stem);
    }

    static String renderRoot(String stem, double rate) {
        var root = template("/Server/Item/RootInteractions/RPG/ActionProfiles/" + SAMPLE + "_Root.json");
        double ratio = SAMPLE_FACTOR / (1 + rate / 100);
        root.addProperty("ClickQueuingTimeout", round(root.get("ClickQueuingTimeout").getAsDouble() * ratio));
        var cooldown = root.getAsJsonObject("Cooldown");
        cooldown.addProperty("Cooldown", round(cooldown.get("Cooldown").getAsDouble() * ratio));
        return root.toString().replace(SAMPLE, stem);
    }

    static String renderAnimation(String stem, double rate) {
        var root = template("/Server/Item/Animations/" + SAMPLE + "_Animations.json");
        for (var entry : root.getAsJsonObject("Animations").entrySet()) {
            var animation = entry.getValue().getAsJsonObject();
            animation.addProperty("Speed", round(animation.get("Speed").getAsDouble() * (1 + rate / 100) / SAMPLE_FACTOR));
        }
        return root.toString();
    }

    private static String renderRuntimeAnimation(String carrier,String stem,double rate) {
        String nativeId=template("/Server/Item/Items/RPG/Gear/"+carrier+".json").get("PlayerAnimationsId").getAsString();
        var installed=ItemPlayerAnimations.getAssetMap().getAsset(nativeId);
        if(installed==null)throw new IllegalStateException("Native sword animation profile not loaded: "+nativeId);
        var inherited=JsonParser.parseString(ItemPlayerAnimations.CODEC.encode(installed,
                new com.hypixel.hytale.codec.ExtraInfo()).asDocument().toJson()).getAsJsonObject();
        var overrides=JsonParser.parseString(renderAnimation(stem,rate)).getAsJsonObject().getAsJsonObject("Animations");
        for(var entry:overrides.entrySet())inherited.getAsJsonObject("Animations").add(entry.getKey(),entry.getValue());
        return inherited.toString();
    }

    static String renderCarrier(String carrier, String rootId) {
        var root = template("/Server/Item/Items/RPG/Gear/" + carrier + ".json");
        if (!"Root_Weapon_Sword_Primary".equals(root.getAsJsonObject("Interactions").get("Primary").getAsString()))
            throw new IllegalArgumentException("Unreviewed sword primary carrier: " + carrier);
        root.getAsJsonObject("Interactions").addProperty("Primary", rootId);
        var vars = root.getAsJsonObject("InteractionVars");
        for (String strike : List.of("Swing_Left_Damage", "Swing_Right_Damage", "Swing_Down_Damage", "Thrust_Damage")) {
            if (!vars.has(strike)) throw new IllegalStateException("Missing audited sword strike " + strike);
            NativeActionProcMetadata.mark(vars.get(strike), strike);
        }
        return root.toString();
    }

    private static void scaleTimeline(JsonElement element, double ratio) {
        if (element.isJsonArray()) { for (var child : element.getAsJsonArray()) scaleTimeline(child, ratio); return; }
        if (!element.isJsonObject()) return;
        var object = element.getAsJsonObject();
        if (object.has("RunTime")) object.addProperty("RunTime", round(object.get("RunTime").getAsDouble() * ratio));
        if (object.has("ChainingAllowance")) object.addProperty("ChainingAllowance",
                round(object.get("ChainingAllowance").getAsDouble() * ratio));
        if (object.has("Next") && object.get("Next").isJsonObject()) {
            var next = object.getAsJsonObject("Next");
            boolean times = !next.entrySet().isEmpty() && next.entrySet().stream().allMatch(e -> e.getKey().matches("[0-9]+(?:\\.[0-9]+)?"));
            if (times) {
                var scaled = new JsonObject();
                for (var entry : next.entrySet()) scaled.add(Double.toString(round(Double.parseDouble(entry.getKey()) * ratio)), entry.getValue());
                object.add("Next", scaled);
            }
        }
        for (var child : object.entrySet()) scaleTimeline(child.getValue(), ratio);
    }

    private static double round(double value) { return Math.round(value * 1_000_000d) / 1_000_000d; }
    private static JsonObject template(String resource) {
        try (InputStream input = NativeSwordActionAssets.class.getResourceAsStream(resource)) {
            if (input == null) throw new IllegalStateException("Gear action template missing: " + resource);
            return JsonParser.parseReader(new InputStreamReader(input, StandardCharsets.UTF_8)).getAsJsonObject();
        } catch (IOException ex) { throw new UncheckedIOException(ex); }
    }
}
