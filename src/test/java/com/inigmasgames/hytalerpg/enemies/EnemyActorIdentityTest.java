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
        component.captureNativeVisualScale(0.8f);
        var decoded=EnemyActorIdentity.CODEC.decode(EnemyActorIdentity.CODEC.encode(component,new ExtraInfo()),new ExtraInfo());
        assertEquals(saved,decoded.state());
        assertEquals(0.8f,decoded.nativeVisualScale());
        assertEquals(saved,decoded.clone().state());
        assertEquals(0.8f,decoded.clone().nativeVisualScale());
        decoded.captureNativeVisualScale(0.9f);
        assertEquals(0.8f,decoded.nativeVisualScale());
        assertThrows(IllegalArgumentException.class,()->new EnemyActorIdentity.State(saved.world(),saved.encounter(),
                saved.pack(),saved.logicalActor(),saved.nativeEntity(),-1,saved.nativeRole()));
        assertThrows(IllegalArgumentException.class,()->new EnemyActorIdentity.State(saved.world(),saved.encounter(),
                saved.pack(),saved.logicalActor(),saved.nativeEntity(),3,""));
    }
}
