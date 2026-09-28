package com.inigmasgames.persistentnpcs;

import com.inigmasgames.persistentnpcs.persistence.ImmersiveNpcDataMigration;
import java.nio.file.Files;
import java.nio.file.Path;
import java.lang.reflect.Modifier;
import java.util.List;

/** Targeted gate for the standalone R170 -> Hytale 0.7.0-pre.4 compatibility port. */
public final class R171PreReleasePortTest {
    private R171PreReleasePortTest() { }

    public static void main(String[] args) throws Exception {
        assert "R171-PRE4-COMPAT".equals(PersistentNpcsPlugin.REVISION);
        assert Modifier.isFinal(PersistentNpcsPlugin.class.getModifiers());
        assert Modifier.isPublic(PersistentNpcsPlugin.class.getConstructor(
                com.hypixel.hytale.server.core.plugin.JavaPluginInit.class).getModifiers());

        String manifest = Files.readString(Path.of("src/main/resources/manifest.json"));
        assert manifest.contains("\"Name\": \"ImmersiveNPCs\"");
        assert manifest.contains("\"Version\": \"0.6.4-R171-pre4-compat\"");
        assert manifest.contains("\"ServerVersion\": \"=0.7.0-pre.4\"");

        for (String source : List.of(
                "src/main/java/com/inigmasgames/persistentnpcs/action/HytaleNpcActionService.java",
                "src/main/java/com/inigmasgames/persistentnpcs/autonomy/HytaleAutonomousCognitionController.java",
                "src/main/java/com/inigmasgames/persistentnpcs/hytale/GroundPositionResolver.java",
                "src/main/java/com/inigmasgames/persistentnpcs/perception/NpcPerceptionService.java")) {
            assert !Files.readString(Path.of(source)).contains(".getBlockType(") : source;
        }

        Path save = Files.createTempDirectory("r171-pre4-port-");
        Path mods = save.resolve("mods");
        Path generated = mods.resolve("ImmersiveNPCs");
        Path legacy = mods.resolve(ImmersiveNpcDataMigration.LEGACY_TECHNICAL_NAME);
        Path legacyRecord = legacy.resolve("persistence/identity-sentinel.json");
        Files.createDirectories(legacyRecord.getParent());
        Files.writeString(legacyRecord, "{\"stableNpcId\":\"mara-r171-test\"}");

        Path resolved = ImmersiveNpcDataMigration.resolveAndMigrate(generated, ignored -> { });
        assert resolved.equals(generated.toAbsolutePath().normalize());
        Path migratedRecord = resolved.resolve("persistence/identity-sentinel.json");
        assert Files.readString(migratedRecord).equals(Files.readString(
                save.resolve(ImmersiveNpcDataMigration.LEGACY_BACKUP_DIRECTORY)
                        .toFile().listFiles()[0].toPath()
                        .resolve("persistence/identity-sentinel.json")));
        assert !Files.exists(legacy);

        System.out.println("R171 PASS: pre.4 metadata, block API compatibility, and reversible legacy-data migration.");
    }
}
