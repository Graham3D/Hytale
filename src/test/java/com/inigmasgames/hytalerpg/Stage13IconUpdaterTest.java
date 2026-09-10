package com.inigmasgames.hytalerpg;

import com.google.gson.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.zip.*;
import static org.junit.jupiter.api.Assertions.*;

/** Runs the actual owner-facing Windows PowerShell updater against isolated JAR fixtures. */
class Stage13IconUpdaterTest {
    @TempDir Path temp;
    private final Path resources = Path.of("src/main/resources");
    private Path jar, art, backups;
    private void setup() throws Exception {
        jar = temp.resolve("RPG.jar"); art = temp.resolve("art"); backups = temp.resolve("backups");
        Files.createDirectories(art.resolve("Skills")); Files.createDirectories(art.resolve("Passives"));
        var files = new ArrayList<Path>(List.of(resources.resolve("rpg/presentation/icon-index.json"),
                resources.resolve("rpg/catalog/skills.json"), resources.resolve("rpg/catalog/passives.json")));
        try (var stream = Files.list(resources.resolve("Server/Item/Items/RPG/Abilities"))) { files.addAll(stream.filter(p -> p.toString().endsWith(".json")).toList()); }
        try (var zip = new ZipOutputStream(Files.newOutputStream(jar))) {
            zip.putNextEntry(new ZipEntry("manifest.json")); zip.write("{\"Group\":\"InigmasGames\",\"Name\":\"HytaleRPGPhase00Audit\"}".getBytes(StandardCharsets.UTF_8)); zip.closeEntry();
            zip.putNextEntry(new ZipEntry("sentinel/")); zip.closeEntry();
            zip.putNextEntry(new ZipEntry("sentinel/gameplay.class")); zip.write(new byte[]{1,2,3,4,5}); zip.closeEntry();
            for (Path file : files) {
                zip.putNextEntry(new ZipEntry(resources.relativize(file).toString().replace('\\','/')));
                zip.write(Files.readAllBytes(file)); zip.closeEntry();
            }
        }
    }
    private byte[] artwork() throws Exception { return Files.readAllBytes(resources.resolve("Common/UI/Custom/Icons/RPG/SkillFirebolt.png")); }
    private Result run(String... extra) throws Exception {
        var args = new ArrayList<>(List.of("powershell.exe", "-NoProfile", "-ExecutionPolicy", "Bypass", "-File",
                Path.of("tools/Update-RpgIcons.ps1").toAbsolutePath().toString(), "-JarPath", jar.toAbsolutePath().toString(),
                "-ArtRoot", art.toAbsolutePath().toString(), "-BackupRoot", backups.toAbsolutePath().toString()));
        args.addAll(List.of(extra));
        if (args.remove("-TestWorkspaceDefaults")) {
            int position = args.indexOf("-ArtRoot"); args.remove(position); args.remove(position);
            position = args.indexOf("-BackupRoot"); args.remove(position); args.remove(position);
        }
        Path output = temp.resolve("output-" + UUID.randomUUID() + ".txt");
        var process = new ProcessBuilder(args).directory(temp.toFile()).redirectErrorStream(true).redirectOutput(output.toFile()).start();
        boolean done = process.waitFor(30, TimeUnit.SECONDS);
        if (!done) process.destroyForcibly();
        assertTrue(done, "Owner updater exceeded bounded test timeout");
        return new Result(process.exitValue(), Files.readString(output));
    }
    private record Result(int exit, String output) {}
    private Map<String, byte[]> contents() throws Exception {
        var map = new HashMap<String, byte[]>();
        try (var zip = new ZipFile(jar.toFile())) { for (var entry : Collections.list(zip.entries())) try (var stream=zip.getInputStream(entry)) { map.put(entry.getName(), stream.readAllBytes()); } }
        return map;
    }
    @Test void installsSkillAndPassivePngsOnlyAndChangesOnlyNativeIconField() throws Exception {
        setup(); var before = contents();
        Files.write(art.resolve("Skills/SkillWhirlwind.png"), artwork());
        Files.write(art.resolve("Passives/PassivePotency.png"), artwork());
        var result = run(); assertEquals(0, result.exit, result.output); assertTrue(result.output.contains("SUCCESS"));
        var after = contents();
        String itemPath = "Server/Item/Items/RPG/Abilities/RPG_Ability_Whirlwind.json";
        for (var entry : before.entrySet()) if (!entry.getKey().equals(itemPath)) assertArrayEquals(entry.getValue(), after.get(entry.getKey()), entry.getKey());
        var previousItem = JsonParser.parseString(new String(before.get(itemPath), StandardCharsets.UTF_8)).getAsJsonObject();
        var nextItem = JsonParser.parseString(new String(after.get(itemPath), StandardCharsets.UTF_8)).getAsJsonObject();
        assertEquals("Icons/Items/RPG/SkillWhirlwind.png", nextItem.remove("Icon").getAsString()); previousItem.remove("Icon"); assertEquals(previousItem, nextItem);
        assertArrayEquals(artwork(), after.get("Common/Icons/Items/RPG/SkillWhirlwind.png"));
        assertArrayEquals(artwork(), after.get("Common/UI/Custom/Icons/RPG/SkillWhirlwind.png"));
        assertArrayEquals(artwork(), after.get("Common/UI/Custom/Icons/RPG/PassivePotency.png"));
        assertEquals(before.size() + 3, after.size());
        // Production resolver discovers future artwork in the updated JAR, without new Java code.
        try (var loader = new java.net.URLClassLoader(new java.net.URL[]{jar.toUri().toURL()}, null)) {
            var load = com.inigmasgames.hytalerpg.ui.skilltree.RpgSkillIcons.class.getDeclaredMethod("load", ClassLoader.class);
            load.setAccessible(true);
            var paths = (Map<?, ?>) load.invoke(null, loader);
            assertEquals("Icons/RPG/SkillWhirlwind.png", paths.get("Skill:whirlwind"));
            assertEquals("Icons/RPG/PassivePotency.png", paths.get("Passive:potency"));
            assertEquals(com.inigmasgames.hytalerpg.ui.skilltree.RpgSkillTreeProjectionService.PLACEHOLDER_ICON,
                    paths.get("Skill:frost_bolt"));
        }
    }
    @Test void repeatIsByteIdenticalAndUndoRestoresExactPriorJar() throws Exception {
        setup(); byte[] original = Files.readAllBytes(jar);
        Files.write(art.resolve("Skills/SkillWhirlwind.png"), artwork());
        var first = run(); assertEquals(0, first.exit, first.output); byte[] updated = Files.readAllBytes(jar);
        var repeat = run(); assertEquals(0, repeat.exit, repeat.output); assertTrue(repeat.output.contains("Already up to date"));
        assertArrayEquals(updated, Files.readAllBytes(jar));
        var undo = run("-RestoreLast"); assertEquals(0, undo.exit, undo.output); assertArrayEquals(original, Files.readAllBytes(jar));
    }
    @Test void typoAbortsEntireBatchBeforeValidImageIsInstalled() throws Exception {
        setup(); byte[] before=Files.readAllBytes(jar);
        Files.write(art.resolve("Skills/SkillWhirlwind.png"), artwork());
        Files.write(art.resolve("Skills/SkillWhirwind.png"), artwork());
        var result=run(); assertNotEquals(0,result.exit); assertTrue(result.output.contains("Unknown Skill PNG"), result.output);
        assertArrayEquals(before,Files.readAllBytes(jar)); assertFalse(Files.exists(backups));
    }
    @Test void malformedPngAbortsWithoutChangingJar() throws Exception {
        setup(); byte[] before=Files.readAllBytes(jar);
        Files.writeString(art.resolve("Passives/PassivePotency.png"), "Not a PNG, even though the extension says png.");
        var result=run(); assertNotEquals(0,result.exit); assertTrue(result.output.contains("Not a PNG"), result.output);
        assertArrayEquals(before,Files.readAllBytes(jar));
    }
    @Test void oversizedHeaderIsRejectedBeforeImageDecodeAndNoJarMutation() throws Exception {
        setup(); byte[] before=Files.readAllBytes(jar), png=artwork();
        png[16]=127; png[20]=127;
        Files.write(art.resolve("Skills/SkillWhirlwind.png"), png);
        var result=run(); assertNotEquals(0,result.exit); assertTrue(result.output.contains("square PNG"), result.output);
        assertArrayEquals(before,Files.readAllBytes(jar));
    }
    @Test void dryRunWritesNeitherJarBackupNorLockAndMissingImagesKeepFallbacks() throws Exception {
        setup(); byte[] before=Files.readAllBytes(jar);
        Files.write(art.resolve("Passives/PassivePotency.png"), artwork());
        var result=run("-CheckOnly"); assertEquals(0,result.exit,result.output); assertTrue(result.output.contains("1 supplied icons"));
        assertArrayEquals(before,Files.readAllBytes(jar)); assertFalse(Files.exists(backups)); assertFalse(Files.exists(Path.of(jar+".icons.lock")));
    }
    @Test void undoRefusesChangedBuildAndPreservesIt() throws Exception {
        setup(); Files.write(art.resolve("Passives/PassivePotency.png"), artwork());
        var update=run(); assertEquals(0,update.exit,update.output);
        Files.write(jar,new byte[]{7},StandardOpenOption.APPEND); byte[] changed=Files.readAllBytes(jar);
        var result=run("-RestoreLast"); assertNotEquals(0,result.exit); assertTrue(result.output.contains("Undo refused"), result.output);
        assertArrayEquals(changed,Files.readAllBytes(jar));
    }
    @Test void corruptBackupIsNeverUsedForUndo() throws Exception {
        setup(); Files.write(art.resolve("Passives/PassivePotency.png"), artwork());
        var update=run(); assertEquals(0,update.exit,update.output); byte[] installed=Files.readAllBytes(jar);
        Path backup; try(var files=Files.list(backups)){backup=files.filter(p->p.toString().endsWith(".jar")).findFirst().orElseThrow();}
        Files.write(backup,new byte[]{9},StandardOpenOption.APPEND);
        var result=run("-RestoreLast"); assertNotEquals(0,result.exit); assertTrue(result.output.contains("backup hash mismatch"),result.output);
        assertArrayEquals(installed,Files.readAllBytes(jar));
    }
    @Test void doubleClickWorkspaceDefaultsResolveInWindowsPowerShellWithoutCallerWorkingDirectory() throws Exception {
        setup(); byte[] original = Files.readAllBytes(jar);
        var result = run("-TestWorkspaceDefaults", "-CheckOnly");
        assertEquals(0, result.exit, result.output); assertTrue(result.output.contains("CHECK PASSED"));
        assertArrayEquals(original, Files.readAllBytes(jar));
    }
}
