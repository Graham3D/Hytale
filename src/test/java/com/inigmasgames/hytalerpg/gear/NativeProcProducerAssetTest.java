package com.inigmasgames.hytalerpg.gear;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class NativeProcProducerAssetTest {
    private static String interaction(String folder,String name) throws Exception {
        return Files.readString(Path.of("src/main/resources/Server/Item/Interactions/RPG",folder,name+".json"));
    }
    @Test void bombAreaUsesTheInspectedUncappedNativeSelector() throws Exception {
        String selector=interaction("Carriers","RPG_Carrier_Bomb_Impact");
        assertTrue(selector.contains("\"Type\": \"Selector\""));
        assertTrue(selector.contains("\"Id\": \"AOECircle\""));
        assertTrue(selector.contains("RPG_Carrier_Bomb_Damage"));
        assertFalse(selector.contains("MaxTargets"));
        assertFalse(selector.contains("HitEntityRules"));
        assertTrue(interaction("Carriers","RPG_Carrier_Bomb_Damage")
                .contains("\"RpgProcCoefficient\": 1.0"));
    }
    @Test void authoredVolleyHasThreeCarriersAndOneThirdPerCarrier() throws Exception {
        String damage=interaction("Gear","RPG_GearRoute_I_Weapon_Shortbow_Signature_Volley_Damage");
        assertTrue(damage.contains("\"RpgProcCoefficient\": 0.3333333333333333"));
        for(int strength=0;strength<3;strength++){
            String release=interaction("Gear","RPG_GearRoute_I_Weapon_Shortbow_Signature_Volley_Strength_"+strength);
            for(String leg:new String[]{"Left","Center","Right"})
                assertTrue(release.contains("_"+leg+"\""));
        }
    }
}
