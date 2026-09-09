package com.inigmasgames.hytalerpg.execution.movement;

import com.inigmasgames.hytalerpg.execution.area.AreaGeometry;
import com.inigmasgames.hytalerpg.execution.math.Vec3;
import java.util.*;

/** Authoritative full-width, full-height swept body contacts; no presentation or damage authority. */
public final class MovementContacts {
    public record Target(String id,AreaGeometry.Bounds bounds){}
    public record Contact(String id,double fraction){}
    private final Set<String> hit=new HashSet<>();
    public List<Contact> query(Vec3 from,Vec3 to,double width,double height,List<Target> targets) {
        if(!Double.isFinite(width)||width<=0||!Double.isFinite(height)||height<=0)throw new IllegalArgumentException("INVALID_MOVEMENT_HITBOX");
        if(targets.size()>64)throw new IllegalStateException("MOVEMENT_TARGET_BUDGET");
        var contacts=new ArrayList<Contact>();var ids=new HashSet<String>();
        for(var t:targets){if(!ids.add(t.id()))throw new IllegalArgumentException("DUPLICATE_MOVEMENT_TARGET");if(hit.contains(t.id()))continue;
            contact(from,to,width,height,t.bounds()).ifPresent(f->contacts.add(new Contact(t.id(),f)));}
        contacts.sort(Comparator.comparingDouble(Contact::fraction).thenComparing(Contact::id));return List.copyOf(contacts);
    }
    public boolean claim(String id){if(hit.contains(id))return false;if(hit.size()>=256)throw new IllegalStateException("MOVEMENT_HIT_LEDGER_BUDGET");return hit.add(id);}
    public int size(){return hit.size();}
    private static OptionalDouble contact(Vec3 from,Vec3 to,double width,double height,AreaGeometry.Bounds bounds){
        Vec3 delta=to.subtract(from),forward=delta.horizontalNormalized(),side=new Vec3(-forward.z(),0,forward.x());
        double lo=0,hi=1;
        // Continuous SAT of the transverse width segment + vertical height against native axis-aligned bounds.
        // Include its normal (forward), so width never adds unauthorized forward range at the endpoint.
        for(Vec3 axis:List.of(new Vec3(1,0,0),new Vec3(0,0,1),new Vec3(0,1,0),forward)){
            double targetMin=0,targetMax=0;
            for(int i=0;i<3;i++){double a=i==0?axis.x():i==1?axis.y():axis.z(),min=i==0?bounds.min().x():i==1?bounds.min().y():bounds.min().z(),max=i==0?bounds.max().x():i==1?bounds.max().y():bounds.max().z();
                targetMin+=Math.min(a*min,a*max);targetMax+=Math.max(a*min,a*max);}
            double lateral=Math.abs(dot(side,axis))*width/2,vertical=axis.y()*height;
            double origin=dot(from,axis),velocity=dot(delta,axis);
            double min=targetMin-lateral-Math.max(0,vertical)-origin,max=targetMax+lateral-Math.min(0,vertical)-origin;
            if(Math.abs(velocity)<1e-12){if(min>1e-9||max< -1e-9)return OptionalDouble.empty();continue;}
            double a=min/velocity,b=max/velocity;lo=Math.max(lo,Math.min(a,b));hi=Math.min(hi,Math.max(a,b));if(lo>hi+1e-9)return OptionalDouble.empty();
        }
        return lo<=1+1e-9&&hi>=0?OptionalDouble.of(Math.clamp(lo,0,1)):OptionalDouble.empty();
    }
    private static double dot(Vec3 a,Vec3 b){return a.x()*b.x()+a.y()*b.y()+a.z()*b.z();}
}
