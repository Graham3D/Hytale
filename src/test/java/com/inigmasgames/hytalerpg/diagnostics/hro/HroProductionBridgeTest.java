package com.inigmasgames.hytalerpg.diagnostics.hro;

import com.inigmasgames.hytalerpg.diagnostics.*;
import com.inigmasgames.hytalerpg.ui.trace.UiInteractionTrace;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class HroProductionBridgeTest {
    @TempDir Path root;
    @Test void disabledLegacySkillRecorderStillFeedsHroAndManualUiSurvivesHroStop()throws Exception {
        UUID owner=UUID.randomUUID(),world=UUID.randomUUID();
        HroRuntime.install(root.resolve("hro"));HroContracts.IDS.forEach(HroRuntime::registered);
        HroRuntime.witness("CyclonePreload");HroRuntime.witness("SharedAegisPreload");HroRuntime.witness("AsyncPlayerConnect");
        try(var skill=new RpgSkillTraceService(root.resolve("legacy-skill.jsonl"),new SkillTraceConfiguration(false,"NORMAL",4,1,false));
            var ui=new UiInteractionTrace(root.resolve("legacy-ui"))) {
            UiInteractionTrace.install(ui);HroRuntime.start(owner,world);HroRecorderTest.await(()->HroRuntime.active(owner));
            assertFalse(skill.available());
            for(var type:List.of(RpgTraceEventType.EQUIP_SKILL_REQUEST,RpgTraceEventType.COMPILE_SUCCESS,RpgTraceEventType.SAVE,RpgTraceEventType.EQUIP_SKILL_COMMITTED))
                skill.trace(RpgTraceRecord.create(owner,type,"assign-operation",Map.of("skillSlot","skill01","skillId","fire_bolt","RPG revision",7)));
            assertFalse(UiInteractionTrace.legacyActive(owner));assertTrue(UiInteractionTrace.active(owner));
            var action=ui.begin(owner,"inventory","gear.weapon","equip",Map.of());assertTrue(action.active());
            action.stage("DOMAIN_OPERATION",Map.of("result","REJECTED","operation","EQUIP"));
            ui.on(owner);assertTrue(UiInteractionTrace.legacyActive(owner));
            HroRuntime.stop();HroRecorderTest.await(()->HroRuntime.status().startsWith("HRO COMPLETE"));
            assertTrue(UiInteractionTrace.legacyActive(owner));
            Path bundle;try(var files=Files.list(root.resolve("hro"))){bundle=files.findFirst().orElseThrow();}
            var seen=new ArrayList<HroEvent>();HroTool.read(bundle,(event,locator)->seen.add(event));
            assertEquals(4,seen.stream().filter(event->event.contract().equals("SKILL.ASSIGN")).count());
            assertTrue(seen.stream().anyMatch(event->event.boundary().equals("DOMAIN_OPERATION")));
            HroTool.analyze(bundle);
        }finally{HroRuntime.shutdown();UiInteractionTrace.install(null);}
    }
    @Test void compiledProductionEntrypointContainsRequiredWitnessesAndOwnerObservers()throws Exception {
        try(var input=getClass().getClassLoader().getResourceAsStream("com/inigmasgames/hywind/HyArpgPlugin.class")) {
            assertNotNull(input);String bytes=new String(input.readAllBytes(),java.nio.charset.StandardCharsets.ISO_8859_1);
            for(String required:List.of("HroRuntime","CyclonePreload","SharedAegisPreload","AsyncPlayerConnect","PLAYER.ADMISSION",
                    "SKILL.ASSIGN","SKILL.PROJECTION","SKILL.EXECUTION","HUD.PUBLICATION","UI.LIFECYCLE","INVENTORY.OWNER","INVENTORY.TRANSFER"))
                assertTrue(bytes.contains(required),required);
        }
    }
    @Test void sameAdmissionWaitThenCompletionClearsTransientBlocker() {
        var contracts=new HroContracts(Set.copyOf(HroContracts.IDS));
        contracts.accept(new HroEvent(1,"run",1,1,1,"PLAYER.ADMISSION","owner","WAIT","admission","world",3L,1L,null,Map.of("reason","PRELOAD_PENDING")),"s:1");
        contracts.accept(new HroEvent(1,"run",2,1,2,"PLAYER.ADMISSION","owner","OWNER_INSTALLED","admission","world",3L,2L,null,Map.of()),"s:2");
        assertEquals(HroContracts.Outcome.PASS,((Map<?,?>)contracts.coverage(false).get("PLAYER.ADMISSION")).get("outcome"));
    }
}
