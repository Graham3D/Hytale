package com.inigmasgames.hytalerpg;

import com.inigmasgames.canvasui.api.Canvas;
import com.inigmasgames.hytalerpg.ui.skilltree.RpgCanvasSkillTreeEditor;
import com.inigmasgames.hytalerpg.ui.skilltree.RpgSkillTreeMutationService;
import com.inigmasgames.hytalerpg.ui.skilltree.RpgSkillTreeProjectionService;
import com.inigmasgames.hytalerpg.ui.skilltree.StaticSkillTreeLayout;
import com.inigmasgames.hytalerpg.ui.skilltree.SkillTreePortBindingStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class R045CanvasSkillTreeEditorTest {
    @TempDir Path temporary;
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
        assertEquals("Fire Bolt [E]", editor.canvas().node("skill01").metadata().get("label"));
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

    @Test void skillToSkillTopologyIsNeverPresentedAsCompatible() {
        var bundle=Stage01BTestSupport.bundle();var layout=new StaticSkillTreeLayout();
        var editor=new RpgCanvasSkillTreeEditor(UUID.randomUUID(),
                new RpgSkillTreeProjectionService(bundle.catalog(),bundle.service(),layout,true),
                new RpgSkillTreeMutationService(bundle.service(),layout));
        var result=editor.canvas().validateConnection("skill01","in","skill02","in");
        assertFalse(result.allowed());
    }

    @Test void occupiedAssignmentsReplaceAtomicallyAndExactEdgeBreakPreservesNodesAndOtherEdges() {
        var bundle=Stage01BTestSupport.bundle();var layout=new StaticSkillTreeLayout();UUID player=UUID.randomUUID();
        var editor=new RpgCanvasSkillTreeEditor(player,new RpgSkillTreeProjectionService(bundle.catalog(),bundle.service(),layout,true),
                new RpgSkillTreeMutationService(bundle.service(),layout));
        assign(editor,"fire_bolt","skill01");
        var replacement=editor.assign("quick_slash","skill01",editor.canvas().snapshot());
        assertTrue(replacement.accepted(),replacement.message());editor.canvas().restore(replacement.authoritativeSnapshot());
        assertEquals("Quick Slash [E]",editor.canvas().node("skill01").metadata().get("label"));
        var unbound=editor.assign("fire_bolt","skill03",editor.canvas().snapshot());
        assertTrue(unbound.accepted(),unbound.message());assertTrue(unbound.message().contains("stored unbound"));
        editor.canvas().restore(unbound.authoritativeSnapshot());
        var unboundReplacement=editor.assign("quick_slash","skill03",editor.canvas().snapshot());
        assertTrue(unboundReplacement.accepted(),unboundReplacement.message());
        editor.canvas().restore(unboundReplacement.authoritativeSnapshot());
        assertEquals("Quick Slash [UNBOUND]",editor.canvas().node("skill03").metadata().get("label"));
        assign(editor,"potency","passive01");assign(editor,"efficiency","passive02");
        connect(editor,"passive01","out","skill01","in",true);
        String first=editor.canvas().edges().iterator().next().edgeId();
        connect(editor,"passive02","out","skill01","in",true);
        String other=editor.canvas().edges().stream().map(com.inigmasgames.canvasui.api.CanvasEdge::edgeId)
                .filter(id->!id.equals(first)).findFirst().orElseThrow();
        var broken=editor.breakLink(first,editor.canvas().snapshot());assertTrue(broken.accepted(),broken.message());
        editor.canvas().restore(broken.authoritativeSnapshot());
        assertNull(editor.canvas().edge(first));assertNotNull(editor.canvas().edge(other));
        assertEquals("Quick Slash [E]",editor.canvas().node("skill01").metadata().get("label"));
        assertEquals("Potency",editor.canvas().node("passive01").metadata().get("label"));
    }

    @Test void exactJointPortsSurviveRedrawReopenAndUnrelatedMutation(){
        var bundle=Stage01BTestSupport.bundle();var layout=new StaticSkillTreeLayout();UUID player=UUID.randomUUID();
        var mutations=new RpgSkillTreeMutationService(bundle.service(),layout);
        var projection=new RpgSkillTreeProjectionService(bundle.catalog(),bundle.service(),layout,true);
        var bindings=new SkillTreePortBindingStore(temporary.resolve("ports"));
        var editor=new RpgCanvasSkillTreeEditor(player,projection,mutations,bindings);
        assign(editor,"fire_bolt","skill01");assign(editor,"potency","passive01");
        connect(editor,"joint01","c","skill01","in",true);
        connect(editor,"passive01","out","joint01","a",true);
        var expected=editor.canvas().edges().stream().collect(java.util.stream.Collectors.toMap(
                com.inigmasgames.canvasui.api.CanvasEdge::edgeId,
                edge->edge.sourcePortId()+"->"+edge.targetPortId()));
        editor.canvas().moveNode("passive02",com.inigmasgames.canvasui.api.CanvasPoint.of(340,420));
        editor.canvas().restore(editor.commit(editor.canvas().snapshot()).authoritativeSnapshot());
        var reopened=new RpgCanvasSkillTreeEditor(player,projection,mutations,bindings);
        var actual=reopened.canvas().edges().stream().collect(java.util.stream.Collectors.toMap(
                com.inigmasgames.canvasui.api.CanvasEdge::edgeId,
                edge->edge.sourcePortId()+"->"+edge.targetPortId()));
        assertEquals(expected,actual);
        assertTrue(actual.values().contains("c->in"));assertTrue(actual.values().contains("out->a"));
    }

    @Test void clearingOccupiedSkillPreservesTopologyAndInspectorIsStructured(){
        var bundle=Stage01BTestSupport.bundle();var layout=new StaticSkillTreeLayout();UUID player=UUID.randomUUID();
        var editor=new RpgCanvasSkillTreeEditor(player,new RpgSkillTreeProjectionService(bundle.catalog(),bundle.service(),layout,true),
                new RpgSkillTreeMutationService(bundle.service(),layout),new SkillTreePortBindingStore(temporary.resolve("clear-ports")));
        assign(editor,"fire_bolt","skill01");assign(editor,"potency","passive01");
        connect(editor,"passive01","out","skill01","in",true);int edges=editor.canvas().edges().size();
        var details=editor.inspect("fire_bolt","");
        assertEquals("SKILL",details.kind());assertEquals("Fire Bolt",details.name());
        assertEquals(java.util.List.of("RESOURCE","COOLDOWN","RANGE","DAMAGE","REQUIRES","LINKED PASSIVES"),
                details.rows().stream().map(com.inigmasgames.canvasui.api.editor.CursorCanvasEditor.DetailRow::label).toList());
        var cleared=editor.clearNode("skill01",editor.canvas().snapshot());assertTrue(cleared.accepted(),cleared.message());
        editor.canvas().restore(cleared.authoritativeSnapshot());
        assertFalse(Boolean.parseBoolean(editor.canvas().node("skill01").metadata().get("occupied")));
        assertEquals(edges,editor.canvas().edges().size());
        String preservedPort=editor.canvas().edges().iterator().next().sourcePortId()+"->"+editor.canvas().edges().iterator().next().targetPortId();
        assign(editor,"fire_bolt","skill01");
        assertEquals(edges,editor.canvas().edges().size());
        assertEquals(preservedPort,editor.canvas().edges().iterator().next().sourcePortId()+"->"+editor.canvas().edges().iterator().next().targetPortId());
        assertFalse(editor.clearNode("passive01",editor.canvas().snapshot()).accepted());
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
