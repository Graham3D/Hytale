package com.inigmasgames.hytalerpg;

import com.google.gson.Gson;
import com.inigmasgames.hytalerpg.execution.area.AreaGeometry;
import com.inigmasgames.hytalerpg.execution.math.Vec3;
import com.inigmasgames.hytalerpg.execution.projectile.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class Stage07LoadTest {
    @Test void full512CarrierLoadHasBoundedBurstLedgersAndCompleteRootCleanup() {
        var registry=new ProjectileLifecycleRegistry();var effects=new ProjectileSecondaryEffects(registry);
        var contexts=new HashMap<UUID,com.inigmasgames.hytalerpg.execution.SkillExecutionContext>();
        // Fixture/catalog construction is not the operation being measured.
        for(int i=0;i<22;i++){var owner=new UUID(0,i+1);contexts.put(owner,Stage07ContinuationTest.context(owner,"shrapnel","accelerant","ballistics"));}
        var carriers=new ArrayList<ProjectileInstance>();long started=System.nanoTime();int next=0;
        for(var context:contexts.values()) {
            var seed=ProjectileExecutionPlan.generationZero(context,context.request().actorId(),Vec3.ZERO,Vec3.FORWARD,"fixture",24,0);
            for(int i=0;i<24&&next<512;i++,next++) {
                var p=new ProjectileInstance(Stage07ContinuationTest.copyPlan(seed,"root-"+next,"carrier-"+next));registry.register(p);carriers.add(p);
            }
        }
        assertEquals(512,registry.size());assertEquals("GLOBAL_PROJECTILE_BUDGET",registry.admission(UUID.randomUUID(),1));
        int hitLedgerEntries=0,burstCount=0;
        for(var carrier:carriers) {
            var context=contexts.get(carrier.plan().ownerId());assertTrue(carrier.acceptTarget("victim-0"));
            var burst=effects.afterDamage(carrier,context.compiledPlan(),Vec3.ZERO,1).burst().orElseThrow();burstCount++;
            for(int target=0;target<64;target++) {
                double x=(target%8)*.2-.8,z=(target/8)*.2-.8;
                assertTrue(burst.geometry().intersects(new AreaGeometry.Bounds(new Vec3(x,-.5,z),new Vec3(x+.1,.5,z+.1))));
                assertTrue(burst.acceptTarget("victim-"+target));assertFalse(burst.acceptTarget("victim-"+target));hitLedgerEntries++;
            }
            assertFalse(burst.acceptTarget("overflow"));assertTrue(effects.afterDamage(carrier,context.compiledPlan(),Vec3.ZERO,1).burst().isEmpty());
            assertTrue(carrier.observe(carrier.plan().maxLifetimeSeconds(),new Vec3(0,0,carrier.plan().maxDistance())).expired());
            registry.remove(carrier);carrier.terminate("MAX_LIFETIME",carrier.flight().lastPosition());
        }
        assertEquals(0,registry.size());assertEquals(0,registry.rootCount());assertEquals(32768,hitLedgerEntries);assertEquals(512,burstCount);
        System.out.println("STAGE07_LOAD "+new Gson().toJson(Map.of("carriers",512,"owners",22,"bursts",burstCount,"secondaryVictimLedgerEntries",hitLedgerEntries,
                "remainingCarriers",registry.size(),"remainingRoots",registry.rootCount(),"milliseconds",(System.nanoTime()-started)/1e6,
                "nativePhysicsMeasured",false,"traceDiskOrNetworkMeasured",false)));
    }
}
