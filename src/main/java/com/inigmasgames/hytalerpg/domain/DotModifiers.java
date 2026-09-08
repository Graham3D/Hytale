package com.inigmasgames.hytalerpg.domain;

import com.inigmasgames.hytalerpg.combat.status.PeriodicStatusRuntime.Kind;
import java.util.List;
import java.util.Set;

/** Source-owned status parameters, separate from direct-hit coefficient and crit. */
public record DotModifiers(boolean combustion,boolean virulence,boolean concentratedVenom) {
    public static DotModifiers from(List<PassiveId> order){
        Set<String> ids=order.stream().map(PassiveId::value).collect(java.util.stream.Collectors.toSet());
        return new DotModifiers(ids.contains("combustion"),ids.contains("virulence"),ids.contains("concentrated_venom"));
    }
    public boolean active(){return combustion||virulence||concentratedVenom;}
    public record Application(double coefficientPerSecond,int addedStacks,int sourceCap){}
    public Application application(Kind kind,double coefficientPerSecond){
        if(kind==null||!Double.isFinite(coefficientPerSecond)||coefficientPerSecond<=0)throw new IllegalArgumentException("Invalid DoT payload");
        return switch(kind){
            case BURN->new Application(coefficientPerSecond*(combustion?1.5:1),1,1);
            case POISON->new Application(coefficientPerSecond*(concentratedVenom?1.75:1),virulence?2:1,concentratedVenom?1:3);
        };
    }
}
