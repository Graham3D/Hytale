package com.inigmasgames.persistentnpcs.social;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.inigmasgames.persistentnpcs.action.NpcActionContext;
import com.inigmasgames.persistentnpcs.action.NpcActionDefinition;
import com.inigmasgames.persistentnpcs.action.NpcActionRegistry;
import com.inigmasgames.persistentnpcs.action.NpcActionRequest;
import com.inigmasgames.persistentnpcs.action.NpcActionResult;
import com.inigmasgames.persistentnpcs.autonomy.AgentOperation;
import com.inigmasgames.persistentnpcs.autonomy.AgentOperationStore;
import com.inigmasgames.persistentnpcs.memory.MemoryRecord;
import com.inigmasgames.persistentnpcs.memory.MemoryStore;
import com.inigmasgames.persistentnpcs.memory.MemoryType;
import com.inigmasgames.persistentnpcs.perception.KnownNpcLocatorResult;
import com.inigmasgames.persistentnpcs.plan.SharedPlan;
import com.inigmasgames.persistentnpcs.plan.SharedPlanDestination;
import com.inigmasgames.persistentnpcs.plan.SharedPlanStartMode;
import com.inigmasgames.persistentnpcs.plan.SharedPlanStatus;
import com.inigmasgames.persistentnpcs.plan.SharedPlanStore;
import com.inigmasgames.persistentnpcs.relationship.RelationshipStore;
import com.inigmasgames.persistentnpcs.task.NpcTask;
import com.inigmasgames.persistentnpcs.task.NpcTaskState;
import com.inigmasgames.persistentnpcs.task.NpcTaskStore;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/** Persists an accepted social guide commitment through existing operation/plan/task stores. */
public final class KnownNpcGuideCoordinator {
    public static final String ACTION_ID = "GUIDE_PLAYER_TO_NPC";
    private static final Duration OPERATION_TIMEOUT = Duration.ofMinutes(30);
    private final RelationshipStore relationships;
    private final AgentOperationStore operations;
    private final SharedPlanStore plans;
    private final NpcTaskStore tasks;
    private final MemoryStore memories;

    public KnownNpcGuideCoordinator(RelationshipStore relationships,
            AgentOperationStore operations, SharedPlanStore plans,
            NpcTaskStore tasks, MemoryStore memories) {
        this.relationships = relationships;
        this.operations = operations;
        this.plans = plans;
        this.tasks = tasks;
        this.memories = memories;
    }

    public void register(NpcActionRegistry registry) {
        registry.register(new NpcActionDefinition(ACTION_ID,
                "Begin physically guiding the focused player to the relationship-gated NPC "
                        + "already resolved by the authoritative server locator.",
                schema(), Set.of(), Set.of(), context -> {
                    KnownNpcLocatorResult result = context.knownNpcLocator();
                    return result != null && result.found() && result.navigationPossible();
                }, this::validate, this::execute, "Known-NPC guide plan created"));
    }

    private NpcActionResult validate(NpcActionRequest request, NpcActionContext context) {
        KnownNpcLocatorResult result = context.knownNpcLocator();
        if (result == null || !result.found() || !result.navigationPossible()) {
            return NpcActionResult.failure("TARGET_UNAVAILABLE",
                    "The relationship-gated target is not currently navigable.");
        }
        if (!relationships.knows(context.profile().id(), result.targetStableId())) {
            return NpcActionResult.failure("UNKNOWN_RELATIONSHIP",
                    "The NPC does not have an established relationship with that target.");
        }
        String requestedName = text(request.parameters(), "targetName");
        if (!requestedName.isBlank() && !requestedName.equalsIgnoreCase(result.targetName())) {
            return NpcActionResult.failure("TARGET_CHANGED",
                    "The requested guide target differs from the authoritative locator result.");
        }
        if (!context.perception().nearbyHostiles().isEmpty()) {
            return NpcActionResult.failure("GUIDE_UNSAFE_NOW",
                    "Guidance cannot begin during immediate nearby danger.");
        }
        return NpcActionResult.success("Guide request validated.");
    }

    private CompletableFuture<NpcActionResult> execute(
            NpcActionRequest request, NpcActionContext context) {
        KnownNpcLocatorResult target = context.knownNpcLocator();
        UUID guideId = context.profile().id();
        UUID playerId = context.session().playerId();
        UUID taskId = UUID.randomUUID();
        AgentOperation operation;
        try {
            operation = operations.claim(ACTION_ID, Set.of(guideId),
                    "Guide the player to " + target.targetName(), Instant.now(),
                    OPERATION_TIMEOUT);
        } catch (IllegalStateException busy) {
            return CompletableFuture.completedFuture(NpcActionResult.failure(
                    "GUIDE_BUSY", "The NPC already has an active operation."));
        }
        try {
            SharedPlan plan = plans.put(new SharedPlan(UUID.randomUUID(),
                    "Guide the player to " + target.targetName(), List.of(playerId, guideId),
                    playerId, guideId, new SharedPlanDestination(
                            context.perception().worldId(), null, null, null,
                            target.targetName()), SharedPlanStartMode.NOW, null,
                    SharedPlanStatus.ACTIVE, Instant.now(), Map.of(
                            "source", "RELATIONSHIP_GATED_NPC_LOCATOR",
                            "targetNpc", target.targetName(),
                            "targetProfileId", target.targetStableId().toString(),
                            "operationId", operation.operationId().toString())));
            tasks.suspendMovementTasks(guideId, taskId,
                    "Paused while guiding the player to " + target.targetName() + ".");
            tasks.put(new NpcTask(taskId, guideId, playerId, ACTION_ID,
                    context.perception().worldId(), null, null, null, null,
                    "Guide the player to " + target.targetName(), NpcTaskState.ACTIVE,
                    Instant.now(), null, Map.of(
                            "targetProfileId", target.targetStableId().toString(),
                            "targetName", target.targetName(),
                            "operationId", operation.operationId().toString(),
                            "sharedPlanId", plan.id().toString(),
                            "lookupRange", "500")));
            memories.append(new MemoryRecord(UUID.randomUUID(), guideId, playerId,
                    Instant.now(), MemoryType.COMMITMENT, 0.9,
                    "I agreed to guide the player to " + target.targetName() + ".",
                    1.0, "GUIDE_PLAYER_TO_NPC", List.of(playerId, target.targetStableId()),
                    target.semanticLocation(), "I made this commitment directly."));
            context.session().clearPendingGuideOffer();
            return CompletableFuture.completedFuture(NpcActionResult.success(
                    "Guidance toward " + target.targetName()
                            + " has begun using native navigation."));
        } catch (RuntimeException failure) {
            operations.complete(operation.operationId(), false,
                    "guide initialization failed");
            tasks.resumeAfterGuide(guideId, taskId);
            return CompletableFuture.completedFuture(NpcActionResult.failure(
                    "GUIDE_START_FAILED", failure.getMessage()));
        }
    }

    private static JsonObject schema() {
        JsonObject schema = new JsonObject();
        schema.addProperty("type", "object");
        JsonObject properties = new JsonObject();
        JsonObject target = new JsonObject();
        target.addProperty("type", "string");
        properties.add("targetName", target);
        schema.add("properties", properties);
        JsonArray required = new JsonArray();
        required.add("targetName");
        schema.add("required", required);
        schema.addProperty("additionalProperties", false);
        return schema;
    }

    private static String text(JsonObject object, String key) {
        try {
            return object.has(key) && !object.get(key).isJsonNull()
                    ? object.get(key).getAsString().replaceAll("\\s+", " ").strip() : "";
        } catch (RuntimeException ignored) {
            return "";
        }
    }
}
