package com.inigmasgames.persistentnpcs;

import java.nio.file.Files;
import java.nio.file.Path;

/** Deterministic gate for the first connected A2 coupled-inventory candidate. */
public final class R131NpcAuthoringStudioA2InventoryBridgeTest {
    private R131NpcAuthoringStudioA2InventoryBridgeTest() { }

    public static void main(String[] arguments) throws Exception {
        String page = read("src/main/java/com/inigmasgames/persistentnpcs/ui/NpcProfilePage.java");
        String bridge = read("src/main/java/com/inigmasgames/persistentnpcs/ui/CustomInventoryTransactionBridge.java");

        assert page.contains(".append(\"AuthoringEditor\", authoringSession.activeEditor().name())")
                : "Dropped bindings must embed a literal server-owned editor value";
        assert page.contains("Long.toString(authoringSession.editorGeneration())")
                : "Dropped bindings must embed a literal editor generation";
        assert !page.contains(".append(\"AuthoringEditor\", \"#AuthoringEditorValue.Text\")")
                : "Dropped does not resolve arbitrary Text selectors";
        assert page.contains("mouseButton == 2 ? 1 : stack.getQuantity()")
                : "client quantity must remain diagnostic only";
        assert bridge.contains("source.moveItemStackFromSlotToSlot(")
                && bridge.contains("source.swapItems(")
                : "native move/merge and occupied-slot swap paths are required";
        assert bridge.contains("validMove(") && bridge.contains("validSwap(")
                : "every native mutation requires an authoritative post-state proof";
        assert bridge.contains("compatibleMerge")
                && bridge.contains("PARTIAL_STACK_CANNOT_SWAP_OCCUPIED_DESTINATION");
        assert bridge.contains("committedOperationCount=")
                && bridge.contains("rejectedOperationCount=")
                && bridge.contains("staleOperationCount=")
                && bridge.contains("duplicateOperationCount=")
                && bridge.contains("invariantViolationCount=");
        assert bridge.contains("fingerprint(intent, sourceBefore, targetBefore)")
                : "replay identity must include authoritative pre-state";
        assert !bridge.contains("setItemStackForSlot(")
                && !bridge.contains("removeItemStackFromSlot(")
                && !bridge.contains("addItemStack(")
                : "A2 may not implement hand-authored stack arithmetic";

        System.out.println("R131 NPC Authoring Studio A2 inventory bridge gate passed.");
    }

    private static String read(String path) throws Exception {
        return Files.readString(Path.of(path));
    }
}
