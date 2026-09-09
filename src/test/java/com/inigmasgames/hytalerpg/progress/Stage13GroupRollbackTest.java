package com.inigmasgames.hytalerpg.progress;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.net.URLClassLoader;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import static org.junit.jupiter.api.Assertions.*;

class Stage13GroupRollbackTest {
    @TempDir Path directory;
    @Test void actualArchivedGReadsGroupedWalAndAsyncCheckpointWithUnchangedVersion()throws Exception{
        var jar=Path.of("evidence/stage-13/cohort-g/artifacts/HytaleRPG-0.0.25.jar").toAbsolutePath();
        assertEquals("9b81faa34d8f41d5c7b43205c52f3e17a44f585d1420c87eb1eeadab0b7d4fee",HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(jar))));
        String expected;
        try(var store=new FileEncounterStore(directory)){
            Stage13EncounterGroupCommitTest.done(Stage13EncounterGroupCommitTest.submit(store,Stage13EncounterGroupCommitTest.frames(store,64)));store.checkpoint();
            var runtime=Stage13EncounterGroupCommitTest.runtime(store);assertTrue(Stage13EncounterGroupCommitTest.done(Stage13EncounterGroupCommitTest.hit(runtime,165).durable()));expected=new com.google.gson.Gson().toJson(store.load(new UUID(1,1),new UUID(2,2)).orElseThrow());
        }
        Path server=Path.of(System.getProperty("user.home"),"AppData/Roaming/Hytale/install/pre-release/package/game/latest/Server/HytaleServer.jar");
        try(var old=new URLClassLoader(new java.net.URL[]{jar.toUri().toURL(),server.toUri().toURL()},ClassLoader.getPlatformClassLoader())){
            var type=old.loadClass("com.inigmasgames.hytalerpg.progress.FileEncounterStore");Object store=type.getConstructor(Path.class).newInstance(directory);
            try{assertEquals(65L,type.getMethod("journalSequence").invoke(store));var value=(Optional<?>)type.getMethod("load",UUID.class,UUID.class).invoke(store,new UUID(1,1),new UUID(2,2));var gsonType=old.loadClass("com.google.gson.Gson");var gson=gsonType.getConstructor().newInstance();assertEquals(expected,gsonType.getMethod("toJson",Object.class).invoke(gson,value.orElseThrow()));}
            finally{type.getMethod("close").invoke(store);}
        }
    }
    @Test void rollbackPreflightRejectsArchivedFOnWalAndAllowsOnlyItsPreWalDirectory()throws Exception{
        var wal=directory.resolve("wal");try(var store=new FileEncounterStore(wal)){Stage13EncounterGroupCommitTest.runtime(store);}
        Path pre=Files.createDirectory(directory.resolve("pre-wal"));
        for(var target:List.of(wal,pre)){
            Path log=directory.resolve(target.getFileName()+".log");var child=new ProcessBuilder("pwsh.exe","-NoProfile","-File",Path.of("tools/Test-EncounterRollbackCompatibility.ps1").toAbsolutePath().toString(),"-CandidateJar",Path.of("evidence/stage-13/cohort-f/artifacts/HytaleRPG-0.0.25.jar").toAbsolutePath().toString(),"-EncounterDirectory",target.toString()).redirectErrorStream(true).redirectOutput(log.toFile()).start();
            try{assertTrue(child.waitFor(20,TimeUnit.SECONDS));if(target.equals(wal)){assertNotEquals(0,child.exitValue());assertTrue(Files.readString(log).contains("PRE_WAL_BINARY_ON_WAL_STATE_FORBIDDEN"));}else assertEquals(0,child.exitValue(),Files.readString(log));}finally{if(child.isAlive())child.destroyForcibly();}
        }
    }
}
