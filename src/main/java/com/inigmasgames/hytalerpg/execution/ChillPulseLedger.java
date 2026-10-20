package com.inigmasgames.hytalerpg.execution;

import java.util.HashMap;
import java.util.Map;

/** Four fifths of an integer Chill payload; retain only fractional credit, never round every pulse up. */
public final class ChillPulseLedger {
    private record Credit(int ordinal,int fifths){}
    private final Map<String,Credit> credits=new HashMap<>();
    public int grant(String target,int ordinal,int authoredStacks){
        if(target==null||target.isBlank()||ordinal<0||authoredStacks<1||authoredStacks>5)throw new IllegalArgumentException("Invalid Chill pulse");
        var prior=credits.get(target);
        if(prior!=null&&ordinal<=prior.ordinal())return 0;
        if(prior==null&&credits.size()>=256)throw new IllegalStateException("CHILL_PULSE_TARGET_BUDGET");
        int fifths=(prior==null?0:prior.fifths())+4*authoredStacks;
        credits.put(target,new Credit(ordinal,fifths%5));return fifths/5;
    }
    public int size(){return credits.size();}
}
