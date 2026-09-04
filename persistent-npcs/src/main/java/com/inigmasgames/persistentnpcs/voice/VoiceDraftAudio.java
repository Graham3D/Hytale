package com.inigmasgames.persistentnpcs.voice;

import java.util.List;

/** Bounded, post-decode recorder result. Raw media never crosses the Custom UI boundary. */
public record VoiceDraftAudio(
        byte[] wav,
        long durationMillis,
        double peakDbfs,
        double rmsDbfs,
        double clippingRatio,
        double silenceRatio,
        List<Double> waveform,
        long decodeMillis) {
    public VoiceDraftAudio {
        wav = wav == null ? new byte[0] : wav.clone();
        waveform = waveform == null ? List.of() : List.copyOf(waveform);
    }
    @Override public byte[] wav() { return wav.clone(); }
}
