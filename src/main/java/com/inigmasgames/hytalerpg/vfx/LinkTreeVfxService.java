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
                            String phase, String element, boolean trap, double seconds) {
        var palette=AreaPresentationTemplate.color(element,phase);
        var color=new org.joml.Vector3f(palette.red(),palette.green(),palette.blue());
        if (shape.kind() != com.inigmasgames.hytalerpg.execution.area.AreaGeometry.Kind.DISC) {
            for (var segment : com.inigmasgames.hytalerpg.execution.area.AreaOutline.segments(shape))
                com.hypixel.hytale.server.core.modules.debug.DebugUtils.addLine(world,
                        new org.joml.Vector3d(segment.from().x(), segment.from().y(), segment.from().z()),
                        new org.joml.Vector3d(segment.to().x(), segment.to().y(), segment.to().z()),
                        color,
                        .035, (float) seconds, com.hypixel.hytale.server.core.modules.debug.DebugUtils.FLAG_NONE);
            return;
        }
        var origin = shape.origin();
        com.hypixel.hytale.server.core.modules.debug.DebugUtils.addDisc(world,
                new org.joml.Vector3d(origin.x(), origin.y() + AreaPresentationTemplate.GROUND_LIFT, origin.z()), shape.radius(), color,
                (float) seconds, com.hypixel.hytale.server.core.modules.debug.DebugUtils.FLAG_NO_SOLID);
        // A second inset ring distinguishes a preparing warning from an active boundary without filling the ground.
        // These player-owned PvE fields are friendly to players; no unsupported PvP allegiance is implied.
        if(AreaPresentationTemplate.warning(phase))
            com.hypixel.hytale.server.core.modules.debug.DebugUtils.addDisc(world,
                    new org.joml.Vector3d(origin.x(),origin.y()+AreaPresentationTemplate.GROUND_LIFT,origin.z()),shape.radius()*.9,color,
                    (float)seconds,com.hypixel.hytale.server.core.modules.debug.DebugUtils.FLAG_NO_SOLID);
        if(trap && !phase.startsWith("IMPACT")) {
            double radius=Math.min(.45,shape.radius());
            for(int i=0;i<4;i++) {
                double a=i*Math.PI/2,b=(i+1)*Math.PI/2;
                com.hypixel.hytale.server.core.modules.debug.DebugUtils.addLine(world,
                        new org.joml.Vector3d(origin.x()+Math.cos(a)*radius,origin.y()+.1,origin.z()+Math.sin(a)*radius),
                        new org.joml.Vector3d(origin.x()+Math.cos(b)*radius,origin.y()+.1,origin.z()+Math.sin(b)*radius),color,
                        .035,(float)seconds,com.hypixel.hytale.server.core.modules.debug.DebugUtils.FLAG_NONE);
            }
        }
    }
    @FunctionalInterface public interface Adapter { boolean emit(World world, Player actor, String nativeEffectId); }
    /** Endpoint-fit outline of the actual volume, with bounded lifetimes and no gameplay interaction. */
    public void presentConnection(World world,com.inigmasgames.hytalerpg.execution.connection.ConnectionShape shape,String element,String phase,double seconds) {
         var palette=AreaPresentationTemplate.color(element,phase);var color=new org.joml.Vector3f(palette.red(),palette.green(),palette.blue());
         if(shape.kind()==com.inigmasgames.hytalerpg.execution.connection.ConnectionShape.Kind.CAPSULE) {
             var end=shape.end();com.hypixel.hytale.server.core.modules.debug.DebugUtils.addSphere(world,
                     new org.joml.Vector3d(end.x(),end.y(),end.z()),color,shape.radius(),(float)seconds);
             connectionLine(world,shape.start(),end,color,seconds);return;
         }
        if(shape.kind()==com.inigmasgames.hytalerpg.execution.connection.ConnectionShape.Kind.CYLINDER) {
            var origin=shape.start();
            com.hypixel.hytale.server.core.modules.debug.DebugUtils.addSphere(world,new org.joml.Vector3d(origin.x(),origin.y(),origin.z()),color,.2,(float)seconds);
            com.hypixel.hytale.server.core.modules.debug.DebugUtils.addDisc(world,new org.joml.Vector3d(origin.x(),origin.y(),origin.z()),shape.radius(),color,
                    (float)seconds,com.hypixel.hytale.server.core.modules.debug.DebugUtils.FLAG_NO_SOLID);
            return;
        }
        var side=shape.right().multiply(shape.width()/2);var up=shape.up().multiply(shape.height()/2);
        var corners=java.util.List.of(side.add(up),side.subtract(up),side.multiply(-1).subtract(up),side.multiply(-1).add(up));
        for(int i=0;i<4;i++) {
            var offset=corners.get(i);var a=shape.start().add(offset);var b=shape.end().add(offset);
            connectionLine(world,a,b,color,seconds);
            connectionLine(world,a,shape.start().add(corners.get((i+1)%4)),color,seconds);
            connectionLine(world,b,shape.end().add(corners.get((i+1)%4)),color,seconds);
        }
    }
    private static void connectionLine(World world,com.inigmasgames.hytalerpg.execution.math.Vec3 a,com.inigmasgames.hytalerpg.execution.math.Vec3 b,
            org.joml.Vector3f color,double seconds) {
        com.hypixel.hytale.server.core.modules.debug.DebugUtils.addLine(world,new org.joml.Vector3d(a.x(),a.y(),a.z()),new org.joml.Vector3d(b.x(),b.y(),b.z()),color,
                .025,(float)seconds,com.hypixel.hytale.server.core.modules.debug.DebugUtils.FLAG_NONE);
    }
    /** Bounded procedural overhead core; no native projectile or gameplay interaction is spawned. */
    public void presentDescending(World world, com.inigmasgames.hytalerpg.execution.math.Vec3 position, String element, double seconds) {
        com.hypixel.hytale.server.core.modules.debug.DebugUtils.addSphere(world,
                new org.joml.Vector3d(position.x(),position.y(),position.z()),
                element.equals("FIRE") ? new org.joml.Vector3f(1f,.3f,.08f) : new org.joml.Vector3f(.35f,.8f,1f),
                .45,(float)seconds);
    }
    public record Result(boolean presented, String reason) { }
}
