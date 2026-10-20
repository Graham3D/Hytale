package com.inigmasgames.hytalerpg.execution.summon;

import com.hypixel.hytale.server.core.asset.type.item.config.Item;
import com.inigmasgames.hytalerpg.gear.GearBindings;
import com.inigmasgames.hytalerpg.gear.GearCatalog;
import com.inigmasgames.hytalerpg.gear.GearInstance;
import java.util.Objects;

/** QA forge-source policy. Material restrictions can be restored here without changing custody. */
public final class IronSentinelSourceEligibility {
    private static final GearBindings BINDINGS=new GearBindings();
    private IronSentinelSourceEligibility() {}

    public static GearBindings.Binding require(GearInstance item){
        Objects.requireNonNull(item);
        if(item.category()!=GearCatalog.Category.HELD&&item.category()!=GearCatalog.Category.ARMOR)
            throw new IllegalArgumentException("TARGET_NOT_WEAPON_OR_ARMOR");
        var binding=BINDINGS.require(item.baseId());
        if(!binding.mapped())throw new IllegalArgumentException("TARGET_UNSUPPORTED_GEAR: "+binding.reason());
        return binding;
    }

    public static GearBindings.Binding requireLoaded(GearInstance item){
        var binding=require(item);
        if(Item.getAssetMap().getAsset(binding.nativeItemId())==null
                ||Item.getAssetMap().getAsset(binding.carrier(item.rarity()))==null)
            throw new IllegalArgumentException("TARGET_UNSUPPORTED_GEAR: native item or managed carrier is not loaded");
        return binding;
    }
}
