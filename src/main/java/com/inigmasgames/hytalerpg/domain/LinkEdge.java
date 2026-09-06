package com.inigmasgames.hytalerpg.domain;

import java.util.Objects;

/** Gameplay-owned Link Tree edge. It intentionally carries no presentation coordinates. */
public record LinkEdge(int schemaVersion, EdgeId edgeId, LinkNodeId sourceNodeId, LinkNodeId targetNodeId) {
    public static final int CURRENT_SCHEMA = 1;
    public LinkEdge {
        if (schemaVersion != CURRENT_SCHEMA) throw new IllegalArgumentException("Unsupported edge schema: " + schemaVersion);
        Objects.requireNonNull(edgeId, "edgeId");
        Objects.requireNonNull(sourceNodeId, "sourceNodeId");
        Objects.requireNonNull(targetNodeId, "targetNodeId");
    }
    public static LinkEdge create(LinkNodeId source, LinkNodeId target) {
        return new LinkEdge(CURRENT_SCHEMA, EdgeId.create(), source, target);
    }
}
