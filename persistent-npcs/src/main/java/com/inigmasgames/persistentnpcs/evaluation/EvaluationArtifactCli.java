package com.inigmasgames.persistentnpcs.evaluation;

import com.google.gson.JsonObject;
import com.inigmasgames.persistentnpcs.json.JsonFiles;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Fixed, schema-validated artifact operations used by project-owned evaluation scripts. */
public final class EvaluationArtifactCli {
    private EvaluationArtifactCli() { }

    public static void main(String[] args) {
        Map<String, String> values = parse(args);
        String command = required(values, "command");
        Path project = Path.of(values.getOrDefault("project", "."))
                .toAbsolutePath().normalize();
        Path evaluation = project.resolve("build/orbis-eval").normalize();
        Path candidates = evaluation.resolve("candidates");
        Path fixtures = project.resolve("src/test/resources/conversation-matrix/frozen")
                .normalize();
        switch (command) {
            case "import-trace" -> importTrace(values, evaluation);
            case "freeze-candidate" -> freeze(values, evaluation, candidates, fixtures);
            case "promote-fixture" -> promote(values, candidates, fixtures);
            case "replay-fixture" -> replay(values, fixtures);
            default -> throw new IllegalArgumentException("Unknown fixed artifact command: "
                    + command);
        }
    }

    private static void importTrace(Map<String, String> values, Path evaluation) {
        Path trace = Path.of(required(values, "trace")).toAbsolutePath().normalize();
        String text = required(values, "text");
        String id = safe(values.getOrDefault("id", "trace-import-"
                + Instant.now().toString().replace(':', '-')));
        var imported = new TraceImportService().analyzeTurn(trace, text);
        Path output = evaluation.resolve("imports").resolve(id + ".json").normalize();
        requireUnder(output, evaluation);
        JsonFiles.writeAtomic(output, imported);
        System.out.println("IMPORTED=" + output);
    }

    private static void freeze(Map<String, String> values, Path evaluation,
            Path candidates, Path fixtures) {
        String run = safe(required(values, "run"));
        String scenario = safe(required(values, "scenario"));
        Path report = evaluation.resolve("runs").resolve(run).resolve("report.json").normalize();
        requireUnder(report, evaluation);
        if (!Files.isRegularFile(report)) throw new IllegalArgumentException(
                "Verified run report not found: " + report);
        JsonObject reportJson = JsonFiles.read(report, JsonObject.class);
        if (!reportJson.has("cleanTerminal") || !reportJson.get("cleanTerminal").getAsBoolean()) {
            throw new IllegalStateException("Only a clean terminal run can become a candidate");
        }
        String utterance = values.getOrDefault("utterance", scenario.replace('-', ' '));
        var fixture = new FrozenConversationFixture(EvaluationContracts.SCHEMA_VERSION,
                scenario, "verified-live:" + run, run, Instant.now(),
                Set.of("LIVE_DISCOVERY", "EXACT_REPLAY"),
                Map.of("utterance", utterance, "sourceReport", report.toString()),
                Map.of("terminal", "TURN_COMPLETED", "productionWriteEscape", "false"),
                Set.of(), Set.of(), List.of("paraphrase", "referent", "negative-control",
                        "cross-profile"), reportJson.has("runtimeIdentity")
                                ? reportJson.get("runtimeIdentity").toString() : "shared-factory",
                "CANDIDATE");
        Path output = new FrozenFixtureRepository(candidates, fixtures)
                .freezeCandidate(fixture);
        System.out.println("CANDIDATE=" + output);
    }

    private static void promote(Map<String, String> values, Path candidates, Path fixtures) {
        if (!Boolean.parseBoolean(values.getOrDefault("reviewed", "false"))) {
            throw new IllegalStateException("Promotion requires --reviewed true");
        }
        Path output = new FrozenFixtureRepository(candidates, fixtures)
                .promote(safe(required(values, "candidate")), true);
        System.out.println("PROMOTED=" + output);
    }

    private static void replay(Map<String, String> values, Path fixtures) {
        String id = safe(required(values, "fixture"));
        Path path = fixtures.resolve(id + ".json").normalize();
        requireUnder(path, fixtures);
        FrozenConversationFixture fixture = JsonFiles.read(path,
                FrozenConversationFixture.class);
        if (!fixture.reviewStatus().equals("PROMOTED_REVIEWED")) {
            throw new IllegalStateException("Fixture is not reviewed/promoted: " + id);
        }
        if (fixture.input().isEmpty() || fixture.expectedBoundaries().isEmpty()) {
            throw new IllegalStateException("Fixture lacks deterministic replay contracts");
        }
        var result = new FrozenFixtureReplayHarness().replay(fixture);
        if (!result.passed()) throw new IllegalStateException(
                "Frozen fixture replay failed: " + result.diagnostic());
        System.out.println("REPLAY_PASSED=" + path + " boundary=" + result.boundary()
                + " variants=" + fixture.requiredVariants().size());
    }

    private static Map<String, String> parse(String[] args) {
        LinkedHashMap<String, String> values = new LinkedHashMap<>();
        for (int index = 0; index < args.length; index++) {
            if (!args[index].startsWith("--") || index + 1 >= args.length) {
                throw new IllegalArgumentException("Expected --key value arguments");
            }
            values.put(args[index].substring(2), args[++index]);
        }
        return Map.copyOf(values);
    }

    private static String required(Map<String, String> values, String key) {
        String value = values.get(key);
        if (value == null || value.isBlank()) throw new IllegalArgumentException(
                "--" + key + " is required");
        return value;
    }

    private static String safe(String value) {
        if (value == null || !value.matches("[A-Za-z0-9_.-]{1,96}")) {
            throw new IllegalArgumentException("safe artifact identity required");
        }
        return value;
    }

    private static void requireUnder(Path path, Path root) {
        Path normalizedRoot = root.toAbsolutePath().normalize();
        if (!path.toAbsolutePath().normalize().startsWith(normalizedRoot)) {
            throw new IllegalArgumentException("artifact path escaped evaluation root");
        }
    }
}
