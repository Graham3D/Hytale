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
        return Map.of("resolvedConfigs",checked,"emptyNativeInteractions",true,"typedElements",true,
                "shippedCrossbowSpeed",nativeCrossbow.getLaunchForce(),"shippedCrossbowRadius",payload.radius(),
                "shippedCrossbowGravity",nativeCrossbow.getGravity(),"equipment",equipment,"connectedProof",false);
    }
    private static void requireEqual(double actual,double expected,String boundary) {
        if(!Double.isFinite(actual)||Math.abs(actual-expected)>1e-6)
            throw new IllegalStateException("NATIVE_PROJECTILE_ASSET_MISMATCH:"+boundary+":actual="+actual+":expected="+expected);
    }
}
