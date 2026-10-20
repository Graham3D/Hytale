package com.inigmasgames.hytalerpg.progress;

/** Compared native state, not a requested status or a duration refresh. */
public record ControlEvidence(boolean immobilized, double slow) {
    public ControlEvidence {
        if (!Double.isFinite(slow) || slow < 0 || slow > 1) throw new IllegalArgumentException("INVALID_CONTROL_EVIDENCE");
    }
    public boolean improvedFrom(ControlEvidence before) {
        java.util.Objects.requireNonNull(before);
        return !before.immobilized && (immobilized || slow > before.slow + 1e-9);
    }
    /** Native position must change away from the source after a recent safe retreat request. */
    public static boolean retreatObserved(com.inigmasgames.hytalerpg.execution.math.Vec3 before,
            com.inigmasgames.hytalerpg.execution.math.Vec3 after,
            com.inigmasgames.hytalerpg.execution.math.Vec3 source, double elapsed, boolean requested) {
        if (before == null || after == null || source == null || !Double.isFinite(elapsed)
                || elapsed <= 0 || elapsed > .25 || !requested) return false;
        var movement=after.subtract(before);var separation=before.subtract(source);
        if(separation.horizontalLength()<1e-9)return false;
        var away=separation.horizontalNormalized();
        double dot=movement.x()*away.x()+movement.z()*away.z();
        return dot > .01 && movement.lengthSquared() <= 4;
    }
}
