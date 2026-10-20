package com.inigmasgames.hytalerpg.enemies;

import com.hypixel.hytale.codec.ExtraInfo;
import com.inigmasgames.hytalerpg.combat.damage.WeaponDamageExecution;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class EnemyProjectileReceiptTest {
    @Test void savedOriginalHolderRetainsExactAcceptedRootAndRejectsOtherProjectiles(){
        var world=UUID.randomUUID();var logicalActor=UUID.randomUUID();var nativeActor=UUID.randomUUID();
        var projectile=UUID.randomUUID();var identity=new WeaponDamageExecution.Identity(
                world,logicalActor,"durable-root-7","Skeleton_Scout_Bow_Shoot","arrow-0");
        var offense=new EnemyOffenseSnapshot(identity,3,"pinned-role-1","channels-1","balance-1",1,
                Map.of("Projectile",13d),13,Map.of("Projectile",15d),null,0);
        var state=new EnemyProjectileReceipt.State(projectile,nativeActor,"Skeleton_Scout_Arrow",13,offense,1,0);
        var saved=new EnemyProjectileReceipt(state);
        var decoded=EnemyProjectileReceipt.CODEC.decode(EnemyProjectileReceipt.CODEC.encode(saved,new ExtraInfo()),new ExtraInfo());
        assertEquals(state,decoded.state());
        assertEquals("durable-root-7",decoded.state().offense().identity().rootId());
        decoded.state().require(projectile,nativeActor,world,3,"pinned-role-1","Skeleton_Scout_Arrow");
        assertThrows(IllegalStateException.class,()->decoded.state().require(UUID.randomUUID(),nativeActor,world,3,
                "pinned-role-1","Skeleton_Scout_Arrow"));
        assertThrows(IllegalStateException.class,()->decoded.state().require(projectile,UUID.randomUUID(),world,3,
                "pinned-role-1","Skeleton_Scout_Arrow"));
        assertThrows(IllegalStateException.class,()->decoded.state().require(projectile,nativeActor,world,4,
                "pinned-role-1","Skeleton_Scout_Arrow"));
        assertThrows(IllegalArgumentException.class,()->new EnemyProjectileReceipt.State(projectile,nativeActor,
                "Skeleton_Scout_Arrow",13,offense,0,0));
    }
}
