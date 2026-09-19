package com.inigmasgames.persistentnpcs.profile;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.inigmasgames.persistentnpcs.llm.ChatMessage;
import com.inigmasgames.persistentnpcs.llm.LlmExecutionPolicy;
import com.inigmasgames.persistentnpcs.llm.LlmRequest;
import com.inigmasgames.persistentnpcs.llm.PinnedLlmProvider;
import com.inigmasgames.persistentnpcs.orbis.OrbisResourceRequest;
import com.inigmasgames.persistentnpcs.orbis.OrbisResourceScheduler;
import com.inigmasgames.persistentnpcs.orbis.ResourcePriority;
import com.inigmasgames.persistentnpcs.orbis.ResourceWorkload;
import com.inigmasgames.persistentnpcs.profile.NpcProfileAuthoringLore.WorldLorePacket;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.regex.Pattern;

/** Low-priority creative authoring that can only produce a reviewable in-memory patch. */
public final class NpcProfileGenerationService {
    public static final String PATCH_SCHEMA_VERSION = "profile-authoring-patch-v1";
    private static final int OUTPUT_TOKEN_BUDGET = 2_200;
    private static final Set<NpcProfileDraft.Field> FULL_PROFILE_ALLOWLIST = Set.copyOf(
            EnumSet.of(NpcProfileDraft.Field.SELF_IDENTITY,
                    NpcProfileDraft.Field.WORKPLACE,
                    NpcProfileDraft.Field.PERSONALITY,
                    NpcProfileDraft.Field.PERSONALITY_TRAITS,
                    NpcProfileDraft.Field.BIOGRAPHY,
                    NpcProfileDraft.Field.VALUES,
                    NpcProfileDraft.Field.LIKES,
                    NpcProfileDraft.Field.DISLIKES,
                    NpcProfileDraft.Field.FEARS,
                    NpcProfileDraft.Field.PURPOSE,
                    NpcProfileDraft.Field.GOALS,
                    NpcProfileDraft.Field.SPEAKING_STYLE,
                    NpcProfileDraft.Field.KNOWLEDGE_DOMAINS));
    private static final Pattern LEAKAGE = Pattern.compile(
            "(?is)(system\\s+prompt|developer\\s+message|ignore\\s+(all\\s+)?previous|"
                    + "api[_ -]?key|bearer\\s+token|password\\s*=|<\\|(?:system|assistant|user)|"
                    + "```(?:json|system)|[a-z]:\\\\(?:users|windows|program files)\\\\|"
                    + "/(?:home|etc|var|server)/)");

    public enum Scope {
        BIOGRAPHY(Set.of(NpcProfileDraft.Field.BIOGRAPHY)),
        PERSONALITY_VALUES(Set.of(NpcProfileDraft.Field.PERSONALITY,
                NpcProfileDraft.Field.PERSONALITY_TRAITS, NpcProfileDraft.Field.VALUES)),
        MOTIVATIONS_GOALS(Set.of(NpcProfileDraft.Field.PURPOSE,
                NpcProfileDraft.Field.GOALS)),
        SPEECH_MANNERISMS(Set.of(NpcProfileDraft.Field.SPEAKING_STYLE)),
        FILL_MISSING_ALLOWED_FIELDS(FULL_PROFILE_ALLOWLIST),
        /** Compatibility name retained for older callers; behavior is identical. */
        FILL_MISSING(FULL_PROFILE_ALLOWLIST),
        REFINE_SELECTED(FULL_PROFILE_ALLOWLIST);

        private final Set<NpcProfileDraft.Field> allowed;
        Scope(Set<NpcProfileDraft.Field> allowed) { this.allowed = Set.copyOf(allowed); }
        public Set<NpcProfileDraft.Field> allowed() { return allowed; }

        public static Scope parse(String value) {
            try { return valueOf(value == null ? "" : value.strip().toUpperCase(Locale.ROOT)); }
            catch (IllegalArgumentException invalid) {
                throw new IllegalArgumentException("Choose a valid generation scope.");
            }
        }
    }

    /** Immutable identity envelope for one coherent profile-authoring request. */
    public record Request(UUID sessionId, long pageGeneration, long editorGeneration,
            long baseRevision, String draftHash, UUID stableNpcId, UUID playerId, Scope scope,
            Set<NpcProfileDraft.Field> selectedFields, NpcProfileDraft draft,
            Optional<WorldLorePacket> approvedLore, String schemaVersion) {
        public Request {
            selectedFields = Set.copyOf(selectedFields == null ? Set.of() : selectedFields);
            approvedLore = approvedLore == null ? Optional.empty() : approvedLore;
            schemaVersion = schemaVersion == null || schemaVersion.isBlank()
                    ? PATCH_SCHEMA_VERSION : schemaVersion;
        }

        /** Compatibility constructor for the original A4 request envelope. */
        public Request(UUID sessionId, long editorGeneration, long baseRevision,
                String draftHash, UUID stableNpcId, UUID playerId, Scope scope,
                Set<NpcProfileDraft.Field> selectedFields, NpcProfileDraft draft) {
            this(sessionId, 0, editorGeneration, baseRevision, draftHash, stableNpcId,
                    playerId, scope, selectedFields, draft, Optional.empty(),
                    PATCH_SCHEMA_VERSION);
        }
    }

    public record ProviderGenerationMetadata(String provider, String model,
            Instant createdAt) { }

    /** Validated typed output with no repository or canon mutation capability. */
    public record GeneratedProfilePatch(UUID requestId, UUID npcStableId,
            long baseProfileRevision, String sourceDraftHash,
            Map<NpcProfileDraft.Field, String> allowedChanges, List<String> warnings,
            ProviderGenerationMetadata metadata, String schemaVersion) {
        public GeneratedProfilePatch {
            allowedChanges = Map.copyOf(allowedChanges == null ? Map.of() : allowedChanges);
            warnings = List.copyOf(warnings == null ? List.of() : warnings);
        }
    }

    public final class Handle implements AutoCloseable {
        private final UUID requestId;
        private final CompletableFuture<GeneratedProfilePatch> future;
        private Handle(UUID requestId, CompletableFuture<GeneratedProfilePatch> future) {
            this.requestId = requestId;
            this.future = future;
        }
        public UUID requestId() { return requestId; }
        public CompletableFuture<GeneratedProfilePatch> future() { return future; }
        @Override public void close() {
            scheduler.cancel(requestId, "profile-editor-closed");
            future.cancel(true);
        }
    }

    private final Supplier<PinnedLlmProvider> providerSupplier;
    private final OrbisResourceScheduler scheduler;
    private final Consumer<String> diagnostics;

    public NpcProfileGenerationService(Supplier<PinnedLlmProvider> providerSupplier,
            OrbisResourceScheduler scheduler, Consumer<String> diagnostics) {
        this.providerSupplier = providerSupplier;
        this.scheduler = scheduler;
        this.diagnostics = diagnostics == null ? ignored -> { } : diagnostics;
    }

    public boolean available() { return providerSupplier != null && scheduler != null; }

    public Handle generate(Request request) {
        if (!available()) throw new IllegalStateException(
                "Generation provider is unavailable; manual editing remains available.");
        validateRequest(request);
        UUID requestId = UUID.randomUUID();
        PinnedLlmProvider pinned = providerSupplier.get();
        Set<NpcProfileDraft.Field> allowed = allowedFields(request);
        if (allowed.isEmpty()) throw new IllegalArgumentException(
                "All eligible profile fields already contain creator-authored content.");
        LlmRequest llm = new LlmRequest(requestId, request.stableNpcId(), request.playerId(),
                List.of(new ChatMessage("system", systemPrompt(allowed)),
                        new ChatMessage("user", authoringInput(request, allowed))))
                .constrained(responseFormat(allowed), 0.72, OUTPUT_TOKEN_BUDGET)
                .withProviderRequestId(requestId)
                .withExecutionPolicy(new LlmExecutionPolicy("PROFILE_AUTHORING",
                        LlmExecutionPolicy.ReasoningMode.DISABLED,
                        List.of("AUTHOR_REQUESTED", "CREATIVE_CANON_DRAFT", "PROPOSAL_ONLY"),
                        OUTPUT_TOKEN_BUDGET));
        diagnostics.accept("NPC_PROFILE_GENERATION_QUEUED timestamp=" + Instant.now()
                + " requestId=" + requestId + " sessionId=" + request.sessionId()
                + " pageGeneration=" + request.pageGeneration()
                + " editorGeneration=" + request.editorGeneration()
                + " scope=" + request.scope() + " fields=" + allowed
                + " loreEntries=" + request.approvedLore().map(packet -> packet.entries().size())
                        .orElse(0)
                + " provider=" + pinned.provider() + " model=" + pinned.model());
        CompletableFuture<GeneratedProfilePatch> future = scheduler.admit(
                new OrbisResourceRequest(requestId, ResourceWorkload.LLM,
                        ResourcePriority.LOW, pinned.delegate(), false, 30_000), ignored -> { })
                .thenCompose(lease -> pinned.delegate().generateResponse(llm)
                        .whenComplete((ignored, failure) -> lease.close()))
                .thenApply(result -> parsePatch(requestId, request, pinned,
                        result.text(), allowed));
        future.whenComplete((patch, failure) -> diagnostics.accept(
                "NPC_PROFILE_GENERATION_COMPLETED timestamp=" + Instant.now()
                        + " requestId=" + requestId + " result="
                        + (failure == null ? "PATCH_READY" : "FAILED")
                        + (failure == null ? " fields=" + patch.allowedChanges().keySet()
                                : " error=" + safe(failure.getMessage()))));
        return new Handle(requestId, future);
    }

    private static void validateRequest(Request request) {
        if (request == null || request.draft() == null) {
            throw new IllegalArgumentException("A current profile draft is required.");
        }
        if (!request.sessionId().equals(request.draft().sessionId())
                || !request.stableNpcId().equals(request.draft().stableNpcId())
                || request.editorGeneration() != request.draft().editorGeneration()
                || request.baseRevision() != request.draft().baseRevision()
                || !request.draftHash().equals(request.draft().draftHash())) {
            throw new IllegalArgumentException("Profile generation request identity is stale.");
        }
    }

    static Set<NpcProfileDraft.Field> allowedFields(Request request) {
        Set<NpcProfileDraft.Field> allowed = request.scope().allowed();
        if (request.scope() == Scope.REFINE_SELECTED) allowed = request.selectedFields();
        if (request.scope() == Scope.FILL_MISSING_ALLOWED_FIELDS
                || request.scope() == Scope.FILL_MISSING) {
            EnumSet<NpcProfileDraft.Field> missing = EnumSet.noneOf(NpcProfileDraft.Field.class);
            for (NpcProfileDraft.Field field : allowed) {
                if (request.draft().generationMissing(field)) missing.add(field);
            }
            return Set.copyOf(missing);
        }
        return allowed.stream().filter(NpcProfileDraft.Field::generated)
                .filter(field -> !request.draft().dirtyFields().contains(field))
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
    }

    static String systemPrompt(Set<NpcProfileDraft.Field> allowed) {
        return "You are Nemotron acting as a creative NPC profile authoring assistant. "
                + "Create one distinctive, internally coherent character from the supplied "
                + "facts. Treat creator facts and approved lore as fixed canon. Creatively "
                + "invent compatible personality, history, preferences, motivations, fears, "
                + "values, habits, expertise, and speech characteristics. Integrate relevant "
                + "lore when present; when lore is partial or absent, invent coherent "
                + "character-level detail rather than returning empty or generic filler. "
                + "Return only the requested JSON patch and exactly one non-empty value for "
                + "each allowlisted field: " + allowed + ". Keep all fields mutually "
                + "consistent. Never create memories, beliefs, relationships, tasks, schedules, "
                + "current world state, action commands, credentials, private server data, "
                + "inventory, gear, health, stats, capabilities, file paths, provider settings, "
                + "unsupported IDs, or canonical speech. Do not contradict or rewrite Basic "
                + "Info. This is creative draft content for creator review; only Save Profile "
                + "can promote it to authored canon.";
    }

    static String authoringInput(Request request, Set<NpcProfileDraft.Field> allowed) {
        NpcProfileDraft draft = request.draft();
        StringBuilder text = new StringBuilder("Generation scope: ").append(request.scope())
                .append("\n\nFIXED CREATOR CANON — preserve exactly:\n")
                .append("NAME: ").append(draft.profileName()).append('\n')
                .append("ROLE: ").append(draft.value(NpcProfileDraft.Field.ROLE)).append('\n')
                .append("SPECIES: ").append(draft.value(
                        NpcProfileDraft.Field.SPECIES_ARCHETYPE)).append('\n')
                .append("AGE: ").append(draft.value(NpcProfileDraft.Field.AGE_CATEGORY)).append('\n')
                .append("HOME: ").append(draft.value(NpcProfileDraft.Field.HOME)).append('\n')
                .append("SUMMARY: ").append(draft.value(NpcProfileDraft.Field.SUMMARY)).append('\n')
                .append("\nEXISTING AUTHORED PROFILE — use for consistency; never replace noneligible values:\n");
        for (NpcProfileDraft.Field field : NpcProfileDraft.Field.values()) {
            if (field == NpcProfileDraft.Field.CREATOR_NOTES || isBasicInfo(field)) continue;
            String value = draft.value(field);
            if (!value.isBlank() && !draft.generationMissing(field)) {
                text.append(field.name()).append(": ").append(value).append('\n');
            }
        }
        text.append("\nBOUNDED APPROVED LORE:\n")
                .append(request.approvedLore().map(WorldLorePacket::promptText)
                        .filter(value -> !value.isBlank())
                        .orElse("No relevant authored lore was found. Creatively fill character-level gaps while preserving fixed creator canon."))
                .append("\n\nGenerate one coherent patch for exactly these fields: ")
                .append(allowed.stream().map(Enum::name).sorted().toList())
                .append("\nList fields use concise comma-separated items in the string value.");
        return text.toString();
    }

    private static boolean isBasicInfo(NpcProfileDraft.Field field) {
        return switch (field) {
            case ROLE, SPECIES_ARCHETYPE, AGE_CATEGORY, HOME, SUMMARY -> true;
            default -> false;
        };
    }

    static JsonObject responseFormat(Set<NpcProfileDraft.Field> allowed) {
        JsonArray values = new JsonArray();
        allowed.stream().map(Enum::name).sorted().forEach(values::add);
        JsonObject field = new JsonObject();
        field.addProperty("type", "string");
        field.add("enum", values);
        JsonObject value = new JsonObject();
        value.addProperty("type", "string");
        value.addProperty("minLength", 1);
        value.addProperty("maxLength", 1800);
        JsonObject itemProperties = new JsonObject();
        itemProperties.add("field", field);
        itemProperties.add("value", value);
        JsonObject item = new JsonObject();
        item.addProperty("type", "object");
        item.add("properties", itemProperties);
        item.add("required", JsonParser.parseString("[\"field\",\"value\"]"));
        item.addProperty("additionalProperties", false);
        JsonObject changes = new JsonObject();
        changes.addProperty("type", "array");
        changes.add("items", item);
        changes.addProperty("minItems", allowed.size());
        changes.addProperty("maxItems", allowed.size());
        JsonObject warnings = new JsonObject();
        warnings.addProperty("type", "array");
        JsonObject warningItem = new JsonObject();
        warningItem.addProperty("type", "string");
        warningItem.addProperty("maxLength", 300);
        warnings.add("items", warningItem);
        warnings.addProperty("maxItems", 6);
        JsonObject properties = new JsonObject();
        properties.add("changes", changes);
        properties.add("warnings", warnings);
        JsonObject schema = new JsonObject();
        schema.addProperty("type", "object");
        schema.add("properties", properties);
        schema.add("required", JsonParser.parseString("[\"changes\",\"warnings\"]"));
        schema.addProperty("additionalProperties", false);
        JsonObject jsonSchema = new JsonObject();
        jsonSchema.addProperty("name", "generated_profile_patch");
        jsonSchema.addProperty("strict", true);
        jsonSchema.add("schema", schema);
        JsonObject format = new JsonObject();
        format.addProperty("type", "json_schema");
        format.add("json_schema", jsonSchema);
        return format;
    }

    static GeneratedProfilePatch parsePatch(UUID requestId, Request request,
            PinnedLlmProvider pinned, String text, Set<NpcProfileDraft.Field> allowed) {
        JsonObject root;
        try {
            root = JsonParser.parseString(text).getAsJsonObject();
        } catch (RuntimeException invalid) {
            throw new IllegalArgumentException(
                    "Generation provider returned an invalid structured patch.", invalid);
        }
        if (!root.keySet().equals(Set.of("changes", "warnings"))
                || !root.get("changes").isJsonArray()
                || !root.get("warnings").isJsonArray()) {
            throw new IllegalArgumentException("Generated patch violated the response schema.");
        }
        EnumMap<NpcProfileDraft.Field, String> changes =
                new EnumMap<>(NpcProfileDraft.Field.class);
        for (var element : root.getAsJsonArray("changes")) {
            if (!element.isJsonObject()) throw new IllegalArgumentException(
                    "Generated patch contains a malformed change.");
            JsonObject change = element.getAsJsonObject();
            if (!change.keySet().equals(Set.of("field", "value"))) {
                throw new IllegalArgumentException("Generated patch contains prohibited fields.");
            }
            NpcProfileDraft.Field field;
            try {
                field = NpcProfileDraft.Field.valueOf(change.get("field").getAsString());
            } catch (RuntimeException invalid) {
                throw new IllegalArgumentException("Generated patch named an unknown field.", invalid);
            }
            if (!allowed.contains(field) || !FULL_PROFILE_ALLOWLIST.contains(field)
                    || !field.generated()) {
                throw new IllegalArgumentException("Patch attempted a non-allowlisted field.");
            }
            if (changes.containsKey(field)) {
                throw new IllegalArgumentException("Generated patch duplicated " + field + '.');
            }
            String value = validateGeneratedValue(field, change.get("value").getAsString());
            changes.put(field, value);
        }
        if (!changes.keySet().equals(allowed)) {
            throw new IllegalArgumentException(
                    "Generated patch omitted one or more eligible profile fields.");
        }
        List<String> warnings = new ArrayList<>();
        for (var value : root.getAsJsonArray("warnings")) {
            if (!value.isJsonPrimitive()) throw new IllegalArgumentException(
                    "Generated warning is malformed.");
            String warning = value.getAsString().replaceAll("\\s+", " ").strip();
            if (warning.length() > 300 || LEAKAGE.matcher(warning).find()) {
                throw new IllegalArgumentException("Generated warning failed validation.");
            }
            if (!warning.isBlank()) warnings.add(warning);
            if (warnings.size() > 6) throw new IllegalArgumentException(
                    "Generated patch contains too many warnings.");
        }
        return new GeneratedProfilePatch(requestId, request.stableNpcId(),
                request.baseRevision(), request.draftHash(), changes, warnings,
                new ProviderGenerationMetadata(pinned.provider(), pinned.model(), Instant.now()),
                request.schemaVersion());
    }

    private static String validateGeneratedValue(NpcProfileDraft.Field field, String value) {
        String clean = value == null ? "" : value.strip();
        if (clean.isBlank()) throw new IllegalArgumentException(
                "Generated " + field + " is empty.");
        if (clean.length() > field.maxLength()) throw new IllegalArgumentException(
                "Generated " + field + " exceeds its budget.");
        if (NpcProfileDraft.isUiSelectorLiteral(clean) || LEAKAGE.matcher(clean).find()) {
            throw new IllegalArgumentException(
                    "Generated " + field + " contains prohibited control or system data.");
        }
        if (clean.chars().anyMatch(character -> Character.isISOControl(character)
                && character != '\n' && character != '\r' && character != '\t')) {
            throw new IllegalArgumentException(
                    "Generated " + field + " contains prohibited control characters.");
        }
        if (field.list() && NpcProfileDraft.parseList(clean).size() > 12) {
            throw new IllegalArgumentException("Generated " + field + " exceeds its list budget.");
        }
        return clean;
    }

    private static String safe(String value) {
        return value == null ? "unknown" : value.replaceAll("\\s+", "_");
    }
}
