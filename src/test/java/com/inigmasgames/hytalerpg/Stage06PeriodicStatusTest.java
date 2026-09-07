package com.inigmasgames.hytalerpg;

import com.inigmasgames.hytalerpg.combat.status.PeriodicStatusRuntime;
import com.inigmasgames.hytalerpg.combat.status.PeriodicStatusRuntime.Kind;
import com.inigmasgames.hytalerpg.combat.status.PeriodicStatusRuntime.Source;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class Stage06PeriodicStatusTest {
    @Test void oneSecondTicksAndFinalRemainderIgnoreFrameRateAndNeverUseDirectCoefficient() {
        for (double frame : new double[]{.05, .25, .5}) {
            var runtime = new PeriodicStatusRuntime<String,String>(); var port = new Port(); var source = source("fire_bolt", Kind.BURN);
            runtime.apply(source,"snapshot", "target", .1, 20, 4.5, 1, 1, 0, port);
            for (int i=1; i <= Math.ceil(4.5/frame); i++) runtime.tick(source.owner(), Math.min(4.5,i*frame), port);
            assertEquals(.45, port.hits.stream().mapToDouble(Hit::coefficient).sum(), 1e-10);
            assertEquals(5, port.hits.size()); assertEquals(.05, port.hits.getLast().coefficient, 1e-10);
            assertEquals(0, runtime.size());
        }
    }
    @Test void weakerRefreshCannotReduceBurnAndStrongerSnapshotDoesNotRewriteAccruedTime() {
        var runtime = new PeriodicStatusRuntime<String,String>(); var port = new Port(); var source=source("wall_of_fire",Kind.BURN);
        runtime.apply(source,"original", "target", .1, 20, 4, 1, 1, 0, port);
        runtime.apply(source,"weak", "target", .1, 10, 4, 1, 1, .25, port);
        runtime.apply(source,"strong", "target", .1, 40, 4, 1, 1, .5, port);
        runtime.tick(source.owner(), 1, port);
        assertEquals(List.of("original","strong"),port.hits.stream().map(Hit::context).toList());
        assertEquals(.05,port.hits.get(0).coefficient,1e-12); assertEquals(.05,port.hits.get(1).coefficient,1e-12);
        assertEquals(1,runtime.view(source.victim(),Kind.BURN,1).stacks());
    }
    @Test void differentSkillsDoNotOverwriteBurnAndOwnerTeardownKeepsOtherSources() {
        var runtime = new PeriodicStatusRuntime<String,String>(); var port=new Port(); var first=source("fire_bolt",Kind.BURN);
        var second=new Source(UUID.randomUUID(),"wall_of_fire",first.victim(),Kind.BURN);
        runtime.apply(first,"first","target",.1,20,4,1,1,0,port);
        runtime.apply(second,"second","target",.1,20,5,1,1,0,port);
        runtime.cancel(first.owner(),1,port); assertEquals(1,runtime.size());
        assertEquals(1,runtime.view(first.victim(),Kind.BURN,1).stacks());
        runtime.cancel(first.owner(),1,port); runtime.tick(second.owner(),1,port);
        assertEquals("second",port.hits.getFirst().context);
    }
    @Test void poisonAddsWithoutRetroactiveStackDamageAndCapsSourceAtThree() {
        var runtime = new PeriodicStatusRuntime<String,String>(); var port=new Port(); var source=source("venom_spray",Kind.POISON);
        runtime.apply(source,"same","target",.06,20,6,1,3,0,port);
        runtime.apply(source,"same","target",.06,20,6,1,3,.5,port);
        runtime.tick(source.owner(),1,port); assertEquals(.09,port.hits.getFirst().coefficient,1e-12);
        runtime.apply(source,"same","target",.06,20,6,8,3,1,port);
        assertEquals(3,runtime.view(source.victim(),Kind.POISON,1).stacks());
    }
    @Test void poisonVictimCapKeepsStrongestPackagesDeterministically() {
        var runtime = new PeriodicStatusRuntime<String,String>(); var port=new Port(); UUID victim=UUID.randomUUID();
        for(int i=0;i<5;i++) runtime.apply(new Source(new UUID(0,i+1),"poison_cloud",victim,Kind.POISON),"s"+i,"target",.06,i+1,8,3,3,0,port);
        assertEquals(12,runtime.view(victim,Kind.POISON,0).stacks()); assertEquals(4,runtime.size());
        for(int i=0;i<5;i++) runtime.tick(new UUID(0,i+1),1,port);
        assertFalse(port.hits.stream().anyMatch(h->h.context.equals("s0"))); assertEquals(4,port.hits.size());
    }
    @Test void nativeFailureCannotReplayPartialTickAndLongGapDropsCatchup() {
        var runtime=new PeriodicStatusRuntime<String,String>(); var port=new Port(); var source=source("fire_bolt",Kind.BURN);
        runtime.apply(source,"source","target",.1,20,4,1,1,0,port); port.fail=true;
        assertThrows(IllegalStateException.class,()->runtime.tick(source.owner(),1,port));
        port.fail=false; runtime.tick(source.owner(),1,port); assertEquals(1,port.hits.size()); assertEquals(0,runtime.size());
        runtime.apply(source,"source","target",.1,20,4,1,1,2,port); runtime.tick(source.owner(),5,port);
        assertEquals(1,port.hits.size()); assertEquals(0,runtime.size());
    }
    @Test void expiredPoisonCannotRejectAnotherCastersLivePackage() {
        var runtime=new PeriodicStatusRuntime<String,String>(); var port=new Port(); UUID victim=UUID.randomUUID();
        for(int i=0;i<4;i++) runtime.apply(new Source(new UUID(0,i+1),"poison_cloud",victim,Kind.POISON),
                "old"+i,"target",.06,100,.5,3,3,0,port);
        var fresh=new Source(new UUID(0,5),"venom_spray",victim,Kind.POISON);
        assertEquals("APPLIED",runtime.apply(fresh,"fresh","target",.06,1,6,1,3,.75,port));
        assertEquals(1,runtime.size()); assertEquals(1,runtime.view(victim,Kind.POISON,.75).stacks());
        assertEquals(4,port.hits.size());
        assertEquals(.36,port.hits.stream().mapToDouble(Hit::coefficient).sum(),1e-12);
    }
    @Test void competingApplicationSettlesOneSecondTickBeforeFractionalEviction() {
        var runtime=new PeriodicStatusRuntime<String,String>(); var port=new Port(); UUID victim=UUID.randomUUID();
        for(int i=0;i<4;i++) runtime.apply(new Source(new UUID(0,i+1),"poison_cloud",victim,Kind.POISON),
                "old"+i,"target",.06,i+1,8,3,3,0,port);
        runtime.apply(new Source(new UUID(0,5),"venom_spray",victim,Kind.POISON),"new","target",.06,10,8,3,3,1.5,port);
        var evicted=port.hits.stream().filter(h->h.context.equals("old0")).toList();
        assertEquals(2,evicted.size()); assertEquals(.18,evicted.getFirst().coefficient,1e-12);
        assertEquals(.09,evicted.getLast().coefficient,1e-12);
        runtime.tick(new UUID(0,2),2,port);
        assertEquals(.36,port.hits.stream().filter(h->h.context.equals("old1")).mapToDouble(Hit::coefficient).sum(),1e-12);
    }
    @Test void lateCapturedApplicationClockCannotMoveRefreshBackwards() {
        var runtime=new PeriodicStatusRuntime<String,String>(); var port=new Port(); var source=source("fire_bolt",Kind.BURN);
        runtime.apply(source,"snapshot","target",.1,20,4,1,1,0,port); runtime.tick(source.owner(),.75,port);
        runtime.apply(source,"weaker","target",.1,10,4,1,1,.5,port);
        assertEquals(4,runtime.view(source.victim(),Kind.BURN,.75).remainingSeconds(),1e-12);
        runtime.tick(source.owner(),1,port); assertEquals(.1,port.hits.getFirst().coefficient,1e-12);
    }
    private static Source source(String skill,Kind kind) {return new Source(UUID.randomUUID(),skill,UUID.randomUUID(),kind);}
    private record Hit(String context,double coefficient,double seconds) { }
    private static class Port implements PeriodicStatusRuntime.Port<String,String> {
        final List<Hit> hits=new ArrayList<>(); boolean fail;
        public boolean tick(Source s,String c,String target,int index,double coefficient,double seconds) {
            hits.add(new Hit(c,coefficient,seconds)); if(fail)throw new IllegalStateException("native fixture failure"); return true;
        }
        public void changed(Source source,String target,PeriodicStatusRuntime.View view) { }
    }
}
