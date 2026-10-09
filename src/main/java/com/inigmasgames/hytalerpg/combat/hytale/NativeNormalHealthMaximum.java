package com.inigmasgames.hytalerpg.combat.hytale;

import com.hypixel.hytale.server.core.modules.entitystats.EntityStatMap;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatValue;
import com.hypixel.hytale.server.core.modules.entitystats.asset.DefaultEntityStatTypes;
import com.hypixel.hytale.server.core.modules.entitystats.asset.EntityStatType;
import com.hypixel.hytale.server.core.modules.entitystats.modifier.Modifier;
import com.hypixel.hytale.server.core.modules.entitystats.modifier.StaticModifier;
import java.util.Map;
import java.util.Set;

/** Reads normal capacity without mutating native stats or including timed EntityEffect capacity. */
public final class NativeNormalHealthMaximum {
    // StatModifiersManager writes active effects under these exact public CalculationType keys.
    private static final Set<String> TIMED = Set.of(
            StaticModifier.CalculationType.ADDITIVE.createKey("Effect"),
            StaticModifier.CalculationType.MULTIPLICATIVE.createKey("Effect"));
    private NativeNormalHealthMaximum() {}
    public static double value(EntityStatMap stats) {
        return stats == null ? 0 : value(stats.get(DefaultEntityStatTypes.getHealth()));
    }
    public static double value(EntityStatValue health) {
        if (health == null) return 0;
        var asset=EntityStatType.getAssetMap().getAsset(health.getIndex());
        if (asset==null) throw new IllegalStateException("NORMAL_HEALTH_ASSET_MISSING");
        return maximum(asset.getMax(),health.getModifiers());
    }
    static double maximum(float baseline,Map<String,Modifier> modifiers) {
        float addition=0,multiplier=0;
        boolean added=false,multiplied=false;
        if(modifiers!=null) for(var entry:modifiers.entrySet()) {
            if(TIMED.contains(entry.getKey()))continue;
            var modifier=entry.getValue();
            if(modifier.getTarget()!=Modifier.ModifierTarget.MAX)continue;
            if(!(modifier instanceof StaticModifier value))
                throw new IllegalStateException("UNKNOWN_NORMAL_HEALTH_MODIFIER:"+entry.getKey());
            if(value.getCalculationType()==StaticModifier.CalculationType.ADDITIVE) {
                addition+=value.getAmount();added=true;
            } else {
                multiplier+=value.getAmount();multiplied=true;
            }
        }
        float maximum=added?StaticModifier.CalculationType.ADDITIVE.compute(baseline,addition):baseline;
        if(multiplied)maximum=StaticModifier.CalculationType.MULTIPLICATIVE.compute(maximum,multiplier);
        if(!Float.isFinite(maximum)||maximum<0)throw new IllegalStateException("INVALID_NORMAL_HEALTH_MAXIMUM");
        return maximum;
    }
}
