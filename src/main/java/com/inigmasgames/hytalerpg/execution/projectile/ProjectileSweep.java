package com.inigmasgames.hytalerpg.execution.projectile;

import com.inigmasgames.hytalerpg.execution.area.AreaGeometry;
import com.inigmasgames.hytalerpg.execution.math.Vec3;
import java.util.OptionalDouble;

/** Segment versus actual translated collision boxes. A contact is geometry, never a damage outcome. */
public final class ProjectileSweep {
    private ProjectileSweep() { }
    public static OptionalDouble contact(Vec3 from,Vec3 to,AreaGeometry.Bounds projectileLocal,AreaGeometry.Bounds target) {
        double[] a={from.x(),from.y(),from.z()},b={to.x(),to.y(),to.z()};
        double[] minimum={target.min().x()-projectileLocal.max().x(),target.min().y()-projectileLocal.max().y(),target.min().z()-projectileLocal.max().z()};
        double[] maximum={target.max().x()-projectileLocal.min().x(),target.max().y()-projectileLocal.min().y(),target.max().z()-projectileLocal.min().z()};
        double enter=0,exit=1;
        for(int i=0;i<3;i++) {
            double delta=b[i]-a[i];
            if(Math.abs(delta)<1e-12) {if(a[i]<minimum[i]||a[i]>maximum[i])return OptionalDouble.empty();continue;}
            double first=(minimum[i]-a[i])/delta,last=(maximum[i]-a[i])/delta;
            if(first>last){double temp=first;first=last;last=temp;}
            enter=Math.max(enter,first);exit=Math.min(exit,last);if(enter>exit)return OptionalDouble.empty();
        }
        return OptionalDouble.of(enter);
    }
    public static OptionalDouble catchFraction(Vec3 from,Vec3 to,Vec3 caster) {
        Vec3 step=to.subtract(from),offset=from.subtract(caster);
        double a=step.lengthSquared(),c=offset.lengthSquared()-.25;
        if(c<=0)return OptionalDouble.of(0);
        if(a<1e-12)return OptionalDouble.empty();
        double b=2*(step.x()*offset.x()+step.y()*offset.y()+step.z()*offset.z()),d=b*b-4*a*c;
        if(d<0)return OptionalDouble.empty();
        double first=(-b-Math.sqrt(d))/(2*a);
        return first>=0&&first<=1?OptionalDouble.of(first):OptionalDouble.empty();
    }
    public static boolean caught(Vec3 from,Vec3 to,Vec3 caster) {return catchFraction(from,to,caster).isPresent();}
    public static Vec3 limit(Vec3 from,Vec3 to,double distance) {
        if(!Double.isFinite(distance)||distance<0)throw new IllegalArgumentException("Invalid remaining path");
        Vec3 step=to.subtract(from);
        return step.length()<=distance?to:from.add(step.normalized().multiply(distance));
    }
}
