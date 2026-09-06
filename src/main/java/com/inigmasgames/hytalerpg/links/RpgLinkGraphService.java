package com.inigmasgames.hytalerpg.links;

import com.inigmasgames.hytalerpg.content.RpgCatalog;
import com.inigmasgames.hytalerpg.domain.LinkEdge;
import com.inigmasgames.hytalerpg.domain.LinkNodeId;
import com.inigmasgames.hytalerpg.domain.PassiveDefinition;
import com.inigmasgames.hytalerpg.domain.PassiveSlot;
import com.inigmasgames.hytalerpg.domain.SkillDefinition;
import com.inigmasgames.hytalerpg.domain.SkillSlot;
import com.inigmasgames.hytalerpg.progress.RpgPlayerState;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Gameplay topology authority. Presentation order and coordinates never enter this service. */
public final class RpgLinkGraphService {
    private final RpgCatalog catalog;
    private final CompatibilityService compatibility;

    public RpgLinkGraphService(RpgCatalog catalog, CompatibilityService compatibility) {
        this.catalog = catalog;
        this.compatibility = compatibility;
    }

    public List<LinkEdge> candidateLink(RpgPlayerState state, LinkNodeId source, LinkNodeId target) {
        List<LinkEdge> candidate = new ArrayList<>(state.linkEdges());
        candidate.removeIf(edge -> edge.sourceNodeId() == source);
        candidate.add(LinkEdge.create(source, target));
        return candidate;
    }

    public List<LinkEdge> candidateUnlinkSource(RpgPlayerState state, LinkNodeId source) {
        List<LinkEdge> candidate = new ArrayList<>(state.linkEdges());
        candidate.removeIf(edge -> edge.sourceNodeId() == source);
        return candidate;
    }

    public List<LinkEdge> candidateUnlinkEdge(RpgPlayerState state, String edgeId) {
        List<LinkEdge> candidate = new ArrayList<>(state.linkEdges());
        candidate.removeIf(edge -> edge.edgeId().value().equals(edgeId));
        return candidate;
    }

    public GraphValidationResult validate(RpgPlayerState state) {
        List<GraphValidationResult.Issue> issues = new ArrayList<>();
        final List<LinkEdge> edges;
        try { edges = state.linkEdges(); }
        catch (RuntimeException error) {
            return GraphValidationResult.invalid(List.of(new GraphValidationResult.Issue(
                    ValidationCode.DANGLING_NODE_REFERENCE, "Unparseable graph edge: " + error.getMessage(), null, null)));
        }

        Set<String> ids = new HashSet<>();
        Set<String> pairs = new HashSet<>();
        Map<LinkNodeId, LinkNodeId> outgoing = new EnumMap<>(LinkNodeId.class);
        Map<LinkNodeId, Integer> incoming = new EnumMap<>(LinkNodeId.class);
        for (LinkEdge edge : edges) {
            if (!ids.add(edge.edgeId().value())) issue(issues, ValidationCode.DUPLICATE_EDGE_ID,
                    "Duplicate edge ID " + edge.edgeId().value(), edge);
            String pair = edge.sourceNodeId() + "->" + edge.targetNodeId();
            if (!pairs.add(pair)) issue(issues, ValidationCode.DUPLICATE_EDGE, "Duplicate graph edge " + pair, edge);
            if (!legal(edge.sourceNodeId(), edge.targetNodeId())) issue(issues, ValidationCode.ILLEGAL_NODE_RELATIONSHIP,
                    "Illegal Link relationship " + edge.sourceNodeId().externalId() + " -> " + edge.targetNodeId().externalId(), edge);
            LinkNodeId prior = outgoing.putIfAbsent(edge.sourceNodeId(), edge.targetNodeId());
            if (prior != null) issue(issues, ValidationCode.SOURCE_OUTPUT_CAPACITY,
                    edge.sourceNodeId().externalId() + " has more than one outgoing edge", edge);
            incoming.merge(edge.targetNodeId(), 1, Integer::sum);
        }
        for (var entry : incoming.entrySet()) {
            if (entry.getKey().kind() == LinkNodeId.NodeKind.JOINT && entry.getValue() > 2) {
                issues.add(new GraphValidationResult.Issue(ValidationCode.JOINT_INPUT_CAPACITY,
                        entry.getKey().externalId() + " accepts at most two incoming routes", null, entry.getKey()));
            }
        }
        if (edges.stream().filter(edge -> edge.sourceNodeId().kind() == LinkNodeId.NodeKind.PASSIVE)
                .map(LinkEdge::sourceNodeId).distinct().count() > 6) {
            issues.add(new GraphValidationResult.Issue(ValidationCode.BUDGET_EXCEEDED,
                    "Link graph exceeds the global six-Passive budget", null, null));
        }
        if (hasCycle(outgoing)) issues.add(new GraphValidationResult.Issue(ValidationCode.CYCLIC_GRAPH,
                "Link graph contains a cycle", null, null));
        if (!issues.isEmpty()) return GraphValidationResult.invalid(issues);

        Map<PassiveSlot, List<LinkNodeId>> routes = new EnumMap<>(PassiveSlot.class);
        for (LinkNodeId passiveNode : LinkNodeId.values()) {
            if (passiveNode.kind() != LinkNodeId.NodeKind.PASSIVE || !outgoing.containsKey(passiveNode)) continue;
            List<LinkNodeId> route = route(passiveNode, outgoing);
            if (route.isEmpty() || route.getLast().kind() != LinkNodeId.NodeKind.SKILL) {
                issues.add(new GraphValidationResult.Issue(ValidationCode.ROUTE_WITHOUT_SKILL,
                        passiveNode.externalId() + " route does not terminate at a Skill", passiveNode,
                        route.isEmpty() ? null : route.getLast()));
                continue;
            }
            PassiveSlot passiveSlot = passiveNode.passiveSlot();
            SkillSlot skillSlot = route.getLast().skillSlot();
            var passiveId = state.passive(passiveSlot);
            var skillId = state.skill(skillSlot);
            if (passiveId.isEmpty()) {
                issues.add(new GraphValidationResult.Issue(ValidationCode.EMPTY_SOURCE_NODE,
                        passiveNode.externalId() + " has no equipped Passive", passiveNode, route.getLast()));
            } else if (skillId.isEmpty()) {
                issues.add(new GraphValidationResult.Issue(ValidationCode.EMPTY_TARGET_NODE,
                        route.getLast().externalId() + " has no equipped Skill", passiveNode, route.getLast()));
            } else {
                PassiveDefinition passive = catalog.passive(passiveId.get()).orElse(null);
                SkillDefinition skill = catalog.skill(skillId.get()).orElse(null);
                if (passive == null) issues.add(new GraphValidationResult.Issue(ValidationCode.UNKNOWN_PASSIVE,
                        "Unknown equipped Passive " + passiveId.get().value(), passiveNode, route.getLast()));
                else if (skill == null) issues.add(new GraphValidationResult.Issue(ValidationCode.UNKNOWN_SKILL,
                        "Unknown equipped Skill " + skillId.get().value(), passiveNode, route.getLast()));
                else {
                    CompatibilityResult result = compatibility.assess(skill, passive);
                    if (!result.accepted()) issues.add(new GraphValidationResult.Issue(result.code(), result.message(), passiveNode, route.getLast()));
                }
            }
            routes.put(passiveSlot, route);
        }
        for (LinkNodeId joint : LinkNodeId.values()) {
            if (joint.kind() != LinkNodeId.NodeKind.JOINT || incoming.getOrDefault(joint, 0) == 0) continue;
            List<LinkNodeId> route = route(joint, outgoing);
            if (route.isEmpty() || route.getLast().kind() != LinkNodeId.NodeKind.SKILL) {
                issues.add(new GraphValidationResult.Issue(ValidationCode.ROUTE_WITHOUT_SKILL,
                        joint.externalId() + " has incoming routes but does not terminate at a Skill", joint,
                        route.isEmpty() ? null : route.getLast()));
            }
        }

        Map<String, Integer> copiesPerSkill = new HashMap<>();
        for (var entry : routes.entrySet()) {
            var passiveId = state.passive(entry.getKey());
            if (passiveId.isEmpty()) continue;
            String key = entry.getValue().getLast().externalId() + ':' + passiveId.get().value();
            int copies = copiesPerSkill.merge(key, 1, Integer::sum);
            int max = catalog.passive(passiveId.get()).map(PassiveDefinition::maxCopies).orElse(0);
            if (copies > max) issues.add(new GraphValidationResult.Issue(ValidationCode.CONFLICTING_MODIFIER,
                    passiveId.get().value() + " exceeds maxCopies=" + max + " for one Skill", null,
                    entry.getValue().getLast()));
        }
        return issues.isEmpty() ? GraphValidationResult.valid(routes) : GraphValidationResult.invalid(issues);
    }

    public CompatibilityResult getCompatibility(RpgPlayerState state, PassiveSlot passiveSlot, SkillSlot skillSlot) {
        var passive = state.passive(passiveSlot).flatMap(catalog::passive);
        var skill = state.skill(skillSlot).flatMap(catalog::skill);
        if (passive.isEmpty()) return CompatibilityResult.rejected(ValidationCode.EMPTY_SOURCE_NODE,
                passiveSlot.externalId() + " has no equipped Passive", Set.of(), Set.of());
        if (skill.isEmpty()) return CompatibilityResult.rejected(ValidationCode.EMPTY_TARGET_NODE,
                skillSlot.externalId() + " has no equipped Skill", Set.of(), Set.of());
        return compatibility.assess(skill.get(), passive.get());
    }

    private static boolean legal(LinkNodeId source, LinkNodeId target) {
        if (source == target) return false;
        return switch (source.kind()) {
            case SKILL -> false;
            case PASSIVE -> target.kind() == LinkNodeId.NodeKind.SKILL || target.kind() == LinkNodeId.NodeKind.JOINT;
            case JOINT -> target.kind() == LinkNodeId.NodeKind.SKILL || target.kind() == LinkNodeId.NodeKind.JOINT;
        };
    }

    private static List<LinkNodeId> route(LinkNodeId source, Map<LinkNodeId, LinkNodeId> outgoing) {
        List<LinkNodeId> route = new ArrayList<>();
        Set<LinkNodeId> visited = new HashSet<>();
        LinkNodeId current = source;
        while (outgoing.containsKey(current) && visited.add(current)) {
            current = outgoing.get(current);
            route.add(current);
        }
        return route;
    }

    private static boolean hasCycle(Map<LinkNodeId, LinkNodeId> outgoing) {
        for (LinkNodeId node : outgoing.keySet()) {
            Set<LinkNodeId> visited = new HashSet<>();
            LinkNodeId current = node;
            while (outgoing.containsKey(current)) {
                if (!visited.add(current)) return true;
                current = outgoing.get(current);
            }
        }
        return false;
    }

    private static void issue(List<GraphValidationResult.Issue> issues, ValidationCode code, String message, LinkEdge edge) {
        issues.add(new GraphValidationResult.Issue(code, message, edge.sourceNodeId(), edge.targetNodeId()));
    }
}
