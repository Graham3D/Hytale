package com.inigmasgames.hytalerpg.execution.strike;

import com.inigmasgames.hytalerpg.domain.SkillSlot;
import java.util.*;

/** Per linked actor/slot Ruthless cadence; a loadout change or owner teardown removes it. */
public final class RuthlessLedger {
    public record Key(UUID actor,SkillSlot slot){public Key{Objects.requireNonNull(actor);Objects.requireNonNull(slot);}}
    private record State(int committedModuloThree,String root){}
    public static final class Commit{
        private final Key key;private final State before,after;
        private Commit(Key key,State before,State after){this.key=key;this.before=before;this.after=after;}
    }
    private final Map<Key,State> states=new HashMap<>();
    public synchronized boolean hasCapacity(Key key){return states.containsKey(key)||states.size()<4096;}
    public synchronized boolean nextEmpowered(Key key){var state=states.get(key);return state!=null&&state.committedModuloThree==2;}
    public synchronized Commit committed(Key key,String root){
        if(root==null||root.isBlank()||root.length()>256)throw new IllegalArgumentException("Invalid Ruthless root");
        if(!hasCapacity(key))throw new IllegalStateException("RUTHLESS_LEDGER_CAPACITY");
        var before=states.get(key);
        if(before!=null&&before.root.equals(root))return new Commit(key,before,before);
        var after=new State(before==null?1:(before.committedModuloThree+1)%3,root);
        states.put(key,after);return new Commit(key,before,after);
    }
    public synchronized void rollback(Commit commit){
        if(commit==null||commit.before==commit.after||states.get(commit.key)!=commit.after)return;
        if(commit.before==null)states.remove(commit.key);else states.put(commit.key,commit.before);
    }
    public synchronized void forget(UUID actor){states.keySet().removeIf(k->k.actor.equals(actor));}
    public synchronized int size(){return states.size();}
}
