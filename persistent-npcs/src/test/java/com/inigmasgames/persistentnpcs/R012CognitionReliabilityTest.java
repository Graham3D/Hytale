package com.inigmasgames.persistentnpcs;

import com.inigmasgames.persistentnpcs.cognition.AttentionAction;
import com.inigmasgames.persistentnpcs.cognition.NpcCognitionService;
import com.inigmasgames.persistentnpcs.cognition.NpcEmotionStore;
import com.inigmasgames.persistentnpcs.cognition.SocialIntent;
import com.inigmasgames.persistentnpcs.conversation.ConversationSession;
import com.inigmasgames.persistentnpcs.conversation.DialogueMode;
import com.inigmasgames.persistentnpcs.perception.EnvironmentSample;
import com.inigmasgames.persistentnpcs.perception.EnvironmentSemanticAnalyzer;
import com.inigmasgames.persistentnpcs.perception.EnvironmentSnapshot;
import com.inigmasgames.persistentnpcs.perception.NpcPerceptionSnapshot;
import com.inigmasgames.persistentnpcs.profile.NpcProfile;
import com.inigmasgames.persistentnpcs.relationship.RelationshipStore;
import com.inigmasgames.persistentnpcs.task.NpcTaskScheduler;
import com.inigmasgames.persistentnpcs.task.NpcTaskStore;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.joml.Vector3d;

public final class R012CognitionReliabilityTest {
    private R012CognitionReliabilityTest() { }

    public static void main(String[] args) throws Exception {
        Path directory = Files.createTempDirectory("persistent-npcs-r012-");
        RelationshipStore relationships = new RelationshipStore(directory);
        relationships.load();
        NpcTaskStore tasks = new NpcTaskStore(directory);
        tasks.load();
        NpcEmotionStore emotions = new NpcEmotionStore(directory);
        emotions.load();
        NpcCognitionService cognition = new NpcCognitionService(
                relationships, tasks, emotions);

        UUID player = UUID.randomUUID();
        EnvironmentSnapshot environment = environment();
        NpcPerceptionSnapshot perception = perception(player, environment);
        ConversationSession cautiousSession = new ConversationSession(
                UUID.randomUUID(), UUID.randomUUID(), player, Instant.now());
        NpcProfile cautious = profile(cautiousSession.npcId(), "Cautious Mara",
                0.15, 0.45, 0.20);
        var cautiousTurn = cognition.evaluate(cautious, cautiousSession, "Follow me.",
                perception, DialogueMode.ORDINARY_CONVERSATION);
        assert !cautiousTurn.appraisal().actionAuthorized();
        assert cautiousTurn.appraisal().socialIntent() == SocialIntent.CLARIFY;
        assert cognition.enforceFollowUp("I need more context.", cautiousTurn)
                .contains("Where are we going?");

        ConversationSession boldSession = new ConversationSession(
                UUID.randomUUID(), UUID.randomUUID(), player, Instant.now());
        NpcProfile bold = profile(boldSession.npcId(), "Bold Mara", 0.80, 0.75, 0.85);
        var boldTurn = cognition.evaluate(bold, boldSession, "Follow me.", perception,
                DialogueMode.ORDINARY_CONVERSATION);
        assert boldTurn.appraisal().actionAuthorized();
        assert boldTurn.appraisal().socialIntent() == SocialIntent.ACCEPT_ACTION;
        assert cognition.fallbackAction("Follow me.", "All right, lead on.", boldTurn)
                .map(action -> action.id().equals("FOLLOW_PLAYER")).orElse(false);
        assert cognition.fallbackAction("Follow me.", "No. Where are we going?", boldTurn)
                .isEmpty();

        var environmentTurn = cognition.evaluate(bold, boldSession,
                "Do you know where we are?", perception, DialogueMode.ENVIRONMENT_QUERY);
        assert environmentTurn.responsePlan().attentionActions()
                .contains(AttentionAction.LOOK_AROUND);
        assert environmentTurn.responsePlan().attentionActions()
                .contains(AttentionAction.RETURN_LOOK_TO_PLAYER);
        assert environmentTurn.selfModel().locationAwareness().startsWith("UNKNOWN");
        assert !environmentTurn.appraisal().compact().toLowerCase().contains("chain of thought");
        assert environmentTurn.appraisalLatencyMillis() < 100;

        NpcEmotionStore reloaded = new NpcEmotionStore(directory);
        reloaded.load();
        assert reloaded.get(bold.id(), Instant.now()).emotion()
                == environmentTurn.appraisal().emotionalState();

        Vector3d playerPosition = new Vector3d(10, 90, 10);
        Vector3d desired = NpcTaskScheduler.trailingPosition(
                playerPosition, new Vector3d(0, 0, 1));
        assert Math.abs(desired.distance(playerPosition) - 2.75) < 0.001;
        assert desired.z < playerPosition.z;

        Path export = Path.of(System.getenv("APPDATA"), "Hytale", "UserData", "Saves",
                "NPC", "exports", "skins", "Mara");
        if (Files.isDirectory(export)) {
            assert Files.isRegularFile(export.resolve("SS_SKIN_Mara.json"));
            assert Files.isRegularFile(export.resolve("SS_MODEL_Mara.json"));
        }
        System.out.println("R012 cognition/reliability tests passed. appraisalMs="
                + environmentTurn.appraisalLatencyMillis());
    }

    private static NpcProfile profile(
            UUID id, String name, double risk, double curiosity, double trust) {
        return new NpcProfile(id, name, "Village resident", "Observant and concise",
                "A grounded village resident.", "Respond to current reality.", "home", "work",
                List.of("honesty"), List.of("manipulation"), List.of(),
                List.of("FOLLOW_PLAYER"), 5, 1, name, "ADULT", "Concise",
                List.of(), List.of(), "Mara", id, "HUMAN", List.of("observant"),
                List.of("honesty"), List.of("danger"), List.of("stay independent"),
                "", "GENERIC", risk, 0.55, curiosity, trust).validated();
    }

    private static EnvironmentSnapshot environment() {
        List<EnvironmentSample> samples = new ArrayList<>();
        samples.add(new EnvironmentSample("Hub_Portal_Default", "Portal", "Solid", "",
                8, 80, 0, true, false, false, false, false, true, false));
        for (int i = 0; i < 30; i++) {
            samples.add(new EnvironmentSample("Stone_Ruins_Pillar", "", "Solid", "",
                    i % 6, 79 + i % 3, i % 5, false, false, false,
                    false, false, false, false));
        }
        return new EnvironmentSemanticAnalyzer().summarize(UUID.randomUUID(), Instant.now(),
                0, 80, 0, 1.0, 80.0, 1.0, 14, samples, 2);
    }

    private static NpcPerceptionSnapshot perception(
            UUID player, EnvironmentSnapshot environment) {
        return new NpcPerceptionSnapshot(UUID.randomUUID(), UUID.randomUUID(),
                environment.worldId(), LocalDateTime.now(), 0, 80, 0,
                List.of(), List.of(), List.of(), List.of(), List.of(), List.of(),
                0, null, List.of(), environment);
    }
}
