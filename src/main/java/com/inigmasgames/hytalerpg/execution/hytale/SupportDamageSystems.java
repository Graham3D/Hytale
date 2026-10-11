package com.inigmasgames.hytalerpg.execution.hytale;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.component.*;
import com.hypixel.hytale.component.dependency.*;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.modules.entity.damage.*;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatMap;
import com.hypixel.hytale.server.core.modules.entitystats.asset.DefaultEntityStatTypes;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.inigmasgames.hytalerpg.combat.hytale.*;
import com.inigmasgames.hytalerpg.diagnostics.RpgTraceEventType;
import com.inigmasgames.hytalerpg.execution.support.FiniteSupportEffects;
import com.inigmasgames.hytalerpg.execution.support.SecondaryDamageAttempt;
import java.util.*;

/** Native post-filter barrier split and actual-HP reflection. Does not directly mutate Health or add an attack executor. */
public final class SupportDamageSystems {
    private SupportDamageSystems(){}
    private static final com.hypixel.hytale.server.core.meta.MetaKey<Double> BEFORE=Damage.META_REGISTRY.registerMetaObject(
            ignored->Double.NaN,false,"InigmasGames:SupportHealthBefore",Codec.DOUBLE);
    private static final com.hypixel.hytale.server.core.meta.MetaKey<Boolean> OBSERVED=Damage.META_REGISTRY.registerMetaObject(
            ignored->false,false,"InigmasGames:SupportDamageObserved",Codec.BOOLEAN);
    private static final com.hypixel.hytale.server.core.meta.MetaKey<String> CYCLONE_RECEIPT=Damage.META_REGISTRY.registerMetaObject(
            ignored->"",false,"InigmasGames:CycloneDurableDebitReceipt",Codec.STRING);
    private static final com.hypixel.hytale.server.core.meta.MetaKey<Boolean> MANA_BEFORE_LIFE_DONE=Damage.META_REGISTRY.registerMetaObject(
            ignored->false,false,"InigmasGames:ManaBeforeLifeProcessed",Codec.BOOLEAN);
    static boolean claimManaBeforeLife(Damage damage){
        if(damage.isCancelled()||damage.getAmount()<=0||Boolean.TRUE.equals(damage.getIfPresentMetaObject(MANA_BEFORE_LIFE_DONE)))return false;
        damage.putMetaObject(MANA_BEFORE_LIFE_DONE,true);
        return true;
    }
    private static double health(ArchetypeChunk<EntityStore> chunk,int index){
        var stats=chunk.getComponent(index,EntityStatMap.getComponentType());
        var value=stats==null?null:stats.get(DefaultEntityStatTypes.getHealth());return value==null?Double.NaN:value.get();
    }
    public static boolean secondaryCannotReflect(HytaleDamageMetadata metadata){
        return metadata!=null&&(metadata.origin()==HytaleDamageMetadata.Origin.REFLECTED||metadata.origin()==HytaleDamageMetadata.Origin.REDIRECTED
                ||metadata.monsterAffix()!=null&&metadata.origin()==HytaleDamageMetadata.Origin.PERIODIC);
    }
    private static SecondaryDamageAttempt submit(HytaleSupportSystem support,FiniteSupportEffects.Effect effect,
            Ref<EntityStore> target,Store<EntityStore> store,DamageCause cause,double amount,HytaleDamageMetadata.Origin origin){
        return submit(support,effect,target,store,cause,amount,origin,null);
    }
    public static double observedHealthBefore(Damage damage){var before=damage.getIfPresentMetaObject(BEFORE);return before==null?Double.NaN:before;}
    private static SecondaryDamageAttempt submit(HytaleSupportSystem support,FiniteSupportEffects.Effect effect,
            Ref<EntityStore> target,Store<EntityStore> store,DamageCause cause,double amount,HytaleDamageMetadata.Origin origin,
            com.inigmasgames.hytalerpg.combat.damage.ConditionalDamage conditional){
        var stats=store.getComponent(target,EntityStatMap.getComponentType());
        var hp=stats==null?null:stats.get(DefaultEntityStatTypes.getHealth());
        var attempt=SecondaryDamageAttempt.once(()->hp==null?Double.NaN:hp.get(),()->new HytaleDamageAdapter().applyResolved(target,store,null,cause,
                new HytaleDamageMetadata(effect.key().owner(),effect.rootCastId(),effect.skillInstanceId(),effect.correlationId(),amount,Double.NaN,
                        effect.skillInstanceId()+"/"+origin+"/"+UUID.randomUUID(),false,origin),amount,conditional));
        if(attempt.failure()!=null){
            // Disable this lease after an uncertain dispatch; never retry a possibly applied native hit.
            support.runtime().finite().remove(effect.key());
            if(effect.context()!=null&&effect.context().profile().support()!=null&&effect.context().profile().support().aura()){
                var owner=store.getExternalData().getRefFromUUID(effect.key().owner());
                if(owner!=null&&owner.isValid())try{
                    support.runtime().terminateAura(effect.key().owner(),effect.key().skill(),"NATIVE_SECONDARY_DAMAGE_DISPATCH_EXCEPTION",support.port(store,owner));
                }catch(RuntimeException cleanup){attempt.failure().addSuppressed(cleanup);}
            }
            var fields=new LinkedHashMap<String,Object>();
            fields.put("reason","NATIVE_SECONDARY_DAMAGE_DISPATCH_EXCEPTION");fields.put("origin",origin);
            fields.put("error",attempt.failure().getClass().getName());fields.put("nativeCompletionProven",false);
            fields.put("healthBefore",Double.isFinite(attempt.before())?attempt.before():"UNAVAILABLE");
            fields.put("healthAfter",Double.isFinite(attempt.after())?attempt.after():"UNAVAILABLE");
            fields.put("transferAcceptedFromObservedHealthLoss",attempt.transferAccepted());
            support.traceFinite(effect,RpgTraceEventType.NATIVE_SUPPORT_REJECTED,fields);
        }
        return attempt;
    }
    public static void reflectAbsorbed(HytaleSupportSystem support,FiniteSupportEffects.Absorption absorption,Damage damage,
                                       Ref<EntityStore> recipient,Store<EntityStore> store,double now){
        // Every existing absorption path reaches here; consumption credit is independent of optional reflection.
        support.absorptionResolved(store,recipient,damage,absorption);
        if(absorption==null||absorption.amount()<=0||absorption.effect().context()==null
                ||!absorption.effect().context().compiledPlan().supportModifiers().reflectiveWard()||
                secondaryCannotReflect(HytaleDamageAdapter.metadata(damage))||!(damage.getSource() instanceof Damage.EntitySource attacker))return;
        var source=attacker.getRef();var effect=absorption.effect();
        if(source.equals(recipient)||!HytaleSupportSystem.alive(store,source)||!HytaleAreaQueries.hostile(store,source,recipient)||
                SupportNativeEffects.control(store,source,support.bosses()).protectedEntity())return;
        if(!claimSecondary(support,effect,now))return;
        var cause=damage.getCause();if(cause==null){support.traceFinite(effect,RpgTraceEventType.NATIVE_SUPPORT_REJECTED,Map.of("boundary","REFLECTION_ORIGINAL_DAMAGE_CAUSE_UNAVAILABLE"));return;}
        double amount=com.inigmasgames.hytalerpg.execution.support.SupportMagnitude.wardReflection(effect.context(),absorption.amount());
        var attempt=submit(support,effect,source,store,cause,amount,HytaleDamageMetadata.Origin.REFLECTED);
        var result=attempt.completed();if(result==null)return;
        support.traceFinite(effect,RpgTraceEventType.DAMAGE_REFLECTED,Map.of("actuallyAbsorbed",absorption.amount(),"requestedReflection",amount,
                "originalChannel",cause.getId(),"healthBefore",result.healthBefore(),"healthAfter",result.healthAfter(),"cancelled",result.cancelled(),"noProc",true,"noCrit",true));
    }
    private static void reflectDurableCyclone(HytaleSupportSystem support,
            com.hypixel.hytale.server.core.universe.PlayerRef player,
            com.inigmasgames.hytalerpg.execution.support.CycloneBarrierLedger.Debit debit,
            Damage damage,Ref<EntityStore> recipient,Store<EntityStore> store){
        if(secondaryCannotReflect(HytaleDamageAdapter.metadata(damage))
                ||!(damage.getSource() instanceof Damage.EntitySource attacker))return;
        var behavior=support.durableReflectionBehavior(player,debit.generation());
        if(behavior==null)return;
        var source=attacker.getRef();
        if(source.equals(recipient)||!HytaleSupportSystem.alive(store,source)
                ||!HytaleAreaQueries.hostile(store,source,recipient)
                ||SupportNativeEffects.control(store,source,support.bosses()).protectedEntity())return;
        var cause=damage.getCause();
        if(cause==null)return;
        double amount=behavior.coefficient()*debit.absorbed();
        if(amount<=0)return;
        if(!support.claimDurableReflection(player,debit.generation(),debit.receipt()))return;
        var stats=store.getComponent(source,EntityStatMap.getComponentType());
        var hp=stats==null?null:stats.get(DefaultEntityStatTypes.getHealth());
        var attempt=SecondaryDamageAttempt.once(()->hp==null?Double.NaN:hp.get(),
                ()->new HytaleDamageAdapter().applyResolved(source,store,null,cause,
                        new HytaleDamageMetadata(player.getUuid(),behavior.rootCastId(),behavior.skillInstanceId(),
                                behavior.correlationId(),amount,Double.NaN,
                                behavior.skillInstanceId()+"/REFLECTED/"+debit.receipt(),false,
                                HytaleDamageMetadata.Origin.REFLECTED),amount,null));
        var result=attempt.completed();
        if(result!=null)try{support.traceDurableReflection(player,debit,amount,result,cause.getId());}
        catch(RuntimeException cosmetic){/* Trace cannot reverse a confirmed reflection. */}
    }
    private static boolean claimSecondary(HytaleSupportSystem support,FiniteSupportEffects.Effect effect,double now){
        int result=support.runtime().claimSupportSecondary(effect,now);
        if(result==-1)support.traceFinite(effect,RpgTraceEventType.NATIVE_SUPPORT_REJECTED,Map.of("boundary","SUPPORT_SECONDARY_BUDGET","oncePerBudgetWindow",true));
        return result==1;
    }
    /** Typed direct-hit component between native mitigation and every RPG absorption path. */
    public static final class HealthCap extends DamageEventSystem {
        private final HytaleSupportSystem support;
        private final Map<com.hypixel.hytale.server.core.modules.interaction.interaction.config.server.combat.DamageCalculator,Boolean> effectCalculators=Collections.synchronizedMap(new WeakHashMap<>());
        public HealthCap(HytaleSupportSystem support){this.support=support;}
        private boolean nativeStatusDamage(Damage damage){
            var sequence=damage.getIfPresentMetaObject(DamageCalculatorSystems.DAMAGE_SEQUENCE);
            if(sequence==null)return false;
            // ActiveEntityEffect.tickDamage uses EntitySource when attributed, but carries the
            // actual effect asset's calculator in DAMAGE_SEQUENCE. Cause (e.g. Fire) is not provenance.
            return effectCalculators.computeIfAbsent(sequence.getDamageCalculator(),calculator->
                    com.hypixel.hytale.server.core.asset.type.entityeffect.config.EntityEffect.getAssetMap().getAssetMap().values().stream()
                            .anyMatch(effect->effect.getDamageCalculator()==calculator));
        }
        @Override public Query<EntityStore> getQuery(){return Query.and(UUIDComponent.getComponentType(),EntityStatMap.getComponentType());}
        @Override public Set<Dependency<EntityStore>> getDependencies(){return Set.of(
                new SystemGroupDependency<>(Order.AFTER,DamageModule.get().getFilterDamageGroup()),
                new SystemDependency<>(Order.BEFORE,Shield.class),new SystemDependency<>(Order.BEFORE,HytaleSupportSystem.Absorb.class),
                new SystemDependency<>(Order.BEFORE,DamageSystems.ApplyDamage.class));}
        @Override public void handle(int index,ArchetypeChunk<EntityStore> chunk,Store<EntityStore> store,CommandBuffer<EntityStore> buffer,Damage damage){
            if(damage.isCancelled()||damage.getAmount()<=0||!(damage.getSource() instanceof Damage.EntitySource source))return;
            var recipient=chunk.getReferenceTo(index);var attacker=source.getRef();
            if(attacker==null||!attacker.isValid()||attacker.equals(recipient)||!HytaleAreaQueries.hostile(store,attacker,recipient))return;
            var meta=HytaleDamageAdapter.metadata(damage);
            if(meta!=null&&meta.origin()!=HytaleDamageMetadata.Origin.DIRECT&&meta.origin()!=HytaleDamageMetadata.Origin.TRIGGERED)return;
            if(meta==null&&nativeStatusDamage(damage))return;
            var cause=damage.getCause();
            if(cause==null||Set.of("Environment","Environmental","Drowning","Fall","OutOfWorld","Suffocation").contains(cause.getId()))return;
            var hp=chunk.getComponent(index,EntityStatMap.getComponentType()).get(DefaultEntityStatTypes.getHealth());if(hp==null||hp.getMax()<=0)return;
            var id=chunk.getComponent(index,UUIDComponent.getComponentType()).getUuid();double now=System.nanoTime()/1e9;
            for(var effect:support.runtime().finite().forTarget(SupportNativeEffects.world(store),id,now)){
                var cap=effect.damageCap();if(cap.isEmpty())continue;
                var owner=store.getExternalData().getRefFromUUID(effect.key().owner());
                if(!HytaleSupportSystem.alive(store,owner)||!HytaleSupportSystem.eligibleAlly(store,owner,recipient)){support.runtime().finite().remove(effect.key());continue;}
                double before=damage.getAmount();double after=cap.get().apply(before,hp.getMax(),com.inigmasgames.hytalerpg.combat.damage.MaxHealthDamageCap.Origin.DIRECT_HOSTILE);
                damage.setAmount((float)after);
                HytaleDamageLifecycleSystems.combatStage(damage,"Hywind finite support","MAX_HEALTH_DAMAGE_CAP",before,damage.getAmount(),
                        Map.of("effect",effect.key().toString(),"maximumHealth",hp.getMax()));
                support.traceFinite(effect,RpgTraceEventType.FINITE_SUPPORT_RESOLVED,Map.of("component","MaxHealthDamageCap","postMitigationBefore",before,"afterCap",after,"currentMaxHealth",hp.getMax(),"perHit",true));
                break; // Identical caps never compound, irrespective of caster count.
            }
        }
    }
    public static final class Shield extends DamageEventSystem {
        private final HytaleSupportSystem support;
        public Shield(HytaleSupportSystem support){this.support=support;}
        @Override public Query<EntityStore> getQuery(){return Query.and(UUIDComponent.getComponentType(),EntityStatMap.getComponentType());}
        @Override public Set<Dependency<EntityStore>> getDependencies(){return Set.of(
                new SystemGroupDependency<>(Order.AFTER,DamageModule.get().getFilterDamageGroup()),
                new SystemDependency<>(Order.BEFORE,HytaleSupportSystem.Absorb.class),
                new SystemDependency<>(Order.BEFORE,HytaleDamageLifecycleSystems.Filter.class),
                new SystemDependency<>(Order.BEFORE,DamageSystems.ApplyDamage.class));}
        @Override public void handle(int index,ArchetypeChunk<EntityStore> chunk,Store<EntityStore> store,CommandBuffer<EntityStore> buffer,Damage damage){
        try(var rpgTickSpan=com.inigmasgames.hytalerpg.diagnostics.NativeRpgTickMetrics.enter(store,com.inigmasgames.hytalerpg.diagnostics.NativeRpgTickMetrics.Phase.SUPPORT)){
            var metadata=HytaleDamageAdapter.metadata(damage);
            if(damage.isCancelled()||damage.getAmount()<=0||metadata!=null&&metadata.origin()==HytaleDamageMetadata.Origin.REDIRECTED)return;
            var id=chunk.getComponent(index,UUIDComponent.getComponentType()).getUuid();var ref=chunk.getReferenceTo(index);
            double now=System.nanoTime()/1e9;var world=SupportNativeEffects.world(store);
            var intrinsic=support.runtime().finite().intrinsicShield(world,id);
            if(intrinsic.isPresent()){
                var saved=store.getComponent(ref,com.inigmasgames.hytalerpg.enemies.EnemyShieldProjection.getComponentType());
                // Validate BEFORE consuming a lease: an incomplete native binding cannot absorb unsaved capacity.
                if(saved==null||!saved.snapshot().equals(intrinsic.get()))throw new IllegalStateException("BULWARK_NATIVE_SAVE_PROJECTION_MISMATCH");
            }
            // Validate again here: membership/owner teardown ticks are not guaranteed to precede an incoming hit.
            for(var effect:support.runtime().finite().forTarget(world,id,now))if(com.inigmasgames.hytalerpg.execution.support.FiniteSupportEffects.isShield(effect)){
                var owner=store.getExternalData().getRefFromUUID(effect.key().owner());
                if(!HytaleSupportSystem.alive(store,owner)||!HytaleSupportSystem.eligibleAlly(store,owner,ref))support.runtime().finite().remove(effect.key());
            }
            // The durable player barrier takes the already-filtered hit before finite shields.
            var player=store.getComponent(ref,com.hypixel.hytale.server.core.universe.PlayerRef.getComponentType());
            com.inigmasgames.hytalerpg.execution.support.CycloneDamageStage.Resolution durable=null;
            if(player!=null&&damage.getAmount()>0&&support.durableCycloneEnabled()){
                String receipt=damage.getIfPresentMetaObject(CYCLONE_RECEIPT);
                if(receipt==null||receipt.isEmpty()){
                    receipt=UUID.randomUUID().toString();damage.putMetaObject(CYCLONE_RECEIPT,receipt);
                }
                boolean previousOwnHit=support.durableOwnReceiptSeen(player,receipt);
                durable=support.durableCycloneDebit(player,receipt,damage.getAmount());
                if(durable!=null&&durable.debit().absorbed()>0){
                    var debit=durable.debit();
                    float remainder=durable.nativeRemainder();
                    HytaleDamageLifecycleSystems.combatStage(damage,"Hywind durable Cyclone barrier","BARRIER_ABSORB",
                            damage.getAmount(),remainder,Map.of("absorbed",debit.absorbed(),"remaining",debit.remaining(),
                                    "generation",String.valueOf(debit.generation()),"receipt",receipt));
                    damage.setAmount(remainder);
                    try{support.traceDurableCyclone(player,debit);}
                    catch(RuntimeException cosmetic){/* Confirmed native damage remains authoritative. */}
                }
                if(damage.getAmount()>0&&!previousOwnHit&&(durable==null||!durable.debit().duplicate())){
                    var sharedDurable=support.durableSharedDebit(player,receipt,damage.getAmount());
                    if(sharedDurable!=null&&sharedDurable.absorbed()>0){
                        float remainder=sharedDurable.nativeRemainder();
                        HytaleDamageLifecycleSystems.combatStage(damage,"Hywind durable Shared Aegis","BARRIER_ABSORB",
                                damage.getAmount(),remainder,Map.of("absorbed",sharedDurable.absorbed(),"receipt",receipt));
                        damage.setAmount(remainder);
                    }
                }
            }else if(player==null&&damage.getAmount()>0&&support.durableCycloneEnabled()
                    &&store.getComponent(ref,com.hypixel.hytale.server.npc.entities.NPCEntity.getComponentType())!=null){
                String receipt=damage.getIfPresentMetaObject(CYCLONE_RECEIPT);
                if(receipt==null||receipt.isEmpty()){
                    receipt=UUID.randomUUID().toString();damage.putMetaObject(CYCLONE_RECEIPT,receipt);
                }
                var sharedDurable=support.durableNpcSharedDebit(store,ref,id,receipt,damage.getAmount());
                if(sharedDurable!=null&&sharedDurable.absorbed()>0){
                    float remainder=sharedDurable.nativeRemainder();
                    HytaleDamageLifecycleSystems.combatStage(damage,"Hywind durable Shared Aegis","BARRIER_ABSORB",
                            damage.getAmount(),remainder,Map.of("absorbed",sharedDurable.absorbed(),"receipt",receipt));
                    damage.setAmount(remainder);
                }
            }
            double shieldBefore=damage.getAmount();
            var hit=support.runtime().finite().shieldHit(world,id,damage.getAmount(),!secondaryCannotReflect(metadata),now,(shield,amount)->{
                var caster=store.getExternalData().getRefFromUUID(shield.key().owner());
                if(!HytaleSupportSystem.alive(store,caster)||SupportNativeEffects.control(store,caster,support.bosses()).protectedEntity())return false;
                // Keep provenance in RPG metadata; an attributed but non-Entity native source skips second block/outgoing scaling.
                // Native FilterUnkillable/world/player-spawn checks still execute, and the cause bypasses resistances only.
                var attempt=submit(support,shield,caster,store,DamageCause.getAssetMap().getAsset("RPG_Redirected"),amount,HytaleDamageMetadata.Origin.REDIRECTED);
                var result=attempt.completed();
                if(result==null)return attempt.transferAccepted();
                support.traceFinite(shield,RpgTraceEventType.BARRIER_REDIRECTED,Map.of("requestedTransfer",amount,"cancelled",result.cancelled(),
                        "nativeAmount",result.nativeAmount(),"healthBefore",result.healthBefore(),"healthAfter",result.healthAfter(),
                        "noProc",true,"noLeech",true,"noCredit",true));
                return !result.cancelled();
            });
            damage.setAmount((float)hit.remainder());
            double shieldCursor=shieldBefore;
            for(var absorption:hit.intrinsicAllocations()){
                double next=Math.max(0,shieldCursor-absorption.amount());
                HytaleDamageLifecycleSystems.combatStage(damage,"Hywind intrinsic shield","BARRIER_ABSORB",shieldCursor,next,
                        Map.of("absorbed",absorption.amount(),"capacityAfter",absorption.after(),"identity",absorption.toString()));
                shieldCursor=next;
                var projection=store.getComponent(ref,com.inigmasgames.hytalerpg.enemies.EnemyShieldProjection.getComponentType());
                if(projection==null)throw new IllegalStateException("BULWARK_NATIVE_SAVE_PROJECTION_MISSING");
                projection.consumed(absorption.after());
                support.traceIntrinsic(absorption,damage);
            }
            for(var absorption:hit.allocations()){
                double next=Math.max(0,shieldCursor-absorption.amount());
                HytaleDamageLifecycleSystems.combatStage(damage,"Hywind finite shield","BARRIER_ABSORB",shieldCursor,next,
                        Map.of("absorbed",absorption.amount(),"capacityAfter",absorption.remaining(),
                                "identity",absorption.effect().key().toString(),"kind",absorption.effect().kind().name()));
                shieldCursor=next;
                support.traceFinite(absorption.effect(),RpgTraceEventType.BARRIER_ABSORBED,
                    Map.of("absorbed",absorption.amount(),"shieldRemaining",absorption.remaining(),"nativeAmountAfterShield",hit.remainder(),
                            "redirected",hit.redirected(),"authority",absorption.effect().kind().name()+"_POST_FILTER"));
            }
            for(var absorption:hit.allocations())reflectAbsorbed(support,absorption,damage,ref,store,now);
            if(player!=null&&durable!=null&&durable.debit().absorbed()>0)
                try{reflectDurableCyclone(support,player,durable.debit(),damage,ref,store);}
                catch(RuntimeException reflectionFailure){support.reportDurableReflectionFailure(
                        player,durable.debit().generation(),durable.debit().receipt(),reflectionFailure);}
            for(var offered:support.runtime().sharedGuards(world,id,now)){
                var owner=store.getExternalData().getRefFromUUID(offered.key().owner());
                if(!HytaleSupportSystem.alive(store,owner)||!HytaleSupportSystem.eligibleAlly(store,owner,ref)||!HytaleSupportSystem.inRange(store,owner,ref,8))continue;
                if(damage.getSource() instanceof Damage.EntitySource source&&HytaleAreaQueries.hostile(store,source.getRef(),ref))support.runtime().hostileDamage(offered.key().owner(),now);
                try{
                    double beforeShared=damage.getAmount();
                    var shared=support.runtime().absorbShared(offered,damage.getAmount(),now,support.port(store,owner));
                    damage.setAmount((float)shared.remainder());
                    HytaleDamageLifecycleSystems.combatStage(damage,"Hywind shared guard","BARRIER_ABSORB",beforeShared,damage.getAmount(),
                            Map.of("identity",offered.key().toString(),"absorbed",beforeShared-damage.getAmount()));
                    reflectAbsorbed(support,shared.absorption(),damage,ref,store,now);
                }catch(RuntimeException failure){support.traceFinite(offered,RpgTraceEventType.NATIVE_SUPPORT_REJECTED,
                        Map.of("boundary","SHARED_AEGIS_DURABLE_ABSORB_FAILED","exception",failure.getClass().getName()));}
            }

        }
    }
    }
    /** Runs once on the same native hit after all barriers and support defenses, before native Health. */
    public static final class ManaBeforeLife extends DamageEventSystem {
        private final com.inigmasgames.hytalerpg.combat.resource.RpgResourceService resources;
        public ManaBeforeLife(com.inigmasgames.hytalerpg.combat.resource.RpgResourceService resources){this.resources=Objects.requireNonNull(resources);}
        @Override public Query<EntityStore> getQuery(){return Query.and(com.hypixel.hytale.server.core.universe.PlayerRef.getComponentType(),EntityStatMap.getComponentType());}
        @Override public Set<Dependency<EntityStore>> getDependencies(){return Set.of(
                new SystemDependency<>(Order.AFTER,HytaleSupportSystem.Absorb.class),
                new SystemDependency<>(Order.AFTER,Shield.class),
                new SystemDependency<>(Order.BEFORE,BeforeApply.class),
                new SystemDependency<>(Order.BEFORE,DamageSystems.ApplyDamage.class));}
        @Override public void handle(int index,ArchetypeChunk<EntityStore> chunk,Store<EntityStore> store,CommandBuffer<EntityStore> buffer,Damage damage){
            if(!claimManaBeforeLife(damage))return;
            var ref=chunk.getReferenceTo(index);
            var player=chunk.getComponent(index,com.hypixel.hytale.server.core.universe.PlayerRef.getComponentType());
            var stats=chunk.getComponent(index,EntityStatMap.getComponentType());
            try{
                var equipped=com.inigmasgames.hytalerpg.gear.GearNativeItems.effects(ref,store).snapshot();
                double fraction=equipped.manaBeforeLifeFraction();
                if(fraction<=0)return;
                float before=damage.getAmount();
                var result=com.inigmasgames.hytalerpg.execution.support.ManaBeforeLifeStage.apply(before,fraction,
                        player.getUuid(),resources,new EntityStatResourcePort(stats));
                if(result.manaSpent()>0)damage.setAmount(result.healthDamage());
                HytaleDamageLifecycleSystems.combatStage(damage,"Hywind Mana Before Life","MANA_BEFORE_LIFE",
                        before,damage.getAmount(),Map.of("manaSpent",result.manaSpent(),"intended",result.intended(),"uncertain",result.uncertain()));
            }catch(RuntimeException failure){
                com.hypixel.hytale.logger.HytaleLogger.getLogger().atWarning().withCause(failure)
                        .log("RPG_MANA_BEFORE_LIFE_FAILED player=%s",player.getUuid());
            }
        }
    }
    /** Captures actual pre-Apply HP after all absorption, including native non-RPG incoming damage. */
    public static final class BeforeApply extends DamageEventSystem {
        @Override public Query<EntityStore> getQuery(){return EntityStatMap.getComponentType();}
        @Override public Set<Dependency<EntityStore>> getDependencies(){return Set.of(
                new SystemDependency<>(Order.AFTER,HytaleSupportSystem.Absorb.class),
                new SystemDependency<>(Order.AFTER,Shield.class),
                new SystemDependency<>(Order.AFTER,ManaBeforeLife.class),
                new SystemDependency<>(Order.BEFORE,DamageSystems.ApplyDamage.class));}
        @Override public void handle(int index,ArchetypeChunk<EntityStore> chunk,Store<EntityStore> store,CommandBuffer<EntityStore> buffer,Damage damage){
        try(var rpgTickSpan=com.inigmasgames.hytalerpg.diagnostics.NativeRpgTickMetrics.enter(store,com.inigmasgames.hytalerpg.diagnostics.NativeRpgTickMetrics.Phase.SUPPORT)){
            damage.putMetaObject(BEFORE,health(chunk,index));

        }
    }
    }
    public static final class Reflect extends DamageEventSystem {
        private final HytaleSupportSystem support;
        private NativeEnemyReflectiveReaction enemyReaction;
        public Reflect(HytaleSupportSystem support){this.support=support;}
        public void configureEnemyReaction(NativeEnemyReflectiveReaction reaction){enemyReaction=Objects.requireNonNull(reaction);}
        @Override public Query<EntityStore> getQuery(){return Query.and(UUIDComponent.getComponentType(),EntityStatMap.getComponentType());}
        @Override public SystemGroup<EntityStore> getGroup(){return DamageModule.get().getInspectDamageGroup();}
        @Override public void handle(int index,ArchetypeChunk<EntityStore> chunk,Store<EntityStore> store,CommandBuffer<EntityStore> buffer,Damage damage){
        try(var rpgTickSpan=com.inigmasgames.hytalerpg.diagnostics.NativeRpgTickMetrics.enter(store,com.inigmasgames.hytalerpg.diagnostics.NativeRpgTickMetrics.Phase.SUPPORT)){
            if(Boolean.TRUE.equals(damage.getIfPresentMetaObject(OBSERVED)))return;damage.putMetaObject(OBSERVED,true);
            if(damage.isCancelled()||secondaryCannotReflect(HytaleDamageAdapter.metadata(damage))||!(damage.getSource() instanceof Damage.EntitySource attacker))return;
            var recipient=chunk.getReferenceTo(index);var source=attacker.getRef();
            if(source.equals(recipient)||!HytaleSupportSystem.alive(store,source)||!HytaleAreaQueries.hostile(store,source,recipient)
                    ||SupportNativeEffects.control(store,source,support.bosses()).protectedEntity())return;
            Double before=damage.getIfPresentMetaObject(BEFORE);double after=health(chunk,index);
            if(before==null||!Double.isFinite(before)||!Double.isFinite(after)||before<=after)return;
            var id=chunk.getComponent(index,UUIDComponent.getComponentType()).getUuid();
            var metadata=HytaleDamageAdapter.metadata(damage);
            boolean originalDirect=metadata!=null?metadata.origin()==HytaleDamageMetadata.Origin.DIRECT:
                    damage.getIfPresentMetaObject(Damage.INTERACTION_TYPE)!=null||damage.getSource() instanceof Damage.ProjectileSource;
            if(originalDirect&&enemyReaction!=null){
                var sourceId=store.getComponent(source,UUIDComponent.getComponentType());
                var sourceStats=store.getComponent(source,EntityStatMap.getComponentType());
                var sourceHealth=sourceStats==null?null:sourceStats.get(DefaultEntityStatTypes.getHealth());
                if(sourceId!=null&&sourceHealth!=null&&sourceHealth.getMax()>0)
                    enemyReaction.acceptPlayer(new NativeEnemyReflectiveReaction.PlayerHit(store,buffer,source,recipient,
                            sourceId.getUuid(),id,"live/"+UUID.randomUUID(),before-after,after,sourceHealth.getMax()));
            }
            double now=System.nanoTime()/1e9;var world=SupportNativeEffects.world(store);
            var effects=new ArrayList<FiniteSupportEffects.Effect>();
            support.runtime().finite().reflection(world,id,now).ifPresent(effects::add);
            support.thorns(world,id,now).ifPresent(effects::add);
            for(var e:effects){
                if(!HytaleSupportSystem.alive(store,source))break;
                if(e.context().profile().support().aura()){
                    var owner=store.getExternalData().getRefFromUUID(e.key().owner());
                    if(!HytaleSupportSystem.alive(store,owner)||!HytaleSupportSystem.eligibleAlly(store,owner,recipient)
                            ||!HytaleSupportSystem.auraInRange(store,owner,recipient,com.inigmasgames.hytalerpg.execution.support.SupportRuntime.radius(e.context()))
                            )continue;
                }
                if(!claimSecondary(support,e,now))continue;
                double amount=(before-after)*e.magnitude();
                var attempt=submit(support,e,source,store,DamageCause.PHYSICAL,amount,HytaleDamageMetadata.Origin.REFLECTED,
                        com.inigmasgames.hytalerpg.execution.support.SupportMagnitude.reflectionConditional(e.context(),before-after,amount));
                var outcome=attempt.completed();if(outcome==null)continue;
                support.traceFinite(e,RpgTraceEventType.DAMAGE_REFLECTED,Map.of("eligibleHealthLoss",before-after,"requestedReflection",amount,
                        "healthBefore",outcome.healthBefore(),"healthAfter",outcome.healthAfter(),"cancelled",outcome.cancelled(),
                        "noProc",true,"noLeech",true,"noCredit",true));
            }

        }
    }
    }
}
