package com.inigmasgames.persistentnpcs.training;

import com.google.gson.JsonObject;
import com.inigmasgames.persistentnpcs.evaluation.EvaluationContracts;
import com.inigmasgames.persistentnpcs.llm.ChatMessage;
import com.inigmasgames.persistentnpcs.llm.LlmProvider;
import com.inigmasgames.persistentnpcs.llm.LlmProviderStatus;
import com.inigmasgames.persistentnpcs.llm.LlmRequest;
import com.inigmasgames.persistentnpcs.llm.LlmResult;
import com.inigmasgames.persistentnpcs.training.candidate.EligibilityEvidence;
import com.inigmasgames.persistentnpcs.training.candidate.TrainingEligibility;
import com.inigmasgames.persistentnpcs.training.candidate.TrainingEligibilityClassifier;
import com.inigmasgames.persistentnpcs.training.corpus.CorpusJsonlExporter;
import com.inigmasgames.persistentnpcs.training.corpus.DistillationCorpusBuilder;
import com.inigmasgames.persistentnpcs.training.corpus.DistillationCorpusCandidate;
import com.inigmasgames.persistentnpcs.training.corpus.ExactInputCaptureProvider;
import com.inigmasgames.persistentnpcs.training.corpus.ProductionInputSnapshot;
import com.inigmasgames.persistentnpcs.training.registry.AppendOnlyJsonlRegistry;
import com.inigmasgames.persistentnpcs.training.registry.ArtifactRoot;
import com.inigmasgames.persistentnpcs.training.registry.CanonicalJson;
import com.inigmasgames.persistentnpcs.training.registry.ModelIdentity;
import com.inigmasgames.persistentnpcs.training.registry.PromptTemplateIdentity;
import com.inigmasgames.persistentnpcs.training.teacher.ReviewedTeacherImport;
import com.inigmasgames.persistentnpcs.training.teacher.TeacherContracts;
import com.inigmasgames.persistentnpcs.training.teacher.TeacherGateway;
import com.inigmasgames.persistentnpcs.training.teacher.TeacherPolicyRegistry;
import com.inigmasgames.persistentnpcs.training.teacher.TeacherProvider;
import com.inigmasgames.persistentnpcs.training.teacher.TeacherRunStore;
import com.inigmasgames.persistentnpcs.training.teacher.TeacherSourcePolicy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;

public final class R125OrbisDistillationBlock1Test {
    private R125OrbisDistillationBlock1Test() { }

    public static void main(String[] args) throws Exception {
        Path temp = Files.createTempDirectory("orbis-training-block1-");
        Path save = temp.resolve("active-save");
        Files.createDirectories(save);
        ArtifactRoot root = new ArtifactRoot(temp.resolve("offline-artifacts"), save);
        root.initialize();
        assert ArtifactRoot.DIRECTORIES.stream().allMatch(name ->
                Files.isDirectory(root.path().resolve(name)));
        assertThrows(() -> new ArtifactRoot(save.resolve("training"), save));
        assertThrows(() -> root.resolve("..", "escape"));
        assert !TrainingMode.OFF.permitsModelMutation();
        assert !TrainingMode.CORPUS_AUDIT.permitsModelMutation();

        JsonObject unordered = new JsonObject();
        unordered.addProperty("z", "a\r\nb"); unordered.addProperty("a", "value");
        JsonObject ordered = new JsonObject();
        ordered.addProperty("a", "value"); ordered.addProperty("z", "a\nb");
        assert CanonicalJson.serialize(unordered).equals(CanonicalJson.serialize(ordered));
        assert CanonicalJson.sha256(unordered).equals(CanonicalJson.sha256(ordered));

        AppendOnlyJsonlRegistry registry = new AppendOnlyJsonlRegistry(
                root.resolve("registry", "immutability-test.jsonl"));
        assert registry.append("stable-id", Map.of("value", 1));
        assert !registry.append("stable-id", Map.of("value", 1));
        assertThrows(() -> registry.append("stable-id", Map.of("value", 2)));

        TrainingEligibilityClassifier classifier = new TrainingEligibilityClassifier();
        EnumMap<EvaluationContracts.BoundaryId, EligibilityEvidence.BoundaryState> pass =
                all(EligibilityEvidence.BoundaryState.PASS);
        assert classifier.classify(evidence(pass, providerDiagnosis(),
                EligibilityEvidence.BoundaryState.PASS,
                EligibilityEvidence.BoundaryState.PASS, true)).eligibility()
                == TrainingEligibility.MODEL_TRAINING_ELIGIBLE;

        EnumMap<EvaluationContracts.BoundaryId, EligibilityEvidence.BoundaryState> sourceFail =
                new EnumMap<>(pass);
        sourceFail.put(EvaluationContracts.BoundaryId.RETRIEVAL,
                EligibilityEvidence.BoundaryState.FAIL);
        assert classifier.classify(evidence(sourceFail, diagnosis(
                EvaluationContracts.BoundaryId.RETRIEVAL,
                EvaluationContracts.FailureClass.RETRIEVAL),
                EligibilityEvidence.BoundaryState.PASS,
                EligibilityEvidence.BoundaryState.PASS, true)).eligibility()
                == TrainingEligibility.ORBIS_SOURCE_REPAIR_REQUIRED;
        assert classifier.classify(evidence(pass, providerDiagnosis(),
                EligibilityEvidence.BoundaryState.FAIL,
                EligibilityEvidence.BoundaryState.PASS, true)).eligibility()
                == TrainingEligibility.ORACLE_OR_DATA_REPAIR_REQUIRED;
        assert classifier.classify(evidence(pass, diagnosis(
                EvaluationContracts.BoundaryId.CANONICAL_RESPONSE,
                EvaluationContracts.FailureClass.CANONICAL_DELIVERY),
                EligibilityEvidence.BoundaryState.PASS,
                EligibilityEvidence.BoundaryState.UNKNOWN, true)).eligibility()
                == TrainingEligibility.CONNECTED_VALIDATION_REQUIRED;
        assert classifier.classify(evidence(pass, null,
                EligibilityEvidence.BoundaryState.PASS,
                EligibilityEvidence.BoundaryState.PASS, true)).eligibility()
                == TrainingEligibility.NOT_TRAINABLE;
        assert classifier.classify(evidence(pass, providerDiagnosis(),
                EligibilityEvidence.BoundaryState.PASS,
                EligibilityEvidence.BoundaryState.PASS, false)).eligibility()
                == TrainingEligibility.NEEDS_REVIEW;

        String sha = "a".repeat(64);
        ModelIdentity model = new ModelIdentity(1, "example/model", "pinned-revision",
                sha, "nemotron_h", "BF16", sha, sha, Map.of("test", "fixture"));
        PromptTemplateIdentity template = new PromptTemplateIdentity(1,
                "ConversationContextBuilder", "R124", sha, Map.of());
        LlmRequest productionRequest = new LlmRequest(UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), List.of(new ChatMessage("system", "identity"),
                        new ChatMessage("user", "Where is the inn?")));
        JsonObject rubric = new JsonObject(); rubric.addProperty("privateRubric", "teacher-only");
        ProductionInputSnapshot snapshot = ProductionInputSnapshot.capture(productionRequest,
                Map.of("temperature", "0.7", "maxTokens", "180"), "GENERAL_SOCIAL",
                template, model, new JsonObject(), new JsonObject(), new JsonObject(),
                new JsonObject(), new JsonObject());
        assert snapshot.messages().equals(productionRequest.canonicalMessages());
        assert snapshot.messages().stream().noneMatch(message ->
                message.content().contains("privateRubric"));
        AtomicReference<ProductionInputSnapshot> captured = new AtomicReference<>();
        LlmProvider noOpProvider = new LlmProvider() {
            @Override public CompletableFuture<LlmResult> generateResponse(LlmRequest request) {
                return CompletableFuture.completedFuture(null);
            }
            @Override public CompletableFuture<LlmProviderStatus> checkStatus() {
                return CompletableFuture.completedFuture(null);
            }
        };
        new ExactInputCaptureProvider(noOpProvider, ignored -> snapshot,
                (requestAtBoundary, capturedSnapshot) -> captured.set(capturedSnapshot))
                .generateResponse(productionRequest).join();
        assert captured.get().providerInputSha256().equals(snapshot.providerInputSha256());

        EligibilityEvidence eligibleEvidence = evidence(pass, providerDiagnosis(),
                EligibilityEvidence.BoundaryState.PASS,
                EligibilityEvidence.BoundaryState.PASS, true);
        var provenance = new DistillationCorpusCandidate.SourceProvenance("eval-1",
                "scenario-1", "turn-1", List.of(12L), Map.of("trace", sha));
        DistillationCorpusCandidate candidate = new DistillationCorpusBuilder(classifier)
                .build(snapshot, "incorrect answer", new JsonObject(), eligibleEvidence,
                        provenance);
        assert candidate.state()
                == DistillationCorpusCandidate.CandidateState.ELIGIBLE_UNLABELED;
        assert candidate.id().value().startsWith("tc_");
        CorpusJsonlExporter exporter = new CorpusJsonlExporter(
                TrainingMode.CORPUS_AUDIT, root);
        assert exporter.export(candidate);
        assert !exporter.export(candidate);
        assert Files.readString(root.resolve("candidates", "distillation-candidates.jsonl"))
                .contains(candidate.id().value());
        assertThrows(() -> new CorpusJsonlExporter(TrainingMode.OFF, root).export(candidate));

        TeacherSourcePolicy approved = new TeacherSourcePolicy(1,
                "human-reviewed-import-v1", "operator-reviewed-offline-import",
                TeacherSourcePolicy.TeacherSourceStatus.APPROVED,
                "internal-review-policy-2026-09-03", "operator-owned-labels",
                Set.of("TARGET_GENERATION"), Set.of("HIDDEN_REASONING_PERSISTENCE"),
                "fixture approval", Instant.parse("2026-09-03T00:00:00Z"));
        TeacherSourcePolicy rejected = new TeacherSourcePolicy(1, "rejected-v1",
                "hosted-output", TeacherSourcePolicy.TeacherSourceStatus.REJECTED,
                "terms-v1", "none", Set.of(), Set.of("MODEL_TRAINING"), "rejected",
                Instant.parse("2026-09-03T00:00:00Z"));
        TeacherPolicyRegistry policies = new TeacherPolicyRegistry(List.of(approved, rejected));
        var teacherIdentity = new TeacherContracts.TeacherIdentity(1, approved.sourceId(),
                "offline-import", "human-reviewed", "v1", approved.policyId(),
                approved.snapshotHash(), Map.of());
        var task = new TeacherContracts.TeacherTaskConfig(1,
                TeacherContracts.TaskType.TARGET_GENERATION, rubric, 1, 2_000, 1,
                Map.of("sampling", "none"));
        var request = new TeacherContracts.TeacherRequest(1, "teacher-request-1",
                candidate.id(), snapshot, candidate.originalModelOutput(), task);
        TeacherProvider provider = new FixtureTeacherProvider(teacherIdentity,
                new TeacherContracts.TeacherResponse(1, request.requestId(),
                        "The inn is beside the market.", "Ground the location.",
                        Map.of("grounding", 1.0), "Uses the authorized location evidence.",
                        List.of("evidence-1"), 0.95, false));
        try (TeacherGateway gateway = new TeacherGateway(policies, 1)) {
            var result = gateway.execute(provider, request);
            assert result.manifest().trust() == TeacherContracts.OutputTrust.PROPOSED_LABEL;
            assert result.manifest().requestHash().equals(CanonicalJson.sha256(request));
            TeacherRunStore store = new TeacherRunStore(TrainingMode.CORPUS_AUDIT, root);
            assert store.persist(result);
            assert !store.persist(result);
        }
        var rejectedIdentity = new TeacherContracts.TeacherIdentity(1, rejected.sourceId(),
                "hosted", "unknown", "v1", rejected.policyId(), rejected.snapshotHash(),
                Map.of());
        try (TeacherGateway gateway = new TeacherGateway(policies, 1)) {
            assertThrows(() -> gateway.execute(new FixtureTeacherProvider(rejectedIdentity,
                    provider.generateTarget(request)), request));
        }

        ReviewedTeacherImport importer = new ReviewedTeacherImport(root,
                id -> id.equals(candidate.id().value()));
        JsonObject importEnvelope = new JsonObject();
        importEnvelope.addProperty("candidateId", candidate.id().value());
        importEnvelope.add("response", com.inigmasgames.persistentnpcs.json.JsonFiles.GSON
                .toJsonTree(provider.generateTarget(request)));
        assert importer.importLine(importEnvelope.toString()).candidateId()
                .equals(candidate.id().value());
        assertThrows(() -> importer.importLine(importEnvelope.toString()));
        JsonObject hidden = importEnvelope.deepCopy();
        hidden.addProperty("chainOfThought", "must never be persisted as a teacher label");
        assertThrows(() -> importer.importLine(hidden.toString()));
        assert Files.list(root.resolve("quarantine")).count() >= 2;

        assert Files.isRegularFile(Path.of("training", "schemas", "model-identity.schema.json"));
        assertProductionRuntimeUnwired();
        System.out.println("R125 Orbis Distillation Block 1 D0-D3 tests passed.");
    }

    private static EligibilityEvidence evidence(
            Map<EvaluationContracts.BoundaryId, EligibilityEvidence.BoundaryState> stages,
            EvaluationContracts.RootCauseDiagnosis diagnosis,
            EligibilityEvidence.BoundaryState oracle,
            EligibilityEvidence.BoundaryState connected, boolean complete) {
        return new EligibilityEvidence(1, stages, diagnosis, oracle, connected, complete,
                "eval-1");
    }

    private static EnumMap<EvaluationContracts.BoundaryId, EligibilityEvidence.BoundaryState>
            all(EligibilityEvidence.BoundaryState state) {
        EnumMap<EvaluationContracts.BoundaryId, EligibilityEvidence.BoundaryState> result =
                new EnumMap<>(EvaluationContracts.BoundaryId.class);
        for (var boundary : EvaluationContracts.BoundaryId.values()) result.put(boundary, state);
        return result;
    }

    private static EvaluationContracts.RootCauseDiagnosis providerDiagnosis() {
        return diagnosis(EvaluationContracts.BoundaryId.PROVIDER,
                EvaluationContracts.FailureClass.PROVIDER_REALIZATION);
    }

    private static EvaluationContracts.RootCauseDiagnosis diagnosis(
            EvaluationContracts.BoundaryId boundary,
            EvaluationContracts.FailureClass failureClass) {
        return new EvaluationContracts.RootCauseDiagnosis(boundary, failureClass,
                "fixture-invariant", "fixture-owner", "expected", "actual", List.of(12L),
                List.of(), 0.95);
    }

    private static void assertProductionRuntimeUnwired() throws Exception {
        try (var paths = Files.walk(Path.of("src", "main", "java"))) {
            for (Path path : paths.filter(Files::isRegularFile).toList()) {
                if (path.toString().contains("persistentnpcs" + java.io.File.separator
                        + "training")) continue;
                assert !Files.readString(path).contains("persistentnpcs.training")
                        : "production runtime imports offline training package: " + path;
            }
        }
    }

    private static void assertThrows(Throwing action) {
        boolean threw = false;
        try { action.run(); } catch (Exception expected) { threw = true; }
        assert threw;
    }

    @FunctionalInterface private interface Throwing { void run() throws Exception; }

    private record FixtureTeacherProvider(TeacherContracts.TeacherIdentity identity,
            TeacherContracts.TeacherResponse response) implements TeacherProvider {
        @Override public Set<TeacherContracts.Capability> capabilities() {
            return Set.of(TeacherContracts.Capability.GENERATE_TARGET,
                    TeacherContracts.Capability.HEALTH_CHECK);
        }
        @Override public TeacherContracts.TeacherResponse generateTarget(
                TeacherContracts.TeacherRequest request) { return response; }
        @Override public TeacherContracts.TeacherResponse critiqueStudentOutput(
                TeacherContracts.TeacherRequest request) { throw new UnsupportedOperationException(); }
        @Override public TeacherContracts.TeacherResponse rankPreference(
                TeacherContracts.TeacherRequest request) { throw new UnsupportedOperationException(); }
        @Override public TeacherContracts.Health healthCheck() {
            return new TeacherContracts.Health(true, "fixture");
        }
    }
}
