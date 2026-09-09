package com.inigmasgames.hytalerpg;

import com.inigmasgames.hytalerpg.progress.*;
import com.inigmasgames.hytalerpg.execution.support.ShieldEscrow;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;
import static com.inigmasgames.hytalerpg.ShieldHandoffCrashProcess.*;

class Stage13ShieldHandoffRecoveryTest {
    @TempDir Path root;
    private static final List<Map<String,Object>> evidence=new CopyOnWriteArrayList<>();
    @ParameterizedTest @ValueSource(strings={"BEFORE_DEBIT","DEBIT_BEFORE_PUBLICATION","OWNER_HIT","SHARED_HIT","CLEAN_SETTLEMENT","ENCOUNTER_CONTRIBUTION_DURABLE","ENCOUNTER_PLAN_DURABLE"})
    void actualHaltNeverRecoversFreeShieldOrDuplicatesAnAcknowledgedDeath(String boundary)throws Exception{
        halt(boundary);
        for(int restart=0;restart<3;restart++){
            if(boundary.startsWith("ENCOUNTER_")){
                try(var store=FileEncounterStore.durableV2(root.resolve("encounters"))){
                    assertEquals(1,store.journalSequence());
                    if(boundary.equals("ENCOUNTER_CONTRIBUTION_DURABLE")){
                        assertEquals(10,store.load(WORLD,ENEMY).orElseThrow().credits().getFirst().actualAmount());
                        assertTrue(store.death(WORLD,ENEMY).isEmpty());assertEquals(0,store.pendingCount());
                    }else{
                        var catalog=com.inigmasgames.hytalerpg.content.RpgCatalog.loadCanonical();var compatibility=new com.inigmasgames.hytalerpg.links.CompatibilityService();
                        var graph=new com.inigmasgames.hytalerpg.links.RpgLinkGraphService(catalog,compatibility);
                        try(var players=new RpgLoadoutService(catalog,new FileRpgPlayerStateRepository(root.resolve("players")),graph,
                                new com.inigmasgames.hytalerpg.links.LinkCompiler(catalog,graph,compatibility),new OwnershipEntitlementPolicy(true),ignored->{})){
                            players.configureEarnedRewards(new FileEarnedRewardStore(root.resolve("rewards")));
                            store.drain(8,players::awardEarned);assertEquals(0,store.pendingCount());
                            var state=players.getLoadout(PLAYER).state();assertEquals(131,state.currentXp);assertEquals(1,state.rewards.sequence());
                        }
                    }
                }
            }else{
                var state=new FileRpgPlayerStateRepository(root.resolve("players")).load(PLAYER).state();
                double expected=boundary.equals("BEFORE_DEBIT")?0:boundary.equals("CLEAN_SETTLEMENT")?20:50;
                assertEquals(expected,state.support.managuard().deficit());
                // Existing owner hit raises the shared deficit floor by 10; the subsequent
                // ally hit consumes another 10. Escrow must preserve that authored coupling.
                assertEquals(boundary.equals("BEFORE_DEBIT")?0:boundary.equals("CLEAN_SETTLEMENT")?20:25,state.support.managuard().sharedDeficit());
                var escrow=new ShieldEscrow();assertEquals(10,escrow.absorb(state.support.managuard(),10,50,false).damageRemaining());
                assertEquals(10,escrow.absorb(state.support.managuard(),10,50,true).damageRemaining());
            }
        }
        evidence.add(Map.of("boundary",boundary,"exitCode",73,"restarts",3,"processHaltNotPowerLoss",true));
    }
    private void halt(String boundary)throws Exception{
        var cp=new ArrayList<String>();for(var type:List.of(ShieldHandoffCrashProcess.class,FileEncounterStore.class,com.google.gson.Gson.class))cp.add(Path.of(type.getProtectionDomain().getCodeSource().getLocation().toURI()).toString());
        cp.add(Path.of("build/resources/main").toAbsolutePath().toString());
        var process=new ProcessBuilder(Path.of(System.getProperty("java.home"),"bin/java.exe").toString(),"-cp",String.join(System.getProperty("path.separator"),cp),ShieldHandoffCrashProcess.class.getName(),root.toString(),boundary)
                .redirectErrorStream(true).redirectOutput(root.resolve("halt.log").toFile()).start();
        try{assertTrue(process.waitFor(20,TimeUnit.SECONDS));assertEquals(73,process.exitValue(),Files.readString(root.resolve("halt.log")));}
        finally{if(process.isAlive())process.destroyForcibly();}
    }
    @AfterAll static void evidence()throws Exception{var path=Path.of("build/stage13-hardening/handoff-crash-matrix.json");Files.createDirectories(path.getParent());Files.writeString(path,new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(evidence));}
}
