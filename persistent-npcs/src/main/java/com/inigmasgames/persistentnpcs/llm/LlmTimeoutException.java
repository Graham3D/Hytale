package com.inigmasgames.persistentnpcs.llm;

public final class LlmTimeoutException extends LlmProviderException {
    public enum Phase {
        RESPONSE_START,
        STREAM_IDLE,
        NON_STREAMING_COMPLETION
    }

    private final Phase phase;

    public LlmTimeoutException(Phase phase, String message, Throwable cause) {
        super(message, cause);
        this.phase = phase;
    }

    public Phase phase() {
        return phase;
    }
}
