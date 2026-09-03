package com.inigmasgames.persistentnpcs.voice;

import com.inigmasgames.persistentnpcs.ai.AiProvider;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/** Async hardware-neutral STT boundary; payloads are immutable Opus frames, never ECS state. */
public interface SpeechToTextProvider extends AiProvider {
    CompletableFuture<SpeechTranscript> transcribe(UUID requestId, List<byte[]> opusFrames);

    default boolean streamingTranscriptionEnabled() { return false; }

    default CompletableFuture<Void> startStream(UUID sessionId) {
        return CompletableFuture.failedFuture(
                new UnsupportedOperationException("Streaming STT is unavailable"));
    }

    default CompletableFuture<String> appendStream(UUID sessionId, List<byte[]> opusFrames) {
        return CompletableFuture.failedFuture(
                new UnsupportedOperationException("Streaming STT is unavailable"));
    }

    default CompletableFuture<SpeechTranscript> finishStream(UUID sessionId) {
        return CompletableFuture.failedFuture(
                new UnsupportedOperationException("Streaming STT is unavailable"));
    }

    /** Legacy adapter point retained for integrations compiled against early milestones. */
    default TranscriptionSession begin(UUID playerId, Consumer<String> partialText) {
        throw new UnsupportedOperationException("Use the asynchronous streaming STT methods");
    }

    interface TranscriptionSession extends AutoCloseable {
        void acceptEncodedAudio(ByteBuffer audio);
        String finish();
    }
}
