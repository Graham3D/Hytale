package com.inigmasgames.hytalerpg;

import com.inigmasgames.canvasui.api.Canvas;
import com.inigmasgames.hytalerpg.ui.skilltree.RpgCanvasSkillTreeEditor;
import com.inigmasgames.hytalerpg.ui.skilltree.RpgSkillTreeMutationService;
import com.inigmasgames.hytalerpg.ui.skilltree.RpgSkillTreeProjectionService;
import com.inigmasgames.hytalerpg.ui.skilltree.StaticSkillTreeLayout;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class R045CanvasSkillTreeEditorTest {
    @Test void libraryDropsAreTypedAndPersistThroughTheAuthoritativeRpgMutation() {
        var bundle = Stage01BTestSupport.bundle();
        var layout = new StaticSkillTreeLayout();
        var mutations = new RpgSkillTreeMutationService(bundle.service(), layout);
        var projection = new RpgSkillTreeProjectionService(bundle.catalog(), bundle.service(), layout, true);
        UUID player = UUID.randomUUID();
        var editor = new RpgCanvasSkillTreeEditor(player, projection, mutations);

        var wrong = editor.assign("fire_bolt", "passive01", editor.canvas().snapshot());
        assertFalse(wrong.accepted());
        assertTrue(bundle.service().getPresentationView(player).state().skill(
                com.inigmasgames.hytalerpg.domain.SkillSlot.SKILL01).isEmpty());

        var skill = editor.assign("fire_bolt", "skill01", editor.canvas().snapshot());
        assertTrue(skill.accepted(), skill.message());
        editor.canvas().restore(skill.authoritativeSnapshot());
        assertEquals("Fire Bolt", editor.canvas().node("skill01").metadata().get("label"));
        assertEquals("fire_bolt", bundle.service().getPresentationView(player).state().skill(
                com.inigmasgames.hytalerpg.domain.SkillSlot.SKILL01).orElseThrow().value());
    }

    @Test void parentingPersistsAndTriangularJointRejectsAFourthNeighbor() {
        var bundle = Stage01BTestSupport.bundle();
        var layout = new StaticSkillTreeLayout();
        var mutations = new RpgSkillTreeMutationService(bundle.service(), layout);
        var projection = new RpgSkillTreeProjectionService(bundle.catalog(), bundle.service(), layout, true);
        UUID player = UUID.randomUUID();
        var editor = new RpgCanvasSkillTreeEditor(player, projection, mutations);
        assign(editor, "fire_bolt", "skill01");
        assign(editor, "potency", "passive01");
        assign(editor, "efficiency", "passive02");
        assign(editor, "fork", "passive03");

        connect(editor, "joint01", "a", "skill01", "in", true);
        connect(editor, "passive01", "out", "joint01", "b", true);
        connect(editor, "passive02", "out", "joint01", "c", true);

        // An occupied visual side permits a temporary atomic-reparent candidate, but authority rejects degree four.
        Canvas canvas = editor.canvas();
        canvas.connect("candidate-fourth", "passive03", "out", "joint01", "b",
                com.inigmasgames.canvasui.api.EdgeStyle.standard("test"));
        var rejected = editor.commit(canvas.snapshot());
        assertFalse(rejected.accepted());
        assertTrue(rejected.message().contains("at most three"));
        canvas.restore(rejected.authoritativeSnapshot());
        assertEquals(3, canvas.edges().size());
        assertEquals(3, bundle.service().getPresentationView(player).state().linkEdges().size());
    }

    @Test void movingANodeIsPresentationOnlyAndRetainsAuthoritativeTopology() {
        var bundle = Stage01BTestSupport.bundle();
        var layout = new StaticSkillTreeLayout();
        UUID player = UUID.randomUUID();
        var editor = new RpgCanvasSkillTreeEditor(player,
                new RpgSkillTreeProjectionService(bundle.catalog(), bundle.service(), layout, true),
                new RpgSkillTreeMutationService(bundle.service(), layout));
        editor.canvas().moveNode("skill01", com.inigmasgames.canvasui.api.CanvasPoint.of(610, 90));
        var result = editor.commit(editor.canvas().snapshot());
        assertTrue(result.accepted());
        assertEquals(610.0, result.authoritativeSnapshot().nodes().stream()
                .filter(node -> node.nodeId().equals("skill01")).findFirst().orElseThrow().x());
    }

    private static void assign(RpgCanvasSkillTreeEditor editor, String content, String node) {
        var result = editor.assign(content, node, editor.canvas().snapshot());
        assertTrue(result.accepted(), result.message());
        editor.canvas().restore(result.authoritativeSnapshot());
    }

    private static void connect(RpgCanvasSkillTreeEditor editor, String source, String sourcePort,
                                String target, String targetPort, boolean expected) {
        Canvas canvas = editor.canvas();
        canvas.connect(source, sourcePort, target, targetPort);
        var result = editor.commit(canvas.snapshot());
        assertEquals(expected, result.accepted(), result.message());
        canvas.restore(result.authoritativeSnapshot());
    }
}
