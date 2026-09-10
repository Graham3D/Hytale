package com.inigmasgames.hytalerpg;

import com.inigmasgames.hytalerpg.execution.*;
import com.inigmasgames.hytalerpg.execution.hytale.NativeStrikeFeedback;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class Stage13QuickSlashSpeedTest {
    @Test void twoHundredPercentIncreasedMeansThreeTimesBaseNotTwice(){
        assertEquals(25.0/60/3,NativeStrikeFeedback.quickSlashInterval("SWORD"),1e-12);
        assertEquals(25.0/60/(.8*3),NativeStrikeFeedback.quickSlashInterval("LONGSWORD"),1e-12);
        assertEquals(20.0/60/(1.2*3),NativeStrikeFeedback.quickSlashInterval("DAGGER"),1e-12);
    }
    @Test void failureDiagnosticsBoundCodeLocationsAndOmitPrivateMessagesAndFiles(){
        var error=new IllegalStateException("secret=DO_NOT_LOG");
        var stack=new StackTraceElement[20];Arrays.fill(stack,new StackTraceElement("NativeDamage", "apply", "C:/private/save.json",42));error.setStackTrace(stack);
        var details=ExecutionFailureDiagnostics.describe("STRIKE_REPEAT",error);
        assertEquals(8,((List<?>)details.get("codeFrames")).size());assertEquals("STRIKE_REPEAT",details.get("stage"));
        assertTrue(details.toString().contains("NativeDamage#apply:42"));assertFalse(details.toString().contains("secret"));assertFalse(details.toString().contains("private"));
        assertEquals("UNKNOWN",ExecutionFailureDiagnostics.describe("private-stage",error).get("stage"));
    }
}
