package com.inigmasgames.persistentnpcs.voice;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Stable, bounded VocalState-to-Chatterbox mapping with per-NPC turn smoothing. */
public final class ChatterboxPerformanceController {
    private static final double SMOOTHING = 0.35;
    private static final double MAX_TURN_DELTA = 0.10;
    private final ConcurrentHashMap<UUID, Double> previousExaggeration =
            new ConcurrentHashMap<>();

    public ChatterboxControls controls(
            UUID npcId, VoicePreset preset, VocalState requestedState) {
        if (npcId == null || preset == null) {
            throw new IllegalArgumentException("NPC id and voice preset are required");
        }
        VocalState state = requestedState == null ? preset.defaultVocalState() : requestedState;
        double target = targetExaggeration(state);
        double smoothed = previousExaggeration.compute(npcId, (ignored, previous) -> {
            if (previous == null) {
                return target;
            }
            double blended = previous + (target - previous) * SMOOTHING;
            return clamp(blended, previous - MAX_TURN_DELTA, previous + MAX_TURN_DELTA);
        });
        double cfg = switch (state.pace()) {
            case SLOW -> 0.57;
            case NORMAL -> 0.50;
            case FAST -> 0.42;
        };
        if (smoothed >= 0.58) {
            cfg -= 0.04;
        }
        double temperature = switch (state.emotion()) {
            case CALM, WHISPERING -> 0.70;
            case FRIENDLY, AFFECTIONATE, CURIOUS, SAD, TENDER, AMUSED -> 0.74;
            case EXCITED, UNEASY, ANGRY, AFRAID, SCARED -> 0.78;
        };
        long seed = Integer.toUnsignedLong((preset.id() + ":" + npcId).hashCode());
        return new ChatterboxControls(round(smoothed), round(clamp(cfg, 0.34, 0.60)),
                round(temperature), seed);
    }

    private static double targetExaggeration(VocalState state) {
        double emotion = switch (state.emotion()) {
            case WHISPERING -> 0.28;
            case CALM -> 0.34;
            case SAD -> 0.36;
            case TENDER, AFFECTIONATE -> 0.35;
            case AMUSED -> 0.41;
            case FRIENDLY -> 0.42;
            case CURIOUS -> 0.46;
            case UNEASY -> 0.51;
            case EXCITED -> 0.60;
            case AFRAID, SCARED -> 0.61;
            case ANGRY -> 0.64;
        };
        double intensity = switch (state.intensity()) {
            case LOW -> -0.03;
            case MEDIUM -> 0.01;
            case HIGH -> 0.05;
        };
        return clamp(emotion + intensity, 0.25, 0.70);
    }

    private static double clamp(double value, double minimum, double maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    private static double round(double value) {
        return Math.round(value * 1000.0) / 1000.0;
    }
}
