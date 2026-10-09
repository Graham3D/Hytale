package com.inigmasgames.hytalerpg.execution.hytale;

import static org.junit.jupiter.api.Assertions.*;
import com.hypixel.hytale.protocol.MountedUpdate;
import com.hypixel.hytale.protocol.NewSpawnUpdate;
import com.hypixel.hytale.protocol.TransformUpdate;
import com.hypixel.hytale.math.shape.Box;
import org.joml.Vector3d;
import org.junit.jupiter.api.Test;

class PromotedNameGlyphsTest {
    @Test void rarityAssetsAreExactAndNormalHasNoColoredName() {
        assertEquals("Champion", PromotedNameGlyphs.rarityAsset("Champion"));
        assertEquals("Unique", PromotedNameGlyphs.rarityAsset("Unique"));
        assertEquals("SuperUnique", PromotedNameGlyphs.rarityAsset("Super Unique"));
        assertNull(PromotedNameGlyphs.rarityAsset("Normal"));
        assertEquals("HywindName_Unique_U0041", PromotedNameGlyphs.assetId("Unique", 'A'));
    }

    @Test void twoViewersGetIndependentUprightFrontFacingPositions() {
        var center = new Vector3d(4.5, 12.25, -7.5);
        for (float viewerYaw : new float[]{0f, (float) (Math.PI / 2), (float) Math.PI,
                (float) (-Math.PI / 2)}) {
            float facing = PromotedNameGlyphs.frontFacingYaw(viewerYaw);
            double frontX = -Math.sin(facing), frontZ = -Math.cos(facing);
            assertEquals(1, frontX * Math.sin(viewerYaw) + frontZ * Math.cos(viewerYaw), 1e-6);
            var first = PromotedNameBillboards.glyphPosition(center, facing, -0.4, 0);
            var last = PromotedNameBillboards.glyphPosition(center, facing, 0.4, 0);
            assertEquals(center.y, first.y);
            assertEquals(center.y, last.y);
            double viewerRightX = Math.cos(viewerYaw), viewerRightZ = -Math.sin(viewerYaw);
            assertTrue((last.x - first.x) * viewerRightX + (last.z - first.z) * viewerRightZ > 0);
        }
    }

    @Test void distanceCompensationIsBoundedAndHysteretic() {
        assertEquals(0, PromotedNameBillboards.distanceBucket(11, -1));
        assertEquals(0, PromotedNameBillboards.distanceBucket(12.2, 0));
        assertEquals(1, PromotedNameBillboards.distanceBucket(13, 0));
        assertEquals(1, PromotedNameBillboards.distanceBucket(23.7, 1));
        assertEquals(2, PromotedNameBillboards.distanceBucket(25, 1));
        assertEquals(1.5f, PromotedNameBillboards.bucketScale(2));
        assertEquals(2, PromotedNameBillboards.distanceBucket(500, -1));
    }

    @Test void licensedProportionalWidthsAreNotMonospaced() {
        assertTrue(PromotedNameWidths.ADVANCES['W' - 33] > PromotedNameWidths.ADVANCES['i' - 33]);
        assertTrue(PromotedNameWidths.SPACE > 0);
    }

    @Test void spawnMountsOnceAndYawRefreshKeepsMountsStable() {
        var name = new PromotedNameGlyphs.Name("AB", "Unique", java.util.List.of(
                new PromotedNameGlyphs.Glyph(0, -0.25, new com.hypixel.hytale.protocol.Model()),
                new PromotedNameGlyphs.Glyph(1, 0.25, new com.hypixel.hytale.protocol.Model())));
        var owner = new Vector3d(4, 8, 9);
        var rows = MonsterPresentationLayout.resolve(owner, new Box(-0.5, 0, -0.5, 0.5, 2, 0.5));
        float yaw = PromotedNameGlyphs.frontFacingYaw(0);
        var spawn = PromotedNameBillboards.spawnPacket(7, 100, new int[]{101, 102}, owner,
                rows, name, yaw, 0, (float)(rows.nameY() - owner.y));
        assertEquals(3, spawn.updates.length);
        assertInstanceOf(NewSpawnUpdate.class, spawn.updates[0].updates[0]);
        assertEquals(7, java.util.Arrays.stream(spawn.updates[0].updates)
                .filter(MountedUpdate.class::isInstance).map(MountedUpdate.class::cast)
                .findFirst().orElseThrow().mountedToEntity);
        for(int i=1;i<3;i++) assertEquals(100, java.util.Arrays.stream(spawn.updates[i].updates)
                .filter(MountedUpdate.class::isInstance).map(MountedUpdate.class::cast)
                .findFirst().orElseThrow().mountedToEntity);
        var refresh = PromotedNameBillboards.refreshPacket(7, 100, new int[]{101, 102}, owner,
                rows, name, PromotedNameGlyphs.frontFacingYaw((float)Math.PI / 2), 0,
                (float)(rows.nameY() - owner.y), false, false);
        assertEquals(3, refresh.updates.length);
        for(var entity:refresh.updates) {
            assertEquals(1, entity.updates.length);
            var transform = assertInstanceOf(TransformUpdate.class, entity.updates[0]).transform;
            assertEquals(0, transform.bodyOrientation.pitch);
            assertEquals(0, transform.bodyOrientation.roll);
        }
    }
}
