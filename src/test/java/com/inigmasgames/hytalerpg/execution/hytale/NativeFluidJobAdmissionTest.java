package com.inigmasgames.hytalerpg.execution.hytale;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class NativeFluidJobAdmissionTest {
    @Test void onlyCompleteLoadedDryColumnsCanBeRejectedBeforeNativeProbing(){
        assertTrue(NativeEnemySpawnGroups.definitelyDry(10,section->false));
        assertFalse(NativeEnemySpawnGroups.definitelyDry(10,section->section==6));
        assertFalse(NativeEnemySpawnGroups.definitelyDry(10,section->section==6?null:false));
        assertFalse(NativeEnemySpawnGroups.definitelyDry(0,section->false));
    }
}
