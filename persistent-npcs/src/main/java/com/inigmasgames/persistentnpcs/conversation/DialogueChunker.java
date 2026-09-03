package com.inigmasgames.persistentnpcs.conversation;

import java.util.function.Consumer;

/** Converts arbitrary token deltas into readable chat-sized dialogue chunks. */
public final class DialogueChunker {
    private static final int MINIMUM_SENTENCE_LENGTH = 16;
    private static final int MAXIMUM_CHUNK_LENGTH = 120;
    private static final int MAXIMUM_DIALOGUE_LENGTH = 1200;

    private final String speaker;
    private final Consumer<String> output;
    private final StringBuilder pending = new StringBuilder();
    private boolean receivedDelta;
    private int acceptedCharacters;

    public DialogueChunker(String speaker, Consumer<String> output) {
        this.speaker = speaker;
        this.output = output;
    }

    public synchronized void accept(String delta) {
        if (delta == null || delta.isEmpty()) {
            return;
        }
        int remaining = MAXIMUM_DIALOGUE_LENGTH - acceptedCharacters;
        if (remaining <= 0) {
            return;
        }
        receivedDelta = true;
        String accepted = delta.length() <= remaining
                ? delta : delta.substring(0, remaining);
        acceptedCharacters += accepted.length();
        pending.append(accepted);
        drain(false);
    }

    public synchronized void complete(String finalDialogue) {
        if (!receivedDelta && finalDialogue != null) {
            pending.append(finalDialogue);
        }
        drain(true);
    }

    private void drain(boolean finalChunk) {
        while (!pending.isEmpty()) {
            int boundary = sentenceBoundary();
            if (boundary < 0 && pending.length() >= MAXIMUM_CHUNK_LENGTH) {
                boundary = whitespaceBoundary();
            }
            if (boundary < 0) {
                if (finalChunk) {
                    emit(pending.length());
                }
                return;
            }
            emit(boundary);
        }
    }

    private int sentenceBoundary() {
        for (int index = MINIMUM_SENTENCE_LENGTH - 1; index < pending.length(); index++) {
            char value = pending.charAt(index);
            if (value == '.' || value == '?' || value == '!' || value == '\n') {
                return index + 1;
            }
        }
        return -1;
    }

    private int whitespaceBoundary() {
        int limit = Math.min(pending.length(), MAXIMUM_CHUNK_LENGTH);
        for (int index = limit - 1; index > 0; index--) {
            if (Character.isWhitespace(pending.charAt(index))) {
                return index + 1;
            }
        }
        return limit;
    }

    private void emit(int length) {
        String chunk = pending.substring(0, length).strip();
        pending.delete(0, length);
        if (!chunk.isEmpty()) {
            output.accept(speaker + ": " + chunk);
        }
    }
}
