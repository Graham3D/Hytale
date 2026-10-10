package com.inigmasgames.hytalerpg.execution.hytale;

import com.hypixel.hytale.math.shape.Box;
import com.inigmasgames.hytalerpg.execution.math.Vec3;
import java.util.function.Predicate;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class HytaleTeleportTargetTest {
    private static final Vec3 FEET = new Vec3(0, 0, 0);
    private static final Box PLAYER = new Box(-.3, 0, -.3, .3, 1.8, .3);

    @Test void ordinaryFlatGroundWithinTenBlocksSucceeds() {
        var result = HytaleTeleportTarget.select(new Terrain(), PLAYER, FEET, new Vec3(1, -.3, 0), 10);
        assertEquals(HytaleTeleportTarget.Failure.PASS, result.failure());
        assertNotNull(result.landing());
        assertTrue(result.landing().x() > 4 && result.landing().x() < 5);
        assertEquals(.01, result.landing().y(), 1e-9);
    }

    @Test void flatTerrainBeyondMaximumRangeIsRejected() {
        var result = HytaleTeleportTarget.select(new Terrain(), PLAYER, FEET, new Vec3(1, -.13, 0), 10);
        assertEquals(HytaleTeleportTarget.Failure.OUT_OF_RANGE_OR_ELEVATION, result.failure());
    }

    @Test void raisedSideFaceWithinFiveBlockElevationResolvesTop() {
        var terrain = new Terrain();
        terrain.wallX = 4;
        terrain.raisedY = 4;
        var result = HytaleTeleportTarget.select(terrain, PLAYER, FEET, new Vec3(1, 0, 0), 10);
        assertEquals(HytaleTeleportTarget.Failure.PASS, result.failure());
        assertEquals(4.5, result.landing().x(), 1e-9);
        assertEquals(4.01, result.landing().y(), 1e-9);
        assertEquals(-1, result.normal().x(), 1e-9);
    }

    @Test void raisedSideFaceAboveElevationLimitIsRejected() {
        var terrain = new Terrain();
        terrain.wallX = 4;
        terrain.raisedY = 6;
        assertEquals(HytaleTeleportTarget.Failure.OUT_OF_RANGE_OR_ELEVATION,
                HytaleTeleportTarget.select(terrain, PLAYER, FEET, new Vec3(1, 0, 0), 10).failure());
    }

    @Test void sideFaceCannotResolveAnUnloadedOrLowerTop() {
        var unloaded = new Terrain();
        unloaded.wallX = 4;
        unloaded.raisedY = 4;
        unloaded.loaded = point -> point.y() < 6;
        assertEquals(HytaleTeleportTarget.Failure.SIDE_GROUND_UNAVAILABLE,
                HytaleTeleportTarget.select(unloaded, PLAYER, FEET, new Vec3(1, 0, 0), 10).failure());
        var lower = new Terrain();
        lower.wallX = 4;
        assertEquals(HytaleTeleportTarget.Failure.SIDE_TOP_BELOW_CONTACT,
                HytaleTeleportTarget.select(lower, PLAYER, FEET, new Vec3(1, 0, 0), 10).failure());
    }

    @Test void noFirstHitAndHazardHaveDistinctFailures() {
        assertEquals(HytaleTeleportTarget.Failure.NO_TERRAIN_HIT,
                HytaleTeleportTarget.select(new Terrain(), PLAYER, FEET, new Vec3(1, 0, 0), 10).failure());
        var terrain = new Terrain();
        terrain.hazard = true;
        assertEquals(HytaleTeleportTarget.Failure.HAZARDOUS_SURFACE,
                HytaleTeleportTarget.select(terrain, PLAYER, FEET, new Vec3(1, -.3, 0), 10).failure());
        var ceiling = new Terrain();
        ceiling.underside = true;
        assertEquals(HytaleTeleportTarget.Failure.UNDERSIDE_HIT,
                HytaleTeleportTarget.select(ceiling, PLAYER, FEET, new Vec3(0, 1, 0), 10).failure());
    }

    @Test void unloadedOriginRayAndDestinationHaveDistinctFailures() {
        var origin = new Terrain();
        origin.loaded = point -> point.x() > 0;
        assertEquals(HytaleTeleportTarget.Failure.ORIGIN_UNLOADED,
                HytaleTeleportTarget.select(origin, PLAYER, FEET, new Vec3(1, -.3, 0), 10).failure());
        var path = new Terrain();
        path.loaded = point -> point.x() < 2;
        assertEquals(HytaleTeleportTarget.Failure.RAY_PATH_UNLOADED,
                HytaleTeleportTarget.select(path, PLAYER, FEET, new Vec3(1, -.3, 0), 10).failure());
        var destination = new Terrain();
        destination.loaded = point -> Math.abs(point.y() - .01) > 1e-6 || point.x() < 1;
        assertEquals(HytaleTeleportTarget.Failure.DESTINATION_UNLOADED,
                HytaleTeleportTarget.select(destination, PLAYER, FEET, new Vec3(1, -.3, 0), 10).failure());
    }

    @Test void missingSupportAndBodyClearanceAreReportedSeparately() {
        var support = new Terrain();
        support.supportMissing = true;
        assertEquals(HytaleTeleportTarget.Failure.GROUND_SUPPORT_MISSING,
                HytaleTeleportTarget.select(support, PLAYER, FEET, new Vec3(1, -.3, 0), 10).failure());
        var body = new Terrain();
        body.bodyClear = false;
        assertEquals(HytaleTeleportTarget.Failure.BODY_BLOCKED,
                HytaleTeleportTarget.select(body, PLAYER, FEET, new Vec3(1, -.3, 0), 10).failure());
    }

    @Test void unloadedCornerAndMissingBoundingBoxAreReportedSeparately() {
        var terrain = new Terrain();
        terrain.loaded = point -> point.z() <= .2;
        assertEquals(HytaleTeleportTarget.Failure.SUPPORT_UNLOADED,
                HytaleTeleportTarget.select(terrain, PLAYER, FEET, new Vec3(1, -.3, 0), 10).failure());
        assertEquals(HytaleTeleportTarget.Failure.BOUNDING_BOX_MISSING,
                HytaleTeleportTarget.select(new Terrain(), null, FEET, new Vec3(1, -.3, 0), 10).failure());
    }

    private static final class Terrain implements HytaleTeleportTarget.Geometry {
        double wallX = Double.POSITIVE_INFINITY;
        double raisedY;
        boolean hazard, bodyClear = true, supportMissing, underside;
        Predicate<Vec3> loaded = point -> true;
        @Override public boolean loaded(Vec3 point) { return loaded.test(point); }
        @Override public boolean bodyClear(Box bounds, Vec3 landing) { return bodyClear; }
        @Override public HytaleTeleportTarget.Hit firstHit(Vec3 origin, Vec3 displacement) {
            if (underside) return new HytaleTeleportTarget.Hit(.25, new Vec3(0, -1, 0), false);
            if (displacement.y() < 0 && supportMissing && origin.x() > 4.7) return null;
            if (displacement.x() > 0 && origin.x() < wallX && origin.x() + displacement.x() >= wallX) {
                double fraction = (wallX - origin.x()) / displacement.x();
                return new HytaleTeleportTarget.Hit(fraction, new Vec3(-1, 0, 0), hazard);
            }
            double ground = origin.x() >= wallX ? raisedY : 0;
            if (displacement.y() >= 0 || origin.y() <= ground || origin.y() + displacement.y() > ground) return null;
            double fraction = (ground - origin.y()) / displacement.y();
            return new HytaleTeleportTarget.Hit(fraction, new Vec3(0, 1, 0), hazard);
        }
    }
}
