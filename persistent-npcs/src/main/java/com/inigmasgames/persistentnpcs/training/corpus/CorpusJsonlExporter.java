package com.inigmasgames.persistentnpcs.training.corpus;

import com.inigmasgames.persistentnpcs.training.TrainingMode;
import com.inigmasgames.persistentnpcs.training.registry.AppendOnlyJsonlRegistry;
import com.inigmasgames.persistentnpcs.training.registry.ArtifactRoot;

/** Only Block-1 write path for corpus candidates; cannot produce trainer-ready rows. */
public final class CorpusJsonlExporter {
    private final TrainingMode mode;
    private final AppendOnlyJsonlRegistry registry;

    public CorpusJsonlExporter(TrainingMode mode, ArtifactRoot root) {
        this.mode = java.util.Objects.requireNonNull(mode, "mode");
        this.registry = new AppendOnlyJsonlRegistry(root.resolve("candidates",
                "distillation-candidates.jsonl"));
    }

    public boolean export(DistillationCorpusCandidate candidate) {
        mode.requireCorpusAudit();
        return registry.append(candidate.id().value(), candidate);
    }
}
