package com.inigmasgames.hytalerpg.execution.support;

import com.inigmasgames.hytalerpg.combat.barrier.ManaguardLedger;
import com.inigmasgames.hytalerpg.combat.healing.HealingCalculationService;
import com.inigmasgames.hytalerpg.combat.resource.ReservationService;
import com.inigmasgames.hytalerpg.combat.resource.ResourceType;
import com.inigmasgames.hytalerpg.execution.OwnedFieldBudget;
import com.inigmasgames.hytalerpg.execution.SkillExecutionContext;
import com.inigmasgames.hytalerpg.execution.SkillExecutionResult;
import com.inigmasgames.hytalerpg.progress.SupportProgress;
import com.inigmasgames.hytalerpg.domain.SupportModifiers;
import java.util.*;

/** One bounded support owner: four Auras/player within the shared eight-field/128-global budget.
 * Stores no native entity references and no parallel current Mana/Health values. */
public final class SupportRuntime {
    private final ReservationService reservations;
    private final OwnedFieldBudget fields;
    private final SupportProgressStore progress;
    private final Map<UUID,LinkedHashMap<String,Aura>> active=new HashMap<>();
    private final Map<UUID,Session> sessions=new HashMap<>();
    private final FiniteSupportEffects finite=new FiniteSupportEffects();
    private final WeaponImbueContacts imbues=new WeaponImbueContacts(finite);
    public FiniteSupportEffects finite(){return finite;}
    public WeaponImbueContacts imbues(){return imbues;}
    public SupportRuntime(ReservationService reservations,OwnedFieldBudget fields,SupportProgressStore progress){
        this.reservations=reservations;this.fields=fields;this.progress=progress;
    }
    public synchronized boolean active(UUID actor,String skill){return active.getOrDefault(actor,new LinkedHashMap<>()).containsKey(skill);}
    public synchronized SupportProgress state(UUID actor){return session(actor).state;}
    /** Turning an owned Aura off is not a new cast and does not require another upfront payment/cooldown. */
    public synchronized SkillExecutionResult stopActive(UUID actor,String skill,SupportWorldPort port){
        if(!active(actor,skill))return null;
        var session=session(actor);var aura=active.get(actor).get(skill);
        if(session.state.toggleLocks().getOrDefault(skill,0d)>1e-9)return SkillExecutionResult.rejected("AURA_TOGGLE_LOCK");
        session.state=progress.save(actor,session.state.toggle(skill,aura.context.profile().support().toggleLockSeconds(),false));session.durable=session.state;
        end(actor,skill,"TOGGLED_OFF",port);return SkillExecutionResult.committed("AURA_OFF",0,0);
    }
    public synchronized void allocateManaguard(UUID actor,int percent,SupportWorldPort port){
        var session=session(actor);flush(actor,session);
        var allocated=session.state.managuard().allocate(percent);
        var aura=active.getOrDefault(actor,new LinkedHashMap<>()).get("managuard");
        if(aura==null){session.state=progress.save(actor,session.state.guard(allocated));session.durable=session.state;return;}
        String skill=aura.context.profile().skillId();
        if(session.state.toggleLocks().getOrDefault(skill,0.0)>1e-9)throw new IllegalStateException("AURA_TOGGLE_LOCK");
        double fraction=percent/100.0*aura.context.compiledPlan().supportModifiers().commitmentFactor(),current=port.resources().current(ResourceType.MANA);
        var previous=reservations.reservations(actor);
        try{
            reservations.addPercentage(actor,aura.allocation,fraction,port.resources());
            allocated=allocated.validateCapacity(port.resources().maximum(ResourceType.MANA)*fraction,
                    auraMagnitude(aura.context));
            session.state=progress.save(actor,session.state.guard(allocated).toggle(skill,aura.context.profile().support().toggleLockSeconds(),false));
            session.durable=session.state;aura.fraction=fraction;
        }catch(RuntimeException failure){
            try{reservations.rollbackMutation(actor,previous,current,port.resources());}
            catch(RuntimeException rollback){
                failure.addSuppressed(rollback);
                try{end(actor,skill,"ALLOCATION_ROLLBACK_FAILED",port);}catch(RuntimeException cleanup){failure.addSuppressed(cleanup);}
            }
            throw failure;
        }
        port.trace(aura.context,"AURA_ALLOCATION_CHANGED",Map.of("percent",percent,"deficit",session.state.managuard().deficit(),"currentMana",port.resources().current(ResourceType.MANA)));
    }
    public synchronized String preflight(UUID actor,String skill,SupportProfile profile,SupportWorldPort port){
        return preflight(actor,skill,profile,SupportModifiers.NONE,port);
    }
    public synchronized String preflight(UUID actor,String skill,SupportProfile profile,SupportModifiers modifiers,SupportWorldPort port){
        if(profile.kind()==SupportProfile.Kind.IMBUE&&!port.rootWeaponContactAvailable())return "NATIVE_ROOT_WEAPON_CONTACT_ID_UNAVAILABLE";
        var state=session(actor).state;
        if(!profile.aura())return "PASS";
        if(state.toggleLocks().getOrDefault(skill,0.0)>1e-9)return "AURA_TOGGLE_LOCK";
        if(active(actor,skill))return "PASS";
        if(active.getOrDefault(actor,new LinkedHashMap<>()).size()>=4)return "OWNER_AURA_BUDGET";
        String capacity=fields.admission(actor);if(!capacity.equals("PASS"))return capacity;
        double fraction=(profile.kind()==SupportProfile.Kind.MANAGUARD?state.managuard().allocationPercent()/100.0:profile.reservationFraction())*modifiers.commitmentFactor();
        double max=port.resources().maximum(ResourceType.MANA),amount=max*fraction;
        if(reservations.reserved(actor,max)+amount>max+1e-9)return "AURA_RESERVATION_OVERFLOW";
        if(port.resources().current(ResourceType.MANA)+1e-9<amount)return "INSUFFICIENT_CURRENT_MANA_FOR_AURA";
        return "PASS";
    }
    public synchronized SkillExecutionResult execute(SkillExecutionContext context,double now,SupportWorldPort port){
        var actor=context.request().actorId();var profile=context.profile().support();var skill=context.profile().skillId();
        String allowed=preflight(actor,skill,profile,context.compiledPlan().supportModifiers(),port);
        if(!allowed.equals("PASS"))throw new IllegalStateException(allowed);
        if(profile.finiteEffect()){
            port.finiteEffect(context,finite,now);
            port.trace(context,"FINITE_SUPPORT_RESOLVED",Map.of("kind",profile.kind().name(),"damage",0));
            port.present(context,radius(context),.3);
            return SkillExecutionResult.committed("FINITE_SUPPORT_APPLIED",0,0);
        }
        if(!profile.aura()){
            UUID target=context.target()==null?actor:context.target().entityId();
            if(target==null)throw new IllegalStateException("HEAL_TARGET_MISSING");
            var health=port.health(target);
            double requested=SupportMagnitude.healing(context,port.masteryMultiplier(context),health.current(),health.maximum());
            double actual=port.heal(context,target,requested);
            port.trace(context,"HEAL_RESOLVED",Map.of("target",target.toString(),"requested",requested,"actualHealing",actual));
            port.present(context,0,.6);
            return SkillExecutionResult.committed("HEAL_APPLIED",1,actual);
        }
        var session=session(actor);flush(actor,session);
        if(active(actor,skill)){
            SupportProgress saved=progress.save(actor,session.state.toggle(skill,profile.toggleLockSeconds(),false));
            session.state=saved;session.durable=saved;
            end(actor,skill,"TOGGLED_OFF",port);
            return SkillExecutionResult.committed("AURA_OFF",0,0);
        }
        Set<UUID> members=profile.allyAura()?members(context,radius(context),port):Set.of();
        Set<UUID> enemies=profile.hostileAura()?enemies(context,radius(context),port):Set.of();
        UUID sharedTarget=profile.kind()==SupportProfile.Kind.MANAGUARD&&context.compiledPlan().supportModifiers().sharedAegis()?port.nearestAlly(context):null;
        requireSharedAdmission(context,sharedTarget);
        double fraction=(profile.kind()==SupportProfile.Kind.MANAGUARD?session.state.managuard().allocationPercent()/100.0:profile.reservationFraction())*
                context.compiledPlan().supportModifiers().commitmentFactor();
        String allocation="aura:"+skill;
        double current=port.resources().current(ResourceType.MANA);
        var previous=reservations.reservations(actor);
        fields.reserve(actor,context.skillInstanceId());
        try{
            if(fraction>0)reservations.addPercentage(actor,allocation,fraction,port.resources());
            SupportProgress next=session.state.toggle(skill,profile.toggleLockSeconds(),true);
            if(profile.kind()==SupportProfile.Kind.MANAGUARD)
                next=next.guard(next.managuard().validateCapacity(port.resources().maximum(ResourceType.MANA)*fraction,
                        auraMagnitude(context)));
            session.state=progress.save(actor,next);session.durable=session.state;
        }catch(RuntimeException failure){
            try{reservations.rollbackMutation(actor,previous,current,port.resources());}
            catch(RuntimeException rollback){failure.addSuppressed(rollback);}
            fields.release(actor,context.skillInstanceId());throw failure;
        }
        var aura=new Aura(context,allocation,fraction,now,members,enemies);
        aura.sharedTarget=sharedTarget;
        active.computeIfAbsent(actor,ignored->new LinkedHashMap<>()).put(skill,aura);
        if(aura.timeline!=null){
            String payment;
            try{payment=advance(aura,now,port);}catch(RuntimeException failure){
                end(actor,skill,"INITIAL_UPKEEP_NATIVE_EXCEPTION",port);throw failure;
            }
            if(!payment.equals("ACTIVE")){end(actor,skill,payment,port);return SkillExecutionResult.committed(payment,0,0);}
        }
        try{port.auraMembership(context,List.copyOf(members),List.copyOf(enemies));port.sharedRecipient(context,sharedTarget);}
        catch(RuntimeException failure){end(actor,skill,"INITIAL_MEMBERSHIP_NATIVE_EXCEPTION",port);throw failure;}
        port.trace(context,"AURA_ACTIVATED",Map.of("epoch",session.state.lastAuraEpoch(),"reservationFraction",fraction,
                "currentMana",port.resources().current(ResourceType.MANA),"members",members.size()));
        port.present(context,radius(context),.2);
        return SkillExecutionResult.committed("AURA_ACTIVE",0,0);
    }
    public synchronized void tick(UUID actor,double now,boolean alive,SupportWorldPort port){
        var session=session(actor);
        if(!Double.isFinite(now)||now<0)throw new IllegalArgumentException("Invalid support clock");
        if(Double.isNaN(session.lastTick)){session.lastTick=now;session.lastHostile=now;return;}
        double seconds=now-session.lastTick;
        if(seconds<.1-1e-9)return;
        session.lastTick=now;
        // No offline, pause, clock-jump or unobserved multi-second recharge credit.
        if(seconds<=1&&alive){
            double eligible=Math.max(0,Math.min(seconds,now-session.lastHostile-6));
            session.state=session.state.observedTime(seconds,eligible);
        }
        if(!alive){cancel(actor,"OWNER_DEAD",port);flush(actor,session);return;}
        var cancelled=reservations.reconcileMaximum(actor,port.resources());
        for(var aura:List.copyOf(active.getOrDefault(actor,new LinkedHashMap<>()).values())){
            String reason=port.valid(aura.context);
            if(cancelled.contains(aura.allocation))reason="MAXIMUM_RESERVATION_CANCELLED";
            if(!reason.equals("PASS")){end(actor,aura.context.profile().skillId(),reason,port);continue;}
            var profile=aura.context.profile().support();
            if(profile.kind()==SupportProfile.Kind.MANAGUARD&&aura.context.compiledPlan().supportModifiers().sharedAegis()){
                try{
                    UUID next=port.nearestAlly(aura.context);
                    requireSharedAdmission(aura.context,next);
                    if(!Objects.equals(next,aura.sharedTarget))port.trace(aura.context,"AURA_MEMBERSHIP_CHANGED",Map.of("sharedRecipient",next==null?"NONE":next.toString(),
                            "sharedDeficit",session.state.managuard().sharedDeficit(),"refilled",false));
                    aura.sharedTarget=next;
                }catch(RuntimeException failure){end(actor,aura.context.profile().skillId(),"SHARED_AEGIS_QUERY_FAILED",port);continue;}
            }
            if(profile.kind()==SupportProfile.Kind.MANAGUARD)
                session.state=session.state.guard(session.state.managuard().validateCapacity(
                        port.resources().maximum(ResourceType.MANA)*aura.fraction,
                        auraMagnitude(aura.context)));
            if(profile.allyAura()||profile.hostileAura()){
                Set<UUID> next;
                try{next=profile.allyAura()?members(aura.context,radius(aura.context),port):Set.of();
                    aura.enemies=profile.hostileAura()?enemies(aura.context,radius(aura.context),port):Set.of();}
                catch(RuntimeException failure){end(actor,aura.context.profile().skillId(),"MEMBERSHIP_FAILED_"+failure.getMessage(),port);continue;}
                if(!aura.members.equals(next))port.trace(aura.context,"AURA_MEMBERSHIP_CHANGED",Map.of("before",aura.members.size(),"after",next.size()));
                aura.members=next;
            }
            if(aura.timeline!=null){
                String result;
                try{result=advance(aura,now,port);}catch(RuntimeException error){result="AURA_NATIVE_FAILURE_"+error.getClass().getSimpleName();}
                if(!result.equals("ACTIVE")){end(actor,aura.context.profile().skillId(),result,port);continue;}
            }
            aura.validUntil=aura.timeline==null?now+.25:Math.min(now+.25,aura.timeline.paidUntil());
            try{port.auraMembership(aura.context,List.copyOf(aura.members),List.copyOf(aura.enemies));port.sharedRecipient(aura.context,aura.sharedTarget);}
            catch(RuntimeException failure){end(actor,aura.context.profile().skillId(),"MEMBERSHIP_NATIVE_EXCEPTION",port);continue;}
            if(now-aura.lastVisual>=.5){port.present(aura.context,radius(aura.context),.2);aura.lastVisual=now;}
        }
        if(now-session.lastSave>=1){flush(actor,session);session.lastSave=now;}
    }
    /** Record hostile input damage even when the shield later absorbs the entire amount. */
    public synchronized void hostileDamage(UUID actor,double now){session(actor).lastHostile=now;}
    public synchronized double absorb(UUID actor,double nativeFilteredDamage,double now,SupportWorldPort port){
        return absorbDetailed(actor,nativeFilteredDamage,now,port).remainder();
    }
    public record GuardHit(double remainder,FiniteSupportEffects.Absorption absorption){}
    public synchronized GuardHit absorbDetailed(UUID actor,double nativeFilteredDamage,double now,SupportWorldPort port){
        if(!Double.isFinite(nativeFilteredDamage)||nativeFilteredDamage<=0)return new GuardHit(nativeFilteredDamage,null);
        var session=session(actor);
        for(var aura:active.getOrDefault(actor,new LinkedHashMap<>()).values()){
            if(aura.context.profile().support().kind()!=SupportProfile.Kind.MANAGUARD)continue;
            if(!port.valid(aura.context).equals("PASS"))continue;
            double capacity=port.resources().maximum(ResourceType.MANA)*aura.fraction*
                    auraMagnitude(aura.context);
            var absorbed=session.state.managuard().absorb(nativeFilteredDamage,capacity);
            if(absorbed.absorbed()<=0)return new GuardHit(nativeFilteredDamage,null);
            // Deficit is durable BEFORE the caller reduces the native Damage amount. Failed save grants no shield.
            var next=progress.save(actor,session.state.guard(absorbed.ledger()));session.state=next;session.durable=next;
            port.trace(aura.context,"BARRIER_ABSORBED",Map.of("nativeFilteredDamage",nativeFilteredDamage,
                    "absorbed",absorbed.absorbed(),"remainingDamage",absorbed.damageRemaining(),"deficit",next.managuard().deficit()));
            var effect=guardEffect(aura,actor,capacity,session.state.managuard().current(capacity),now);
            return new GuardHit(absorbed.damageRemaining(),new FiniteSupportEffects.Absorption(effect,absorbed.absorbed(),effect.shieldRemaining()));
        }
        return new GuardHit(nativeFilteredDamage,null);
    }
    private static FiniteSupportEffects.Effect guardEffect(Aura aura,UUID recipient,double capacity,double remaining,double now){
        var c=aura.context;return new FiniteSupportEffects.Effect(new FiniteSupportEffects.Key(c.target().worldId(),c.request().actorId(),c.profile().skillId(),recipient),
                SupportProfile.Kind.SHARED_SHIELD,capacity,0,now,aura.validUntil,c.rootCastId(),c.skillInstanceId(),c.request().correlationId(),c,remaining);
    }
    public synchronized List<FiniteSupportEffects.Effect> sharedGuards(UUID world,UUID recipient,double now){
        var result=new ArrayList<FiniteSupportEffects.Effect>();
        for(var list:active.values())for(var aura:list.values())if(aura.context.target().worldId().equals(world)&&recipient.equals(aura.sharedTarget)&&now<=aura.validUntil){
            var ledger=session(aura.context.request().actorId()).state.managuard();double capacity=ledger.lastValidatedCapacity();
            result.add(guardEffect(aura,recipient,capacity*.5,ledger.sharedCurrent(capacity),now));
        }
        result.sort(Comparator.comparing(e->e.key().owner().toString()));return List.copyOf(result);
    }
    private void requireSharedAdmission(SkillExecutionContext c,UUID recipient){
        if(recipient==null)return;int count=0;
        for(var list:active.values())for(var aura:list.values())if(recipient.equals(aura.sharedTarget)&&aura.context.target().worldId().equals(c.target().worldId())&&
                !aura.context.skillInstanceId().equals(c.skillInstanceId())&&++count>=32)throw new IllegalStateException("SHARED_AEGIS_TARGET_BUDGET");
    }
    /** 1 = granted, -1 = first rejection (trace once), 0 = already-reported rejection. */
    public synchronized int claimSupportSecondary(FiniteSupportEffects.Effect effect,double now){
        var c=effect.context();
        if(c.profile().support()!=null&&c.profile().support().aura()){
            var aura=active.getOrDefault(c.request().actorId(),new LinkedHashMap<>()).get(c.profile().skillId());
            if(aura==null)return 0;long epoch=(long)Math.floor(now);
            if(epoch!=aura.epoch)aura.limitReported=false;
            if(claimAuraSecondary(c,now))return 1;
            if(aura.limitReported)return 0;aura.limitReported=true;return -1;
        }
        return finite.claimSecondary(effect,now);
    }
    public synchronized GuardHit absorbShared(FiniteSupportEffects.Effect offered,double incoming,double now,SupportWorldPort port){
        var c=offered.context();UUID owner=c.request().actorId();var aura=active.getOrDefault(owner,new LinkedHashMap<>()).get(c.profile().skillId());
        if(aura==null||!aura.context.skillInstanceId().equals(c.skillInstanceId())||!offered.key().target().equals(aura.sharedTarget)||now>aura.validUntil||
                !port.valid(c).equals("PASS"))return new GuardHit(incoming,null);
        double capacity=port.resources().maximum(ResourceType.MANA)*aura.fraction*auraMagnitude(c);var state=session(owner);
        var absorption=state.state.managuard().absorbShared(incoming,capacity);
        if(absorption.absorbed()<=0)return new GuardHit(incoming,null);
        state.state=progress.save(owner,state.state.guard(absorption.ledger()));state.durable=state.state;
        var effect=guardEffect(aura,offered.key().target(),capacity*.5,state.state.managuard().sharedCurrent(capacity),now);
        port.trace(c,"BARRIER_ABSORBED",Map.of("target",offered.key().target().toString(),"absorbed",absorption.absorbed(),
                "sharedDeficit",state.state.managuard().sharedDeficit(),"remainingDamage",absorption.damageRemaining(),"derived",true));
        return new GuardHit(absorption.damageRemaining(),new FiniteSupportEffects.Absorption(effect,absorption.absorbed(),effect.shieldRemaining()));
    }
    public synchronized double manaRegenerationIncreased(UUID recipient,double now){
        double strongest=0;
        for(var bySkill:active.values())for(var aura:bySkill.values())
            if(aura.context.profile().support().kind()==SupportProfile.Kind.MANA_REGEN&&now<=aura.validUntil&&aura.members.contains(recipient))
                strongest=Math.max(strongest,aura.context.profile().support().coefficient()*aura.context.compiledPlan().supportModifiers().beneficialFactor());
        return strongest;
    }
    public synchronized double cooldownRecoveryIncreased(UUID recipient,double now){
        double strongest=0;
        for(var bySkill:active.values())for(var aura:bySkill.values())
            if(aura.context.profile().support().kind()==SupportProfile.Kind.COOLDOWN_AURA&&now<=aura.validUntil&&aura.members.contains(recipient))
                strongest=Math.max(strongest,aura.context.profile().support().coefficient()*aura.context.compiledPlan().supportModifiers().beneficialFactor());
        return strongest;
    }
    public synchronized Optional<FiniteSupportEffects.Effect> thorns(UUID world,UUID target,double now){
        Aura strongest=null;
        for(var bySkill:active.values())for(var aura:bySkill.values())
            if(aura.context.target().worldId().equals(world)&&aura.context.profile().support().kind()==SupportProfile.Kind.THORNS
                    &&now<=aura.validUntil&&aura.members.contains(target)&&(strongest==null||magnitude(aura)>magnitude(strongest)))strongest=aura;
        if(strongest==null)return Optional.empty();var c=strongest.context;
        return Optional.of(new FiniteSupportEffects.Effect(new FiniteSupportEffects.Key(world,c.request().actorId(),c.profile().skillId(),target),
                SupportProfile.Kind.REFLECT,magnitude(strongest),0,0,strongest.validUntil,c.rootCastId(),c.skillInstanceId(),c.request().correlationId(),c,0));
    }
    public synchronized boolean claimAuraSecondary(SkillExecutionContext context,double now){
        var aura=active.getOrDefault(context.request().actorId(),new LinkedHashMap<>()).get(context.profile().skillId());
        if(aura==null||!aura.context.skillInstanceId().equals(context.skillInstanceId())||now>aura.validUntil)return false;
        long epoch=(long)Math.floor(now);if(epoch!=aura.epoch){aura.epoch=epoch;aura.secondary=0;}
        if(aura.secondary>=8)return false;aura.secondary++;return true;
    }
    private static double magnitude(Aura aura){return aura.context.profile().support().coefficient()*auraMagnitude(aura.context)*
            (aura.context.compiledPlan().supportModifiers().selflessness()?1.35:1);}
    private static double auraMagnitude(SkillExecutionContext context){return context.snapshot().modifiers().factor()*context.compiledPlan().supportModifiers().effectFactor()*
            (context.profile().support().kind()==SupportProfile.Kind.MANAGUARD?context.compiledPlan().supportModifiers().barrierFactor():1);}
    private String advance(Aura aura,double now,SupportWorldPort port){
        return aura.timeline.advance(now,new AuraTimeline.Port(){
            public boolean pay(double seconds,int quantum){return port.upkeep(aura.context,seconds,quantum);}
            public void pulse(int ordinal,boolean chill){port.auraPulse(aura.context,List.copyOf(aura.enemies),ordinal,chill);}
        });
    }
    public synchronized void cancel(UUID actor,String reason,SupportWorldPort port){
        finite.forget(actor);
        imbues.forget(actor);
        RuntimeException failed=null;
        for(String skill:List.copyOf(active.getOrDefault(actor,new LinkedHashMap<>()).keySet())){
            try{end(actor,skill,reason,port);}catch(RuntimeException failure){if(failed==null)failed=failure;else failed.addSuppressed(failure);}
        }
        if(failed!=null)throw failed;
    }
    public synchronized void detach(UUID actor,String reason,SupportWorldPort port){
        try{cancel(actor,reason,port);}
        finally{try{var session=sessions.get(actor);if(session!=null)flush(actor,session);}finally{sessions.remove(actor);}}
    }
    public synchronized void terminateAura(UUID actor,String skill,String reason,SupportWorldPort port){end(actor,skill,reason,port);}
    private void end(UUID actor,String skill,String reason,SupportWorldPort port){
        var bySkill=active.get(actor);if(bySkill==null)return;var aura=bySkill.get(skill);if(aura==null)return;
        try{reservations.remove(actor,aura.allocation,port.resources());}
        finally{
            // A native write failure must not retain buffs/field capacity. Ready cleanup can release a stale stat modifier.
            reservations.remove(actor,aura.allocation);
            bySkill.remove(skill);if(bySkill.isEmpty())active.remove(actor);
            fields.release(actor,aura.context.skillInstanceId());
            port.auraEnded(aura.context);
        }
        port.trace(aura.context,"AURA_TERMINATED",Map.of("reason",reason,"refundMana",false));
    }
    private Set<UUID> members(SkillExecutionContext context,double radius,SupportWorldPort port){
        List<UUID> targets=port.allies(context,radius);
        if(targets.size()>64)throw new IllegalStateException("AURA_TARGET_BUDGET");
        var result=new HashSet<>(targets);if(context.compiledPlan().supportModifiers().selflessness())result.remove(context.request().actorId());
        return Set.copyOf(result);
    }
    private Set<UUID> enemies(SkillExecutionContext context,double radius,SupportWorldPort port){
        var targets=port.enemies(context,radius);if(targets.size()>64)throw new IllegalStateException("AURA_TARGET_BUDGET");return Set.copyOf(targets);
    }
    public static double radius(SkillExecutionContext context){return context.profile().support().radius()*context.compiledPlan().executionModifiers().radiusFactor()*context.compiledPlan().supportModifiers().radiusFactor();}
    private Session session(UUID actor){return sessions.computeIfAbsent(actor,id->new Session(progress.read(id)));}
    private void flush(UUID actor,Session session){
        if(session.state.equals(session.durable))return;
        session.state=progress.save(actor,session.state);session.durable=session.state;
    }
    public synchronized int auraCount(){return active.values().stream().mapToInt(Map::size).sum();}
    private static final class Session {
        SupportProgress state,durable;double lastTick=Double.NaN,lastSave,lastHostile;
        Session(SupportProgress state){this.state=state;durable=state;}
    }
    private static final class Aura {
        final SkillExecutionContext context;final String allocation;double fraction;
        Set<UUID> members,enemies;UUID sharedTarget;double validUntil,lastVisual;final AuraTimeline timeline;long epoch=-1;int secondary;boolean limitReported;
        Aura(SkillExecutionContext context,String allocation,double fraction,double now,Set<UUID> members,Set<UUID> enemies){
            this.context=context;this.allocation=allocation;this.fraction=fraction;this.members=members;this.enemies=enemies;validUntil=now+.25;lastVisual=now;
            timeline=context.profile().support().upkeepPerSecond()>0?new AuraTimeline(context.profile().support(),now):null;
        }
    }
}
