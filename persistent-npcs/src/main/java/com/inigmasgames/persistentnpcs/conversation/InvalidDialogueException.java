package com.inigmasgames.persistentnpcs.conversation;

/** Raised when a model response cannot safely be presented or persisted as NPC dialogue. */
public final class InvalidDialogueException extends RuntimeException {
    public InvalidDialogueException(String message) {
        super(message);
    }
}
