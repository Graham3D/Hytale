package com.inigmasgames.hytalerpg.gear;

import com.google.gson.*;
import org.junit.jupiter.api.Test;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** Exercises the production on-demand renderer against every pinned native primary graph. */
class NativePrimaryActionAssetsTest {
    private static final String PREFIX = "/rpg/gear/action-templates/native-primary/";
    private static JsonObject resource(String name) {
        try (var stream = NativePrimaryActionAssetsTest.class.getResourceAsStream(name)) {
            assertNotNull(stream, name);
            return JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
        } catch (Exception failure) { throw new AssertionError(name, failure); }
    }
    private static Map<String,String> renamed(JsonObject spec, String stem) {
        var result = new HashMap<String,String>();
        for (JsonElement name : spec.getAsJsonArray("nodes"))
            result.put(name.getAsString(), stem + "_" + name.getAsString());
        return result;
    }
    private static void compareSelectors(JsonElement before, JsonElement after, double reach, List<String> found) {
        if (before.isJsonArray()) {
            for (int i = 0; i < before.getAsJsonArray().size(); i++)
                compareSelectors(before.getAsJsonArray().get(i), after.getAsJsonArray().get(i), reach, found);
            return;
        }
        if (!before.isJsonObject()) return;
        JsonObject source = before.getAsJsonObject(), target = after.getAsJsonObject();
        if (source.has("Selector") && source.get("Selector").isJsonObject()) {
            JsonObject old = source.getAsJsonObject("Selector"), actual = target.getAsJsonObject("Selector");
            if (old.has("EndDistance")) {
                found.add(old.get("Id").getAsString());
                assertEquals(old.get("EndDistance").getAsDouble() + reach,
                        actual.get("EndDistance").getAsDouble(), 1e-6);
                if (reach > 0) {
                    assertTrue(actual.get("TestLineOfSight").getAsBoolean());
                    assertTrue(target.has("HitBlock"));
                    assertEquals(old.get("Id"), actual.get("Id"));
                    for (String width : List.of("Length", "ExtendTop", "ExtendBottom", "ExtendLeft", "ExtendRight"))
                        if (old.has(width)) assertEquals(old.get(width), actual.get(width));
                }
            }
        }
        for (var entry : source.entrySet()) if (target.has(entry.getKey()))
            compareSelectors(entry.getValue(), target.get(entry.getKey()), reach, found);
    }
    private static void compareTimeline(JsonElement before, JsonElement after, String label) {
        if (before.isJsonArray()) {
            assertEquals(before.getAsJsonArray().size(), after.getAsJsonArray().size(), label);
            for (int i = 0; i < before.getAsJsonArray().size(); i++)
                compareTimeline(before.getAsJsonArray().get(i), after.getAsJsonArray().get(i), label + "/" + i);
            return;
        }
        if (!before.isJsonObject()) return;
        JsonObject source = before.getAsJsonObject(), target = after.getAsJsonObject();
        for (String field : List.of("RunTime", "ChainingAllowance", "ClickQueuingTimeout"))
            if (source.has(field)) assertEquals(source.get(field).getAsDouble() / 1.15,
                    target.get(field).getAsDouble(), 1e-6, label + "/" + field);
        if (source.has("Cooldown") && source.get("Cooldown").isJsonObject()) {
            JsonObject old = source.getAsJsonObject("Cooldown"), changed = target.getAsJsonObject("Cooldown");
            if (old.has("Cooldown")) assertEquals(old.get("Cooldown").getAsDouble() / 1.15,
                    changed.get("Cooldown").getAsDouble(), 1e-6, label + "/Cooldown");
        }
        if (source.has("Next") && source.get("Next").isJsonObject()) {
            JsonObject old = source.getAsJsonObject("Next"), changed = target.getAsJsonObject("Next");
            if (!old.entrySet().isEmpty() && old.entrySet().stream().allMatch(e -> e.getKey().matches("[0-9]+(?:\\.[0-9]+)?"))) {
                assertEquals(old.size(), changed.size(), label + "/Next");
                var oldEntries = new ArrayList<>(old.entrySet());
                var newEntries = new ArrayList<>(changed.entrySet());
                for (int i = 0; i < oldEntries.size(); i++) {
                    assertEquals(Double.parseDouble(oldEntries.get(i).getKey()) / 1.15,
                            Double.parseDouble(newEntries.get(i).getKey()), 1e-6, label + "/Next/" + i);
                    compareTimeline(oldEntries.get(i).getValue(), newEntries.get(i).getValue(), label + "/Next/" + i);
                }
            } else compareTimeline(old, changed, label + "/Next");
        }
        else if (source.has("Next") && target.has("Next"))
            compareTimeline(source.get("Next"), target.get("Next"), label + "/Next");
        for (var entry : source.entrySet())
            if (!entry.getKey().equals("Next") && !entry.getKey().equals("Cooldown") && target.has(entry.getKey()))
                compareTimeline(entry.getValue(), target.get(entry.getKey()), label + "/" + entry.getKey());
    }
    @Test void everyAuthoredPrimaryFamilyRendersExactNativeTimelineAnimationsAndMeleeGeometry() {
        JsonObject manifest = resource(PREFIX + "manifest.json");
        var melee = Set.of("battleaxe", "mace", "daggers", "longsword", "twin");
        for (var entry : manifest.entrySet()) {
            String family = entry.getKey(), stem = "RPG_Action_Test_" + family;
            JsonObject spec = entry.getValue().getAsJsonObject();
            Map<String,String> names = renamed(spec, stem);
            double reach = melee.contains(family) ? .4 : 0;
            JsonObject baseRoot = resource(PREFIX + family + "/root.json");
            JsonObject root = JsonParser.parseString(NativePrimaryActionAssets.renderRoot(family, names, 15)).getAsJsonObject();
            compareTimeline(baseRoot, root, family + "/root");
            assertEquals(names.get(baseRoot.getAsJsonArray("Interactions").get(0).getAsString()),
                    root.getAsJsonArray("Interactions").get(0).getAsString(), family);
            if (baseRoot.has("Cooldown")) assertEquals(
                    baseRoot.getAsJsonObject("Cooldown").get("Cooldown").getAsDouble() / 1.15,
                    root.getAsJsonObject("Cooldown").get("Cooldown").getAsDouble(), 1e-6, family);
            if (spec.has("launchRoot") && !spec.get("launchRoot").isJsonNull()) {
                JsonObject launch = resource(PREFIX + family + "/launch-root.json");
                JsonObject changed = JsonParser.parseString(NativePrimaryActionAssets.renderLaunchRoot(family, names, 15)).getAsJsonObject();
                compareTimeline(launch, changed, family + "/launch-root");
                assertEquals(names.get(launch.getAsJsonArray("Interactions").get(0).getAsString()),
                        changed.getAsJsonArray("Interactions").get(0).getAsString());
            }
            JsonObject baseAnimations = resource(PREFIX + family + "/animations.json");
            JsonObject animations = JsonParser.parseString(NativePrimaryActionAssets.renderAnimations(family, spec, 15)).getAsJsonObject();
            assertEquals(baseAnimations.getAsJsonObject("Animations").get("Idle"),
                    animations.getAsJsonObject("Animations").get("Idle"), family + " idle control");
            for (JsonElement attackName : spec.getAsJsonArray("attackAnimations")) {
                String name = attackName.getAsString();
                JsonObject original = baseAnimations.getAsJsonObject("Animations").getAsJsonObject(name);
                JsonObject changed = animations.getAsJsonObject("Animations").getAsJsonObject(name);
                double oldSpeed = original.has("Speed") ? original.get("Speed").getAsDouble() : 1;
                assertEquals(oldSpeed * 1.15, changed.get("Speed").getAsDouble(), 1e-6, family + "/" + name);
                for (String camera : List.of("FirstPerson", "ThirdPerson"))
                    if (original.has(camera)) assertEquals(original.get(camera), changed.get(camera));
            }
            int selectors = 0;
            for (JsonElement name : spec.getAsJsonArray("nodes")) {
                String id = name.getAsString();
                JsonObject base = resource(PREFIX + family + "/" + id + ".json");
                JsonObject changed;
                try { changed = JsonParser.parseString(NativePrimaryActionAssets.renderNode(family, id, names,
                        spec, 15, reach, stem + "_Animations")).getAsJsonObject(); }
                catch (RuntimeException failure) { throw new AssertionError(family + "/" + id, failure); }
                var found = new ArrayList<String>();
                compareSelectors(base, changed, reach, found);
                compareTimeline(base, changed, family + "/" + id);
                selectors += found.size();
                assertEquals(base.has("HitEntity"), changed.has("HitEntity"), family + "/" + id);
            }
            if (melee.contains(family)) assertTrue(selectors > 0, family + " needs native contacts");
        }
    }
    @Test void carrierVariantChangesOnlyNativePrimaryAndTwinUtilityCapability() {
        JsonObject manifest = resource(PREFIX + "manifest.json");
        var examples = Map.ofEntries(Map.entry("battleaxe", "RPG_Gear_battleaxe_adamantite_h"),
                Map.entry("mace", "RPG_Gear_mace_adamantite_h"),
                Map.entry("daggers", "RPG_Gear_daggers_adamantite_h"),
                Map.entry("longsword", "RPG_Gear_longsword_adamantite_h"),
                Map.entry("shortbow", "RPG_Gear_shortbow_adamantite_h"),
                Map.entry("crossbow", "RPG_Gear_crossbow_heavy_h"),
                Map.entry("staff", "RPG_Gear_staff_apprentice_h"),
                Map.entry("wand", "RPG_Gear_wand_amber_h"),
                Map.entry("book", "RPG_Gear_book_apprentice_h"),
                Map.entry("bomb", "RPG_Gear_bomb_fire_h"),
                Map.entry("twin", "RPG_Gear_daggers_adamantite_h"));
        for (var entry : examples.entrySet()) {
            String family = entry.getKey(), carrier = entry.getValue(), root = "RPG_Action_Test_" + family + "_Root";
            JsonObject base = resource("/Server/Item/Items/RPG/Gear/" + carrier + ".json");
            JsonObject variant = JsonParser.parseString(NativePrimaryActionAssets.renderCarrier(carrier, family,
                    manifest.getAsJsonObject(family), root)).getAsJsonObject();
            assertEquals(root, variant.getAsJsonObject("Interactions").get("Primary").getAsString());
            assertEquals(base.get("InteractionVars"), variant.get("InteractionVars"), family);
            assertEquals(base.get("MaxDurability"), variant.get("MaxDurability"), family);
            assertEquals(base.get("Model"), variant.get("Model"), family);
            assertEquals(base.get("Texture"), variant.get("Texture"), family);
            if (family.equals("twin")) assertTrue(variant.getAsJsonObject("Utility").get("Compatible").getAsBoolean());
            else assertEquals(base.get("Utility"), variant.get("Utility"), family);
            assertEquals(base.getAsJsonObject("Interactions").get("Secondary"),
                    variant.getAsJsonObject("Interactions").get("Secondary"), family);
        }
        assertThrows(IllegalArgumentException.class, () -> NativePrimaryActionAssets.renderCarrier(
                "RPG_Gear_sword_adamantite_h", "mace", manifest.getAsJsonObject("mace"), "invalid"));
        String mapped = "RPG_Gear_battleaxe_adamantite_h__ABattleaxe_S1048_R12";
        assertTrue(NativePrimaryActionAssets.variantOf(mapped, "RPG_Gear_battleaxe_adamantite_h"));
        assertEquals(GearNativeItems.nativeId("RPG_Gear_battleaxe_adamantite_h"), GearNativeItems.nativeId(mapped));
        assertFalse(NativePrimaryActionAssets.variantOf("RPG_Gear_battleaxe_adamantite_h__ABattleaxe_S1048_R11",
                "RPG_Gear_battleaxe_adamantite_h"));
    }
    @Test void onlyReachableNativeReplaceVariablesReceiveAttackTiming() {
        JsonObject manifest = resource(PREFIX + "manifest.json").getAsJsonObject("shortbow");
        String carrier = "RPG_Gear_shortbow_adamantite_h", stem = "RPG_Action_Test_shortbow";
        JsonObject source = resource("/Server/Item/Items/RPG/Gear/" + carrier + ".json");
        JsonObject rendered = JsonParser.parseString(NativePrimaryActionAssets.renderCarrier(carrier, "shortbow",
                manifest, stem + "_Root", renamed(manifest, stem), 15, 0, stem + "_Animations")).getAsJsonObject();
        JsonObject oldVars = source.getAsJsonObject("InteractionVars"), newVars = rendered.getAsJsonObject("InteractionVars");
        JsonObject oldCharge = oldVars.getAsJsonObject("Primary_Shoot_Charge");
        JsonObject newCharge = newVars.getAsJsonObject("Primary_Shoot_Charge");
        JsonObject oldStage = oldCharge.getAsJsonArray("Interactions").get(0).getAsJsonObject();
        JsonObject newStage = newCharge.getAsJsonArray("Interactions").get(0).getAsJsonObject();
        assertTrue(oldStage.getAsJsonObject("Next").has("1.2"));
        assertTrue(newStage.getAsJsonObject("Next").has("1.043478"));
        assertEquals(stem + "_Animations", newStage.getAsJsonObject("Effects")
                .get("ItemPlayerAnimationsId").getAsString());
        if (oldVars.has("Guard_Wield")) assertEquals(oldVars.get("Guard_Wield"), newVars.get("Guard_Wield"));
    }
    @Test void focusLaunchVariableSelectsScaledManagedReleaseRoot() {
        JsonObject all = resource(PREFIX + "manifest.json");
        for (String family : List.of("staff", "wand", "book")) {
            JsonObject spec = all.getAsJsonObject(family);
            String stem = "RPG_Action_Test_" + family;
            String carrier = switch (family) {
                case "staff" -> "RPG_Gear_staff_apprentice_h";
                case "wand" -> "RPG_Gear_wand_amber_h";
                default -> "RPG_Gear_book_apprentice_h";
            };
            JsonObject source = resource("/Server/Item/Items/RPG/Gear/" + carrier + ".json");
            JsonObject variant = JsonParser.parseString(NativePrimaryActionAssets.renderCarrier(carrier, family,
                    spec, stem + "_Root", renamed(spec, stem), 15, 0, stem + "_Animations")).getAsJsonObject();
            boolean changed = false;
            for (var row : source.getAsJsonObject("InteractionVars").entrySet())
                if (row.getValue().isJsonPrimitive() && row.getValue().getAsString().equals(spec.get("launchRoot").getAsString())) {
                    assertEquals(stem + "_Launch_Root", variant.getAsJsonObject("InteractionVars")
                            .get(row.getKey()).getAsString());
                    changed = true;
                }
            assertTrue(changed, family + " has a managed release variable");
            boolean effectBound = false;
            for (JsonElement name : spec.getAsJsonArray("actionVariables")) {
                String key = name.getAsString();
                if (!key.endsWith("_Effect") && !key.endsWith("_Effects")) continue;
                JsonElement old = source.getAsJsonObject("InteractionVars").get(key);
                if (old != null && old.isJsonPrimitive() && old.getAsString().endsWith("_Effect")) {
                    assertEquals(stem + "_" + old.getAsString(), variant.getAsJsonObject("InteractionVars")
                            .get(key).getAsString());
                    effectBound = true;
                }
            }
            assertTrue(effectBound, family + " binds primary effect timings to the same variant");
        }
    }
    @Test void crossbowOrdinaryShotScalesButExplicitAmmoReloadRemainsFixed() {
        JsonObject spec = resource(PREFIX + "manifest.json").getAsJsonObject("crossbow");
        String carrier = "RPG_Gear_crossbow_heavy_h", stem = "RPG_Action_Test_crossbow";
        JsonObject source = resource("/Server/Item/Items/RPG/Gear/" + carrier + ".json");
        JsonObject variant = JsonParser.parseString(NativePrimaryActionAssets.renderCarrier(carrier, "crossbow",
                spec, stem + "_Root", renamed(spec, stem), 15, 0, stem + "_Animations")).getAsJsonObject();
        var oldVars = source.getAsJsonObject("InteractionVars");
        var newVars = variant.getAsJsonObject("InteractionVars");
        JsonObject oldShot = oldVars.getAsJsonObject("Standard_Projectile_Launch")
                .getAsJsonArray("Interactions").get(0).getAsJsonObject();
        JsonObject newShot = newVars.getAsJsonObject("Standard_Projectile_Launch")
                .getAsJsonArray("Interactions").get(0).getAsJsonObject();
        assertEquals(oldShot.get("RunTime").getAsDouble() / 1.15, newShot.get("RunTime").getAsDouble(), 1e-6);
        assertEquals(oldVars.get("Reload_Effects"), newVars.get("Reload_Effects"));
        assertEquals(oldVars.get("Reload_Start"), newVars.get("Reload_Start"));
    }
    @Test void primaryNativeDamageLeavesCarrySelectorBudgetWithoutMarkingVolleyPellets() {
        JsonObject all = resource(PREFIX + "manifest.json");
        String carrier = "RPG_Gear_shortbow_adamantite_h", stem = "RPG_Action_Test_shortbow";
        JsonObject spec = all.getAsJsonObject("shortbow");
        JsonObject variant = JsonParser.parseString(NativePrimaryActionAssets.renderCarrier(carrier, "shortbow",
                spec, stem + "_Root", renamed(spec, stem), 15, 0, stem + "_Animations")).getAsJsonObject();
        JsonObject vars = variant.getAsJsonObject("InteractionVars");
        for (int strength = 0; strength <= 4; strength++) {
            String key = "Primary_Shoot_Damage_Strength_" + strength;
            JsonObject leaf = vars.getAsJsonObject(key).getAsJsonArray("Interactions").get(0).getAsJsonObject();
            assertEquals(key, leaf.get("RpgProcSelector").getAsString());
            assertEquals(1, leaf.get("RpgProcCoefficient").getAsDouble());
        }
        JsonObject volley = vars.getAsJsonObject("Signature_Volley_Damage")
                .getAsJsonArray("Interactions").get(0).getAsJsonObject();
        assertEquals("Signature_Volley_Damage", volley.get("RpgProcSelector").getAsString());
        assertEquals(1.0 / 3, volley.get("RpgProcCoefficient").getAsDouble(), 1e-9,
                "three simultaneous authored Volley projectiles share one opportunity");
        JsonObject staffSpec = all.getAsJsonObject("staff");
        String staffCarrier = "RPG_Gear_staff_apprentice_h", staffStem = "RPG_Action_Test_staff";
        JsonObject staff = JsonParser.parseString(NativePrimaryActionAssets.renderCarrier(staffCarrier, "staff",
                staffSpec, staffStem + "_Root", renamed(staffSpec, staffStem), 15, 0,
                staffStem + "_Animations")).getAsJsonObject();
        JsonObject focus = staff.getAsJsonObject("InteractionVars").getAsJsonObject("Spear_Swing_Left_Damage")
                .getAsJsonArray("Interactions").get(0).getAsJsonObject();
        assertEquals("RPG_Carrier_Focus_Melee_Damage", focus.get("Parent").getAsString());
        assertEquals("Spear_Swing_Left_Damage", focus.get("RpgProcSelector").getAsString());
        assertEquals(1, focus.get("RpgProcCoefficient").getAsDouble());
    }
    @Test void managedFocusAndBombProjectilesKeepTravelAndFuseWhileUsingVariantImpactLeaf() {
        for (String family : List.of("staff", "wand", "book", "bomb")) {
            String stem = "RPG_Action_Test_" + family;
            String original = "RPG_Carrier_" + Character.toUpperCase(family.charAt(0))
                    + family.substring(1) + "_Projectile";
            JsonObject source = resource("/Server/ProjectileConfigs/RPG/Carriers/" + original + ".json");
            JsonObject changed = JsonParser.parseString(NativePrimaryActionAssets.renderProjectileConfig(family, stem)).getAsJsonObject();
            assertEquals(source.get("Physics"), changed.get("Physics"), family);
            assertEquals(source.get("LaunchForce"), changed.get("LaunchForce"), family);
            assertTrue(changed.toString().contains(stem + (family.equals("bomb") ? "_Impact" : "_Projectile_Damage")));
            JsonObject leaf = JsonParser.parseString(NativePrimaryActionAssets.renderProjectileDamage(family)).getAsJsonObject();
            assertEquals(1, leaf.get("RpgProcCoefficient").getAsDouble());
            if (family.equals("bomb")) {
                JsonObject impact = JsonParser.parseString(NativePrimaryActionAssets.renderBombImpact(stem)).getAsJsonObject();
                assertEquals("AOECircle", impact.getAsJsonObject("Selector").get("Id").getAsString());
                assertEquals(resource("/Server/Item/Interactions/RPG/Carriers/RPG_Carrier_Bomb_Impact.json").get("RunTime"),
                        impact.get("RunTime"), "bomb fuse/contact timing is fixed after launch");
            }
        }
    }
    @Test void daggerReachMovesBothSeparatedSweepTrailAndStabCue() {
        JsonObject spec = resource(PREFIX + "manifest.json").getAsJsonObject("daggers");
        var names = renamed(spec, "RPG_Action_Test_daggers");
        String sweep = "Weapon_Daggers_Primary_Pounce_Sweep_Effect";
        JsonObject oldSweep = resource(PREFIX + "daggers/" + sweep + ".json");
        JsonObject newSweep = JsonParser.parseString(NativePrimaryActionAssets.renderNode("daggers", sweep,
                names, spec, 12.3, .31, "RPG_Action_Test_daggers_Animations")).getAsJsonObject();
        assertEquals(oldSweep.getAsJsonObject("Effects").getAsJsonArray("Trails").get(0).getAsJsonObject()
                .getAsJsonObject("PositionOffset").get("X").getAsDouble() + .31,
                newSweep.getAsJsonObject("Effects").getAsJsonArray("Trails").get(0).getAsJsonObject()
                        .getAsJsonObject("PositionOffset").get("X").getAsDouble(), 1e-6);
        String stab = "Weapon_Daggers_Primary_Stab_Left_Selector";
        JsonObject oldStab = resource(PREFIX + "daggers/" + stab + ".json");
        JsonObject newStab = JsonParser.parseString(NativePrimaryActionAssets.renderNode("daggers", stab,
                names, spec, 12.3, .31, "RPG_Action_Test_daggers_Animations")).getAsJsonObject();
        assertTrue(newStab.getAsJsonObject("Selector").get("TestLineOfSight").getAsBoolean());
        assertEquals(oldStab.getAsJsonObject("Selector").get("EndDistance").getAsDouble() + .31,
                newStab.getAsJsonObject("Selector").get("EndDistance").getAsDouble(), 1e-6);
        assertEquals(oldStab.getAsJsonObject("Effects").getAsJsonArray("Particles").get(0).getAsJsonObject()
                .getAsJsonObject("PositionOffset").get("Z").getAsDouble() + .31,
                newStab.getAsJsonObject("Effects").getAsJsonArray("Particles").get(0).getAsJsonObject()
                        .getAsJsonObject("PositionOffset").get("Z").getAsDouble(), 1e-6);
    }
    @Test void everyPackagedManagedCarrierRarityCanBindItsFamilyPrimaryWithoutLosingNativeVars() throws Exception {
        JsonObject all = resource(PREFIX + "manifest.json");
        Path folder = Path.of("src", "main", "resources", "Server", "Item", "Items", "RPG", "Gear");
        int count = 0;
        try (var files = Files.list(folder)) {
            for (Path path : files.filter(p -> p.getFileName().toString().endsWith(".json")).toList()) {
                String carrier = path.getFileName().toString().replaceFirst("\\.json$", "");
                String family = null;
                for (String candidate : List.of("battleaxe", "mace", "daggers", "longsword", "shortbow",
                        "crossbow", "staff", "wand", "book", "shield", "bomb"))
                    if (carrier.startsWith("RPG_Gear_" + candidate + "_")) { family = candidate; break; }
                if (family == null) continue;
                JsonObject spec = all.getAsJsonObject(family);
                String stem = "RPG_Action_All_" + family;
                JsonObject original = resource("/Server/Item/Items/RPG/Gear/" + carrier + ".json");
                JsonObject variant = JsonParser.parseString(NativePrimaryActionAssets.renderCarrier(carrier,
                        family, spec, stem + "_Root", renamed(spec, stem), 15,
                        Set.of("battleaxe", "mace", "daggers", "longsword").contains(family) ? .12 : 0,
                        stem + "_Animations")).getAsJsonObject();
                assertEquals(original.get("InteractionVars") == null, variant.get("InteractionVars") == null, carrier);
                if (original.has("InteractionVars")) for (JsonElement variable : spec.getAsJsonArray("actionVariables")) {
                    String key = variable.getAsString();
                    if (family.equals("crossbow") && key.startsWith("Reload_")) continue;
                    JsonElement old = original.getAsJsonObject("InteractionVars").get(key);
                    JsonElement changed = variant.getAsJsonObject("InteractionVars").get(key);
                    if (old != null && changed != null && old.isJsonObject() && changed.isJsonObject())
                        compareTimeline(old, changed, carrier + "/" + key);
                }
                assertEquals(original.get("MaxDurability"), variant.get("MaxDurability"), carrier);
                assertEquals(original.get("Texture"), variant.get("Texture"), carrier);
                count++;
            }
        }
        assertTrue(count >= 900, "expected five-rarity melee, ranged, focus, shield and bomb carriers");
    }
}
