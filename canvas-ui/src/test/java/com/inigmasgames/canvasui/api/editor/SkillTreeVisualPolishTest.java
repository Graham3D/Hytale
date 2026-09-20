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
        assertTrue(ui.contains("#GraphTreeBackground"));
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
        assertFalse(ui.contains("HYWIND RPG"));
        assertFalse(ui.contains("SHAPE YOUR JOURNEY"));
        assertFalse(ui.contains("#GraphEditorRevision"));
        assertFalse(ui.contains("#GraphEditorStatus"));
        assertFalse(ui.contains("#GraphInspectorFooter"));
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
        assertTrue(renderer.contains("node.occupied()?SKILL_TREE_ASSETS + \"Slot.png\""));
        assertTrue(renderer.contains(":SKILL_TREE_ASSETS + \"SpecialSlotTemporary.png\""));
        assertTrue(renderer.contains("SKILL_TREE_ASSETS + \"skilltree_joint.png\""));
        assertTrue(renderer.contains("SKILL_TREE_ASSETS + \"StructuralCraftingArrowUp.png\""));
        assertTrue(ui.contains("Assets/SkillTree/Hytale/ContainerFullPatch.png"));
        assertTrue(ui.contains("Assets/SkillTree/Hytale/DiagramCraftingBackground.png"));
        assertTrue(ui.contains("Anchor: (Width: 236, Top: 4, Height: 11)"));
        assertTrue(ui.contains("Anchor: (Width: 236, Bottom: 4, Height: 11)"));
        assertFalse(ui.contains("Anchor: (Horizontal: 236"));
        assertTrue(ui.contains("Assets/SkillTree/Hytale/Buttons/Primary.png"));
        assertTrue(ui.contains("Assets/SkillTree/Hytale/Buttons/Destructive.png"));
        assertFalse(ui.contains("@2x.png"));
        assertFalse(renderer.contains("@2x.png"));
    }

    @Test void libraryInspectorSearchAndTreeBoundsMatchThePolishContract() throws Exception {
        String ui = Files.readString(UI);
        String renderer = Files.readString(RENDERER);
        String service = Files.readString(SERVICE);
        String search = Files.readString(Path.of("src/main/resources/Common/UI/Custom/CanvasGraphSearchPage.ui"));
        assertTrue(renderer.contains("LIBRARY_ROWS = 13"));
        assertTrue(ui.contains("#GraphLibraryRow12"));
        assertTrue(ui.contains("Top: 652"));
        assertTrue(ui.contains("#GraphInspectorRow5"));
        assertTrue(ui.contains("Assets/SkillTree/Hytale/Divider.png"));
        assertTrue(service.contains("this::constrainEditorNode"));
        assertTrue(service.contains("CanvasGraphEditorHud.TREE_LEFT"));
        assertTrue(search.contains("only the"));
        assertFalse(search.contains("GraphSearchReadOnlyShade"));
        assertFalse(search.contains("GraphSearchSkills"));
    }

    @Test void saveUsesExistingCommitAuthorityAndExitUsesTheVisibleFooterRegion() throws Exception {
        String service = Files.readString(SERVICE);
        assertTrue(service.contains("context == Context.GRAPH_EDITOR && saveHit(sample.x(), sample.y())"));
        assertTrue(service.contains("persistGraph();"));
        assertTrue(service.contains("VISIBLE_EXIT_REGION"));
        assertTrue(service.contains("private boolean saveHit"));
    }
}
