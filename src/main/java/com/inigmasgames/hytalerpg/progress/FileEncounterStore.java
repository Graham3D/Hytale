package com.inigmasgames.hytalerpg.progress;

import com.google.gson.*;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.function.*;
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
    private FileChannel writerChannel;
    private java.nio.channels.FileLock writerLock;
    private EncounterJournal journal;
    private boolean closed,uncertain;
    private final EncounterPersistenceTimings timings=new EncounterPersistenceTimings();
    public EncounterPersistenceTimings timings(){return timings;}
    public FileEncounterStore(Path directory){this(directory,ignored->{});}
    /** Fault injection is for process-interruption tests only. */
    public FileEncounterStore(Path directory,Consumer<Boundary> fault){this(directory,fault,ignored->{});}
    public FileEncounterStore(Path directory,Consumer<Boundary> fault,Consumer<JournalBoundary> journalFault){this.directory=directory.toAbsolutePath().normalize();this.fault=Objects.requireNonNull(fault);this.journalFault=Objects.requireNonNull(journalFault);}

    public Optional<EncounterContributions.Snapshot> load(UUID world,UUID enemy){return locked(()->loadLocked(world,enemy));}
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
        try{journal.reserve(records);}catch(CapacityRejected rejection){throw rejection;}catch(IOException|RuntimeException error){throw persistenceFailure(error);}
        return new Reservation();
    });}
    public final class Reservation implements AutoCloseable {
        private final FileEncounterStore owner=FileEncounterStore.this;
        private boolean released;
        private Reservation(){}
        @Override public void close(){synchronized(FileEncounterStore.this){if(!released){journal.release();released=true;}}}
    }
    public void save(EncounterContributions.Snapshot value){try(var reservation=reserve(1)){save(value,reservation);}}
    public void save(EncounterContributions.Snapshot value,Reservation reservation){locked(()->{
        if(reservation==null||reservation.owner!=this||reservation.released)throw new CapacityRejected();
        var spawn=value.spawn();
        if(deathLocked(spawn.world(),spawn.enemy()).isPresent())throw new IllegalStateException("ENCOUNTER_ALREADY_DIED");
        if(rejected(spawn.world(),spawn.enemy())&&!value.disqualified())throw new IllegalStateException("ENCOUNTER_DISQUALIFICATION_ROLLBACK");
        try{journal.append(value);fault.accept(Boundary.AFTER_CONTEXT);}catch(IOException|RuntimeException error){throw persistenceFailure(error);}return null;
    });}
    static void validateTransition(EncounterContributions.Snapshot old,EncounterContributions.Snapshot value){
        if(!old.spawn().equals(value.spawn()))throw new IllegalStateException("SPAWN_CONTEXT_CHANGED");
        if(old.disqualified()&&!value.disqualified())throw new IllegalStateException("ENCOUNTER_DISQUALIFICATION_ROLLBACK");
          if(old.firstCombat()>=0&&old.lastObserved()-old.progressAt()>EncounterContributions.FARM_WINDOW_MS&&value.progressAt()!=old.progressAt())
              throw new IllegalStateException("EXHAUSTED_FARM_ENCOUNTER_CANNOT_REOPEN");
        if(value.lastObserved()<old.lastObserved()||value.lowestHealthFraction()>old.lowestHealthFraction()
                ||old.firstCombat()>=0&&(value.firstCombat()!=old.firstCombat()||value.progressAt()<old.progressAt()))throw new IllegalStateException("ENCOUNTER_WATERMARK_ROLLBACK");
    }
    public void checkpoint(){locked(()->{try{journal.checkpoint();}catch(IOException|RuntimeException error){throw persistenceFailure(error);}return null;});}
    public long journalSequence(){return locked(()->journal.sequence());}
    /** Runs under the runtime's serialized callback before freezing its in-memory death plan. */
    public <T> T withDeathCapacity(Supplier<T> operation){return locked(()->{if(pendingFiles().size()>=MAX_PENDING)throw new CapacityRejected();return operation.get();});}
    /** Separate durable tombstone also covers conversion/ownership before context capture. */
    public void disqualify(UUID world,UUID enemy){locked(()->{
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
    public EncounterContributions.DeathPlan freeze(EncounterContributions.DeathPlan plan){return locked(()->{
        var spawn=plan.spawn();var previous=deathLocked(spawn.world(),spawn.enemy());
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
            journal=new EncounterJournal(directory.resolve("journal"),new EncounterJournal.Checkpoints(){
                public EncounterJournal.Entry load(EncounterJournal.Key key){return baseline(key);}
                public long floor(){Path path=directory.resolve("checkpoint-floor.json");return Files.exists(path)?read(path,Floor.class).sequence():0;}
                public void floor(long sequence){write(directory.resolve("checkpoint-floor.json"),new Floor(sequence),true);}
                public void save(EncounterJournal.Key key,EncounterJournal.Entry value){saveCheckpoint(key,value);}
            },timings,journalFault);
        }catch(IOException|RuntimeException error){releaseHandles();uncertain=true;throw failure("ENCOUNTER_STORE_UNAVAILABLE",error);}
    }
    private EncounterJournal.Entry baseline(EncounterJournal.Key key){
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
    @Override public synchronized void close(){if(!closed){releaseHandles();closed=true;}}
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
        try{
            Files.createDirectories(path.getParent());if(!replace&&Files.exists(path))throw new IllegalStateException("ENCOUNTER_IMMUTABLE_FILE_EXISTS");
            long serializationStart=System.nanoTime();
            var payload=JSON.toJsonTree(value);var envelope=new JsonObject();envelope.addProperty("schema",1);
            envelope.addProperty("checksum",RewardIntent.digest(JSON.toJson(payload)));envelope.add("payload",payload);
            byte[] bytes=JSON.toJson(envelope).getBytes(StandardCharsets.UTF_8);if(bytes.length>MAX_FILE_BYTES)throw new IllegalStateException("ENCOUNTER_FILE_BOUNDS");
            timings.record(EncounterPersistenceTimings.Phase.CHECKPOINT_SERIALIZATION,System.nanoTime()-serializationStart);
            long writeStart=System.nanoTime(),forceTime=0;
            try(var channel=FileChannel.open(temporary,CREATE_NEW,WRITE)){var buffer=ByteBuffer.wrap(bytes);while(buffer.hasRemaining())channel.write(buffer);
                long forceStart=System.nanoTime();try{channel.force(true);}finally{forceTime=System.nanoTime()-forceStart;timings.record(EncounterPersistenceTimings.Phase.CHECKPOINT_FORCE,forceTime);}}
            if(replace)Files.move(temporary,path,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);else Files.move(temporary,path,StandardCopyOption.ATOMIC_MOVE);
            timings.record(EncounterPersistenceTimings.Phase.CHECKPOINT_WRITE,System.nanoTime()-writeStart-forceTime);
        }catch(IOException error){throw failure("ENCOUNTER_WRITE_FAILED",error);}
        finally{try{Files.deleteIfExists(temporary);}catch(IOException ignored){/* Incomplete uniquely named files are not replayable. */}}
    }
    private static IllegalStateException failure(String boundary,Exception cause){return new IllegalStateException(boundary,cause);}
}
