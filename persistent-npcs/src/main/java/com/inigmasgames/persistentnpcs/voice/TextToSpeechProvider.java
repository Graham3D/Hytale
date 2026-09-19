package com.inigmasgames.persistentnpcs.voice;

import com.inigmasgames.persistentnpcs.ai.AiProvider;
import java.nio.ByteBuffer;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/** Async hardware-neutral TTS boundary; the server remains authoritative for playback. */
public interface TextToSpeechProvider extends AiProvider {
    CompletableFuture<OpusClip> synthesize(UUID requestId, UUID responseId,
            VoiceRenderPlan plan, String text);

    default CompletableFuture<OpusClip> stream(UUID requestId, UUID responseId,
            VoiceRenderPlan plan, String text, Consumer<byte[]> opusChunk) {
        return synthesize(requestId, responseId, plan, text).thenApply(clip -> {
            clip.frames().forEach(frame -> opusChunk.accept(frame.clone()));
            return clip;
        });
    }

    /** Legacy adapter point retained for early integrations. */
    default void synthesize(UUID npcId, String text, Consumer<ByteBuffer> audioChunk) {
        throw new UnsupportedOperationException("A VoiceRenderPlan is required");
    }
}
