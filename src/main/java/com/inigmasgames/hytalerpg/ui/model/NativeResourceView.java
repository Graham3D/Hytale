package com.inigmasgames.hytalerpg.ui.model;

public record NativeResourceView(double current, double maximum) {
    public NativeResourceView {
        if (!Double.isFinite(current) || !Double.isFinite(maximum) || maximum < 0.0)
            throw new IllegalArgumentException("Resource values must be finite and maximum nonnegative");
    }
}
