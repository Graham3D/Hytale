package com.inigmasgames.hytalerpg.combat.hytale;

import com.hypixel.hytale.server.core.modules.entitystats.modifier.Modifier;
import com.hypixel.hytale.server.core.modules.entitystats.modifier.StaticModifier;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class NativeAffixNormalHealthMaximumTest {
    private static Modifier add(float value){return new StaticModifier(Modifier.ModifierTarget.MAX,
            StaticModifier.CalculationType.ADDITIVE,value);}
    private static Modifier multiply(float value){return new StaticModifier(Modifier.ModifierTarget.MAX,
            StaticModifier.CalculationType.MULTIPLICATIVE,value);}
    @Test void keepsPermanentGearAndNativeArmorButIgnoresTimedCapacity() {
        var modifiers=new LinkedHashMap<String,Modifier>();
        modifiers.put("hytalerpg:derived-max-health",add(18));
        modifiers.put("hytalerpg:gear-capacity",add(12));
        modifiers.put(StaticModifier.CalculationType.ADDITIVE.createKey("Armor"),add(10));
        modifiers.put(StaticModifier.CalculationType.MULTIPLICATIVE.createKey("Armor"),multiply(1.5f));
        assertEquals(210,NativeNormalHealthMaximum.maximum(100,modifiers));
        modifiers.put(StaticModifier.CalculationType.ADDITIVE.createKey("Effect"),add(200));
        modifiers.put(StaticModifier.CalculationType.MULTIPLICATIVE.createKey("Effect"),multiply(2));
        assertEquals(210,NativeNormalHealthMaximum.maximum(100,modifiers));
        modifiers.remove("hytalerpg:gear-capacity");
        assertEquals(192,NativeNormalHealthMaximum.maximum(100,modifiers));
        assertEquals(100,NativeNormalHealthMaximum.maximum(100,Map.of()));
    }
    @Test void preservesNpcAndSummonProjectionAndNeverTreatsCurrentHealthAsMaximum() {
        assertEquals(275,NativeNormalHealthMaximum.maximum(100,Map.of("RPG_DIFFICULTY_MAX",add(175))));
        assertEquals(80,NativeNormalHealthMaximum.maximum(100,Map.of("RPG_SUMMON_MAX",add(-20))));
        assertEquals(100,NativeNormalHealthMaximum.maximum(100,Map.of("minimum",
                new StaticModifier(Modifier.ModifierTarget.MIN,StaticModifier.CalculationType.ADDITIVE,10))));
    }
}
