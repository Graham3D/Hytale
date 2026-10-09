package com.inigmasgames.hytalerpg.gear;

import com.google.gson.*;
import com.hypixel.hytale.server.core.asset.type.item.config.Item;
import com.hypixel.hytale.server.core.asset.type.itemanimation.config.ItemPlayerAnimations;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.Interaction;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.RootInteraction;
import com.hypixel.hytale.server.core.modules.projectile.config.ProjectileConfig;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Exact, on-demand primary variants from pinned authored graphs. Only the selected action
 * gets a new root; item metadata, durability and custody remain on the original stack. */
final class NativePrimaryActionAssets {
    private static final String PREFIX = "/rpg/gear/action-templates/native-primary/";
    private static final int MAX_ITEMS = 4096;
    private static final Set<String> published = new HashSet<>();
    private NativePrimaryActionAssets() {}

    static String family(NativeGearActionProfiles.Profile profile) {
        String base = profile.nativeBaseId();
        return NativeGearActionProfiles.family(base, profile.rootId().startsWith("RPG_Action_Twin_"));
    }
    static String variantId(String carrier, NativeGearActionProfiles.Profile profile) {
        if (!profile.rootId().endsWith("_Root")) throw new IllegalArgumentException("Invalid action root identity");
        String stem = profile.rootId().substring(0, profile.rootId().length() - 5);
        return carrier + "__A" + stem.substring("RPG_Action_".length());
    }
    static boolean variantOf(String id, String carrier) {
        return id.startsWith(carrier + "__A") && id.matches(java.util.regex.Pattern.quote(carrier)
                + "__A(?:Battleaxe|Mace|Daggers|Longsword|Shortbow|Crossbow|Staff|Wand|Book|Bomb|Twin)_S1[0-9]{3}_R(?:00|1[2-9]|2[0-9]|3[0-9]|4[0-9]|50)");
    }
    static synchronized String publish(String carrier, NativeGearActionProfiles.Profile profile) {
        String family = family(profile), id = variantId(carrier, profile);
        String stem = profile.rootId().substring(0, profile.rootId().length() - 5);
        JsonObject manifest = read(PREFIX + "manifest.json").getAsJsonObject(family);
        if (manifest == null) throw new IllegalArgumentException("Unreviewed native action family " + family);
        if (complete(id, carrier, profile, family, stem, manifest)) return id;
        published.removeIf(key -> Item.getAssetMap().getAsset(key) == null);
        if (Item.getAssetMap().getAsset(id) == null) requireCapacity(published.size());
        Map<String,String> renamed = new HashMap<>();
        for (JsonElement name : manifest.getAsJsonArray("nodes")) {
            String original = name.getAsString();
            renamed.put(original, stem + "_" + original);
        }
        String animationId = stem + "_Animations";
        if (ItemPlayerAnimations.getAssetMap().getAsset(animationId) == null)
            NativeSwordActionAssets.load(ItemPlayerAnimations.getAssetStore(),
                    Map.of(animationId, renderRuntimeAnimations(carrier, family, manifest, profile.frozenRatePercent())));
        if (Set.of("staff", "wand", "book", "bomb").contains(family)) {
            String damageId = stem + "_Projectile_Damage";
            if (Interaction.getAssetMap().getAsset(damageId) == null)
                NativeSwordActionAssets.load(Interaction.getAssetStore(), Map.of(damageId,
                        renderProjectileDamage(family)));
            if (family.equals("bomb") && Interaction.getAssetMap().getAsset(stem + "_Impact") == null)
                NativeSwordActionAssets.load(Interaction.getAssetStore(), Map.of(stem + "_Impact",
                        renderBombImpact(stem)));
            if (ProjectileConfig.getAssetMap().getAsset(stem + "_Projectile") == null)
                NativeSwordActionAssets.load(ProjectileConfig.getAssetStore(), Map.of(stem + "_Projectile",
                        renderProjectileConfig(family, stem)));
        }
        Map<String,String> nodes = new LinkedHashMap<>();
        for (var entry : renamed.entrySet()) if (Interaction.getAssetMap().getAsset(entry.getValue()) == null)
            nodes.put(entry.getValue(), renderNode(family, entry.getKey(), renamed, manifest,
                    profile.frozenRatePercent(), profile.reachMetres(), animationId));
        if (!nodes.isEmpty()) NativeSwordActionAssets.load(Interaction.getAssetStore(), nodes);
        var roots = new LinkedHashMap<String,String>();
        if (RootInteraction.getAssetMap().getAsset(profile.rootId()) == null)
            roots.put(profile.rootId(), renderRoot(family, renamed, profile.frozenRatePercent()));
        if (manifest.has("launchRoot") && !manifest.get("launchRoot").isJsonNull()) {
            String launch = stem + "_Launch_Root";
            if (RootInteraction.getAssetMap().getAsset(launch) == null)
                roots.put(launch, renderLaunchRoot(family, renamed, profile.frozenRatePercent()));
        }
        JsonObject sourceVars = read("/Server/Item/Items/RPG/Gear/" + carrier + ".json")
                .getAsJsonObject("InteractionVars");
        if (sourceVars != null) for (var variable : sourceVars.entrySet()) {
            if (!variable.getValue().isJsonPrimitive()) continue;
            String node = renamed.get(variable.getValue().getAsString());
            if (node != null && !variable.getValue().getAsString().startsWith("RPG_Carrier_")
                    && RootInteraction.getAssetMap().getAsset(node) == null)
                roots.put(node, "{\"Interactions\":[\"" + node + "\"]}");
        }
        if (!roots.isEmpty()) NativeSwordActionAssets.load(RootInteraction.getAssetStore(), roots);
        profile.requireLoadedRoot();
        if (manifest.has("launchRoot") && !manifest.get("launchRoot").isJsonNull()
                && RootInteraction.getAssetMap().getAsset(stem + "_Launch_Root") == null)
            throw new IllegalStateException("Native focus launch root not loaded: " + stem);
        NativeSwordActionAssets.load(Item.getAssetStore(), Map.of(id, renderCarrier(carrier, family, manifest,
                profile.rootId(), renamed, profile.frozenRatePercent(), profile.reachMetres(), animationId)));
        if (Item.getAssetMap().getAsset(id) == null) throw new IllegalStateException("Native action carrier not loaded: " + id);
        published.add(id);
        return id;
    }
    static void requireCapacity(int liveItems) {
        if (liveItems >= MAX_ITEMS) throw new IllegalStateException("Native action variant capacity exceeded");
    }
    private static boolean complete(String id, String carrier, NativeGearActionProfiles.Profile profile,
                                    String family, String stem, JsonObject manifest) {
        if (Item.getAssetMap().getAsset(id) == null ||
                RootInteraction.getAssetMap().getAsset(profile.rootId()) == null ||
                ItemPlayerAnimations.getAssetMap().getAsset(profile.animationsId()) == null) return false;
        for (JsonElement name : manifest.getAsJsonArray("nodes"))
            if (Interaction.getAssetMap().getAsset(stem + "_" + name.getAsString()) == null) return false;
        if (Set.of("staff", "wand", "book", "bomb").contains(family) &&
                (ProjectileConfig.getAssetMap().getAsset(stem + "_Projectile") == null ||
                 Interaction.getAssetMap().getAsset(stem + "_Projectile_Damage") == null)) return false;
        if (family.equals("bomb") && Interaction.getAssetMap().getAsset(stem + "_Impact") == null)
            return false;
        if (Set.of("staff", "wand", "book").contains(family) &&
                RootInteraction.getAssetMap().getAsset(stem + "_Launch_Root") == null) return false;
        JsonObject sourceVars = read("/Server/Item/Items/RPG/Gear/" + carrier + ".json")
                .getAsJsonObject("InteractionVars");
        if (sourceVars != null) for (var variable : sourceVars.entrySet())
            if (variable.getValue().isJsonPrimitive() &&
                    !variable.getValue().getAsString().startsWith("RPG_Carrier_") &&
                    manifest.getAsJsonArray("nodes").asList().stream()
                    .anyMatch(node -> node.getAsString().equals(variable.getValue().getAsString())) &&
                    RootInteraction.getAssetMap().getAsset(stem + "_" + variable.getValue().getAsString()) == null)
                return false;
        return true;
    }
    static String renderNode(String family, String original, Map<String,String> renamed, JsonObject manifest,
                             double rate, double reach, String animationId) {
        JsonObject node = read(PREFIX + family + "/" + original + ".json");
        // Dynamic buffers are decoded independently. Native pack inheritance does not
        // supply the codec discriminator before the public store selects a codec.
        if (!node.has("Type") && node.has("Parent"))
            node.addProperty("Type", "DamageEntity");
        transform(node, renamed, manifest, 1 + rate / 100, reach, animationId, false);
        return node.toString();
    }
    static String renderRoot(String family, Map<String,String> renamed, double rate) {
        JsonObject root = read(PREFIX + family + "/root.json");
        transform(root, renamed, null, 1 + rate / 100, 0, null, true);
        return root.toString();
    }
    static String renderLaunchRoot(String family, Map<String,String> renamed, double rate) {
        JsonObject root = read(PREFIX + family + "/launch-root.json");
        transform(root, renamed, null, 1 + rate / 100, 0, null, true);
        return root.toString();
    }
    static String renderAnimations(String family, JsonObject manifest, double rate) {
        JsonObject animations = read(PREFIX + family + "/animations.json");
        for (JsonElement name : manifest.getAsJsonArray("attackAnimations")) {
            JsonObject animation = animations.getAsJsonObject("Animations").getAsJsonObject(name.getAsString());
            if (animation == null) throw new IllegalStateException("Missing pinned attack animation " + name);
            double speed = animation.has("Speed") ? animation.get("Speed").getAsDouble() : 1;
            animation.addProperty("Speed", round(speed * (1 + rate / 100)));
        }
        return animations.toString();
    }
    private static String renderRuntimeAnimations(String carrier, String family, JsonObject manifest, double rate) {
        // Each native carrier names its own animation profile. Clone that exact decoded
        // profile so unrelated cues and camera/pullback/wiggle settings survive intact.
        String installedId = read("/Server/Item/Items/RPG/Gear/" + carrier + ".json")
                .get("PlayerAnimationsId").getAsString();
        var installed = ItemPlayerAnimations.getAssetMap().getAsset(installedId);
        if (installed == null) throw new IllegalStateException("Native animation profile not loaded: " + installedId);
        JsonObject animations = JsonParser.parseString(ItemPlayerAnimations.CODEC.encode(installed,
                new com.hypixel.hytale.codec.ExtraInfo()).asDocument().toJson()).getAsJsonObject();
        JsonObject pinned = read(PREFIX + family + "/animations.json").getAsJsonObject("Animations");
        for (JsonElement name : manifest.getAsJsonArray("attackAnimations")) {
            JsonObject animation = animations.getAsJsonObject("Animations").getAsJsonObject(name.getAsString());
            if (animation == null) {
                animation = pinned.getAsJsonObject(name.getAsString());
                if (animation == null) throw new IllegalStateException("Missing pinned attack animation "
                        + installedId + "/" + name);
                animation = animation.deepCopy();
                animations.getAsJsonObject("Animations").add(name.getAsString(), animation);
            }
            double speed = animation.has("Speed") ? animation.get("Speed").getAsDouble() : 1;
            animation.addProperty("Speed", round(speed * (1 + rate / 100)));
        }
        return animations.toString();
    }
    static String renderProjectileDamage(String family) {
        String source = family.equals("bomb") ? "RPG_Carrier_Bomb_Damage" : "RPG_Carrier_Focus_Projectile_Damage";
        JsonObject leaf = read("/Server/Item/Interactions/RPG/Carriers/" + source + ".json");
        leaf.remove(NativeGearAttackAcceptance.PROC_SELECTOR);
        leaf.remove(NativeGearAttackAcceptance.PROC_COEFFICIENT);
        NativeActionProcMetadata.mark(leaf, family.equals("bomb") ? "Bomb_Impact_Damage" : "Focus_Projectile_Damage");
        return leaf.toString();
    }
    static String renderBombImpact(String stem) {
        JsonObject impact = read("/Server/Item/Interactions/RPG/Carriers/RPG_Carrier_Bomb_Impact.json");
        return impact.toString().replace("\"RPG_Carrier_Bomb_Damage\"",
                "\"" + stem + "_Projectile_Damage\"");
    }
    static String renderProjectileConfig(String family, String stem) {
        String original = "RPG_Carrier_" + Character.toUpperCase(family.charAt(0)) + family.substring(1) + "_Projectile";
        JsonObject config = read("/Server/ProjectileConfigs/RPG/Carriers/" + original + ".json");
        String source = config.toString();
        if (family.equals("bomb")) source = source.replace("\"RPG_Carrier_Bomb_Impact\"", "\"" + stem + "_Impact\"");
        else source = source.replace("\"RPG_Carrier_Focus_Projectile_Damage\"",
                "\"" + stem + "_Projectile_Damage\"");
        return source;
    }
    static String renderCarrier(String carrier, String family, JsonObject manifest, String rootId) {
        return renderCarrier(carrier, family, manifest, rootId, Map.of(), 0, 0, null);
    }
    static String renderCarrier(String carrier, String family, JsonObject manifest, String rootId,
                                Map<String,String> renamed, double rate, double reach, String animationId) {
        JsonObject item = read("/Server/Item/Items/RPG/Gear/" + carrier + ".json");
        String original = item.getAsJsonObject("Interactions").get("Primary").getAsString();
        String expected = family.equals("twin") ? "Root_Weapon_Daggers_Primary" : manifest.get("root").getAsString();
        if (!original.equals(expected))
            throw new IllegalArgumentException("Unreviewed primary root " + original + " on " + carrier);
        item.getAsJsonObject("Interactions").addProperty("Primary", rootId);
        if (!renamed.isEmpty() && item.has("InteractionVars")) {
            JsonObject vars = item.getAsJsonObject("InteractionVars");
            if (manifest.has("launchRoot") && !manifest.get("launchRoot").isJsonNull()) {
                String launch = manifest.get("launchRoot").getAsString();
                String changed = rootId.substring(0, rootId.length() - 5) + "_Launch_Root";
                for (var entry : new ArrayList<>(vars.entrySet()))
                    if (entry.getValue().isJsonPrimitive() && launch.equals(entry.getValue().getAsString()))
                        vars.addProperty(entry.getKey(), changed);
            }
            var queue = new ArrayDeque<String>();
            for (JsonElement variable : manifest.getAsJsonArray("actionVariables")) queue.add(variable.getAsString());
            for (JsonElement node : manifest.getAsJsonArray("nodes"))
                collectVars(read(PREFIX + family + "/" + node.getAsString() + ".json"), queue);
            var visited = new HashSet<String>();
            while (!queue.isEmpty()) {
                String key = queue.removeFirst();
                if (family.equals("crossbow") && key.startsWith("Reload_")) continue;
                if (!visited.add(key) || !vars.has(key)) continue;
                JsonElement replacement = vars.get(key);
                if (replacement.isJsonPrimitive() && replacement.getAsString().equals("RPG_Carrier_Focus_Melee_Damage")) {
                    JsonObject leaf = new JsonObject();
                    leaf.addProperty("Parent", replacement.getAsString());
                    leaf.addProperty("Type", ManagedCarrierDamageInteraction.TYPE);
                    JsonArray interactions = new JsonArray(); interactions.add(leaf);
                    JsonObject wrapper = new JsonObject(); wrapper.add("Interactions", interactions);
                    vars.add(key, wrapper); replacement = wrapper;
                } else if (replacement.isJsonPrimitive() && renamed.containsKey(replacement.getAsString())) {
                    vars.addProperty(key, renamed.get(replacement.getAsString()));
                    replacement = vars.get(key);
                }
                collectVars(replacement, queue);
                transform(replacement, renamed, manifest, 1 + rate / 100, reach, animationId, false);
                NativeActionProcMetadata.mark(replacement, key, procShare(family, key));
            }
        }
        if (family.equals("twin")) {
            JsonObject utility = item.has("Utility") ? item.getAsJsonObject("Utility") : new JsonObject();
            utility.addProperty("Compatible", true);
            utility.addProperty("Usable", false);
            item.add("Utility", utility);
            item.getAsJsonObject("Weapon").addProperty("RenderDualWielded", true);
        }
        return item.toString();
    }
    private static void collectVars(JsonElement tree, ArrayDeque<String> found) {
        if (tree.isJsonArray()) { for (JsonElement child : tree.getAsJsonArray()) collectVars(child, found); return; }
        if (!tree.isJsonObject()) return;
        JsonObject object = tree.getAsJsonObject();
        if (object.has("Var") && object.get("Var").isJsonPrimitive()) found.add(object.get("Var").getAsString());
        if (object.has("Config") && object.get("Config").isJsonPrimitive()) {
            String id = object.get("Config").getAsString();
            if (id.startsWith("RPG_GearRoute_C_") || id.startsWith("RPG_Carrier_")) {
                JsonObject config = maybeRead("/Server/ProjectileConfigs/RPG/Gear/" + id + ".json");
                if (config == null) config = maybeRead("/Server/ProjectileConfigs/RPG/Carriers/" + id + ".json");
                if (config == null) throw new IllegalStateException("Missing managed native projectile config " + id);
                collectVars(config, found);
            }
        }
        for (var entry : object.entrySet()) collectVars(entry.getValue(), found);
    }
    private static double procShare(String family, String variable) {
        if (!family.equals("shortbow") || !variable.equals("Signature_Volley_Damage")) return 1;
        int carriers = -1;
        for (int strength = 0; strength <= 2; strength++) {
            JsonObject stage = read(PREFIX + family + "/RPG_GearRoute_I_Weapon_Shortbow_Signature_Volley_Strength_"
                    + strength + ".json");
            int current = countProjectileLaunches(stage);
            if (carriers < 0) carriers = current;
            else if (carriers != current) throw new IllegalStateException("Volley carrier count changed by strength");
        }
        if (carriers != 3) throw new IllegalStateException("Unreviewed native Volley carrier count " + carriers);
        return 1.0 / carriers;
    }
    private static int countProjectileLaunches(JsonElement tree) {
        if (tree.isJsonArray()) {
            int count = 0; for (JsonElement child : tree.getAsJsonArray()) count += countProjectileLaunches(child);
            return count;
        }
        if (!tree.isJsonObject()) return 0;
        JsonObject object = tree.getAsJsonObject();
        int count = object.has("Type") && object.get("Type").getAsString().equals("RPG_GearProjectile") ? 1 : 0;
        for (var child : object.entrySet()) count += countProjectileLaunches(child.getValue());
        return count;
    }
    private static void transform(JsonElement element, Map<String,String> renamed, JsonObject manifest,
                                  double factor, double reach, String animationId, boolean root) {
        if (element.isJsonArray()) {
            JsonArray array = element.getAsJsonArray();
            for (int i = 0; i < array.size(); i++) {
                JsonElement value = array.get(i);
                if (value.isJsonPrimitive() && value.getAsJsonPrimitive().isString()
                        && renamed.containsKey(value.getAsString())) array.set(i, new JsonPrimitive(renamed.get(value.getAsString())));
                else transform(value, renamed, manifest, factor, reach, animationId, false);
            }
            return;
        }
        if (!element.isJsonObject()) return;
        JsonObject object = element.getAsJsonObject();
        for (String field : List.of("RunTime", "ChainingAllowance", "ClickQueuingTimeout"))
            if (object.has(field) && object.get(field).isJsonPrimitive() && object.get(field).getAsJsonPrimitive().isNumber())
                object.addProperty(field, round(object.get(field).getAsDouble() / factor));
        if (object.has("Cooldown") && object.get("Cooldown").isJsonObject()) {
            JsonObject cooldown = object.getAsJsonObject("Cooldown");
            if (cooldown.has("Cooldown")) cooldown.addProperty("Cooldown", round(cooldown.get("Cooldown").getAsDouble() / factor));
        }
        if (animationId != null && object.has("Config") && object.get("Config").isJsonPrimitive()) {
            String config = object.get("Config").getAsString();
            if (config.matches("RPG_Carrier_(?:Staff|Wand|Book|Bomb)_Projectile"))
                object.addProperty("Config", animationId.substring(0, animationId.length() - "_Animations".length()) + "_Projectile");
        }
        if (object.has("Selector") && reach > 0) {
            JsonObject selector = object.getAsJsonObject("Selector");
            if (selector.has("EndDistance")) {
                String shape = selector.get("Id").getAsString();
                if (!Set.of("Horizontal", "Stab").contains(shape) || !object.has("HitBlock"))
                    throw new IllegalStateException("Unaudited native melee selector: " + shape);
                selector.addProperty("TestLineOfSight", true);
                selector.addProperty("EndDistance", round(selector.get("EndDistance").getAsDouble() + reach));
            }
        }
        if (manifest != null && animationId != null && object.has("Effects")) {
            JsonObject effects = object.getAsJsonObject("Effects");
            if (reach > 0 && effects.has("Trails")) for (JsonElement trail : effects.getAsJsonArray("Trails")) {
                JsonObject offset = trail.getAsJsonObject().getAsJsonObject("PositionOffset");
                if (offset != null && offset.has("X")) offset.addProperty("X", round(offset.get("X").getAsDouble() + reach));
            }
            if (reach > 0) for (String field : List.of("Particles", "FirstPersonParticles"))
                if (effects.has(field)) for (JsonElement particle : effects.getAsJsonArray(field)) {
                    JsonObject visual = particle.getAsJsonObject();
                    if (visual.has("SystemId") && visual.get("SystemId").getAsString().contains("Trail")) {
                        JsonObject offset = visual.getAsJsonObject("PositionOffset");
                        if (offset != null && offset.has("Z")) offset.addProperty("Z", round(offset.get("Z").getAsDouble() + reach));
                    }
                }
            if (effects.has("ItemAnimationId")) {
                String name = effects.get("ItemAnimationId").getAsString();
                for (JsonElement eligible : manifest.getAsJsonArray("attackAnimations")) if (eligible.getAsString().equals(name)) {
                    effects.addProperty("ItemPlayerAnimationsId", animationId); break;
                }
            }
        }
        if (object.has("Next") && object.get("Next").isJsonObject()) {
            JsonObject next = object.getAsJsonObject("Next");
            if (!next.entrySet().isEmpty() && next.entrySet().stream().allMatch(e -> e.getKey().matches("[0-9]+(?:\\.[0-9]+)?"))) {
                JsonObject scaled = new JsonObject();
                for (var entry : next.entrySet()) {
                    String key = Double.toString(round(Double.parseDouble(entry.getKey()) / factor));
                    if (scaled.has(key)) throw new IllegalStateException("Native timing checkpoint collision " + key);
                    scaled.add(key, entry.getValue());
                }
                object.add("Next", scaled);
            }
        }
        for (var entry : new ArrayList<>(object.entrySet())) {
            JsonElement value = entry.getValue();
            if ((entry.getKey().equals("Next") || entry.getKey().equals("Interactions") || entry.getKey().equals("Failed"))
                    && value.isJsonPrimitive() && value.getAsJsonPrimitive().isString()
                    && renamed.containsKey(value.getAsString())) object.addProperty(entry.getKey(), renamed.get(value.getAsString()));
            else transform(value, renamed, manifest, factor, reach, animationId, false);
        }
    }
    private static double round(double value) { return Math.round(value * 1_000_000d) / 1_000_000d; }
    private static JsonObject read(String resource) {
        try (InputStream input = NativePrimaryActionAssets.class.getResourceAsStream(resource)) {
            if (input == null) throw new IllegalStateException("Missing pinned native action template " + resource);
            return JsonParser.parseReader(new InputStreamReader(input, StandardCharsets.UTF_8)).getAsJsonObject();
        } catch (IOException ex) { throw new UncheckedIOException(ex); }
    }
    private static JsonObject maybeRead(String resource) {
        try (InputStream input = NativePrimaryActionAssets.class.getResourceAsStream(resource)) {
            return input == null ? null : JsonParser.parseReader(new InputStreamReader(input, StandardCharsets.UTF_8)).getAsJsonObject();
        } catch (IOException ex) { throw new UncheckedIOException(ex); }
    }
}
