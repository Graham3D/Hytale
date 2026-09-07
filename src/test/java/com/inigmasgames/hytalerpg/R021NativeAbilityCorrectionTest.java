package com.inigmasgames.hytalerpg;

import com.hypixel.hytale.server.core.inventory.InventoryComponent;
import com.inigmasgames.hytalerpg.domain.SkillSlot;
import com.inigmasgames.hytalerpg.input.NativeAbilityProjectionService;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class R021NativeAbilityCorrectionTest {
    private static final Path ITEMS = Path.of("src/main/resources/Server/Item/Items/RPG/Abilities");

    @Test void exactInstalledContainerGeometryMapsOnlyTwoRpgSlots() {
        assertEquals(3, InventoryComponent.ABILITIES_LINE_WIDTH);
        assertEquals(2, InventoryComponent.ABILITIES_LINES);
        assertEquals(6, InventoryComponent.DEFAULT_ABILITIES_CAPACITY);
        assertEquals(0, NativeAbilityProjectionService.primaryIndex(SkillSlot.SKILL01));
        assertEquals(3, NativeAbilityProjectionService.primaryIndex(SkillSlot.SKILL02));
        assertEquals(-1, NativeAbilityProjectionService.primaryIndex(SkillSlot.SKILL03));
        assertEquals("Ability2", NativeAbilityProjectionService.nativeAction(SkillSlot.SKILL01));
        assertEquals("Ability3", NativeAbilityProjectionService.nativeAction(SkillSlot.SKILL02));
        assertEquals("UNAVAILABLE", NativeAbilityProjectionService.nativeAction(SkillSlot.SKILL03));
    }

    @Test void executableCohortHasTriggerOnlyNativeItemAssets() throws Exception {
        List<String> skills = List.of("Quick_Slash", "Heavy_Swing", "Shield_Bash", "Quickstep", "Pounce",
                "Riposte", "Fire_Bolt", "Frost_Bolt", "Arcane_Bolt", "Stone_Bolt", "Quick_Shot", "Axe_Toss");
        assertEquals(12, Files.list(ITEMS).filter(path -> path.toString().endsWith(".json")).count());
        for (String skill : skills) {
            Path asset = ITEMS.resolve("RPG_Ability_" + skill + ".json");
            assertTrue(Files.isRegularFile(asset), asset.toString());
            String json = Files.readString(asset);
            assertTrue(json.contains("\"Slot\": \"Primary\""), skill);
            assertTrue(json.contains("\"Cooldown\": 0"), skill);
            assertTrue(json.contains("\"Cost\": 0"), skill);
            assertTrue(json.contains("\"CostType\": \"None\""), skill);
            assertTrue(json.contains("\"Cast\": \"Root_RPG_Ability_Bridge\""), skill);
        }
        String root = Files.readString(Path.of(
                "src/main/resources/Server/Item/RootInteractions/RPG/Root_RPG_Ability_Bridge.json"));
        assertTrue(root.contains("\"Type\": \"Simple\""));
        assertFalse(root.contains("Damage"));
        assertFalse(root.contains("ChangeStat"));
        assertFalse(root.contains("TriggerCooldown"));
    }

    @Test void itemIdsAreDeterministicAndOwnershipIsPrefixBounded() {
        assertEquals("RPG_Ability_Quick_Slash", NativeAbilityProjectionService.itemIdFor("quick_slash"));
        assertEquals("RPG_Ability_Fire_Bolt", NativeAbilityProjectionService.itemIdFor("fire_bolt"));
        assertTrue(NativeAbilityProjectionService.isOwnedItem("RPG_Ability_Fire_Bolt"));
        assertFalse(NativeAbilityProjectionService.isOwnedItem("Rune_Fireball"));
        assertFalse(NativeAbilityProjectionService.isOwnedItem(""));
    }

    @Test void productionSourceHasNoCustomAbilityHudOrDuplicatePhysicalKeyOwnership() throws Exception {
        String hud = Files.readString(Path.of("src/main/resources/Common/UI/Custom/RpgHud.ui"));
        String plugin = Files.readString(Path.of(
                "src/main/java/com/inigmasgames/hytalerpg/phase00/Phase00Plugin.java"));
        String runtimeHud = Files.readString(Path.of(
                "src/main/java/com/inigmasgames/hytalerpg/ui/hud/RpgHud.java"));
        assertFalse(hud.contains("#RpgAbility"));
        assertFalse(runtimeHud.contains("writeSkills"));
        assertFalse(plugin.contains("AbilityInputObserver.observe"));
        assertFalse(plugin.contains("new AbilityInputsProbeCommand"));
        assertTrue(plugin.contains("new HytaleAbilitySkillInputAdapter(nativeAbilities::observeInput)"));
    }
}
