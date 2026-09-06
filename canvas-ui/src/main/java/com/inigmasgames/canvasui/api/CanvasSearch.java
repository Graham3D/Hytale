package com.inigmasgames.canvasui.api;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

/** Locale-stable substring search over consumer-supplied presentation metadata. */
public final class CanvasSearch {
    private CanvasSearch() { }

    public static boolean matches(String query, NodeVisual visual) {
        String needle = normalize(query);
        if (needle.isEmpty()) return true;
        if (normalize(visual.searchName()).contains(needle)
                || normalize(visual.searchDescription()).contains(needle)) return true;
        return visual.searchTags().stream().map(CanvasSearch::normalize).anyMatch(value -> value.contains(needle));
    }

    public static Set<String> matchingNodeIds(String query, Collection<CanvasNode> nodes, CanvasDefinition definition) {
        Set<String> result = new LinkedHashSet<>();
        for (CanvasNode node : nodes) {
            NodeVisual visual = definition.nodeType(node.type()).renderer()
                    .render(new NodeRenderContext(node, NodeVisualState.NORMAL));
            if (matches(query, visual)) result.add(node.nodeId());
        }
        return Set.copyOf(result);
    }

    public static String normalize(String value) {
        return value == null ? "" : value.strip().toLowerCase(Locale.ROOT);
    }
}
