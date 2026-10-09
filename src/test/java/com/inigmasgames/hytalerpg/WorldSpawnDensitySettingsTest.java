package com.inigmasgames.hytalerpg;

import com.inigmasgames.hytalerpg.spawning.WorldSpawnDensitySettings;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import static org.junit.jupiter.api.Assertions.*;

class WorldSpawnDensitySettingsTest {
    @TempDir Path temp;

    @Test void persistsChangesAndRestoresNativeBaselineWithoutCompounding() {
        var path = temp.resolve("world-spawn-density.json");
        var settings = new WorldSpawnDensitySettings(path);
        assertEquals(1.0, settings.multiplier());
        for (double value : new double[] {4.0, 2.0, 1.0, 1.5, 4.0}) {
            settings.set(value);
            assertEquals(value, new WorldSpawnDensitySettings(path).multiplier());
            assertEquals((int) Math.ceil(500 * value), WorldSpawnDensitySettings.scaledCap(500, value));
        }
        settings.set(1.0);
        assertEquals(500, WorldSpawnDensitySettings.scaledCap(500, settings.multiplier()));
        assertEquals(1.0, new WorldSpawnDensitySettings(path).multiplier());
    }

    @Test void manualEditOfLegacyEnvelopeIsAcceptedWithoutResettingSave() throws Exception {
        var path = temp.resolve("world-spawn-density.json");
        var settings = new WorldSpawnDensitySettings(path);
        settings.set(4.0);
        // R234 wrote an envelope whose checksum still describes 4.0 after an owner edit.
        var legacy = "{\"checksum\":\"ccc0d48898694aa24f055986462ec40f9ca2989f7f3098b7d798c05b536b64c0\"," +
                "\"data\":{\"schemaVersion\":1,\"worldSpawnDensityMultiplier\":8.0}}";
        Files.writeString(path, legacy);
        assertEquals(8.0, new WorldSpawnDensitySettings(path).multiplier());
        assertEquals(legacy, Files.readString(path));
        new WorldSpawnDensitySettings(path).set(6.0);
        assertEquals(6.0, new WorldSpawnDensitySettings(path).multiplier());
        assertFalse(Files.readString(path).contains("checksum"));
        Files.writeString(path, Files.readString(path).replace("6.0", "8.0"));
        assertEquals(8.0, new WorldSpawnDensitySettings(path).multiplier());
    }

    @Test void rejectsUnsafeValuesAndMalformedSettingsWithoutReplacingSave() throws Exception {
        var path = temp.resolve("world-spawn-density.json");
        var settings = new WorldSpawnDensitySettings(path);
        for (double value : new double[] {0, -2, 100, Double.NaN, Double.POSITIVE_INFINITY})
            assertThrows(IllegalArgumentException.class, () -> settings.set(value));
        assertFalse(Files.exists(path));
        for (var invalid : new String[] {
                "{\"schemaVersion\":2,\"worldSpawnDensityMultiplier\":8.0}",
                "{\"schemaVersion\":1,\"worldSpawnDensityMultiplier\":9.0}",
                "{\"schemaVersion\":1,\"worldSpawnDensityMultiplier\":",
                "{\"data\":null}"
        }) {
            Files.writeString(path, invalid);
            assertThrows(IllegalStateException.class, () -> new WorldSpawnDensitySettings(path));
            assertEquals(invalid, Files.readString(path));
        }
    }

    @Test void installedNativeEnvironmentTargetFollowsDensityWithoutSpeciesMutation() {
        var nativeEnvironment = new com.hypixel.hytale.server.spawning.world.WorldEnvironmentSpawnData(7, 1.0);
        nativeEnvironment.adjustSegmentCount(2048);
        assertEquals(2.0, nativeEnvironment.getExpectedNPCs());
        assertTrue(nativeEnvironment.getNpcStatMap().isEmpty());
        nativeEnvironment.setDensity(4.0, null); // No loaded chunk references in this native fixture.
        assertEquals(8.0, nativeEnvironment.getExpectedNPCs());
        nativeEnvironment.setDensity(2.0, null);
        assertEquals(4.0, nativeEnvironment.getExpectedNPCs());
        nativeEnvironment.setDensity(1.0, null);
        assertEquals(2.0, nativeEnvironment.getExpectedNPCs());
        assertTrue(nativeEnvironment.getNpcStatMap().isEmpty());
    }
}
