package com.inigmasgames.hytalerpg.execution.connection;

import com.inigmasgames.hytalerpg.execution.area.AreaGeometry;
import com.inigmasgames.hytalerpg.execution.math.Vec3;
import java.util.*;

/** Oriented full-width prism or radial cylinder against actual target AABBs; particles are never colliders. */
public record ConnectionShape(Kind kind,Vec3 start,Vec3 end,double width,double height,double radius) {
    public enum Kind { LINE,CYLINDER }
    public ConnectionShape {
        if(kind==null||start==null||end==null||!Double.isFinite(width)||!Double.isFinite(height)||!Double.isFinite(radius)
                ||width<0||height<=0||radius<0||kind==Kind.LINE&&width<=0||kind==Kind.CYLINDER&&radius<=0)
            throw new IllegalArgumentException("Invalid connection shape");
    }
    public static ConnectionShape line(Vec3 start,Vec3 end,double width,double height){return new ConnectionShape(Kind.LINE,start,end,width,height,0);}
    public static ConnectionShape cylinder(Vec3 center,double radius,double height){return new ConnectionShape(Kind.CYLINDER,center,center,0,height,radius);}
    public Vec3 direction(){return end.subtract(start).normalized();}
    public Vec3 right(){var d=direction();return new Vec3(d.z(),0,-d.x()).lengthSquared()<1e-12?new Vec3(1,0,0):new Vec3(d.z(),0,-d.x()).normalized();}
    public Vec3 up(){return cross(direction(),right()).normalized();}
    public boolean intersects(AreaGeometry.Bounds target) {
        if(kind==Kind.CYLINDER)return new AreaGeometry(AreaGeometry.Kind.DISC,start.add(new Vec3(0,-height/2,0)),Vec3.FORWARD,radius,360,0,0,height).intersects(target);
        var axes=List.of(direction(),right(),up());var world=List.of(new Vec3(1,0,0),new Vec3(0,1,0),Vec3.FORWARD);
        var tests=new ArrayList<Vec3>();tests.addAll(axes);tests.addAll(world);for(var a:axes)for(var b:world)tests.add(cross(a,b));
        Vec3 center=start.add(end).multiply(.5),delta=target.centre().subtract(center),half=target.max().subtract(target.min()).multiply(.5);
        double[] extent={end.subtract(start).length()/2,width/2,height/2};
        for(var axis:tests) {
            if(axis.lengthSquared()<1e-20)continue;
            double ours=0;for(int i=0;i<3;i++)ours+=extent[i]*Math.abs(dot(axis,axes.get(i)));
            double theirs=half.x()*Math.abs(axis.x())+half.y()*Math.abs(axis.y())+half.z()*Math.abs(axis.z());
            if(Math.abs(dot(delta,axis))>ours+theirs+1e-9)return false;
        }
        return true;
    }
    public double entryDistance(AreaGeometry.Bounds target) {
        var d=direction();var half=target.max().subtract(target.min()).multiply(.5);
        return Math.max(0,dot(target.centre().subtract(start),d)-(half.x()*Math.abs(d.x())+half.y()*Math.abs(d.y())+half.z()*Math.abs(d.z())));
    }
    public static double dot(Vec3 a,Vec3 b){return a.x()*b.x()+a.y()*b.y()+a.z()*b.z();}
    public static Vec3 cross(Vec3 a,Vec3 b){return new Vec3(a.y()*b.z()-a.z()*b.y(),a.z()*b.x()-a.x()*b.z(),a.x()*b.y()-a.y()*b.x());}
}
