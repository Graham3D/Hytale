package com.inigmasgames.hytalerpg.execution.area;

import java.util.*;

/** One attempted position write/victim/root/second; bounded rolling state suitable for paid persistent roots. */
public final class RootDisplacementLedger {
    private final Map<String,Double> last=new HashMap<>();private double latest=Double.NEGATIVE_INFINITY;
    public synchronized String claim(String victim,double now){
        if(victim==null||victim.isBlank()||victim.length()>256||!Double.isFinite(now))return "INVALID_DISPLACEMENT_ID_OR_CLOCK";
        if(now<latest)return "DISPLACEMENT_CLOCK_REVERSED";
        latest=now;last.entrySet().removeIf(entry->now-entry.getValue()>=1);
        if(last.containsKey(victim))return "ROOT_TARGET_DISPLACEMENT_ICD";
        if(last.size()>=256)return "ROOT_DISPLACEMENT_TARGET_BUDGET";
        last.put(victim,now);return "PASS";
    }
    public synchronized int size(){return last.size();}
}
