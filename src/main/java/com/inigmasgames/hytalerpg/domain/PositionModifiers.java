package com.inigmasgames.hytalerpg.domain;
import java.util.List;
/** Area-component control, independent from native authored displacement. */
public record PositionModifiers(boolean vacuum,boolean repulsion){
    public PositionModifiers{if(vacuum&&repulsion)throw new IllegalArgumentException("CONFLICTING_AREA_POSITION_MODES");}
    public static PositionModifiers from(List<PassiveId> ids){return new PositionModifiers(ids.stream().anyMatch(p->p.value().equals("vacuum")),ids.stream().anyMatch(p->p.value().equals("repulsion")));}
    public boolean active(){return vacuum||repulsion;}
    public double distance(boolean impactForce){return vacuum?2:repulsion?2.5*(impactForce?1.75:1):0;}
}
