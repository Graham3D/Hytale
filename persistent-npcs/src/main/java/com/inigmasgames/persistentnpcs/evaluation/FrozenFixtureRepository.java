package com.inigmasgames.persistentnpcs.evaluation;

import com.inigmasgames.persistentnpcs.json.JsonFiles;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;

/** Two-step candidate/promotion lifecycle; promotion is explicit and reviewed. */
public final class FrozenFixtureRepository {
    private final Path candidateRoot;
    private final Path repositoryRoot;

    public FrozenFixtureRepository(Path candidateRoot, Path repositoryRoot) {
        this.candidateRoot = candidateRoot.toAbsolutePath().normalize();
        this.repositoryRoot = repositoryRoot.toAbsolutePath().normalize();
    }

    public Path freezeCandidate(FrozenConversationFixture fixture) {
        if (!"CANDIDATE".equals(fixture.reviewStatus())) throw new IllegalArgumentException(
                "Freeze output must remain a candidate");
        Path path = safe(candidateRoot, fixture.fixtureId() + ".json");
        JsonFiles.writeAtomic(path, fixture); return path;
    }

    public Path promote(String fixtureId, boolean reviewed) {
        if (!reviewed) throw new IllegalStateException("Explicit review is required");
        Path candidate = safe(candidateRoot, fixtureId + ".json");
        FrozenConversationFixture source = JsonFiles.read(candidate,
                FrozenConversationFixture.class);
        FrozenConversationFixture promoted = new FrozenConversationFixture(
                source.schemaVersion(), source.fixtureId(), source.sourceFailureId(),
                source.sourceRunId(), source.frozenAt(), source.coverageTags(), source.input(),
                source.expectedBoundaries(), source.requiredPropositions(),
                source.forbiddenClaims(), source.requiredVariants(),
                source.productionGraphHash(), "PROMOTED_REVIEWED");
        Path target = safe(repositoryRoot, fixtureId + ".json");
        JsonFiles.writeAtomic(target, promoted);
        updateManifest(); return target;
    }

    private void updateManifest() {
        try {
            ArrayList<String> ids = new ArrayList<>();
            if (Files.isDirectory(repositoryRoot)) try (var paths = Files.list(repositoryRoot)) {
                paths.filter(path -> path.getFileName().toString().endsWith(".json"))
                        .filter(path -> !path.getFileName().toString().equals("manifest.json"))
                        .map(path -> path.getFileName().toString().replaceFirst("\\.json$", ""))
                        .sorted().forEach(ids::add);
            }
            JsonFiles.writeAtomic(repositoryRoot.resolve("manifest.json"),
                    new FixtureManifest(EvaluationContracts.SCHEMA_VERSION, List.copyOf(ids)));
        } catch (IOException failure) {
            throw new IllegalStateException("Could not update fixture manifest", failure);
        }
    }

    private static Path safe(Path root, String name) {
        if (!name.matches("[A-Za-z0-9_.-]{1,110}\\.json")) throw new IllegalArgumentException(
                "safe fixture filename required");
        Path target = root.resolve(name).normalize();
        if (!target.startsWith(root)) throw new IllegalArgumentException("unsafe fixture path");
        return target;
    }

    public record FixtureManifest(int schemaVersion, List<String> fixtures) { }
}
