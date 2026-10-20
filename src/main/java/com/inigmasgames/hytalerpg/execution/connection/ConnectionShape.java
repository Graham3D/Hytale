package com.inigmasgames.hytalerpg.execution.connection;

import com.inigmasgames.hytalerpg.execution.area.AreaGeometry;
import com.inigmasgames.hytalerpg.execution.math.Vec3;
import java.util.*;

/** Oriented full-width prism or radial cylinder against actual target AABBs; particles are never colliders. */
public record ConnectionShape(Kind kind,Vec3 start,Vec3 end,double width,double height,double radius) {
    public enum Kind { LINE,CYLINDER,CAPSULE }
    public ConnectionShape {
        if(kind==null||start==null||end==null||!Double.isFinite(width)||!Double.isFinite(height)||!Double.isFinite(radius)
                ||width<0||height<=0||radius<0||kind==Kind.LINE&&width<=0||kind!=Kind.LINE&&radius<=0)
            throw new IllegalArgumentException("Invalid connection shape");
    }
    public static ConnectionShape line(Vec3 start,Vec3 end,double width,double height){return new ConnectionShape(Kind.LINE,start,end,width,height,0);}
    public static ConnectionShape cylinder(Vec3 center,double radius,double height){return new ConnectionShape(Kind.CYLINDER,center,center,0,height,radius);}
    public static ConnectionShape capsule(Vec3 from,Vec3 to,double radius){return new ConnectionShape(Kind.CAPSULE,from,to,0,radius*2,radius);}
    public Vec3 direction(){return end.subtract(start).normalized();}
    public Vec3 right(){var d=direction();return new Vec3(d.z(),0,-d.x()).lengthSquared()<1e-12?new Vec3(1,0,0):new Vec3(d.z(),0,-d.x()).normalized();}
    public Vec3 up(){return cross(direction(),right()).normalized();}
    public boolean intersects(AreaGeometry.Bounds target) {
        if(kind==Kind.CAPSULE)return segmentDistanceSquared(start,end,target)<=radius*radius+1e-9;
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
    public static double pointDistanceSquared(Vec3 point,AreaGeometry.Bounds bounds){
        double x=Math.max(bounds.min().x()-point.x(),Math.max(0,point.x()-bounds.max().x()));
        double y=Math.max(bounds.min().y()-point.y(),Math.max(0,point.y()-bounds.max().y()));
        double z=Math.max(bounds.min().z()-point.z(),Math.max(0,point.z()-bounds.max().z()));return x*x+y*y+z*z;
    }
    /** Exact piecewise-quadratic minimum of segment-to-AABB squared distance. */
    public static double segmentDistanceSquared(Vec3 start,Vec3 end,AreaGeometry.Bounds bounds){
        double[] a={start.x(),start.y(),start.z()},d={end.x()-start.x(),end.y()-start.y(),end.z()-start.z()},
                lo={bounds.min().x(),bounds.min().y(),bounds.min().z()},hi={bounds.max().x(),bounds.max().y(),bounds.max().z()};
        var cuts=new java.util.TreeSet<Double>();cuts.add(0d);cuts.add(1d);
        for(int i=0;i<3;i++)if(Math.abs(d[i])>1e-12)for(double bound:new double[]{lo[i],hi[i]}){double t=(bound-a[i])/d[i];if(t>0&&t<1)cuts.add(t);}
        var times=new ArrayList<>(cuts);double best=Double.POSITIVE_INFINITY;
        for(int n=1;n<times.size();n++){
            double left=times.get(n-1),right=times.get(n),mid=(left+right)/2,b=0,c=0;
            for(int i=0;i<3;i++){double at=a[i]+d[i]*mid;if(at<lo[i]||at>hi[i]){double offset=a[i]-(at<lo[i]?lo[i]:hi[i]);b+=offset*d[i];c+=d[i]*d[i];}}
            double t=c<=1e-20?left:Math.clamp(-b/c,left,right);
            best=Math.min(best,pointDistanceSquared(start.add(end.subtract(start).multiply(t)),bounds));
        }return best;
    }
    public static Vec3 cross(Vec3 a,Vec3 b){return new Vec3(a.y()*b.z()-a.z()*b.y(),a.z()*b.x()-a.x()*b.z(),a.x()*b.y()-a.y()*b.x());}
}
