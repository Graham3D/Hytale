package com.inigmasgames.hytalerpg;

import com.inigmasgames.hytalerpg.execution.hytale.SupportNativeEffects;
import com.google.gson.JsonParser;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Packaged presentation structure only; native rendering and occlusion require connected QA. */
class Stage09SupportPresentationTest {
    @Test void ownerPaletteIsStableAndBounded(){
        Set<String> palette=new HashSet<>();
        for(int i=0;i<100;i++){
            var id=new UUID(0,i);var asset=SupportNativeEffects.markTint(id);
            assertEquals(asset,SupportNativeEffects.markTint(id));palette.add(asset);
        }
        assertEquals(Set.of("RPG_Support_Mark_0","RPG_Support_Mark_1","RPG_Support_Mark_2","RPG_Support_Mark_3"),palette);
    }
    @Test void tintFallbacksHaveNoGameplayOrWorldTrackingFields()throws Exception{
        for(String id:List.of("RPG_Support_Mark_0","RPG_Support_Mark_1","RPG_Support_Mark_2","RPG_Support_Mark_3","RPG_Support_Hex_Tint")){
            var stream=getClass().getResourceAsStream("/Server/Entity/Effects/RPG/"+id+".json");assertNotNull(stream);
            try(var reader=new InputStreamReader(stream,StandardCharsets.UTF_8)){
                var asset=JsonParser.parseReader(reader).getAsJsonObject();
                assertEquals(Set.of("Duration","ApplicationEffects"),asset.keySet());assertEquals(.2,asset.get("Duration").getAsDouble());
                var effects=asset.getAsJsonObject("ApplicationEffects");assertEquals(1,effects.size());
                assertTrue(effects.keySet().stream().allMatch(k->k.equals("EntityTopTint")||k.equals("EntityBottomTint")));
            }
        }
    }
    @Test void rallyProjectionIsOnlyTheAuthoredMovementMultiplier()throws Exception{
        try(var reader=new InputStreamReader(Objects.requireNonNull(getClass().getResourceAsStream("/Server/Entity/Effects/RPG/RPG_Rally_Movement.json")),StandardCharsets.UTF_8)){
            var asset=JsonParser.parseReader(reader).getAsJsonObject();assertEquals(Set.of("Duration","ApplicationEffects"),asset.keySet());
            var effects=asset.getAsJsonObject("ApplicationEffects");assertEquals(Set.of("HorizontalSpeedMultiplier"),effects.keySet());
            assertEquals(1.1,effects.get("HorizontalSpeedMultiplier").getAsDouble());
        }
    }
}
