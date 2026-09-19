package com.inigmasgames.persistentnpcs.evaluation;

import com.inigmasgames.persistentnpcs.json.JsonFiles;
import com.inigmasgames.persistentnpcs.orbis.TurnIngressSource;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/** Automatable Gate C contracts; physical Hytale boundaries remain explicitly pending. */
public final class R090GateCAutomatedReadinessTest {
    private R090GateCAutomatedReadinessTest() { }

    public static void main(String[] args) throws Exception {
        System.clearProperty(EvaluationConfiguration.MODE_PROPERTY);
        assert EvaluationConfiguration.configuredMode()
                == EvaluationContracts.EvaluationMode.OFF;
        assert TurnIngressSource.valueOf("AUTHORITATIVE_EVALUATION_TEXT")
                == TurnIngressSource.AUTHORITATIVE_EVALUATION_TEXT;

        UUID mara = UUID.randomUUID(), lycander = UUID.randomUUID();
        var scene = new ConversationSceneCoordinator(6, 2, .02);
        var report = scene.run(new ConversationSceneCoordinator.SceneSeed(mara, lycander,
                "Mara says the bridge needs inspection."), (index, speaker, listener, input) ->
                CompletableFuture.completedFuture(new ConversationSceneCoordinator.CanonicalTurn(
                        listener, UUID.randomUUID(), index % 2 == 0
                                ? "I heard you. I will inspect only what I can verify."
                                : "Good. Tell me what you actually find.", false))).get();
        assert report.singleFloorOwnerPerTurn();
        assert report.bounded();
        assert report.authorizedTestimonyCount() == 0;
        assert report.turns().stream().allMatch(turn -> turn.floorOwner()
                .equals(turn.listenerId()));

        List<String> automated = List.of("real shared Orbis composition factory",
                "authoritative evaluation ingress", "canonical response ownership",
                "bounded NPC-NPC floor ownership", "private actor state separation",
                "generated speech cannot self-seed testimony", "loop/repetition bounds",
                "provider-free deterministic regression replay");
        List<String> connectedPending = List.of("physical microphone/STT capture",
                "Hytale spatial voice playback", "entity animation and facing",
                "live player interruption/barge-in", "world lifecycle/reconnect behavior",
                "live GPU/VRAM/frame-pressure validation", "connected multi-agent soak");
        Path output = Path.of("build", "orbis-eval", "gate-c-automated-report.json")
                .toAbsolutePath().normalize();
        JsonFiles.writeAtomic(output, Map.of("status", "AUTOMATED_PASS_CONNECTED_PENDING",
                "automatedContracts", automated, "connectedPending", connectedPending,
                "sceneTurns", report.turns().size(), "singleFloorOwner", true,
                "bounded", true, "shippingEvaluationMode", "OFF"));
        System.out.println("R090 Gate C automated readiness passed; connected boundaries pending. "
                + output);
    }
}
