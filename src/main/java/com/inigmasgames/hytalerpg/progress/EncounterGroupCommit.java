package com.inigmasgames.hytalerpg.progress;

import java.util.*;
import java.util.concurrent.*;

/** Bounded immutable submissions. One writer drains already-queued work; no artificial collection delay. */
final class EncounterGroupCommit {
    static final int MAX_GROUP_RECORDS=64,MAX_GROUP_BYTES=256*1024,MAX_PENDING_RECORDS=256,MAX_PENDING_OPERATIONS=256,MAX_CONTEXT_PENDING=64;
    // Conservative derived bound including next-fit operation packing, byte rollover and
    // one partly filled in-flight group: <=8 record groups, each <=5 byte-rollover groups
    // even at the 16 KiB frame cap plus its four-byte length prefix.
    static final int MAX_PENDING_GROUPS=40;
    static final long DEADLINE_MILLIS=5000;
    private final FileEncounterStore store;
    private final ArrayDeque<Work> queue=new ArrayDeque<>();
    private final Map<EncounterJournal.Key,Integer> contexts=new HashMap<>();
    private int pendingRecords,pendingOperations;
    private boolean stopping;
    private Throwable failure;
    private Thread worker;
    private CompletableFuture<Void> tail=CompletableFuture.completedFuture(null);
    private record Work(List<EncounterContributions.Snapshot> snapshots,Lease lease,CompletableFuture<Void> receipt,long submitted){}
    EncounterGroupCommit(FileEncounterStore store){this.store=store;}
    final class Lease implements AutoCloseable {
        final List<EncounterJournal.Key> keys;boolean queued,closed;
        Lease(List<EncounterJournal.Key> keys){this.keys=keys;}
        @Override public void close(){synchronized(EncounterGroupCommit.this){if(!queued&&!closed)release(this);}}
    }
    synchronized Lease reserve(List<EncounterJournal.Key> keys){
        store.checkSubmissionState();
        if(stopping||failure!=null)throw new IllegalStateException("ENCOUNTER_PERSISTENCE_UNCERTAIN_RESTART_REQUIRED",failure);
        var counts=new HashMap<EncounterJournal.Key,Integer>();for(var key:keys)counts.merge(key,1,Integer::sum);
        if(keys.size()>MAX_GROUP_RECORDS||pendingRecords+keys.size()>MAX_PENDING_RECORDS||pendingOperations>=MAX_PENDING_OPERATIONS
                ||counts.entrySet().stream().anyMatch(e->contexts.getOrDefault(e.getKey(),0)+e.getValue()>MAX_CONTEXT_PENDING)){
            store.timings().admissionRejected();throw new FileEncounterStore.CapacityRejected();
        }
        for(var entry:counts.entrySet())contexts.merge(entry.getKey(),entry.getValue(),Integer::sum);
        pendingRecords+=keys.size();pendingOperations++;return new Lease(List.copyOf(keys));
    }
    synchronized CompletableFuture<Void> submit(Lease lease,List<EncounterContributions.Snapshot> snapshots){
        if(lease.closed||lease.queued||snapshots.size()>lease.keys.size())throw new FileEncounterStore.CapacityRejected();
        var permitted=new ArrayList<>(lease.keys);
        for(var snapshot:snapshots)if(!permitted.remove(new EncounterJournal.Key(snapshot.spawn().world(),snapshot.spawn().enemy())))throw new IllegalArgumentException("SUBMISSION_IDENTITY");
        if(failure!=null||stopping)throw new IllegalStateException("ENCOUNTER_PERSISTENCE_UNCERTAIN_RESTART_REQUIRED",failure);
        if(snapshots.isEmpty()){release(lease);return CompletableFuture.completedFuture(null);}
        var receipt=new CompletableFuture<Void>();lease.queued=true;
        receipt.orTimeout(DEADLINE_MILLIS,TimeUnit.MILLISECONDS).whenComplete((ignored,error)->{if(error!=null)store.markUncertain(error);});
        queue.add(new Work(List.copyOf(snapshots),lease,receipt,System.nanoTime()));tail=receipt;
        store.foregroundQueued();
        store.timings().operation(snapshots.size());
        store.timings().pending(pendingRecords,pendingOperations);
        if(worker==null){worker=Thread.ofPlatform().daemon().name("RPG-encounter-group-writer").start(this::run);}
        notifyAll();return receipt;
    }
    private void run(){
        var cpu=java.lang.management.ManagementFactory.getThreadMXBean();
        try{
            while(true){
                var group=new ArrayList<Work>();var snapshots=new ArrayList<EncounterContributions.Snapshot>();
                synchronized(this){
                    while(queue.isEmpty()&&!stopping&&failure==null)wait();
                    if(failure!=null||queue.isEmpty()&&stopping)return;
                }
                store.groupFault(FileEncounterStore.GroupBoundary.BEFORE_DEQUEUE);
                synchronized(this){
                    while(!queue.isEmpty()&&snapshots.size()+queue.peek().snapshots().size()<=MAX_GROUP_RECORDS){var work=queue.remove();group.add(work);snapshots.addAll(work.snapshots());}
                }
                try{
                    long started=System.nanoTime();for(var work:group)store.timings().record(EncounterPersistenceTimings.Phase.GROUP_WAIT,started-work.submitted());
                    long wallStart=System.nanoTime(),cpuStart=cpu.isCurrentThreadCpuTimeSupported()?cpu.getCurrentThreadCpuTime():-1;
                    long forcesBefore=store.timings().count(EncounterPersistenceTimings.Phase.JOURNAL_FORCE);
                    try{store.commitGroup(snapshots);}finally{
                        store.timings().record(EncounterPersistenceTimings.Phase.GROUP_WALL,System.nanoTime()-wallStart);
                        if(cpuStart>=0)store.timings().record(EncounterPersistenceTimings.Phase.GROUP_CPU,cpu.getCurrentThreadCpuTime()-cpuStart);
                    }
                    store.timings().commitBatch(snapshots.size(),(int)(store.timings().count(EncounterPersistenceTimings.Phase.JOURNAL_FORCE)-forcesBefore));
                    long forcedAt=store.timings().forceCompletedNanos();
                    store.groupFault(FileEncounterStore.GroupBoundary.BEFORE_ACKNOWLEDGEMENTS);
                    boolean queued;synchronized(this){queued=!queue.isEmpty();}if(queued)store.groupFault(FileEncounterStore.GroupBoundary.ANOTHER_GROUP_QUEUED);
                    for(int i=0;i<group.size();i++){
                        var work=group.get(i);store.checkSubmissionState();
                        store.timings().record(EncounterPersistenceTimings.Phase.SEALED_OPERATION_TO_FORCE,forcedAt-work.submitted());
                        long latency=System.nanoTime()-work.submitted();for(var ignored:work.snapshots())store.timings().record(EncounterPersistenceTimings.Phase.DURABLE_ACK,latency);
                        synchronized(this){release(work.lease());}
                        work.receipt().complete(null);
                        store.timings().record(EncounterPersistenceTimings.Phase.FORCE_TO_COMPLETION,System.nanoTime()-forcedAt);
                        if(i==0)store.groupFault(FileEncounterStore.GroupBoundary.MID_ACKNOWLEDGEMENTS);
                    }
                }catch(Throwable error){
                    store.markUncertain(error);
                    synchronized(this){failure=error;for(var work:group){if(!work.lease().closed)release(work.lease());work.receipt().completeExceptionally(error);}failQueued(error);}
                    return;
                }
            }
        }catch(Throwable error){if(error instanceof InterruptedException)Thread.currentThread().interrupt();store.markUncertain(error);synchronized(this){failure=error;failQueued(error);}}
    }
    private void failQueued(Throwable error){while(!queue.isEmpty()){var work=queue.remove();release(work.lease());work.receipt().completeExceptionally(error);}notifyAll();}
    private void release(Lease lease){if(lease.closed)return;lease.closed=true;if(lease.queued)store.foregroundCompleted();pendingOperations--;pendingRecords-=lease.keys.size();for(var key:lease.keys)contexts.compute(key,(ignored,count)->count==1?null:count-1);notifyAll();}
    synchronized boolean pending(){return pendingOperations!=0;}
    void await(){CompletableFuture<Void> receipt;synchronized(this){receipt=tail;}await(receipt);}
    static void await(CompletableFuture<?> receipt){
        try{receipt.get(DEADLINE_MILLIS,TimeUnit.MILLISECONDS);}catch(InterruptedException error){Thread.currentThread().interrupt();throw new IllegalStateException("ENCOUNTER_PERSISTENCE_UNCERTAIN_RESTART_REQUIRED",error);}
        catch(ExecutionException|TimeoutException error){throw new IllegalStateException("ENCOUNTER_PERSISTENCE_UNCERTAIN_RESTART_REQUIRED",error);}
    }
    void close(){
        Thread thread;synchronized(this){stopping=true;notifyAll();thread=worker;}
        if(thread==null)return;
        try{thread.join(DEADLINE_MILLIS);if(thread.isAlive()){thread.interrupt();thread.join(DEADLINE_MILLIS);if(thread.isAlive())throw new IllegalStateException("ENCOUNTER_WRITER_SHUTDOWN_TIMEOUT");}}
        catch(InterruptedException error){Thread.currentThread().interrupt();throw new IllegalStateException("ENCOUNTER_WRITER_SHUTDOWN_INTERRUPTED",error);}
    }
}
