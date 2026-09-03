package com.inigmasgames.persistentnpcs.evaluation;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public final class R090H6CampaignTest {
    private R090H6CampaignTest() { }
    public static void main(String[] args) throws Exception {
        var budget = new EvaluationContracts.ResourceBudget(50, 160,
                Duration.ofMinutes(10), 8 * 1024 * 1024);
        var planner = new AutonomousCampaignPlanner();
        var plan = planner.plan("gate-b-deterministic", 110, 50, budget);
        assert plan.probes().size() == 160;
        assert plan.probes().stream().filter(AutonomousCampaignPlanner.Probe::live).count() == 50;
        Path production = EvaluationTestRoots.profileSnapshot("Mara");
        var scenario = EvaluationScenarioCatalog.campaign(production, "Mara",
                plan.probes().subList(0, 110));
        var root = Path.of("build", "orbis-eval", "h6").toAbsolutePath().normalize();
        ArrayList<AutonomousCampaignPlanner.ProbeResult> results = new ArrayList<>();
        try (var host = new OrbisEvaluationHost(root, production, "h6-real-probes",
                EvaluationContracts.EvaluationMode.STATIC_REPLAY,
                new DeterministicEvaluationProvider(), "FIXTURE", "provider-free",
                "in-process", ignored -> { })) {
            host.start(scenario);
            for (int index = 0; index < 110; index++) {
                var turn = host.submit(scenario.turns().get(index)).get(10, TimeUnit.SECONDS);
                boolean passed = turn.verdicts().stream().allMatch(value -> value.verdict()
                        == EvaluationContracts.EvaluationVerdict.PASS);
                results.add(new AutonomousCampaignPlanner.ProbeResult(
                        plan.probes().get(index), passed,
                        turn.diagnosis() == null ? null
                                : turn.diagnosis().earliestFailedBoundary(),
                        passed ? "real production-graph probe pass"
                                : String.valueOf(turn.diagnosis())));
            }
            host.finish();
        }
        var report = planner.summarize(plan, results);
        assert report.passed() : report.prioritizedFailures();
        assert report.coverage().size() == 10 : report.coverage();
        var history = Files.createTempDirectory("orbis-h6-history-");
        var path = new HardeningRecordStore(history).write(
                new HardeningRecordStore.HardeningRecord(
                "H6-campaign", Instant.now(), "provider-free curriculum audit",
                EvaluationContracts.BoundaryId.INGRESS,
                "execute every probe through shared Orbis composition",
                List.of("110 real deterministic production-graph probes"),
                Map.of("status", "PASS", "completed", "110")));
        assert Files.isRegularFile(path);
        System.out.println("R090 H6 autonomous campaign gate passed: deterministic=110");
    }
}
