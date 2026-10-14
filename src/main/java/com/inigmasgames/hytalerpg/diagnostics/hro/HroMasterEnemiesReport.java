package com.inigmasgames.hytalerpg.diagnostics.hro;

import java.util.*;

/** Bounded analysis on HRO's existing writer. Never invokes gameplay, RNG or recovery. */
final class HroMasterEnemiesReport {
    enum Status { PASS, FAIL, BLOCKED, NOT_OBSERVED, NOT_APPLICABLE }
    private static final int OPERATIONS=2048,DETAILS=32;
    private final Map<String,List<Map<String,Object>>> evidence=new TreeMap<>();
    private final Map<String,Set<String>> denominators=new TreeMap<>();
    private final Map<String,Status> checks=new TreeMap<>();
    private final Map<String,Status> effectReadbacks=new HashMap<>();
    private final Set<String> selectedAffixes=new HashSet<>();
    private final Map<String,Status> operatorReadbacks=new TreeMap<>();
    /** Identity-scoped observations. A named readback is narrower than complete affix acceptance. */
    private final Map<String,Map<String,Map<String,Status>>> execution=new TreeMap<>();
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
        if(e.boundary().equals("ELITE_PUBLISHED")&&"NATURAL".equals(e.facts().get("provenance"))&&"2".equals(e.facts().get("schemaVersion"))
                &&String.valueOf(e.facts().get("nativeSourceReceipt")).startsWith("native-job/"))count("publishedNaturalV2Encounters",e.operationId());
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
        if(e.boundary().equals("ME12_EFFECT_RESULT")&&Set.of("ME-013","ME-016").contains(String.valueOf(e.facts().get("affixId")))){
            Status readback;
            try{double requested=Double.parseDouble(e.facts().get("requested")),actual=Double.parseDouble(e.facts().get("applied"));
                readback=!Double.isFinite(requested)||!Double.isFinite(actual)||actual<0||requested<0||actual>requested+1e-5?Status.FAIL:
                        "APPLIED".equals(e.facts().get("result"))&&actual>0?Status.PASS:
                        "EMPTY".equals(e.facts().get("result"))&&actual==0?Status.NOT_APPLICABLE:Status.BLOCKED;
            }catch(RuntimeException invalid){readback=Status.BLOCKED;}
            effectReadbacks.merge(e.facts().get("affixId"),readback,HroMasterEnemiesReport::combine);
        }
        if(e.boundary().equals("ME12_SELECTED_AFFIX")&&operator(e.facts().get("affixId")))selectedAffixes.add(e.facts().get("affixId"));
        if(e.boundary().equals("ME12_OPERATOR_READBACK")&&Set.of("ME-014","ME-025").contains(e.facts().get("affixId"))
                &&"NATIVE_EFFECT_PRESENT_AFTER_APPLICATION".equals(e.facts().get("predicate"))){
            var status=map(e.facts().get("status"));
            if(status==Status.PASS&&!"true".equals(e.facts().get("present")))status=Status.FAIL;
            operatorReadbacks.merge(e.facts().get("affixId"),status,(a,b)->a==Status.FAIL||b==Status.FAIL?Status.FAIL:a==Status.BLOCKED||b==Status.BLOCKED?Status.BLOCKED:b);
        }
        if(e.contract().equals("AFFIX.PROC")&&e.provider().equals("MasterEnemies")
                &&e.boundary().equals("ME12_OPERATOR_READBACK")&&operator(e.facts().get("affixId"))){
            var id=e.facts().get("affixId");var predicate=e.facts().get("predicate");
            var actor=e.facts().get("nativeActor");var generation=e.facts().get("generation");
            if(requiredPredicates(id).contains(predicate)&&actor!=null&&generation!=null
                    &&e.worldSegmentId()!=null&&e.operationId()!=null)
                execution(id,e.worldSegmentId()+"/"+actor+"/"+generation+"/"+e.operationId(),
                        predicate,map(e.facts().get("status")));
        }
    }
    private void count(String name,String operation){var set=denominators.computeIfAbsent(name,k->new HashSet<>());
        if(set.size()<OPERATIONS)set.add(operation);else if(!set.contains(operation))omittedOperations++;}
    private void execution(String affix,String operation,String predicate,Status status){
        var operations=execution.computeIfAbsent(affix,k->new TreeMap<>());
        if(!operations.containsKey(operation)&&operations.size()>=OPERATIONS){omittedOperations++;return;}
        operations.computeIfAbsent(operation,k->new TreeMap<>()).merge(predicate,status,HroMasterEnemiesReport::combine);
    }
    private void check(String name,Status status){checks.merge(name,status,(a,b)->a==Status.FAIL||b==Status.FAIL?Status.FAIL:a==Status.BLOCKED||b==Status.BLOCKED?Status.BLOCKED:b);}
    private static Status combine(Status a,Status b){return a==Status.FAIL||b==Status.FAIL?Status.FAIL:a==Status.BLOCKED||b==Status.BLOCKED?Status.BLOCKED:a==Status.PASS||b==Status.PASS?Status.PASS:b;}
    static Status map(String raw){if(raw==null)return Status.BLOCKED;return switch(raw){case "PASS"->Status.PASS;case "FAIL"->Status.FAIL;case "NOT_APPLICABLE"->Status.NOT_APPLICABLE;
        case "NOT_EXERCISED","NOT_OBSERVED"->Status.NOT_OBSERVED;default->Status.BLOCKED;};}
    Map<String,Object> snapshot(Map<String,Object> identity,Map<String,Object> rawCoverage,boolean incomplete,long drops){
        boolean synthetic=Boolean.TRUE.equals(identity.get("synthetic"))
                ||identity.get("captureBuild") instanceof Map<?,?> source&&Boolean.TRUE.equals(source.get("synthetic"));
        boolean identified=identity.get("captureBuild") instanceof Map<?,?> source
                &&String.valueOf(source.get("rpg.sourceCommit")).matches("[0-9a-f]{40}")
                &&String.valueOf(source.get("jarSha256")).matches("(?i)[0-9a-f]{64}");
        var statuses=new TreeMap<String,Status>();
        for(String key:List.of("quantityBounds","serverTargetPublication","generatorAvailability","excludedActorOpportunity"))statuses.put(key,checks.getOrDefault(key,Status.NOT_OBSERVED));
        statuses.put("nativeCertification",Status.BLOCKED);statuses.put("realSkillSource",Status.BLOCKED);statuses.put("realPassiveSource",Status.BLOCKED);
        statuses.put("connectedRendering",Status.BLOCKED);statuses.put("tierDistribution",Status.BLOCKED);
        rawCoverage.forEach((id,row)->{if(row instanceof Map<?,?> values)statuses.put("hro/"+id,map(String.valueOf(values.get("outcome"))));});
        if(incomplete||drops>0||omittedOperations>0)statuses.replaceAll((key,value)->value==Status.PASS?Status.BLOCKED:value);
        var counts=new TreeMap<String,Integer>();
        for(String key:List.of("eligibleOpportunities","tierSelections","publishedNaturalEncounters","publishedNaturalV2Encounters","observedAttacks","legitimateRewardEvents"))counts.put(key,denominators.getOrDefault(key,Set.of()).size());
        var report=new LinkedHashMap<String,Object>();report.put("schemaVersion",1);report.put("identity",identity);report.put("statuses",statuses);report.put("denominators",counts);
        report.put("evidence",evidence);report.put("omittedDetailOrIdentityRows",omitted);report.put("droppedEvents",drops);report.put("incomplete",incomplete);
        report.put("omittedOperationIdentities",omittedOperations);
        var effects=new TreeMap<String,Object>();
        for(var operator:com.inigmasgames.hytalerpg.enemies.EnemyAffixRegistry.Operator.values()){
            var id=operator.id();var required=requiredPredicates(id);var observed=new TreeMap<String,Status>();
            var operations=execution.getOrDefault(id,Map.of());
            for(var predicate:required){
                Status aggregate=Status.NOT_OBSERVED;
                for(var readbacks:operations.values())aggregate=combine(aggregate,readbacks.getOrDefault(predicate,Status.NOT_OBSERVED));
                observed.put(predicate,downgrade(aggregate,synthetic||!identified||incomplete||drops>0||omittedOperations>0));
            }
            // Some requirements need mutually exclusive scenarios (for example Horde
            // publication and rollback). Only an affix-specific causal validator may
            // combine them into a complete PASS; named events alone cannot.
            Status acceptance=identified&&!synthetic&&observed.containsValue(Status.FAIL)?Status.FAIL:Status.BLOCKED;
            boolean instrumented=Set.of("ME-013","ME-016").contains(operator.id());
            var status=operatorReadbacks.getOrDefault(operator.id(),Status.NOT_OBSERVED);
            if((incomplete||drops>0||omittedOperations>0)&&status==Status.PASS)status=Status.BLOCKED;
            effects.put(operator.id(),Map.of("selected",selectedAffixes.contains(operator.id()),
                    "directResourceReadback",instrumented?downgrade(effectReadbacks.getOrDefault(operator.id(),Status.NOT_OBSERVED),incomplete||drops>0||omittedOperations>0):Status.NOT_APPLICABLE,
                    "nativeEffectPresenceReadback",Set.of("ME-014","ME-025").contains(operator.id())?status:Status.NOT_APPLICABLE,
                    "nativeAcceptance",acceptance,"requiredExecutionEvidence",requiredExecutionEvidence(operator.ordinal()+1),
                    "executionPredicates",observed,"observedOperations",operations.size(),
                    "reason","Named owner readbacks are partial; full causal joins, fault scenarios and connected native acceptance remain required"));
        }
        report.put("affixReadbackCoverage",effects);
        for(String family:List.of("nativeEncounters","generation","combat","rewardsAndPersistence","learning","presentation","performance"))
            if(!evidence.containsKey(family))statuses.put("observations/"+family,Status.NOT_OBSERVED);
        report.put("axes",Map.of("implemented",true,"automatedPassed","SEE_EXACT_COMMIT_STAGE3_MANIFEST","nativeConnectedPassed",false,"ownerAccepted",false));
        report.put("releaseGate","OPEN");report.put("synthetic",synthetic);
        report.put("limitations",List.of("PASS applies only to the named observed predicate; server publication does not prove rendering or combat execution",
                "Native coverage: 84 configured; 5 authored exclusions; 179 uncertified IDs; 41 catalog-only (5 retired); 16 Normal immunity exclusions",
                "Turret/Seeker modern projectile route has offline fixtures, but production role certificates and complete native-owner lifecycle fixture remain missing; Firesteel incoming attachment needs owner-boundary proof",
                "Real accepted Skill/first-copy Passive sources unavailable; synthetic fixtures are not source certification",
                "70/25/5 requires automated analytical/sampling evidence; gameplay frequency is not a distribution test",
                "Missing facts are NOT_OBSERVED; unavailable providers or dependencies are BLOCKED; raw HRO evidence is retained"));
        return report;
    }
    private static boolean operator(String id){return id!=null&&id.matches("ME-0(0[1-9]|1[0-9]|2[0-7])");}
    /** Exact readbacks needed at the existing native owners; a missing owner stays BLOCKED. */
    static List<String> requiredPredicates(String id){
        if(!operator(id))throw new IllegalArgumentException("ME12_OPERATOR_ID");
        return switch(Integer.parseInt(id.substring(3))){
            case 1->List.of("NATIVE_MOVEMENT_APPLIED","NATIVE_RECOVERY_PRESERVED");
            case 2->List.of("PHYSICAL_DELTA_FROZEN","NATIVE_HEALTH_LOSS");
            case 3->List.of("ELEMENTAL_MITIGATION_APPLIED","NATIVE_IMMUNITY_AND_CAP_PRESERVED");
            case 4->List.of("DEFENSE_RATING_APPLIED","PHYSICAL_MITIGATION_APPLIED");
            case 5,6,7,8,9,10,11->List.of("ELEMENTAL_DELTA_APPLIED","DEFENSIVE_RESISTANCE_APPLIED","NATIVE_EFFECTS_PRESERVED");
            case 12->List.of("SPECTRAL_CHANNEL_FROZEN","NATIVE_COMPONENT_RECEIPT");
            case 13->List.of("RESOURCE_APPLIED","OPPORTUNITY_LOCK","RESOURCE_CAP");
            case 14->List.of("NATIVE_EFFECT_PRESENT_AFTER_APPLICATION","OUTGOING_REDUCTION_APPLIED","EFFECT_EXPIRED");
            case 15->List.of("NATIVE_IMPULSE_PRESERVED","SINGLE_MODIFIER_APPLIED");
            case 16->List.of("RESOURCE_APPLIED","HEALTH_RATE_CAP","NO_RECURSIVE_HEAL");
            case 17->List.of("STUN_STAGGER_REJECTED","OTHER_STATUS_ALLOWED");
            case 18->List.of("MOVEMENT_CONTROL_REJECTED","DAMAGE_STATUS_PRESERVED");
            case 19->List.of("ENGAGEMENT_PHASE_APPLIED","DAMAGE_MOVEMENT_RECOVERY_APPLIED");
            case 20->List.of("DISTINCT_MINION_DEATH_STACK","BOUNDED_STACK_APPLIED","STACK_RECOVERED");
            case 21->List.of("MINION_HEALTH_DIRECT_MOVEMENT_APPLIED","MINION_REWARD_ISOLATED");
            case 22->List.of("TWO_NATIVE_MEMBERS_PUBLISHED","NATIVE_POPULATION_ACCOUNTED","ROLLBACK_IDEMPOTENT");
            case 23->List.of("SELECTOR_MEMBERSHIP_APPLIED","AURA_RADIUS_APPLIED","AURA_EXPIRED");
            case 24->List.of("GUARD_PROTECTION_APPLIED","PROTECTION_RELEASED","NATIVE_HEALTH_PRESERVED");
            case 25->List.of("NATIVE_EFFECT_PRESENT_AFTER_APPLICATION","DEFENSE_REDUCTION_APPLIED","EFFECT_EXPIRED");
            case 26->List.of("SOURCE_HEALTH_LOSS","REFLECTED_RECEIPT","NO_REFLECTION_LOOP");
            case 27->List.of("INITIAL_SHIELD_APPLIED","SHIELD_ABSORPTION_APPLIED","NO_RELOAD_REGENERATION");
            default->throw new IllegalArgumentException("ME12_OPERATOR_ID");
        };
    }
    private static Status downgrade(Status status,boolean incomplete){return status==Status.PASS&&incomplete?Status.BLOCKED:status;}
    static String requiredExecutionEvidence(int id){return switch(id){
        case 1->"Native movement and recovery timing readback; movement-only route distinguished";
        case 2->"Frozen physical contribution plus completed native Health receipt";
        case 3->"Incoming elemental mitigation, immunity and cap readback";
        case 4->"Native defense rating contribution and mitigated physical packet";
        case 5,6,7,8,9,10,11->"Added elemental power and defensive resistance; original native hit/effects preserved";
        case 12->"Frozen selected spectral channel, no collision reroll, completed component receipt";
        case 13->"Requested/applied Mana, opportunity lock and victim aggregate cap";
        case 14->"Native Cursed effect admission, presence, outgoing reduction and expiry";
        case 15->"Original native impulse plus one modifier; no duplicated displacement";
        case 16->"Applied Health leech, own-health rate cap and no recursive healing";
        case 17->"Actual rejected stun/stagger mutation with unaffected legal statuses";
        case 18->"Movement control rejection with unaffected damage/status components";
        case 19->"Engagement phase, active/inactive damage, movement and recovery readbacks";
        case 20->"Distinct minion death stacks, bounded direct/movement change and recovery";
        case 21->"Initial minion Health/direct/movement snapshot; no own affix or leader rewards";
        case 22->"Exactly two additional native members, population accounting and rollback";
        case 23->"Selector-specific membership, radius, contribution and expiry readback";
        case 24->"Original damage/status blocked until legal guards retire; native Health remains authoritative";
        case 25->"Native Armor Break effect presence, defense reduction and expiry";
        case 26->"Completed source Health loss, one later reflected receipt, no reflection loop";
        case 27->"Initial shield, actual absorption, no regeneration on reload and cleanup";
        default->throw new IllegalArgumentException("ME12_OPERATOR_ID");
    };}
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
