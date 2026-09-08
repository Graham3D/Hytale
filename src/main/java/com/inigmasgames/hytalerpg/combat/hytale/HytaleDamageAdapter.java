package com.inigmasgames.hytalerpg.combat.hytale;

import com.google.gson.Gson;
import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.component.ComponentAccessor;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.server.core.meta.MetaKey;
import com.hypixel.hytale.server.core.modules.entity.damage.Damage;
import com.hypixel.hytale.server.core.modules.entity.damage.DamageCause;
import com.hypixel.hytale.server.core.modules.entity.damage.DamageSystems;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatMap;
import com.hypixel.hytale.server.core.modules.entitystats.asset.DefaultEntityStatTypes;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.inigmasgames.hytalerpg.combat.damage.DamageCalculationService;

/** Only bridge allowed to narrow the kernel's double result and submit it to Hytale Damage. */
public final class HytaleDamageAdapter {
    static final Gson GSON = new Gson();
    public static final MetaKey<String> RPG_METADATA = Damage.META_REGISTRY.registerMetaObject(
            ignored -> "", false, "InigmasGames:HytaleRPGDamage", Codec.STRING);
    // Installed MetaRegistry allows null codec when persistence=false. This context never goes on the wire/save.
    private static final MetaKey<com.inigmasgames.hytalerpg.execution.SkillExecutionContext> EXECUTION_CONTEXT=Damage.META_REGISTRY.registerMetaObject(
            ignored->null,false,"InigmasGames:RpgMeaningfulRoot",null);
    public static com.inigmasgames.hytalerpg.execution.SkillExecutionContext executionContext(Damage damage){return damage.getIfPresentMetaObject(EXECUTION_CONTEXT);}
    public static void attachExecutionContext(Damage damage,HytaleDamageMetadata metadata,com.inigmasgames.hytalerpg.execution.SkillExecutionContext context){
        if(context==null)return;
        if(!context.request().actorId().equals(metadata.actorId())||!context.rootCastId().equals(metadata.rootCastId())
                ||!context.skillInstanceId().equals(metadata.skillInstanceId())||!context.request().correlationId().equals(metadata.correlationId()))
            throw new IllegalArgumentException("FOREIGN_NATIVE_DAMAGE_CONTEXT");
        damage.putMetaObject(EXECUTION_CONTEXT,context);
    }

    public void apply(Ref<EntityStore> target, ComponentAccessor<EntityStore> accessor,
                      Ref<EntityStore> source, DamageCause cause,
                      HytaleDamageMetadata metadata, DamageCalculationService.Result calculation) {
        applyObserved(target, accessor, source, cause, metadata, calculation);
    }

    /** Same native dispatch with an observation result; cancellation must also gate secondary statuses. */
    public NativeResult applyObserved(Ref<EntityStore> target, ComponentAccessor<EntityStore> accessor,
                      Ref<EntityStore> source, DamageCause cause,
                      HytaleDamageMetadata metadata, DamageCalculationService.Result calculation) {
        return applyResolved(target,accessor,source,cause,metadata,calculation.preMitigationDamage());
    }
    public NativeResult applyObserved(Ref<EntityStore> target,ComponentAccessor<EntityStore> accessor,Ref<EntityStore> source,DamageCause cause,
            HytaleDamageMetadata metadata,DamageCalculationService.Result calculation,com.inigmasgames.hytalerpg.combat.damage.ConditionalDamage conditional){
        return applyResolved(target,accessor,source,cause,metadata,calculation.preMitigationDamage(),conditional);
    }
    /** Already-resolved secondary amount (e.g. a post-mitigation split), not another offensive scaling pass. */
    public NativeResult applyObserved(Ref<EntityStore> target,ComponentAccessor<EntityStore> accessor,Ref<EntityStore> source,DamageCause cause,
            HytaleDamageMetadata metadata,DamageCalculationService.Result calculation,com.inigmasgames.hytalerpg.combat.damage.ConditionalDamage conditional,
            com.inigmasgames.hytalerpg.execution.SkillExecutionContext context){
        return applyResolved(target,accessor,source,cause,metadata,calculation.preMitigationDamage(),conditional,context);
    }
    public NativeResult applyResolved(Ref<EntityStore> target,ComponentAccessor<EntityStore> accessor,
                      Ref<EntityStore> source,DamageCause cause,HytaleDamageMetadata metadata,double amount){
        return applyResolved(target,accessor,source,cause,metadata,amount,null);
    }
    public NativeResult applyResolved(Ref<EntityStore> target,ComponentAccessor<EntityStore> accessor,
                      Ref<EntityStore> source,DamageCause cause,HytaleDamageMetadata metadata,double amount,
                      com.inigmasgames.hytalerpg.combat.damage.ConditionalDamage conditional){
        return applyResolved(target,accessor,source,cause,metadata,amount,conditional,null);
    }
    public NativeResult applyResolved(Ref<EntityStore> target,ComponentAccessor<EntityStore> accessor,
                      Ref<EntityStore> source,DamageCause cause,HytaleDamageMetadata metadata,double amount,
                      com.inigmasgames.hytalerpg.combat.damage.ConditionalDamage conditional,
                      com.inigmasgames.hytalerpg.execution.SkillExecutionContext context){
        if(!Double.isFinite(amount)||amount<0||amount>Float.MAX_VALUE)throw new IllegalArgumentException("Invalid native damage amount");
        EntityStatMap targetStats = accessor.getComponent(target, EntityStatMap.getComponentType());
        double before = targetStats == null || targetStats.get(DefaultEntityStatTypes.getHealth()) == null
                ? Double.NaN : targetStats.get(DefaultEntityStatTypes.getHealth()).get();
        HytaleDamageMetadata complete = new HytaleDamageMetadata(metadata.actorId(), metadata.rootCastId(),
                metadata.skillInstanceId(), metadata.correlationId(), amount, before,
                metadata.effectInstanceId(),metadata.canProc(),metadata.origin());
        Damage damage = new Damage(source == null ? Damage.NULL_SOURCE : new Damage.EntitySource(source),
                cause, (float)amount);
        damage.putMetaObject(RPG_METADATA, GSON.toJson(complete));
        attachExecutionContext(damage,metadata,context);
        HytaleConditionalDamage.attach(damage,conditional);
        DamageSystems.executeDamage(target, accessor, damage);
        double after = targetStats == null || targetStats.get(DefaultEntityStatTypes.getHealth()) == null
                ? Double.NaN : targetStats.get(DefaultEntityStatTypes.getHealth()).get();
        return new NativeResult(damage.isCancelled(), damage.getAmount(), before, after,metadata(damage).preMitigationDamage());
    }
    public record NativeResult(boolean cancelled, double nativeAmount, double healthBefore, double healthAfter,double preMitigationAmount) {
        public NativeResult(boolean cancelled,double nativeAmount,double healthBefore,double healthAfter){this(cancelled,nativeAmount,healthBefore,healthAfter,nativeAmount);}
    }
    public static HytaleDamageMetadata metadata(Damage damage) {
        String json = damage.getIfPresentMetaObject(RPG_METADATA);
        return json == null || json.isBlank() ? null : GSON.fromJson(json, HytaleDamageMetadata.class);
    }
}
