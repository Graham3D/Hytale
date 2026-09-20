package com.inigmasgames.canvasui.api.editor;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SkillTreeVisualPolishTest {
    private static final Path UI = Path.of("src/main/resources/Common/UI/Custom/CanvasGraphEditorHud.ui");
    private static final Path RENDERER = Path.of("src/main/java/com/inigmasgames/canvasui/rendering/CanvasGraphEditorHud.java");
    private static final Path SERVICE = Path.of("src/main/java/com/inigmasgames/canvasui/runtime/cursor/CursorHudProbeService.java");

    @Test void fourDecorativeFramesAndFooterActionsMatchTheApprovedComposition() throws Exception {
        String ui = Files.readString(UI);
        assertTrue(ui.contains("#GraphEditorSurface"));
        assertTrue(ui.contains("#GraphLibraryFrame"));
        assertTrue(ui.contains("#GraphTreeFrame"));
        assertTrue(ui.contains("#GraphInspectorPanel"));
        assertTrue(ui.contains("#GraphLibraryFrame { Anchor: (Left: 0, Top: 0, Width: 252"));
        assertTrue(ui.contains("#GraphInspectorPanel { Anchor: (Right: 0, Top: 0, Width: 252"));
        assertTrue(ui.contains("#GraphEditorSave"));
        assertTrue(ui.contains("Text: \"SAVE\""));
        assertTrue(ui.contains("#GraphEditorExit"));
        assertTrue(ui.contains("Text: \"EXIT\""));
        assertFalse(ui.contains("#GraphEditorClose"));
        assertFalse(ui.contains("Text: \"CLOSE\""));
        assertFalse(ui.contains("Text: \"SKILL LIBRARY\""));
        assertFalse(ui.contains("Tutorial"));
        assertFalse(ui.contains("Import"));
        assertFalse(ui.contains("Export"));
    }

    @Test void customTextureUrisAreClientRelativeAndSlotMappingIsNotReversed() throws Exception {
        String ui = Files.readString(UI);
        String renderer = Files.readString(RENDERER);
        assertFalse(ui.contains("Common/UI/Custom/Assets/SkillTree"));
        assertFalse(renderer.contains("Common/UI/Custom/Assets/SkillTree"));
        assertTrue(renderer.contains("private static final String SKILL_TREE_ASSETS = \"Assets/SkillTree/\""));
        assertTrue(renderer.contains("node.occupied()?SKILL_TREE_ASSETS + \"Slot@2x.png\""));
        assertTrue(renderer.contains(":SKILL_TREE_ASSETS + \"SpecialSlotTemporary@2x.png\""));
        assertTrue(renderer.contains("SKILL_TREE_ASSETS + \"skilltree_joint.png\""));
        assertTrue(renderer.contains("SKILL_TREE_ASSETS + \"StructuralCraftingArrowUp@2x.png\""));
        assertTrue(ui.contains("Assets/SkillTree/Hytale/ContainerFullPatch@2x.png"));
        assertTrue(ui.contains("Assets/SkillTree/Hytale/DiagramCraftingBackground@2x.png"));
        assertTrue(ui.contains("Assets/SkillTree/Hytale/Buttons/Primary@2x.png"));
        assertTrue(ui.contains("Assets/SkillTree/Hytale/Buttons/Destructive@2x.png"));
    }

    @Test void saveUsesExistingCommitAuthorityAndExitUsesTheVisibleFooterRegion() throws Exception {
        String service = Files.readString(SERVICE);
        assertTrue(service.contains("context == Context.GRAPH_EDITOR && saveHit(sample.x(), sample.y())"));
        assertTrue(service.contains("persistGraph();"));
        assertTrue(service.contains("VISIBLE_EXIT_REGION"));
        assertTrue(service.contains("private boolean saveHit"));
    }
}
