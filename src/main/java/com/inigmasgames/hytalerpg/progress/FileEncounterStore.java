package com.inigmasgames.hytalerpg.progress;

import com.google.gson.*;
import com.inigmasgames.hytalerpg.execution.summon.IronSentinelBinding;
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
    // Equipment receipts freeze both the before and after spatial bags, so their
    // envelope can exceed the single-record limit even when each bag is valid.
    public static final int MAX_SPATIAL_EQUIPMENT_FILE_BYTES=4*1024*1024;
    public enum Boundary { AFTER_CONTEXT, AFTER_DISQUALIFY, AFTER_FREEZE, AFTER_AWARD, AFTER_CURSOR, AFTER_COMPLETION, AFTER_CLEANUP,
        AFTER_ENEMY_COMPENSATION_MARKER, AFTER_FIRST_ORIGINAL_CONTEXT, AFTER_ENEMY_COMPENSATION_PACK_ABORT }
    public enum JournalBoundary { AFTER_APPEND, AFTER_FORCE, AFTER_CHECKPOINT_FILE, AFTER_CHECKPOINT_POINTER, AFTER_CHECKPOINTS, AFTER_ROTATION, AFTER_FLOOR }
    public enum GroupBoundary { BEFORE_DEQUEUE, AFTER_FIRST_FRAME, AFTER_GROUP_APPEND, BEFORE_FORCE, AFTER_GROUP_FORCE, BEFORE_ACKNOWLEDGEMENTS, MID_ACKNOWLEDGEMENTS, ANOTHER_GROUP_QUEUED, CHECKPOINT_WORKER_STARTED }
    public enum DurabilityBoundary { PREPARE_CREATED, PREPARE_HEADER_WRITTEN, PREPARE_FORCED, ACTIVATION_WRITTEN, ACTIVATION_FORCED, BEFORE_ACTIVE_SWITCH, AFTER_ACTIVE_SWITCH, BUNDLE_SERIALIZATION, BUNDLE_PARTIAL_WRITE, BUNDLE_BEFORE_FORCE, BUNDLE_AFTER_FORCE, BUNDLES_DURABLE, MANIFEST_PARTIAL_WRITE, MANIFEST_BEFORE_FORCE, MANIFEST_AFTER_FORCE, MANIFEST_PUBLISHED, BEFORE_WAL_RETIRE, DURING_WAL_RETIRE }
    public static final class CapacityRejected extends IllegalStateException { public CapacityRejected(){super("ENCOUNTER_PERSISTENCE_CAPACITY");} }
    private record ActionRootHighWater(UUID world,UUID encounter,UUID logicalActor,long generation,long lastReserved){
        ActionRootHighWater{
            Objects.requireNonNull(world);Objects.requireNonNull(encounter);Objects.requireNonNull(logicalActor);
            if(generation<0||lastReserved<1)throw new IllegalArgumentException("ENEMY_ACTION_ROOT_HIGH_WATER");
        }
    }
    private record EnemyEntityIndex(UUID world,UUID entity,UUID logicalActor){
        EnemyEntityIndex{Objects.requireNonNull(world);Objects.requireNonNull(entity);Objects.requireNonNull(logicalActor);}
    }
    /** A durable reservation may leave gaps after a crash; it can never reissue a root ID. */
    public record EnemyActionRootBlock(UUID world,UUID logicalActor,long generation,long first,long last) implements DurableActionRootLease.Block {
        public EnemyActionRootBlock{
            Objects.requireNonNull(world);Objects.requireNonNull(logicalActor);
            if(generation<0||first<1||last<first||last-first>=256)throw new IllegalArgumentException("ENEMY_ACTION_ROOT_BLOCK");
        }
        public String id(long sequence){
            if(sequence<first||sequence>last)throw new IllegalArgumentException("ENEMY_ACTION_ROOT_OUTSIDE_BLOCK");
            return "me.action/"+world+"/"+logicalActor+"/"+generation+"/"+sequence;
        }
        @Override public UUID actor(){return logicalActor;}
    }
    private record PlayerHitRootHighWater(UUID world,UUID player,long lastReserved){
        PlayerHitRootHighWater{
            Objects.requireNonNull(world);Objects.requireNonNull(player);
            if(lastReserved<1)throw new IllegalArgumentException("PLAYER_HIT_ROOT_HIGH_WATER");
        }
    }
    private record PlayerActionRootBinding(UUID world,UUID player,String action,String root){
        PlayerActionRootBinding{
            Objects.requireNonNull(world);Objects.requireNonNull(player);
            if(action==null||action.isBlank()||action.length()>512||root==null||root.isBlank())
                throw new IllegalArgumentException("PLAYER_ACTION_ROOT_BINDING");
        }
    }
    public record PlayerHitRootBlock(UUID world,UUID actor,long first,long last) implements DurableActionRootLease.Block {
        public PlayerHitRootBlock{
            Objects.requireNonNull(world);Objects.requireNonNull(actor);
            if(first<1||last<first||last-first>=256)throw new IllegalArgumentException("PLAYER_HIT_ROOT_BLOCK");
        }
        @Override public String id(long sequence){
            if(sequence<first||sequence>last)throw new IllegalArgumentException("PLAYER_HIT_ROOT_OUTSIDE_BLOCK");
            return "combat.hit/"+world+"/"+actor+"/"+sequence;
        }
    }
    private record Checkpoint(UUID world,UUID enemy,long sequence){Checkpoint{Objects.requireNonNull(world);Objects.requireNonNull(enemy);if(sequence<1)throw new IllegalArgumentException("CHECKPOINT_SEQUENCE");}}
    private record Floor(long sequence){Floor{if(sequence<0)throw new IllegalArgumentException("CHECKPOINT_FLOOR");}}
    private record Rejected(UUID world,UUID enemy){Rejected{Objects.requireNonNull(world);Objects.requireNonNull(enemy);}}
    private record Delivery(EncounterContributions.DeathPlan plan,int next){
        Delivery {Objects.requireNonNull(plan);if(next<0||next>plan.shares().size())throw new IllegalArgumentException("INVALID_DEATH_CURSOR");}
    }
    private static final Gson JSON=new GsonBuilder().disableHtmlEscaping().create();
    private final Path directory;
    private Consumer<EncounterContributions.DeathPlan> gearDelivery=ignored->{};
    private Consumer<String> gearFault=ignored->{};
    public synchronized void configureGearFault(Consumer<String> fault){gearFault=Objects.requireNonNull(fault);}
    public synchronized void configureGearDelivery(Consumer<EncounterContributions.DeathPlan> delivery){gearDelivery=Objects.requireNonNull(delivery);}
    /** Atomic group decision first; a crash during derived indexes can only replay the same sealed plan. IO worker only. */
    public com.inigmasgames.hytalerpg.enemies.EnemyBirthPlan reserveEnemyBirth(com.inigmasgames.hytalerpg.enemies.EnemyBirthPlan plan){
        return locked(()->{
            Path path=keyPath("enemy-births",plan.world(),plan.encounter());
            if(Files.exists(path)){
                var saved=read(path,com.inigmasgames.hytalerpg.enemies.EnemyBirthPlan.class);
                if(!saved.equals(plan))throw new IllegalStateException("SEALED_ENEMY_BIRTH_MISMATCH");
            }else{
                for(var actor:plan.actors()){
                    if(deathLocked(plan.world(),actor.entityId()).isPresent())throw new IllegalStateException("ENEMY_BIRTH_ALREADY_DEFEATED");
                    var previous=enemyDescriptor(plan.world(),actor.logicalActorId());
                    if(previous.isPresent()&&!previous.get().equals(actor))throw new IllegalStateException("SEALED_ENEMY_DESCRIPTOR_MISMATCH");
                }
                if(plan.pack()!=null)enemyPack(plan.world(),plan.pack().packId()).ifPresent(previous->requireSamePackBirth(previous,plan.pack()));
                write(path,plan,false);
            }
            // Every index is idempotent. A caller cannot publish any actor until this method succeeds.
            for(var actor:plan.actors())reserveEnemyDescriptor(actor);
            if(plan.pack()!=null)reserveEnemyPack(plan.pack());
            return plan;
        });
    }
    /** One source of truth for a native special birth, including its exact ordinary fallback contexts. */
    public com.inigmasgames.hytalerpg.enemies.EnemyBirthPlan reserveEnemyBirthRoot(
            com.inigmasgames.hytalerpg.enemies.EnemyBirthRoot root){
        return locked(()->{
            Path path=keyPath("enemy-birth-roots",root.world(),root.encounter());
            if(Files.exists(path)){
                if(!read(path,com.inigmasgames.hytalerpg.enemies.EnemyBirthRoot.class).equals(root))
                    throw new IllegalStateException("SEALED_ENEMY_BIRTH_ROOT_MISMATCH");
            }else{
                if(enemyBirth(root.world(),root.encounter()).isPresent())
                    throw new IllegalStateException("ENEMY_BIRTH_ROOT_LATE_SOURCE");
                write(path,root,false);
            }
            return reserveEnemyBirth(root.plan());
        });
    }
    public Optional<com.inigmasgames.hytalerpg.enemies.EnemyBirthRoot> enemyBirthRoot(UUID world,UUID encounter){
        return locked(()->{Path path=keyPath("enemy-birth-roots",world,encounter);
            if(!Files.exists(path))return Optional.empty();
            var root=read(path,com.inigmasgames.hytalerpg.enemies.EnemyBirthRoot.class);
            if(!root.world().equals(world)||!root.encounter().equals(encounter))
                throw new IllegalStateException("ENEMY_BIRTH_ROOT_LOOKUP_IDENTITY");
            return Optional.of(root);
        });
    }
    private List<com.inigmasgames.hytalerpg.enemies.EnemyBirthRoot> enemyBirthRoots(UUID world){
        Path folder=directory.resolve("enemy-birth-roots");if(!Files.exists(folder))return List.of();
        var roots=new ArrayList<com.inigmasgames.hytalerpg.enemies.EnemyBirthRoot>();int scanned=0;
        try(var shards=Files.newDirectoryStream(folder)){
            for(Path shard:shards){
                if(!Files.isDirectory(shard))throw new IllegalStateException("ENEMY_BIRTH_ROOT_INDEX_SHARD");
                try(var files=Files.newDirectoryStream(shard,"*.json")){
                    for(Path path:files){
                        if(++scanned>65536)throw new IllegalStateException("ENEMY_BIRTH_ROOT_INDEX_CAPACITY");
                        var root=read(path,com.inigmasgames.hytalerpg.enemies.EnemyBirthRoot.class);
                        if(!path.equals(keyPath("enemy-birth-roots",root.world(),root.encounter())))
                            throw new IllegalStateException("ENEMY_BIRTH_ROOT_INDEX_IDENTITY");
                        if(root.world().equals(world))roots.add(root);
                    }
                }
            }
        }catch(IOException failure){throw persistenceFailure(failure);}
        roots.sort(Comparator.comparing(com.inigmasgames.hytalerpg.enemies.EnemyBirthRoot::encounter));
        return List.copyOf(roots);
    }
    public Optional<com.inigmasgames.hytalerpg.enemies.EnemyBirthCompensation> enemyBirthCompensation(UUID world,UUID encounter){
        return locked(()->{Path path=keyPath("enemy-birth-compensations",world,encounter);
            if(!Files.exists(path))return Optional.empty();
            var value=read(path,com.inigmasgames.hytalerpg.enemies.EnemyBirthCompensation.class);
            if(!value.world().equals(world)||!value.encounter().equals(encounter))
                throw new IllegalStateException("ENEMY_BIRTH_COMPENSATION_LOOKUP_IDENTITY");
            return Optional.of(value);
        });
    }
    private List<com.inigmasgames.hytalerpg.enemies.EnemyBirthCompensation> enemyBirthCompensations(UUID world){
        Path folder=directory.resolve("enemy-birth-compensations");if(!Files.exists(folder))return List.of();
        var values=new ArrayList<com.inigmasgames.hytalerpg.enemies.EnemyBirthCompensation>();int scanned=0;
        try(var shards=Files.newDirectoryStream(folder)){
            for(Path shard:shards){
                if(!Files.isDirectory(shard))throw new IllegalStateException("ENEMY_BIRTH_COMPENSATION_INDEX_SHARD");
                try(var files=Files.newDirectoryStream(shard,"*.json")){
                    for(Path path:files){
                        if(++scanned>65536)throw new IllegalStateException("ENEMY_BIRTH_COMPENSATION_INDEX_CAPACITY");
                        var value=read(path,com.inigmasgames.hytalerpg.enemies.EnemyBirthCompensation.class);
                        if(!path.equals(keyPath("enemy-birth-compensations",value.world(),value.encounter())))
                            throw new IllegalStateException("ENEMY_BIRTH_COMPENSATION_INDEX_IDENTITY");
                        if(value.world().equals(world))values.add(value);
                    }
                }
            }
        }catch(IOException failure){throw persistenceFailure(failure);}
        values.sort(Comparator.comparing(com.inigmasgames.hytalerpg.enemies.EnemyBirthCompensation::encounter));
        return List.copyOf(values);
    }
    /** The staged native roster has no combat receipts; restore its original contexts as one durable decision. */
    public com.inigmasgames.hytalerpg.enemies.EnemyBirthCompensation compensateEnemyBirth(UUID world,UUID encounter){
        awaitSubmissions();awaitCheckpoints();
        return locked(()->compensateEnemyBirthPrepared(world,encounter));
    }
    private com.inigmasgames.hytalerpg.enemies.EnemyBirthCompensation compensateEnemyBirthPrepared(UUID world,UUID encounter){
            var root=enemyBirthRoot(world,encounter).orElseThrow(()->new IllegalStateException("ENEMY_BIRTH_ORIGINALS_MISSING"));
            var choice=com.inigmasgames.hytalerpg.enemies.EnemyBirthCompensation.of(root);
            var existing=enemyBirthCompensation(world,encounter);
            if(existing.isPresent()&&!existing.get().equals(choice))throw new IllegalStateException("ENEMY_BIRTH_COMPENSATION_CONFLICT");
            var pack=enemyPack(world,root.plan().pack().packId()).orElseThrow(()->new IllegalStateException("ENEMY_BIRTH_PACK_MISSING"));
            requireSamePackBirth(root.plan().pack(),pack);
            if(pack.generation()!=root.plan().generation()||pack.state()!=com.inigmasgames.hytalerpg.enemies.EnemyPackRecord.State.RESERVED
                    &&pack.state()!=com.inigmasgames.hytalerpg.enemies.EnemyPackRecord.State.STAGED
                    &&!(existing.isPresent()&&pack.state()==com.inigmasgames.hytalerpg.enemies.EnemyPackRecord.State.ABORTED
                            &&"BIRTH_COMPENSATED".equals(pack.abortReason()))
                    ||!pack.deadMemberReceipts().isEmpty())throw new IllegalStateException("ENEMY_BIRTH_COMPENSATION_TOO_LATE");
            if(existing.isPresent()&&pack.state()==com.inigmasgames.hytalerpg.enemies.EnemyPackRecord.State.ABORTED){
                for(var source:root.originalSpawns()){
                    Path path=keyPath("contexts",world,source.enemy());
                    if(!Files.exists(path)||!read(path,EncounterContributions.Snapshot.class).spawn().equals(source))
                        throw new IllegalStateException("ENEMY_BIRTH_COMPENSATION_INCOMPLETE_CONTEXT");
                }
                return choice;
            }
            var originals=new HashMap<UUID,EnemyRewardRegistry.Spawn>();
            for(var source:root.originalSpawns())originals.put(source.enemy(),source);
            for(var actor:root.plan().actors()){
                var key=new EncounterJournal.Key(world,actor.entityId());
                if(deathLocked(world,actor.entityId()).isPresent()||rejected(world,actor.entityId())
                        ||Files.exists(keyPath("checkpoints",world,actor.entityId())))
                    throw new IllegalStateException("ENEMY_BIRTH_COMPENSATION_ACTIVITY");
                var logged=journal.entry(key);
                if(logged!=null&&logged.sequence()!=0)throw new IllegalStateException("ENEMY_BIRTH_COMPENSATION_JOURNAL");
                Path context=keyPath("contexts",world,actor.entityId());
                if(!Files.exists(context))continue;
                var snapshot=read(context,EncounterContributions.Snapshot.class);
                var source=originals.get(actor.entityId());
                var accepted=source==null?null:new EnemyRewardRegistry.Spawn(source.world(),source.enemy(),source.roleId(),
                        source.combatIdentity(),source.biomeKey(),source.level(),source.rank(),source.rarity(),
                        source.registryProfile(),source.spawnedAtMillis(),source.milestone(),source.combat(),actor.immutableRewardContext());
                boolean extraSpecial=source==null&&snapshot.spawn().world().equals(world)
                        &&snapshot.spawn().enemy().equals(actor.entityId())&&snapshot.spawn().roleId().equals(actor.nativeRoleId())
                        &&snapshot.spawn().level()==actor.combatLevel()&&Objects.equals(snapshot.spawn().enemyRewards(),actor.immutableRewardContext());
                if(!snapshot.spawn().equals(source)&&!snapshot.spawn().equals(accepted)&&!extraSpecial
                        ||!snapshot.credits().isEmpty()||snapshot.firstCombat()!=-1||snapshot.progressAt()!=-1
                        ||snapshot.lowestHealthFraction()!=1||snapshot.disqualified()
                        ||snapshot.lastObserved()!=snapshot.spawn().spawnedAtMillis())
                    throw new IllegalStateException("ENEMY_BIRTH_COMPENSATION_CONTEXT_ACTIVE");
            }
            if(existing.isEmpty()){
                write(keyPath("enemy-birth-compensations",world,encounter),choice,false);
                fault.accept(Boundary.AFTER_ENEMY_COMPENSATION_MARKER);
            }
            int restored=0;
            for(var source:root.originalSpawns()){
                Path path=keyPath("contexts",world,source.enemy());
                var ordinary=new EncounterContributions.Snapshot(source,List.of(),-1,-1,source.spawnedAtMillis(),1,false);
                if(!Files.exists(path)||!read(path,EncounterContributions.Snapshot.class).equals(ordinary))
                    write(path,ordinary,Files.exists(path));
                if(++restored==1)fault.accept(Boundary.AFTER_FIRST_ORIGINAL_CONTEXT);
                journal.discardUnmodifiedBaseline(new EncounterJournal.Key(world,source.enemy()));
            }
            if(pack.state()!=com.inigmasgames.hytalerpg.enemies.EnemyPackRecord.State.ABORTED){
                transitionEnemyPack(world,pack.packId(),before->before.abort("BIRTH_COMPENSATED"));
                fault.accept(Boundary.AFTER_ENEMY_COMPENSATION_PACK_ABORT);
            }
            return choice;
    }
    public Optional<com.inigmasgames.hytalerpg.enemies.EnemyBirthPlan> enemyBirth(UUID world,UUID encounter){
        return locked(()->{Path path=keyPath("enemy-births",world,encounter);
            return Files.exists(path)?Optional.of(read(path,com.inigmasgames.hytalerpg.enemies.EnemyBirthPlan.class)):Optional.empty();});
    }
    /** Exact sealed decisions for world-load recovery; never invokes the planner or redraws rarity. */
    public List<com.inigmasgames.hytalerpg.enemies.EnemyBirthPlan> enemyBirths(UUID world){
        Objects.requireNonNull(world);
        return locked(()->{
            Path root=directory.resolve("enemy-births");if(!Files.exists(root))return List.of();
            var records=new ArrayList<com.inigmasgames.hytalerpg.enemies.EnemyBirthPlan>();int scanned=0;
            try(var shards=Files.newDirectoryStream(root)){
                for(Path shard:shards){
                    if(!Files.isDirectory(shard))throw new IllegalStateException("ENEMY_BIRTH_INDEX_SHARD");
                    try(var files=Files.newDirectoryStream(shard,"*.json")){
                        for(Path path:files){
                            if(++scanned>65536)throw new IllegalStateException("ENEMY_BIRTH_INDEX_CAPACITY");
                            var birth=read(path,com.inigmasgames.hytalerpg.enemies.EnemyBirthPlan.class);
                            if(!path.equals(keyPath("enemy-births",birth.world(),birth.encounter())))
                                throw new IllegalStateException("ENEMY_BIRTH_INDEX_IDENTITY");
                            if(birth.world().equals(world))records.add(birth);
                        }
                    }
                }
            }catch(IOException failure){throw persistenceFailure(failure);}
            records.sort(Comparator.comparing(com.inigmasgames.hytalerpg.enemies.EnemyBirthPlan::encounter));
            return List.copyOf(records);
        });
    }
    public record EnemyWorldInventory(List<com.inigmasgames.hytalerpg.enemies.EnemyBirthPlan> births,
            List<com.inigmasgames.hytalerpg.enemies.EnemyPackRecord> packs,
            List<com.inigmasgames.hytalerpg.enemies.SuperUniqueAnchor> anchors,
            List<com.inigmasgames.hytalerpg.enemies.EnemyBirthCompensation> compensations){
        public EnemyWorldInventory{births=List.copyOf(births);packs=List.copyOf(packs);anchors=List.copyOf(anchors);compensations=List.copyOf(compensations);}
        public EnemyWorldInventory(List<com.inigmasgames.hytalerpg.enemies.EnemyBirthPlan> births,
                List<com.inigmasgames.hytalerpg.enemies.EnemyPackRecord> packs){this(births,packs,List.of(),List.of());}
        public EnemyWorldInventory(List<com.inigmasgames.hytalerpg.enemies.EnemyBirthPlan> births,
                List<com.inigmasgames.hytalerpg.enemies.EnemyPackRecord> packs,
                List<com.inigmasgames.hytalerpg.enemies.SuperUniqueAnchor> anchors){this(births,packs,anchors,List.of());}
    }
    /** Repair projections from sealed decisions before a world can admit fresh special packs. */
    public EnemyWorldInventory recoverEnemyWorld(UUID world){
        Objects.requireNonNull(world);
        awaitSubmissions();awaitCheckpoints();
        return locked(()->{
            for(var root:enemyBirthRoots(world))reserveEnemyBirthRoot(root);
            for(var compensation:enemyBirthCompensations(world))compensateEnemyBirthPrepared(compensation.world(),compensation.encounter());
            var births=enemyBirths(world);var expected=new HashMap<UUID,com.inigmasgames.hytalerpg.enemies.EnemyPackRecord>();
            for(var birth:births){
                reserveEnemyBirth(birth);
                if(birth.pack()!=null&&expected.putIfAbsent(birth.pack().packId(),birth.pack())!=null)
                    throw new IllegalStateException("ENEMY_WORLD_DUPLICATE_PACK_BIRTH");
            }
            var packs=enemyPacks(world);if(packs.size()!=expected.size())throw new IllegalStateException("ENEMY_WORLD_ORPHAN_PACK");
            for(var pack:packs){
                var birth=expected.get(pack.packId());if(birth==null)throw new IllegalStateException("ENEMY_WORLD_ORPHAN_PACK");
                requireSamePackBirth(birth,pack);
            }
            return new EnemyWorldInventory(births,packs,enemyAnchors(world),enemyBirthCompensations(world));
        });
    }
    /** IO-worker reservation before native action admission; no world callback waits for a file write. */
    public EnemyActionRootBlock reserveEnemyActionRoots(UUID world,UUID encounter,long generation,UUID logicalActor,int count){
        if(count<1||count>256)throw new IllegalArgumentException("ENEMY_ACTION_ROOT_RESERVATION_SIZE");
        return locked(()->{
            var birth=enemyBirth(world,encounter).orElseThrow(()->new IllegalStateException("ENEMY_ACTION_BIRTH_NOT_SEALED"));
            if(birth.generation()!=generation||birth.actors().stream().noneMatch(actor->actor.logicalActorId().equals(logicalActor)))
                throw new IllegalStateException("ENEMY_ACTION_ROOT_FOREIGN_ACTOR");
            Path path=keyPath("enemy-action-roots",world,logicalActor);
            var previous=Files.exists(path)?read(path,ActionRootHighWater.class):null;
            if(previous!=null&&(!previous.world().equals(world)||!previous.encounter().equals(encounter)
                    ||!previous.logicalActor().equals(logicalActor)||previous.generation()!=generation))
                throw new IllegalStateException("ENEMY_ACTION_ROOT_GENERATION_CONFLICT");
            long first=previous==null?1:Math.addExact(previous.lastReserved(),1);
            long last=Math.addExact(first,count-1L);
            write(path,new ActionRootHighWater(world,encounter,logicalActor,generation,last),previous!=null);
            return new EnemyActionRootBlock(world,logicalActor,generation,first,last);
        });
    }
    /** Same durable writer/high-water boundary as enemy actions, with a separate player namespace. */
    public PlayerHitRootBlock reservePlayerHitRoots(UUID world,UUID player,int count){
        Objects.requireNonNull(world);Objects.requireNonNull(player);
        if(count<1||count>256)throw new IllegalArgumentException("PLAYER_HIT_ROOT_RESERVATION_SIZE");
        return locked(()->{
            Path path=keyPath("player-hit-roots",world,player);
            var previous=Files.exists(path)?read(path,PlayerHitRootHighWater.class):null;
            if(previous!=null&&(!previous.world().equals(world)||!previous.player().equals(player)))
                throw new IllegalStateException("PLAYER_HIT_ROOT_IDENTITY_CONFLICT");
            long first=previous==null?1:Math.addExact(previous.lastReserved(),1);
            long last=Math.addExact(first,count-1L);
            write(path,new PlayerHitRootHighWater(world,player,last),previous!=null);
            return new PlayerHitRootBlock(world,player,first,last);
        });
    }
    /** Idempotent accepted-action binding; a crash after high-water and before binding only wastes an ID. */
    public String bindPlayerActionRoot(UUID world,UUID player,String action){
        Objects.requireNonNull(world);Objects.requireNonNull(player);
        if(action==null||action.isBlank()||action.length()>512)throw new IllegalArgumentException("PLAYER_ACTION_ROOT_KEY");
        return locked(()->{
            UUID key=UUID.nameUUIDFromBytes(("player-action/"+player+"/"+action).getBytes(StandardCharsets.UTF_8));
            Path path=keyPath("player-action-root-bindings",world,key);
            if(Files.exists(path)){
                var saved=read(path,PlayerActionRootBinding.class);
                if(!world.equals(saved.world())||!player.equals(saved.player())||!action.equals(saved.action()))
                    throw new IllegalStateException("PLAYER_ACTION_ROOT_BINDING_CONFLICT");
                return saved.root();
            }
            var block=reservePlayerHitRoots(world,player,1);
            String root=block.id(block.first());
            write(path,new PlayerActionRootBinding(world,player,action,root),false);
            return root;
        });
    }
    /** Descriptor publication precedes special-actor exposure; duplicate birth callbacks cannot reroll it. */
    public com.inigmasgames.hytalerpg.enemies.EnemyDescriptor reserveEnemyDescriptor(com.inigmasgames.hytalerpg.enemies.EnemyDescriptor descriptor){
        return locked(()->{Path path=keyPath("enemy-descriptors",descriptor.worldId(),descriptor.logicalActorId());
            if(Files.exists(path)){
                var saved=read(path,com.inigmasgames.hytalerpg.enemies.EnemyDescriptor.class);
                if(!saved.equals(descriptor))throw new IllegalStateException("SEALED_ENEMY_DESCRIPTOR_MISMATCH");
                reserveEnemyEntityIndex(saved);
                return saved;
            }
            if(deathLocked(descriptor.worldId(),descriptor.entityId()).isPresent())throw new IllegalStateException("ENEMY_ALREADY_DEFEATED");
            write(path,descriptor,false);reserveEnemyEntityIndex(descriptor);return descriptor;
        });
    }
    private void reserveEnemyEntityIndex(com.inigmasgames.hytalerpg.enemies.EnemyDescriptor descriptor){
        var index=new EnemyEntityIndex(descriptor.worldId(),descriptor.entityId(),descriptor.logicalActorId());
        Path path=keyPath("enemy-native-actors",index.world(),index.entity());
        if(Files.exists(path)){
            if(!read(path,EnemyEntityIndex.class).equals(index))throw new IllegalStateException("SEALED_ENEMY_ENTITY_INDEX_MISMATCH");
        }else write(path,index,false);
    }
    public Optional<com.inigmasgames.hytalerpg.enemies.EnemyDescriptor> enemyDescriptor(UUID world,UUID logicalActor){
        return locked(()->{Path path=keyPath("enemy-descriptors",world,logicalActor);
            return Files.exists(path)?Optional.of(read(path,com.inigmasgames.hytalerpg.enemies.EnemyDescriptor.class)):Optional.empty();});
    }
    public Optional<com.inigmasgames.hytalerpg.enemies.EnemyDescriptor> enemyDescriptorByEntity(UUID world,UUID entity){
        return locked(()->{
            Path path=keyPath("enemy-native-actors",world,entity);
            if(!Files.exists(path))return Optional.empty();
            var index=read(path,EnemyEntityIndex.class);
            if(!index.world().equals(world)||!index.entity().equals(entity))throw new IllegalStateException("ENEMY_ENTITY_INDEX_IDENTITY");
            var actor=enemyDescriptor(world,index.logicalActor()).orElseThrow(()->new IllegalStateException("ENEMY_ENTITY_DESCRIPTOR_MISSING"));
            if(!actor.entityId().equals(entity))throw new IllegalStateException("ENEMY_ENTITY_DESCRIPTOR_MISMATCH");
            return Optional.of(actor);
        });
    }
    public com.inigmasgames.hytalerpg.enemies.SuperUniqueAnchor placeEnemyAnchor(com.inigmasgames.hytalerpg.enemies.SuperUniqueAnchor anchor){
        if(anchor.state()!=com.inigmasgames.hytalerpg.enemies.SuperUniqueAnchor.State.IDLE||anchor.cycle()!=0||anchor.revision()!=1)
            throw new IllegalArgumentException("NEW_ANCHOR_REQUIRED");
        return locked(()->{Path path=keyPath("enemy-anchors",anchor.worldId(),anchor.anchorId());
            if(Files.exists(path)){
                var saved=read(path,com.inigmasgames.hytalerpg.enemies.SuperUniqueAnchor.class);
                if(!saved.equals(anchor))throw new IllegalStateException("ANCHOR_PLACEMENT_CONFLICT");return saved;
            }
            write(path,anchor,false);return anchor;
        });
    }
    public Optional<com.inigmasgames.hytalerpg.enemies.SuperUniqueAnchor> enemyAnchor(UUID world,UUID anchor){
        return locked(()->{Path path=keyPath("enemy-anchors",world,anchor);if(!Files.exists(path))return Optional.empty();
            var saved=read(path,com.inigmasgames.hytalerpg.enemies.SuperUniqueAnchor.class);
            if(!saved.worldId().equals(world)||!saved.anchorId().equals(anchor))throw new IllegalStateException("ENEMY_ANCHOR_LOOKUP_IDENTITY");
            return Optional.of(saved);});
    }
    /** Durable anchor inventory for world-load scheduling; tombstones remain visible for audit. */
    public List<com.inigmasgames.hytalerpg.enemies.SuperUniqueAnchor> enemyAnchors(UUID world){
        Objects.requireNonNull(world);
        return locked(()->{
            Path root=directory.resolve("enemy-anchors");if(!Files.exists(root))return List.of();
            var records=new ArrayList<com.inigmasgames.hytalerpg.enemies.SuperUniqueAnchor>();int scanned=0;
            try(var shards=Files.newDirectoryStream(root)){
                for(Path shard:shards){
                    if(!Files.isDirectory(shard))throw new IllegalStateException("ENEMY_ANCHOR_INDEX_SHARD");
                    try(var files=Files.newDirectoryStream(shard,"*.json")){
                        for(Path path:files){
                            if(++scanned>65536)throw new IllegalStateException("ENEMY_ANCHOR_INDEX_CAPACITY");
                            var anchor=read(path,com.inigmasgames.hytalerpg.enemies.SuperUniqueAnchor.class);
                            if(!path.equals(keyPath("enemy-anchors",anchor.worldId(),anchor.anchorId())))
                                throw new IllegalStateException("ENEMY_ANCHOR_INDEX_IDENTITY");
                            if(anchor.worldId().equals(world))records.add(anchor);
                        }
                    }
                }
            }catch(IOException failure){throw persistenceFailure(failure);}
            records.sort(Comparator.comparing(com.inigmasgames.hytalerpg.enemies.SuperUniqueAnchor::anchorId));
            return List.copyOf(records);
        });
    }
    public com.inigmasgames.hytalerpg.enemies.SuperUniqueAnchor reserveEnemyAnchor(UUID world,UUID anchor,long now){
        return updateEnemyAnchor(world,anchor,before->before.reserve(now));
    }
    public com.inigmasgames.hytalerpg.enemies.SuperUniqueAnchor publishEnemyAnchor(UUID world,UUID anchor,UUID pack){
        return updateEnemyAnchor(world,anchor,before->{var roster=enemyPack(world,pack).orElseThrow();
            if(!Objects.equals(before.encounterId(),roster.encounterId())||roster.generation()!=before.cycle()
                    ||!roster.anchor().equals(before.position())
                    ||roster.state()!=com.inigmasgames.hytalerpg.enemies.EnemyPackRecord.State.GUARDED&&roster.state()!=com.inigmasgames.hytalerpg.enemies.EnemyPackRecord.State.RELEASED)
                throw new IllegalStateException("ANCHOR_PACK_NOT_PUBLISHED");
            return before.published(roster.encounterId());});
    }
    public com.inigmasgames.hytalerpg.enemies.SuperUniqueAnchor settleEnemyAnchor(UUID world,UUID anchor,UUID pack,long now){
        return updateEnemyAnchor(world,anchor,before->{var roster=reconcileEnemyPackDefeats(world,pack);
            if(((before.state()==com.inigmasgames.hytalerpg.enemies.SuperUniqueAnchor.State.RESERVED
                    ||before.state()==com.inigmasgames.hytalerpg.enemies.SuperUniqueAnchor.State.LIVE)
                    &&!Objects.equals(before.encounterId(),roster.encounterId()))||roster.generation()!=before.cycle()
                    ||!roster.anchor().equals(before.position()))throw new IllegalStateException("ANCHOR_PACK_IDENTITY_MISMATCH");
            if(roster.state()!=com.inigmasgames.hytalerpg.enemies.EnemyPackRecord.State.DEFEATED&&roster.state()!=com.inigmasgames.hytalerpg.enemies.EnemyPackRecord.State.ABORTED)
                throw new IllegalStateException("ANCHOR_PACK_NOT_TERMINAL");
            return before.terminal(roster.encounterId(),"pack-terminal/"+world+"/"+pack+"/"+roster.generation()+"/"+roster.state(),now);});
    }
    /** Administrative removal persists pack abort first, never a combat defeat. */
    public com.inigmasgames.hytalerpg.enemies.SuperUniqueAnchor removeEnemyAnchor(UUID world,UUID anchor,UUID activePack){
        return updateEnemyAnchor(world,anchor,before->{
            if(before.encounterId()!=null){
                var matches=enemyPacks(world).stream().filter(value->value.encounterId().equals(before.encounterId())).toList();
                if(matches.isEmpty()&&activePack==null
                        &&before.state()==com.inigmasgames.hytalerpg.enemies.SuperUniqueAnchor.State.RESERVED)return before.removed();
                if(matches.size()!=1)throw new IllegalStateException("LIVE_ANCHOR_PACK_MISSING_OR_DUPLICATE");
                var roster=matches.getFirst();
                if(activePack!=null&&!roster.packId().equals(activePack)||roster.generation()!=before.cycle()
                        ||!roster.anchor().equals(before.position()))throw new IllegalStateException("FOREIGN_ANCHOR_PACK");
                if(roster.state()!=com.inigmasgames.hytalerpg.enemies.EnemyPackRecord.State.DEFEATED)
                    transitionEnemyPack(world,roster.packId(),value->value.abort("ANCHOR_REMOVED"));
            }
            return before.removed();});
    }
    private com.inigmasgames.hytalerpg.enemies.SuperUniqueAnchor updateEnemyAnchor(UUID world,UUID anchor,
            UnaryOperator<com.inigmasgames.hytalerpg.enemies.SuperUniqueAnchor> operation){
        return locked(()->{var before=enemyAnchor(world,anchor).orElseThrow(()->new IllegalStateException("UNKNOWN_ENEMY_ANCHOR"));
            var after=Objects.requireNonNull(operation.apply(before));if(!before.equals(after))write(keyPath("enemy-anchors",world,anchor),after,true);return after;});
    }
    /** ME roster records share this writer lock, checksums and publication primitive.
     * Invoke on the encounter IO worker; never from a damage filter or native tick. */
    public Optional<com.inigmasgames.hytalerpg.enemies.EnemyPackRecord> enemyPack(UUID world,UUID pack){
        return locked(()->{Path path=keyPath("enemy-packs",world,pack);if(!Files.exists(path))return Optional.empty();
            var saved=read(path,com.inigmasgames.hytalerpg.enemies.EnemyPackRecord.class);
            if(!saved.worldId().equals(world)||!saved.packId().equals(pack))throw new IllegalStateException("ENEMY_PACK_LOOKUP_IDENTITY");
            return Optional.of(saved);});
    }
    public Optional<com.inigmasgames.hytalerpg.enemies.EnemyPackRecord> enemyPackForNativeEntity(UUID world,UUID entity){
        return locked(()->enemyDescriptorByEntity(world,entity).flatMap(actor->actor.packId()==null?Optional.empty():enemyPack(world,actor.packId())));
    }
    /** Recovery inventory under the existing checksummed writer; terminal rows remain visible for reconciliation. */
    public List<com.inigmasgames.hytalerpg.enemies.EnemyPackRecord> enemyPacks(UUID world){
        Objects.requireNonNull(world);
        return locked(()->{
            Path root=directory.resolve("enemy-packs");if(!Files.exists(root))return List.of();
            var records=new ArrayList<com.inigmasgames.hytalerpg.enemies.EnemyPackRecord>();
            int scanned=0;
            try(var shards=Files.newDirectoryStream(root)){
                for(Path shard:shards){
                    if(!Files.isDirectory(shard))throw new IllegalStateException("ENEMY_PACK_INDEX_SHARD");
                    try(var files=Files.newDirectoryStream(shard,"*.json")){
                        for(Path path:files){
                            if(++scanned>65536)throw new IllegalStateException("ENEMY_PACK_INDEX_CAPACITY");
                            var record=read(path,com.inigmasgames.hytalerpg.enemies.EnemyPackRecord.class);
                            if(!path.equals(keyPath("enemy-packs",record.worldId(),record.packId())))
                                throw new IllegalStateException("ENEMY_PACK_INDEX_IDENTITY");
                            if(record.worldId().equals(world))records.add(record);
                        }
                    }
                }
            }catch(IOException failure){throw persistenceFailure(failure);}
            records.sort(Comparator.comparing(com.inigmasgames.hytalerpg.enemies.EnemyPackRecord::packId));
            return List.copyOf(records);
        });
    }
    public com.inigmasgames.hytalerpg.enemies.EnemyPackRecord reserveEnemyPack(com.inigmasgames.hytalerpg.enemies.EnemyPackRecord record){
        if(record.state()!=com.inigmasgames.hytalerpg.enemies.EnemyPackRecord.State.RESERVED||!record.deadMemberReceipts().isEmpty())throw new IllegalArgumentException("PACK_RESERVATION_REQUIRED");
        return locked(()->{var previous=enemyPack(record.worldId(),record.packId());
            if(previous.isPresent()){
                requireSamePackBirth(previous.get(),record);return previous.get();
            }
            for(var member:record.birthRoster())if(deathLocked(record.worldId(),member.nativeEntityId()).isPresent())
                throw new IllegalStateException("PACK_MEMBER_ALREADY_TERMINALLY_DEFEATED");
            write(keyPath("enemy-packs",record.worldId(),record.packId()),record,false);return record;
        });
    }
    public com.inigmasgames.hytalerpg.enemies.EnemyPackRecord transitionEnemyPack(UUID world,UUID pack,
            UnaryOperator<com.inigmasgames.hytalerpg.enemies.EnemyPackRecord> transition){
        return locked(()->{var before=enemyPack(world,pack).orElseThrow(()->new IllegalStateException("PACK_NOT_RESERVED"));
            var after=Objects.requireNonNull(transition.apply(before));requireSamePackBirth(before,after);
            if(!before.deadMemberReceipts().equals(after.deadMemberReceipts())||before.packboundReleased()!=after.packboundReleased())
                throw new IllegalStateException("PACK_TRANSITION_CANNOT_INVENT_DEFEATS");
            boolean permitted=before.equals(after)||switch(before.state()){
                case RESERVED->after.state()==com.inigmasgames.hytalerpg.enemies.EnemyPackRecord.State.STAGED||after.state()==com.inigmasgames.hytalerpg.enemies.EnemyPackRecord.State.ABORTED;
                case STAGED->after.state()==com.inigmasgames.hytalerpg.enemies.EnemyPackRecord.State.GUARDED||after.state()==com.inigmasgames.hytalerpg.enemies.EnemyPackRecord.State.RELEASED||after.state()==com.inigmasgames.hytalerpg.enemies.EnemyPackRecord.State.ABORTED;
                case GUARDED,RELEASED->after.state()==com.inigmasgames.hytalerpg.enemies.EnemyPackRecord.State.SUSPENDED||after.state()==com.inigmasgames.hytalerpg.enemies.EnemyPackRecord.State.ABORTED;
                case SUSPENDED->after.state()==before.suspendedFrom()||after.state()==com.inigmasgames.hytalerpg.enemies.EnemyPackRecord.State.ABORTED;
                case DEFEATED,ABORTED->false;
            };
            if(!permitted)throw new IllegalStateException("PACK_STATE_ROLLBACK_OR_SKIP");
            if(!before.equals(after))write(keyPath("enemy-packs",world,pack),after,true);return after;
        });
    }
    /** Replays only already-durable terminal receipts from this store, including interrupted final-guard commits. */
    public com.inigmasgames.hytalerpg.enemies.EnemyPackRecord reconcileEnemyPackDefeats(UUID world,UUID pack){
        return locked(()->{var before=enemyPack(world,pack).orElseThrow(()->new IllegalStateException("PACK_NOT_RESERVED"));var after=before;
            if(before.state()==com.inigmasgames.hytalerpg.enemies.EnemyPackRecord.State.ABORTED||before.state()==com.inigmasgames.hytalerpg.enemies.EnemyPackRecord.State.DEFEATED)return before;
            // Guard receipts precede the leader's receipt when replaying a journal frontier.
            var ordered=before.birthRoster().stream().sorted(Comparator.comparing(m->Objects.equals(m.logicalActorId(),before.leaderId()))).toList();
            for(var member:ordered){
                var defeat=deathLocked(world,member.nativeEntityId());
                if(defeat.isPresent())after=after.terminalDefeat(member.logicalActorId(),defeat.get().spawn().eventId());
            }
            if(!before.equals(after))write(keyPath("enemy-packs",world,pack),after,true);return after;
        });
    }
    private static void requireSamePackBirth(com.inigmasgames.hytalerpg.enemies.EnemyPackRecord before,com.inigmasgames.hytalerpg.enemies.EnemyPackRecord after){
        if(!before.packId().equals(after.packId())||!before.worldId().equals(after.worldId())||!before.encounterId().equals(after.encounterId())
                ||before.generation()!=after.generation()||!before.anchor().equals(after.anchor())||!before.birthRoster().equals(after.birthRoster())
                ||!Objects.equals(before.leaderId(),after.leaderId())||!before.guardIds().equals(after.guardIds())||!before.spawnPlanReceipt().equals(after.spawnPlanReceipt()))
            throw new IllegalStateException("SEALED_PACK_BIRTH_MISMATCH");
    }
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
    /** Gear receipts share the existing encounter writer lock, checksums and atomic publication. */
    public <T> T gearTransaction(String namespace,String key,Class<T> type,Function<Optional<T>,T> operation){
        if(!Set.of("claims","loot","loot-picks","loot-quantity","cursors","salvage","sentinels","sentinel-replacement-backups","spatial-pickups","spatial-equipment","spatial-stock","spatial-stock-drop").contains(namespace)||key==null||key.isBlank()||key.length()>512)throw new IllegalArgumentException("Gear receipt key");
        return locked(()->{Path path=directory.resolve("gear").resolve(namespace).resolve(RewardIntent.digest(key)+".json");
            T before=Files.exists(path)?read(path,type):null;T after=Objects.requireNonNull(operation.apply(Optional.ofNullable(before)));
            if(!after.equals(before)){write(path,after,before!=null);try{gearFault.accept("AFTER_"+namespace);}catch(RuntimeException failure){uncertain=true;throw failure;}}return after;});
    }
    public <T> Optional<T> gearRead(String namespace,String key,Class<T> type){
        if(!Set.of("claims","loot","loot-picks","loot-quantity","cursors","salvage","sentinels","sentinel-replacement-backups","spatial-pickups","spatial-equipment","spatial-stock","spatial-stock-drop").contains(namespace))throw new IllegalArgumentException("Gear namespace");
        return locked(()->{Path path=directory.resolve("gear").resolve(namespace).resolve(RewardIntent.digest(key)+".json");return Files.exists(path)?Optional.of(read(path,type)):Optional.empty();});
    }
    /** Bounded diagnostic/projection query, executed only on the gear IO worker. */
    public <T> List<T> gearRecords(String namespace,Class<T> type){
        if(!Set.of("loot","salvage","sentinels","spatial-pickups","spatial-equipment","spatial-stock","spatial-stock-drop").contains(namespace))throw new IllegalArgumentException("Gear namespace");
        return locked(()->{var result=new ArrayList<T>();Path folder=directory.resolve("gear").resolve(namespace);if(!Files.exists(folder))return List.of();
            try(var files=Files.newDirectoryStream(folder,"*.json")){for(Path path:files){if(result.size()>=4096)throw new IllegalStateException("Gear projection index capacity; archival index required");result.add(read(path,type));}}
            catch(IOException error){throw persistenceFailure(error);}return List.copyOf(result);});
    }
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
            if(!previous.get().equals(plan))throw new IllegalStateException("DEATH_PLAN_CONFLICT");
            reconcileFrozenEnemyPack(spawn);return previous.get();
        }
        frozenEnemyPackAdmission(spawn);
        var context=loadLocked(spawn.world(),spawn.enemy()).orElseThrow(()->new IllegalStateException("UNREGISTERED_ENCOUNTER"));
        if(!context.spawn().equals(spawn)||plan.deathAtMillis()<context.lastObserved())throw new IllegalStateException("DEATH_CONTEXT_MISMATCH");
        if(context.disqualified()&&!plan.shares().isEmpty())throw new IllegalStateException("DISQUALIFIED_DEATH_REWARD");
        // Core eligibility must already have produced these recipients. Never accept a share without persisted credit.
        for(var share:plan.shares())if(context.credits().stream().noneMatch(c->c.player().equals(share.player())
                &&plan.deathAtMillis()>=c.observedAtMillis()&&plan.deathAtMillis()-c.observedAtMillis()<=EncounterContributions.CONTRIBUTION_WINDOW_MS))throw new IllegalStateException("DEATH_WITHOUT_PERSISTED_CONTRIBUTION");
        if(pendingFiles().size()>=MAX_PENDING)throw new IllegalStateException("DEATH_QUEUE_CAPACITY");
        write(pending(spawn.world(),spawn.enemy()),new Delivery(plan,0),false);fault.accept(Boundary.AFTER_FREEZE);
        reconcileFrozenEnemyPack(spawn);return plan;
    });}
    private Optional<com.inigmasgames.hytalerpg.enemies.EnemyDescriptor> frozenEnemyPackAdmission(EnemyRewardRegistry.Spawn spawn){
        var descriptor=enemyDescriptorByEntity(spawn.world(),spawn.enemy());
        if(descriptor.isEmpty()){
            if(spawn.enemyRewards()!=null)throw new IllegalStateException("ENEMY_DEATH_REWARD_WITHOUT_DESCRIPTOR");
            return descriptor;
        }
        var actor=descriptor.get();
        if(compensatedOrdinary(actor,spawn))return Optional.empty();
        if(!actor.entityId().equals(spawn.enemy())||!Objects.equals(actor.immutableRewardContext(),spawn.enemyRewards()))
            throw new IllegalStateException("ENEMY_DEATH_FROZEN_CONTEXT_MISMATCH");
        if(actor.packId()!=null){
            var pack=enemyPack(spawn.world(),actor.packId()).orElseThrow(()->new IllegalStateException("ENEMY_DEATH_PACK_MISSING"));
            if(pack.generation()!=actor.encounterGeneration()||!pack.economicAdmission(actor.logicalActorId())
                    ||pack.blocksExternalMutation(actor.logicalActorId())||pack.deadMemberReceipts().containsKey(actor.logicalActorId()))
                throw new IllegalStateException("ENEMY_DEATH_PACK_NOT_ADMITTED");
        }
        return descriptor;
    }
    private void reconcileFrozenEnemyPack(EnemyRewardRegistry.Spawn spawn){
        var descriptor=enemyDescriptorByEntity(spawn.world(),spawn.enemy());
        if(descriptor.isPresent()&&descriptor.get().packId()!=null&&!compensatedOrdinary(descriptor.get(),spawn))
            reconcileEnemyPackDefeats(spawn.world(),descriptor.get().packId());
    }
    private boolean compensatedOrdinary(com.inigmasgames.hytalerpg.enemies.EnemyDescriptor actor,EnemyRewardRegistry.Spawn spawn){
        var decision=enemyBirthCompensation(actor.worldId(),actor.encounterId());
        if(decision.isEmpty())return false;
        var root=enemyBirthRoot(actor.worldId(),actor.encounterId()).orElseThrow(()->new IllegalStateException("ENEMY_COMPENSATION_ROOT_MISSING"));
        if(!decision.get().equals(com.inigmasgames.hytalerpg.enemies.EnemyBirthCompensation.of(root)))
            throw new IllegalStateException("ENEMY_COMPENSATION_ROOT_MISMATCH");
        var pack=enemyPack(actor.worldId(),root.plan().pack().packId()).orElseThrow();
        if(pack.state()!=com.inigmasgames.hytalerpg.enemies.EnemyPackRecord.State.ABORTED
                ||!"BIRTH_COMPENSATED".equals(pack.abortReason()))throw new IllegalStateException("ENEMY_COMPENSATION_NOT_COMPLETE");
        var original=root.originalSpawns().stream().filter(source->source.enemy().equals(actor.entityId())).findFirst();
        if(original.isEmpty()||!original.get().equals(spawn))throw new IllegalStateException("ENEMY_COMPENSATION_CONTEXT_MISMATCH");
        return true;
    }
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
                    gearDelivery.accept(plan);
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
            if(!Files.isRegularFile(path)||Files.size(path)>fileLimit(path))throw new IllegalStateException("ENCOUNTER_FILE_BOUNDS");
            var envelope=JsonParser.parseString(Files.readString(path,StandardCharsets.UTF_8)).getAsJsonObject();
            if(envelope.size()!=3||envelope.get("schema").getAsInt()!=1)throw new IllegalStateException("ENCOUNTER_FILE_SCHEMA");
            var payload=envelope.get("payload");if(!RewardIntent.digest(JSON.toJson(payload)).equals(envelope.get("checksum").getAsString()))throw new IllegalStateException("ENCOUNTER_FILE_CHECKSUM");
            T value=JSON.fromJson(payload,type);
            if(value==null||!matchesPersistedShape(payload,JSON.toJsonTree(value),type))
                throw new IllegalStateException("ENCOUNTER_FILE_SHAPE");
            return value;
        }catch(IOException|RuntimeException error){throw failure("ENCOUNTER_FILE_UNREADABLE: "+path,error);}
    }
    private static boolean matchesPersistedShape(JsonElement persisted,JsonElement current,Class<?> type){
        if(current.equals(persisted))return true;
        // The v1/v2 Sentinel ledger predates the frozen owner equipment snapshot.
        // Its canonical constructor supplies an empty ownerItems list. Accept only
        // that one additive default after verifying the original payload checksum.
        if(type!=IronSentinelBinding.class||!persisted.isJsonObject()||!current.isJsonObject())return false;
        JsonObject original=persisted.getAsJsonObject();
        if(!original.has("schemaVersion")||original.get("schemaVersion").getAsInt()>2
                ||original.has("ownerItems"))return false;
        JsonObject expected=original.deepCopy();
        expected.add("ownerItems",new JsonArray());
        return current.equals(expected);
    }
    private static int fileLimit(Path path){
        Path parent=path.getParent();
        return parent!=null&&parent.getFileName().toString().equals("spatial-equipment")
                &&parent.getParent()!=null&&parent.getParent().getFileName().toString().equals("gear")
                ?MAX_SPATIAL_EQUIPMENT_FILE_BYTES:MAX_FILE_BYTES;
    }
    private void write(Path path,Object value,boolean replace){
        Path temporary=path.resolveSibling(path.getFileName()+"."+UUID.randomUUID()+".tmp");
        foregroundQueued();
        try{
            Files.createDirectories(path.getParent());if(!replace&&Files.exists(path))throw new IllegalStateException("ENCOUNTER_IMMUTABLE_FILE_EXISTS");
            long serializationStart=System.nanoTime();
            var payload=JSON.toJsonTree(value);var envelope=new JsonObject();envelope.addProperty("schema",1);
            envelope.addProperty("checksum",RewardIntent.digest(JSON.toJson(payload)));envelope.add("payload",payload);
            byte[] bytes=JSON.toJson(envelope).getBytes(StandardCharsets.UTF_8);if(bytes.length>fileLimit(path))throw new IllegalStateException("ENCOUNTER_FILE_BOUNDS");
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
