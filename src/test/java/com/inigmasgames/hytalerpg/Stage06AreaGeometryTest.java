package com.inigmasgames.hytalerpg;
import com.inigmasgames.hytalerpg.execution.area.AreaGeometry;
import com.inigmasgames.hytalerpg.execution.math.Vec3;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class Stage06AreaGeometryTest {
    private AreaGeometry.Bounds bounds(double x, double y, double z, double size) {
        return new AreaGeometry.Bounds(new Vec3(x, y, z), new Vec3(x+size, y+size, z+size));
    }
    @Test void cylinderUsesCollisionBoundsAndIndependentGroundHeight() {
        var shape = new AreaGeometry(AreaGeometry.Kind.DISC, Vec3.ZERO, Vec3.FORWARD, 5, 360, 0, 0, 3);
        assertTrue(shape.intersects(bounds(4.9, 2.9, -.1, .4)));
        assertFalse(shape.intersects(bounds(4.9, 3.1, -.1, .4)));
        assertFalse(shape.intersects(bounds(0, -2, 0, 1)));
        assertFalse(shape.intersects(bounds(5.1, 0, 0, .2)));
        assertTrue(shape.intersects(bounds(4.9, 0, -.1, .4)), "centre outside is not a rejection");
    }
    @Test void coneClipsBoundsBeforeTestingRangeAndUsesHalfAngle() {
        var shape = new AreaGeometry(AreaGeometry.Kind.SECTOR, Vec3.ZERO, Vec3.FORWARD, 8, 60, 0, 0, 2.5);
        assertTrue(shape.intersects(bounds(-.1, 0, 7.9, .4)));
        assertFalse(shape.intersects(bounds(5, 0, 3, .2)));
        assertFalse(shape.intersects(bounds(0, 0, -2, .2)));
        assertTrue(shape.intersects(bounds(-1, 0, -1, 2)));
        assertFalse(shape.intersects(bounds(0, 2.6, 1, .2)));
    }
    @Test void wallUsesFullWidthLengthAndOrientedBounds() {
        var shape = new AreaGeometry(AreaGeometry.Kind.RECTANGLE, Vec3.ZERO, Vec3.FORWARD, 0, 0, 10, 2, 3);
        assertTrue(shape.intersects(bounds(.9, 0, 4.9, .2)));
        assertFalse(shape.intersects(bounds(1.1, 0, 0, .2)));
        assertFalse(shape.intersects(bounds(0, 0, 5.1, .2)));
        assertThrows(IllegalArgumentException.class, () -> new AreaGeometry(AreaGeometry.Kind.DISC,
                Vec3.ZERO, Vec3.FORWARD, Double.NaN, 0, 0, 0, 3));
    }
}
