package com.inigmasgames.hytalerpg.combat.resource;

import java.util.UUID;

/** One bounded scalar ledger per committed root; descendants share the object, not copies of its cap. */
public final class RootLeechBudget {
    private final UUID actor; private final String root;
    private ResourceType resource=ResourceType.NONE;
    private boolean initialized,disabled;
    private double cap,restored;
    public RootLeechBudget(UUID actor,String root){if(actor==null||root==null||root.isBlank())throw new IllegalArgumentException("Leech root identity required");this.actor=actor;this.root=root;}
    public UUID actor(){return actor;}
    public boolean owns(UUID actor,String root){return this.actor.equals(actor)&&this.root.equals(root);}
    public synchronized void initialize(ResourceType type,double spendableMaximum){
        if(initialized)throw new IllegalStateException("Leech budget cannot reset within a root");
        if((type!=ResourceType.MANA&&type!=ResourceType.STAMINA)||!Double.isFinite(spendableMaximum)||spendableMaximum<0)
            throw new IllegalArgumentException("Leech requires a finite spendable Mana/Stamina maximum");
        resource=type;cap=.08*spendableMaximum;initialized=true;
    }
    public synchronized ResourceType resource(){return resource;}
    public synchronized double restored(){return restored;}
    public synchronized double cap(){return cap;}
    public record Recovery(String gate,double healthLost,double requested,double restored,double totalRestored,double rootCap) { }
    /** A receipt is constructed after one completed native damage dispatch, never from a client claim. */
    public static final class HitReceipt {
        private final double before,after; private final boolean cancelled,hostile,noLeech;
        private boolean claimed;
        public HitReceipt(double before,double after,boolean cancelled,boolean hostile,boolean noLeech){this.before=before;this.after=after;this.cancelled=cancelled;this.hostile=hostile;this.noLeech=noLeech;}
        private synchronized double claim(){
            if(claimed)return -1;claimed=true;
            if(cancelled||!hostile||noLeech||!Double.isFinite(before)||!Double.isFinite(after)||before<=0)return 0;
            return Math.min(before,Math.max(0,before-after)); // Negative post-health cannot grant overkill credit.
        }
    }
    synchronized Recovery recover(HitReceipt receipt,double spendableMaximum,NativeResourcePort port){
        double lost=receipt.claim();
        if(lost<0)return result("DUPLICATE_RECEIPT",0,0,0);
        if(!initialized||disabled)return result("UNAVAILABLE_BUDGET",lost,0,0);
        if(lost==0)return result("NO_ELIGIBLE_HEALTH_LOSS",0,0,0);
        double current=port.current(resource);
        if(!Double.isFinite(current)||current<0||!Double.isFinite(spendableMaximum)||spendableMaximum<0)
            return result("INVALID_NATIVE_RESOURCE",lost,0,0);
        // A capacity increase cannot expand this root's original cap; a decrease constrains future returns.
        double available=Math.max(0,Math.min(cap,.08*spendableMaximum)-restored);
        double requested=Math.min(lost*.03,Math.min(available,Math.max(0,spendableMaximum-current)));
        if(requested<=0)return result(available==0?"ROOT_CAP_REACHED":"RESOURCE_FULL",lost,0,0);
        // A failed/uncertain native write is never retried or refunded into the budget.
        restored+=requested;
        try{
            double actual=port.restoreResourceAtMost(resource,requested,spendableMaximum);
            if(!Double.isFinite(actual)||actual<0||actual>requested+1e-8){disabled=true;return result("NATIVE_CREDIT_UNCERTAIN",lost,requested,0);}
            restored-=requested-actual;
            return result(actual>0?"RESTORED":"BELOW_NATIVE_PRECISION",lost,requested,actual);
        }catch(RuntimeException error){disabled=true;return result("NATIVE_CREDIT_FAILED_NO_RETRY",lost,requested,0);}
    }
    private Recovery result(String gate,double lost,double requested,double actual){return new Recovery(gate,lost,requested,actual,restored,cap);}
    synchronized Recovery unavailable(HitReceipt receipt){double lost=receipt.claim();disabled=true;return result("NATIVE_RESOURCE_READ_FAILED_NO_RETRY",Math.max(0,lost),0,0);}
}
