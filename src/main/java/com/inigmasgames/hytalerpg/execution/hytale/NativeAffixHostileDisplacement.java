package com.inigmasgames.hytalerpg.execution.hytale;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.ComponentAccessor;
import com.hypixel.hytale.math.shape.Box;
import com.hypixel.hytale.server.core.modules.collision.CollisionModule;
import com.hypixel.hytale.server.core.modules.collision.CollisionResult;
import com.hypixel.hytale.server.core.modules.entity.component.BoundingBox;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.movement.MovementStatesComponent;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import com.inigmasgames.hytalerpg.combat.status.ControlProfile;
import com.inigmasgames.hytalerpg.execution.area.AreaDisplacementPlanner;
import com.inigmasgames.hytalerpg.execution.math.Vec3;
import com.inigmasgames.hytalerpg.gear.GearNativeItems;
import com.inigmasgames.hytalerpg.gear.GearEffectSnapshot;
import java.util.function.Predicate;
import java.util.function.ToDoubleBiFunction;
import org.joml.Vector3d;

/** Hostile push/pull request enters the existing bounded swept planner after live target WA-082. */
public final class NativeAffixHostileDisplacement {
    private NativeAffixHostileDisplacement() { }
    public static AreaDisplacementPlanner.Plan apply(Store<EntityStore> store,Ref<EntityStore> victim,
            Vec3 center,boolean pull,double requested,ControlProfile control) {
        return apply(store,store,victim,center,pull,requested,0,control);
    }
    /** Hostile player moves through Player.moveTo; NPCs retain the bounded native transform path. */
    public static AreaDisplacementPlanner.Plan apply(Store<EntityStore> store,ComponentAccessor<EntityStore> accessor,
            Ref<EntityStore> victim,Vec3 center,boolean pull,double requested,double pullCore,ControlProfile control) {
        if(store==null||victim==null||!victim.isValid()||center==null||control==null)
            throw new IllegalArgumentException("INVALID_HOSTILE_DISPLACEMENT");
        var transform=store.getComponent(victim,TransformComponent.getComponentType());
        var bounds=store.getComponent(victim,BoundingBox.getComponentType());
        var npc=store.getComponent(victim,NPCEntity.getComponentType());
        var player=store.getComponent(victim,Player.getComponentType());
        if(transform==null||bounds==null||(npc==null||npc.getRole()==null)&&player==null)
            throw new IllegalStateException("HOSTILE_DISPLACEMENT_NATIVE_TARGET_UNAVAILABLE");
        var start=vec(transform.getPosition());
        if(!Double.isFinite(pullCore)||pullCore<0)throw new IllegalArgumentException("INVALID_PULL_CORE");
        if(pull)requested=Math.min(requested,Math.max(0,center.subtract(start).horizontalLength()-pullCore));
        var movement=player==null?null:store.getComponent(victim,MovementStatesComponent.getComponentType());
        boolean grounded=player==null?npc.getRole().isOnGround():movement!=null&&movement.getMovementStates()!=null
                &&movement.getMovementStates().onGround;
        var plan=plan(start,center,pull,requested,control,grounded,
                GearNativeItems.recipientEffects(victim,store),
                (point,segment)->collision(store,bounds,point,segment),
                point->HytaleAreaQueries.ground(store,
                    point.add(new Vec3(0,bounds.getBoundingBox().min.y()+.15,0)),
                    new Vec3(0,-1,0),.35).isPresent());
        if(plan.distance()>0){
            if(player!=null)player.moveTo(victim,plan.destination().x(),plan.destination().y(),plan.destination().z(),accessor);
            else applyNpcDestination(transform,plan);
        }
        return plan;
    }
    public static void applyNpcDestination(TransformComponent transform,AreaDisplacementPlanner.Plan plan) {
        if(plan.distance()>0)transform.setPosition(new Vector3d(plan.destination().x(),plan.destination().y(),plan.destination().z()));
    }
    public static AreaDisplacementPlanner.Plan plan(Vec3 start,Vec3 center,boolean pull,double requested,
            ControlProfile control,boolean grounded,GearEffectSnapshot targetGear,
            ToDoubleBiFunction<Vec3,Vec3> collision,Predicate<Vec3> supported) {
        if(targetGear==null||control==null)throw new IllegalArgumentException("INVALID_HOSTILE_DISPLACEMENT");
        double reduced=requested*Math.max(0,1-targetGear.percent("WA-082"));
        return AreaDisplacementPlanner.plan(start,center,pull,reduced,control.displacementMultiplier(),
                grounded,collision,supported);
    }
    private static double collision(Store<EntityStore> store,BoundingBox bounds,
                                    Vec3 point,Vec3 segment) {
        int samples=Math.max(1,(int)Math.ceil(segment.length()/.25));
        var box=bounds.getBoundingBox();
        for(int i=0;i<=samples;i++){
            var p=point.add(segment.multiply((double)i/samples));
            for(double x:new double[]{box.min.x(),box.max.x()})for(double z:new double[]{box.min.z(),box.max.z()})
                if(!HytaleAreaQueries.loaded(store,p.add(new Vec3(x,0,z))))
                    return Math.max(0,(i-1d)/samples);
        }
        var result=new CollisionResult();result.setDefaultPlayerSettings();result.disableCharacterCollisions();
        CollisionModule.findCollisions(new Box(box),new Vector3d(point.x(),point.y(),point.z()),
                new Vector3d(segment.x(),segment.y(),segment.z()),result,store);
        double fraction=1;
        for(int i=0;i<result.getBlockCollisionCount();i++)
            fraction=Math.min(fraction,result.getBlockCollision(i).collisionStart);
        double margin=.025/Math.max(.025,segment.length());
        return Math.max(0,Math.min(1,fraction-(fraction<1?margin:0)));
    }
    private static Vec3 vec(Vector3d point){return new Vec3(point.x,point.y,point.z);}
}
