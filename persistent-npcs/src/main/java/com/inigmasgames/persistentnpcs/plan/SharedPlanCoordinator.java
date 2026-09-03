package com.inigmasgames.persistentnpcs.plan;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.inigmasgames.persistentnpcs.action.NpcActionContext;
import com.inigmasgames.persistentnpcs.action.NpcActionDefinition;
import com.inigmasgames.persistentnpcs.action.NpcActionRegistry;
import com.inigmasgames.persistentnpcs.action.NpcActionRequest;
import com.inigmasgames.persistentnpcs.action.NpcActionResult;
import com.inigmasgames.persistentnpcs.memory.MemoryRecord;
import com.inigmasgames.persistentnpcs.memory.MemoryStore;
import com.inigmasgames.persistentnpcs.memory.MemoryType;
import com.inigmasgames.persistentnpcs.task.NpcTask;
import com.inigmasgames.persistentnpcs.task.NpcTaskState;
import com.inigmasgames.persistentnpcs.task.NpcTaskStore;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/** Validates an agreed LLM proposal, then persists and executes it deterministically. */
public final class SharedPlanCoordinator {
    public static final String ACTION_ID = "CREATE_SHARED_PLAN";
    private final SharedPlanStore plans;
    private final NpcTaskStore tasks;
    private final MemoryStore memories;

    public SharedPlanCoordinator(
            SharedPlanStore plans, NpcTaskStore tasks, MemoryStore memories) {
        this.plans = plans;
        this.tasks = tasks;
        this.memories = memories;
    }

    public void register(NpcActionRegistry registry) {
        registry.register(new NpcActionDefinition(ACTION_ID,
                "Accept a mutually discussed activity/commitment with the focused player. "
                        + "Use only after the NPC naturally agrees. The server resolves all IDs.",
                schema(), Set.of("SHARED_PLAN"), Set.of(),
                context -> context.perception().npcEntityId() != null,
                this::validate,
                this::execute,
                "Persistent shared plan created"));
    }

    private NpcActionResult validate(NpcActionRequest request, NpcActionContext context) {
        JsonObject values = request.parameters();
        String purpose = text(values, "purpose");
        SharedPlanStartMode mode = enumValue(
                SharedPlanStartMode.class, text(values, "startMode"));
        String leader = text(values, "leader").toUpperCase(Locale.ROOT);
        if (purpose.isBlank() || purpose.length() > 240) {
            return NpcActionResult.failure("INVALID_PLAN_PURPOSE",
                    "A concise expressed purpose is required.");
        }
        if (mode == null || !(leader.equals("PLAYER") || leader.equals("NPC"))) {
            return NpcActionResult.failure("INVALID_PLAN_ROLES",
                    "startMode must be NOW/SCHEDULED and leader must be PLAYER/NPC.");
        }
        if (mode == SharedPlanStartMode.SCHEDULED) {
            int hour = integer(values, "hour", -1);
            int minute = integer(values, "minute", 0);
            if (hour < 0 || hour > 23 || minute < 0 || minute > 59) {
                return NpcActionResult.failure("INVALID_PLAN_TIME",
                        "Scheduled plans require hour 0-23 and minute 0-59.");
            }
        }
        SharedPlanDestination destination;
        try {
            destination = destination(values, context);
        } catch (IllegalArgumentException failure) {
            return NpcActionResult.failure("INVALID_PLAN_DESTINATION", failure.getMessage());
        }
        if (leader.equals("NPC") && mode == SharedPlanStartMode.NOW
                && (destination == null || !destination.hasCoordinates())) {
            return NpcActionResult.failure("PLAN_DESTINATION_REQUIRED",
                    "An NPC-led immediate plan requires validated coordinates.");
        }
        if (mode == SharedPlanStartMode.NOW
                && !context.perception().nearbyHostiles().isEmpty()) {
            return NpcActionResult.failure("PLAN_UNSAFE_NOW",
                    "An ordinary shared activity cannot begin during nearby hostility.");
        }
        return NpcActionResult.success("Shared plan validated.");
    }

    private CompletableFuture<NpcActionResult> execute(
            NpcActionRequest request, NpcActionContext context) {
        try {
            JsonObject values = request.parameters();
            SharedPlanStartMode mode = SharedPlanStartMode.valueOf(
                    text(values, "startMode").toUpperCase(Locale.ROOT));
            UUID npcId = context.profile().id();
            UUID playerId = context.session().playerId();
            UUID leader = "NPC".equalsIgnoreCase(text(values, "leader"))
                    ? npcId : playerId;
            Instant scheduled = mode == SharedPlanStartMode.SCHEDULED
                    ? scheduledTime(context, integer(values, "hour", -1),
                            integer(values, "minute", 0)) : null;
            SharedPlan plan = plans.put(new SharedPlan(UUID.randomUUID(),
                    text(values, "purpose"), List.of(playerId, npcId), playerId, leader,
                    destination(values, context), mode, scheduled,
                    mode == SharedPlanStartMode.NOW
                            ? SharedPlanStatus.ACTIVE : SharedPlanStatus.SCHEDULED,
                    Instant.now(), Map.of(
                            "expressedPurpose", compact(context.playerMessage(), 300),
                            "source", "DIRECT_PLAYER_NPC_AGREEMENT")));
            createExecutionTask(plan, context);
            remember(plan, context);
            return CompletableFuture.completedFuture(NpcActionResult.success(
                    "Shared plan " + plan.id() + " accepted: " + plan.purpose()
                            + ". Status=" + plan.status() + "."));
        } catch (RuntimeException failure) {
            return CompletableFuture.completedFuture(NpcActionResult.failure(
                    "PLAN_EXECUTION_REJECTED", failure.getMessage()));
        }
    }

    public SharedPlan transition(UUID planId, SharedPlanStatus status, String outcome) {
        SharedPlan current = plans.get(planId);
        if (current == null) throw new IllegalArgumentException("Unknown shared plan " + planId);
        SharedPlan updated = plans.put(current.withStatus(status));
        if (status.terminal()) {
            for (UUID participant : updated.participants()) {
                if (!participant.equals(updated.leader())) continue;
                // Terminal navigation details are not persisted; only the social outcome is.
                memories.append(new MemoryRecord(UUID.randomUUID(), participant,
                        updated.initiator(), Instant.now(), MemoryType.COMMITMENT, 0.75,
                        "Shared plan outcome: " + updated.purpose() + " -> " + status
                                + (outcome == null || outcome.isBlank()
                                        ? "." : " (" + compact(outcome, 180) + ")."),
                        1.0, "SHARED_PLAN", updated.participants(),
                        updated.destination() == null ? "" : updated.destination().describe(),
                        "I remember how our plan ended."));
            }
        }
        return updated;
    }

    private void createExecutionTask(SharedPlan plan, NpcActionContext context) {
        UUID npcId = context.profile().id();
        UUID playerId = context.session().playerId();
        SharedPlanDestination destination = plan.destination();
        Map<String, String> data = new LinkedHashMap<>();
        data.put("sharedPlanId", plan.id().toString());
        data.put("sharedPlanPurpose", plan.purpose());
        data.put("travelLeader", plan.leader().equals(npcId) ? "NPC" : "PLAYER");
        data.put("source", "SHARED_PLAN");
        String type;
        NpcTaskState state;
        if (plan.startMode() == SharedPlanStartMode.SCHEDULED) {
            type = "SCHEDULE_MEETING";
            state = NpcTaskState.PLANNED;
        } else if (plan.leader().equals(playerId)) {
            type = "FOLLOW_PLAYER";
            state = NpcTaskState.ACTIVE;
            data.put("movementState", "FOLLOWING_PLAYER");
            tasks.cancelMovementTasks(npcId, "Superseded by accepted shared plan.");
        } else {
            type = "GO_TO";
            state = NpcTaskState.TRAVELING;
            tasks.cancelMovementTasks(npcId, "Superseded by accepted shared plan.");
        }
        boolean positioned = destination != null && destination.hasCoordinates();
        Double x = positioned ? destination.x() : context.perception().x();
        Double y = positioned ? destination.y() : context.perception().y();
        Double z = positioned ? destination.z() : context.perception().z();
        UUID world = destination == null || destination.worldId() == null
                ? context.perception().worldId() : destination.worldId();
        tasks.put(new NpcTask(UUID.randomUUID(), npcId, playerId, type, world,
                x, y, z, plan.scheduledTime(), plan.purpose(), state,
                Instant.now(), null, data));
    }

    private void remember(SharedPlan plan, NpcActionContext context) {
        String when = plan.startMode() == SharedPlanStartMode.SCHEDULED
                ? " at " + plan.scheduledTime() : " now";
        String where = plan.destination() == null ? ""
                : " Destination: " + plan.destination().describe() + ".";
        memories.append(new MemoryRecord(UUID.randomUUID(), context.profile().id(),
                context.session().playerId(), Instant.now(), MemoryType.COMMITMENT, 0.9,
                "I agreed with the player to " + plan.purpose() + when + "." + where,
                1.0, "SHARED_PLAN", plan.participants(),
                plan.destination() == null ? "" : plan.destination().describe(),
                "I made this commitment directly with the player."));
    }

    private static Instant scheduledTime(NpcActionContext context, int hour, int minute) {
        LocalDateTime game = context.perception().gameTime();
        if (game == null) throw new IllegalStateException("Authoritative game time unavailable");
        LocalDateTime result = game.withHour(hour).withMinute(minute).withSecond(0).withNano(0);
        if (!result.isAfter(game)) result = result.plusDays(1);
        return result.toInstant(ZoneOffset.UTC);
    }

    private static SharedPlanDestination destination(
            JsonObject values, NpcActionContext context) {
        boolean any = values.has("x") || values.has("y") || values.has("z");
        Double x = any ? number(values, "x") : null;
        Double y = any ? number(values, "y") : null;
        Double z = any ? number(values, "z") : null;
        SharedPlanDestination result = new SharedPlanDestination(
                context.perception().worldId(), x, y, z, text(values, "destination"))
                .normalized();
        if (result.hasCoordinates()) {
            double dx = result.x() - context.perception().x();
            double dy = result.y() - context.perception().y();
            double dz = result.z() - context.perception().z();
            if (dx * dx + dy * dy + dz * dz > 192 * 192) {
                throw new IllegalArgumentException("Destination exceeds the 192 meter limit");
            }
        }
        return !result.hasCoordinates() && result.label().isBlank() ? null : result;
    }

    private static JsonObject schema() {
        JsonObject schema = new JsonObject();
        schema.addProperty("type", "object");
        JsonObject properties = new JsonObject();
        properties.add("purpose", stringProperty(null));
        properties.add("startMode", stringProperty(List.of("NOW", "SCHEDULED")));
        properties.add("leader", stringProperty(List.of("PLAYER", "NPC")));
        properties.add("destination", stringProperty(null));
        for (String coordinate : List.of("x", "y", "z")) {
            JsonObject property = new JsonObject();
            property.addProperty("type", "number");
            properties.add(coordinate, property);
        }
        for (String time : List.of("hour", "minute")) {
            JsonObject property = new JsonObject();
            property.addProperty("type", "integer");
            properties.add(time, property);
        }
        schema.add("properties", properties);
        JsonArray required = new JsonArray();
        required.add("purpose"); required.add("startMode"); required.add("leader");
        schema.add("required", required);
        schema.addProperty("additionalProperties", false);
        return schema;
    }

    private static JsonObject stringProperty(List<String> values) {
        JsonObject property = new JsonObject();
        property.addProperty("type", "string");
        if (values != null) {
            JsonArray enums = new JsonArray();
            values.forEach(enums::add);
            property.add("enum", enums);
        }
        return property;
    }

    private static <E extends Enum<E>> E enumValue(Class<E> type, String value) {
        try {
            return Enum.valueOf(type, value.toUpperCase(Locale.ROOT));
        } catch (RuntimeException failure) {
            return null;
        }
    }

    private static String text(JsonObject object, String key) {
        return object.has(key) && !object.get(key).isJsonNull()
                ? compact(object.get(key).getAsString(), 400) : "";
    }

    private static int integer(JsonObject object, String key, int fallback) {
        try { return object.has(key) ? object.get(key).getAsInt() : fallback; }
        catch (RuntimeException failure) { return fallback; }
    }

    private static Double number(JsonObject object, String key) {
        if (!object.has(key) || object.get(key).isJsonNull()) return null;
        double value = object.get(key).getAsDouble();
        if (!Double.isFinite(value)) throw new IllegalArgumentException("Non-finite " + key);
        return value;
    }

    private static String compact(String value, int maximum) {
        String text = value == null ? "" : value.replaceAll("\\s+", " ").strip();
        return text.length() <= maximum ? text : text.substring(0, maximum);
    }
}
