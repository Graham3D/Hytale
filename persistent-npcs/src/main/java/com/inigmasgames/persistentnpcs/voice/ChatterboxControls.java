package com.inigmasgames.persistentnpcs.voice;

/** Provider controls accepted by the original expressive Chatterbox model. */
public record ChatterboxControls(
        double exaggeration,
        double cfgWeight,
        double temperature,
        long seed) {
}
