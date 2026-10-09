package com.inigmasgames.hytalerpg.execution.hytale;

import com.hypixel.hytale.component.*;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.RefSystem;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.entity.effect.EffectControllerComponent;
import com.hypixel.hytale.server.core.modules.entity.component.Invulnerable;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import com.inigmasgames.hytalerpg.combat.hytale.*;
import com.inigmasgames.hytalerpg.gear.*;
import com.inigmasgames.hytalerpg.progress.RpgLoadoutService;
import java.util.Objects;
import java.util.UUID;
import java.util.function.ToDoubleFunction;

/** Typed native wiring for WA-144–147; item auras are published by HytaleSupportSystem's tick. */
public final class NativeItemAffixBindings {
    private final RpgLoadoutService loadouts;
    private final HytaleSkillExecutionSystem skills;
    private final ItemSkillTriggerRuntime triggers;
    private final ToDoubleFunction<HytaleDamageLifecycleSystems.AppliedReceipt> acceptedDirectDamage;
    private final ToDoubleFunction<HytaleDamageLifecycleSystems.AppliedReceipt> procCoefficient;
    private final ItemSkillTriggerRuntime.Port children;
    private final ItemSkillTriggerRuntime.Port sentinelChildren;

    /** Receipt extractors are method references to main-owned authoritative applied fields. */
    public NativeItemAffixBindings(RpgLoadoutService loadouts,HytaleSkillExecutionSystem skills,
            ToDoubleFunction<HytaleDamageLifecycleSystems.AppliedReceipt> acceptedDirectDamage,
            ToDoubleFunction<HytaleDamageLifecycleSystems.AppliedReceipt> procCoefficient,
            ItemSkillTriggerRuntime.Port sentinelChildren){
        this.loadouts=Objects.requireNonNull(loadouts);this.skills=Objects.requireNonNull(skills);
        this.acceptedDirectDamage=Objects.requireNonNull(acceptedDirectDamage);
        this.procCoefficient=Objects.requireNonNull(procCoefficient);
        this.triggers=new ItemSkillTriggerRuntime(NativeItemAffixBindings::stableRoll);
        this.children=skills::enqueueItemChild;
        this.sentinelChildren=Objects.requireNonNull(sentinelChildren);
    }
    public NativeItemSkillAvailabilitySystem availabilityTick(){return new NativeItemSkillAvailabilitySystem(loadouts);}
    public NativeItemSkillBlockObserver blockObserver(){return new NativeItemSkillBlockObserver(triggers,children);}
    public HytaleDamageLifecycleSystems.AppliedObserver applicationObserver(){return this::observed;}
    public Removal removal(){return new Removal(this);}
    public void detach(UUID actor){
        if(loadouts.ready(actor))loadouts.detachItemSkillAvailability(actor);
        triggers.clearActor(actor);
    }
    public void worldUnload(UUID world){triggers.clearWorld(world);}
    public ItemSkillTriggerRuntime triggers(){return triggers;}
    public static double stableRoll(String purpose){
        if(purpose==null||purpose.isBlank())throw new IllegalArgumentException("INVALID_ITEM_ROLL_KEY");
        long hash=0xcbf29ce484222325L;
        for(char ch:purpose.toCharArray()){hash^=ch;hash*=0x100000001b3L;}
        return (hash>>>11)*0x1.0p-53;
    }
    /** Sentinel owner calls only after its native successful block and bound-item validation. */
    public void sentinelBlock(ItemSkillTriggerRuntime.Block receipt,double now){
        triggers.blocked(receipt,now,sentinelChildren);
    }

    private void observed(HytaleDamageLifecycleSystems.AppliedReceipt receipt,Ref<EntityStore> target,
                          Ref<EntityStore> source,CommandBuffer<EntityStore> buffer){
        var gear=receipt.gearHit();var meta=receipt.metadata();
        if(gear==null||meta==null||source==null||!source.isValid()||target==null||!target.isValid()
                ||receipt.cancelled()||receipt.blocked()||meta.origin()!=HytaleDamageMetadata.Origin.DIRECT
                ||!HytaleAreaQueries.hostile(buffer.getStore(),target,source))return;
        if(receipt.executionContext()!=null&&receipt.executionContext().profile().strike()==null)return;
        var actor=buffer.getComponent(source,UUIDComponent.getComponentType());
        if(actor==null||!actor.getUuid().equals(meta.actorId()))return;
        var hit=gear.hit();if(hit==null||hit.itemId()==null||hit.snapshot().forItem(hit.itemId()).empty())return;
        // Skill channel two and later carry canProc=false to avoid repeated callbacks. A
        // cancelled first channel must not consume this contact's only item opportunity.
        if(!meta.canProc()){
            if(receipt.executionContext()==null||receipt.executionContext().derivedRelease())return;
            for(var channel:GearCombatEffects.Channel.values()){
                if(hit.amount(channel)<=0)continue;
                if(gear.channel()==channel)return;
                break;
            }
        }
        var npc=buffer.getComponent(target,NPCEntity.getComponentType());
        var effects=buffer.getComponent(target,EffectControllerComponent.getComponentType());
        boolean protectedTarget=buffer.getComponent(target,Invulnerable.getComponentType())!=null
                ||npc!=null&&npc.getRole()!=null&&npc.getRole().isInvulnerable()
                ||effects!=null&&effects.isInvulnerable();
        double accepted=acceptedDirectDamage.applyAsDouble(receipt),coefficient=procCoefficient.applyAsDouble(receipt);
        if(!Double.isFinite(accepted)||accepted<0||!Double.isFinite(coefficient)||coefficient<0||coefficient>1)
            throw new IllegalStateException("INVALID_ITEM_APPLICATION_RECEIPT");
        var world=buffer.getStore().getExternalData().getWorld().getWorldConfig().getUuid();
        var route=buffer.getComponent(source,PlayerRef.getComponentType())!=null?children:sentinelChildren;
        triggers.applied(new ItemSkillTriggerRuntime.DirectHit(world,meta.actorId(),hit.itemId(),receipt.targetId(),
                hit.rootId(),gear.contactId(),hit.snapshot(),true,true,false,false,false,accepted,coefficient,
                protectedTarget),System.nanoTime()/1e9,route);
    }
    public static final class Removal extends RefSystem<EntityStore> {
        private final NativeItemAffixBindings bindings;
        Removal(NativeItemAffixBindings bindings){this.bindings=bindings;}
        @Override public Query<EntityStore> getQuery(){return PlayerRef.getComponentType();}
        @Override public void onEntityAdded(Ref<EntityStore> ref,AddReason reason,Store<EntityStore> store,CommandBuffer<EntityStore> buffer) { }
        @Override public void onEntityRemove(Ref<EntityStore> ref,RemoveReason reason,Store<EntityStore> store,CommandBuffer<EntityStore> buffer){
            var player=store.getComponent(ref,PlayerRef.getComponentType());if(player!=null)bindings.detach(player.getUuid());
        }
    }
}
