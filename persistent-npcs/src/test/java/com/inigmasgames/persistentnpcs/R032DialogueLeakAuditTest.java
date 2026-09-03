package com.inigmasgames.persistentnpcs;

import com.google.gson.JsonObject;
import com.inigmasgames.persistentnpcs.cognition.NpcCognitionService;
import com.inigmasgames.persistentnpcs.cognition.SourcedBelief;
import com.inigmasgames.persistentnpcs.cognition.SourcedBeliefStore;
import com.inigmasgames.persistentnpcs.conversation.ConversationSession;
import com.inigmasgames.persistentnpcs.conversation.DialogueMode;
import com.inigmasgames.persistentnpcs.conversation.DialogueRequestState;
import com.inigmasgames.persistentnpcs.conversation.SpokenTextSafetyValidator;
import com.inigmasgames.persistentnpcs.diagnostics.NpcTurnAuditLog;
import com.inigmasgames.persistentnpcs.diagnostics.NpcTraceManager;
import com.inigmasgames.persistentnpcs.json.JsonFiles;
import com.inigmasgames.persistentnpcs.llm.LlmLatency;
import com.inigmasgames.persistentnpcs.memory.MemoryRecord;
import com.inigmasgames.persistentnpcs.memory.MemoryStore;
import com.inigmasgames.persistentnpcs.memory.MemoryType;
import com.inigmasgames.persistentnpcs.profile.NpcProfile;
import com.inigmasgames.persistentnpcs.profile.ProfileRepository;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class R032DialogueLeakAuditTest {
    private static final String LEAK = "[2026-08-29T02:14:26.340100600Z: "
            + "Player-asserted fact: Yeah, that's what I said.] "
            + "Status: Player has reiterated intent. No new actionable request received.";

    private R032DialogueLeakAuditTest() { }

    public static void main(String[] args) throws Exception {
        conversationalActsDoNotBecomeSourcedFacts();
        legacyDialogueActsRemainStoredButAreNotRetrievedAsFacts();
        screenshotLeakIsRejectedAtEveryStreamingBoundary();
        operatorTraceIsResponseCorrelatedJsonl();
        System.out.println("R032.1 dialogue leakage and gated trace tests passed.");
    }

    private static void conversationalActsDoNotBecomeSourcedFacts() {
        for (String value : List.of("Yeah, that's what I said.", "How are you?",
                "Will you take me to her?", "Okay, lead on.", "Take me tomorrow",
                "Hello?")) {
            assert !NpcCognitionService.isDeclarativePlayerReport(value) : value;
        }
        for (String value : List.of("My name is Graham.", "I saw Mara near the forge.",
                "Mara is missing.", "I live above the workshop.",
                "I promised to return your hammer.")) {
            assert NpcCognitionService.isDeclarativePlayerReport(value) : value;
        }
    }

    private static void screenshotLeakIsRejectedAtEveryStreamingBoundary() {
        assert !SpokenTextSafetyValidator.isSafe(LEAK);
        assert !SpokenTextSafetyValidator.isSafe(
                "[2026-08-29T02:14:26.340100600Z: Player-asserted fact: A report.");
        assert !SpokenTextSafetyValidator.isSafe(
                "] [2026-08-29T02:14:26.340100600Z: Player-reported belief: Another report.");
        assert !SpokenTextSafetyValidator.isSafe("] Status: Player has reiterated intent.");
        assert !SpokenTextSafetyValidator.isSafe("No new actionable request received.");
        assert SpokenTextSafetyValidator.isSafe(
                "Aye, I heard you. The sky is still over our heads.");
    }

    private static void legacyDialogueActsRemainStoredButAreNotRetrievedAsFacts()
            throws Exception {
        Path root = Files.createTempDirectory("r032-belief-filter-");
        UUID npc = UUID.randomUUID();
        UUID player = UUID.randomUUID();
        SourcedBeliefStore beliefs = new SourcedBeliefStore(root);
        beliefs.load();
        beliefs.append(belief(npc, player, "Yeah, that's what I said."));
        beliefs.append(belief(npc, player, "I live above the workshop."));
        List<SourcedBelief> relevant = beliefs.relevant(npc, List.of(player), 8);
        assert relevant.size() == 1 : relevant;
        assert relevant.getFirst().proposition().equals("I live above the workshop.");

        MemoryStore memories = new MemoryStore(root, 20);
        memories.load();
        memories.append(playerFact(npc, player, "Yeah, that's what I said."));
        memories.append(playerFact(npc, player, "I live above the workshop."));
        assert memories.forNpc(npc).size() == 2 : "Migration must be non-destructive";
        List<MemoryRecord> retrieved = memories.retrieveForCognition(
                npc, player, "Where do you live?", 8);
        assert retrieved.size() == 1 : retrieved;
        assert retrieved.getFirst().summary().contains("above the workshop");
    }

    private static void operatorTraceIsResponseCorrelatedJsonl() throws Exception {
        Path root = Files.createTempDirectory("r032-npc-audit-");
        NpcProfile profile = profile("Lycander");
        ConversationSession session = new ConversationSession(UUID.randomUUID(),
                profile.id(), UUID.randomUUID(), Instant.now());
        UUID completedResponse = UUID.randomUUID();
        UUID rejectedResponse = UUID.randomUUID();
        NpcTraceManager traces = new NpcTraceManager(
                new ProfileRepository(root), ignored -> { });
        NpcTurnAuditLog audit = new NpcTurnAuditLog(traces);
        audit.input(profile, session, UUID.randomUUID(), "Normal untraced use");
        assert !Files.exists(root.resolve("profiles/Lycander/traces"));
        var started = traces.toggle(UUID.randomUUID(), profile);
        audit.completed(profile, session, completedResponse, "The sky",
                "The sky is still there.", "The sky is still there.", new DialogueRequestState(
                        DialogueMode.ORDINARY_CONVERSATION, List.of(), List.of(), false),
                null, null, new LlmLatency(Instant.now(), 120, 240, true),
                300, null);
        audit.rejected(profile, session, rejectedResponse, "Yeah, that's what I said.",
                LEAK, "timestamped internal record", null);
        traces.awaitIdle();

        Path path = started.path();
        List<String> lines = Files.readAllLines(path);
        assert lines.size() == 3 : lines;
        JsonObject completed = JsonFiles.GSON.fromJson(lines.get(1), JsonObject.class);
        JsonObject rejected = JsonFiles.GSON.fromJson(lines.get(2), JsonObject.class);
        assert completed.get("schema").getAsString().equals("ImmersiveNPCs.NpcTrace.v1");
        assert completed.get("responseId").getAsString().equals(completedResponse.toString());
        assert completed.get("spokenText").getAsString().equals("The sky is still there.");
        assert completed.getAsJsonObject("timing").get("nemotronTtftMillis")
                .getAsLong() == 120;
        assert rejected.get("responseId").getAsString().equals(rejectedResponse.toString());
        assert rejected.get("event").getAsString().equals("DIALOGUE_REJECTED");
        assert rejected.get("rawModelOutput").getAsString().contains("Player-asserted fact");
        traces.close();
    }

    private static NpcProfile profile(String name) {
        return new NpcProfile(UUID.randomUUID(), name, "villager", "grounded",
                "A practical authored NPC.", "Live a grounded life.", "", "",
                List.of(), List.of(), List.of(), List.of(), 0).validated();
    }

    private static SourcedBelief belief(UUID npc, UUID player, String proposition) {
        return new SourcedBelief(UUID.randomUUID(), npc, player, player,
                "focused player", "PLAYER_REPORT", proposition, Instant.now(),
                0.68, 0.2, UUID.randomUUID(), UUID.randomUUID(), List.of());
    }

    private static MemoryRecord playerFact(UUID npc, UUID player, String proposition) {
        return new MemoryRecord(UUID.randomUUID(), npc, player, Instant.now(),
                MemoryType.PLAYER_FACT, 0.55,
                "Player-reported belief: " + proposition, 0.68,
                "PLAYER_REPORT:source=" + player, List.of(player), "",
                "Information received; not independently verified.");
    }
}
