package com.inigmasgames.persistentnpcs.evaluation;

import com.inigmasgames.persistentnpcs.json.JsonFiles;
import java.nio.file.Path;
import java.nio.file.Files;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Fixed-command local entry point used by tools/orbis-eval; never started by the plugin. */
public final class OrbisEvaluationCli {
    private OrbisEvaluationCli() { }

    public static void main(String[] args) throws Exception {
        Map<String, String> values = parse(args);
        String command = values.getOrDefault("command", "run");
        Path production = Path.of(values.getOrDefault("production", Path.of(
                System.getenv("APPDATA"), "Hytale", "UserData", "Saves", "NPC", "mods",
                "ImmersiveNPCs").toString())).toAbsolutePath().normalize();
        Path output = Path.of(values.getOrDefault("output", Path.of("build", "orbis-eval")
                .toString())).toAbsolutePath().normalize();
        if (command.equals("preflight")) {
            preflight(production, output); return;
        }
        if (command.equals("gate-a") || command.equals("gate-b")
                || command.equals("behavior-hardening")
                || command.equals("gate-b-cleanup")
                || command.equals("lycander-desire-stress")) {
            int turns = command.equals("gate-b") ? 50
                    : command.equals("behavior-hardening") ? 13
                    : command.equals("gate-b-cleanup") ? 6
                    : command.equals("lycander-desire-stress") ? 20 : 10;
            runBoundedCampaign(command, production, output, turns); return;
        }
        if (command.equals("multi-agent")) {
            runMultiAgent(production, output); return;
        }
        String scenarioName = values.getOrDefault("scenario", "h2-lycander-live");
        if (!scenarioName.equals("h2-lycander-live")) throw new IllegalArgumentException(
                "Unknown fixed scenario: " + scenarioName);
        String runId = values.getOrDefault("run", "h2-lycander-live-" + Instant.now()
                .toString().replace(':', '-'));
        Path source = snapshotSource(production, output, runId, List.of("Lycander"));
        var scenario = EvaluationScenarioCatalog.lycanderLiveSmoke(source);
        EvaluationConfiguration.requireToolMode(EvaluationContracts.EvaluationMode.LIVE_HEADLESS);
        ArrayList<OrbisEvaluationHost.TurnEvaluationResult> turns = new ArrayList<>();
        try (var host = OrbisEvaluationHost.live(output, source, runId, System.out::println)) {
            var handle = host.start(scenario);
            for (var turn : scenario.turns()) {
                var result = host.submit(turn).get();
                turns.add(result);
                System.out.println("TURN " + turn.index() + " terminal=" + result.terminalState()
                        + " elapsedMs=" + result.elapsedMillis() + " dialogue="
                        + result.canonicalResponses().values());
            }
            var report = host.finish();
            Path reportPath = output.resolve("runs").resolve(runId).resolve("report.json");
            JsonFiles.writeAtomic(reportPath, reportMap(handle, report, turns));
            if (turns.stream().anyMatch(value -> !value.passed())) {
                throw new IllegalStateException("Evaluation failed; see " + reportPath);
            }
            System.out.println("REPORT=" + reportPath);
        }
    }

    private static void runBoundedCampaign(String gate, Path production, Path output,
            int turnCount) throws Exception {
        int perActor = gate.equals("gate-b") ? 25 : turnCount;
        ArrayList<Map<String, Object>> summaries = new ArrayList<>();
        int completed = 0, diagnosed = 0;
        List<String> actors = gate.equals("lycander-desire-stress")
                ? List.of("Lycander") : List.of("Mara", "Lycander");
        for (String actor : actors) {
            String runId = gate + "-" + actor.toLowerCase(java.util.Locale.ROOT) + "-"
                    + Instant.now().toString().replace(':', '-');
            Path source = snapshotSource(production, output, runId, List.of(actor));
            var scenario = gate.equals("behavior-hardening")
                    ? EvaluationScenarioCatalog.behaviorHardening(source, actor)
                    : gate.equals("gate-b-cleanup")
                            ? EvaluationScenarioCatalog.gateBCleanup(source, actor)
                    : gate.equals("lycander-desire-stress")
                            ? EvaluationScenarioCatalog.desireStress(source, actor, perActor)
                    : EvaluationScenarioCatalog.gateA(source, actor, perActor);
            try (var host = OrbisEvaluationHost.live(output, source, runId,
                    value -> { })) {
                host.start(scenario);
                for (var turn : scenario.turns()) {
                    var result = host.submit(turn).get(); completed++;
                    if (!result.passed()) diagnosed++;
                    LinkedHashMap<String, Object> summary = new LinkedHashMap<>();
                    summary.put("actor", actor); summary.put("input", turn.utterance());
                    summary.put("terminal", result.terminalState());
                    summary.put("elapsedMs", result.elapsedMillis());
                    summary.put("dialogue", result.canonicalResponses().values());
                    summary.put("earliestBoundary", result.diagnosis() == null ? "NONE"
                            : result.diagnosis().earliestFailedBoundary().name());
                    summary.put("diagnosis", result.diagnosis() == null
                            ? Map.of() : result.diagnosis());
                    summary.put("failedVerdicts", result.verdicts().stream().filter(value ->
                            value.verdict() != EvaluationContracts.EvaluationVerdict.PASS)
                            .toList());
                    if (!result.passed()) {
                        // Preserve a bounded complete failure trail. Successful turns remain
                        // compact, while a failed provider/stream/recovery/ledger path can be
                        // reconstructed backward from its terminal event.
                        summary.put("failureTrail", result.observations());
                    }
                    result.observations().stream().filter(value -> value.eventType()
                            == com.inigmasgames.persistentnpcs.orbis.OrbisEventType
                                    .TURN_PLAN_COMPILED).findFirst().ifPresent(value ->
                                            summary.put("turnPlan", value.facts()));
                    result.observations().stream().filter(value -> value.eventType()
                            == com.inigmasgames.persistentnpcs.orbis.OrbisEventType
                                    .LLM_DISPATCHED).findFirst().ifPresent(value ->
                                            summary.put("dispatch", value.facts()));
                    summaries.add(Map.copyOf(summary));
                }
                host.finish();
            }
        }
        LinkedHashMap<String, Object> report = new LinkedHashMap<>();
        report.put("gate", gate.toUpperCase(java.util.Locale.ROOT));
        report.put("completed", completed); report.put("diagnosed", diagnosed);
        report.put("provider", "NEMOTRON"); report.put("model", "nemotron-3-nano:4b");
        report.put("turns", summaries);
        Path path = output.resolve(gate + "-report.json"); JsonFiles.writeAtomic(path, report);
        int required = gate.equals("gate-b") ? 50
                : gate.equals("behavior-hardening") ? 26
                : gate.equals("gate-b-cleanup") ? 12
                : gate.equals("lycander-desire-stress") ? 20 : 20;
        if (completed < required) throw new IllegalStateException(
                "bounded campaign did not complete required turns");
        if (diagnosed > 0) throw new IllegalStateException(gate.toUpperCase(
                java.util.Locale.ROOT) + " failed " + diagnosed
                + " turn contracts; see " + path);
        System.out.println(gate.toUpperCase(java.util.Locale.ROOT) + " completed=" + completed
                + " diagnosed=" + diagnosed + " report=" + path);
    }

    private static void runMultiAgent(Path production, Path output) throws Exception {
        String runId = "h8-live-" + Instant.now().toString().replace(':', '-');
        Path source = snapshotSource(production, output, runId,
                List.of("Mara", "Lycander"));
        var scenario = EvaluationScenarioCatalog.maraLycanderScene(source);
        try (var host = OrbisEvaluationHost.live(output, source, runId, value -> { })) {
            host.start(scenario);
            var seed = scenario.turns().getFirst();
            var coordinator = new ConversationSceneCoordinator(6, 2, .03);
            var report = coordinator.run(new ConversationSceneCoordinator.SceneSeed(
                    seed.speaker(), seed.audience().getFirst(), seed.utterance()),
                    (index, speaker, listener, utterance) -> {
                        var turn = new EvaluationContracts.ScenarioTurn(index, speaker,
                                List.of(listener), utterance, EvaluationContracts.IngressKind
                                        .AUTHORITATIVE_EVALUATION_TEXT,
                                EvaluationContracts.PacingPolicy.WAIT_FOR_TERMINAL,
                                EvaluationContracts.ExpectedTurnContract.openSocial(), Map.of());
                        return host.submit(turn).thenApply(result ->
                                new ConversationSceneCoordinator.CanonicalTurn(listener,
                                        UUID.randomUUID(), result.canonicalResponses().values()
                                                .stream().findFirst().orElse(""), false));
                    }).get();
            host.finish();
            Path path = output.resolve("h8-live-report.json");
            JsonFiles.writeAtomic(path, Map.of("turns", report.turns(), "terminal",
                    report.terminalReason(), "singleFloorOwner",
                    report.singleFloorOwnerPerTurn(), "bounded", report.bounded(),
                    "authorizedTestimonyCount", report.authorizedTestimonyCount()));
            if (!report.singleFloorOwnerPerTurn() || !report.bounded()
                    || report.turns().size() < 2) throw new IllegalStateException(
                            "H8 live scene gate failed");
            System.out.println("H8 LIVE turns=" + report.turns().size() + " terminal="
                    + report.terminalReason() + " report=" + path);
        }
    }

    private static void preflight(Path production, Path output) {
        if (!Files.isDirectory(production)) throw new IllegalStateException(
                "Production root does not exist: " + production);
        if (output.startsWith(production) || production.startsWith(output)) {
            throw new IllegalStateException("Evaluation and production roots overlap");
        }
        try { Files.createDirectories(output); }
        catch (java.io.IOException failure) { throw new IllegalStateException(
                "Evaluation root is not writable: " + output, failure); }
        var providers = JsonFiles.read(production.resolve("llm-providers.json"),
                com.inigmasgames.persistentnpcs.ai.LlmProviderCatalog.class).validated();
        var definition = providers.providers().get(providers.activeProvider());
        if (definition == null) throw new IllegalStateException(
                "Active LLM provider has no definition: " + providers.activeProvider());
        var scenario = EvaluationScenarioCatalog.lycanderLiveSmoke(production);
        var profiles = new com.inigmasgames.persistentnpcs.profile.ProfileRepository(production);
        profiles.load("Mara"); profiles.load("Lycander");
        LinkedHashMap<String, Object> result = new LinkedHashMap<>();
        result.put("productionRoot", production.toString());
        result.put("evaluationRoot", output.toString());
        result.put("scenario", scenario.id());
        result.put("actors", scenario.actors().stream().map(
                EvaluationContracts.ScenarioActor::name).toList());
        result.put("configuredShippingMode", EvaluationConfiguration.configuredMode().name());
        result.put("javaVersion", Runtime.version().toString());
        result.put("activeProvider", providers.activeProvider());
        result.put("activeModel", definition.model());
        result.put("productionConfigPresent", Files.isRegularFile(
                production.resolve("config.json")));
        result.put("rootIsolation", true);
        System.out.println(JsonFiles.GSON.toJson(result));
    }

    private static Path snapshotSource(Path production, Path output, String runId,
            List<String> actorNames) {
        if (runId == null || !runId.matches("[A-Za-z0-9_.:-]{1,160}")) {
            throw new IllegalArgumentException("safe source-snapshot id required");
        }
        Path root = output.resolve("source-snapshots").resolve(
                runId.replace(':', '-')).normalize();
        if (!root.startsWith(output) || root.startsWith(production)) {
            throw new IllegalArgumentException("unsafe evaluation source snapshot");
        }
        try {
            Files.createDirectories(root);
            for (String config : List.of("config.json", "llm-providers.json")) {
                Files.copy(production.resolve(config), root.resolve(config),
                        java.nio.file.StandardCopyOption.REPLACE_EXISTING,
                        java.nio.file.StandardCopyOption.COPY_ATTRIBUTES);
            }
            for (String actor : actorNames) {
                String safe = com.inigmasgames.persistentnpcs.profile.ProfileRepository
                        .sanitizeProfileName(actor).toLowerCase(java.util.Locale.ROOT);
                Path target = root.resolve("profiles").resolve(safe)
                        .resolve(safe + ".json");
                Files.createDirectories(target.getParent());
                Files.copy(production.resolve("profiles").resolve(safe)
                                .resolve(safe + ".json"), target,
                        java.nio.file.StandardCopyOption.REPLACE_EXISTING,
                        java.nio.file.StandardCopyOption.COPY_ATTRIBUTES);
            }
            return root;
        } catch (java.io.IOException failure) {
            throw new IllegalStateException("Could not create evaluation source snapshot", failure);
        }
    }

    private static Map<String, Object> reportMap(OrbisEvaluationHost.EvaluationRunHandle handle,
            OrbisEvaluationHost.EvaluationRunReport report,
            List<OrbisEvaluationHost.TurnEvaluationResult> turns) {
        LinkedHashMap<String, Object> values = new LinkedHashMap<>();
        values.put("schemaVersion", EvaluationContracts.SCHEMA_VERSION);
        values.put("scenarioId", report.scenarioId()); values.put("mode", report.mode().name());
        values.put("runtimeIdentity", report.runtimeIdentity());
        values.put("startedAt", report.startedAt().toString());
        values.put("finishedAt", report.finishedAt().toString());
        values.put("sandboxRoot", handle.sandboxRoot().toString());
        values.put("cleanTerminal", report.cleanTerminal());
        values.put("reproductionCommand", report.reproductionCommand());
        values.put("turns", turns); return values;
    }

    private static Map<String, String> parse(String[] args) {
        LinkedHashMap<String, String> values = new LinkedHashMap<>();
        for (int index = 0; index < args.length; index++) {
            String key = args[index];
            if (!key.startsWith("--") || index + 1 >= args.length) {
                throw new IllegalArgumentException("Expected --key value arguments");
            }
            values.put(key.substring(2), args[++index]);
        }
        return Map.copyOf(values);
    }
}
