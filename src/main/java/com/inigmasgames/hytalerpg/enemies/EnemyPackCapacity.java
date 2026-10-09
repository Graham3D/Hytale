package com.inigmasgames.hytalerpg.enemies;

import com.inigmasgames.hytalerpg.execution.math.Vec3;
import com.inigmasgames.hytalerpg.progress.EncounterContributions;
import java.util.*;
import static com.inigmasgames.hytalerpg.enemies.EnemyAffixRegistry.require;

/** Volatile active/pending pack leases. Durable pack records are recovered separately. */
public final class EnemyPackCapacity {
    public record Reservation(UUID world,UUID encounter,UUID pack,long generation,Vec3 anchor){
        public Reservation{Objects.requireNonNull(world);Objects.requireNonNull(encounter);Objects.requireNonNull(pack);Objects.requireNonNull(anchor);require(generation>=0,"PACK_CAPACITY_GENERATION");}
        public static Reservation of(EnemyPackRecord pack){return new Reservation(pack.worldId(),pack.encounterId(),pack.packId(),pack.generation(),pack.anchor());}
    }
    public enum Gate { RESERVED, ALREADY_RESERVED, WORLD_LIMIT, CELL_LIMIT, ENCOUNTER_CAPACITY }
    public record Admission(Gate gate,Reservation reservation){public boolean accepted(){return reservation!=null;}}
    public record Snapshot(int configuredActiveLimit,int configuredCellLimit,int loadedActiveProductionPacks,
            int pendingNewBirths,int grandfatheredOverCapPacks,Map<String,Integer> activeBy64mCell,
            long newBirthRejectionsWorldCap,long newBirthRejectionsCellCap){
        public Snapshot{activeBy64mCell=Map.copyOf(activeBy64mCell);}
        public int occupied(){return loadedActiveProductionPacks+pendingNewBirths;}
    }
    private record Key(UUID world,UUID encounter){}
    private record Cell(long x,long z){@Override public String toString(){return x+","+z;}}
    private static final class Lease {
        final Reservation reservation;final Set<UUID> loaded=new HashSet<>();
        boolean pending;int transitions;
        Lease(Reservation reservation,boolean pending){this.reservation=reservation;this.pending=pending;}
    }
    private final java.util.function.Supplier<EnemyBalance.Promotion> policy;
    private final Map<Key,Lease> leases=new HashMap<>();
    private final Map<UUID,long[]> denials=new HashMap<>();
    public EnemyPackCapacity(EnemyBalance balance){this(()->Objects.requireNonNull(balance).promotion());}
    public EnemyPackCapacity(java.util.function.Supplier<EnemyBalance.Promotion> policy){this.policy=Objects.requireNonNull(policy);}

    /** Atomic check-and-reserve counts pending births alongside loaded incumbents. */
    public synchronized Admission reserve(Reservation requested){
        Objects.requireNonNull(requested);var key=new Key(requested.world(),requested.encounter());var existing=leases.get(key);
        if(existing!=null){require(existing.reservation.equals(requested),"CONFLICTING_PACK_CAPACITY_RESERVATION");return new Admission(Gate.ALREADY_RESERVED,existing.reservation);}
        var gate=available(requested);
        if(gate!=Gate.RESERVED){
            var counts=denials.computeIfAbsent(requested.world(),ignored->new long[2]);
            if(gate==Gate.WORLD_LIMIT)counts[0]++;else if(gate==Gate.CELL_LIMIT)counts[1]++;
            return new Admission(gate,null);
        }
        leases.put(key,new Lease(requested,true));return new Admission(Gate.RESERVED,requested);
    }
    /** Publication or saved-actor rebind. An incumbent never competes with a new birth for a slot. */
    public synchronized boolean activate(Reservation incumbent,Collection<UUID> confirmedLoaded){
        Objects.requireNonNull(incumbent);Objects.requireNonNull(confirmedLoaded);
        var key=new Key(incumbent.world(),incumbent.encounter());var lease=leases.get(key);
        if(lease!=null)require(lease.reservation.equals(incumbent),"CONFLICTING_PACK_CAPACITY_RESERVATION");
        if(confirmedLoaded.isEmpty()){
            if(lease!=null&&lease.pending)leases.remove(key);
            return false;
        }
        if(lease==null){lease=new Lease(incumbent,false);leases.put(key,lease);}
        boolean first=lease.loaded.isEmpty();
        for(var actor:confirmedLoaded)lease.loaded.add(Objects.requireNonNull(actor));
        lease.pending=false;
        return first;
    }
    /** Hold a last-member unload until its durable SUSPENDED transition is acknowledged. */
    public synchronized void beginTransition(Reservation expected){var lease=matching(expected);if(lease!=null)lease.transitions++;}
    public synchronized boolean endTransition(Reservation expected){
        var lease=matching(expected);if(lease==null)return false;
        require(lease.transitions>0,"PACK_CAPACITY_TRANSITION_UNBALANCED");lease.transitions--;
        return releaseEmpty(expected,lease);
    }
    /** A duplicate/stale native removal cannot decrement another pack or another member. */
    public synchronized boolean removeActor(Reservation expected,UUID actor){
        var lease=matching(expected);if(lease==null||!lease.loaded.remove(actor))return false;
        return releaseEmpty(expected,lease);
    }
    private boolean releaseEmpty(Reservation expected,Lease lease){
        if(lease.pending||lease.transitions>0||!lease.loaded.isEmpty())return false;
        return leases.remove(new Key(expected.world(),expected.encounter()),lease);
    }
    /** Terminal durable receipt or pre-seal fallback; stale generations cannot release a new lease. */
    public synchronized boolean release(Reservation expected){
        Objects.requireNonNull(expected);var key=new Key(expected.world(),expected.encounter());
        var lease=leases.get(key);
        // A completion from an older generation must never retire a newer lease.
        return lease!=null&&lease.reservation.equals(expected)&&leases.remove(key,lease);
    }
    private Lease matching(Reservation expected){
        Objects.requireNonNull(expected);var lease=leases.get(new Key(expected.world(),expected.encounter()));
        if(lease!=null)require(lease.reservation.equals(expected),"CONFLICTING_PACK_CAPACITY_RESERVATION");
        return lease;
    }
    public synchronized int count(UUID world){return (int)leases.values().stream().filter(value->value.reservation.world().equals(world)).count();}
    public synchronized int count(UUID world,Vec3 anchor){var target=cell(anchor);return (int)leases.values().stream()
            .filter(value->value.reservation.world().equals(world)&&cell(value.reservation.anchor()).equals(target)).count();}
    public synchronized boolean contains(Reservation reservation){return matching(reservation)!=null;}
    public synchronized boolean pending(Reservation reservation){var lease=matching(reservation);return lease!=null&&lease.pending;}
    public synchronized List<Reservation> snapshot(UUID world){return leases.values().stream().map(value->value.reservation)
            .filter(value->value.world().equals(world)).sorted(Comparator.comparing(Reservation::pack)).toList();}
    public synchronized Snapshot metrics(UUID world){
        var limits=policy.get();var cells=new TreeMap<String,Integer>();int loaded=0,pending=0;
        for(var lease:leases.values())if(lease.reservation.world().equals(world)){
            if(lease.pending)pending++;else if(!lease.loaded.isEmpty())loaded++;
            cells.merge(cell(lease.reservation.anchor()).toString(),1,Integer::sum);
        }
        int overWorld=Math.max(0,loaded+pending-limits.worldLimit());
        int overCell=cells.values().stream().mapToInt(count->Math.max(0,count-limits.cellLimit())).sum();
        var rejected=denials.getOrDefault(world,new long[2]);
        return new Snapshot(limits.worldLimit(),limits.cellLimit(),loaded,pending,Math.max(overWorld,overCell),cells,
                rejected[0],rejected[1]);
    }
    /** Only the confirmed complete world-unload owner may clear volatile leases. */
    public synchronized void unload(UUID world){leases.keySet().removeIf(key->key.world().equals(world));denials.remove(world);}
    /** Validate recovered records, but never infer loaded occupancy from a durable descriptor. */
    public synchronized void restore(UUID world,Collection<EnemyPackRecord> records){
        require(count(world)==0,"PACK_CAPACITY_RESTORE_REQUIRES_EMPTY_WORLD");
        var packs=new HashSet<UUID>();var encounters=new HashSet<UUID>();
        for(var record:records)require(record.worldId().equals(world)&&packs.add(record.packId())
                &&encounters.add(record.encounterId()),"PACK_CAPACITY_RESTORE_IDENTITY");
    }
    private Gate available(Reservation requested){
        if(leases.size()>=EncounterContributions.MAX_ENCOUNTERS)return Gate.ENCOUNTER_CAPACITY;
        var limits=policy.get();int world=count(requested.world());
        if(world>=limits.worldLimit())return Gate.WORLD_LIMIT;
        return count(requested.world(),requested.anchor())>=limits.cellLimit()?Gate.CELL_LIMIT:Gate.RESERVED;
    }
    public synchronized String cellLabel(Vec3 anchor){return cell(anchor).toString();}
    private Cell cell(Vec3 anchor){return new Cell(coordinate(anchor.x()),coordinate(anchor.z()));}
    private long coordinate(double value){double result=Math.floor(value/policy.get().cellMeters());require(result>=Long.MIN_VALUE&&result<0x1p63,"PACK_ANCHOR_CELL_RANGE");return (long)result;}
}
