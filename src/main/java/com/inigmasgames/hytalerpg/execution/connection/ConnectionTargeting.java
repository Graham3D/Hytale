package com.inigmasgames.hytalerpg.execution.connection;

import com.inigmasgames.hytalerpg.execution.math.Vec3;
import java.util.*;

/** Conservative server-aim selection: no fabricated client target or invisible aim-assist cone. */
public final class ConnectionTargeting {
    private ConnectionTargeting(){ }
    public record Selection(ConnectionWorldPort.Target target,String verdict){ }
    public static Selection select(ConnectionProfile profile,ConnectionWorldPort port){
        var frame=port.frame();Vec3 origin=frame.feet().add(new Vec3(0,profile.originHeight(),0));
        var end=port.unobstructedEndpoint(origin,origin.add(frame.aim().normalized().multiply(profile.range())));
        var line=ConnectionShape.line(origin,end,profile.width(),profile.height());var query=port.query(line,64);
        if(query.overflow()||query.targets().size()>64)return new Selection(null,"CANDIDATE_BUDGET_REJECTED");
        var target=query.targets().stream().filter(t->line.intersects(t.bounds())&&ConnectionShape.pointDistanceSquared(origin,t.bounds())<=profile.range()*profile.range()+1e-9&&port.lineOfSight(origin,t))
                .sorted(Comparator.comparingDouble((ConnectionWorldPort.Target t)->line.entryDistance(t.bounds())).thenComparing(ConnectionWorldPort.Target::id)).findFirst();
        return new Selection(target.orElse(null),target.isPresent()?"PASS":"NO_VALID_AIMED_TARGET");
    }
}
