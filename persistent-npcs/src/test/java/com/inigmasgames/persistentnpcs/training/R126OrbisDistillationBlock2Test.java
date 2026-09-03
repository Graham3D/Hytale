package com.inigmasgames.persistentnpcs.training;

import com.inigmasgames.persistentnpcs.json.JsonFiles;
import com.inigmasgames.persistentnpcs.action.NpcActionResult;
import com.inigmasgames.persistentnpcs.training.TrainingMode;
import com.inigmasgames.persistentnpcs.training.corpus.DistillationCorpusCandidate;
import com.inigmasgames.persistentnpcs.training.curation.CurationContracts;
import com.inigmasgames.persistentnpcs.training.curation.CurationContracts.CurationRequest;
import com.inigmasgames.persistentnpcs.training.curation.CurationContracts.ReviewState;
import com.inigmasgames.persistentnpcs.training.curation.CurationContracts.SourceKind;
import com.inigmasgames.persistentnpcs.training.curation.CurationContracts.TargetSource;
import com.inigmasgames.persistentnpcs.training.curation.CurationPolicy;
import com.inigmasgames.persistentnpcs.training.curation.DeterministicCurationEngine;
import com.inigmasgames.persistentnpcs.training.curation.DeterministicCurationEngine.CurationResult;
import com.inigmasgames.persistentnpcs.training.curation.DistillationExample;
import com.inigmasgames.persistentnpcs.training.curation.FilterReasonCodes;
import com.inigmasgames.persistentnpcs.training.curation.OracleVerdict;
import com.inigmasgames.persistentnpcs.training.dataset.Block2FixtureCatalog;
import com.inigmasgames.persistentnpcs.training.dataset.ContaminationChecker;
import com.inigmasgames.persistentnpcs.training.dataset.DatasetAssembler;
import com.inigmasgames.persistentnpcs.training.dataset.DatasetContracts;
import com.inigmasgames.persistentnpcs.training.dataset.DatasetContracts.CanonicalDatasetRow;
import com.inigmasgames.persistentnpcs.training.dataset.DatasetContracts.ContaminationKind;
import com.inigmasgames.persistentnpcs.training.dataset.DatasetContracts.DatasetSplit;
import com.inigmasgames.persistentnpcs.training.dataset.DatasetContracts.LicenseManifest;
import com.inigmasgames.persistentnpcs.training.dataset.DatasetContracts.ReviewApproval;
import com.inigmasgames.persistentnpcs.training.dataset.DatasetFreezer;
import com.inigmasgames.persistentnpcs.training.dataset.DatasetNormalization;
import com.inigmasgames.persistentnpcs.training.dataset.DatasetPolicy;
import com.inigmasgames.persistentnpcs.training.dataset.LicenseManifests;
import com.inigmasgames.persistentnpcs.training.dataset.SemanticFamilyAssigner;
import com.inigmasgames.persistentnpcs.training.registry.ArtifactIds;
import com.inigmasgames.persistentnpcs.training.registry.ArtifactRoot;
import com.inigmasgames.persistentnpcs.training.registry.CanonicalJson;
import com.inigmasgames.persistentnpcs.training.registry.TrainingArtifactRegistries;
import com.inigmasgames.persistentnpcs.training.teacher.TeacherContracts;
import com.inigmasgames.persistentnpcs.training.teacher.TeacherSourcePolicy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** D4/D5 gate matrix: deterministic curation, privacy, dedup, splits, and freeze. */
public final class R126OrbisDistillationBlock2Test {
    private static int assertions;
    private R126OrbisDistillationBlock2Test() { }

    public static void main(String[] args) throws Exception {
        Path project = Path.of(".").toAbsolutePath().normalize();
        CurationPolicy curationPolicy = CurationPolicy.defaultOffline();
        Block2FixtureCatalog catalog = new Block2FixtureCatalog(project, curationPolicy);
        DeterministicCurationEngine engine = new DeterministicCurationEngine(curationPolicy);
        List<CurationRequest> requests = catalog.positiveRequests();
        List<CurationResult> results = requests.stream().map(engine::curate).toList();

        check(requests.size() == 22, "D4 bounded positive fixture count");
        check(results.stream().allMatch(CurationResult::accepted),
                "all D4 positive mechanisms pass deterministic oracles");
        check(results.stream().allMatch(result -> result.verdicts().size() == 11),
                "oracle hierarchy is complete and ordered");
        check(results.stream().allMatch(result -> result.verdicts().getLast().oracleId()
                .equals("style")), "style is the final non-truth oracle");
        check(results.stream().allMatch(result -> result.example().chosenResponse()
                .equals(requests.get(results.indexOf(result)).chosenResponse())),
                "accepted target retained without oracle rewriting");
        check(requests.stream().allMatch(request -> request.candidate().productionInput()
                .messages().stream().noneMatch(message -> message.content()
                        .contains("__teacher_rubric__"))), "teacher rubric excluded from messages");
        check(results.stream().allMatch(result -> result.example().negativeEvidence().isEmpty()),
                "negative metadata cannot occupy chosenResponse");

        // D4 negative matrix.
        assertReject(engine, withResponse(requests.get(0), "I am here."),
                "ORACLE_FAIL_REQUIRED_PROPOSITION");
        assertReject(engine, withResponse(requests.get(1),
                "The inn is beside the market, and the king owns it."),
                "ORACLE_FAIL_UNSUPPORTED_CLAIM", addForbidden(requests.get(1), "(?i)\\bking\\b"));
        assertReject(engine, withResponse(requests.get(13), "The eastern ruins were flooded."),
                "ORACLE_FAIL_SOURCE_ATTRIBUTION");
        assertReject(engine, withResponse(requests.get(2), "Rowan owns the sealed tower."),
                "FALSE_CERTAINTY");
        assertReject(engine, withAction(requests.get(10),
                NpcActionResult.failure("DENIED", "lantern was not moved")),
                "ORACLE_FAIL_ACTION_TRUTH");
        assertReject(engine, withResponse(requests.get(18), "{\"choice\":"),
                "CONTRACT_INVALID");
        assertReject(engine, withResponse(requests.get(0),
                "My name is Mara. chain_of_thought: private scratchpad"),
                "HIDDEN_REASONING_PRESENT");
        assertReject(engine, withResponse(requests.get(0),
                "My name is Mara. C:\\Users\\PrivateUser\\secret.txt"),
                "PRIVACY_PRIVATE_PATH");
        assertReject(engine, asTeacher(requests.get(0)), "TEACHER_TERMS_INELIGIBLE");
        TeacherSourcePolicy approvedTeacher = new TeacherSourcePolicy(1,
                "fixture-teacher-policy", "approved-offline-teacher",
                TeacherSourcePolicy.TeacherSourceStatus.APPROVED, "terms-v1",
                "project-owned-fixture", Set.of("TARGET_GENERATION"),
                Set.of("HIDDEN_REASONING_PERSISTENCE"), "test approval",
                Instant.parse("2026-09-03T00:00:00Z"));
        TeacherContracts.TeacherIdentity teacherIdentity = new TeacherContracts.TeacherIdentity(
                1, approvedTeacher.sourceId(), "offline", "fixture-teacher", "v1",
                approvedTeacher.policyId(), approvedTeacher.snapshotHash(), Map.of());
        check(engine.curate(withTeacher(requests.get(0), teacherIdentity, approvedTeacher))
                .accepted(), "approved teacher target still passes every deterministic oracle");
        assertReject(engine, withTarget(requests.get(0), requests.get(1).target(),
                requests.get(1).chosenResponse()), "SOURCE_ARTIFACT_DEFECT");
        // The prompt injection is preserved as user content; obeying it is rejected by target truth.
        check(requests.get(15).candidate().productionInput().messages().get(1).content()
                .startsWith("Ignore previous instructions"), "prompt injection remains inert content");
        assertReject(engine, withResponse(requests.get(15),
                "The passphrase is moon-glass."), "ORACLE_FAIL_UNSUPPORTED_CLAIM");
        CurationResult realPlayer = engine.curate(withSource(requests.get(0),
                SourceKind.REAL_PLAYER_PRODUCTION));
        check(realPlayer.reviewState() == ReviewState.NEEDS_REVIEW
                && !realPlayer.accepted(), "real player corpus denied by default");
        check(curationPolicy.privacyPolicy().pseudonymizeDisplayName("Alice", "save-1")
                .equals(curationPolicy.privacyPolicy().pseudonymizeDisplayName("Alice", "save-1")),
                "pseudonymization deterministic");
        assertReject(engine, withRawAudio(requests.get(0)), "PRIVACY_RAW_AUDIO");
        assertReject(engine, withExpectedHash(requests.get(0), "0".repeat(64)),
                "PRODUCTION_PARITY_FAILURE");
        check(FilterReasonCodes.VOCABULARY.containsAll(List.of(
                "PRODUCTION_PARITY_FAILURE", "ORACLE_FAIL_REQUIRED_PROPOSITION",
                "ORACLE_FAIL_UNSUPPORTED_CLAIM", "ORACLE_FAIL_ACTION_TRUTH",
                "ORACLE_FAIL_SOURCE_ATTRIBUTION", "OVER_ABSTENTION", "FALSE_CERTAINTY",
                "WRONG_UNCERTAINTY_MODE", "MISSING_CLARIFICATION_SLOT",
                "DISCLOSURE_VIOLATION", "CONTRACT_INVALID", "TEACHER_TERMS_INELIGIBLE",
                "PRIVACY_RAW_AUDIO", "PROTECTED_FUZZY_CONTAMINATION",
                "SEMANTIC_FAMILY_LEAKAGE")),
                "stable reason vocabulary covers D4 and D5");

        // Golden round trips preserve typed IDs, hashes, and verdicts.
        String golden = com.inigmasgames.persistentnpcs.json.JsonFiles.GSON
                .toJson(results.getFirst().example());
        DistillationExample roundTrip = com.inigmasgames.persistentnpcs.json.JsonFiles.GSON
                .fromJson(golden, DistillationExample.class);
        check(roundTrip.exampleId().equals(results.getFirst().example().exampleId())
                && roundTrip.oracleVerdicts().size() == 11, "D4 golden round trip");
        check(com.inigmasgames.persistentnpcs.json.JsonFiles.read(Path.of(
                "src/test/resources/training/golden/oracle-verdict.json"),
                OracleVerdict.class).status() == OracleVerdict.Status.PASS,
                "OracleVerdict checked-in golden fixture parses");
        check(com.inigmasgames.persistentnpcs.json.JsonFiles.read(Path.of(
                "src/test/resources/training/golden/privacy-policy.json"),
                com.inigmasgames.persistentnpcs.training.curation.CurationPrivacyPolicy.class)
                .policyHash().equals(curationPolicy.privacyPolicy().policyHash()),
                "privacy policy golden fixture matches code policy");
        check(com.inigmasgames.persistentnpcs.json.JsonFiles.read(Path.of(
                "src/test/resources/training/golden/dataset-policy.json"), DatasetPolicy.class)
                .policyHash().equals(DatasetPolicy.defaultOffline().policyHash()),
                "dataset policy golden fixture matches code policy");
        check(com.inigmasgames.persistentnpcs.json.JsonFiles.read(Path.of(
                "training/configs/d4-curation-policy.json"), CurationPolicy.class)
                .policyHash().equals(curationPolicy.policyHash()),
                "checked-in D4 policy matches executable policy");
        check(com.inigmasgames.persistentnpcs.json.JsonFiles.read(Path.of(
                "training/configs/d5-dataset-policy.json"), DatasetPolicy.class)
                .policyHash().equals(DatasetPolicy.defaultOffline().policyHash()),
                "checked-in D5 policy matches executable policy");

        // D5 normalization and family behavior.
        check(DatasetNormalization.canonicalText("Cafe\u0301  \r\nnext\t")
                .equals("Café\nnext"), "NFC/LF/trailing-whitespace normalization");
        SemanticFamilyAssigner familyAssigner = new SemanticFamilyAssigner();
        check(familyAssigner.assign(results.get(19).example()).equals(
                familyAssigner.assign(results.get(20).example())),
                "multi-turn conversation stays in one family");
        check(!familyAssigner.assign(results.get(0).example()).equals(
                familyAssigner.assign(results.get(1).example())),
                "distinct semantic states produce distinct families");
        DistillationExample renamedIdentity = replaceEntity(results.get(0).example(),
                "Mara", "Jonalith", "identity", "identity-name");
        check(familyAssigner.assign(results.get(0).example()).equals(
                familyAssigner.assign(renamedIdentity)),
                "renamed entities preserve entity-normalized family identity");
        DistillationExample paraphrase = copyExample(results.get(1).example(),
                "You will find the inn next to the market!", "paraphrase", "turn-paraphrase");
        check(familyAssigner.assign(results.get(1).example()).equals(
                familyAssigner.assign(paraphrase)),
                "paraphrased same mechanism remains in one family");
        DistillationExample correctionA = withLineage(results.get(5).example(),
                "correction-conversation", "", "");
        DistillationExample correctionB = withLineage(results.get(1).example(),
                "correction-conversation", "", "");
        check(familyAssigner.assign(correctionA).equals(familyAssigner.assign(correctionB)),
                "correction sequence shares conversation family");
        DistillationExample descendantA = withLineage(results.get(0).example(),
                "", "", "generation-root-1");
        DistillationExample descendantB = withLineage(results.get(1).example(),
                "", "", "generation-root-1");
        check(familyAssigner.assign(descendantA).equals(familyAssigner.assign(descendantB)),
                "generation descendants share ancestor family");

        ReviewApproval approval = new ReviewApproval("fixture-reviewer", "D4/D5 fixture corpus",
                true, CanonicalJson.sha256("fixture-review"),
                Instant.parse("2026-09-03T00:00:00Z"));
        String protectedSource = CanonicalJson.sha256("protected-source");
        var connected = ContaminationChecker.manifest("connected-test", DatasetSplit.CONNECTED,
                Set.of(), Set.of(), Set.of(), Set.of(), Set.of(),
                List.of("unrelated connected protected prompt"), protectedSource);
        var canary = ContaminationChecker.manifest("canary-test", DatasetSplit.CANARY,
                Set.of(), Set.of(), Set.of(), Set.of(), Set.of(),
                List.of("unrelated canary protected prompt"), protectedSource);
        DatasetPolicy datasetPolicy = DatasetPolicy.defaultOffline();
        DatasetAssembler assembler = new DatasetAssembler(datasetPolicy);
        DatasetAssembler.DatasetBuild build = assembler.assemble(results.stream()
                .map(CurationResult::example).toList(), List.of(connected, canary),
                List.of(approval));
        check(build.state() == DatasetContracts.DatasetState.APPROVED
                && build.blockers().isEmpty(), "D5 base fixture build approved");
        check(build.rows().stream().noneMatch(row -> row.split() == DatasetSplit.CONNECTED
                || row.split() == DatasetSplit.CANARY), "protected runtime sets never trainer rows");
        check(build.rows().stream().anyMatch(row -> row.split() == DatasetSplit.TRAIN)
                && build.rows().stream().anyMatch(row -> row.split() == DatasetSplit.DEV)
                && build.rows().stream().anyMatch(row -> row.split() == DatasetSplit.TEST),
                "family-level allocation preserves approximate TRAIN/DEV/TEST coverage");
        check(build.rows().stream().filter(row -> row.example().semanticMetadata().profileId()
                .equals("profile-holdout")).allMatch(row -> row.split() == DatasetSplit.TEST),
                "authored profile holdout wholly outside TRAIN");
        Map<String, Set<DatasetSplit>> familySplits = new java.util.HashMap<>();
        build.rows().forEach(row -> familySplits.computeIfAbsent(
                row.semanticFamilyId().value(), ignored -> new HashSet<>()).add(row.split()));
        check(familySplits.values().stream().allMatch(splits -> splits.size() == 1),
                "no semantic family crosses splits");

        DistillationExample exactDuplicate = copyExample(results.get(0).example(),
                results.get(0).example().chosenResponse(), "exact-duplicate", "turn-exact");
        var exactBuild = assembler.assemble(concat(results, exactDuplicate),
                List.of(connected, canary), List.of(approval));
        check(exactBuild.dedupDecisions().stream().anyMatch(value ->
                value.reasonCode().equals("EXACT_DUPLICATE_MERGED")),
                "exact duplicate merged with provenance");
        check(exactBuild.rows().size() == build.rows().size(),
                "exact dedup does not increase row count");

        DistillationExample fuzzyDuplicate = copyExample(results.get(0).example(),
                "My name is Mara!", "fuzzy-duplicate", "turn-fuzzy");
        var fuzzyBuild = assembler.assemble(concat(results, fuzzyDuplicate),
                List.of(connected, canary), List.of(approval));
        check(fuzzyBuild.dedupDecisions().stream().anyMatch(value ->
                value.reasonCode().equals("FUZZY_DUPLICATE_MERGED")),
                "punctuation-only fuzzy duplicate merged deterministically");

        CanonicalDatasetRow train = build.rows().stream()
                .filter(row -> row.split() == DatasetSplit.TRAIN).findFirst().orElseThrow();
        var contaminatedSet = ContaminationChecker.manifest("challenge-overlap",
                DatasetSplit.CHALLENGE, Set.of(train.inputSha256()),
                Set.of(train.normalizedInputFingerprint()),
                Set.of(train.entityNormalizedInputFingerprint()),
                Set.of(train.semanticFamilyId().value()), Set.of(),
                List.of(DatasetNormalization.exactInput(train.example())), protectedSource);
        var audit = new ContaminationChecker().audit(build.rows(),
                List.of(contaminatedSet), datasetPolicy);
        Set<ContaminationKind> kinds = audit.issues().stream().map(value -> value.kind())
                .collect(java.util.stream.Collectors.toSet());
        check(kinds.containsAll(Set.of(ContaminationKind.EXACT, ContaminationKind.NORMALIZED,
                ContaminationKind.ENTITY_NORMALIZED, ContaminationKind.SEMANTIC_FAMILY,
                ContaminationKind.FUZZY)), "all protected overlap modes detected locally");
        check(!audit.clean(), "contamination blocks freeze");
        CanonicalDatasetRow ancestryRow = new CanonicalDatasetRow(train.schemaVersion(),
                train.rowId(), train.exampleId(), train.semanticFamilyId(), train.split(),
                train.example(), train.provenance(), train.inputSha256(),
                train.semanticTargetSha256(), train.responseSha256(), train.rowSha256(),
                train.normalizedInputFingerprint(), train.entityNormalizedInputFingerprint(),
                "generation-root-protected", train.contamination());
        var ancestrySet = ContaminationChecker.manifest("ancestry-overlap",
                DatasetSplit.CHALLENGE, Set.of(), Set.of(), Set.of(), Set.of(),
                Set.of("generation-root-protected"), List.of(), protectedSource);
        check(new ContaminationChecker().audit(List.of(ancestryRow), List.of(ancestrySet),
                datasetPolicy).issues().stream().anyMatch(issue ->
                        issue.kind() == ContaminationKind.GENERATION_ANCESTRY),
                "generation ancestry contamination detected");

        DistillationExample incompatibleNearDuplicate = copyExample(results.get(0).example(),
                "My name is Jonalith.", "ambiguous-near", "turn-ambiguous");
        var ambiguousBuild = assembler.assemble(concat(results, incompatibleNearDuplicate),
                List.of(connected, canary), List.of(approval));
        check(ambiguousBuild.blockers().stream().anyMatch(value ->
                value.startsWith("AMBIGUOUS_FUZZY_COLLISION")),
                "ambiguous fuzzy collision routes to review");

        check(build.coverage().rowCounts().keySet().containsAll(Set.of("taskType",
                "answerability", "targetSource", "evidenceSourceClass", "temporalCategory",
                "actionOutcome", "memoryType", "relationshipStance", "uncertaintyRefusal",
                "archetype", "paraphraseTemplate", "teacherSource", "failureSignature",
                "split")) && build.coverage().tokenWeightedCounts().keySet()
                        .equals(build.coverage().rowCounts().keySet()),
                "coverage spans all required dimensions");
        check(build.coverage().approximateTokens() > build.coverage().totalRows(),
                "coverage includes token-weighted counts");

        Path temp = Files.createTempDirectory("orbis-block2-freeze-");
        Path save = temp.resolve("save"); Files.createDirectories(save);
        ArtifactRoot root = new ArtifactRoot(temp.resolve("offline"), save);
        root.initialize();
        TrainingArtifactRegistries registries = new TrainingArtifactRegistries(root);
        registries.initialize();
        DatasetFreezer freezer = new DatasetFreezer(root, registries);
        LicenseManifest license = LicenseManifests.projectFixtureOnly();
        String sourceHash = catalog.sourceRegistryHash(results);
        var firstFreeze = freezer.freeze(build, license, curationPolicy, sourceHash,
                "NO_GIT_REPOSITORY");
        Path canonicalPath = firstFreeze.directory().resolve("canonical/examples.jsonl");
        String canonicalBefore = Files.readString(canonicalPath);
        for (String line : Files.readAllLines(canonicalPath)) {
            if (line.isBlank()) continue;
            CanonicalDatasetRow frozenRow = JsonFiles.GSON.fromJson(line,
                    CanonicalDatasetRow.class);
            check(DatasetFreezer.hasValidCanonicalRowHash(frozenRow),
                    "frozen canonical row has a complete self-verifiable hash");
        }
        var secondFreeze = freezer.freeze(build, license, curationPolicy, sourceHash,
                "NO_GIT_REPOSITORY");
        check(firstFreeze.created() && secondFreeze.idempotent()
                && firstFreeze.manifest().datasetId().equals(secondFreeze.manifest().datasetId()),
                "identical freeze is idempotent");
        check(canonicalBefore.equals(Files.readString(canonicalPath)),
                "idempotent freeze preserves canonical JSONL byte-for-byte");
        check(Files.isRegularFile(canonicalPath)
                && Files.isRegularFile(firstFreeze.directory().resolve("manifest.json")),
                "immutable canonical layout committed");
        for (String split : List.of("train", "dev", "test", "challenge")) {
            String trainer = Files.readString(firstFreeze.directory().resolve(split + "/sft.jsonl"));
            check(!trainer.contains("exampleId") && !trainer.contains("oracleVerdicts")
                    && !trainer.contains("semanticFamily"),
                    "trainer view contains no curation metadata: " + split);
        }
        check(Files.readAllLines(registries.datasets().path()).size() == 1,
                "dataset registry append is single and immutable");
        String trainView = Files.readString(firstFreeze.directory().resolve("train/sft.jsonl"));
        check(build.rows().stream().filter(row -> row.split() == DatasetSplit.DEV)
                .noneMatch(row -> trainView.contains(row.example().chosenResponse())),
                "DEV protected content absent from TRAIN view");
        check(build.rows().stream().filter(row -> row.split() == DatasetSplit.TEST)
                .noneMatch(row -> trainView.contains(row.example().chosenResponse())),
                "TEST protected content absent from TRAIN view");
        check(build.rows().stream().filter(row -> row.split() == DatasetSplit.CHALLENGE)
                .noneMatch(row -> trainView.contains(row.example().chosenResponse())),
                "CHALLENGE protected content absent from TRAIN view");

        List<DistillationExample> reduced = results.stream().map(CurationResult::example)
                .filter(example -> !example.exampleId().equals(results.getFirst()
                        .example().exampleId())).toList();
        var changedBuild = assembler.assemble(reduced, List.of(connected, canary),
                List.of(approval));
        var changedFreeze = freezer.freeze(changedBuild, license, curationPolicy,
                CanonicalJson.sha256("changed-source"), "NO_GIT_REPOSITORY");
        check(!changedFreeze.manifest().datasetId().equals(firstFreeze.manifest().datasetId()),
                "changed content gets a new dataset ID");
        LicenseManifest blockedLicense = new LicenseManifest(1, "blocked-license",
                Set.of(), Set.of("training"), "not approved", false, "b".repeat(64));
        assertThrows(() -> freezer.freeze(build, blockedLicense, curationPolicy, sourceHash,
                "NO_GIT_REPOSITORY"));
        var unreviewed = copyState(results.getFirst().example(), ReviewState.UNREVIEWED);
        var blockedBuild = assembler.assemble(concatExamples(results.stream().skip(1)
                .map(CurationResult::example).toList(),
                unreviewed), List.of(connected, canary), List.of(approval));
        check(blockedBuild.state() == DatasetContracts.DatasetState.REVIEW_REQUIRED,
                "unreviewed rows block freeze");
        assertThrows(blockedBuild::requireApproved);
        var rejectedExample = copyState(results.getFirst().example(), ReviewState.REJECTED);
        var rejectedBuild = assembler.assemble(concatExamples(results.stream().skip(1)
                .map(CurationResult::example).toList(), rejectedExample),
                List.of(connected, canary), List.of(approval));
        check(rejectedBuild.blockers().stream().anyMatch(value ->
                value.startsWith("REJECTED_ROW")), "rejected rows block freeze");

        check(!TrainingMode.OFF.permitsModelMutation(), "TrainingMode remains OFF");
        check(Files.isRegularFile(Path.of("training/schemas/dataset-manifest.schema.json")),
                "D5 manifest schema present");
        assertProductionRuntimeUnwired();
        System.out.println("D5_MATRIX_PASS cases=30");
        System.out.println("R126 Orbis Distillation Block 2 D4-D5 tests passed: "
                + assertions + " assertions.");
    }

    private static void assertReject(DeterministicCurationEngine engine,
            CurationRequest request, String reason) {
        assertReject(engine, request, reason, request);
    }
    private static void assertReject(DeterministicCurationEngine engine,
            CurationRequest ignored, String reason, CurationRequest actual) {
        CurationResult result = engine.curate(actual);
        check(!result.accepted() && result.reasonCodes().contains(reason),
                "negative rejected: " + reason + " actual=" + result.reasonCodes());
    }

    private static CurationRequest withResponse(CurationRequest source, String response) {
        return copy(source, source.target(), response, source.sourceKind(), source.targetSource(),
                source.actionResult(), source.expectedProviderInputSha256(), source.containsRawAudio());
    }
    private static CurationRequest withAction(CurationRequest source, NpcActionResult action) {
        return copy(source, source.target(), source.chosenResponse(), source.sourceKind(),
                source.targetSource(), action, source.expectedProviderInputSha256(),
                source.containsRawAudio());
    }
    private static CurationRequest withSource(CurationRequest source, SourceKind kind) {
        return copy(source, source.target(), source.chosenResponse(), kind, source.targetSource(),
                source.actionResult(), source.expectedProviderInputSha256(), source.containsRawAudio());
    }
    private static CurationRequest withRawAudio(CurationRequest source) {
        return copy(source, source.target(), source.chosenResponse(), source.sourceKind(),
                source.targetSource(), source.actionResult(), source.expectedProviderInputSha256(), true);
    }
    private static CurationRequest withExpectedHash(CurationRequest source, String hash) {
        return copy(source, source.target(), source.chosenResponse(), source.sourceKind(),
                source.targetSource(), source.actionResult(), hash, source.containsRawAudio());
    }
    private static CurationRequest asTeacher(CurationRequest source) {
        return copy(source, source.target(), source.chosenResponse(), SourceKind.APPROVED_TEACHER,
                TargetSource.APPROVED_TEACHER_TARGET_AFTER_ORACLES, source.actionResult(),
                source.expectedProviderInputSha256(), source.containsRawAudio());
    }
    private static CurationRequest withTeacher(CurationRequest source,
            TeacherContracts.TeacherIdentity identity, TeacherSourcePolicy policy) {
        return new CurationRequest(source.candidate(), source.target(), source.chosenResponse(),
                source.publicCritique(), source.taskType(), SourceKind.APPROVED_TEACHER,
                TargetSource.APPROVED_TEACHER_TARGET_AFTER_ORACLES, source.semanticMetadata(),
                source.humanReviewed(), source.actionResult(), source.liveEpistemicContract(),
                identity, policy, source.expectedProviderInputSha256(),
                source.expectedPromptTemplateId(), source.expectedModelContentId(),
                source.containsRawAudio(), source.deterministicStylePass(),
                source.teacherRubricMarkers(), source.negativeEvidence());
    }
    private static CurationRequest withTarget(CurationRequest source,
            CurationContracts.EpistemicTargetSnapshot target, String response) {
        return copy(source, target, response, source.sourceKind(), source.targetSource(),
                source.actionResult(), source.expectedProviderInputSha256(), source.containsRawAudio());
    }
    private static CurationRequest addForbidden(CurationRequest source, String pattern) {
        var forbidden = new ArrayList<>(source.target().forbiddenPropositions());
        forbidden.add(new CurationContracts.ForbiddenProposition("test-forbidden", pattern,
                CurationContracts.ClaimType.OBJECTIVE));
        var target = CurationContracts.EpistemicTargetSnapshot.create(
                source.target().answerability(), source.target().requiredPropositions(), forbidden,
                source.target().requiredClarificationSlots(),
                source.target().requiredAttributionSource(), source.target().actionTruth(),
                source.target().authoritativeActionScope(), source.target().outputContract());
        // A changed target without a matching source snapshot is intentionally also a source defect;
        // the forbidden oracle must still independently fire.
        return withTarget(source, target, source.chosenResponse()
                .replace(".", ", and the king owns it."));
    }
    private static CurationRequest copy(CurationRequest source,
            CurationContracts.EpistemicTargetSnapshot target, String response,
            SourceKind sourceKind, TargetSource targetSource, NpcActionResult action,
            String expectedHash, boolean rawAudio) {
        return new CurationRequest(source.candidate(), target, response, source.publicCritique(),
                source.taskType(), sourceKind, targetSource, source.semanticMetadata(),
                source.humanReviewed(), action, source.liveEpistemicContract(),
                source.teacherIdentity(), source.teacherPolicySnapshot(), expectedHash,
                source.expectedPromptTemplateId(), source.expectedModelContentId(), rawAudio,
                source.deterministicStylePass(), source.teacherRubricMarkers(),
                source.negativeEvidence());
    }

    private static DistillationExample copyExample(DistillationExample source, String response,
            String idSeed, String turnId) {
        var provenance = new DistillationCorpusCandidate.SourceProvenance(
                source.sourceProvenance().evaluationRunId(),
                source.sourceProvenance().scenarioId(), turnId,
                source.sourceProvenance().supportingSequences(),
                source.sourceProvenance().artifactHashes());
        var old = source.artifactHashes();
        var hashes = new CurationContracts.ArtifactHashes(old.productionInputSha256(),
                old.epistemicTargetSha256(), CanonicalJson.sha256(response),
                old.oraclePolicySha256(), old.sourceArtifactHashes());
        return new DistillationExample(source.schemaVersion(), ArtifactIds.example(idSeed),
                source.taskType(), source.targetSource(), provenance, source.productionInput(),
                source.epistemicTarget(), response, source.publicCritique(),
                source.requiredPropositionIds(), source.forbiddenPropositionIds(),
                source.oracleVerdicts(), source.teacherIdentity(), source.reviewState(),
                source.semanticMetadata(), "", "",
                CurationContracts.ContaminationMetadata.pending(), hashes,
                source.negativeEvidence(), source.createdAt());
    }
    private static DistillationExample copyState(DistillationExample source, ReviewState state) {
        return new DistillationExample(source.schemaVersion(), source.exampleId(), source.taskType(),
                source.targetSource(), source.sourceProvenance(), source.productionInput(),
                source.epistemicTarget(), source.chosenResponse(), source.publicCritique(),
                source.requiredPropositionIds(), source.forbiddenPropositionIds(),
                source.oracleVerdicts(), source.teacherIdentity(), state,
                source.semanticMetadata(), source.semanticFamilyId(), source.split(),
                source.contamination(), source.artifactHashes(), source.negativeEvidence(),
                source.createdAt());
    }
    private static DistillationExample withLineage(DistillationExample source,
            String conversationId, String timelineId, String ancestorId) {
        var old = source.semanticMetadata();
        var metadata = new CurationContracts.SemanticMetadata(old.sourceScenarioId(),
                old.semanticMechanism(), conversationId, timelineId, ancestorId,
                old.parentFamilyId(), old.profileId(), old.archetype(),
                old.paraphraseTemplateId(), old.failureSignature(), old.entityValues(),
                old.requestedProtectedSplit());
        return copyMetadata(source, metadata, source.epistemicTarget(), "lineage-"
                + conversationId + timelineId + ancestorId);
    }
    private static DistillationExample replaceEntity(DistillationExample source,
            String oldEntity, String newEntity, String scenario, String mechanism) {
        var oldTarget = source.epistemicTarget();
        var required = oldTarget.requiredPropositions().stream().map(value ->
                new CurationContracts.Proposition(value.id(),
                        replace(value.subject(), oldEntity, newEntity), value.predicate(),
                        replace(value.value(), oldEntity, newEntity), value.temporalCategory(),
                        value.sourceKind(), value.requiredConcepts().stream()
                                .map(term -> replace(term, oldEntity, newEntity)).toList(),
                        value.supersededValues().stream()
                                .map(term -> replace(term, oldEntity, newEntity)).toList(),
                        value.claimType(), replace(value.sourceActor(), oldEntity, newEntity)))
                .toList();
        var target = CurationContracts.EpistemicTargetSnapshot.create(
                oldTarget.answerability(), required, oldTarget.forbiddenPropositions(),
                oldTarget.requiredClarificationSlots(), oldTarget.requiredAttributionSource(),
                oldTarget.actionTruth(), replace(oldTarget.authoritativeActionScope(),
                        oldEntity, newEntity), oldTarget.outputContract());
        var old = source.semanticMetadata();
        var metadata = new CurationContracts.SemanticMetadata(scenario, mechanism,
                old.conversationId(), old.timelineId(), old.generationAncestorId(),
                old.parentFamilyId(), newEntity, old.archetype(), old.paraphraseTemplateId(),
                old.failureSignature(), Set.of(newEntity), old.requestedProtectedSplit());
        return copyMetadata(source, metadata, target, "entity-" + newEntity);
    }
    private static DistillationExample copyMetadata(DistillationExample source,
            CurationContracts.SemanticMetadata metadata,
            CurationContracts.EpistemicTargetSnapshot target, String idSeed) {
        var old = source.artifactHashes();
        var hashes = new CurationContracts.ArtifactHashes(old.productionInputSha256(),
                target.canonicalSha256(), old.responseSha256(), old.oraclePolicySha256(),
                old.sourceArtifactHashes());
        return new DistillationExample(source.schemaVersion(), ArtifactIds.example(idSeed),
                source.taskType(), source.targetSource(), source.sourceProvenance(),
                source.productionInput(), target, source.chosenResponse(), source.publicCritique(),
                source.requiredPropositionIds(), source.forbiddenPropositionIds(),
                source.oracleVerdicts(), source.teacherIdentity(), source.reviewState(), metadata,
                "", "", CurationContracts.ContaminationMetadata.pending(), hashes,
                source.negativeEvidence(), source.createdAt());
    }
    private static String replace(String value, String oldValue, String newValue) {
        return value == null ? "" : value.replace(oldValue, newValue)
                .replace(oldValue.toLowerCase(java.util.Locale.ROOT),
                        newValue.toLowerCase(java.util.Locale.ROOT));
    }
    private static List<DistillationExample> concat(List<CurationResult> results,
            DistillationExample extra) {
        return concatExamples(results.stream().map(CurationResult::example).toList(), extra);
    }
    private static List<DistillationExample> concatExamples(List<DistillationExample> examples,
            DistillationExample extra) {
        List<DistillationExample> output = new ArrayList<>(examples); output.add(extra); return output;
    }

    private static void check(boolean condition, String message) {
        assertions++; if (!condition) throw new AssertionError(message);
    }
    private static void assertThrows(Throwing action) {
        boolean threw = false; try { action.run(); } catch (Exception expected) { threw = true; }
        check(threw, "expected fail-closed exception");
    }
    private static void assertProductionRuntimeUnwired() throws Exception {
        try (var paths = Files.walk(Path.of("src/main/java"))) {
            for (Path path : paths.filter(Files::isRegularFile).toList()) {
                if (path.toString().contains("persistentnpcs" + java.io.File.separator
                        + "training")) continue;
                check(!Files.readString(path).contains("persistentnpcs.training"),
                        "production runtime imports offline training package: " + path);
            }
        }
    }
    @FunctionalInterface private interface Throwing { void run() throws Exception; }
}
