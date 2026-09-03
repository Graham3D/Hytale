package com.inigmasgames.persistentnpcs.training;

/** Offline-only modes available in Distillation Block 1. */
public enum TrainingMode {
    OFF,
    CORPUS_AUDIT;

    public void requireCorpusAudit() {
        if (this != CORPUS_AUDIT) {
            throw new IllegalStateException("Training subsystem is inert unless mode=CORPUS_AUDIT");
        }
    }

    /** D0-D3 must never enable training, packaging, promotion, or production inference. */
    public boolean permitsModelMutation() {
        return false;
    }
}
