package com.inigmasgames.persistentnpcs.training.registry;

/** Strongly typed, content-derived identifiers used by the offline artifact graph. */
public final class ArtifactIds {
    private ArtifactIds() { }

    public record TrainingCandidateId(String value) { public TrainingCandidateId { value = valid("candidate", value); } }
    public record ExampleId(String value) { public ExampleId { value = valid("example", value); } }
    public record DatasetRowId(String value) { public DatasetRowId { value = valid("row", value); } }
    public record DatasetId(String value) { public DatasetId { value = valid("dataset", value); } }
    public record DatasetVersionId(String value) { public DatasetVersionId { value = valid("dataset-version", value); } }
    public record SemanticFamilyId(String value) { public SemanticFamilyId { value = valid("semantic-family", value); } }
    public record TeacherRunId(String value) { public TeacherRunId { value = valid("teacher-run", value); } }
    public record TrainingRunId(String value) { public TrainingRunId { value = valid("training-run", value); } }
    public record EvaluationRunId(String value) { public EvaluationRunId { value = valid("evaluation-run", value); } }
    public record ModelBundleId(String value) { public ModelBundleId { value = valid("model-bundle", value); } }
    public record PromotionId(String value) { public PromotionId { value = valid("promotion", value); } }

    public static TrainingCandidateId candidate(Object value) {
        return new TrainingCandidateId("tc_" + CanonicalJson.sha256(value));
    }

    public static DatasetRowId row(Object value) {
        return new DatasetRowId("dr_" + CanonicalJson.sha256(value));
    }

    public static ExampleId example(Object value) {
        return new ExampleId("ex_" + CanonicalJson.sha256(value));
    }

    public static SemanticFamilyId semanticFamily(Object value) {
        return new SemanticFamilyId("sf_" + CanonicalJson.sha256(value));
    }

    public static DatasetId dataset(Object value) {
        return new DatasetId("ds_" + CanonicalJson.sha256(value));
    }

    public static DatasetVersionId datasetVersion(Object value) {
        return new DatasetVersionId("dv_" + CanonicalJson.sha256(value));
    }

    public static TeacherRunId teacherRun(Object value) {
        return new TeacherRunId("tr_" + CanonicalJson.sha256(value));
    }

    private static String valid(String kind, String value) {
        if (value == null || !value.matches("[a-z0-9][a-z0-9_-]{8,96}")) {
            throw new IllegalArgumentException("valid content-derived " + kind + " id required");
        }
        return value;
    }
}
