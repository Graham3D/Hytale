package com.inigmasgames.hytalerpg.diagnostics.hro;

import java.util.*;

/** Bounded analysis on HRO's existing writer. Never invokes gameplay, RNG or recovery. */
final class HroMasterEnemiesReport {
    enum Status { PASS, FAIL, BLOCKED, NOT_OBSERVED, NOT_APPLICABLE }
    private static final int OPERATIONS=2048,DETAILS=32;
    private final Map<String,List<Map<String,Object>>> evidence=new TreeMap<>();
    private final Map<String,Set<String>> denominators=new TreeMap<>();
    private final Map<String,Status> checks=new TreeMap<>();
    private final Set<String> effectReadbacks=new HashSet<>();
    private long omitted;
    private long omittedOperations;
    void accept(HroEvent e,String locator){
        String family=switch(e.contract()){
            case "SPAWN.ADMISSION" -> "nativeEncounters";
            case "SPAWN.PROMOTION" -> "generation";
            case "COMBAT.RECONCILE","CYCLONE.ACCOUNTING","AFFIX.PROC" -> "combat";
            case "PERSISTENCE.RECOVERY" -> e.boundary().startsWith("ME12_LEARN")?"learning":"rewardsAndPersistence";
            case "HUD.PUBLICATION" -> "presentation";
            case "PERFORMANCE" -> "performance";
            default -> null;
        };
        if(family==null)return;
        var rows=evidence.computeIfAbsent(family,k->new ArrayList<>());
        if(rows.size()<DETAILS)rows.add(Map.of("operationId",e.operationId(),"boundary",e.boundary(),"facts",e.facts(),
                "locator",locator,"utcMillis",e.utcMillis(),"worldSegment",String.valueOf(e.worldSegmentId()),"connection",String.valueOf(e.connectionId())));
        else omitted++;
        if(e.boundary().equals("RARITY_DECISION"))count("eligibleOpportunities",e.operationId());
        if(e.boundary().equals("ME12_IDENTITY"))count("tierSelections",e.operationId());
        if(e.boundary().equals("ELITE_PUBLISHED")&&"NATURAL".equals(e.facts().get("provenance")))count("publishedNaturalEncounters",e.operationId());
        if(e.boundary().equals("ME12_NATIVE_HIT"))count("observedAttacks",e.operationId().split("/",2)[0]);
        if(e.boundary().equals("ME12_REWARD_COMMITTED"))count("legitimateRewardEvents",e.operationId());
        if(e.boundary().equals("ME12_QUANTITY")){
            try{int base=Integer.parseInt(e.facts().get("baseCount")),total=Integer.parseInt(e.facts().get("finalCount"));
                check("quantityBounds",base>=0&&base<=16&&total>=base&&total<=16&&(base!=0||total==0)?Status.PASS:Status.FAIL);
            }catch(RuntimeException invalid){check("quantityBounds",Status.BLOCKED);}
        }
        if(e.boundary().equals("ME12_DEPENDENCY"))check("nativeCertification",Status.BLOCKED);
        if(e.boundary().equals("ME12_EXCLUDED"))check("excludedActorOpportunity",Status.NOT_APPLICABLE);
        if(e.boundary().equals("ME12_GENERATOR_HOLD"))check("generatorAvailability",Status.BLOCKED);
        if(e.boundary().equals("ME12_HUD"))check("serverTargetPublication",Status.PASS);
        if(e.boundary().equals("ME12_EFFECT_RESULT")&&Set.of("ME-013","ME-016").contains(String.valueOf(e.facts().get("affixId"))))effectReadbacks.add(e.facts().get("affixId"));
    }
    private void count(String name,String operation){var set=denominators.computeIfAbsent(name,k->new HashSet<>());
        if(set.size()<OPERATIONS)set.add(operation);else if(!set.contains(operation))omittedOperations++;}
    private void check(String name,Status status){checks.merge(name,status,(a,b)->a==Status.FAIL||b==Status.FAIL?Status.FAIL:a==Status.BLOCKED||b==Status.BLOCKED?Status.BLOCKED:b);}
    static Status map(String raw){return switch(raw){case "PASS"->Status.PASS;case "FAIL"->Status.FAIL;case "NOT_APPLICABLE"->Status.NOT_APPLICABLE;
        case "NOT_EXERCISED","NOT_OBSERVED"->Status.NOT_OBSERVED;default->Status.BLOCKED;};}
    Map<String,Object> snapshot(Map<String,Object> identity,Map<String,Object> rawCoverage,boolean incomplete,long drops){
        var statuses=new TreeMap<String,Status>();
        for(String key:List.of("quantityBounds","serverTargetPublication","generatorAvailability","excludedActorOpportunity"))statuses.put(key,checks.getOrDefault(key,Status.NOT_OBSERVED));
        statuses.put("nativeCertification",Status.BLOCKED);statuses.put("realSkillSource",Status.BLOCKED);statuses.put("realPassiveSource",Status.BLOCKED);
        statuses.put("connectedRendering",Status.BLOCKED);statuses.put("tierDistribution",Status.BLOCKED);
        rawCoverage.forEach((id,row)->{if(row instanceof Map<?,?> values)statuses.put("hro/"+id,map(String.valueOf(values.get("outcome"))));});
        if(incomplete||drops>0||omittedOperations>0)statuses.replaceAll((key,value)->value==Status.PASS?Status.BLOCKED:value);
        var counts=new TreeMap<String,Integer>();
        for(String key:List.of("eligibleOpportunities","tierSelections","publishedNaturalEncounters","observedAttacks","legitimateRewardEvents"))counts.put(key,denominators.getOrDefault(key,Set.of()).size());
        var report=new LinkedHashMap<String,Object>();report.put("schemaVersion",1);report.put("identity",identity);report.put("statuses",statuses);report.put("denominators",counts);
        report.put("evidence",evidence);report.put("omittedDetailOrIdentityRows",omitted);report.put("droppedEvents",drops);report.put("incomplete",incomplete);
        report.put("omittedOperationIdentities",omittedOperations);
        var effects=new TreeMap<String,Object>();
        for(var operator:com.inigmasgames.hytalerpg.enemies.EnemyAffixRegistry.Operator.values()){
            boolean instrumented=Set.of("ME-013","ME-016").contains(operator.id());
            effects.put(operator.id(),Map.of("directResourceReadback",instrumented?effectReadbacks.contains(operator.id())?incomplete||drops>0?Status.BLOCKED:Status.PASS:Status.NOT_OBSERVED:Status.BLOCKED,
                    "nativeAcceptance",Status.BLOCKED,"reason",instrumented?"Actual requested/applied resource observation; not a whole-affix semantic verdict":"Use raw combat/shield/status HRO evidence; no dedicated per-affix readback predicate in this view"));
        }
        report.put("affixReadbackCoverage",effects);
        for(String family:List.of("nativeEncounters","generation","combat","rewardsAndPersistence","learning","presentation","performance"))
            if(!evidence.containsKey(family))statuses.put("observations/"+family,Status.NOT_OBSERVED);
        report.put("axes",Map.of("implemented",true,"automatedPassed","SEE_EXACT_COMMIT_STAGE3_MANIFEST","nativeConnectedPassed",false,"ownerAccepted",false));
        report.put("releaseGate","OPEN");report.put("synthetic",Boolean.TRUE.equals(identity.get("synthetic"))
                ||identity.get("captureBuild") instanceof Map<?,?> build&&Boolean.TRUE.equals(build.get("synthetic")));
        report.put("limitations",List.of("PASS applies only to the named observed predicate; server publication does not prove rendering or combat execution",
                "Native coverage: 84 configured; 5 authored exclusions; 179 uncertified IDs; 41 catalog-only (5 retired); 16 Normal immunity exclusions",
                "Turret/Seeker projectiles, Firesteel attachment and exact role/spawn certificates remain BLOCKED",
                "Real accepted Skill/first-copy Passive sources unavailable; synthetic fixtures are not source certification",
                "70/25/5 requires automated analytical/sampling evidence; gameplay frequency is not a distribution test",
                "Missing facts are NOT_OBSERVED; unavailable providers or dependencies are BLOCKED; raw HRO evidence is retained"));
        return report;
    }
    static String markdown(Map<String,Object> report){
        var out=new StringBuilder("# Master Enemies diagnostic report\n\n");
        out.append(Boolean.TRUE.equals(report.get("synthetic"))?"SYNTHETIC OFFLINE FIXTURE. No native or owner acceptance.\n\n":"Observed capture only. No release acceptance.\n\n");
        out.append("Monster Affix: OPEN; ownerAccepted=false.\n\n");
        ((Map<?,?>)report.get("statuses")).forEach((k,v)->out.append("- ").append(k).append(": ").append(v).append('\n'));
        out.append("\nSeparate denominators: ").append(report.get("denominators")).append("\n\nDropped: ").append(report.get("droppedEvents"))
                .append("; omitted detail: ").append(report.get("omittedDetailOrIdentityRows")).append(". Full facts and event locators: master-enemies.json.\n");
        ((Map<?,?>)report.get("evidence")).forEach((family,value)->{
            out.append("\n## ").append(family).append("\n\n");
            if(value instanceof List<?> rows)for(Object row:rows.stream().limit(8).toList())out.append("- ").append(row).append('\n');
        });
        for(var limitation:(List<?>)report.get("limitations"))out.append("\n").append(limitation).append('\n');
        return out.toString();
    }
}
