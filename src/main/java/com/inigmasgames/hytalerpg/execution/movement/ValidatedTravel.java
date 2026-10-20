package com.inigmasgames.hytalerpg.execution.movement;

import com.inigmasgames.hytalerpg.execution.SkillExecutionContext;
import com.inigmasgames.hytalerpg.execution.math.Vec3;

/** One movement-owned ledger. Only post-move authoritative positions within a collision-approved
 * segment count. An external discontinuity invalidates the bonus, not the existing skill mechanics. */
public final class ValidatedTravel {
    private static final double EPSILON=1e-4;
    private Vec3 last;
    private double meters;
    private boolean valid=true;
    public ValidatedTravel(Vec3 origin){last=java.util.Objects.requireNonNull(origin);}
    public void observe(Vec3 before,Vec3 permittedEnd,Vec3 after,double elapsedSeconds){
        if(!valid)return;
        if(!Double.isFinite(elapsedSeconds)||elapsedSeconds<0||before.distanceSquared(last)>EPSILON*EPSILON){valid=false;return;}
        Vec3 permitted=permittedEnd.subtract(before),actual=after.subtract(before);
        if(elapsedSeconds==0){if(actual.length()>EPSILON)valid=false;return;}
        double length2=permitted.lengthSquared();
        double fraction=length2<1e-12?0:(actual.x()*permitted.x()+actual.y()*permitted.y()+actual.z()*permitted.z())/length2;
        if(fraction<0||fraction>1+EPSILON||actual.subtract(permitted.multiply(fraction)).length()>EPSILON
                ||length2<1e-12&&actual.length()>EPSILON){valid=false;return;}
        // Count the accepted path (including leap height), not requested distance or origin->end.
        meters=Math.min(10,meters+Math.min(actual.length(),permitted.length()));last=after;
    }
    public boolean valid(){return valid;}
    public double meters(){return valid?meters:0;}
    public double increased(SkillExecutionContext context){
        return context.compiledPlan().foundationModifiers().momentum()&&context.profile().movement()!=null&&context.profile().strike()!=null?.05*meters():0;
    }
    public SkillExecutionContext impact(SkillExecutionContext context){
        double bonus=increased(context);
        return bonus==0?context:context.withSnapshot(context.snapshot().withModifiers(context.snapshot().modifiers().withIncreased(bonus)));
    }
}
