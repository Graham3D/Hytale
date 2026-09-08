package com.inigmasgames.hytalerpg.execution.support;

import com.inigmasgames.hytalerpg.combat.barrier.ManaguardLedger;
import com.inigmasgames.hytalerpg.combat.healing.HealingCalculationService;
import com.inigmasgames.hytalerpg.combat.resource.ReservationService;
import com.inigmasgames.hytalerpg.combat.resource.ResourceType;
import com.inigmasgames.hytalerpg.execution.OwnedFieldBudget;
import com.inigmasgames.hytalerpg.execution.SkillExecutionContext;
import com.inigmasgames.hytalerpg.execution.SkillExecutionResult;
import com.inigmasgames.hytalerpg.progress.SupportProgress;
import java.util.*;

/** One bounded support owner: four Auras/player within the shared eight-field/128-global budget.
 * Stores no native entity references and no parallel current Mana/Health values. */
public final class SupportRuntime {
    private final ReservationService reservations;
    private final OwnedFieldBudget fields;
    private final SupportProgressStore progress;
    private final Map<UUID,LinkedHashMap<String,Aura>> active=new HashMap<>();
    private final Map<UUID,Session> sessions=new HashMap<>();
    public SupportRuntime(ReservationService reservations,OwnedFieldBudget fields,SupportProgressStore progress){
        this.reservations=reservations;this.fields=fields;this.progress=progress;
    }
    public synchronized boolean active(UUID actor,String skill){return active.getOrDefault(actor,new LinkedHashMap<>()).containsKey(skill);}
    public synchronized SupportProgress state(UUID actor){return session(actor).state;}
    public synchronized void allocateManaguard(UUID actor,int percent,SupportWorldPort port){
        var session=session(actor);flush(actor,session);
        var allocated=session.state.managuard().allocate(percent);
        var aura=active.getOrDefault(actor,new LinkedHashMap<>()).get("managuard");
        if(aura==null){session.state=progress.save(actor,session.state.guard(allocated));session.durable=session.state;return;}
        String skill=aura.context.profile().skillId();
        if(session.state.toggleLocks().getOrDefault(skill,0.0)>1e-9)throw new IllegalStateException("AURA_TOGGLE_LOCK");
        double fraction=percent/100.0,current=port.resources().current(ResourceType.MANA);
        var previous=reservations.reservations(actor);
        try{
            reservations.addPercentage(actor,aura.allocation,fraction,port.resources());
            allocated=allocated.validateCapacity(port.resources().maximum(ResourceType.MANA)*fraction,
                    aura.context.snapshot().modifiers().factor());
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
        var state=session(actor).state;
        if(!profile.aura())return "PASS";
        if(state.toggleLocks().getOrDefault(skill,0.0)>1e-9)return "AURA_TOGGLE_LOCK";
        if(active(actor,skill))return "PASS";
        if(active.getOrDefault(actor,new LinkedHashMap<>()).size()>=4)return "OWNER_AURA_BUDGET";
        String capacity=fields.admission(actor);if(!capacity.equals("PASS"))return capacity;
        double fraction=profile.kind()==SupportProfile.Kind.MANAGUARD?state.managuard().allocationPercent()/100.0:profile.reservationFraction();
        double max=port.resources().maximum(ResourceType.MANA),amount=max*fraction;
        if(reservations.reserved(actor,max)+amount>max+1e-9)return "AURA_RESERVATION_OVERFLOW";
        if(port.resources().current(ResourceType.MANA)+1e-9<amount)return "INSUFFICIENT_CURRENT_MANA_FOR_AURA";
        return "PASS";
    }
    public synchronized SkillExecutionResult execute(SkillExecutionContext context,double now,SupportWorldPort port){
        var actor=context.request().actorId();var profile=context.profile().support();var skill=context.profile().skillId();
        String allowed=preflight(actor,skill,profile,port);
        if(!allowed.equals("PASS"))throw new IllegalStateException(allowed);
        if(!profile.aura()){
            UUID target=context.target()==null?actor:context.target().entityId();
            if(target==null)throw new IllegalStateException("HEAL_TARGET_MISSING");
            double requested=new HealingCalculationService().direct(20,profile.coefficient(),
                    context.snapshot().derivedStats().healingMultiplier(),port.masteryMultiplier(context),
                    0)*context.snapshot().modifiers().factor();
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
        Set<UUID> members=profile.kind()==SupportProfile.Kind.MANA_REGEN?members(context,radius(context),port):Set.of();
        double fraction=profile.kind()==SupportProfile.Kind.MANAGUARD?session.state.managuard().allocationPercent()/100.0:profile.reservationFraction();
        String allocation="aura:"+skill;
        double current=port.resources().current(ResourceType.MANA);
        var previous=reservations.reservations(actor);
        fields.reserve(actor,context.skillInstanceId());
        try{
            reservations.addPercentage(actor,allocation,fraction,port.resources());
            SupportProgress next=session.state.toggle(skill,profile.toggleLockSeconds(),true);
            if(profile.kind()==SupportProfile.Kind.MANAGUARD)
                next=next.guard(next.managuard().validateCapacity(port.resources().maximum(ResourceType.MANA)*fraction,
                        context.snapshot().modifiers().factor()));
            session.state=progress.save(actor,next);session.durable=session.state;
        }catch(RuntimeException failure){
            try{reservations.rollbackMutation(actor,previous,current,port.resources());}
            catch(RuntimeException rollback){failure.addSuppressed(rollback);}
            fields.release(actor,context.skillInstanceId());throw failure;
        }
        var aura=new Aura(context,allocation,fraction,now,members);
        active.computeIfAbsent(actor,ignored->new LinkedHashMap<>()).put(skill,aura);
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
            if(profile.kind()==SupportProfile.Kind.MANAGUARD)
                session.state=session.state.guard(session.state.managuard().validateCapacity(
                        port.resources().maximum(ResourceType.MANA)*aura.fraction,
                        aura.context.snapshot().modifiers().factor()));
            if(profile.kind()==SupportProfile.Kind.MANA_REGEN){
                Set<UUID> next;
                try{next=members(aura.context,radius(aura.context),port);}
                catch(RuntimeException failure){end(actor,aura.context.profile().skillId(),"MEMBERSHIP_FAILED_"+failure.getMessage(),port);continue;}
                if(!aura.members.equals(next))port.trace(aura.context,"AURA_MEMBERSHIP_CHANGED",Map.of("before",aura.members.size(),"after",next.size()));
                aura.members=next;
            }
            aura.validUntil=now+.25;
            if(now-aura.lastVisual>=.5){port.present(aura.context,radius(aura.context),.2);aura.lastVisual=now;}
        }
        if(now-session.lastSave>=1){flush(actor,session);session.lastSave=now;}
    }
    /** Record hostile input damage even when the shield later absorbs the entire amount. */
    public synchronized void hostileDamage(UUID actor,double now){session(actor).lastHostile=now;}
    public synchronized double absorb(UUID actor,double nativeFilteredDamage,double now,SupportWorldPort port){
        if(!Double.isFinite(nativeFilteredDamage)||nativeFilteredDamage<=0)return nativeFilteredDamage;
        var session=session(actor);
        for(var aura:active.getOrDefault(actor,new LinkedHashMap<>()).values()){
            if(aura.context.profile().support().kind()!=SupportProfile.Kind.MANAGUARD)continue;
            if(!port.valid(aura.context).equals("PASS"))continue;
            double capacity=port.resources().maximum(ResourceType.MANA)*aura.fraction*
                    aura.context.snapshot().modifiers().factor();
            var absorbed=session.state.managuard().absorb(nativeFilteredDamage,capacity);
            if(absorbed.absorbed()<=0)return nativeFilteredDamage;
            // Deficit is durable BEFORE the caller reduces the native Damage amount. Failed save grants no shield.
            var next=progress.save(actor,session.state.guard(absorbed.ledger()));session.state=next;session.durable=next;
            port.trace(aura.context,"BARRIER_ABSORBED",Map.of("nativeFilteredDamage",nativeFilteredDamage,
                    "absorbed",absorbed.absorbed(),"remainingDamage",absorbed.damageRemaining(),"deficit",next.managuard().deficit()));
            return absorbed.damageRemaining();
        }
        return nativeFilteredDamage;
    }
    public synchronized double manaRegenerationIncreased(UUID recipient,double now){
        double strongest=0;
        for(var bySkill:active.values())for(var aura:bySkill.values())
            if(aura.context.profile().support().kind()==SupportProfile.Kind.MANA_REGEN&&now<=aura.validUntil&&aura.members.contains(recipient))
                strongest=Math.max(strongest,aura.context.profile().support().coefficient());
        return strongest;
    }
    public synchronized void cancel(UUID actor,String reason,SupportWorldPort port){
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
    private void end(UUID actor,String skill,String reason,SupportWorldPort port){
        var bySkill=active.get(actor);if(bySkill==null)return;var aura=bySkill.get(skill);if(aura==null)return;
        try{reservations.remove(actor,aura.allocation,port.resources());}
        finally{
            // A native write failure must not retain buffs/field capacity. Ready cleanup can release a stale stat modifier.
            reservations.remove(actor,aura.allocation);
            bySkill.remove(skill);if(bySkill.isEmpty())active.remove(actor);
            fields.release(actor,aura.context.skillInstanceId());
        }
        port.trace(aura.context,"AURA_TERMINATED",Map.of("reason",reason,"refundMana",false));
    }
    private Set<UUID> members(SkillExecutionContext context,double radius,SupportWorldPort port){
        List<UUID> targets=port.allies(context,radius);
        if(targets.size()>64)throw new IllegalStateException("AURA_TARGET_BUDGET");
        return Set.copyOf(targets);
    }
    private static double radius(SkillExecutionContext context){return context.profile().support().radius()*context.compiledPlan().executionModifiers().radiusFactor();}
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
        Set<UUID> members;double validUntil,lastVisual;
        Aura(SkillExecutionContext context,String allocation,double fraction,double now,Set<UUID> members){
            this.context=context;this.allocation=allocation;this.fraction=fraction;this.members=members;validUntil=now+.25;lastVisual=now;
        }
    }
}
