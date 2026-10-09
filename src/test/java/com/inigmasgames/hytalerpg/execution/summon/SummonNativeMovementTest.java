package com.inigmasgames.hytalerpg.execution.summon;

import com.google.gson.JsonParser;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

final class SummonNativeMovementTest {
    @Test void everyFrozenPursuitRollHasAnExactNativeNpcSpeedEffect(){
        for(int tenth=48;tenth<=360;tenth++){
            String id=SummonNativeMovement.assetId(tenth/10.0);
            var stream=getClass().getClassLoader().getResourceAsStream("Server/Entity/Effects/RPG/Summon/"+id+".json");
            assertNotNull(stream,id);
            try(var reader=new InputStreamReader(stream,StandardCharsets.UTF_8)){
                var json=JsonParser.parseReader(reader).getAsJsonObject();
                double multiplier=json.getAsJsonObject("ApplicationEffects").get("HorizontalSpeedMultiplier").getAsDouble();
                assertEquals(1+tenth/1000.0,multiplier,1e-6,id);
                assertTrue(json.get("Duration").getAsDouble()>=.2,id);
            }catch(java.io.IOException failure){fail(failure);}
        }
        assertEquals("",SummonNativeMovement.assetId(0));
        assertThrows(IllegalArgumentException.class,()->SummonNativeMovement.assetId(4.7));
        assertThrows(IllegalArgumentException.class,()->SummonNativeMovement.assetId(Double.NaN));
    }
}
