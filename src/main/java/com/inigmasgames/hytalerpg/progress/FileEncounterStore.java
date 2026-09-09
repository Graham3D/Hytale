package com.inigmasgames.hytalerpg.progress;

import com.google.gson.*;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.function.*;
import java.util.concurrent.*;
import static java.nio.file.StandardOpenOption.*;

/**
 * Persistent encounter contexts and frozen death delivery. Permanent completion receipts are
 * never scanned or evicted. Only the bounded pending directory is enumerated for recovery.
 * Back up together with players and earned-rewards; no cross-directory rollback inference.
 */
public final class FileEncounterStore implements AutoCloseable {
    public static final int MAX_PENDING=256,MAX_FILE_BYTES=262144;
    public enum Boundary { AFTER_CONTEXT, AFTER_DISQUALIFY, AFTER_FREEZE, AFTER_AWARD, AFTER_CURSOR, AFTER_COMPLETION, AFTER_CLEANUP }
    public enum JournalBoundary { AFTER_APPEND, AFTER_FORCE, AFTER_CHECKPOINT_FILE, AFTER_CHECKPOINT_POINTER, AFTER_CHECKPOINTS, AFTER_ROTATION, AFTER_FLOOR }
    public enum GroupBoundary { BEFORE_DEQUEUE, AFTER_FIRST_FRAME, AFTER_GROUP_APPEND, BEFORE_FORCE, AFTER_GROUP_FORCE, BEFORE_ACKNOWLEDGEMENTS, MID_ACKNOWLEDGEMENTS, ANOTHER_GROUP_QUEUED, CHECKPOINT_WORKER_STARTED }
    public enum DurabilityBoundary { PREPARE_CREATED, PREPARE_HEADER_WRITTEN, PREPARE_FORCED, ACTIVATION_WRITTEN, ACTIVATION_FORCED, BEFORE_ACTIVE_SWITCH, AFTER_ACTIVE_SWITCH, BUNDLE_SERIALIZATION, BUNDLE_PARTIAL_WRITE, BUNDLE_BEFORE_FORCE, BUNDLE_AFTER_FORCE, BUNDLES_DURABLE, MANIFEST_PARTIAL_WRITE, MANIFEST_BEFORE_FORCE, MANIFEST_AFTER_FORCE, MANIFEST_PUBLISHED, BEFORE_WAL_RETIRE, DURING_WAL_RETIRE }
    public static final class CapacityRejected extends IllegalStateException { public CapacityRejected(){super("ENCOUNTER_PERSISTENCE_CAPACITY");} }
    private record Checkpoint(UUID world,UUID enemy,long sequence){Checkpoint{Objects.requireNonNull(world);Objects.requireNonNull(enemy);if(sequence<1)throw new IllegalArgumentException("CHECKPOINT_SEQUENCE");}}
    private record Floor(long sequence){Floor{if(sequence<0)throw new IllegalArgumentException("CHECKPOINT_FLOOR");}}
    private record Rejected(UUID world,UUID enemy){Rejected{Objects.requireNonNull(world);Objects.requireNonNull(enemy);}}
    private record Delivery(EncounterContributions.DeathPlan plan,int next){
        Delivery {Objects.requireNonNull(plan);if(next<0||next>plan.shares().size())throw new IllegalArgumentException("INVALID_DEATH_CURSOR");}
    }
    private static final Gson JSON=new GsonBuilder().disableHtmlEscaping().create();
    private final Path directory;
    private final Consumer<Boundary> fault;
    private final Consumer<JournalBoundary> journalFault;
    private final Consumer<GroupBoundary> groupFault;
    private final EncounterGroupCommit groups=new EncounterGroupCommit(this);
    private final Object checkpointPublication=new Object();
    private final Semaphore checkpointSlots=new Semaphore(2,true);
    private final ExecutorService checkpointWorker=Executors.newSingleThreadExecutor(r->{var thread=new Thread(r,"RPG-encounter-checkpoint");thread.setDaemon(true);thread.setPriority(Thread.MIN_PRIORITY);return thread;});
    private volatile CompletableFuture<Void> checkpointTail=CompletableFuture.completedFuture(null);
    private FileChannel writerChannel;
    private java.nio.channels.FileLock writerLock;
    private volatile EncounterLog journal;
    private boolean durabilityV2,preallocate=true;
    private Consumer<DurabilityBoundary> durabilityFault=ignored->{};
    private volatile boolean closed,uncertain,closing,legacyReservation;
    private final EncounterPersistenceTimings timings=new EncounterPersistenceTimings();
    private final EncounterBarrierArbiter barriers=new EncounterBarrierArbiter(timings);
    private record AdmissionState(boolean dead,boolean excluded){}
    // Lifetime writer lock excludes other legitimate writers. Immutable tombstones/death state
    // need validation once per cached context, not three filesystem probes for every WAL frame.
    private final Map<EncounterJournal.Key,AdmissionState> admissionStates=new LinkedHashMap<>(16,.75f,true);
    public EncounterPersistenceTimings timings(){return timings;}
    public FileEncounterStore(Path directory){this(directory,ignored->{});}
    /** Fault injection is for process-interruption tests only. */
    public FileEncounterStore(Path directory,Consumer<Boundary> fault){this(directory,fault,ignored->{});}
    public FileEncounterStore(Path directory,Consumer<Boundary> fault,Consumer<JournalBoundary> journalFault){this(directory,fault,journalFault,ignored->{});}
    public FileEncounterStore(Path directory,Consumer<Boundary> fault,Consumer<JournalBoundary> journalFault,Consumer<GroupBoundary> groupFault){this.directory=directory.toAbsolutePath().normalize();this.fault=Objects.requireNonNull(fault);this.journalFault=Objects.requireNonNull(journalFault);this.groupFault=Objects.requireNonNull(groupFault);}
    /** Explicit version transition, never an implicit in-place downgrade. Legacy constructors keep
     * the maintained G/H v1 compatibility API; production selects this versioned factory. */
    public static FileEncounterStore durableV2(Path directory){return durableV2(directory,ignored->{},ignored->{},true);}
    public static FileEncounterStore durableV2(Path directory,Consumer<DurabilityBoundary> fault,Consumer<GroupBoundary> groupFault,boolean preallocate){
        return durableV2(directory,ignored->{},fault,groupFault,preallocate);
    }
    public static FileEncounterStore durableV2(Path directory,Consumer<Boundary> legacyFault,Consumer<DurabilityBoundary> fault,Consumer<GroupBoundary> groupFault,boolean preallocate){
        var store=new FileEncounterStore(directory,legacyFault,ignored->{},groupFault);store.durabilityV2=true;store.durabilityFault=Objects.requireNonNull(fault);store.preallocate=preallocate;return store;
    }
    public Map<String,Object> barrierMetrics(){return barriers.snapshot();}
    public Map<String,Object> journalDiagnostics(){return locked(()->journal instanceof EncounterJournalV2 v2?v2.diagnostics():Map.of("format",1));}
    /** Explicit diagnostic setup fence, not used to omit any workload rotation/checkpoint. */
    public void awaitPreparation(){locked(()->{if(journal instanceof EncounterJournalV2 v2)try{v2.awaitPreparation();}catch(IOException e){throw persistenceFailure(e);}return null;});}
    public void resetTimings(){awaitPreparation();timings.reset();barriers.reset();}
    void foregroundQueued(){if(durabilityV2)barriers.queued();}
    void foregroundCompleted(){if(durabilityV2)barriers.completed();}

    public Optional<EncounterContributions.Snapshot> load(UUID world,UUID enemy){awaitSubmissions();awaitCheckpoints();return locked(()->loadLocked(world,enemy));}
    /** Immutable admission-time fence. Later unrelated writes cannot become load prerequisites. */
    CompletionStage<Void> loadFrontier(){return CompletableFuture.allOf(groups.frontier().toCompletableFuture(),checkpointTail).minimalCompletionStage();}
    /** Worker only, after the captured load frontier; the attachment gates new same-context mutations. */
    Optional<EncounterContributions.Snapshot> loadPrepared(UUID world,UUID enemy){return locked(()->loadLocked(world,enemy));}
    private Optional<EncounterContributions.Snapshot> loadLocked(UUID world,UUID enemy){
        // Public reads retain on-disk validation. Hot-path save uses the replayed durable mirror.
        var baseline=baseline(new EncounterJournal.Key(world,enemy));if(baseline==null)return Optional.empty();
        var value=journal.entry(new EncounterJournal.Key(world,enemy)).snapshot();
        if(rejected(world,enemy))value=value.invalidated();return Optional.of(value);
    }
    /** Caller creates this only from audited natural spawn evidence. LOAD is read-only, never reclassification. */
    public EncounterContributions.Snapshot create(EnemyRewardRegistry.Spawn spawn){return locked(()->{
        if(rejected(spawn.world(),spawn.enemy()))throw new IllegalStateException("ENCOUNTER_PERMANENTLY_DISQUALIFIED");
        if(deathLocked(spawn.world(),spawn.enemy()).isPresent())throw new IllegalStateException("ENCOUNTER_ALREADY_DIED");
        var existing=loadLocked(spawn.world(),spawn.enemy());
        if(existing.isPresent()){
            if(!existing.get().spawn().equals(spawn))throw new IllegalStateException("SPAWN_CONTEXT_CHANGED");return existing.get();
        }
        var initial=new EncounterContributions.Snapshot(spawn,List.of(),-1,-1,spawn.spawnedAtMillis(),1,false);
        write(keyPath("contexts",spawn.world(),spawn.enemy()),initial,false);fault.accept(Boundary.AFTER_CONTEXT);return initial;
    });}
    /** Reserve storage capacity before mutating the runtime ledger, including multi-encounter healing. */
    public Reservation reserve(int records){return locked(()->{
        synchronized(groups){
        if(groups.pending())throw new CapacityRejected();
        try{journal.reserve(records);}catch(CapacityRejected rejection){throw rejection;}catch(IOException|RuntimeException error){throw persistenceFailure(error);}
        legacyReservation=true;
        return new Reservation();
        }
    });}
    public final class Reservation implements AutoCloseable {
        private final FileEncounterStore owner=FileEncounterStore.this;
        private boolean released;
        private Reservation(){}
        @Override public void close(){synchronized(FileEncounterStore.this){if(!released){journal.release();legacyReservation=false;released=true;}}}
    }
    public void save(EncounterContributions.Snapshot value){try(var reservation=reserve(1)){save(value,reservation);}}
    public void save(EncounterContributions.Snapshot value,Reservation reservation){locked(()->{
        if(reservation==null||reservation.owner!=this||reservation.released)throw new CapacityRejected();
        var spawn=value.spawn();
        if(deathLocked(spawn.world(),spawn.enemy()).isPresent())throw new IllegalStateException("ENCOUNTER_ALREADY_DIED");
        if(rejected(spawn.world(),spawn.enemy())&&!value.disqualified())throw new IllegalStateException("ENCOUNTER_DISQUALIFICATION_ROLLBACK");
        foregroundQueued();
        try{journal.append(value);fault.accept(Boundary.AFTER_CONTEXT);}catch(IOException|RuntimeException error){throw persistenceFailure(error);}finally{foregroundCompleted();}return null;
    });}
    static void validateTransition(EncounterContributions.Snapshot old,EncounterContributions.Snapshot value){
        if(!old.spawn().equals(value.spawn()))throw new IllegalStateException("SPAWN_CONTEXT_CHANGED");
        if(old.disqualified()&&!value.disqualified())throw new IllegalStateException("ENCOUNTER_DISQUALIFICATION_ROLLBACK");
          if(old.firstCombat()>=0&&old.lastObserved()-old.progressAt()>EncounterContributions.FARM_WINDOW_MS&&value.progressAt()!=old.progressAt())
              throw new IllegalStateException("EXHAUSTED_FARM_ENCOUNTER_CANNOT_REOPEN");
        if(value.lastObserved()<old.lastObserved()||value.lowestHealthFraction()>old.lowestHealthFraction()
                ||old.firstCombat()>=0&&(value.firstCombat()!=old.firstCombat()||value.progressAt()<old.progressAt()))throw new IllegalStateException("ENCOUNTER_WATERMARK_ROLLBACK");
    }
    public void checkpoint(){awaitSubmissions();locked(()->{try{journal.checkpoint();}catch(IOException|RuntimeException error){throw persistenceFailure(error);}return null;});awaitCheckpoints();}
    public void awaitCheckpoints(){try{EncounterGroupCommit.await(checkpointTail);}catch(RuntimeException error){throw persistenceFailure(error);}}
    public void awaitSubmissions(){try{groups.await();}catch(RuntimeException error){throw persistenceFailure(error);}}
    public CompletionStage<Void> submissionFrontier(){return groups.frontier();}
    public Map<String,Object> submissionMetrics(){return groups.metrics();}
    /** The receipt, not successful submission, is the durability boundary. */
    public final class SubmissionReservation implements AutoCloseable {
        private final EncounterGroupCommit.Lease lease;
        private SubmissionReservation(EncounterGroupCommit.Lease lease){this.lease=lease;}
        public CompletionStage<Void> submit(List<EncounterContributions.Snapshot> values){return groups.submit(lease,values).minimalCompletionStage();}
        @Override public void close(){lease.close();}
    }
    public SubmissionReservation reserveSubmission(UUID world,List<UUID> enemies){
        if(journal==null)locked(()->null);
        synchronized(groups){
        if(closing||legacyReservation)throw new CapacityRejected();
        return new SubmissionReservation(groups.reserve(enemies.stream().map(enemy->new EncounterJournal.Key(world,enemy)).toList()));
        }
    }
    void checkSubmissionState(){if(closed||uncertain)throw new IllegalStateException("ENCOUNTER_PERSISTENCE_UNCERTAIN_RESTART_REQUIRED");}
    void markUncertain(Throwable error){uncertain=true;}
    void groupFault(GroupBoundary boundary){groupFault.accept(boundary);}
    void commitGroup(List<EncounterContributions.Snapshot> values){locked(()->{
        if(legacyReservation)throw new CapacityRejected();
        long validationStart=System.nanoTime();
        for(var value:values){var spawn=value.spawn();var key=new EncounterJournal.Key(spawn.world(),spawn.enemy());
            AdmissionState state=durabilityV2?admissionStates.get(key):null;
            if(state==null){state=new AdmissionState(deathLocked(spawn.world(),spawn.enemy()).isPresent(),rejected(spawn.world(),spawn.enemy()));
                if(durabilityV2){if(admissionStates.size()>=EncounterContributions.MAX_ENCOUNTERS)admissionStates.remove(admissionStates.keySet().iterator().next());admissionStates.put(key,state);}}
            if(state.dead())throw new IllegalStateException("ENCOUNTER_ALREADY_DIED");if(state.excluded()&&!value.disqualified())throw new IllegalStateException("ENCOUNTER_DISQUALIFICATION_ROLLBACK");
        }
        timings.record(EncounterPersistenceTimings.Phase.PRECOMMIT_VALIDATION,System.nanoTime()-validationStart);
        try{journal.appendGroup(values);fault.accept(Boundary.AFTER_CONTEXT);}catch(IOException|RuntimeException error){throw persistenceFailure(error);}return null;
    });}
    private void reserveCheckpoint(){
        long start=System.nanoTime();
        try{if(!checkpointSlots.tryAcquire(EncounterGroupCommit.DEADLINE_MILLIS,TimeUnit.MILLISECONDS))throw new IllegalStateException("CHECKPOINT_BACKLOG_TIMEOUT");}
        catch(InterruptedException error){Thread.currentThread().interrupt();throw persistenceFailure(error);}
        finally{timings.record(EncounterPersistenceTimings.Phase.CHECKPOINT_BACKPRESSURE,System.nanoTime()-start);}
        timings.checkpointBacklog(2-checkpointSlots.availablePermits());
    }
    private void scheduleCheckpoint(Runnable publication){
        var future=new CompletableFuture<Void>();checkpointTail=future;
        try{checkpointWorker.execute(()->{
            try{checkSubmissionState();publication.run();future.complete(null);}catch(Throwable error){markUncertain(error);future.completeExceptionally(error);}finally{checkpointSlots.release();}
        });}catch(RuntimeException error){checkpointSlots.release();throw persistenceFailure(error);}
    }
    public long journalSequence(){return locked(()->journal.sequence());}
    /** Runs under the runtime's serialized callback before freezing its in-memory death plan. */
    public <T> T withDeathCapacity(Supplier<T> operation){awaitSubmissions();return locked(()->{
        synchronized(groups){if(groups.pending()||legacyReservation||pendingFiles().size()>=MAX_PENDING)throw new CapacityRejected();legacyReservation=true;}
        try{return operation.get();}finally{legacyReservation=false;}
    });}
    /** Separate durable tombstone also covers conversion/ownership before context capture. */
    public void disqualify(UUID world,UUID enemy){awaitSubmissions();disqualifyPrepared(world,enemy);}
    /** Worker-only: caller has closed context admission and awaited its finite predecessor. */
    void disqualifyPrepared(UUID world,UUID enemy){locked(()->{
        admissionStates.remove(new EncounterJournal.Key(world,enemy));
        Path path=keyPath("excluded",world,enemy);if(!Files.exists(path))write(path,new Rejected(world,enemy),false);
        else if(!read(path,Rejected.class).equals(new Rejected(world,enemy)))throw new IllegalStateException("ENCOUNTER_ID_MISMATCH");
        fault.accept(Boundary.AFTER_DISQUALIFY);return null;
    });}
    private boolean rejected(UUID world,UUID enemy){
        Path path=keyPath("excluded",world,enemy);if(!Files.exists(path))return false;
        if(!read(path,Rejected.class).equals(new Rejected(world,enemy)))throw new IllegalStateException("ENCOUNTER_ID_MISMATCH");return true;
    }
    public Optional<EncounterContributions.DeathPlan> death(UUID world,UUID enemy){return locked(()->deathLocked(world,enemy));}
    private Optional<EncounterContributions.DeathPlan> deathLocked(UUID world,UUID enemy){
        Path complete=keyPath("deaths",world,enemy),pending=pending(world,enemy);
        EncounterContributions.DeathPlan plan=null;
        if(Files.exists(complete))plan=read(complete,EncounterContributions.DeathPlan.class);
        else if(Files.exists(pending))plan=read(pending,Delivery.class).plan();
        if(plan!=null)identity(plan.spawn(),world,enemy);return Optional.ofNullable(plan);
    }
    /** Atomically record the full immutable plan before the first player award. */
    public EncounterContributions.DeathPlan freeze(EncounterContributions.DeathPlan plan){awaitSubmissions();return freezePrepared(plan);}
    /** Worker-only: no wait on unrelated submissions admitted after death capture. */
    EncounterContributions.DeathPlan freezePrepared(EncounterContributions.DeathPlan plan){return locked(()->{
        var spawn=plan.spawn();admissionStates.remove(new EncounterJournal.Key(spawn.world(),spawn.enemy()));var previous=deathLocked(spawn.world(),spawn.enemy());
        if(previous.isPresent()){
            if(!previous.get().equals(plan))throw new IllegalStateException("DEATH_PLAN_CONFLICT");return previous.get();
        }
        var context=loadLocked(spawn.world(),spawn.enemy()).orElseThrow(()->new IllegalStateException("UNREGISTERED_ENCOUNTER"));
        if(!context.spawn().equals(spawn)||plan.deathAtMillis()<context.lastObserved())throw new IllegalStateException("DEATH_CONTEXT_MISMATCH");
        if(context.disqualified()&&!plan.shares().isEmpty())throw new IllegalStateException("DISQUALIFIED_DEATH_REWARD");
        // Core eligibility must already have produced these recipients. Never accept a share without persisted credit.
        for(var share:plan.shares())if(context.credits().stream().noneMatch(c->c.player().equals(share.player())
                &&plan.deathAtMillis()>=c.observedAtMillis()&&plan.deathAtMillis()-c.observedAtMillis()<=EncounterContributions.CONTRIBUTION_WINDOW_MS))throw new IllegalStateException("DEATH_WITHOUT_PERSISTED_CONTRIBUTION");
        if(pendingFiles().size()>=MAX_PENDING)throw new IllegalStateException("DEATH_QUEUE_CAPACITY");
        write(pending(spawn.world(),spawn.enemy()),new Delivery(plan,0),false);fault.accept(Boundary.AFTER_FREEZE);return plan;
    });}
    /** The supplied authority must be the durable earned-reward service: it deduplicates a crash after award, before cursor. */
    public int drain(int awardBudget,BiConsumer<UUID,EarnedReward> award){
        return drainLearning(awardBudget,(player,reward,learning)->{
            if(learning!=null)throw new IllegalStateException("LEARNING_DELIVERY_ADAPTER_REQUIRED");award.accept(player,reward);
        });
    }
    @FunctionalInterface public interface AwardDelivery {void accept(UUID player,EarnedReward reward,LearningSources.Opportunity learning);}
    public int drainLearning(int awardBudget,AwardDelivery award){
        awaitSubmissions();
        return drainPrepared(awardBudget,award);
    }
    /** Only already frozen plans are discoverable here. No dependency on active-context WAL tails. */
    int drainPrepared(int awardBudget,AwardDelivery award){
        if(awardBudget<1||awardBudget>EncounterContributions.MAX_CONTRIBUTORS)throw new IllegalArgumentException("DEATH_AWARD_BUDGET");Objects.requireNonNull(award);
        return locked(()->{
            int attempts=0;
            for(Path path:pendingFiles()){
                var delivery=read(path,Delivery.class);var plan=delivery.plan();var spawn=plan.spawn();
                if(!path.equals(pending(spawn.world(),spawn.enemy())))throw new IllegalStateException("DEATH_QUEUE_ID_MISMATCH");
                Path complete=keyPath("deaths",spawn.world(),spawn.enemy());
                if(Files.exists(complete)){
                    if(!read(complete,EncounterContributions.DeathPlan.class).equals(plan))throw new IllegalStateException("DEATH_COMPLETION_CONFLICT");
                    deleteCompleted(path);continue;
                }
                while(delivery.next()<plan.shares().size()&&attempts<awardBudget){
                    var share=plan.shares().get(delivery.next());award.accept(share.player(),plan.reward(share),share.learning());attempts++;
                    fault.accept(Boundary.AFTER_AWARD);
                    delivery=new Delivery(plan,delivery.next()+1);write(path,delivery,true);fault.accept(Boundary.AFTER_CURSOR);
                }
                if(delivery.next()==plan.shares().size()){
                    write(complete,plan,false);fault.accept(Boundary.AFTER_COMPLETION);deleteCompleted(path);
                }
                if(attempts==awardBudget)break;
            }return attempts;
        });
    }
    public int pendingCount(){return locked(()->pendingFiles().size());}
    private List<Path> pendingFiles(){
        Path folder=directory.resolve("pending");List<Path> paths=new ArrayList<>();
        if(!Files.exists(folder))return paths;
        try(var entries=Files.newDirectoryStream(folder,"*.json")){
            for(var path:entries){paths.add(path);if(paths.size()>MAX_PENDING)throw new IllegalStateException("DEATH_QUEUE_BOUNDS");}
        }catch(IOException error){throw failure("DEATH_QUEUE_UNREADABLE",error);}
        paths.sort(Comparator.comparing(p->p.getFileName().toString()));return paths;
    }
    private void deleteCompleted(Path path){try{Files.delete(path);}catch(IOException error){throw failure("DEATH_QUEUE_CLEANUP_FAILED",error);}fault.accept(Boundary.AFTER_CLEANUP);}
    private Path pending(UUID world,UUID enemy){return directory.resolve("pending").resolve(key(world,enemy)+".json");}
    private Path keyPath(String kind,UUID world,UUID enemy){String key=key(world,enemy);return directory.resolve(kind).resolve(key.substring(0,2)).resolve(key+".json");}
    private static String key(UUID world,UUID enemy){return RewardIntent.digest(Objects.requireNonNull(world)+"/"+Objects.requireNonNull(enemy));}
    private static void identity(EnemyRewardRegistry.Spawn spawn,UUID world,UUID enemy){if(!spawn.world().equals(world)||!spawn.enemy().equals(enemy))throw new IllegalStateException("ENCOUNTER_ID_MISMATCH");}
    private synchronized <T> T locked(Supplier<T> operation){
        if(closed)throw new IllegalStateException("ENCOUNTER_STORE_CLOSED");
        if(uncertain)throw new IllegalStateException("ENCOUNTER_PERSISTENCE_UNCERTAIN_RESTART_REQUIRED");
        if(writerLock==null)open();
        return operation.get();
    }
    private void open(){
        try {
            Files.createDirectories(directory);writerChannel=FileChannel.open(directory.resolve("writer.lock"),CREATE,WRITE);
            writerLock=writerChannel.tryLock();if(writerLock==null)throw new IllegalStateException("ENCOUNTER_WRITER_BUSY");
            var legacy=new EncounterJournal.Checkpoints(){
                public EncounterJournal.Entry load(EncounterJournal.Key key){return baseline(key);}
                public long floor(){Path path=directory.resolve("checkpoint-floor.json");return Files.exists(path)?read(path,Floor.class).sequence():0;}
                public void floor(long sequence){write(directory.resolve("checkpoint-floor.json"),new Floor(sequence),true);}
                public void save(EncounterJournal.Key key,EncounterJournal.Entry value){saveCheckpoint(key,value);}
                public void submit(Runnable publication){scheduleCheckpoint(publication);}
                public void reserveCheckpoint(){FileEncounterStore.this.reserveCheckpoint();}
                public void groupFault(GroupBoundary boundary){FileEncounterStore.this.groupFault(boundary);}
            };
            if(!durabilityV2)journal=new EncounterJournal(directory.resolve("journal"),legacy,timings,journalFault);
            else {
                Path floor=directory.resolve("checkpoint-floor.json");
                boolean v2=Files.exists(floor)&&JsonParser.parseString(Files.readString(floor)).getAsJsonObject().get("schema").getAsInt()==2;
                long legacyFloor=0;
                if(!v2){
                    // Finish the old reader's own replay/checkpoint before publishing any new format.
                    // A crash before v2 manifest publication leaves the complete v1 directory valid.
                    try(var previous=new EncounterJournal(directory.resolve("journal"),legacy,timings,journalFault)){
                        previous.checkpoint();awaitCheckpoints();legacyFloor=previous.sequence();
                    }
                }
                journal=new EncounterJournalV2(directory,legacy,barriers,timings,durabilityFault,journalFault,v2,legacyFloor,preallocate);
            }
        }catch(IOException|RuntimeException error){releaseHandles();uncertain=true;throw failure("ENCOUNTER_STORE_UNAVAILABLE",error);}
    }
    private EncounterJournal.Entry baseline(EncounterJournal.Key key){
        synchronized(checkpointPublication){return readBaseline(key);}
    }
    private EncounterJournal.Entry readBaseline(EncounterJournal.Key key){
        Path original=keyPath("contexts",key.world(),key.enemy());if(!Files.exists(original))return null;
        var value=read(original,EncounterContributions.Snapshot.class);identity(value.spawn(),key.world(),key.enemy());
        Path pointer=keyPath("checkpoints",key.world(),key.enemy());long sequence=0;
        if(Files.exists(pointer)) {
            var saved=read(pointer,Checkpoint.class);if(!saved.world().equals(key.world())||!saved.enemy().equals(key.enemy()))throw new IllegalStateException("CHECKPOINT_ID_MISMATCH");
            var updated=read(checkpointFile(pointer,saved.sequence()),EncounterContributions.Snapshot.class);
            validateTransition(value,updated);value=updated;sequence=saved.sequence();
        }
        return new EncounterJournal.Entry(sequence,value);
    }
    private static Path checkpointFile(Path pointer,long sequence){return pointer.resolveSibling(pointer.getFileName()+"."+sequence+".snapshot");}
    private void saveCheckpoint(EncounterJournal.Key key,EncounterJournal.Entry value){
        synchronized(checkpointPublication){publishCheckpoint(key,value);}
    }
    private void publishCheckpoint(EncounterJournal.Key key,EncounterJournal.Entry value){
        Path pointer=keyPath("checkpoints",key.world(),key.enemy());Path snapshot=checkpointFile(pointer,value.sequence());
        Checkpoint old=Files.exists(pointer)?read(pointer,Checkpoint.class):null;
        if(Files.exists(snapshot)) {
            if(!read(snapshot,EncounterContributions.Snapshot.class).equals(value.snapshot()))throw new IllegalStateException("CHECKPOINT_CONFLICT");
        }else write(snapshot,value.snapshot(),false);
        journalFault.accept(JournalBoundary.AFTER_CHECKPOINT_FILE);
        write(pointer,new Checkpoint(key.world(),key.enemy(),value.sequence()),true);
        journalFault.accept(JournalBoundary.AFTER_CHECKPOINT_POINTER);
        if(old!=null&&old.sequence()!=value.sequence())try{Files.deleteIfExists(checkpointFile(pointer,old.sequence()));}catch(IOException error){throw persistenceFailure(error);}
    }
    private IllegalStateException persistenceFailure(Exception error){uncertain=true;return failure("ENCOUNTER_PERSISTENCE_UNCERTAIN_RESTART_REQUIRED",error);}
    private void releaseHandles(){
        try{if(journal!=null)journal.close();}catch(IOException ignored){uncertain=true;}
        try{if(writerLock!=null)writerLock.close();}catch(IOException ignored){uncertain=true;}
        try{if(writerChannel!=null)writerChannel.close();}catch(IOException ignored){uncertain=true;}
        writerLock=null;writerChannel=null;
    }
    /** Close is not a durability boundary: each acknowledged append was already forced. */
    @Override public void close(){
        // Never acquire the store monitor before stopping the writer: it may be in force().
        synchronized(groups){if(closed||closing)return;closing=true;}
        try{
            groups.close();checkpointWorker.shutdown();
            if(!checkpointWorker.awaitTermination(EncounterGroupCommit.DEADLINE_MILLIS,TimeUnit.MILLISECONDS)){
                checkpointWorker.shutdownNow();
                if(!checkpointWorker.awaitTermination(EncounterGroupCommit.DEADLINE_MILLIS,TimeUnit.MILLISECONDS))throw new IllegalStateException("CHECKPOINT_SHUTDOWN_TIMEOUT");
            }
            synchronized(this){releaseHandles();closed=true;}
        }catch(InterruptedException error){checkpointWorker.shutdownNow();Thread.currentThread().interrupt();throw persistenceFailure(error);}
        catch(RuntimeException error){checkpointWorker.shutdownNow();throw persistenceFailure(error);}
    }
    private static <T> T read(Path path,Class<T> type){
        try{
            if(!Files.isRegularFile(path)||Files.size(path)>MAX_FILE_BYTES)throw new IllegalStateException("ENCOUNTER_FILE_BOUNDS");
            var envelope=JsonParser.parseString(Files.readString(path,StandardCharsets.UTF_8)).getAsJsonObject();
            if(envelope.size()!=3||envelope.get("schema").getAsInt()!=1)throw new IllegalStateException("ENCOUNTER_FILE_SCHEMA");
            var payload=envelope.get("payload");if(!RewardIntent.digest(JSON.toJson(payload)).equals(envelope.get("checksum").getAsString()))throw new IllegalStateException("ENCOUNTER_FILE_CHECKSUM");
            T value=JSON.fromJson(payload,type);if(value==null||!JSON.toJsonTree(value).equals(payload))throw new IllegalStateException("ENCOUNTER_FILE_SHAPE");return value;
        }catch(IOException|RuntimeException error){throw failure("ENCOUNTER_FILE_UNREADABLE: "+path,error);}
    }
    private void write(Path path,Object value,boolean replace){
        Path temporary=path.resolveSibling(path.getFileName()+"."+UUID.randomUUID()+".tmp");
        foregroundQueued();
        try{
            Files.createDirectories(path.getParent());if(!replace&&Files.exists(path))throw new IllegalStateException("ENCOUNTER_IMMUTABLE_FILE_EXISTS");
            long serializationStart=System.nanoTime();
            var payload=JSON.toJsonTree(value);var envelope=new JsonObject();envelope.addProperty("schema",1);
            envelope.addProperty("checksum",RewardIntent.digest(JSON.toJson(payload)));envelope.add("payload",payload);
            byte[] bytes=JSON.toJson(envelope).getBytes(StandardCharsets.UTF_8);if(bytes.length>MAX_FILE_BYTES)throw new IllegalStateException("ENCOUNTER_FILE_BOUNDS");
            timings.record(EncounterPersistenceTimings.Phase.CHECKPOINT_SERIALIZATION,System.nanoTime()-serializationStart);
            long writeStart=System.nanoTime(),forceTime=0;
            try(var channel=FileChannel.open(temporary,CREATE_NEW,WRITE)){var buffer=ByteBuffer.wrap(bytes);while(buffer.hasRemaining())channel.write(buffer);
                long forceStart=System.nanoTime();try{if(durabilityV2)barriers.force(channel,EncounterBarrierArbiter.Kind.AUTHORITY);else channel.force(true);}finally{forceTime=System.nanoTime()-forceStart;if(!durabilityV2)timings.record(EncounterPersistenceTimings.Phase.CHECKPOINT_FORCE,forceTime);}}
            if(replace)Files.move(temporary,path,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);else Files.move(temporary,path,StandardCopyOption.ATOMIC_MOVE);
            timings.record(EncounterPersistenceTimings.Phase.CHECKPOINT_WRITE,System.nanoTime()-writeStart-forceTime);
        }catch(IOException error){if(durabilityV2)throw persistenceFailure(error);throw failure("ENCOUNTER_WRITE_FAILED",error);}
        finally{foregroundCompleted();try{Files.deleteIfExists(temporary);}catch(IOException ignored){/* Incomplete uniquely named files are not replayable. */}}
    }
    private static IllegalStateException failure(String boundary,Exception cause){return new IllegalStateException(boundary,cause);}
}
