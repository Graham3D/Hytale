package com.inigmasgames.hytalerpg.vfx;

import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.world.World;
import java.util.Map;

/** Sole presentation gateway. Missing recipes/effects are non-authoritative no-ops. */
public final class LinkTreeVfxService {
    private final Adapter adapter;
    private final Map<String, String> recipes;
    public LinkTreeVfxService(Adapter adapter, Map<String, String> recipes) {
        this.adapter = adapter; this.recipes = Map.copyOf(recipes);
    }
    public Result present(World world, Player actor, String recipeId) {
        String nativeEffect = recipes.get(recipeId);
        if (nativeEffect == null) return new Result(false, "UNMAPPED_RECIPE");
        try { return adapter.emit(world, actor, nativeEffect)
                ? new Result(true, "PRESENTED") : new Result(false, "ADAPTER_UNAVAILABLE"); }
        catch (Throwable ignored) { return new Result(false, "PRESENTATION_FAILURE"); }
    }
    @FunctionalInterface public interface Adapter { boolean emit(World world, Player actor, String nativeEffectId); }
    public record Result(boolean presented, String reason) { }
}
