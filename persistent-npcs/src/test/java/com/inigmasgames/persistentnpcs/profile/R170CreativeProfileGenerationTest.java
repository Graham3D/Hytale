package com.inigmasgames.persistentnpcs.profile;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.inigmasgames.persistentnpcs.llm.LlmProvider;
import com.inigmasgames.persistentnpcs.llm.LlmProviderStatus;
import com.inigmasgames.persistentnpcs.llm.LlmRequest;
import com.inigmasgames.persistentnpcs.llm.LlmResult;
import com.inigmasgames.persistentnpcs.llm.PinnedLlmProvider;
import com.inigmasgames.persistentnpcs.profile.NpcProfileGenerationService.GeneratedProfilePatch;
import com.inigmasgames.persistentnpcs.profile.NpcProfileGenerationService.Request;
import com.inigmasgames.persistentnpcs.profile.NpcProfileGenerationService.Scope;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/** Deterministic gate for creative, full-profile, draft-only generation. */
public final class R170CreativeProfileGenerationTest {
    private R170CreativeProfileGenerationTest() { }

    public static void main(String[] arguments) throws Exception {
        Path root = Files.createTempDirectory("r170-full-profile-");
        try {
            ProfileRepository profiles = new ProfileRepository(root);
            profiles.createTemplate("Tarin");
            profiles.createTemplate("Archivist");
            NpcProfileRegistry registry = new NpcProfileRegistry(profiles);
            registry.load();
            NpcProfileAuthoringService authoring = new NpcProfileAuthoringService(
                    profiles, registry, ignored -> { });

            enrichSettlementCanon(authoring);
            registry.load();
            fullProfileDraftFlow(profiles, registry, authoring);
            malformedAndStalePatchesAreAtomic(profiles, authoring);
            uiAndLifecycleContract();
            System.out.println("R170 PASS: full-profile creative patch, bounded lore, preservation, validation, stale rejection, and Save-only canon promotion.");
        } finally {
            try (var paths = Files.walk(root)) {
                for (Path path : paths.sorted(java.util.Comparator.reverseOrder()).toList()) {
                    Files.deleteIfExists(path);
                }
            }
        }
    }

    private static void enrichSettlementCanon(NpcProfileAuthoringService authoring) {
        NpcProfileDraft lore = authoring.begin("Archivist", UUID.randomUUID(), 1);
        lore.update(NpcProfileDraft.Field.ROLE, "Keeper of Sandsdeep's tide records");
        lore.update(NpcProfileDraft.Field.SPECIES_ARCHETYPE, "HUMAN");
        lore.update(NpcProfileDraft.Field.AGE_CATEGORY, "ELDER");
        lore.update(NpcProfileDraft.Field.HOME, "Sandsdeep");
        lore.update(NpcProfileDraft.Field.SUMMARY,
                "Maintains the settlement's flood ledgers and oral histories.");
        lore.update(NpcProfileDraft.Field.BIOGRAPHY,
                "The Archivist records monsoon marks beneath Sandsdeep's old cistern.");
        authoring.save(lore, UUID.randomUUID());
    }

    private static void fullProfileDraftFlow(ProfileRepository profiles,
            NpcProfileRegistry registry, NpcProfileAuthoringService authoring) throws Exception {
        UUID session = UUID.randomUUID();
        NpcProfileDraft draft = authoring.begin("Tarin", session, 7);
        draft.update(NpcProfileDraft.Field.ROLE, "Caravan mapmaker");
        draft.update(NpcProfileDraft.Field.SPECIES_ARCHETYPE, "HUMAN");
        draft.update(NpcProfileDraft.Field.AGE_CATEGORY, "ADULT");
        draft.update(NpcProfileDraft.Field.HOME, "Sandsdeep");
        draft.update(NpcProfileDraft.Field.SUMMARY,
                "Charts safe passages for traders crossing the salt flats.");
        draft.update(NpcProfileDraft.Field.LIKES, "carefully annotated maps");
        String creatorRole = draft.value(NpcProfileDraft.Field.ROLE);
        String creatorSummary = draft.value(NpcProfileDraft.Field.SUMMARY);
        String manualLikes = draft.value(NpcProfileDraft.Field.LIKES);
        String authoritativeBefore = Files.readString(profiles.profilePath("Tarin"));

        Optional<NpcProfileAuthoringLore.WorldLorePacket> lore =
                NpcProfileAuthoringLore.relevantTo(draft, registry.profiles());
        assert lore.isPresent() && lore.get().entries().size() == 1
                : "Only the relevant same-settlement authored NPC should be included";
        assert lore.get().promptText().contains("flood ledgers")
                || lore.get().promptText().contains("monsoon marks");

        Request request = request(draft, session, 44, lore);
        Set<NpcProfileDraft.Field> allowed = NpcProfileGenerationService.allowedFields(request);
        assert request.scope() == Scope.FILL_MISSING_ALLOWED_FIELDS;
        assert allowed.size() >= 10 : "A minimally authored profile needs a multi-section patch";
        for (NpcProfileDraft.Field field : List.of(NpcProfileDraft.Field.BIOGRAPHY,
                NpcProfileDraft.Field.PERSONALITY,
                NpcProfileDraft.Field.PERSONALITY_TRAITS,
                NpcProfileDraft.Field.VALUES, NpcProfileDraft.Field.DISLIKES,
                NpcProfileDraft.Field.FEARS, NpcProfileDraft.Field.GOALS,
                NpcProfileDraft.Field.SPEAKING_STYLE,
                NpcProfileDraft.Field.KNOWLEDGE_DOMAINS)) {
            assert allowed.contains(field) : field + " must be generated";
        }
        assert !allowed.contains(NpcProfileDraft.Field.LIKES)
                : "Manually dirty fields must win";
        for (NpcProfileDraft.Field fixed : List.of(NpcProfileDraft.Field.ROLE,
                NpcProfileDraft.Field.SPECIES_ARCHETYPE,
                NpcProfileDraft.Field.AGE_CATEGORY, NpcProfileDraft.Field.HOME,
                NpcProfileDraft.Field.SUMMARY, NpcProfileDraft.Field.CREATOR_NOTES)) {
            assert !allowed.contains(fixed) : "Creator/system field escaped allowlist: " + fixed;
        }

        String prompt = NpcProfileGenerationService.authoringInput(request, allowed);
        assert prompt.contains("FIXED CREATOR CANON") && prompt.contains("Caravan mapmaker")
                && prompt.contains("Sandsdeep") && prompt.contains("BOUNDED APPROVED LORE");
        assert !prompt.contains("CREATOR_NOTES");
        JsonObject schema = NpcProfileGenerationService.responseFormat(allowed);
        JsonObject changesSchema = schema.getAsJsonObject("json_schema")
                .getAsJsonObject("schema").getAsJsonObject("properties")
                .getAsJsonObject("changes");
        assert changesSchema.get("minItems").getAsInt() == allowed.size();
        assert changesSchema.get("maxItems").getAsInt() == allowed.size();

        GeneratedProfilePatch patch = parse(request, allowed, richPatch(allowed));
        assert patch.allowedChanges().size() == allowed.size();
        draft.acceptGeneratedPatch(patch);
        assert draft.value(NpcProfileDraft.Field.BIOGRAPHY).contains("salt-road");
        assert draft.value(NpcProfileDraft.Field.PERSONALITY).toLowerCase()
                .contains("methodical");
        assert draft.value(NpcProfileDraft.Field.LIKES).equals(manualLikes);
        assert draft.value(NpcProfileDraft.Field.ROLE).equals(creatorRole);
        assert draft.value(NpcProfileDraft.Field.SUMMARY).equals(creatorSummary);
        assert Files.readString(profiles.profilePath("Tarin")).equals(authoritativeBefore)
                : "Unsaved generated content must never enter the authoritative profile";

        authoring.save(draft, UUID.randomUUID());
        NpcProfile saved = profiles.load("Tarin");
        assert saved.biography().contains("salt-road");
        assert saved.personalityTraits().size() >= 2;
        assert saved.values().size() >= 2;
        assert saved.likes().equals(List.of("carefully annotated maps"));
        assert saved.role().equals(creatorRole) && saved.summary().equals(creatorSummary);

        NpcProfileRegistry restarted = new NpcProfileRegistry(profiles);
        restarted.load();
        assert restarted.requireName("Tarin").goals().contains("publish a reliable atlas")
                : "Saved generated canon must survive registry restart";

        NpcProfileDraft sparse = authoring.begin("Tarin", UUID.randomUUID(), 8);
        sparse.update(NpcProfileDraft.Field.HOME, "A place with no supporting authored lore");
        assert NpcProfileAuthoringLore.relevantTo(sparse, List.of()).isEmpty();
        Request sparseRequest = request(sparse, sparse.sessionId(), 45, Optional.empty());
        String sparsePrompt = NpcProfileGenerationService.authoringInput(
                sparseRequest, Set.of(NpcProfileDraft.Field.WORKPLACE));
        assert sparsePrompt.contains("Creatively fill character-level gaps")
                : "Sparse lore must enable creativity, not empty output";
    }

    private static void malformedAndStalePatchesAreAtomic(ProfileRepository profiles,
            NpcProfileAuthoringService authoring) {
        NpcProfileDraft draft = authoring.begin("Tarin", UUID.randomUUID(), 9);
        draft.update(NpcProfileDraft.Field.WORKPLACE, "");
        Request request = request(draft, draft.sessionId(), 46, Optional.empty());
        Set<NpcProfileDraft.Field> allowed = NpcProfileGenerationService.allowedFields(request);
        String before = draft.draftHash();

        boolean malformedRejected = false;
        try {
            NpcProfileGenerationService.parsePatch(UUID.randomUUID(), request, pinned(),
                    "{\"changes\":[],\"warnings\":[],\"inventory\":\"forbidden\"}",
                    allowed);
        } catch (IllegalArgumentException expected) {
            malformedRejected = true;
        }
        assert malformedRejected && draft.draftHash().equals(before)
                : "Malformed/prohibited output must leave the draft intact";

        GeneratedProfilePatch patch = parse(request, allowed, richPatch(allowed));
        draft.update(NpcProfileDraft.Field.WORKPLACE, "Creator changed this after generation");
        String changed = draft.draftHash();
        boolean staleRejected = false;
        try { draft.acceptGeneratedPatch(patch); }
        catch (IllegalArgumentException expected) { staleRejected = true; }
        assert staleRejected && draft.draftHash().equals(changed)
                : "A stale patch must be rejected atomically";
    }

    private static void uiAndLifecycleContract() throws Exception {
        String page = Files.readString(Path.of(
                "src/main/java/com/inigmasgames/persistentnpcs/ui/NpcProfilePage.java"));
        String generation = Files.readString(Path.of(
                "src/main/java/com/inigmasgames/persistentnpcs/profile/NpcProfileGenerationService.java"));
        String ui = Files.readString(Path.of(
                "src/main/resources/Common/UI/Custom/Pages/ProfileEditor/AllSections.ui"));
        assert page.contains("Scope.FILL_MISSING_ALLOWED_FIELDS")
                && page.contains("authoringSession.pageGeneration() != expectedPageGeneration")
                && page.contains("profileDraft.acceptGeneratedPatch(proposal)");
        assert page.contains("profileDraft.baseRevision() != expectedBaseRevision")
                && page.contains("profileDraft.stableNpcId().equals(expectedStableId)");
        assert generation.contains("ResourcePriority.LOW")
                && generation.contains("CREATIVE_CANON_DRAFT")
                && generation.contains("0.72")
                && !generation.contains("editor.authoring().save")
                && !generation.contains("ProfileRepository");
        assert ui.contains("Text: \"Generate Profile\"")
                && !ui.contains("Generate Biography");
    }

    private static Request request(NpcProfileDraft draft, UUID session,
            long pageGeneration,
            Optional<NpcProfileAuthoringLore.WorldLorePacket> lore) {
        return new Request(session, pageGeneration, draft.editorGeneration(),
                draft.baseRevision(), draft.draftHash(), draft.stableNpcId(),
                UUID.randomUUID(), Scope.FILL_MISSING_ALLOWED_FIELDS,
                draft.dirtyFields(), draft, lore,
                NpcProfileGenerationService.PATCH_SCHEMA_VERSION);
    }

    private static GeneratedProfilePatch parse(Request request,
            Set<NpcProfileDraft.Field> allowed, String json) {
        return NpcProfileGenerationService.parsePatch(UUID.randomUUID(), request,
                pinned(), json, allowed);
    }

    private static String richPatch(Set<NpcProfileDraft.Field> allowed) {
        Map<NpcProfileDraft.Field, String> values = Map.ofEntries(
                Map.entry(NpcProfileDraft.Field.SELF_IDENTITY,
                        "A patient surveyor who measures worth by dependable routes."),
                Map.entry(NpcProfileDraft.Field.WORKPLACE, "Sandsdeep caravan court"),
                Map.entry(NpcProfileDraft.Field.PERSONALITY,
                        "Methodical, quietly daring, observant, and wry under pressure."),
                Map.entry(NpcProfileDraft.Field.PERSONALITY_TRAITS,
                        "methodical, observant, quietly daring, dry-witted"),
                Map.entry(NpcProfileDraft.Field.BIOGRAPHY,
                        "Tarin learned the salt-road by following old cistern markers and now charts safer passages for Sandsdeep's caravans."),
                Map.entry(NpcProfileDraft.Field.VALUES,
                        "precision, keeping promises, practical generosity"),
                Map.entry(NpcProfileDraft.Field.LIKES,
                        "carefully annotated maps, cool dawns, travelers' riddles"),
                Map.entry(NpcProfileDraft.Field.DISLIKES,
                        "careless directions, boastful guides, wasted water"),
                Map.entry(NpcProfileDraft.Field.FEARS,
                        "leading a caravan astray, losing irreplaceable field notes"),
                Map.entry(NpcProfileDraft.Field.PURPOSE,
                        "Make dangerous routes understandable and survivable."),
                Map.entry(NpcProfileDraft.Field.GOALS,
                        "publish a reliable atlas, map the buried cistern road"),
                Map.entry(NpcProfileDraft.Field.SPEAKING_STYLE,
                        "Measured and concrete, using bearings and terrain metaphors with dry humor."),
                Map.entry(NpcProfileDraft.Field.KNOWLEDGE_DOMAINS,
                        "cartography, salt-flat navigation, caravan customs, Sandsdeep waterworks"));
        JsonArray changes = new JsonArray();
        allowed.stream().sorted().forEach(field -> {
            JsonObject change = new JsonObject();
            change.addProperty("field", field.name());
            change.addProperty("value", values.get(field));
            changes.add(change);
        });
        JsonObject root = new JsonObject();
        root.add("changes", changes);
        root.add("warnings", new JsonArray());
        return root.toString();
    }

    private static PinnedLlmProvider pinned() {
        return new PinnedLlmProvider("NEMOTRON", "test-nemotron", "local", new LlmProvider() {
            @Override public CompletableFuture<LlmResult> generateResponse(LlmRequest request) {
                return CompletableFuture.failedFuture(new AssertionError("not called"));
            }
            @Override public CompletableFuture<LlmProviderStatus> checkStatus() {
                return CompletableFuture.completedFuture(new LlmProviderStatus(
                        "local", "test-nemotron", true, true, true, "available"));
            }
            @Override public String description() { return "deterministic-test"; }
        });
    }
}
