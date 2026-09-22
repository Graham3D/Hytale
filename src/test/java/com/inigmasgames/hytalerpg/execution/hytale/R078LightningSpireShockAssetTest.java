package com.inigmasgames.hytalerpg.execution.hytale;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import org.junit.jupiter.api.Test;

final class R078LightningSpireShockAssetTest {
    @Test void everyHitFrameIsPackagedForDeterministicOneShotPlayback() throws Exception {
        assertEquals(.1,NativeLightningSpireVisuals.SHOCK_FRAME_SECONDS,1e-12);
        assertEquals(8,NativeLightningSpireVisuals.SHOCK_FRAMES);
        for(int frame=0;frame<8;frame++){
            assertNotNull(getClass().getResource("/Common/VFX/RPG/LightningSpire/Shock_Frame_"+frame+".png"));
            assertNotNull(getClass().getResource("/Server/Models/RPG/Hywind_Lightning_Spire_Shock_Frame_"+frame+".json"));
        }
        String source=Files.readString(Path.of("src/main/java/com/inigmasgames/hytalerpg/execution/hytale/NativeLightningSpireVisuals.java"));
        assertTrue(source.contains("shockFlashes.add(new ShockFlash"));
        assertTrue(source.contains("Math.floor(age/SHOCK_FRAME_SECONDS)"));
    }
}
