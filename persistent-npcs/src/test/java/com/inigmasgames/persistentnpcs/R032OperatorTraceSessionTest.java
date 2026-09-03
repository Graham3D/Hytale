package com.inigmasgames.persistentnpcs;

import com.google.gson.JsonObject;
import com.inigmasgames.persistentnpcs.command.ImmersiveNpcTraceCommand;
import com.inigmasgames.persistentnpcs.conversation.ConversationSession;
import com.inigmasgames.persistentnpcs.diagnostics.NpcTraceManager;
import com.inigmasgames.persistentnpcs.diagnostics.NpcTurnAuditLog;
import com.inigmasgames.persistentnpcs.json.JsonFiles;
import com.inigmasgames.persistentnpcs.profile.NpcProfile;
import com.inigmasgames.persistentnpcs.profile.NpcProfileRegistry;
import com.inigmasgames.persistentnpcs.profile.ProfileRepository;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

public final class R032OperatorTraceSessionTest {
    private R032OperatorTraceSessionTest() { }

    public static void main(String[] args) throws Exception {
        noSessionMeansNoTraceFilesystemWrites();
        sessionsAreProfileLocalNpcIsolatedAndTickFiltered();
        toggleDisconnectAndRestartDoNotResume();
        commandAuthorizationIsStrictlyOperatorControlled();
        sourceHasNoAlwaysOnNpcAuditSink();
        System.out.println("R032.2 operator trace session tests passed.");
    }

    private static void noSessionMeansNoTraceFilesystemWrites() throws Exception {
        Fixture fixture = fixture();
        NpcTurnAuditLog recorder = new NpcTurnAuditLog(fixture.manager());
        ConversationSession conversation = new ConversationSession(UUID.randomUUID(),
                fixture.lycander().id(), UUID.randomUUID(), Instant.now());
        recorder.input(fixture.lycander(), conversation, UUID.randomUUID(), "Hello");
        recorder.failed(fixture.lycander(), conversation, UUID.randomUUID(), "Hello",
                new IllegalStateException("fixture"), null);
        assert !Files.exists(fixture.root().resolve("profiles/Lycander/traces"));
        assert !Files.exists(fixture.root().resolve("logs/npcs"));
    }

    private static void sessionsAreProfileLocalNpcIsolatedAndTickFiltered() throws Exception {
        Fixture fixture = fixture();
        UUID operator = UUID.randomUUID();
        var result = fixture.manager().toggle(operator, fixture.lycander());
        Path expectedParent = fixture.root().resolve("profiles/Lycander/traces")
                .toAbsolutePath().normalize();
        assert result.started();
        assert result.path().getParent().equals(expectedParent) : result.path();
        assert result.path().getFileName().toString()
                .equals("Lycander_2026-08-28_22-40-00.jsonl") : result.path();

        UUID response = UUID.randomUUID();
        JsonObject lycanderEvent = event("MODEL_OUTPUT", response);
        lycanderEvent.addProperty("rawModelOutput", "Aye.");
        fixture.manager().record(fixture.mara().id(), event("MODEL_OUTPUT", response));
        fixture.manager().record(fixture.lycander().id(),
                event("NAVIGATION_TICK", response));
        fixture.manager().record(fixture.lycander().id(), lycanderEvent);
        fixture.manager().awaitIdle();

        List<String> lines = Files.readAllLines(result.path());
        assert lines.size() == 2 : lines;
        JsonObject recorded = JsonFiles.GSON.fromJson(lines.get(1), JsonObject.class);
        assert recorded.get("npcId").getAsString()
                .equals(fixture.lycander().id().toString());
        assert recorded.get("responseId").getAsString().equals(response.toString());
        assert recorded.get("rawModelOutput").getAsString().equals("Aye.");
        assert lines.stream().noneMatch(line -> line.contains("NAVIGATION_TICK"));
    }

    private static void toggleDisconnectAndRestartDoNotResume() throws Exception {
        Fixture fixture = fixture();
        UUID operator = UUID.randomUUID();
        var lycander = fixture.manager().toggle(operator, fixture.lycander());
        assert fixture.manager().isActive(operator, fixture.lycander().id());
        var stopped = fixture.manager().toggle(operator, fixture.lycander());
        assert !stopped.started();
        fixture.manager().awaitIdle();
        int stoppedLines = Files.readAllLines(lycander.path()).size();
        fixture.manager().record(fixture.lycander().id(),
                event("MODEL_OUTPUT", UUID.randomUUID()));
        fixture.manager().awaitIdle();
        assert Files.readAllLines(lycander.path()).size() == stoppedLines;

        var secondLycander = fixture.manager().toggle(operator, fixture.lycander());
        var mara = fixture.manager().toggle(operator, fixture.mara());
        assert fixture.manager().activeSessionCount() == 2;
        assert fixture.manager().disconnect(operator) == 2;
        fixture.manager().awaitIdle();
        assert fixture.manager().activeSessionCount() == 0;
        int lycanderClosedLines = Files.readAllLines(secondLycander.path()).size();
        int maraClosedLines = Files.readAllLines(mara.path()).size();

        NpcTraceManager afterReconnect = new NpcTraceManager(
                new ProfileRepository(fixture.root()), fixture.clock(), ignored -> { });
        assert afterReconnect.activeSessionCount() == 0;
        afterReconnect.record(fixture.lycander().id(),
                event("MODEL_OUTPUT", UUID.randomUUID()));
        assert Files.readAllLines(secondLycander.path()).size() == lycanderClosedLines;
        assert Files.readAllLines(mara.path()).size() == maraClosedLines;
    }

    private static void commandAuthorizationIsStrictlyOperatorControlled() throws Exception {
        Fixture fixture = fixture();
        UUID operator = UUID.randomUUID();
        NpcProfileRegistry registry = new NpcProfileRegistry(
                new ProfileRepository(fixture.root()));
        registry.register(fixture.lycander());
        ImmersiveNpcTraceCommand command = new ImmersiveNpcTraceCommand(
                registry, fixture.manager(), operator::equals);
        assert command.isOperator(operator);
        assert !command.isOperator(UUID.randomUUID());
        assert fixture.manager().activeSessionCount() == 0
                : "Authorization checks must not start a trace";
    }

    private static void sourceHasNoAlwaysOnNpcAuditSink() throws Exception {
        Path source = Path.of("src/main/java/com/inigmasgames/persistentnpcs");
        String plugin = Files.readString(source.resolve("PersistentNpcsPlugin.java"));
        String manager = Files.readString(source.resolve("diagnostics/NpcTraceManager.java"));
        String command = Files.readString(source.resolve("command/ImmersiveNpcTraceCommand.java"));
        assert !plugin.contains("NPC_TURN_AUDIT_READY");
        assert !plugin.contains("logs/npcs");
        assert manager.contains("profileDirectory.resolve(\"traces\")");
        assert command.contains("hytale:Admin");
        assert command.contains("if (!isOperator(playerRef.getUuid()))");
    }

    private static JsonObject event(String type, UUID responseId) {
        JsonObject event = new JsonObject();
        event.addProperty("event", type);
        event.addProperty("responseId", responseId.toString());
        return event;
    }

    private static Fixture fixture() throws Exception {
        Path root = Files.createTempDirectory("r032-operator-trace-");
        Clock clock = Clock.fixed(Instant.parse("2026-08-29T02:40:00Z"),
                ZoneId.of("America/New_York"));
        NpcTraceManager manager = new NpcTraceManager(
                new ProfileRepository(root), clock, ignored -> { });
        return new Fixture(root, clock, manager, profile("Lycander"), profile("Mara"));
    }

    private static NpcProfile profile(String name) {
        return new NpcProfile(UUID.randomUUID(), name, "villager", "grounded",
                "A practical authored NPC.", "Live a grounded life.", "", "",
                List.of(), List.of(), List.of(), List.of(), 0).validated();
    }

    private record Fixture(Path root, Clock clock, NpcTraceManager manager,
            NpcProfile lycander, NpcProfile mara) { }
}
