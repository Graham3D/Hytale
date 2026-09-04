package com.inigmasgames.persistentnpcs;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import com.inigmasgames.persistentnpcs.profile.NpcInventoryState;
import com.inigmasgames.persistentnpcs.profile.NpcProfileEditorService;

/** Structural safety gate for the R105 production NPC Profile visual overlay. */
public final class R104NpcMeshPreviewProbeSafetyTest {
    private R104NpcMeshPreviewProbeSafetyTest() { }

    public static void main(String[] args) throws Exception {
        String session = read("src/main/java/com/inigmasgames/persistentnpcs/ui/"
                + "NpcMeshPreviewSession.java");
        String page = read("src/main/java/com/inigmasgames/persistentnpcs/ui/"
                + "NpcProfilePage.java");
        String command = read("src/main/java/com/inigmasgames/persistentnpcs/command/"
                + "AbstractImmersiveNpcProfileCommand.java");
        String plugin = read("src/main/java/com/inigmasgames/persistentnpcs/"
                + "PersistentNpcsPlugin.java");
        String productionUi = read("src/main/resources/Common/UI/Custom/Pages/"
                + "ImmersiveNpcProfile.ui");

        assert session.contains("NetworkId.getComponentType()")
                : "Probe must address the authentic viewer network entity";
        assert session.contains("new ModelUpdate(authentic.getModel().toPacket()")
                : "Probe must snapshot the authentic player model baseline";
        assert session.contains("writeNoCache(")
                : "Probe must remain a client-local packet overlay";
        assert session.contains("new EntityUpdates(")
                : "Probe must use the supported entity update protocol";
        assert !session.contains("putComponent(")
                : "Probe must never mutate authoritative ECS visual state";
        assert session.contains("AtomicBoolean closed")
                && session.contains("closed.compareAndSet(false, true)")
                : "Restoration must be idempotent";
        assert session.indexOf("targetApplied = true") < session.indexOf("send(target)")
                : "A partial target write must still be eligible for restoration";
        for (String marker : new String[] {
                "BASELINE_CAPTURED", "PREVIEW_OPENED", "MODEL_UPDATE_SENT",
                "PLAYER_SKIN_UPDATE_SENT", "RESTORATION_SKIN_SENT",
                "EQUIPMENT_UPDATE_SENT", "RESTORATION_EQUIPMENT_SENT",
                "RESTORATION_SENT", "RESTORATION_COMPLETED_ASSUMED",
                "PREVIEW_SESSION_CLOSED", "viewerNetworkId=", "targetModelId="}) {
            assert session.contains(marker) : "Missing probe diagnostic marker " + marker;
        }
        assert command.contains("NpcMeshPreviewSession.begin")
                : "Existing NPC profiles must start the validated visual session";
        assert !command.contains("previewProbeMode")
                : "The development probe selector must not survive production integration";
        assert command.contains("page.onDismiss")
                : "Failed page mounting must restore the baseline";
        assert plugin.contains("NpcMeshPreviewSession.close(playerId)")
                : "Disconnect must close the viewer preview";
        assert plugin.contains("DrainPlayerFromWorldEvent.class")
                : "Leaving a world must close the viewer probe";
        assert count(plugin, "NpcMeshPreviewSession.closeAll()") >= 2
                : "World removal and shutdown must close all previews";
        assert page.contains("applyPreviewAfterPageMount")
                && page.contains("addCleanup(\"viewer-preview-restoration\", this::closePreview)")
                : "The NPC Profile must own apply and restoration lifecycle";
        assert page.indexOf("addCleanup(\"viewer-preview-restoration\", this::closePreview)")
                < page.indexOf("addCleanup(\"inventory-persistence-flush\", inventory::close)")
                : "Preview restoration must run before final persistence teardown";
        assert page.indexOf("authoringSession.close();")
                < page.indexOf("\n                close();")
                : "Explicit close must run idempotent recovery before page dismissal";
        assert session.indexOf("send(target);") < session.indexOf("send(targetSkin);")
                : "Probe B must send the validated skin immediately after the model";
        assert session.indexOf("send(baseline);") < session.indexOf("send(baselineSkin);")
                : "Restoration must restore the authentic model before authentic skin";
        assert session.indexOf("send(targetSkin);") < session.indexOf("send(targetEquipment);")
                : "Probe C must send authoritative equipment after the validated skin";
        assert session.indexOf("send(baselineSkin);") < session.indexOf("send(baselineEquipment);")
                : "Restoration must restore authentic equipment after model and skin";
        assert session.contains("refreshEquipment(EquipmentUpdate equipment)")
                && session.contains("EQUIPMENT_REFRESH_SENT")
                : "Authoritative NPC gear changes must refresh the client-local preview";
        assert page.contains("preview.refreshEquipment")
                : "The profile inventory callback must drive preview equipment refresh";

        NpcInventoryState equipmentState = new NpcInventoryState(
                NpcInventoryState.CURRENT_SCHEMA_VERSION, UUID.randomUUID(),
                List.of(
                        item((short) 0, "Helmet_A"),
                        item((short) 1, "Chest_A"),
                        item((short) 2, "Hands_A"),
                        item((short) 3, "Legs_A")),
                List.of(
                        item((short) 0, "Weapon_A"),
                        item((short) 1, "Shield_A"),
                        item((short) 2, "Arrow_A")),
                List.of(), false, true, false, true, false);
        var visible = NpcProfileEditorService.previewEquipmentFrom(equipmentState);
        assert visible.visibleArmorIds().length == 4;
        assert visible.visibleArmorIds()[0].isEmpty() : "Hidden helmet must not render";
        assert visible.visibleArmorIds()[1].equals("Chest_A");
        assert visible.visibleArmorIds()[2].isEmpty() : "Hidden gauntlets must not render";
        assert visible.visibleArmorIds()[3].equals("Legs_A");
        assert visible.rightHandItemId().equals("Weapon_A");
        assert visible.leftHandItemId().equals("Shield_A");
        assert !java.util.Arrays.asList(visible.visibleArmorIds()).contains("Arrow_A")
                : "Preferred ammunition is policy state, not visible equipment";

        assert productionUi.contains("ItemGrid #ArmorGrid")
                && productionUi.contains("Group #PlayerGridHost")
                : "The working R103 NPC Profile grids must remain intact";
        assert productionUi.contains("CharacterPreviewComponent #NpcCharacterPreview")
                : "The A/B/C-connected-validated sequence must target the production preview";
        assert productionUi.contains("Group #NpcGridHost")
                && productionUi.contains("ItemGrid #LoadoutGrid")
                : "Preview integration must preserve every authoritative NPC grid";

        System.out.println("R105 NPC Profile mesh preview integration tests passed.");
    }

    private static String read(String path) throws Exception {
        return Files.readString(Path.of(path));
    }

    private static int count(String source, String needle) {
        int count = 0;
        int offset = 0;
        while ((offset = source.indexOf(needle, offset)) >= 0) {
            count++;
            offset += needle.length();
        }
        return count;
    }

    private static NpcInventoryState.PersistedItemStack item(short slot, String id) {
        return new NpcInventoryState.PersistedItemStack(
                slot, id, 1, 0.0, 0.0, 0, "{}", false);
    }
}
