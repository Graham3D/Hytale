package com.inigmasgames.hytalerpg.diagnostics.hro;

import java.lang.instrument.Instrumentation;
import java.lang.reflect.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;

/** Test-only Java agent; never packaged in Hywind or attached to the owner's game. */
public final class HroHeapQualification {
    private static Instrumentation instrumentation;
    public static void premain(String ignored,Instrumentation agent){instrumentation=agent;}
    public static void main(String[] args)throws Exception {
        if(instrumentation==null)throw new IllegalStateException("Test agent missing");
        Path output=Path.of("build/reports/hro");Files.createDirectories(output);
        UUID owner=UUID.randomUUID();var entered=new CountDownLatch(1);var release=new CountDownLatch(1);
        Path directory=Files.createTempDirectory(Path.of("build"),"hro-heap-");
        try(var recorder=new HroRecorder(directory,System::nanoTime,()->Map.of("fixture","heap"),()->{
            entered.countDown();try{release.await();}catch(InterruptedException e){Thread.currentThread().interrupt();}
        })) {
            recorder.start(owner,owner);
            long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(10);
            while(!recorder.active(owner)&&System.nanoTime()<deadline)Thread.sleep(1);
            if(!entered.await(10,TimeUnit.SECONDS))throw new IllegalStateException("Sink never entered");
            for(int n=0;n<600;n++) {
                Map<String,Object> facts=new LinkedHashMap<>();
                for(String key:List.of("result","reason","skillSlot","logicalSlot","skillId","nativeItemId","nativeAction",
                        "nativePrimaryIndex","operation","ownershipMode","componentPresent","expectedSpatial","bagRevisionBefore",
                        "bagRevisionAfter","RPG revision","revision","rawNativeSlot","resolvedNativeSlot","quantityBefore","quantityAfter"))
                    facts.put(key,(n+"-"+key+"-\u03bb").repeat(100));
                recorder.observe(owner,"SKILL.PROJECTION","heap","SNAPSHOT","op-"+n,owner,1L,1L,1L,facts);
            }
            Field field=HroRecorder.class.getDeclaredField("session");field.setAccessible(true);
            long measured=retained(field.get(recorder));
            // Saturate the new worker state too: 2048 distinct selected-proc lifecycles with the largest
            // scalar payload. Non-Latin strings deliberately exercise two-byte String storage.
            var contracts=new HroContracts(Set.copyOf(HroContracts.IDS));var mechanical=new HroMechanicalReport();
            for(int n=0;n<2048;n++){
                Map<String,String> facts=new LinkedHashMap<>();
                for(String key:List.of("affixId","itemId","rootId","equipmentRevision","units","bucket","value","applied",
                        "reason","cause","origin","targetId","before","after","generation","receiptId","phase","activation","proof"))
                    facts.put(key,(n+"\u03bb").repeat(192).substring(0,192));
                facts.put("selected","true");
                var event=new HroEvent(1,"heap",n+1,1,1,"AFFIX.PROC","heap","ROLL","op"+n,"world",1L,1L,1L,facts);
                contracts.accept(event,"events/segment-0001.jsonl.gz:"+n);mechanical.accept(event,"events/segment-0001.jsonl.gz:"+n);
            }
            long workerBytes=retained(contracts)+retained(mechanical);
            // Additional allowance covers transient serialization/catalog loading, packet-local observers and worker stack.
            long qualified=measured+workerBytes+16L*1024*1024;
            Files.writeString(output.resolve("heap.json"),HroBundle.JSON.toJson(Map.of("method","Instrumentation.getObjectSize identity graph",
                    "slowDiskQueueSessionBytes",measured,"nonQueueConservativeReserveBytes",16L*1024*1024,
                    "saturatedWorkerStateBytes",workerBytes,
                    "qualifiedBoundBytes",qualified,"limitBytes",64L*1024*1024,"withinBudget",qualified<=64L*1024*1024)));
            if(qualified>64L*1024*1024)throw new IllegalStateException("HRO_HEAP_TARGET_EXCEEDED");
            release.countDown();recorder.stop();
            while(!recorder.status().startsWith("HRO COMPLETE")&&System.nanoTime()<deadline)Thread.sleep(2);
        }finally{release.countDown();}
    }
    private static long retained(Object root)throws IllegalAccessException {
        var seen=Collections.newSetFromMap(new IdentityHashMap<Object,Boolean>());var pending=new ArrayDeque<Object>();pending.add(root);long bytes=0;
        while(!pending.isEmpty()) {
            Object value=pending.removeFirst();if(!seen.add(value))continue;
            // Paths refer to the shared process filesystem, which HRO does not own.
            if(value instanceof java.nio.file.FileSystem || value instanceof java.nio.file.spi.FileSystemProvider
                    || value instanceof Class<?> || value instanceof Enum<?> || value instanceof Thread)continue;
            bytes+=instrumentation.getObjectSize(value);Class<?> type=value.getClass();
            if(type.isArray()) {
                if(!type.getComponentType().isPrimitive())for(Object child:(Object[])value)if(child!=null)pending.add(child);
                continue;
            }
            for(Class<?> current=type;current!=null;current=current.getSuperclass())for(Field f:current.getDeclaredFields()) {
                if(Modifier.isStatic(f.getModifiers())||f.getType().isPrimitive())continue;
                f.setAccessible(true);Object child=f.get(value);if(child!=null)pending.add(child);
            }
        }
        return bytes;
    }
}
