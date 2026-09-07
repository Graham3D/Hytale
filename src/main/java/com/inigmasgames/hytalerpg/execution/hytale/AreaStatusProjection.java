package com.inigmasgames.hytalerpg.execution.hytale;

import com.hypixel.hytale.component.Component;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

/** Transient marker owned by the native target entity, not a retained global target-reference map. */
public final class AreaStatusProjection implements Component<EntityStore> {
    private static ComponentType<EntityStore, AreaStatusProjection> type;
    double elapsed;
    double retryAfter;
    boolean failureLogged;
    public static ComponentType<EntityStore, AreaStatusProjection> getComponentType() { return type; }
    public static void bind(ComponentType<EntityStore, AreaStatusProjection> registered) { type = registered; }
    @Override public AreaStatusProjection clone() {
        AreaStatusProjection copy = new AreaStatusProjection(); copy.elapsed = elapsed;
        copy.retryAfter = retryAfter; copy.failureLogged = failureLogged; return copy;
    }
}
