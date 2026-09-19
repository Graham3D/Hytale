package com.inigmasgames.persistentnpcs;

import com.inigmasgames.persistentnpcs.conversation.ConversationSessionManager;
import com.inigmasgames.persistentnpcs.hytale.NpcRuntimeRegistry;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

public final class R021VoiceRuntimeCommandsTest {
    private R021VoiceRuntimeCommandsTest() { }

    public static void main(String[] args) throws Exception {
        UUID profile = UUID.randomUUID();
        UUID inactiveEntity = UUID.randomUUID();
        UUID activeEntity = UUID.randomUUID();
        UUID activeWorld = UUID.randomUUID();
        NpcRuntimeRegistry runtimes = new NpcRuntimeRegistry();
        assert runtimes.registerIfAbsent(profile, null, inactiveEntity);
        assert runtimes.registerIfAbsent(profile, activeWorld, activeEntity);
        assert runtimes.forProfile(profile).orElseThrow().entityId().equals(activeEntity);
        assert runtimes.profileForEntity(inactiveEntity).isEmpty();
        assert !runtimes.registerIfAbsent(profile, activeWorld, UUID.randomUUID());

        UUID replacement = UUID.randomUUID();
        assert runtimes.registerIfAbsent(profile, activeWorld, replacement,
                entityId -> !entityId.equals(activeEntity));
        assert runtimes.forProfile(profile).orElseThrow().entityId().equals(replacement);
        assert runtimes.profileForEntity(activeEntity).isEmpty();
        assert runtimes.profileForEntity(replacement).orElseThrow().equals(profile);

        UUID player = UUID.randomUUID();
        ConversationSessionManager sessions = new ConversationSessionManager(Duration.ofMinutes(5));
        sessions.focus(profile, player, Instant.now());
        sessions.endNpc(profile);
        assert sessions.active(player, Instant.now()).isEmpty();

        Path source = Path.of("src/main/java/com/inigmasgames/persistentnpcs");
        String createCommand = Files.readString(source.resolve("command/ImmersiveNpcCreateCommand.java"));
        String updateCommand = Files.readString(source.resolve("command/ImmersiveNpcUpdateCommand.java"));
        String adapter = Files.readString(source.resolve("hytale/HytaleNpcAdapter.java"));
        String appearance = Files.readString(source.resolve("profile/AppearanceRepository.java"));
        String voice = Files.readString(source.resolve("orbis/OrbisTurnCoordinator.java"));
        assert !Files.exists(source.resolve("command/AiNpcCommand.java"));
        assert createCommand.contains("super(\"create\"");
        assert updateCommand.contains("super(\"update\"");
        assert adapter.contains("appearances.queueApply");
        assert adapter.contains("RemoveReason.REMOVE");
        assert adapter.contains("persistentDataPreserved=true");
        assert appearance.contains("PlayerSkinComponent.getComponentType()");
        assert appearance.contains("commandBuffer.putComponent");
        assert voice.contains("CAPTURE_FRAME_ACCEPTED");
        assert voice.contains("no-eligible-npc-in-hearing-range");
        System.out.println("R021 targeted voice/runtime command tests passed.");
    }
}
