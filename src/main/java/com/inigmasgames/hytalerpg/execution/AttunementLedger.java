package com.inigmasgames.hytalerpg.execution;

import com.inigmasgames.hytalerpg.domain.SkillSlot;
import java.util.*;

/** Ephemeral successful-manual-commit stacks. No timers, native stats or saved progression. */
public final class AttunementLedger {
    public record Key(UUID actor, SkillSlot slot) { public Key { Objects.requireNonNull(actor); Objects.requireNonNull(slot); } }
    private record State(int stacks, double lastCommit, String root) { }
    public static final class Commit {
        private final Key key; private final State previous,applied;
        private Commit(Key key,State previous,State applied){this.key=key;this.previous=previous;this.applied=applied;}
    }
    private final Map<Key,State> states = new HashMap<>();
    private void prune(double now) {
        if(!Double.isFinite(now)||now<0)throw new IllegalArgumentException("Invalid Attunement time");
        states.values().removeIf(s->now-s.lastCommit>=4 || now<s.lastCommit);
    }
    public synchronized int stacks(Key key,double now) { prune(now);var s=states.get(key);return s==null?0:s.stacks; }
    public synchronized boolean hasCapacity(Key key,double now) { prune(now);return states.containsKey(key)||states.size()<4096; }
    public synchronized Commit committed(Key key,String root,double now) {
        if(root==null||root.isBlank()||root.length()>256)throw new IllegalArgumentException("Invalid manual root");
        prune(now);var prior=states.get(key);
        if(prior!=null&&prior.root.equals(root))return new Commit(key,prior,prior);
        if(!hasCapacity(key,now))throw new IllegalStateException("ATTUNEMENT_LEDGER_CAPACITY");
        var applied=new State(Math.min(5,prior==null?1:prior.stacks+1),now,root);
        states.put(key,applied);return new Commit(key,prior,applied);
    }
    /** Transaction rollback only; never erase a later commit or revive state cleared by a loadout edit. */
    public synchronized void rollback(Commit commit) {
        if(commit==null||commit.previous==commit.applied||states.get(commit.key)!=commit.applied)return;
        if(commit.previous==null)states.remove(commit.key);else states.put(commit.key,commit.previous);
    }
    public synchronized void forget(UUID actor) { states.keySet().removeIf(k->k.actor.equals(actor)); }
    public synchronized int size(double now) { prune(now);return states.size(); }
}
