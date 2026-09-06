package com.inigmasgames.canvasui.demo;

import com.inigmasgames.canvasui.api.Canvas;
import com.inigmasgames.canvasui.api.CanvasDefinition;
import com.inigmasgames.canvasui.api.CanvasPoint;
import com.inigmasgames.canvasui.api.CanvasPort;
import com.inigmasgames.canvasui.api.ConnectionCode;
import com.inigmasgames.canvasui.api.ConnectionResult;
import com.inigmasgames.canvasui.api.EdgeStyle;
import com.inigmasgames.canvasui.api.NodeDefinition;
import com.inigmasgames.canvasui.api.NodeVisual;
import com.inigmasgames.canvasui.api.PanGesture;

import java.util.Map;

final class DemoDefinitions {
    private DemoDefinitions() { }

    static CanvasDefinition generic(String id, FileCanvasPersistenceAdapter persistence) {
        NodeDefinition source = NodeDefinition.builder("source").size(150, 72)
                .port(CanvasPort.output("out", "data", 4, 150, 36))
                .renderer(context -> visual(context, "Source", "#28476aee")).build();
        NodeDefinition transform = NodeDefinition.builder("transform").size(160, 82)
                .port(CanvasPort.input("in", "data", 2, 0, 41)).port(CanvasPort.output("out", "data", 3, 160, 41))
                .renderer(context -> visual(context, "Transform", "#4b3869ee")).build();
        NodeDefinition output = NodeDefinition.builder("output").size(150, 72)
                .port(CanvasPort.input("in", "data", 4, 0, 36))
                .renderer(context -> visual(context, "Output", "#285b49ee")).build();
        NodeDefinition router = NodeDefinition.builder("router").size(125, 100)
                .port(CanvasPort.input("in-a", "data", 2, 0, 30)).port(CanvasPort.input("in-b", "data", 2, 0, 70))
                .port(CanvasPort.output("out", "data", 4, 125, 50))
                .renderer(context -> visual(context, "Router", "#6a4d27ee")).build();
        return CanvasDefinition.builder(id).fixedZoom(true).pannable(true).panGesture(PanGesture.MIDDLE_BUTTON)
                .registerNodeType(source).registerNodeType(transform).registerNodeType(output).registerNodeType(router)
                .connectionPolicy((s, sp, t, tp) -> s.nodeId().equals(t.nodeId())
                        ? ConnectionResult.reject(ConnectionCode.REJECT_SELF_CONNECTION, "self-connections are disabled by this demo")
                        : ConnectionResult.allow())
                .persistence(persistence).build();
    }

    static void seedGeneric(Canvas canvas) {
        canvas.createNode("source-a", "source", CanvasPoint.of(140, 130), Map.of("label", "Source A"));
        canvas.createNode("source-b", "source", CanvasPoint.of(140, 330), Map.of("label", "Source B"));
        canvas.createNode("transform-a", "transform", CanvasPoint.of(430, 110), Map.of("label", "Transform A"));
        canvas.createNode("transform-b", "transform", CanvasPoint.of(430, 350), Map.of("label", "Transform B"));
        canvas.createNode("router-a", "router", CanvasPoint.of(720, 225), Map.of());
        canvas.createNode("output-a", "output", CanvasPoint.of(980, 240), Map.of("label", "Output"));
        canvas.connect("edge-1", "source-a", "out", "transform-a", "in", EdgeStyle.standard("data"));
        canvas.connect("edge-2", "source-b", "out", "transform-b", "in", EdgeStyle.standard("data"));
        canvas.connect("edge-3", "transform-a", "out", "router-a", "in-a", EdgeStyle.standard("data"));
        canvas.connect("edge-4", "transform-b", "out", "router-a", "in-b", EdgeStyle.standard("data"));
        canvas.connect("edge-5", "router-a", "out", "output-a", "in", EdgeStyle.standard("data"));
    }

    static CanvasDefinition topologyProof(String id, FileCanvasPersistenceAdapter persistence) {
        NodeDefinition producer = NodeDefinition.builder("producer").size(120, 62)
                .port(CanvasPort.input("in", "route", 2, 0, 31)).port(CanvasPort.output("out", "route", 4, 120, 31))
                .renderer(c -> visual(c, "Producer", "#355d80ee")).build();
        NodeDefinition destination = NodeDefinition.builder("destination").size(120, 62)
                .port(CanvasPort.input("in", "route", 6, 0, 31)).port(CanvasPort.output("out", "route", 2, 120, 31))
                .renderer(c -> visual(c, "Destination", "#416d45ee")).build();
        NodeDefinition router = NodeDefinition.builder("router").size(115, 92)
                .port(CanvasPort.input("in-a", "route", 3, 0, 25)).port(CanvasPort.input("in-b", "route", 3, 0, 67))
                .port(CanvasPort.output("out", "route", 6, 115, 46))
                .renderer(c -> visual(c, "Router", "#6a4d27ee")).build();
        return CanvasDefinition.builder(id).registerNodeType(producer).registerNodeType(destination).registerNodeType(router)
                .panGesture(PanGesture.MIDDLE_BUTTON).connectionPolicy((source, sp, target, tp) -> {
                    boolean allowed = (source.type().equals("producer") && (target.type().equals("destination") || target.type().equals("router")))
                            || (source.type().equals("router") && (target.type().equals("router") || target.type().equals("destination")));
                    return allowed ? ConnectionResult.allow()
                            : ConnectionResult.reject(ConnectionCode.REJECT_TYPE, source.type() + " -> " + target.type() + " rejected by consumer policy");
                }).persistence(persistence).build();
    }

    static void seedTopologyProof(Canvas canvas) {
        for (int i = 1; i <= 6; i++) canvas.createNode("producer-" + i, "producer",
                CanvasPoint.of(100, 75 + i * 80), Map.of("label", "Source " + i, "role", "source-like"));
        canvas.createNode("router-1", "router", CanvasPoint.of(410, 210), Map.of("label", "Router A"));
        canvas.createNode("router-2", "router", CanvasPoint.of(650, 310), Map.of("label", "Router B"));
        for (int i = 1; i <= 4; i++) canvas.createNode("destination-" + i, "destination",
                CanvasPoint.of(930, 120 + i * 110), Map.of("label", "Destination " + i, "role", "destination-like"));
        canvas.connect("proof-1", "producer-1", "out", "destination-1", "in", EdgeStyle.standard("route"));
        canvas.connect("proof-2", "producer-2", "out", "router-1", "in-a", EdgeStyle.standard("route"));
        canvas.connect("proof-3", "producer-3", "out", "router-1", "in-b", EdgeStyle.standard("route"));
        canvas.connect("proof-4", "router-1", "out", "router-2", "in-a", EdgeStyle.standard("route"));
        canvas.connect("proof-5", "producer-4", "out", "router-2", "in-b", EdgeStyle.standard("route"));
        canvas.connect("proof-6", "router-2", "out", "destination-2", "in", EdgeStyle.standard("route"));
    }

    private static NodeVisual visual(com.inigmasgames.canvasui.api.NodeRenderContext context,
                                     String fallback, String normal) {
        String color = switch (context.state()) {
            case HOVERED -> "#375674ee";
            case SELECTED -> "#586f2aee";
            case DISABLED -> "#30343aaa";
            case INVALID_CONNECTION_TARGET -> "#7d2f35ee";
            default -> normal;
        };
        String subtitle = context.node().type().equals("router") ? "3 ports" : context.node().type();
        return new NodeVisual(context.node().metadata().getOrDefault("label", fallback), subtitle,
                color, "#78c6d0", "#eef6ff");
    }
}
