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
import java.nio.file.Path;

/** RPG authority adapter for the production CanvasUI graph editor. */
public final class RpgCanvasSkillTreeEditor implements CursorCanvasEditor {
    private static final String[] JOINT_PORTS = {"a", "b", "c"};
    private final UUID player;
    private final RpgSkillTreeProjectionService projection;
    private final RpgSkillTreeMutationService mutations;
    private final SkillTreePortBindingStore portBindings;
    private final Canvas canvas;

    public RpgCanvasSkillTreeEditor(UUID player, RpgSkillTreeProjectionService projection,
                                    RpgSkillTreeMutationService mutations) {
        this(player,projection,mutations,new SkillTreePortBindingStore(Path.of(System.getProperty("java.io.tmpdir"),"hywind-skilltree-port-tests",player.toString())));
    }

    public RpgCanvasSkillTreeEditor(UUID player, RpgSkillTreeProjectionService projection,
                                    RpgSkillTreeMutationService mutations,SkillTreePortBindingStore portBindings) {
        this.player = player;
        this.projection = projection;
        this.mutations = mutations;
        this.portBindings=java.util.Objects.requireNonNull(portBindings);
        this.canvas = createCanvas();
        canvas.restore(authoritativeSnapshot(canvas.snapshot()));
    }

    @Override public String editorId() { return "rpg-skill-tree-v2"; }
    @Override public String title() { return "SKILL TREE"; }
    @Override public Canvas canvas() { return canvas; }

    @Override public List<LibraryEntry> library(LibraryKind kind) {
        StaticSkillTreeViewModel.Tab tab = kind == LibraryKind.SKILL
                ? StaticSkillTreeViewModel.Tab.SKILLS : StaticSkillTreeViewModel.Tab.PASSIVES;
        return projection.project(player, tab, "", "", "", null, "").library().stream()
                .map(item -> new LibraryEntry(item.id(), item.name(), item.category(), item.iconPath(), kind)).toList();
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
        restoreDormant(node);
        return new Result(true, entry.name() + " assigned to " + node.externalId()
                + (node == LinkNodeId.SKILL03 ? " (stored unbound; this build has no native Ability4 input)" : ""),
                authoritativeSnapshot(presentationSnapshot));
    }

    @Override public Result commit(CanvasSnapshot candidateSnapshot) {
        RpgLoadoutView current = mutations.view(player);
        Map<LinkNodeId, LinkNodeId> existing = outgoing(current.state().linkEdges());
        List<EdgeCandidate> candidate = candidatePairs(candidateSnapshot);
        EdgeCandidate addition = null;
        for (int i = candidate.size() - 1; i >= 0; i--) {
            EdgeCandidate pair = candidate.get(i);
            if (!pair.target.equals(existing.get(pair.source))) { addition = pair; break; }
        }
        if (addition == null) return new Result(true, "Ready", authoritativeSnapshot(candidateSnapshot));

        Map<LinkNodeId, LinkNodeId> proposed = new EnumMap<>(existing);
        proposed.put(addition.source, addition.target);
        String capacity = jointCapacityFailure(proposed);
        if (capacity != null) return reject(capacity, candidateSnapshot);
        EdgeCandidate acceptedCandidate = addition;
        MutationResult result = mutations.link(player, acceptedCandidate.source, acceptedCandidate.target);
        if (!result.success()) return reject(result.code() + ": " + result.message(), candidateSnapshot);
        LinkEdge accepted=mutations.view(player).state().linkEdges().stream()
                .filter(edge->edge.sourceNodeId()==acceptedCandidate.source&&edge.targetNodeId()==acceptedCandidate.target).findFirst()
                .orElseThrow(()->new IllegalStateException("ACCEPTED_LINK_MISSING"));
        portBindings.bind(player,accepted,acceptedCandidate.sourcePort,acceptedCandidate.targetPort);
        return new Result(true, "Parented " + acceptedCandidate.source.externalId() + " → " + acceptedCandidate.target.externalId(),
                authoritativeSnapshot(candidateSnapshot));
    }

    @Override public Result breakLink(String linkId, CanvasSnapshot presentationSnapshot) {
        boolean exists = mutations.view(player).state().linkEdges().stream()
                .anyMatch(edge -> edge.edgeId().value().equals(linkId));
        if (!exists) return reject("Link is no longer present", presentationSnapshot);
        MutationResult result;
        try { result = mutations.unlinkEdge(player, linkId); }
        catch (IllegalArgumentException error) { return reject("Invalid link identity", presentationSnapshot); }
        if (!result.success()) return reject(result.code() + ": " + result.message(), presentationSnapshot);
        portBindings.remove(player,linkId);
        portBindings.prune(player,mutations.view(player).state().linkEdges());
        return new Result(true, "Link broken", authoritativeSnapshot(presentationSnapshot));
    }

    @Override public Result clearNode(String nodeId,CanvasSnapshot presentationSnapshot){
        LinkNodeId node;try{node=LinkNodeId.parse(nodeId);}catch(IllegalArgumentException error){return reject("Unknown node",presentationSnapshot);}
        if(node.kind()!=LinkNodeId.NodeKind.SKILL)return reject("Only occupied Skill nodes can be unequipped",presentationSnapshot);
        var projected=projection.project(player,StaticSkillTreeViewModel.Tab.SKILLS,"","","",node,"").nodes().get(node);
        if(projected==null||!projected.occupied())return reject("Skill node is already empty",presentationSnapshot);
        portBindings.preserveForClear(player,node,presentationSnapshot);
        MutationResult result=mutations.clear(player,mutations.view(player).state().revision,node);
        if(!result.success())return reject(result.code()+": "+result.message(),presentationSnapshot);
        return new Result(true,"Unequipped "+projected.title()+"; topology preserved",authoritativeSnapshot(presentationSnapshot));
    }

    @Override public Inspector inspect(String entryId,String nodeId){
        LinkNodeId node=null;if(nodeId!=null&&!nodeId.isBlank())try{node=LinkNodeId.parse(nodeId);}catch(IllegalArgumentException ignored){}
        StaticSkillTreeViewModel.Tab tab=LibraryKind.PASSIVE.name().equalsIgnoreCase(kindOf(entryId,node))?StaticSkillTreeViewModel.Tab.PASSIVES:StaticSkillTreeViewModel.Tab.SKILLS;
        var details=projection.project(player,tab,"","","",node,entryId==null?"":entryId).details();
        return new Inspector(details.kind(),details.id(),details.name(),details.category(),details.description(),details.iconPath(),
                details.rows().stream().map(row->new DetailRow(row.label(),row.value(),row.semanticKind())).toList(),details.validation());
    }

    private String kindOf(String entryId,LinkNodeId node){
        if(entryId!=null&&!entryId.isBlank())for(var entry:library(LibraryKind.PASSIVE))if(entry.id().equals(entryId))return LibraryKind.PASSIVE.name();
        return node!=null&&node.kind()==LinkNodeId.NodeKind.PASSIVE?LibraryKind.PASSIVE.name():LibraryKind.SKILL.name();
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
                .port(CanvasPort.input("in", "rpg-link", 8, 35, 31)).build();
        // max=2 permits an atomic reparent candidate; the authoritative snapshot always restores one output.
        NodeDefinition passive = NodeDefinition.builder("passive").size(124, 54)
                .port(CanvasPort.output("out", "rpg-link", 2, 89, 27)).build();
        NodeDefinition joint = NodeDefinition.builder("joint").size(72, 72)
                .port(CanvasPort.bidirectional("a", "rpg-link", 2, 36, 5))
                .port(CanvasPort.bidirectional("b", "rpg-link", 2, 67, 53))
                .port(CanvasPort.bidirectional("c", "rpg-link", 2, 5, 53)).build();
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
        result.createNode(LinkNodeId.PASSIVE01.externalId(), "passive", CanvasPoint.of(300, 48), Map.of());
        result.createNode(LinkNodeId.PASSIVE02.externalId(), "passive", CanvasPoint.of(300, 116), Map.of());
        result.createNode(LinkNodeId.PASSIVE03.externalId(), "passive", CanvasPoint.of(300, 184), Map.of());
        result.createNode(LinkNodeId.PASSIVE04.externalId(), "passive", CanvasPoint.of(300, 292), Map.of());
        result.createNode(LinkNodeId.PASSIVE05.externalId(), "passive", CanvasPoint.of(300, 360), Map.of());
        result.createNode(LinkNodeId.PASSIVE06.externalId(), "passive", CanvasPoint.of(300, 428), Map.of());
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
            String label=content == null ? id.externalId() : content.title();
            if(id.kind()==LinkNodeId.NodeKind.SKILL)label+=" ["+nativeHotkey(id)+"]";
            Map<String, String> metadata = Map.of(
                    "label", label,
                    "subtitle", "",
                    "icon", content == null ? "" : content.iconPath(),
                    "occupied", Boolean.toString(content != null && content.occupied()));
            nodes.add(new CanvasSnapshot.NodeState(id.externalId(), id.kind().name().toLowerCase(),
                    prior == null ? fallback.x() : prior.x(), prior == null ? fallback.y() : prior.y(), metadata, true));
        }
        portBindings.prune(player,view.state().linkEdges());
        List<CanvasSnapshot.EdgeState> edges = edgeStates(view.state().linkEdges());
        return new CanvasSnapshot(canvas.definition().canvasId(), presentation.viewport(), nodes, edges,
                presentation.selectedNodeId());
    }

    private List<CanvasSnapshot.EdgeState> edgeStates(List<LinkEdge> links) {
        return portBindings.states(player,links);
    }

    private void restoreDormant(LinkNodeId node){
        for(var dormant:portBindings.dormant(player,node)){
            boolean live=mutations.view(player).state().linkEdges().stream().anyMatch(edge->edge.sourceNodeId()==dormant.source()&&edge.targetNodeId()==dormant.target());
            if(live){portBindings.remove(player,dormant.edgeId());continue;}
            MutationResult linked=mutations.link(player,dormant.source(),dormant.target());
            if(!linked.success())continue;
            LinkEdge accepted=mutations.view(player).state().linkEdges().stream()
                    .filter(edge->edge.sourceNodeId()==dormant.source()&&edge.targetNodeId()==dormant.target()).findFirst().orElseThrow();
            portBindings.bind(player,accepted,dormant.sourcePort(),dormant.targetPort());portBindings.remove(player,dormant.edgeId());
        }
    }

    private static String nativeHotkey(LinkNodeId node){return switch(node){case SKILL01->"E";case SKILL02->"R";case SKILL03->"UNBOUND";default->"";};}

    private static Map<LinkNodeId, LinkNodeId> outgoing(List<LinkEdge> edges) {
        Map<LinkNodeId, LinkNodeId> result = new EnumMap<>(LinkNodeId.class);
        edges.forEach(edge -> result.put(edge.sourceNodeId(), edge.targetNodeId()));
        return result;
    }

    private static List<EdgeCandidate> candidatePairs(CanvasSnapshot snapshot) {
        List<EdgeCandidate> result = new ArrayList<>();
        for (CanvasSnapshot.EdgeState edge : snapshot.edges()) {
            try { result.add(new EdgeCandidate(LinkNodeId.parse(edge.sourceNodeId()),edge.sourcePortId(),LinkNodeId.parse(edge.targetNodeId()),edge.targetPortId())); }
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

    private record EdgeCandidate(LinkNodeId source,String sourcePort,LinkNodeId target,String targetPort) { }
}
