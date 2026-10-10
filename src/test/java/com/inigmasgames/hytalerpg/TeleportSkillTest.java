package com.inigmasgames.hytalerpg;

import com.google.gson.JsonParser;
import com.inigmasgames.hytalerpg.domain.PassiveId;
import com.inigmasgames.hytalerpg.domain.SkillId;
import com.inigmasgames.hytalerpg.execution.Stage04SkillProfiles;
import com.inigmasgames.hytalerpg.execution.TeleportScaling;
import com.inigmasgames.hytalerpg.links.CompatibilityService;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TeleportSkillTest {
    @Test void rankMilestonesAndItemLevelsStopAtTheIntrinsicCap() {
        int[] levels={1,2,3,10,19,20,21,1001};
        double[] ranges={10,11,11,15,19,20,20,20};
        double[] costs={.20,.19,.19,.15,.11,.10,.10,.10};
        for(int i=0;i<levels.length;i++){
            assertEquals(ranges[i],TeleportScaling.range(levels[i]));
            assertEquals(costs[i],TeleportScaling.manaFraction(levels[i]),1e-12);
        }
        assertEquals(10,TeleportScaling.range(-10));
        assertEquals(.20,TeleportScaling.manaFraction(-10),1e-12);
    }

    @Test void onlyTeleportComponentsAcceptLinks() {
        var catalog=Stage01BTestSupport.bundle().catalog();
        var skill=catalog.skill(new SkillId("teleport")).orElseThrow();
        var compatibility=new CompatibilityService();
        for(String id:new String[]{"efficiency","long_reach","second_wind","lifeblood","attunement"})
            assertTrue(compatibility.assess(skill,catalog.passive(new PassiveId(id)).orElseThrow()).accepted(),id);
        for(String id:new String[]{"echo","rapid_invocation","momentum","skill_delay","potency"})
            assertFalse(compatibility.assess(skill,catalog.passive(new PassiveId(id)).orElseThrow()).accepted(),id);
    }

    @Test void elevationAndRangeAreMeasuredInThreeDimensions() {
        var start=new com.inigmasgames.hytalerpg.execution.math.Vec3(0,64,0);
        assertTrue(TeleportScaling.withinBounds(start,new com.inigmasgames.hytalerpg.execution.math.Vec3(4,69,0),10));
        assertFalse(TeleportScaling.withinBounds(start,new com.inigmasgames.hytalerpg.execution.math.Vec3(0,70,0),20));
        assertTrue(TeleportScaling.withinBounds(start,new com.inigmasgames.hytalerpg.execution.math.Vec3(0,58,0),10));
        assertFalse(TeleportScaling.withinBounds(start,new com.inigmasgames.hytalerpg.execution.math.Vec3(9,69,0),10));
    }

    @Test void manaUsesTotalMaximumAndExistingUpfrontRounding() {
        var declared=new com.inigmasgames.hytalerpg.combat.resource.ResourceCost(
                com.inigmasgames.hytalerpg.combat.resource.ResourceType.MANA,
                105*TeleportScaling.manaFraction(1));
        assertEquals(21,declared.modified(1).amount());
        assertEquals(18,declared.modified(.85).amount());
        assertEquals(11,new com.inigmasgames.hytalerpg.combat.resource.ResourceCost(
                com.inigmasgames.hytalerpg.combat.resource.ResourceType.MANA,
                105*TeleportScaling.manaFraction(20)).modified(1).amount());
    }

    @Test void runtimeUsesOnlyTheRequestedLandingSpawner() {
        var profile=Stage04SkillProfiles.loadCanonical(Stage01BTestSupport.bundle().catalog()).require("teleport");
        assertEquals(2.5,profile.cooldownSeconds());
        assertEquals(0,profile.windupSeconds());
        assertEquals(20,TeleportScaling.effectiveRange(20,profile));
        assertNotNull(getClass().getResource("/Server/Particles/RPG/Teleport/RPG_Teleport_Landing.particlesystem"));
        var json=JsonParser.parseReader(new InputStreamReader(getClass().getResourceAsStream(
                "/Server/Particles/RPG/Teleport/RPG_Teleport_Landing.particlesystem"),StandardCharsets.UTF_8)).getAsJsonObject();
        var spawners=json.getAsJsonArray("Spawners");
        assertEquals(1,spawners.size());
        assertEquals("MagicPortal_StonesSpawn",spawners.get(0).getAsJsonObject().get("SpawnerId").getAsString());
    }
}
