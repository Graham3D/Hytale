package com.inigmasgames.hytalerpg.execution;

import com.inigmasgames.hytalerpg.domain.CompiledSkillPlan.ExecutionModifiers;
import com.inigmasgames.hytalerpg.domain.SkillSlot;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Bounded world-tick release queue. Capacity is reserved before resource commitment; no worker-thread gameplay. */
public final class SkillReleaseScheduler {
    public static final int OWNER_CAP=6,GLOBAL_CAP=256;
    private final Map<String,Entry> entries=new LinkedHashMap<>();
    public record Release(String reservation,SkillExecutionContext context) { }
    public synchronized String conditional(SkillExecutionContext child,double due){
        requireClock(due);if(!child.conditionalRepeat())return "NOT_CONDITIONAL_RELEASE";
        if(entries.containsKey(child.skillInstanceId()))return "DUPLICATE_RELEASE_RESERVATION";
        if(entries.size()>=GLOBAL_CAP)return "GLOBAL_RELEASE_BUDGET";
        if(entries.values().stream().filter(e->e.owner.equals(child.request().actorId())).count()>=OWNER_CAP)return "OWNER_RELEASE_BUDGET";
        var entry=new Entry(child.request().actorId(),child.request().slot(),new ExecutionModifiers(1,0,0,1,false));
        entry.context=child;entry.due=due;entries.put(child.skillInstanceId(),entry);return "PASS";
    }
    public synchronized List<SkillExecutionContext> cancelConditional(UUID actor){
        var cancelled=new ArrayList<SkillExecutionContext>();
        entries.values().removeIf(e->{if(!e.owner.equals(actor)||e.context==null||!e.context.conditionalRepeat())return false;cancelled.add(e.context);return true;});return List.copyOf(cancelled);
    }
    public synchronized String reserve(String instance,UUID owner,SkillSlot slot,ExecutionModifiers modifiers) {
        if(!modifiers.scheduled()) return "PASS";
        if(entries.containsKey(instance)) return "DUPLICATE_RELEASE_RESERVATION";
        if(entries.values().stream().anyMatch(e->e.owner.equals(owner)&&e.slot==slot&&e.primaryPending)) return "PENDING_PRIMARY_FOR_SLOT";
        if(entries.size()>=GLOBAL_CAP) return "GLOBAL_RELEASE_BUDGET";
        if(entries.values().stream().filter(e->e.owner.equals(owner)).count()>=OWNER_CAP) return "OWNER_RELEASE_BUDGET";
        entries.put(instance,new Entry(owner,slot,modifiers));return "PASS";
    }
    public synchronized void arm(SkillExecutionContext context,double now) {
        requireClock(now);
        var entry=entries.get(context.skillInstanceId());if(entry==null)return;
        entry.context=context;entry.due=entry.modifiers.delaySeconds()>0?now+entry.modifiers.delaySeconds():Double.POSITIVE_INFINITY;
    }
    public synchronized void primaryReleased(SkillExecutionContext context,double now) {
        requireClock(now);
        var entry=entries.get(context.skillInstanceId());if(entry==null)return;
        if(entry.modifiers.barrageBatches()>1) {
            entry.primaryPending=false;entry.primaryReleasedAt=now;entry.context=context.barrageCopy(1);
            entry.due=now+entry.modifiers.barrageInterval();return;
        }
        if(entry.modifiers.echoDelaySeconds()<=0) { entries.remove(context.skillInstanceId());return; }
        entry.primaryPending=false;entry.context=context.echoCopy();entry.due=now+entry.modifiers.echoDelaySeconds();
    }
    public synchronized void additionalReleased(Release release) {
        if(!isCurrent(release))return;
        var entry=entries.get(release.reservation());var context=release.context();
        int next=context.barrageBatch()+1;
        if(context.conditionalRepeat()||context.echo()||next>=entry.modifiers.barrageBatches()) {entries.remove(release.reservation());return;}
        entry.context=context.barrageCopy(next);entry.due=entry.primaryReleasedAt+next*entry.modifiers.barrageInterval();
    }
    public synchronized List<Release> due(UUID owner,double now) {
        requireClock(now);
        List<Release> ready=new ArrayList<>();
        for(var item:entries.entrySet()) {
            var entry=item.getValue();if(!entry.owner.equals(owner)||entry.context==null||now<entry.due-1e-9)continue;
            entry.due=Double.POSITIVE_INFINITY; // Claim before callback; exceptions cannot replay this release.
            ready.add(new Release(item.getKey(),entry.context));
        }
        return List.copyOf(ready);
    }
    public synchronized void finish(String reservation) { entries.remove(reservation); }
    /** A cancellation invalidates already claimed work before its world-thread dispatch. */
    public synchronized boolean isCurrent(Release release) {
        var entry=entries.get(release.reservation());
        return entry!=null && entry.context==release.context();
    }
    public synchronized List<SkillExecutionContext> cancel(UUID owner) {
        List<SkillExecutionContext> cancelled=new ArrayList<>();
        entries.values().removeIf(e->{if(!e.owner.equals(owner))return false;if(e.context!=null)cancelled.add(e.context);return true;});
        return List.copyOf(cancelled);
    }
    public synchronized boolean hasPendingPrimary(UUID owner,SkillSlot slot) {
        return entries.values().stream().anyMatch(e->e.owner.equals(owner)&&e.slot==slot&&e.primaryPending);
    }
    public synchronized int size() { return entries.size(); }
    private static void requireClock(double now) {
        if(!Double.isFinite(now)) throw new IllegalArgumentException("Invalid release clock");
    }
    private static final class Entry {
        final UUID owner;final SkillSlot slot;final ExecutionModifiers modifiers;
        boolean primaryPending;SkillExecutionContext context;double due=Double.POSITIVE_INFINITY,primaryReleasedAt;
        Entry(UUID owner,SkillSlot slot,ExecutionModifiers modifiers) {
            this.owner=owner;this.slot=slot;this.modifiers=modifiers;primaryPending=modifiers.delaySeconds()>0;
        }
    }
}
