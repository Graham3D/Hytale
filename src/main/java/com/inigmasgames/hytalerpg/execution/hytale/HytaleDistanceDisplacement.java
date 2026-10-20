package com.inigmasgames.hytalerpg.execution.hytale;

import com.hypixel.hytale.component.*;
import com.hypixel.hytale.server.core.modules.entity.component.BoundingBox;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.collision.*;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.inigmasgames.hytalerpg.execution.area.AreaDisplacementPlanner;
import com.inigmasgames.hytalerpg.execution.math.Vec3;
import com.hypixel.hytale.math.shape.Box;
import org.joml.Vector3d;
import java.util.function.BooleanSupplier;

/** Existing native collision sweep and movement write, shared without a Skill context. */
public final class HytaleDistanceDisplacement {
    private HytaleDistanceDisplacement(){}
    public static double collisionFraction(Store<EntityStore> store,Ref<EntityStore> actor,Vec3 origin,Vec3 displacement){
        if(displacement.distanceSquared(Vec3.ZERO)<1e-12)return 1;
        var bounds=store.getComponent(actor,BoundingBox.getComponentType());if(bounds==null)return 0;
        var box=bounds.getBoundingBox();int samples=Math.max(1,(int)Math.ceil(displacement.length()/.25));if(samples>256)return 0;
        for(int i=0;i<=samples;i++){
            Vec3 p=origin.add(displacement.multiply((double)i/samples));
            for(double x:new double[]{box.min.x(),box.max.x()})for(double z:new double[]{box.min.z(),box.max.z()})
                if(!HytaleAreaQueries.loaded(store,p.add(new Vec3(x,0,z))))return Math.max(0,(i-1d)/samples);
        }
        var result=new CollisionResult();result.setDefaultPlayerSettings();result.disableCharacterCollisions();
        CollisionModule.findCollisions(new Box(box),vector(origin),vector(displacement),result,store);
        double fraction=1;for(int i=0;i<result.getBlockCollisionCount();i++)fraction=Math.min(fraction,result.getBlockCollision(i).collisionStart);
        double margin=.025/Math.max(.025,Math.sqrt(displacement.distanceSquared(Vec3.ZERO)));
        return Math.max(0,Math.min(1,fraction-(fraction<1?margin:0)));
    }
    public static AreaDisplacementPlanner.Plan plan(Store<EntityStore> store,Ref<EntityStore> target,Vec3 center,Vec3 forward,double meters,double scale,boolean grounded){
        var transform=store.getComponent(target,TransformComponent.getComponentType());var bounds=store.getComponent(target,BoundingBox.getComponentType());
        if(transform==null||bounds==null)throw new IllegalStateException("DISPLACEMENT_NATIVE_GEOMETRY_MISSING");
        return AreaDisplacementPlanner.push(vec(transform.getPosition()),center,forward,meters,scale,grounded,
                (point,segment)->collisionFraction(store,target,point,segment),
                point->HytaleAreaQueries.ground(store,point.add(new Vec3(0,bounds.getBoundingBox().min.y()+.15,0)),new Vec3(0,-1,0),.35).isPresent());
    }
    public static double apply(Store<EntityStore> store,Ref<EntityStore> target,Vec3 expectedStart,AreaDisplacementPlanner.Plan plan,BooleanSupplier current){
        if(!store.isInThread())throw new IllegalStateException("DISPLACEMENT_WRONG_WORLD_THREAD");
        if(!current.getAsBoolean()||!target.isValid()||target.getStore()!=store||plan.distance()<=0)return 0;
        var transform=store.getComponent(target,TransformComponent.getComponentType());
        if(transform==null||!vec(transform.getPosition()).equals(expectedStart))return 0;
        // Recheck the actual swept route immediately before the authoritative write.
        if(collisionFraction(store,target,expectedStart,plan.destination().subtract(expectedStart))<1)return 0;
        var player=store.getComponent(target,Player.getComponentType());
        if(player==null&&store.getComponent(target,com.hypixel.hytale.server.npc.entities.NPCEntity.getComponentType())==null)return 0;
        if(player==null)transform.setPosition(vector(plan.destination()));
        else {double fall=player.getCurrentFallDistance();player.moveTo(target,plan.destination().x(),plan.destination().y(),plan.destination().z(),store);player.setCurrentFallDistance(Math.max(fall,player.getCurrentFallDistance()));}
        var observed=vec(transform.getPosition());
        if(observed.distanceSquared(plan.destination())>.0001)throw new IllegalStateException("DISPLACEMENT_NATIVE_WRITE_NOT_CONFIRMED");
        return observed.subtract(expectedStart).horizontalLength();
    }
    private static Vector3d vector(Vec3 value){return new Vector3d(value.x(),value.y(),value.z());}
    private static Vec3 vec(Vector3d value){return new Vec3(value.x(),value.y(),value.z());}
}
