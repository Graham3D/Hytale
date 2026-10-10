package com.inigmasgames.hytalerpg.execution.hytale;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.shape.Box;
import com.hypixel.hytale.server.core.modules.collision.CollisionModule;
import com.hypixel.hytale.server.core.modules.collision.CollisionResult;
import com.hypixel.hytale.server.core.modules.collision.BlockCollisionData;
import com.hypixel.hytale.server.core.modules.entity.component.BoundingBox;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.inigmasgames.hytalerpg.execution.math.Vec3;
import java.util.Optional;
import org.joml.Vector3d;

/** First-hit native terrain selection with full-body landing checks. */
final class HytaleTeleportTarget {
    private HytaleTeleportTarget() { }

    static Optional<Vec3> select(Store<EntityStore> store,Ref<EntityStore> player,Vec3 feet,Vec3 aim,double range) {
        if(!HytaleAreaQueries.loaded(store,feet))return Optional.empty();
        Vec3 eye=feet.add(new Vec3(0,1.35,0));
        Vec3 ray=aim.normalized().multiply(range+1.35);
        var hit=firstHit(store,eye,ray);
        if(hit==null||hazard(hit)||hit.collisionNormal.y()<-.5)return Optional.empty();
        Vec3 contact=eye.add(ray.multiply(hit.collisionStart));
        Vec3 segment=contact.subtract(eye);
        for(int i=0,n=Math.max(1,(int)Math.ceil(segment.length()));i<=n;i++)
            if(!HytaleAreaQueries.loaded(store,eye.add(segment.multiply((double)i/n))))return Optional.empty();
        double x=contact.x(),z=contact.z();
        Vec3 landing;
        if(hit.collisionNormal.y()>.5){
            landing=new Vec3(x,contact.y()+.01,z);
        }else{
            // Probe the solid column that supplied the visible first wall face.
            x=Math.floor(x-hit.collisionNormal.x()*.08)+.5;
            z=Math.floor(z-hit.collisionNormal.z()*.08)+.5;
            Vec3 top=new Vec3(x,feet.y()+7,z);
            landing=safeGround(store,top,14).orElse(null);
            if(landing==null||landing.y()+.01<contact.y())return Optional.empty();
        }
        if(!valid(store,player,feet,landing,range))return Optional.empty();
        return Optional.of(landing);
    }

    static boolean valid(Store<EntityStore> store,Ref<EntityStore> actor,Vec3 start,Vec3 landing,double range) {
        if(!com.inigmasgames.hytalerpg.execution.TeleportScaling.withinBounds(start,landing,range)
                ||!HytaleAreaQueries.loaded(store,landing))return false;
        var bounds=store.getComponent(actor,BoundingBox.getComponentType());
        if(bounds==null)return false;
        var box=bounds.getBoundingBox();
        for(double x:new double[]{box.min.x(),box.max.x()})for(double z:new double[]{box.min.z(),box.max.z()})
            if(!HytaleAreaQueries.loaded(store,landing.add(new Vec3(x,0,z)))
                    ||safeGround(store,landing.add(new Vec3(x,box.min.y()+.15,z)),.35).isEmpty())return false;
        return clearBody(store,actor,landing);
    }

    static boolean clearBody(Store<EntityStore> store,Ref<EntityStore> actor,Vec3 landing) {
        var bounds=store.getComponent(actor,BoundingBox.getComponentType());
        if(bounds==null||!HytaleAreaQueries.loaded(store,landing))return false;
        var collision=new CollisionResult();collision.setDefaultPlayerSettings();collision.disableCharacterCollisions();
        CollisionModule.findCollisions(new Box(bounds.getBoundingBox()),new Vector3d(landing.x(),landing.y(),landing.z()),new Vector3d(0,.01,0),collision,store);
        return collision.getBlockCollisionCount()==0;
    }

    static Optional<Vec3> safeGround(Store<EntityStore> store,Vec3 origin,double depth) {
        if(!HytaleAreaQueries.loaded(store,origin))return Optional.empty();
        Vec3 down=new Vec3(0,-depth,0);
        var hit=firstHit(store,origin,down);
        if(hit==null||hit.collisionNormal.y()<.5||hazard(hit))return Optional.empty();
        return Optional.of(origin.add(down.multiply(hit.collisionStart)).add(new Vec3(0,.01,0)));
    }

    private static boolean hazard(BlockCollisionData hit) {
        return hit.willDamage||hit.fluidId>0||hit.fluid!=null;
    }

    private static BlockCollisionData firstHit(Store<EntityStore> store,Vec3 origin,Vec3 displacement) {
        var collision=new CollisionResult();collision.setDefaultPlayerSettings();collision.disableCharacterCollisions();
        collision.enableDamageBlocks();collision.setDamageBlocking(true);
        CollisionModule.findCollisions(new Box(-.01,-.01,-.01,.01,.01,.01),
                new Vector3d(origin.x(),origin.y(),origin.z()),
                new Vector3d(displacement.x(),displacement.y(),displacement.z()),collision,store);
        BlockCollisionData first=null;
        for(int i=0;i<collision.getBlockCollisionCount();i++){
            var next=collision.getBlockCollision(i);
            if(next.collisionStart>=0&&next.collisionStart<=1
                    &&(first==null||next.collisionStart<first.collisionStart))first=next;
        }
        return first;
    }
}
