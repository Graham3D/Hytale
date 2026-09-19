package com.inigmasgames.taverns;

/** Per-player Relaxing progress and the paused/snapshotted Relaxed duration. */
final class RelaxedSession {
    static final float RELAXING_SECONDS = 10.0f;

    private float relaxingElapsed;
    private boolean seated;
    private boolean completedForCurrentSeat;
    private boolean relaxed;
    private boolean snapshotDurationOnLeave;
    private float pausedRemainingSeconds;
    private int currentRelaxedMinutes;

    RelaxingUpdate tickInside(float delta, boolean currentlySeated, int relaxedMinutes) {
        currentRelaxedMinutes = Math.max(0, relaxedMinutes);
        if (!currentlySeated) {
            seated = false;
            completedForCurrentSeat = false;
            relaxingElapsed = 0.0f;
            return RelaxingUpdate.HIDDEN;
        }
        if (!seated) {
            seated = true;
            completedForCurrentSeat = false;
            relaxingElapsed = 0.0f;
        }
        if (completedForCurrentSeat) {
            return RelaxingUpdate.HIDDEN;
        }

        relaxingElapsed = Math.min(
                RELAXING_SECONDS,
                relaxingElapsed + Math.max(0.0f, delta));
        if (relaxingElapsed >= RELAXING_SECONDS) {
            completedForCurrentSeat = true;
            relaxed = true;
            snapshotDurationOnLeave = true;
            pausedRemainingSeconds = 0.0f;
            return new RelaxingUpdate(false, 1.0f, true);
        }
        return new RelaxingUpdate(
                true,
                relaxingElapsed / RELAXING_SECONDS,
                false);
    }

    void recoverInfiniteEffect() {
        relaxed = true;
        snapshotDurationOnLeave = true;
    }

    void pauseEffect(float remainingSeconds) {
        relaxed = true;
        snapshotDurationOnLeave = false;
        pausedRemainingSeconds = Math.max(0.0f, remainingSeconds);
    }

    float durationForLeaving() {
        if (!relaxed) {
            return 0.0f;
        }
        if (snapshotDurationOnLeave) {
            pausedRemainingSeconds = currentRelaxedMinutes * 60.0f;
            snapshotDurationOnLeave = false;
        }
        return pausedRemainingSeconds;
    }

    boolean isRelaxed() {
        return relaxed;
    }

    void expire() {
        relaxed = false;
        snapshotDurationOnLeave = false;
        pausedRemainingSeconds = 0.0f;
    }

    record RelaxingUpdate(boolean visible, float progress, boolean completed) {
        private static final RelaxingUpdate HIDDEN =
                new RelaxingUpdate(false, 0.0f, false);
    }
}
