package com.inigmasgames.persistentnpcs.training.curation;

import com.inigmasgames.persistentnpcs.training.corpus.DistillationCorpusCandidate;
import com.inigmasgames.persistentnpcs.training.corpus.ProductionInputSnapshot;
import com.inigmasgames.persistentnpcs.training.curation.CurationContracts.ArtifactHashes;
import com.inigmasgames.persistentnpcs.training.curation.CurationContracts.ContaminationMetadata;
import com.inigmasgames.persistentnpcs.training.curation.CurationContracts.EpistemicTargetSnapshot;
import com.inigmasgames.persistentnpcs.training.curation.CurationContracts.NegativeEvidence;
import com.inigmasgames.persistentnpcs.training.curation.CurationContracts.ReviewState;
import com.inigmasgames.persistentnpcs.training.curation.CurationContracts.SemanticMetadata;
import com.inigmasgames.persistentnpcs.training.curation.CurationContracts.TargetSource;
import com.inigmasgames.persistentnpcs.training.curation.CurationContracts.TaskType;
import com.inigmasgames.persistentnpcs.training.registry.ArtifactIds;
import com.inigmasgames.persistentnpcs.training.teacher.TeacherContracts;
import java.time.Instant;
import java.util.List;

/** Canonical D4 example. chosenResponse is the only positive target field. */
public record DistillationExample(int schemaVersion, ArtifactIds.ExampleId exampleId,
        TaskType taskType, TargetSource targetSource,
        DistillationCorpusCandidate.SourceProvenance sourceProvenance,
        ProductionInputSnapshot productionInput, EpistemicTargetSnapshot epistemicTarget,
        String chosenResponse, String publicCritique,
        List<String> requiredPropositionIds, List<String> forbiddenPropositionIds,
        List<OracleVerdict> oracleVerdicts,
        TeacherContracts.TeacherIdentity teacherIdentity,
        ReviewState reviewState, SemanticMetadata semanticMetadata,
        String semanticFamilyId, String split, ContaminationMetadata contamination,
        ArtifactHashes artifactHashes, List<NegativeEvidence> negativeEvidence,
        Instant createdAt) {
    public static final int SCHEMA_VERSION = 1;

    public DistillationExample {
        if (schemaVersion != SCHEMA_VERSION || exampleId == null || taskType == null
                || targetSource == null || sourceProvenance == null || productionInput == null
                || epistemicTarget == null || reviewState == null || semanticMetadata == null
                || contamination == null || artifactHashes == null || createdAt == null) {
            throw new IllegalArgumentException("complete distillation example required");
        }
        chosenResponse = chosenResponse == null ? "" : chosenResponse;
        publicCritique = publicCritique == null ? "" : publicCritique;
        requiredPropositionIds = List.copyOf(requiredPropositionIds == null ? List.of()
                : requiredPropositionIds);
        forbiddenPropositionIds = List.copyOf(forbiddenPropositionIds == null ? List.of()
                : forbiddenPropositionIds);
        oracleVerdicts = List.copyOf(oracleVerdicts == null ? List.of() : oracleVerdicts);
        semanticFamilyId = semanticFamilyId == null ? "" : semanticFamilyId;
        split = split == null ? "" : split;
        negativeEvidence = List.copyOf(negativeEvidence == null ? List.of()
                : negativeEvidence);
        if (reviewState != ReviewState.REJECTED && reviewState != ReviewState.NEEDS_REVIEW
                && chosenResponse.isBlank()) {
            throw new IllegalArgumentException("accepted example requires chosen response");
        }
    }
}
