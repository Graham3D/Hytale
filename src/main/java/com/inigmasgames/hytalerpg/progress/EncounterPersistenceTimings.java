package com.inigmasgames.hytalerpg.progress;

import java.util.*;

/** Bounded diagnostic samples; never consulted for persistence or gameplay decisions. */
public final class EncounterPersistenceTimings {
    public enum Phase { JOURNAL_APPEND, JOURNAL_FORCE, CHECKPOINT_SERIALIZATION, CHECKPOINT_WRITE, CHECKPOINT_FORCE }
    private static final int LIMIT=8192;
    private static final class Samples {long count,total,max;final long[] values=new long[LIMIT];}
    private final EnumMap<Phase,Samples> phases=new EnumMap<>(Phase.class);
    public EncounterPersistenceTimings(){reset();}
    public synchronized void reset(){for(var p:Phase.values())phases.put(p,new Samples());}
    public synchronized void record(Phase phase,long nanos){var s=phases.get(phase);s.values[(int)(s.count++%LIMIT)]=nanos;s.total+=nanos;s.max=Math.max(s.max,nanos);}
    public synchronized long totalNanos(Phase phase){return phases.get(phase).total;}
    public synchronized Map<String,Object> snapshot(){
        var out=new LinkedHashMap<String,Object>();
        for(var phase:Phase.values()){
            var s=phases.get(phase);int n=(int)Math.min(s.count,LIMIT);var sorted=Arrays.copyOf(s.values,n);Arrays.sort(sorted);
            out.put(phase.name(),Map.of("count",s.count,"totalMs",s.total/1e6,"maxMs",s.max/1e6,"retainedSamples",n,
                "p50Ms",n==0?0:sorted[(int)Math.ceil(n*.50)-1]/1e6,"p95Ms",n==0?0:sorted[(int)Math.ceil(n*.95)-1]/1e6,
                "p99Ms",n==0?0:sorted[(int)Math.ceil(n*.99)-1]/1e6));
        }return out;
    }
}
