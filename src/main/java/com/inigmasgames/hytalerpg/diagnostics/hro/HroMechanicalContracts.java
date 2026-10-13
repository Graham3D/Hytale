package com.inigmasgames.hytalerpg.diagnostics.hro;

import java.util.*;
import static com.inigmasgames.hytalerpg.diagnostics.hro.HroContracts.Outcome.*;

/** Worker-only receipt predicates. Never calls a gameplay calculator, RNG, repository or native owner. */
final class HroMechanicalContracts {
    static final Map<String,String> ANCHORS = Map.of(
            "GEAR.EQUIPPED", "HytaleGearEquipment.observe / GearQaTrace.equipment",
            "AFFIX.MODIFIER", "GearEffectSnapshot / GearCombatEffects",
            "AFFIX.PROC", "GearHitProcessor / StatusService",
            "COMBAT.RECONCILE", "CombatDamageTraceService / HytaleDamageLifecycleSystems",
            "CYCLONE.ACCOUNTING", "CycloneBarrierLedger / CycloneDamageStage / SupportDamageSystems",
            "SPAWN.ADMISSION", "NativeEnemyBirthDecision / EnemyWorldAdmission",
            "SPAWN.PROMOTION", "NativeEnemyBirthPublication / EnemyWorldAdmission",
            "PERSISTENCE.RECOVERY", "CycloneBarrierLedger.append / load / RpgLoadoutService");
    record Verdict(HroContracts.Outcome outcome,String reason) { }
    private static Verdict verdict(HroContracts.Outcome outcome,String reason){return new Verdict(outcome,reason);}
    static Verdict accept(HroEvent e,Map<String,String> state) {
        var f=e.facts();String b=e.boundary();
        switch(e.contract()) {
            case "GEAR.EQUIPPED":
                if(b.equals("EQUIPMENT_EMPTY")&&"0".equals(f.get("quantity"))&&has(f,"equipmentRevision"))return verdict(NOT_APPLICABLE,"NO_VALID_EQUIPPED_CONTRIBUTOR");
                if(!has(f,"itemId","equipmentRevision","accepted")||!Set.of("true","false").contains(f.get("accepted")))return verdict(INCONCLUSIVE,"EQUIPMENT_IDENTITY_GAP");
                return verdict("true".equals(f.get("accepted"))?PASS:BLOCKED,
                        "true".equals(f.get("accepted"))?"VALIDATED_EQUIPMENT_MEMBERSHIP":"EQUIPMENT_REQUIREMENT_REJECTED");
            case "AFFIX.MODIFIER":
                if(!b.equals("CONTRIBUTION"))return verdict(INCONCLUSIVE,"ROLLED_VALUE_IS_NOT_APPLIED_CONTRIBUTION");
                if(!has(f,"affixId","itemId","equipmentRevision","units","bucket","value","applied"))return verdict(INCONCLUSIVE,"CONTRIBUTION_PROVENANCE_GAP");
                if(!f.get("units").equals("PERCENT_POINTS_TO_FRACTION"))return verdict(INCONCLUSIVE,"UNIT_CONTRACT_UNSUPPORTED");
                if(!finite(f,"value","applied"))return verdict(INCONCLUSIVE,"NONFINITE_CONTRIBUTION");
                return verdict(close(n(f,"value")/100,n(f,"applied"))?PASS:FAIL,"PERCENT_UNIT_BOUNDARY");
            case "AFFIX.PROC":
                if(b.equals("ROLL")) {
                    if(!has(f,"affixId","itemId","rootId","selected"))return verdict(INCONCLUSIVE,"PROC_IDENTITY_GAP");
                    state.putAll(f);
                    return "false".equals(f.get("selected"))?verdict(NOT_APPLICABLE,"EXISTING_ROLL_NOT_SELECTED"):null;
                }
                if(b.equals("OUTCOME")) {
                    if(!"true".equals(state.get("selected")))return verdict(INCONCLUSIVE,"SELECTED_ROLL_NOT_OBSERVED");
                    if("IMMUNE".equals(f.get("result"))&&"OWNER_IMMUNITY_DECISION".equals(f.get("proof")))return verdict(BLOCKED,"LEGAL_TARGET_IMMUNITY");
                    if("APPLIED".equals(f.get("result"))&&"NATIVE_READBACK".equals(f.get("proof")))return verdict(PASS,"NATIVE_EFFECT_OUTCOME_OBSERVED");
                    if("MISSING".equals(f.get("result"))&&"true".equals(f.get("settled"))&&"NATIVE_READBACK".equals(f.get("proof")))return verdict(FAIL,"SELECTED_PROC_NATIVE_EFFECT_MISSING");
                }
                return verdict(INCONCLUSIVE,"NATIVE_PROC_OUTCOME_GAP");
            case "COMBAT.RECONCILE":
                if(!b.equals("NATIVE_RESULT"))return null;
                if(!has(f,"reconciliation")||!finite(f,"healthBefore","healthAfter","accepted","postAbsorption"))return verdict(INCONCLUSIVE,"NATIVE_HEALTH_BOUNDARY_GAP");
                return switch(f.get("reconciliation")) {
                    case "MATCH" -> verdict(PASS,"EXISTING_NATIVE_RECONCILIATION_MATCH");
                    case "MISMATCH" -> verdict(FAIL,"NATIVE_APPLY_HEALTH_DIVERGENCE");
                    default -> verdict(INCONCLUSIVE,"NATIVE_HEALTH_UNOBSERVABLE");
                };
            case "CYCLONE.ACCOUNTING":
                if(b.equals("MANA_BEFORE_LIFE")) {
                    if(!finite(f,"before","after","manaSpent","intended")||!"false".equals(f.get("uncertain")))return verdict(INCONCLUSIVE,"NATIVE_MANA_READBACK_UNCERTAIN");
                    return verdict(n(f,"manaSpent")>=0&&n(f,"manaSpent")<=n(f,"intended")&&close(n(f,"before"),n(f,"after")+n(f,"manaSpent"))?PASS:FAIL,"CONFIRMED_MANA_HEALTH_PROTECTION_CONSERVATION");
                }
                if(b.equals("SHARED_DEBIT"))return verdict(INCONCLUSIVE,"SHARED_DEBIT_OBSERVED_CHILD_GENERATION_NOT_EXPOSED");
                if(b.equals("DEBIT")) {
                    if(!has(f,"generation","receiptId")||!finite(f,"before","remaining","absorbed","requested","damageRemaining"))return verdict(INCONCLUSIVE,"DURABLE_DEBIT_IDENTITY_OR_VALUES_GAP");
                    if("true".equals(f.get("duplicate")))return verdict(n(f,"absorbed")==0&&close(n(f,"before"),n(f,"remaining"))?NOT_APPLICABLE:FAIL,"DUPLICATE_DEBIT_MUST_NOT_ABSORB");
                    if(!"true".equals(f.get("durable")))return verdict(n(f,"absorbed")==0?BLOCKED:FAIL,"UNCONFIRMED_DEBIT");
                    boolean valid=n(f,"absorbed")>=0&&n(f,"remaining")>=0&&n(f,"damageRemaining")>=0
                            &&close(n(f,"before"),n(f,"absorbed")+n(f,"remaining"))
                            &&close(n(f,"requested"),n(f,"absorbed")+n(f,"damageRemaining"));
                    return verdict(valid?PASS:FAIL,"DURABLE_BARRIER_CONSERVATION");
                }
                if(b.equals("REFLECTION")) {
                    if(!finite(f,"absorbed","requestedReflection","reflectionFactor"))return verdict(INCONCLUSIVE,"REFLECTION_FACTOR_OR_ABSORPTION_GAP");
                    return verdict(close(n(f,"requestedReflection"),n(f,"absorbed")*n(f,"reflectionFactor"))?PASS:FAIL,"REFLECTION_USES_ACTUAL_ABSORPTION");
                }
                if(b.equals("PAYMENT")) {
                    if(!has(f,"generation","receiptId"))return verdict(INCONCLUSIVE,"PAYMENT_IDENTITY_GAP");
                    String key="payment:"+f.get("receiptId");
                    if(state.containsKey(key))return verdict(FAIL,"DUPLICATE_DURABLE_PAYMENT_RECEIPT");
                    if(state.size()<32)state.put(key,"observed");else return verdict(INCONCLUSIVE,"PAYMENT_STATE_LIMIT");
                    return verdict(PASS,"DURABLE_PAYMENT_OBSERVED");
                }
                return verdict(INCONCLUSIVE,"DEFENSIVE_BOUNDARY_OBSERVED_SUCCESSOR_UNVERIFIED");
            case "SPAWN.ADMISSION":
                if(!has(f,"provenance","encounter"))return verdict(INCONCLUSIVE,"SPAWN_PROVENANCE_GAP");
                if(b.equals("RARITY_DECISION"))return verdict("NORMAL".equals(f.get("rarity"))?NOT_APPLICABLE:INCONCLUSIVE,
                        "NORMAL".equals(f.get("rarity"))?"LEGAL_NORMAL_DECISION":"PROMOTION_SUCCESSOR_REQUIRED");
                if(b.equals("ADMISSION_CLOSED")||b.equals("FALLBACK"))return verdict(BLOCKED,f.getOrDefault("reason","ADMISSION_CLOSED"));
                return verdict(INCONCLUSIVE,"SPAWN_ADMISSION_BOUNDARY_GAP");
            case "SPAWN.PROMOTION":
                if(!has(f,"provenance","encounter"))return verdict(INCONCLUSIVE,"SPAWN_PROVENANCE_GAP");
                if(b.equals("PLAN_ACCEPTED")){state.put("plan","true");return null;}
                if(b.equals("ELITE_PUBLISHED"))return verdict("true".equals(state.get("plan"))?PASS:INCONCLUSIVE,
                        "true".equals(state.get("plan"))?"FROZEN_PLAN_NATIVE_PUBLICATION_SETTLED":"PRIOR_FROZEN_PLAN_NOT_OBSERVED");
                if(b.equals("SETTLED")&&"true".equals(state.get("plan"))&&"false".equals(f.get("published")))return verdict(FAIL,"ACCEPTED_PLAN_NOT_PUBLISHED_AT_SETTLEMENT");
                if(b.equals("BIRTH_COMPENSATED")||b.equals("BIRTH_DECLINED"))return verdict(BLOCKED,"LEGAL_BIRTH_COMPENSATION");
                return null;
            case "PERSISTENCE.RECOVERY":
                if(!has(f,"durableSubject","schema","revision","fingerprint"))return verdict(INCONCLUSIVE,"DURABLE_IDENTITY_GAP");
                if(b.equals("SAVED")){state.clear();state.putAll(f);return null;}
                if(b.equals("LOADED")) {
                    if(!state.containsKey("fingerprint"))return verdict(INCONCLUSIVE,"EARLIER_DURABLE_EVIDENCE_MISSING");
                    if(!Objects.equals(state.get("durableSubject"),f.get("durableSubject"))||!Objects.equals(state.get("schema"),f.get("schema")))return verdict(INCONCLUSIVE,"DURABLE_IDENTITY_OR_SCHEMA_MISMATCH");
                    if(!Objects.equals(state.get("revision"),f.get("revision")))return verdict(INCONCLUSIVE,"INTERVENING_DURABLE_REVISION_OR_RECOVERY_POLICY");
                    return verdict(Objects.equals(state.get("fingerprint"),f.get("fingerprint"))?PASS:FAIL,"MATCHING_DURABLE_REVISION_CONTENT");
                }
                return null;
            default: throw new IllegalArgumentException("Unknown mechanical contract");
        }
    }
    private static boolean has(Map<String,String> f,String... keys){for(String k:keys)if(f.get(k)==null||f.get(k).isBlank())return false;return true;}
    private static boolean finite(Map<String,String> f,String... keys){for(String k:keys)if(!Double.isFinite(n(f,k)))return false;return true;}
    private static double n(Map<String,String> f,String key){try{return Double.parseDouble(f.getOrDefault(key,"NaN"));}catch(NumberFormatException ignored){return Double.NaN;}}
    private static boolean close(double a,double b){return Math.abs(a-b)<=Math.max(0.00001,Math.max(Math.abs(a),Math.abs(b))*0.000001);}
}
