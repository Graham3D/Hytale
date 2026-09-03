package com.inigmasgames.persistentnpcs.evaluation;

import com.inigmasgames.persistentnpcs.profile.ProfileRepository;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/** H0/H1 gate: one authoritative Orbis turn, reset, and zero production writes. */
public final class R090H0H1EvaluationHarnessTest {
    private R090H0H1EvaluationHarnessTest() { }

    public static void main(String[] args) throws Exception {
        assert EvaluationConfiguration.configuredMode()
                == EvaluationContracts.EvaluationMode.OFF;
        Path production = EvaluationTestRoots.profileSnapshot("Mara");
        var mara = new ProfileRepository(production).load("Mara");
        Path profile = production.resolve("profiles").resolve("mara").resolve("mara.json");
        assert Files.isRegularFile(profile);
        var scenario = scenario(mara.id(), profile);
        Path evaluation = Path.of("build", "orbis-eval").toAbsolutePath().normalize();
        try (var host = new OrbisEvaluationHost(evaluation, production, "h1-gate",
                EvaluationContracts.EvaluationMode.STATIC_REPLAY,
                new DeterministicEvaluationProvider(),
                "FIXTURE", "provider-free", "in-process", ignored -> { })) {
            var initial = host.start(scenario);
            var result = host.submit(scenario.turns().getFirst()).get(10, TimeUnit.SECONDS);
            assert "TURN_COMPLETED".equals(result.terminalState()) : result;
            assert result.canonicalResponses().values().stream()
                    .anyMatch("Hello. I'm listening."::equals) : result;
            assert result.observations().stream().anyMatch(value ->
                    "AUTHORITATIVE_EVALUATION_TEXT -> AUTHORITATIVE_TRANSCRIPT".equals(
                            value.facts().get("ingressProvenance"))) : result.observations();
            var correction = host.submit(scenario.turns().get(1)).get(10, TimeUnit.SECONDS);
            assert correction.stateDelta().memoriesAdded().stream().anyMatch(value ->
                    value.toLowerCase().contains("graham")) : correction.stateDelta();
            assert correction.stateDelta().beliefsAdded().stream().anyMatch(value ->
                    value.toLowerCase().contains("graham")) : correction.stateDelta();
            var perception = host.submit(scenario.turns().get(2)).get(10, TimeUnit.SECONDS);
            assert perception.canonicalResponses().values().stream().anyMatch(value ->
                    value.toLowerCase().contains("lantern")) : perception;
            host.reset(scenario);
            assert host.snapshot().pendingTurns() == 0;
            assert initial.sandboxRoot().startsWith(evaluation);
            host.finish();
        }
        System.out.println("R090 H0/H1 evaluation harness gate passed.");
    }

    private static EvaluationContracts.ConversationScenario scenario(UUID mara, Path profile) {
        var actor = new EvaluationContracts.ScenarioActor(mara, "Mara", profile);
        UUID player = UUID.randomUUID();
        var world = new EvaluationContracts.ScenarioWorldState("evaluation", 0, 64, 0,
                Map.of("location", "quiet evaluation room"), Set.of("Mara"),
                Map.of(player, Set.of("lantern")));
        var turn = new EvaluationContracts.ScenarioTurn(0, player, List.of(mara),
                "Hello, Mara.", EvaluationContracts.IngressKind.AUTHORITATIVE_EVALUATION_TEXT,
                EvaluationContracts.PacingPolicy.WAIT_FOR_TERMINAL,
                EvaluationContracts.ExpectedTurnContract.openSocial(), Map.of());
        var correctionExpected = new EvaluationContracts.ExpectedTurnContract("CORRECT",
                "CORRECTION", Set.of(), Set.of(), null, List.of(), Set.of(), "",
                new EvaluationContracts.ExpectedStateDelta(Set.of("Graham"),
                        Set.of("Graham"), Set.of(), Set.of()), Set.of("PROFILE"), Set.of(),
                15_000);
        var correction = new EvaluationContracts.ScenarioTurn(1, turn.speaker(), List.of(mara),
                "My name is Graham, not Grant.",
                EvaluationContracts.IngressKind.AUTHORITATIVE_EVALUATION_TEXT,
                EvaluationContracts.PacingPolicy.WAIT_FOR_TERMINAL, correctionExpected,
                Map.of());
        var perceptionExpected = new EvaluationContracts.ExpectedTurnContract("PERCEIVE",
                "CURRENT_PERCEPTION", Set.of(), Set.of("DIRECT_OBSERVATION"),
                com.inigmasgames.persistentnpcs.epistemic.Answerability.KNOWN,
                List.of(new EvaluationContracts.ExpectedProposition("CURRENT_PLAYER",
                        "HELD_ITEM", "lantern", "OBJECTIVE_FACT", "CURRENT",
                        Set.of("DIRECT_OBSERVATION"))), Set.of(), "",
                EvaluationContracts.ExpectedStateDelta.none(), Set.of("PROFILE"), Set.of(),
                15_000);
        var perception = new EvaluationContracts.ScenarioTurn(2, turn.speaker(), List.of(mara),
                "What am I holding?",
                EvaluationContracts.IngressKind.AUTHORITATIVE_EVALUATION_TEXT,
                EvaluationContracts.PacingPolicy.WAIT_FOR_TERMINAL, perceptionExpected,
                Map.of());
        return new EvaluationContracts.ConversationScenario("h1-authoritative-turn",
                "Provider-free production-parity smoke", List.of(actor), world,
                new EvaluationContracts.ScenarioCognitiveState(List.of(), List.of(),
                        List.of(), List.of(), List.of(), List.of()),
                List.of(turn, correction, perception),
                Set.of("H0", "H1", "PARITY", "ISOLATION"),
                EvaluationContracts.ResetPolicy.RESET_EACH_SCENARIO);
    }

}
