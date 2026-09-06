package com.inigmasgames.hytalerpg.execution.math;

/** Small dependency-free vector used by deterministic executor tests. */
public record Vec3(double x, double y, double z) {
    public static final Vec3 ZERO = new Vec3(0, 0, 0);
    public static final Vec3 FORWARD = new Vec3(0, 0, 1);
    public Vec3 {
        if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z))
            throw new IllegalArgumentException("Vector coordinates must be finite");
    }
    public Vec3 add(Vec3 other) { return new Vec3(x + other.x, y + other.y, z + other.z); }
    public Vec3 subtract(Vec3 other) { return new Vec3(x - other.x, y - other.y, z - other.z); }
    public Vec3 multiply(double value) { return new Vec3(x * value, y * value, z * value); }
    public double horizontalLengthSquared() { return x * x + z * z; }
    public double horizontalLength() { return Math.sqrt(horizontalLengthSquared()); }
    public double distanceSquared(Vec3 other) {
        double dx = x - other.x, dy = y - other.y, dz = z - other.z;
        return dx * dx + dy * dy + dz * dz;
    }
    public Vec3 horizontalNormalized() {
        double length = horizontalLength();
        return length < 1.0e-9 ? new Vec3(0, 0, 1) : new Vec3(x / length, 0, z / length);
    }
}
