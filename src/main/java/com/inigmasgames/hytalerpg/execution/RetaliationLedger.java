package com.inigmasgames.hytalerpg.execution;
import com.inigmasgames.hytalerpg.domain.SkillSlot;
import java.util.*;

/** Bounded actual-loss history and one pending owner input, not another combat/resource executor. */
public final class RetaliationLedger {
    private static final int OWNER_CAP=512,EVENT_CAP=256,SAMPLE_CAP=128;
    private final Map<UUID,Owner> owners=new HashMap<>();
    public record Ticket(SkillSlot slot,String planHash,String correlation,double threshold){}
    private record Sample(double at,double loss){}
    private static final class Entry{final String plan;final Deque<Sample> samples=new ArrayDeque<>();double lastAttempt=Double.NEGATIVE_INFINITY;Entry(String plan){this.plan=plan;}}
    private static final class Owner{final Map<SkillSlot,Entry> entries=new EnumMap<>(SkillSlot.class);final Map<String,Double> events=new LinkedHashMap<>();double nextAttempt=Double.NEGATIVE_INFINITY,lastTouched;Ticket pending;}
    private static void clock(double now){if(!Double.isFinite(now))throw new IllegalArgumentException("INVALID_RETALIATION_CLOCK");}
    private void prune(UUID actor,double now){var o=owners.get(actor);if(o==null)return;if(o.pending==null&&now-o.lastTouched>=10){owners.remove(actor);return;}
        o.events.values().removeIf(t->now-t>=10);for(var e:o.entries.values())e.samples.removeIf(s->now-s.at>=10);}
    private static double total(Entry e){return e.samples.stream().mapToDouble(Sample::loss).sum();}
    private static void cap(Entry e,double threshold){double excess=total(e)-threshold;while(excess>1e-9&&!e.samples.isEmpty()){var old=e.samples.removeFirst();if(old.loss>excess){e.samples.addFirst(new Sample(old.at,old.loss-excess));break;}excess-=old.loss;}}
    public synchronized String observe(UUID actor,Map<SkillSlot,String> plans,String event,double before,double after,double maximum,boolean hostile,boolean recursive,double now){
        clock(now);prune(actor,now);
        if(event==null||event.isBlank()||event.length()>256||!Double.isFinite(before)||!Double.isFinite(after)||!Double.isFinite(maximum)||maximum<=0)return "INVALID_RETALIATION_RECEIPT";
        if(!hostile||recursive||before<=after||before<=0||after<0)return "NO_ELIGIBLE_HOSTILE_LOSS";
        if(plans.isEmpty())return "NO_RETALIATION_LINK";
        if(!owners.containsKey(actor)){owners.values().removeIf(o->o.pending==null&&now-o.lastTouched>=10);if(owners.size()>=OWNER_CAP)return "RETALIATION_OWNER_CAPACITY";}
        var o=owners.computeIfAbsent(actor,a->new Owner());o.lastTouched=now;
        if(o.events.containsKey(event))return "DUPLICATE_RETALIATION_RECEIPT";
        if(o.events.size()>=EVENT_CAP)return "RETALIATION_EVENT_CAPACITY";
        o.events.put(event,now);o.entries.keySet().retainAll(plans.keySet());
        boolean overflow=false;double threshold=maximum*.15;
        for(var item:plans.entrySet()){
            var e=o.entries.get(item.getKey());if(e==null||!e.plan.equals(item.getValue())){e=new Entry(item.getValue());o.entries.put(item.getKey(),e);}
            if(e.samples.size()>=SAMPLE_CAP){overflow=true;continue;}
            e.samples.addLast(new Sample(now,Math.min(before-after,threshold)));cap(e,threshold);
        }
        return overflow?"RETALIATION_SAMPLE_CAPACITY":"RETALIATION_LOSS_RECORDED";
    }
    public synchronized Optional<Ticket> claim(UUID actor,Map<SkillSlot,String> plans,double maximum,double now){
        clock(now);prune(actor,now);var o=owners.get(actor);if(o==null||o.pending!=null||now<o.nextAttempt-1e-9||!Double.isFinite(maximum)||maximum<=0)return Optional.empty();
        double threshold=maximum*.15;
        o.entries.entrySet().removeIf(e->!e.getValue().plan.equals(plans.get(e.getKey())));
        var ready=o.entries.entrySet().stream().filter(e->!e.getValue().samples.isEmpty()&&total(e.getValue())>=threshold-1e-9)
                .min(Comparator.<Map.Entry<SkillSlot,Entry>>comparingDouble(e->e.getValue().lastAttempt).thenComparingDouble(e->e.getValue().samples.getFirst().at).thenComparing(Map.Entry::getKey));
        if(ready.isEmpty())return Optional.empty();var selected=ready.get();cap(selected.getValue(),threshold);
        selected.getValue().lastAttempt=now;o.nextAttempt=now+1;o.lastTouched=now;o.pending=new Ticket(selected.getKey(),selected.getValue().plan,"retaliation-"+UUID.randomUUID(),threshold);return Optional.of(o.pending);
    }
    public synchronized boolean authorized(UUID actor,SkillSlot slot,String plan,String correlation){var o=owners.get(actor);return o!=null&&o.pending!=null&&o.pending.slot==slot&&o.pending.planHash.equals(plan)&&o.pending.correlation.equals(correlation);}
    public synchronized void complete(UUID actor,String correlation,boolean committed,double now){
        clock(now);var o=owners.get(actor);if(o==null||o.pending==null||!o.pending.correlation.equals(correlation))return;
        var ticket=o.pending;o.pending=null;o.lastTouched=now;var e=o.entries.get(ticket.slot);
        if(e!=null&&e.plan.equals(ticket.planHash)){if(committed)e.samples.clear();else{e.samples.removeIf(s->now-s.at>=10);cap(e,ticket.threshold);}}
    }
    public synchronized void clearPlans(UUID actor){var o=owners.get(actor);if(o!=null){o.entries.clear();o.pending=null;}}
    public synchronized boolean tracked(UUID actor){return owners.containsKey(actor);}
    public synchronized void forget(UUID actor){owners.remove(actor);}
    public synchronized double accumulated(UUID actor,SkillSlot slot,double now){clock(now);prune(actor,now);var o=owners.get(actor);var e=o==null?null:o.entries.get(slot);return e==null?0:total(e);}
    public synchronized boolean pending(UUID actor){var o=owners.get(actor);return o!=null&&o.pending!=null;}
    public synchronized int size(){return owners.size();}
}
