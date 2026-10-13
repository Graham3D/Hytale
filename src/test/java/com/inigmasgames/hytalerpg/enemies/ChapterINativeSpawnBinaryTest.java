package com.inigmasgames.hytalerpg.enemies;

import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Map;
import java.util.Objects;
import java.util.zip.ZipFile;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Pin the actual 0.7.0 native job, flock, and concrete-role seams used by natural promotion. */
class ChapterINativeSpawnBinaryTest {
    @Test void installedReleaseRetainsReviewedSynchronousSpawnAndFlockClasses() throws Exception {
        var release=Path.of(Objects.requireNonNull(System.getenv("APPDATA")),
                "Hytale/install/release/package/game/latest/Server/HytaleServer.jar");
        var expected=Map.of(
                "com/hypixel/hytale/server/spawning/world/system/WorldSpawnJobSystems$Ticking.class",
                "5472a837ee8f7bab4b6c3f18935486cda0c6227b1a4969f92a740d940ffff4f1",
                "com/hypixel/hytale/server/spawning/world/system/WorldSpawnJobSystems.class",
                "40fd8b61d389160f2a462afb46b19608e7a2def3af5e8c9034ee04e17ae1e515",
                "com/hypixel/hytale/server/flock/FlockPlugin.class",
                "5147c97dccac1da0d35b7fa12be831e4a603fc358c9dc46de151509a82c6f771",
                "com/hypixel/hytale/server/npc/NPCPlugin.class",
                "bbf81fc3cdc6e3b80e2c14d28693d76286e277c71337c3947422a0369fef5bbf",
                "com/hypixel/hytale/server/spawning/world/component/SpawnJobData.class",
                "4711814b8fa2d1c40ec9c423d34c857e8d3f7f24e92a06328170a8b74e47a77f",
                "com/hypixel/hytale/server/npc/entities/NPCEntity.class",
                "f82db11aa82a3d570e801279b6279b3dd93c00a4637baaa71ddd9b4ed120c1b9",
                "com/hypixel/hytale/server/npc/role/support/WorldSupport.class",
                "93225a015cd07bb7285bd95c4e1bfa7e7b06eca9c812d5c7ee51cc4bcd46b86e");
        try(var server=new ZipFile(release.toFile())) {
            for(var pin:expected.entrySet()) {
                var entry=server.getEntry(pin.getKey());
                assertNotNull(entry,pin.getKey());
                try(var bytes=server.getInputStream(entry)) {
                    var actual=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes.readAllBytes()));
                    assertEquals(pin.getValue(),actual,pin.getKey());
                }
            }
        }
    }
}
