package com.inigmasgames.persistentnpcs;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.inigmasgames.persistentnpcs.json.JsonFiles;
import com.inigmasgames.persistentnpcs.profile.NpcProfileAuthoringService;
import com.inigmasgames.persistentnpcs.profile.NpcProfileDraft;
import com.inigmasgames.persistentnpcs.profile.NpcProfileRegistry;
import com.inigmasgames.persistentnpcs.profile.ProfileRepository;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

/** Deterministic R164 gate for category rendering and typed authoring fields. */
public final class R164ProfileEditorPolishTest {
    private R164ProfileEditorPolishTest() { }

    public static void main(String[] args) throws Exception {
        Path root = Files.createTempDirectory("r164-profile-editor-");
        try {
            ProfileRepository profiles = new ProfileRepository(root);
            profiles.createTemplate("R164Npc");
            Path path = profiles.profilePath("R164Npc");
            JsonObject raw = JsonParser.parseString(Files.readString(path)).getAsJsonObject();
            raw.addProperty("futureExtension", "preserved");
            Files.writeString(path, JsonFiles.GSON.toJson(raw));
            NpcProfileRegistry registry = new NpcProfileRegistry(profiles);
            registry.load();
            NpcProfileAuthoringService authoring = new NpcProfileAuthoringService(
                    profiles, registry, ignored -> { });

            NpcProfileDraft draft = authoring.begin("R164Npc", UUID.randomUUID(), 1);
            draft.update(NpcProfileDraft.Field.SUMMARY,
                    "A concise creator-authored summary.");
            draft.update(NpcProfileDraft.Field.CREATOR_NOTES,
                    "Private staging note; never cognition input.");
            var saved = authoring.save(draft, UUID.randomUUID());
            assert saved.profile().summary().equals("A concise creator-authored summary.");
            assert saved.profile().creatorNotes().equals(
                    "Private staging note; never cognition input.");
            assert saved.profile().schemaVersion() == 1
                    : "Existing profile schema version remains backward compatible";
            JsonObject committed = JsonParser.parseString(Files.readString(path)).getAsJsonObject();
            assert committed.get("futureExtension").getAsString().equals("preserved");

            boolean rejected = false;
            try { draft.update(NpcProfileDraft.Field.SUMMARY, "x".repeat(501)); }
            catch (IllegalArgumentException expected) { rejected = true; }
            assert rejected : "Summary must be rejected above 500 characters";

            String master = Files.readString(Path.of(
                    "src/main/resources/Common/UI/Custom/Pages/ImmersiveNpcProfile.ui"));
            assert master.contains("#ProfileForm") && master.contains("#ProfileCategoryBasicInfo");
            assert !master.contains("GENERATE PROPOSAL")
                    : "The old always-visible proposal panel must be gone";
            for (String fragment : new String[] { "BasicInfo", "Background", "Personality",
                    "ValuesBeliefs", "Motivations", "Relationships", "SpeechStyle", "Notes" }) {
                assert Files.isRegularFile(Path.of("src/main/resources/Common/UI/Custom/Pages/"
                        + "ProfileEditor/" + fragment + ".ui"));
            }
            String basic = Files.readString(Path.of(
                    "src/main/resources/Common/UI/Custom/Pages/ProfileEditor/BasicInfo.ui"));
            assert basic.contains("MaxLength: 500") && basic.contains("#ProfileSummaryCounter");
            String page = Files.readString(Path.of(
                    "src/main/java/com/inigmasgames/persistentnpcs/ui/NpcProfilePage.java"));
            assert page.contains("Pages/ProfileEditor/AllSections.ui")
                    && !page.contains("commands.clear(\"#ProfileForm\")")
                    && page.contains("profileCategory = ProfileCategory.BACKGROUND");
            String generation = Files.readString(Path.of(
                    "src/main/java/com/inigmasgames/persistentnpcs/profile/"
                            + "NpcProfileGenerationService.java"));
            assert generation.contains("field == NpcProfileDraft.Field.CREATOR_NOTES")
                    : "Creator notes must never enter generation input";
            System.out.println("R164 Profile Editor polish gate passed.");
        } finally {
            try (var paths = Files.walk(root)) {
                for (Path path : paths.sorted(java.util.Comparator.reverseOrder()).toList()) {
                    Files.deleteIfExists(path);
                }
            }
        }
    }
}
