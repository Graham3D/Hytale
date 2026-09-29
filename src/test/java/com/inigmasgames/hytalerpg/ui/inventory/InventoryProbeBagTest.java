package com.inigmasgames.hytalerpg.ui.inventory;

import com.hypixel.hytale.protocol.packets.interface_.CustomUICommand;
import com.hypixel.hytale.protocol.packets.interface_.CustomUICommandType;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import org.bson.BsonDocument;
import org.junit.jupiter.api.Test;
import java.util.Arrays;
import java.util.HashSet;
import static org.junit.jupiter.api.Assertions.*;

class InventoryProbeBagTest {
    private static SpatialLayout.Entry entry(String id, int x, int y, int width, int height) {
        return new SpatialLayout.Entry(id, new SpatialLayout.Size(width, height), new SpatialLayout.Position(x, y));
    }

    @Test void authoredMagicTilesOnlyRenderAtTheirExactFootprints() {
        for (var size : new SpatialLayout.Size[]{new SpatialLayout.Size(1, 1), new SpatialLayout.Size(1, 2),
                new SpatialLayout.Size(1, 3), new SpatialLayout.Size(1, 4),
                new SpatialLayout.Size(2, 2), new SpatialLayout.Size(2, 3), new SpatialLayout.Size(2, 4)}) {
            String path = InventoryProbeBag.rarityArtForQuality("RPG_Gear_Rare", size);
            assertNotNull(path);
            assertNotNull(getClass().getResource("/Common/UI/Custom/" + path));
        }
        assertNull(InventoryProbeBag.rarityArtForQuality("RPG_Gear_Rare", new SpatialLayout.Size(2, 1)));
        assertNull(InventoryProbeBag.rarityArtForQuality("Common", new SpatialLayout.Size(1, 1)));
        assertEquals("#1d4dff", InventoryProbeBag.rarityColorForQuality("RPG_Gear_Rare"));
        assertEquals("#a000ff", InventoryProbeBag.rarityColorForQuality("RPG_Gear_RandomRare"));
        assertEquals("#51c534", InventoryProbeBag.rarityColorForQuality("RPG_Gear_Set"));
        assertEquals("#ff9100", InventoryProbeBag.rarityColorForQuality("RPG_Gear_Legendary"));
    }

    @Test void appendedNativeGridUsesItsRootIdForSlotUpdates() {
        var renderer = new InventoryProbeBag();
        var commands = new UICommandBuilder();
        renderer.reset(commands);
        assertEquals("#WorkspaceNativeGrid", renderer.nativeGrid(commands, 1));
        renderer.cells(commands);
        renderer.item(commands, entry("bow", 0, 0, 2, 4), "Weapon_Shortbow_Copper", 1);
        assertTrue(Arrays.stream(commands.getCommands()).anyMatch(command ->
                "#Bag[76] #Icon.ItemId".equals(command.selector)));
        assertAnchor(Arrays.stream(commands.getCommands()).filter(command ->
                "#Bag[76] #PoseArt.Anchor".equals(command.selector)).findFirst().orElseThrow(),
                "#Bag[76] #PoseArt.Anchor", 0, 1, 150, 300);
        assertAnchor(Arrays.stream(commands.getCommands()).filter(command ->
                "#Bag[76] #RarityArt.Anchor".equals(command.selector)).findFirst().orElseThrow(),
                "#Bag[76] #RarityArt.Anchor", 0, 1, 150, 300);
    }

    @Test void emptyPartialAndFullBagsUsePackagedDocumentsAndAddressExistingChildren() {
        for (int itemCount : new int[]{0, 3, InventoryGridGeometry.CELLS}) {
            var renderer = new InventoryProbeBag();
            var commands = new UICommandBuilder();
            renderer.reset(commands);
            renderer.cells(commands);
            for (int i = 0; i < itemCount; i++)
                renderer.item(commands, entry("slot" + i, i % 15, i / 15, 1, 1), "Rock_Stone", i + 1);
            var hitSelectors = new HashSet<String>();
            for (int y = 0; y < InventoryGridGeometry.ROWS; y++) for (int x = 0; x < 15; x++)
                assertTrue(hitSelectors.add(renderer.hit(commands, x, y)));
            int children = 0;
            for (var command : commands.getCommands()) {
                assertNotEquals(CustomUICommandType.AppendInline, command.type);
                assertNotEquals(CustomUICommandType.InsertBeforeInline, command.type);
                if (command.type == CustomUICommandType.Append) {
                    assertEquals("#Bag", command.selector);
                    assertNotNull(getClass().getResource("/Common/UI/Custom/" + command.text), command.text);
                    children++;
                } else if (command.type == CustomUICommandType.Set) {
                    int index = Integer.parseInt(command.selector.substring(5, command.selector.indexOf(']')));
                    assertTrue(index >= 0 && index < children, command.selector);
                    assertFalse(command.selector.contains(".Anchor."), command.selector);
                    if (command.selector.endsWith(".Anchor")) {
                        var anchor = BsonDocument.parse(command.data).getDocument("0");
                        for (String field : new String[]{"Left", "Top", "Width", "Height"})
                            assertTrue(anchor.containsKey(field), command.data);
                    }
                }
            }
            assertEquals(InventoryGridGeometry.CELLS * 2 + itemCount, children);
            assertEquals(InventoryGridGeometry.CELLS, hitSelectors.size());
        }
    }

    @Test void moveAndSearchTargetTheCorrectItemAfterRefreshChangesItsIndex() {
        var renderer = new InventoryProbeBag();
        var initial = new UICommandBuilder();
        renderer.reset(initial);
        renderer.cells(initial);
        renderer.item(initial, entry("stone", 0, 0, 1, 1), "Rock_Stone", 12);
        renderer.item(initial, entry("bow", 1, 0, 2, 4), "Weapon_Shortbow_Iron", 1);
        var icon = Arrays.stream(initial.getCommands()).filter(c -> "#Bag[76] #Icon.Anchor".equals(c.selector)).findFirst().orElseThrow();
        assertAnchor(icon, "#Bag[76] #Icon.Anchor", 8, 84, 134, 134);
        var move = new UICommandBuilder();
        renderer.move(move, entry("bow", 10, 0, 2, 4));
        assertAnchor(move.getCommands()[0], "#Bag[76].Anchor", 760, 0, 150, 302);

        var refresh = new UICommandBuilder();
        renderer.reset(refresh);
        renderer.cells(refresh);
        renderer.item(refresh, entry("bow", 0, 0, 2, 4), "Weapon_Shortbow_Iron", 1);
        renderer.dim(refresh, "bow", true);
        renderer.dim(refresh, "bow", false);
        renderer.select(refresh, "bow", true);
        renderer.select(refresh, "bow", false);
        var dims = Arrays.stream(refresh.getCommands()).filter(c -> c.selector.endsWith("#Dim.Visible")).toList();
        assertEquals(2, dims.size());
        assertEquals("#Bag[75] #Dim.Visible", dims.getFirst().selector);
        assertTrue(BsonDocument.parse(dims.getFirst().data).getBoolean("0").getValue());
        assertFalse(BsonDocument.parse(dims.getLast().data).getBoolean("0").getValue());
        var selected = Arrays.stream(refresh.getCommands()).filter(c -> c.selector.endsWith("#Selected.Visible")).toList();
        assertEquals(2, selected.size());
        assertEquals("#Bag[75] #Selected.Visible", selected.getFirst().selector);
        var after = new UICommandBuilder();
        renderer.move(after, entry("bow", 13, 0, 2, 4));
        assertAnchor(after.getCommands()[0], "#Bag[75].Anchor", 988, 0, 150, 302);
    }

    private static void assertAnchor(CustomUICommand command, String selector, int left, int top, int width, int height) {
        assertEquals(CustomUICommandType.Set, command.type);
        assertEquals(selector, command.selector);
        assertEquals(BsonDocument.parse("{Left:" + left + ",Top:" + top + ",Width:" + width + ",Height:" + height + "}"),
                BsonDocument.parse(command.data).getDocument("0"));
    }
}
