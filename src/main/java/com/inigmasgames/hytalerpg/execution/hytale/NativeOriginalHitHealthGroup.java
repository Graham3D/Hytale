package com.inigmasgames.hytalerpg.execution.hytale;

import java.util.*;

/** Post-Apply component collector for one proven native original-hit receipt. */
final class NativeOriginalHitHealthGroup {
    record Snapshot(double actualHealthLoss,double healthAfter,int components){}
    private final Set<Integer> seen=new HashSet<>();
    private double loss,healthAfter=Double.NaN;
    boolean add(int component,double before,double after,boolean cancelled){
        if(component<0||component>32||!Double.isFinite(before)||!Double.isFinite(after)
                ||before<0||after<0)return false;
        if(!seen.add(component))return false;
        loss+=cancelled?0:Math.max(0,before-after);
        healthAfter=after;
        return true;
    }
    Snapshot snapshot(){return new Snapshot(loss,healthAfter,seen.size());}
}
