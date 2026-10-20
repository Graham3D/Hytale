package com.inigmasgames.hytalerpg;

import com.inigmasgames.hytalerpg.progress.*;
import com.inigmasgames.hytalerpg.execution.support.ShieldEscrow;
import java.nio.file.*;
import java.net.*;
import java.util.*;
import java.security.MessageDigest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class Stage13EscrowArchiveRollbackTest {
    @TempDir Path temp;
    @Test void exactStageIReaderHonorsEscrowDebitAndCompletedRewardOnCoordinatedCopy()throws Exception{
        var jar=Path.of("evidence/stage-13/cohort-i/artifacts/HytaleRPG-0.0.25.jar").toAbsolutePath();
        assertEquals("0082fa775eb2c7c42445194e3e0736515ed21cbcef77bec42e04eca1cfbf3d36",sha(jar));
        var running=temp.resolve("running");var checkpoint=temp.resolve("coordinated");var restored=temp.resolve("restored");var actor=UUID.randomUUID();
        var state=RpgPlayerState.create(actor);var actual=SupportProgress.INITIAL.guard(SupportProgress.INITIAL.managuard().validateCapacity(50,1));
        state.support=new ShieldEscrow().prepare(actual).persisted().nextRevision();var repository=new FileRpgPlayerStateRepository(running.resolve("players"));repository.save(state);
        var rewards=new FileEarnedRewardStore(running.resolve("earned-rewards"));var reward=new EarnedReward("escrow-rollback-event",10,1,Map.of(),"ELIGIBLE_ENEMY_DEATH","","","rollback");
        rewards.award(actor,reward,new EarnedRewardStore.Authority(){
            public RewardCheckpoint current(){return RewardCheckpoint.of(state);}
            public void commit(RewardIntent intent){intent.after().applyTo(state);repository.save(state);}
        });
        var world=UUID.randomUUID();var enemy=UUID.randomUUID();
        try(var encounters=FileEncounterStore.durableV2(running.resolve("encounters"))){encounters.disqualify(world,enemy);}
        // Opaque world bytes test coordinated backup coverage, not native save compatibility/rendering.
        Files.createDirectories(running.resolve("world"));Files.writeString(running.resolve("world/opaque-fixture"),"same-checkpoint-world-bytes");
        copy(running,checkpoint);var original=hashes(checkpoint);
        state.support=actual.nextRevision();repository.save(state); // Independent later state must NOT leak into rollback.
        copy(checkpoint,restored);assertEquals(original,hashes(restored));
        var server=Path.of(System.getProperty("user.home"),"AppData/Roaming/Hytale/install/pre-release/package/game/latest/Server/HytaleServer.jar");
        try(var legacy=new URLClassLoader(new URL[]{jar.toUri().toURL(),server.toUri().toURL()},ClassLoader.getPlatformClassLoader())){
            var playerClass=legacy.loadClass("com.inigmasgames.hytalerpg.progress.RpgPlayerState");var repoClass=legacy.loadClass("com.inigmasgames.hytalerpg.progress.FileRpgPlayerStateRepository");
            var repo=repoClass.getConstructor(Path.class).newInstance(restored.resolve("players"));var load=repoClass.getMethod("load",UUID.class).invoke(repo,actor);var loaded=load.getClass().getMethod("state").invoke(load);
            var support=playerClass.getField("support").get(loaded);var guard=support.getClass().getMethod("managuard").invoke(support);
            assertEquals(50d,guard.getClass().getMethod("deficit").invoke(guard));assertEquals(25d,guard.getClass().getMethod("sharedDeficit").invoke(guard));
            assertEquals(0d,guard.getClass().getMethod("current",double.class).invoke(guard,50d));assertEquals(0d,guard.getClass().getMethod("sharedCurrent",double.class).invoke(guard,50d));
            var authorityClass=legacy.loadClass("com.inigmasgames.hytalerpg.progress.EarnedRewardStore$Authority");var checkpointClass=legacy.loadClass("com.inigmasgames.hytalerpg.progress.RewardCheckpoint");
            var authority=java.lang.reflect.Proxy.newProxyInstance(legacy,new Class<?>[]{authorityClass},(proxy,method,args)->{
                if(method.getName().equals("current"))return checkpointClass.getMethod("of",playerClass).invoke(null,loaded);
                throw new AssertionError("Completed reward replayed during rollback");
            });
            var storeClass=legacy.loadClass("com.inigmasgames.hytalerpg.progress.FileEarnedRewardStore");var oldStore=storeClass.getConstructor(Path.class).newInstance(restored.resolve("earned-rewards"));
            assertEquals(false,storeClass.getMethod("recover",UUID.class,authorityClass).invoke(oldStore,actor,authority));
            var rewardClass=legacy.loadClass("com.inigmasgames.hytalerpg.progress.EarnedReward");
            var oldReward=rewardClass.getConstructor(String.class,long.class,long.class,Map.class,String.class,String.class,String.class,String.class)
                    .newInstance("escrow-rollback-event",10L,1L,Map.of(),"ELIGIBLE_ENEMY_DEATH","","","rollback");
            var duplicate=storeClass.getMethod("award",UUID.class,rewardClass,authorityClass).invoke(oldStore,actor,oldReward,authority);
            assertEquals("DUPLICATE",duplicate.getClass().getMethod("outcome").invoke(duplicate).toString());
            var encounterClass=legacy.loadClass("com.inigmasgames.hytalerpg.progress.FileEncounterStore");
            var oldEncounter=encounterClass.getMethod("durableV2",Path.class).invoke(null,restored.resolve("encounters"));
            try{assertEquals(0,encounterClass.getMethod("pendingCount").invoke(oldEncounter));}
            finally{((AutoCloseable)oldEncounter).close();}
        }
        assertEquals(original,hashes(checkpoint));
    }
    private static void copy(Path from,Path to)throws Exception{try(var paths=Files.walk(from)){for(var p:paths.toList()){var target=to.resolve(from.relativize(p));if(Files.isDirectory(p))Files.createDirectories(target);else Files.copy(p,target);}}}
    private static Map<String,String> hashes(Path root)throws Exception{var out=new TreeMap<String,String>();try(var paths=Files.walk(root)){for(var p:paths.filter(Files::isRegularFile).toList())out.put(root.relativize(p).toString(),sha(p));}return out;}
    private static String sha(Path path)throws Exception{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path)));}
}
