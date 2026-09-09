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
public final class FileEncounterStore {
    public static final int MAX_PENDING=256,MAX_FILE_BYTES=262144;
    public enum Boundary { AFTER_CONTEXT, AFTER_DISQUALIFY, AFTER_FREEZE, AFTER_AWARD, AFTER_CURSOR, AFTER_COMPLETION, AFTER_CLEANUP }
    private record Rejected(UUID world,UUID enemy){Rejected{Objects.requireNonNull(world);Objects.requireNonNull(enemy);}}
    private record Delivery(EncounterContributions.DeathPlan plan,int next){
        Delivery {Objects.requireNonNull(plan);if(next<0||next>plan.shares().size())throw new IllegalArgumentException("INVALID_DEATH_CURSOR");}
    }
    private static final Gson JSON=new GsonBuilder().disableHtmlEscaping().create();
    private final Path directory;
    private final Consumer<Boundary> fault;
    public FileEncounterStore(Path directory){this(directory,ignored->{});}
    /** Fault injection is for process-interruption tests only. */
    public FileEncounterStore(Path directory,Consumer<Boundary> fault){this.directory=directory.toAbsolutePath().normalize();this.fault=Objects.requireNonNull(fault);}

    public Optional<EncounterContributions.Snapshot> load(UUID world,UUID enemy){return locked(()->loadLocked(world,enemy));}
    private Optional<EncounterContributions.Snapshot> loadLocked(UUID world,UUID enemy){
        Path path=keyPath("contexts",world,enemy);if(!Files.exists(path))return Optional.empty();
        var value=read(path,EncounterContributions.Snapshot.class);identity(value.spawn(),world,enemy);
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
    public void save(EncounterContributions.Snapshot value){locked(()->{
        var spawn=value.spawn();var old=loadLocked(spawn.world(),spawn.enemy()).orElseThrow(()->new IllegalStateException("UNREGISTERED_ENCOUNTER"));
        if(!old.spawn().equals(spawn))throw new IllegalStateException("SPAWN_CONTEXT_CHANGED");
        if(deathLocked(spawn.world(),spawn.enemy()).isPresent())throw new IllegalStateException("ENCOUNTER_ALREADY_DIED");
          if(old.disqualified()&&!value.disqualified())throw new IllegalStateException("ENCOUNTER_DISQUALIFICATION_ROLLBACK");
          if(old.firstCombat()>=0&&old.lastObserved()-old.progressAt()>EncounterContributions.FARM_WINDOW_MS&&value.progressAt()!=old.progressAt())
              throw new IllegalStateException("EXHAUSTED_FARM_ENCOUNTER_CANNOT_REOPEN");
        if(value.lastObserved()<old.lastObserved()||value.lowestHealthFraction()>old.lowestHealthFraction()
                ||old.firstCombat()>=0&&(value.firstCombat()!=old.firstCombat()||value.progressAt()<old.progressAt()))throw new IllegalStateException("ENCOUNTER_WATERMARK_ROLLBACK");
        write(keyPath("contexts",spawn.world(),spawn.enemy()),value,true);fault.accept(Boundary.AFTER_CONTEXT);return null;
    });}
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
        try{Files.createDirectories(directory);try(var channel=FileChannel.open(directory.resolve("writer.lock"),CREATE,WRITE);var lock=channel.tryLock()){
            if(lock==null)throw new IllegalStateException("ENCOUNTER_WRITER_BUSY");return operation.get();
        }}catch(IOException|java.nio.channels.OverlappingFileLockException error){throw failure("ENCOUNTER_STORE_UNAVAILABLE",error);}
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
    private static void write(Path path,Object value,boolean replace){
        Path temporary=path.resolveSibling(path.getFileName()+"."+UUID.randomUUID()+".tmp");
        try{
            Files.createDirectories(path.getParent());if(!replace&&Files.exists(path))throw new IllegalStateException("ENCOUNTER_IMMUTABLE_FILE_EXISTS");
            var payload=JSON.toJsonTree(value);var envelope=new JsonObject();envelope.addProperty("schema",1);
            envelope.addProperty("checksum",RewardIntent.digest(JSON.toJson(payload)));envelope.add("payload",payload);
            byte[] bytes=JSON.toJson(envelope).getBytes(StandardCharsets.UTF_8);if(bytes.length>MAX_FILE_BYTES)throw new IllegalStateException("ENCOUNTER_FILE_BOUNDS");
            try(var channel=FileChannel.open(temporary,CREATE_NEW,WRITE)){var buffer=ByteBuffer.wrap(bytes);while(buffer.hasRemaining())channel.write(buffer);channel.force(true);}
            if(replace)Files.move(temporary,path,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);else Files.move(temporary,path,StandardCopyOption.ATOMIC_MOVE);
        }catch(IOException error){throw failure("ENCOUNTER_WRITE_FAILED",error);}
        finally{try{Files.deleteIfExists(temporary);}catch(IOException ignored){/* Incomplete uniquely named files are not replayable. */}}
    }
    private static IllegalStateException failure(String boundary,Exception cause){return new IllegalStateException(boundary,cause);}
}
