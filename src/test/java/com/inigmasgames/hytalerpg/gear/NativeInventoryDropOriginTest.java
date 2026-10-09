package com.inigmasgames.hytalerpg.gear;

import org.joml.Vector3d;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class NativeInventoryDropOriginTest {
    @Test void groundedAndFlyingOriginsMatchNativeEyePlusLookWithoutGroundSearch() {
        for (double altitude : new double[]{64, 100, 250}) {
            var actor = new Vector3d(12, altitude, -40);
            var look = new Vector3d(0, -.6, -.8);
            var receipt = NativeInventoryDropOrigin.receiptPosition(actor, 1.7, look);
            var actualSpawn = new Vector3d(receipt).add(0, .5, 0);
            assertEquals(new Vector3d(actor).add(0, 1.7, 0).add(look), actualSpawn);
            assertEquals(new Vector3d(12, altitude, -40), actor);
            assertEquals(new Vector3d(0, -.6, -.8), look);
        }
    }
}
