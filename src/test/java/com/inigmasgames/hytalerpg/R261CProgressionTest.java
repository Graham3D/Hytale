package com.inigmasgames.hytalerpg;

import com.inigmasgames.hytalerpg.combat.attribute.*;
import com.inigmasgames.hytalerpg.combat.balance.CombatBalanceProfile;
import org.junit.jupiter.api.Test;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class R261CProgressionTest {
    private final CombatBalanceProfile profile=CombatBalanceProfile.loadCanonical();
    private final DerivedStatService derived=new DerivedStatService(profile,new EffectiveAttributeService(profile));

    @Test void naturalHealthIsMonotonicAndHasDocumentedEndpoints(){
        int[] levels={1,10,25,50,75,99};int[] expected={100,126,170,244,317,388};
        for(int i=0;i<levels.length;i++)assertEquals(expected[i],CharacterHealthCurve.naturalMaximum(levels[i]));
        int previous=100;
        for(int level=2;level<=99;level++){
            int next=CharacterHealthCurve.naturalMaximum(level);
            assertTrue(next>=previous&&next-previous<=3);previous=next;
            assertEquals(next,derived.derive(Map.of(),level).maxHealth());
        }
        assertThrows(IllegalArgumentException.class,()->CharacterHealthCurve.naturalMaximum(100));
    }
    @Test void effectiveStrengthAndIntelligenceContributeTwoPerPointAboveTen(){
        var stats=derived.derive(Map.of(RpgAttribute.STR,500,RpgAttribute.INT,500),99);
        assertEquals(388+2*(320-10),stats.maxHealth());
        assertEquals(100+2*(320-10),stats.maxMana());
        assertEquals(100,derived.derive(Map.of(RpgAttribute.INT,0)).maxMana());
        assertEquals(100,derived.derive(Map.of(RpgAttribute.STR,0)).maxHealth());
    }
}
