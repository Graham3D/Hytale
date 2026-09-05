package com.inigmasgames.persistentnpcs.profile;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.inigmasgames.persistentnpcs.json.JsonFiles;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Permanent gate for the R167 identity and single-form Profile Editor repair. */
public final class R167StableIdentityAndProfileEditorTest {
    private R167StableIdentityAndProfileEditorTest() { }

    public static void main(String[] args) throws Exception {
        Path root = Files.createTempDirectory("r167-profile-editor-");
        try {
            stableInventoryOwnerMigratesOnlyFromNull(root.resolve("identity"));
            submittedValuesAndGeneratedDraftPersistAtSaveOnly(root.resolve("profile"));
            mountedUiAndEventContractAreSafe();
            System.out.println("R167 PASS: legacy null storage identity migrates safely, conflicts fail closed, all profile values bind at Save, navigation is UI-only, and generated biography remains draft-only until Save.");
        } finally {
            try (var paths = Files.walk(root)) {
                for (Path path : paths.sorted(java.util.Comparator.reverseOrder()).toList()) {
                    Files.deleteIfExists(path);
                }
            }
        }
    }

    private static void stableInventoryOwnerMigratesOnlyFromNull(Path root) {
        ProfileRepository profiles = new ProfileRepository(root);
        NpcProfile mara = profiles.createTemplate("Mara");
        try (NpcInventoryRepository inventories = new NpcInventoryRepository(profiles)) {
            inventories.save("Mara", NpcInventoryState.empty());
            NpcInventoryState migrated = inventories.loadForProfile(
                    "Mara", mara.stableId(), ignored -> { });
            assert mara.stableId().equals(migrated.stableNpcId());
            assert mara.stableId().equals(inventories.load("Mara").stableNpcId());

            UUID foreign = UUID.randomUUID();
            inventories.save("Mara", NpcInventoryState.empty().withStableNpcId(foreign));
            boolean refused = false;
            try {
                inventories.loadForProfile("Mara", mara.stableId(), ignored -> { });
            } catch (IllegalStateException expected) {
                refused = expected.getMessage().contains("NPC_STABLE_PROFILE_ID_MISMATCH");
            }
            assert refused : "A non-null conflicting storage owner must fail closed";
            assert foreign.equals(inventories.load("Mara").stableNpcId())
                    : "Conflict handling must not rewrite the persisted owner";
        }
    }

    private static void submittedValuesAndGeneratedDraftPersistAtSaveOnly(Path root)
            throws Exception {
        ProfileRepository profiles = new ProfileRepository(root);
        profiles.createTemplate("Hoit");
        NpcProfileRegistry registry = new NpcProfileRegistry(profiles);
        registry.load();
        NpcProfileAuthoringService authoring = new NpcProfileAuthoringService(
                profiles, registry, ignored -> { });
        UUID session = UUID.randomUUID();
        NpcProfileDraft draft = authoring.begin("Hoit", session, 1);
        for (NpcProfileDraft.Field field : NpcProfileDraft.Field.values()) {
            draft.update(field, field.list()
                    ? "submitted " + field.jsonName() + " one, submitted "
                            + field.jsonName() + " two"
                    : "submitted " + field.jsonName());
        }
        authoring.save(draft, UUID.randomUUID());
        String savedText = Files.readString(profiles.profilePath("Hoit"));
        assert !savedText.contains("#Profile") && !savedText.contains("Input.Value");
        JsonObject saved = JsonParser.parseString(savedText).getAsJsonObject();
        for (NpcProfileDraft.Field field : NpcProfileDraft.Field.values()) {
            if (field.list()) {
                assert saved.getAsJsonArray(field.jsonName()).size() == 2
                        : field + " must persist both submitted list values";
                assert saved.getAsJsonArray(field.jsonName()).get(0).getAsString()
                        .equals("submitted " + field.jsonName() + " one")
                        : field + " first value changed";
                assert saved.getAsJsonArray(field.jsonName()).get(1).getAsString()
                        .equals("submitted " + field.jsonName() + " two")
                        : field + " second value changed";
            } else {
                assert saved.get(field.jsonName()).getAsString()
                        .equals("submitted " + field.jsonName())
                        : field + " value changed";
            }
        }

        NpcProfileDraft generated = authoring.begin("Hoit", session, 2);
        String beforeGenerationSave = Files.readString(profiles.profilePath("Hoit"));
        generated.setProposal(new NpcProfileDraft.Proposal(UUID.randomUUID(), "BIOGRAPHY",
                "test", "test", Instant.now(),
                Map.of(NpcProfileDraft.Field.BIOGRAPHY,
                        "Hoit grew up near Sandsdeep."), java.util.List.of()));
        generated.acceptProposal(Set.of(NpcProfileDraft.Field.BIOGRAPHY));
        assert generated.value(NpcProfileDraft.Field.BIOGRAPHY)
                .equals("Hoit grew up near Sandsdeep.");
        assert Files.readString(profiles.profilePath("Hoit")).equals(beforeGenerationSave)
                : "Generated text must remain draft-only";
        authoring.save(generated, UUID.randomUUID());
        assert JsonFiles.read(profiles.profilePath("Hoit"), NpcProfile.class).biography()
                .equals("Hoit grew up near Sandsdeep.");

        JsonObject corrupt = JsonParser.parseString(
                Files.readString(profiles.profilePath("Hoit"))).getAsJsonObject();
        corrupt.addProperty("role", "#ProfileRoleInput.Value");
        Files.writeString(profiles.profilePath("Hoit"), JsonFiles.GSON.toJson(corrupt));
        registry.load();
        NpcProfileDraft repairable = authoring.begin("Hoit", UUID.randomUUID(), 3);
        assert repairable.value(NpcProfileDraft.Field.ROLE).isEmpty();
        boolean selectorRejected = false;
        try {
            repairable.update(NpcProfileDraft.Field.ROLE, "#ProfileRoleInput.Value");
        } catch (IllegalArgumentException expected) {
            selectorRejected = true;
        }
        assert selectorRejected;
    }

    private static void mountedUiAndEventContractAreSafe() throws Exception {
        String master = Files.readString(Path.of(
                "src/main/resources/Common/UI/Custom/Pages/ImmersiveNpcProfile.ui"));
        String all = Files.readString(Path.of(
                "src/main/resources/Common/UI/Custom/Pages/ProfileEditor/AllSections.ui"));
        String page = Files.readString(Path.of(
                "src/main/java/com/inigmasgames/persistentnpcs/ui/NpcProfilePage.java"));
        assert master.contains("LayoutMode: TopScrolling")
                && master.contains("KeepScrollPosition: true");
        for (String section : new String[] { "SectionBasicInfo", "SectionBackground",
                "SectionPersonality", "SectionValues", "SectionMotivations",
                "SectionRelationships", "SectionSpeechStyle", "SectionNotes" }) {
            assert all.contains("#" + section) : section + " must be mounted";
        }
        assert page.contains("authoringEvent(\"PROFILE_SECTION\")")
                && page.contains(".append(\"@ProfileSection\", category.name())");
        assert !page.contains("authoringEvent(\"PROFILE_CATEGORY\")")
                && !page.contains("commands.clear(\"#ProfileForm\")");
        assert page.contains(".append(\"@ProfileFieldValue\", selector + \".Value\")");
        for (String field : new String[] { "Role", "SelfIdentity", "Species", "Age",
                "Home", "Summary", "Workplace", "Personality", "Traits", "Values",
                "Likes", "Dislikes", "Fears", "Biography", "Purpose", "Goals",
                "Speaking", "Knowledge", "Notes" }) {
            assert page.contains(".append(\"@Profile" + field + "\",")
                    : field + " must be captured on Save";
        }
        assert !all.contains("Apply to Draft") && !all.contains("Discard Proposal");
        assert page.contains("profileDraft.acceptProposal(Set.of(NpcProfileDraft.Field.BIOGRAPHY))");
    }
}
