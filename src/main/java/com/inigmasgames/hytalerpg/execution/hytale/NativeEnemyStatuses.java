package com.inigmasgames.hytalerpg.execution.hytale;

import com.inigmasgames.hytalerpg.enemies.EnemyStatusEffects;
import com.inigmasgames.hytalerpg.combat.status.PeriodicContext;
import com.inigmasgames.hytalerpg.execution.support.FiniteSupportEffects;
import java.util.*;
import java.util.function.Consumer;

/** Completed original native hit -> existing stack, periodic and finite-stat owners. */
public final class NativeEnemyStatuses implements Consumer<NativeEnemyActions.Delivery> {
    private final HytaleSkillExecutionSystem statuses;private final HytaleSupportSystem support;private final HytaleDifficultyCombat actors;
    private final com.inigmasgames.hytalerpg.combat.RpgCombatKernel kernel;private final HytaleBossBarTracker bosses;
    private final com.inigmasgames.hytalerpg.combat.diagnostics.CombatTrace trace;
    private final EnemyStatusEffects effects=new EnemyStatusEffects();
    public NativeEnemyStatuses(HytaleSkillExecutionSystem statuses,HytaleSupportSystem support,HytaleDifficultyCombat actors,
            com.inigmasgames.hytalerpg.combat.RpgCombatKernel kernel,HytaleBossBarTracker bosses,com.inigmasgames.hytalerpg.combat.diagnostics.CombatTrace trace){
        this.statuses=Objects.requireNonNull(statuses);this.support=Objects.requireNonNull(support);this.actors=Objects.requireNonNull(actors);
        this.kernel=Objects.requireNonNull(kernel);this.bosses=Objects.requireNonNull(bosses);this.trace=Objects.requireNonNull(trace);
    }
    public EnemyStatusEffects effects(){return effects;}
    private EnemyStatusEffects.Target target(NativeEnemyActions.Delivery delivery){return actors.enemyState(delivery.descriptor().worldId(),delivery.hit().victim())
            .map(state->new EnemyStatusEffects.Target(state.descriptor().logicalActorId(),state.descriptor().encounterGeneration()))
            .orElseGet(()->new EnemyStatusEffects.Target(delivery.hit().victim(),0));}
    @Override public void accept(NativeEnemyActions.Delivery delivery){
        if(!delivery.store().isInThread())throw new IllegalStateException("ENEMY_STATUS_WRONG_WORLD_THREAD");
        if(!delivery.current().getAsBoolean()||delivery.hit().actualHealthLoss()<=0
                ||!kernel.statuses().allowsExternalMutation(delivery.descriptor().entityId(),delivery.hit().victim()))return;
        var store=delivery.store();var actor=delivery.actor();var victim=delivery.victim();
        if(actor==null||victim==null||!actor.isValid()||!victim.isValid()
                ||!HytaleAreaQueries.hostile(store,actor,victim)
                ||SupportNativeEffects.control(store,victim,bosses).protectedEntity())return;
        var controller=store.getComponent(victim,com.hypixel.hytale.server.core.entity.effect.EffectControllerComponent.getComponentType());
        if(controller==null)return;
        for(var affix:delivery.descriptor().ownAffixes()){
            String asset=switch(affix.affixId()){
                case "ME-014"->"RPG_ME_Cursed_"+tier(affix.value("outgoingDirectReduction"),.10,.125,.15);
                case "ME-025"->"RPG_ME_Armor_Broken_"+tier(affix.value("defenseReduction"),.15,.20,.25);
                default->null;
            };
            if(asset==null||asset.endsWith("_0"))continue;
            var effect=com.hypixel.hytale.server.core.asset.type.entityeffect.config.EntityEffect.getAssetMap().getAsset(asset);
            if(effect!=null)controller.addEffect(victim,effect,4f,
                    com.hypixel.hytale.server.core.asset.type.entityeffect.config.OverlapBehavior.OVERWRITE,store,actor);
        }
    }
    private static int tier(double value,double normal,double nightmare,double hell){
        if(Math.abs(value-normal)<1e-6)return 1;
        if(Math.abs(value-nightmare)<1e-6)return 2;
        if(Math.abs(value-hell)<1e-6)return 3;
        return 0;
    }
    private void knockback(NativeEnemyActions.Delivery d,EnemyStatusEffects.Package packet,java.util.function.BooleanSupplier current,java.util.function.BooleanSupplier claim){
        var request=com.inigmasgames.hytalerpg.enemies.EnemyDisplacementMerge.resolve(d.nativeImpulse(),0,packet.horizontalMeters());
        // The original leaf already submitted this native owner. Never write, scale or cancel its vertical/velocity values.
        if(request.preserveNativeImpulse())return;
        var store=d.store();var target=d.victim();var source=d.actor();
        java.util.function.BooleanSupplier eligible=()->current.getAsBoolean()&&source!=null&&target!=null&&source.isValid()&&target.isValid()
                &&source.getStore()==store&&target.getStore()==store&&HytaleSupportSystem.alive(store,source)&&HytaleSupportSystem.alive(store,target)
                &&store.getExternalData().getWorld().getWorldConfig().getUuid().equals(d.descriptor().worldId())
                &&sameActor(store,source,d.descriptor().entityId())&&sameActor(store,target,d.hit().victim())
                &&HytaleAreaQueries.hostile(store,source,target)&&kernel.statuses().allowsExternalMutation(d.descriptor().entityId(),d.hit().victim());
        if(!eligible.getAsBoolean())return;
        var control=SupportNativeEffects.control(store,target,bosses);if(control.displacementMultiplier()<=0)return;
        var origin=store.getComponent(source,com.hypixel.hytale.server.core.modules.entity.component.TransformComponent.getComponentType());
        var transform=store.getComponent(target,com.hypixel.hytale.server.core.modules.entity.component.TransformComponent.getComponentType());
        var bounds=store.getComponent(target,com.hypixel.hytale.server.core.modules.entity.component.BoundingBox.getComponentType());
        if(origin==null||transform==null||bounds==null)return;
        var npc=store.getComponent(target,com.hypixel.hytale.server.npc.entities.NPCEntity.getComponentType());
        if(npc==null&&store.getComponent(target,com.hypixel.hytale.server.core.entity.entities.Player.getComponentType())==null)return;
        var movement=store.getComponent(target,com.hypixel.hytale.server.core.entity.movement.MovementStatesComponent.getComponentType());
        boolean grounded=npc!=null?npc.getRole()!=null&&npc.getRole().isOnGround():movement!=null&&movement.getMovementStates().onGround;
        if(npc!=null&&(npc.getRole()==null||npc.getRole().getKnockbackScale()<=0))return;
        var start=new com.inigmasgames.hytalerpg.execution.math.Vec3(transform.getPosition().x(),transform.getPosition().y(),transform.getPosition().z());
        var center=new com.inigmasgames.hytalerpg.execution.math.Vec3(origin.getPosition().x(),origin.getPosition().y(),origin.getPosition().z());
        var plan=HytaleDistanceDisplacement.plan(store,target,center,d.forward(),request.horizontalMeters(),control.displacementMultiplier(),grounded);
        if(plan.distance()<=0)return;
        var admitted=kernel.statuses().admit(packet.application(),control,true,packet.random(),claim);
        if(admitted!=com.inigmasgames.hytalerpg.combat.status.StatusService.Admission.ACCEPTED)return;
        double actual=HytaleDistanceDisplacement.apply(store,target,start,plan,()->eligible.getAsBoolean()&&SupportNativeEffects.control(store,target,bosses).displacementMultiplier()==control.displacementMultiplier());
        trace.emit(d.descriptor().entityId(),com.inigmasgames.hytalerpg.diagnostics.RpgTraceEventType.AREA_DISPLACEMENT,
                new com.inigmasgames.hytalerpg.combat.diagnostics.CombatTrace.Context(packet.source().rootId(),"",packet.source().strikeId()),
                Map.of("sourceKind","MONSTER_AFFIX","affixId","ME-015","targetId",d.hit().victim(),"requested",request.horizontalMeters(),"planned",plan.distance(),"applied",actual,"reason",plan.reason()));
    }
    private static boolean sameActor(com.hypixel.hytale.component.Store<com.hypixel.hytale.server.core.universe.world.storage.EntityStore> store,
            com.hypixel.hytale.component.Ref<com.hypixel.hytale.server.core.universe.world.storage.EntityStore> ref,UUID expected){
        var id=store.getComponent(ref,com.hypixel.hytale.server.core.entity.UUIDComponent.getComponentType());
        return id!=null&&id.getUuid().equals(expected);
    }
    /** ME-015 adds one modifier to the already authored native knockback component. */
    public static final class Knockback extends com.hypixel.hytale.server.core.modules.entity.damage.DamageEventSystem {
        private final HytaleDifficultyCombat actors;
        public Knockback(HytaleDifficultyCombat actors){this.actors=Objects.requireNonNull(actors);}
        @Override public com.hypixel.hytale.component.query.Query<com.hypixel.hytale.server.core.universe.world.storage.EntityStore> getQuery(){
            return com.hypixel.hytale.component.query.Query.any();
        }
        @Override public java.util.Set<com.hypixel.hytale.component.dependency.Dependency<com.hypixel.hytale.server.core.universe.world.storage.EntityStore>> getDependencies(){
            return java.util.Set.of(new com.hypixel.hytale.component.dependency.SystemGroupDependency<>(
                    com.hypixel.hytale.component.dependency.Order.AFTER,
                    com.hypixel.hytale.server.core.modules.entity.damage.DamageModule.get().getGatherDamageGroup()),
                    new com.hypixel.hytale.component.dependency.SystemGroupDependency<>(
                            com.hypixel.hytale.component.dependency.Order.BEFORE,
                            com.hypixel.hytale.server.core.modules.entity.damage.DamageModule.get().getFilterDamageGroup()));
        }
        @Override public void handle(int index,com.hypixel.hytale.component.ArchetypeChunk<com.hypixel.hytale.server.core.universe.world.storage.EntityStore> chunk,
                com.hypixel.hytale.component.Store<com.hypixel.hytale.server.core.universe.world.storage.EntityStore> store,
                com.hypixel.hytale.component.CommandBuffer<com.hypixel.hytale.server.core.universe.world.storage.EntityStore> buffer,
                com.hypixel.hytale.server.core.modules.entity.damage.Damage damage){
            try(var rpgTickSpan=com.inigmasgames.hytalerpg.diagnostics.NativeRpgTickMetrics.enter(store,com.inigmasgames.hytalerpg.diagnostics.NativeRpgTickMetrics.Phase.STATUS_FIELD)){
            if(damage.isCancelled()||damage.getAmount()<=0||damage.getIfPresentMetaObject(com.hypixel.hytale.server.core.modules.entity.damage.Damage.INTERACTION_TYPE)==null
                    ||!(damage.getSource() instanceof com.hypixel.hytale.server.core.modules.entity.damage.Damage.EntitySource source))return;
            var nativeKnockback=damage.getIfPresentMetaObject(com.hypixel.hytale.server.core.modules.entity.damage.Damage.KNOCKBACK_COMPONENT);
            if(nativeKnockback==null||source.getRef()==null||!source.getRef().isValid())return;
            var id=store.getComponent(source.getRef(),com.hypixel.hytale.server.core.entity.UUIDComponent.getComponentType());
            if(id==null)return;
            var world=store.getExternalData().getWorld().getWorldConfig().getUuid();
            actors.enemyState(world,id.getUuid()).flatMap(state->state.descriptor().own(
                    com.inigmasgames.hytalerpg.enemies.EnemyAffixRegistry.Operator.KNOCKBACK))
                    .ifPresent(affix->nativeKnockback.addModifier(1+affix.value("additionalNativeKnockbackStrength")));
        
            }}
    }
}
