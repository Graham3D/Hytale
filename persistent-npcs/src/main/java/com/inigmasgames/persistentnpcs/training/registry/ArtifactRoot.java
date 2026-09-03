package com.inigmasgames.persistentnpcs.training.registry;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/** Owns the offline root and rejects save-tree placement and traversal. */
public final class ArtifactRoot {
    public static final List<String> DIRECTORIES = List.of("registry", "candidates",
            "teacher-runs", "datasets", "runs", "models", "reports", "quarantine");
    private final Path root;

    public ArtifactRoot(Path root, Path activeSaveRoot) {
        this.root = root.toAbsolutePath().normalize();
        Path save = activeSaveRoot == null ? null : activeSaveRoot.toAbsolutePath().normalize();
        if (this.root.getNameCount() < 2 || (save != null
                && (this.root.startsWith(save) || save.startsWith(this.root)))) {
            throw new IllegalArgumentException("offline artifact root must be outside active save");
        }
    }

    public Path initialize() {
        try {
            Files.createDirectories(root);
            for (String directory : DIRECTORIES) Files.createDirectories(root.resolve(directory));
            return root;
        } catch (IOException exception) {
            throw new UncheckedIOException("could not initialize offline root " + root, exception);
        }
    }

    public Path resolve(String first, String... more) {
        Path candidate = root.resolve(Path.of(first, more)).normalize();
        if (!candidate.startsWith(root)) throw new IllegalArgumentException("artifact path escapes root");
        return candidate;
    }

    public Path path() { return root; }
}
