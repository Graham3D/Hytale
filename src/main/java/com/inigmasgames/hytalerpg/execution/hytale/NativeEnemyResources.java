package com.inigmasgames.hytalerpg.execution.hytale;

import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatMap;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import com.inigmasgames.hytalerpg.combat.RpgCombatKernel;
import com.inigmasgames.hytalerpg.combat.hytale.EntityStatResourcePort;
import com.inigmasgames.hytalerpg.enemies.EnemyResourceEffects;
import java.util.Objects;
import java.util.function.Consumer;

/** Post-leaf resource delivery through real native EntityStatMap values and the shared mutation gate. */
public final class NativeEnemyResources implements Consumer<NativeEnemyActions.Delivery> {
    private final RpgCombatKernel kernel;
    private final HytaleBossBarTracker bosses;
    private final EnemyResourceEffects effects;
    private final com.inigmasgames.hytalerpg.combat.diagnostics.CombatTrace trace;
    public NativeEnemyResources(RpgCombatKernel kernel,HytaleBossBarTracker bosses,com.inigmasgames.hytalerpg.combat.diagnostics.CombatTrace trace){
        this.kernel=Objects.requireNonNull(kernel);this.bosses=Objects.requireNonNull(bosses);
        this.trace=Objects.requireNonNull(trace);
        effects=new EnemyResourceEffects(kernel.resources());
    }
    public EnemyResourceEffects effects(){return effects;}
    @Override public void accept(NativeEnemyActions.Delivery delivery){
        if(!delivery.store().isInThread())throw new IllegalStateException("ENEMY_RESOURCE_WRONG_WORLD_THREAD");
        if(!current(delivery))return;
        var store=delivery.store();long now=System.nanoTime();
        var sourceStats=store.getComponent(delivery.actor(),EntityStatMap.getComponentType());
        var victimStats=store.getComponent(delivery.victim(),EntityStatMap.getComponentType());
        if(victimStats!=null)observe(delivery,"ME-013",effects.manaBurn(delivery.descriptor(),delivery.hit(),new EntityStatResourcePort(victimStats),now,
                ()->current(delivery)&&HytaleSupportSystem.alive(store,delivery.victim())));
        // A killing hit can leech; only the source must still be alive at the native Health write.
        if(sourceStats!=null)observe(delivery,"ME-016",effects.vampiric(delivery.descriptor(),delivery.hit(),new EntityStatResourcePort(sourceStats),now,
                ()->current(delivery)&&HytaleSupportSystem.alive(store,delivery.actor())));
    }
    private void observe(NativeEnemyActions.Delivery delivery,String affix,EnemyResourceEffects.Result result){
        if(result.gate().equals("INELIGIBLE"))return;
        var identity=delivery.hit().offense().identity();
        trace.emit(delivery.descriptor().entityId(),result.actual()>0
                ?com.inigmasgames.hytalerpg.diagnostics.RpgTraceEventType.RESOURCE_RECOVERY
                :com.inigmasgames.hytalerpg.diagnostics.RpgTraceEventType.RESOURCE_REJECTED,
                new com.inigmasgames.hytalerpg.combat.diagnostics.CombatTrace.Context(identity.rootId(),"",identity.authoredTickId()),
                java.util.Map.of("sourceKind","MONSTER_AFFIX","affixId",affix,"generation",delivery.descriptor().encounterGeneration(),
                        "logicalActorId",delivery.descriptor().logicalActorId(),"targetId",delivery.hit().victim(),"gate",result.gate(),
                        "allowed",result.allowed(),"actual",result.actual(),"operation",affix.equals("ME-013")?"MANA_DEBIT":"HEALTH_CREDIT"));
    }
    private boolean current(NativeEnemyActions.Delivery delivery){
        var store=delivery.store();var actor=delivery.actor();var victim=delivery.victim();
        if(!delivery.current().getAsBoolean()||actor==null||victim==null||!actor.isValid()||!victim.isValid()
                ||actor.getStore()!=store||victim.getStore()!=store||!store.isInThread()
                ||!store.getExternalData().getWorld().getWorldConfig().getUuid().equals(delivery.descriptor().worldId()))return false;
        var sourceId=store.getComponent(actor,UUIDComponent.getComponentType());var victimId=store.getComponent(victim,UUIDComponent.getComponentType());
        return sourceId!=null&&victimId!=null&&sourceId.getUuid().equals(delivery.descriptor().entityId())
                &&victimId.getUuid().equals(delivery.hit().victim())&&store.getComponent(actor,NPCEntity.getComponentType())!=null
                &&HytaleAreaQueries.hostile(store,actor,victim)&&!SupportNativeEffects.control(store,victim,bosses).protectedEntity()
                &&kernel.statuses().allowsExternalMutation(sourceId.getUuid(),victimId.getUuid());
    }
}
