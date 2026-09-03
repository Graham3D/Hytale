package com.inigmasgames.persistentnpcs.epistemic;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/** E3 final objective-claim authority used by DialogueClaimValidator. */
public final class EpistemicClaimFirewall {
    private final AtomicClaimExtractor extractor = new AtomicClaimExtractor();

    public Result validate(String generated, EpistemicContract contract,
            boolean authoritativeActionResult) {
        return validate(generated, contract, authoritativeActionResult && contract != null
                ? contract.answerPlan().requestedAction() : "", true);
    }

    /** Phrase gates enforce the required direct answer only on the first immutable phrase. */
    public Result validate(String generated, EpistemicContract contract,
            boolean authoritativeActionResult, boolean requireDirectAnswer) {
        return validate(generated, contract, authoritativeActionResult && contract != null
                ? contract.answerPlan().requestedAction() : "", requireDirectAnswer);
    }

    public Result validate(String generated, EpistemicContract contract,
            String authoritativeActionResult, boolean requireDirectAnswer) {
        long started = System.nanoTime();
        String original = generated == null ? "" : generated.strip();
        if (contract == null || contract.mode() != EpistemicFeatureMode.AUTHORITATIVE) {
            return new Result(original, original, List.of(), false, true,
                    "EPISTEMIC_ROUTE_NOT_AUTHORITATIVE", 0, 0, 0,
                    (System.nanoTime() - started) / 1_000L);
        }
        long extractionStarted = System.nanoTime();
        List<AtomicClaim> claims = extractor.extract(original);
        long extractionMicros = (System.nanoTime() - extractionStarted) / 1_000L;
        long validationStarted = System.nanoTime();
        List<AtomicClaimResult> results = claims.stream()
                .map(claim -> assess(claim, contract, authoritativeActionResult)).toList();
        int objectiveCount = (int) results.stream().filter(AtomicClaimResult::releasable)
                .filter(result -> result.status() != ClaimSupportStatus.SUBJECTIVE_ALLOWED
                        && result.status() != ClaimSupportStatus.HYPOTHETICAL_ALLOWED)
                .map(AtomicClaimResult::claim).filter(AtomicClaim::objective)
                .filter(claim -> authoritativeActionResult == null
                        || authoritativeActionResult.isBlank()
                        || !(claim.predicateKey().equals("ACTION_RESULT")
                                || claim.predicateKey().equals("ACTION_COMMITMENT")))
                .count();
        boolean overBudget = objectiveCount > contract.answerPlan().maxObjectiveClaims();
        boolean direct = !requireDirectAnswer || directAnswerFirst(claims, results, contract);
        List<AtomicClaimResult> releasable = results.stream()
                .filter(AtomicClaimResult::releasable).toList();
        List<AtomicClaimResult> rejected = results.stream()
                .filter(value -> !value.releasable()).toList();
        long repairStarted = System.nanoTime();
        // Preserve the exact provider wording when every clause is authorized. Reassembly is
        // only needed to remove rejected clauses; re-punctuating an already-valid response can
        // otherwise diverge from immutable streaming speech.
        String repaired = rejected.isEmpty() ? original : repair(original, releasable);
        boolean planRejected = overBudget || !direct;
        boolean fallbackAuthorized = true;
        boolean deterministicReplacement = planRejected || repaired.isBlank();
        if (planRejected || repaired.isBlank()) {
            repaired = deterministicRealization(contract);
        }
        // Always verdict the text that can actually reach the ledger. A repaired clause set or
        // deterministic realization must never inherit verdicts from the rejected provider
        // draft. This is also the bounded EPI-001 recovery owner: if an evidence realization
        // cannot be expressed safely (for example, first-person testimony viewed from the NPC),
        // fall back once to explicit epistemic uncertainty and validate that through the same
        // atomic-claim path. No second model call is involved.
        if (!normalize(original).equals(normalize(repaired))) {
            List<AtomicClaim> finalClaims = extractor.extract(repaired);
            List<AtomicClaimResult> fallbackResults = finalClaims.stream()
                    .map(claim -> assess(claim, contract, authoritativeActionResult)).toList();
            fallbackAuthorized = finalAuthorized(finalClaims, fallbackResults, contract);
            // Clause repair retains rejected draft verdicts for diagnostics. Whole-response
            // replacement exposes only the verdicts for the replacement that can be committed.
            if (deterministicReplacement) results = fallbackResults;
        }
        if (!fallbackAuthorized) {
            repaired = safeAnswerPlanFallback(contract);
            List<AtomicClaim> safeClaims = extractor.extract(repaired);
            List<AtomicClaimResult> safeResults = safeClaims.stream()
                    .map(claim -> assess(claim, contract, authoritativeActionResult)).toList();
            fallbackAuthorized = finalAuthorized(safeClaims, safeResults, contract);
            results = safeResults;
        }
        long repairMicros = (System.nanoTime() - repairStarted) / 1_000L;
        boolean repairedOutput = !normalize(original).equals(normalize(repaired));
        boolean valid = !repaired.isBlank() && fallbackAuthorized;
        long validationMicros = (System.nanoTime() - validationStarted) / 1_000L;
        String reason = !direct ? "DIRECT_ANSWER_OR_REQUIRED_SLOT_MISSING"
                : overBudget ? "OBJECTIVE_CLAIM_BUDGET_EXCEEDED"
                : !rejected.isEmpty() ? "UNSUPPORTED_CLAUSES_REMOVED"
                : "ALL_ATOMIC_CLAIMS_AUTHORIZED";
        return new Result(original, repaired, results, repairedOutput, valid, reason,
                extractionMicros, validationMicros,
                repairMicros,
                (System.nanoTime() - started) / 1_000L);
    }

    private static AtomicClaimResult assess(AtomicClaim claim, EpistemicContract contract,
            String authoritativeActionResult) {
        if (claim.mode() == ClaimMode.SUBJECTIVE_OPINION
                || claim.mode() == ClaimMode.EMOTION || claim.mode() == ClaimMode.DESIRE
                || claim.mode() == ClaimMode.METAPHOR || claim.mode() == ClaimMode.QUESTION) {
            return result(claim, ClaimSupportStatus.SUBJECTIVE_ALLOWED, List.of(),
                    "expressive non-objective speech is allowed");
        }
        if (claim.mode() == ClaimMode.HYPOTHETICAL) return result(claim,
                ClaimSupportStatus.HYPOTHETICAL_ALLOWED, List.of(),
                "explicitly hypothetical language");
        // Predicate-free punchlines/interjections are expressive content in a subjective
        // conversation. Objective copulas are extracted as a different predicate and still
        // require evidence, so this does not authorize unsupported world or biography claims.
        if (contract.answerability() == Answerability.SUBJECTIVE
                && claim.predicateKey().equals("ANSWER_VALUE")) {
            return result(claim, ClaimSupportStatus.SUBJECTIVE_ALLOWED, List.of(),
                    "predicate-free answer fragment in subjective conversation");
        }
        if (claim.predicateKey().equals("UNPARSEABLE_OBJECTIVE")) return result(claim,
                ClaimSupportStatus.UNPARSEABLE_OBJECTIVE_CLAIM, List.of(),
                "objective-looking clause could not be bound safely");
        if (claim.predicateKey().equals("ACTION_RESULT")
                || claim.predicateKey().equals("ACTION_COMMITMENT")) {
            String authority = authoritativeActionResult == null ? ""
                    : authoritativeActionResult.strip();
            boolean exactAction = !authority.isBlank()
                    && (tokenOverlap(claim.text(), authority) >= .20
                            || claim.predicateKey().equals("ACTION_COMMITMENT")
                                    && objectCompatible(claim.objectValue(),
                                            contract.answerPlan().requestedAction()));
            return exactAction
                    ? result(claim, ClaimSupportStatus.SUPPORTED, List.of("ACTION_RESULT"),
                            "matching authoritative action result is present")
                    : result(claim, ClaimSupportStatus.UNSUPPORTED, List.of(),
                            "capability or unrelated action is not this commit/result");
        }
        List<EvidenceRef> contradictions = contract.evidence().contradicting().stream()
                .filter(evidence -> compatible(claim, evidence)).toList();
        if (!contradictions.isEmpty()) return result(claim, ClaimSupportStatus.CONTRADICTED,
                ids(contradictions), "compatible evidence contradicts this value");
        List<EvidenceRef> supports = contract.evidence().supporting().stream()
                .filter(evidence -> compatible(claim, evidence)).toList();
        if (supports.isEmpty() && claim.predicateKey().equals("ANSWER_VALUE")) {
            supports = contract.evidence().supporting().stream()
                    .filter(evidence -> objectCompatible(claim.objectValue(), evidence.objectValue()))
                    .toList();
        }
        if (supports.isEmpty()) return result(claim, ClaimSupportStatus.UNSUPPORTED, List.of(),
                "no subject/predicate/property-compatible authorized evidence");
        boolean inferred = supports.stream().allMatch(value -> !value.authoritative()
                && value.status() != EpistemicStatus.KNOWN);
        return result(claim, inferred ? ClaimSupportStatus.SUPPORTED_AS_INFERENCE
                : ClaimSupportStatus.SUPPORTED, ids(supports),
                inferred ? "supported only as sourced belief/inference"
                        : "typed authorized proposition matched");
    }

    private static boolean compatible(AtomicClaim claim, EvidenceRef evidence) {
        String predicate = claim.predicateKey();
        if (!subjectCompatible(claim.subjectKey(), evidence.subjectKey())) return false;
        if (claim.temporalScope().equals("CURRENT")
                && (evidence.temporalScope().equals("HISTORICAL")
                        || evidence.freshness().equals("STALE"))) return false;
        if (predicate.equals("ACTION_CAPABILITY")) {
            return evidence.predicateKey().equals("ACTION_CAPABILITY")
                    && objectCompatible(claim.objectValue(), evidence.objectValue());
        }
        if (predicate.equals("RELATIONSHIP")) {
            return evidence.predicateKey().equals("RELATIONSHIP")
                    && objectCompatible(claim.objectValue(), evidence.objectValue());
        }
        if (predicate.equals("PAST_EVENT")) {
            return evidence.predicateKey().equals("PAST_EVENT")
                    && tokenOverlap(claim.text(), evidence.compactProposition()) >= .30;
        }
        if (predicate.equals("CURRENT_TASK")) {
            return (evidence.predicateKey().equals("CURRENT_TASK")
                    || evidence.predicateKey().equals("ACTIVE_OPERATION"))
                    && tokenOverlap(claim.text(), evidence.compactProposition()) >= .20;
        }
        if (predicate.equals("POSSESSION")) {
            return evidence.predicateKey().equals("POSSESSION")
                    && objectCompatible(claim.objectValue(), evidence.objectValue());
        }
        if (!predicate.equals(evidence.predicateKey())) return false;
        if (predicate.startsWith("PROPERTY:")) {
            // Entity presence in contextual evidence never reaches this support list and a
            // different PROPERTY predicate can never authorize this property.
            return objectCompatible(claim.objectValue(), evidence.objectValue())
                    || evidence.objectValue().isBlank();
        }
        return objectCompatible(claim.objectValue(), evidence.objectValue());
    }

    private static boolean directAnswerFirst(List<AtomicClaim> claims,
            List<AtomicClaimResult> results, EpistemicContract contract) {
        Answerability answerability = contract.answerability();
        if (claims.isEmpty()) return false;
        AtomicClaim first = claims.getFirst();
        AtomicClaimResult firstResult = results.getFirst();
        String firstText = normalize(first.text());
        return switch (answerability) {
            // KNOWN is an evidence-backed direct-answer contract. An uncertainty phrase is
            // safe prose, but it cannot discharge a required proposition that the contract
            // already knows. We replace such drafts deterministically from the admissible
            // evidence below rather than allowing the model to discard the answer.
            case KNOWN -> firstResult.releasable()
                    && contract.evidence().supporting().stream().anyMatch(evidence ->
                            compatible(first, evidence)
                            || first.predicateKey().equals("ANSWER_VALUE")
                                    && objectCompatible(first.objectValue(), evidence.objectValue()));
            case PARTIALLY_KNOWN, INFERRED -> uncertainty(firstText)
                    || firstResult.releasable()
                    && contract.evidence().supporting().stream().anyMatch(evidence ->
                            compatible(first, evidence)
                            || first.predicateKey().equals("ANSWER_VALUE")
                                    && objectCompatible(first.objectValue(), evidence.objectValue()));
            case UNKNOWN, NEEDS_CURRENT_PERCEPTION -> uncertainty(firstText);
            case CONFLICTED -> uncertainty(firstText) || firstText.contains("conflict");
            case NEEDS_CLARIFICATION, AMBIGUOUS, UNRESOLVED ->
                    (first.mode() == ClaimMode.QUESTION || firstText.startsWith("could ")
                            || firstText.startsWith("what ") || firstText.startsWith("which "))
                            && clarifiesAmbiguity(firstText,
                                    contract.dialogueFrame().ambiguityReason());
            case NEEDS_ACTION -> (first.predicateKey().startsWith("ACTION_")
                    && firstResult.releasable()) || uncertainty(firstText);
            case SUBJECTIVE -> subjectiveDirectAnswer(first, firstResult, contract);
            default -> firstResult.releasable();
        };
    }

    private static String repair(String original, List<AtomicClaimResult> accepted) {
        if (accepted.isEmpty()) return "";
        return accepted.stream().sorted(Comparator.comparingInt(value ->
                        value.claim().startInclusive()))
                .map(value -> value.claim().text().strip())
                .filter(value -> !value.isBlank())
                .collect(Collectors.joining(". "))
                .replaceAll("\\s+", " ").strip()
                .replaceAll("[.!?]+$", "") + ".";
    }

    private static String deterministicRealization(EpistemicContract contract) {
        DialogueFrame frame = contract.dialogueFrame();
        Answerability answerability = contract.answerability();
        EvidenceRef first = contract.evidence().supporting().stream()
                .filter(value -> value.predicateKey().equals(frame.predicateKey()))
                .findFirst().orElseGet(() -> contract.evidence().supporting().stream()
                        .findFirst().orElse(null));
        if (answerability == Answerability.KNOWN
                || answerability == Answerability.PARTIALLY_KNOWN
                || answerability == Answerability.INFERRED) {
            if (first == null) return "";
            if (frame.act() == DialogueAct.CORRECTION) {
                return first.objectValue() + ".";
            }
            if (frame.predicateKey().equals("NAME")
                    || contract.answerPlan().answerKind().equals("NAME")) {
                return first.objectValue() + ".";
            }
            if (frame.predicateKey().equals("HELD_ITEM")
                    || contract.answerPlan().answerKind().equals("HELD_ITEM")) {
                return first.objectValue().equals("NONE")
                    ? "You're holding nothing." : "You're holding " + first.objectValue() + ".";
            }
            if (frame.predicateKey().equals("CURRENT_TASK")
                    || contract.answerPlan().answerKind().equals("CURRENT_SELF_STATE")) {
                return "I'm working on " + first.objectValue().toLowerCase(Locale.ROOT) + ".";
            }
            if (frame.predicateKey().equals("PAST_EVENT")
                    || contract.answerPlan().answerKind().equals("RECALL")) {
                String recollection = playerTestimonyRealization(first.compactProposition());
                if (!recollection.isBlank()) return recollection;
            }
            if (frame.act() == DialogueAct.CLARIFICATION_REQUEST) {
                return "I meant this: " + first.compactProposition();
            }
            String proposition = first.compactProposition().replaceAll("[.!?]+$", "").strip();
            return proposition.isBlank() ? "I don't remember that clearly." : proposition + ".";
        }
        if (answerability == Answerability.SUBJECTIVE) {
            if (first != null && first.predicateKey().equals("DESIRE")) {
                return "I want " + first.objectValue().toLowerCase(Locale.ROOT) + ".";
            }
            if (first != null && first.predicateKey().equals("EMOTION")) {
                return "I feel " + first.objectValue().toLowerCase(Locale.ROOT) + ".";
            }
            if (first == null) return "I haven't settled on an opinion about that.";
            if (first.predicateKey().equals("LIKE")) return "I like " + first.objectValue() + ".";
            if (first.predicateKey().equals("DISLIKE")) return "I dislike " + first.objectValue() + ".";
            return "That's a matter of opinion for me.";
        }
        if (answerability == Answerability.CONFLICTED) {
            return "I've heard conflicting things, so I can't answer that as fact.";
        }
        if (answerability == Answerability.NEEDS_CLARIFICATION
                || answerability == Answerability.AMBIGUOUS
                || answerability == Answerability.UNRESOLVED) {
            if (frame.ambiguityReason().equals("UNRESOLVED_ACTION_OBJECT_AND_LOCATION")) {
                return "Which object and where do you mean?";
            }
            if (frame.ambiguityReason().equals("UNRESOLVED_ACTION_OBJECT")) {
                return "Which object do you mean?";
            }
            return "Could you clarify what you mean?";
        }
        if (answerability == Answerability.NEEDS_ACTION && first != null
                && first.predicateKey().equals("ACTION_CAPABILITY")) {
            return "I can " + first.objectValue().toLowerCase(Locale.ROOT)
                    .replace('_', ' ') + ".";
        }
        return "I don't know that.";
    }

    /** Re-expresses first-person player testimony without making it the NPC's biography. */
    private static String playerTestimonyRealization(String proposition) {
        String value = proposition == null ? "" : proposition.replaceAll("\\s+", " ").strip();
        value = value.replaceFirst("(?i)^Player-reported belief:\\s*", "");
        java.util.regex.Matcher quoted = java.util.regex.Pattern.compile(
                "(?i)player said:\\s*[\\\"“]([^\\\"”]+)[\\\"”]").matcher(value);
        if (quoted.find()) value = quoted.group(1).strip();
        value = value.replaceAll("[.!?]+$", "").strip();
        if (value.matches("(?i)^I\\s+have\\s+.+")) {
            value = value.replaceFirst("(?i)^I\\s+have\\s+", "you had ");
        } else if (value.matches("(?i)^I\\s+am\\s+.+")) {
            value = value.replaceFirst("(?i)^I\\s+am\\s+", "you were ");
        } else if (value.matches("(?i)^I\\s+.+")) {
            value = value.replaceFirst("(?i)^I\\s+", "you ");
        } else {
            return "";
        }
        return "You told me " + value + ".";
    }

    private static boolean finalAuthorized(List<AtomicClaim> claims,
            List<AtomicClaimResult> results, EpistemicContract contract) {
        if (claims.isEmpty() || claims.size() != results.size()) return false;
        return results.stream().allMatch(AtomicClaimResult::releasable)
                && directAnswerFirst(claims, results, contract);
    }

    private static String safeAnswerPlanFallback(EpistemicContract contract) {
        if (contract == null) return "I don't know that.";
        return switch (contract.answerability()) {
            case CONFLICTED -> "I've heard conflicting things, so I can't answer that as fact.";
            case NEEDS_CLARIFICATION, AMBIGUOUS, UNRESOLVED ->
                    "Could you clarify what you mean?";
            case KNOWN, PARTIALLY_KNOWN, INFERRED ->
                    contract.queryPlan().queryKind().equals("EPISODIC_RECALL")
                            ? "I don't remember that clearly." : "I can't tell you that safely.";
            default -> "I don't know that.";
        };
    }

    private static AtomicClaimResult result(AtomicClaim claim, ClaimSupportStatus status,
            List<String> evidence, String reason) {
        return new AtomicClaimResult(claim, status, evidence, reason);
    }
    private static List<String> ids(List<EvidenceRef> values) {
        return values.stream().map(EvidenceRef::stableId).toList();
    }
    private static boolean subjectCompatible(String claim, String evidence) {
        if (claim == null || claim.isBlank() || claim.equals("UNRESOLVED")) return false;
        if (claim.equals(evidence)) return true;
        return claim.startsWith("OBJECT:") && evidence.startsWith("OBJECT:")
                && objectCompatible(claim.substring(7), evidence.substring(7));
    }
    private static boolean objectCompatible(String claim, String evidence) {
        String a = normalizeKey(claim), b = normalizeKey(evidence);
        if ((a.equals("NOTHING") || a.equals("EMPTY") || a.equals("EMPTY_HANDED"))
                && b.equals("NONE") || (b.equals("NOTHING") || b.equals("EMPTY")
                        || b.equals("EMPTY_HANDED")) && a.equals("NONE")) return true;
        if (a.isBlank() || b.isBlank()) return a.isBlank() && b.isBlank();
        return a.equals(b) || a.contains(b) || b.contains(a)
                || tokenOverlap(a, b) >= .50;
    }
    private static double tokenOverlap(String left, String right) {
        Set<String> a = tokens(left), b = tokens(right);
        if (a.isEmpty() || b.isEmpty()) return 0;
        long common = a.stream().filter(b::contains).count();
        return common / (double) Math.min(a.size(), b.size());
    }
    private static Set<String> tokens(String value) {
        return java.util.Arrays.stream(normalizeKey(value).split("_+"))
                .filter(token -> !token.isBlank())
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
    }
    private static boolean uncertainty(String text) {
        return text.contains("don't know") || text.contains("do not know")
                || text.contains("can't tell") || text.contains("cannot tell")
                || text.contains("don't remember") || text.contains("not sure")
                || text.contains("unclear") || text.contains("conflict");
    }
    private static boolean subjectiveDirectAnswer(AtomicClaim first,
            AtomicClaimResult firstResult, EpistemicContract contract) {
        if (!firstResult.releasable()) return false;
        String predicate = contract.dialogueFrame().predicateKey();
        if (predicate.equals("DESIRE")) {
            return first.mode() == ClaimMode.DESIRE || first.predicateKey().equals("DESIRE");
        }
        if (predicate.equals("EMOTION")) {
            return (first.mode() == ClaimMode.EMOTION || first.predicateKey().equals("EMOTION"))
                    && contract.evidence().supporting().stream()
                            .filter(value -> value.predicateKey().equals("EMOTION"))
                            .anyMatch(value -> objectCompatible(first.objectValue(),
                                    value.objectValue()));
        }
        return true;
    }
    private static boolean clarifiesAmbiguity(String text, String reason) {
        if (!"UNRESOLVED_ACTION_OBJECT_AND_LOCATION".equals(reason)
                && !"UNRESOLVED_ACTION_OBJECT".equals(reason)) return true;
        boolean object = text.contains("which") || text.contains("what")
                || text.contains("object") || text.contains("item")
                || text.contains("this") || text.contains("that") || text.contains("it");
        boolean location = text.contains("where") || text.contains("location")
                || text.contains("place") || text.contains("spot")
                || text.contains("here") || text.contains("there");
        return object && (!"UNRESOLVED_ACTION_OBJECT_AND_LOCATION".equals(reason) || location);
    }
    private static String normalize(String value) { return value == null ? "" : value
            .toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{N}' ]", " ")
            .replaceAll("\\s+", " ").strip(); }
    private static String normalizeKey(String value) { return normalize(value).toUpperCase(Locale.ROOT)
            .replace(' ', '_'); }

    public record Result(String originalDialogue, String dialogue,
            List<AtomicClaimResult> claims, boolean repaired, boolean valid, String reason,
            long extractionMicros, long validationMicros, long repairMicros,
            long totalMicros) {
        public Result {
            originalDialogue = originalDialogue == null ? "" : originalDialogue;
            dialogue = dialogue == null ? "" : dialogue;
            claims = List.copyOf(claims == null ? List.of() : claims);
            reason = reason == null ? "" : reason;
        }
    }
}
