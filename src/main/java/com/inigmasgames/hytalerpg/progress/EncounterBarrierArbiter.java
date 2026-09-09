package com.inigmasgames.hytalerpg.progress;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.util.*;
import java.util.concurrent.TimeUnit;

/** One physical barrier at a time. No monitor is held across an OS force call. */
final class EncounterBarrierArbiter {
    enum Kind { FOREGROUND, AUTHORITY, PREPARATION, BUNDLE, MANIFEST }
    private final EncounterPersistenceTimings timings;
    private int foreground, suspended;
    private Kind active;
    private long overlapAttempts;
    private final EnumMap<Kind,Long> counts=new EnumMap<>(Kind.class);
    private final List<Map<String,Object>> events=new ArrayList<>();
    EncounterBarrierArbiter(EncounterPersistenceTimings timings){this.timings=timings;}
    synchronized void queued(){foreground++;notifyAll();}
    synchronized void completed(){if(--foreground<0)throw new IllegalStateException("BARRIER_FOREGROUND_ACCOUNTING");notifyAll();}
    /** The writer cannot encode/force while awaiting bounded preparation/checkpoint capacity.
     * Explicit suspension lets maintenance progress, rather than deadlocking its own prerequisite. */
    synchronized void suspend(){suspended++;notifyAll();}
    synchronized void resume(){if(--suspended<0)throw new IllegalStateException("BARRIER_SUSPEND_ACCOUNTING");notifyAll();}
    void force(FileChannel channel,Kind kind)throws IOException {
        long waitStart=System.nanoTime(),deadline=waitStart+TimeUnit.MILLISECONDS.toNanos(EncounterGroupCommit.DEADLINE_MILLIS);
        synchronized(this){
            boolean high=kind==Kind.FOREGROUND||kind==Kind.AUTHORITY;
            boolean contended=active!=null||(!high&&foreground>0&&suspended==0);
            if(contended)overlapAttempts++;
            while(active!=null||(!high&&foreground>0&&suspended==0)){
                long remaining=deadline-System.nanoTime();if(remaining<=0)throw new IOException("BARRIER_PRIORITY_TIMEOUT");
                try{TimeUnit.NANOSECONDS.timedWait(this,remaining);}catch(InterruptedException e){Thread.currentThread().interrupt();throw new IOException(e);}
            }
            active=kind;counts.merge(kind,1L,Long::sum);
            if(events.size()<8192)events.add(Map.of("kind",kind.name(),"queuedOperations",foreground,"writerSuspended",suspended>0));
        }
        timings.record(kind==Kind.FOREGROUND?EncounterPersistenceTimings.Phase.FOREGROUND_BARRIER_WAIT:EncounterPersistenceTimings.Phase.MAINTENANCE_BARRIER_WAIT,System.nanoTime()-waitStart);
        long start=System.nanoTime();
        try{channel.force(true);}finally{
            timings.record(kind==Kind.FOREGROUND?EncounterPersistenceTimings.Phase.JOURNAL_FORCE:EncounterPersistenceTimings.Phase.CHECKPOINT_FORCE,System.nanoTime()-start);
            synchronized(this){active=null;notifyAll();}
        }
    }
    synchronized Map<String,Object> snapshot(){return Map.of("forcesByClass",new EnumMap<>(counts),"overlapAttempts",overlapAttempts,"forceEvents",List.copyOf(events));}
    synchronized void reset(){if(active!=null)throw new IllegalStateException("BARRIER_ACTIVE");counts.clear();events.clear();overlapAttempts=0;}
}
