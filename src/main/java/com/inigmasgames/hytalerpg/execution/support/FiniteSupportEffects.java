package com.inigmasgames.hytalerpg.execution.support;

import com.inigmasgames.hytalerpg.combat.damage.ModifierBuckets;
import com.inigmasgames.hytalerpg.execution.SkillExecutionContext;
import java.util.*;

/** Bounded, source-owned finite effects. No native references, damage writer, second resource pool or Aura timer. */
public final class FiniteSupportEffects {
    public static final int MAX_EFFECTS=4096,MAX_OWNER_EFFECTS=256,MAX_TARGET_EFFECTS=32;
    public record Key(UUID world,UUID owner,String skill,UUID target){}
    public record Effect(Key key,SupportProfile.Kind kind,double magnitude,double movement,double starts,double ends,
                         String rootCastId,String skillInstanceId,String correlationId){}
    private final Map<Key,Effect> effects=new LinkedHashMap<>();
    public synchronized void apply(SkillExecutionContext context,List<UUID> targets,double seconds,double now){
        var next=prepare(context,targets,seconds,now);effects.clear();effects.putAll(next);
    }
    /** Validate before native/status side effects; apply revalidates again when publishing the batch. */
    public synchronized void requireAdmission(SkillExecutionContext context,List<UUID> targets,double seconds,double now){
        prepare(context,targets,seconds,now);
    }
    private Map<Key,Effect> prepare(SkillExecutionContext context,List<UUID> targets,double seconds,double now){
        var p=context.profile().support();
        if(!p.finiteEffect()||!Double.isFinite(now)||!Double.isFinite(seconds)||seconds<=0||seconds>120)
            throw new IllegalArgumentException("Invalid finite support effect");
        if(targets.isEmpty()||targets.size()>64||new HashSet<>(targets).size()!=targets.size())throw new IllegalStateException("SUPPORT_TARGET_BUDGET");
        expire(now);
        var next=new LinkedHashMap<>(effects);
        UUID actor=context.request().actorId(),world=context.target().worldId();
        // One mark per caster, not one mark per victim or one additional mark on each reapplication.
        if(p.kind()==SupportProfile.Kind.MARK)next.entrySet().removeIf(e->e.getKey().owner().equals(actor)&&e.getValue().kind()==p.kind());
        for(UUID target:targets){
            var key=new Key(world,actor,context.profile().skillId(),Objects.requireNonNull(target));
            next.put(key,new Effect(key,p.kind(),p.coefficient(),p.movementIncreased(),now,now+seconds,
                    context.rootCastId(),context.skillInstanceId(),context.request().correlationId()));
        }
        if(next.size()>MAX_EFFECTS||next.values().stream().filter(e->e.key.owner.equals(actor)).count()>MAX_OWNER_EFFECTS)
            throw new IllegalStateException("FINITE_SUPPORT_OWNER_OR_GLOBAL_BUDGET");
        for(UUID target:targets)if(next.values().stream().filter(e->e.key.target.equals(target)).count()>MAX_TARGET_EFFECTS)
            throw new IllegalStateException("FINITE_SUPPORT_TARGET_BUDGET");
        return next;
    }
    public synchronized List<Effect> forTarget(UUID world,UUID target,double now){
        expire(now);return effects.values().stream().filter(e->e.key.world.equals(world)&&e.key.target.equals(target)).toList();
    }
    /** Identical named bonuses/debuffs refresh/choose strongest; different Increased contributions add once. */
    public synchronized ModifierBuckets damageModifiers(UUID world,UUID source,UUID target,ModifierBuckets base,double now){
        expire(now);double rally=0,mark=0,weak=0;
        for(var e:effects.values()){
            if(!e.key.world.equals(world))continue;
            if(e.key.target.equals(source)&&e.kind==SupportProfile.Kind.RALLY)rally=Math.max(rally,e.magnitude);
            if(e.key.target.equals(source)&&e.kind==SupportProfile.Kind.WEAKEN)weak=Math.max(weak,1-e.magnitude);
            if(e.key.owner.equals(source)&&e.key.target.equals(target)&&e.kind==SupportProfile.Kind.MARK)mark=Math.max(mark,e.magnitude);
        }
        var increased=new ArrayList<>(base.increased());if(rally>0)increased.add(rally);if(mark>0)increased.add(mark);
        var less=new ArrayList<>(base.less());if(weak>0)less.add(weak);
        return new ModifierBuckets(increased,base.reduced(),base.more(),less);
    }
    /** Native non-RPG hits have no RPG mark and no RPG snapshot bucket. Self-costs/reflections are excluded by caller. */
    public synchronized double nativeOutgoingFactor(UUID world,UUID source,double now){
        expire(now);double rally=0,weak=0;
        for(var e:effects.values())if(e.key.world.equals(world)&&e.key.target.equals(source)){
            if(e.kind==SupportProfile.Kind.RALLY)rally=Math.max(rally,e.magnitude);
            if(e.kind==SupportProfile.Kind.WEAKEN)weak=Math.max(weak,1-e.magnitude);
        }
        return (1+rally)*(1-weak);
    }
    public synchronized Optional<Effect> control(UUID world,UUID target,SupportProfile.Kind kind,double now){
        return forTarget(world,target,now).stream().filter(e->e.kind==kind)
                .max(Comparator.comparingDouble(Effect::starts).thenComparing(e->e.key.owner.toString()));
    }
    public synchronized boolean directDamage(UUID world,UUID target,double now,boolean direct,boolean positiveApplied){
        if(!direct||!positiveApplied)return false;
        return effects.values().removeIf(e->e.key.world.equals(world)&&e.key.target.equals(target)&&e.kind==SupportProfile.Kind.FEAR&&now-e.starts>=.25);
    }
    public synchronized double movementIncreased(UUID world,UUID target,double now){
        return forTarget(world,target,now).stream().mapToDouble(Effect::movement).max().orElse(0);
    }
    public synchronized void remove(Key key){effects.remove(key);}
    public synchronized void forget(UUID actor){effects.values().removeIf(e->e.key.owner.equals(actor)||e.key.target.equals(actor));}
    public synchronized void clearWorld(UUID world){effects.values().removeIf(e->e.key.world.equals(world));}
    public synchronized void expire(double now){effects.values().removeIf(e->e.ends<=now);}
    public synchronized int size(){return effects.size();}
}
