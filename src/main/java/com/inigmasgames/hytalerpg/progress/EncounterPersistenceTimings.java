package com.inigmasgames.hytalerpg.progress;

import java.util.*;

/** Bounded diagnostic samples; never consulted for persistence or gameplay decisions. */
public final class EncounterPersistenceTimings {
    public enum Phase { JOURNAL_APPEND, JOURNAL_FORCE, CHECKPOINT_SERIALIZATION, CHECKPOINT_WRITE, CHECKPOINT_FORCE, GROUP_WAIT, DURABLE_ACK, CHECKPOINT_QUEUE, CHECKPOINT_SCHEDULING, CHECKPOINT_ROTATION, CHECKPOINT_BACKPRESSURE, GROUP_CPU, GROUP_WALL, ENCODE_VALIDATE, FOREGROUND_BARRIER_WAIT, MAINTENANCE_BARRIER_WAIT, SUCCESSOR_PREPARATION, SUCCESSOR_ACTIVATION, PREPARED_WAIT, FORCE_TO_COMPLETION, PRECOMMIT_VALIDATION, SEALED_OPERATION_TO_FORCE }
    private static final int LIMIT=8192;
    private static final class Samples {long count,total,max;final long[] values=new long[LIMIT];}
    private final EnumMap<Phase,Samples> phases=new EnumMap<>(Phase.class);
    private final TreeMap<Integer,Long> groupSizes=new TreeMap<>(),groupBytes=new TreeMap<>();
    private long rejections;private int maxPendingRecords,maxPendingOperations,maxCheckpointBacklog;
    private long preparedMisses,bundleCount,bundleBytes;
    private long forceCompletedNanos;
    private final TreeMap<Integer,Long> operationSizes=new TreeMap<>();
    private final List<Map<String,Integer>> batches=new ArrayList<>();
    public EncounterPersistenceTimings(){reset();}
    public synchronized void reset(){for(var p:Phase.values())phases.put(p,new Samples());groupSizes.clear();groupBytes.clear();rejections=0;maxPendingRecords=0;maxPendingOperations=0;maxCheckpointBacklog=0;preparedMisses=0;bundleCount=0;bundleBytes=0;forceCompletedNanos=0;operationSizes.clear();batches.clear();}
    public synchronized void operation(int records){operationSizes.merge(records,1L,Long::sum);}
    public synchronized void commitBatch(int records,int forces){if(batches.size()<LIMIT)batches.add(Map.of("records",records,"forces",forces));}
    public synchronized long forceCompletedNanos(){return forceCompletedNanos;}
    public synchronized void preparedMiss(){preparedMisses++;}
    public synchronized void bundle(int bytes){bundleCount++;bundleBytes+=bytes;}
    public synchronized Map<String,Object> v2(){return Map.of("preparedMisses",preparedMisses,"bundleCount",bundleCount,"bundleBytes",bundleBytes,"sealedOperationSizes",new TreeMap<>(operationSizes),"dequeuedCommitBatches",List.copyOf(batches));}
    public synchronized void group(int records,int bytes){groupSizes.merge(records,1L,Long::sum);groupBytes.merge(bytes,1L,Long::sum);}
    public synchronized void admissionRejected(){rejections++;}
    public synchronized void pending(int records,int operations){maxPendingRecords=Math.max(maxPendingRecords,records);maxPendingOperations=Math.max(maxPendingOperations,operations);}
    public synchronized void checkpointBacklog(int count){maxCheckpointBacklog=Math.max(maxCheckpointBacklog,count);}
    public synchronized long count(Phase phase){return phases.get(phase).count;}
    public synchronized Map<String,Object> grouping(){return Map.of("recordsPerGroup",new TreeMap<>(groupSizes),"bytesPerGroup",new TreeMap<>(groupBytes),"admissionRejections",rejections,"maxPendingRecords",maxPendingRecords,"maxPendingOperations",maxPendingOperations,"maxCheckpointBacklog",maxCheckpointBacklog);}
    public synchronized void record(Phase phase,long nanos){if(phase==Phase.JOURNAL_FORCE)forceCompletedNanos=System.nanoTime();var s=phases.get(phase);s.values[(int)(s.count++%LIMIT)]=nanos;s.total+=nanos;s.max=Math.max(s.max,nanos);}
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
