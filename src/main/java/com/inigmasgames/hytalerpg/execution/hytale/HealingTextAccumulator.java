package com.inigmasgames.hytalerpg.execution.hytale;

import java.util.*;

/** Presentation only: no Health writes, native references, payment or gameplay timers. */
public final class HealingTextAccumulator {
    public record Key(UUID owner, UUID world, String root, String target, String instance, String correlation) {
        public Key(UUID owner,UUID world,String root,String target){this(owner,world,root,target,root,root);}
    }
    public record Value(Key key, double actualHealing, int pulses) { }
    private record Pending(double until, double amount, int pulses) { }
    private final Map<Key,Pending> pending=new LinkedHashMap<>();
    public synchronized List<Value> add(Key key,double now,double before,double after) {
        if(!Double.isFinite(now)||!Double.isFinite(before)||!Double.isFinite(after))throw new IllegalArgumentException("Nonfinite heal observation");
        var result=new ArrayList<Value>();
        var prior=pending.get(key);
        if(prior!=null&&now>=prior.until){result.add(new Value(key,prior.amount,prior.pulses));pending.remove(key);prior=null;}
        if(prior==null&&pending.size()>=768){ // Six recipients per admitted root; flush instead of discarding.
            var oldest=pending.entrySet().iterator().next();
            result.add(new Value(oldest.getKey(),oldest.getValue().amount,oldest.getValue().pulses));pending.remove(oldest.getKey());
        }
        pending.put(key,new Pending(prior==null?now+.5:prior.until,
                (prior==null?0:prior.amount)+Math.max(0,after-before),(prior==null?0:prior.pulses)+1));
        return List.copyOf(result);
    }
    public synchronized List<Value> flush(UUID owner, String root, double now, boolean force) {
        var values=new ArrayList<Value>();
        pending.entrySet().removeIf(e->{
            if(!e.getKey().owner.equals(owner)||(root!=null&&!root.equals(e.getKey().root))||!force&&now<e.getValue().until)return false;
            values.add(new Value(e.getKey(),e.getValue().amount,e.getValue().pulses));return true;
        });
        return List.copyOf(values);
    }
    public static String text(double amount) {
        if(amount==0)return "0";
        return "+"+java.math.BigDecimal.valueOf(amount).setScale(2,java.math.RoundingMode.HALF_UP).stripTrailingZeros().toPlainString();
    }
    public synchronized int size(){return pending.size();}
}
