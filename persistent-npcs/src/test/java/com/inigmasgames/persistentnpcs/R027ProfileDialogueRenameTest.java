package com.inigmasgames.persistentnpcs;

import com.inigmasgames.persistentnpcs.conversation.CommittedDialogueResponse;
import com.inigmasgames.persistentnpcs.json.JsonFiles;
import com.inigmasgames.persistentnpcs.persistence.ImmersiveNpcDataMigration;
import com.inigmasgames.persistentnpcs.profile.AppearanceRepository;
import com.inigmasgames.persistentnpcs.profile.NpcProfile;
import com.inigmasgames.persistentnpcs.profile.NpcProfileEditorService;
import com.inigmasgames.persistentnpcs.profile.NpcProfileRegistry;
import com.inigmasgames.persistentnpcs.profile.ProfileRepository;
import com.inigmasgames.persistentnpcs.voice.TtsTextNormalizer;
import com.inigmasgames.persistentnpcs.voice.VocalState;
import com.inigmasgames.persistentnpcs.voice.SpeechPhraseChunker;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class R027ProfileDialogueRenameTest {
    private R027ProfileDialogueRenameTest() { }

    public static void main(String[] args) throws Exception {
        migrationIsNonDestructiveAndCanonical();
        profileImportSanitizesAndPreservesIdentity();
        namedProfileIsAuthoritativeWithLegacyFallback();
        committedDialogueIsImmutableAndSharedVerbatim();
        streamedPhrasesCommitBeforeCompletion();
        update6UiAndIdentityAreWired();
        System.out.println("R027 profile UI, dialogue sync, and rename tests passed.");
    }

    private static void migrationIsNonDestructiveAndCanonical() throws Exception {
        Path save = Files.createTempDirectory("immersive-npcs-r027");
        Path mods = save.resolve("mods");
        Path oldData = mods.resolve("InigmasGames_PersistentNPCs");
        Path generated = mods.resolve("InigmasGames_ImmersiveNPCs");
        Files.createDirectories(oldData.resolve("memories"));
        Files.writeString(oldData.resolve("memories/kept.json"), "legacy");
        Path groupedProfile = oldData.resolve("profiles/mara");
        Files.createDirectories(groupedProfile);
        JsonFiles.writeAtomic(groupedProfile.resolve("mara.json"),
                profile(UUID.randomUUID(), "Mara", "Legacy biography"));
        Files.writeString(groupedProfile.resolve("SS_SKIN_Mara.json"), "{}");
        Files.createDirectories(mods.resolve("ImmersiveNPCs/memories"));
        Files.writeString(mods.resolve("ImmersiveNPCs/memories/kept.json"), "authoritative");
        Path resolved = ImmersiveNpcDataMigration.resolveAndMigrate(generated, ignored -> { });
        assert resolved.equals(mods.resolve("ImmersiveNPCs").toAbsolutePath().normalize());
        assert Files.readString(resolved.resolve("memories/kept.json")).equals("authoritative");
        assert !Files.exists(oldData);
        assert Files.isRegularFile(resolved.resolve("profiles/mara/mara.json"));
        assert Files.isRegularFile(resolved.resolve("profiles/mara/SS_Skin_Character.json"));
        Path backupRoot = save.resolve(ImmersiveNpcDataMigration.LEGACY_BACKUP_DIRECTORY);
        try (var archives = Files.list(backupRoot)) {
            Path archived = archives.filter(Files::isDirectory).findFirst().orElseThrow();
            assert Files.readString(archived.resolve("memories/kept.json")).equals("legacy");
            assert Files.isRegularFile(archived.resolve("profiles/mara/mara.json"));
        }
    }

    private static void profileImportSanitizesAndPreservesIdentity() throws Exception {
        Path data = Files.createTempDirectory("immersive-npcs-profile");
        ProfileRepository profiles = new ProfileRepository(data);
        NpcProfileRegistry registry = new NpcProfileRegistry(profiles);
        registry.load();
        NpcProfileEditorService editor = new NpcProfileEditorService(profiles, registry,
                new AppearanceRepository(data, ignored -> { }));
        assertThrows(() -> ProfileRepository.sanitizeProfileName("../Mara"));
        assertThrows(() -> ProfileRepository.sanitizeProfileName("Mara\\Elsewhere"));

        String name = "Rowan";
        editor.beginCreate(name);
        UUID scaffoldIdentity = profiles.load(name).id();
        Path selected = data.resolve("rowan-selected.json");
        NpcProfile first = profile(UUID.randomUUID(), name, "First biography");
        JsonFiles.writeAtomic(selected, first);
        NpcProfile created = editor.commit(name, false,
                Map.of(NpcProfileEditorService.ProfileFileField.PROFILE, selected));
        Path canonical = data.resolve("profiles/Rowan/Rowan.json");
        assert Files.isRegularFile(canonical);
        assert created.id().equals(scaffoldIdentity)
                : "The identity allocated by /npc create must survive template replacement";

        NpcProfile replacement = profile(UUID.randomUUID(), name, "Updated biography");
        JsonFiles.writeAtomic(selected, replacement);
        NpcProfile updated = editor.commit(name, true,
                Map.of(NpcProfileEditorService.ProfileFileField.PROFILE, selected));
        assert updated.id().equals(created.id());
        assert updated.stableId().equals(created.stableId());
        assert updated.biography().equals("Updated biography");
    }

    private static void namedProfileIsAuthoritativeWithLegacyFallback() throws Exception {
        Path data = Files.createTempDirectory("immersive-npcs-named-profile");
        Path directory = data.resolve("profiles/Mara");
        Files.createDirectories(directory);
        NpcProfile named = profile(UUID.randomUUID(), "Mara", "Named authoritative biography");
        NpcProfile legacy = profile(UUID.randomUUID(), "Mara", "Legacy generic biography");
        JsonFiles.writeAtomic(directory.resolve("Mara.json"), named);
        JsonFiles.writeAtomic(directory.resolve("profile.json"), legacy);

        ProfileRepository repository = new ProfileRepository(data);
        assert repository.load("Mara").id().equals(named.id());
        assert repository.load("Mara").biography().equals("Named authoritative biography");
        Files.delete(directory.resolve("Mara.json"));
        assert repository.load("Mara").id().equals(legacy.id());
        assert repository.load("Mara").biography().equals("Legacy generic biography");
    }

    private static void committedDialogueIsImmutableAndSharedVerbatim() {
        UUID responseId = UUID.randomUUID();
        List<CommittedDialogueResponse.CommittedChunk> sinks = new ArrayList<>();
        CommittedDialogueResponse response = new CommittedDialogueResponse(responseId, sinks::add);
        VocalState state = VocalState.infer("You're here.");
        response.commit("You're here.", state);
        response.commit("Stay awhile.", state);
        assert sinks.size() == 2;
        assert sinks.get(0).responseId().equals(responseId);
        assert sinks.get(0).chunkIndex() == 0;
        assert sinks.get(0).text().equals("You're here.");
        assert response.text().equals("You're here. Stay awhile.");
        assert TtsTextNormalizer.normalize(sinks.get(0).text()).equals("You're here.");
        assertThrows(() -> response.chunks().add(null));
        response.cancel();
        assertThrows(() -> response.commit("Too late.", state));
    }

    private static void streamedPhrasesCommitBeforeCompletion() {
        UUID responseId = UUID.randomUUID();
        List<CommittedDialogueResponse.CommittedChunk> sinks = new ArrayList<>();
        CommittedDialogueResponse response = new CommittedDialogueResponse(responseId, sinks::add);
        SpeechPhraseChunker chunker = SpeechPhraseChunker.exact(
                (index, phrase, state) -> response.commit(phrase, state));
        VocalState state = VocalState.infer("The forge is warm today.");
        chunker.accept("The forge is warm today, and the coals are burning brightly. ", state);
        assert sinks.size() == 1 : "A complete streamed phrase must commit before completion";
        assert sinks.get(0).text().equals(
                "The forge is warm today, and the coals are burning brightly.");
        chunker.accept("Hand me the tongs", state);
        assert sinks.size() == 1 : "A partial trailing phrase must remain buffered";
        int count = chunker.complete("ignored streaming fallback", state);
        assert count == 2;
        assert sinks.get(1).text().equals("Hand me the tongs");
    }

    private static void update6UiAndIdentityAreWired() throws Exception {
        String manifest = Files.readString(Path.of("src/main/resources/manifest.json"));
        String page = Files.readString(Path.of("src/main/java/com/inigmasgames/persistentnpcs/ui/NpcProfilePage.java"));
        String command = Files.readString(Path.of(
                "src/main/java/com/inigmasgames/persistentnpcs/command/AbstractImmersiveNpcProfileCommand.java"));
        String editor = Files.readString(Path.of("src/main/java/com/inigmasgames/persistentnpcs/profile/NpcProfileEditorService.java"));
        String voice = Files.readString(Path.of("src/main/java/com/inigmasgames/persistentnpcs/voice/HytaleSpatialVoiceAdapter.java"));
        assert manifest.contains("\"Name\": \"ImmersiveNPCs\"");
        assert manifest.matches("(?s).*\\\"Version\\\"\\s*:\\s*\\\"0\\.6\\.[0-9]+-R[0-9]+.*")
                : "Manifest must retain a versioned ImmersiveNPCs revision";
        assert page.contains("InteractiveCustomUIPage");
        assert page.contains("ServerFileBrowser");
        assert editor.contains("<name>.json");
        assert command.contains("getPageManager().openCustomPage");
        assert Files.readString(Path.of(
                "src/main/java/com/inigmasgames/persistentnpcs/hytale/HytaleConversationBridge.java"))
                .contains("orbis.submitText(");
        assert voice.contains("playOrbis");
        assert !voice.contains("cancelResponse");
        assert !voice.contains("activeResponseByNpc");
    }

    private static NpcProfile profile(UUID id, String name, String biography) {
        return new NpcProfile(id, name, "Village resident", "Observant", biography,
                "Respond to the world honestly.", "", "", List.of(), List.of(),
                List.of(), List.of(), 0).validated();
    }

    private static void assertThrows(Runnable action) {
        boolean threw = false;
        try {
            action.run();
        } catch (RuntimeException expected) {
            threw = true;
        }
        assert threw;
    }
}
