package com.inigmasgames.hytalerpg.execution.hytale;

import com.hypixel.hytale.component.*;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.HolderSystem;
import com.hypixel.hytale.component.system.tick.EntityTickingSystem;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.modules.entity.damage.DeathComponent;
import com.hypixel.hytale.server.core.modules.entity.damage.DeathSystems;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import com.inigmasgames.hytalerpg.enemies.*;
import com.inigmasgames.hytalerpg.execution.support.FiniteSupportEffects;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/** Reprojects ME-023 through the existing support leases and dynamic enemy provider owner. */
public final class NativeEnemyAura extends EntityTickingSystem<EntityStore> {
    private final HytaleDifficultyCombat combat;
    private final HytaleSupportSystem support;
    private final FiniteSupportEffects effects;
    private final EnemyNativeBindings bindings;
    private final EnemyBalance balance;
    private final Map<EnemyActorIdentity.State,Double> nextPass=new ConcurrentHashMap<>();
    public NativeEnemyAura(HytaleDifficultyCombat combat,HytaleSupportSystem support,
            EnemyNativeBindings bindings,EnemyBalance balance){
        this.combat=Objects.requireNonNull(combat);this.support=Objects.requireNonNull(support);
        this.effects=support.runtime().finite();this.bindings=Objects.requireNonNull(bindings);
        this.balance=Objects.requireNonNull(balance);
    }
    @Override public Query<EntityStore> getQuery(){return Query.and(EnemyActorIdentity.getComponentType(),NPCEntity.getComponentType());}
    @Override public boolean isParallel(int size,int tasks){return false;}
    @Override public void tick(float dt,int index,ArchetypeChunk<EntityStore> chunk,Store<EntityStore> store,
            CommandBuffer<EntityStore> buffer){
            try(var rpgTickSpan=com.inigmasgames.hytalerpg.diagnostics.NativeRpgTickMetrics.enter(store,com.inigmasgames.hytalerpg.diagnostics.NativeRpgTickMetrics.Phase.SUPPORT)){
        var identity=chunk.getComponent(index,EnemyActorIdentity.getComponentType()).state();
        double now=System.nanoTime()/1e9;
        if(now<nextPass.getOrDefault(identity,0.0))return;
        var id=chunk.getComponent(index,UUIDComponent.getComponentType());
        if(id==null||!id.getUuid().equals(identity.nativeEntity()))throw new IllegalStateException("ENEMY_AURA_NATIVE_IDENTITY");
        var actor=combat.enemyState(identity.world(),identity.nativeEntity()).map(HytaleDifficultyCombat.EnemyState::descriptor).orElse(null);
        if(actor==null)return; // A saved actor cannot create a birth before durable rebind.
        identity.require(actor);
        var pack=combat.enemyPack(identity.world(),identity.nativeEntity()).orElse(null);
        if(pack==null)return;
        var leader=actor.own(EnemyAffixRegistry.Operator.AURA_ENCHANTED);
        if(leader.isPresent()){
            long interval=(long)leader.get().value("membershipUpdateMs");
            nextPass.put(identity,now+interval/1000.0);
            reconcile(store,actor,pack,now);
        }else if(actor.packRole()==EnemyDescriptor.PackRole.MINION
                &&(combat.enemyState(identity.world(),pack.birthRoster().stream()
                    .filter(m->m.logicalActorId().equals(pack.leaderId())).findFirst().orElseThrow().nativeEntityId())
                    .map(s->s.descriptor().own(EnemyAffixRegistry.Operator.AURA_ENCHANTED).isPresent()).orElse(false)
                    ||combat.enemyState(identity.world(),identity.nativeEntity())
                    .map(s->s.providers().auraMember()).orElse(false))){
            nextPass.put(identity,now+.250);
            refresh(store,actor,pack,now);
        }
    
            }}
    private void reconcile(Store<EntityStore> store,EnemyDescriptor leader,EnemyPackRecord pack,double now){
        var refs=new HashMap<UUID,Ref<EntityStore>>();
        for(var member:pack.birthRoster()){
            var ref=store.getExternalData().getRefFromUUID(member.nativeEntityId());
            if(ref==null||!ref.isValid()||ref.getStore()!=store)continue;
            var identity=store.getComponent(ref,EnemyActorIdentity.getComponentType());
            if(identity==null||!identity.state().world().equals(pack.worldId())
                    ||!identity.state().pack().equals(pack.packId())
                    ||identity.state().generation()!=pack.generation()
                    ||!identity.state().logicalActor().equals(member.logicalActorId())
                    ||!identity.state().nativeEntity().equals(member.nativeEntityId()))continue;
            refs.put(member.logicalActorId(),ref);
        }
        boolean active=pack.state()==EnemyPackRecord.State.GUARDED||pack.state()==EnemyPackRecord.State.RELEASED;
        support.projectEnemyAura(store,leader,pack,refs,now,()->active
                &&combat.enemyState(leader.worldId(),leader.entityId())
                    .map(HytaleDifficultyCombat.EnemyState::descriptor).filter(leader::equals).isPresent());
        for(var member:pack.birthRoster()){
            var state=combat.enemyState(pack.worldId(),member.nativeEntityId()).orElse(null);
            if(state!=null&&refs.containsKey(member.logicalActorId()))refresh(store,state.descriptor(),pack,now);
        }
    }
    /** Called after a durable pack publication/transition, before its first public display. */
    public void reconcilePack(Store<EntityStore> store,EnemyPackRecord pack){
        if(!store.isInThread()||!store.getExternalData().getWorld().getWorldConfig().getUuid().equals(pack.worldId()))
            throw new IllegalStateException("ENEMY_AURA_PACK_WORLD");
        if(pack.leaderId()==null)return; // Champion packs have no leader or aura relation.
        var leaderMember=pack.birthRoster().stream().filter(m->m.logicalActorId().equals(pack.leaderId())).findFirst().orElseThrow();
        var state=combat.enemyState(pack.worldId(),leaderMember.nativeEntityId()).orElse(null);
        if(state!=null&&state.descriptor().own(EnemyAffixRegistry.Operator.AURA_ENCHANTED).isPresent()){
            reconcile(store,state.descriptor(),pack,System.nanoTime()/1e9);
        }else{
            effects.withdrawAuraActor(pack.worldId(),pack.leaderId(),pack.generation());
            double now=System.nanoTime()/1e9;
            for(var member:pack.birthRoster()){
                var live=combat.enemyState(pack.worldId(),member.nativeEntityId()).orElse(null);
                if(live!=null&&live.providers().auraMember())refresh(store,live.descriptor(),pack,now);
            }
        }
    }
    private void refresh(Store<EntityStore> store,EnemyDescriptor actor,EnemyPackRecord pack,double now){
        var ref=store.getExternalData().getRefFromUUID(actor.entityId());
        if(ref==null||!ref.isValid()||ref.getStore()!=store||!HytaleSupportSystem.alive(store,ref))return;
        var identity=store.getComponent(ref,EnemyActorIdentity.getComponentType());
        if(identity==null||!identity.state().equals(EnemyActorIdentity.State.of(actor)))return;
        var role=bindings.requireActorRole(actor);
        var clock=store.getComponent(ref,EnemyEngagementClock.getComponentType());
        long phase=clock==null?0:clock.state().phaseMillis();
        var capabilities=role.capabilities();
        var next=EnemyAffixSnapshot.resolve(actor,EnemyBalance.forRevision(actor.balanceRevision()),pack,phase,
                capabilities.contains(EnemyAffixRegistry.Capability.MOBILE),
                capabilities.contains(EnemyAffixRegistry.Capability.RECOVERY_TIMELINE),effects,now);
        combat.refreshEnemyEngagement(store,actor,next);
    }
    private void withdraw(Store<EntityStore> store,EnemyActorIdentity.State identity){
        if(!store.isInThread()||!store.getExternalData().getWorld().getWorldConfig().getUuid().equals(identity.world()))
            throw new IllegalStateException("ENEMY_AURA_WITHDRAW_WORLD");
        nextPass.remove(identity);
        effects.withdrawAuraActor(identity.world(),identity.logicalActor(),identity.generation());
        var pack=combat.enemyPack(identity.world(),identity.nativeEntity()).orElse(null);
        if(pack==null||pack.leaderId()==null)return;
        var leader=pack.birthRoster().stream().filter(m->m.logicalActorId().equals(pack.leaderId())).findFirst().orElseThrow();
        boolean auraPack=combat.enemyState(pack.worldId(),leader.nativeEntityId())
                .map(s->s.descriptor().own(EnemyAffixRegistry.Operator.AURA_ENCHANTED).isPresent()).orElse(false)
                ||pack.birthRoster().stream().anyMatch(member->combat.enemyState(pack.worldId(),member.nativeEntityId())
                    .map(s->s.providers().auraMember()).orElse(false));
        if(!auraPack)return;
        double now=System.nanoTime()/1e9;
        for(var member:pack.birthRoster()){
            if(member.logicalActorId().equals(identity.logicalActor()))continue;
            var state=combat.enemyState(pack.worldId(),member.nativeEntityId()).orElse(null);
            if(state!=null)refresh(store,state.descriptor(),pack,now);
        }
    }
    /** Native death changes membership on the same world owner before any later action snapshot. */
    public static final class Death extends DeathSystems.OnDeathSystem {
        private final NativeEnemyAura aura;
        public Death(NativeEnemyAura aura){this.aura=Objects.requireNonNull(aura);}
        @Override public Query<EntityStore> getQuery(){return EnemyActorIdentity.getComponentType();}
        @Override public void onComponentAdded(Ref<EntityStore> ref,DeathComponent death,Store<EntityStore> store,
                CommandBuffer<EntityStore> buffer){
            var identity=store.getComponent(ref,EnemyActorIdentity.getComponentType());
            if(identity!=null)aura.withdraw(store,identity.state());
        }
    }
    /** Unload/despawn has the same finite-lease cleanup; birth selector remains durable. */
    public static final class Removal extends HolderSystem<EntityStore> {
        private final NativeEnemyAura aura;
        public Removal(NativeEnemyAura aura){this.aura=Objects.requireNonNull(aura);}
        @Override public Query<EntityStore> getQuery(){return EnemyActorIdentity.getComponentType();}
        @Override public void onEntityAdd(Holder<EntityStore> holder,AddReason reason,Store<EntityStore> store){}
        @Override public void onEntityRemoved(Holder<EntityStore> holder,RemoveReason reason,Store<EntityStore> store){
            var identity=holder.getComponent(EnemyActorIdentity.getComponentType());
            if(identity!=null)aura.withdraw(store,identity.state());
        }
    }
}
