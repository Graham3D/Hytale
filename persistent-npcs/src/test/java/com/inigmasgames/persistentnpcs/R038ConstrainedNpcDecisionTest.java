package com.inigmasgames.persistentnpcs;

import com.google.gson.JsonObject;
import com.inigmasgames.persistentnpcs.action.NpcActionContext;
import com.inigmasgames.persistentnpcs.action.NpcActionDefinition;
import com.inigmasgames.persistentnpcs.action.NpcActionRegistry;
import com.inigmasgames.persistentnpcs.action.NpcActionRequest;
import com.inigmasgames.persistentnpcs.action.NpcActionResult;
import com.inigmasgames.persistentnpcs.cognition.ActionPromiseGuard;
import com.inigmasgames.persistentnpcs.cognition.GroundedIntent;
import com.inigmasgames.persistentnpcs.cognition.NpcDecision;
import com.inigmasgames.persistentnpcs.cognition.NpcDecisionCommitPolicy;
import com.inigmasgames.persistentnpcs.cognition.NpcDecisionSchema;
import com.inigmasgames.persistentnpcs.cognition.NpcDecisionValidator;
import com.inigmasgames.persistentnpcs.diagnostics.NpcTurnAuditLog;
import com.inigmasgames.persistentnpcs.conversation.ConversationSession;
import com.inigmasgames.persistentnpcs.json.JsonFiles;
import com.inigmasgames.persistentnpcs.llm.ChatMessage;
import com.inigmasgames.persistentnpcs.llm.LlmRequest;
import com.inigmasgames.persistentnpcs.llm.LlmToolDefinition;
import com.inigmasgames.persistentnpcs.perception.NpcPerceptionSnapshot;
import com.inigmasgames.persistentnpcs.profile.NpcProfile;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.Optional;
import com.inigmasgames.persistentnpcs.voice.ParalinguisticEvent;
import com.inigmasgames.persistentnpcs.voice.VocalEmotion;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

public final class R038ConstrainedNpcDecisionTest {
    private R038ConstrainedNpcDecisionTest() { }

    public static void main(String[] args) throws Exception {
        schemaBindsResponseNpcCapabilitiesAndGroundedTargets();
        invalidOrUnknownOutputFailsClosed();
        promisesRequireMatchingActions();
        failedValidationProducesTruthfulSpeechWithoutExecution();
        ordinarySpeechNeedsNoAction();
        systemInstructionsAreCoalescedForQwen();
        providerAndDiagnosticsAreWired();
        structuredTraceDoesNotReflectIntoOptional();
        System.out.println("R038 constrained NPC decision tests passed.");
    }

    private static void schemaBindsResponseNpcCapabilitiesAndGroundedTargets() {
        Fixture fixture = fixture();
        NpcDecisionSchema.Contract contract = fixture.contract();
        JsonObject schema = contract.responseFormat().getAsJsonObject("json_schema")
                .getAsJsonObject("schema");
        assert contract.offeredTools().size() == 1;
        assert contract.allowedTargetIds().contains(fixture.playerId);
        assert schema.toString().contains("FOLLOW_PLAYER");
        JsonObject properties = schema.getAsJsonObject("properties");
        assert !properties.has("responseId") && !properties.has("npcStableId");
        assert !schema.toString().contains("actorStableId")
                : "authoritative identities must not consume provider output tokens";

        var validation = fixture.validator.validate(fixture.json("I'll follow you.",
                "FOLLOW_PLAYER", fixture.profile.id(), fixture.playerId, new JsonObject()),
                fixture.responseId, fixture.profile.id(), contract);
        assert validation.valid() : validation.rejectedFieldsOrActions();
        assert validation.decision().responseId().equals(fixture.responseId);
        assert validation.decision().npcStableId().equals(fixture.profile.id());
        assert validation.decision().actions().getFirst().targetStableId()
                .equals(fixture.playerId);
    }

    private static void invalidOrUnknownOutputFailsClosed() {
        Fixture fixture = fixture();
        var malformed = fixture.validator.validate("not-json", fixture.responseId,
                fixture.profile.id(), fixture.contract());
        assert !malformed.valid();

        var unknown = fixture.validator.validate(fixture.json("I'll attack it.",
                "ATTACK", fixture.profile.id(), fixture.playerId, new JsonObject()),
                fixture.responseId, fixture.profile.id(), fixture.contract());
        assert !unknown.valid();
        assert unknown.rejectedFieldsOrActions().stream()
                .anyMatch(value -> value.contains("not offered"));

        JsonObject extra = JsonFiles.GSON.fromJson(fixture.json("Hello.", null,
                fixture.profile.id(), fixture.playerId, new JsonObject()), JsonObject.class);
        extra.addProperty("hiddenReasoning", "must never pass");
        var extraField = fixture.validator.validate(extra.toString(), fixture.responseId,
                fixture.profile.id(), fixture.contract());
        assert !extraField.valid();

        JsonObject identityInjection = JsonFiles.GSON.fromJson(fixture.json("Hello.", null,
                fixture.profile.id(), fixture.playerId, new JsonObject()), JsonObject.class);
        identityInjection.addProperty("npcStableId", UUID.randomUUID().toString());
        var injected = fixture.validator.validate(identityInjection.toString(),
                fixture.responseId, fixture.profile.id(), fixture.contract());
        assert !injected.valid() : "provider identity echoes must be rejected as unknown fields";

        NpcDecisionSchema.Contract broad = fixture.contract();
        NpcDecisionSchema.Contract voiceRestricted = new NpcDecisionSchema.Contract(
                broad.responseFormat(), broad.schema(), broad.offeredTools(),
                broad.allowedIntents(), broad.allowedTargetIds(), broad.allowedEvidenceRefs(),
                Set.of("CALM"), Set.of("NONE"));
        JsonObject unauthorizedEvent = JsonFiles.GSON.fromJson(fixture.json("Hello.", null,
                fixture.profile.id(), fixture.playerId, new JsonObject()), JsonObject.class);
        unauthorizedEvent.addProperty("paralinguisticEvent", "LAUGH");
        var eventRejected = fixture.validator.validate(unauthorizedEvent.toString(),
                fixture.responseId, fixture.profile.id(), voiceRestricted);
        assert !eventRejected.valid();
        assert eventRejected.rejectedFieldsOrActions().stream()
                .anyMatch(value -> value.contains("not authorized"));
    }

    private static void promisesRequireMatchingActions() {
        Fixture fixture = fixture();
        var noActionPromise = fixture.validator.validate(fixture.json("I'll follow you.", null,
                fixture.profile.id(), fixture.playerId, new JsonObject()), fixture.responseId,
                fixture.profile.id(), fixture.contract());
        assert !noActionPromise.valid();
        assert noActionPromise.rejectedFieldsOrActions().stream()
                .anyMatch(value -> value.contains("promise"));

        var valid = fixture.validator.validate(fixture.json("I'll follow you.",
                "FOLLOW_PLAYER", fixture.profile.id(), fixture.playerId, new JsonObject()),
                fixture.responseId, fixture.profile.id(), fixture.contract());
        assert valid.valid();
        assert ActionPromiseGuard.violation(valid.decision().spokenText(),
                valid.decision().actions()).isEmpty();
    }

    private static void failedValidationProducesTruthfulSpeechWithoutExecution() {
        Fixture fixture = fixture();
        AtomicInteger executions = new AtomicInteger();
        NpcActionRegistry registry = new NpcActionRegistry();
        registry.register(new NpcActionDefinition("FOLLOW_PLAYER", "Follow.",
                emptySchema(), Set.of("FOLLOW_PLAYER"), Set.of(), ignored -> true,
                (request, context) -> NpcActionResult.failure("PATH_BLOCKED",
                        "No traversable native path exists."),
                (request, context) -> {
                    executions.incrementAndGet();
                    return CompletableFuture.completedFuture(
                            NpcActionResult.success("Following."));
                }, "Follow result"));
        NpcActionRequest bound = new NpcActionRequest("FOLLOW_PLAYER", new JsonObject(),
                "decision:" + fixture.responseId, fixture.responseId,
                fixture.profile.id(), fixture.playerId);
        NpcActionResult fresh = registry.validate(bound, fixture.context);
        assert !fresh.success();
        assert !registry.execute(bound, fixture.context).join().success();
        assert executions.get() == 0 : "failed validation reached Hytale execution";

        NpcDecision rejected = fixture.validator.validate(fixture.json("I'll follow you.",
                "FOLLOW_PLAYER", fixture.profile.id(), fixture.playerId, new JsonObject()),
                fixture.responseId, fixture.profile.id(), fixture.contract()).decision();
        NpcDecision truthful = NpcDecisionCommitPolicy.truthfulFailure(rejected, fresh);
        assert truthful.actions().isEmpty();
        assert truthful.intent() == GroundedIntent.REFUSE_UNGROUNDED_ACTION;
        assert !truthful.spokenText().toLowerCase().contains("follow");
        assert ActionPromiseGuard.violation(truthful.spokenText(), truthful.actions()).isEmpty();

        NpcActionResult unknown = registry.execute(new NpcActionRequest("TELEPORT",
                new JsonObject(), "decision:" + fixture.responseId, fixture.responseId,
                fixture.profile.id(), fixture.playerId), fixture.context).join();
        assert !unknown.success() && unknown.code().equals("UNKNOWN_ACTION");
    }

    private static void ordinarySpeechNeedsNoAction() {
        Fixture fixture = fixture();
        var result = fixture.validator.validate(fixture.json(
                "Morning. The forge is quiet for once.", null, fixture.profile.id(),
                fixture.playerId, new JsonObject()), fixture.responseId,
                fixture.profile.id(), fixture.contract());
        assert result.valid() : result.rejectedFieldsOrActions();
        assert result.decision().actions().isEmpty();
    }

    private static void systemInstructionsAreCoalescedForQwen() {
        LlmRequest base = new LlmRequest(UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), List.of(
                        new ChatMessage("system", "Authored identity and world context."),
                        new ChatMessage("user", "Hello Mara.")));
        LlmRequest structured = base.withSystemInstruction(
                "Return the constrained NPC decision.")
                .constrained(new JsonObject(), 0.25);
        assert structured.messages().size() == 2;
        assert structured.messages().get(0).role().equals("system");
        assert structured.messages().get(0).content().contains("Authored identity");
        assert structured.messages().get(0).content().contains("constrained NPC decision");
        assert structured.messages().get(1).role().equals("user");
        assert structured.maxTokensOverride() == 256
                : "structured NPC decisions must remain gameplay-bounded";

        LlmRequest actionFollowUp = structured.withSystemInstruction(
                "The authoritative server completed the action.");
        assert actionFollowUp.messages().stream()
                .filter(message -> message.role().equals("system")).count() == 1;
        assert actionFollowUp.messages().getFirst().content()
                .contains("authoritative server completed");
        assert actionFollowUp.canonicalMessages().equals(actionFollowUp.messages());
    }

    private static void providerAndDiagnosticsAreWired() throws Exception {
        String provider = java.nio.file.Files.readString(java.nio.file.Path.of(
                "src/main/java/com/inigmasgames/persistentnpcs/llm/"
                        + "OpenAiCompatibleProvider.java"));
        String conversation = java.nio.file.Files.readString(java.nio.file.Path.of(
                "src/main/java/com/inigmasgames/persistentnpcs/conversation/"
                        + "ConversationService.java"));
        String inspector = java.nio.file.Files.readString(java.nio.file.Path.of(
                "src/main/java/com/inigmasgames/persistentnpcs/ui/CognitionInspectorPage.java"));
        String trace = java.nio.file.Files.readString(java.nio.file.Path.of(
                "src/main/java/com/inigmasgames/persistentnpcs/diagnostics/NpcTurnAuditLog.java"));
        assert provider.contains("response_format");
        assert conversation.contains("resolveStructuredDecision");
        assert conversation.contains("NpcDecisionCommitPolicy.truthfulFailure");
        assert inspector.contains("actionsOffered")
                && inspector.contains("schemaValidation")
                && inspector.contains("committedAgentOperation")
                && inspector.contains("finalActionResult");
        assert trace.contains("STRUCTURED_NPC_DECISION")
                && trace.contains("rejectedFieldsOrActions")
                && trace.contains("canonicalSpokenText");
    }

    private static void structuredTraceDoesNotReflectIntoOptional() throws Exception {
        UUID response = UUID.randomUUID();
        UUID npc = UUID.randomUUID();
        NpcDecision decision = new NpcDecision(response, npc, GroundedIntent.PROCESS_INFORMATION,
                "I hear you.", VocalEmotion.CALM, Optional.of(ParalinguisticEvent.SIGH),
                List.of(), List.of("PLAYER_REPORT:test"));
        var method = NpcTurnAuditLog.class.getDeclaredMethod("decisionJson", NpcDecision.class);
        method.setAccessible(true);
        JsonObject json = (JsonObject) method.invoke(null, decision);
        assert json.get("responseId").getAsString().equals(response.toString());
        assert json.get("paralinguisticEvent").getAsString().equals("[sigh]");
        assert json.getAsJsonArray("actions").isEmpty();
    }

    private static Fixture fixture() {
        UUID npcId = UUID.randomUUID();
        UUID playerId = UUID.randomUUID();
        UUID responseId = UUID.randomUUID();
        NpcProfile profile = new NpcProfile(npcId, "Mara", "apprentice blacksmith",
                "bold and practical", "A village smith.", "Learn the craft.", "home",
                "forge", List.of(), List.of(), List.of(), List.of("FOLLOW_PLAYER"), 0);
        ConversationSession session = new ConversationSession(UUID.randomUUID(), npcId,
                playerId, Instant.now());
        NpcPerceptionSnapshot perception = new NpcPerceptionSnapshot(npcId,
                UUID.randomUUID(), UUID.randomUUID(), null, 0, 64, 0, List.of(), List.of(),
                List.of(), List.of(), List.of(), List.of(), null, null, List.of());
        NpcActionContext context = new NpcActionContext(profile, session, perception,
                "Follow me.");
        LlmToolDefinition follow = new LlmToolDefinition("FOLLOW_PLAYER",
                "Begin following the focused player.", emptySchema());
        return new Fixture(profile, playerId, responseId, context,
                new NpcDecisionValidator(), List.of(follow));
    }

    private static JsonObject emptySchema() {
        JsonObject schema = new JsonObject();
        schema.addProperty("type", "object");
        schema.add("properties", new JsonObject());
        schema.addProperty("additionalProperties", false);
        return schema;
    }

    private record Fixture(NpcProfile profile, UUID playerId, UUID responseId,
            NpcActionContext context, NpcDecisionValidator validator,
            List<LlmToolDefinition> tools) {
        private NpcDecisionSchema.Contract contract() {
            return NpcDecisionSchema.build(responseId, null, context, tools);
        }

        private String json(String spoken, String actionId, UUID actor, UUID target,
                JsonObject parameters) {
            JsonObject root = new JsonObject();
            root.addProperty("intent", GroundedIntent.AMBIENT_RESPONSE.name());
            root.addProperty("spokenText", spoken);
            root.addProperty("emotion", "CALM");
            root.addProperty("paralinguisticEvent", "NONE");
            var actions = new com.google.gson.JsonArray();
            if (actionId != null) {
                JsonObject action = new JsonObject();
                action.addProperty("actionId", actionId);
                action.addProperty("targetStableId", target.toString());
                action.add("parameters", parameters);
                actions.add(action);
            }
            root.add("actions", actions);
            root.add("groundingEvidenceRefs", new com.google.gson.JsonArray());
            return root.toString();
        }
    }
}
