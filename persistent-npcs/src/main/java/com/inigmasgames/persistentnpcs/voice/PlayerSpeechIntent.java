package com.inigmasgames.persistentnpcs.voice;

/** Deterministic speech classification used before any response wording is generated. */
public enum PlayerSpeechIntent {
    CONVERSATION,
    DIRECT_ADDRESS,
    LOCATE_SPEAKER,
    REQUEST_ANSWER,
    SEARCH_CALL
}
