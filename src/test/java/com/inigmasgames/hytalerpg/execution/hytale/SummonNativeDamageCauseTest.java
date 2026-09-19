package com.inigmasgames.hytalerpg.execution.hytale;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SummonNativeDamageCauseTest {
    @Test void physicalSummonDamageUsesTheNativePhysicalCauseAndGuardsNull() throws Exception {
        String execution=Files.readString(Path.of("src/main/java/com/inigmasgames/hytalerpg/execution/hytale/HytaleSkillExecutionSystem.java"));
        String adapter=Files.readString(Path.of("src/main/java/com/inigmasgames/hytalerpg/combat/hytale/HytaleDamageAdapter.java"));
        assertTrue(execution.contains("if(\"PHYSICAL\".equals(element))return DamageCause.PHYSICAL"));
        assertTrue(execution.contains("SUMMON_NATIVE_DAMAGE_CAUSE_MISSING_"));
        assertTrue(execution.contains("SUMMON_ARROW_PHYSICAL_CAUSE_INVALID"));
        assertTrue(execution.contains("resolvedDamageCause\",\"PHYSICAL"));
        assertTrue(adapter.contains("if(cause==null)throw new IllegalArgumentException(\"NATIVE_DAMAGE_CAUSE_MISSING\")"));
    }
}
