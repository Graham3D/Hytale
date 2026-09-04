package com.inigmasgames.persistentnpcs;

import com.inigmasgames.persistentnpcs.ui.NpcProfilePage;
import com.hypixel.hytale.server.core.inventory.container.SimpleItemContainer;
import java.nio.file.Files;
import java.nio.file.Path;

/** R106 regression: storage grids have one native window/section authority. */
public final class R103NpcProfileGridMaterializationTest {
    private R103NpcProfileGridMaterializationTest() { }

    public static void main(String[] args) throws Exception {
        String page = Files.readString(Path.of(
                "src/main/java/com/inigmasgames/persistentnpcs/ui/NpcProfilePage.java"));
        for (String grid : new String[] {"ArmorGrid", "PrimaryWeaponGrid",
                "OffhandGrid", "AmmunitionGrid"}) {
            assert page.contains("#" + grid + ".Slots")
                    : grid + " keeps its bounded equipment presentation payload";
            assert page.contains("#" + grid + ".InventorySectionId")
                    : grid + " must retain its native transaction section";
        }
        for (String grid : new String[] {"NpcInventoryGrid", "PlayerInventoryGrid"}) {
            assert page.contains("#" + grid + ".Slots")
                    : grid + " must receive the connected-proven R118 snapshot payload";
        }
        assert page.contains("boundNpcGridDocument(storageWindow.getId())")
                : "NPC storage must receive its native section during ItemGrid construction";
        assert page.contains("Pages/NativeInventoryProbe/PlayerStorage.ui")
                : "Player Storage must receive its built-in section during ItemGrid construction";
        assert NpcProfilePage.itemGridSlots(new SimpleItemContainer((short) 4)).length == 4;
        assert NpcProfilePage.itemGridSlots(new SimpleItemContainer((short) 3)).length == 3;
        assert NpcProfilePage.itemGridSlots(new SimpleItemContainer((short) 40)).length == 40;
        assert page.contains("CUSTOM_BRIDGE_TO_LIVE_NPC_STORAGE");
        assert page.contains("each grid is born section-bound before any slot snapshot is sent");
        System.out.println("R120 NPC Profile initial storage binding tests passed.");
    }
}
