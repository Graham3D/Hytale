package com.inigmasgames.hytalerpg;

import com.inigmasgames.hytalerpg.execution.hytale.HealingProbePolicy;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class Stage13HealingProbeAJTest {
    @Test void onlyTwoRecipientControlsRequireAnEntity(){
        var required=EnumSet.of(HealingProbePolicy.Mode.RECIPIENT_ONCE,HealingProbePolicy.Mode.RECIPIENT_OVERWRITE);
        for(var mode:HealingProbePolicy.Mode.values()){
            assertEquals(required.contains(mode),HealingProbePolicy.needsRecipient(mode));
            assertDoesNotThrow(()->HealingProbePolicy.validateTarget(mode,"native"));
            if(required.contains(mode)){
                assertThrows(IllegalArgumentException.class,()->HealingProbePolicy.validateTarget(mode,"none"));
                assertDoesNotThrow(()->HealingProbePolicy.validateTarget(mode,UUID.randomUUID().toString()));
            }else{
                assertDoesNotThrow(()->HealingProbePolicy.validateTarget(mode,"none"));
                assertThrows(IllegalArgumentException.class,()->HealingProbePolicy.validateTarget(mode,"invalid"));
            }
        }
    }
    @Test void fixtureIsFreshFlatAndUsesKnownFloorWithNoAmbientCombat()throws Exception{
        var json=com.google.gson.JsonParser.parseString(Files.readString(Path.of("tools/fixtures/healing-probe-world.json"))).getAsJsonObject();
        var gen=json.getAsJsonObject("WorldGen");assertEquals("Flat",gen.get("Type").getAsString());
        var layer=gen.getAsJsonArray("Layers").get(0).getAsJsonObject();
        assertEquals(0,layer.get("From").getAsInt());assertEquals(64,layer.get("To").getAsInt());assertEquals("Soil_Grass",layer.get("BlockType").getAsString());
        var spawn=json.getAsJsonObject("SpawnProvider");assertEquals("Global",spawn.get("Id").getAsString());
        assertEquals(64,spawn.getAsJsonObject("SpawnPoint").get("Y").getAsDouble());
        assertFalse(json.get("IsSpawningNPC").getAsBoolean());
        var launcher=Files.readString(Path.of("tools/New-HealingProbeAI.ps1"));
        assertTrue(launcher.contains("Refuse to reuse a world"));assertTrue(launcher.contains("'authenticated'"));
        assertTrue(launcher.contains("tools/fixtures/healing-probe-world.json"));
    }
    @Test void lifecycleIsNullSafeAndSetupFailuresAreExplicit()throws Exception{
        var source=Files.readString(Path.of("src/main/java/com/inigmasgames/hytalerpg/execution/hytale/HealingPresentationProbe.java"));
        assertTrue(source.contains("if(HealingProbePolicy.needsRecipient(r.mode))"));
        assertTrue(source.contains("r.target!=null&&!r.target.isValid()"));
        assertTrue(source.contains("r.target==null?r.endpoint:anchor(r,r.target)"));
        assertTrue(source.contains("TARGET_NOT_REQUIRED"));assertTrue(source.contains("This is not a rendering result."));
        assertTrue(source.contains("spawnNPCWithColumnProbe"));assertFalse(source.contains("spawnNPCWithSpaceValidation"));
        assertFalse(source.contains(".setBlock("));
    }
}
