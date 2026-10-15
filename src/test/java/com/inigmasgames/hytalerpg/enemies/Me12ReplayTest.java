package com.inigmasgames.hytalerpg.enemies;

import com.google.gson.*;
import com.inigmasgames.hytalerpg.gear.GearRandom;
import com.inigmasgames.hytalerpg.progress.RewardIntent;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class Me12ReplayTest {
    @TempDir Path directory;
    @Test void S06CrossJvmPlansAreByteIdenticalAndLegacyVectorsRemainUnchanged() throws Exception {
        var cp=new ArrayList<String>();for(var type:List.of(Me12ReplayProcess.class,Me12Planner.class,Gson.class))cp.add(Path.of(type.getProtectionDomain().getCodeSource().getLocation().toURI()).toString());
        cp.add(Path.of("build/resources/main").toAbsolutePath().toString());var transcripts=new ArrayList<String>();
        for(int i=0;i<2;i++){
            Path output=directory.resolve("replay-"+i+".txt");var command=new ArrayList<String>();command.add(Path.of(System.getProperty("java.home"),"bin/java.exe").toString());
            if(i==1)command.add("-XX:TieredStopAtLevel=1");Collections.addAll(command,"-cp",String.join(System.getProperty("path.separator"),cp),Me12ReplayProcess.class.getName());
            command.add(1,"-Djava.io.tmpdir="+System.getProperty("java.io.tmpdir"));
            var childCommand=new ProcessBuilder(command).redirectErrorStream(true).redirectOutput(output.toFile());
            childCommand.environment().remove("JAVA_TOOL_OPTIONS"); // The inherited JVM banner is not a planner transcript.
            var child=childCommand.start();
            assertTrue(child.waitFor(30,TimeUnit.SECONDS));assertEquals(0,child.exitValue(),Files.readString(output));transcripts.add(Files.readString(output));
        }
        assertEquals(transcripts.get(0),transcripts.get(1));assertEquals(128,transcripts.getFirst().lines().count());
        Path report=Path.of("build/reports/me12/stage2-replay.json");Files.createDirectories(report.getParent());
        Files.writeString(report,new Gson().toJson(Map.of("runtime",System.getProperty("java.runtime.version"),"processes",2,"plans",128,"transcriptSha256",RewardIntent.digest(transcripts.getFirst()),"firstPlan",JsonParser.parseString(transcripts.getFirst().lines().findFirst().orElseThrow()))));
        var legacy=JsonParser.parseString(Files.readString(Path.of("src/test/resources/enemies/me12-v1-wire/legacy-rng.json"))).getAsJsonObject();
        var random=new GearRandom("master-enemies-v1.1/"+EnemyBalance.base().revision()+"/ME12-baseline-golden");
        assertEquals(legacy.get("rarity").getAsLong(),random.stream("me.rarity").nextLong(1000));assertEquals(legacy.get("roster").getAsInt(),random.stream("me.minion-count").nextInt(2,6));
        assertEquals(legacy.get("gear").getAsLong(),new GearRandom("ME12-baseline-golden").stream("rarity").nextLong(1000000));
    }
}
