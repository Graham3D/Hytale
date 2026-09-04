package com.inigmasgames.persistentnpcs.voice;

import java.util.ArrayList;
import java.util.List;

/** Converts only bounded amplitude-envelope values into symmetric UI bar heights. */
public final class VoiceWaveformPresentation {
    public static final int UI_BUCKETS = 32;
    private VoiceWaveformPresentation() { }

    public static List<Integer> heights(List<Double> source) {
        List<Integer> result = new ArrayList<>(UI_BUCKETS);
        for (int index = 0; index < UI_BUCKETS; index++) {
            double amplitude = sample(source, index);
            result.add(Math.max(2, Math.min(116, 2 + (int) Math.round(amplitude * 114.0))));
        }
        return List.copyOf(result);
    }

    private static double sample(List<Double> source, int targetIndex) {
        if (source == null || source.isEmpty()) return 0.0;
        int start = targetIndex * source.size() / UI_BUCKETS;
        int end = Math.max(start + 1, (targetIndex + 1) * source.size() / UI_BUCKETS);
        double maximum = 0.0;
        for (int index = start; index < Math.min(end, source.size()); index++) {
            Double value = source.get(index);
            if (value != null && Double.isFinite(value)) {
                maximum = Math.max(maximum, Math.max(0.0, Math.min(1.0, value)));
            }
        }
        return maximum;
    }
}
