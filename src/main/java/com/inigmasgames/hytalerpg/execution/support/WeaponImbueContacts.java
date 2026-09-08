package com.inigmasgames.hytalerpg.execution.support;

import com.inigmasgames.hytalerpg.execution.SkillExecutionContext;
import java.util.*;

/** Pure consumption of authenticated root-weapon contact evidence. Native attribution is a separate capability gate. */
public final class WeaponImbueContacts {
    public record Hit(UUID world,UUID actor,UUID target,String rootAttackId,String contactId,String itemId,
                      boolean authenticatedRoot,boolean hostile,boolean applied,boolean derived){}
    public record Payload(SkillExecutionContext context,double fireCoefficient,boolean applyBurn,double burnSeconds,
                          double burnCoefficientPerSecond,String rootAttackId,String contactId){}
    private record Contact(UUID actor,String root,String contact,UUID target){}
    private record Burn(UUID actor,UUID target){}
    private final FiniteSupportEffects effects;
    private final Map<Contact,Double> consumed=new LinkedHashMap<>();
    private final Map<Burn,Double> burnAt=new LinkedHashMap<>();
    public WeaponImbueContacts(FiniteSupportEffects effects){this.effects=effects;}
    public synchronized Optional<Payload> contact(Hit hit,double now){
        if(!Double.isFinite(now)||hit.world==null||hit.actor==null||hit.target==null||hit.rootAttackId==null||hit.rootAttackId.isBlank()
                ||hit.contactId==null||hit.contactId.isBlank()||hit.itemId==null)throw new IllegalArgumentException("Incomplete weapon contact evidence");
        if(!hit.authenticatedRoot||!hit.hostile||!hit.applied||hit.derived||hit.actor.equals(hit.target))return Optional.empty();
        var active=effects.control(hit.world,hit.actor,SupportProfile.Kind.IMBUE,now);
        if(active.isEmpty())return Optional.empty();var e=active.get();
        if(e.context().equipment().mainHand()==null||!hit.itemId.equals(e.context().equipment().mainHand().itemId())){
            effects.remove(e.key());return Optional.empty();
        }
        consumed.values().removeIf(time->now-time>=30);burnAt.values().removeIf(time->now-time>=30);
        var key=new Contact(hit.actor,hit.rootAttackId,hit.contactId,hit.target);if(consumed.containsKey(key))return Optional.empty();
        if(consumed.size()>=16384||consumed.keySet().stream().filter(k->k.actor.equals(hit.actor)).count()>=512
                ||consumed.keySet().stream().filter(k->k.actor.equals(hit.actor)&&k.root.equals(hit.rootAttackId)).count()>=16)
            throw new IllegalStateException("IMBUE_CONTACT_OR_ROOT_SECONDARY_BUDGET");
        var victim=new Burn(hit.actor,hit.target);boolean burn=now-burnAt.getOrDefault(victim,Double.NEGATIVE_INFINITY)>=1;
        if(burn&&!burnAt.containsKey(victim)&&burnAt.size()>=16384)throw new IllegalStateException("IMBUE_BURN_TARGET_BUDGET");
        consumed.put(key,now);if(burn)burnAt.put(victim,now);
        return Optional.of(new Payload(e.context(),e.magnitude(),burn,4,.1,hit.rootAttackId,hit.contactId));
    }
    public synchronized void forget(UUID actor){consumed.keySet().removeIf(k->k.actor.equals(actor)||k.target.equals(actor));burnAt.keySet().removeIf(k->k.actor.equals(actor)||k.target.equals(actor));}
}
