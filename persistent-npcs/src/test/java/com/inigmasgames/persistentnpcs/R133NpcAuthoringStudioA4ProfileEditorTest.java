package com.inigmasgames.persistentnpcs;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.inigmasgames.persistentnpcs.json.JsonFiles;
import com.inigmasgames.persistentnpcs.profile.NpcProfileAuthoringService;
import com.inigmasgames.persistentnpcs.profile.NpcProfileDraft;
import com.inigmasgames.persistentnpcs.profile.NpcProfileRegistry;
import com.inigmasgames.persistentnpcs.profile.ProfileRepository;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

/** Deterministic A4 gate: safe drafts, atomic commits, conflicts, and proposal-only AI. */
public final class R133NpcAuthoringStudioA4ProfileEditorTest {
    private R133NpcAuthoringStudioA4ProfileEditorTest() { }

    public static void main(String[] args) throws Exception {
        Path root = Files.createTempDirectory("r133-profile-editor-");
        try {
            ProfileRepository profiles = new ProfileRepository(root);
            profiles.createTemplate("A4TestNpc");
            Path path = profiles.profilePath("A4TestNpc");
            JsonObject source = JsonParser.parseString(Files.readString(path)).getAsJsonObject();
            JsonObject extension = new JsonObject();
            extension.addProperty("futureSchemaValue", "must-survive");
            source.add("unknownExtension", extension);
            Files.writeString(path, JsonFiles.GSON.toJson(source), StandardCharsets.UTF_8);

            NpcProfileRegistry registry = new NpcProfileRegistry(profiles);
            registry.load();
            NpcProfileAuthoringService service = new NpcProfileAuthoringService(
                    profiles, registry, System.out::println);

            System.out.println("R133 stage=draft-identity");
            NpcProfileDraft draft = service.begin("A4TestNpc", UUID.randomUUID(), 7);
            UUID stableId = draft.stableNpcId();
            assert !draft.dirty();
            draft.update(NpcProfileDraft.Field.BIOGRAPHY,
                    "A carefully reviewed authored biography.");
            draft.update(NpcProfileDraft.Field.VALUES, "honesty\npatience,craft");
            assert draft.dirtyFields().contains(NpcProfileDraft.Field.BIOGRAPHY);

            System.out.println("R133 stage=atomic-unknown-preservation");
            var saved = service.save(draft, UUID.randomUUID());
            assert saved.profile().stableId().equals(stableId);
            assert saved.profile().biography().equals("A carefully reviewed authored biography.");
            assert saved.profile().values().equals(java.util.List.of("honesty", "patience", "craft"));
            JsonObject committed = JsonParser.parseString(Files.readString(path)).getAsJsonObject();
            assert committed.getAsJsonObject("unknownExtension")
                    .get("futureSchemaValue").getAsString().equals("must-survive")
                    : "Unknown JSON fields must survive safe tree patching";
            assert Files.isRegularFile(saved.rollbackPath());
            assert Files.isRegularFile(path.resolveSibling("profile-authoring-audit.jsonl"));
            assert Files.isRegularFile(path.resolveSibling("profile-authoring-revision.json"));

            System.out.println("R133 stage=optimistic-conflict");
            NpcProfileDraft stale = service.begin("A4TestNpc", UUID.randomUUID(), 8);
            JsonObject external = JsonParser.parseString(Files.readString(path)).getAsJsonObject();
            external.addProperty("externalWriterMarker", true);
            Files.writeString(path, JsonFiles.GSON.toJson(external), StandardCharsets.UTF_8);
            stale.update(NpcProfileDraft.Field.ROLE, "Conflicting role");
            boolean rejected = false;
            try { service.save(stale, UUID.randomUUID()); }
            catch (NpcProfileAuthoringService.RevisionConflictException expected) {
                rejected = true;
            }
            assert rejected : "Stale drafts must never overwrite a concurrent writer";
            assert JsonParser.parseString(Files.readString(path)).getAsJsonObject()
                    .get("externalWriterMarker").getAsBoolean();

            System.out.println("R133 stage=ui-and-generation-contract");
            String ui = source("src/main/resources/Common/UI/Custom/Pages/ImmersiveNpcProfile.ui");
            String page = source("src/main/java/com/inigmasgames/persistentnpcs/ui/NpcProfilePage.java");
            String generation = source("src/main/java/com/inigmasgames/persistentnpcs/profile/"
                    + "NpcProfileGenerationService.java");
            assert ui.contains("#ProfileEditorPage") && ui.contains("#ProfileSaveButton");
            assert !ui.contains("HorizontalAlignment: Right")
                    : "Current client LabelAlignment uses End, not Right";
            int generate = ui.indexOf("#ProfileGenerateButton");
            int reset = ui.indexOf("#ProfileResetButton");
            int cancel = ui.indexOf("#ProfileCancelButton");
            int save = ui.indexOf("#ProfileSaveButton");
            assert generate < reset && reset < cancel && cancel < save
                    : "Required A4 action order is Generate, Reset, Cancel, Save Profile";
            assert page.contains("NpcAuthoringPermissions.GENERATE");
            assert page.contains("NPC_PROFILE_GENERATION_STALE_REJECTED");
            assert generation.contains("ResourcePriority.LOW")
                    && generation.contains("PROPOSAL_ONLY")
                    && generation.contains("ReasoningMode.DISABLED");
            assert generation.contains("Never create memories, beliefs, relationships, tasks")
                    : "Generated content must remain inside the A4 profile allowlist";
            assert !generation.contains("editor.authoring().save")
                    : "Generation service must not commit canon";
            System.out.println("R133 NPC Authoring Studio A4 profile-editor gate passed.");
        } finally {
            deleteTree(root);
        }
    }

    private static String source(String path) throws Exception {
        return Files.readString(Path.of(path));
    }

    private static void deleteTree(Path root) throws Exception {
        if (!Files.exists(root)) return;
        try (var paths = Files.walk(root)) {
            for (Path path : paths.sorted(java.util.Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(path);
            }
        }
    }
}
