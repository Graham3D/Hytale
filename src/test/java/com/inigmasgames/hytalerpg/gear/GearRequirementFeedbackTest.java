package com.inigmasgames.hytalerpg.gear;

import com.inigmasgames.hytalerpg.combat.attribute.RpgAttribute;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

final class GearRequirementFeedbackTest {
    @Test void namesOneOrMultipleMissingAttributesInStableOrder() {
        var gate = new GearRequirements.Gate(1,
                Map.of(RpgAttribute.DEX, 25, RpgAttribute.STR, 18));
        assertEquals("Insufficient Strength and Dexterity", gate.playerFeedback(20,
                Map.of(RpgAttribute.STR, 11, RpgAttribute.DEX, 14)));
        assertEquals("Insufficient Dexterity", gate.playerFeedback(20,
                Map.of(RpgAttribute.STR, 18, RpgAttribute.DEX, 14)));
        assertNull(gate.playerFeedback(20,
                Map.of(RpgAttribute.STR, 18, RpgAttribute.DEX, 25)));
    }

    @Test void levelOnlyRequirementDoesNotPretendAnAttributeIsMissing() {
        var gate = new GearRequirements.Gate(30, Map.of(RpgAttribute.INT, 15));
        assertEquals("Requires level 30", gate.playerFeedback(20, Map.of(RpgAttribute.INT, 15)));
        assertEquals("Insufficient Intelligence", gate.playerFeedback(20, Map.of(RpgAttribute.INT, 10)));
    }
}
