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
    /** Procedural gameplay-readability template; supplied authoritative dimensions are never inferred from art. */
    public void presentArea(World world, com.inigmasgames.hytalerpg.execution.area.AreaGeometry shape,
                            String phase, double seconds) {
        if (shape.kind() != com.inigmasgames.hytalerpg.execution.area.AreaGeometry.Kind.DISC) {
            for (var segment : com.inigmasgames.hytalerpg.execution.area.AreaOutline.segments(shape))
                com.hypixel.hytale.server.core.modules.debug.DebugUtils.addLine(world,
                        new org.joml.Vector3d(segment.from().x(), segment.from().y(), segment.from().z()),
                        new org.joml.Vector3d(segment.to().x(), segment.to().y(), segment.to().z()),
                        phase.equals("IMPACT") ? com.hypixel.hytale.server.core.modules.debug.DebugUtils.COLOR_CYAN
                                : com.hypixel.hytale.server.core.modules.debug.DebugUtils.COLOR_YELLOW,
                        .035, (float) seconds, com.hypixel.hytale.server.core.modules.debug.DebugUtils.FLAG_NONE);
            return;
        }
        var origin = shape.origin();
        com.hypixel.hytale.server.core.modules.debug.DebugUtils.addDisc(world,
                new org.joml.Vector3d(origin.x(), origin.y() + .025, origin.z()), shape.radius(),
                phase.equals("IMPACT") ? com.hypixel.hytale.server.core.modules.debug.DebugUtils.COLOR_CYAN
                        : com.hypixel.hytale.server.core.modules.debug.DebugUtils.COLOR_YELLOW,
                (float) seconds, com.hypixel.hytale.server.core.modules.debug.DebugUtils.FLAG_NONE);
    }
    @FunctionalInterface public interface Adapter { boolean emit(World world, Player actor, String nativeEffectId); }
    /** Bounded procedural overhead core; no native projectile or gameplay interaction is spawned. */
    public void presentDescending(World world, com.inigmasgames.hytalerpg.execution.math.Vec3 position, String element, double seconds) {
        com.hypixel.hytale.server.core.modules.debug.DebugUtils.addSphere(world,
                new org.joml.Vector3d(position.x(),position.y(),position.z()),
                element.equals("FIRE") ? new org.joml.Vector3f(1f,.3f,.08f) : new org.joml.Vector3f(.35f,.8f,1f),
                .45,(float)seconds);
    }
    public record Result(boolean presented, String reason) { }
}
