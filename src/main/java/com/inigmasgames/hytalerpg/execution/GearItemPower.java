package com.inigmasgames.hytalerpg.execution;

import com.inigmasgames.hytalerpg.gear.GearAffixRuntime;
import com.inigmasgames.hytalerpg.gear.GearInstance;
import com.inigmasgames.hytalerpg.gear.GearPower;

/** Frozen source-item power for a rank-one triggered spell, including non-focus legal carriers. */
public final class GearItemPower {
    private GearItemPower() { }
    public static double forTriggeredSpell(GearInstance item,String root) {
        var magic=GearAffixRuntime.magic(item);
        if(magic!=null&&magic>0)return magic;
        var range=GearAffixRuntime.physical(item);
        if(range.minimum()>=.1)return GearPower.sample(range.minimum(),range.maximum(),root+"/item-spell");
        throw new IllegalArgumentException("ITEM_TRIGGER_SOURCE_POWER_UNAVAILABLE:"+item.identity());
    }
}
