package com.inigmasgames.hytalerpg.combat.barrier;

/** Durable missing-shield accounting, not a second Health pool. No wall-clock/offline credit. */
public record ManaguardLedger(double deficit, double lastValidatedCapacity, int allocationPercent,double sharedDeficit) {
    public ManaguardLedger(double deficit,double capacity,int allocation){this(deficit,capacity,allocation,deficit*.5);}
    public static final ManaguardLedger INITIAL=new ManaguardLedger(0,0,50);
    public ManaguardLedger {
        if(!Double.isFinite(deficit)||deficit<0||!Double.isFinite(lastValidatedCapacity)||lastValidatedCapacity<0
                ||!Double.isFinite(sharedDeficit)||sharedDeficit<0||allocationPercent<1||allocationPercent>50)throw new IllegalArgumentException("Invalid Managuard ledger");
    }
    public double current(double capacity){
        validate(capacity); return Math.max(0,capacity-deficit);
    }
    public ManaguardLedger validateCapacity(double reservedMana,double barrierMultiplier){
        validate(reservedMana);validate(barrierMultiplier);double capacity=reservedMana*barrierMultiplier;validate(capacity);
        return new ManaguardLedger(deficit,capacity,allocationPercent,sharedDeficit);
    }
    public ManaguardLedger allocate(int percent){return new ManaguardLedger(deficit,lastValidatedCapacity,percent,sharedDeficit);}
    public Absorption absorb(double filteredDamage,double capacity){
        validate(filteredDamage); validate(capacity);
        double absorbed=Math.min(filteredDamage,current(capacity));
        return new Absorption(new ManaguardLedger(deficit+absorbed,capacity,allocationPercent,Math.max(sharedDeficit,(deficit+absorbed)*.5)),absorbed,filteredDamage-absorbed);
    }
    /** One persistent ally pool. Its ceiling follows the owner's remaining shield; recipients do not own/reset it. */
    public double sharedCurrent(double capacity){return Math.min(current(capacity)*.5,Math.max(0,capacity*.5-sharedDeficit));}
    public Absorption absorbShared(double damage,double capacity){
        validate(damage);validate(capacity);double absorbed=Math.min(damage,sharedCurrent(capacity));
        return new Absorption(new ManaguardLedger(deficit,capacity,allocationPercent,sharedDeficit+absorbed),absorbed,damage-absorbed);
    }
    /** Caller supplies only observed connected OOC seconds after the six-second hostile-damage delay. */
    public ManaguardLedger recharge(double eligibleConnectedSeconds){
        validate(eligibleConnectedSeconds);
        return new ManaguardLedger(Math.max(0,deficit-lastValidatedCapacity*.10*eligibleConnectedSeconds),
                lastValidatedCapacity,allocationPercent,Math.max(0,sharedDeficit-lastValidatedCapacity*.05*eligibleConnectedSeconds));
    }
    private static void validate(double value){
        if(!Double.isFinite(value)||value<0)throw new IllegalArgumentException("Invalid Managuard value");
    }
    public record Absorption(ManaguardLedger ledger,double absorbed,double damageRemaining){}
}
