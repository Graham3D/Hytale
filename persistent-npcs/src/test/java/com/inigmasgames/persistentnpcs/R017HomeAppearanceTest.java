package com.inigmasgames.persistentnpcs;

import com.google.gson.JsonParser;
import com.inigmasgames.persistentnpcs.cognition.NpcCognitionService;
import com.inigmasgames.persistentnpcs.cognition.NpcEmotionStore;
import com.inigmasgames.persistentnpcs.conversation.ConversationSession;
import com.inigmasgames.persistentnpcs.conversation.DialogueMode;
import com.inigmasgames.persistentnpcs.home.HomeBehaviorConfig;
import com.inigmasgames.persistentnpcs.home.NpcHomeAnchorStore;
import com.inigmasgames.persistentnpcs.home.NpcHomeBehaviorController;
import com.inigmasgames.persistentnpcs.home.NpcMovementState;
import com.inigmasgames.persistentnpcs.perception.NpcPerceptionSnapshot;
import com.inigmasgames.persistentnpcs.profile.AppearanceRepository;
import com.inigmasgames.persistentnpcs.profile.NpcProfile;
import com.inigmasgames.persistentnpcs.relationship.RelationshipStore;
import com.inigmasgames.persistentnpcs.task.NpcTask;
import com.inigmasgames.persistentnpcs.task.NpcTaskState;
import com.inigmasgames.persistentnpcs.task.NpcTaskStore;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.joml.Vector3d;

public final class R017HomeAppearanceTest {
    private R017HomeAppearanceTest() { }

    public static void main(String[] args) throws Exception {
        Path root = Files.createTempDirectory("persistent-npcs-r017-");
        Path data = root.resolve("mods").resolve("ImmersiveNPCs");
        NpcTaskStore tasks = new NpcTaskStore(data);
        tasks.load();
        NpcHomeAnchorStore anchors = new NpcHomeAnchorStore(data);
        anchors.load();
        NpcHomeBehaviorController home = new NpcHomeBehaviorController(
                anchors, tasks, new HomeBehaviorConfig(true, 4.0, 30, 75, 5),
                ignored -> { });

        UUID npc = UUID.randomUUID();
        UUID world = UUID.randomUUID();
        UUID player = UUID.randomUUID();
        Vector3d original = new Vector3d(10, 64, 20);
        var initial = home.initialize(npc, world, original, Instant.now());
        assert initial.movementState() == NpcMovementState.IDLE_HOME;
        assert initial.home().equals(original);
        assert initial.anchor().equals(original);

        Vector3d investigation = NpcHomeBehaviorController.wanderTarget(
                initial.anchor(), 4.0, 123456L);
        assert investigation.distance(initial.anchor()) <= 4.01;
        assert investigation.distance(initial.anchor()) >= 1.49;

        home.beginFollowing(npc);
        assert anchors.get(npc).movementState() == NpcMovementState.FOLLOWING_PLAYER;
        Vector3d temporary = new Vector3d(30, 65, 40);
        var waiting = home.stopFollowing(npc, world, temporary, true, Instant.now());
        assert waiting.movementState() == NpcMovementState.IDLE_HOME;
        assert waiting.temporaryAnchor();
        assert waiting.anchor().equals(temporary);
        assert waiting.home().equals(original);
        home.beginFollowing(npc);
        var returning = home.stopFollowing(npc, world, new Vector3d(35, 65, 45),
                false, Instant.now());
        assert returning.movementState() == NpcMovementState.RETURNING_HOME;
        assert returning.target().equals(temporary);

        NpcHomeAnchorStore reloadedAnchors = new NpcHomeAnchorStore(data);
        reloadedAnchors.load();
        assert reloadedAnchors.get(npc).home().equals(original);
        assert reloadedAnchors.get(npc).anchor().equals(temporary);

        NpcTask legacy = followTask(npc, player, world, Map.of());
        Map<String, String> provenance = new LinkedHashMap<>();
        provenance.put("movementState", "FOLLOWING_PLAYER");
        provenance.put("source", "PLAYER_ACTION");
        NpcTask explicit = followTask(npc, player, world, provenance);
        tasks.put(legacy);
        tasks.put(explicit);
        assert tasks.cancelLegacyFollowTasks(npc, "migration") == 1;
        assert tasks.activeFor(npc).size() == 1;
        assert tasks.activeFor(npc).get(0).taskId().equals(explicit.taskId());

        RelationshipStore relationships = new RelationshipStore(data);
        relationships.load();
        NpcEmotionStore emotions = new NpcEmotionStore(data);
        emotions.load();
        NpcCognitionService cognition = new NpcCognitionService(
                relationships, tasks, emotions);
        NpcProfile mara = profile(npc);
        ConversationSession session = new ConversationSession(
                UUID.randomUUID(), npc, player, Instant.now());
        var follow = cognition.evaluate(mara, session, "Stay with me.",
                NpcPerceptionSnapshot.unavailable(npc),
                DialogueMode.ORDINARY_CONVERSATION);
        assert "FOLLOW_PLAYER".equals(follow.appraisal().requestedAction());
        var stop = cognition.evaluate(mara, session, "Wait here.",
                NpcPerceptionSnapshot.unavailable(npc),
                DialogueMode.ORDINARY_CONVERSATION);
        assert "STOP_FOLLOWING".equals(stop.appraisal().requestedAction());
        assert stop.appraisal().actionAuthorized();
        assert cognition.fallbackAction("Wait here.", "Very well.", stop)
                .filter(action -> action.id().equals("STOP_FOLLOWING"))
                .filter(action -> action.parameters().get("waitHere").getAsBoolean())
                .isPresent();

        Path skinDir = root.resolve("exports").resolve("skins").resolve("Mara");
        Files.createDirectories(skinDir);
        Path skin = skinDir.resolve("SS_SKIN_Mara.json");
        Path model = skinDir.resolve("SS_MODEL_Mara.json");
        Files.writeString(skin, "{}");
        Files.writeString(model, "{}");
        AppearanceRepository appearances = new AppearanceRepository(data, ignored -> { });
        assert appearances.resolveSkinFile("Mara").orElseThrow().equals(skin);
        assert appearances.resolveModelFile("Mara").orElseThrow().equals(model);

        Path installedSkinDir = Path.of(System.getenv("APPDATA"), "Hytale", "data",
                "pre-release", "Saves", "ImmersiveNPCs", "exports", "skins", "Mara");
        if (Files.isDirectory(installedSkinDir)) {
            String authoredSkin = Files.readString(
                    installedSkinDir.resolve("SS_SKIN_Mara.json"));
            String authoredModel = Files.readString(
                    installedSkinDir.resolve("SS_MODEL_Mara.json"));
            assert JsonParser.parseString(authoredSkin).isJsonObject();
            assert JsonParser.parseString(authoredModel).isJsonObject();
        }
        System.out.println("R017 home/follow/appearance tests passed.");
    }

    private static NpcTask followTask(
            UUID npc, UUID player, UUID world, Map<String, String> data) {
        return new NpcTask(UUID.randomUUID(), npc, player, "FOLLOW_PLAYER", world,
                1.0, 2.0, 3.0, null, "Follow requester", NpcTaskState.ACTIVE,
                Instant.now(), null, data);
    }

    private static NpcProfile profile(UUID id) {
        return new NpcProfile(id, "Mara", "Village resident", "Direct",
                "A grounded village resident.", "Respond to current reality.",
                "home", "work", List.of(), List.of(), List.of(),
                List.of("FOLLOW_PLAYER", "STOP_FOLLOWING"), 5, 1, "Mara", "ADULT",
                "Direct", List.of(), List.of(), "Mara", id, "HUMAN",
                List.of("observant"), List.of("honesty"), List.of("danger"),
                List.of("stay independent"), "mara", "none", "GENERIC",
                0.8, 0.7, 0.7, 0.8).validated();
    }
}
