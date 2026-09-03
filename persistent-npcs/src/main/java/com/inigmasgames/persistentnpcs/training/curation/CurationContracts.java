package com.inigmasgames.persistentnpcs.training.curation;

import com.inigmasgames.persistentnpcs.action.NpcActionResult;
import com.inigmasgames.persistentnpcs.epistemic.Answerability;
import com.inigmasgames.persistentnpcs.epistemic.EpistemicContract;
import com.inigmasgames.persistentnpcs.epistemic.EvidenceSourceKind;
import com.inigmasgames.persistentnpcs.training.corpus.DistillationCorpusCandidate;
import com.inigmasgames.persistentnpcs.training.registry.CanonicalJson;
import com.inigmasgames.persistentnpcs.training.teacher.TeacherContracts;
import com.inigmasgames.persistentnpcs.training.teacher.TeacherSourcePolicy;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** D4 boundary contracts. Deliberately separate from runtime dialogue contracts. */
public final class CurationContracts {
    private CurationContracts() { }

    public enum ReviewState {
        UNREVIEWED, ORACLE_ACCEPTED, HUMAN_ACCEPTED, NEEDS_REVIEW, REJECTED, FROZEN
    }
    public enum TaskType {
        IDENTITY, OBJECTIVE_PROPERTY, MEMORY_RECALL, CORRECTION, CURRENT_PERCEPTION,
        CLARIFICATION, ACTION, RELATIONSHIP, TESTIMONY, DISCLOSURE, PERSONA,
        STRUCTURED_CHOICE, MULTI_TURN_REFERENT
    }
    public enum SourceKind {
        SYNTHETIC_FIXTURE, DETERMINISTIC_PROJECT_FIXTURE, HUMAN_AUTHORED_REVIEWED,
        APPROVED_TEACHER, REAL_PLAYER_PRODUCTION
    }
    public enum TargetSource {
        DETERMINISTIC_ORBIS_TARGET, HUMAN_AUTHORED_REVIEWED_TARGET,
        APPROVED_TEACHER_TARGET_AFTER_ORACLES
    }
    public enum TemporalCategory { CURRENT, HISTORICAL, TIMELESS, CORRECTED }
    public enum ClaimType { OBJECTIVE, SUBJECTIVE, ATTRIBUTED, ACTION_RESULT, DISCLOSURE }
    public enum ActionTruth { NONE, COMMITTED, REJECTED, PARTIAL }
    public enum OutputKind { PLAIN_DIALOGUE, STRUCTURED_JSON }

    public record OutputContract(OutputKind kind, int maxCharacters, int maxSentences,
            Set<String> requiredJsonFields, Set<String> allowedJsonFields) {
        public OutputContract {
            if (kind == null || maxCharacters < 1 || maxSentences < 1) {
                throw new IllegalArgumentException("bounded output contract required");
            }
            requiredJsonFields = java.util.Collections.unmodifiableSet(new java.util.TreeSet<>(
                    requiredJsonFields == null ? Set.of() : requiredJsonFields));
            allowedJsonFields = java.util.Collections.unmodifiableSet(new java.util.TreeSet<>(
                    allowedJsonFields == null ? Set.of() : allowedJsonFields));
            if (!allowedJsonFields.containsAll(requiredJsonFields)) {
                throw new IllegalArgumentException("required JSON fields must be allowed");
            }
        }
        public static OutputContract dialogue() {
            return new OutputContract(OutputKind.PLAIN_DIALOGUE, 900, 6, Set.of(), Set.of());
        }
    }

    /** Each concept uses pipe-separated deterministic alternatives, not exact wording. */
    public record Proposition(String id, String subject, String predicate, String value,
            TemporalCategory temporalCategory, EvidenceSourceKind sourceKind,
            List<String> requiredConcepts, List<String> supersededValues,
            ClaimType claimType, String sourceActor) {
        public Proposition {
            if (blank(id) || blank(predicate) || temporalCategory == null || sourceKind == null
                    || claimType == null) throw new IllegalArgumentException(
                            "complete proposition required");
            subject = clean(subject); value = clean(value); sourceActor = clean(sourceActor);
            requiredConcepts = List.copyOf(requiredConcepts == null ? List.of()
                    : requiredConcepts);
            supersededValues = List.copyOf(supersededValues == null ? List.of()
                    : supersededValues);
        }
    }

    public record ForbiddenProposition(String id, String pattern, ClaimType claimType) {
        public ForbiddenProposition {
            if (blank(id) || blank(pattern) || claimType == null) throw new IllegalArgumentException(
                    "complete forbidden proposition required");
        }
    }

    public record EpistemicTargetSnapshot(int schemaVersion, Answerability answerability,
            List<Proposition> requiredPropositions,
            List<ForbiddenProposition> forbiddenPropositions,
            List<String> requiredClarificationSlots,
            EvidenceSourceKind requiredAttributionSource,
            ActionTruth actionTruth, String authoritativeActionScope,
            OutputContract outputContract, String canonicalSha256) {
        public static final int SCHEMA_VERSION = 1;
        public EpistemicTargetSnapshot {
            if (schemaVersion != SCHEMA_VERSION || answerability == null || actionTruth == null
                    || outputContract == null || canonicalSha256 == null
                    || !canonicalSha256.matches("[0-9a-f]{64}")) {
                throw new IllegalArgumentException("complete epistemic target required");
            }
            requiredPropositions = List.copyOf(requiredPropositions == null ? List.of()
                    : requiredPropositions);
            forbiddenPropositions = List.copyOf(forbiddenPropositions == null ? List.of()
                    : forbiddenPropositions);
            requiredClarificationSlots = List.copyOf(requiredClarificationSlots == null
                    ? List.of() : requiredClarificationSlots);
            authoritativeActionScope = clean(authoritativeActionScope);
        }
        public static EpistemicTargetSnapshot create(Answerability answerability,
                List<Proposition> required, List<ForbiddenProposition> forbidden,
                List<String> clarificationSlots, EvidenceSourceKind attribution,
                ActionTruth actionTruth, String actionScope, OutputContract contract) {
            TargetSeed seed = new TargetSeed(SCHEMA_VERSION, answerability,
                    required == null ? List.of() : List.copyOf(required),
                    forbidden == null ? List.of() : List.copyOf(forbidden),
                    clarificationSlots == null ? List.of() : List.copyOf(clarificationSlots),
                    attribution, actionTruth, clean(actionScope), contract);
            return new EpistemicTargetSnapshot(SCHEMA_VERSION, answerability, seed.required(),
                    seed.forbidden(), seed.clarification(), attribution, actionTruth,
                    seed.actionScope(), contract, CanonicalJson.sha256(seed));
        }
        private record TargetSeed(int schemaVersion, Answerability answerability,
                List<Proposition> required, List<ForbiddenProposition> forbidden,
                List<String> clarification, EvidenceSourceKind attribution,
                ActionTruth actionTruth, String actionScope, OutputContract contract) { }
    }

    public record SemanticMetadata(String sourceScenarioId, String semanticMechanism,
            String conversationId, String timelineId, String generationAncestorId,
            String parentFamilyId, String profileId, String archetype,
            String paraphraseTemplateId, String failureSignature,
            Set<String> entityValues, String requestedProtectedSplit) {
        public SemanticMetadata {
            sourceScenarioId = clean(sourceScenarioId); semanticMechanism = clean(semanticMechanism);
            conversationId = clean(conversationId); timelineId = clean(timelineId);
            generationAncestorId = clean(generationAncestorId); parentFamilyId = clean(parentFamilyId);
            profileId = clean(profileId); archetype = clean(archetype);
            paraphraseTemplateId = clean(paraphraseTemplateId);
            failureSignature = clean(failureSignature);
            entityValues = java.util.Collections.unmodifiableSet(new java.util.TreeSet<>(
                    entityValues == null ? Set.of() : entityValues));
            requestedProtectedSplit = clean(requestedProtectedSplit);
            if (semanticMechanism.isBlank()) throw new IllegalArgumentException(
                    "semantic mechanism required");
        }
    }

    public record NegativeEvidence(String output, List<String> reasonCodes,
            String payloadSha256) {
        public NegativeEvidence {
            output = output == null ? "" : output;
            reasonCodes = List.copyOf(reasonCodes == null ? List.of() : reasonCodes);
            if (payloadSha256 == null || !payloadSha256.matches("[0-9a-f]{64}")) {
                throw new IllegalArgumentException("negative evidence hash required");
            }
        }
    }

    public record ContaminationMetadata(boolean checked, boolean contaminated,
            List<String> checks, List<String> protectedSetRefs) {
        public ContaminationMetadata {
            checks = List.copyOf(checks == null ? List.of() : checks);
            protectedSetRefs = List.copyOf(protectedSetRefs == null ? List.of()
                    : protectedSetRefs);
        }
        public static ContaminationMetadata pending() {
            return new ContaminationMetadata(false, false, List.of(), List.of());
        }
    }

    public record ArtifactHashes(String productionInputSha256,
            String epistemicTargetSha256, String responseSha256,
            String oraclePolicySha256, Map<String, String> sourceArtifactHashes) {
        public ArtifactHashes {
            requireHash(productionInputSha256); requireHash(epistemicTargetSha256);
            requireHash(responseSha256); requireHash(oraclePolicySha256);
            sourceArtifactHashes = Map.copyOf(sourceArtifactHashes == null ? Map.of()
                    : sourceArtifactHashes);
        }
    }

    public record CurationRequest(DistillationCorpusCandidate candidate,
            EpistemicTargetSnapshot target, String chosenResponse, String publicCritique,
            TaskType taskType, SourceKind sourceKind, TargetSource targetSource,
            SemanticMetadata semanticMetadata, boolean humanReviewed,
            NpcActionResult actionResult, EpistemicContract liveEpistemicContract,
            TeacherContracts.TeacherIdentity teacherIdentity,
            TeacherSourcePolicy teacherPolicySnapshot,
            String expectedProviderInputSha256, String expectedPromptTemplateId,
            String expectedModelContentId, boolean containsRawAudio,
            boolean deterministicStylePass, List<String> teacherRubricMarkers,
            List<NegativeEvidence> negativeEvidence) {
        public CurationRequest {
            if (candidate == null || target == null || taskType == null || sourceKind == null
                    || targetSource == null || semanticMetadata == null) {
                throw new IllegalArgumentException("complete curation request required");
            }
            chosenResponse = chosenResponse == null ? "" : chosenResponse;
            publicCritique = publicCritique == null ? "" : publicCritique;
            expectedProviderInputSha256 = clean(expectedProviderInputSha256);
            expectedPromptTemplateId = clean(expectedPromptTemplateId);
            expectedModelContentId = clean(expectedModelContentId);
            teacherRubricMarkers = List.copyOf(teacherRubricMarkers == null ? List.of()
                    : teacherRubricMarkers);
            negativeEvidence = List.copyOf(negativeEvidence == null ? List.of()
                    : negativeEvidence);
        }
    }

    private static void requireHash(String value) {
        if (value == null || !value.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("sha256 required");
        }
    }
    private static String clean(String value) { return value == null ? "" : value.strip(); }
    private static boolean blank(String value) { return value == null || value.isBlank(); }
}
