package com.inigmasgames.hytalerpg.execution.area;

import com.inigmasgames.hytalerpg.execution.math.Vec3;
import java.util.ArrayList;
import java.util.List;

/** Ground footprints intersect actual collision bounds, with an independent vertical interval. */
public record AreaGeometry(Kind kind, Vec3 origin, Vec3 direction, double radius, double angleDegrees,
                           double length, double width, double height) {
    public enum Kind { DISC, SECTOR, RECTANGLE }
    public AreaGeometry {
        if (kind == null || origin == null || direction == null || !finite(radius, angleDegrees, length, width, height)
                || radius < 0 || length < 0 || width < 0 || height <= 0
                || (kind == Kind.SECTOR && (angleDegrees <= 0 || angleDegrees > 180)))
            throw new IllegalArgumentException("Invalid area geometry");
        direction = direction.horizontalNormalized();
    }
    public record Bounds(Vec3 min, Vec3 max) {
        public Bounds {
            if (min.x() > max.x() || min.y() > max.y() || min.z() > max.z())
                throw new IllegalArgumentException("Inverted collision bounds");
        }
        public Vec3 centre() { return min.add(max).multiply(.5); }
    }
    public boolean intersects(Bounds bounds) {
        if (bounds.max.y() < origin.y() || bounds.min.y() > origin.y() + height) return false;
        if (kind == Kind.DISC) return horizontalDistance(bounds) <= radius + 1e-9;
        List<Point> polygon = new ArrayList<>(List.of(
                local(bounds.min.x(), bounds.min.z()), local(bounds.max.x(), bounds.min.z()),
                local(bounds.max.x(), bounds.max.z()), local(bounds.min.x(), bounds.max.z())));
        if (kind == Kind.RECTANGLE) {
            polygon = clip(polygon, 1, 0, length / 2);
            polygon = clip(polygon, -1, 0, length / 2);
            polygon = clip(polygon, 0, 1, width / 2);
            polygon = clip(polygon, 0, -1, width / 2);
            return !polygon.isEmpty();
        }
        double half = Math.toRadians(angleDegrees / 2), s = Math.sin(half), c = Math.cos(half);
        polygon = clip(polygon, -s, c, 0);
        polygon = clip(polygon, -s, -c, 0);
        if (polygon.isEmpty()) return false;
        // If the footprint origin lies inside the target rectangle, distance is zero.
        if (bounds.min.x() <= origin.x() && bounds.max.x() >= origin.x()
                && bounds.min.z() <= origin.z() && bounds.max.z() >= origin.z()) return true;
        for (int i = 0; i < polygon.size(); i++)
            if (segmentDistance(polygon.get(i), polygon.get((i + 1) % polygon.size())) <= radius + 1e-9) return true;
        return false;
    }
    public double horizontalDistance(Bounds bounds) {
        double x = Math.clamp(origin.x(), bounds.min.x(), bounds.max.x()) - origin.x();
        double z = Math.clamp(origin.z(), bounds.min.z(), bounds.max.z()) - origin.z();
        return Math.hypot(x, z);
    }
    public double broadphaseRadius() {
        return Math.hypot(kind == Kind.RECTANGLE ? Math.hypot(length, width) / 2 : radius, height);
    }
    public AreaGeometry at(Vec3 point, double newRadius) {
        return new AreaGeometry(kind, point, direction, newRadius, angleDegrees, length, width, height);
    }
    private Point local(double x, double z) {
        double dx = x - origin.x(), dz = z - origin.z();
        return new Point(dx * direction.x() + dz * direction.z(), dx * direction.z() - dz * direction.x());
    }
    private static List<Point> clip(List<Point> polygon, double nx, double nz, double maximum) {
        List<Point> out = new ArrayList<>();
        if (polygon.isEmpty()) return out;
        Point previous = polygon.getLast(); double before = previous.x * nx + previous.z * nz - maximum;
        for (Point current : polygon) {
            double after = current.x * nx + current.z * nz - maximum;
            if ((before <= 1e-9) != (after <= 1e-9)) {
                double t = before / (before - after);
                out.add(new Point(previous.x + (current.x - previous.x) * t, previous.z + (current.z - previous.z) * t));
            }
            if (after <= 1e-9) out.add(current);
            previous = current; before = after;
        }
        return out;
    }
    private static double segmentDistance(Point a, Point b) {
        double x = b.x - a.x, z = b.z - a.z, square = x*x + z*z;
        double t = square < 1e-15 ? 0 : Math.clamp(-(a.x*x + a.z*z) / square, 0, 1);
        return Math.hypot(a.x + x*t, a.z + z*t);
    }
    private static boolean finite(double... values) {
        for (double value : values) if (!Double.isFinite(value)) return false;
        return true;
    }
    private record Point(double x, double z) { }
}
