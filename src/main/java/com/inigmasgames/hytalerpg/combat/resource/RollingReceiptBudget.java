package com.inigmasgames.hytalerpg.combat.resource;

import java.util.*;
import java.util.function.DoubleUnaryOperator;

/** Weighted rolling extension of RootLeechBudget's reserve/write/readback contract. */
public final class RollingReceiptBudget<K> {
    public enum Gate { APPLIED, EMPTY, DUPLICATE, CAPACITY, CLOCK_REVERSED, WRITE_UNCERTAIN }
    public record Result(Gate gate,double allowed,double actual){}
    public record Saved<K>(K key,String receipt,long remainingNanos,double amount){
        public Saved{
            Objects.requireNonNull(key);receiptId(receipt);
            if(remainingNanos<=0||!finiteAmount(amount))throw new IllegalArgumentException("ROLLING_RECEIPT_SNAPSHOT");
        }
    }
    private record Receipt(String id,long at,double amount){}
    private final long windowNanos;
    private final int maximumKeys,maximumPerKey,maximumReceipts;
    private final Map<K,ArrayDeque<Receipt>> history=new LinkedHashMap<>();
    private boolean started,writing;
    private long latest;
    private int size;

    public RollingReceiptBudget(long windowNanos,int maximumKeys,int maximumPerKey,int maximumReceipts){
        if(windowNanos<=0||windowNanos>120_000_000_000L||maximumKeys<1||maximumPerKey<1
                ||maximumReceipts<1||maximumKeys>65536||maximumPerKey>65536||maximumReceipts>65536)
            throw new IllegalArgumentException("ROLLING_BUDGET_BOUNDS");
        this.windowNanos=windowNanos;this.maximumKeys=maximumKeys;this.maximumPerKey=maximumPerKey;this.maximumReceipts=maximumReceipts;
    }
    /** Native write must be synchronous, bounded and on its owning world thread. No failed write is retried. */
    public synchronized Result apply(K key,String receipt,long now,double requested,double currentCap,DoubleUnaryOperator write){
        Objects.requireNonNull(key);Objects.requireNonNull(write);receiptId(receipt);
        if(!finiteAmount(requested)||!finiteAmount(currentCap))throw new IllegalArgumentException("ROLLING_BUDGET_AMOUNT");
        if(writing)throw new IllegalStateException("REENTRANT_ROLLING_WRITE");
        if(started&&now<latest)return new Result(Gate.CLOCK_REVERSED,0,0);
        expire(now);var entries=history.get(key);
        if(entries!=null&&entries.stream().anyMatch(value->value.id.equals(receipt)))return new Result(Gate.DUPLICATE,0,0);
        if(size>=maximumReceipts||entries==null&&history.size()>=maximumKeys||entries!=null&&entries.size()>=maximumPerKey)
            return new Result(Gate.CAPACITY,0,0);
        double used=entries==null?0:entries.stream().mapToDouble(Receipt::amount).sum();
        double allowed=Math.min(requested,Math.max(0,currentCap-used));
        if(entries==null){entries=new ArrayDeque<>();history.put(key,entries);}
        // Even an empty or uncertain attempt is claimed for this live window.
        entries.addLast(new Receipt(receipt,now,allowed));size++;
        if(allowed==0)return new Result(Gate.EMPTY,0,0);
        writing=true;
        try{
            double actual=write.applyAsDouble(allowed);
            if(!finiteAmount(actual)||actual>allowed)return new Result(Gate.WRITE_UNCERTAIN,allowed,0);
            entries.removeLast();entries.addLast(new Receipt(receipt,now,actual));
            return new Result(actual>0?Gate.APPLIED:Gate.EMPTY,allowed,actual);
        }catch(RuntimeException failure){return new Result(Gate.WRITE_UNCERTAIN,allowed,0);}
        finally{writing=false;}
    }
    private void expire(long now){
        latest=now;started=true;
        for(var iterator=history.values().iterator();iterator.hasNext();){
            var entries=iterator.next();
            while(!entries.isEmpty()&&now-entries.getFirst().at>=windowNanos){entries.removeFirst();size--;}
            if(entries.isEmpty())iterator.remove();
        }
    }
    public synchronized List<Saved<K>> snapshot(long now){
        if(writing||started&&now<latest)throw new IllegalStateException("ROLLING_SNAPSHOT_STATE");
        expire(now);var saved=new ArrayList<Saved<K>>(size);
        history.forEach((key,entries)->entries.forEach(value->saved.add(new Saved<>(key,value.id,windowNanos-(now-value.at),value.amount))));
        return List.copyOf(saved);
    }
    /** Fresh owner only; remaining windows survive reconnect/rebind without carrying process timestamps. */
    public synchronized void restore(List<Saved<K>> saved,long now){
        if(started||writing||saved.size()>maximumReceipts)throw new IllegalStateException("ROLLING_RESTORE_STATE");
        var next=new LinkedHashMap<K,ArrayDeque<Receipt>>();
        for(var value:saved){
            if(value.remainingNanos>windowNanos)throw new IllegalArgumentException("ROLLING_RESTORE_WINDOW");
            var entries=next.computeIfAbsent(value.key,ignored->new ArrayDeque<>());
            long at=Math.subtractExact(now,windowNanos-value.remainingNanos);
            if(entries.size()>=maximumPerKey||entries.stream().anyMatch(entry->entry.id.equals(value.receipt))
                    ||!entries.isEmpty()&&at<entries.getLast().at)throw new IllegalArgumentException("ROLLING_RESTORE_RECEIPTS");
            entries.addLast(new Receipt(value.receipt,at,value.amount));
        }
        if(next.size()>maximumKeys)throw new IllegalArgumentException("ROLLING_RESTORE_KEYS");
        history.putAll(next);size=saved.size();latest=now;started=true;
    }
    private static void receiptId(String value){if(value==null||value.isBlank()||value.length()>1024)throw new IllegalArgumentException("ROLLING_RECEIPT_ID");}
    private static boolean finiteAmount(double value){return Double.isFinite(value)&&value>=0;}
}
