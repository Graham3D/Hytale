package com.inigmasgames.hytalerpg;

import com.google.gson.Gson;
import com.inigmasgames.hytalerpg.progress.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.net.URLClassLoader;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** The pre-WAL binary must receive the pre-WAL coordinated backup, never the updated store. */
class Stage13JournalRollbackTest {
    @TempDir Path directory;
    @Test void actualArchivedFRestoresItsPreWalCheckpointAfterCurrentJournalMutatesCopy()throws Exception {
        Path jar=Path.of("evidence/stage-13/cohort-f/artifacts/HytaleRPG-0.0.25.jar").toAbsolutePath();
        assertEquals("f7f55fcf05afea2a985ac2801cb4f78d346389c2e135e2dcea22e1f193bcfa83",sha(jar));
        Path server=Path.of(System.getProperty("user.home"),"AppData/Roaming/Hytale/install/pre-release/package/game/latest/Server/HytaleServer.jar");
        Path running=directory.resolve("running"),backup=directory.resolve("pre-wal-checkpoint"),restored=directory.resolve("restored");
        try(var legacy=new URLClassLoader(new java.net.URL[]{jar.toUri().toURL(),server.toUri().toURL()},ClassLoader.getPlatformClassLoader())){
            var storeClass=legacy.loadClass("com.inigmasgames.hytalerpg.progress.FileEncounterStore");
            var spawnClass=legacy.loadClass("com.inigmasgames.hytalerpg.progress.EnemyRewardRegistry$Spawn");
            var snapshotClass=legacy.loadClass("com.inigmasgames.hytalerpg.progress.EncounterContributions$Snapshot");
            var gsonClass=legacy.loadClass("com.google.gson.Gson");var gson=gsonClass.getConstructor().newInstance();
            var ledger=new EncounterContributions();ledger.begin(Stage13EncounterJournalTest.spawn());
            assertTrue(ledger.damage(Stage13EncounterJournalTest.WORLD,Stage13EncounterJournalTest.ENEMY,Stage13EncounterJournalTest.ACTOR,100,90,100,true,101));
            var snapshot=ledger.snapshot(Stage13EncounterJournalTest.WORLD,Stage13EncounterJournalTest.ENEMY);String original=new Gson().toJson(snapshot);
            Object oldStore=storeClass.getConstructor(Path.class).newInstance(running);
            Object oldSpawn=gsonClass.getMethod("fromJson",String.class,Class.class).invoke(gson,new Gson().toJson(snapshot.spawn()),spawnClass);
            storeClass.getMethod("create",spawnClass).invoke(oldStore,oldSpawn);
            Object oldSnapshot=gsonClass.getMethod("fromJson",String.class,Class.class).invoke(gson,original,snapshotClass);
            storeClass.getMethod("save",snapshotClass).invoke(oldStore,oldSnapshot);
            copy(running,backup);Map<String,String> hashes=hashes(backup);
            try(var current=new FileEncounterStore(running)){
                var runtime=Stage13EncounterJournalTest.runtime(current);Stage13EncounterJournalTest.hit(runtime,102);
                current.checkpoint();assertEquals(102,current.load(Stage13EncounterJournalTest.WORLD,Stage13EncounterJournalTest.ENEMY).orElseThrow().lastObserved());
            }
            copy(backup,restored);assertEquals(hashes,hashes(restored));assertFalse(Files.exists(restored.resolve("journal")));
            Object rollback=storeClass.getConstructor(Path.class).newInstance(restored);
            var recovered=(Optional<?>)storeClass.getMethod("load",UUID.class,UUID.class).invoke(rollback,Stage13EncounterJournalTest.WORLD,Stage13EncounterJournalTest.ENEMY);
            assertEquals(original,gsonClass.getMethod("toJson",Object.class).invoke(gson,recovered.orElseThrow()));
        }
    }
    private static void copy(Path from,Path to)throws Exception{try(var paths=Files.walk(from)){for(Path p:paths.toList()){Path target=to.resolve(from.relativize(p));if(Files.isDirectory(p))Files.createDirectories(target);else Files.copy(p,target);}}}
    private static Map<String,String> hashes(Path root)throws Exception{var result=new TreeMap<String,String>();try(var paths=Files.walk(root)){for(Path p:paths.filter(Files::isRegularFile).toList())result.put(root.relativize(p).toString(),sha(p));}return result;}
    private static String sha(Path path)throws Exception{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path)));}
}
