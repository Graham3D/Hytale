package com.inigmasgames.hytalerpg.diagnostics;

import java.util.*;
import java.util.function.Consumer;
import java.util.function.LongSupplier;

/** Bounded values-only aggregation before JSON serialization. PERFORMANCE never samples or aggregates raw ticks. */
public final class SkillTraceRouter {
    public static final long PERIOD_NANOS=10_000_000_000L;
    public static final int MAX_WORLDS=64;
    private final Consumer<RpgTraceRecord> sink;
    private final LongSupplier clock;
    private final Map<String,Window> windows=new LinkedHashMap<>();
    private volatile SkillTraceLevel level;
    private long suppressedCompileStages;
    private static final class Window {
        final long started;final RpgTraceRecord first;RpgTraceRecord last;
        long count,paired,over4,over8;double total,max,worldTotal,worldMax;
        final Map<String,Double> phaseTotal=new TreeMap<>(),phaseMax=new TreeMap<>();
        Window(long now,RpgTraceRecord record){started=now;first=record;}
    }
    public SkillTraceRouter(SkillTraceLevel level,Consumer<RpgTraceRecord> sink){this(level,sink,System::nanoTime);}
    public SkillTraceRouter(SkillTraceLevel level,Consumer<RpgTraceRecord> sink,LongSupplier clock){this.level=Objects.requireNonNull(level);this.sink=Objects.requireNonNull(sink);this.clock=Objects.requireNonNull(clock);}
    public SkillTraceLevel level(){return level;}
    public synchronized long suppressedCompileStages(){return suppressedCompileStages;}
    public synchronized void setLevel(SkillTraceLevel next){
        Objects.requireNonNull(next);flush();var old=level;level=next;
        sink.accept(RpgTraceRecord.create(null,RpgTraceEventType.TRACE_LEVEL_CHANGED,UUID.randomUUID().toString(),
                Map.of("previousLevel",old,"level",next,"rawNativeTicks",next==SkillTraceLevel.PERFORMANCE,"suppressedCompileStages",suppressedCompileStages)));
    }
    public synchronized void trace(RpgTraceRecord record){
        if(record.eventType()==RpgTraceEventType.COMPILE_STAGE&&level!=SkillTraceLevel.DETAILED
                &&!"FAIL".equals(record.details().get("validationResult"))&&!record.details().containsKey("failureCode")){
            suppressedCompileStages++;return;
        }
        if(record.eventType()!=RpgTraceEventType.NATIVE_RPG_TICK_SAMPLE||level==SkillTraceLevel.PERFORMANCE){sink.accept(record);return;}
        var details=record.details();Object world=details.get("world");
        if(world==null||!validNumber(details.get("rpgWallMs"))){sink.accept(record);return;} // Malformed diagnostics must remain visible.
        String key=world.toString();if(key.length()>96){sink.accept(record);return;}
        long now=clock.getAsLong();var w=windows.get(key);
        if(w==null){if(windows.size()>=MAX_WORLDS){sink.accept(record);return;}w=new Window(now,record);windows.put(key,w);}
        double ms=((Number)details.get("rpgWallMs")).doubleValue();w.count++;w.total+=ms;w.max=Math.max(w.max,ms);if(ms>4)w.over4++;if(ms>8)w.over8++;w.last=record;
        if(Boolean.TRUE.equals(details.get("wholeWorldTickPaired"))&&validNumber(details.get("wholeWorldTickMs"))){double whole=((Number)details.get("wholeWorldTickMs")).doubleValue();w.paired++;w.worldTotal+=whole;w.worldMax=Math.max(w.worldMax,whole);}
        if(details.get("exclusivePhaseMs") instanceof Map<?,?> phases)for(var phase:NativeRpgTickMetrics.Phase.values()){
            Object value=phases.get(phase.name());if(validNumber(value)){double n=((Number)value).doubleValue();w.phaseTotal.merge(phase.name(),n,Double::sum);w.phaseMax.merge(phase.name(),n,Math::max);}
        }
        if(now-w.started>=PERIOD_NANOS){publish(key,w);windows.remove(key);}
    }
    private static boolean validNumber(Object value){return value instanceof Number n&&Double.isFinite(n.doubleValue())&&n.doubleValue()>=0;}
    private void publish(String world,Window w){
        var d=new LinkedHashMap<String,Object>();d.put("world",world);d.put("level",level);d.put("sampleCount",w.count);
        d.put("firstNativeTick",w.first.details().getOrDefault("nativeTick",-1));d.put("lastNativeTick",w.last.details().getOrDefault("nativeTick",-1));
        d.put("firstTimestamp",w.first.timestamp());d.put("lastTimestamp",w.last.timestamp());
        d.put("rpgWallTotalMs",w.total);d.put("rpgWallMeanMs",w.total/w.count);d.put("rpgWallMaxMs",w.max);d.put("over4MsCount",w.over4);d.put("over8MsCount",w.over8);
        d.put("wholeWorldPairedCount",w.paired);d.put("wholeWorldTotalMs",w.worldTotal);d.put("wholeWorldMaxMs",w.worldMax);
        d.put("exclusivePhaseTotalMs",Map.copyOf(w.phaseTotal));d.put("exclusivePhaseMaxMs",Map.copyOf(w.phaseMax));
        d.put("rejectedWorlds",w.last.details().getOrDefault("rejectedWorlds",0));d.put("suppressedCompileStages",suppressedCompileStages);
        d.put("scope","NATIVE_TICK_AGGREGATE_NOT_PERCENTILE_QUALIFICATION");d.put("rawSamplesRetained",false);d.put("percentileQualification",false);
        sink.accept(RpgTraceRecord.create(null,RpgTraceEventType.NATIVE_RPG_TICK_SUMMARY,world,d));
    }
    public synchronized void flush(){for(var e:windows.entrySet())publish(e.getKey(),e.getValue());windows.clear();}
}
