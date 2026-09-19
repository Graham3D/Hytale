package com.inigmasgames.persistentnpcs.evaluation;

import com.inigmasgames.persistentnpcs.autonomy.AgentOperationStore;
import com.inigmasgames.persistentnpcs.cognition.NpcEmotionStore;
import com.inigmasgames.persistentnpcs.cognition.SourcedBeliefStore;
import com.inigmasgames.persistentnpcs.economy.ObligationStore;
import com.inigmasgames.persistentnpcs.memory.MemoryStore;
import com.inigmasgames.persistentnpcs.plan.SharedPlanStore;
import com.inigmasgames.persistentnpcs.profile.NpcProfileRegistry;
import com.inigmasgames.persistentnpcs.profile.ProfileRepository;
import com.inigmasgames.persistentnpcs.quest.DynamicQuestStore;
import com.inigmasgames.persistentnpcs.relationship.RelationshipStore;
import com.inigmasgames.persistentnpcs.task.NpcTaskStore;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;

/** Resettable evaluation-only cognitive/persistence root with write-escape guards. */
public final class ScenarioSandbox implements AutoCloseable {
    private final Path evaluationRoot;
    private final Path productionRoot;
    private final Path root;
    private final Map<Path, FileFingerprint> productionBefore;
    private State state;

    public ScenarioSandbox(Path evaluationRoot, Path productionRoot, String runId) {
        this.evaluationRoot = normalize(evaluationRoot);
        this.productionRoot = normalize(productionRoot);
        if (runId == null || !runId.matches("[A-Za-z0-9_.-]{1,96}")) {
            throw new IllegalArgumentException("safe evaluation run id required");
        }
        this.root = this.evaluationRoot.resolve("runs").resolve(runId)
                .resolve("sandbox").normalize();
        requireIsolated(root);
        productionBefore = fingerprintTree(this.productionRoot, false);
    }

    public synchronized State initialize(
            EvaluationContracts.ConversationScenario scenario, int memoryLimit) {
        requireSupportedCognitiveSeed(scenario.cognition());
        quiesceState();
        resetFiles();
        for (EvaluationContracts.ScenarioActor actor : scenario.actors()) {
            copyProfile(actor);
        }
        ProfileRepository repository = new ProfileRepository(root);
        NpcProfileRegistry profiles = new NpcProfileRegistry(repository);
        profiles.load();
        RelationshipStore relationships = new RelationshipStore(root);
        MemoryStore memories = new MemoryStore(root, Math.max(64, memoryLimit));
        NpcTaskStore tasks = new NpcTaskStore(root);
        DynamicQuestStore quests = new DynamicQuestStore(root);
        SharedPlanStore plans = new SharedPlanStore(root);
        ObligationStore obligations = new ObligationStore(root);
        AgentOperationStore operations = new AgentOperationStore(root);
        SourcedBeliefStore beliefs = new SourcedBeliefStore(root);
        NpcEmotionStore emotions = new NpcEmotionStore(root);
        relationships.load();
        relationships.importAuthored(repository.relationshipSources(), profiles);
        memories.load(); tasks.load(); quests.load(); plans.load();
        obligations.load(); operations.load(); beliefs.load(); emotions.load();
        state = new State(root, profiles, relationships, memories, tasks, quests, plans,
                obligations, operations, beliefs, emotions, scenario.world(), Instant.now());
        assertNoProductionWriteEscape();
        return state;
    }

    private void quiesceState() {
        if (state == null) return;
        state.memories().flush();
        state.beliefs().close();
        state = null;
    }

    private static void requireSupportedCognitiveSeed(
            EvaluationContracts.ScenarioCognitiveState seed) {
        if (seed == null) return;
        if (!seed.authoredFacts().isEmpty() || !seed.memories().isEmpty()
                || !seed.beliefs().isEmpty() || !seed.relationships().isEmpty()
                || !seed.commitments().isEmpty() || !seed.secrets().isEmpty()) {
            throw new UnsupportedOperationException(
                    "Typed scenario cognitive-state seeding is not implemented; refusing "
                            + "to run a scenario whose declared state would be ignored");
        }
    }

    public synchronized State state() {
        if (state == null) throw new IllegalStateException("sandbox is not initialized");
        return state;
    }

    public Path root() { return root; }

    public synchronized void assertNoProductionWriteEscape() {
        Map<Path, FileFingerprint> after = fingerprintTree(productionRoot, false);
        if (!productionBefore.equals(after)) {
            throw new IllegalStateException(
                    "Evaluation write escaped into the production data root");
        }
        if (root.startsWith(productionRoot) || productionRoot.startsWith(root)) {
            throw new IllegalStateException("Evaluation and production roots overlap");
        }
    }

    public synchronized SandboxSnapshot snapshot() {
        Map<Path, FileFingerprint> values = fingerprintTree(root, true);
        return new SandboxSnapshot(root, values.size(), hashFingerprints(values));
    }

    private void copyProfile(EvaluationContracts.ScenarioActor actor) {
        Path source = normalize(actor.profileSource());
        if (!Files.isRegularFile(source)) {
            throw new IllegalArgumentException("Profile source does not exist: " + source);
        }
        String safeName = ProfileRepository.sanitizeProfileName(actor.name());
        Path destination = root.resolve("profiles").resolve(safeName)
                .resolve(safeName + ".json").normalize();
        if (!destination.startsWith(root)) throw new IllegalStateException(
                "Unsafe sandbox profile target");
        try {
            Files.createDirectories(destination.getParent());
            Files.copy(source, destination, StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.COPY_ATTRIBUTES);
        } catch (IOException failure) {
            throw new IllegalStateException("Could not clone evaluation profile", failure);
        }
    }

    private void resetFiles() {
        requireIsolated(root);
        if (Files.exists(root)) try (var paths = Files.walk(root)) {
            paths.sorted(Comparator.reverseOrder()).forEach(path -> {
                try { Files.deleteIfExists(path); }
                catch (IOException failure) { throw new DeleteFailure(failure); }
            });
        } catch (DeleteFailure failure) {
            throw new IllegalStateException("Could not reset evaluation sandbox", failure.cause);
        } catch (IOException failure) {
            throw new IllegalStateException("Could not enumerate evaluation sandbox", failure);
        }
        try { Files.createDirectories(root); }
        catch (IOException failure) { throw new IllegalStateException(
                "Could not create evaluation sandbox", failure); }
    }

    private void requireIsolated(Path candidate) {
        if (!candidate.startsWith(evaluationRoot) || candidate.equals(evaluationRoot)
                || candidate.startsWith(productionRoot) || productionRoot.startsWith(candidate)) {
            throw new IllegalArgumentException("Unsafe evaluation sandbox path: " + candidate);
        }
    }

    private static Path normalize(Path value) {
        if (value == null) throw new IllegalArgumentException("path required");
        return value.toAbsolutePath().normalize();
    }

    private static Map<Path, FileFingerprint> fingerprintTree(Path base) {
        return fingerprintTree(base, false);
    }

    private static Map<Path, FileFingerprint> fingerprintTree(Path base,
            boolean tolerateAtomicReplacement) {
        LinkedHashMap<Path, FileFingerprint> values = new LinkedHashMap<>();
        if (!Files.exists(base)) return Map.of();
        try (var paths = Files.walk(base)) {
            paths.filter(Files::isRegularFile).sorted().forEach(path -> {
                try {
                    String name = path.getFileName().toString();
                    if (name.endsWith(".tmp") || name.contains(".install-")) return;
                    values.put(base.relativize(path), new FileFingerprint(
                            Files.size(path), Files.getLastModifiedTime(path).toMillis(),
                            sha256(path)));
                } catch (java.nio.file.NoSuchFileException atomicReplacement) {
                    // An asynchronous store may atomically replace its sandbox file while this
                    // observer takes a diagnostic snapshot. The next snapshot sees the target.
                } catch (IOException failure) {
                    if (tolerateAtomicReplacement || !Files.exists(path)) return;
                    throw new FingerprintFailure(failure);
                }
            });
        } catch (FingerprintFailure failure) {
            throw new IllegalStateException("Could not fingerprint data root", failure.cause);
        } catch (IOException failure) {
            throw new IllegalStateException("Could not enumerate data root", failure);
        }
        return Map.copyOf(values);
    }

    private static String sha256(Path path) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (var input = Files.newInputStream(path)) {
                byte[] buffer = new byte[8192];
                for (int read; (read = input.read(buffer)) >= 0; ) {
                    if (read > 0) digest.update(buffer, 0, read);
                }
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (java.security.NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    private static String hashFingerprints(Map<Path, FileFingerprint> values) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            values.forEach((path, fingerprint) -> digest.update((path + "=" + fingerprint)
                    .getBytes(java.nio.charset.StandardCharsets.UTF_8)));
            return HexFormat.of().formatHex(digest.digest());
        } catch (java.security.NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    @Override public synchronized void close() {
        quiesceState();
        assertNoProductionWriteEscape();
    }

    private record FileFingerprint(long size, long modifiedMillis, String sha256) { }
    private static final class DeleteFailure extends RuntimeException {
        private final IOException cause;
        private DeleteFailure(IOException cause) { this.cause = cause; }
    }
    private static final class FingerprintFailure extends RuntimeException {
        private final IOException cause;
        private FingerprintFailure(IOException cause) { this.cause = cause; }
    }

    public record SandboxSnapshot(Path root, int fileCount, String contentHash) { }
    public record State(Path root, NpcProfileRegistry profiles,
            RelationshipStore relationships, MemoryStore memories, NpcTaskStore tasks,
            DynamicQuestStore quests, SharedPlanStore plans, ObligationStore obligations,
            AgentOperationStore operations, SourcedBeliefStore beliefs,
            NpcEmotionStore emotions, EvaluationContracts.ScenarioWorldState world,
            Instant initializedAt) { }
}
