package com.inigmasgames.canvasui.runtime.cursor;

import com.inigmasgames.canvasui.api.CanvasPoint;
import com.inigmasgames.canvasui.api.CanvasViewport;

import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * One authoritative pointer transform for the cursor-HUD backend.
 *
 * <p>Hytale 0.7.0-pre.2 exposes {@code MouseInteraction.screenPoint}, but its
 * server API exposes neither the client's physical viewport nor its logical UI
 * scale.  A per-session five-landmark measurement therefore resolves the raw
 * client domain directly into the HUD's known logical coordinate system.  This
 * is resolution/UI-scale independent and deliberately avoids a 1920x1080
 * assumption or a machine-specific constant.</p>
 */
public final class CanvasPointerTransform {
    public static final int CANVAS_LEFT = 60;
    public static final int CANVAS_TOP = 116;
    public static final int CANVAS_WIDTH = 830;
    public static final int CANVAS_HEIGHT = 470;
    public static final int CLOSE_LEFT = 850;
    public static final int CLOSE_TOP = 48;
    public static final int CLOSE_RIGHT = 934;
    public static final int CLOSE_BOTTOM = 90;
    public static final double MAX_CENTER_ERROR = 5.0;
    public static final double MAX_CORNER_ERROR = 8.0;

    public enum Landmark {
        TOP_LEFT(CANVAS_LEFT, CANVAS_TOP),
        TOP_RIGHT(CANVAS_LEFT + CANVAS_WIDTH, CANVAS_TOP),
        BOTTOM_LEFT(CANVAS_LEFT, CANVAS_TOP + CANVAS_HEIGHT),
        BOTTOM_RIGHT(CANVAS_LEFT + CANVAS_WIDTH, CANVAS_TOP + CANVAS_HEIGHT),
        CENTER(CANVAS_LEFT + CANVAS_WIDTH / 2.0, CANVAS_TOP + CANVAS_HEIGHT / 2.0);

        private final CanvasPoint expected;
        Landmark(double x, double y) { expected = CanvasPoint.of(x, y); }
        public CanvasPoint expected() { return expected; }
    }

    public enum State { CAPTURING, READY, REJECTED }

    public record Calibration(double rawLeft, double rawRight, double rawTop, double rawBottom,
                              double maximumCornerError, double centerError) {
        public Calibration {
            if (!finite(rawLeft, rawRight, rawTop, rawBottom, maximumCornerError)
                    || (!Double.isNaN(centerError) && !Double.isFinite(centerError)))
                throw new IllegalArgumentException("calibration values must be finite");
            if (Math.abs(rawRight - rawLeft) < 0.000001 || Math.abs(rawBottom - rawTop) < 0.000001)
                throw new IllegalArgumentException("calibration has a degenerate axis");
        }
    }

    public record Coordinates(CanvasPoint raw, CanvasPoint normalized, CanvasPoint viewport,
                              CanvasPoint local, CanvasPoint canvas) { }

    public record Capture(Landmark landmark, CanvasPoint raw, CanvasPoint expected,
                          CanvasPoint converted, double error, State state, String reason) { }

    private final Map<Landmark, CanvasPoint> samples = new EnumMap<>(Landmark.class);
    private Calibration calibration;
    private State state = State.CAPTURING;

    public CanvasPointerTransform() { }

    public CanvasPointerTransform(Calibration calibration) {
        this.calibration = calibration;
        this.state = State.READY;
    }

    public State state() { return state; }
    public boolean ready() { return state == State.READY && calibration != null; }
    public Optional<Calibration> calibration() { return Optional.ofNullable(calibration); }

    public Landmark nextLandmark() {
        for (Landmark landmark : Landmark.values()) if (!samples.containsKey(landmark)) return landmark;
        return null;
    }

    public Capture captureNext(double rawX, double rawY) {
        if (state != State.CAPTURING) throw new IllegalStateException("calibration is not capturing");
        if (!validRaw(rawX, rawY)) throw new IllegalArgumentException("raw pointer is invalid");
        Landmark landmark = nextLandmark();
        if (landmark == null) throw new IllegalStateException("all calibration landmarks are populated");
        CanvasPoint raw = CanvasPoint.of(rawX, rawY);
        samples.put(landmark, raw);

        if (landmark == Landmark.BOTTOM_RIGHT) {
            try {
                calibration = fitCorners(samples);
            } catch (IllegalArgumentException rejected) {
                state = State.REJECTED;
                return new Capture(landmark, raw, landmark.expected(), null, Double.POSITIVE_INFINITY,
                        state, rejected.getMessage());
            }
            double cornerError = maximumCornerError(calibration, samples);
            calibration = new Calibration(calibration.rawLeft(), calibration.rawRight(),
                    calibration.rawTop(), calibration.rawBottom(), cornerError, Double.NaN);
            if (cornerError > MAX_CORNER_ERROR) {
                state = State.REJECTED;
                return new Capture(landmark, raw, landmark.expected(), toViewport(raw), cornerError,
                        state, "corner error exceeds " + MAX_CORNER_ERROR + " logical pixels");
            }
            CanvasPoint converted = toViewport(raw);
            return new Capture(landmark, raw, landmark.expected(), converted,
                    distance(converted, landmark.expected()), state, "capture center to validate");
        }

        if (landmark == Landmark.CENTER) {
            CanvasPoint converted = toViewport(raw);
            double centerError = distance(converted, landmark.expected());
            calibration = new Calibration(calibration.rawLeft(), calibration.rawRight(),
                    calibration.rawTop(), calibration.rawBottom(), calibration.maximumCornerError(), centerError);
            state = centerError <= MAX_CENTER_ERROR ? State.READY : State.REJECTED;
            return new Capture(landmark, raw, landmark.expected(), converted, centerError, state,
                    state == State.READY ? "mapping accepted" : "center error exceeds " + MAX_CENTER_ERROR + " logical pixels");
        }

        return new Capture(landmark, raw, landmark.expected(), null, Double.NaN, state,
                "capture " + nextLandmark());
    }

    public Coordinates convert(double rawX, double rawY, CanvasViewport viewport) {
        return convert(rawX, rawY, viewport, CanvasPoint.of(CANVAS_LEFT, CANVAS_TOP));
    }

    public Coordinates convert(double rawX, double rawY, CanvasViewport viewport, CanvasPoint localOrigin) {
        if (!ready()) throw new IllegalStateException("pointer transform is not calibrated");
        CanvasPoint raw = CanvasPoint.of(rawX, rawY);
        CanvasPoint normalized = toNormalized(raw);
        CanvasPoint view = toViewport(raw);
        CanvasPoint local = view.subtract(localOrigin);
        return new Coordinates(raw, normalized, view, local, viewport.toCanvas(local));
    }

    public double viewportWidth() {
        Calibration value=requireFit();
        return 2.0 * CANVAS_WIDTH / Math.abs(value.rawRight()-value.rawLeft());
    }

    public double viewportHeight() {
        Calibration value=requireFit();
        return 2.0 * CANVAS_HEIGHT / Math.abs(value.rawBottom()-value.rawTop());
    }

    public CanvasPoint toViewport(double rawX, double rawY) {
        return toViewport(CanvasPoint.of(rawX, rawY));
    }

    public boolean closeHit(double rawX, double rawY) {
        if (!ready()) return false;
        CanvasPoint point = toViewport(rawX, rawY);
        return point.x() >= CLOSE_LEFT && point.x() <= CLOSE_RIGHT
                && point.y() >= CLOSE_TOP && point.y() <= CLOSE_BOTTOM;
    }

    public String diagnostic() {
        if (calibration == null) return "LANDMARK_CAPTURE_" + String.valueOf(nextLandmark());
        return String.format(Locale.ROOT,
                "%s rawX=%.5f..%.5f rawY=%.5f..%.5f cornerErr=%.2f centerErr=%s physicalViewport=UNAVAILABLE logicalScale=UNAVAILABLE",
                state, calibration.rawLeft(), calibration.rawRight(), calibration.rawTop(), calibration.rawBottom(),
                calibration.maximumCornerError(), Double.isFinite(calibration.centerError())
                        ? String.format(Locale.ROOT, "%.2f", calibration.centerError()) : "PENDING");
    }

    private CanvasPoint toNormalized(CanvasPoint raw) {
        Calibration value = requireFit();
        return CanvasPoint.of((raw.x() - value.rawLeft()) / (value.rawRight() - value.rawLeft()),
                (raw.y() - value.rawTop()) / (value.rawBottom() - value.rawTop()));
    }

    private CanvasPoint toViewport(CanvasPoint raw) {
        CanvasPoint normalized = toNormalized(raw);
        return CanvasPoint.of(CANVAS_LEFT + normalized.x() * CANVAS_WIDTH,
                CANVAS_TOP + normalized.y() * CANVAS_HEIGHT);
    }

    private Calibration requireFit() {
        if (calibration == null) throw new IllegalStateException("pointer transform has no fitted corners");
        return calibration;
    }

    private static Calibration fitCorners(Map<Landmark, CanvasPoint> values) {
        CanvasPoint tl = require(values, Landmark.TOP_LEFT);
        CanvasPoint tr = require(values, Landmark.TOP_RIGHT);
        CanvasPoint bl = require(values, Landmark.BOTTOM_LEFT);
        CanvasPoint br = require(values, Landmark.BOTTOM_RIGHT);
        double left = (tl.x() + bl.x()) / 2.0;
        double right = (tr.x() + br.x()) / 2.0;
        double top = (tl.y() + tr.y()) / 2.0;
        double bottom = (bl.y() + br.y()) / 2.0;
        if (!finite(left, right, top, bottom) || Math.abs(right - left) < 0.000001
                || Math.abs(bottom - top) < 0.000001)
            throw new IllegalArgumentException("captured landmarks form a degenerate mapping");
        return new Calibration(left, right, top, bottom, 0.0, Double.NaN);
    }

    private static double maximumCornerError(Calibration calibration, Map<Landmark, CanvasPoint> samples) {
        CanvasPointerTransform fitted = new CanvasPointerTransform(
                new Calibration(calibration.rawLeft(), calibration.rawRight(), calibration.rawTop(),
                        calibration.rawBottom(), 0.0, 0.0));
        double maximum = 0.0;
        for (Landmark landmark : new Landmark[]{Landmark.TOP_LEFT, Landmark.TOP_RIGHT,
                Landmark.BOTTOM_LEFT, Landmark.BOTTOM_RIGHT}) {
            maximum = Math.max(maximum, distance(fitted.toViewport(require(samples, landmark)), landmark.expected()));
        }
        return maximum;
    }

    private static CanvasPoint require(Map<Landmark, CanvasPoint> values, Landmark landmark) {
        CanvasPoint value = values.get(landmark);
        if (value == null) throw new IllegalArgumentException("missing landmark " + landmark);
        return value;
    }

    private static double distance(CanvasPoint a, CanvasPoint b) {
        return Math.hypot(a.x() - b.x(), a.y() - b.y());
    }

    private static boolean validRaw(double x, double y) {
        return finite(x, y) && Math.abs(x) <= 1_000_000.0 && Math.abs(y) <= 1_000_000.0;
    }

    private static boolean finite(double... values) {
        for (double value : values) if (!Double.isFinite(value)) return false;
        return true;
    }
}
