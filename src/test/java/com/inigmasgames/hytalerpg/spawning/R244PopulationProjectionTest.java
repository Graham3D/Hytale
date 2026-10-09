package com.inigmasgames.hytalerpg.spawning;
import com.hypixel.hytale.server.spawning.world.WorldNPCSpawnStat;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static com.inigmasgames.hytalerpg.spawning.NativePopulationRoles.Category.*;
class R244PopulationProjectionTest {
    @org.junit.jupiter.api.BeforeAll static void options() throws Exception{com.hypixel.hytale.server.core.Options.parse(new String[]{"--bare"});}
    private static WorldNPCSpawnStat stat(int index){
        try{
            var uf=sun.misc.Unsafe.class.getDeclaredField("theUnsafe");uf.setAccessible(true);
            var stat=(WorldNPCSpawnStat)((sun.misc.Unsafe)uf.get(null)).allocateInstance(WorldNPCSpawnStat.class);
            var role=WorldNPCSpawnStat.class.getDeclaredField("roleIndex");role.setAccessible(true);role.setInt(stat,index);return stat;
        }catch(ReflectiveOperationException e){throw new AssertionError(e);}
    }
    @Test void nativeStoredValuesEnabledDisabledEnabledAndNativeReconstruction(){
        var hostile=stat(1);var wildlife=stat(2);
        var fish=stat(3);var stats=List.of(hostile,wildlife,fish);
        var rows=List.of(new PopulationWeightPlan.Row(1,HOSTILE,1,5,1,true),new PopulationWeightPlan.Row(2,WILDLIFE,1,7,1,true),new PopulationWeightPlan.Row(3,OTHER,1,3,1,true));
        hostile.adjustActual(5);wildlife.adjustActual(7);fish.adjustActual(3);fish.setUnspawnable(true);
        for(boolean enabled:List.of(true,false,true)){
            // Hytale rebuilt native targets inside its tick; the selection seam must overwrite these before selection.
            stats.forEach(s->s.setExpected(40));
            NativePopulationBalance.publish(stats,PopulationWeightPlan.calculate(120,rows,.65,.35,enabled));
            assertEquals(enabled?52:40,hostile.getExpected(),1e-9);assertEquals(enabled?28:40,wildlife.getExpected(),1e-9);
            assertEquals(40,fish.getExpected(),1e-9);assertEquals(15,stats.stream().mapToInt(WorldNPCSpawnStat::getActual).sum());
            assertTrue(fish.isUnspawnable());
        }
    }
    @Test void nativeHookUnusedAndTeardownAreNoOps() throws Exception {
        com.inigmasgames.hytale.patch.NativePopulationProjectionHook.beforeSelection(null);
        var calls=new java.util.concurrent.atomic.AtomicInteger();
        var hook=com.inigmasgames.hytale.patch.NativePopulationProjectionHook.install(s->calls.incrementAndGet());
        com.inigmasgames.hytale.patch.NativePopulationProjectionHook.beforeSelection(null);assertEquals(1,calls.get());
        hook.close();com.inigmasgames.hytale.patch.NativePopulationProjectionHook.beforeSelection(null);assertEquals(1,calls.get());
    }
}
