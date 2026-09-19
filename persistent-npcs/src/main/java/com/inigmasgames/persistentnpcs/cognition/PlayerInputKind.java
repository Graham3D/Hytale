package com.inigmasgames.persistentnpcs.cognition;

/** Deterministic dialogue-act classification used before belief persistence. */
public enum PlayerInputKind {
    DECLARATIVE_FACT,
    QUESTION,
    COMMAND,
    ACKNOWLEDGEMENT,
    CONFIRMATION,
    OTHER
}
