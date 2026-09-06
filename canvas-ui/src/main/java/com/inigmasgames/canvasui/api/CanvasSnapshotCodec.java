package com.inigmasgames.canvasui.api;

import java.io.StringReader;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

/** Versioned, dependency-free snapshot codec suitable for consumer persistence adapters. */
public final class CanvasSnapshotCodec {
    private CanvasSnapshotCodec() { }

    public static String encode(CanvasSnapshot snapshot) {
        try {
            Properties p = new Properties();
            p.setProperty("format", "1"); p.setProperty("canvas", enc(snapshot.canvasId()));
            p.setProperty("vx", Double.toString(snapshot.viewport().offsetX()));
            p.setProperty("vy", Double.toString(snapshot.viewport().offsetY()));
            p.setProperty("selected", enc(snapshot.selectedNodeId() == null ? "" : snapshot.selectedNodeId()));
            p.setProperty("nodes", Integer.toString(snapshot.nodes().size()));
            for (int i = 0; i < snapshot.nodes().size(); i++) {
                CanvasSnapshot.NodeState n = snapshot.nodes().get(i); String k = "n." + i + '.';
                p.setProperty(k + "id", enc(n.nodeId())); p.setProperty(k + "type", enc(n.type()));
                p.setProperty(k + "x", Double.toString(n.x())); p.setProperty(k + "y", Double.toString(n.y()));
                p.setProperty(k + "enabled", Boolean.toString(n.enabled())); p.setProperty(k + "metadata", encMap(n.metadata()));
            }
            p.setProperty("edges", Integer.toString(snapshot.edges().size()));
            for (int i = 0; i < snapshot.edges().size(); i++) {
                CanvasSnapshot.EdgeState e = snapshot.edges().get(i); String k = "e." + i + '.';
                p.setProperty(k + "id", enc(e.edgeId())); p.setProperty(k + "sn", enc(e.sourceNodeId()));
                p.setProperty(k + "sp", enc(e.sourcePortId())); p.setProperty(k + "tn", enc(e.targetNodeId()));
                p.setProperty(k + "tp", enc(e.targetPortId())); p.setProperty(k + "thickness", Integer.toString(e.style().thickness()));
                p.setProperty(k + "color", enc(e.style().color())); p.setProperty(k + "semantic", enc(e.style().semanticType()));
                p.setProperty(k + "material", enc(e.style().material()));
                p.setProperty(k + "state", enc(e.style().state()));
            }
            StringWriter out = new StringWriter(); p.store(out, "CanvasUI snapshot"); return out.toString();
        } catch (Exception error) { throw new IllegalStateException("Unable to encode CanvasUI snapshot", error); }
    }

    public static CanvasSnapshot decode(String encoded) {
        try {
            Properties p = new Properties(); p.load(new StringReader(encoded));
            if (!"1".equals(p.getProperty("format"))) throw new IllegalArgumentException("unsupported snapshot format");
            List<CanvasSnapshot.NodeState> nodes = new ArrayList<>();
            for (int i = 0; i < integer(p, "nodes"); i++) {
                String k = "n." + i + '.';
                nodes.add(new CanvasSnapshot.NodeState(dec(p.getProperty(k + "id")), dec(p.getProperty(k + "type")),
                        number(p, k + "x"), number(p, k + "y"), decMap(p.getProperty(k + "metadata", "")),
                        Boolean.parseBoolean(p.getProperty(k + "enabled"))));
            }
            List<CanvasSnapshot.EdgeState> edges = new ArrayList<>();
            for (int i = 0; i < integer(p, "edges"); i++) {
                String k = "e." + i + '.';
                edges.add(new CanvasSnapshot.EdgeState(dec(p.getProperty(k + "id")), dec(p.getProperty(k + "sn")),
                        dec(p.getProperty(k + "sp")), dec(p.getProperty(k + "tn")), dec(p.getProperty(k + "tp")),
                        new EdgeStyle(integer(p, k + "thickness"), dec(p.getProperty(k + "color")),
                                dec(p.getProperty(k + "semantic")), dec(p.getProperty(k + "material")), dec(p.getProperty(k + "state")))));
            }
            String selected = dec(p.getProperty("selected", ""));
            return new CanvasSnapshot(dec(p.getProperty("canvas")), new CanvasViewport(number(p, "vx"), number(p, "vy")),
                    nodes, edges, selected.isBlank() ? null : selected);
        } catch (RuntimeException error) { throw error; }
        catch (Exception error) { throw new IllegalArgumentException("Unable to decode CanvasUI snapshot", error); }
    }

    private static int integer(Properties p, String key) { return Integer.parseInt(p.getProperty(key, "0")); }
    private static double number(Properties p, String key) { return Double.parseDouble(p.getProperty(key, "0")); }
    private static String enc(String value) { return Base64.getUrlEncoder().withoutPadding().encodeToString(value.getBytes(StandardCharsets.UTF_8)); }
    private static String dec(String value) { return value == null || value.isBlank() ? "" : new String(Base64.getUrlDecoder().decode(value), StandardCharsets.UTF_8); }
    private static String encMap(Map<String, String> map) {
        StringBuilder out = new StringBuilder(); map.forEach((k, v) -> out.append(enc(k)).append('=').append(enc(v)).append(';')); return out.toString();
    }
    private static Map<String, String> decMap(String value) {
        Map<String, String> result = new LinkedHashMap<>();
        for (String pair : value.split(";")) { if (pair.isBlank()) continue; String[] parts = pair.split("=", 2); result.put(dec(parts[0]), dec(parts.length == 2 ? parts[1] : "")); }
        return result;
    }
}
