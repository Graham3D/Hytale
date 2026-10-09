package com.inigmasgames.hytalerpg.enemies;

import com.inigmasgames.hytalerpg.execution.math.Vec3;
import com.inigmasgames.hytalerpg.progress.EncounterContributions;
import java.util.*;
import static com.inigmasgames.hytalerpg.enemies.EnemyAffixRegistry.require;

/** ME anchor limits only. A reservation does not replace native population/placement admission. */
public final class EnemyPackCapacity {
    public record Reservation(UUID world,UUID encounter,UUID pack,long generation,Vec3 anchor){
        public Reservation{Objects.requireNonNull(world);Objects.requireNonNull(encounter);Objects.requireNonNull(pack);Objects.requireNonNull(anchor);require(generation>=0,"PACK_CAPACITY_GENERATION");}
        public static Reservation of(EnemyPackRecord pack){return new Reservation(pack.worldId(),pack.encounterId(),pack.packId(),pack.generation(),pack.anchor());}
    }
    public enum Gate { RESERVED, ALREADY_RESERVED, WORLD_LIMIT, CELL_LIMIT, ENCOUNTER_CAPACITY }
    public record Admission(Gate gate,Reservation reservation){public boolean accepted(){return reservation!=null;}}
    private record Key(UUID world,UUID pack){}
    private record Cell(long x,long z){}
    private final java.util.function.Supplier<EnemyBalance.Promotion> policy;
    private final Map<Key,Reservation> active=new HashMap<>();
    public EnemyPackCapacity(EnemyBalance balance){this(()->Objects.requireNonNull(balance).promotion());}
    public EnemyPackCapacity(java.util.function.Supplier<EnemyBalance.Promotion> policy){this.policy=Objects.requireNonNull(policy);}
    public synchronized Admission reserve(Reservation requested){
        Objects.requireNonNull(requested);var key=new Key(requested.world(),requested.pack());var existing=active.get(key);
        if(existing!=null){require(existing.equals(requested),"CONFLICTING_PACK_CAPACITY_RESERVATION");return new Admission(Gate.ALREADY_RESERVED,existing);}
        var gate=available(requested,active.values());if(gate!=Gate.RESERVED)return new Admission(gate,null);
        active.put(key,requested);return new Admission(Gate.RESERVED,requested);
    }
    /** A late callback for an old generation cannot free a current reservation. */
    public synchronized boolean release(Reservation expected){return active.remove(new Key(expected.world(),expected.pack()),expected);}
    public synchronized int count(UUID world){return (int)active.values().stream().filter(value->value.world().equals(world)).count();}
    public synchronized int count(UUID world,Vec3 anchor){var cell=cell(anchor);return (int)active.values().stream().filter(value->value.world().equals(world)&&cell(value.anchor()).equals(cell)).count();}
    public synchronized List<Reservation> snapshot(UUID world){return active.values().stream().filter(value->value.world().equals(world)).sorted(Comparator.comparing(Reservation::pack)).toList();}
    /** Only the confirmed complete world-unload owner may use this operation. */
    public synchronized void unload(UUID world){active.keySet().removeIf(key->key.world().equals(world));}
    /** Restore the complete loaded world's durable reservations atomically before fresh admission. */
    public synchronized void restore(UUID world,Collection<EnemyPackRecord> records){
        require(count(world)==0,"PACK_CAPACITY_RESTORE_REQUIRES_EMPTY_WORLD");
        var proposed=new HashMap<>(active);var seen=new HashSet<UUID>();
        for(var record:records){
            require(record.worldId().equals(world)&&seen.add(record.packId()),"PACK_CAPACITY_RESTORE_IDENTITY");
            if(record.state()==EnemyPackRecord.State.ABORTED||record.state()==EnemyPackRecord.State.DEFEATED)continue;
            var reservation=Reservation.of(record);
            // Existing durable packs are grandfathered if the operator later lowers new-pack limits.
            require(proposed.size()<EncounterContributions.MAX_ENCOUNTERS,"PACK_CAPACITY_RECOVERY_GLOBAL_LIMIT");
            proposed.put(new Key(world,record.packId()),reservation);
        }
        active.clear();active.putAll(proposed);
    }
    private Gate available(Reservation requested,Collection<Reservation> existing){
        var requestedCell=cell(requested.anchor());
        if(existing.size()>=EncounterContributions.MAX_ENCOUNTERS)return Gate.ENCOUNTER_CAPACITY;
        int world=0,local=0;for(var value:existing)if(value.world().equals(requested.world())){
            world++;if(cell(value.anchor()).equals(requestedCell))local++;
        }
        var limits=policy.get();
        return world>=limits.worldLimit()?Gate.WORLD_LIMIT:local>=limits.cellLimit()?Gate.CELL_LIMIT:Gate.RESERVED;
    }
    private Cell cell(Vec3 anchor){return new Cell(coordinate(anchor.x()),coordinate(anchor.z()));}
    private long coordinate(double value){double cell=Math.floor(value/policy.get().cellMeters());require(cell>=Long.MIN_VALUE&&cell<0x1p63,"PACK_ANCHOR_CELL_RANGE");return (long)cell;}
}
