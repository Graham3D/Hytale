package com.inigmasgames.persistentnpcs.training.dataset;

import com.inigmasgames.persistentnpcs.training.curation.DistillationExample;
import com.inigmasgames.persistentnpcs.training.registry.ArtifactIds;
import com.inigmasgames.persistentnpcs.training.registry.CanonicalJson;
import java.util.LinkedHashMap;
import java.util.Map;

/** Groups semantic mechanisms and lineage before any split is assigned. */
public final class SemanticFamilyAssigner {
    public ArtifactIds.SemanticFamilyId assign(DistillationExample example) {
        var metadata = example.semanticMetadata();
        if (!metadata.parentFamilyId().isBlank()) {
            return parseOrHash("parent", metadata.parentFamilyId());
        }
        if (!metadata.generationAncestorId().isBlank()) {
            return ArtifactIds.semanticFamily(Map.of("generationAncestorId",
                    metadata.generationAncestorId()));
        }
        if (!metadata.conversationId().isBlank()) {
            return ArtifactIds.semanticFamily(Map.of("conversationId",
                    metadata.conversationId()));
        }
        if (!metadata.timelineId().isBlank()) {
            return ArtifactIds.semanticFamily(Map.of("timelineId", metadata.timelineId()));
        }
        Map<String, Object> seed = new LinkedHashMap<>();
        seed.put("scenario", metadata.sourceScenarioId());
        seed.put("mechanism", metadata.semanticMechanism());
        seed.put("taskType", example.taskType().name());
        seed.put("answerability", example.epistemicTarget().answerability().name());
        seed.put("actionTruth", example.epistemicTarget().actionTruth().name());
        seed.put("entityNormalizedTarget", familyTarget(example));
        seed.put("archetype", metadata.archetype());
        return ArtifactIds.semanticFamily(seed);
    }

    private static Object familyTarget(DistillationExample example) {
        var target = example.epistemicTarget();
        var entities = example.semanticMetadata().entityValues();
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("answerability", target.answerability().name());
        value.put("actionTruth", target.actionTruth().name());
        value.put("actionScope", DatasetNormalization.entityNormalizedText(
                target.authoritativeActionScope(), entities));
        value.put("outputKind", target.outputContract().kind().name());
        value.put("required", target.requiredPropositions().stream().map(proposition -> Map.of(
                "predicate", proposition.predicate(),
                "value", DatasetNormalization.entityNormalizedText(proposition.value(), entities),
                "temporal", proposition.temporalCategory().name(),
                "source", proposition.sourceKind().name(),
                "claimType", proposition.claimType().name(),
                "concepts", proposition.requiredConcepts().stream().map(concept ->
                        DatasetNormalization.entityNormalizedText(concept, entities)).toList()))
                .toList());
        value.put("forbidden", target.forbiddenPropositions().stream().map(forbidden -> Map.of(
                "pattern", DatasetNormalization.entityNormalizedText(forbidden.pattern(), entities),
                "claimType", forbidden.claimType().name())).toList());
        return value;
    }

    private static ArtifactIds.SemanticFamilyId parseOrHash(String kind, String value) {
        if (value.matches("sf_[0-9a-f]{64}")) return new ArtifactIds.SemanticFamilyId(value);
        return ArtifactIds.semanticFamily(Map.of(kind, value));
    }
}
