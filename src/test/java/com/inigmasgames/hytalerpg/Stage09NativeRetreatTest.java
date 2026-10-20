package com.inigmasgames.hytalerpg;

import com.inigmasgames.hytalerpg.execution.hytale.SupportNativeEffects;
import org.joml.Vector3d;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Actual native SteeringForceEvade; navigability acceptance is a fixture, not a connected terrain test. */
class Stage09NativeRetreatTest {
    @Test void nativeEvadeRequestsDirectionAwayFromSource(){
        var steering=SupportNativeEffects.safeRetreat(new Vector3d(2,0,0),new Vector3d(),new Vector3d(1,0,1),.5,delta->{
            assertEquals(.5,delta.length(),1e-9);assertTrue(delta.x()>0);return true;
        });
        assertTrue(steering.hasTranslation());assertTrue(steering.getX()>0);assertEquals(.5,steering.getMaxDistance());
    }
    @Test void unsafeTerrainPredicateSuppressesTranslation(){
        var steering=SupportNativeEffects.safeRetreat(new Vector3d(2,0,0),new Vector3d(),new Vector3d(1,0,1),.5,delta->false);
        assertFalse(steering.hasTranslation());
    }
    @Test void groundSelectorDoesNotRequestVerticalTeleport(){
        var steering=SupportNativeEffects.safeRetreat(new Vector3d(2,10,0),new Vector3d(0,-10,0),new Vector3d(1,0,1),.5,delta->{
            assertEquals(0,delta.y(),1e-9);return true;
        });
        assertEquals(0,steering.getY(),1e-9);
    }
    @Test void unboundedOrInvalidProbeStepsAreRejected(){
        assertThrows(IllegalArgumentException.class,()->SupportNativeEffects.safeRetreat(new Vector3d(),new Vector3d(),new Vector3d(1,0,1),2,delta->true));
        assertThrows(IllegalArgumentException.class,()->SupportNativeEffects.safeRetreat(new Vector3d(),new Vector3d(),new Vector3d(1,0,1),Double.NaN,delta->true));
    }
}
