package com.inigmasgames.hytalerpg.execution.support;

import com.inigmasgames.hytalerpg.combat.damage.ModifierBuckets;
import com.inigmasgames.hytalerpg.execution.SkillExecutionContext;
import java.util.*;

/** Bounded, source-owned finite effects. No native references, damage writer, second resource pool or Aura timer. */
public final class FiniteSupportEffects {
    public static final int MAX_EFFECTS=4096,MAX_OWNER_EFFECTS=256,MAX_TARGET_EFFECTS=32;
    public record Key(UUID world,UUID owner,String skill,UUID target){}
    public record Effect(Key key,SupportProfile.Kind kind,double magnitude,double movement,double starts,double ends,
                         String rootCastId,String skillInstanceId,String correlationId,SkillExecutionContext context,double shieldRemaining){
        Effect remaining(double value){return new Effect(key,kind,magnitude,movement,starts,ends,rootCastId,skillInstanceId,correlationId,context,value);}
    }
    private final Map<Key,Effect> effects=new LinkedHashMap<>();
    public synchronized void apply(SkillExecutionContext context,List<UUID> targets,double seconds,double now){
        var next=prepare(context,targets,seconds,now);effects.clear();effects.putAll(next);
    }
    public synchronized void applyShield(SkillExecutionContext context,List<UUID> targets,double seconds,double capacity,double now){
        if(context.profile().support().kind()!=SupportProfile.Kind.SHIELD||!Double.isFinite(capacity)||capacity<=0)
            throw new IllegalArgumentException("Invalid shield capacity");
        var next=prepare(context,targets,seconds,now);
        for(var target:targets){
            var key=new Key(context.target().worldId(),context.request().actorId(),context.profile().skillId(),target);
            var e=next.get(key);
            // max(oldRemaining, newCreated), capped at newCreated, equals capacity: replace, never add copies.
            next.put(key,new Effect(key,e.kind,capacity,e.movement,e.starts,e.ends,e.rootCastId,e.skillInstanceId,e.correlationId,e.context,capacity));
        }
        effects.clear();effects.putAll(next);
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
            double magnitude=p.kind()==SupportProfile.Kind.REFLECT?p.coefficient()*context.snapshot().modifiers().factor():p.coefficient();
            next.put(key,new Effect(key,p.kind(),magnitude,p.movementIncreased(),now,now+seconds,
                    context.rootCastId(),context.skillInstanceId(),context.request().correlationId(),context,0));
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
        return victimModifiers(world,source,target,outgoingModifiers(world,source,base,now),now);
    }
    /** Capture outgoing buff/debuff contributions with source-owned DoT snapshots, never at every later DoT tick. */
    public synchronized ModifierBuckets outgoingModifiers(UUID world,UUID source,ModifierBuckets base,double now){
        expire(now);double rally=0,howl=0,weak=0;
        for(var e:effects.values()){
            if(!e.key.world.equals(world))continue;
            if(e.key.target.equals(source)&&e.kind==SupportProfile.Kind.RALLY)rally=Math.max(rally,e.magnitude);
            if(e.key.target.equals(source)&&e.kind==SupportProfile.Kind.HOWL)howl=Math.max(howl,e.magnitude);
            if(e.key.target.equals(source)&&e.kind==SupportProfile.Kind.WEAKEN)weak=Math.max(weak,1-e.magnitude);
        }
        var increased=new ArrayList<>(base.increased());if(rally>0)increased.add(rally);if(howl>0)increased.add(howl);
        var less=new ArrayList<>(base.less());if(weak>0)less.add(weak);
        return new ModifierBuckets(increased,base.reduced(),base.more(),less);
    }
    /** Victim-side mark is evaluated at the native hit boundary, not baked into the offensive DoT snapshot. */
    public synchronized ModifierBuckets victimModifiers(UUID world,UUID source,UUID target,ModifierBuckets base,double now){
        double mark=forTarget(world,target,now).stream().filter(e->e.kind==SupportProfile.Kind.MARK&&e.key.owner.equals(source))
                .mapToDouble(Effect::magnitude).max().orElse(0);
        if(mark==0)return base;var increased=new ArrayList<>(base.increased());increased.add(mark);
        return new ModifierBuckets(increased,base.reduced(),base.more(),base.less());
    }
    /** Native non-RPG hits have no RPG mark and no RPG snapshot bucket. Self-costs/reflections are excluded by caller. */
    public synchronized double nativeOutgoingFactor(UUID world,UUID source,double now){
        expire(now);double rally=0,howl=0,weak=0;
        for(var e:effects.values())if(e.key.world.equals(world)&&e.key.target.equals(source)){
            if(e.kind==SupportProfile.Kind.RALLY)rally=Math.max(rally,e.magnitude);
            if(e.kind==SupportProfile.Kind.HOWL)howl=Math.max(howl,e.magnitude);
            if(e.kind==SupportProfile.Kind.WEAKEN)weak=Math.max(weak,1-e.magnitude);
        }
        return (1+rally+howl)*(1-weak);
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
        var named=new EnumMap<SupportProfile.Kind,Double>(SupportProfile.Kind.class);
        for(var e:forTarget(world,target,now))named.merge(e.kind,e.movement,Math::max);
        return named.values().stream().mapToDouble(Double::doubleValue).sum();
    }
    public record Absorption(Effect effect,double amount,double remaining){}
    public record ShieldHit(double remainder,double absorbed,double redirected,List<Absorption> allocations){}
    @FunctionalInterface public interface Transfer { boolean transfer(Effect shield,double amount); }
    /** Already-mitigated incoming damage: one redirect, then ordered bounded absorption; no direct Health writes. */
    public synchronized ShieldHit shieldHit(UUID world,UUID target,double incoming,boolean mayRedirect,double now,Transfer transfer){
        if(!Double.isFinite(incoming)||incoming<0)throw new IllegalArgumentException("Invalid incoming damage");
        var shields=forTarget(world,target,now).stream().filter(e->e.kind==SupportProfile.Kind.SHIELD&&e.shieldRemaining>0)
                .sorted(Comparator.comparingDouble(Effect::starts).thenComparing(e->e.key.owner.toString())).toList();
        double remaining=incoming,redirected=0,absorbed=0;var allocations=new ArrayList<Absorption>();
        if(mayRedirect){
            var chosen=shields.stream().filter(e->!e.key.owner.equals(target)).findFirst();
            if(chosen.isPresent()&&transfer.transfer(chosen.get(),incoming*.2)){redirected=incoming*.2;remaining-=redirected;}
        }
        for(var shield:shields){
            var current=effects.get(shield.key);if(current==null)continue;
            double used=Math.min(remaining,current.shieldRemaining);remaining-=used;absorbed+=used;
            if(used>0)allocations.add(new Absorption(current,used,current.shieldRemaining-used));
            if(current.shieldRemaining-used<=1e-9)effects.remove(shield.key);
            else effects.put(shield.key,current.remaining(current.shieldRemaining-used));
            if(remaining<=1e-9)break;
        }
        return new ShieldHit(remaining,absorbed,redirected,List.copyOf(allocations));
    }
    public synchronized Optional<Effect> reflection(UUID world,UUID target,double now){
        return forTarget(world,target,now).stream().filter(e->e.kind==SupportProfile.Kind.REFLECT)
                .max(Comparator.comparingDouble(Effect::magnitude).thenComparing(e->e.key.owner.toString()));
    }
    public synchronized void remove(Key key){effects.remove(key);}
    public synchronized void forget(UUID actor){effects.values().removeIf(e->e.key.owner.equals(actor)||e.key.target.equals(actor));}
    public synchronized void clearWorld(UUID world){effects.values().removeIf(e->e.key.world.equals(world));}
    public synchronized void expire(double now){effects.values().removeIf(e->e.ends<=now);}
    public synchronized int size(){return effects.size();}
}
