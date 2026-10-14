package com.inigmasgames.hytalerpg.execution.hytale;

import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class NativeEnemyFlockExtensionTest {
    @Test void loadedNeighborWithoutRequestedEnvironmentHasNoExtensionHeadroom(){
        var chunk=new com.hypixel.hytale.server.spawning.world.component.ChunkSpawnData();
        var counted=new com.hypixel.hytale.server.spawning.world.component.ChunkSpawnedNPCData();
        assertThrows(NullPointerException.class,()->chunk.getEnvironmentSpawnData(42));
        assertEquals(0,NativeEnemyFlockExtension.environmentHeadroom(chunk,counted,42));
        assertFalse(new NativeEnemyFlockExtension.Headroom(1,8,8,1,8,
                List.of(new NativeEnemyFlockExtension.Headroom.Chunk(0,
                        NativeEnemyFlockExtension.environmentHeadroom(chunk,counted,42)))).admits(1));
    }
    @Test void requiresEveryNativePopulationBudgetToCoverTheAdditionalMembers(){
        var enough=new NativeEnemyFlockExtension.Headroom(8,12,12,3,6,
                List.of(new NativeEnemyFlockExtension.Headroom.Chunk(1,4),
                        new NativeEnemyFlockExtension.Headroom.Chunk(0,3)));
        assertTrue(enough.admits(2));
        assertFalse(enough.admits(4));
        assertFalse(new NativeEnemyFlockExtension.Headroom(11,12,12,3,6,enough.nearby()).admits(2));
        assertFalse(new NativeEnemyFlockExtension.Headroom(8,12,12,5,6,enough.nearby()).admits(2));
        assertFalse(new NativeEnemyFlockExtension.Headroom(8,12,12,3,6,
                List.of(new NativeEnemyFlockExtension.Headroom.Chunk(2,3))).admits(2));
        assertFalse(new NativeEnemyFlockExtension.Headroom(8,12,12,3,6,List.of()).admits(2));
        assertEquals("WORLD_MAXIMUM",new NativeEnemyFlockExtension.Headroom(11,12,12,3,6,enough.nearby()).rejection(2));
        assertEquals("ENVIRONMENT_EXPECTED",new NativeEnemyFlockExtension.Headroom(8,12,12,5,6,enough.nearby()).rejection(2));
        assertEquals("CHUNK_HEADROOM",new NativeEnemyFlockExtension.Headroom(8,12,12,3,6,
                List.of(new NativeEnemyFlockExtension.Headroom.Chunk(2,3))).rejection(2));
    }
    @Test void selectedNativePrefixAndRollbackTailKeepExactMixedIdentities(){
        var world=UUID.randomUUID();var encounter=UUID.randomUUID();
        var job=new NativeEnemySpawnGroups.Job(world,42,7,"Skeleton_Frost_Fighter",2,3,3);
        var receipt=new NativeEnemySpawnGroups.Reservation(world,encounter,1);
        var first=UUID.randomUUID();var second=UUID.randomUUID();var third=UUID.randomUUID();
        var members=List.of(
                new NativeEnemySpawnGroups.Member(first,"Skeleton_Frost_Soldier",new EnemyStaging.State(world,encounter,first,1,8)),
                new NativeEnemySpawnGroups.Member(second,"Skeleton_Frost_Fighter",new EnemyStaging.State(world,encounter,second,1,8)),
                new NativeEnemySpawnGroups.Member(third,"Skeleton_Frost_Soldier",new EnemyStaging.State(world,encounter,third,1,8)));
        var reserved=new NativeEnemySpawnGroups.Group(job,receipt,members,false);
        var split=NativeEnemyFlockExtension.partition(reserved,1);
        assertEquals(List.of(first),split.kept().members().stream().map(NativeEnemySpawnGroups.Member::entity).toList());
        assertEquals(List.of(second,third),split.removed().members().stream().map(NativeEnemySpawnGroups.Member::entity).toList());
        assertEquals(1,split.kept().job().expectedMembers());assertEquals(2,split.removed().job().expectedMembers());
        assertEquals(receipt,split.kept().reservation());assertEquals(receipt,split.removed().reservation());
        assertNull(NativeEnemyFlockExtension.partition(reserved,0).kept());
        assertSame(reserved,NativeEnemyFlockExtension.partition(reserved,3).kept());
        assertThrows(IllegalArgumentException.class,()->NativeEnemyFlockExtension.partition(reserved,4));
        assertThrows(IllegalArgumentException.class,()->NativeEnemyFlockExtension.partition(
                new NativeEnemySpawnGroups.Group(job,receipt,members,true),1));
    }
}
