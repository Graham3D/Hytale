package com.inigmasgames.hytalerpg.diagnostics;

import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.*;

/** Owner-thread wall spans, including admission/lock waits. Diagnostic state never authorizes gameplay.
 * Installed 0.7 World increments getTick() at the END of tick(); TickingThread records its
 * complete wall duration immediately afterwards. At the next tick, getLastValue is that prior tick.
 * No worker receives the Store or World. No synthetic sample is promoted to a native measurement. */
public final class NativeRpgTickMetrics {
    private NativeRpgTickMetrics(){}
    public enum Phase { EXECUTION, DAMAGE, STATUS_FIELD, SUPPORT, SUMMON, PROGRESSION, HUD, LIFECYCLE }
    public static final int MAX_WORLDS=64;
    private static final Map<UUID,Tick> worlds=new HashMap<>();
    private static final ThreadLocal<Span> current=new ThreadLocal<>();
    private static volatile RpgSkillTracer trace=ignored->{};
    private static long rejectedWorlds;
    private static final class Tick {long id;final long[] nanos=new long[Phase.values().length];Tick(long id){this.id=id;}}
    public static void configure(RpgSkillTracer tracer){trace=Objects.requireNonNull(tracer);}
    public static Span enter(Store<EntityStore> store,Phase phase){
        long started=System.nanoTime();var world=store.getExternalData().getWorld();
        // Actual native ownership only. Off-thread calls are never mislabelled server ticks.
        if(!world.isInThread())return new Span(null,null,phase,started);
        return enter(world.getWorldConfig().getUuid(),world.getTick(),world.getBufferedTickLengthMetricSet().getLastValue(),phase,started);
    }
    static Span enter(UUID world,long nativeTick,long priorWorldNanos,Phase phase,long started){
        Tick tick;Map<String,Object> finished=null;
        synchronized(worlds){
            tick=worlds.get(world);
            if(tick==null){if(worlds.size()>=MAX_WORLDS){rejectedWorlds++;return new Span(null,null,phase,started);}tick=new Tick(nativeTick);worlds.put(world,tick);}
            if(tick.id!=nativeTick){
                var phases=new LinkedHashMap<String,Double>();long total=0;
                for(var p:Phase.values()){long n=tick.nanos[p.ordinal()];phases.put(p.name(),n/1e6);total+=n;}
                boolean adjacent=nativeTick==tick.id+1;
                finished=Map.of("world",world,"nativeTick",tick.id,"rpgWallMs",total/1e6,"exclusivePhaseMs",Map.copyOf(phases),
                        "wholeWorldTickMs",adjacent?priorWorldNanos/1e6:-1d,
                        "nonRpgRemainderMs",adjacent?Math.max(0,priorWorldNanos-total)/1e6:-1d,
                        "wholeWorldTickPaired",adjacent,"rejectedWorlds",rejectedWorlds,
                        "scope","NATIVE_OWNER_CALLBACK_WALL_TIME_NOT_DURABLE_ACK");
                tick=new Tick(nativeTick);worlds.put(world,tick);
            }
        }
        var span=new Span(tick,current.get(),phase,started);current.set(span);
        // Already bounded async diagnostics; serialization/admission cost belongs to this tick.
        if(finished!=null)trace.trace(RpgTraceRecord.create(null,RpgTraceEventType.NATIVE_RPG_TICK_SAMPLE,world.toString(),finished));
        return span;
    }
    public static final class Span implements AutoCloseable {
        private final Tick tick;private final Span parent;private final Phase phase;private final long start;
        private long children;private boolean closed;
        private Span(Tick tick,Span parent,Phase phase,long start){this.tick=tick;this.parent=parent;this.phase=phase;this.start=start;}
        @Override public void close(){
            if(closed)return;closed=true;if(tick==null)return;
            long elapsed=System.nanoTime()-start;
            synchronized(worlds){tick.nanos[phase.ordinal()]+=Math.max(0,elapsed-children);}
            if(parent!=null)parent.children+=elapsed;
            current.set(parent);
        }
    }
}
