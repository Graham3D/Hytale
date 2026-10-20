package com.inigmasgames.hytalerpg.gear;

import com.hypixel.hytale.protocol.InteractionType;
import com.inigmasgames.hytalerpg.combat.attribute.RpgAttribute;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class GearHotbarInteractionTest {
    @Test void invalidGearCannotTrapItsOwnerInTheHotbar() {
        var requirement=new GearRequirements.Gate(1,Map.of(RpgAttribute.STR,20));
        assertFalse(requirement.failures(99,Map.of(RpgAttribute.STR,10),false).isEmpty());
        assertFalse(HytaleGearEquipment.validatesHeldItem(InteractionType.SwapFrom));
        assertFalse(HytaleGearEquipment.validatesHeldItem(InteractionType.SwapTo));
        assertTrue(HytaleGearEquipment.validatesHeldItem(InteractionType.Primary));
        assertTrue(HytaleGearEquipment.validatesHeldItem(InteractionType.Secondary));
        assertTrue(HytaleGearEquipment.validatesHeldItem(InteractionType.Ability1));
    }
}
