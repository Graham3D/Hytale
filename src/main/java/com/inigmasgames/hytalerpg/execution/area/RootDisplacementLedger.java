package com.inigmasgames.hytalerpg.execution.area;

import com.inigmasgames.hytalerpg.execution.TimedOpportunityLedger;

/** One attempted position write/victim/root/second; bounded rolling state suitable for paid persistent roots. */
public final class RootDisplacementLedger {
    private final TimedOpportunityLedger<String> locks=new TimedOpportunityLedger<>(256);
    public synchronized String claim(String victim,double now){
        if(victim==null||victim.isBlank()||victim.length()>256||!Double.isFinite(now))return "INVALID_DISPLACEMENT_ID_OR_CLOCK";
        return switch(locks.claim(victim,now,1)){
            case CLAIMED->"PASS";case CLOCK_REVERSED->"DISPLACEMENT_CLOCK_REVERSED";
            case LOCKED->"ROOT_TARGET_DISPLACEMENT_ICD";case CAPACITY->"ROOT_DISPLACEMENT_TARGET_BUDGET";
        };
    }
    public synchronized int size(){return locks.size();}
}
