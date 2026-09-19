package com.inigmasgames.hytalerpg.ui.skilltree;

import com.inigmasgames.canvasui.api.Canvas;
import com.inigmasgames.canvasui.api.CanvasDefinition;
import com.inigmasgames.canvasui.api.CanvasEdge;
import com.inigmasgames.canvasui.api.CanvasPoint;
import com.inigmasgames.canvasui.api.CanvasPort;
import com.inigmasgames.canvasui.api.CanvasSnapshot;
import com.inigmasgames.canvasui.api.ConnectionCode;
import com.inigmasgames.canvasui.api.ConnectionResult;
import com.inigmasgames.canvasui.api.EdgeStyle;
import com.inigmasgames.canvasui.api.NodeDefinition;
import com.inigmasgames.canvasui.api.PanGesture;
import com.inigmasgames.canvasui.api.editor.CursorCanvasEditor;
import com.inigmasgames.hytalerpg.domain.LinkEdge;
import com.inigmasgames.hytalerpg.domain.LinkNodeId;
import com.inigmasgames.hytalerpg.progress.MutationResult;
import com.inigmasgames.hytalerpg.progress.RpgLoadoutView;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** RPG authority adapter for the production CanvasUI graph editor. */
public final class RpgCanvasSkillTreeEditor implements CursorCanvasEditor {
    private static final String[] JOINT_PORTS = {"a", "b", "c"};
    private final UUID player;
    private final RpgSkillTreeProjectionService projection;
    private final RpgSkillTreeMutationService mutations;
    private final Canvas canvas;

    public RpgCanvasSkillTreeEditor(UUID player, RpgSkillTreeProjectionService projection,
                                    RpgSkillTreeMutationService mutations) {
        this.player = player;
        this.projection = projection;
        this.mutations = mutations;
        this.canvas = createCanvas();
        canvas.restore(authoritativeSnapshot(canvas.snapshot()));
    }

    @Override public String editorId() { return "rpg-skill-tree-v2"; }
    @Override public String title() { return "HYTALE RPG — SKILL TREE"; }
    @Override public Canvas canvas() { return canvas; }

    @Override public List<LibraryEntry> library(LibraryKind kind) {
        StaticSkillTreeViewModel.Tab tab = kind == LibraryKind.SKILL
                ? StaticSkillTreeViewModel.Tab.SKILLS : StaticSkillTreeViewModel.Tab.PASSIVES;
        return projection.project(player, tab, "", "", "", null, "").library().stream()
                .map(item -> new LibraryEntry(item.id(), item.name(), item.category(), kind)).toList();
    }

    @Override public Result assign(String entryId, String nodeId, CanvasSnapshot presentationSnapshot) {
        LinkNodeId node;
        try { node = LinkNodeId.parse(nodeId); }
        catch (IllegalArgumentException error) { return reject("Unknown target node", presentationSnapshot); }
        LibraryEntry entry = find(entryId);
        if (entry == null) return reject("Library entry is no longer available", presentationSnapshot);
        if (node.kind() == LinkNodeId.NodeKind.JOINT)
            return reject("Joint nodes route links and cannot hold content", presentationSnapshot);
        if ((entry.kind() == LibraryKind.SKILL) != (node.kind() == LinkNodeId.NodeKind.SKILL))
            return reject("Drop skills on Skill nodes and passives on Passive nodes", presentationSnapshot);
        long revision = mutations.view(player).state().revision;
        MutationResult mutation = mutations.assignCanvas(player, revision, node, entry.id());
        if (!mutation.success()) return reject(mutation.code() + ": " + mutation.message(), presentationSnapshot);
        return new Result(true, entry.name() + " assigned to " + node.externalId(),
                authoritativeSnapshot(presentationSnapshot));
    }

    @Override public Result commit(CanvasSnapshot candidateSnapshot) {
        RpgLoadoutView current = mutations.view(player);
        Map<LinkNodeId, LinkNodeId> existing = outgoing(current.state().linkEdges());
        List<NodePair> candidate = candidatePairs(candidateSnapshot);
        NodePair addition = null;
        for (int i = candidate.size() - 1; i >= 0; i--) {
            NodePair pair = candidate.get(i);
            if (!pair.target.equals(existing.get(pair.source))) { addition = pair; break; }
        }
        if (addition == null) return new Result(true, "Layout saved", authoritativeSnapshot(candidateSnapshot));

        Map<LinkNodeId, LinkNodeId> proposed = new EnumMap<>(existing);
        proposed.put(addition.source, addition.target);
        String capacity = jointCapacityFailure(proposed);
        if (capacity != null) return reject(capacity, candidateSnapshot);
        MutationResult result = mutations.link(player, addition.source, addition.target);
        if (!result.success()) return reject(result.code() + ": " + result.message(), candidateSnapshot);
        return new Result(true, "Parented " + addition.source.externalId() + " → " + addition.target.externalId(),
                authoritativeSnapshot(candidateSnapshot));
    }

    private Result reject(String message, CanvasSnapshot presentation) {
        return new Result(false, message, authoritativeSnapshot(presentation));
    }

    private LibraryEntry find(String id) {
        for (LibraryKind kind : LibraryKind.values())
            for (LibraryEntry entry : library(kind)) if (entry.id().equals(id)) return entry;
        return null;
    }

    private Canvas createCanvas() {
        NodeDefinition skill = NodeDefinition.builder("skill").size(132, 62)
                .port(CanvasPort.input("in", "rpg-link", 8, 0, 31)).build();
        // max=2 permits an atomic reparent candidate; the authoritative snapshot always restores one output.
        NodeDefinition passive = NodeDefinition.builder("passive").size(124, 54)
                .port(CanvasPort.output("out", "rpg-link", 2, 124, 27)).build();
        NodeDefinition joint = NodeDefinition.builder("joint").size(72, 72)
                .port(CanvasPort.bidirectional("a", "rpg-link", 2, 36, 0))
                .port(CanvasPort.bidirectional("b", "rpg-link", 2, 72, 55))
                .port(CanvasPort.bidirectional("c", "rpg-link", 2, 0, 55)).build();
        CanvasDefinition definition = CanvasDefinition.builder("rpg-skill-tree-" + player)
                .pannable(false).zoomable(false).panGesture(PanGesture.MIDDLE_BUTTON)
                .allowCycles(false).allowDuplicateEdges(false)
                .registerNodeType(skill).registerNodeType(passive).registerNodeType(joint)
                .connectionPolicy((source, sourcePort, target, targetPort) -> {
                    if (source.nodeId().equals(target.nodeId()))
                        return ConnectionResult.reject(ConnectionCode.REJECT_SELF_CONNECTION, "A node cannot parent itself");
                    boolean legal = ("passive".equals(source.type()) && ("skill".equals(target.type()) || "joint".equals(target.type())))
                            || ("joint".equals(source.type()) && "skill".equals(target.type()));
                    return legal ? ConnectionResult.allow() : ConnectionResult.reject(ConnectionCode.REJECT_TYPE,
                            "Use Passive → Skill/Joint or Joint → Skill");
                }).build();
        Canvas result = new Canvas(definition);
        result.createNode(LinkNodeId.SKILL01.externalId(), "skill", CanvasPoint.of(680, 42), Map.of());
        result.createNode(LinkNodeId.SKILL02.externalId(), "skill", CanvasPoint.of(680, 204), Map.of());
        result.createNode(LinkNodeId.SKILL03.externalId(), "skill", CanvasPoint.of(680, 366), Map.of());
        result.createNode(LinkNodeId.JOINT01.externalId(), "joint", CanvasPoint.of(515, 118), Map.of());
        result.createNode(LinkNodeId.JOINT02.externalId(), "joint", CanvasPoint.of(515, 304), Map.of());
        result.createNode(LinkNodeId.PASSIVE01.externalId(), "passive", CanvasPoint.of(236, 15), Map.of());
        result.createNode(LinkNodeId.PASSIVE02.externalId(), "passive", CanvasPoint.of(236, 83), Map.of());
        result.createNode(LinkNodeId.PASSIVE03.externalId(), "passive", CanvasPoint.of(236, 151), Map.of());
        result.createNode(LinkNodeId.PASSIVE04.externalId(), "passive", CanvasPoint.of(236, 259), Map.of());
        result.createNode(LinkNodeId.PASSIVE05.externalId(), "passive", CanvasPoint.of(236, 327), Map.of());
        result.createNode(LinkNodeId.PASSIVE06.externalId(), "passive", CanvasPoint.of(236, 395), Map.of());
        return result;
    }

    private CanvasSnapshot authoritativeSnapshot(CanvasSnapshot presentation) {
        RpgLoadoutView view = mutations.view(player);
        StaticSkillTreeViewModel projected = projection.project(player, StaticSkillTreeViewModel.Tab.SKILLS,
                "", "", "", null, "");
        Map<String, CanvasSnapshot.NodeState> old = new HashMap<>();
        presentation.nodes().forEach(node -> old.put(node.nodeId(), node));
        List<CanvasSnapshot.NodeState> nodes = new ArrayList<>();
        for (LinkNodeId id : LinkNodeId.values()) {
            CanvasSnapshot.NodeState prior = old.get(id.externalId());
            CanvasPoint fallback = canvas.node(id.externalId()).position();
            StaticSkillTreeViewModel.TreeNode content = projected.nodes().get(id);
            Map<String, String> metadata = Map.of("label", content == null ? id.externalId() : content.title(),
                    "subtitle", content == null ? id.kind().name() : content.subtitle());
            nodes.add(new CanvasSnapshot.NodeState(id.externalId(), id.kind().name().toLowerCase(),
                    prior == null ? fallback.x() : prior.x(), prior == null ? fallback.y() : prior.y(), metadata, true));
        }
        List<CanvasSnapshot.EdgeState> edges = edgeStates(view.state().linkEdges());
        return new CanvasSnapshot(canvas.definition().canvasId(), presentation.viewport(), nodes, edges,
                presentation.selectedNodeId());
    }

    private List<CanvasSnapshot.EdgeState> edgeStates(List<LinkEdge> links) {
        Map<LinkNodeId, Integer> nextPort = new EnumMap<>(LinkNodeId.class);
        List<CanvasSnapshot.EdgeState> result = new ArrayList<>();
        for (LinkEdge edge : links) {
            String sourcePort = edge.sourceNodeId().kind() == LinkNodeId.NodeKind.PASSIVE ? "out"
                    : jointPort(edge.sourceNodeId(), nextPort);
            String targetPort = edge.targetNodeId().kind() == LinkNodeId.NodeKind.SKILL ? "in"
                    : jointPort(edge.targetNodeId(), nextPort);
            result.add(new CanvasSnapshot.EdgeState(edge.edgeId().value(), edge.sourceNodeId().externalId(), sourcePort,
                    edge.targetNodeId().externalId(), targetPort, EdgeStyle.standard("rpg-parent")));
        }
        return List.copyOf(result);
    }

    private static String jointPort(LinkNodeId joint, Map<LinkNodeId, Integer> nextPort) {
        int index = nextPort.getOrDefault(joint, 0);
        if (index >= JOINT_PORTS.length) throw new IllegalStateException(joint.externalId() + " exceeds three linked nodes");
        nextPort.put(joint, index + 1);
        return JOINT_PORTS[index];
    }

    private static Map<LinkNodeId, LinkNodeId> outgoing(List<LinkEdge> edges) {
        Map<LinkNodeId, LinkNodeId> result = new EnumMap<>(LinkNodeId.class);
        edges.forEach(edge -> result.put(edge.sourceNodeId(), edge.targetNodeId()));
        return result;
    }

    private static List<NodePair> candidatePairs(CanvasSnapshot snapshot) {
        List<NodePair> result = new ArrayList<>();
        for (CanvasSnapshot.EdgeState edge : snapshot.edges()) {
            try { result.add(new NodePair(LinkNodeId.parse(edge.sourceNodeId()), LinkNodeId.parse(edge.targetNodeId()))); }
            catch (IllegalArgumentException ignored) { }
        }
        return result;
    }

    private static String jointCapacityFailure(Map<LinkNodeId, LinkNodeId> outgoing) {
        Map<LinkNodeId, Set<LinkNodeId>> neighbors = new EnumMap<>(LinkNodeId.class);
        outgoing.forEach((source, target) -> {
            if (source.kind() == LinkNodeId.NodeKind.JOINT)
                neighbors.computeIfAbsent(source, ignored -> new LinkedHashSet<>()).add(target);
            if (target.kind() == LinkNodeId.NodeKind.JOINT)
                neighbors.computeIfAbsent(target, ignored -> new LinkedHashSet<>()).add(source);
        });
        for (var entry : neighbors.entrySet()) if (entry.getValue().size() > 3)
            return entry.getKey().externalId() + " is triangular and accepts at most three linked nodes";
        return null;
    }

    private record NodePair(LinkNodeId source, LinkNodeId target) { }
}
