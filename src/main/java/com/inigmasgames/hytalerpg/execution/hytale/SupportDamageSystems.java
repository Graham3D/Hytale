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
    private static double health(ArchetypeChunk<EntityStore> chunk,int index){
        var stats=chunk.getComponent(index,EntityStatMap.getComponentType());
        var value=stats==null?null:stats.get(DefaultEntityStatTypes.getHealth());return value==null?Double.NaN:value.get();
    }
    public static boolean secondaryCannotReflect(HytaleDamageMetadata metadata){
        return metadata!=null&&(metadata.origin()==HytaleDamageMetadata.Origin.REFLECTED||metadata.origin()==HytaleDamageMetadata.Origin.REDIRECTED);
    }
    private static SecondaryDamageAttempt submit(HytaleSupportSystem support,FiniteSupportEffects.Effect effect,
            Ref<EntityStore> target,Store<EntityStore> store,DamageCause cause,double amount,HytaleDamageMetadata.Origin origin){
        var stats=store.getComponent(target,EntityStatMap.getComponentType());
        var hp=stats==null?null:stats.get(DefaultEntityStatTypes.getHealth());
        var attempt=SecondaryDamageAttempt.once(()->hp==null?Double.NaN:hp.get(),()->new HytaleDamageAdapter().applyResolved(target,store,null,cause,
                new HytaleDamageMetadata(effect.key().owner(),effect.rootCastId(),effect.skillInstanceId(),effect.correlationId(),amount,Double.NaN,
                        effect.skillInstanceId()+"/"+origin+"/"+UUID.randomUUID(),false,origin),amount));
        if(attempt.failure()!=null){
            // Disable this lease after an uncertain dispatch; never retry a possibly applied native hit.
            support.runtime().finite().remove(effect.key());
            if(effect.context().profile().support().aura()){
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
            var metadata=HytaleDamageAdapter.metadata(damage);
            if(damage.isCancelled()||damage.getAmount()<=0||metadata!=null&&metadata.origin()==HytaleDamageMetadata.Origin.REDIRECTED)return;
            var id=chunk.getComponent(index,UUIDComponent.getComponentType()).getUuid();var ref=chunk.getReferenceTo(index);
            double now=System.nanoTime()/1e9;var world=SupportNativeEffects.world(store);
            // Validate again here: membership/owner teardown ticks are not guaranteed to precede an incoming hit.
            for(var effect:support.runtime().finite().forTarget(world,id,now))if(effect.kind()==com.inigmasgames.hytalerpg.execution.support.SupportProfile.Kind.SHIELD){
                var owner=store.getExternalData().getRefFromUUID(effect.key().owner());
                if(!HytaleSupportSystem.alive(store,owner)||!HytaleSupportSystem.eligibleAlly(store,owner,ref))support.runtime().finite().remove(effect.key());
            }
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
            for(var absorption:hit.allocations())support.traceFinite(absorption.effect(),RpgTraceEventType.BARRIER_ABSORBED,
                    Map.of("absorbed",absorption.amount(),"shieldRemaining",absorption.remaining(),"nativeAmountAfterShield",hit.remainder(),
                            "redirected",hit.redirected(),"authority","SPIRIT_SHIELD_POST_FILTER"));
        }
    }
    /** Captures actual pre-Apply HP after all absorption, including native non-RPG incoming damage. */
    public static final class BeforeApply extends DamageEventSystem {
        @Override public Query<EntityStore> getQuery(){return EntityStatMap.getComponentType();}
        @Override public Set<Dependency<EntityStore>> getDependencies(){return Set.of(
                new SystemDependency<>(Order.AFTER,HytaleSupportSystem.Absorb.class),
                new SystemDependency<>(Order.AFTER,Shield.class),
                new SystemDependency<>(Order.BEFORE,DamageSystems.ApplyDamage.class));}
        @Override public void handle(int index,ArchetypeChunk<EntityStore> chunk,Store<EntityStore> store,CommandBuffer<EntityStore> buffer,Damage damage){
            damage.putMetaObject(BEFORE,health(chunk,index));
        }
    }
    public static final class Reflect extends DamageEventSystem {
        private final HytaleSupportSystem support;
        public Reflect(HytaleSupportSystem support){this.support=support;}
        @Override public Query<EntityStore> getQuery(){return Query.and(UUIDComponent.getComponentType(),EntityStatMap.getComponentType());}
        @Override public SystemGroup<EntityStore> getGroup(){return DamageModule.get().getInspectDamageGroup();}
        @Override public void handle(int index,ArchetypeChunk<EntityStore> chunk,Store<EntityStore> store,CommandBuffer<EntityStore> buffer,Damage damage){
            if(Boolean.TRUE.equals(damage.getIfPresentMetaObject(OBSERVED)))return;damage.putMetaObject(OBSERVED,true);
            if(damage.isCancelled()||secondaryCannotReflect(HytaleDamageAdapter.metadata(damage))||!(damage.getSource() instanceof Damage.EntitySource attacker))return;
            var recipient=chunk.getReferenceTo(index);var source=attacker.getRef();
            if(source.equals(recipient)||!HytaleSupportSystem.alive(store,source)||!HytaleAreaQueries.hostile(store,source,recipient)
                    ||SupportNativeEffects.control(store,source,support.bosses()).protectedEntity())return;
            Double before=damage.getIfPresentMetaObject(BEFORE);double after=health(chunk,index);
            if(before==null||!Double.isFinite(before)||!Double.isFinite(after)||before<=after)return;
            var id=chunk.getComponent(index,UUIDComponent.getComponentType()).getUuid();
            double now=System.nanoTime()/1e9;var world=SupportNativeEffects.world(store);
            var effects=new ArrayList<FiniteSupportEffects.Effect>();
            support.runtime().finite().reflection(world,id,now).ifPresent(effects::add);
            support.runtime().thorns(world,id,now).ifPresent(effects::add);
            for(var e:effects){
                if(!HytaleSupportSystem.alive(store,source))break;
                if(e.context().profile().support().aura()){
                    var owner=store.getExternalData().getRefFromUUID(e.key().owner());
                    if(!HytaleSupportSystem.alive(store,owner)||!HytaleSupportSystem.eligibleAlly(store,owner,recipient)
                            ||!HytaleSupportSystem.auraInRange(store,owner,recipient,e.context().profile().support().radius()*e.context().compiledPlan().executionModifiers().radiusFactor())
                            ||!support.runtime().claimAuraSecondary(e.context(),now))continue;
                }
                double amount=(before-after)*e.magnitude();
                var attempt=submit(support,e,source,store,DamageCause.PHYSICAL,amount,HytaleDamageMetadata.Origin.REFLECTED);
                var outcome=attempt.completed();if(outcome==null)continue;
                support.traceFinite(e,RpgTraceEventType.DAMAGE_REFLECTED,Map.of("eligibleHealthLoss",before-after,"requestedReflection",amount,
                        "healthBefore",outcome.healthBefore(),"healthAfter",outcome.healthAfter(),"cancelled",outcome.cancelled(),
                        "noProc",true,"noLeech",true,"noCredit",true));
            }
        }
    }
}
