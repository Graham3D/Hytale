package com.inigmasgames.hytalerpg.enemies;

import com.inigmasgames.hytale.patch.NativeDamageReceiptHook;
import com.inigmasgames.hytalerpg.combat.damage.WeaponDamageExecution;
import com.inigmasgames.hytalerpg.execution.hytale.EnemyNativeStrikeScope;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class EnemyOriginalReceiptIdentityTest {
    @Test void acceptedRootAndNativeComponentDistinguishOverlappingOriginalHits(){
        var world=UUID.randomUUID();var logical=UUID.randomUUID();var nativeActor=UUID.randomUUID();
        var target=UUID.randomUUID();
        var first=new EnemyOffenseSnapshot(new WeaponDamageExecution.Identity(world,logical,"committed-root-1","native-bite","bite"),
                4,"binding","channels","balance",1,Map.of("Physical",20d),20,Map.of("Physical",20d),null,0);
        var second=new EnemyOffenseSnapshot(new WeaponDamageExecution.Identity(world,logical,"committed-root-2","native-bite","bite"),
                4,"binding","channels","balance",1,Map.of("Physical",20d),20,Map.of("Physical",20d),null,0);
        var context=new NativeDamageReceiptHook.Context(world,nativeActor,nativeActor,nativeActor,target,
                17,List.of(),"native-bite","bite-leaf",2,1,0,"Physical");
        var receipt=EnemyNativeStrikeScope.originalReceipt(first,nativeActor,target,context);
        assertEquals(64,receipt.length());
        assertEquals(receipt,EnemyNativeStrikeScope.originalReceipt(first,nativeActor,target,context));
        assertNotEquals(receipt,EnemyNativeStrikeScope.originalReceipt(second,nativeActor,target,context));
        assertEquals(receipt,EnemyNativeStrikeScope.originalReceipt(first,nativeActor,target,
                new NativeDamageReceiptHook.Context(world,nativeActor,nativeActor,nativeActor,target,
                        18,List.of(),"native-bite","bite-leaf",2,1,0,"Physical")));
        assertThrows(IllegalStateException.class,()->EnemyNativeStrikeScope.originalReceipt(first,nativeActor,target,
                new NativeDamageReceiptHook.Context(world,nativeActor,nativeActor,UUID.randomUUID(),target,
                        17,List.of(),"native-bite","bite-leaf",2,1,0,"Physical")));
        assertThrows(IllegalStateException.class,()->EnemyNativeStrikeScope.originalReceipt(first,nativeActor,target,
                new NativeDamageReceiptHook.Context(world,nativeActor,nativeActor,nativeActor,target,
                        17,List.of(),"native-bite","bite-leaf",2,1,0,"Fire")));
    }
}
