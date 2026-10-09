package com.inigmasgames.hytalerpg.gear;

import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.server.core.asset.type.item.config.ItemQuality;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.Interaction;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.RootInteraction;
import com.hypixel.hytale.server.core.modules.projectile.config.ProjectileConfig;
import org.bson.BsonDocument;
import org.bson.BsonValue;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.io.TempDir;

import com.google.gson.JsonParser;
import java.net.URL;
import java.net.URLClassLoader;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/** Native public decoder gate for the 90 staged focus, shield and bomb bases. */
public final class NativeAffixCarrierAssetQualificationTest {
    private static NativeAssetTestFixtures fixture;
    @BeforeAll static void installNativeAssets() throws Exception { fixture = NativeAssetTestFixtures.open(); }
    @AfterAll static void releaseNativeAssets() { if (fixture != null) fixture.close(); }
    @TempDir Path temp;
    private static final Path SERVER = Path.of(System.getProperty(
            "nativeCarrier.serverRoot", "src/main/resources/Server"));
    private static final Path INTERACTIONS = SERVER.resolve("Item/Interactions/RPG/Carriers");
    private static final Path ROOTS = SERVER.resolve("Item/RootInteractions/RPG/Carriers");
    private static final Path PROJECTILES = SERVER.resolve("ProjectileConfigs/RPG/Carriers");
    private static final Path ITEMS = SERVER.resolve("Item/Items/RPG/Gear");
    private static final Map<String, String> QUALITIES = Map.of("", "RPG_Gear_Common",
            "_Uncommon", "RPG_Gear_Uncommon", "_Rare", "RPG_Gear_Rare",
            "_Epic", "RPG_Gear_Epic", "_Legendary", "RPG_Gear_Legendary");

    private static List<Path> jsonFiles(Path folder) throws Exception {
        try (Stream<Path> files = Files.list(folder)) {
            return files.filter(path -> path.getFileName().toString().endsWith(".json")).sorted().toList();
        }
    }

    @Test void installedCoreAbilityRequiresHeldWeaponButAcceptsManagedStaff() throws Exception {
        loadGraphs(fixture);
        String id = "RPG_Gear_staff_apprentice_n_Uncommon";
        var staff = fixture.decodeItem(id, ITEMS.resolve(id + ".json"));
        assertNotNull(staff.getWeapon());
        var nativeAbility = new com.hypixel.hytale.server.core.asset.type.item.config.CoreItemAbility();
        assertFalse(nativeAbility.canUseWith(null), "Native abilities reject an empty active hand before the RPG callback");
        assertTrue(nativeAbility.canUseWith(new com.hypixel.hytale.server.core.inventory.ItemStack(id, 1)),
                "Managed gear is not excluded just because it is Hywind gear");
        var material = temp.resolve("QA_NonWeapon.json");
        Files.writeString(material, "{\"MaxStack\":25}");
        fixture.decodeItem("QA_NonWeapon", material);
        assertFalse(nativeAbility.canUseWith(new com.hypixel.hytale.server.core.inventory.ItemStack("QA_NonWeapon", 1)));
    }
    private static String id(Path file) {
        var name = file.getFileName().toString();
        return name.substring(0, name.length() - 5);
    }

    @Test void nativeLeafDecoderRetainsExactAuthoredProcShare() throws Exception {
        for(String type:List.of(ManagedGearDamageInteraction.TYPE,ManagedCarrierDamageInteraction.TYPE)) {
            var path=temp.resolve(type+".json");
            Files.writeString(path,"{\"Type\":\""+type+"\",\"DamageCalculator\":{\"BaseDamage\":{\"Physical\":1}},"
                    +"\"RpgProcSelector\":\"volley/pellet\",\"RpgProcCoefficient\":0.3333333333333333}");
            var leaf=fixture.decodeInteraction("QA_Proc_"+type,path);
            var variables=leaf instanceof ManagedGearDamageInteraction gear?gear.procVariables(Map.of()):
                    ((ManagedCarrierDamageInteraction)leaf).procVariables(Map.of());
            assertEquals(1.0/3,NativeGearAttackAcceptance.coefficient("volley/pellet",variables));
            assertThrows(IllegalArgumentException.class,()->NativeGearAttackAcceptance.coefficient("different",variables));
        }
    }
    private static List<GearBindings.Binding> stagedBindings() {
        var result = new ArrayList<GearBindings.Binding>();
        for (var row : new GearBindings().all())
            if (row.baseId().matches("gm\\.(?:staff|wand|book|shield|bomb)_.+\\.(?:n|nm|h)")) result.add(row);
        assertEquals(90, result.size(), "Staged carrier base count");
        return result;
    }

    private static void loadGraphs(NativeAssetTestFixtures fixture) throws Exception {
        for (var path : jsonFiles(INTERACTIONS)) fixture.decodeInteraction(id(path), path);
        for (var path : jsonFiles(ROOTS)) fixture.decodeRoot(id(path), path);
        for (var path : jsonFiles(PROJECTILES)) fixture.decodeProjectile(id(path), path);
    }

    @Test void publicAssetStoreDecodesEveryPrivateInteractionRootAndProjectileWithRealTypes() throws Exception {
            loadGraphs(fixture);
            assertEquals(11, jsonFiles(INTERACTIONS).size());
            assertEquals(9, jsonFiles(ROOTS).size());
            assertEquals(4, jsonFiles(PROJECTILES).size());
            for (var path : jsonFiles(INTERACTIONS)) {
                Interaction value = Interaction.getAssetMap().getAsset(id(path));
                assertNotNull(value, path.toString());
                if (id(path).endsWith("_Damage"))
                    assertInstanceOf(ManagedCarrierDamageInteraction.class, value, path.toString());
                if (id(path).endsWith("_Launch"))
                    assertInstanceOf(ManagedCarrierProjectile.class, value, path.toString());
            }
            for (var path : jsonFiles(ROOTS))
                assertNotNull(RootInteraction.getAssetMap().getAsset(id(path)), path.toString());
            for (var path : jsonFiles(PROJECTILES)) {
                ProjectileConfig value = ProjectileConfig.getAssetMap().getAsset(id(path));
                assertNotNull(value, path.toString());
                assertFalse(value.getInteractions().isEmpty(), path.toString());
            }
    }

    @Test void everyReferencedCustomNodeIsPublishedAndNoPrivateGraphHasFixedNativeDamage() throws Exception {
        var definitions = new LinkedHashSet<String>();
        var graphFiles = new ArrayList<Path>();
        for (var folder : List.of(INTERACTIONS, ROOTS, PROJECTILES)) {
            var files = jsonFiles(folder);
            graphFiles.addAll(files);
            for (var path : files) definitions.add(id(path));
        }
        assertEquals(24, graphFiles.size());
        for (var path : graphFiles) auditTree(BsonDocument.parse(Files.readString(path)), definitions, path.toString());
        for (var binding : stagedBindings()) {
            var stem = "RPG_Gear_" + binding.baseId().substring(3).replace('.', '_');
            for (var suffix : QUALITIES.keySet()) {
                var path = ITEMS.resolve(stem + suffix + ".json");
                assertTrue(Files.isRegularFile(path), path.toString());
                var document = BsonDocument.parse(Files.readString(path));
                for (var section : List.of("Interactions", "InteractionVars")) {
                    if (!document.containsKey(section)) continue;
                    for (var value : document.getDocument(section).values()) {
                        if (!value.isString()) continue;
                        var ref = value.asString().getValue();
                        if (ref.startsWith("RPG_Carrier_")) {
                            assertTrue(Files.isRegularFile(ROOTS.resolve(ref + ".json")),
                                    path + " references an unpublished native RootInteraction " + ref);
                        }
                    }
                }
            }
        }
    }

    private static void auditTree(BsonValue value, Set<String> definitions, String path) {
        if (value.isDocument()) {
            var object = value.asDocument();
            assertFalse(object.containsKey("EntityDamage"), path + " has fixed native EntityDamage");
            if (object.containsKey("Type") && object.get("Type").isString()) {
                var type = object.getString("Type").getValue();
                assertFalse(Set.of("DamageEntity", "LaunchProjectile", "Explode", "ModifyInventory")
                        .contains(type) || type.startsWith("Explode_"), path + " has unsafe native type " + type);
            }
            for (var child : object.values()) auditTree(child, definitions, path);
        } else if (value.isArray()) {
            for (var child : value.asArray()) auditTree(child, definitions, path);
        } else if (value.isString()) {
            var reference = value.asString().getValue();
            if (reference.startsWith("RPG_Carrier_"))
                assertTrue(definitions.contains(reference), path + " references missing custom node " + reference);
        }
    }

    @Test void itemAssetLoaderDecodesAll450DurableFiveRarityCarriers() throws Exception {
            loadGraphs(fixture);
            var catalog = GearCatalog.load();
            int loaded = 0;
            for (var binding : stagedBindings()) {
                var base = catalog.base(binding.baseId());
                var stem = "RPG_Gear_" + binding.baseId().substring(3).replace('.', '_');
                for (var row : QUALITIES.entrySet()) {
                    var id = stem + row.getKey();
                    var item = fixture.decodeItem(id, ITEMS.resolve(id + ".json"));
                    assertEquals(id, item.getId());
                    assertEquals(1, item.getMaxStack(), id);
                    assertEquals(base.durability(), item.getMaxDurability(), id);
                    assertEquals(ItemQuality.getAssetMap().getIndex(row.getValue()), item.getQualityIndex(), id);
                    assertNotNull(item.getInteractions(), id);
                    assertFalse(item.getInteractions().isEmpty(), id);
                    if (binding.baseId().startsWith("gm.bomb_"))
                        assertEquals("RPG_Carrier_Bomb_Throw_Root", item.getInteractions().get(InteractionType.Primary));
                    loaded++;
                }
            }
            assertEquals(450, loaded);
    }

    @Test void installedActionBranchesReachManagedLeavesWithoutFixedNativeDamage() throws Exception {
        int inspected = 0;
        try (var audit = new NativeCarrierReachabilityAudit(SERVER)) {
            for (var binding : stagedBindings()) {
                var stem = "RPG_Gear_" + binding.baseId().substring(3).replace('.', '_');
                for (var suffix : QUALITIES.keySet()) {
                    var item = ITEMS.resolve(stem + suffix + ".json");
                    assertFalse(audit.inspect(item).isEmpty(), item.toString());
                    inspected++;
                }
            }
        }
        assertEquals(450, inspected);
    }

    @Test void all450NativeStacksPreserveFrozenMetadataIdentityQualityAndDurability() throws Exception {
        assertEquals(450, fixture.loadCarrierAssets());
        var input = getClass().getResourceAsStream("/rpg/gear/native-bindings-v1.json");
        assertNotNull(input);
        String source;
        try (input) { source = new String(input.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8); }
        var bindings = JsonParser.parseString(source).getAsJsonObject();
        int promoted = 0;
        for (var value : bindings.getAsJsonArray("bindings")) {
            var row = value.getAsJsonObject();
            var base = row.get("baseId").getAsString();
            if (!base.matches("gm\\.(?:staff|wand|book|shield|bomb)_.+\\.(?:n|nm|h)")) continue;
            var stem = "RPG_Gear_" + base.substring(3).replace('.', '_');
            row.addProperty("managedItemId", stem);
            row.addProperty("disposition", "MAPPED");
            row.addProperty("resolutionClass", "VALIDATED_NATIVE_REUSE");
            row.addProperty("reason", "Test-only native roundtrip overlay");
            if (base.startsWith("gm.bomb_")) row.addProperty("nativeItemId", "Weapon_Bomb");
            promoted++;
        }
        assertEquals(90, promoted);
        var resourceDir = temp.resolve("rpg/gear");
        Files.createDirectories(resourceDir);
        Files.writeString(resourceDir.resolve("native-bindings-v1.json"), bindings.toString());

        URL tests = NativeCarrierIsolatedRoundTrip.class.getProtectionDomain().getCodeSource().getLocation();
        URL production = GearNativeItems.class.getProtectionDomain().getCodeSource().getLocation();
        var urls = new ArrayList<URL>();
        urls.add(temp.toUri().toURL());
        urls.add(tests);
        urls.add(production);
        try (var loader = new BindingOverlayLoader(urls.toArray(URL[]::new), getClass().getClassLoader())) {
            var probe = Class.forName(NativeCarrierIsolatedRoundTrip.class.getName(), true, loader);
            assertNotSame(NativeCarrierIsolatedRoundTrip.class, probe);
            assertEquals(450, probe.getMethod("run").invoke(null));
        }
    }

    /** Child-first only for our code; Hytale AssetRegistry remains the actual shared native registry. */
    private static final class BindingOverlayLoader extends URLClassLoader {
        BindingOverlayLoader(URL[] urls, ClassLoader parent) { super(urls, parent); }
        @Override protected synchronized Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            if (name.startsWith("com.inigmasgames.hytalerpg.")) {
                var loaded = findLoadedClass(name);
                if (loaded == null) loaded = findClass(name);
                if (resolve) resolveClass(loaded);
                return loaded;
            }
            return super.loadClass(name, resolve);
        }
        @Override public URL getResource(String name) {
            if (name.equals("rpg/gear/native-bindings-v1.json")) return findResource(name);
            return super.getResource(name);
        }
    }
}
