package com.inigmasgames.hytalerpg.gear;

import com.hypixel.hytale.codec.ExtraInfo;
import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.server.core.asset.type.item.config.Item;
import com.hypixel.hytale.server.core.asset.type.itemanimation.config.ItemPlayerAnimations;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.Interaction;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.RootInteraction;
import com.hypixel.hytale.server.core.modules.projectile.config.ProjectileConfig;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Files;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.zip.ZipFile;

import static org.junit.jupiter.api.Assertions.*;

/** Decodes requested action variants through the public native asset stores in an offline JVM. */
public final class NativeAffixActionAssetQualificationTest {
    private static NativeAssetTestFixtures fixture;
    private static final Path ITEMS = Path.of("src/main/resources/Server/Item/Items/RPG/Gear");
    private static final GearCatalog CATALOG = GearCatalog.load();

    @BeforeAll static void open() throws Exception { fixture = NativeAssetTestFixtures.open(); }
    @AfterAll static void close() { if (fixture != null) fixture.close(); }

    private static GearInstance gear(String baseId, String... affixes) {
        var rolls = new java.util.ArrayList<GearInstance.AffixRoll>();
        for (String id : affixes) {
            var definition = CATALOG.affix(id);
            double value = id.equals("WA-008") ? 12.3 : id.equals("WA-014") ? .31 : 1;
            rolls.add(new GearInstance.AffixRoll(id, definition.side(), definition.exclusionGroup(),
                    1, value, new GearRequirements.Gate(1, Map.of()), definition.name(), definition.name()));
        }
        var base = CATALOG.base(baseId);
        return GearInstance.authoredQa(base, UUID.randomUUID(), base.sourceWindow().getLast(), 1000,
                GearRarity.UNCOMMON, rolls, BigDecimal.ZERO);
    }

    private static void verifyAuxiliaryRoots(com.google.gson.JsonObject item, Map<String, String> renamed)
            throws Exception {
        if (!item.has("InteractionVars")) return;
        var zipPath = Path.of(System.getProperty("user.home"), "AppData", "Roaming", "Hytale",
                "install", "pre-release", "package", "game", "latest", "Assets.zip");
        try (var zip = new ZipFile(zipPath.toFile())) {
            for (var variable : item.getAsJsonObject("InteractionVars").entrySet()) {
                if (!variable.getValue().isJsonPrimitive()) continue;
                String original = variable.getValue().getAsString();
                if (!renamed.containsKey(original) || original.startsWith("RPG_Carrier_")) continue;
                var rootEntry = zip.stream().filter(entry -> entry.getName().startsWith("Server/Item/RootInteractions/")
                        && entry.getName().endsWith("/" + original + ".json")).findFirst()
                        .orElseThrow(() -> new IllegalStateException("Missing installed auxiliary root " + original));
                try (var input = zip.getInputStream(rootEntry)) {
                    var root = JsonParser.parseReader(new InputStreamReader(input, StandardCharsets.UTF_8))
                            .getAsJsonObject();
                    assertEquals(1, root.size(), original);
                    assertEquals(1, root.getAsJsonArray("Interactions").size(), original);
                    assertEquals(original, root.getAsJsonArray("Interactions").get(0).getAsString());
                }
            }
        }
    }

    @Test void nativeStoresDecodePublishedPrimaryGraphAndPersistedStack() throws Exception {
        fixture.loadInstalledDamageParents();
        for (String family : List.of("battleaxe", "mace", "daggers", "twin", "longsword", "shortbow",
                "crossbow", "staff", "wand", "book", "bomb")) {
            String catalogFamily = family.equals("twin") ? "daggers" : family;
            String baseId = CATALOG.bases().stream().map(GearCatalog.Base::id)
                    .filter(id -> id.startsWith("gm." + catalogFamily + "_") && id.endsWith(".nm"))
                    .sorted().findFirst().orElseThrow();
            var gear = family.equals("twin") ? gear(baseId, "WA-141") :
                    family.matches("battleaxe|mace|daggers|longsword")
                    ? gear(baseId, "WA-008", "WA-014") : gear(baseId, "WA-008");
            var binding = new GearBindings().require(baseId);
            String carrier = binding.carrier(gear.rarity());
            var original = JsonParser.parseString(Files.readString(ITEMS.resolve(carrier + ".json")))
                    .getAsJsonObject();
            fixture.seedPackagedRootReferences(original.toString());
            fixture.loadInstalledAnimation(original.get("PlayerAnimationsId").getAsString());
            fixture.decodeItem(carrier, ITEMS.resolve(carrier + ".json"));
            var stack = GearNativeItems.create(gear, 45, Map.of())
                    .withDurability(Math.max(1, CATALOG.base(baseId).durability() - 17));
            var snapshot = new GearEffectSnapshot(List.of(gear));
            var profile = NativeGearActionProfiles.primary(snapshot, gear.identity());
            var manifest = JsonParser.parseString(Files.readString(Path.of(
                    "src/main/resources/rpg/gear/action-templates/native-primary/manifest.json")))
                    .getAsJsonObject().getAsJsonObject(family);
            fixture.loadActionCommonReferences(Files.readString(Path.of(
                    "src/main/resources/rpg/gear/action-templates/native-primary", family, "animations.json")));
            String stem = profile.rootId().substring(0, profile.rootId().length() - 5);
            var renamed = new java.util.HashMap<String, String>();
            for (var value : manifest.getAsJsonArray("nodes"))
                renamed.put(value.getAsString(), stem + "_" + value.getAsString());
            verifyAuxiliaryRoots(original, renamed);
            var graph = new com.google.gson.JsonArray();
            for (var value : manifest.getAsJsonArray("nodes")) {
                String rendered = NativePrimaryActionAssets.renderNode(family, value.getAsString(), renamed, manifest,
                        profile.frozenRatePercent(), profile.reachMetres(), profile.animationsId());
                fixture.seedActionReferences(rendered);
                graph.add(JsonParser.parseString(rendered));
            }
            String renderedCarrier = NativePrimaryActionAssets.renderCarrier(carrier, family, manifest,
                    profile.rootId(), renamed, profile.frozenRatePercent(), profile.reachMetres(), profile.animationsId());
            fixture.seedActionReferences(renderedCarrier);
            graph.add(JsonParser.parseString(renderedCarrier));
            var graphRoot = new com.google.gson.JsonObject();
            graphRoot.add("items", graph);
            fixture.loadInstalledParentReferences(graphRoot.toString());
            String variant = NativePrimaryActionAssets.publish(carrier, profile);
            assertNotNull(ItemPlayerAnimations.getAssetMap().getAsset(profile.animationsId()), family);
            assertNotNull(RootInteraction.getAssetMap().getAsset(profile.rootId()), family);
            String node = manifest.getAsJsonArray("nodes").get(0).getAsString();
            assertNotNull(Interaction.getAssetMap().getAsset(profile.rootId().substring(0,
                    profile.rootId().length() - 5) + "_" + node), family);
            for (var value : manifest.getAsJsonArray("nodes"))
                assertNotNull(Interaction.getAssetMap().getAsset(stem + "_" + value.getAsString()), family);
            assertNotNull(Item.getAssetMap().getAsset(variant), family);
            assertEquals(profile.rootId(), Item.getAssetMap().getAsset(variant)
                    .getInteractions().get(InteractionType.Primary), family);
            if (family.matches("staff|wand|book|bomb"))
                assertNotNull(ProjectileConfig.getAssetMap().getAsset(
                        profile.rootId().substring(0, profile.rootId().length() - 5) + "_Projectile"), family);
            if (family.matches("staff|wand|book|bomb"))
                assertNotNull(Interaction.getAssetMap().getAsset(stem + "_Projectile_Damage"), family);
            if (family.matches("staff|wand|book"))
                assertNotNull(RootInteraction.getAssetMap().getAsset(stem + "_Launch_Root"), family);
            if (family.equals("bomb"))
                assertNotNull(Interaction.getAssetMap().getAsset(stem + "_Impact"), family);
            var changed = GearNativeItems.actionVariant(stack, snapshot, gear.identity());
            assertEquals(variant, changed.getItemId(), family);
            ItemStack restored = ItemStack.CODEC.decode(ItemStack.CODEC.encode(changed, new ExtraInfo()), new ExtraInfo());
            assertEquals(changed.getItemId(), restored.getItemId(), family);
            assertEquals(changed.getMetadata(), restored.getMetadata(), family);
            assertEquals(changed.getDurability(), restored.getDurability(), family);
            assertEquals(changed.getQualityIndex(), restored.getQualityIndex(), family);
            assertEquals(gear, GearNativeItems.read(restored), family);
            assertEquals(carrier, GearNativeItems.actionVariant(restored, new GearEffectSnapshot(List.of()),
                    gear.identity()).getItemId(), family);
            var other = gear(baseId, "WA-008");
            assertEquals(carrier, GearNativeItems.actionVariant(restored,
                    new GearEffectSnapshot(List.of(other)), gear.identity()).getItemId(), family);
            assertEquals(variant, NativePrimaryActionAssets.publish(carrier, profile), family);
            verifyColdRebind(changed, snapshot, gear, stem, profile.animationsId());
        }
    }

    @Test void twinUtilityRequiresDistinctQualifiedSameBaseSourceAndPersists() throws Exception {
        String baseId = CATALOG.bases().stream().map(GearCatalog.Base::id)
                .filter(id -> id.startsWith("gm.daggers_") && id.endsWith(".nm"))
                .sorted().findFirst().orElseThrow();
        var main = gear(baseId, "WA-141");
        var offhand = gear(baseId);
        String carrier = new GearBindings().require(baseId).carrier(GearRarity.UNCOMMON);
        var source = JsonParser.parseString(Files.readString(ITEMS.resolve(carrier + ".json"))).getAsJsonObject();
        fixture.seedPackagedRootReferences(source.toString());
        fixture.loadInstalledAnimation(source.get("PlayerAnimationsId").getAsString());
        fixture.decodeItem(carrier, ITEMS.resolve(carrier + ".json"));
        String utilityJson = NativeTwinUtilityAssets.renderCarrier(carrier);
        fixture.seedActionReferences(utilityJson);
        fixture.loadInstalledParentReferences(utilityJson);
        var accepted = new GearEffectSnapshot(List.of(main, offhand));
        assertTrue(NativeTwinUtilityAssets.eligible(accepted, main.identity(), offhand));
        assertFalse(NativeTwinUtilityAssets.eligible(accepted, main.identity(), main));
        assertFalse(NativeTwinUtilityAssets.eligible(new GearEffectSnapshot(List.of(offhand)),
                main.identity(), offhand));
        var original = GearNativeItems.create(offhand, 45, Map.of());
        var converted = GearNativeItems.actionVariant(original, accepted, main.identity());
        assertEquals(NativeTwinUtilityAssets.variantId(carrier), converted.getItemId());
        assertNotNull(Item.getAssetMap().getAsset(converted.getItemId()));
        var restored = ItemStack.CODEC.decode(ItemStack.CODEC.encode(converted, new ExtraInfo()), new ExtraInfo());
        assertEquals(offhand, GearNativeItems.read(restored));
        assertEquals(original.getItemId(), GearNativeItems.actionVariant(restored,
                new GearEffectSnapshot(List.of(offhand)), null).getItemId());
    }

    @Test void swordVariantAlsoDecodesThroughNativeStores() throws Exception {
        var sword = gear("gm.sword_iron.nm", "WA-008", "WA-014");
        String carrier = new GearBindings().require(sword.baseId()).carrier(GearRarity.UNCOMMON);
        var source = JsonParser.parseString(Files.readString(ITEMS.resolve(carrier + ".json"))).getAsJsonObject();
        fixture.loadInstalledAnimation(source.get("PlayerAnimationsId").getAsString());
        fixture.decodeItem(carrier, ITEMS.resolve(carrier + ".json"));
        var accepted = new GearEffectSnapshot(List.of(sword));
        var profile = NativeGearActionProfiles.primary(accepted, sword.identity());
        String stem = profile.rootId().substring(0, profile.rootId().length() - 5);
        String sample = "RPG_Action_Sword_S115_R40_Weapon_Sword_Primary";
        var graph = new com.google.gson.JsonArray();
        try (var files = Files.list(Path.of("src/main/resources/Server/Item/Interactions/RPG/ActionProfiles"))) {
            for (var path : files.filter(p -> p.getFileName().toString().startsWith(sample)
                    && p.getFileName().toString().endsWith(".json")).toList()) {
                String suffix = path.getFileName().toString().substring(sample.length(),
                        path.getFileName().toString().length() - 5);
                String rendered = NativeSwordActionAssets.renderNode(stem, suffix,
                        profile.frozenRatePercent(), profile.reachMetres());
                fixture.seedActionReferences(rendered);
                graph.add(JsonParser.parseString(rendered));
            }
        }
        String item = NativeSwordActionAssets.renderCarrier(carrier, profile.rootId());
        fixture.seedActionReferences(item);
        graph.add(JsonParser.parseString(item));
        var wrapper = new com.google.gson.JsonObject();
        wrapper.add("items", graph);
        fixture.loadInstalledParentReferences(wrapper.toString());
        String variant = NativeSwordActionAssets.publish(carrier, profile);
        assertNotNull(ItemPlayerAnimations.getAssetMap().getAsset(profile.animationsId()));
        assertNotNull(RootInteraction.getAssetMap().getAsset(profile.rootId()));
        assertNotNull(Item.getAssetMap().getAsset(variant));
        var stack = GearNativeItems.actionVariant(GearNativeItems.create(sword, 45, Map.of()),
                accepted, sword.identity());
        assertEquals(variant, stack.getItemId());
        assertEquals(sword, GearNativeItems.read(ItemStack.CODEC.decode(
                ItemStack.CODEC.encode(stack, new ExtraInfo()), new ExtraInfo())));
        verifyColdRebind(stack, accepted, sword, stem, profile.animationsId());
    }

    /** Simulates ephemeral asset loss; only frozen ItemStack data survives this boundary. */
    private static void verifyColdRebind(ItemStack stack, GearEffectSnapshot accepted, GearInstance gear,
                                         String stem, String animationsId) {
        var saved = ItemStack.CODEC.encode(stack, new ExtraInfo());
        Item.getAssetStore().removeAssets(List.of(stack.getItemId()));
        RootInteraction.getAssetStore().removeAssets(RootInteraction.getAssetMap().getAssetMap().keySet().stream()
                .filter(id -> id.startsWith(stem + "_")).toList());
        Interaction.getAssetStore().removeAssets(Interaction.getAssetMap().getAssetMap().keySet().stream()
                .filter(id -> id.startsWith(stem + "_")).toList());
        ProjectileConfig.getAssetStore().removeAssets(ProjectileConfig.getAssetMap().getAssetMap().keySet().stream()
                .filter(id -> id.startsWith(stem + "_")).toList());
        ItemPlayerAnimations.getAssetStore().removeAssets(List.of(animationsId));
        assertNull(Item.getAssetMap().getAsset(stack.getItemId()));
        var restored = ItemStack.CODEC.decode(saved, new ExtraInfo());
        assertEquals(gear, GearNativeItems.read(restored));
        // Exercise the native accessor before publication too: stale cached EMPTY must not survive rebind.
        restored.getItem();
        var rebound = GearNativeItems.actionVariant(restored, accepted, gear.identity());
        assertEquals(stack.getItemId(), rebound.getItemId());
        assertNotNull(Item.getAssetMap().getAsset(rebound.getItemId()));
        assertEquals(rebound.getItemId(), rebound.getItem().getId());
        assertEquals(stack.getMetadata(), rebound.getMetadata());
        assertEquals(stack.getDurability(), rebound.getDurability());
        assertEquals(stack.getQualityIndex(), rebound.getQualityIndex());
        assertEquals(gear, GearNativeItems.read(rebound));
    }

    @Test void nativeVariantCapacityFailsClosedAtBoundary() {
        assertDoesNotThrow(() -> NativePrimaryActionAssets.requireCapacity(4095));
        assertThrows(IllegalStateException.class, () -> NativePrimaryActionAssets.requireCapacity(4096));
    }
}
