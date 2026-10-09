package com.inigmasgames.hytalerpg.execution.summon;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.ComponentAccessor;
import com.hypixel.hytale.server.core.asset.type.entityeffect.config.EntityEffect;
import com.hypixel.hytale.server.core.asset.type.entityeffect.config.OverlapBehavior;
import com.hypixel.hytale.server.core.entity.effect.EffectControllerComponent;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.inigmasgames.hytalerpg.gear.GearAffixRuntime;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Predicate;

/** Native NPC locomotion multiplier on the authored 0.1 percentage-point grid. */
public final class SummonNativeMovement {
    private SummonNativeMovement() {}
    /** Native controller acceptance together with the speed actually declared by the submitted effect. */
    public record Applied(String effectId,float horizontalSpeedMultiplier,float durationSeconds) {}

    public static String assetId(double points) {
        if (!Double.isFinite(points) || points < 0) throw new IllegalArgumentException("Invalid Pursuit roll");
        if (points == 0) return "";
        int tenths=(int)Math.round(points*10);
        if (tenths < 48 || tenths > 360 || Math.abs(points*10-tenths)>1e-6)
            throw new IllegalArgumentException("Pursuit value outside authored native asset grid: "+points);
        return "RPG_Summon_Pursuit_"+String.format(java.util.Locale.ROOT,"%03d",tenths);
    }

    public static void refresh(Store<EntityStore> store,Ref<EntityStore> entity,SummonRegistry.Lease lease) {
        if (!GearAffixRuntime.ENABLED.contains("WA-118")) return;
        var controller=store.getComponent(entity,EffectControllerComponent.getComponentType());
        if(controller!=null)synchronize(lease,controller,entity,store,id->EntityEffect.getAssetMap().getAsset(id));
    }

    /** The live refresh and native fixture both use this controller write and invalidation path. */
    static Optional<Applied> synchronize(SummonRegistry.Lease lease,EffectControllerComponent controller,
            Ref<EntityStore> entity,ComponentAccessor<EntityStore> accessor,Function<String,EntityEffect> assets){
        var applied=apply(lease,assets,effect->controller.addEffect(entity,effect,.25f,OverlapBehavior.OVERWRITE,accessor)
                &&controller.hasEffect(effect));
        var map=EntityEffect.getAssetMap();
        for(int index:controller.getActiveEffectIndexes()){
            var effect=map.getAsset(index);
            if(effect!=null&&effect.getId().startsWith("RPG_Summon_Pursuit_")
                    &&(applied.isEmpty()||!effect.getId().equals(applied.get().effectId())))
                controller.removeEffect(entity,index,accessor);
        }
        return applied;
    }

    /** The same selection and acceptance path is used by the live NPC and focused native fixture. */
    static Optional<Applied> apply(SummonRegistry.Lease lease,Function<String,EntityEffect> assets,
                                   Predicate<EntityEffect> nativeApply) {
        if (!lease.ironSentinel()&&lease.context().profile().summon().decoy()) return Optional.empty();
        double points=lease.ownerEffects().value("WA-118");
        String id=assetId(points);
        if (id.isEmpty()) return Optional.empty();
        var effect=assets.apply(id);
        if (effect==null) throw new IllegalStateException("SUMMON_PURSUIT_NATIVE_ASSET_MISSING: "+id);
        var payload=effect.getApplicationEffects();
        float measured=payload==null?Float.NaN:payload.getHorizontalSpeedMultiplier();
        float expected=(float)(1+points/100);
        if (!Float.isFinite(measured)||Math.abs(measured-expected)>1e-5f)
            throw new IllegalStateException("SUMMON_PURSUIT_NATIVE_SPEED_MISMATCH: "+id);
        if (!nativeApply.test(effect))
            throw new IllegalStateException("SUMMON_PURSUIT_NATIVE_EFFECT_REJECTED: "+id);
        return Optional.of(new Applied(id,measured,.25f));
    }
}
