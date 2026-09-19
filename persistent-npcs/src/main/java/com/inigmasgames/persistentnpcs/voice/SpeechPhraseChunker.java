package com.inigmasgames.persistentnpcs.voice;

import com.inigmasgames.persistentnpcs.conversation.DialogueNaturalnessFilter;
import com.inigmasgames.persistentnpcs.conversation.CanonicalDialogueAssembler;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;

/** Converts streaming deltas into natural sentence-sized TTS work items. */
public final class SpeechPhraseChunker {
    private static final int MINIMUM_SENTENCE_LENGTH = 32;
    private static final int FIRST_PHRASE_MINIMUM_LENGTH = 12;
    private static final int FIRST_PHRASE_MAXIMUM_LENGTH = 72;
    private static final int MAXIMUM_PHRASE_LENGTH = 220;
    private final ChunkConsumer output;
    private final List<String> comparisonUtterances = new ArrayList<>();
    private final StringBuilder pending = new StringBuilder();
    private final StringBuilder received = new StringBuilder();
    private final boolean filterNaturalness;
    private boolean receivedDelta;
    private VocalState state;
    private int emittedChunks;

    public SpeechPhraseChunker(BiConsumer<String, VocalState> output) {
        this((index, phrase, state) -> output.accept(phrase, state), List.of(), true);
    }

    public SpeechPhraseChunker(ChunkConsumer output, List<String> recentNpcUtterances) {
        this(output, recentNpcUtterances, true);
    }

    private SpeechPhraseChunker(
            ChunkConsumer output, List<String> recentNpcUtterances,
            boolean filterNaturalness) {
        this.output = output;
        this.filterNaturalness = filterNaturalness;
        if (recentNpcUtterances != null) {
            comparisonUtterances.addAll(recentNpcUtterances.stream()
                    .filter(value -> value != null && !value.isBlank()).toList());
        }
    }

    /** Chunks already-authoritative dialogue without changing or suppressing wording. */
    public static SpeechPhraseChunker exact(ChunkConsumer output) {
        return new SpeechPhraseChunker(output, List.of(), false);
    }

    public synchronized void accept(String delta, VocalState vocalState) {
        if (delta == null || delta.isEmpty()) {
            return;
        }
        receivedDelta = true;
        state = vocalState;
        pending.append(delta);
        received.append(delta);
        drain(false);
    }

    public synchronized int complete(String finalDialogue, VocalState vocalState) {
        state = vocalState;
        String finalCanonical = CanonicalDialogueAssembler.assemble(finalDialogue);
        if (!receivedDelta) {
            pending.append(finalCanonical);
        } else {
            // Provider callbacks normally contain the complete final text. If a provider closes
            // after returning a final tail that was not delivered as a delta, append only that
            // missing canonical suffix so later lines cannot become display-only text.
            String streamedCanonical = CanonicalDialogueAssembler.assemble(received.toString());
            if (!finalCanonical.equals(streamedCanonical)
                    && finalCanonical.startsWith(streamedCanonical)) {
                String suffix = finalCanonical.substring(streamedCanonical.length());
                if (!suffix.isBlank()) {
                    pending.append(suffix);
                }
            }
        }
        drain(true);
        return emittedChunks;
    }

    private void drain(boolean finalChunk) {
        while (!pending.isEmpty()) {
            int maximumLength = emittedChunks == 0
                    ? FIRST_PHRASE_MAXIMUM_LENGTH : MAXIMUM_PHRASE_LENGTH;
            int boundary = sentenceBoundary(emittedChunks == 0
                    ? FIRST_PHRASE_MINIMUM_LENGTH : MINIMUM_SENTENCE_LENGTH);
            if ((boundary < 0 || boundary > maximumLength)
                    && pending.length() >= maximumLength) {
                boundary = clauseBoundary(maximumLength);
                if (boundary < 0) boundary = whitespaceBoundary(maximumLength);
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

    private int sentenceBoundary(int minimumLength) {
        for (int index = minimumLength - 1; index < pending.length(); index++) {
            char value = pending.charAt(index);
            if (value == '.' || value == '?' || value == '!') {
                return index + 1;
            }
        }
        return -1;
    }

    private int whitespaceBoundary(int maximumLength) {
        for (int index = Math.min(pending.length(), maximumLength) - 1;
                index > 0; index--) {
            if (Character.isWhitespace(pending.charAt(index))) {
                return index + 1;
            }
        }
        return Math.min(pending.length(), maximumLength);
    }

    private int clauseBoundary(int maximumLength) {
        int limit = Math.min(pending.length(), maximumLength);
        for (int index = limit - 1; index >= 12; index--) {
            char value = pending.charAt(index);
            if (value == ',' || value == ';' || value == ':' || value == '\u2014') {
                // Chunk reconstruction inserts one inter-chunk space. Do not split attached
                // punctuation such as "brought—did", because that would manufacture lexical
                // whitespace and diverge from the committed response.
                if (index + 1 >= pending.length()
                        || Character.isWhitespace(pending.charAt(index + 1))) {
                    return index + 1;
                }
            }
        }
        return -1;
    }

    private void emit(int length) {
        String phrase = CanonicalDialogueAssembler.assemble(pending.substring(0, length));
        pending.delete(0, length);
        if (!phrase.isBlank()) {
            String filtered = filterNaturalness
                    ? DialogueNaturalnessFilter.filterChunk(phrase, comparisonUtterances)
                    : phrase;
            if (filtered.isBlank()) return;
            int index = emittedChunks++;
            comparisonUtterances.add(filtered);
            output.accept(index, filtered,
                    state == null ? VocalState.infer(filtered) : state);
        }
    }

    @FunctionalInterface
    public interface ChunkConsumer {
        void accept(int chunkIndex, String phrase, VocalState state);
    }
}
