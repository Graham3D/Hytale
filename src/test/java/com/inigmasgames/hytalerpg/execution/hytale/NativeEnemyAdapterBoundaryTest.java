package com.inigmasgames.hytalerpg.execution.hytale;

import com.hypixel.hytale.server.core.modules.interaction.interaction.config.server.DamageEntityInteraction;
import org.joml.Vector3d;
import org.junit.jupiter.api.Test;
import java.util.Arrays;
import static org.junit.jupiter.api.Assertions.*;

/** Regressions for the two native R230 Elite attack adapter failures. */
class NativeEnemyAdapterBoundaryTest {
    @Test void launchDirectionUsesNativeShotVectorAndPreservesVerticalShotYaw(){
        var nativeShot=NativeEnemyActions.launchHorizontal(new Vector3d(3,2,4),null);
        assertEquals(.6,nativeShot.x(),1e-12);
        assertEquals(.8,nativeShot.z(),1e-12);
        var yaw=new com.hypixel.hytale.math.vector.Rotation3f(0,1.2f,0);
        var vertical=NativeEnemyActions.launchHorizontal(new Vector3d(0,10,0),yaw);
        assertEquals(1,vertical.horizontalLength(),1e-12);
        assertThrows(IllegalStateException.class,()->NativeEnemyActions.launchHorizontal(new Vector3d(0,0,0),null));
    }

    @Test void convertedMeleeLeafInheritsNativeDamageSimulation(){
        // An empty override advanced different operation paths for converted native leaves.
        assertTrue(Arrays.stream(NativeEnemyDamageInteraction.class.getDeclaredMethods())
                .noneMatch(method->method.getName().equals("simulateTick0")));
        assertTrue(Arrays.stream(DamageEntityInteraction.class.getDeclaredMethods())
                .anyMatch(method->method.getName().equals("simulateTick0")));
    }
}
