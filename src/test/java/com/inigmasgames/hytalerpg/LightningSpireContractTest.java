package com.inigmasgames.hytalerpg;

import static org.junit.jupiter.api.Assertions.*;
import com.inigmasgames.hytalerpg.domain.PassiveId;
import com.inigmasgames.hytalerpg.domain.SkillId;
import com.inigmasgames.hytalerpg.links.CompatibilityService;
import java.util.List;
import org.junit.jupiter.api.Test;

final class LightningSpireContractTest {
    @Test void compatibilityIsConstructAndWaveComponentScoped(){
        var catalog=Stage01BTestSupport.bundle().catalog();var skill=catalog.skill(new SkillId("lightning_coil")).orElseThrow();var gate=new CompatibilityService();
        for(String id:List.of("potency","efficiency","second_wind","overcharge","concentration","expanded_radius","lingering",
                "executioner","opportunist","vacuum","repulsion","attunement","leeching","long_reach"))
            assertTrue(gate.assess(skill,catalog.passive(new PassiveId(id)).orElseThrow()).accepted(),id);
        for(String id:List.of("shockwave","rapid_pulse","mobile_domain","cascade","aftermath","echo","piercing","fork","chain",
                "return","ricochet","homing","accelerant","ballistics","shrapnel","splinterburst","multistrike","ruthless",
                "swarm","minion_empowerment","death_pact"))
            assertFalse(gate.assess(skill,catalog.passive(new PassiveId(id)).orElseThrow()).accepted(),id);
        assertTrue(skill.aliases().contains("Lightning Coil"));assertEquals("lightning_coil",skill.id().value());assertEquals("Lightning Spire",skill.name());
    }

    @Test void ownerAssetsAndNativeModelCarriersArePackaged(){
        for(String resource:List.of(
                "/Common/VFX/RPG/LightningSpire/LightningSpire_R072.blockymodel",
                "/Common/VFX/RPG/LightningSpire/LightningSpire.png",
                "/Common/VFX/RPG/LightningSpire/Shock_Strip_Vertical.png",
                "/Common/VFX/RPG/LightningSpire/Shockwave_Strip_Vertical.png",
                "/Common/VFX/RPG/LightningSpire/skill_lightningspire_bg.png",
                "/Server/Models/RPG/Hywind_Lightning_Spire.json",
                "/Server/Models/RPG/Hywind_Lightning_Spire_Shock.json",
                "/Server/Models/RPG/Hywind_Lightning_Spire_Shockwave.json"))assertNotNull(getClass().getResource(resource),resource);
    }
}
