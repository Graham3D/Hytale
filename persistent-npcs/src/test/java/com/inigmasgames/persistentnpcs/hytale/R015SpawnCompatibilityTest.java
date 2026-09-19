package com.inigmasgames.persistentnpcs.hytale;

import com.hypixel.hytale.math.vector.Rotation3f;
import org.joml.Vector3d;

/** Guards the spawn rotation against the 0.5.9/Update 6 lookAt ABI break. */
public final class R015SpawnCompatibilityTest {
    private R015SpawnCompatibilityTest() {
    }

    public static void main(String[] args) {
        Rotation3f north = HytaleNpcAdapter.rotationFacing(new Vector3d(0, 0, -1));
        assert close(north.pitch(), 0.0f);
        assert close(north.yaw(), 0.0f);

        Rotation3f east = HytaleNpcAdapter.rotationFacing(new Vector3d(1, 0, 0));
        assert close(east.pitch(), 0.0f);
        assert close(east.yaw(), (float) (-Math.PI / 2.0));

        Rotation3f above = HytaleNpcAdapter.rotationFacing(new Vector3d(0, 1, -1));
        assert close(above.pitch(), (float) (Math.PI / 4.0));

        Rotation3f zero = HytaleNpcAdapter.rotationFacing(new Vector3d());
        assert zero.isFinite();

        System.out.println("R015 spawn rotation compatibility test passed.");
    }

    private static boolean close(float actual, float expected) {
        return Math.abs(actual - expected) < 0.0001f;
    }
}
