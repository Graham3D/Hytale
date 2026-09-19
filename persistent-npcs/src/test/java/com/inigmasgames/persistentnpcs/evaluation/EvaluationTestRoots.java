package com.inigmasgames.persistentnpcs.evaluation;

import com.inigmasgames.persistentnpcs.profile.ProfileRepository;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/** Stable read-source snapshot so a running Hytale server cannot invalidate isolation tests. */
final class EvaluationTestRoots {
    private EvaluationTestRoots() { }

    static Path profileSnapshot(String... names) throws java.io.IOException {
        Path live = Path.of(System.getenv("APPDATA"), "Hytale", "UserData", "Saves", "NPC",
                "mods", "ImmersiveNPCs");
        Path snapshot = Files.createTempDirectory("orbis-eval-production-snapshot-");
        for (String name : names) {
            String safe = ProfileRepository.sanitizeProfileName(name).toLowerCase(
                    java.util.Locale.ROOT);
            Path source = live.resolve("profiles").resolve(safe).resolve(safe + ".json");
            Path target = snapshot.resolve("profiles").resolve(safe).resolve(safe + ".json");
            Files.createDirectories(target.getParent());
            Files.copy(source, target, StandardCopyOption.COPY_ATTRIBUTES);
        }
        return snapshot;
    }
}
