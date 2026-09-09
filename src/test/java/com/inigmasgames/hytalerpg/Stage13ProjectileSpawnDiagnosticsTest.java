package com.inigmasgames.hytalerpg;

import com.inigmasgames.hytalerpg.execution.hytale.ProjectileSpawnDiagnostics;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class Stage13ProjectileSpawnDiagnosticsTest {
    @Test void everySpawnStageSurvivesRollbackWithoutChangingOriginalException(){
        for(var stage:ProjectileSpawnDiagnostics.Stage.values()){
            var e=new IllegalArgumentException("secret credential "+"x".repeat(10000));ProjectileSpawnDiagnostics.mark(e,stage);
            var d=ProjectileSpawnDiagnostics.describe(e,Map.of("reason","ATOMIC_BATCH_ROLLBACK","batchSize",1));
            assertEquals(stage.name(),d.get("spawnStage"));assertEquals("ATOMIC_BATCH_ROLLBACK",d.get("reason"));assertEquals("IllegalArgumentException",d.get("spawnError"));
            assertFalse(d.toString().contains("secret"));assertTrue(d.toString().length()<1000);assertEquals(IllegalArgumentException.class,e.getClass());
        }
    }
    @Test void exactNativeEnumMapFailureHasStableSafeCodeAndFirstBoundaryWins(){
        var e=assertThrows(IllegalArgumentException.class,()->new EnumMap(java.util.Map.of()));
        ProjectileSpawnDiagnostics.mark(e,ProjectileSpawnDiagnostics.Stage.NATIVE_ALLOCATION);ProjectileSpawnDiagnostics.mark(e,ProjectileSpawnDiagnostics.Stage.CONTINUATION_SETUP);
        var d=ProjectileSpawnDiagnostics.describe(e,Map.of());assertEquals("NATIVE_EMPTY_ENUM_MAP",d.get("spawnFailureCode"));assertEquals("NATIVE_ALLOCATION",d.get("spawnStage"));
        assertTrue(d.get("spawnFailureOrigin").toString().startsWith("java.util.EnumMap."));assertEquals(1,e.getSuppressed().length);
    }
    @Test void preCarrierBatchErrorsRemainDistinguishable(){
        var d=ProjectileSpawnDiagnostics.describe(new IllegalStateException("sensitive"),Map.of("reason","ATOMIC_BATCH_ROLLBACK"));
        assertEquals("PRE_CARRIER_BATCH",d.get("spawnStage"));assertFalse(d.toString().contains("sensitive"));
    }
}
