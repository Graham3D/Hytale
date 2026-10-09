package com.inigmasgames.hytalerpg.spawning;

import com.google.gson.JsonParser;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class NativeAquaticSpawnAssetTest {
    @Test void caveFishUseTheInstalledNativeWaterEligibilityWithoutChangingWeightsOrEnvironments() throws Exception {
        var path="/Server/NPC/Spawn/World/Zone3/Spawns_Zone3_Caves.json";
        try(var stream=NativeAquaticSpawnAssetTest.class.getResourceAsStream(path)){
            assertNotNull(stream);
            var json=JsonParser.parseReader(new InputStreamReader(stream,StandardCharsets.UTF_8)).getAsJsonObject();
            assertEquals(5,json.getAsJsonArray("Environments").size());
            var npcs=json.getAsJsonArray("NPCs");assertEquals(3,npcs.size());
            assertEquals(Set.of("Snapjaw","Frostgill","Trilobite"),npcs.asList().stream()
                    .map(value->value.getAsJsonObject().get("Id").getAsString()).collect(Collectors.toSet()));
            npcs.forEach(value->{
                var npc=value.getAsJsonObject();
                assertEquals(5,npc.get("Weight").getAsInt());
                assertEquals("Water",npc.get("SpawnFluidTag").getAsString());
                assertFalse(npc.has("SpawnBlockSet"));
            });
        }
    }
}
