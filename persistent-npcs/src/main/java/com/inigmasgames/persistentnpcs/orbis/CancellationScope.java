package com.inigmasgames.persistentnpcs.orbis;

import java.util.ArrayList;
import java.util.List;

/** Hierarchical cancellation state, mutated only by the Orbis coordinator. */
public final class CancellationScope {
    private final CancellationScope parent;
    private final List<CancellationScope> children = new ArrayList<>();
    private boolean cancelled;
    private CancellationReason reason;

    public CancellationScope() { this(null); }

    private CancellationScope(CancellationScope parent) {
        this.parent = parent;
    }

    public CancellationScope child() {
        CancellationScope child = new CancellationScope(this);
        children.add(child);
        if (isCancelled()) child.cancel(reason());
        return child;
    }

    public boolean cancel(CancellationReason value) {
        if (cancelled) return false;
        cancelled = true;
        reason = value == null ? CancellationReason.PROVIDER_FAILURE : value;
        for (CancellationScope child : List.copyOf(children)) child.cancel(reason);
        return true;
    }

    public boolean isCancelled() {
        return cancelled || parent != null && parent.isCancelled();
    }

    public CancellationReason reason() {
        return cancelled ? reason : parent == null ? null : parent.reason();
    }
}
