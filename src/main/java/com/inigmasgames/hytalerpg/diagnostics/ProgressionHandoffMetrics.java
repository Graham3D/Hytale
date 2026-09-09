package com.inigmasgames.hytalerpg.diagnostics;

import java.util.*;

/** Diagnostics only: bounded durable-result values, never a reward queue or recovery log. */
public final class ProgressionHandoffMetrics {
    private static final int LIMIT=256;
    private record Result(String event,long observed,long committed){}
    private final Map<UUID,ArrayDeque<Result>> ready=new HashMap<>();
    private int count;private long dropped,published;
    public synchronized void committed(UUID player,String event,long observedNanos){
        if(count>=LIMIT){dropped++;return;}
        ready.computeIfAbsent(player,ignored->new ArrayDeque<>()).add(new Result(event,observedNanos,System.nanoTime()));count++;
    }
    /** Called after the native owner's committed projection/read; never from the persistence worker. */
    public void published(UUID player,RpgSkillTracer trace){
        var values=new ArrayList<Result>();synchronized(this){
            var queue=ready.get(player);if(queue==null)return;
            for(int i=0;i<8&&!queue.isEmpty();i++){values.add(queue.remove());count--;published++;}
            if(queue.isEmpty())ready.remove(player);
        }
        long now=System.nanoTime();for(var value:values)trace.trace(RpgTraceRecord.create(player,RpgTraceEventType.PROGRESSION_OWNER_PUBLISHED,value.event(),
                Map.of("eventId",value.event(),"eventToDurableMs",value.observed()>0?(value.committed()-value.observed())/1e6:-1d,
                        "durableToOwnerMs",(now-value.committed())/1e6,"endToEndMs",value.observed()>0?(now-value.observed())/1e6:-1d,
                        "originKnown",value.observed()>0,"publication","OWNER_COMMITTED_STATE_OBSERVED_NOT_CLIENT_RENDER_PROOF")));
    }
    public synchronized Map<String,Object> snapshot(){long now=System.nanoTime();return Map.of("readyCompletions",count,"maxReadyCompletions",LIMIT,"droppedDiagnosticSamples",dropped,
            "ownerPublished",published,"oldestReadyMs",ready.values().stream().flatMap(Collection::stream).mapToLong(v->now-v.committed()).max().orElse(0)/1e6);}
}
