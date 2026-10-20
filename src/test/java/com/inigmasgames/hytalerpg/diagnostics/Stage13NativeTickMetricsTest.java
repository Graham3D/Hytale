package com.inigmasgames.hytalerpg.diagnostics;

import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Tests bookkeeping and source routing ONLY; these samples are not native runtime performance. */
class Stage13NativeTickMetricsTest {
    @Test void nestedPhaseSpansPartitionOneActualIdentifierWithoutDoubleCounting(){
        var events=new ArrayList<RpgTraceRecord>();NativeRpgTickMetrics.configure(events::add);var world=UUID.randomUUID();
        try(var outer=NativeRpgTickMetrics.enter(world,700,0,NativeRpgTickMetrics.Phase.EXECUTION,System.nanoTime())){
            try(var inner=NativeRpgTickMetrics.enter(world,700,0,NativeRpgTickMetrics.Phase.DAMAGE,System.nanoTime())){}
        }
        try(var next=NativeRpgTickMetrics.enter(world,701,20_000_000,NativeRpgTickMetrics.Phase.HUD,System.nanoTime())){}
        assertEquals(1,events.size());var details=events.getFirst().details();assertEquals(700L,details.get("nativeTick"));
        @SuppressWarnings("unchecked") var phases=(Map<String,Double>)details.get("exclusivePhaseMs");
        double total=phases.values().stream().mapToDouble(Double::doubleValue).sum();assertEquals(total,(Double)details.get("rpgWallMs"),1e-9);
        assertEquals(20d,details.get("wholeWorldTickMs"));assertEquals(true,details.get("wholeWorldTickPaired"));
        NativeRpgTickMetrics.configure(ignored->{});
    }
    @Test void actualNativeTickSourceAndProductionHandoffRoutingAreRetained()throws Exception{
        var base=Path.of("src/main/java/com/inigmasgames/hytalerpg");
        var metrics=Files.readString(base.resolve("diagnostics/NativeRpgTickMetrics.java"));assertTrue(metrics.contains("world.getTick()"));assertTrue(metrics.contains("world.isInThread()"));
        var rewards=Files.readString(base.resolve("execution/hytale/HytaleEncounterRewards.java"));
        for(String required:List.of("observeDamage(","prepareDeathNative(","detachObserved(","rewards.deliveryTick()","runtime.drainReadyPlans(8)"))assertTrue(rewards.contains(required),required);
        assertFalse(rewards.contains("durableEffects.await()"));assertFalse(rewards.contains("runtime.drain(8)"));assertFalse(rewards.contains("runtime.death("));
        var support=Files.readString(base.resolve("execution/hytale/HytaleSupportSystem.java"));assertTrue(support.contains("SupportProgressStore.Async"));assertTrue(support.contains("NATIVE_SYNCHRONOUS_SUPPORT_SAVE_FORBIDDEN"));
        assertTrue(java.lang.reflect.Modifier.isSynchronized(com.inigmasgames.hytalerpg.execution.hytale.HytalePlayerPersistenceReady.class.getMethod("begin",UUID.class,UUID.class).getModifiers()),"cross-world readiness capacity check and insertion must be atomic");
        var conversion=new com.inigmasgames.hytalerpg.execution.hytale.HytaleConversionSystem(null,null,null);
        var receipts=conversion.getClass().getDeclaredField("exclusionReceipts");receipts.setAccessible(true);
        assertInstanceOf(java.util.concurrent.ConcurrentMap.class,receipts.get(conversion),"owner completion removal and cross-world admission share receipt storage");
        var nativeRoot=base.resolve("execution/hytale");
        try(var paths=Files.list(nativeRoot)){
            for(var file:paths.filter(p->p.toString().endsWith(".java")).toList()){
                var text=Files.readString(file);
                if(text.contains("@Override public void tick(")||text.contains("@Override public void handle("))assertTrue(text.contains("NativeRpgTickMetrics.enter("),file.toString());
            }
        }
    }
    @Test void publicationMetricsAreBoundedAndExplicitlyNotClientRenderingProof(){
        var latency=new ProgressionHandoffMetrics();var actor=UUID.randomUUID();var records=new ArrayList<RpgTraceRecord>();
        for(int i=0;i<260;i++)latency.committed(actor,"event-"+i,System.nanoTime());
        assertEquals(256,latency.snapshot().get("readyCompletions"));assertEquals(4L,latency.snapshot().get("droppedDiagnosticSamples"));
        latency.published(actor,records::add);assertEquals(8,records.size());assertEquals(248,latency.snapshot().get("readyCompletions"));
        assertTrue(records.stream().allMatch(r->r.details().get("publication").equals("OWNER_COMMITTED_STATE_OBSERVED_NOT_CLIENT_RENDER_PROOF")));
    }
}
