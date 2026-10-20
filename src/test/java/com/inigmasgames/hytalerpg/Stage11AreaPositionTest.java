package com.inigmasgames.hytalerpg;

import com.inigmasgames.hytalerpg.execution.area.*;
import com.inigmasgames.hytalerpg.execution.math.Vec3;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Stage11 cohort P in progress: pure planning/ICD tests, not yet native displacement proof. */
class Stage11AreaPositionTest {
    AreaDisplacementPlanner.Plan plan(boolean pull,double requested,double scale){return AreaDisplacementPlanner.plan(new Vec3(5,0,0),Vec3.ZERO,pull,requested,scale,true,(p,s)->1,p->true);}
    @Test void vacuumMovesTowardCenterExactlyTwoMetres(){var p=plan(true,2,1);assertEquals(new Vec3(3,0,0),p.destination());assertEquals(2,p.distance());}
    @Test void repulsionMovesAwayExactlyTwoPointFiveMetres(){var p=plan(false,2.5,1);assertEquals(new Vec3(7.5,0,0),p.destination());assertEquals(2.5,p.distance());}
    @Test void eliteHalvesDisplacementAndImmuneDoesNotMove(){assertEquals(1,plan(true,2,.5).distance());assertEquals(1.25,plan(false,2.5,.5).distance());assertEquals(0,plan(true,2,0).distance());}
    @Test void vacuumCannotCrossCenter(){var p=AreaDisplacementPlanner.plan(new Vec3(.5,0,0),Vec3.ZERO,true,2,1,true,(a,b)->1,a->true);assertEquals(Vec3.ZERO,p.destination());assertEquals(.5,p.distance());}
    @Test void centeredTargetDoesNotInventRandomPushDirection(){var p=AreaDisplacementPlanner.plan(Vec3.ZERO,Vec3.ZERO,false,2.5,1,true,(a,b)->1,a->true);assertEquals(0,p.distance());assertEquals("AT_EFFECT_CENTER",p.reason());}
    @Test void movementRemainsHorizontal(){var p=AreaDisplacementPlanner.plan(new Vec3(5,2,0),Vec3.ZERO,true,2,1,true,(a,b)->1,a->true);assertEquals(2,p.destination().y());assertEquals(3,p.destination().x());}
    @Test void collisionClampsToSweptFractionAndStops(){var count=new AtomicInteger();var p=AreaDisplacementPlanner.plan(new Vec3(5,0,0),Vec3.ZERO,true,2,1,true,(a,b)->count.incrementAndGet()==3?.5:1,a->true);assertEquals(.625,p.distance());assertEquals(3,count.get());assertEquals("NATIVE_COLLISION",p.reason());}
    @Test void unsupportedPathDoesNotTeleportAcrossGap(){var p=AreaDisplacementPlanner.plan(new Vec3(5,0,0),Vec3.ZERO,true,2,1,true,(a,b)->1,a->a.x()>=4.5);assertEquals(.5,p.distance());assertEquals("NO_SUPPORTED_PATH",p.reason());}
    @Test void airborneOrUnsupportedOriginIsRejectedBeforeSweep(){for(boolean grounded:new boolean[]{false,true}){var count=new AtomicInteger();var p=AreaDisplacementPlanner.plan(new Vec3(5,0,0),Vec3.ZERO,true,2,1,grounded,(a,b)->{count.incrementAndGet();return 1;},a->false);assertEquals(0,p.distance());assertEquals(0,count.get());}}
    @Test void maximumWorkIsBoundedAndExtraImpactPushFitsWithoutGuessingVelocity(){var count=new AtomicInteger();var p=AreaDisplacementPlanner.plan(new Vec3(5,0,0),Vec3.ZERO,false,2.5*1.75,1,true,(a,b)->{assertTrue(b.horizontalLength()<=.25+1e-9);count.incrementAndGet();return 1;},a->true);assertEquals(4.375,p.distance());assertEquals(18,count.get());}
    @Test void malformedCollisionFractionFailsClosed(){for(double fraction:new double[]{Double.NaN,Double.POSITIVE_INFINITY,-.1,1.1}){var p=AreaDisplacementPlanner.plan(new Vec3(5,0,0),Vec3.ZERO,false,2.5,1,true,(a,b)->fraction,a->true);assertEquals(0,p.distance());assertEquals("COLLISION_UNAVAILABLE",p.reason());}}
    @Test void malformedDistanceAndControlScaleReject(){for(double amount:new double[]{-1,9,Double.NaN})assertThrows(IllegalArgumentException.class,()->plan(true,amount,1));assertThrows(IllegalArgumentException.class,()->plan(false,2.5,1.1));}
    @Test void oneAttemptPerTargetPerRootPerExactSecond(){var l=new RootDisplacementLedger();assertEquals("PASS",l.claim("a",0));assertEquals("ROOT_TARGET_DISPLACEMENT_ICD",l.claim("a",.999999));assertEquals("PASS",l.claim("a",1));assertEquals(1,l.size());}
    @Test void distinctTargetsAndRootLedgersAreIndependent(){var a=new RootDisplacementLedger();var b=new RootDisplacementLedger();assertEquals("PASS",a.claim("victim",10));assertEquals("PASS",a.claim("other",10));assertEquals("PASS",b.claim("victim",10));}
    @Test void targetLedgerIsBoundedAndExpiredEntriesArePruned(){var l=new RootDisplacementLedger();for(int i=0;i<256;i++)assertEquals("PASS",l.claim("t"+i,0));assertEquals("ROOT_DISPLACEMENT_TARGET_BUDGET",l.claim("extra",.5));assertEquals("PASS",l.claim("extra",1));assertEquals(1,l.size());}
    @Test void badIdentityAndTimeCannotResetAllowance(){var l=new RootDisplacementLedger();l.claim("a",10);assertEquals("DISPLACEMENT_CLOCK_REVERSED",l.claim("b",9));assertEquals("INVALID_DISPLACEMENT_ID_OR_CLOCK",l.claim("a",Double.NaN));assertEquals("INVALID_DISPLACEMENT_ID_OR_CLOCK",l.claim(" ",10));assertEquals("ROOT_TARGET_DISPLACEMENT_ICD",l.claim("a",10));}
}
