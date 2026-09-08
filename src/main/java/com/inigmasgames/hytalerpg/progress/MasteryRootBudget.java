package com.inigmasgames.hytalerpg.progress;

import java.util.function.LongConsumer;

/** Shared by every child of one committed root; no entity handles or growing event set. */
public final class MasteryRootBudget {
    private long ordinal,lastAwardNanos;
    private boolean awarded,uncertain;
    private String primaryInstance;
    private boolean sustained;
    public synchronized void bindPrimary(String instance,boolean sustained){
        if(primaryInstance==null){primaryInstance=java.util.Objects.requireNonNull(instance);this.sustained=sustained;}
    }
    public synchronized String primaryInstance(){if(primaryInstance==null)throw new IllegalStateException("UNBOUND_MASTERY_ROOT");return primaryInstance;}
    public synchronized boolean sustained(){return sustained;}
    public synchronized String award(boolean manual,boolean meaningful,boolean eligible,boolean sustained,long now,LongConsumer durableAward){
        if(uncertain)return "MASTERY_PERSISTENCE_UNCERTAIN";
        if(!manual)return "NOT_MANUAL_ROOT";
        if(!meaningful)return "NO_MEANINGFUL_RESULT";
        if(!eligible)return "ENCOUNTER_INELIGIBLE";
        if(awarded&&(!sustained||now-lastAwardNanos<5_000_000_000L))return "ROOT_MASTERY_DEDUP";
        try{durableAward.accept(ordinal);}catch(RuntimeException failure){uncertain=true;throw failure;}
        ordinal=Math.addExact(ordinal,1);lastAwardNanos=now;awarded=true;return "AWARDED_OR_DURABLY_DEDUPLICATED";
    }
}
