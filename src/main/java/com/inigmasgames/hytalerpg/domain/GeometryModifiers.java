package com.inigmasgames.hytalerpg.domain;
import java.util.List;
/** Explicit geometry/impact fields, not a generic scale applied to every numeric property. */
public record GeometryModifiers(boolean impactForce,boolean widening,boolean focusedChannel){
    public GeometryModifiers{if(widening&&focusedChannel)throw new IllegalArgumentException("CONFLICTING_BEAM_WIDTH_MODIFIERS");}
    public static GeometryModifiers from(List<PassiveId> ids){
        return new GeometryModifiers(has(ids,"impact_force"),has(ids,"widening"),has(ids,"focused_channel"));
    }
    private static boolean has(List<PassiveId> ids,String value){return ids.stream().anyMatch(p->p.value().equals(value));}
    public boolean active(){return impactForce||widening||focusedChannel;}
    public double width(double fullWidth){
        if(!Double.isFinite(fullWidth)||fullWidth<=0)throw new IllegalArgumentException("NO_SCALABLE_WIDTH");
        return widening||focusedChannel?Math.max(.10,fullWidth*(widening?1.5:.65)):fullWidth;
    }
}
