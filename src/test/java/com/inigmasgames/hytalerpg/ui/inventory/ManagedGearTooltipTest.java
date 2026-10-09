package com.inigmasgames.hytalerpg.ui.inventory;

import com.inigmasgames.hytalerpg.combat.attribute.RpgAttribute;
import com.inigmasgames.hytalerpg.gear.GearAffixQaSuite;
import com.inigmasgames.hytalerpg.gear.GearCatalog;
import com.inigmasgames.hytalerpg.gear.GearNativeItems;
import com.inigmasgames.hytalerpg.gear.GearTooltip;
import org.junit.jupiter.api.Test;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.imageio.ImageIO;
import java.util.Map;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

final class ManagedGearTooltipTest {
    private static final UUID OWNER = UUID.fromString("b7dd679b-c38e-4a8e-8d9b-715c35b14af4");

    @Test void authoritativeRarityAffixesRequirementsAndDurabilityShapeTheView() {
        var qa = new GearAffixQaSuite(GearCatalog.load());
        for (String band : new String[]{"common", "magic", "rare"}) {
            var gear = qa.create("tooltip-" + band + "-battleaxe", OWNER);
            var lines = GearTooltip.describe(gear, 1, Map.of(RpgAttribute.STR, 0));
            var model = ManagedGearTooltip.model(new GearNativeItems.TooltipView(gear, lines, 37, 120));
            assertEquals(gear.displayName(), model.name());
            assertEquals(gear.rarity().label, model.rarity());
            assertEquals(band.equals("common") ? "Common" : band.equals("magic") ? "Rare" : "Epic", model.band());
            assertEquals(gear.affixes().size(), model.affixes().size());
            assertEquals("Durability: 37/120", model.durability());
            assertTrue(model.requirements().stream().anyMatch(line -> line.style() == GearTooltip.Style.ERROR));
            assertFalse(model.name().contains("ID:"));
            assertFalse(model.damage().isBlank());
            assertTrue(model.flavor().isBlank());
        }
    }

    @Test void popupCentersAboveTheFullFootprintAndClampsOnlyAtAnEdge() {
        var entry = new SpatialLayout.Entry("bow", new SpatialLayout.Size(2, 4),
                new SpatialLayout.Position(10, 1));
        var whole = ManagedGearTooltip.bagRect(entry);
        assertEquals(150, whole.width());
        assertEquals(302, whole.height());
        var placement = ManagedGearTooltip.place(whole, 400, 250, 1164, 900);
        assertEquals(whole.x() + (whole.width() - 400) / 2, placement.x());
        assertEquals(whole.y() - 250 - 10, placement.y());
        // Hovering any covered cell resolves to the same Entry and therefore
        // the same complete rectangle, rather than centering on that one cell.
        var layout = new SpatialLayout(15, 5);
        assertTrue(layout.add(entry.id(), entry.size(), entry.position()));
        assertEquals(entry, layout.at(10, 1).orElseThrow());
        assertEquals(entry, layout.at(11, 4).orElseThrow());
        for (int[] viewport : new int[][]{{1280, 800}, {1920, 1080}, {1000, 700}}) {
            int width = viewport[0], height = viewport[1];
            var right = ManagedGearTooltip.place(new ManagedGearTooltip.Rect(width - 90, 60, 74, 74),
                    400, 300, width, height);
            assertTrue(right.x() + 400 <= width - 8);
            var bottom = ManagedGearTooltip.place(new ManagedGearTooltip.Rect(60, height - 80, 74, 74),
                    400, 300, width, height);
            assertTrue(bottom.x() >= 8 && bottom.x() + 400 <= width - 8);
            assertTrue(bottom.y() >= 8 && bottom.y() + 300 <= height - 8);
        }
    }

    @Test void nativeItemFallbackUsesTheSamePanelWithoutInventingGearFields() {
        var model = ManagedGearTooltip.nativeModel("Meat", "Common", "Common", "Food",
                java.util.List.of("Restores health", "Cook before eating"), 0, 0);
        assertEquals("Meat", model.name());
        assertEquals(java.util.List.of("Restores health", "Cook before eating"), model.base());
        assertTrue(model.affixes().isEmpty());
        assertTrue(model.damage().isEmpty());
        assertTrue(model.requirements().isEmpty());
        assertTrue(model.durability().isEmpty());
    }

    @Test void hoverTargetsSuppressNativeInfoAndRowsHaveRealTextControls() throws Exception {
        var root = Path.of("src/main/resources/Common/UI/Custom");
        assertTrue(Files.readString(root.resolve("RpgInventory/GridCommon.ui")).contains("InfoDisplay: None"));
        for (String slot : new String[]{"Weapon", "Offhand", "Head", "Chest", "Hands", "Legs", "RingLeft", "RingRight"})
            assertTrue(Files.readString(root.resolve("InventoryGearTargets/" + slot + "Section1.ui"))
                    .contains("InfoDisplay: None"), slot);
        var tooltip = Files.readString(root.resolve("RpgGearTooltip.ui"));
        assertTrue(tooltip.contains("HitTestVisible: false"));
        assertTrue(tooltip.contains("#TooltipDividerIdentity"));
        assertTrue(tooltip.contains("#TooltipDividerBase"));
        assertTrue(tooltip.contains("#TooltipDividerAffix"));
        assertFalse(tooltip.contains("RPG_Gear_"));
        assertFalse(tooltip.contains("ItemIcon"));
        assertTrue(tooltip.contains("Group #TitleRare"));
        assertTrue(tooltip.contains("Group #TitleEpic"));
        assertTrue(tooltip.contains("Group #AccentRare"));
        assertTrue(tooltip.contains("Group #AccentEpic"));
        assertFalse(tooltip.contains("Group #TitleMagic"));
        assertTrue(tooltip.contains("TextColor: #6b00ff"));
        assertTrue(tooltip.contains("Background: #6b00ff"));
        for (String band : new String[]{"Common", "Magic", "Rare", "Legendary"}) {
            var original = Path.of("src/main/resources/Common/UI/ItemQualities/Tooltips/Hywind",
                    "ItemTooltip" + band + "@2x.png");
            var uiPath = Path.of("src/main/resources/Common/UI/Icons/RPG/Tooltips",
                    "ItemTooltip" + band + "@2x.png");
            assertEquals(-1, Files.mismatch(original, uiPath), "UI frame copy must preserve the source pixels");
        }
        // A CustomUI document resolves textures beside itself. The inventory body
        // is the connected, known-good control for this local Icons/Hytale namespace.
        var inventoryUi = Files.readString(root.resolve("RpgInventoryProbe.ui"));
        assertTrue(inventoryUi.contains("TexturePath: \"Icons/Hytale/ContainerPatch.png\", Border: 23"));
        assertTrue(Files.isRegularFile(root.resolve("Icons/Hytale/ContainerPatch@2x.png")));
        assertTrue(tooltip.contains("Background: (TexturePath: \"Icons/Hytale/ItemTooltipDefault.png\", Border: 24)"));
        var frame = root.resolve("Icons/Hytale/ItemTooltipDefault@2x.png");
        assertTrue(Files.isRegularFile(frame));
        var image = ImageIO.read(frame.toFile());
        assertNotNull(image);
        assertEquals(107, image.getWidth());
        assertEquals(107, image.getHeight());
        assertFalse(Files.readString(Path.of("src/main/java/com/inigmasgames/hytalerpg/ui/inventory/ManagedGearTooltip.java"))
                .contains("ROOT + \".Background\""), "hover must not replace the native tooltip frame");
    }
}
