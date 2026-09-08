package com.inigmasgames.hytalerpg.combat.cooldown;

import com.inigmasgames.hytalerpg.combat.balance.CombatBalanceProfile;
import com.inigmasgames.hytalerpg.domain.CompiledSkillPlan;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.LongSupplier;

/** Runtime-only cooldown state. Recovery is a work-rate divisor, never a silent duration subtraction. */
public final class RpgCooldownService {
    private final CombatBalanceProfile profile;
    private final LongSupplier nanoTime;
    private final Map<Key, Work> work = new HashMap<>();
    private final Map<UUID, AuraRate> auraRates = new HashMap<>();
    public RpgCooldownService(CombatBalanceProfile profile, LongSupplier nanoTime) {
        this.profile = profile; this.nanoTime = nanoTime;
    }
    public synchronized boolean canActivate(UUID actor, String skillId) { return remaining(actor, skillId) <= 0.0; }
    public synchronized Calculation startCooldown(UUID actor, String skillId, double baseSeconds,
                                                  double durationFactor, double wisdomRecovery,
                                                  CompiledSkillPlan.KernelModifiers modifiers) {
        if (!canActivate(actor, skillId)) throw new IllegalStateException("Skill is already on cooldown");
        Calculation calculation = calculate(actor,baseSeconds,durationFactor,wisdomRecovery,modifiers);
        long now=nanoTime.getAsLong();double baseRecovery=wisdomRecovery+(modifiers==null?0:modifiers.cooldownRecoveryBonus());
        double rate=rate(actor,baseRecovery,now);
        work.put(new Key(actor,skillId),new Work(calculation.finalSeconds*rate,baseRecovery,now));
        return calculation;
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
        Key key = new Key(actor, skillId);
        var value=work.get(key);if(value==null)return 0;
        long now=nanoTime.getAsLong();advance(actor,value,now);
        if(value.remaining<=1e-9){work.remove(key);return 0;}
        return value.remaining/rate(actor,value.baseRecovery,now);
    }
    private void advance(UUID actor,Work value,long now){
        if(now<value.last)throw new IllegalStateException("Cooldown clock moved backwards");
        var aura=auraRates.get(actor);long boundary=aura==null?value.last:Math.max(value.last,Math.min(now,aura.expires));
        if(boundary>value.last)value.remaining-=(boundary-value.last)/1e9*rate(actor,value.baseRecovery,value.last);
        if(now>boundary)value.remaining-=(now-boundary)/1e9*(1+clamp(value.baseRecovery,0,profile.cooldownRecoveryCap));
        value.last=now;
    }
    private double rate(UUID actor,double baseRecovery,long now){
        var aura=auraRates.get(actor);boolean active=aura!=null&&now<aura.expires;
        return (1+clamp(baseRecovery+(active?aura.recovery:0),0,profile.cooldownRecoveryCap))/(active?aura.durationMultiplier:1);
    }
    public synchronized boolean clear(UUID actor, String skillId) { return work.remove(new Key(actor, skillId)) != null; }
    public synchronized void clear(UUID actor) { work.keySet().removeIf(key -> key.actor.equals(actor));auraRates.remove(actor); }
    private static double clamp(double value, double min, double max) { return Math.max(min, Math.min(max, value)); }
    private record Key(UUID actor, String skill) { }
    private record AuraRate(double recovery,double durationMultiplier,long expires){}
    private static final class Work {double remaining;final double baseRecovery;long last;Work(double remaining,double baseRecovery,long last){this.remaining=remaining;this.baseRecovery=baseRecovery;this.last=last;}}
    public record Calculation(double baseSeconds, double durationFactor, double wisdomRecovery,
                              double passiveRecovery, double appliedRecovery, double finalSeconds) { }
}
