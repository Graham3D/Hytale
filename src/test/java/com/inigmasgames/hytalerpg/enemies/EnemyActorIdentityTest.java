package com.inigmasgames.hytalerpg.enemies;

import com.hypixel.hytale.codec.ExtraInfo;
import com.inigmasgames.hytalerpg.execution.hytale.EnemyStaging;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class EnemyActorIdentityTest {
    @Test void savedEnemyComponentsAcceptNativeRegistryDefaultValidation(){
        var info=new ExtraInfo();
        var visited=new HashSet<com.hypixel.hytale.codec.Codec<?>>();
        assertDoesNotThrow(()->EnemyShieldProjection.CODEC.validateDefaults(info,visited));
        assertDoesNotThrow(()->EnemyActorIdentity.CODEC.validateDefaults(info,visited));
        assertDoesNotThrow(()->EnemyEngagementClock.CODEC.validateDefaults(info,visited));
        assertDoesNotThrow(()->EnemyProjectileReceipt.CODEC.validateDefaults(info,visited));
        assertDoesNotThrow(()->EnemyStaging.CODEC.validateDefaults(info,visited));
    }
    @Test void savedIdentityRoundTripsWithoutInventingARoleOrReward(){
        var saved=new EnemyActorIdentity.State(UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID(),
                UUID.randomUUID(),UUID.randomUUID(),3,"Larva_Void");
        var component=new EnemyActorIdentity(saved);
        var decoded=EnemyActorIdentity.CODEC.decode(EnemyActorIdentity.CODEC.encode(component,new ExtraInfo()),new ExtraInfo());
        assertEquals(saved,decoded.state());
        assertEquals(saved,decoded.clone().state());
        assertThrows(IllegalArgumentException.class,()->new EnemyActorIdentity.State(saved.world(),saved.encounter(),
                saved.pack(),saved.logicalActor(),saved.nativeEntity(),-1,saved.nativeRole()));
        assertThrows(IllegalArgumentException.class,()->new EnemyActorIdentity.State(saved.world(),saved.encounter(),
                saved.pack(),saved.logicalActor(),saved.nativeEntity(),3,""));
    }
}
