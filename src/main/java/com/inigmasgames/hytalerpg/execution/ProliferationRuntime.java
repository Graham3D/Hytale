package com.inigmasgames.hytalerpg.execution;
import com.inigmasgames.hytalerpg.execution.area.AreaGeometry;
import com.inigmasgames.hytalerpg.execution.connection.ConnectionShape;
import com.inigmasgames.hytalerpg.execution.math.Vec3;
import java.util.*;

/** Bounded death-to-status routing. Source status authorities retain actual packages and native validation. */
public final class ProliferationRuntime {
    public enum Kind { BURN,POISON,CHILL }
    public record Payload(SkillExecutionContext context,Kind kind,int stacks,int sourceCap,double coefficient,double strength,double remaining){
        public Payload {if(context==null||kind==null||stacks<1||stacks>12||sourceCap<stacks||sourceCap>12||!Double.isFinite(coefficient)||coefficient<=0||!Double.isFinite(strength)||strength<=0||!Double.isFinite(remaining)||remaining<=0||remaining>120)throw new IllegalArgumentException("INVALID_PROLIFERATION_PACKAGE");}
        Payload scaled(SkillExecutionContext child){return new Payload(child,kind,kind==Kind.CHILL?Math.max(1,(int)Math.floor(stacks*.7)):stacks,sourceCap,coefficient*.7,strength*.7,remaining*.7);}
    }
    public record Target(UUID id,AreaGeometry.Bounds bounds,boolean alive,boolean hostile,boolean protectedTarget,boolean lineOfSight){}
    public record Query(List<Target> targets,boolean overflow){public Query{targets=List.copyOf(targets);}}
    public interface Port {
        Query query(SkillExecutionContext source,Vec3 origin,double radius,int budget);
        String apply(Payload child,Target target);
        void trace(SkillExecutionContext context,String verdict,UUID victim);
        default String claimSecondary(SkillExecutionContext context,String token){return context.effects().claim(token,2,true);}
    }
    private record AuraDeath(UUID owner,String skill,UUID dead,Kind kind){}
    private final Map<AuraDeath,Double> auraDeaths=new HashMap<>();
    private final java.util.function.DoubleSupplier clock;
    public ProliferationRuntime(){this(()->System.nanoTime()/1e9);}
    public ProliferationRuntime(java.util.function.DoubleSupplier clock){this.clock=Objects.requireNonNull(clock);}
    public synchronized void cancel(UUID owner){auraDeaths.keySet().removeIf(key->key.owner.equals(owner));}
    private String claimDeath(SkillExecutionContext c,UUID dead,Kind kind){
        if(c.profile().support()==null||!c.profile().support().aura())return c.effects().claimProcContact("proliferation/"+dead+"/"+kind);
        double now=clock.getAsDouble();if(!Double.isFinite(now))return "INVALID_PROLIFERATION_CLOCK";
        auraDeaths.values().removeIf(end->end<=now);var key=new AuraDeath(c.request().actorId(),c.profile().skillId(),dead,kind);
        if(auraDeaths.containsKey(key))return "DUPLICATE_AURA_STATUS_DEATH";
        if(auraDeaths.size()>=4096||auraDeaths.keySet().stream().filter(k->k.owner.equals(key.owner)).count()>=256)return "AURA_STATUS_DEATH_WINDOW_BUDGET";
        auraDeaths.put(key,now+120);return "PASS";
    }
    public synchronized void death(UUID dead,Vec3 origin,List<Payload> sources,Port port){
        if(dead==null||origin==null||sources==null)throw new IllegalArgumentException("INVALID_STATUS_DEATH");
        if(sources.size()>32){for(var p:sources.stream().limit(1).toList())port.trace(p.context,"PROLIFERATION_DEATH_SOURCE_BUDGET",dead);return;}
        var strongest=new TreeMap<String,Payload>();
        for(var payload:sources){var c=payload.context;if(!c.compiledPlan().proliferation()||c.derivedRelease())continue;
            String key=c.request().actorId()+"/"+c.profile().skillId()+"/"+payload.kind;
            strongest.merge(key,payload,(a,b)->a.strength*a.stacks>=b.strength*b.stacks?a:b);
        }
        var queries=new HashMap<String,List<Target>>();
        for(var p:strongest.values()){
            var context=p.context;
            String claim=claimDeath(context,dead,p.kind);
            if(!claim.equals("PASS")){port.trace(context,claim,dead);continue;}
            String key=context.request().actorId()+"/"+context.profile().skillId();
            try{
                var targets=queries.get(key);
                if(targets==null){
                    var query=port.query(context,origin,4,256);
                    if(query.overflow||query.targets.size()>256){port.trace(context,"PROLIFERATION_CANDIDATE_BUDGET",dead);queries.put(key,List.of());continue;}
                    var unique=new TreeMap<String,Target>();
                    for(var t:query.targets)if(t.id!=null&&t.bounds!=null&&!t.id.equals(dead)&&t.alive&&t.hostile&&!t.protectedTarget&&t.lineOfSight&&ConnectionShape.pointDistanceSquared(origin,t.bounds)<=16+1e-9)unique.putIfAbsent(t.id.toString(),t);
                    targets=unique.values().stream().sorted(Comparator.<Target>comparingDouble(t->ConnectionShape.pointDistanceSquared(origin,t.bounds)).thenComparing(t->t.id.toString())).limit(3).toList();queries.put(key,targets);
                }
                for(var target:targets){
                    String token="proliferation/"+dead+"/"+p.kind+"/"+target.id;
                    String admitted=port.claimSecondary(context,token);
                    if(!admitted.equals("PASS")){port.trace(context,admitted,target.id);continue;}
                    var child=context.proliferationCopy(token);
                    try{port.trace(child,port.apply(p.scaled(child),target),target.id);}catch(RuntimeException error){port.trace(child,"PROLIFERATION_NATIVE_REJECTED:"+error.getClass().getSimpleName()+":"+error.getMessage(),target.id);}
                }
            }catch(RuntimeException error){port.trace(context,"PROLIFERATION_QUERY_REJECTED:"+error.getClass().getSimpleName(),dead);}
        }
    }
}
