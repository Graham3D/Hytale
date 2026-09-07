package com.inigmasgames.hytalerpg.execution.area;

import com.inigmasgames.hytalerpg.execution.math.Vec3;
import java.util.ArrayList;
import java.util.List;
import java.util.SplittableRandom;

/** Seeded equal-area radial strata, shuffled to avoid a predictable clockwise sweep.
 * Insetting centers by the child radius keeps every warning/hit footprint inside its advertised zone. */
public final class StratifiedAreaPattern {
    private StratifiedAreaPattern() { }
    public static List<Vec3> offsets(String seed, int count, double radius, double childRadius) {
        if (seed == null || count < 1 || count > 48 || !Double.isFinite(radius) || !Double.isFinite(childRadius)
                || childRadius <= 0 || radius < childRadius) throw new IllegalArgumentException("Invalid impact pattern");
        long hash = 0xcbf29ce484222325L;
        for (int i = 0; i < seed.length(); i++) { hash ^= seed.charAt(i); hash *= 0x100000001b3L; }
        var random = new SplittableRandom(hash); var points = new ArrayList<Vec3>(count);
        double limit = radius - childRadius;
        double rotation = random.nextDouble() * Math.PI * 2;
        for (int i = 0; i < count; i++) {
            double distance = limit * Math.sqrt((i + random.nextDouble()) / count);
            double angle = rotation + i * Math.PI * (3 - Math.sqrt(5));
            points.add(new Vec3(Math.cos(angle) * distance, 0, Math.sin(angle) * distance));
        }
        for (int i = count - 1; i > 0; i--) java.util.Collections.swap(points, i, random.nextInt(i + 1));
        return List.copyOf(points);
    }
}
