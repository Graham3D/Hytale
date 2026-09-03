package com.inigmasgames.persistentnpcs.training.teacher;

import com.inigmasgames.persistentnpcs.training.TrainingMode;
import com.inigmasgames.persistentnpcs.training.registry.AppendOnlyJsonlRegistry;
import com.inigmasgames.persistentnpcs.training.registry.ArtifactRoot;

/** Append-only persistence for proposed/unverified D3 teacher artifacts. */
public final class TeacherRunStore {
    private final TrainingMode mode;
    private final AppendOnlyJsonlRegistry registry;
    public TeacherRunStore(TrainingMode mode, ArtifactRoot root) {
        this.mode = java.util.Objects.requireNonNull(mode, "mode");
        this.registry = new AppendOnlyJsonlRegistry(root.resolve("teacher-runs",
                "teacher-runs.jsonl"));
    }
    public boolean persist(TeacherContracts.TeacherRunResult result) {
        mode.requireCorpusAudit();
        if (result.manifest().trust() != TeacherContracts.OutputTrust.PROPOSED_LABEL
                && result.manifest().trust() != TeacherContracts.OutputTrust.UNVERIFIED) {
            throw new IllegalStateException("D3 teacher output cannot be persisted as gold");
        }
        return registry.append(result.manifest().id().value(), result);
    }
}
