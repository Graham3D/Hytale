package com.inigmasgames.hytalerpg;

import com.inigmasgames.hytalerpg.progress.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.lang.reflect.*;
import java.net.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** Actual Stage12 H archive on a copied three-store checkpoint; no live-world writes. */
class Stage13ArchivedRollbackTest {
    @TempDir Path temp;
    @Test void coordinatedCheckpointRestoresInArchivedHWithoutRewardReplay()throws Exception{
        Path jar=Path.of("evidence/stage-12/cohort-h/artifacts/HytaleRPG-0.0.24.jar").toAbsolutePath();
        assertEquals("c55dd5c1a939e5727ad01945fdc6dc0b7d7ecf1c185d94ac95b0bdae885eb87b",sha(jar));
        Path server=Path.of(System.getProperty("user.home"),"AppData/Roaming/Hytale/install/pre-release/package/game/latest/Server/HytaleServer.jar");
        try(var legacy=new URLClassLoader(new URL[]{jar.toUri().toURL(),server.toUri().toURL()},ClassLoader.getPlatformClassLoader())){
            Class<?> playerClass=legacy.loadClass("com.inigmasgames.hytalerpg.progress.RpgPlayerState"),repoClass=legacy.loadClass("com.inigmasgames.hytalerpg.progress.FileRpgPlayerStateRepository");
            UUID player=UUID.randomUUID();Object state=playerClass.getMethod("create",UUID.class).invoke(null,player);
            assertEquals(9,playerClass.getField("schemaVersion").getInt(state));Path running=temp.resolve("running"),backup=temp.resolve("checkpoint"),restored=temp.resolve("restored");
            Object repository=repoClass.getConstructor(Path.class).newInstance(running.resolve("players"));repoClass.getMethod("save",playerClass).invoke(repository,state);
            Class<?> authorityClass=legacy.loadClass("com.inigmasgames.hytalerpg.progress.EarnedRewardStore$Authority"),checkpointClass=legacy.loadClass("com.inigmasgames.hytalerpg.progress.RewardCheckpoint");
            Object authority=java.lang.reflect.Proxy.newProxyInstance(legacy,new Class<?>[]{authorityClass},(proxy,method,args)->{
                if(method.getName().equals("current"))return checkpointClass.getMethod("of",playerClass).invoke(null,state);
                if(method.getName().equals("commit")){
                    Object after=args[0].getClass().getMethod("after").invoke(args[0]);checkpointClass.getMethod("applyTo",playerClass).invoke(after,state);repoClass.getMethod("save",playerClass).invoke(repository,state);return null;
                }
                throw new UnsupportedOperationException(method.getName());
            });
            Class<?> storeClass=legacy.loadClass("com.inigmasgames.hytalerpg.progress.FileEarnedRewardStore"),rewardClass=legacy.loadClass("com.inigmasgames.hytalerpg.progress.EarnedReward");
            Object store=storeClass.getConstructor(Path.class).newInstance(running.resolve("earned-rewards"));
            Object reward=rewardClass.getConstructors()[0].newInstance("rollback-fixture",10L,1L,Map.of(),"ELIGIBLE_ENEMY_DEATH","","","rollback");
            storeClass.getMethod("award",UUID.class,rewardClass,authorityClass).invoke(store,player,reward,authority);
            // Include permanent encounter exclusion authority in the coordinated filesystem checkpoint.
            var encounters=new FileEncounterStore(running.resolve("encounters"));UUID world=UUID.randomUUID(),enemy=UUID.randomUUID();encounters.disqualify(world,enemy);encounters.close();
            copy(running,backup);Map<String,String> baseline=hashes(backup);
            var upgraded=new FileRpgPlayerStateRepository(running.resolve("players")).load(player).state();assertEquals(9,upgraded.schemaVersion);assertEquals(10,upgraded.currentXp);
            // Same player schema does not justify rolling back only the player file. Mutate the live-copy
            // independently and prove the coordinated checkpoint, not an in-place rewind, is restored.
            var catalog=com.inigmasgames.hytalerpg.content.RpgCatalog.loadCanonical();
            var compatibility=new com.inigmasgames.hytalerpg.links.CompatibilityService();
            var graph=new com.inigmasgames.hytalerpg.links.RpgLinkGraphService(catalog,compatibility);
            var current=new RpgLoadoutService(catalog,new FileRpgPlayerStateRepository(running.resolve("players")),graph,
                new com.inigmasgames.hytalerpg.links.LinkCompiler(catalog,graph,compatibility),new OwnershipEntitlementPolicy(true),ignored->{});
            current.configureEarnedRewards(new FileEarnedRewardStore(running.resolve("earned-rewards")));
            assertEquals(EarnedRewardStore.Outcome.COMMITTED,current.awardEarned(player,new EarnedReward("post-upgrade",20,0,Map.of(),"ELIGIBLE_ENEMY_DEATH","","","rollback-new")).outcome());
            assertEquals(30,new FileRpgPlayerStateRepository(running.resolve("players")).load(player).state().currentXp);
            // Restore to a fresh directory. No recursive deletion or one-sided rollback.
            copy(backup,restored);assertEquals(baseline,hashes(restored));
            Object restoredRepo=repoClass.getConstructor(Path.class).newInstance(restored.resolve("players"));Object loaded=repoClass.getMethod("load",UUID.class).invoke(restoredRepo,player);
            Object restoredState=loaded.getClass().getMethod("state").invoke(loaded);assertEquals(9,playerClass.getField("schemaVersion").getInt(restoredState));assertEquals(10,playerClass.getField("currentXp").getLong(restoredState));
            Object restoredAuthority=java.lang.reflect.Proxy.newProxyInstance(legacy,new Class<?>[]{authorityClass},(proxy,method,args)->{
                if(method.getName().equals("current"))return checkpointClass.getMethod("of",playerClass).invoke(null,restoredState);
                fail("A completed coordinated checkpoint must not replay a commit");return null;
            });
            Object restoredStore=storeClass.getConstructor(Path.class).newInstance(restored.resolve("earned-rewards"));
            assertEquals(false,storeClass.getMethod("recover",UUID.class,authorityClass).invoke(restoredStore,player,restoredAuthority));
            Object duplicate=storeClass.getMethod("award",UUID.class,rewardClass,authorityClass).invoke(restoredStore,player,reward,restoredAuthority);
            assertEquals("DUPLICATE",duplicate.getClass().getMethod("outcome").invoke(duplicate).toString());
            Class<?> encounterClass=legacy.loadClass("com.inigmasgames.hytalerpg.progress.FileEncounterStore");Object restoredEncounters=encounterClass.getConstructor(Path.class).newInstance(restored.resolve("encounters"));
            Class<?> registryClass=legacy.loadClass("com.inigmasgames.hytalerpg.progress.EnemyRewardRegistry"),originClass=legacy.loadClass("com.inigmasgames.hytalerpg.progress.EnemyRewardRegistry$Origin");
            Object registry=registryClass.getMethod("load").invoke(null);
            Object origin=originClass.getField("WILD_WORLD_SPAWN").get(null);
            var spawn=(Optional<?>)registryClass.getMethod("classify",UUID.class,UUID.class,String.class,String.class,originClass,long.class)
                    .invoke(registry,world,enemy,"Wolf_Black","Default/Zone1_Tier1/Forest_Birch",origin,100L);
            var excluded=assertThrows(InvocationTargetException.class,()->encounterClass.getMethod("create",spawn.orElseThrow().getClass()).invoke(restoredEncounters,spawn.orElseThrow()));
            assertEquals("ENCOUNTER_PERMANENTLY_DISQUALIFIED",excluded.getCause().getMessage());
        }
    }
    private static void copy(Path from,Path to)throws Exception{try(var paths=Files.walk(from)){for(Path p:paths.toList()){Path target=to.resolve(from.relativize(p));if(Files.isDirectory(p))Files.createDirectories(target);else Files.copy(p,target);}}}
    private static Map<String,String> hashes(Path root)throws Exception{var out=new TreeMap<String,String>();try(var paths=Files.walk(root)){for(Path p:paths.filter(Files::isRegularFile).toList())out.put(root.relativize(p).toString(),sha(p));}return out;}
    private static String sha(Path path)throws Exception{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path)));}
}
