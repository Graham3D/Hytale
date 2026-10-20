package com.inigmasgames.hytalerpg.enemies;

import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static com.inigmasgames.hytalerpg.enemies.EnemyAffixRegistry.Operator.*;
import static org.junit.jupiter.api.Assertions.*;

class EnemyStatusEffectsTest {
    final EnemyResourceEffectsTest hits=new EnemyResourceEffectsTest();
    @Test void simplifiedElementalAffixesAndNativeKnockbackCreateNoStatusPackage(){
        var effects=new EnemyStatusEffects();var target=new EnemyStatusEffects.Target(hits.victim,0);
        for(var op:List.of(COLD_ENCHANTED,LIGHTNING_ENCHANTED,POISON_ENCHANTED,KNOCKBACK)){
            var actor=hits.actor(op);
            assertFalse(EnemyStatusEffects.requiresSource(actor));
            assertTrue(effects.prepare(actor,hits.hit(actor,"bite",100,10),null,target,"birth").isEmpty());
        }
    }
    @Test void cursedAndArmorBreakerUseFourSecondNativeEffects()throws Exception{
        for(String family:List.of("Cursed","Armor_Broken"))for(int tier=1;tier<=3;tier++){
            var path=Path.of("src/main/resources/Server/Entity/Effects/RPG/Enemies/RPG_ME_"+family+"_"+tier+".json");
            var json=com.google.gson.JsonParser.parseString(Files.readString(path)).getAsJsonObject();
            assertEquals(4,json.get("Duration").getAsInt());assertTrue(json.get("Debuff").getAsBoolean());
            assertEquals("Overwrite",json.get("OverlapBehavior").getAsString());
            if(family.equals("Armor_Broken"))assertEquals(Set.of("Physical","Projectile"),json.getAsJsonObject("DamageResistance").keySet());
        }
    }
}
