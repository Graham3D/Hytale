package com.inigmasgames.hytalerpg.execution;

import java.util.*;

/** Extracted native displacement attempt lock: bounded live entries, monotonic time, no live eviction. */
public final class TimedOpportunityLedger<K> {
    public enum Result { CLAIMED, LOCKED, CAPACITY, CLOCK_REVERSED }
    private record Lock(double start,double duration){}
    public record Remaining<K>(K key,double seconds){
        public Remaining{Objects.requireNonNull(key);if(!Double.isFinite(seconds)||seconds<=0)throw new IllegalArgumentException("INVALID_REMAINING_OPPORTUNITY_LOCK");}
    }
    private final int capacity;
    private final Map<K,Lock> locks=new LinkedHashMap<>();
    private double latest=Double.NEGATIVE_INFINITY;
    public TimedOpportunityLedger(int capacity){if(capacity<1||capacity>65536)throw new IllegalArgumentException("OPPORTUNITY_LOCK_CAPACITY");this.capacity=capacity;}
    public synchronized Result claim(K key,double now,double duration){
        Objects.requireNonNull(key);
        if(!Double.isFinite(now)||!Double.isFinite(duration)||duration<=0)throw new IllegalArgumentException("OPPORTUNITY_LOCK_TIME");
        if(now<latest)return Result.CLOCK_REVERSED;
        expire(now);
        if(locks.containsKey(key))return Result.LOCKED;
        if(locks.size()>=capacity)return Result.CAPACITY;
        locks.put(key,new Lock(now,duration));return Result.CLAIMED;
    }
    private void expire(double now){latest=now;locks.values().removeIf(lock->now-lock.start>=lock.duration);}
    public synchronized int size(){return locks.size();}
    /** Capture remaining duration, never an absolute process-monotonic timestamp. */
    public synchronized List<Remaining<K>> snapshot(double now){
        if(!Double.isFinite(now)||now<latest)throw new IllegalArgumentException("OPPORTUNITY_SNAPSHOT_CLOCK");
        expire(now);var result=new ArrayList<Remaining<K>>(locks.size());
        locks.forEach((key,lock)->result.add(new Remaining<>(key,lock.duration-(now-lock.start))));return List.copyOf(result);
    }
    /** Rebind/reload into a fresh lifetime. Live locks cannot be overwritten by an older snapshot. */
    public synchronized void restore(List<Remaining<K>> saved,double now){
        if(!Double.isFinite(now)||latest!=Double.NEGATIVE_INFINITY||!locks.isEmpty()||saved.size()>capacity)
            throw new IllegalStateException("OPPORTUNITY_RESTORE_STATE");
        var next=new LinkedHashMap<K,Lock>();
        for(var entry:saved)if(next.putIfAbsent(entry.key(),new Lock(now,entry.seconds()))!=null)throw new IllegalArgumentException("DUPLICATE_OPPORTUNITY_LOCK");
        locks.putAll(next);latest=now;
    }
}
