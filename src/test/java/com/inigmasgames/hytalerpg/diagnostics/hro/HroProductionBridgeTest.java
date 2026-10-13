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
    @Test void hroOnlyNativeDamageUsesExistingReconciliationAndLeavesPacketUnchanged()throws Exception {
        UUID owner=UUID.randomUUID(),world=UUID.randomUUID(),target=UUID.randomUUID();
        HroRuntime.install(root.resolve("native-hro"));HroContracts.IDS.forEach(HroRuntime::registered);
        try(var assets=com.inigmasgames.hytalerpg.gear.NativeAssetTestFixtures.open();
            var trace=new CombatDamageTraceService(root.resolve("combat.jsonl"),failure->{throw new AssertionError(failure);},ignored->{})) {
            HroRuntime.start(owner,world);HroRecorderTest.await(()->HroRuntime.active(owner));
            assertFalse(trace.active());
            var damage=new com.hypixel.hytale.server.core.modules.entity.damage.Damage(
                    com.hypixel.hytale.server.core.modules.entity.damage.Damage.NULL_SOURCE,0,25);
            trace.begin(damage,world,owner,target,"player","npc",100d,100d);
            assertTrue(trace.tracked(damage));trace.accepted(damage);trace.beforeApply(damage,100d);trace.applied(damage,75d);trace.applied(damage,75d);
            assertEquals(25,damage.getAmount());assertFalse(damage.isCancelled());assertFalse(trace.active());
            HroRuntime.stop();HroRecorderTest.await(()->HroRuntime.status().startsWith("HRO COMPLETE"));
            Path bundle;try(var paths=Files.list(root.resolve("native-hro"))){bundle=paths.findFirst().orElseThrow();}
            var events=new ArrayList<HroEvent>();HroTool.read(bundle,(e,l)->events.add(e));
            var results=events.stream().filter(e->e.boundary().equals("NATIVE_RESULT")).toList();assertEquals(1,results.size());
            assertEquals("MATCH",results.getFirst().facts().get("reconciliation"));
            assertEquals("MISMATCH",CombatDamageTraceService.reconcile(100d,90d,25));
            assertEquals("MATCH",CombatDamageTraceService.reconcile(10d,0d,25));
            assertEquals("UNOBSERVABLE",CombatDamageTraceService.reconcile(null,0d,25));
        }finally{HroRuntime.shutdown();}
    }
    @Test void realJournalSaveReplayReceiptsJoinWithoutAnyDiagnosticWritesToLedger()throws Exception {
        UUID owner=UUID.randomUUID(),world=UUID.randomUUID(),activation=UUID.randomUUID(),generation=UUID.randomUUID();
        Path journal=root.resolve("journal"),output=root.resolve("durable-hro");
        HroRuntime.install(output);HroContracts.IDS.forEach(HroRuntime::registered);
        try {
            HroRuntime.start(owner,world);HroRecorderTest.await(()->HroRuntime.active(owner));
            var ledger=new com.inigmasgames.hytalerpg.execution.support.CycloneBarrierLedger(journal);var lease=ledger.attach(owner);
            var tx=new com.inigmasgames.hytalerpg.execution.support.CycloneActivationTransaction(ledger);
            tx.begin(lease,activation,generation,150,()->true,()->true,()->true);
            tx.manaCommitted(lease,activation,"native-mana",40);tx.cooldownCommitted(lease,activation,"native-cooldown");tx.publish(lease,activation);
            ledger.debit(lease,generation,"hit",200);ledger.detach(lease);
            var repository=new com.inigmasgames.hytalerpg.progress.FileRpgPlayerStateRepository(root.resolve("players"));
            var state=com.inigmasgames.hytalerpg.progress.RpgPlayerState.create(owner);state.revision=7;
            repository.save(state);byte[] playerBytes=Files.readAllBytes(repository.path(owner));
            byte[] bytes=Files.readAllBytes(journal.resolve(owner+".journal"));
            HroRuntime.stop();HroRecorderTest.await(()->HroRuntime.status().startsWith("HRO COMPLETE"));
            Path earlier;try(var paths=Files.list(output)){earlier=paths.findFirst().orElseThrow();}
            HroRuntime.start(owner,world);HroRecorderTest.await(()->HroRuntime.active(owner));
            var recovered=new com.inigmasgames.hytalerpg.execution.support.CycloneBarrierLedger(journal);var after=recovered.attach(owner);
            assertEquals(0,recovered.state(after).remaining());assertArrayEquals(bytes,Files.readAllBytes(journal.resolve(owner+".journal")));
            assertEquals(7,repository.load(owner).state().revision);assertArrayEquals(playerBytes,Files.readAllBytes(repository.path(owner)));
            HroRuntime.stop();HroRecorderTest.await(()->HroRuntime.status().startsWith("HRO COMPLETE"));
            Path later;try(var paths=Files.list(output)){later=paths.filter(p->!p.equals(earlier)).findFirst().orElseThrow();}
            HroTool.recoveryJoin(earlier,later);
            String report=Files.readString(later.resolve("recovery-analysis.json"));
            assertTrue(report.contains("PASS"),report);assertTrue(report.contains("MATCHING_DURABLE_REVISION_CONTENT"),report);
            assertFalse(report.contains(owner.toString()),"Cross-run subject must be opaque");
        }finally{HroRuntime.shutdown();}
    }
    @Test void actualEquipmentUnitsAndProcDrawsAreIdenticalWithHroEnabled()throws Exception {
        UUID owner=UUID.randomUUID(),world=UUID.randomUUID(),target=UUID.randomUUID();
        var suite=new com.inigmasgames.hytalerpg.gear.GearAffixQaSuite(com.inigmasgames.hytalerpg.gear.GearCatalog.load());
        var item=suite.preview(suite.fixtures().stream().filter(f->f.fixtureId().equals("ab-wa-135-affixed")).findFirst().orElseThrow());
        var snapshot=new com.inigmasgames.hytalerpg.gear.GearEffectSnapshot(List.of(item));
        var contact=new com.inigmasgames.hytalerpg.gear.GearSignatureProcRuntime.Contact(world,owner,item.identity(),target,"root","contact",snapshot,
                true,true,true,false,false,false,10,100,1000,com.inigmasgames.hytalerpg.gear.GearSignatureProcRuntime.Kind.COMMON,
                100,Map.of(com.inigmasgames.hytalerpg.gear.GearCombatEffects.Channel.PHYSICAL,100d),1,false,false,false);
        var children=new ArrayList<com.inigmasgames.hytalerpg.gear.GearSignatureProcRuntime.Child>();
        var port=new com.inigmasgames.hytalerpg.gear.GearSignatureProcRuntime.Port(){
            public void enqueue(com.inigmasgames.hytalerpg.gear.GearSignatureProcRuntime.Child child){children.add(child);}
            public boolean execute(com.inigmasgames.hytalerpg.gear.GearSignatureProcRuntime.Contact c){return false;}
            public List<UUID> burstTargets(com.inigmasgames.hytalerpg.gear.GearSignatureProcRuntime.Contact c,double radius,int max){return List.of();}
        };
        var draws=new java.util.concurrent.atomic.AtomicInteger();
        new com.inigmasgames.hytalerpg.gear.GearSignatureProcRuntime(()->{draws.incrementAndGet();return 0;}).applied(contact,1,port);
        var disabled=List.copyOf(children);int disabledDraws=draws.get();children.clear();draws.set(0);
        HroRuntime.install(root.resolve("gear-hro"));HroContracts.IDS.forEach(HroRuntime::registered);
        try {
            HroRuntime.start(owner,world);HroRecorderTest.await(()->HroRuntime.active(owner));
            com.inigmasgames.hytalerpg.gear.GearQaTrace.equipment(owner,snapshot,List.of(item),Set.of(item.identity()));
            double value;try(var scope=HroGear.calculation(owner,"root")){value=snapshot.percent("WA-135");}
            assertEquals(snapshot.value("WA-135")/100,value);
            new com.inigmasgames.hytalerpg.gear.GearSignatureProcRuntime(()->{draws.incrementAndGet();return 0;}).applied(contact,1,port);
            assertEquals(disabled,children);assertEquals(disabledDraws,draws.get());
            assertFalse(com.inigmasgames.hytalerpg.gear.GearQaTrace.active(owner));
            HroRuntime.stop();HroRecorderTest.await(()->HroRuntime.status().startsWith("HRO COMPLETE"));
            Path bundle;try(var paths=Files.list(root.resolve("gear-hro"))){bundle=paths.findFirst().orElseThrow();}
            var events=new ArrayList<HroEvent>();HroTool.read(bundle,(e,l)->events.add(e));
            assertTrue(events.stream().anyMatch(e->e.boundary().equals("CONTRIBUTION")));
            assertEquals(1,events.stream().filter(e->e.boundary().equals("ROLL")).count());
        }finally{HroRuntime.shutdown();}
    }
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
