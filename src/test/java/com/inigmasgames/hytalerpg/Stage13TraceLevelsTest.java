package com.inigmasgames.hytalerpg;

import com.inigmasgames.hytalerpg.diagnostics.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class Stage13TraceLevelsTest {
    static RpgTraceRecord tick(String world,long id,double ms){return RpgTraceRecord.create(null,RpgTraceEventType.NATIVE_RPG_TICK_SAMPLE,world,
            Map.of("world",world,"nativeTick",id,"rpgWallMs",ms,"wholeWorldTickPaired",true,"wholeWorldTickMs",ms+2,
                    "exclusivePhaseMs",Map.of("EXECUTION",ms),"rejectedWorlds",3));}
    @Test void normalAggregatesAllTicksWithoutClaimingPercentileQualification(){
        var out=new ArrayList<RpgTraceRecord>();var clock=new AtomicLong();var r=new SkillTraceRouter(SkillTraceLevel.NORMAL,out::add,clock::get);
        for(int i=0;i<600;i++){if(i==599)clock.set(SkillTraceRouter.PERIOD_NANOS);r.trace(tick("w",i,i==599?9:1));}
        assertEquals(1,out.size());var summary=out.getFirst();assertEquals(RpgTraceEventType.NATIVE_RPG_TICK_SUMMARY,summary.eventType());var d=summary.details();
        assertEquals(600L,d.get("sampleCount"));assertEquals(608d,d.get("rpgWallTotalMs"));assertEquals(9d,d.get("rpgWallMaxMs"));
        assertEquals(1L,d.get("over4MsCount"));assertEquals(1L,d.get("over8MsCount"));assertEquals(600L,d.get("wholeWorldPairedCount"));
        assertEquals(0L,d.get("firstNativeTick"));assertEquals(599L,d.get("lastNativeTick"));assertEquals(3,d.get("rejectedWorlds"));
        assertEquals(Map.of("EXECUTION",608d),d.get("exclusivePhaseTotalMs"));assertEquals(Map.of("EXECUTION",9d),d.get("exclusivePhaseMaxMs"));
        assertEquals(false,d.get("rawSamplesRetained"));assertEquals(false,d.get("percentileQualification"));r.flush();assertEquals(1,out.size());
    }
    @Test void performanceRetainsEveryRawNativeSampleUnmodified(){
        var out=new ArrayList<RpgTraceRecord>();var r=new SkillTraceRouter(SkillTraceLevel.PERFORMANCE,out::add);var input=new ArrayList<RpgTraceRecord>();
        for(int i=0;i<1000;i++){var record=tick("w",i,i/100d);input.add(record);r.trace(record);}r.flush();assertEquals(input,out);
    }
    @Test void everyEventDrivenEventIsRetainedInEveryLevelExceptSuccessfulCompileStages(){
        for(var level:SkillTraceLevel.values()){
            var out=new ArrayList<RpgTraceRecord>();var r=new SkillTraceRouter(level,out::add);
            for(var type:RpgTraceEventType.values())if(type!=RpgTraceEventType.COMPILE_STAGE&&type!=RpgTraceEventType.NATIVE_RPG_TICK_SAMPLE){
                var record=RpgTraceRecord.create(UUID.randomUUID(),type,"root",Map.of("rootCastId","root","skillInstanceId","instance","failureCode","TEST"));r.trace(record);assertSame(record,out.getLast());
            }
        }
    }
    @Test void compileSuccessIsDetailedOnlyAndCompileFailureAlwaysVisible(){
        for(var level:SkillTraceLevel.values()){
            var out=new ArrayList<RpgTraceRecord>();var r=new SkillTraceRouter(level,out::add);
            var success=RpgTraceRecord.create(null,RpgTraceEventType.COMPILE_STAGE,"cast",Map.of("stage","BASE_POWER"));
            var failure=RpgTraceRecord.create(null,RpgTraceEventType.COMPILE_STAGE,"cast",Map.of("validationResult","FAIL"));
            var code=RpgTraceRecord.create(null,RpgTraceEventType.COMPILE_STAGE,"cast",Map.of("failureCode","BAD_GRAPH"));
            r.trace(success);r.trace(failure);r.trace(code);assertEquals(level==SkillTraceLevel.DETAILED?List.of(success,failure,code):List.of(failure,code),out);
            assertEquals(level==SkillTraceLevel.DETAILED?0:1,r.suppressedCompileStages());
        }
    }
    @Test void levelChangeFlushesPartialWindowBeforeSwitchAndCloseFlushIsIdempotent(){
        var out=new ArrayList<RpgTraceRecord>();var r=new SkillTraceRouter(SkillTraceLevel.NORMAL,out::add);r.trace(tick("w",1,1));
        r.setLevel(SkillTraceLevel.PERFORMANCE);assertEquals(RpgTraceEventType.NATIVE_RPG_TICK_SUMMARY,out.get(0).eventType());assertEquals(RpgTraceEventType.TRACE_LEVEL_CHANGED,out.get(1).eventType());
        var raw=tick("w",2,2);r.trace(raw);assertSame(raw,out.get(2));r.setLevel(SkillTraceLevel.NORMAL);r.trace(tick("w",3,3));r.flush();int size=out.size();r.flush();assertEquals(size,out.size());
        assertEquals(1L,out.getLast().details().get("sampleCount"));assertEquals(3d,out.getLast().details().get("rpgWallTotalMs"));
    }
    @Test void aggregationStateIsBoundedAndMalformedOrOverflowSamplesRemainVisible(){
        var out=new ArrayList<RpgTraceRecord>();var r=new SkillTraceRouter(SkillTraceLevel.NORMAL,out::add);
        for(int i=0;i<SkillTraceRouter.MAX_WORLDS;i++)r.trace(tick("world"+i,1,1));assertTrue(out.isEmpty());
        var overflow=tick("extra",1,1);r.trace(overflow);assertSame(overflow,out.getLast());
        var malformed=RpgTraceRecord.create(null,RpgTraceEventType.NATIVE_RPG_TICK_SAMPLE,"w",Map.of("world","w","rpgWallMs",Double.NaN));
        r.trace(malformed);assertSame(malformed,out.getLast());r.flush();assertEquals(66,out.size());
    }
    @Test void traceLevelsValidateConfigurationAndSupportExplicitDebugAlias(){
        assertEquals(SkillTraceLevel.DETAILED,SkillTraceLevel.parse("debug"));assertEquals(SkillTraceLevel.PERFORMANCE,SkillTraceLevel.parse("performance"));
        assertThrows(IllegalArgumentException.class,()->new SkillTraceConfiguration(true,"INVALID",8,4,true));
        assertEquals("NORMAL",new SkillTraceConfiguration(true,"normal",8,4,true).level());
    }
}
