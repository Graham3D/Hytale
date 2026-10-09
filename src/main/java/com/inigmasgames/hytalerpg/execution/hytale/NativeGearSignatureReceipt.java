package com.inigmasgames.hytalerpg.execution.hytale;

import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.SystemGroup;
import com.hypixel.hytale.component.dependency.*;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.modules.entity.damage.*;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatMap;
import com.hypixel.hytale.server.core.modules.entitystats.asset.DefaultEntityStatTypes;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.inigmasgames.hytalerpg.combat.hytale.HytaleDamageAdapter;
import com.inigmasgames.hytalerpg.combat.hytale.HytaleDamageLifecycleSystems;
import com.inigmasgames.hytalerpg.gear.GearSignatureProcRuntime;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** Post-Apply bridge. The combat owner must supply accepted strike facts that are absent
 * from GearCombatEffects.Hit; no amount or coefficient is inferred from native Damage. */
public final class NativeGearSignatureReceipt implements HytaleDamageLifecycleSystems.AppliedObserver {
    public record Incoming(UUID world,UUID wearer,UUID attacker,String root,String contact,
                           com.inigmasgames.hytalerpg.gear.GearEffectSnapshot validEquipment,
                           boolean directMelee,boolean hostile,boolean reflected,boolean blocked,
                           double actualHpLoss,boolean protectedAttacker) { }
    public interface Facts {
        Optional<GearSignatureProcRuntime.Contact> outgoing(HytaleDamageLifecycleSystems.AppliedReceipt receipt,
                Ref<EntityStore> target,Ref<EntityStore> source,CommandBuffer<EntityStore> buffer);
        Optional<Incoming> incoming(HytaleDamageLifecycleSystems.AppliedReceipt receipt,
                Ref<EntityStore> target,Ref<EntityStore> source,CommandBuffer<EntityStore> buffer);
        /** Native NPC/basic Damage has no RPG metadata; classify melee from its real interaction witness. */
        Optional<Incoming> rawIncoming(Damage damage,double before,double after,Ref<EntityStore> target,
                Ref<EntityStore> source,CommandBuffer<EntityStore> buffer);
    }
    private final GearSignatureProcRuntime runtime;
    private final NativeGearSignatureProcs nativePort;
    private final Facts facts;
    private static final com.hypixel.hytale.server.core.meta.MetaKey<Double> RAW_BEFORE=
            Damage.META_REGISTRY.registerMetaObject(ignored->null,false,"Hywind:SignatureRawHpBefore",null);
    public NativeGearSignatureReceipt(GearSignatureProcRuntime runtime,NativeGearSignatureProcs nativePort,Facts facts){
        this.runtime=Objects.requireNonNull(runtime);this.nativePort=Objects.requireNonNull(nativePort);
        this.facts=Objects.requireNonNull(facts);
    }
    /** Death reward owner calls this once with its credited root and original hit composition. */
    public void creditedKill(GearSignatureProcRuntime.Contact contact,
                             com.hypixel.hytale.component.Store<EntityStore> store,Ref<EntityStore> source){
        if(!contact.creditedKill()||!contact.world().equals(store.getExternalData().getWorld().getWorldConfig().getUuid()))
            throw new IllegalArgumentException("FOREIGN_SIGNATURE_DEATH_RECEIPT");
        runtime.creditedKill(contact,System.nanoTime()/1e9,nativePort.at(store,source));
    }
    /** Metadata-free native melee HP observation, before native ApplyDamage. */
    public final class RawBefore extends DamageEventSystem {
        @Override public Query<EntityStore> getQuery(){return EntityStatMap.getComponentType();}
        @Override public Set<Dependency<EntityStore>> getDependencies(){return Set.of(
                new SystemGroupDependency<>(Order.AFTER,DamageModule.get().getFilterDamageGroup()),
                new SystemDependency<>(Order.BEFORE,DamageSystems.ApplyDamage.class));}
        @Override public void handle(int index,ArchetypeChunk<EntityStore> chunk,Store<EntityStore> store,
                                     CommandBuffer<EntityStore> buffer,Damage damage){
            try(var rpgTickSpan=com.inigmasgames.hytalerpg.diagnostics.NativeRpgTickMetrics.enter(
                    store,com.inigmasgames.hytalerpg.diagnostics.NativeRpgTickMetrics.Phase.DAMAGE)){
            if(HytaleDamageAdapter.metadata(damage)!=null||damage.isCancelled()
                    ||!(damage.getSource() instanceof Damage.EntitySource)
                    ||damage.getIfPresentMetaObject(Damage.INTERACTION_TYPE)==null)return;
            var hp=chunk.getComponent(index,EntityStatMap.getComponentType()).get(DefaultEntityStatTypes.getHealth());
            if(hp!=null)damage.putMetaObject(RAW_BEFORE,(double)hp.get());
            }
        }
    }
    /** Reads the same native Damage after Apply. Does not invent a managed gear hit. */
    public final class RawAfter extends DamageEventSystem {
        @Override public Query<EntityStore> getQuery(){return Query.and(EntityStatMap.getComponentType(),UUIDComponent.getComponentType());}
        @Override public Set<Dependency<EntityStore>> getDependencies(){return Set.of(
                new SystemDependency<>(Order.AFTER,DamageSystems.ApplyDamage.class),
                new SystemGroupDependency<>(Order.BEFORE,DamageModule.get().getInspectDamageGroup()));}
        @Override public void handle(int index,ArchetypeChunk<EntityStore> chunk,Store<EntityStore> store,
                                     CommandBuffer<EntityStore> buffer,Damage damage){
            try(var rpgTickSpan=com.inigmasgames.hytalerpg.diagnostics.NativeRpgTickMetrics.enter(
                    store,com.inigmasgames.hytalerpg.diagnostics.NativeRpgTickMetrics.Phase.DAMAGE)){
            if(damage.isCancelled()||HytaleDamageAdapter.metadata(damage)!=null)return;
            var before=damage.getIfPresentMetaObject(RAW_BEFORE);
            if(before==null||!(damage.getSource() instanceof Damage.EntitySource source)
                    ||source.getRef()==null||!source.getRef().isValid())return;
            var hp=chunk.getComponent(index,EntityStatMap.getComponentType()).get(DefaultEntityStatTypes.getHealth());
            if(hp==null)return;
            double after=hp.get(),loss=Math.min(Math.max(0,before),Math.max(0,before-after));
            if(loss<=0)return;
            var target=chunk.getReferenceTo(index);
            facts.rawIncoming(damage,before,after,target,source.getRef(),buffer).ifPresent(incoming->{
                var id=chunk.getComponent(index,UUIDComponent.getComponentType()).getUuid();
                var attacker=buffer.getComponent(source.getRef(),UUIDComponent.getComponentType());
                if(!incoming.wearer().equals(id)||incoming.actualHpLoss()<loss||attacker==null
                        ||!incoming.attacker().equals(attacker.getUuid())
                        ||!incoming.world().equals(store.getExternalData().getWorld().getWorldConfig().getUuid()))
                    throw new IllegalArgumentException("FOREIGN_RAW_SIGNATURE_REFLECTION");
                runtime.received(incoming.world(),incoming.wearer(),incoming.attacker(),incoming.root(),incoming.contact(),
                        incoming.validEquipment(),incoming.directMelee(),incoming.hostile(),incoming.reflected(),
                        incoming.blocked(),incoming.actualHpLoss(),incoming.protectedAttacker(),
                        System.nanoTime()/1e9,nativePort.at(store,target));
            });
            }
        }
    }
    @Override public void observed(HytaleDamageLifecycleSystems.AppliedReceipt receipt,Ref<EntityStore> target,
                                   Ref<EntityStore> source,CommandBuffer<EntityStore> buffer){
        double now=System.nanoTime()/1e9;
        facts.outgoing(receipt,target,source,buffer).ifPresent(contact->{
            if(!contact.target().equals(receipt.targetId())||contact.actualHpLoss()<receipt.actualHealthLoss()
                    ||!contact.owner().equals(receipt.metadata().actorId())
                    ||!contact.root().equals(receipt.metadata().rootCastId())
                    ||receipt.gearHit()==null||!contact.item().equals(receipt.gearHit().hit().itemId())
                    ||!contact.contact().equals(receipt.gearHit().contactId())
                    ||!contact.world().equals(buffer.getStore().getExternalData().getWorld().getWorldConfig().getUuid()))
                throw new IllegalArgumentException("FOREIGN_SIGNATURE_CONTACT");
            runtime.applied(contact,now,nativePort.at(buffer.getStore(),source));
        });
        facts.incoming(receipt,target,source,buffer).ifPresent(incoming->{
            var attacker=source==null?null:buffer.getComponent(source,UUIDComponent.getComponentType());
            if(!incoming.wearer().equals(receipt.targetId())||incoming.actualHpLoss()<receipt.actualHealthLoss())
                throw new IllegalArgumentException("FOREIGN_SIGNATURE_REFLECTION");
            if(attacker==null||!incoming.attacker().equals(attacker.getUuid())
                    ||!incoming.world().equals(buffer.getStore().getExternalData().getWorld().getWorldConfig().getUuid()))
                throw new IllegalArgumentException("FOREIGN_SIGNATURE_REFLECTION");
            runtime.received(incoming.world(),incoming.wearer(),incoming.attacker(),incoming.root(),incoming.contact(),
                    incoming.validEquipment(),incoming.directMelee(),incoming.hostile(),incoming.reflected(),
                    incoming.blocked(),incoming.actualHpLoss(),incoming.protectedAttacker(),now,nativePort.at(buffer.getStore(),target));
        });
    }
}
