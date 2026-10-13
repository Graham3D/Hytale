package com.inigmasgames.hytalerpg.diagnostics.hro;

import com.google.gson.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.api.io.TempDir;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class HroComparisonTest {
    @TempDir Path root;
    static JsonObject recipe(String outcome) {
        var recipe=HroBundle.JSON.toJsonTree(Map.of("schemaVersion",1,"contract","COMBAT.RECONCILE","scenario","single physical hit","expectation","hp-reconciliation-v1",
                "match",Map.of("cause","Physical"),"context",Map.of("configuration","c1","featureGates","g1","gear","unarmed","difficulty","normal",
                        "roleAncestry","target/v1","nativePatch","none","adapter","hro-v1","prerequisites","fixed single target","proofScope","SERVER_ONLY"),
                "approval",Map.of("by","synthetic-owner","at","2026-10-13","reason","fixture acceptance","acceptedOutcome",outcome))).getAsJsonObject();
        return recipe;
    }
    static Path bundle(Path root,String id,boolean pass,long loss,int operations,boolean origin)throws Exception {
        Path directory=Files.createDirectory(root.resolve(id));
        try(var bundle=new HroBundle(directory,Map.of("schemaVersion",1,"runId",id,"state","RECORDING","captureBuild",origin?
                Map.of("jarSha256","a".repeat(64),"serverSha256","b".repeat(64),"rpg.sourceCommit",id):Map.of("unknown","true")),Set.copyOf(HroContracts.IDS))) {
            for(int i=1;i<=operations;i++)bundle.event(new HroEvent(1,id,i,1000-i,8000+i,"COMBAT.RECONCILE","fixture","NATIVE_RESULT",id+"-different-uuid-"+i,"world",1L,1L,1L,
                    Map.of("cause","Physical","healthBefore","100","healthAfter",pass?"75":"50","accepted","25","postAbsorption","25","reconciliation",pass?"MATCH":"MISMATCH")));
            bundle.finish(loss,"STOPPED",Map.of("scope","SYNTHETIC"));
        }return directory;
    }
    Path json(String name,JsonObject object)throws IOException { Path path=root.resolve(name);Files.writeString(path,object.toString());return path; }
    @ParameterizedTest @CsvSource({"true,false,REGRESSION","false,false,PERSISTING_FAILURE","false,true,RESOLVED_FAILURE","true,true,UNCHANGED_PASS"})
    void matchesSemanticScenarioAcrossDifferentIdsAndReversedWallClocks(boolean oldPass,boolean newPass,String expected)throws Exception {
        Path old=bundle(root,"old",oldPass,0,1,true),next=bundle(root,"new",newPass,0,1,true),baseline=root.resolve("baseline.json");
        Path recipe=json("recipe.json",recipe(oldPass?"PASS":"FAIL"));HroComparison.baseline(old,recipe,baseline);
        HroComparison.compare(next,baseline,recipe,root.resolve("comparison.json"));
        assertEquals(expected,HroOffline.object(root.resolve("comparison.json")).get("comparison").getAsString());
        assertThrows(FileAlreadyExistsException.class,()->HroComparison.baseline(old,recipe,baseline));
    }
    @Test void expectationChangeRequiresExplicitApprovalAndPreservesCurrentFailure()throws Exception {
        Path old=bundle(root,"old",true,0,1,true),next=bundle(root,"new",false,0,1,true),baseline=root.resolve("baseline.json");
        JsonObject recipe=recipe("PASS");HroComparison.baseline(old,json("old.json",recipe),baseline);
        recipe.addProperty("expectation","changed-v2");
        HroComparison.compare(next,baseline,json("changed.json",recipe),root.resolve("unapproved.json"));
        assertEquals("INCOMPARABLE",HroOffline.value(HroOffline.object(root.resolve("unapproved.json")),"comparison"));
        recipe.add("expectationChange",HroBundle.JSON.toJsonTree(Map.of("fromBaseline",HroOffline.value(HroOffline.object(baseline),"baselineId"),"by","owner","at","today","reason","intentional rule change","acceptedOutcome","FAIL")));
        HroComparison.compare(next,baseline,json("approved.json",recipe),root.resolve("approved-result.json"));
        var report=HroOffline.object(root.resolve("approved-result.json"));assertEquals("INTENTIONAL_CHANGE",HroOffline.value(report,"comparison"));assertEquals("FAIL",HroOffline.value(report,"currentContractOutcome"));
    }
    @ParameterizedTest @CsvSource({"loss","ambiguous","origin","config","scenario"})
    void confoundersDoNotBecomeRegressions(String issue)throws Exception {
        Path old=bundle(root,"old",true,0,1,true),next=bundle(root,"new",false,issue.equals("loss")?1:0,issue.equals("ambiguous")?2:1,!issue.equals("origin")),baseline=root.resolve("baseline.json");
        JsonObject recipe=recipe("PASS");HroComparison.baseline(old,json("old.json",recipe),baseline);
        if(issue.equals("config"))recipe.getAsJsonObject("context").addProperty("configuration","changed");
        if(issue.equals("scenario"))recipe.addProperty("scenario","different");
        HroComparison.compare(next,baseline,json("new.json",recipe),root.resolve("result.json"));
        assertEquals("INCOMPARABLE",HroOffline.value(HroOffline.object(root.resolve("result.json")),"comparison"));
    }
    @Test void missingApprovalAndNonSemanticMatchAndTamperedBaselineRejected()throws Exception {
        Path capture=bundle(root,"run",true,0,1,true);JsonObject recipe=recipe("PASS");recipe.remove("approval");
        assertThrows(IOException.class,()->HroComparison.baseline(capture,json("noapproval.json",recipe),root.resolve("a.json")));
        JsonObject invalid=recipe("PASS");invalid.getAsJsonObject("match").addProperty("targetId","uuid");
        assertThrows(IOException.class,()->HroComparison.baseline(capture,json("identity.json",invalid),root.resolve("b.json")));
        Path approved=json("ok.json",recipe("PASS")),baseline=root.resolve("baseline.json");HroComparison.baseline(capture,approved,baseline);
        JsonObject tampered=HroOffline.object(baseline);tampered.addProperty("runId","other");Files.writeString(baseline,tampered.toString());
        assertThrows(IOException.class,()->HroComparison.compare(capture,baseline,approved,root.resolve("c.json")));
    }
    @Test void cascadesGroupOnlyExplicitCorrelation() {
        var a=new HroContracts.Finding("PLAYER.ADMISSION",1,HroContracts.Outcome.BLOCKED,"NO_BASELINE","OWNER","one","REGISTERED","GATE","segment:1","owner","KEEP");
        var b=new HroContracts.Finding("HUD.PUBLICATION",1,HroContracts.Outcome.INCONCLUSIVE,"NO_BASELINE","OWNER","two","REQUEST","MISSING","segment:2","hud","KEEP");
        assertEquals(2,HroMaps.cascades(List.of(a,b)).size());
    }
    @Test void writesSanitizedExampleComparisonForHandoff()throws Exception {
        Path reports=Files.createDirectories(Path.of("build/reports/hro"));Path example=Files.createTempDirectory(reports,"stage3-example-");
        Path old=bundle(example,"accepted-control",true,0,1,true),next=bundle(example,"failing-candidate",false,0,1,true);
        Path recipe=example.resolve("recipe.json");Files.writeString(recipe,recipe("PASS").toString());
        HroComparison.baseline(old,recipe,example.resolve("baseline.json"));HroComparison.compare(next,example.resolve("baseline.json"),recipe,example.resolve("comparison.json"));
        Files.writeString(reports.resolve("stage3-example-path.txt"),example.toAbsolutePath().toString());
    }
    @Test void sequenceGapDowngradesMechanicalReportDespiteValidChecksums()throws Exception {
        Path directory=root.resolve("gap");
        try(var bundle=new HroBundle(directory,Map.of("schemaVersion",1,"runId","gap","state","RECORDING","captureBuild","fixture"),Set.copyOf(HroContracts.IDS))) {
            bundle.event(new HroEvent(1,"gap",2,1,1,"COMBAT.RECONCILE","fixture","NATIVE_RESULT","hit","world",1L,1L,1L,
                    Map.of("healthBefore","100","healthAfter","75","accepted","25","postAbsorption","25","reconciliation","MATCH")));
            bundle.finish(0,"STOPPED",Map.of());
        }
        HroTool.analyze(directory);var report=HroOffline.object(directory.resolve("analysis.json"));
        assertFalse(report.getAsJsonObject("mechanical").get("evidenceComplete").getAsBoolean());
        assertEquals("INCONCLUSIVE",report.getAsJsonArray("findings").get(0).getAsJsonObject().get("outcome").getAsString());
    }
}
