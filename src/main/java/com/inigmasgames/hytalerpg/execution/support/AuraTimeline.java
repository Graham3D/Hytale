package com.inigmasgames.hytalerpg.execution.support;

/** Paid quarter-second slices, authored pulse clocks, no frame-rate damage or unbounded catch-up. */
public final class AuraTimeline {
    public interface Port { boolean pay(double seconds,int quantum);void pulse(int ordinal,boolean chill); }
    private final SupportProfile profile;
    private final double start,end;
    private double paidUntil,last;
    private int quantum,damageTick=1,chillTick=1;
    private String terminal;
    public AuraTimeline(SupportProfile profile,double start){
        this.profile=profile;this.start=start;last=start;paidUntil=start;
        end=profile.durationSeconds()>0?start+profile.durationSeconds():Double.POSITIVE_INFINITY;
    }
    public String advance(double now,Port port){
        if(terminal!=null)return terminal;
        if(!Double.isFinite(now)||now<last-1e-9)throw new IllegalArgumentException("Invalid Aura clock");
        if(now-last>1.000001)return terminal="AURA_UNOBSERVED_UPKEEP_GAP";
        last=now;
        for(int operations=0;operations<24;operations++){
            double damage=profile.damageInterval()>0?start+damageTick*profile.damageInterval():Double.POSITIVE_INFINITY;
            double chill=profile.chillInterval()>0?start+chillTick*profile.chillInterval():Double.POSITIVE_INFINITY;
            double next=Math.min(paidUntil,Math.min(damage,Math.min(chill,end)));
            if(next>now+1e-9)return "ACTIVE";
            // Pulses at the end of an already-paid slice execute before buying the next slice.
            if(damage<=paidUntil+1e-9&&damage<=end+1e-9&&damage<=now+1e-9){port.pulse(damageTick++,false);continue;}
            if(chill<=paidUntil+1e-9&&chill<=end+1e-9&&chill<=now+1e-9){port.pulse(chillTick++,true);continue;}
            if(end<=paidUntil+1e-9&&end<=now+1e-9)return terminal="AURA_FINITE_EXPIRED";
            double seconds=Math.min(.25,end-paidUntil);
            if(!port.pay(seconds,++quantum))return terminal="AURA_UPKEEP_UNAFFORDABLE";
            paidUntil+=seconds;
        }
        return terminal="AURA_EVENT_BUDGET";
    }
    public double paidUntil(){return paidUntil;}
}
