package com.inigmasgames.hytalerpg.execution.area;

import com.inigmasgames.hytalerpg.execution.math.Vec3;
import java.util.ArrayList;
import java.util.List;

/** Bounded procedural footprint outline. Arc sagitta is <= 0.15 m before the 0.025 m visual lift. */
public final class AreaOutline {
    public record Segment(Vec3 from, Vec3 to) { }
    private AreaOutline() { }
    public static List<Segment> segments(AreaGeometry shape) {
        Vec3 center = shape.origin().add(new Vec3(0, .025, 0));
        Vec3 forward = shape.direction(), right = new Vec3(forward.z(), 0, -forward.x());
        List<Vec3> points = new ArrayList<>();
        if (shape.kind() == AreaGeometry.Kind.RECTANGLE) {
            for (int[] corner : new int[][]{{-1,-1},{-1,1},{1,1},{1,-1}})
                points.add(center.add(forward.multiply(corner[0] * shape.length() / 2))
                        .add(right.multiply(corner[1] * shape.width() / 2)));
        } else {
            double angle = shape.kind() == AreaGeometry.Kind.DISC ? Math.PI * 2 : Math.toRadians(shape.angleDegrees());
            int count = Math.max(3, (int) Math.ceil(angle / (2 * Math.acos(1 - Math.min(.15 / Math.max(.15, shape.radius()), 1)))));
            if (count > 64) throw new IllegalArgumentException("AREA_OUTLINE_BUDGET");
            if (shape.kind() == AreaGeometry.Kind.SECTOR) points.add(center);
            for (int i = 0; i <= count; i++) {
                double current = -angle / 2 + angle * i / count;
                points.add(center.add(forward.multiply(Math.cos(current) * shape.radius()))
                        .add(right.multiply(Math.sin(current) * shape.radius())));
            }
        }
        List<Segment> lines = new ArrayList<>();
        for (int i = 0; i < points.size(); i++) lines.add(new Segment(points.get(i), points.get((i + 1) % points.size())));
        if (shape.kind() == AreaGeometry.Kind.RECTANGLE) {
            Vec3 lift = new Vec3(0, shape.height(), 0);
            for (int i = 0; i < points.size(); i++) {
                lines.add(new Segment(points.get(i), points.get(i).add(lift)));
                lines.add(new Segment(points.get(i).add(lift), points.get((i+1)%points.size()).add(lift)));
            }
        }
        return List.copyOf(lines);
    }
}
