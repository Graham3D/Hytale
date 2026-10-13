package com.inigmasgames.hytalerpg.diagnostics.hro;

import com.google.gson.*;
import java.io.*;
import java.nio.file.*;
import java.util.*;

/** Immutable, explicitly approved per-contract evidence baselines. Offline only. */
final class HroComparison {
    private HroComparison() { }
    private static final List<String> CONTEXT=List.of("configuration","featureGates","gear","difficulty","roleAncestry","nativePatch","adapter","prerequisites","proofScope");
    record Observation(String outcome,String reason,String operation,List<String> evidence,String manifestHash,
                       JsonObject captureBuild,String quality) { }

    static void baseline(Path bundle,Path recipePath,Path output) throws IOException {
        JsonObject recipe=HroOffline.object(recipePath);validate(recipe);
        JsonObject approval=recipe.getAsJsonObject("approval");
        if(approval==null)throw new IOException("EXPLICIT_APPROVAL_REQUIRED");
        HroOffline.required(approval,"by");HroOffline.required(approval,"at");HroOffline.required(approval,"reason");
        Observation observed=observe(bundle,recipe);
        if(!knownBuild(observed.captureBuild()))throw new IOException("BASELINE_CAPTURE_ORIGIN_MISSING");
        if(!observed.quality().equals("COMPLETE")||!(observed.outcome().equals("PASS")||observed.outcome().equals("FAIL")||observed.outcome().equals("BLOCKED")))
            throw new IOException("BASELINE_EVIDENCE_NOT_QUALIFIED");
        if(!observed.outcome().equals(HroOffline.required(approval,"acceptedOutcome")))throw new IOException("APPROVAL_OUTCOME_MISMATCH");
        var content=new LinkedHashMap<String,Object>();content.put("schemaVersion",1);content.put("kind","HRO_CONTRACT_BASELINE");
        content.put("recipe",recipe);content.put("observation",observed);content.put("evidenceIndexSha256",HroBundle.sha256(bundle.resolve("checksums.sha256")));
        content.put("runId",HroOffline.object(bundle.resolve("manifest.json")).get("runId"));
        content.put("implementationInventory",HroMaps.implementation());content.put("approvalIsOwnerSuppliedNotAuthentication",true);
        content.put("baselineId",HroOffline.hash(content));HroOffline.create(output,content);
    }
    static void compare(Path bundle,Path baselinePath,Path recipePath,Path output) throws IOException {
        compare(bundle,baselinePath,HroOffline.object(recipePath),output);
    }
    static void compare(Path bundle,Path baselinePath,JsonObject recipe,Path output) throws IOException {
        compare(bundle,baselinePath,recipe,output,()->0,Long.MAX_VALUE);
    }
    static final class AnalysisBudgetExceeded extends RuntimeException { }
    static void compare(Path bundle,Path baselinePath,JsonObject recipe,Path output,
                        java.util.function.LongSupplier clock,long deadline) throws IOException {
        JsonObject baseline=HroOffline.object(baselinePath);String identity=HroOffline.required(baseline,"baselineId");
        JsonObject signed=baseline.deepCopy();signed.remove("baselineId");
        if(!identity.equals(HroOffline.hash(signed))||!HroOffline.value(baseline,"kind").equals("HRO_CONTRACT_BASELINE")
                ||!HroOffline.value(baseline,"schemaVersion").equals("1"))throw new IOException("INVALID_BASELINE");
        validate(recipe);
        JsonObject previous=baseline.getAsJsonObject("recipe"),prior=baseline.getAsJsonObject("observation");
        Observation current=observe(bundle,recipe,clock,deadline);var confounders=new ArrayList<String>();
        for(String key:List.of("contract","scenario","match"))if(!Objects.equals(previous.get(key),recipe.get(key)))confounders.add(key+" differs");
        for(String key:CONTEXT)if(!Objects.equals(previous.getAsJsonObject("context").get(key),recipe.getAsJsonObject("context").get(key)))confounders.add(key+" differs");
        if(!current.quality().equals("COMPLETE"))confounders.add(current.quality());
        if(!knownBuild(current.captureBuild())||!knownBuild(prior.getAsJsonObject("captureBuild")))confounders.add("capture origin missing");
        String before=HroOffline.value(prior,"outcome"),after=current.outcome();
        boolean expectationChanged=!Objects.equals(previous.get("expectation"),recipe.get("expectation"));
        JsonObject change=recipe.has("expectationChange")?recipe.getAsJsonObject("expectationChange"):new JsonObject();
        if(recipe.has("expectationChange"))for(String key:List.of("fromBaseline","acceptedOutcome","by","at","reason"))HroOffline.required(change,key);
        boolean approvedChange=expectationChanged&&identity.equals(HroOffline.value(change,"fromBaseline"))
                &&after.equals(HroOffline.value(change,"acceptedOutcome"))&&!HroOffline.value(change,"by").equals("UNKNOWN")
                &&!HroOffline.value(change,"reason").equals("UNKNOWN")&&!HroOffline.value(change,"at").equals("UNKNOWN");
        if(expectationChanged&&!approvedChange)confounders.add("expectation changed without matching approval");
        String comparison=!confounders.isEmpty()?"INCOMPARABLE":approvedChange?"INTENTIONAL_CHANGE":classify(before,after);
        var report=new LinkedHashMap<String,Object>();report.put("schemaVersion",1);report.put("baselineId",identity);
        report.put("contract",recipe.get("contract"));report.put("scenario",recipe.get("scenario"));report.put("comparison",comparison);
        report.put("currentContractOutcome",after);report.put("priorContractOutcome",before);report.put("current",current);report.put("prior",prior);
        report.put("confounders",confounders);report.put("expectationChange",change);report.put("causalAttribution","UNDETERMINED; source/native changes are not proof of cause");
        report.put("compatibility",HroMaps.compatibility(prior.getAsJsonObject("captureBuild"),current.captureBuild()));
        report.put("contextProvenance","OPERATOR_DECLARED; verify recipe against evidence; UUIDs and wall clocks are not matching keys");
        HroOffline.create(output,report);
    }
    static String classify(String before,String after) {
        if(before.equals("FAIL")&&after.equals("FAIL"))return "PERSISTING_FAILURE";
        if(before.equals("PASS")&&after.equals("FAIL"))return "REGRESSION";
        if(before.equals("FAIL")&&after.equals("PASS"))return "RESOLVED_FAILURE";
        if(before.equals("PASS")&&after.equals("PASS"))return "UNCHANGED_PASS";
        if(before.equals("BLOCKED")&&after.equals("BLOCKED"))return "PERSISTING_BLOCKER";
        return "INCOMPARABLE";
    }
    private static boolean knownBuild(JsonObject build) { return build!=null&&HroOffline.value(build,"jarSha256").matches("[0-9a-fA-F]{64}")
            &&HroOffline.value(build,"serverSha256").matches("[0-9a-fA-F]{64}"); }
    private static void validate(JsonObject recipe)throws IOException {
        if(!HroOffline.value(recipe,"schemaVersion").equals("1"))throw new IOException("RECIPE_SCHEMA");
        if(!HroContracts.IDS.contains(HroOffline.required(recipe,"contract")))throw new IOException("UNKNOWN_CONTRACT");
        HroOffline.required(recipe,"scenario");HroOffline.required(recipe,"expectation");
        if(!recipe.has("match")||!recipe.get("match").isJsonObject()||recipe.getAsJsonObject("match").size()==0||recipe.getAsJsonObject("match").size()>12)throw new IOException("SEMANTIC_MATCH_REQUIRED");
        for(var e:recipe.getAsJsonObject("match").entrySet()) {
            if(!e.getValue().isJsonPrimitive()||e.getValue().getAsString().length()>192
                    ||e.getKey().matches("(?i).*(uuid|operationId|rootId|targetId|itemId|receiptId|generation|timestamp).*"))throw new IOException("NON_SEMANTIC_MATCH");
        }
        if(!recipe.has("context")||!recipe.get("context").isJsonObject())throw new IOException("CONTEXT_REQUIRED");
        for(String key:CONTEXT)HroOffline.required(recipe.getAsJsonObject("context"),key);
    }
    static Observation observe(Path bundle,JsonObject recipe)throws IOException {
        return observe(bundle,recipe,()->0,Long.MAX_VALUE);
    }
    private static Observation observe(Path bundle,JsonObject recipe,java.util.function.LongSupplier clock,long deadline)throws IOException {
        HroTool.verifyChecksums(bundle);JsonObject manifest=HroOffline.object(bundle.resolve("manifest.json"));
        if(clock.getAsLong()>=deadline)throw new AnalysisBudgetExceeded();
        JsonObject integrity=HroOffline.object(bundle.resolve("integrity.json"));
        String contract=HroOffline.required(recipe,"contract"),run=HroOffline.required(manifest,"runId");
        var operations=new LinkedHashMap<String,Map<String,String>>();long[] count={0};boolean[] valid={true};
        HroTool.read(bundle,(event,locator)->{
            if(!run.equals(event.runId())||event.schemaVersion()!=1||event.sequence()!=++count[0])valid[0]=false;
            if((count[0]&255)==0&&clock.getAsLong()>=deadline)throw new AnalysisBudgetExceeded();
            if(!event.contract().equals(contract))return;
            if(operations.size()>=2048&&!operations.containsKey(event.operationId())){valid[0]=false;return;}
            var facts=operations.computeIfAbsent(event.operationId(),ignored->new HashMap<>());
            event.facts().forEach((key,value)->{if(recipe.getAsJsonObject("match").has(key)) {
                String prior=facts.putIfAbsent(key,value);if(prior!=null&&!prior.equals(value))facts.put(key,"AMBIGUOUS");
            }});
        });
        List<String> matches=operations.entrySet().stream().filter(entry->recipe.getAsJsonObject("match").entrySet().stream()
                .allMatch(match->match.getValue().getAsString().equals(entry.getValue().get(match.getKey())))).map(Map.Entry::getKey).toList();
        JsonObject build=manifest.has("captureBuild")&&manifest.get("captureBuild").isJsonObject()?manifest.getAsJsonObject("captureBuild"):new JsonObject();
        String quality=!valid[0]||!HroOffline.value(manifest,"eventCount").equals(Long.toString(count[0]))?"INVALID_SEQUENCE_OR_ORIGIN":
                !HroOffline.value(manifest,"state").equals("COMPLETE")||!HroOffline.value(integrity,"evidenceComplete").equals("true")?"INCOMPLETE_CAPTURE":
                matches.size()!=1?"MISSING_OR_AMBIGUOUS_SCENARIO":"COMPLETE";
        if(matches.size()!=1)return new Observation("INCONCLUSIVE",quality,"NONE",List.of(),HroBundle.sha256(bundle.resolve("manifest.json")),build,quality);
        Set<String> registered=new HashSet<>();HroOffline.object(bundle.resolve("capabilities.json")).getAsJsonArray("registeredContracts").forEach(e->registered.add(e.getAsString()));
        var evaluator=new HroContracts(registered);var evidence=new ArrayList<String>();String operation=matches.getFirst();
        HroTool.read(bundle,(event,locator)->{if(event.contract().equals(contract)&&event.operationId().equals(operation)) {
            evaluator.accept(event,locator);if(evidence.size()<32)evidence.add(locator);
        }if((evidence.size()&255)==0&&clock.getAsLong()>=deadline)throw new AnalysisBudgetExceeded();});
        var findings=evaluator.findings(!quality.equals("COMPLETE"));
        var finding=findings.stream().filter(f->f.contract().equals(contract)&&f.operationId().equals(operation)).reduce((a,b)->b);
        String outcome=finding.map(f->f.outcome().name()).orElse("INCONCLUSIVE"),reason=finding.map(HroContracts.Finding::firstDivergence).orElse("NO_TERMINAL_EVIDENCE");
        return new Observation(outcome,reason,operation,List.copyOf(evidence),HroBundle.sha256(bundle.resolve("manifest.json")),build,quality);
    }
}
