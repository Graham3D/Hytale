package com.inigmasgames.hytalerpg.execution.hytale;

import org.junit.jupiter.api.Test;
import java.util.List;
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
    }
}
