package com.inigmasgames.hytalerpg;

import com.google.gson.*;
import java.nio.file.*;
import java.util.Map;
import java.util.HexFormat;
import java.security.MessageDigest;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Asset/ownership contracts, not a claim of connected camera rendering. */
class MantlePresentationContractTest {
    static JsonObject effect(String name)throws Exception{
        return JsonParser.parseString(Files.readString(Path.of("src/main/resources/Server/Entity/Effects/RPG/RPG_Mantle_"+name+".json"))).getAsJsonObject();
    }
    @Test void nativeCameraVariantsShareOneFiniteRemovableOwnerAndHalfScale()throws Exception{
        var aura=effect("Aura");assertFalse(aura.get("Infinite").getAsBoolean());assertEquals(.75,aura.get("Duration").getAsDouble());
        var app=aura.getAsJsonObject("ApplicationEffects");assertFalse(app.has("ScreenEffect"));
        assertEquals(app.get("Particles"),app.get("FirstPersonParticles"));
        assertEquals(1,app.getAsJsonArray("Particles").size());
        var p=app.getAsJsonArray("Particles").get(0).getAsJsonObject();
        assertEquals(.5,p.get("Scale").getAsDouble());assertEquals("Self",p.get("TargetEntityPart").getAsString());
        assertFalse(p.has("TargetNodeName"));assertFalse(p.has("DetachedFromModel"));assertTrue(p.get("ClearParticlesOnRemove").getAsBoolean());
    }
    @Test void impactUsesUnmodifiedNativeAssetAtBodyHeightWithFinitePlayback()throws Exception{
        var impact=effect("Impact");assertEquals(7,impact.get("Duration").getAsDouble());assertFalse(impact.get("Infinite").getAsBoolean());
        var app=impact.getAsJsonObject("ApplicationEffects");assertEquals(1,app.getAsJsonArray("Particles").size());
        var p=app.getAsJsonArray("Particles").get(0).getAsJsonObject();
        assertEquals("Impact_Fire",p.get("SystemId").getAsString());assertEquals("Entity",p.get("TargetEntityPart").getAsString());
        assertEquals(1.25,p.getAsJsonObject("PositionOffset").get("Y").getAsDouble());assertFalse(p.has("Scale"));
        assertTrue(p.get("ClearParticlesOnRemove").getAsBoolean());assertFalse(impact.has("DamageCalculator"));
    }
    @Test void spiralFlashAmbientEmitterAndGameplayProfileAreByteIdenticalToWorkingAQ()throws Exception{
        var aq=Map.of(
                "Server/Particles/RPG/RPG_Mantle_Pulse.particlesystem","A685E8B5E0D57546212481F4913CC3CCFED0EDF281920C9428139DA29F4E0E85",
                "Server/Particles/RPG/RPG_Mantle_Aura.particlesystem","3DFC0CE2F5A19792A5F3B18854AAF27911DBE29F0DE208A90F01BD28B2BE6CEE",
                "Server/Entity/Effects/RPG/RPG_Mantle_Pulse.json","3206A5674FADCBF63561936D75D2DF2C5C827BB33A866C86AD561EA6D15F3007",
                "Server/Entity/Effects/RPG/RPG_Mantle_Flash.json","B6CB0A448A973C75A6043B2AB3B4741AD544DE53173AE59D672E560149323753",
                "rpg/runtime/mantle-of-flame-v1.json","329538ECE123E5748367D634A6B57E0528E1FBAFA163D698DEEE3F523E297D66",
                "rpg/runtime/managed-weapon-fire-v1.json","1B6B4DC96D3A3FAC044A0053BE0C44A5611F5F98BDE18E451C66228BED8ACBA7");
        for(var entry:aq.entrySet())assertEquals(entry.getValue(),HexFormat.of().withUpperCase().formatHex(
                MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(Path.of("src/main/resources",entry.getKey())))),entry.getKey());
    }
}
