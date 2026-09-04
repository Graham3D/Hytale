package com.inigmasgames.persistentnpcs;

import com.inigmasgames.persistentnpcs.authoring.NpcAuthoringEventEnvelope;
import com.inigmasgames.persistentnpcs.authoring.NpcAuthoringPermissions;
import com.inigmasgames.persistentnpcs.authoring.NpcAuthoringSession;
import com.inigmasgames.persistentnpcs.authoring.NpcAuthoringSessionRegistry;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Deterministic A1 gate for leases, envelopes, close semantics, and shell geometry. */
public final class R129NpcAuthoringStudioA1Test {
    private R129NpcAuthoringStudioA1Test() { }

    public static void main(String[] args) throws Exception {
        NpcAuthoringSessionRegistry registry = NpcAuthoringSessionRegistry.shared();
        registry.closeAll();
        UUID viewer = UUID.randomUUID();
        UUID npc = UUID.randomUUID();
        List<String> cleanup = new ArrayList<>();
        NpcAuthoringSession session = registry.acquire(viewer, npc, null,
                Map.of("PROFILE", "A", "INVENTORY", "B"), ignored -> true,
                ignored -> { });
        session.addCleanup("first", () -> cleanup.add("first"));
        session.addCleanup("second", () -> cleanup.add("second"));
        session.ready();
        assert registry.activeSessionCount() == 1;

        NpcAuthoringEventEnvelope valid = new NpcAuthoringEventEnvelope(
                1, session.sessionId(), viewer, npc, session.pageGeneration(),
                NpcAuthoringSession.EditorKind.NONE, session.editorGeneration(), "CANCEL");
        session.validate(valid, Set.of("CANCEL"), NpcAuthoringPermissions.OPEN);

        boolean secondWriterRejected = false;
        try {
            registry.acquire(UUID.randomUUID(), npc, null, Map.of(), ignored -> true,
                    ignored -> { });
        } catch (IllegalStateException expected) {
            secondWriterRejected = true;
        }
        assert secondWriterRejected : "one writer lease per stable NPC must fail closed";

        long priorEditorGeneration = session.editorGeneration();
        session.openEditor(NpcAuthoringSession.EditorKind.PROFILE);
        assert session.state() == NpcAuthoringSession.WorkspaceState.PROFILE_EDIT;
        assert session.editorGeneration() > priorEditorGeneration;
        boolean staleEditorRejected = false;
        try {
            session.validate(valid, Set.of("CANCEL"), NpcAuthoringPermissions.OPEN);
        } catch (IllegalArgumentException expected) {
            staleEditorRejected = true;
        }
        assert staleEditorRejected : "stale editor generation must be rejected";
        session.markDirty(NpcAuthoringSession.DirtyDomain.PROFILE);
        boolean dirtyCloseRejected = false;
        try {
            session.closeEditor(false);
        } catch (IllegalStateException expected) {
            dirtyCloseRejected = true;
        }
        assert dirtyCloseRejected : "dirty navigation must require Save, Discard, or Stay";
        session.markSaved(NpcAuthoringSession.EditorKind.PROFILE);
        assert !session.isDirty(NpcAuthoringSession.EditorKind.PROFILE);
        session.closeEditor(true);

        session.close();
        session.close();
        assert cleanup.equals(List.of("first", "second"));
        assert registry.activeSessionCount() == 0;
        assert session.state() == NpcAuthoringSession.WorkspaceState.CLOSED;

        String page = Files.readString(Path.of(
                "src/main/java/com/inigmasgames/persistentnpcs/ui/NpcProfilePage.java"));
        String bridge = Files.readString(Path.of(
                "src/main/java/com/inigmasgames/persistentnpcs/ui/CustomInventoryTransactionBridge.java"));
        String ui = Files.readString(Path.of(
                "src/main/resources/Common/UI/Custom/Pages/ImmersiveNpcProfile.ui"));
        assert page.contains("NpcAuthoringEventEnvelope")
                && page.contains("authoringSession.validate")
                && page.contains("authoringSession.close()")
                && !page.contains("InventoryUtils.moveItem(ref,");
        assert bridge.contains("moveItemStackFromSlotToSlot")
                : "existing inventory mutation authority must remain intact";
        assert ui.contains("#GearPanel") && ui.contains("#StatsStrip")
                && ui.contains("#ProfileAssetsPanel")
                && ui.contains("#InventoriesPanel")
                && ui.contains("#TransferRail")
                && ui.contains("#ContextEditorPage")
                && ui.contains("#DirtyEditorConfirmPage")
                && ui.contains("#DirtySaveButton")
                && ui.contains("#DirtyDiscardButton")
                && ui.contains("#DirtyStayButton");
        assert ui.contains("Anchor: (Width: 1520, Height: 960)");
        assert ui.contains("SlotSize: 58") && ui.contains("SlotSize: 62");
        assert ui.contains("@Text = \"INVENTORIES\"")
                && ui.contains("Text: \"NPC GEAR & STATS\"");
        assert !ui.contains("Anchor: (Width: 1420, Height: 990)")
                : "legacy giant outer frame must not return";

        System.out.println("R129 NPC Authoring Studio A1 deterministic gate passed.");
    }
}
