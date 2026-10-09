package com.inigmasgames.hytalerpg.gear;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.zip.ZipFile;
import static org.junit.jupiter.api.Assertions.*;

/** Deterministic checks of the generated native assets and the exact binding input.
 * These are asset integration checks, not a connected client/contact test. */
class NativeGearActionProfilesTest {
    private static final String STEM = "RPG_Action_Sword_S115_R40";
    private final GearCatalog catalog = GearCatalog.load();

    private GearInstance sword(UUID id, String... affixIds) {
        return sword(id, 15, .4, affixIds);
    }

    private GearInstance sword(UUID id, double rate, double reach, String... affixIds) {
        var rolls = new java.util.ArrayList<GearInstance.AffixRoll>();
        for (String affixId : affixIds) {
            var definition = catalog.affix(affixId);
            double value = affixId.equals("WA-008") ? rate : reach;
            rolls.add(new GearInstance.AffixRoll(affixId, definition.side(), definition.exclusionGroup(),
                    1, value, new GearRequirements.Gate(1, Map.of()), definition.name(), definition.name()));
        }
        return GearInstance.authoredQa(catalog.base("gm.sword_iron.nm"), id, 45, 1000,
                GearRarity.UNCOMMON, rolls, BigDecimal.ZERO);
    }

    private static JsonObject resource(String path) throws Exception {
        try (var stream = NativeGearActionProfilesTest.class.getResourceAsStream(path)) {
            assertNotNull(stream, path);
            return JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
        }
    }

    private static JsonObject installed(String path) throws Exception {
        var zipPath = Path.of(System.getProperty("user.home"), "AppData", "Roaming", "Hytale",
                "install", "pre-release", "package", "game", "latest", "Assets.zip");
        try (var zip = new ZipFile(zipPath.toFile())) {
            var entry = zip.getEntry(path);
            assertNotNull(entry, path);
            try (var stream = zip.getInputStream(entry)) {
                return JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
            }
        }
    }

    @Test void acceptedLocalSwordSelectsAnImmutableNativeRootWithoutIdleItemLeak() {
        var main = sword(UUID.randomUUID(), "WA-008", "WA-014");
        var idle = sword(UUID.randomUUID(), "WA-008");
        var accepted = new GearEffectSnapshot(List.of(main, idle));
        var profile = NativeGearActionProfiles.swordPrimary(accepted, main.identity());
        assertEquals(STEM + "_Root", profile.rootId());
        assertEquals(STEM + "_Animations", profile.animationsId());
        assertEquals(15, profile.frozenRatePercent());
        assertEquals(15, profile.effectiveRatePercent());
        assertEquals(.4, profile.reachMetres());
        assertEquals(main.identity(), profile.itemId());
        assertEquals(accepted.revision(), profile.snapshotRevision());
        assertEquals(0, NativeGearActionProfiles.swordPrimary(accepted, idle.identity()).reachMetres());
        assertThrows(IllegalArgumentException.class,
                () -> NativeGearActionProfiles.swordPrimary(accepted, UUID.randomUUID()));
        var unaffixed = sword(UUID.randomUUID());
        assertThrows(IllegalArgumentException.class,
                () -> NativeGearActionProfiles.swordPrimary(new GearEffectSnapshot(List.of(unaffixed)),
                        unaffixed.identity()));
    }

    @Test void lowTierFrozenRollsRetainExactNativeVariantIdentity() {
        var low = sword(UUID.randomUUID(), 4.8, .12, "WA-008", "WA-014");
        var accepted = new GearEffectSnapshot(List.of(low));
        var profile = NativeGearActionProfiles.primary(accepted, low.identity());
        assertEquals(4.8, profile.frozenRatePercent());
        assertEquals(.12, profile.reachMetres());
        assertEquals("RPG_Action_Sword_S1048_R12_Root", profile.rootId());
        assertTrue(NativeSwordActionAssets.variantOf("RPG_Gear_sword_iron_nm__S1048_R12", "RPG_Gear_sword_iron_nm"));
        assertFalse(NativeSwordActionAssets.variantOf("RPG_Gear_sword_iron_nm__S1048_R11", "RPG_Gear_sword_iron_nm"));
    }
    @Test void nonSwordAcceptedPrimaryUsesItsOwnAuthoredGraphAndLocalRoll() {
        var definition = catalog.affix("WA-008");
        var roll = new GearInstance.AffixRoll("WA-008", definition.side(), definition.exclusionGroup(),
                1, 12.3, new GearRequirements.Gate(1, Map.of()), definition.name(), definition.name());
        var base = catalog.base("gm.battleaxe_iron.nm");
        var axe = GearInstance.authoredQa(base, UUID.randomUUID(), 45, 1000,
                GearRarity.UNCOMMON, List.of(roll), BigDecimal.ZERO);
        var idle = sword(UUID.randomUUID(), 18, .5, "WA-008", "WA-014");
        var snapshot = new GearEffectSnapshot(List.of(axe, idle));
        var profile = NativeGearActionProfiles.primary(snapshot, axe.identity());
        assertEquals("RPG_Action_Battleaxe_S1123_R00_Root", profile.rootId());
        assertEquals(12.3, profile.frozenRatePercent());
        assertEquals(0, profile.reachMetres());
        String carrier = new GearBindings().require(axe.baseId()).carrier(axe.rarity());
        assertEquals(carrier + "__ABattleaxe_S1123_R00", NativePrimaryActionAssets.variantId(carrier, profile));
        assertThrows(IllegalArgumentException.class,
                () -> NativeGearActionProfiles.primary(snapshot, UUID.randomUUID()));
    }

    @Test void nativeTimelineAndBothCameraAnimationsAreScaledTogether() throws Exception {
        var sourceRoot = installed("Server/Item/RootInteractions/Weapons/Sword/Root_Weapon_Sword_Primary.json");
        var root = resource("/Server/Item/RootInteractions/RPG/ActionProfiles/" + STEM + "_Root.json");
        assertEquals(sourceRoot.getAsJsonObject("Cooldown").get("Cooldown").getAsDouble() / 1.15,
                root.getAsJsonObject("Cooldown").get("Cooldown").getAsDouble(), 1e-6);
        assertEquals(STEM + "_Weapon_Sword_Primary", root.getAsJsonArray("Interactions").get(0).getAsString());
        var sourceSwing = installed("Server/Item/Interactions/Weapons/Sword/Attacks/Primary/Swing_Left/Weapon_Sword_Primary_Swing_Left.json");
        var swing = resource("/Server/Item/Interactions/RPG/ActionProfiles/" + STEM + "_Weapon_Sword_Primary_Swing_Left.json");
        assertEquals(sourceSwing.get("RunTime").getAsDouble() / 1.15, swing.get("RunTime").getAsDouble(), 1e-6);
        assertEquals(STEM + "_Animations", swing.getAsJsonObject("Effects").get("ItemPlayerAnimationsId").getAsString());
        var sourceChain = installed("Server/Item/Interactions/Weapons/Sword/Attacks/Primary/Weapon_Sword_Primary_Chain.json");
        var chain = resource("/Server/Item/Interactions/RPG/ActionProfiles/" + STEM + "_Weapon_Sword_Primary_Chain.json");
        assertEquals(sourceChain.get("ChainingAllowance").getAsDouble() / 1.15,
                chain.get("ChainingAllowance").getAsDouble(), 1e-6);
        var stockAnimations = installed("Server/Item/Animations/Sword.json").getAsJsonObject("Animations");
        var animations = resource("/Server/Item/Animations/" + STEM + "_Animations.json").getAsJsonObject("Animations");
        for (String name : List.of("SwingLeft", "SwingRight", "SwingDownStrong", "StabDashCharging", "StabDashCharged")) {
            var stock = stockAnimations.getAsJsonObject(name);
            var profile = animations.getAsJsonObject(name);
            assertEquals(stock.get("Speed").getAsDouble() * 1.15, profile.get("Speed").getAsDouble(), 1e-6);
            assertEquals(stock.get("FirstPerson"), profile.get("FirstPerson"));
            assertEquals(stock.get("ThirdPerson"), profile.get("ThirdPerson"));
        }
    }

    @Test void nativeSelectorRetainsArcBlockPathAndLineOfSightWhileExtendingContactAndTrail() throws Exception {
        var carrier = resource("/Server/Item/Items/RPG/Gear/RPG_Gear_sword_iron_nm.json");
        for (String attack : List.of("Swing_Left", "Swing_Right", "Swing_Down", "Thrust")) {
            String name = "Weapon_Sword_Primary_" + attack + "_Selector.json";
            String folder = attack.equals("Thrust") ? "Thrust" : attack;
            var stock = installed("Server/Item/Interactions/Weapons/Sword/Attacks/Primary/" + folder + "/" + name);
            var profile = resource("/Server/Item/Interactions/RPG/ActionProfiles/" + STEM + "_" + name);
            var originalShape = stock.getAsJsonObject("Selector");
            var extendedShape = profile.getAsJsonObject("Selector");
            assertEquals(originalShape.get("EndDistance").getAsDouble() + .4,
                    extendedShape.get("EndDistance").getAsDouble(), 1e-8);
            assertEquals(originalShape.get("Id"), extendedShape.get("Id"));
            assertEquals(originalShape.get("StartDistance"), extendedShape.get("StartDistance"));
            assertTrue(extendedShape.get("TestLineOfSight").getAsBoolean());
            assertEquals(stock.getAsJsonObject("HitBlock"), profile.getAsJsonObject("HitBlock"));
            String damageVar = attack + "_Damage";
            var managedLeaf = carrier.getAsJsonObject("InteractionVars").getAsJsonObject(damageVar)
                    .getAsJsonArray("Interactions").get(0).getAsJsonObject();
            assertEquals("RPG_GearDamage", managedLeaf.get("Type").getAsString());
            assertEquals(damageVar, profile.getAsJsonObject("HitEntity").getAsJsonArray("Interactions")
                    .get(0).getAsJsonObject().get("Var").getAsString());
            if (stock.has("Effects") && stock.getAsJsonObject("Effects").has("Trails")) {
                var before = stock.getAsJsonObject("Effects").getAsJsonArray("Trails");
                var after = profile.getAsJsonObject("Effects").getAsJsonArray("Trails");
                assertEquals(before.size(), after.size());
                for (int i = 0; i < before.size(); i++)
                    assertEquals(before.get(i).getAsJsonObject().getAsJsonObject("PositionOffset").get("X").getAsDouble() + .4,
                            after.get(i).getAsJsonObject().getAsJsonObject("PositionOffset").get("X").getAsDouble(), 1e-8);
            }
        }
    }

    @Test void exactFrozenRateAndReachRenderNativeTimelinesWithoutBins() throws Exception {
        var item = sword(UUID.randomUUID(), 12.3, .31, "WA-008", "WA-014");
        var accepted = new GearEffectSnapshot(List.of(item));
        var profile = NativeGearActionProfiles.swordPrimary(accepted, item.identity());
        assertEquals("RPG_Action_Sword_S1123_R31_Root", profile.rootId());
        assertEquals(12.3, profile.effectiveRatePercent());
        String stem = "RPG_Action_Sword_S1123_R31";
        var root = JsonParser.parseString(NativeSwordActionAssets.renderRoot(stem, 12.3)).getAsJsonObject();
        var stockRoot = installed("Server/Item/RootInteractions/Weapons/Sword/Root_Weapon_Sword_Primary.json");
        assertEquals(stockRoot.getAsJsonObject("Cooldown").get("Cooldown").getAsDouble() / 1.123,
                root.getAsJsonObject("Cooldown").get("Cooldown").getAsDouble(), 2e-6);
        var selector = JsonParser.parseString(NativeSwordActionAssets.renderNode(stem, "_Swing_Left_Selector", 12.3, .31)).getAsJsonObject();
        var stockSelector = installed("Server/Item/Interactions/Weapons/Sword/Attacks/Primary/Swing_Left/Weapon_Sword_Primary_Swing_Left_Selector.json");
        assertEquals(stockSelector.getAsJsonObject("Selector").get("EndDistance").getAsDouble() + .31,
                selector.getAsJsonObject("Selector").get("EndDistance").getAsDouble(), 1e-6);
        assertTrue(selector.getAsJsonObject("Selector").get("TestLineOfSight").getAsBoolean());
        assertEquals(stockSelector.getAsJsonObject("HitBlock"), selector.getAsJsonObject("HitBlock"));
        var animation = JsonParser.parseString(NativeSwordActionAssets.renderAnimation(stem, 12.3))
                .getAsJsonObject().getAsJsonObject("Animations");
        var stockAnimation = installed("Server/Item/Animations/Sword.json").getAsJsonObject("Animations");
        for (String name : List.of("SwingLeft", "SwingRight", "SwingDownStrong", "StabDashCharging", "StabDashCharged"))
            assertEquals(stockAnimation.getAsJsonObject(name).get("Speed").getAsDouble() * 1.123,
                    animation.getAsJsonObject(name).get("Speed").getAsDouble(), 2e-6);
        assertEquals(NativeSwordActionAssets.variantId("RPG_Gear_sword_iron_nm", profile),
                "RPG_Gear_sword_iron_nm__S1123_R31");
    }

    @Test void carrierOverrideKeepsManagedDamageAndOtherNativeActions() throws Exception {
        var source = resource("/Server/Item/Items/RPG/Gear/RPG_Gear_sword_iron_nm.json");
        var variant = JsonParser.parseString(NativeSwordActionAssets.renderCarrier(
                "RPG_Gear_sword_iron_nm", "RPG_Action_Sword_S1123_R31_Root")).getAsJsonObject();
        assertEquals("RPG_Action_Sword_S1123_R31_Root", variant.getAsJsonObject("Interactions").get("Primary").getAsString());
        variant.getAsJsonObject("Interactions").add("Primary", source.getAsJsonObject("Interactions").get("Primary"));
        for (String strike : List.of("Swing_Left_Damage", "Swing_Right_Damage", "Swing_Down_Damage", "Thrust_Damage")) {
            var leaf = variant.getAsJsonObject("InteractionVars").getAsJsonObject(strike)
                    .getAsJsonArray("Interactions").get(0).getAsJsonObject();
            assertEquals(strike, leaf.get("RpgProcSelector").getAsString());
            assertEquals(1, leaf.get("RpgProcCoefficient").getAsDouble());
        }
        assertEquals(source, variant);
        assertTrue(NativeSwordActionAssets.variantOf("RPG_Gear_sword_iron_nm__S1123_R31", "RPG_Gear_sword_iron_nm"));
        assertFalse(NativeSwordActionAssets.variantOf("RPG_Gear_sword_iron_nm__S1123_R51", "RPG_Gear_sword_iron_nm"));
        assertFalse(NativeSwordActionAssets.variantOf("RPG_Gear_sword_iron_nm__S1123_R31", "RPG_Gear_sword_steel_nm"));
        assertEquals("Weapon_Sword_Iron",GearNativeItems.nativeId("RPG_Gear_sword_iron_nm__S1123_R31"));
    }

}
