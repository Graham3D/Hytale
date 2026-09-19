package com.inigmasgames.canvasui.rendering;

import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.entities.player.hud.CustomUIHud;
import com.hypixel.hytale.server.core.ui.Anchor;
import com.hypixel.hytale.server.core.ui.PatchStyle;
import com.hypixel.hytale.server.core.ui.Value;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.inigmasgames.canvasui.CanvasUI;
import com.inigmasgames.canvasui.api.Canvas;
import com.inigmasgames.canvasui.api.CanvasEdge;
import com.inigmasgames.canvasui.api.CanvasNode;
import com.inigmasgames.canvasui.api.CanvasPoint;
import com.inigmasgames.canvasui.api.CanvasPort;
import com.inigmasgames.canvasui.api.NodeDefinition;
import com.inigmasgames.canvasui.api.editor.CursorCanvasEditor;
import com.inigmasgames.canvasui.api.editor.TreeLinkGeometry;
import com.inigmasgames.canvasui.api.editor.TreeLinkInteraction;

import javax.annotation.Nonnull;
import java.util.List;

/** Fixed-pool passive HUD renderer for the production cursor graph editor. */
public final class CanvasGraphEditorHud extends CustomUIHud {
    public static final String KEY = "inigmas:canvasui:graph-editor";
    public static final int LIBRARY_ROWS = 6;
    private static final int NODE_POOL = 11;
    private static final int EDGE_SEGMENT_POOL = 96;
    private static final TreeLinkGeometry LINK_GEOMETRY = new TreeLinkGeometry();
    private final String title;

    public CanvasGraphEditorHud(PlayerRef playerRef, String title) {
        super(playerRef, KEY, 860);
        this.title = title == null || title.isBlank() ? "RPG SKILL TREE" : title;
    }

    @Override protected void build(@Nonnull UICommandBuilder commands) {
        commands.append("CanvasGraphEditorHud.ui");
        commands.set("#GraphEditorTitle.TextSpans", Message.raw(title));
        commands.set("#GraphEditorRevision.TextSpans", Message.raw(CanvasUI.REVISION));
    }

    public void render(Canvas canvas, CursorCanvasEditor.LibraryKind tab,
                       List<CursorCanvasEditor.LibraryEntry> page, int pageIndex, int pageCount,
                       String query, String status, DragVisual drag, TreeLinkInteraction links) {
        UICommandBuilder commands = new UICommandBuilder();
        commands.setObject("#GraphEditorSkillsTab.Background", color(tab == CursorCanvasEditor.LibraryKind.SKILL
                ? "#1f6c89ff" : "#10283bea"));
        commands.setObject("#GraphEditorPassivesTab.Background", color(tab == CursorCanvasEditor.LibraryKind.PASSIVE
                ? "#1f6c89ff" : "#10283bea"));
        commands.set("#GraphEditorPage.TextSpans", Message.raw("PAGE " + (pageIndex + 1) + " / " + Math.max(1, pageCount)));
        commands.set("#GraphEditorSearch.TextSpans", Message.raw(query == null || query.isBlank() ? "Search..." : query));
        commands.set("#GraphEditorNoMatches.Visible", page.isEmpty());
        commands.set("#GraphEditorStatus.TextSpans", Message.raw(status == null ? "" : status));
        commands.set("#GraphEditorDrag.TextSpans", Message.raw(drag == null
                ? "Drag a library entry onto a matching node. Drag ports to create links."
                : drag.state() + ": " + drag.entry().name()));
        for (int i = 0; i < LIBRARY_ROWS; i++) {
            boolean visible = i < page.size();
            commands.set("#GraphLibraryRow" + i + ".Visible", visible);
            if (visible) {
                CursorCanvasEditor.LibraryEntry entry = page.get(i);
                commands.set("#GraphLibraryRow" + i + " #Name.TextSpans", Message.raw(entry.name()));
                commands.set("#GraphLibraryRow" + i + " #Category.TextSpans", Message.raw(entry.category()));
                commands.setObject("#GraphLibraryRow" + i + " #Icon.Background", texture(entry.iconPath()));
                commands.setObject("#GraphLibraryRow" + i + ".Background", color(entry.kind() == CursorCanvasEditor.LibraryKind.SKILL
                        ? "#183a56f2" : "#3a2856f2"));
            }
        }
        renderNodes(commands, canvas);
        renderContinuousEdges(commands, canvas, links == null ? null : links.selectedLinkId());
        renderDrag(commands, drag);
        renderContext(commands, links);
        update(false, commands);
    }

    private static void renderNodes(UICommandBuilder commands, Canvas canvas) {
        int index = 0;
        for (CanvasNode node : canvas.nodes()) {
            if (index >= NODE_POOL) break;
            String selector = "#GraphNode" + index;
            NodeDefinition type = canvas.definition().nodeType(node.type());
            CanvasPoint point = canvas.viewport().toScreen(node.position());
            commands.set(selector + ".Visible", true);
            commands.setObject(selector + ".Anchor", anchor((int)Math.round(point.x()), (int)Math.round(point.y()),
                    type.width(), type.height()));
            String body = switch (node.type()) {
                case "skill" -> node.nodeId().equals(canvas.selectedNodeId()) ? "#23608fff" : "#173f60f2";
                case "passive" -> node.nodeId().equals(canvas.selectedNodeId()) ? "#744ca1ff" : "#4c326bf2";
                default -> node.nodeId().equals(canvas.selectedNodeId()) ? "#a26c24ff" : "#6d4a20f2";
            };
            commands.setObject(selector + ".Background", color("joint".equals(node.type()) ? "#00000000" : body));
            commands.set(selector + " #Title.TextSpans", Message.raw(node.metadata().getOrDefault("label", node.nodeId())));
            commands.set(selector + " #Subtitle.TextSpans", Message.raw(node.metadata().getOrDefault("subtitle", node.type())));
            commands.set(selector + " #Triangle.Visible", "joint".equals(node.type()));
            boolean icon = !"joint".equals(node.type()) && Boolean.parseBoolean(node.metadata().getOrDefault("occupied", "false"));
            commands.set(selector + " #Icon.Visible", icon);
            if (icon) commands.setObject(selector + " #Icon.Background", texture(node.metadata().getOrDefault("icon", "")));
            commands.set(selector + " #Port0.Visible", false);
            commands.set(selector + " #Port1.Visible", false);
            commands.set(selector + " #Port2.Visible", false);
            int portIndex = 0;
            for (CanvasPort port : type.ports().values()) {
                if (portIndex >= 3) break;
                commands.set(selector + " #Port" + portIndex + ".Visible", true);
                commands.setObject(selector + " #Port" + portIndex + ".Anchor",
                        anchor((int)Math.round(port.anchorPosition().x()) - 6,
                                (int)Math.round(port.anchorPosition().y()) - 6, 12, 12));
                portIndex++;
            }
            index++;
        }
        while (index < NODE_POOL) commands.set("#GraphNode" + index++ + ".Visible", false);
    }

    private static void renderContinuousEdges(UICommandBuilder commands, Canvas canvas, String selectedLinkId) {
        int index=0;
        for (CanvasEdge edge : canvas.edges()) {
            boolean selected=edge.edgeId().equals(selectedLinkId);
            for(TreeLinkGeometry.Segment segment:LINK_GEOMETRY.route(canvas,edge)){
                if(index>=EDGE_SEGMENT_POOL)break;
                int thickness=selected?5:3;
                int left=(int)Math.round(segment.left()-(segment.width()==0?thickness/2.0:0));
                int top=(int)Math.round(segment.top()-(segment.height()==0?thickness/2.0:0));
                int width=Math.max(thickness,(int)Math.round(segment.width()));
                int height=Math.max(thickness,(int)Math.round(segment.height()));
                commands.set("#GraphEdgeDot"+index+".Visible",true);
                commands.setObject("#GraphEdgeDot"+index+".Anchor",anchor(left,top,width,height));
                commands.setObject("#GraphEdgeDot"+index+".Background",color(selected?"#fff0a0ff":"#74d9eaff"));
                index++;
            }
        }
        while(index<EDGE_SEGMENT_POOL)commands.set("#GraphEdgeDot"+index++ + ".Visible",false);
    }

    private static void renderDrag(UICommandBuilder commands,DragVisual drag){
        commands.set("#GraphDragGhost.Visible",drag!=null);
        if(drag==null)return;
        commands.setObject("#GraphDragGhost.Anchor",anchor((int)Math.round(drag.point().x())-24,(int)Math.round(drag.point().y())-24,48,48));
        commands.setObject("#GraphDragGhost.Background",texture(drag.entry().iconPath()));
    }

    private static void renderContext(UICommandBuilder commands,TreeLinkInteraction links){
        boolean visible=links!=null&&links.contextOpen()&&links.popupAnchor()!=null;
        commands.set("#GraphLinkContext.Visible",visible);
        if(visible)commands.setObject("#GraphLinkContext.Anchor",anchor(
                (int)Math.round(Math.min(690,Math.max(208,links.popupAnchor().x()))),
                (int)Math.round(Math.min(372,Math.max(4,links.popupAnchor().y()))),126,88));
        commands.set("#GraphDeleteLink.Visible",links!=null&&links.selectedLinkId()!=null);
    }

    private static PatchStyle color(String value) { return new PatchStyle().setColor(Value.of(value)); }
    private static PatchStyle texture(String value) { return new PatchStyle().setTexturePath(Value.of(value)); }
    private static Anchor anchor(int left, int top, int width, int height) {
        Anchor anchor = new Anchor();
        anchor.setLeft(Value.of(left)); anchor.setTop(Value.of(top));
        anchor.setWidth(Value.of(width)); anchor.setHeight(Value.of(height));
        return anchor;
    }

    public record DragVisual(CursorCanvasEditor.LibraryEntry entry, CanvasPoint point, String state) { }
}
