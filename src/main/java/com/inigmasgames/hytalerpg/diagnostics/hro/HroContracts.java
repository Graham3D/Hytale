package com.inigmasgames.hytalerpg.diagnostics.hro;

import java.util.*;

/** Explicit Stage 1 predicates. Absence alone never establishes a gameplay failure. Worker-only. */
public final class HroContracts {
    public enum Outcome { PASS, FAIL, BLOCKED, NOT_EXERCISED, INCONCLUSIVE, NOT_APPLICABLE }
    public static final List<String> IDS = List.of("BOOT.ARTIFACT", "PLAYER.ADMISSION", "SKILL.ASSIGN",
            "SKILL.PROJECTION", "SKILL.EXECUTION", "HUD.PUBLICATION", "UI.LIFECYCLE",
            "INVENTORY.OWNER", "INVENTORY.TRANSFER", "GEAR.EQUIPPED", "AFFIX.MODIFIER", "AFFIX.PROC",
            "COMBAT.RECONCILE", "CYCLONE.ACCOUNTING", "SPAWN.ADMISSION", "SPAWN.PROMOTION", "PERSISTENCE.RECOVERY");
    private static final Map<String,String> STAGE_ONE_ANCHORS = Map.of(
            "BOOT.ARTIFACT", "HyArpgPlugin.setup / HroIdentity",
            "PLAYER.ADMISSION", "HytalePlayerPersistenceReady / HytaleSupportSystem.connectionReady",
            "SKILL.ASSIGN", "RpgLoadoutService.equipSkill / mutate",
            "SKILL.PROJECTION", "NativeAbilityProjectionService.updateStatus",
            "SKILL.EXECUTION", "SkillExecutionService / RpgSkillTraceService",
            "HUD.PUBLICATION", "RpgHudCoordinator.install / refresh",
            "UI.LIFECYCLE", "UiInteractionTrace / InventoryProbePage.onDismiss",
            "INVENTORY.OWNER", "SpatialBagComponent / InventoryProbePage.traceInput",
            "INVENTORY.TRANSFER", "SpatialInventoryTransferCoordinator / HytaleGearLoot");
    public static final Map<String,String> ANCHORS;
    static { var all=new HashMap<>(STAGE_ONE_ANCHORS);all.putAll(HroMechanicalContracts.ANCHORS);ANCHORS=Map.copyOf(all); }
    public record Finding(String contract, int version, Outcome outcome, String comparison, String proof,
                          String operationId, String lastSuccessfulBoundary, String firstDivergence,
                          String evidence, String sourceAnchor, String recommendation) { }
    private static final int MAX_OPERATIONS = 2048, MAX_FINDINGS = 128;
    private static final class Operation {
        String last, locator; Long connection, revision; String world;
        boolean committed, dispatched, paid, rejected, compiled, saved;
        Outcome outcome;
        final Map<String,String> mechanical = new HashMap<>();
    }
    private final Map<String, Operation> operations = new HashMap<>();
    private final Map<String, EnumMap<Outcome, Long>> counts = new LinkedHashMap<>();
    private final List<Finding> findings = new ArrayList<>();
    private final Set<String> registered;
    private long losses, omittedFindings;
    public HroContracts(Set<String> registered) {
        this.registered = new HashSet<>(registered);
        for (String id : IDS) counts.put(id, new EnumMap<>(Outcome.class));
    }
    void registered(String contract){if(IDS.contains(contract))registered.add(contract);}
    public void lost(long count) { losses += count; }
    public void accept(HroEvent event, String locator) {
        if (!counts.containsKey(event.contract())) return;
        String key = event.contract() + ":" + event.operationId();
        Operation op = operations.get(key);
        if (op == null) {
            if (operations.size() >= MAX_OPERATIONS) { losses++; return; }
            op = new Operation(); operations.put(key, op);
        }
        var f = event.facts(); String b = event.boundary();
        if (op.connection != null && event.connectionId() != null && !op.connection.equals(event.connectionId())
                || op.world != null && event.worldSegmentId() != null && !op.world.equals(event.worldSegmentId())) {
            emit(event, op, Outcome.INCONCLUSIVE, "STALE_WORLD_OR_CONNECTION", locator); return;
        }
        if((event.contract().equals("SKILL.ASSIGN")||event.contract().equals("SKILL.PROJECTION")) && op.revision!=null && event.stateRevision()!=null
                && !op.revision.equals(event.stateRevision())) {
            emit(event,op,Outcome.INCONCLUSIVE,"LOADOUT_REVISION_MISMATCH",locator);return;
        }
        if (event.connectionId() != null) op.connection = event.connectionId();
        if (event.worldSegmentId() != null) op.world = event.worldSegmentId();
        if (event.stateRevision() != null) op.revision = event.stateRevision();
        if(HroMechanicalContracts.ANCHORS.containsKey(event.contract())) {
            for(String identity:List.of("itemId","targetId","equipmentRevision","affixId","rootId","generation","provenance","encounter")) {
                String value=f.get(identity),prior=op.mechanical.get("identity:"+identity);
                if(value!=null&&prior!=null&&!prior.equals(value)){
                    emit(event,op,Outcome.INCONCLUSIVE,"MECHANICAL_IDENTITY_MISMATCH:"+identity,locator);return;
                }
                if(value!=null)op.mechanical.put("identity:"+identity,value);
            }
        }
        switch (event.contract()) {
            case "BOOT.ARTIFACT" -> {
                boolean missingPreload=f.containsKey("preloadRegistration")&&!truth(f,"preloadRegistration");
                terminal(event, op, missingPreload?Outcome.BLOCKED:truth(f,"identityVerified") ? Outcome.PASS : Outcome.INCONCLUSIVE,
                        missingPreload?"PRODUCTION_PRELOAD_REGISTRATION_MISSING":truth(f,"identityVerified") ? "JAR_AND_NATIVE_IDENTITY_OBSERVED" : "ARTIFACT_IDENTITY_GAP", locator);
            }
            case "PLAYER.ADMISSION" -> {
                if (b.equals("OWNER_INSTALLED")) terminal(event, op, Outcome.PASS, "OWNER_INSTALL_RETURNED_TRUE", locator);
                else if (b.equals("WAIT")) emit(event, op, Outcome.BLOCKED, f.getOrDefault("reason","OWNER_WAIT"), locator);
                else if (b.equals("PREPARATION_FAILED")) terminal(event, op, Outcome.BLOCKED,"PREPARATION_FAILED",locator);
                else if (b.equals("STALE_CONNECTION") || b.equals("STALE_WORLD"))
                    terminal(event, op, Outcome.INCONCLUSIVE,b,locator);
                else if (b.equals("DISCONNECT")) terminal(event, op, Outcome.NOT_APPLICABLE,"LEGAL_DISCONNECT",locator);
            }
            case "SKILL.ASSIGN" -> {
                if (b.equals("COMPILE_SUCCESS")) op.compiled = true;
                if (b.equals("SAVE")) op.saved = true;
                if (b.equals("EQUIP_SKILL_COMMITTED") || b.equals("UNEQUIP_SKILL_COMMITTED"))
                    terminal(event,op,op.compiled && op.saved ? Outcome.PASS : Outcome.INCONCLUSIVE,
                            op.compiled && op.saved ? "COMPILED_AND_SAVED_ASSIGNMENT" : "SAVE_OR_COMPILE_BOUNDARY_MISSING",locator);
                else if (b.equals("COMPILE_FAILURE")) terminal(event,op,Outcome.BLOCKED,"ASSIGNMENT_REJECTED",locator);
            }
            case "SKILL.PROJECTION" -> {
                String result = f.getOrDefault("result", "UNKNOWN");
                if(b.equals("SAVED_SLOT_OBSERVED") && f.getOrDefault("skillId","").isEmpty()) {
                    terminal(event,op,Outcome.NOT_APPLICABLE,"EMPTY_LOGICAL_SLOT",locator);break;
                }
                if (result.equals("PROJECTED")) terminal(event,op,Outcome.PASS,"NATIVE_CONTAINER_READBACK",locator);
                else if (result.contains("UNAVAILABLE") || result.contains("UNBOUND") || result.contains("OCCUPIED")
                        || result.contains("NOT_IMPLEMENTED") || result.contains("TOO_SMALL") || result.equals("NO_SUPPORTED_INPUT_BINDING"))
                    terminal(event,op,Outcome.BLOCKED,result,locator);
                else if (result.equals("NATIVE_CONTAINER_WRITE_REJECTED")) terminal(event,op,Outcome.FAIL,result,locator);
                else if (result.equals("EMPTY") || result.equals("CLEARED") || result.equals("NATIVE_SLOT_PRESERVED"))
                    terminal(event,op,Outcome.NOT_APPLICABLE,"NO_ASSIGNED_PROJECTION_REQUIRED",locator);
            }
            case "SKILL.EXECUTION" -> {
                if (b.equals("RESOURCE_COMMIT")) op.paid = true;
                if (b.equals("SKILL_COMMITTED")) {
                    if(op.committed) emit(event,op,Outcome.FAIL,"DUPLICATE_ADMITTED_EXECUTION",locator);
                    op.committed = true;
                }
                if (b.equals("EXECUTOR_DISPATCH")) {
                    if(op.dispatched) emit(event,op,Outcome.FAIL,"DUPLICATE_EXECUTOR_DISPATCH",locator);
                    else emit(event,op,op.committed ? Outcome.PASS : Outcome.INCONCLUSIVE,
                            op.committed ? "ONE_ADMITTED_DISPATCH_EFFECT_UNVERIFIED" : "COMMIT_NOT_OBSERVED",locator);
                    op.dispatched = true;
                }
                if (b.equals("SKILL_ACTIVATION_REJECTED") || b.equals("SKILL_VALIDATION_REJECTED")) {
                    op.rejected = true;
                    emit(event,op,op.paid ? Outcome.FAIL : Outcome.INCONCLUSIVE,
                            op.paid ? "REJECTED_ACTIVATION_SPENT_RESOURCE" : "REJECTED_DEBIT_ABSENCE_NOT_PROVEN",locator);
                }
                if (op.rejected && b.equals("RESOURCE_COMMIT")) emit(event,op,Outcome.FAIL,"DEBIT_AFTER_REJECTION",locator);
                if(b.equals("SKILL_EXECUTION_FAILED") || b.equals("SKILL_COMMIT_FAILED"))
                    emit(event,op,Outcome.FAIL,b,locator);
            }
            case "HUD.PUBLICATION" -> {
                if(b.equals("HUD_LAYOUT_READY")) terminal(event,op,Outcome.PASS,"SERVER_PUBLICATION_ONLY_VISUAL_PENDING",locator);
                else if(b.equals("HUD_REFRESH_FAILED")) terminal(event,op,Outcome.FAIL,b,locator);
            }
            case "UI.LIFECYCLE" -> {
                if (b.equals("PAGE_CLOSE") || b.equals("HUD_TEARDOWN")) terminal(event,op,Outcome.INCONCLUSIVE,
                        "CLOSE_OBSERVED_HUD_RESTORATION_OR_TRANSFER_UNCONFIRMED",locator);
                if (b.equals("DOCUMENT_CLOSED")) terminal(event,op,truth(f,"closedOnce") && truth(f,"hudRestored")
                        ? Outcome.PASS : truth(f,"duplicateClose") ? Outcome.FAIL : Outcome.INCONCLUSIVE,
                        "DOCUMENT_CLOSE_AND_HUD_OWNERSHIP",locator);
            }
            case "INVENTORY.OWNER" -> {
                if(truth(f,"expectedSpatial") && !truth(f,"componentPresent"))
                    terminal(event,op,Outcome.FAIL,"STATE_MIGRATION_OR_CONFIGURATION_MISMATCH",locator);
                else if(!f.containsKey("expectedSpatial")) terminal(event,op,Outcome.INCONCLUSIVE,
                        "CUSTODY_OBSERVED_EXPECTED_MODE_UNAVAILABLE",locator);
                else terminal(event,op,Outcome.PASS,"CUSTODY_MODE_MATCHES_EXPLICIT_EXPECTATION",locator);
            }
            case "INVENTORY.TRANSFER" -> {
                if ("-1".equals(f.get("rawNativeSlot")) && "-1".equals(f.get("resolvedNativeSlot")))
                    terminal(event,op,Outcome.FAIL,"INACTIVE_SLOT_NOT_TRANSLATED",locator);
                else if(f.containsKey("quantityBefore") && f.containsKey("quantityAfter") && truth(f,"settled"))
                    terminal(event,op,Objects.equals(f.get("quantityBefore"),f.get("quantityAfter")) && truth(f,"singleCustodian")
                            ? Outcome.PASS : Outcome.FAIL,"SETTLED_CUSTODY_CONSERVATION",locator);
                else if (b.equals("DOMAIN_OPERATION")) terminal(event,op,Outcome.INCONCLUSIVE,
                        "OWNER_RESULT_OBSERVED_ITEM_CONSERVATION_GAP",locator);
            }
            default -> {
                var verdict=HroMechanicalContracts.accept(event,op.mechanical);
                if(verdict!=null)emit(event,op,verdict.outcome(),verdict.reason(),locator);
            }
        }
        op.last = b; op.locator = locator;
    }
    private static boolean truth(Map<String,String> facts,String key) { return "true".equals(facts.get(key)); }
    private void terminal(HroEvent e, Operation op, Outcome result, String reason, String locator) {
        emit(e,op,result,reason,locator);
        operations.remove(e.contract()+":"+e.operationId());
    }
    private void emit(HroEvent e, Operation op, Outcome result, String reason, String locator) {
        // A later successful dispatch cannot erase an already observed duplicate/debit violation.
        if (op.outcome == Outcome.FAIL) return;
        if(!registered.contains(e.contract()) || losses > 0) result=Outcome.INCONCLUSIVE;
        if(op.outcome!=null)counts.get(e.contract()).computeIfPresent(op.outcome,(k,v)->v<=1?null:v-1);
        counts.get(e.contract()).merge(result,1L,Long::sum);
        op.outcome=result;
        Finding finding=new Finding(e.contract(),1,result,"NO_BASELINE",e.contract().equals("SKILL.PROJECTION")&&result==Outcome.PASS?
                "NATIVE_READBACK":"OWNER_DECISION",e.operationId(),op.last,reason,locator,ANCHORS.get(e.contract()),"KEEP_AUTHORITY; inspect first unsupported boundary");
        for(int i=0;i<findings.size();i++) {
            Finding prior=findings.get(i);
            if(prior.contract().equals(e.contract())&&prior.operationId().equals(e.operationId())){findings.set(i,finding);return;}
        }
        if(findings.size()<MAX_FINDINGS)findings.add(finding);
        else {
            omittedFindings++;
            if(result==Outcome.FAIL||result==Outcome.BLOCKED||result==Outcome.INCONCLUSIVE)
                for(int i=findings.size()-1;i>=0;i--)if(findings.get(i).outcome()==Outcome.PASS||findings.get(i).outcome()==Outcome.NOT_APPLICABLE){findings.set(i,finding);break;}
        }
    }
    public Map<String, Object> coverage(boolean interrupted) {
        Map<String,Object> result=new LinkedHashMap<>();
        for(String id:IDS) {
            var outcomes=counts.get(id);
            boolean pending=operations.entrySet().stream().anyMatch(e->e.getKey().startsWith(id+":")&&e.getValue().outcome==null);
            Outcome overall = !registered.contains(id) || losses>0 || interrupted ? Outcome.INCONCLUSIVE
                    : outcomes.containsKey(Outcome.FAIL) ? Outcome.FAIL : outcomes.containsKey(Outcome.BLOCKED) ? Outcome.BLOCKED
                    : pending || outcomes.containsKey(Outcome.INCONCLUSIVE) ? Outcome.INCONCLUSIVE
                    : outcomes.containsKey(Outcome.PASS) ? Outcome.PASS : outcomes.containsKey(Outcome.NOT_APPLICABLE)
                    ? Outcome.NOT_APPLICABLE : Outcome.NOT_EXERCISED;
            result.put(id,Map.of("outcome",overall,"registered",registered.contains(id),"counts",Map.copyOf(outcomes),
                    "pending",pending,"losses",losses,"reason",!registered.contains(id)?"OBSERVER_NOT_REGISTERED":
                            losses>0?"EVIDENCE_DROPPED":pending?"SUCCESSOR_NOT_OBSERVED_CAPTURE_BOUNDARY":"OBSERVED_BOUNDARIES_ONLY"));
        }
        return result;
    }
    public List<Finding> findings(boolean incomplete) {
        List<Finding> result = new ArrayList<>(findings);
        // A missing successor has a navigable evidence anchor, but is never a timeout failure.
        operations.entrySet().stream().filter(entry -> entry.getValue().outcome == null)
                .sorted(Map.Entry.comparingByKey()).forEach(entry -> {
                    int separator = entry.getKey().indexOf(':');
                    String contract = entry.getKey().substring(0, separator);
                    Operation op = entry.getValue();
                    Finding gap = new Finding(contract, 1, Outcome.INCONCLUSIVE, "NO_BASELINE", "EVIDENCE_GAP",
                            entry.getKey().substring(separator + 1), op.last, "SUCCESSOR_NOT_OBSERVED_CAPTURE_BOUNDARY",
                            op.locator, ANCHORS.get(contract), "Inspect the next owner boundary; do not infer gameplay failure from absence");
                    if (result.size() < MAX_FINDINGS) result.add(gap);
                    else for (int i = result.size() - 1; i >= 0; i--) {
                        if (result.get(i).outcome() == Outcome.PASS || result.get(i).outcome() == Outcome.NOT_APPLICABLE) {
                            result.set(i, gap); break;
                        }
                    }
                });
        if(!incomplete && losses==0)return List.copyOf(result);
        return result.stream().map(f->new Finding(f.contract(),f.version(),Outcome.INCONCLUSIVE,f.comparison(),f.proof(),
                f.operationId(),f.lastSuccessfulBoundary(),"CAPTURE_INCOMPLETE: "+f.firstDivergence(),f.evidence(),f.sourceAnchor(),f.recommendation())).toList();
    }
    public long omittedFindings() { return omittedFindings; }
}
