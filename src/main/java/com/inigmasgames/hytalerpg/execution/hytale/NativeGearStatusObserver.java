package com.inigmasgames.hytalerpg.execution.hytale;

import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.inigmasgames.hytalerpg.combat.hytale.HytaleDamageLifecycleSystems;
import com.inigmasgames.hytalerpg.combat.hytale.NativeHealthRegenerationSuppression;
import com.inigmasgames.hytalerpg.combat.status.ControlProfile;
import com.inigmasgames.hytalerpg.combat.status.GearStatusRuntime;
import com.inigmasgames.hytalerpg.combat.status.RpgStatusType;
import com.inigmasgames.hytalerpg.combat.status.StatusService;
import com.inigmasgames.hytalerpg.gear.GearEffectSnapshot;
import com.inigmasgames.hytalerpg.gear.GearNativeItems;
import java.util.Map;
import java.util.List;
import java.util.Objects;

/** Real post-ApplyDamage gear-status hook. All target filtering and skill package input are mandatory. */
public final class NativeGearStatusObserver implements HytaleDamageLifecycleSystems.AppliedObserver {
    public interface Opportunity {
        boolean accepted(HytaleDamageLifecycleSystems.AppliedReceipt receipt);
        boolean hostile(Ref<EntityStore> source,Ref<EntityStore> target,CommandBuffer<EntityStore> buffer);
        ControlProfile control(Ref<EntityStore> target,CommandBuffer<EntityStore> buffer);
        double existingStatusResistance(Ref<EntityStore> target,CommandBuffer<EntityStore> buffer);
        double itemProcCoefficient(HytaleDamageLifecycleSystems.AppliedReceipt receipt);
        double skillChance(RpgStatusType type,HytaleDamageLifecycleSystems.AppliedReceipt receipt);
        int skillStacks(RpgStatusType type,HytaleDamageLifecycleSystems.AppliedReceipt receipt);
        boolean deepFreeze(HytaleDamageLifecycleSystems.AppliedReceipt receipt);
        String canonicalSkill(HytaleDamageLifecycleSystems.AppliedReceipt receipt);
        boolean targetChannelActive(Ref<EntityStore> target,CommandBuffer<EntityStore> buffer);
        GearEffectSnapshot targetGear(Ref<EntityStore> target,CommandBuffer<EntityStore> buffer);
        /** Existing execution RNG, keyed and journaled by root/contact/status/purpose. */
        double draw(RpgStatusType type,HytaleDamageLifecycleSystems.AppliedReceipt receipt,String purpose);
        GearStatusRuntime.PeriodicAdmission periodic(HytaleDamageLifecycleSystems.AppliedReceipt receipt,
                                                    Ref<EntityStore> target,CommandBuffer<EntityStore> buffer);
    }
    private static final Map<RpgStatusType,String> IDS=Map.ofEntries(
            Map.entry(RpgStatusType.BLEED,"WA-053"),Map.entry(RpgStatusType.BURN,"WA-054"),
            Map.entry(RpgStatusType.POISON,"WA-055"),Map.entry(RpgStatusType.CHILL,"WA-056"),
            Map.entry(RpgStatusType.ELECTRIFIED,"WA-057"),Map.entry(RpgStatusType.SLOW,"WA-058"),
            Map.entry(RpgStatusType.STUN,"WA-059"),Map.entry(RpgStatusType.SILENCE,"WA-060"),
            Map.entry(RpgStatusType.BLIND,"WA-061"),Map.entry(RpgStatusType.FEAR,"WA-062"),
            Map.entry(RpgStatusType.ROOT,"WA-063"));
    private static final List<RpgStatusType> ORDER=List.of(RpgStatusType.BLEED,RpgStatusType.BURN,
            RpgStatusType.POISON,RpgStatusType.CHILL,RpgStatusType.ELECTRIFIED,RpgStatusType.SLOW,
            RpgStatusType.STUN,RpgStatusType.SILENCE,RpgStatusType.BLIND,RpgStatusType.FEAR,
            RpgStatusType.ROOT);
    private final StatusService statuses;
    private final GearStatusRuntime.Contacts contacts;
    private final Opportunity opportunity;
    public NativeGearStatusObserver(StatusService statuses,GearStatusRuntime.Contacts contacts,Opportunity opportunity){
        this.statuses=Objects.requireNonNull(statuses);
        this.contacts=Objects.requireNonNull(contacts);
        this.opportunity=Objects.requireNonNull(opportunity);
    }
    @Override public void observed(HytaleDamageLifecycleSystems.AppliedReceipt receipt,Ref<EntityStore> target,
                                   Ref<EntityStore> source,CommandBuffer<EntityStore> buffer) {
        var gear=receipt.gearHit();
        if(gear==null||gear.hit().itemId()==null||!opportunity.accepted(receipt)
                ||receipt.cancelled()||receipt.blocked()||receipt.acceptedDamage()<=0
                ||source==null||!source.isValid()||!target.isValid()
                ||receipt.metadata().origin()!=com.inigmasgames.hytalerpg.combat.hytale.HytaleDamageMetadata.Origin.DIRECT
                ||!opportunity.hostile(source,target,buffer))return;
        var sourceId=buffer.getComponent(source,UUIDComponent.getComponentType());
        if(sourceId==null||!sourceId.getUuid().equals(receipt.metadata().actorId()))return;
        GearEffectSnapshot outgoing=GearStatusRuntime.sourceScoped(gear.hit().snapshot(),gear.hit().itemId());
        if(outgoing.empty())return;
        GearEffectSnapshot defense=opportunity.targetGear(target,buffer);
        var control=opportunity.control(target,buffer);
        double resistance=opportunity.existingStatusResistance(target,buffer);
        double coefficient=receipt.procCoefficient();
        var periodic=opportunity.periodic(receipt,target,buffer);
        var hit=new GearStatusRuntime.AppliedHit(sourceId.getUuid(),gear.hit().rootId(),gear.contactId(),
                receipt.targetId(),true,true,true,control.protectedEntity(),receipt.acceptedDamage(),coefficient,
                Map.of(gear.channel(),receipt.acceptedDamage()));
        boolean changed=false;
        for(var type:ORDER){
            double skillChance=opportunity.skillChance(type,receipt);
            if(outgoing.percent(IDS.get(type))<=0 && skillChance<=0
                    &&(type!=RpgStatusType.BLEED||outgoing.percent("WA-134")<=0))continue;
            var result=GearStatusRuntime.admit(statuses,contacts,hit,type,control,outgoing,
                    gear.hit().snapshot(),defense,resistance,
                    skillChance,opportunity.draw(type,receipt,"opportunity"),
                    opportunity.draw(type,receipt,"source"),periodic,
                    opportunity.canonicalSkill(receipt),opportunity.targetChannelActive(target,buffer),
                    opportunity.skillStacks(type,receipt),opportunity.deepFreeze(receipt));
            changed|=result.controlResult()!=null
                    &&result.controlResult().outcome()!=StatusService.Outcome.REJECTED;
        }
        if(changed){
            buffer.ensureComponent(target,AreaStatusProjection.getComponentType());
            HytaleAreaStatuses.synchronize(statuses,receipt.targetId(),target,buffer.getStore(),source);
        }
        if(statuses.healthRegenerationFactor(receipt.targetId())<1)
            NativeHealthRegenerationSuppression.install(
                    buffer.getComponent(target,com.hypixel.hytale.server.core.modules.entitystats.EntityStatMap.getComponentType()),
                    receipt.targetId(),statuses);
    }
}
