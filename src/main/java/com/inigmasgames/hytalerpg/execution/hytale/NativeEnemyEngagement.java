package com.inigmasgames.hytalerpg.execution.hytale;

import com.hypixel.hytale.component.*;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.tick.EntityTickingSystem;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import com.hypixel.hytale.server.npc.role.support.MarkedEntitySupport;
import com.inigmasgames.hytalerpg.enemies.*;
import com.inigmasgames.hytalerpg.execution.support.FiniteSupportEffects;
import java.util.*;

/** ME-019 uses the native locked hostile target and one saved actor clock. */
public final class NativeEnemyEngagement extends EntityTickingSystem<EntityStore> {
    private final HytaleDifficultyCombat combat;
    private final FiniteSupportEffects effects;
    private final EnemyNativeBindings bindings;
    private final EnemyBalance balance;
    public NativeEnemyEngagement(HytaleDifficultyCombat combat,FiniteSupportEffects effects,
            EnemyNativeBindings bindings,EnemyBalance balance){
        this.combat=Objects.requireNonNull(combat);this.effects=Objects.requireNonNull(effects);
        this.bindings=Objects.requireNonNull(bindings);this.balance=Objects.requireNonNull(balance);
    }
    @Override public Query<EntityStore> getQuery(){return Query.and(EnemyEngagementClock.getComponentType(),NPCEntity.getComponentType());}
    @Override public boolean isParallel(int size,int tasks){return false;}
    @Override public void tick(float dt,int index,ArchetypeChunk<EntityStore> chunk,Store<EntityStore> store,
            CommandBuffer<EntityStore> buffer){
            try(var rpgTickSpan=com.inigmasgames.hytalerpg.diagnostics.NativeRpgTickMetrics.enter(store,com.inigmasgames.hytalerpg.diagnostics.NativeRpgTickMetrics.Phase.LIFECYCLE)){
        var ref=chunk.getReferenceTo(index);var clock=chunk.getComponent(index,EnemyEngagementClock.getComponentType());
        var id=chunk.getComponent(index,UUIDComponent.getComponentType());
        if(id==null||!id.getUuid().equals(clock.state().nativeEntity()))throw new IllegalStateException("ENEMY_ENGAGEMENT_NATIVE_IDENTITY");
        var world=store.getExternalData().getWorld().getWorldConfig().getUuid();
        var current=combat.enemyState(world,id.getUuid()).orElse(null);
        if(current==null)return; // A saved clock cannot create or reattach a birth.
        var actor=current.descriptor();clock.requireActor(actor);
        var frenzy=actor.own(EnemyAffixRegistry.Operator.FRENZIED).orElseThrow();
        if(frenzy.value("cycleMs")!=EnemyEngagementClock.CYCLE_MILLIS)
            throw new IllegalStateException("ENEMY_ENGAGEMENT_CYCLE_REVISION");
        long activeWindowStart=(long)frenzy.value("activeWindowStartMs");
        var pack=combat.enemyPack(world,id.getUuid()).orElse(null);
        if(pack==null||pack.state()==EnemyPackRecord.State.RESERVED||pack.state()==EnemyPackRecord.State.STAGED)return;
        if(pack.state()==EnemyPackRecord.State.ABORTED||pack.state()==EnemyPackRecord.State.DEFEATED)return;
        var npc=chunk.getComponent(index,NPCEntity.getComponentType());
        if(!npc.getRoleName().equals(actor.nativeRoleId()))throw new IllegalStateException("ENEMY_ENGAGEMENT_NATIVE_ROLE");
        var role=bindings.role(actor.nativeRoleId()).orElseThrow(()->new IllegalStateException("ENEMY_ENGAGEMENT_UNCERTIFIED_ROLE"));
        if(!actor.nativeBindingRevision().equals(bindings.revision()))
            throw new IllegalStateException("ENEMY_ENGAGEMENT_UNAVAILABLE_REVISION");
        var balance=EnemyBalance.forRevision(actor.balanceRevision());
        var marked=store.getComponent(ref,MarkedEntitySupport.getComponentType());
        var target=marked==null?null:marked.getMarkedEntityRef(MarkedEntitySupport.DEFAULT_TARGET_SLOT);
        boolean engaged=target!=null&&target.isValid()&&HytaleSupportSystem.alive(store,ref)
                &&HytaleSupportSystem.alive(store,target)&&HytaleAreaQueries.hostile(store,ref,target);
        if(!clock.advance(dt,engaged,pack.state()==EnemyPackRecord.State.SUSPENDED,activeWindowStart))return;
        var capabilities=role.capabilities();
        var next=EnemyAffixSnapshot.resolve(actor,balance,pack,clock.state().phaseMillis(),
                capabilities.contains(EnemyAffixRegistry.Capability.MOBILE),
                capabilities.contains(EnemyAffixRegistry.Capability.RECOVERY_TIMELINE),effects,System.nanoTime()/1e9);
        combat.refreshEnemyEngagement(store,actor,next);
    
            }}
}
