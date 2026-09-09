package com.inigmasgames.hytalerpg.progress;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

class Stage13V2GroupCrashRecoveryTest {
    @TempDir Path directory;
    static final List<Map<String,Object>> evidence=new CopyOnWriteArrayList<>();
    @ParameterizedTest @EnumSource(value=FileEncounterStore.GroupBoundary.class,names={"AFTER_FIRST_FRAME","AFTER_GROUP_APPEND","BEFORE_FORCE","AFTER_GROUP_FORCE","BEFORE_ACKNOWLEDGEMENTS","MID_ACKNOWLEDGEMENTS","ANOTHER_GROUP_QUEUED","CHECKPOINT_WORKER_STARTED"})
    void haltWithoutShutdownRecoversOnlyTheReplayablePrefix(FileEncounterStore.GroupBoundary boundary)throws Exception{
        var cp=new ArrayList<String>();for(Class<?> type:List.of(EncounterV2GroupCrashProcess.class,FileEncounterStore.class,com.google.gson.Gson.class))cp.add(Path.of(type.getProtectionDomain().getCodeSource().getLocation().toURI()).toString());
        cp.add(Path.of("build/resources/main").toAbsolutePath().toString());Path log=directory.resolve("child.log");
        var child=new ProcessBuilder(Path.of(System.getProperty("java.home"),"bin/java.exe").toString(),"-cp",String.join(System.getProperty("path.separator"),cp),EncounterV2GroupCrashProcess.class.getName(),directory.toString(),boundary.name()).redirectErrorStream(true).redirectOutput(log.toFile()).start();
        try{assertTrue(child.waitFor(30,TimeUnit.SECONDS),"Child timeout");assertEquals(73,child.exitValue(),Files.readString(log));}finally{if(child.isAlive())child.destroyForcibly();}
        long count=boundary==FileEncounterStore.GroupBoundary.AFTER_FIRST_FRAME?1:boundary==FileEncounterStore.GroupBoundary.CHECKPOINT_WORKER_STARTED?65:64;
        // An OS-surviving process halt can replay complete unforced bytes. This is NOT a power-loss guarantee.
        // No pre-force member was acknowledged; a partial frame would fail closed (separate corruption matrix).
        for(int restart=0;restart<3;restart++)try(var store=FileEncounterStore.durableV2(directory)){
            assertEquals(count,store.journalSequence());var saved=store.load(new UUID(1,1),new UUID(2,2)).orElseThrow();assertEquals(100+count,saved.lastObserved());assertEquals(1,saved.credits().size());
            if(boundary==FileEncounterStore.GroupBoundary.ANOTHER_GROUP_QUEUED)assertTrue(store.load(new UUID(1,1),new UUID(2,3)).orElseThrow().credits().isEmpty());
        }
        evidence.add(Map.of("boundary",boundary.name(),"exitCode",73,"replayedFrames",count,"restartPasses",3,"shutdownHooksUsed",false,"powerLossProof",false));
    }
    @AfterAll static void evidence()throws Exception{Path file=Path.of("build/stage13-hardening/v2-group-crash-matrix.json");Files.createDirectories(file.getParent());Files.writeString(file,new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(evidence));}
}
