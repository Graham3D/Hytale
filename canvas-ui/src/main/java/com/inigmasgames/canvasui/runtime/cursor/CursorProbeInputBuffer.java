package com.inigmasgames.canvasui.runtime.cursor;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;

/** Bounded handoff from the packet callback to the owning world executor. */
final class CursorProbeInputBuffer {
    enum OfferResult { ACCEPTED, DROPPED_MOTION, REJECTED_TRANSITION }

    private final int capacity;
    private final ArrayDeque<CursorProbeSample> samples = new ArrayDeque<>();
    private long droppedMotion;

    CursorProbeInputBuffer(int capacity) {
        if (capacity < 2) throw new IllegalArgumentException("capacity must be at least two");
        this.capacity = capacity;
    }

    synchronized OfferResult offer(CursorProbeSample sample) {
        if (samples.size() >= capacity) {
            if (!sample.transition()) {
                droppedMotion++;
                return OfferResult.DROPPED_MOTION;
            }
            CursorProbeSample removable = null;
            for (CursorProbeSample candidate : samples) {
                if (!candidate.transition()) { removable = candidate; break; }
            }
            if (removable == null) return OfferResult.REJECTED_TRANSITION;
            samples.remove(removable);
            droppedMotion++;
        }
        samples.addLast(sample);
        return OfferResult.ACCEPTED;
    }

    synchronized List<CursorProbeSample> drain(int maximum) {
        int count = Math.min(Math.max(0, maximum), samples.size());
        List<CursorProbeSample> drained = new ArrayList<>(count);
        for (int i = 0; i < count; i++) drained.add(samples.removeFirst());
        return drained;
    }

    synchronized boolean isEmpty() { return samples.isEmpty(); }
    synchronized long droppedMotion() { return droppedMotion; }
}
