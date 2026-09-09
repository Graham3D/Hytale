package com.inigmasgames.hytalerpg.execution.strike;

import java.util.*;

/** Three first-successful native roots in a rolling four seconds. Never counts RPG/derived hits. */
public final class FinisherLedger {
    private static final class State { final ArrayDeque<Double> hits=new ArrayDeque<>(3);boolean token;double last=Double.NEGATIVE_INFINITY; }
    private final Map<UUID,State> actors=new HashMap<>();
    public synchronized void observedRoot(UUID actor,double now){
        if(actor==null||!Double.isFinite(now))throw new IllegalArgumentException("Invalid native hit witness");
        var state=actors.get(actor);
        if(state==null){if(actors.size()>=1024)throw new IllegalStateException("FINISHER_OWNER_CAPACITY");state=new State();actors.put(actor,state);}
        if(now<state.last)throw new IllegalArgumentException("Native hit clock regressed");state.last=now;
        expire(state,now);if(state.token)return;
        state.hits.addLast(now);if(state.hits.size()==3){state.token=true;state.hits.clear();}
    }
    public synchronized boolean consume(UUID actor){
        var state=actors.get(actor);if(state==null||!state.token)return false;
        state.token=false;state.hits.clear();return true;
    }
    public synchronized int pips(UUID actor,double now){
        if(!Double.isFinite(now))throw new IllegalArgumentException("Invalid combo clock");
        var state=actors.get(actor);if(state==null)return 0;expire(state,now);return state.token?3:state.hits.size();
    }
    public synchronized void forget(UUID actor){actors.remove(actor);}
    private static void expire(State state,double now){while(!state.hits.isEmpty()&&now-state.hits.getFirst()>4)state.hits.removeFirst();}
}
