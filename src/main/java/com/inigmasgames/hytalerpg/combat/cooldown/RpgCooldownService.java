package com.inigmasgames.hytalerpg.combat.cooldown;

import com.inigmasgames.hytalerpg.combat.balance.CombatBalanceProfile;
import com.inigmasgames.hytalerpg.domain.CompiledSkillPlan;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.LongSupplier;

/** RPG cooldown authority. Recovery is a work-rate divisor; optional durable work never serializes Aura leases. */
public final class RpgCooldownService {
    private final CombatBalanceProfile profile;
    private final LongSupplier nanoTime;
    private final Map<Key, Work> work = new HashMap<>();
    private final Map<UUID, AuraRate> auraRates = new HashMap<>();
    public interface Persistence {Map<String,SavedCooldown> load(UUID actor);void save(UUID actor,Map<String,SavedCooldown> values);}
    private Persistence persistence;
    private final java.util.Set<UUID> restored=new java.util.HashSet<>();
    private final Map<UUID,Long> checkpoints=new HashMap<>();
    public synchronized void bindPersistence(Persistence persistence){
        if(this.persistence!=null||!work.isEmpty())throw new IllegalStateException("Cooldown persistence must bind before gameplay");
        this.persistence=java.util.Objects.requireNonNull(persistence);
    }
    public synchronized void restore(UUID actor){
        if(persistence==null||restored.contains(actor))return;
        var saved=SavedCooldown.validate(persistence.load(actor));long now=nanoTime.getAsLong();
        saved.forEach((skill,value)->{if(value.remainingWork()>0)work.put(new Key(actor,skill),new Work(value,now));});
        restored.add(actor);checkpoints.put(actor,now);
    }
    public synchronized Map<String,SavedCooldown> snapshot(UUID actor){
        restore(actor);long now=nanoTime.getAsLong();var saved=new HashMap<String,SavedCooldown>();
        work.forEach((key,value)->{if(key.actor.equals(actor)){advance(actor,value,now);if(!value.queue.isEmpty())saved.put(key.skill,value.saved());}});
        return SavedCooldown.validate(saved);
    }
    public synchronized boolean checkpoint(UUID actor){
        if(persistence==null)return false;restore(actor);long now=nanoTime.getAsLong();
        if(now-checkpoints.getOrDefault(actor,now)<1_000_000_000L)return false;
        checkpoints.put(actor,now); // Failed disk writes must not retry at frame rate.
        persistence.save(actor,snapshot(actor));return true;
    }
    /** Disconnect is not an explicit reset. Save observed work, then evict; do not credit unobserved offline time. */
    public synchronized void detach(UUID actor){
        if(persistence!=null)persistence.save(actor,snapshot(actor));
        work.keySet().removeIf(k->k.actor.equals(actor));auraRates.remove(actor);restored.remove(actor);checkpoints.remove(actor);
    }
    public RpgCooldownService(CombatBalanceProfile profile, LongSupplier nanoTime) {
        this.profile = profile; this.nanoTime = nanoTime;
    }
    public synchronized boolean canActivate(UUID actor, String skillId) { return remaining(actor, skillId) <= 0.0; }
    public synchronized boolean canActivate(UUID actor,String skillId,int capacity){return availableCharges(actor,skillId,capacity)>0;}
    public synchronized int availableCharges(UUID actor,String skillId,int capacity){
        if(capacity<1||capacity>2)throw new IllegalArgumentException("Unsupported charge capacity");
        remaining(actor,skillId);var value=work.get(new Key(actor,skillId));
        return Math.max(0,capacity-(value==null?0:value.queue.size()));
    }
    public synchronized Calculation startCooldown(UUID actor, String skillId, double baseSeconds,
                                                  double durationFactor, double wisdomRecovery,
                                                  CompiledSkillPlan.KernelModifiers modifiers) {
        return spendCharge(actor,skillId,1,baseSeconds,durationFactor,wisdomRecovery,modifiers).calculation();
    }
    /** Debt belongs to the skill, not its slot or current capacity. Relinking cannot erase spent charges. */
    public synchronized Spend spendCharge(UUID actor,String skillId,int capacity,double baseSeconds,double durationFactor,
                                          double wisdomRecovery,CompiledSkillPlan.KernelModifiers modifiers){
        if (!canActivate(actor, skillId,capacity)) throw new IllegalStateException("Skill has no available charge");
        Calculation calculation = calculate(actor,baseSeconds,durationFactor,wisdomRecovery,modifiers);
        long now=nanoTime.getAsLong();double baseRecovery=wisdomRecovery+(modifiers==null?0:modifiers.cooldownRecoveryBonus());
        double rate=rate(actor,baseRecovery,now);
        var saved=new HashMap<>(snapshot(actor));var key=new Key(actor,skillId);var current=work.get(key);
        var next=current==null?new Work(now):current.copy();var token=UUID.randomUUID();
        next.queue.addLast(new Charge(token,calculation.finalSeconds*rate,baseRecovery));
        saved.put(skillId,next.saved());
        if(persistence!=null)persistence.save(actor,SavedCooldown.validate(saved)); // Persist before the paid executor may run.
        work.put(key,next);return new Spend(actor,skillId,token,calculation);
    }
    /** Undo only this unexecuted spend. Never clear an older charge's cooldown on a failed second cast. */
    public synchronized boolean refundCharge(Spend spend){
        if(spend==null)return false;var saved=new HashMap<>(snapshot(spend.actor));var key=new Key(spend.actor,spend.skill);
        var current=work.get(key);if(current==null||current.queue.isEmpty()||!current.queue.getLast().token.equals(spend.token))return false;
        var next=current.copy();next.queue.removeLast();if(next.queue.isEmpty())saved.remove(spend.skill);else saved.put(spend.skill,next.saved());
        if(persistence!=null)persistence.save(spend.actor,SavedCooldown.validate(saved));
        if(next.queue.isEmpty())work.remove(key);else work.put(key,next);return true;
    }
    public synchronized Calculation calculate(UUID actor,double baseSeconds,double durationFactor,double wisdomRecovery,
                                               CompiledSkillPlan.KernelModifiers modifiers){
        var aura=auraRates.get(actor);long now=nanoTime.getAsLong();
        double bonus=aura!=null&&aura.expires>now?aura.recovery:0;
        double penalty=aura!=null&&aura.expires>now?aura.durationMultiplier:1;
        return calculate(baseSeconds,durationFactor*penalty,wisdomRecovery+bonus,modifiers);
    }
    /** Preserve completed cooldown work at every membership transition; stale Aura leases expire automatically. */
    public synchronized void setAuraRate(UUID actor,double recovery,double durationMultiplier,double leaseSeconds){
        if(!Double.isFinite(recovery)||recovery<0||!Double.isFinite(durationMultiplier)||durationMultiplier<1
                ||!Double.isFinite(leaseSeconds)||leaseSeconds<=0||leaseSeconds>1)throw new IllegalArgumentException("Invalid cooldown Aura rate");
        long now=nanoTime.getAsLong();
        work.forEach((key,value)->{if(key.actor.equals(actor))advance(actor,value,now);});
        if(recovery==0&&durationMultiplier==1)auraRates.remove(actor);
        else auraRates.put(actor,new AuraRate(recovery,durationMultiplier,now+Math.round(leaseSeconds*1e9)));
    }
    public Calculation calculate(double baseSeconds, double durationFactor, double wisdomRecovery,
                                 CompiledSkillPlan.KernelModifiers modifiers) {
        if (!Double.isFinite(baseSeconds)||!Double.isFinite(durationFactor)||!Double.isFinite(wisdomRecovery)||baseSeconds < 0.0 || durationFactor < 0.0)
            throw new IllegalArgumentException("Cooldown values must be finite and non-negative");
        double passiveRecovery = modifiers == null ? 0.0 : modifiers.cooldownRecoveryBonus();
        double totalRecovery = clamp(wisdomRecovery + passiveRecovery, 0.0, profile.cooldownRecoveryCap);
        double seconds = Math.max(profile.minimumCooldownSeconds, baseSeconds * durationFactor / (1.0 + totalRecovery));
        return new Calculation(baseSeconds, durationFactor, wisdomRecovery, passiveRecovery, totalRecovery, seconds);
    }
    public synchronized double remaining(UUID actor, String skillId) {
        restore(actor);
        Key key = new Key(actor, skillId);
        var value=work.get(key);if(value==null)return 0;
        long now=nanoTime.getAsLong();advance(actor,value,now);
        if(value.queue.isEmpty()){work.remove(key);return 0;}
        var first=value.queue.getFirst();return first.remaining/rate(actor,first.baseRecovery,now);
    }
    private void advance(UUID actor,Work value,long now){
        if(now<value.last)throw new IllegalStateException("Cooldown clock moved backwards");
        var aura=auraRates.get(actor);long boundary=aura==null?value.last:Math.max(value.last,Math.min(now,aura.expires));
        if(boundary>value.last)advanceSegment(actor,value,(boundary-value.last)/1e9,value.last);
        if(now>boundary)advanceSegment(actor,value,(now-boundary)/1e9,boundary);
        value.last=now;
    }
    private void advanceSegment(UUID actor,Work value,double seconds,long at){
        // At most two queue entries; carry elapsed work serially into the next charge, never in parallel.
        while(seconds>0&&!value.queue.isEmpty()){
            var first=value.queue.getFirst();double speed=rate(actor,first.baseRecovery,at),needed=first.remaining/speed;
            if(seconds+1e-12<needed){first.remaining-=seconds*speed;return;}
            seconds=Math.max(0,seconds-needed);value.queue.removeFirst();
        }
    }
    private double rate(UUID actor,double baseRecovery,long now){
        var aura=auraRates.get(actor);boolean active=aura!=null&&now<aura.expires;
        return (1+clamp(baseRecovery+(active?aura.recovery:0),0,profile.cooldownRecoveryCap))/(active?aura.durationMultiplier:1);
    }
    public synchronized boolean clear(UUID actor, String skillId) {
        var saved=new HashMap<>(snapshot(actor));saved.remove(skillId);if(persistence!=null)persistence.save(actor,saved);
        return work.remove(new Key(actor,skillId))!=null;
    }
    public synchronized void clear(UUID actor) {
        restore(actor);if(persistence!=null)persistence.save(actor,Map.of());work.keySet().removeIf(key -> key.actor.equals(actor));auraRates.remove(actor);
    }
    private static double clamp(double value, double min, double max) { return Math.max(min, Math.min(max, value)); }
    private record Key(UUID actor, String skill) { }
    private record AuraRate(double recovery,double durationMultiplier,long expires){}
    private static final class Charge {final UUID token;double remaining;final double baseRecovery;
        Charge(UUID token,double remaining,double recovery){this.token=token;this.remaining=remaining;this.baseRecovery=recovery;}
        Charge copy(){return new Charge(token,remaining,baseRecovery);}}
    private static final class Work {
        final java.util.ArrayDeque<Charge> queue=new java.util.ArrayDeque<>();long last;
        Work(long now){last=now;}
        Work(SavedCooldown saved,long now){this(now);queue.add(new Charge(UUID.randomUUID(),saved.remainingWork(),saved.baseRecovery()));
            saved.queued().forEach(v->queue.add(new Charge(UUID.randomUUID(),v.remainingWork(),v.baseRecovery())));}
        Work copy(){var result=new Work(last);queue.forEach(v->result.queue.add(v.copy()));return result;}
        SavedCooldown saved(){var first=queue.getFirst();return new SavedCooldown(first.remaining,first.baseRecovery,
                queue.stream().skip(1).map(v->new SavedCooldown.Queued(v.remaining,v.baseRecovery)).toList());}
    }
    public record Spend(UUID actor,String skill,UUID token,Calculation calculation){}
    public record Calculation(double baseSeconds, double durationFactor, double wisdomRecovery,
                              double passiveRecovery, double appliedRecovery, double finalSeconds) { }
}
