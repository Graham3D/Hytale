package com.inigmasgames.canvasui.api.editor;

import com.inigmasgames.canvasui.api.Canvas;
import com.inigmasgames.canvasui.api.CanvasDefinition;
import com.inigmasgames.canvasui.api.CanvasPoint;
import com.inigmasgames.canvasui.api.CanvasPort;
import com.inigmasgames.canvasui.api.EdgeStyle;
import com.inigmasgames.canvasui.api.NodeDefinition;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class TreeEditorInteractionTest {
    private static CursorCanvasEditor.LibraryEntry entry(String id,String name,CursorCanvasEditor.LibraryKind kind){
        return new CursorCanvasEditor.LibraryEntry(id,name,"test","Common/Icons/"+id+".png",kind);
    }

    @Test void searchIsCaseInsensitiveSubstringBeforePaginationAndZeroMatchesIsValid(){
        var source=List.of(entry("a","Lightning Arrow",CursorCanvasEditor.LibraryKind.SKILL),
                entry("b","Fire Bolt",CursorCanvasEditor.LibraryKind.SKILL),
                entry("c","Ball Lightning",CursorCanvasEditor.LibraryKind.SKILL));
        var all=LibraryBrowser.project(source,"",0,2);
        assertEquals(3,all.totalMatches());assertEquals(2,all.pageCount());
        var filtered=LibraryBrowser.project(source,"LIGHT",9,1);
        assertEquals(2,filtered.totalMatches());assertEquals(1,filtered.pageIndex());
        assertEquals("Ball Lightning",filtered.entries().getFirst().name());
        var none=LibraryBrowser.project(source,"frost",0,2);
        assertTrue(none.entries().isEmpty());assertEquals(1,none.pageCount());
    }

    @Test void continuousWindowClampsOffsetAfterSearchAndExposesProportionalScrollbar(){
        var source=List.of(entry("a","Arc",CursorCanvasEditor.LibraryKind.SKILL),
                entry("b","Ball Lightning",CursorCanvasEditor.LibraryKind.SKILL),
                entry("c","Charged Bolt",CursorCanvasEditor.LibraryKind.SKILL),
                entry("d","Fire Bolt",CursorCanvasEditor.LibraryKind.SKILL));
        var bottom=LibraryBrowser.window(source,"",99,2);
        assertEquals(2,bottom.offset());assertEquals(List.of("c","d"),bottom.entries().stream().map(CursorCanvasEditor.LibraryEntry::id).toList());
        assertEquals(0.5,bottom.thumbFraction());assertEquals(1.0,bottom.progress());
        var filtered=LibraryBrowser.window(source,"ball",bottom.offset(),2);
        assertEquals(0,filtered.offset());assertEquals(0,filtered.maximumOffset());
        assertEquals(List.of("b"),filtered.entries().stream().map(CursorCanvasEditor.LibraryEntry::id).toList());
    }

    @Test void dragRequiresThresholdCarriesIconAndUsesBoundedSnapAndReturnStates(){
        var drag=new TreeDragController();var item=entry("bolt","Lightning Bolt",CursorCanvasEditor.LibraryKind.SKILL);
        drag.arm(item,CanvasPoint.of(10,10),CanvasPoint.of(10,10));
        assertEquals(TreeDragController.State.ARMED,drag.state());
        assertFalse(drag.move(CanvasPoint.of(13,13)));
        assertTrue(drag.move(CanvasPoint.of(20,20)));assertSame(item,drag.entry());assertEquals(item.iconPath(),drag.entry().iconPath());
        drag.animateTo(CanvasPoint.of(100,100),true,1_000);
        assertEquals(TreeDragController.State.SNAPPING_TO_TARGET,drag.state());
        assertFalse(drag.completeIfDue(1_100));assertTrue(drag.completeIfDue(1_200));
        assertEquals(TreeDragController.State.IDLE,drag.state());
    }

    @Test void compatibleEmptyNearestNodeWinsWhileWrongOccupiedAndDistantTargetsReject(){
        Canvas canvas=canvas();var resolver=new TreeDropResolver();
        var direct=resolver.resolve(canvas,CursorCanvasEditor.LibraryKind.SKILL,CanvasPoint.of(140,125));
        assertTrue(direct.accepted());assertEquals("skill-a",direct.nodeId());
        assertFalse(resolver.resolve(canvas,CursorCanvasEditor.LibraryKind.PASSIVE,CanvasPoint.of(140,125)).accepted());
        assertFalse(resolver.resolve(canvas,CursorCanvasEditor.LibraryKind.SKILL,CanvasPoint.of(440,125)).accepted());
        assertFalse(resolver.resolve(canvas,CursorCanvasEditor.LibraryKind.SKILL,CanvasPoint.of(700,400)).accepted());
    }

    @Test void continuousEdgeGeometryAnchorsPortsAndHitSelectionDoesNotMutateGraph(){
        Canvas canvas=canvas();canvas.connect("edge-a","passive","out","skill-a","in",EdgeStyle.standard("test"));
        var geometry=new TreeLinkGeometry();var edge=canvas.edge("edge-a");var route=geometry.route(canvas,edge);
        assertFalse(route.isEmpty());assertEquals(TreeLinkGeometry.port(canvas,"passive","out"),route.getFirst().start());
        assertEquals(TreeLinkGeometry.port(canvas,"skill-a","in"),route.getLast().end());
        assertEquals("edge-a",geometry.hit(canvas,CanvasPoint.of(260,127)));
        var state=new TreeLinkInteraction();state.select("edge-a");assertEquals("edge-a",state.selectedLinkId());
        state.openContext("edge-a",CanvasPoint.of(260,127));assertEquals("edge-a",state.contextTargetLinkId());
        state.select("other");assertNull(state.contextTargetLinkId());assertEquals(1,canvas.edges().size());
        state.clear();assertNull(state.selectedLinkId());assertEquals(1,canvas.edges().size());
    }

    @Test void previewAndCommittedLinksUseIdenticalContinuousGeometry(){
        Canvas canvas=canvas();canvas.connect("edge-a","passive","out","skill-a","in",EdgeStyle.standard("test"));
        var geometry=new TreeLinkGeometry();var edge=canvas.edge("edge-a");
        assertEquals(geometry.route(canvas,edge),geometry.route(
                TreeLinkGeometry.port(canvas,"passive","out"),TreeLinkGeometry.port(canvas,"skill-a","in")));
    }

    @Test void commonImmutableViewModelSupportsHudAndReadOnlySearchMode() throws Exception {
        Canvas canvas=canvas();var window=LibraryBrowser.window(List.of(
                entry("bolt","Lightning Bolt",CursorCanvasEditor.LibraryKind.SKILL)),"light",0,10);
        var hud=SkillTreeViewModel.project("SKILL TREE",canvas,CursorCanvasEditor.LibraryKind.SKILL,
                "light",window,null,"Ready",false);
        var search=SkillTreeViewModel.project("SKILL TREE",canvas,CursorCanvasEditor.LibraryKind.SKILL,
                "light",window,null,"Search",true);
        assertEquals(hud.nodes(),search.nodes());assertEquals(hud.entries(),search.entries());
        assertFalse(hud.searchMode());assertTrue(search.searchMode());
        assertThrows(UnsupportedOperationException.class,()->search.entries().clear());
        String editor=Files.readString(Path.of("src/main/resources/Common/UI/Custom/CanvasGraphEditorHud.ui"));
        String searchUi=Files.readString(Path.of("src/main/resources/Common/UI/Custom/CanvasGraphSearchPage.ui"));
        assertTrue(editor.contains("SKILL LIBRARY"));assertTrue(editor.contains("SHAPE YOUR JOURNEY"));
        assertFalse(editor.contains("PREV"));assertFalse(editor.contains("NEXT"));
        assertFalse(editor.toLowerCase().contains("skill points"));
        assertTrue(searchUi.contains("SEARCH MODE"));assertTrue(searchUi.contains("GraphSearchInput"));
    }

    private static Canvas canvas(){
        var skill=NodeDefinition.builder("skill").size(100,50).port(CanvasPort.input("in","rpg",2,0,25)).build();
        var passive=NodeDefinition.builder("passive").size(100,50).port(CanvasPort.output("out","rpg",2,100,25)).build();
        Canvas canvas=new Canvas(CanvasDefinition.builder("tree").pannable(false).zoomable(false)
                .registerNodeType(skill).registerNodeType(passive).build());
        canvas.createNode("skill-a","skill",CanvasPoint.of(100,100),Map.of("occupied","false"));
        canvas.createNode("skill-b","skill",CanvasPoint.of(400,100),Map.of("occupied","true"));
        canvas.createNode("passive","passive",CanvasPoint.of(300,220),Map.of("occupied","false"));
        return canvas;
    }
}
