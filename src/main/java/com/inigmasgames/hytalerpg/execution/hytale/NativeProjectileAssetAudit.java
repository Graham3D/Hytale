package com.inigmasgames.hytalerpg.execution.hytale;

import com.hypixel.hytale.server.core.asset.type.item.config.Item;
import com.hypixel.hytale.server.core.modules.projectile.config.ProjectileConfig;
import com.inigmasgames.hytalerpg.execution.Stage04SkillProfiles;
import java.util.*;

/** Runs against resolved installed assets at plugin start, not JSON names or mocked codecs. */
public final class NativeProjectileAssetAudit {
    private NativeProjectileAssetAudit() {}
    public static Map<String,Object> requireAssets(Stage04SkillProfiles profiles) {
        int checked=0;
        for (var profile:profiles.all().values()) {
            var payload=profile.projectile(); if(payload==null)continue;
            if(NativeProjectilePayloads.cause(payload)==null)throw new IllegalStateException("PROJECTILE_ELEMENT_UNRESOLVED:"+profile.skillId());
            var presentation=payload.details().presentation();
            for(String particle:List.of(presentation.castParticle(),presentation.projectileParticle(),presentation.impactParticle()))
                if(!particle.isBlank()&&com.hypixel.hytale.server.core.asset.type.particle.config.ParticleSystem.getAssetMap().getAsset(particle)==null)
                    throw new IllegalStateException("PROJECTILE_PARTICLE_UNRESOLVED:"+profile.skillId()+":"+particle);
            var configs=new LinkedHashMap<String,Double>(); configs.put(payload.configId(),payload.speed());
            payload.configIdsByWeaponKind().forEach((kind,id)->configs.put(id,payload.speedFor(kind)));
            for(var expected:configs.entrySet()) {
                var config=ProjectileConfig.getAssetMap().getAsset(expected.getKey());
                if(config==null||config.getModel()==null||config.getModel().getBoundingBox()==null)
                    throw new IllegalStateException("PROJECTILE_MODEL_UNRESOLVED:"+expected.getKey());
                if(config.getInteractions()!=null&&!config.getInteractions().isEmpty())
                    throw new IllegalStateException("PROJECTILE_NATIVE_GAMEPLAY_ROOT_FORBIDDEN:"+expected.getKey());
                requireEqual(config.getLaunchForce(),expected.getValue(),expected.getKey()+"/speed");
                requireEqual(config.getGravity(),payload.gravity(),expected.getKey()+"/gravity");
                var bounds=config.getModel().getBoundingBox(); double r=payload.radius();
                for(double actual:new double[]{bounds.min.x(),bounds.min.y(),bounds.min.z()})requireEqual(actual,-r,expected.getKey()+"/min");
                for(double actual:new double[]{bounds.max.x(),bounds.max.y(),bounds.max.z()})requireEqual(actual,r,expected.getKey()+"/max");
                checked++;
            }
        }
        var sparkAsset=com.hypixel.hytale.server.core.asset.type.model.config.ModelAsset.getAssetMap().getAsset("Hywind_Charged_Bolt");
        if(sparkAsset==null
                ||!"VFX/RPG/Spark/Spark_TriplePlane_R069.blockymodel".equals(sparkAsset.getModel())
                ||!"VFX/RPG/Spark/Spark_Strip_Vertical.png".equals(sparkAsset.getTexture())
                ||sparkAsset.getMinScale()!=1||sparkAsset.getMaxScale()!=1
                ||sparkAsset.getLight()!=null||(sparkAsset.getParticles()!=null&&sparkAsset.getParticles().length!=0))
            throw new IllegalStateException("SPARK_WORLD_QUAD_MODEL_UNRESOLVED");
        for(String state:List.of("Idle","FlyIdle")) {
            var animationSet=sparkAsset.getAnimationSetMap().get(state);
            if(animationSet==null||animationSet.getAnimations().length!=1
                    ||!"VFX/RPG/Spark/Spark_TriplePlane_FourFrame_R069.blockyanim".equals(animationSet.getAnimations()[0].getAnimation())
                    ||!animationSet.getAnimations()[0].isLooping())
                throw new IllegalStateException("SPARK_WORLD_QUAD_ANIMATION_UNRESOLVED:"+state);
        }
        var nativeCrossbow=ProjectileConfig.getAssetMap().getAsset("Projectile_Config_Arrow_Crossbow");
        var payload=profiles.require("crossbow_bolt").projectile();
        if(nativeCrossbow==null||nativeCrossbow.getModel()==null)throw new IllegalStateException("NATIVE_CROSSBOW_CONTROL_MISSING");
        requireEqual(nativeCrossbow.getLaunchForce(),payload.speed(),"shippedCrossbow/speed");
        requireEqual(nativeCrossbow.getGravity(),payload.gravity(),"shippedCrossbow/gravity");
        var box=nativeCrossbow.getModel().getBoundingBox();
        for(double actual:new double[]{box.min.x(),box.min.y(),box.min.z()})requireEqual(actual,-payload.radius(),"shippedCrossbow/min");
        for(double actual:new double[]{box.max.x(),box.max.y(),box.max.z()})requireEqual(actual,payload.radius(),"shippedCrossbow/max");
        var equipment=new LinkedHashMap<String,Object>();
        var powers=com.inigmasgames.hytalerpg.combat.power.NativeItemPowerRegistry.loadCanonical();
        var equipmentFailures=new ArrayList<String>();
        for(var entry:powers.all()) {
            var item=Item.getAssetMap().getAsset(entry.itemId());
            if(item==null||item.getData()==null||powers.resolve(entry.itemId(),item.getData().getRawTags()).isEmpty()) {
                equipmentFailures.add(entry.itemId()+":expected="+entry.nativeFamily()+":actual="+
                        (item==null||item.getData()==null?"MISSING":new com.google.gson.Gson().toJson(item.getData().getRawTags())));
                continue;
            }
            Double summary=HytaleEquipmentAdapter.damageSummaryMeanForAudit(item);
            equipment.put(entry.itemId(),Map.of("kind",entry.kind(),"basicPower",entry.basePower(),"selection",entry.selectionPolicy(),
                    "nativeSummaryIsNotBasePower",summary==null?"UNAVAILABLE":summary));
        }
        if(!equipmentFailures.isEmpty())throw new IllegalStateException("NATIVE_EQUIPMENT_FAMILY_UNRESOLVED:"+equipmentFailures);
        var bow=ProjectileConfig.getAssetMap().getAsset("Projectile_Config_Arrow_Shortbow_Strength_4");
        if(bow==null||bow.getModel()==null)throw new IllegalStateException("NATIVE_CHARGED_BOW_CONTROL_UNRESOLVED");
        requireEqual(bow.getLaunchForce(),85,"nativeChargedBow/speed");requireEqual(bow.getGravity(),25,"nativeChargedBow/gravity");
        var bowBox=bow.getModel().getBoundingBox();
        for(double actual:new double[]{bowBox.min.x(),bowBox.min.y(),bowBox.min.z()})requireEqual(actual,-.075,"nativeChargedBow/min");
        for(double actual:new double[]{bowBox.max.x(),bowBox.max.y(),bowBox.max.z()})requireEqual(actual,.075,"nativeChargedBow/max");
        var snipe=profiles.require("snipe").projectile();
        // Owner explicitly requests no drop, not native heavy-arrow gravity; retain native speed and model.
        if(!snipe.details().nativeCapabilityGate().isEmpty())throw new IllegalStateException("SNIPE_OBSOLETE_RANGE_GATE");
        requireEqual(snipe.speed(),bow.getLaunchForce(),"snipe/chargedSpeed");
        requireEqual(snipe.gravity(),0,"snipe/straightGravity");
        requireEqual(snipe.radius(),.075,"snipe/nativeRadius");
        requireEqual(snipe.maxDistance(),48,"snipe/authoredRpgRange");
        var snipeConfig=ProjectileConfig.getAssetMap().getAsset(snipe.configId());
        var snipeModel=snipeConfig.getModel();
        if(!"Arrow_Crude".equals(snipeModel.getModelAssetId())||!snipeModel.equals(bow.getModel())
                ||!snipeModel.getAnimationSetMap().containsKey("FlyIdle")||snipeModel.getTrails().length!=2)
            throw new IllegalStateException("SNIPE_NOT_EXACT_NATIVE_ARROW_MODEL");
        requireEqual(snipeConfig.getPhysicsConfig().toPacket().gravity,0,"snipe/nativePhysicsPacketGravity");
        var firePresentation=new LinkedHashMap<String,Object>();
        var fireball=profiles.require("fireball").projectile();
        for(var tier:com.inigmasgames.hytalerpg.execution.projectile.FireProjectilePresentation.Tier.values()){
            var config=ProjectileConfig.getAssetMap().getAsset(tier.configId());
            if(config==null||config.getModel()==null||config.getModel().getBoundingBox()==null)
                throw new IllegalStateException("FIREBALL_PRESENTATION_CONFIG_UNRESOLVED:"+tier.configId());
            if(config.getInteractions()!=null&&!config.getInteractions().isEmpty())
                throw new IllegalStateException("FIREBALL_PRESENTATION_NATIVE_GAMEPLAY_ROOT_FORBIDDEN:"+tier.configId());
            requireEqual(config.getLaunchForce(),fireball.speed(),tier.configId()+"/speed");
            requireEqual(config.getGravity(),fireball.gravity(),tier.configId()+"/gravity");
            var tierBox=config.getModel().getBoundingBox();
            for(double actual:new double[]{tierBox.min.x(),tierBox.min.y(),tierBox.min.z()})requireEqual(actual,-fireball.radius(),tier.configId()+"/min");
            for(double actual:new double[]{tierBox.max.x(),tierBox.max.y(),tierBox.max.z()})requireEqual(actual,fireball.radius(),tier.configId()+"/max");
            firePresentation.put(tier.name(),Map.of("configId",tier.configId(),"visualScale",tier.visualScale(),
                    "model",config.getModel().getModelAssetId(),"mechanicalRadius",fireball.radius()));
        }
        if(com.hypixel.hytale.server.core.asset.type.particle.config.ParticleSystem.getAssetMap().getAsset(
                com.inigmasgames.hytalerpg.execution.projectile.FireProjectilePresentation.AIM_PARTICLE)==null)
            throw new IllegalStateException("FIREBALL_WHITE_AIM_PRESENTATION_UNRESOLVED");
        for(String sound:List.of(
                com.inigmasgames.hytalerpg.execution.projectile.FireProjectilePresentation.FIRE_BOLT_LAUNCH_SOUND,
                com.inigmasgames.hytalerpg.execution.projectile.FireProjectilePresentation.FIRE_BOLT_IMPACT_SOUND,
                com.inigmasgames.hytalerpg.execution.projectile.FireProjectilePresentation.FIREBALL_LAUNCH_SOUND,
                com.inigmasgames.hytalerpg.execution.projectile.FireProjectilePresentation.FIREBALL_IMPACT_SOUND))
            if(com.hypixel.hytale.server.core.asset.type.soundevent.config.SoundEvent.getAssetMap().getAsset(sound)==null)
                throw new IllegalStateException("FIRE_PROJECTILE_SOUND_UNRESOLVED:"+sound);
        var release=com.inigmasgames.hytalerpg.input.NativeSnipeReleaseAudit.requireAssets();
        return Map.ofEntries(Map.entry("resolvedConfigs",checked),Map.entry("emptyNativeInteractions",true),Map.entry("typedElements",true),
                Map.entry("shippedCrossbowSpeed",nativeCrossbow.getLaunchForce()),Map.entry("shippedCrossbowRadius",payload.radius()),
                Map.entry("shippedCrossbowGravity",nativeCrossbow.getGravity()),Map.entry("equipment",equipment),Map.entry("connectedProof",false),
                Map.entry("sparkPresentation",Map.of("model","FIXED_HORIZONTAL_WORLD_QUAD","texture",sparkAsset.getTexture(),
                        "animation","FOUR_FRAMES_100MS","yellowFallback",false)),
                Map.entry("nativeChargedBow",Map.of("speed",85,"gravity",25,"radius",.075,"maximumRange","RPG_AUTHORED_48M_NOT_NATIVE_MAXIMUM","release",release,
                        "snipeGravity",0,"snipeModel",snipeModel.getModelAssetId(),"snipeModelEqualsNative",true)),
                Map.entry("snipeActivationGate",snipe.details().nativeCapabilityGate()),Map.entry("firePresentation",firePresentation));
    }
    private static void requireEqual(double actual,double expected,String boundary) {
        if(!Double.isFinite(actual)||Math.abs(actual-expected)>1e-6)
            throw new IllegalStateException("NATIVE_PROJECTILE_ASSET_MISMATCH:"+boundary+":actual="+actual+":expected="+expected);
    }
}
