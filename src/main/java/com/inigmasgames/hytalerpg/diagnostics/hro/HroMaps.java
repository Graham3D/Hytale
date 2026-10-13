package com.inigmasgames.hytalerpg.diagnostics.hro;

import com.google.gson.*;
import java.util.*;

/** Versioned source/authority map. Symbol anchors survive whitespace-only source edits. */
final class HroMaps {
    private HroMaps() { }
    private static final String ROOT="src/main/java/com/inigmasgames/hytalerpg/";
    static List<Map<String,String>> implementation() {
        String[][] rows={
            {"BOOT.ARTIFACT","diagnostics/hro/HroIdentity.java","capture","ADAPT_NATIVE_BOUNDARY"},
            {"PLAYER.ADMISSION","execution/hytale/HytalePlayerPersistenceReady.java","class HytalePlayerPersistenceReady","ADAPT_NATIVE_BOUNDARY"},
            {"SKILL.ASSIGN","progress/RpgLoadoutService.java","equipSkill","KEEP_AUTHORITY"},
            {"SKILL.PROJECTION","input/NativeAbilityProjectionService.java","updateStatus","ADAPT_NATIVE_BOUNDARY"},
            {"SKILL.EXECUTION","execution/SkillExecutionService.java","class SkillExecutionService","KEEP_AUTHORITY"},
            {"HUD.PUBLICATION","ui/hud/RpgHudCoordinator.java","class RpgHudCoordinator","ADAPT_NATIVE_BOUNDARY"},
            {"UI.LIFECYCLE","ui/inventory/InventoryProbePage.java","onDismiss","REPLACE_PRESENTATION"},
            {"INVENTORY.OWNER","ui/inventory/SpatialBagComponent.java","class SpatialBagComponent","KEEP_MODEL"},
            {"INVENTORY.TRANSFER","ui/inventory/SpatialInventoryTransferCoordinator.java","class SpatialInventoryTransferCoordinator","KEEP_AUTHORITY"},
            {"GEAR.EQUIPPED","gear/GearEffectSnapshot.java","GearEffectSnapshot","KEEP_MODEL"},
            {"AFFIX.MODIFIER","gear/GearEffectSnapshot.java","percent","KEEP_AUTHORITY"},
            {"AFFIX.PROC","gear/GearSignatureProcRuntime.java","class GearSignatureProcRuntime","KEEP_AUTHORITY"},
            {"COMBAT.RECONCILE","diagnostics/CombatDamageTraceService.java","reconcile","ADAPT_NATIVE_BOUNDARY"},
            {"CYCLONE.ACCOUNTING","execution/support/CycloneBarrierLedger.java","CycloneBarrierLedger","KEEP_AUTHORITY"},
            {"SPAWN.ADMISSION","enemies/EnemyWorldAdmission.java","class EnemyWorldAdmission","ADAPT_NATIVE_BOUNDARY"},
            {"SPAWN.PROMOTION","enemies/EnemyWorldAdmission.java","birthPhase","KEEP_AUTHORITY"},
            {"PERSISTENCE.RECOVERY","progress/FileRpgPlayerStateRepository.java","class FileRpgPlayerStateRepository","KEEP_AUTHORITY"}};
        var result=new ArrayList<Map<String,String>>();
        for(String[] row:rows)result.add(Map.of("contract",row[0],"path",ROOT+row[1],"symbol",row[2],"recommendation",row[3],
                "migrationOwner","Hywind "+row[0],"preserve","Existing authority, receipts and validation; presentation cannot perform mutations"));
        return List.copyOf(result);
    }
    static Map<String,Object> compatibility(JsonObject before,JsonObject after) {
        var differences=new ArrayList<Map<String,String>>();
        for(String key:List.of("jarSha256","serverSha256","clientVersion","assetIdentity","offlineNativeAudit","entrypoint")) {
            JsonElement a=before==null?null:before.get(key),b=after==null?null:after.get(key);
            differences.add(Map.of("binding",key,"oldHash",a==null?"UNKNOWN":HroOffline.hash(a),"newHash",b==null?"UNKNOWN":HroOffline.hash(b),
                    "status",a==null||b==null?"UNKNOWN":a.equals(b)?"IDENTITY_UNCHANGED_BEHAVIOR_UNPROVEN":"CHANGED_REQUIRES_BOUNDARY_REVIEW"));
        }
        return Map.of("schemaVersion",1,"bindings",differences,"retainedImplementation",implementation(),
                "nativeCapability","Missing patched hooks remain blockers; identity equality does not establish behavior compatibility");
    }
    static List<Map<String,Object>> cascades(List<HroContracts.Finding> findings) {
        var groups=new TreeMap<String,List<HroContracts.Finding>>();
        for(var finding:findings) {
            String operation=finding.operationId();
            // Only explicit operation correlation can group symptoms; timestamps and nearby lines cannot.
            String key=operation==null||operation.isBlank()||operation.equals("UNKNOWN")?finding.contract()+":"+finding.evidence():operation;
            groups.computeIfAbsent(key,ignored->new ArrayList<>()).add(finding);
        }
        var result=new ArrayList<Map<String,Object>>();
        groups.forEach((key,rows)->result.add(Map.of("operation",key,"relationship","SHARED_EXPLICIT_CORRELATION_NOT_CAUSAL_PROOF",
                "findings",List.copyOf(rows),"firstSupportedDivergence",rows.stream().filter(f->f.outcome()==HroContracts.Outcome.FAIL||f.outcome()==HroContracts.Outcome.BLOCKED)
                        .map(HroContracts.Finding::firstDivergence).findFirst().orElse("NO_PROVEN_FAILURE"))));
        return List.copyOf(result);
    }
    static List<Map<String,Object>> details(List<HroContracts.Finding> findings) {
        var result=new ArrayList<Map<String,Object>>();
        for(var finding:findings) {
            var row=new LinkedHashMap<String,Object>();row.put("finding",finding);
            row.put("severity",finding.outcome()==HroContracts.Outcome.FAIL?"ERROR":finding.outcome()==HroContracts.Outcome.BLOCKED?"WARNING":"INFORMATION");
            row.put("scope","OBSERVED_INVOKING_SUBJECT_OPERATION_ONLY");
            row.put("source",implementation().stream().filter(source->source.get("contract").equals(finding.contract())).findFirst().orElse(Map.of()));
            row.put("legalAlternatives","Contract evaluator checks explicit rejection, cancellation, immunity, unbound slots and incomplete evidence where applicable; unobserved alternatives remain unknown");
            row.put("nextObservation",finding.outcome()==HroContracts.Outcome.INCONCLUSIVE?"Capture the next owner boundary during ordinary QA; do not infer success or failure from absence":"Inspect the referenced operation and source predicate; retain existing authority");
            result.add(row);
        }return List.copyOf(result);
    }
}
