package com.inigmasgames.canvasui.rendering;

import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.entities.player.hud.CustomUIHud;
import com.hypixel.hytale.server.core.ui.Anchor;
import com.hypixel.hytale.server.core.ui.Value;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.inigmasgames.canvasui.CanvasUI;
import com.inigmasgames.canvasui.api.Canvas;
import com.inigmasgames.canvasui.api.CanvasNode;
import com.inigmasgames.canvasui.api.CanvasPoint;
import com.inigmasgames.canvasui.api.NodeDefinition;

import javax.annotation.Nonnull;

/** Passive keyed HUD used only by the cursor-camera feasibility probe. */
public final class CanvasCursorProbeHud extends CustomUIHud {
    public static final String KEY = "inigmas:canvasui:cursor-probe";
    private final String context;

    public CanvasCursorProbeHud(PlayerRef playerRef, String context) {
        super(playerRef, KEY, 850);
        this.context = context;
    }

    public record Status(double rawX, double rawY, Double normalizedX, Double normalizedY,
                         Double viewportX, Double viewportY, Double localX, Double localY,
                         boolean markerVisible, int markerLeft, int markerTop,
                         long pointerEvents, long gameplayObserved, long gameplayGuarded,
                         long gameplayAllowed, long queueDrops, long traceDrops,
                         String mapping, String calibrationPrompt, String last) { }

    @Override protected void build(@Nonnull UICommandBuilder commands) {
        commands.append("CanvasCursorProbeHud.ui");
        writeIdentity(commands, context);
    }

    public void refresh(Status status) {
        UICommandBuilder commands = new UICommandBuilder();
        writeStatus(commands, status);
        update(false, commands);
    }

    static void writeIdentity(UICommandBuilder commands, String context) {
        commands.set("#CursorProbeRevision.TextSpans", Message.raw(CanvasUI.REVISION));
        commands.set("#CursorProbeContext.TextSpans", Message.raw(context));
        commands.set("#CursorProbeControlBanner.TextSpans", Message.raw(
                "CURSOR_CAMERA_CUSTOM_PAGE_CONTROL".equals(context)
                        ? "CUSTOMUI_PAGE_NEGATIVE_CONTROL" : "CURSOR-HUD INPUT BACKEND"));
        commands.set("#CursorProbeCalibration.Visible", "CURSOR_CAMERA_PASSIVE_HUD".equals(context));
    }

    static void writeStatus(UICommandBuilder commands, Status status) {
        commands.set("#CursorProbePointer.Visible", status.markerVisible());
        if (status.markerVisible()) commands.setObject("#CursorProbePointer.Anchor",
                anchor(status.markerLeft(), status.markerTop(), 14, 14));
        commands.set("#CursorProbeCoordinates.TextSpans", Message.raw(coordinates(status)));
        commands.set("#CursorProbePointerCount.TextSpans", Message.raw("POINTER EVENTS " + status.pointerEvents()));
        commands.set("#CursorProbeGameplayObserved.TextSpans", Message.raw("GAMEPLAY OBSERVED " + status.gameplayObserved()));
        commands.set("#CursorProbeGameplayGuarded.TextSpans", Message.raw("GUARDED " + status.gameplayGuarded()));
        commands.set("#CursorProbeGameplayAllowed.TextSpans", Message.raw("ALLOWED " + status.gameplayAllowed()));
        commands.set("#CursorProbeDropCount.TextSpans", Message.raw(
                "QUEUE/TRACE DROP " + status.queueDrops() + "/" + status.traceDrops()));
        commands.set("#CursorProbeMapping.TextSpans", Message.raw(status.mapping()));
        commands.set("#CursorProbeCalibrationPrompt.TextSpans", Message.raw(status.calibrationPrompt()));
        commands.set("#CursorProbeCalibration.Visible", status.calibrationPrompt() != null
                && !status.calibrationPrompt().isBlank());
        commands.set("#CursorProbeLast.TextSpans", Message.raw(status.last()));
    }

    public void setDragProofVisible(boolean visible) {
        UICommandBuilder commands = new UICommandBuilder();
        commands.set("#CursorDragProof.Visible", visible);
        if (visible) commands.set("#CursorProbeCalibration.Visible", false);
        update(false, commands);
    }

    public void updateGraph(Canvas canvas) {
        UICommandBuilder commands = new UICommandBuilder();
        CanvasRenderer renderer = new CanvasRenderer(canvas.definition().edgeRenderer());
        writeNode(commands, canvas, renderer, "proof-a", "#CursorDragNodeA");
        writeNode(commands, canvas, renderer, "proof-b", "#CursorDragNodeB");
        java.util.List<EdgeSegment> segments = canvas.edge("proof-edge") == null ? java.util.List.of()
                : renderer.edgeSegments(canvas, canvas.edge("proof-edge"));
        for (int i = 0; i < 4; i++) {
            commands.set("#CursorDragEdge" + i + ".Visible", i < segments.size());
            if (i < segments.size()) commands.setObject("#CursorDragEdge" + i + ".Anchor",
                    segmentAnchor(segments.get(i), 3));
        }
        update(false, commands);
    }

    public void graphStatus(String value) {
        UICommandBuilder commands = new UICommandBuilder();
        commands.set("#CursorDragStatus.TextSpans", Message.raw(value == null ? "" : value));
        update(false, commands);
    }

    private static void writeNode(UICommandBuilder commands, Canvas canvas, CanvasRenderer renderer,
                                  String nodeId, String selector) {
        CanvasNode node = canvas.node(nodeId);
        if (node == null) return;
        NodeDefinition type = canvas.definition().nodeType(node.type());
        CanvasPoint point = renderer.nodeScreenPoint(canvas, node);
        commands.setObject(selector + ".Anchor", anchor((int)Math.round(point.x()),
                (int)Math.round(point.y()), type.width(), type.height()));
        commands.setObject(selector + ".Background", new com.hypixel.hytale.server.core.ui.PatchStyle()
                .setColor(Value.of(node.nodeId().equals(canvas.selectedNodeId()) ? "#496d38f5" : "#28476af2")));
        commands.set(selector + " #Title.TextSpans", Message.raw(node.metadata().getOrDefault("label", nodeId)));
    }

    private static Anchor segmentAnchor(EdgeSegment segment, int thickness) {
        int x1 = (int)Math.round(segment.start().x());
        int y1 = (int)Math.round(segment.start().y());
        int x2 = (int)Math.round(segment.end().x());
        int y2 = (int)Math.round(segment.end().y());
        if (Math.abs(x2 - x1) >= Math.abs(y2 - y1))
            return anchor(Math.min(x1, x2), y1 - thickness / 2, Math.max(2, Math.abs(x2 - x1)), thickness);
        return anchor(x1 - thickness / 2, Math.min(y1, y2), thickness, Math.max(2, Math.abs(y2 - y1)));
    }

    private static String coordinates(Status status) {
        if (!Double.isFinite(status.rawX()) || !Double.isFinite(status.rawY())) return "RAW unavailable";
        String raw = String.format(java.util.Locale.ROOT, "RAW %.4f,%.4f", status.rawX(), status.rawY());
        if (status.viewportX() == null) return raw + " | mapping pending";
        return raw + String.format(java.util.Locale.ROOT,
                " | NORM %.3f,%.3f | VIEW %.1f,%.1f | LOCAL %.1f,%.1f",
                status.normalizedX(), status.normalizedY(), status.viewportX(), status.viewportY(),
                status.localX(), status.localY());
    }

    private static Anchor anchor(int left, int top, int width, int height) {
        Anchor anchor = new Anchor();
        anchor.setLeft(Value.of(left)); anchor.setTop(Value.of(top));
        anchor.setWidth(Value.of(width)); anchor.setHeight(Value.of(height));
        return anchor;
    }
}
