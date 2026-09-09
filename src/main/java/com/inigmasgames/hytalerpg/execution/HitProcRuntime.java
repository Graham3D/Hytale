package com.inigmasgames.hytalerpg.execution;

import java.util.*;
import java.util.function.DoubleSupplier;

/** Decisions from completed native damage receipts. No Health writer, timer, or second combat engine. */
public final class HitProcRuntime {
    public record Hit(UUID victim,String contact,String element,boolean direct,boolean canProc,boolean hostile,
                      boolean protectedTarget,boolean boss,boolean player,boolean frozenBefore,boolean cancelled,
                      double healthBefore,double healthAfter,double healthMinimum,double preMitigation,double procCoefficient,double increasedUnit){
        public Hit(UUID victim,String contact,String element,boolean direct,boolean canProc,boolean hostile,
                   boolean protectedTarget,boolean boss,boolean player,boolean frozenBefore,boolean cancelled,
                   double healthBefore,double healthAfter,double healthMinimum,double preMitigation,double procCoefficient){
            this(victim,contact,element,direct,canProc,hostile,protectedTarget,boss,player,frozenBefore,cancelled,
                    healthBefore,healthAfter,healthMinimum,preMitigation,procCoefficient,Double.NaN);
        }
        public boolean valid(){return victim!=null&&contact!=null&&!contact.isBlank()&&contact.length()<=480&&element!=null
                &&Double.isFinite(healthBefore)&&Double.isFinite(healthAfter)&&Double.isFinite(healthMinimum)
                &&Double.isFinite(preMitigation)&&preMitigation>0&&Double.isFinite(procCoefficient)&&procCoefficient>0&&procCoefficient<=1;}
    }
    public interface Port {
        String bleed(SkillExecutionContext child,Hit hit,double dps,double seconds);
        String fear(SkillExecutionContext child,Hit hit,double seconds);
        String shatter(SkillExecutionContext child,Hit hit,double radius,double amount);
        void trace(SkillExecutionContext context,String kind,String verdict,double value);
    }
    private record FearKey(UUID actor,UUID target){}
    private final Map<FearKey,Double> fearReady=new HashMap<>();
    private final DoubleSupplier random;
    public HitProcRuntime(){this(()->java.util.concurrent.ThreadLocalRandom.current().nextDouble());}
    public HitProcRuntime(DoubleSupplier random){this.random=Objects.requireNonNull(random);}
    public synchronized void observed(SkillExecutionContext c,Hit hit,double now,Port port){
        var mods=c.compiledPlan().hitProcs();if(!mods.active())return;
        if(!hit.valid()||!Double.isFinite(now)){port.trace(c,"HIT_PROC","INVALID_NATIVE_RECEIPT",0);return;}
        if(c.derivedRelease()||!hit.hostile||hit.protectedTarget||hit.player||hit.cancelled
                ||hit.healthBefore<=hit.healthMinimum||hit.healthAfter>=hit.healthBefore)return;
        boolean direct=hit.direct&&hit.canProc;
        if(!direct&&!(mods.shatter()&&hit.element.equals("COLD")&&hit.frozenBefore&&hit.healthAfter<=hit.healthMinimum))return;
        String contact=c.effects().claimProcContact(hit.contact+"/"+hit.victim);
        if(!contact.equals("PASS")){port.trace(c,"HIT_PROC",contact,0);return;}
        fearReady.values().removeIf(at->at<=now);
        boolean alive=hit.healthAfter>hit.healthMinimum;
        if(mods.hemorrhage()&&direct&&alive&&hit.element.equals("PHYSICAL")&&roll(c,"hemorrhage",.25*hit.procCoefficient,port))
            dispatch(c,"hemorrhage",hit.contact+"/"+hit.victim,port,child->port.bleed(child,hit,hit.preMitigation*.12,4*(c.compiledPlan().foundationModifiers().lingering()?1.4:1)));
        var fearKey=new FearKey(c.request().actorId(),hit.victim);
        if(mods.terror()&&direct&&alive&&!hit.boss){
            if(fearReady.containsKey(fearKey))port.trace(c,"terror","TERROR_TARGET_ICD",fearReady.get(fearKey)-now);
            else if(roll(c,"terror",.15*hit.procCoefficient,port)){
                if(fearReady.size()>=4096||fearReady.keySet().stream().filter(k->k.actor.equals(c.request().actorId())).count()>=256)port.trace(c,"terror","TERROR_TARGET_BUDGET",0);
                else{
                    // An attempted native proc can be ambiguous; do not reroll immediately after failure.
                    fearReady.put(fearKey,now+8);
                    dispatch(c,"terror",hit.contact+"/"+hit.victim,port,child->port.fear(child,hit,1.5));
                }
            }
        }
        if(mods.shatter()&&!alive&&hit.frozenBefore&&hit.element.equals("COLD"))
            dispatch(c,"shatter",hit.victim.toString(),port,child->port.shatter(child,hit,shatterRadius(c.compiledPlan()),shatterAmount(c.compiledPlan(),hit)));
    }
    public static double shatterRadius(com.inigmasgames.hytalerpg.domain.CompiledSkillPlan plan){
        return 3*plan.executionModifiers().radiusFactor()*(plan.foundationModifiers().concentration()?.7:1);
    }
    public static double shatterAmount(com.inigmasgames.hytalerpg.domain.CompiledSkillPlan plan,Hit hit){
        double base=hit.preMitigation;
        if(plan.concentrationOnlyOnSecondary()){
            if(!Double.isFinite(hit.increasedUnit)||hit.increasedUnit<0)throw new IllegalArgumentException("RESOLVED_ADDITIVE_UNIT_UNAVAILABLE");
            base+=.30*hit.increasedUnit;
        }
        double amount=base*.5*(plan.radiusOnlyOnSecondary()?.9:1)*(plan.positionOnlyOnSecondary()?.9:1)*(plan.impactOnlyOnSecondary()?.9:1);
        if(!Double.isFinite(amount)||amount<0||amount>Float.MAX_VALUE)throw new IllegalArgumentException("SHATTER_DAMAGE_OVERFLOW");
        return amount;
    }
    private boolean roll(SkillExecutionContext c,String kind,double chance,Port port){
        double value=random.getAsDouble();if(!Double.isFinite(value)||value<0||value>=1){port.trace(c,kind,"INVALID_PROC_ROLL",chance);return false;}
        boolean accepted=value<chance;port.trace(c,kind,accepted?"PROC_ROLL_PASS":"PROC_ROLL_MISS",chance);return accepted;
    }
    private void dispatch(SkillExecutionContext c,String kind,String key,Port port,java.util.function.Function<SkillExecutionContext,String> call){
        String gate=c.effects().claim(kind+"/"+key,2,true);if(!gate.equals("PASS")){port.trace(c,kind,gate,0);return;}
        var child=c.hitProcCopy(kind,c.effects().triggered());
        try{port.trace(child,kind,call.apply(child),0);}catch(RuntimeException failure){
            port.trace(child,kind,"NATIVE_PROC_FAILED_PAID_ROOT_RETAINED:"+failure.getClass().getSimpleName()+":"+failure.getMessage(),0);
        }
    }
    public synchronized void cancel(UUID actor){fearReady.keySet().removeIf(key->key.actor.equals(actor));}
    public synchronized int size(){return fearReady.size();}
    public static double coefficient(SkillExecutionContext c){
        int simultaneous=c.profile().connection()!=null&&c.profile().connection().kind()==com.inigmasgames.hytalerpg.execution.connection.ConnectionProfile.Kind.ORBIT?
                c.profile().connection().details().bladeCount():c.profile().projectile()!=null?
                c.compiledPlan().projectileModifiers().batchSize()*c.profile().projectile().details().pattern().count():1;
        return 1d/Math.max(1,simultaneous);
    }
}
