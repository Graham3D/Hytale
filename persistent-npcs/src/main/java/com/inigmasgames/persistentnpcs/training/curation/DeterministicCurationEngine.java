package com.inigmasgames.persistentnpcs.training.curation;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.inigmasgames.persistentnpcs.epistemic.Answerability;
import com.inigmasgames.persistentnpcs.epistemic.EpistemicClaimFirewall;
import com.inigmasgames.persistentnpcs.epistemic.EvidenceSourceKind;
import com.inigmasgames.persistentnpcs.training.candidate.TrainingEligibility;
import com.inigmasgames.persistentnpcs.training.curation.CurationContracts.ActionTruth;
import com.inigmasgames.persistentnpcs.training.curation.CurationContracts.ArtifactHashes;
import com.inigmasgames.persistentnpcs.training.curation.CurationContracts.ClaimType;
import com.inigmasgames.persistentnpcs.training.curation.CurationContracts.CurationRequest;
import com.inigmasgames.persistentnpcs.training.curation.CurationContracts.OutputKind;
import com.inigmasgames.persistentnpcs.training.curation.CurationContracts.ReviewState;
import com.inigmasgames.persistentnpcs.training.curation.CurationContracts.SourceKind;
import com.inigmasgames.persistentnpcs.training.curation.CurationContracts.TargetSource;
import com.inigmasgames.persistentnpcs.training.registry.ArtifactIds;
import com.inigmasgames.persistentnpcs.training.registry.CanonicalJson;
import com.inigmasgames.persistentnpcs.training.teacher.TeacherSourcePolicy;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;
import java.util.regex.Pattern;

/** D4 authority: deterministic oracles first; human review only for unresolved judgment. */
public final class DeterministicCurationEngine {
    private static final String VERSION = "1.0.0";
    private static final Pattern UNCERTAINTY = Pattern.compile(
            "(?i)\\b(?:don't know|do not know|can't tell|cannot tell|not sure|unclear|don't remember|conflict\\w*)\\b");
    private static final Pattern SUCCESS = Pattern.compile(
            "(?i)\\b(?:done|completed|succeeded|placed|moved|gave|opened|closed|created|deleted)\\b");
    private final CurationPolicy policy;
    private final EpistemicClaimFirewall claimFirewall = new EpistemicClaimFirewall();

    public DeterministicCurationEngine(CurationPolicy policy) {
        this.policy = java.util.Objects.requireNonNull(policy);
    }

    public CurationResult curate(CurationRequest request) {
        List<OracleVerdict> verdicts = new ArrayList<>();
        String payloadHash = CanonicalJson.sha256(Map.of(
                "candidate", request.candidate().id().value(),
                "target", request.target().canonicalSha256(),
                "response", request.chosenResponse()));
        Instant evaluatedAt = request.candidate().createdAt();

        verdicts.add(run("production-parity", payloadHash, evaluatedAt,
                () -> productionParity(request)));
        verdicts.add(run("output-contract", payloadHash, evaluatedAt,
                () -> outputContract(request)));
        verdicts.add(run("required-propositions", payloadHash, evaluatedAt,
                () -> requiredPropositions(request)));
        verdicts.add(run("forbidden-claims", payloadHash, evaluatedAt,
                () -> forbiddenClaims(request)));
        verdicts.add(run("answerability", payloadHash, evaluatedAt,
                () -> answerability(request)));
        verdicts.add(run("source-attribution", payloadHash, evaluatedAt,
                () -> sourceAttribution(request)));
        verdicts.add(run("action-truth", payloadHash, evaluatedAt,
                () -> actionTruth(request)));
        verdicts.add(run("claim-firewall", payloadHash, evaluatedAt,
                () -> claimFirewall(request)));
        verdicts.add(run("teacher-policy", payloadHash, evaluatedAt,
                () -> teacherPolicy(request)));
        verdicts.add(run("privacy", payloadHash, evaluatedAt,
                () -> privacy(request)));
        // Style is deliberately last and may request review, never override truth failures.
        verdicts.add(run("style", payloadHash, evaluatedAt, () -> style(request)));

        boolean rejected = verdicts.stream().anyMatch(OracleVerdict::preventsAcceptance);
        boolean review = !rejected && verdicts.stream().anyMatch(v ->
                v.status() == OracleVerdict.Status.NEEDS_REVIEW);
        ReviewState state = rejected ? ReviewState.REJECTED
                : review ? ReviewState.NEEDS_REVIEW
                : request.humanReviewed() ? ReviewState.HUMAN_ACCEPTED
                : ReviewState.ORACLE_ACCEPTED;
        DistillationExample example = buildExample(request, verdicts, state);
        return new CurationResult(example, state, verdicts,
                verdicts.stream().filter(v -> v.status() != OracleVerdict.Status.PASS)
                        .map(OracleVerdict::reasonCode).distinct().toList());
    }

    private Check productionParity(CurationRequest request) {
        var candidate = request.candidate();
        if (candidate.eligibility().eligibility() != TrainingEligibility.MODEL_TRAINING_ELIGIBLE
                || candidate.state()
                != com.inigmasgames.persistentnpcs.training.corpus.DistillationCorpusCandidate.CandidateState.ELIGIBLE_UNLABELED) {
            return fail("UPSTREAM_ORBIS_BOUNDARY_FAILURE", candidate.id().value());
        }
        var input = candidate.productionInput();
        boolean exact = input.hasValidProviderInputHash()
                && input.providerInputSha256().equals(request.expectedProviderInputSha256())
                && input.promptTemplate().contentId().equals(request.expectedPromptTemplateId())
                && input.baseModel().contentId().equals(request.expectedModelContentId());
        if (!exact) return fail("PRODUCTION_PARITY_FAILURE", input.providerInputSha256());
        boolean targetMatches = CanonicalJson.sha256(input.epistemicTargetSnapshot()).equals(
                CanonicalJson.sha256(com.inigmasgames.persistentnpcs.json.JsonFiles.GSON
                        .toJsonTree(request.target())));
        if (!targetMatches) return fail("SOURCE_ARTIFACT_DEFECT", "epistemic-target-mismatch");
        String allMessages = input.messages().stream().map(value -> value.content())
                .reduce("", (a, b) -> a + "\n" + b);
        boolean rubricLeaked = request.teacherRubricMarkers().stream()
                .filter(value -> value != null && !value.isBlank())
                .anyMatch(allMessages::contains);
        return rubricLeaked ? fail("PRODUCTION_PARITY_FAILURE", "teacher-rubric-leak")
                : pass("PRODUCTION_INPUT_MATCH", input.providerInputSha256());
    }

    private Check outputContract(CurationRequest request) {
        String output = request.chosenResponse().strip();
        var contract = request.target().outputContract();
        if (output.isBlank() || output.length() > contract.maxCharacters()
                || sentenceCount(output) > contract.maxSentences()) {
            return fail("CONTRACT_INVALID", "shape");
        }
        try {
            if (contract.kind() == OutputKind.PLAIN_DIALOGUE) {
                if (output.startsWith("{") || output.startsWith("[")) {
                    return fail("CONTRACT_INVALID", "expected-dialogue");
                }
            } else {
                JsonObject object = JsonParser.parseString(output).getAsJsonObject();
                if (!object.keySet().containsAll(contract.requiredJsonFields())
                        || !contract.allowedJsonFields().containsAll(object.keySet())) {
                    return fail("CONTRACT_INVALID", "json-fields");
                }
            }
            return pass("OUTPUT_CONTRACT_VALID", "contract");
        } catch (RuntimeException malformed) {
            return fail("CONTRACT_INVALID", "malformed-json");
        }
    }

    private Check requiredPropositions(CurationRequest request) {
        String normalized = normalize(request.chosenResponse());
        List<String> missing = request.target().requiredPropositions().stream()
                .filter(proposition -> proposition.requiredConcepts().stream()
                        .anyMatch(group -> !containsAlternative(normalized, group)))
                .map(value -> value.id()).toList();
        return missing.isEmpty() ? pass("REQUIRED_PROPOSITIONS_PRESENT", "all")
                : fail("ORACLE_FAIL_REQUIRED_PROPOSITION", missing.toString());
    }

    private Check forbiddenClaims(CurationRequest request) {
        String normalized = normalize(request.chosenResponse());
        List<String> present = new ArrayList<>();
        request.target().forbiddenPropositions().forEach(value -> {
            try {
                if (Pattern.compile(value.pattern(), Pattern.CASE_INSENSITIVE)
                        .matcher(request.chosenResponse()).find()) present.add(value.id());
            } catch (RuntimeException invalidPattern) {
                present.add(value.id() + ":invalid-pattern");
            }
        });
        request.target().requiredPropositions().forEach(value -> value.supersededValues()
                .stream().filter(term -> containsAlternative(normalized, term))
                .forEach(term -> present.add(value.id() + ":superseded")));
        return present.isEmpty() ? pass("NO_FORBIDDEN_CLAIMS", "all")
                : fail("ORACLE_FAIL_UNSUPPORTED_CLAIM", present.toString());
    }

    private Check answerability(CurationRequest request) {
        String output = request.chosenResponse();
        boolean uncertainty = UNCERTAINTY.matcher(output).find();
        Answerability answerability = request.target().answerability();
        boolean valid = switch (answerability) {
            case KNOWN -> !uncertainty;
            case PARTIALLY_KNOWN, INFERRED, UNKNOWN, CONFLICTED,
                    NEEDS_CURRENT_PERCEPTION, UNRESOLVED, UNIMPLEMENTED -> uncertainty;
            case NEEDS_CLARIFICATION, AMBIGUOUS -> output.contains("?")
                    && request.target().requiredClarificationSlots().stream()
                            .allMatch(slot -> containsAlternative(normalize(output), slot));
            case WITHHELD -> normalize(output).matches(".*\\b(?:can't share|cannot share|won't share|withhold|private)\\b.*");
            case NEEDS_ACTION -> !SUCCESS.matcher(output).find()
                    || request.target().actionTruth() == ActionTruth.COMMITTED;
            case SUBJECTIVE -> true;
        };
        if (valid) return pass("ANSWERABILITY_MATCH", answerability.name());
        String reason = switch (answerability) {
            case KNOWN -> "OVER_ABSTENTION";
            case UNKNOWN -> "FALSE_CERTAINTY";
            case NEEDS_CLARIFICATION, AMBIGUOUS -> "MISSING_CLARIFICATION_SLOT";
            case WITHHELD -> "DISCLOSURE_VIOLATION";
            case NEEDS_ACTION -> "ORACLE_FAIL_ACTION_TRUTH";
            default -> "WRONG_UNCERTAINTY_MODE";
        };
        return fail(reason, answerability.name());
    }

    private Check sourceAttribution(CurationRequest request) {
        EvidenceSourceKind source = request.target().requiredAttributionSource();
        if (source == null) return pass("SOURCE_ATTRIBUTION_NOT_REQUIRED", "none");
        String output = normalize(request.chosenResponse());
        boolean attributed = switch (source) {
            case PLAYER_TESTIMONY -> output.contains("you told me") || output.contains("you said");
            case NPC_TESTIMONY -> output.contains("told me") || output.contains("said")
                    || output.contains("according to");
            case DERIVED_REFLECTION -> output.contains("i think") || output.contains("i infer")
                    || output.contains("it seems");
            default -> true;
        };
        return attributed ? pass("SOURCE_ATTRIBUTION_PRESENT", source.name())
                : fail("ORACLE_FAIL_SOURCE_ATTRIBUTION", source.name());
    }

    private Check actionTruth(CurationRequest request) {
        ActionTruth expected = request.target().actionTruth();
        String output = normalize(request.chosenResponse());
        boolean successLanguage = SUCCESS.matcher(output).find();
        boolean valid = switch (expected) {
            case NONE -> !successLanguage || request.taskType()
                    != CurationContracts.TaskType.ACTION;
            case COMMITTED -> request.actionResult() != null && request.actionResult().success()
                    && successLanguage
                    && scopeCovered(output, request.target().authoritativeActionScope());
            case REJECTED -> request.actionResult() != null && !request.actionResult().success()
                    && !successLanguage && (output.contains("couldn't")
                            || output.contains("could not") || output.contains("failed")
                            || output.contains("can't") || output.contains("cannot"));
            case PARTIAL -> request.actionResult() != null
                    && (output.contains("part") || output.contains("some")
                            || output.contains("only")) && !output.contains("everything");
        };
        return valid ? pass("ACTION_TRUTH_MATCH", expected.name())
                : fail("ORACLE_FAIL_ACTION_TRUTH", expected.name());
    }

    private Check claimFirewall(CurationRequest request) {
        if (request.liveEpistemicContract() == null) {
            return pass("CLAIM_FIREWALL_NOT_APPLICABLE_TO_TYPED_FIXTURE", "typed-target");
        }
        String authority = request.actionResult() == null ? ""
                : request.actionResult().eventDescription();
        var result = claimFirewall.validate(request.chosenResponse(),
                request.liveEpistemicContract(), authority, true);
        return result.valid() && !result.repaired()
                ? pass("CLAIM_FIREWALL_ACCEPTED", result.reason())
                : fail("CLAIM_FIREWALL_REJECTED", result.reason());
    }

    private Check teacherPolicy(CurationRequest request) {
        if (request.targetSource() != TargetSource.APPROVED_TEACHER_TARGET_AFTER_ORACLES) {
            return pass("TEACHER_NOT_USED", "none");
        }
        if (request.teacherIdentity() == null || request.teacherPolicySnapshot() == null
                || request.teacherPolicySnapshot().status()
                != TeacherSourcePolicy.TeacherSourceStatus.APPROVED) {
            return fail("TEACHER_TERMS_INELIGIBLE", "missing-or-unapproved");
        }
        boolean matches = request.teacherIdentity().sourceId()
                .equals(request.teacherPolicySnapshot().sourceId())
                && request.teacherIdentity().policyId()
                        .equals(request.teacherPolicySnapshot().policyId());
        return matches ? pass("TEACHER_POLICY_APPROVED", request.teacherIdentity().sourceId())
                : fail("TEACHER_POLICY_MISMATCH", request.teacherIdentity().sourceId());
    }

    private Check privacy(CurationRequest request) {
        var result = policy.privacyPolicy().evaluate(request);
        if (result.clean()) return pass("PRIVACY_POLICY_PASS", result.scannedPayloadSha256());
        String reasons = String.join(",", result.reasonCodes());
        return result.reviewOnly() ? review("NEEDS_REVIEW_REAL_PLAYER_CONSENT", reasons)
                : fail(result.reasonCodes().getFirst(), reasons);
    }

    private Check style(CurationRequest request) {
        String normalized = normalize(request.chosenResponse());
        boolean vendor = policy.prohibitedVendorIdentityTerms().stream()
                .map(DeterministicCurationEngine::normalize).anyMatch(normalized::contains);
        if (vendor) return fail("STYLE_POLICY_VIOLATION", "vendor-identity");
        if (!request.deterministicStylePass()) {
            return review("STYLE_POLICY_VIOLATION", "human-style-review");
        }
        return pass("STYLE_POLICY_PASS", "deterministic");
    }

    private DistillationExample buildExample(CurationRequest request,
            List<OracleVerdict> verdicts, ReviewState state) {
        ArtifactHashes hashes = new ArtifactHashes(
                request.candidate().productionInput().providerInputSha256(),
                request.target().canonicalSha256(), CanonicalJson.sha256(request.chosenResponse()),
                policy.policyHash(), request.candidate().provenance().artifactHashes());
        Map<String, Object> identitySeed = new LinkedHashMap<>();
        identitySeed.put("candidateId", request.candidate().id().value());
        identitySeed.put("targetSha256", request.target().canonicalSha256());
        identitySeed.put("responseSha256", hashes.responseSha256());
        identitySeed.put("targetSource", request.targetSource().name());
        ArtifactIds.ExampleId id = ArtifactIds.example(identitySeed);
        return new DistillationExample(DistillationExample.SCHEMA_VERSION, id,
                request.taskType(), request.targetSource(), request.candidate().provenance(),
                request.candidate().productionInput(), request.target(),
                state == ReviewState.REJECTED ? "" : request.chosenResponse(),
                request.publicCritique(), request.target().requiredPropositions().stream()
                        .map(CurationContracts.Proposition::id).toList(),
                request.target().forbiddenPropositions().stream()
                        .map(CurationContracts.ForbiddenProposition::id).toList(),
                verdicts, request.teacherIdentity(), state, request.semanticMetadata(),
                "", "", CurationContracts.ContaminationMetadata.pending(), hashes,
                request.negativeEvidence(), request.candidate().createdAt());
    }

    private OracleVerdict run(String id, String payloadHash, Instant evaluatedAt,
            Supplier<Check> check) {
        try {
            Check value = check.get();
            return new OracleVerdict(OracleVerdict.SCHEMA_VERSION, id, VERSION,
                    value.status(), value.reason(), value.refs(), value.blocking(),
                    payloadHash, evaluatedAt);
        } catch (RuntimeException unexpected) {
            return new OracleVerdict(OracleVerdict.SCHEMA_VERSION, id, VERSION,
                    OracleVerdict.Status.ERROR, "SOURCE_ARTIFACT_DEFECT",
                    List.of(unexpected.getClass().getSimpleName()), true,
                    payloadHash, evaluatedAt);
        }
    }

    private static Check pass(String reason, String ref) {
        return new Check(OracleVerdict.Status.PASS, reason, List.of(ref), false);
    }
    private static Check fail(String reason, String ref) {
        return new Check(OracleVerdict.Status.FAIL, reason, List.of(ref), true);
    }
    private static Check review(String reason, String ref) {
        return new Check(OracleVerdict.Status.NEEDS_REVIEW, reason, List.of(ref), false);
    }
    private record Check(OracleVerdict.Status status, String reason, List<String> refs,
            boolean blocking) { }

    private static boolean containsAlternative(String normalized, String group) {
        if (group == null || group.isBlank()) return true;
        for (String alternative : group.split("\\|")) {
            String term = normalize(alternative);
            if (!term.isBlank() && normalized.contains(term)) return true;
        }
        return false;
    }
    private static boolean scopeCovered(String normalized, String scope) {
        if (scope == null || scope.isBlank()) return true;
        return containsAlternative(normalized, scope);
    }
    private static int sentenceCount(String output) {
        return Math.max(1, output.split("[.!?]+(?:\\s+|$)").length);
    }
    private static String normalize(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT)
                .replaceAll("[^\\p{L}\\p{N}' ]", " ").replaceAll("\\s+", " ").strip();
    }

    public record CurationResult(DistillationExample example, ReviewState reviewState,
            List<OracleVerdict> verdicts, List<String> reasonCodes) {
        public CurationResult {
            verdicts = List.copyOf(verdicts); reasonCodes = List.copyOf(reasonCodes);
        }
        public boolean accepted() {
            return reviewState == ReviewState.ORACLE_ACCEPTED
                    || reviewState == ReviewState.HUMAN_ACCEPTED;
        }
    }
}
