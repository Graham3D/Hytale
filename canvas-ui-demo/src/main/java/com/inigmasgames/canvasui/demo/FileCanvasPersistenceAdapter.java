package com.inigmasgames.canvasui.demo;

import com.inigmasgames.canvasui.api.CanvasPersistenceAdapter;
import com.inigmasgames.canvasui.api.CanvasSnapshot;
import com.inigmasgames.canvasui.api.CanvasViewport;
import com.inigmasgames.canvasui.api.EdgeStyle;

import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;

/** Demo-only storage. Production consumers provide their own adapter. */
final class FileCanvasPersistenceAdapter implements CanvasPersistenceAdapter {
    private final Path path;
    FileCanvasPersistenceAdapter(Path path) { this.path = path; }

    @Override
    public Optional<CanvasSnapshot> load(String canvasId) {
        if (!Files.isRegularFile(path)) return Optional.empty();
        Properties p = new Properties();
        try (InputStream input = Files.newInputStream(path)) {
            p.load(input);
            if (!canvasId.equals(p.getProperty("canvasId"))) return Optional.empty();
            CanvasViewport viewport = new CanvasViewport(number(p, "viewport.x"), number(p, "viewport.y"));
            List<CanvasSnapshot.NodeState> nodes = new ArrayList<>();
            for (int i = 0; i < integer(p, "node.count"); i++) {
                String prefix = "node." + i + '.';
                nodes.add(new CanvasSnapshot.NodeState(value(p, prefix + "id"), value(p, prefix + "type"),
                        number(p, prefix + "x"), number(p, prefix + "y"), decodeMap(p.getProperty(prefix + "metadata", "")),
                        Boolean.parseBoolean(p.getProperty(prefix + "enabled", "true"))));
            }
            List<CanvasSnapshot.EdgeState> edges = new ArrayList<>();
            for (int i = 0; i < integer(p, "edge.count"); i++) {
                String prefix = "edge." + i + '.';
                edges.add(new CanvasSnapshot.EdgeState(value(p, prefix + "id"), value(p, prefix + "sourceNode"),
                        value(p, prefix + "sourcePort"), value(p, prefix + "targetNode"), value(p, prefix + "targetPort"),
                        new EdgeStyle(integer(p, prefix + "thickness"), value(p, prefix + "color"),
                                value(p, prefix + "semantic"), value(p, prefix + "material"), value(p, prefix + "state"))));
            }
            String selected = decode(p.getProperty("selected", ""));
            return Optional.of(new CanvasSnapshot(canvasId, viewport, nodes, edges, selected.isBlank() ? null : selected));
        } catch (Exception error) {
            throw new IllegalStateException("Unable to load CanvasUI demo layout: " + path, error);
        }
    }

    @Override
    public void save(String canvasId, CanvasSnapshot snapshot) {
        Properties p = new Properties();
        p.setProperty("format", "1"); p.setProperty("canvasId", canvasId);
        p.setProperty("viewport.x", Double.toString(snapshot.viewport().offsetX()));
        p.setProperty("viewport.y", Double.toString(snapshot.viewport().offsetY()));
        p.setProperty("selected", encode(snapshot.selectedNodeId() == null ? "" : snapshot.selectedNodeId()));
        p.setProperty("node.count", Integer.toString(snapshot.nodes().size()));
        for (int i = 0; i < snapshot.nodes().size(); i++) {
            CanvasSnapshot.NodeState node = snapshot.nodes().get(i); String prefix = "node." + i + '.';
            p.setProperty(prefix + "id", encode(node.nodeId())); p.setProperty(prefix + "type", encode(node.type()));
            p.setProperty(prefix + "x", Double.toString(node.x())); p.setProperty(prefix + "y", Double.toString(node.y()));
            p.setProperty(prefix + "enabled", Boolean.toString(node.enabled())); p.setProperty(prefix + "metadata", encodeMap(node.metadata()));
        }
        p.setProperty("edge.count", Integer.toString(snapshot.edges().size()));
        for (int i = 0; i < snapshot.edges().size(); i++) {
            CanvasSnapshot.EdgeState edge = snapshot.edges().get(i); String prefix = "edge." + i + '.';
            p.setProperty(prefix + "id", encode(edge.edgeId())); p.setProperty(prefix + "sourceNode", encode(edge.sourceNodeId()));
            p.setProperty(prefix + "sourcePort", encode(edge.sourcePortId())); p.setProperty(prefix + "targetNode", encode(edge.targetNodeId()));
            p.setProperty(prefix + "targetPort", encode(edge.targetPortId()));
            p.setProperty(prefix + "thickness", Integer.toString(edge.style().thickness()));
            p.setProperty(prefix + "color", encode(edge.style().color())); p.setProperty(prefix + "semantic", encode(edge.style().semanticType()));
            p.setProperty(prefix + "material", encode(edge.style().material()));
            p.setProperty(prefix + "state", encode(edge.style().state()));
        }
        try {
            Files.createDirectories(path.getParent());
            Path temp = path.resolveSibling(path.getFileName() + ".tmp");
            try (OutputStream output = Files.newOutputStream(temp)) { p.store(output, "CanvasUI demo snapshot"); }
            Files.move(temp, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (Exception error) {
            throw new IllegalStateException("Unable to save CanvasUI demo layout: " + path, error);
        }
    }

    private static int integer(Properties p, String key) { return Integer.parseInt(p.getProperty(key, "0")); }
    private static double number(Properties p, String key) { return Double.parseDouble(p.getProperty(key, "0")); }
    private static String value(Properties p, String key) { return decode(p.getProperty(key, "")); }
    private static String encode(String value) { return Base64.getUrlEncoder().withoutPadding().encodeToString(value.getBytes(StandardCharsets.UTF_8)); }
    private static String decode(String value) { return value.isBlank() ? "" : new String(Base64.getUrlDecoder().decode(value), StandardCharsets.UTF_8); }
    private static String encodeMap(Map<String, String> map) {
        StringBuilder out = new StringBuilder();
        map.forEach((key, value) -> out.append(encode(key)).append('=').append(encode(value)).append(';'));
        return out.toString();
    }
    private static Map<String, String> decodeMap(String value) {
        Map<String, String> result = new LinkedHashMap<>();
        for (String pair : value.split(";")) {
            if (pair.isBlank()) continue;
            String[] parts = pair.split("=", 2); result.put(decode(parts[0]), decode(parts.length == 2 ? parts[1] : ""));
        }
        return result;
    }
}
