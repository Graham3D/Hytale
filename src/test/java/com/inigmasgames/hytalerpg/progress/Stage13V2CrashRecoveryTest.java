package com.inigmasgames.hytalerpg.progress;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;
import static com.inigmasgames.hytalerpg.progress.Stage13EncounterGroupCommitTest.*;

class Stage13V2CrashRecoveryTest {
    @TempDir Path directory;
    private static final List<Map<String,Object>> EVIDENCE=new CopyOnWriteArrayList<>();
    @ParameterizedTest @EnumSource(FileEncounterStore.DurabilityBoundary.class)
    void actualProcessHaltAtEveryNewBoundaryReplaysWithoutLostOrDuplicateAcceptedCredit(FileEncounterStore.DurabilityBoundary boundary)throws Exception {
        halt(boundary.name());long expected=boundary==FileEncounterStore.DurabilityBoundary.ACTIVATION_WRITTEN?1024:1025;
        for(int i=0;i<3;i++)try(var recovered=FileEncounterStore.durableV2(directory)){
            assertEquals(expected,recovered.journalSequence());var saved=recovered.load(WORLD,ENEMY).orElseThrow();assertEquals(100+expected,saved.lastObserved());assertEquals(1,saved.credits().size());assertEquals(10,saved.credits().getFirst().actualAmount());
            assertTrue(recovered.death(WORLD,ENEMY).isEmpty());assertEquals(0,recovered.pendingCount());
        }
        EVIDENCE.add(Map.of("boundary",boundary.name(),"exitCode",73,"replayed",expected,"restarts",3,"processHaltNotPowerLoss",true));
    }
    @ParameterizedTest @ValueSource(strings={"PREPARE_CREATED","PREPARE_HEADER_WRITTEN","PREPARE_FORCED","MANIFEST_PARTIAL_WRITE","MANIFEST_BEFORE_FORCE","MANIFEST_AFTER_FORCE","MANIFEST_PUBLISHED"})
    void interruptedInitialMigrationCannotExposeV2WorkToOldReader(String boundary)throws Exception {
        try(var old=new FileEncounterStore(directory)){old.create(spawn(ENEMY));}
        halt("BOOTSTRAP_"+boundary);
        for(int i=0;i<3;i++)try(var recovered=FileEncounterStore.durableV2(directory)){assertEquals(0,recovered.journalSequence());assertTrue(recovered.load(WORLD,ENEMY).orElseThrow().credits().isEmpty());}
        EVIDENCE.add(Map.of("boundary","BOOTSTRAP_"+boundary,"exitCode",73,"replayed",0,"restarts",3,"processHaltNotPowerLoss",true));
    }
    private void halt(String boundary)throws Exception {
        var cp=new ArrayList<String>();for(var type:List.of(EncounterV2CrashProcess.class,FileEncounterStore.class,com.google.gson.Gson.class))cp.add(Path.of(type.getProtectionDomain().getCodeSource().getLocation().toURI()).toString());
        cp.add(Path.of("build/resources/main").toAbsolutePath().toString());Path log=directory.resolve("child.log");
        var child=new ProcessBuilder(Path.of(System.getProperty("java.home"),"bin/java.exe").toString(),"-cp",String.join(System.getProperty("path.separator"),cp),EncounterV2CrashProcess.class.getName(),directory.toString(),boundary).redirectErrorStream(true).redirectOutput(log.toFile()).start();
        try{assertTrue(child.waitFor(30,TimeUnit.SECONDS),"Process timeout");assertEquals(73,child.exitValue(),Files.readString(log));}finally{if(child.isAlive())child.destroyForcibly();}
    }
    @AfterAll static void evidence()throws Exception {Path path=Path.of("build/stage13-hardening/v2-crash-matrix.json");Files.createDirectories(path.getParent());Files.writeString(path,new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(EVIDENCE));}
}
