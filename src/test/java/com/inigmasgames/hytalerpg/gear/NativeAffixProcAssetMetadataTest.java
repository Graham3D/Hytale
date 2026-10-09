package com.inigmasgames.hytalerpg.gear;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/** Tests the metadata actually shipped on baseline items and damage assets. */
final class NativeAffixProcAssetMetadataTest {
    private static final Path SERVER = Path.of("src/main/resources/Server");
    private static final Path ITEMS = SERVER.resolve("Item/Items/RPG/Gear");
    private static final Path INTERACTIONS = SERVER.resolve("Item/Interactions/RPG");
    private static final Path PROJECTILES = SERVER.resolve("ProjectileConfigs/RPG/Gear");
    private static NativeAssetTestFixtures fixture;
    @TempDir Path temp;

    @BeforeAll static void openAssets() throws Exception { fixture = NativeAssetTestFixtures.open(); }
    @AfterAll static void closeAssets() { if (fixture != null) fixture.close(); }

    private static JsonObject read(Path path) throws Exception {
        return JsonParser.parseString(Files.readString(path)).getAsJsonObject();
    }
    private static List<Path> jsonFiles(Path folder) throws Exception {
        try (Stream<Path> stream = Files.walk(folder)) {
            return stream.filter(path -> path.toString().endsWith(".json")).sorted().toList();
        }
    }
    private static void collect(JsonElement tree, List<JsonObject> leaves, List<JsonObject> launches) {
        if (tree == null || tree.isJsonNull()) return;
        if (tree.isJsonArray()) {
            for (var child : tree.getAsJsonArray()) collect(child, leaves, launches);
            return;
        }
        if (!tree.isJsonObject()) return;
        var object = tree.getAsJsonObject();
        if (object.has("Type")) {
            var type = object.get("Type").getAsString();
            if (type.equals(ManagedGearDamageInteraction.TYPE) ||
                    type.equals(ManagedCarrierDamageInteraction.TYPE)) leaves.add(object);
            if (type.equals("RPG_GearProjectile") || type.equals("RPG_CarrierProjectile")) launches.add(object);
        }
        for (var entry : object.entrySet()) collect(entry.getValue(), leaves, launches);
    }
    private static List<JsonObject> damageLeaves(JsonElement tree) {
        var leaves = new ArrayList<JsonObject>();
        collect(tree, leaves, new ArrayList<>());
        return leaves;
    }

    @Test void everyPackagedBaselineDamageLeafHasValidMetadata() throws Exception {
        int carriers = 0, leaves = 0;
        for (var item : jsonFiles(ITEMS)) {
            carriers++;
            for (var leaf : damageLeaves(read(item))) {
                assertTrue(leaf.has("RpgProcSelector"), item.toString());
                assertFalse(leaf.get("RpgProcSelector").getAsString().isBlank(), item.toString());
                assertTrue(leaf.has("RpgProcCoefficient"), item.toString());
                double coefficient = leaf.get("RpgProcCoefficient").getAsDouble();
                assertTrue(Double.isFinite(coefficient) && coefficient > 0 && coefficient <= 1, item.toString());
                if (leaf.get("RpgProcSelector").getAsString().contains("Signature_Volley_Damage"))
                    assertEquals(1.0 / 3, coefficient, 1e-12, item.toString());
                else assertEquals(1, coefficient, 1e-12, item.toString());
                leaves++;
            }
        }
        for (var folder : List.of("Gear", "Carriers")) {
            for (var path : jsonFiles(INTERACTIONS.resolve(folder))) {
                for (var leaf : damageLeaves(read(path))) {
                    assertEquals(path.getFileName().toString().replace(".json", ""),
                            leaf.get("RpgProcSelector").getAsString(), path.toString());
                    assertEquals(path.getFileName().toString().contains("Signature_Volley_Damage") ? 1.0 / 3 : 1.0,
                            leaf.get("RpgProcCoefficient").getAsDouble(), 1e-12, path.toString());
                    leaves++;
                }
            }
        }
        assertEquals(2040, carriers, "all packaged managed carriers are included");
        assertTrue(leaves > 4000, "baseline leaves, including per-item inline leaves");
    }

    @Test void emittedVolleyMetadataDecodesAndBudgetsThreeRealPellets() throws Exception {
        var item = read(ITEMS.resolve("RPG_Gear_shortbow_adamantite_h_Epic.json"));
        var volley = damageLeaves(item).stream().filter(leaf ->
                leaf.get("RpgProcSelector").getAsString().equals("Signature_Volley_Damage"))
                .findFirst().orElseThrow();
        var source = temp.resolve("volley.json");
        Files.writeString(source, volley.toString());
        var decoded = (ManagedGearDamageInteraction) fixture.decodeInteraction("QA_Baseline_Volley", source);
        var metadata = decoded.procVariables(Map.of());
        assertEquals(1.0 / 3, NativeGearAttackAcceptance.coefficient("Signature_Volley_Damage", metadata), 1e-12);
        assertThrows(IllegalArgumentException.class, () ->
                NativeGearAttackAcceptance.coefficient("wrong/selector", metadata));

        for (int strength = 0; strength <= 2; strength++) {
            var stage = read(INTERACTIONS.resolve("Gear/RPG_GearRoute_I_Weapon_Shortbow_Signature_Volley_Strength_"
                    + strength + ".json"));
            var launches = new ArrayList<JsonObject>();
            collect(stage, new ArrayList<>(), launches);
            assertEquals(3, launches.size(), "volley strength " + strength);
            for (var launch : launches) {
                var config = read(PROJECTILES.resolve(launch.get("Config").getAsString() + ".json"));
                assertTrue(config.toString().contains("Signature_Volley_Damage"), launch.toString());
            }
            assertEquals(1, launches.size() * NativeGearAttackAcceptance.coefficient(
                    "Signature_Volley_Damage", metadata), 1e-12);
        }
    }

    @Test void baselineCarrierLeafAlsoDecodesTheEmittedPair() throws Exception {
        var source = INTERACTIONS.resolve("Carriers/RPG_Carrier_Focus_Melee_Damage.json");
        var decoded = (ManagedCarrierDamageInteraction) fixture.decodeInteraction("QA_Baseline_Focus", source);
        var metadata = decoded.procVariables(Map.of());
        assertEquals(1, NativeGearAttackAcceptance.coefficient("RPG_Carrier_Focus_Melee_Damage", metadata));
        assertThrows(IllegalArgumentException.class, () ->
                NativeGearAttackAcceptance.coefficient("Focus_Projectile_Damage", metadata));
    }
}
