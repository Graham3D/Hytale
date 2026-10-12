package com.inigmasgames.hytalerpg.gear;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class HytaleGearLootActiveSlotTest {
    @Test void inactiveChapterOneHotbarUsesTheFirstNativeSlotForAWeaponTransfer() {
        assertEquals(0, HytaleGearLoot.equipmentIndex((byte) -1, (short) 9));
        assertEquals(4, HytaleGearLoot.equipmentIndex((byte) 4, (short) 9));
        assertEquals(-1, HytaleGearLoot.equipmentIndex((byte) -1, (short) 0));
        assertEquals(-1, HytaleGearLoot.equipmentIndex((byte) 9, (short) 9));
    }
}
