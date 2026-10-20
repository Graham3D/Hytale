package com.inigmasgames.hytalerpg.execution.hytale;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.entity.damage.DamageCause;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatMap;
import com.hypixel.hytale.server.core.modules.entitystats.asset.DefaultEntityStatTypes;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.inigmasgames.hytalerpg.combat.damage.MonsterAffixSource;
import com.inigmasgames.hytalerpg.combat.hytale.HytaleDamageAdapter;
import com.inigmasgames.hytalerpg.combat.hytale.HytaleDamageMetadata;
import com.inigmasgames.hytalerpg.enemies.*;
import com.inigmasgames.hytalerpg.execution.support.SecondaryDamageAttempt;
import com.inigmasgames.hytalerpg.progress.RewardIntent;
import java.util.*;
import java.util.function.Consumer;
import java.util.function.BooleanSupplier;

/** Completed native ME strike -> one grouped actual-loss offer -> later native Gather. */
public final class NativeEnemyReflectiveReaction implements Consumer<NativeEnemyActions.Delivery> {
    public record PlayerHit(Store<EntityStore> store,CommandBuffer<EntityStore> buffer,
            Ref<EntityStore> attacker,Ref<EntityStore> victim,UUID attackerId,UUID victimId,
            String receipt,double actualHealthLoss,double defenderHealthAfter,double attackerMaxHealth){}
    private record Original(Store<EntityStore> store,CommandBuffer<EntityStore> buffer,
            Ref<EntityStore> attacker,Ref<EntityStore> victim,UUID attackerId,EnemyDescriptor defender,
            String receipt,double loss,double healthAfter,double attackerMax,BooleanSupplier current){}
    private final HytaleDifficultyCombat combat;
    private final EnemyWorldAdmission admission;
    private final NativeEnemyActions actions;
    private final HytaleBossBarTracker bosses;
    private final EnemyReflectiveEffects effects;
    public NativeEnemyReflectiveReaction(HytaleDifficultyCombat combat,EnemyWorldAdmission admission,
            NativeEnemyActions actions,HytaleBossBarTracker bosses,EnemyReflectiveEffects effects){
        this.combat=Objects.requireNonNull(combat);this.admission=Objects.requireNonNull(admission);
        this.actions=Objects.requireNonNull(actions);this.bosses=Objects.requireNonNull(bosses);
        this.effects=Objects.requireNonNull(effects);
    }
    @Override public void accept(NativeEnemyActions.Delivery delivery){
        if(!delivery.store().isInThread())throw new IllegalStateException("ENEMY_REFLECTIVE_WORLD_THREAD");
        UUID world=delivery.descriptor().worldId();
        var defenderState=combat.enemyState(world,delivery.hit().victim()).orElse(null);
        if(defenderState==null||defenderState.descriptor().own(EnemyAffixRegistry.Operator.REFLECTIVE).isEmpty())return;
        var defender=defenderState.descriptor();
        if(!current(delivery,defender))return;
        var store=delivery.store();
        var defenderStats=store.getComponent(delivery.victim(),EntityStatMap.getComponentType());
        var attackerStats=store.getComponent(delivery.actor(),EntityStatMap.getComponentType());
        var defenderHealth=defenderStats==null?null:defenderStats.get(DefaultEntityStatTypes.getHealth());
        var attackerHealth=attackerStats==null?null:attackerStats.get(DefaultEntityStatTypes.getHealth());
        if(defenderHealth==null||attackerHealth==null||!Double.isFinite(defenderHealth.get())
                ||!Double.isFinite(attackerHealth.getMax())||attackerHealth.getMax()<=0)return;
        var id=delivery.hit().offense().identity();
        String receipt=RewardIntent.digest(new com.google.gson.Gson().toJson(List.of("me.reflective/original",
                id.worldId(),id.actorId(),delivery.hit().offense().generation(),id.rootId(),
                id.executionId(),id.authoredTickId(),delivery.hit().victim())));
        queue(new Original(store,delivery.buffer(),delivery.actor(),delivery.victim(),
                delivery.descriptor().entityId(),defender,receipt,delivery.hit().actualHealthLoss(),
                defenderHealth.get(),attackerHealth.getMax(),()->current(delivery,defender)));
    }
    /** Existing player native weapon owner supplies one completed original hit after Apply. */
    public void acceptPlayer(PlayerHit hit){
        if(!hit.store().isInThread())throw new IllegalStateException("ENEMY_REFLECTIVE_PLAYER_WORLD_THREAD");
        var world=hit.store().getExternalData().getWorld().getWorldConfig().getUuid();
        var state=combat.enemyState(world,hit.victimId()).orElse(null);
        if(state==null||state.descriptor().own(EnemyAffixRegistry.Operator.REFLECTIVE).isEmpty())return;
        var defender=state.descriptor();
        if(!currentPlayer(hit,defender))return;
        queue(new Original(hit.store(),hit.buffer(),hit.attacker(),hit.victim(),hit.attackerId(),defender,
                hit.receipt(),hit.actualHealthLoss(),hit.defenderHealthAfter(),hit.attackerMaxHealth(),
                ()->currentPlayer(hit,defender)));
    }
    private void queue(Original hit){
        UUID world=hit.defender().worldId();
        java.util.function.Consumer<Store<EntityStore>> laterTask=later->{
            try{
                if(later!=hit.store()||!hit.current().getAsBoolean())return;
                var defender=hit.defender();
                var result=effects.offer(defender,hit.attackerId(),hit.receipt(),hit.loss(),
                        hit.healthAfter(),hit.attackerMax(),distance(later,hit.attacker(),hit.victim()),
                        true,System.nanoTime(),hit.current(),raw->{
                            if(raw<=0||raw>Float.MAX_VALUE)return 0;
                            var provenance=new MonsterAffixSource(defender.worldId(),defender.logicalActorId(),
                                    defender.entityId(),defender.encounterGeneration(),"ME-026",defender.balanceRevision(),
                                    hit.receipt(),"reflective");
                            var stats=later.getComponent(hit.attacker(),EntityStatMap.getComponentType());
                            var hp=stats==null?null:stats.get(DefaultEntityStatTypes.getHealth());
                            var attempt=SecondaryDamageAttempt.once(()->hp==null?Double.NaN:hp.get(),
                                    ()->new HytaleDamageAdapter().applyResolved(hit.attacker(),later,null,DamageCause.PHYSICAL,
                                            HytaleDamageMetadata.monster(provenance,"reflective/"+hit.receipt(),
                                                    HytaleDamageMetadata.Origin.REFLECTED,raw),raw));
                            if(attempt.failure()!=null)throw attempt.failure();
                            return raw;
                        });
                if(result.gate()==com.inigmasgames.hytalerpg.combat.resource.RollingReceiptBudget.Gate.WRITE_UNCERTAIN)
                    quarantine(world);
            }catch(RuntimeException uncertain){quarantine(world);throw uncertain;}
        };
        try{
            if(hit.buffer()!=null)hit.buffer().run(laterTask);
            else hit.store().getExternalData().getWorld().execute(()->laterTask.accept(hit.store()));
        }catch(RuntimeException uncertain){quarantine(world);throw uncertain;}
    }
    private boolean currentPlayer(PlayerHit hit,EnemyDescriptor defender){
        var store=hit.store();var attacker=hit.attacker();var victim=hit.victim();
        if(!store.isInThread()||!admission.admits(defender.worldId())
                ||attacker==null||victim==null||!attacker.isValid()||!victim.isValid()
                ||attacker.getStore()!=store||victim.getStore()!=store)return false;
        var sourceId=store.getComponent(attacker,UUIDComponent.getComponentType());
        var targetId=store.getComponent(victim,UUIDComponent.getComponentType());
        var bound=combat.enemyState(defender.worldId(),defender.entityId()).orElse(null);
        var pack=combat.enemyPack(defender.worldId(),defender.entityId()).orElse(null);
        var affix=defender.own(EnemyAffixRegistry.Operator.REFLECTIVE).orElse(null);
        return sourceId!=null&&sourceId.getUuid().equals(hit.attackerId())
                &&targetId!=null&&targetId.getUuid().equals(hit.victimId())
                &&bound!=null&&bound.descriptor().equals(defender)
                &&pack!=null&&pack.economicAdmission(defender.logicalActorId())
                &&affix!=null
                &&HytaleSupportSystem.alive(store,attacker)
                &&HytaleAreaQueries.hostile(store,attacker,victim)
                &&!SupportNativeEffects.control(store,attacker,bosses).protectedEntity();
    }
    private boolean current(NativeEnemyActions.Delivery delivery,EnemyDescriptor defender){
        var store=delivery.store();var attacker=delivery.actor();var victim=delivery.victim();
        if(!store.isInThread()||!admission.admits(defender.worldId())||!delivery.current().getAsBoolean()
                ||attacker==null||victim==null||!attacker.isValid()||!victim.isValid()
                ||attacker.getStore()!=store||victim.getStore()!=store)return false;
        var sourceId=store.getComponent(attacker,UUIDComponent.getComponentType());
        var targetId=store.getComponent(victim,UUIDComponent.getComponentType());
        var bound=combat.enemyState(defender.worldId(),defender.entityId()).orElse(null);
        var pack=combat.enemyPack(defender.worldId(),defender.entityId()).orElse(null);
        var affix=defender.own(EnemyAffixRegistry.Operator.REFLECTIVE).orElse(null);
        return sourceId!=null&&sourceId.getUuid().equals(delivery.descriptor().entityId())
                &&targetId!=null&&targetId.getUuid().equals(defender.entityId())
                &&bound!=null&&bound.descriptor().equals(defender)
                &&pack!=null&&pack.economicAdmission(defender.logicalActorId())
                &&affix!=null&&distance(store,attacker,victim)<=affix.value("maximumOriginDistanceMeters")
                &&HytaleSupportSystem.alive(store,attacker)&&HytaleSupportSystem.alive(store,victim)
                &&HytaleAreaQueries.hostile(store,attacker,victim)
                &&!SupportNativeEffects.control(store,attacker,bosses).protectedEntity();
    }
    private static double distance(Store<EntityStore> store,Ref<EntityStore> a,Ref<EntityStore> b){
        var first=store.getComponent(a,TransformComponent.getComponentType());
        var second=store.getComponent(b,TransformComponent.getComponentType());
        if(first==null||second==null)return Double.POSITIVE_INFINITY;
        return Math.sqrt(first.getPosition().distanceSquared(second.getPosition().x(),
                second.getPosition().y(),second.getPosition().z()));
    }
    private void quarantine(UUID world){admission.failClosed(world);combat.quarantineEnemyWorld(world);actions.quarantineWorld(world);}
}
