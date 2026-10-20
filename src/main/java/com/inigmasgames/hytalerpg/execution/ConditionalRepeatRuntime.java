package com.inigmasgames.hytalerpg.execution;

import com.inigmasgames.hytalerpg.execution.math.Vec3;
import java.util.*;

/** Decisions over authoritative native receipts; execution remains in the shared world-tick release queue. */
public final class ConditionalRepeatRuntime {
    private record Key(UUID actor,String skill,String kind){}
    private final Map<Key,Double> readyAt=new HashMap<>();
    public record Hit(UUID victim,Vec3 position,boolean rootComponent,boolean direct,boolean canProc,boolean critical,
                      boolean hostile,boolean protectedTarget,boolean cancelled,double before,double after,double minimum){
        public Hit{if(victim==null||position==null||!Double.isFinite(before)||!Double.isFinite(after)||!Double.isFinite(minimum))throw new IllegalArgumentException("INVALID_NATIVE_REPEAT_RECEIPT");}
    }
    public interface Port{
        CommittedTarget killTarget(SkillExecutionContext source,Hit hit);
        String enqueue(SkillExecutionContext child,double due);
    }
    public synchronized String observed(SkillExecutionContext c,Hit h,double now,Port port){
        if(!Double.isFinite(now))throw new IllegalArgumentException("INVALID_REPEAT_CLOCK");
        String kind=c.compiledPlan().conditionalRepeat();
        if(kind.isEmpty()||c.derivedRelease()||c.request().origin()!=SkillExecutionRequest.Origin.MANUAL||!h.rootComponent())return "NOT_ROOT_REPEAT_SOURCE";
        if(!ProfileComponentPolicy.conditionalRepeat(c.profile(),kind.equals("critical_trigger")))return "PRIMARY_NOT_REPEATABLE";
        if(h.cancelled()||!h.hostile()||h.protectedTarget()||h.before()<=h.minimum()||h.after()>=h.before())return "NO_HOSTILE_NATIVE_HEALTH_LOSS";
        boolean critical=kind.equals("critical_trigger");
        if(critical?!(h.direct()&&h.canProc()&&h.critical()):h.after()>h.minimum())return "CONDITION_NOT_MET";
        readyAt.values().removeIf(t->t<=now);
        var key=new Key(c.request().actorId(),c.profile().skillId(),kind);
        if(readyAt.containsKey(key))return "CONDITIONAL_REPEAT_ICD";
        if(readyAt.size()>=4096||readyAt.keySet().stream().filter(k->k.actor.equals(key.actor)).count()>=16)return "CONDITIONAL_REPEAT_LEDGER_CAPACITY";
        if(!c.effects().once(kind))return "ROOT_REPEAT_ALREADY_CLAIMED";
        // One opportunity per root, including no-target/failed native release: never retry a paid child blindly.
        CommittedTarget solution=critical?c.target():port.killTarget(c,h);
        if(solution==null)return "NO_VALID_REPEAT_TARGET";
        var child=c.conditionalCopy(solution);
        int count=child.profile().projectile()!=null&&!child.compiledPlan().orbit()?child.compiledPlan().projectileModifiers().batchSize():1;
        for(int i=0;i<count;i++){String budget=c.effects().claim(child.skillInstanceId()+"/primary-"+i,2,true);if(!budget.equals("PASS"))return budget;}
        String queued=port.enqueue(child,now+(critical?.20:0));
        if(!queued.equals("PASS"))return queued;
        readyAt.put(key,now+(critical?1.5:1));return "CONDITIONAL_REPEAT_SCHEDULED";
    }
    public synchronized void forget(UUID actor){readyAt.keySet().removeIf(k->k.actor.equals(actor));}
    public synchronized int size(){return readyAt.size();}
}
