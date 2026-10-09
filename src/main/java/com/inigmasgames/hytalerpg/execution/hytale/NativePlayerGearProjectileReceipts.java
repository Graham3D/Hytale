package com.inigmasgames.hytalerpg.execution.hytale;

import com.hypixel.hytale.component.*;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.entity.entities.ProjectileComponent;
import com.hypixel.hytale.server.core.modules.entity.damage.*;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatMap;
import com.hypixel.hytale.server.core.modules.entitystats.asset.DefaultEntityStatTypes;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.inigmasgames.hytalerpg.combat.hytale.HytaleDamageAdapter;
import com.inigmasgames.hytalerpg.combat.hytale.NativeDamageLeafInteraction;
import com.inigmasgames.hytalerpg.combat.hytale.NativeDamageLeafReceipts;
import com.inigmasgames.hytalerpg.gear.ManagedGearDamageInteraction;
import com.inigmasgames.hytalerpg.gear.ManagedGearProjectile;
import com.inigmasgames.hytalerpg.enemies.EnemyAffixRegistry;
import java.util.Objects;

/** Receipt-only adapter for the existing managed bow/crossbow native projectile and damage owners. */
public final class NativePlayerGearProjectileReceipts extends DamageEventSystem {
    private final NativeEnemyReflectiveReaction reaction;
    private final HytaleDifficultyCombat combat;
    public NativePlayerGearProjectileReceipts(NativeEnemyReflectiveReaction reaction,HytaleDifficultyCombat combat){
        this.reaction=Objects.requireNonNull(reaction);
        this.combat=Objects.requireNonNull(combat);
    }
    @Override public Query<EntityStore> getQuery(){return Query.any();}
    @Override public SystemGroup<EntityStore> getGroup(){return DamageModule.get().getGatherDamageGroup();}
    @Override public void handle(int index,ArchetypeChunk<EntityStore> chunk,Store<EntityStore> store,
            CommandBuffer<EntityStore> buffer,Damage damage){
            try(var rpgTickSpan=com.inigmasgames.hytalerpg.diagnostics.NativeRpgTickMetrics.enter(store,com.inigmasgames.hytalerpg.diagnostics.NativeRpgTickMetrics.Phase.DAMAGE)){
        if(!(damage.getSource() instanceof Damage.ProjectileSource source)
                ||HytaleDamageAdapter.metadata(damage)!=null)return;
        var projectile=source.getProjectile();
        if(projectile==null||!projectile.isValid()||projectile.getStore()!=store)return;
        var frozen=ManagedGearProjectile.snapshot(projectile,buffer);
        if(frozen==null||frozen.originalHitRoot()==null)return;
        var call=NativeDamageLeafInteraction.currentInvocation();
        var shooter=source.getRef();var victim=chunk.getReferenceTo(index);
        if(call==null||!(call.leaf() instanceof ManagedGearDamageInteraction)
                ||call.context().getEntity()!=projectile||call.context().getOwningEntity()!=shooter
                ||call.context().getCommandBuffer()!=buffer||call.context().getTargetEntity()!=victim
                ||shooter==null||!shooter.isValid()||shooter.getStore()!=store||shooter==victim
                ||buffer.getComponent(shooter,PlayerRef.getComponentType())==null)return;
        var projectileId=buffer.getComponent(projectile,UUIDComponent.getComponentType());
        var shooterId=buffer.getComponent(shooter,UUIDComponent.getComponentType());
        var victimId=chunk.getComponent(index,UUIDComponent.getComponentType());
        var nativeProjectile=buffer.getComponent(projectile,ProjectileComponent.getComponentType());
        if(projectileId==null||shooterId==null||victimId==null||nativeProjectile==null
                ||!shooterId.getUuid().equals(nativeProjectile.getCreatorUuid()))return;
        if(damage.getCause()!=DamageCause.PROJECTILE&&damage.getCause()!=DamageCause.PHYSICAL)return;
        var world=store.getExternalData().getWorld().getWorldConfig().getUuid();
        if(combat.enemyState(world,victimId.getUuid()).map(state->state.descriptor()
                .own(EnemyAffixRegistry.Operator.REFLECTIVE).isPresent()).orElse(false)==false)return;
        String receipt=PlayerOriginalHitIdentity.projectile(frozen.originalHitRoot(),
                projectileId.getUuid().toString(),victimId.getUuid());
        try{NativeDamageLeafReceipts.observeProjectile(damage,result->{
            if(result.cancelled()||!Double.isFinite(result.healthBefore())||!Double.isFinite(result.healthAfter())
                    ||result.healthBefore()<=result.healthAfter())return;
            var sourceStats=store.getComponent(shooter,EntityStatMap.getComponentType());
            var hp=sourceStats==null?null:sourceStats.get(DefaultEntityStatTypes.getHealth());
            if(hp==null||!Double.isFinite(hp.getMax())||hp.getMax()<=0)return;
            try{reaction.acceptPlayer(new NativeEnemyReflectiveReaction.PlayerHit(store,buffer,
                    shooter,victim,shooterId.getUuid(),victimId.getUuid(),receipt,
                    result.healthBefore()-result.healthAfter(),result.healthAfter(),hp.getMax()));}
            catch(RuntimeException ignored){} // Reflection cannot alter the original gear impact.
        });}
        catch(RuntimeException ignored){} // A missing receipt cannot alter native gear damage.
    
            }}
}
