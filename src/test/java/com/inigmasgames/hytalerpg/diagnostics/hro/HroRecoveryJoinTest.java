package com.inigmasgames.hytalerpg.diagnostics.hro;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class HroRecoveryJoinTest {
    @TempDir Path root;
    private Path bundle(String run,String boundary,String identity,long sequence,long dropped)throws Exception {
        Path path=root.resolve(run);
        try(var b=new HroBundle(path,Map.of("schemaVersion",1,"runId",run,"state","RECORDING","captureBuild",identity),Set.copyOf(HroContracts.IDS))){
            if(boundary!=null)b.event(new HroEvent(1,run,sequence,boundary.equals("SAVED")?100:200,1,
                    "PERSISTENCE.RECOVERY","FileRpgPlayerStateRepository",boundary,"rpg-state",null,null,null,4L,
                    Map.of("durableSubject","subject","schema","13","revision","4","fingerprint","existing-checksum")));
            b.finish(dropped,"STOPPED",Map.of("fixture",true));
        }
        return path;
    }
    private String join(Path first,Path second)throws Exception{HroTool.recoveryJoin(first,second);return Files.readString(second.resolve("recovery-analysis.json"));}
    @Test void verifiedOrderedDurableJoinPasses()throws Exception{assertTrue(join(bundle("a","SAVED","build",1,0),bundle("b","LOADED","build",1,0)).contains("\"outcome\":\"PASS\""));}
    @Test void missingEarlierIsInconclusive()throws Exception{assertTrue(join(bundle("a",null,"build",1,0),bundle("b","LOADED","build",1,0)).contains("EARLIER_DURABLE_EVIDENCE_MISSING"));}
    @Test void droppedEarlierCannotPass()throws Exception{String report=join(bundle("a","SAVED","build",1,1),bundle("b","LOADED","build",1,0));assertTrue(report.contains("CAPTURE_INCOMPLETE"));assertFalse(report.contains("\"outcome\":\"PASS\""));}
    @Test void changedBuildIsNotAssumedCompatible()throws Exception{assertTrue(join(bundle("a","SAVED","old",1,0),bundle("b","LOADED","new",1,0)).contains("BUILD_IDENTITY_DIFFERS"));}
    @Test void sequenceGapCannotBecomeCertainEvenWithValidChecksums()throws Exception{String report=join(bundle("a","SAVED","build",2,0),bundle("b","LOADED","build",1,0));assertTrue(report.contains("RECOVERY_EVIDENCE_INCOMPLETE_OR_INVALID"));assertFalse(report.contains("\"outcome\":\"PASS\""));}
    @Test void tamperedBundleIsRejectedBeforeJoining()throws Exception{Path first=bundle("a","SAVED","build",1,0),second=bundle("b","LOADED","build",1,0);Files.writeString(first.resolve("integrity.json"),"{}");assertThrows(java.io.IOException.class,()->HroTool.recoveryJoin(first,second));}
}
