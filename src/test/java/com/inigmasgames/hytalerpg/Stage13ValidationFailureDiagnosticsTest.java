package com.inigmasgames.hytalerpg;

import com.inigmasgames.hytalerpg.combat.resource.ResourceType;
import com.inigmasgames.hytalerpg.diagnostics.RpgTraceEventType;
import com.inigmasgames.hytalerpg.domain.CompiledSkillPlan;
import com.inigmasgames.hytalerpg.execution.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class Stage13ValidationFailureDiagnosticsTest {
    @Test void failedValidationRetainsSafeFramesAndCorrelationWithoutChargingOrDispatch(){
        var h=new Stage11ResourcePassivesTest.H("healing_beam"){
            @Override public Validation familyPrerequisites(Stage04SkillProfile p,CompiledSkillPlan plan){
                throw new NullPointerException("private inventory secret /private/path");
            }
        };
        h.weapon="STAFF";var result=h.cast();
        assertEquals("VALIDATION_ERROR_NullPointerException",result.code());assertFalse(result.committed());
        assertEquals(100,h.current(ResourceType.MANA));assertEquals(0,h.cooldownSaves);assertTrue(h.contexts.isEmpty());
        var records=((Stage01BTestSupport.RecordingTracer)h.b.tracer()).records;
        var failure=records.stream().filter(r->r.eventType()==RpgTraceEventType.SKILL_VALIDATION_REJECTED).findFirst().orElseThrow();
        assertEquals("VALIDATION",failure.details().get("stage"));assertEquals(false,failure.details().get("paidRootRetained"));
        assertFalse(((List<?>)failure.details().get("codeFrames")).isEmpty());
        assertNotNull(failure.details().get("rootCastId"));assertNotNull(failure.details().get("skillInstanceId"));
        assertEquals("resource-passive-1",failure.correlationId());assertFalse(failure.details().toString().contains("private inventory"));
        assertTrue(records.stream().noneMatch(r->r.eventType()==RpgTraceEventType.SKILL_COMMITTED));
    }
    @Test void validationDiagnosticsStayBoundedAndOmitMessagesAndFilenames(){
        var error=new NullPointerException("secret");var frames=new StackTraceElement[20];
        Arrays.fill(frames,new StackTraceElement("a".repeat(300),"method","C:/secret/file",12));error.setStackTrace(frames);
        var details=ExecutionFailureDiagnostics.describe("VALIDATION",error);
        assertEquals(8,((List<?>)details.get("codeFrames")).size());assertFalse(details.toString().contains("secret"));
        assertTrue(((List<?>)details.get("codeFrames")).stream().allMatch(f->f.toString().length()<330));
    }
}
