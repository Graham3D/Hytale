package com.inigmasgames.hytalerpg;

import com.inigmasgames.hytalerpg.progress.ControlEvidence;
import com.inigmasgames.hytalerpg.execution.math.Vec3;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;

class Stage12ControlEvidenceTest {
    @Test void nativeImmobilizationQualifiesOnlyWhenNew(){
        assertTrue(new ControlEvidence(true,0).improvedFrom(new ControlEvidence(false,.35)));
        assertFalse(new ControlEvidence(true,0).improvedFrom(new ControlEvidence(true,0)));
        assertFalse(new ControlEvidence(true,.35).improvedFrom(new ControlEvidence(true,0)));
    }
    @Test void slowRequiresStrongerUnmaskedNativeEffect(){
        assertTrue(new ControlEvidence(false,.1).improvedFrom(new ControlEvidence(false,.05)));
        assertFalse(new ControlEvidence(false,.05).improvedFrom(new ControlEvidence(false,.05)));
        assertFalse(new ControlEvidence(false,.05).improvedFrom(new ControlEvidence(false,.1)));
        assertFalse(new ControlEvidence(false,.35).improvedFrom(new ControlEvidence(true,0)));
    }
    @ParameterizedTest @ValueSource(doubles={-1,1.01,Double.NaN,Double.POSITIVE_INFINITY})
    void invalidNativeMagnitudeRejected(double value){assertThrows(IllegalArgumentException.class,()->new ControlEvidence(false,value));}
    private static final Vec3 BEFORE=new Vec3(1,0,0),SOURCE=new Vec3(0,0,0);
    @Test void fearRequiresActualPositionChangeNotMerelyARequest(){
        assertFalse(ControlEvidence.retreatObserved(BEFORE,BEFORE,SOURCE,.05,true));
        assertFalse(ControlEvidence.retreatObserved(BEFORE,new Vec3(1.2,0,0),SOURCE,.05,false));
        assertTrue(ControlEvidence.retreatObserved(BEFORE,new Vec3(1.2,0,0),SOURCE,.05,true));
    }
    @Test void fearRejectsTowardSidewaysVerticalAndTeleport(){
        for(var after:java.util.List.of(new Vec3(.8,0,0),new Vec3(1,0,.2),new Vec3(1,1,0),new Vec3(4,0,0)))
            assertFalse(ControlEvidence.retreatObserved(BEFORE,after,SOURCE,.05,true));
    }
    @ParameterizedTest @ValueSource(doubles={-1,0,.251,Double.NaN,Double.POSITIVE_INFINITY})
    void fearRejectsStaleOrInvalidObservation(double time){assertFalse(ControlEvidence.retreatObserved(BEFORE,new Vec3(1.2,0,0),SOURCE,time,true));}
}
