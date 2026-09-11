package com.inigmasgames.hytalerpg.execution.connection;

import com.inigmasgames.hytalerpg.domain.PassiveId;
import com.inigmasgames.hytalerpg.execution.math.Vec3;
import java.util.*;

/** One ephemeral, bounded visited ledger per root pulse. No persistent child skill instances. */
public final class TetherContinuations {
    private TetherContinuations() {}
    public record Modifiers(boolean arc,boolean fork,boolean chain) {
        public static Modifiers from(List<PassiveId> ids) {
            var names=ids.stream().map(PassiveId::value).toList();
            return new Modifiers(names.contains("arc"),names.contains("fork"),names.contains("chain"));
        }
    }
    public record Payload(ConnectionWorldPort.Target source,ConnectionWorldPort.Target recipient,
                          double coefficient,String continuation,boolean noTetherFanout) {
        public Payload {if(!noTetherFanout)throw new IllegalArgumentException("Tether children must be terminal");}
    }
    public interface Targets {
        ConnectionWorldPort.Query nearby(Vec3 point,double radius,int cap);
        boolean eligible(ConnectionWorldPort.Target target);
        boolean lineOfSight(Vec3 point,ConnectionWorldPort.Target target);
    }
    public static List<Payload> select(ConnectionWorldPort.Target primary,Modifiers modifiers,Targets targets) {
        Objects.requireNonNull(primary);var visited=new HashSet<String>();visited.add(primary.id());
        var result=new ArrayList<Payload>(5);
        if(modifiers.arc())add(primary,.60,"ARC",visited,result,targets);
        if(modifiers.fork())for(int i=0;i<2;i++)add(primary,.45,"FORK",visited,result,targets);
        var previous=primary;
        if(modifiers.chain())for(double factor:new double[]{.70,.49}) {
            var next=add(previous,factor,"CHAIN",visited,result,targets);if(next==null)break;previous=next;
        }
        return List.copyOf(result);
    }
    private static ConnectionWorldPort.Target add(ConnectionWorldPort.Target from,double factor,String kind,Set<String> visited,
                                                   List<Payload> results,Targets targets) {
        var origin=from.bounds().centre();var query=targets.nearby(origin,8,64);
        if(query.overflow()||query.targets().size()>64)throw new IllegalStateException("TETHER_CONTINUATION_CANDIDATE_BUDGET");
        var next=query.targets().stream().filter(t->!visited.contains(t.id())&&targets.eligible(t)
                &&t.bounds().centre().distanceSquared(origin)<=64+1e-9&&targets.lineOfSight(origin,t))
                .sorted(Comparator.comparingDouble((ConnectionWorldPort.Target t)->t.bounds().centre().distanceSquared(origin))
                        .thenComparing(ConnectionWorldPort.Target::id)).findFirst().orElse(null);
        if(next!=null){visited.add(next.id());results.add(new Payload(from,next,factor,kind,true));}
        return next;
    }
}
