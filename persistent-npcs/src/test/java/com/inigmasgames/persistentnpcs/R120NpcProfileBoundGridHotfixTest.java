package com.inigmasgames.persistentnpcs;

import java.nio.file.Files;
import java.nio.file.Path;

/** Regression gate for the client-fatal unbound ItemGrid slot assignment. */
public final class R120NpcProfileBoundGridHotfixTest {
    private R120NpcProfileBoundGridHotfixTest() { }

    public static void main(String[] arguments) throws Exception {
        String page = read("src/main/java/com/inigmasgames/persistentnpcs/ui/NpcProfilePage.java");
        String ui = read("src/main/resources/Common/UI/Custom/Pages/ImmersiveNpcProfile.ui");
        String player = read("src/main/resources/Common/UI/Custom/Pages/ProfileInventory/PlayerStorage.ui");

        assert ui.contains("Group #NpcGridHost");
        assert ui.contains("Group #PlayerGridHost");
        assert !ui.contains("ItemGrid #InventoryGrid")
                : "Production must not construct an unbound NPC ItemGrid";
        assert !ui.contains("ItemGrid #PlayerInventoryGrid")
                : "Production must not construct an unbound Player ItemGrid";

        int pageDocument = page.indexOf("commands.append(\"Pages/ImmersiveNpcProfile.ui\")");
        int npcDocument = page.indexOf("commands.append(\"#NpcGridHost\"");
        int playerDocument = page.indexOf("commands.append(\"#PlayerGridHost\"");
        int snapshots = page.indexOf("setNpcProfileUi(commands);", playerDocument);
        assert pageDocument >= 0 && npcDocument > pageDocument;
        assert playerDocument > npcDocument;
        assert snapshots > playerDocument
                : "Both section-bound grid documents must exist before .Slots";

        assert page.contains("boundNpcGridDocument(storageWindow.getId())");
        assert page.contains("Pages/ProfileInventory/NpcSection");
        assert page.contains("Pages/ProfileInventory/PlayerStorage.ui");
        assert player.contains("InventorySectionId: -2;");
        for (int id = 1; id <= 8; id++) {
            String bound = read("src/main/resources/Common/UI/Custom/Pages/ProfileInventory/NpcSection"
                    + id + ".ui");
            assert bound.contains("InventorySectionId: " + id + ";");
        }

        int method = page.indexOf("private void setNpcProfileUi(");
        int nextMethod = page.indexOf("private void setProfileFilesUi(", method);
        String snapshotMethod = page.substring(method, nextMethod);
        assert !snapshotMethod.contains("#NpcInventoryGrid.InventorySectionId")
                : "Runtime rebind must not replace initial section-bound construction";
        assert !snapshotMethod.contains("#PlayerInventoryGrid.InventorySectionId")
                : "Player Storage must also be bound during construction";
        assert snapshotMethod.contains("#NpcInventoryGrid.Slots");
        assert snapshotMethod.contains("#PlayerInventoryGrid.Slots");

        System.out.println("R120 NPC Profile bound-grid construction hotfix gate passed.");
    }

    private static String read(String path) throws Exception {
        return Files.readString(Path.of(path));
    }
}
