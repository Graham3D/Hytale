package com.inigmasgames.hytalerpg.diagnostics.hro;

import com.inigmasgames.hytalerpg.gear.GearCatalog;
import java.util.*;

/** Fixed catalog and bounded operation counts; no retained event stream. Diagnostic worker only. */
final class HroMechanicalReport {
    private final Map<String,Map<String,Object>> affixes=new TreeMap<>();
    private final Map<String,Long> spawn=new TreeMap<>();
    private final Set<String> decisions=new HashSet<>();
    private final Map<String,List<Map<String,Object>>> detail=new LinkedHashMap<>();
    private long overflow;
    private long omittedDetails;
    HroMechanicalReport() {
        for(var affix:GearCatalog.load().rollableAffixes()) {
            var row=new LinkedHashMap<String,Object>();row.put("operator",affix.operator());
            row.put("states",new TreeSet<>(Set.of("DEFINED","CAPABILITY_BLOCKED")));
            row.put("capability","INDIVIDUAL_NATIVE_OUTCOME_NOT_ESTABLISHED");
            row.put("reason","NO_MECHANICAL_BOUNDARY_OBSERVED");affixes.put(affix.id(),row);
        }
    }
    @SuppressWarnings("unchecked")
    void accept(HroEvent event,String locator) {
        String section=switch(event.contract()) {
            case "COMBAT.RECONCILE" -> "combat";case "CYCLONE.ACCOUNTING" -> "defensiveResources";
            case "SPAWN.PROMOTION" -> "spawnPublication";case "PERSISTENCE.RECOVERY" -> "recovery";
            default -> null;
        };
        if(section!=null){var rows=detail.computeIfAbsent(section,k->new ArrayList<>());
            if(rows.size()<32)rows.add(Map.of("operationId",event.operationId(),"boundary",event.boundary(),"facts",event.facts(),
                    "evidence",locator,"sourceAnchor",HroContracts.ANCHORS.get(event.contract())));
            else omittedDetails++;
        }
        String id=event.facts().get("affixId");var row=affixes.get(id==null?"":id);
        if(row!=null) {
            var states=(Set<String>)row.get("states");states.add("INSTRUMENTED");
            if(event.boundary().equals("CONTRIBUTION")||event.boundary().equals("ROLL"))states.add("EXERCISED");
            if(event.boundary().equals("OUTCOME")&&Set.of("NATIVE_READBACK","OWNER_IMMUNITY_DECISION").contains(event.facts().getOrDefault("proof","")))states.add("OUTCOME_OBSERVED");
            if(!states.contains("OUTCOME_OBSERVED"))states.add("INSUFFICIENT_EVIDENCE");
            else states.remove("INSUFFICIENT_EVIDENCE");
            if(states.contains("OUTCOME_OBSERVED")){states.remove("CAPABILITY_BLOCKED");row.put("capability","OBSERVED_FOR_THIS_OPERATION_ONLY");}
            row.put("reason","OBSERVED_OPERATION_ONLY_NOT_FAMILY_ACCEPTANCE");row.put("evidence",locator);
        }
        if(event.contract().equals("SPAWN.ADMISSION")&&event.boundary().equals("RARITY_DECISION")) {
            if(decisions.size()>=2048){overflow++;return;}
            if(decisions.add(event.operationId())) {
                String provenance=event.facts().getOrDefault("provenance","UNKNOWN");
                String rarity=event.facts().getOrDefault("rarity","UNKNOWN");
                spawn.merge(provenance+"/"+rarity,1L,Long::sum);
            }
        }
    }
    Map<String,Object> snapshot(boolean incomplete) {
        var report=new LinkedHashMap<String,Object>(Map.of("affixes",affixes,"affixScope","AUTHORED_CATALOG_WITH_OBSERVED_OPERATION_STATES",
                "evidenceComplete",!incomplete,"spawnDecisionCounts",spawn,"omittedDecisions",overflow,
                "frequencyOutcome","INCONCLUSIVE","frequencyReason","INDEPENDENT_TRIAL_MODEL_NOT_ESTABLISHED; zero Elites is not a failure",
                "combatScope","Existing native reconciliation; unobserved individual filters remain gaps",
                "defensiveScope","Captured durable boundaries only; shared child generations/native effects require individual receipts"));
        report.put("receipts",detail);report.put("omittedDetailRows",omittedDetails);
        report.put("detailLimit","32 rows per section; use operation index/extract for remaining bounded stream");
        return report;
    }
}
