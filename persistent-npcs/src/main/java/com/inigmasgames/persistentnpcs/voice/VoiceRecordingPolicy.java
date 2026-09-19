package com.inigmasgames.persistentnpcs.voice;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Pure, deterministic capture/quality policy shared by runtime and A6 fault gates. */
public final class VoiceRecordingPolicy {
    private VoiceRecordingPolicy() { }

    public static final double MAX_CLIPPING_RATIO = 0.02;
    public static final double MAX_SILENCE_RATIO = 0.85;

    public static boolean validOpusFrame(byte[] frame) {
        return frame != null && frame.length > 0 && frame.length <= 512;
    }

    public static SequenceStats sequences(List<Integer> sequenceNumbers) {
        Set<Integer> seen = new HashSet<>();
        Integer prior = null;
        int gaps = 0;
        int duplicates = 0;
        int outOfOrder = 0;
        for (int raw : sequenceNumbers == null ? List.<Integer>of() : sequenceNumbers) {
            int sequence = raw & 0xffff;
            if (!seen.add(sequence)) duplicates++;
            if (prior != null) {
                int delta = (sequence - prior) & 0xffff;
                if (delta == 0) { /* duplicate counted above */ }
                else if (delta > 32768) outOfOrder++;
                else if (delta > 1) gaps += delta - 1;
            }
            prior = sequence;
        }
        return new SequenceStats(gaps, duplicates, outOfOrder);
    }

    public static String qualityIssue(VoiceDraftAudio audio, SequenceStats sequences,
            int maximumSequenceGaps) {
        if (audio == null || audio.durationMillis() <= 5_000) {
            return "Record a longer sample with natural speech.";
        }
        if (audio.silenceRatio() > MAX_SILENCE_RATIO) {
            return "Most of the recording is silent.";
        }
        if (audio.clippingRatio() > MAX_CLIPPING_RATIO) {
            return "The recording is too loud and may sound distorted.";
        }
        SequenceStats safe = sequences == null ? new SequenceStats(0, 0, 0) : sequences;
        if (safe.gaps() > maximumSequenceGaps || safe.outOfOrder() > 0) {
            return "The recording dropped audio frames.";
        }
        return null;
    }

    public record SequenceStats(int gaps, int duplicates, int outOfOrder) { }
}
