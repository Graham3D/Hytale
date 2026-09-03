package com.inigmasgames.persistentnpcs.training.candidate;

/** Mutually exclusive routing result; only MODEL_TRAINING_ELIGIBLE enters D4 curation. */
public enum TrainingEligibility {
    MODEL_TRAINING_ELIGIBLE,
    ORBIS_SOURCE_REPAIR_REQUIRED,
    ORACLE_OR_DATA_REPAIR_REQUIRED,
    CONNECTED_VALIDATION_REQUIRED,
    NOT_TRAINABLE,
    NEEDS_REVIEW
}
