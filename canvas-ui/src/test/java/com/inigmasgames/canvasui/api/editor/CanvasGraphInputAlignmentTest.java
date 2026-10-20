package com.inigmasgames.canvasui.api.editor;

import com.inigmasgames.canvasui.rendering.CanvasGraphEditorHud;
import com.inigmasgames.canvasui.runtime.cursor.CanvasPointerTransform;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CanvasGraphInputAlignmentTest {
    @Test void staleLandmarkFitDoesNotOpenAtAnotherDisplaySize() {
        var saved = new CanvasPointerTransform.Calibration(
                2.0 * 60 / 1920 - 1, 2.0 * 890 / 1920 - 1,
                2.0 * 116 / 1080 - 1, 2.0 * 586 / 1080 - 1, 0, 0);
        var transform = new CanvasPointerTransform(saved);
        assertTrue(transform.matchesViewport(1920, 1080));
        assertFalse(transform.matchesViewport(2560, 1440));
        assertEquals(114, CanvasGraphEditorHud.WORKSPACE_LEFT);
        assertEquals(128, CanvasGraphEditorHud.WORKSPACE_TOP);
        assertEquals(230, CanvasGraphEditorHud.LIBRARY_TRACK_LEFT);
    }

    @Test void everyGraphNodeHasAFourthRenderedPort() throws Exception {
        try (var input = getClass().getResourceAsStream("/Common/UI/Custom/CanvasGraphEditorHud.ui")) {
            assertNotNull(input);
            String document = new String(input.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
            assertEquals(11, document.split("Group #Port3 \\{", -1).length - 1);
        }
    }
}
