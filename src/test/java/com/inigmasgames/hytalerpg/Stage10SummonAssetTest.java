package com.inigmasgames.hytalerpg;

import com.google.gson.*;
import org.junit.jupiter.api.Test;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;

class Stage10SummonAssetTest {
    @Test void nativeRoleContainsOnlyMotionNotCombatRewardOrFlockMutation() throws Exception {
        var role=read("/Server/NPC/Roles/RPG/RPG_Summon_Wolf.json");
        assertEquals("Generic",role.get("Type").getAsString());assertEquals("Wolf_Black",role.get("Appearance").getAsString());
        assertEquals("Friendly",role.get("DefaultPlayerAttitude").getAsString());assertFalse(role.get("IsMemory").getAsBoolean());
        assertFalse(role.get("PickupDropOnDeath").getAsBoolean());
        assertEquals(Set.of("Type","Appearance","MaxHealth","IsMemory","DefaultPlayerAttitude","DefaultNPCAttitude",
                "ApplySeparation","PickupDropOnDeath","MotionControllerList","Instructions","NameTranslationKey"),role.keySet());
        assertEquals(1,role.getAsJsonArray("Instructions").size());
        var instruction=role.getAsJsonArray("Instructions").get(0).getAsJsonObject();
        assertEquals(Set.of("Sensor","BodyMotion"),instruction.keySet());
        assertEquals("Target",instruction.getAsJsonObject("Sensor").get("Type").getAsString());
        assertEquals("Seek",instruction.getAsJsonObject("BodyMotion").get("Type").getAsString());
    }
    @Test void abilityTriggerOwnsNoNativeGameplayCharge() throws Exception {
        var ability=read("/Server/Item/Items/RPG/Abilities/RPG_Ability_Wolf_Summon.json").getAsJsonObject("Ability");
        assertEquals(0,ability.get("Cost").getAsInt());assertEquals("None",ability.get("CostType").getAsString());
        assertEquals(0,ability.get("Cooldown").getAsInt());assertEquals("Root_RPG_Ability_Bridge",ability.get("Cast").getAsString());
    }
    private static JsonObject read(String path)throws Exception{
        try(var stream=Stage10SummonAssetTest.class.getResourceAsStream(path)){
            assertNotNull(stream);return JsonParser.parseReader(new InputStreamReader(stream,StandardCharsets.UTF_8)).getAsJsonObject();
        }
    }
}
