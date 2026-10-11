package com.inigmasgames.hytalerpg.ui.model;

import java.util.UUID;

/** Read-only durable barrier snapshot; zero capacity means no active protection. */
public record BarrierView(long revision, UUID generation, double current, double maximum) {
    public static final BarrierView NONE = new BarrierView(0, null, 0, 0);
    public BarrierView {
        if (revision < 0 || !Double.isFinite(current) || !Double.isFinite(maximum)
                || current < 0 || maximum < 0 || current > maximum
                || (generation == null) != (maximum == 0))
            throw new IllegalArgumentException("Invalid barrier presentation snapshot");
    }
    public boolean active() { return generation != null && current > 0; }
}
