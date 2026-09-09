package com.inigmasgames.hytalerpg;

import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.server.core.entity.InteractionManager;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Exercises the installed engine, without claiming a native/client casting test. */
class Stage13NativeCadenceAuditTest {
    @Test void globalTimeShiftIsAnAdditiveTransientOffsetNotAnAttackRateLease()throws Exception{
        var manager=new InteractionManager(null,null);
        assertEquals(0,manager.getGlobalTimeShift(InteractionType.Primary));
        manager.setGlobalTimeShift(InteractionType.Primary,.2f);
        assertEquals(.2f,manager.getGlobalTimeShift(InteractionType.Primary));
        manager.clearAllGlobalTimeShift(.05f);
        assertEquals(.25f,manager.getGlobalTimeShift(InteractionType.Primary));
        manager.clearAllGlobalTimeShift(.05f);
        assertEquals(0,manager.getGlobalTimeShift(InteractionType.Primary));
        assertThrows(IllegalArgumentException.class,()->manager.setGlobalTimeShift(InteractionType.Primary,-.1f));
        var file=java.nio.file.Path.of("build/stage13-hardening/native-cadence-audit.json");java.nio.file.Files.createDirectories(file.getParent());
        java.nio.file.Files.writeString(file,new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(java.util.Map.of(
            "class",InteractionManager.class.getName(),"setOffset",.2,"firstClearWithDelta05",.25,"secondClearWithDelta05",0,
            "negativeOffsetRejected",true,"connectedProof",false,"scope","Installed public methods: offset addition then reset; not a rate lease",
            "activationGate","NATIVE_PER_ACTOR_BASIC_ATTACK_CADENCE_UNVERIFIED","provesUniversalEngineImpossibility",false)));
    }
}
