package com.inigmasgames.hytalerpg.execution.support;

import com.inigmasgames.hytalerpg.combat.barrier.ManaguardLedger;
import com.inigmasgames.hytalerpg.progress.SupportProgress;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/**
 * Owner-thread absorption authority. The existing saved deficit is the crash debit, not an
 * unspent-credit record. Only a successful durable receipt can publish credit. Recovery creates
 * a NEW escrow with zero credit; unused authorization is deliberately forfeited.
 *
 * This object never performs IO, waits, or runs a native callback on a completion thread.
 */
public final class ShieldEscrow {
    private long generation=1, sequence, published;
    private double ownerCredit, sharedCredit, ownerSpent, sharedSpent, ownerDebitFloor, sharedDebitFloor;
    private Pending pending;
    private boolean uncertain;

    public record Debit(long generation,long sequence,SupportProgress persisted,
                        double ownerCapacity,double sharedCapacity,double ownerSpent,double sharedSpent) {}
    private record Pending(Debit debit,CompletableFuture<SupportProgress> receipt) {}

    /** One immutable debit at a time; the caller reserves downstream persistence capacity first. */
    public Debit prepare(SupportProgress actual){
        usable();if(pending!=null)throw new IllegalStateException("SHIELD_ESCROW_AUTHORIZATION_PENDING");
        var g=actual.managuard();double capacity=g.lastValidatedCapacity();
        // All potentially usable capacity is already missing after a crash, independently for
        // owner and ally pools. A smaller validated maximum must not erase an older deficit.
        var booked=new ManaguardLedger(Math.max(ownerDebitFloor,Math.max(g.deficit(),capacity)),capacity,g.allocationPercent(),
                Math.max(sharedDebitFloor,Math.max(g.sharedDeficit(),capacity*.5)));
        return new Debit(generation,Math.addExact(sequence,1),actual.guard(booked),
                g.current(capacity),g.sharedCurrent(capacity),ownerSpent,sharedSpent);
    }
    public void submitted(Debit debit,CompletionStage<SupportProgress> receipt){
        usable();if(pending!=null||debit.generation()!=generation||debit.sequence()!=sequence+1)
            throw new IllegalStateException("STALE_SHIELD_ESCROW_DEBIT");
        sequence=debit.sequence();pending=new Pending(debit,receipt.toCompletableFuture());
        ownerDebitFloor=debit.persisted().managuard().deficit();sharedDebitFloor=debit.persisted().managuard().sharedDeficit();
    }
    /** Returns the durable saved metadata, or null while pending. No new work is submitted here. */
    public SupportProgress poll(){
        usable();if(pending==null||!pending.receipt().isDone())return null;
        var current=pending;pending=null;
        final SupportProgress saved;
        try{saved=current.receipt().getNow(null);}catch(RuntimeException error){uncertain=true;ownerCredit=sharedCredit=0;throw error;}
        var d=current.debit();
        if(saved==null||!saved.managuard().equals(d.persisted().managuard())||saved.revision()<d.persisted().revision()){
            uncertain=true;ownerCredit=sharedCredit=0;throw new IllegalStateException("SHIELD_ESCROW_DEBIT_RECEIPT_MISMATCH");
        }
        if(d.generation()!=generation||d.sequence()<=published)return saved; // metadata only; no revived credit
        published=d.sequence();
        // Replace, do not add. Old authorization spent while IO was pending is subtracted from
        // the captured allowance; it can never be reissued by a late completion.
        ownerCredit=Math.max(0,d.ownerCapacity()-(ownerSpent-d.ownerSpent()));
        sharedCredit=Math.max(0,d.sharedCapacity()-(sharedSpent-d.sharedSpent()));
        return saved;
    }
    public ManaguardLedger.Absorption absorb(ManaguardLedger actual,double damage,double capacity,boolean shared){
        usable();if(!Double.isFinite(damage)||damage<0)throw new IllegalArgumentException("Invalid incoming damage");
        double available=shared?Math.min(sharedCredit,actual.sharedCurrent(capacity)):Math.min(ownerCredit,actual.current(capacity));
        double amount=Math.min(damage,available);
        var result=shared?actual.absorbShared(amount,capacity):actual.absorb(amount,capacity);
        if(shared){sharedCredit-=amount;sharedSpent+=amount;}else{ownerCredit-=amount;ownerSpent+=amount;}
        return new ManaguardLedger.Absorption(result.ledger(),amount,damage-amount);
    }
    public double available(ManaguardLedger actual,double capacity,boolean shared){
        if(uncertain)return 0;
        return shared?Math.min(sharedCredit,actual.sharedCurrent(capacity)):Math.min(ownerCredit,actual.current(capacity));
    }
    /** Revoke BEFORE settling unused credit on clean teardown. Late receipts cannot revive it. */
    public void revoke(){generation=Math.addExact(generation,1);ownerCredit=sharedCredit=0;}
    public boolean pending(){return pending!=null;}
    public boolean uncertain(){return uncertain;}
    private void usable(){if(uncertain)throw new IllegalStateException("SHIELD_ESCROW_PERSISTENCE_UNCERTAIN_RESTART_REQUIRED");}
}
