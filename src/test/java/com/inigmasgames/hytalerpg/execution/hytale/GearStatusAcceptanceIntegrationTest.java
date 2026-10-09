package com.inigmasgames.hytalerpg.execution.hytale;

import org.junit.jupiter.api.Test;
import com.inigmasgames.hytalerpg.gear.GearEffectSnapshot;
import static org.junit.jupiter.api.Assertions.*;

class GearStatusAcceptanceIntegrationTest {
    @Test void absorbedItemContactDoesNotManufactureAnAuthoredSkillStatus(){
        assertEquals(0,GearStatusBindings.canonicalSkillChance(1,0));
        assertEquals(1,GearStatusBindings.canonicalSkillChance(1,0.01));
        assertEquals(.25,GearStatusBindings.canonicalSkillChance(.25,4));
        assertThrows(IllegalArgumentException.class,()->GearStatusBindings.canonicalSkillChance(1,-1));
        assertFalse(GearStatusBindings.skillPackageSucceeded(
                GearStatusBindings.canonicalSkillChance(1,0),1,1,0,
                GearEffectSnapshot.EMPTY,GearEffectSnapshot.EMPTY,0));
        assertTrue(GearStatusBindings.skillPackageSucceeded(1,1,1,0,
                GearEffectSnapshot.EMPTY,GearEffectSnapshot.EMPTY,0));
        assertFalse(GearStatusBindings.skillPackageSucceeded(.25,1,1,0,
                GearEffectSnapshot.EMPTY,GearEffectSnapshot.EMPTY,.5));
    }
}
