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
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.Supplier;

/** Low-priority, proposal-only profile generation. It never mutates canonical state. */
public final class NpcProfileGenerationService {
    public enum Scope {
        BIOGRAPHY(Set.of(NpcProfileDraft.Field.BIOGRAPHY)),
        PERSONALITY_VALUES(Set.of(NpcProfileDraft.Field.PERSONALITY,
                NpcProfileDraft.Field.PERSONALITY_TRAITS, NpcProfileDraft.Field.VALUES)),
        MOTIVATIONS_GOALS(Set.of(NpcProfileDraft.Field.PURPOSE,
                NpcProfileDraft.Field.GOALS)),
        SPEECH_MANNERISMS(Set.of(NpcProfileDraft.Field.SPEAKING_STYLE)),
        FILL_MISSING(Set.of(NpcProfileDraft.Field.values())),
        REFINE_SELECTED(Set.of(NpcProfileDraft.Field.values()));

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

    public record Request(UUID sessionId, long editorGeneration, long baseRevision,
            String draftHash, UUID stableNpcId, UUID playerId, Scope scope,
            Set<NpcProfileDraft.Field> selectedFields, NpcProfileDraft draft) { }

    public final class Handle implements AutoCloseable {
        private final UUID requestId;
        private final CompletableFuture<NpcProfileDraft.Proposal> future;
        private Handle(UUID requestId, CompletableFuture<NpcProfileDraft.Proposal> future) {
            this.requestId = requestId;
            this.future = future;
        }
        public UUID requestId() { return requestId; }
        public CompletableFuture<NpcProfileDraft.Proposal> future() { return future; }
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
        if (request == null || request.draft() == null) {
            throw new IllegalArgumentException("A current profile draft is required.");
        }
        UUID requestId = UUID.randomUUID();
        PinnedLlmProvider pinned = providerSupplier.get();
        Set<NpcProfileDraft.Field> allowed = allowedFields(request);
        if (allowed.isEmpty()) throw new IllegalArgumentException(
                "The selected generation scope has no eligible fields.");
        LlmRequest llm = new LlmRequest(requestId, request.stableNpcId(), request.playerId(),
                List.of(new ChatMessage("system", systemPrompt(allowed)),
                        new ChatMessage("user", authoringInput(request, allowed))))
                .constrained(responseFormat(allowed), 0.35, 640)
                .withProviderRequestId(requestId)
                .withExecutionPolicy(new LlmExecutionPolicy("PROFILE_AUTHORING",
                        LlmExecutionPolicy.ReasoningMode.DISABLED,
                        List.of("AUTHOR_REQUESTED", "PROPOSAL_ONLY"), 640));
        diagnostics.accept("NPC_PROFILE_GENERATION_QUEUED timestamp=" + Instant.now()
                + " requestId=" + requestId + " sessionId=" + request.sessionId()
                + " editorGeneration=" + request.editorGeneration()
                + " scope=" + request.scope() + " provider=" + pinned.provider()
                + " model=" + pinned.model());
        CompletableFuture<NpcProfileDraft.Proposal> future = scheduler.admit(
                new OrbisResourceRequest(requestId, ResourceWorkload.LLM,
                        ResourcePriority.LOW, pinned.delegate(), false, 30_000), ignored -> { })
                .thenCompose(lease -> pinned.delegate().generateResponse(llm)
                        .whenComplete((ignored, failure) -> lease.close()))
                .thenApply(result -> parseProposal(requestId, request.scope(), pinned,
                        result.text(), allowed));
        future.whenComplete((proposal, failure) -> diagnostics.accept(
                "NPC_PROFILE_GENERATION_COMPLETED timestamp=" + Instant.now()
                        + " requestId=" + requestId + " result="
                        + (failure == null ? "PROPOSAL_READY" : "FAILED")
                        + (failure == null ? " fields=" + proposal.changes().keySet()
                                : " error=" + safe(failure.getMessage()))));
        return new Handle(requestId, future);
    }

    private static Set<NpcProfileDraft.Field> allowedFields(Request request) {
        Set<NpcProfileDraft.Field> allowed = request.scope().allowed();
        if (request.scope() == Scope.REFINE_SELECTED) {
            allowed = request.selectedFields() == null ? Set.of() : request.selectedFields();
        }
        if (request.scope() == Scope.FILL_MISSING) {
            java.util.EnumSet<NpcProfileDraft.Field> missing = java.util.EnumSet.noneOf(
                    NpcProfileDraft.Field.class);
            for (NpcProfileDraft.Field field : allowed) {
                if (field.generated() && request.draft().value(field).isBlank()) missing.add(field);
            }
            return Set.copyOf(missing);
        }
        return allowed.stream().filter(NpcProfileDraft.Field::generated)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
    }

    private static String systemPrompt(Set<NpcProfileDraft.Field> allowed) {
        return "You are an NPC profile authoring assistant. Return only the requested JSON "
                + "proposal. Propose edits only for these allowlisted fields: " + allowed
                + ". Never create memories, beliefs, relationships, tasks, schedules, current "
                + "world state, action commands, credentials, private server data, or canonical "
                + "speech. Keep each field concise and consistent with supplied profile facts. "
                + "This is a reviewable draft proposal, never an authoritative commit.";
    }

    private static String authoringInput(Request request, Set<NpcProfileDraft.Field> allowed) {
        StringBuilder text = new StringBuilder("Scope: ").append(request.scope())
                .append("\nNPC stable ID: ").append(request.stableNpcId())
                .append("\nExisting author-controlled profile fields:\n");
        for (NpcProfileDraft.Field field : NpcProfileDraft.Field.values()) {
            if (field == NpcProfileDraft.Field.CREATOR_NOTES) continue;
            text.append(field.name()).append(": ").append(request.draft().value(field)).append('\n');
        }
        text.append("\nReturn changes only for: ").append(allowed);
        return text.toString();
    }

    private static JsonObject responseFormat(Set<NpcProfileDraft.Field> allowed) {
        JsonArray values = new JsonArray();
        allowed.stream().map(Enum::name).sorted().forEach(values::add);
        JsonObject field = new JsonObject();
        field.addProperty("type", "string");
        field.add("enum", values);
        JsonObject value = new JsonObject();
        value.addProperty("type", "string");
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
        changes.addProperty("maxItems", Math.min(8, allowed.size()));
        JsonObject warnings = new JsonObject();
        warnings.addProperty("type", "array");
        JsonObject warningItem = new JsonObject(); warningItem.addProperty("type", "string");
        warnings.add("items", warningItem); warnings.addProperty("maxItems", 6);
        JsonObject properties = new JsonObject(); properties.add("changes", changes);
        properties.add("warnings", warnings);
        JsonObject schema = new JsonObject(); schema.addProperty("type", "object");
        schema.add("properties", properties);
        schema.add("required", JsonParser.parseString("[\"changes\",\"warnings\"]"));
        schema.addProperty("additionalProperties", false);
        JsonObject jsonSchema = new JsonObject(); jsonSchema.addProperty("name", "npc_profile_proposal");
        jsonSchema.addProperty("strict", true); jsonSchema.add("schema", schema);
        JsonObject format = new JsonObject(); format.addProperty("type", "json_schema");
        format.add("json_schema", jsonSchema); return format;
    }

    private static NpcProfileDraft.Proposal parseProposal(UUID requestId, Scope scope,
            PinnedLlmProvider pinned, String text, Set<NpcProfileDraft.Field> allowed) {
        JsonObject root;
        try { root = JsonParser.parseString(text).getAsJsonObject(); }
        catch (RuntimeException invalid) { throw new IllegalArgumentException(
                "Generation provider returned an invalid structured proposal.", invalid); }
        EnumMap<NpcProfileDraft.Field, String> changes = new EnumMap<>(NpcProfileDraft.Field.class);
        JsonArray array = root.has("changes") && root.get("changes").isJsonArray()
                ? root.getAsJsonArray("changes") : new JsonArray();
        for (var element : array) {
            JsonObject change = element.getAsJsonObject();
            NpcProfileDraft.Field field = NpcProfileDraft.Field.valueOf(
                    change.get("field").getAsString());
            if (!allowed.contains(field) || !field.generated()) {
                throw new IllegalArgumentException("Proposal attempted a non-allowlisted field.");
            }
            String value = change.get("value").getAsString().strip();
            if (value.length() > field.maxLength()) {
                throw new IllegalArgumentException("Generated " + field + " exceeds its budget.");
            }
            changes.put(field, value);
        }
        List<String> warnings = new ArrayList<>();
        if (root.has("warnings") && root.get("warnings").isJsonArray()) {
            root.getAsJsonArray("warnings").forEach(value -> warnings.add(value.getAsString()));
        }
        return new NpcProfileDraft.Proposal(requestId, scope.name(), pinned.provider(),
                pinned.model(), Instant.now(), changes, warnings);
    }

    private static String safe(String value) {
        return value == null ? "unknown" : value.replaceAll("\\s+", "_");
    }
}
