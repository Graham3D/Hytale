package com.inigmasgames.persistentnpcs.training.teacher;

import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.inigmasgames.persistentnpcs.training.corpus.ProductionInputSnapshot;
import com.inigmasgames.persistentnpcs.training.registry.ArtifactIds;
import com.inigmasgames.persistentnpcs.training.registry.CanonicalJson;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class TeacherContracts {
    private TeacherContracts() { }
    public enum Capability { GENERATE_TARGET, CRITIQUE_STUDENT_OUTPUT, RANK_PREFERENCE, HEALTH_CHECK }
    public enum TaskType { TARGET_GENERATION, CRITIQUE, PREFERENCE_RANKING }
    public enum OutputTrust { PROPOSED_LABEL, UNVERIFIED }

    public record TeacherIdentity(int schemaVersion, String sourceId, String provider,
            String model, String modelRevision, String policyId,
            String termsSnapshotHash, Map<String, String> metadata) {
        public static final int SCHEMA_VERSION = 1;
        public TeacherIdentity {
            if (schemaVersion != SCHEMA_VERSION || blank(sourceId) || blank(provider)
                    || blank(model) || blank(modelRevision) || blank(policyId)
                    || termsSnapshotHash == null
                    || !termsSnapshotHash.matches("[0-9a-fA-F]{64}")) {
                throw new IllegalArgumentException("complete teacher identity required");
            }
            metadata = Map.copyOf(metadata == null ? Map.of() : metadata);
        }
        public String contentId() { return "teacher_" + CanonicalJson.sha256(this); }
    }

    public record TeacherTaskConfig(int schemaVersion, TaskType taskType,
            JsonElement rubric, int maximumAttempts, long timeoutMillis,
            int maximumConcurrency, Map<String, String> determinism) {
        public TeacherTaskConfig {
            if (schemaVersion != 1 || taskType == null || timeoutMillis < 1
                    || maximumAttempts < 1 || maximumAttempts > 2
                    || maximumConcurrency < 1 || maximumConcurrency > 8) {
                throw new IllegalArgumentException("bounded teacher task config required");
            }
            rubric = rubric == null ? JsonNull.INSTANCE : rubric.deepCopy();
            determinism = Map.copyOf(determinism == null ? Map.of() : determinism);
        }
    }

    /** Rubric is deliberately separate from productionInput.messages. */
    public record TeacherRequest(int schemaVersion, String requestId,
            ArtifactIds.TrainingCandidateId candidateId,
            ProductionInputSnapshot productionInput,
            String studentOutput, TeacherTaskConfig taskConfig) {
        public TeacherRequest {
            if (schemaVersion != 1 || blank(requestId) || candidateId == null
                    || productionInput == null || taskConfig == null) {
                throw new IllegalArgumentException("complete teacher request required");
            }
            studentOutput = studentOutput == null ? "" : studentOutput;
        }
    }

    /** Structured response contains conclusions only; hidden chain-of-thought has no field. */
    public record TeacherResponse(int schemaVersion, String requestId,
            String finalAnswer, String critique, Map<String, Double> scores,
            String rationaleSummary, List<String> evidenceRefs,
            double confidence, boolean refusal) {
        public TeacherResponse {
            if (schemaVersion != 1 || blank(requestId)) throw new IllegalArgumentException(
                    "teacher response identity required");
            finalAnswer = finalAnswer == null ? "" : finalAnswer;
            critique = critique == null ? "" : critique;
            scores = Map.copyOf(scores == null ? Map.of() : scores);
            rationaleSummary = rationaleSummary == null ? "" : rationaleSummary;
            evidenceRefs = List.copyOf(evidenceRefs == null ? List.of() : evidenceRefs);
            confidence = Math.max(0.0, Math.min(1.0, confidence));
            if (!refusal && finalAnswer.isBlank()) throw new IllegalArgumentException(
                    "non-refusal teacher response requires finalAnswer");
        }
    }

    public record TeacherRunManifest(int schemaVersion, ArtifactIds.TeacherRunId id,
            TeacherIdentity teacher, TeacherSourcePolicy policySnapshot,
            String requestHash, String responseHash, TeacherTaskConfig taskConfig,
            OutputTrust trust, int attempts, long elapsedMillis, Instant completedAt) {
        public TeacherRunManifest {
            if (schemaVersion != 1 || id == null || teacher == null || policySnapshot == null
                    || blank(requestHash) || blank(responseHash) || taskConfig == null
                    || trust == null || attempts < 1 || elapsedMillis < 0 || completedAt == null) {
                throw new IllegalArgumentException("complete teacher run manifest required");
            }
            if (trust != OutputTrust.PROPOSED_LABEL && trust != OutputTrust.UNVERIFIED) {
                throw new IllegalArgumentException("D3 output cannot be gold");
            }
        }
    }

    public record TeacherRunResult(TeacherRunManifest manifest, TeacherResponse response) { }
    public record Health(boolean available, String detail) { }
    private static boolean blank(String value) { return value == null || value.isBlank(); }
}
