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

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.List;

/** Fixed-pool passive HUD renderer for the production cursor graph editor. */
public final class CanvasGraphEditorHud extends CustomUIHud {
    public static final String KEY = "inigmas:canvasui:graph-editor";
    public static final int LIBRARY_ROWS = 7;
    private static final int NODE_POOL = 11;
    private static final int EDGE_DOT_POOL = 96;
    private static final int DOTS_PER_EDGE = 12;
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
                       String status, String draggingEntry) {
        UICommandBuilder commands = new UICommandBuilder();
        commands.setObject("#GraphEditorSkillsTab.Background", color(tab == CursorCanvasEditor.LibraryKind.SKILL
                ? "#1f6c89ff" : "#10283bea"));
        commands.setObject("#GraphEditorPassivesTab.Background", color(tab == CursorCanvasEditor.LibraryKind.PASSIVE
                ? "#1f6c89ff" : "#10283bea"));
        commands.set("#GraphEditorPage.TextSpans", Message.raw("PAGE " + (pageIndex + 1) + " / " + Math.max(1, pageCount)));
        commands.set("#GraphEditorStatus.TextSpans", Message.raw(status == null ? "" : status));
        commands.set("#GraphEditorDrag.TextSpans", Message.raw(draggingEntry == null || draggingEntry.isBlank()
                ? "Drag a library entry onto a matching node. Drag ports to create links."
                : "DRAGGING: " + draggingEntry));
        for (int i = 0; i < LIBRARY_ROWS; i++) {
            boolean visible = i < page.size();
            commands.set("#GraphLibraryRow" + i + ".Visible", visible);
            if (visible) {
                CursorCanvasEditor.LibraryEntry entry = page.get(i);
                commands.set("#GraphLibraryRow" + i + " #Name.TextSpans", Message.raw(entry.name()));
                commands.set("#GraphLibraryRow" + i + " #Category.TextSpans", Message.raw(entry.category()));
                commands.setObject("#GraphLibraryRow" + i + ".Background", color(entry.kind() == CursorCanvasEditor.LibraryKind.SKILL
                        ? "#183a56f2" : "#3a2856f2"));
            }
        }
        renderNodes(commands, canvas);
        renderSplineDots(commands, canvas);
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

    private static void renderSplineDots(UICommandBuilder commands, Canvas canvas) {
        List<CanvasPoint> dots = new ArrayList<>();
        for (CanvasEdge edge : canvas.edges()) {
            CanvasPoint a = port(canvas, edge.sourceNodeId(), edge.sourcePortId());
            CanvasPoint b = port(canvas, edge.targetNodeId(), edge.targetPortId());
            double bend = Math.max(46.0, Math.abs(b.x() - a.x()) * 0.42);
            CanvasPoint c1 = CanvasPoint.of(a.x() + bend, a.y());
            CanvasPoint c2 = CanvasPoint.of(b.x() - bend, b.y());
            for (int i = 0; i < DOTS_PER_EDGE && dots.size() < EDGE_DOT_POOL; i++) {
                double t = i / (double)(DOTS_PER_EDGE - 1);
                double u = 1.0 - t;
                dots.add(CanvasPoint.of(
                        u*u*u*a.x() + 3*u*u*t*c1.x() + 3*u*t*t*c2.x() + t*t*t*b.x(),
                        u*u*u*a.y() + 3*u*u*t*c1.y() + 3*u*t*t*c2.y() + t*t*t*b.y()));
            }
        }
        for (int i = 0; i < EDGE_DOT_POOL; i++) {
            boolean visible = i < dots.size();
            commands.set("#GraphEdgeDot" + i + ".Visible", visible);
            if (visible) commands.setObject("#GraphEdgeDot" + i + ".Anchor",
                    anchor((int)Math.round(dots.get(i).x()) - 2, (int)Math.round(dots.get(i).y()) - 2, 5, 5));
        }
    }

    private static CanvasPoint port(Canvas canvas, String nodeId, String portId) {
        CanvasNode node = canvas.node(nodeId);
        CanvasPort port = canvas.definition().nodeType(node.type()).port(portId);
        return canvas.viewport().toScreen(node.position().add(port.anchorPosition().x(), port.anchorPosition().y()));
    }

    private static PatchStyle color(String value) { return new PatchStyle().setColor(Value.of(value)); }
    private static Anchor anchor(int left, int top, int width, int height) {
        Anchor anchor = new Anchor();
        anchor.setLeft(Value.of(left)); anchor.setTop(Value.of(top));
        anchor.setWidth(Value.of(width)); anchor.setHeight(Value.of(height));
        return anchor;
    }
}
