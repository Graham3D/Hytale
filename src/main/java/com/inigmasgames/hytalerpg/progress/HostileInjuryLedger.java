package com.inigmasgames.hytalerpg.progress;

import java.util.*;

/** Bounded, ephemeral provenance for still-missing hostile Health, never a Health authority. */
public final class HostileInjuryLedger {
    public static final int MAX_RECIPIENTS=4096,MAX_INJURIES=64;
    private record Key(UUID world,UUID recipient){}
    private record Injury(UUID enemy,double low,double high,long at){}
    private static final class State {double health;final List<Injury> injuries=new ArrayList<>();State(double hp){health=hp;}}
    private final Map<Key,State> states=new HashMap<>();
    public synchronized void damage(UUID world,UUID recipient,UUID eligibleEnemy,double before,double after,long now){
        if(!valid(before,after)||before<=after)return;
        var key=new Key(world,recipient);var state=state(key,before);if(state==null)return;
        reconcile(state,before,now);
        if(eligibleEnemy!=null){
            if(state.injuries.size()<MAX_INJURIES)state.injuries.add(new Injury(eligibleEnemy,after,before,now));
        }
        state.health=after;
    }
    /** Any known Health resource spend or unclassified write invalidates outstanding healing credit. */
    public synchronized void invalidate(UUID world,UUID recipient){states.remove(new Key(world,recipient));}
    public synchronized Map<UUID,Double> healed(UUID world,UUID recipient,double before,double after,long now){
        if(!valid(before,after)||after<=before)return Map.of();
        var state=states.get(new Key(world,recipient));if(state==null)return Map.of();reconcile(state,before,now);
        Map<UUID,Double> result=new LinkedHashMap<>();
        for(var injury:state.injuries){double overlap=Math.max(0,Math.min(after,injury.high)-Math.max(before,injury.low));
            if(overlap>0)result.merge(injury.enemy,overlap,Double::sum);}
        trim(state,after);state.health=after;return Map.copyOf(result);
    }
    public synchronized void observedHealth(UUID world,UUID recipient,double current,long now){
        var state=states.get(new Key(world,recipient));if(state!=null&&Double.isFinite(current)&&current>=0)reconcile(state,current,now);
    }
    public synchronized void forget(UUID recipient){states.keySet().removeIf(k->k.recipient.equals(recipient));}
    public synchronized int size(){return states.size();}
    private State state(Key key,double hp){if(!states.containsKey(key)&&states.size()>=MAX_RECIPIENTS)return null;return states.computeIfAbsent(key,k->new State(hp));}
    private static void reconcile(State state,double health,long now){
        state.injuries.removeIf(i->now<i.at||now-i.at>20_000);
        // A missing downward transition has unknown provenance; retain no stale entitlement.
        if(health<state.health-1e-6)state.injuries.clear();else trim(state,health);
        state.health=health;
    }
    private static void trim(State state,double health){
        var kept=new ArrayList<Injury>();for(var i:state.injuries)if(i.high>health)kept.add(new Injury(i.enemy,Math.max(i.low,health),i.high,i.at));
        state.injuries.clear();state.injuries.addAll(kept);
    }
    private static boolean valid(double before,double after){return Double.isFinite(before)&&Double.isFinite(after)&&before>=0&&after>=0;}
}
