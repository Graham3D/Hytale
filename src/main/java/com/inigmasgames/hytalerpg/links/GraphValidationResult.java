package com.inigmasgames.hytalerpg.links;

import com.inigmasgames.hytalerpg.domain.LinkNodeId;
import com.inigmasgames.hytalerpg.domain.PassiveSlot;

import java.util.List;
import java.util.Map;

public record GraphValidationResult(boolean valid, List<Issue> issues, Map<PassiveSlot, List<LinkNodeId>> routes) {
    public GraphValidationResult { issues = List.copyOf(issues); routes = Map.copyOf(routes); }
    public static GraphValidationResult valid(Map<PassiveSlot, List<LinkNodeId>> routes) {
        return new GraphValidationResult(true, List.of(), routes);
    }
    public static GraphValidationResult invalid(List<Issue> issues) {
        return new GraphValidationResult(false, issues, Map.of());
    }
    public Issue firstIssue() { return issues.getFirst(); }
    public record Issue(ValidationCode code, String message, LinkNodeId source, LinkNodeId target) {}
}
